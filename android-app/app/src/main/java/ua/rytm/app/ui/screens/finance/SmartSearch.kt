package ua.rytm.app.ui.screens.finance

import java.time.LocalDate
import java.time.YearMonth

/**
 * History search in plain words (Copilot-style): "кафе жовтень",
 * "більше 500", "від 100 до 300 продукти", "вчора", "taxi under 200".
 * Recognised pieces become filters; whatever is left is the usual text search.
 * Amounts compare in the entry's own currency.
 */
object SmartSearch {
    data class Query(
        val text: String,
        val minAmount: Double? = null,
        val maxAmount: Double? = null,
        val month: YearMonth? = null,
        val date: LocalDate? = null,
    ) {
        val hasFilters: Boolean get() = minAmount != null || maxAmount != null || month != null || date != null

        fun matches(t: Transaction): Boolean =
            (minAmount == null || t.amount >= minAmount) &&
                (maxAmount == null || t.amount <= maxAmount) &&
                (month == null || t.date.startsWith(month.toString())) &&
                (date == null || t.date == date.toString())
    }

    // Exact word forms, not stems: "черв" would also catch "червоний", "mar" "market".
    private val monthWords: Map<String, Int> = buildMap {
        listOf(
            "січень січня січні january", "лютий лютого лютому february", "березень березня березні march",
            "квітень квітня квітні april", "травень травня травні may", "червень червня червні june",
            "липень липня липні july", "серпень серпня серпні august", "вересень вересня вересні september",
            "жовтень жовтня жовтні october", "листопад листопада листопаді november", "грудень грудня грудні december",
        ).forEachIndexed { i, forms -> forms.split(" ").forEach { put(it, i + 1) } }
    }
    private val num = """(\d+(?:[.,]\d+)?)"""
    private val between = Regex("""(?:від|from)\s*$num\s*(?:до|to|-)\s*$num""")
    private val min = Regex("""(?:більше|понад|більш ніж|від|over|more than|above|from|>\s*=?)\s*$num""")
    private val max = Regex("""(?:менше|до|under|less than|below|<\s*=?)\s*$num""")

    fun parse(raw: String, today: LocalDate): Query {
        var s = " " + raw.trim().lowercase() + " "
        var lo: Double? = null
        var hi: Double? = null
        fun d(x: String) = x.replace(',', '.').toDoubleOrNull()
        between.find(s)?.let { m -> lo = d(m.groupValues[1]); hi = d(m.groupValues[2]); s = s.replace(m.value, " ") }
        if (lo == null) min.find(s)?.let { m -> lo = d(m.groupValues[1]); s = s.replace(m.value, " ") }
        if (hi == null) max.find(s)?.let { m -> hi = d(m.groupValues[1]); s = s.replace(m.value, " ") }

        var date: LocalDate? = null
        when {
            Regex("""\s(сьогодні|today)\s""").containsMatchIn(s) -> { date = today; s = s.replace(Regex("""\s(сьогодні|today)\s"""), " ") }
            Regex("""\s(вчора|yesterday)\s""").containsMatchIn(s) -> { date = today.minusDays(1); s = s.replace(Regex("""\s(вчора|yesterday)\s"""), " ") }
        }

        var month: YearMonth? = null
        val kept = mutableListOf<String>()
        for (w in s.split(Regex("\\s+")).filter { it.isNotBlank() }) {
            val m = monthWords[w]
            if (month == null && m != null) {
                val ym = YearMonth.of(today.year, m)
                // "грудень" typed in October means last December.
                month = if (ym.isAfter(YearMonth.from(today))) ym.minusYears(1) else ym
            } else if (w !in setOf("у", "в", "за", "in", "for")) {
                kept += w
            }
        }
        return Query(text = kept.joinToString(" "), minAmount = lo, maxAmount = hi, month = month, date = date)
    }
}
