package `in`.qaida.app.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.ln
import kotlin.random.Random

class CardFactoryTest {
    private val hour = 3_600_000L
    private val now = 1_700_000_000_000L

    private fun clips(id: String, ctx: String, n: Int) = (1..n).map { AudioClip("${id}_${ctx}_v$it.opus", "v$it") }

    // Structural fixture: ids and glyphs are meaningless; forms are tagged so the test can tell them apart.
    private fun letter(id: String, confusable: List<String>, voices: Int = 4, harakat: Boolean = true) = CatalogLetter(
        id = id, glyph = "g$id", unlockTier = 1, ordinal = 0, confusableWith = confusable, confusionSets = setOf(0),
        isolatedClips = clips(id, "isolated", voices),
        forms = LetterForms("g$id", "i$id", "m$id", "f$id"),
        harakatClips = if (harakat) listOf("fatha", "kasra", "damma").associateWith { clips(id, it, 2) } else emptyMap(),
    )

    private val catalog = listOf(
        letter("a", listOf("b", "c", "d", "e")), letter("b", listOf("a")), letter("c", listOf("a")),
        letter("d", listOf("a")), letter("e", listOf("a")),
    ).associateBy { it.id }
    private val a = catalog.getValue("a")

    private fun build(type: CardType, level: Int = 2, target: CatalogLetter = a, lastVoice: String? = null, seed: Int = 1) =
        CardFactory.build(type, target, catalog, isIntro = false, level = level, lastVoice = lastVoice, rng = Random(seed))

    @Test
    fun `HEAR_PICK options are the target plus up to three confusables, never other letters`() {
        repeat(200) { seed ->
            val card = build(CardType.HEAR_PICK, seed = seed)
            val ids = card.options.map { it.key }
            assertEquals(4, ids.toSet().size)
            assertTrue("a" in ids && ids.all { it in setOf("a", "b", "c", "d", "e") }, "$ids")
            assertEquals("a", card.correctKey)
        }
    }

    @Test
    fun `a letter with one confusable gets two options, not random padding`() {
        val card = build(CardType.HEAR_PICK, target = catalog.getValue("b"))
        assertEquals(setOf("a", "b"), card.options.map { it.key }.toSet())
    }

    @Test
    fun `voice differs from the last one used when more than one exists`() {
        repeat(200) { seed ->
            val clip = (build(CardType.HEAR_PICK, lastVoice = "v2", seed = seed).prompt as Prompt.Listen).clips.single()
            assertNotEquals("v2", clip.voiceId)
        }
    }

    @Test
    fun `a single voice is still used`() {
        val solo = letter("s", listOf("a"), voices = 1)
        val card = CardFactory.build(CardType.HEAR_PICK, solo, catalog + ("s" to solo), false, 2, "v1", Random(1))
        assertEquals("v1", (card.prompt as Prompt.Listen).clips.single().voiceId)
    }

    @Test
    fun `SEE_PICK shows the glyph and offers one clip per letter`() {
        val card = build(CardType.SEE_PICK)
        assertEquals(Prompt.Read("ga"), card.prompt)
        assertTrue(card.options.all { it.kind == AnswerOption.Kind.CLIP && it.clip!!.file.startsWith(it.key + "_isolated_") })
        assertEquals("a", card.correctKey)
    }

    @Test
    fun `AB_SAME_DIFF is same exactly when both clips are the target letter`() {
        var sawSame = false; var sawDifferent = false
        repeat(200) { seed ->
            val card = build(CardType.AB_SAME_DIFF, seed = seed)
            val pair = (card.prompt as Prompt.Listen).clips
            assertEquals(2, pair.size)
            val same = pair.all { it.file.startsWith("a_") }
            assertEquals(if (same) CardFactory.SAME else CardFactory.DIFFERENT, card.correctKey)
            if (same) { sawSame = true; assertNotEquals(pair[0].voiceId, pair[1].voiceId) } else sawDifferent = true
        }
        assertTrue(sawSame && sawDifferent)
    }

    @Test
    fun `FORM_PICK shows a positional form and asks for the letter`() {
        val shown = (0 until 100).map { (build(CardType.FORM_PICK, seed = it).prompt as Prompt.Read).text }.toSet()
        assertEquals(setOf("ia", "ma", "fa"), shown)
        assertEquals("a", build(CardType.FORM_PICK).correctKey)
    }

    @Test
    fun `HARAKAH_PICK offers the three vowelled forms plus a confusable with the same vowel`() {
        repeat(100) { seed ->
            val card = build(CardType.HARAKAH_PICK, seed = seed)
            val haraka = card.correctKey.substringAfter('|')
            val keys = card.options.map { it.key }.toSet()
            assertTrue(setOf("a|fatha", "a|kasra", "a|damma").all { it in keys }, "$keys")
            assertEquals(4, keys.size)
            assertTrue(keys.any { !it.startsWith("a|") && it.endsWith("|$haraka") })
            assertTrue((card.prompt as Prompt.Listen).clips.single().file.startsWith("a_${haraka}_"))
            val correct = card.options.first { it.key == card.correctKey }
            assertEquals("ga" + CardFactory.HARAKAH_MARKS.getValue(haraka), correct.text)
        }
    }

    @Test
    fun `hints appear only at level 1`() {
        assertEquals("ga", build(CardType.HEAR_PICK, level = 1).hintGlyph)
        assertNull(build(CardType.HEAR_PICK, level = 2).hintGlyph)
        assertTrue(build(CardType.FORM_PICK, level = 1).hintClip != null)
        assertNull(build(CardType.FORM_PICK, level = 3).hintClip)
    }

    // --- Levels ---

    private fun reviewed(s: Double, p: Double) = ReviewState(s, now - (-s * ln(p) / ln(2.0) * hour).toLong())

    @Test
    fun `level rises with stability and falls back after a lapse`() {
        assertEquals(1, Levels.of(null, now))
        assertEquals(1, Levels.of(reviewed(s = 10.0, p = 0.9), now)) // p1d = 0.19
        assertEquals(2, Levels.of(reviewed(s = 40.0, p = 0.9), now)) // p1d = 0.66
        assertEquals(3, Levels.of(reviewed(s = 150.0, p = 0.9), now)) // p1d = 0.90
        assertEquals(4, Levels.of(reviewed(s = 400.0, p = 0.9), now)) // p1d = 0.96
        assertEquals(1, Levels.of(reviewed(s = 400.0, p = 0.4), now)) // lapsed
    }

    @Test
    fun `level behaviour matches the scaffold table`() {
        assertTrue(Levels.autoplay(1)); assertTrue(!Levels.autoplay(2))
        assertEquals(1f, Levels.volume(2)); assertEquals(0.7f, Levels.volume(3))
        assertNull(Levels.maxPlays(3)); assertEquals(1, Levels.maxPlays(4))
    }

    @Test
    fun `types beyond HEAR_PICK need an established HEAR_PICK`() {
        val young = mapOf(ItemKey("a", CardType.HEAR_PICK) to reviewed(5.0, 0.9))
        assertEquals(listOf(CardType.HEAR_PICK), CardTypes.available(a, young))
        val ok = mapOf(ItemKey("a", CardType.HEAR_PICK) to reviewed(9.0, 0.9))
        assertEquals(CardType.entries - CardType.PRODUCE, CardTypes.available(a, ok))
        val noHarakat = letter("n", listOf("a"), harakat = false)
        val ok2 = mapOf(ItemKey("n", CardType.HEAR_PICK) to reviewed(9.0, 0.9))
        assertTrue(CardType.HARAKAH_PICK !in CardTypes.available(noHarakat, ok2))
    }
}
