package ua.rytm.app.ui.screens.finance

/**
 * A bank statement line, made readable for the history row: «KOLO KIYEV UA»
 * → «KOLO», «RPAY *VODAFONE KYIV» → «VODAFONE», «Daineko Yuliia DNIPRO UA»
 * → «Daineko Yuliia», «NovaPay 380444962757» → «NovaPay». The payment-gateway
 * prefix, the trailing city and country and long reference numbers say
 * nothing a person looks for (outside review 2026-11-28; monobank shows the
 * merchant the same way). Display only: the stored comment and the
 * operation's details keep the full line.
 */
object StatementText {
    private val gateway = Regex("""^(RPAY|LIQPAY|LIQ\*PAY|WFP|PORTMONE|IPAY|PAYPAL|APPLE\.COM/BILL)\s*\*?\s*""", RegexOption.IGNORE_CASE)
    private val country = Regex("""\s+(UA|UKR|UKRAINE|GB|GBR|US|USA|IE|IRL|NL|NLD|CY|CYP|DE|PL|LT|EE|LV|CH|LU)$""", RegexOption.IGNORE_CASE)
    private val city = Regex(
        """\s+(KYIV|KIEV|KIYEV|KYYIV|DNIPRO|DNEPR|LVIV|ODESA|ODESSA|KHARKIV|ZAPORIZHZHIA|VINNYTSIA|POLTAVA|CHERNIHIV|IRPIN|BROVARY|BUCHA|BILA TSERKVA|LONDON|DUBLIN|AMSTERDAM|LIMASSOL|NICOSIA|WARSAW|VILNIUS|TALLINN|RIGA|BERLIN|LUXEMBOURG|MOUNTAIN VIEW|SAN FRANCISCO)$""",
        RegexOption.IGNORE_CASE,
    )
    /** A phone or reference number: six digits or more at the end. */
    private val reference = Regex("""\s+\+?\d{6,}$""")

    fun clean(raw: String): String {
        var s = raw.trim().replace(gateway, "")
        // City and country come in either order and sometimes after a reference.
        repeat(2) {
            s = s.replace(reference, "").replace(country, "").replace(city, "")
        }
        return s.trim().ifEmpty { raw.trim() }
    }

    /**
     * Whether [text] already says [part], across alphabets: the subcategory
     * «Коло» and the statement's «KOLO» are the same word, so the row shows it
     * once («Продукти · Коло» over «ПУМБ · KOLO» said it twice).
     */
    fun mentions(text: String, part: String): Boolean {
        val needle = key(part)
        return needle.isNotEmpty() && key(text).contains(needle)
    }

    private fun key(s: String): String = buildString {
        s.lowercase().forEach { c -> append(TRANSLIT[c] ?: if (c.isLetterOrDigit()) c.toString() else "") }
    }

    private val TRANSLIT: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "h", 'ґ' to "g", 'д' to "d", 'е' to "e", 'є' to "ye",
        'ж' to "zh", 'з' to "z", 'и' to "y", 'і' to "i", 'ї' to "yi", 'й' to "y", 'к' to "k", 'л' to "l",
        'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ь' to "", 'ю' to "yu",
        'я' to "ya", 'ы' to "y", 'э' to "e", 'ё' to "yo", 'ъ' to "", '\'' to "", '’' to "", 'ʼ' to "",
    )
}
