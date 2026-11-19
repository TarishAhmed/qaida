package `in`.qaida.app.data.content

import `in`.qaida.app.domain.ConfusionSet
import `in`.qaida.app.domain.ContentPack
import `in`.qaida.app.domain.Letter
import `in`.qaida.app.domain.LetterForms
import `in`.qaida.app.domain.Makhraj
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class AssetValidatorTest {

    @Test
    fun `bundled content pack is valid`() {
        val source = FileContentSource.forVariantUnderTest()
        // Release has no content until the real pack is supplied; packaging a release is blocked separately.
        assumeTrue(source.exists(ContentPaths.LETTERS), "no content pack for this variant")

        val report = AssetValidator.validate(source.loadPack(), source)
        report.warnings.forEach { println("content warning: $it") }
        assertTrue(report.ok, "content errors:\n" + report.errors.joinToString("\n"))
    }

    @Test
    fun `valid fixture passes`() {
        assertTrue(AssetValidator.validate(fixture(), filesPresent).ok)
    }

    @Test
    fun `missing audio file fails`() {
        val report = AssetValidator.validate(fixture(), filesPresent.without("audio/a_isolated_v2.opus"))
        assertTrue(report.errors.any { "audio file missing: audio/a_isolated_v2.opus" in it }, report.errors.toString())
    }

    @Test
    fun `empty confusableWith fails`() {
        val pack = fixture().mapLetter("a") { it.copy(confusableWith = emptyList()) }
        assertTrue(AssetValidator.validate(pack, filesPresent).errors.any { "confusableWith is empty" in it })
    }

    @Test
    fun `undefined makhrajId fails`() {
        val pack = fixture().mapLetter("b") { it.copy(makhrajId = "nowhere") }
        assertTrue(AssetValidator.validate(pack, filesPresent).errors.any { "makhrajId 'nowhere' is not defined" in it })
    }

    @Test
    fun `confusable that is not a letter fails`() {
        val pack = fixture().mapLetter("a") { it.copy(confusableWith = listOf("b", "ghost")) }
        assertTrue(AssetValidator.validate(pack, filesPresent).errors.any { "'ghost' is not a letter" in it })
    }

    @Test
    fun `audio not named letter_context_voice fails`() {
        val pack = fixture().mapLetter("a") { it.copy(audio = mapOf("isolated" to listOf("a_v1.opus"))) }
        val report = AssetValidator.validate(pack, filesPresent + "audio/a_v1.opus")
        assertTrue(report.errors.any { "must be named a_isolated_<voiceId>.opus" in it }, report.errors.toString())
    }

    @Test
    fun `gap in unlock tiers fails`() {
        val pack = fixture().mapLetter("c") { it.copy(unlockTier = 3) }.mapLetter("d") { it.copy(unlockTier = 3) }
        assertTrue(AssetValidator.validate(pack, filesPresent).errors.any { "without gaps" in it })
    }

    @Test
    fun `single voice is a warning, not an error`() {
        val pack = fixture().mapLetter("a") { it.copy(audio = mapOf("isolated" to listOf("a_isolated_v1.opus"))) }
        val report = AssetValidator.validate(pack, filesPresent)
        assertTrue(report.ok)
        assertTrue(report.warnings.any { "1 isolated voice" in it })
    }

    @Test
    fun `voice id is read from the file name`() {
        assertEquals("v3", AssetValidator.voiceOf("dhal", "isolated", "dhal_isolated_v3.opus"))
        assertEquals(null, AssetValidator.voiceOf("dhal", "isolated", "zay_isolated_v3.opus"))
    }

    // Structural fixture only: letter ids and glyphs are deliberately meaningless.
    private fun fixture(): ContentPack {
        fun letter(id: String, others: List<String>, tier: Int) = Letter(
            id = id, glyph = "?", forms = LetterForms("?", "?", "?", "?"),
            nameEn = id, nameMl = id, makhrajId = "m1", makhrajNoteEn = "-", makhrajNoteMl = "-",
            confusableWith = others,
            audio = mapOf("isolated" to (1..4).map { "${id}_isolated_v$it.opus" }),
            unlockTier = tier,
        )
        return ContentPack(
            letters = listOf(letter("a", listOf("b", "c", "d"), 1), letter("b", listOf("a", "c", "d"), 1),
                letter("c", listOf("a", "b", "d"), 2), letter("d", listOf("a", "b", "c"), 2)),
            confusionSets = listOf(ConfusionSet(listOf("a", "b", "c", "d"))),
            makhraj = listOf(Makhraj("m1", "makhraj/m1.svg", "-", "-")),
        )
    }

    private val filesPresent: Set<String> =
        listOf("a", "b", "c", "d").flatMap { id -> (1..4).map { "audio/${id}_isolated_v$it.opus" } }.toSet() + "makhraj/m1.svg"

    private fun Set<String>.without(path: String) = this - path

    private fun ContentPack.mapLetter(id: String, f: (Letter) -> Letter) =
        copy(letters = letters.map { if (it.id == id) f(it) else it })

    private fun AssetValidator.validate(pack: ContentPack, files: Set<String>) =
        validate(pack, object : ContentSource {
            override fun readText(path: String) = error("not used")
            override fun exists(path: String) = path in files
        })
}
