package ua.rytm.app.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class ReceiptParseResult(val amount: Double?, val date: String?, val rawText: String)

private val receiptDateRegex = Regex("\\b(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})\\b")
private val decimalRegex = Regex("\\d[\\d ]*[.,]\\d{2}\\b")
private val integerRegex = Regex("\\b\\d+\\b")
private val totalKeywordRegex = Regex("(сума|разом|усього|всього|до\\s*сплати|к\\s*сплате|итого|total|amount\\s*due|grand\\s*total)", RegexOption.IGNORE_CASE)

fun parseReceiptText(text: String): ReceiptParseResult {
    val dateMatch = receiptDateRegex.find(text)
    val date = dateMatch?.groupValues?.let { values ->
        val day = values[1].toIntOrNull() ?: return@let null
        val month = values[2].toIntOrNull() ?: return@let null
        var year = values[3].toIntOrNull() ?: return@let null
        if (values[3].length == 2) year += 2000
        runCatching { java.time.LocalDate.of(year, month, day).toString() }.getOrNull()
    }
    val amountText = dateMatch?.let { text.replaceRange(it.range, " ") } ?: text
    fun amounts(value: String, regex: Regex) = regex.findAll(value).mapNotNull {
        it.value.replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { number -> number > 0 }
    }.toList()
    val keywordAmount = amountText.lineSequence().firstNotNullOfOrNull { line ->
        if (totalKeywordRegex.containsMatchIn(line)) (amounts(line, decimalRegex).ifEmpty { amounts(line, integerRegex) }).lastOrNull() else null
    }
    val amount = keywordAmount ?: amounts(amountText, decimalRegex).maxOrNull() ?: amounts(amountText, integerRegex).maxOrNull()
    return ReceiptParseResult(amount, date, text)
}

class ReceiptOcrRepository {
    suspend fun scan(context: Context, uri: Uri): ReceiptParseResult {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try { parseReceiptText(recognizer.process(image).await().text) } finally { recognizer.close() }
    }
}
