package ua.rytm.app.ui.screens.finance

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import ua.rytm.app.R
import ua.rytm.app.ui.builtInTextResource
import java.io.File
import java.time.YearMonth

/** What the month's PDF report shows; pure, so it is unit-tested apart from the drawing. */
data class MonthReport(
    val month: YearMonth,
    val income: Double,
    val expense: Double,
    /** Every expense category of the month (not just the recap's top 3), largest first, in UAH. */
    val categories: List<Pair<String, Double>>,
    /** The month's entries, oldest first. */
    val rows: List<Transaction>,
) {
    companion object {
        fun build(transactions: List<Transaction>, month: YearMonth, toUah: (Transaction) -> Double): MonthReport {
            val prefix = month.toString()
            val inMonth = transactions.filter { it.date.startsWith(prefix) }
            val expenses = inMonth.filter { it.type == TxType.EXPENSE }
            return MonthReport(
                month = month,
                income = inMonth.filter { it.type == TxType.INCOME }.sumOf(toUah),
                expense = expenses.sumOf(toUah),
                categories = expenses.groupBy { it.category }.map { (c, l) -> c to l.sumOf(toUah) }.sortedByDescending { it.second },
                rows = inMonth.sortedWith(compareBy<Transaction> { it.date }.thenBy { it.createdAt }),
            )
        }
    }
}

/**
 * Draws [report] on A4 pages (PdfDocument, no library) and opens the share
 * sheet. Built-in category/wallet names go through the same translations as
 * the screen; amounts are shown even with "hide amounts" on — exporting is
 * an explicit act, same as CSV export.
 */
fun shareMonthReportPdf(context: Context, report: MonthReport, walletName: (String?) -> String, title: String) {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "rytm-report-${report.month}.pdf")
    writeMonthReportPdf(context, report, walletName, file)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, title))
}

/** Renders [report] into [file]; split from sharing so a test can render it. */
fun writeMonthReportPdf(context: Context, report: MonthReport, walletName: (String?) -> String, file: File) {
    fun label(v: String) = builtInTextResource(v)?.let(context::getString) ?: v
    val pageW = 595; val pageH = 842; val margin = 40f
    val doc = PdfDocument()
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF1C1C1F.toInt() }
    val muted = Paint(text).apply { color = 0xFF626269.toInt() }
    val bold = Paint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val h1 = Paint(bold).apply { textSize = 18f }
    val h2 = Paint(bold).apply { textSize = 12f }
    val accent = 0xFF7C3AED.toInt()
    val line = Paint().apply { color = 0x1F000000; strokeWidth = 0.7f }
    val monthTitle = report.month.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale.getDefault())
        .replaceFirstChar { it.titlecase() } + " " + report.month.year

    var pageNo = 0
    lateinit var page: PdfDocument.Page
    var y = 0f
    fun newPage() {
        if (pageNo > 0) doc.finishPage(page)
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        y = margin
        page.canvas.drawText("Rytm · $monthTitle", margin, pageH - 20f, muted)
        page.canvas.drawText(pageNo.toString(), pageW - margin, pageH - 20f, Paint(muted).apply { textAlign = Paint.Align.RIGHT })
    }
    fun ensure(h: Float) { if (y + h > pageH - 50f) newPage() }
    fun right(s: String, p: Paint, yy: Float) = page.canvas.drawText(s, pageW - margin, yy, Paint(p).apply { textAlign = Paint.Align.RIGHT })
    fun ellipsize(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
    fun uah(v: Double) = "${formatMoney(v)} ₴"

    newPage()
    page.canvas.drawRect(margin, y, margin + 4f, y + 26f, Paint().apply { color = accent })
    page.canvas.drawText(context.getString(R.string.month_report_title, monthTitle), margin + 12f, y + 19f, h1)
    y += 46f
    listOf(
        context.getString(R.string.tx_income) to report.income,
        context.getString(R.string.tx_expense) to report.expense,
        context.getString(R.string.month_report_net) to report.income - report.expense,
    ).forEach { (k, v) -> page.canvas.drawText(k, margin, y, text); right(uah(v), bold, y); y += 16f }
    y += 14f

    if (report.categories.isNotEmpty()) {
        page.canvas.drawText(context.getString(R.string.month_report_categories), margin, y, h2); y += 18f
        val max = report.categories.first().second.coerceAtLeast(1.0)
        report.categories.forEach { (cat, sum) ->
            ensure(18f)
            page.canvas.drawText(ellipsize(label(cat), text, 200f), margin, y, text)
            val barX = margin + 210f; val barW = (pageW - margin - 90f - barX) * (sum / max).toFloat()
            page.canvas.drawRect(barX, y - 7f, barX + barW, y, Paint().apply { color = accent; alpha = 140 })
            right(uah(sum), text, y)
            y += 16f
        }
        y += 14f
    }

    ensure(40f)
    page.canvas.drawText(context.getString(R.string.month_report_entries, report.rows.size), margin, y, h2); y += 16f
    val cols = floatArrayOf(margin, margin + 62f, margin + 190f)
    report.rows.forEach { t ->
        ensure(16f)
        val sign = when (t.type) { TxType.INCOME -> "+"; TxType.EXPENSE -> "−"; TxType.TRANSFER -> "" }
        val cat = if (t.type == TxType.TRANSFER) "${walletName(t.walletId)} → ${walletName(t.targetWalletId)}" else label(t.category)
        page.canvas.drawText(t.date.substring(8, 10) + "." + t.date.substring(5, 7), cols[0], y, muted)
        page.canvas.drawText(ellipsize(cat, text, 122f), cols[1], y, text)
        page.canvas.drawText(ellipsize(t.comment.orEmpty(), muted, pageW - margin - 100f - cols[2]), cols[2], y, muted)
        right("$sign${formatMoney(t.amount)} ${currencySymbol(t.currency)}", text, y)
        page.canvas.drawLine(margin, y + 5f, pageW - margin, y + 5f, line)
        y += 16f
    }
    doc.finishPage(page)

    file.outputStream().use { doc.writeTo(it) }
    doc.close()
}
