package ua.rytm.app.bank

/**
 * A transaction guessed from a bank app's push. Never saved on its own — it
 * only pre-fills the new-transaction form the user confirms.
 */
data class BankSuggestion(val isIncome: Boolean, val amount: Double, val currency: String, val merchant: String?, val account: String? = null)

/**
 * Format-tolerant parser for Ukrainian bank pushes (Monobank, ПУМБ, Privat,
 * A-Bank, Sense, Oschad…). Banks change their wording often and we have no
 * sample corpus, so instead of per-bank templates it looks for:
 *  - the first money amount with a currency (₴, грн, UAH, $, USD, €, EUR);
 *    an amount right after "баланс"/"залишок"/"доступно" is the balance and
 *    is skipped;
 *  - direction from the sign (−/-/+) or keywords (списання, оплата, покупка
 *    → expense; зарахування, поповнення, надходження, переказ від → income);
 *  - the merchant: the first remaining line that is not an amount, balance
 *    or card mask.
 * Anything it can't place returns null — no guess is better than a wrong one.
 */
object BankNotificationParser {
    /** Packages whose notifications are read; nothing else ever is. */
    val BANK_PACKAGES = setOf(
        "com.ftband.mono",                 // monobank
        "com.fuib.android.spot.online",    // ПУМБ
        "ua.privatbank.ap24",              // Приват24
        "ua.com.abank",                    // A-Bank
        "com.sensebank.mobile",            // Sense Bank
        "ua.oschadbank.online",            // Ощад 24/7
        "ua.raiffeisen.myraif",            // Raiffeisen
        "com.izibank.app",                 // izibank
    )

    private val currencyMap = mapOf("₴" to "UAH", "грн" to "UAH", "uah" to "UAH", "$" to "USD", "usd" to "USD", "€" to "EUR", "eur" to "EUR")
    private const val CUR = "(₴|грн\\.?|uah|usd|eur|\\$|€)"
    // sign? number (spaces/nbsp as thousands, , or . decimals) currency — or currency before the number.
    private val amountAfter = Regex("([−\\-+]?)\\s*(\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})*(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)\\s*$CUR", RegexOption.IGNORE_CASE)
    private val amountBefore = Regex("([−\\-+]?)\\s*$CUR\\s*(\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})*(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)", RegexOption.IGNORE_CASE)
    private val balanceWords = Regex("(баланс|залишок|доступно|ліміт|balance|available|limit)\\s*:?\\s*$", RegexOption.IGNORE_CASE)
    private val expenseWords = Regex("списан|оплат|покупк|платіж|зняття|переказ на|withdraw|purchase|payment", RegexOption.IGNORE_CASE)
    private val incomeWords = Regex("зарахуван|поповнен|надходжен|переказ від|повернен|refund|received", RegexOption.IGNORE_CASE)
    private val senderLine = Regex("(^|\\n)\\s*від\\s*:", RegexOption.IGNORE_CASE)
    private val cashbackWords = Regex("кешбек|cashback", RegexOption.IGNORE_CASE)
    private val cardMask = Regex("\\*{1,4}\\d{2,4}|\\d{4}\\s?\\*{2,}")

    fun parse(packageName: String, title: String?, text: String?): BankSuggestion? {
        if (packageName !in BANK_PACKAGES) return null
        val full = listOfNotNull(title, text).joinToString("\n").trim()
        if (full.isEmpty()) return null

        var sign = ""; var number: String? = null; var cur: String? = null; var start = -1; var end = -1
        val candidates = (amountAfter.findAll(full).map { m -> Triple(m, m.groupValues[1], m.groupValues[2] to m.groupValues[3]) } +
            amountBefore.findAll(full).map { m -> Triple(m, m.groupValues[1], m.groupValues[3] to m.groupValues[2]) })
            .sortedBy { it.first.range.first }
        for ((m, s, numCur) in candidates) {
            val before = full.substring(0, m.range.first)
            if (balanceWords.containsMatchIn(before.takeLast(30))) continue
            sign = s; number = numCur.first; cur = numCur.second; start = m.range.first; end = m.range.last + 1
            break
        }
        val amount = number?.replace(Regex("[ \\u00A0\\u202F]"), "")?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val currency = currencyMap[cur!!.lowercase().trimEnd('.')] ?: "UAH"

        val isIncome = when {
            sign == "+" -> true
            sign == "-" || sign == "−" -> false
            incomeWords.containsMatchIn(full) -> true
            // monobank incoming transfer: "👉💳 1.00₴" / "Від: Ім'я Прізвище" (seen live 07.10).
            senderLine.containsMatchIn(full) -> true
            // "Кешбек 1.50₴" under a purchase is not income; only a push that leads with cashback is.
            cashbackWords.containsMatchIn(full.substring(0, end)) -> true
            expenseWords.containsMatchIn(full) -> false
            else -> return null
        }

        // Replaced by a line break, not removed: "АТБ 250 грн. Баланс…" must not glue the merchant to the balance.
        val rest = full.replaceRange(start, end, "\n")
        val merchant = rest.split(Regex("[\\n·|]|\\.\\s"))
            .map { cleanMerchant(it.trim().trim(',', '.', ':', '—', '-').trim()) }
            .firstOrNull { line ->
                line.length in 2..60 &&
                    !line.contains(Regex("баланс|залишок|доступно|ліміт|кешбек|cashback|balance|available", RegexOption.IGNORE_CASE)) &&
                    !amountAfter.containsMatchIn(line) && !amountBefore.containsMatchIn(line) &&
                    !cardMask.containsMatchIn(line) && !line.startsWith("Картка", ignoreCase = true) &&
                    line.any { it.isLetter() }
            }
        // Card/account mask ("*5536"), shown on transfer suggestions as "*5536 → *3924".
        val account = Regex("\\*\\d{2,4}").find(full)?.value
        return BankSuggestion(isIncome, amount, currency, merchant, account)
    }

    /** Drops leading verbs ("Оплата", "Покупка в") so the comment reads as a place. */
    private fun cleanMerchant(s: String): String =
        s.replace(Regex("^(оплата|покупка|списання|зарахування|поповнення|платіж|переказ)\\s*(в|у|на|від)?\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(від|в|у)\\s*:?\\s+", RegexOption.IGNORE_CASE), "").trim()
}
