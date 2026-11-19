package `in`.qaida.app.data.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ContentParserTest {

    private val letter = """
        {"id":"a","glyph":"?","forms":{"isolated":"?","initial":"?","medial":"?","final":"?"},
         "nameEn":"x","nameMl":"x","makhrajId":"m","makhrajNoteEn":"-","makhrajNoteMl":"-",
         "confusableWith":["b"],"audio":{"isolated":["a_isolated_v1.opus"]},"unlockTier":1}
    """.trimIndent()
    private val makhraj = """[{"id":"m","diagram":"makhraj/m.svg","labelEn":"-","labelMl":"-"}]"""

    @Test
    fun `parses all three files`() {
        val pack = ContentParser.parse("[$letter]", """[["a","b"]]""", makhraj)
        val a = pack.letters.single()
        assertEquals("a", a.id)
        assertEquals(listOf("b"), a.confusableWith)
        assertEquals(mapOf("isolated" to listOf("a_isolated_v1.opus")), a.audio)
        assertEquals(1, a.unlockTier)
        assertEquals(listOf("a", "b"), pack.confusionSets.single().letterIds)
        assertEquals("makhraj/m.svg", pack.makhraj.single().diagram)
    }

    @Test
    fun `missing key names the file, index and key`() {
        val broken = letter.replace(""""nameMl":"x",""", "")
        val e = assertThrows<ContentFormatException> { ContentParser.parse("[$broken]", "[]", makhraj) }
        assertTrue("letters.json[0].nameMl" in e.message!!, e.message)
    }

    @Test
    fun `wrong type is rejected`() {
        val broken = letter.replace(""""unlockTier":1""", """"unlockTier":"1"""")
        assertThrows<ContentFormatException> { ContentParser.parse("[$broken]", "[]", makhraj) }
    }

    @Test
    fun `confusion set must be an array`() {
        val e = assertThrows<ContentFormatException> { ContentParser.parse("[$letter]", """["a"]""", makhraj) }
        assertTrue("confusion_sets.json[0]" in e.message!!, e.message)
    }

    @Test
    fun `malformed json is reported per file`() {
        val e = assertThrows<ContentFormatException> { ContentParser.parse("[", "[]", makhraj) }
        assertTrue(e.message!!.startsWith("letters.json"), e.message)
    }
}
