package `in`.qaida.app.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class CardFactoryTest {
    private fun letter(id: String, confusable: List<String>, voices: Int = 4) = CatalogLetter(
        id = id, glyph = "?", unlockTier = 1, ordinal = 0, confusableWith = confusable, confusionSets = setOf(0),
        isolatedClips = (1..voices).map { AudioClip("${id}_isolated_v$it.opus", "v$it") },
    )

    private val catalog = listOf(
        letter("a", listOf("b", "c", "d", "e")), letter("b", listOf("a")), letter("c", listOf("a")),
        letter("d", listOf("a")), letter("e", listOf("a")), letter("z", emptyList()),
    ).associateBy { it.id }

    @Test
    fun `options are the target plus up to three of its confusables, never other letters`() {
        repeat(200) { seed ->
            val card = CardFactory.hearPick(catalog.getValue("a"), catalog, false, null, Random(seed))
            val ids = card.options.map { it.id }
            assertEquals(4, ids.size)
            assertTrue("a" in ids)
            assertTrue(ids.all { it in setOf("a", "b", "c", "d", "e") }, "$ids")
            assertEquals(ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `a letter with one confusable gets two options, not random padding`() {
        val card = CardFactory.hearPick(catalog.getValue("b"), catalog, false, null, Random(1))
        assertEquals(setOf("a", "b"), card.options.map { it.id }.toSet())
    }

    @Test
    fun `voice differs from the last one used when more than one exists`() {
        repeat(200) { seed ->
            val card = CardFactory.hearPick(catalog.getValue("a"), catalog, false, "v2", Random(seed))
            assertNotEquals("v2", card.clip.voiceId)
        }
    }

    @Test
    fun `a single voice is still used`() {
        val solo = letter("s", listOf("a"), voices = 1)
        val card = CardFactory.hearPick(solo, catalog + ("s" to solo), false, "v1", Random(1))
        assertEquals("v1", card.clip.voiceId)
    }
}
