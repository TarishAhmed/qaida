package `in`.qaida.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** English and Malayalam stay complete and in step: same keys, same format arguments. */
class StringsTest {
    private fun load(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        val out = LinkedHashMap<String, String>()
        for (tag in listOf("string", "plurals")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                if (e.getAttribute("translatable") == "false") continue
                out[e.getAttribute("name")] = e.textContent
            }
        }
        return out
    }

    private val args = Regex("%\\d+\\$[sd]")

    @Test
    fun `every English string has a Malayalam translation and vice versa`() {
        val en = load("values")
        val ml = load("values-ml")
        assertEquals(en.keys - ml.keys, emptySet<String>(), "missing in Malayalam")
        assertEquals(ml.keys - en.keys, emptySet<String>(), "only in Malayalam")
    }

    @Test
    fun `translations keep the same format arguments`() {
        val en = load("values")
        val ml = load("values-ml")
        for ((k, v) in en) {
            assertEquals(args.findAll(v).map { it.value }.toSet(), args.findAll(ml.getValue(k)).map { it.value }.toSet(), k)
        }
    }

    @Test
    fun `nothing user-facing is hard-coded in the UI`() {
        val offenders = File("src/main/java").walkTopDown().filter { it.extension == "kt" && "/ui/" in it.path }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line ->
                Regex("""Text\(\s*"[A-Za-z][^"]*"""").find(line)?.let { "${f.name}:${i + 1}: ${line.trim()}" }
            } }.toList()
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }
}
