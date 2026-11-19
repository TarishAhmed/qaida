package `in`.qaida.app.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.ln

class ProgressionTest {
    private val hour = 3_600_000L
    private val now = 1_700_000_000_000L
    private val hear = CardType.HEAR_PICK

    /** Two tiers of four, plus a third, in disjoint sets. Ids are meaningless. */
    private val catalog = List(12) { i ->
        CatalogLetter("l$i", "?", unlockTier = i / 4 + 1, ordinal = i, confusableWith = emptyList(),
            confusionSets = setOf(i / 4), isolatedClips = emptyList())
    }

    /** A state with recall [p] at [now] and stability [s] hours. */
    private fun at(p: Double, s: Double) = ReviewState(s, now - (-s * ln(p) / ln(2.0) * hour).toLong())

    private fun tier(n: Int, p: Double, s: Double, type: CardType = hear) =
        catalog.filter { it.unlockTier == n }.associate { ItemKey(it.id, type) to at(p, s) }

    @Test
    fun `tier 1 is always open`() {
        assertEquals(listOf(1), Progression.newlyUnlocked(catalog, emptyMap(), emptySet(), now))
        assertEquals(emptyList<Int>(), Progression.newlyUnlocked(catalog, emptyMap(), setOf(1), now))
    }

    @Test
    fun `tier 2 stays locked right after a first correct pass, however high p is`() {
        // p is 1.0 straight after an answer; stability is still hours, not a day.
        val states = tier(1, p = 0.999, s = 3.8)
        assertEquals(emptyList<Int>(), Progression.newlyUnlocked(catalog, states, setOf(1), now))
    }

    @Test
    fun `tier 2 unlocks when every tier 1 letter is at p 0_8 with a day of stability`() {
        assertEquals(listOf(2), Progression.newlyUnlocked(catalog, tier(1, p = 0.81, s = 26.0), setOf(1), now))
    }

    @Test
    fun `one tier 1 letter below 0_8 keeps tier 2 locked`() {
        val states = tier(1, p = 0.9, s = 30.0) + (ItemKey("l3", hear) to at(0.79, 30.0))
        assertEquals(emptyList<Int>(), Progression.newlyUnlocked(catalog, states, setOf(1), now))
    }

    @Test
    fun `a never-reviewed tier 1 letter keeps tier 2 locked`() {
        val states = tier(1, p = 0.9, s = 30.0) - ItemKey("l2", hear)
        assertEquals(emptyList<Int>(), Progression.newlyUnlocked(catalog, states, setOf(1), now))
    }

    @Test
    fun `unlocks cascade when several tiers are ready at once`() {
        val states = tier(1, 0.9, 30.0) + tier(2, 0.9, 30.0)
        assertEquals(listOf(2, 3), Progression.newlyUnlocked(catalog, states, setOf(1), now))
    }

    @Test
    fun `an unlocked tier is not re-reported, even after decay`() {
        val decayed = tier(1, p = 0.2, s = 30.0)
        assertEquals(emptyList<Int>(), Progression.newlyUnlocked(catalog, decayed, setOf(1, 2), now))
    }

    @Test
    fun `letter mastery follows its weakest graded item and ignores PRODUCE`() {
        val states = mapOf(
            ItemKey("l0", hear) to at(0.97, 200.0), // gold on its own
            ItemKey("l0", CardType.SEE_PICK) to at(0.6, 10.0), // amber
            ItemKey("l0", CardType.PRODUCE) to at(0.1, 1.0), // self-rated: ignored
        )
        val l0 = Progression.status(catalog, states, setOf(1), now).first { it.letter.id == "l0" }
        assertEquals(Mastery.AMBER, l0.mastery)
        assertEquals(0.6, l0.recall, 1e-3)
    }

    @Test
    fun `grid buckets track p`() {
        val states = mapOf(
            ItemKey("l0", hear) to at(0.3, 10.0),
            ItemKey("l1", hear) to at(0.6, 10.0),
            ItemKey("l2", hear) to at(0.9, 10.0),
            ItemKey("l3", hear) to at(0.97, 200.0),
        )
        val m = Progression.status(catalog, states, setOf(1), now).associate { it.letter.id to it.mastery }
        assertEquals(Mastery.RED, m["l0"])
        assertEquals(Mastery.AMBER, m["l1"])
        assertEquals(Mastery.GREEN, m["l2"])
        assertEquals(Mastery.GOLD, m["l3"])
        assertEquals(Mastery.NEW, m["l4"])
    }

    @Test
    fun `review count is due letters in unlocked tiers only`() {
        val states = tier(1, p = 0.5, s = 10.0) + tier(2, p = 0.5, s = 10.0)
        val statuses = Progression.status(catalog, states, setOf(1), now)
        assertEquals(4, Progression.lettersNeedingReview(statuses))
    }

    @Test
    fun `history rises with spaced correct answers, dips on a miss, and ignores PRODUCE`() {
        val day = 24 * hour
        fun a(t: Long, ok: Boolean?, type: CardType = hear) = Progression.LoggedAnswer(type, now + t, ok, null)
        val h = Progression.history(listOf(
            a(0, true), a(day, true), a(2 * day, true), a(3 * day, false),
            a(3 * day + hour, null, CardType.PRODUCE), a(5 * day, true),
        ))
        assertEquals(5, h.size)
        assert(h[0] < h[1] && h[1] < h[2]) { "$h" }
        assert(h[3] < h[2]) { "$h" }
        assert(h.all { it in 0.0..1.0 })
    }
}
