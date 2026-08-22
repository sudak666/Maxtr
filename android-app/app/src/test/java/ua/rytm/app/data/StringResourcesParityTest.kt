package ua.rytm.app.data

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StringResourcesParityTest {
    private fun strings(path: String): Map<String, String> {
        val file = File(path)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            repeat(nodes.length) { index ->
                val node = nodes.item(index)
                put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
            }
        }
    }

    @Test
    fun ukrainianAndEnglishResourcesHaveExactKeyParityAndValidText() {
        val uk = strings("src/main/res/values/strings.xml")
        val en = strings("src/main/res/values-en/strings.xml")

        assertEquals(uk.keys, en.keys)
        (uk.values + en.values).forEach { value ->
            assertFalse("Replacement character found in resource: $value", '\uFFFD' in value)
            assertFalse("Empty localized resource", value.isBlank())
        }
    }
}
