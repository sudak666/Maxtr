package ua.rytm.app.ui.screens.finance

/**
 * A bank statement line, made readable for the history row: «KOLO KIYEV UA»
 * → «KOLO», «RPAY *VODAFONE KYIV» → «VODAFONE», «Daineko Yuliia DNIPRO UA»
 * → «Daineko Yuliia». The payment-gateway prefix and the trailing city and
 * country say nothing a person looks for (outside review 2026-11-28; monobank
 * shows the merchant the same way). Display only: the stored comment and the
 * operation's details keep the full line.
 */
object StatementText {
    private val gateway = Regex("""^(RPAY|LIQPAY|LIQ\*PAY|WFP|PORTMONE|IPAY|PAYPAL|GOOGLE\s*\*|APPLE\.COM/BILL)\s*\*?\s*""", RegexOption.IGNORE_CASE)
    private val country = Regex("""\s+(UA|UKR|UKRAINE)$""", RegexOption.IGNORE_CASE)
    private val city = Regex(
        """\s+(KYIV|KIEV|KIYEV|KYYIV|DNIPRO|DNEPR|LVIV|ODESA|ODESSA|KHARKIV|ZAPORIZHZHIA|VINNYTSIA|POLTAVA|CHERNIHIV|IRPIN|BROVARY|BUCHA|BILA TSERKVA)$""",
        RegexOption.IGNORE_CASE,
    )

    fun clean(raw: String): String {
        var s = raw.trim().replace(gateway, "")
        s = s.replace(country, "")
        s = s.replace(city, "")
        return s.trim().ifEmpty { raw.trim() }
    }
}
