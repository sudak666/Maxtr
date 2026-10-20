package ua.rytm.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ua.rytm.app.ui.screens.finance.MonthReport
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.writeMonthReportPdf
import java.io.File
import java.time.YearMonth

/**
 * Renders the month PDF on a real device (Robolectric has no native
 * PdfDocument) and saves page PNGs to the app's external files dir, so the
 * layout can be looked at: adb pull /data/local/tmp/report-N.png
 */
@RunWith(AndroidJUnit4::class)
class MonthReportPdfDeviceTest {
    @Test fun rendersMultiPageReport() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val cats = listOf("Продукти", "Кафе", "Транспорт", "Оренда квартири", "Дуже довга назва категорії для перевірки обрізання")
        val rows = (1..90).map { i ->
            Transaction(
                id = "$i", type = when { i % 15 == 0 -> TxType.TRANSFER; i % 7 == 0 -> TxType.INCOME; else -> TxType.EXPENSE },
                amount = i * 37.5, date = "2026-09-%02d".format(i % 28 + 1), walletId = "w", targetWalletId = "w2",
                category = cats[i % cats.size], comment = if (i % 3 == 0) "Дуже довгий коментар до операції номер $i для перевірки" else "АТБ",
            )
        }
        val report = MonthReport.build(rows, YearMonth.of(2026, 9)) { it.amount }
        val dir = ctx.getExternalFilesDir(null)!!
        val pdf = File(dir, "report.pdf")
        writeMonthReportPdf(ctx, report, { if (it == "w") "ПУМБ" else "Монети" }, pdf)
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
            assertTrue(r.pageCount >= 2)
            for (i in 0 until r.pageCount) r.openPage(i).use { p ->
                val bmp = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(dir, "report-$i.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                // The test APK is uninstalled afterwards (taking its files with it): copy out for adb pull.
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("cp ${File(dir, "report-$i.png").absolutePath} /data/local/tmp/report-$i.png").close()
            }
        }
    }
}
