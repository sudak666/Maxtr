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

    private fun arrays(path: String): Map<String, List<String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string-array")
        return buildMap {
            repeat(nodes.length) { index ->
                val node = nodes.item(index)
                val items = node.childNodes.let { children ->
                    (0 until children.length).mapNotNull { childIndex -> children.item(childIndex).takeIf { it.nodeName == "item" }?.textContent }
                }
                put(node.attributes.getNamedItem("name").nodeValue, items)
            }
        }
    }

    private fun plurals(path: String): Map<String, Map<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("plurals")
        return buildMap {
            repeat(nodes.length) { index ->
                val node = nodes.item(index)
                val items = buildMap {
                    val children = node.childNodes
                    repeat(children.length) { childIndex ->
                        val item = children.item(childIndex)
                        if (item.nodeName == "item") put(item.attributes.getNamedItem("quantity").nodeValue, item.textContent)
                    }
                }
                put(node.attributes.getNamedItem("name").nodeValue, items)
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
        val ukArrays = arrays("src/main/res/values/strings.xml")
        val enArrays = arrays("src/main/res/values-en/strings.xml")
        assertEquals(ukArrays.keys, enArrays.keys)
        ukArrays.forEach { (key, items) -> assertEquals("Localized array size differs: $key", items.size, enArrays.getValue(key).size) }
        (ukArrays.values.flatten() + enArrays.values.flatten()).forEach { value ->
            assertFalse("Replacement character found in array resource: $value", '\uFFFD' in value)
            assertFalse("Empty localized array item", value.isBlank())
        }
        val ukPlurals = plurals("src/main/res/values/strings.xml")
        val enPlurals = plurals("src/main/res/values-en/strings.xml")
        assertEquals(ukPlurals.keys, enPlurals.keys)
        (ukPlurals + enPlurals).forEach { (key, quantities) ->
            assertFalse("Plural resource has no other quantity: $key", "other" !in quantities)
            quantities.values.forEach { value ->
                assertFalse("Replacement character found in plural resource: $value", '\uFFFD' in value)
                assertFalse("Empty localized plural item", value.isBlank())
            }
        }
    }
}
