package ua.rytm.app.ui.screens.finance

/**
 * One line ("кава 65 моно", "+25000 зарплата пумб", "таксі 120,50") → a draft
 * for the transaction form. Never saves anything: the result only pre-fills
 * the form, the same way a bank suggestion does.
 */
object QuickEntryParser {
    data class Result(val amount: Double, val isIncome: Boolean, val walletId: String?, val comment: String)

    private val amountRegex = Regex("""(?<![\p{L}\d])(\d{1,3}(?:[  ]\d{3})+|\d+)(?:[.,](\d{1,2}))?(?![\d\p{L}])""")
    private val currencyWords = setOf("грн", "₴", "uah", "гривень", "гривні", "гривня")
    private val incomeWords = setOf("зарплата", "зп", "дохід", "аванс", "премія", "отримав", "отримала", "salary", "income")

    fun parse(text: String, wallets: List<Wallet>): Result? {
        val line = text.trim()
        if (line.isEmpty()) return null
        val match = amountRegex.find(line) ?: return null
        val whole = match.groupValues[1].replace(" ", "").replace(" ", "")
        val fraction = match.groupValues[2]
        val amount = (if (fraction.isEmpty()) whole else "$whole.$fraction").toDoubleOrNull()?.takeIf { it > 0 } ?: return null

        val rest = (line.removeRange(match.range)).trim()
        val explicitIncome = line.startsWith("+")
        val tokens = rest.removePrefix("+").split(Regex("""\s+""")).filter { it.isNotBlank() }
        val words = tokens.map { it.lowercase().trim(',', '.', ';', ':') }

        val wallet = wallets.firstOrNull { w -> words.any { word -> matchesWallet(word, w.name) } }
        val isIncome = explicitIncome || words.any { it in incomeWords }
        val comment = tokens.filterIndexed { i, _ ->
            val word = words[i]
            word !in currencyWords && (wallet == null || !matchesWallet(word, wallet.name))
        }.joinToString(" ").trim(',', ' ')
        return Result(amount, isIncome, wallet?.id, comment)
    }

    /** "моно" matches "monobank"/"Моно картка", "пумб" matches "ПУМБ 5536": a word of the name starting with it. */
    private fun matchesWallet(word: String, walletName: String): Boolean {
        if (word.length < 3) return false
        val nameWords = walletName.lowercase().split(Regex("""[\s\-_/]+""")).filter { it.isNotBlank() }
        return nameWords.any { it.startsWith(word) || transliterated(it).startsWith(word) }
    }

    private val latinToCyr = mapOf(
        "sh" to "ш", "ch" to "ч", "zh" to "ж", "ya" to "я", "yu" to "ю",
        "a" to "а", "b" to "б", "v" to "в", "g" to "г", "d" to "д", "e" to "е", "z" to "з", "i" to "і", "y" to "и",
        "k" to "к", "l" to "л", "m" to "м", "n" to "н", "o" to "о", "p" to "п", "r" to "р", "s" to "с", "t" to "т",
        "u" to "у", "f" to "ф", "h" to "х", "c" to "к",
    )

    /** "monobank" → "монобанк", so a spoken/typed "моно" finds a Latin-named wallet. */
    private fun transliterated(word: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < word.length) {
            val two = if (i + 1 < word.length) word.substring(i, i + 2) else null
            val mapped2 = two?.let { latinToCyr[it] }
            if (mapped2 != null) { sb.append(mapped2); i += 2; continue }
            sb.append(latinToCyr[word[i].toString()] ?: word[i].toString()); i++
        }
        return sb.toString()
    }
}
