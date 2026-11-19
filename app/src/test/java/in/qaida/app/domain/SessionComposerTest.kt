package `in`.qaida.app.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.ln
import kotlin.random.Random

class SessionComposerTest {
    private val hour = 3_600_000L
    private val now = 1_700_000_000_000L
    private val hear = CardType.HEAR_PICK

    /** Letters in disjoint confusion sets of [setSize]; set i is tier i+1. Ids and glyphs are meaningless. */
    private fun letters(count: Int, setSize: Int = 4): List<CatalogLetter> = List(count) { i ->
        val set = i / setSize
        val members = (set * setSize until minOf(count, (set + 1) * setSize)).map { "l$it" }
        CatalogLetter(
            id = "l$i", glyph = "?", unlockTier = set + 1, ordinal = i,
            confusableWith = members - "l$i", confusionSets = setOf(set),
            isolatedClips = listOf(AudioClip("l${i}_isolated_v1.opus", "v1")),
        )
    }

    /** A state whose recall at [now] is exactly [p]. Stability stays below the 8 h that opens other card types. */
    private fun stateAt(p: Double, s: Double = 5.0) =
        ReviewState(s, now - (-s * ln(p) / ln(2.0) * hour).toLong())

    private fun compose(unlocked: List<CatalogLetter>, states: Map<ItemKey, ReviewState>, seed: Int = 1) =
        SessionComposer.compose(unlocked, states, now, Random(seed), maxCards = 12)

    private fun assertNoConsecutiveSameLetter(cards: List<PlannedCard>) {
        cards.zipWithNext().forEach { (a, b) ->
            assertTrue(a.item.letterId != b.item.letterId, "consecutive ${a.item.letterId} in ${cards.map { it.item.letterId }}")
        }
    }

    @Test
    fun `cold start introduces two letters in teaching order, two cards each, interleaved`() {
        val cards = compose(letters(4), emptyMap())
        assertEquals(4, cards.size)
        assertTrue(cards.all { it.isIntro })
        assertEquals(setOf("l0", "l1"), cards.map { it.item.letterId }.toSet())
        assertNoConsecutiveSameLetter(cards)
    }

    @Test
    fun `later sessions introduce one new letter`() {
        val ls = letters(4)
        val states = mapOf(ItemKey("l0", hear) to stateAt(0.85), ItemKey("l1", hear) to stateAt(0.92))
        val cards = compose(ls, states)
        val intro = cards.filter { it.isIntro }.map { it.item.letterId }
        assertEquals(listOf("l2", "l2"), intro)
        assertEquals(setOf("l0", "l1", "l2"), cards.map { it.item.letterId }.toSet())
    }

    @Test
    fun `a session straight after another introduces two letters, not one alone`() {
        val ls = letters(4)
        // l0 and l1 were answered moments ago: too fresh for any slot.
        val states = mapOf(ItemKey("l0", hear) to stateAt(0.99), ItemKey("l1", hear) to stateAt(0.99))
        val cards = compose(ls, states)
        assertEquals(4, cards.size)
        assertEquals(setOf("l2", "l3"), cards.map { it.item.letterId }.toSet())
        assertNoConsecutiveSameLetter(cards)
    }

    @Test
    fun `weak five and due items come before fill, capped at twelve`() {
        val ls = letters(20)
        // 10 due items with recall 0.10..0.55, 10 not due with recall 0.91..0.94.
        val states = ls.mapIndexed { i, l ->
            ItemKey(l.id, hear) to if (i < 10) stateAt(0.10 + i * 0.05) else stateAt(0.91 + (i - 10) * 0.003)
        }.toMap()
        val cards = compose(ls, states)
        val ids = cards.map { it.item.letterId }
        assertEquals(12, cards.size, "8 due (2 weak + 6) + 4 fill; no new letters left")
        assertEquals(8, ids.count { it.removePrefix("l").toInt() < 10 })
        // The two most overdue due items not taken by the weak-five draw are always among the six.
        val dueChosen = ids.filter { it.removePrefix("l").toInt() < 10 }.toSet()
        assertTrue((0 until 5).count { "l$it" in dueChosen } >= 2, "weak five under-represented: $ids")
        // Fill takes the not-due items closest to due: the lowest recall among l10..l19.
        assertEquals(setOf("l10", "l11", "l12", "l13"), ids.filter { it.removePrefix("l").toInt() >= 10 }.toSet())
    }

    @Test
    fun `fill never uses items still at recall 0_95 or above`() {
        val ls = letters(4)
        val states = ls.associate { ItemKey(it.id, hear) to stateAt(0.97) }
        assertTrue(compose(ls, states).isEmpty())
    }

    @Test
    fun `each item appears once except the two introduction cards`() {
        val ls = letters(12)
        val states = ls.drop(1).associate { ItemKey(it.id, hear) to stateAt(0.3) }
        val counts = compose(ls, states).groupingBy { it.item }.eachCount()
        assertEquals(2, counts.getValue(ItemKey("l0", hear)))
        assertTrue(counts.filterKeys { it.letterId != "l0" }.values.all { it == 1 })
    }

    @Test
    fun `never two consecutive cards for the same letter, across random sessions`() {
        repeat(2_000) { seed ->
            val rng = Random(seed)
            val ls = letters(rng.nextInt(1, 17), setSize = rng.nextInt(2, 5))
            val states = ls.filter { rng.nextDouble() < 0.7 }
                .associate { ItemKey(it.id, hear) to stateAt(rng.nextDouble(0.05, 0.99)) }
            assertNoConsecutiveSameLetter(compose(ls, states, seed))
        }
    }

    @Test
    fun `confusion sets are not blocked when the mix allows it`() {
        // Three disjoint sets of four letters, all due: twelve distinct letters, four from each set.
        val ls = letters(12)
        val states = ls.associate { ItemKey(it.id, hear) to stateAt(0.5) }
        repeat(500) { seed ->
            val cards = Interleaver.arrange(
                ls.map { PlannedCard(ItemKey(it.id, hear), false) }, ls.associate { it.id to it.confusionSets }, Random(seed),
            )
            val sets = cards.map { c -> ls.first { it.id == c.item.letterId }.confusionSets.single() }
            assertFalse(sets.zipWithNext().any { (a, b) -> a == b }, "adjacent cards share a set: $sets")
        }
        // Through the real composer: all twelve due, none new, so 2 weak + 6 due and nothing to fill.
        val composed = compose(ls, states)
        assertEquals(8, composed.size)
        assertNoConsecutiveSameLetter(composed)
    }

    @Test
    fun `an infeasible mix drops a card rather than repeat a letter back to back`() {
        val cards = Interleaver.arrange(
            List(2) { PlannedCard(ItemKey("l0", hear), true) }, mapOf("l0" to setOf(0)), Random(3),
        )
        assertEquals(1, cards.size)
    }

    @Test
    fun `other card types join only once a letter's HEAR_PICK has held for 8 hours`() {
        val ls = letters(4)
        val young = ls.associate { ItemKey(it.id, hear) to stateAt(0.5, s = 5.0) }
        assertTrue(compose(ls, young).all { it.item.cardType == hear })

        val established = ls.associate { ItemKey(it.id, hear) to stateAt(0.5, s = 9.0) }
        val types = compose(ls, established).map { it.item.cardType }.toSet()
        assertTrue(types.size > 1, "expected new card types, got $types")
    }

    @Test
    fun `HEAR_PICK keeps at least 60 percent of every full session`() {
        val ls = letters(12)
        repeat(300) { seed ->
            val rng = Random(seed)
            // Every type of every letter reviewed and due at random recall.
            val states = ls.flatMap { l -> CardType.entries.filter { it != CardType.PRODUCE }.map { ItemKey(l.id, it) } }
                .associateWith { stateAt(rng.nextDouble(0.05, 0.9), s = 20.0) }
            val cards = compose(ls, states, seed)
            val others = cards.count { it.item.cardType != hear }
            assertTrue(others <= (12 * SessionComposer.MAX_OTHER_SHARE).toInt(), "others=$others of ${cards.size}")
            assertNoConsecutiveSameLetter(cards)
        }
    }

    @Test
    fun `at most one PRODUCE card per session, and none when speaking practice is off`() {
        val ls = letters(8)
        val states = ls.flatMap { l -> CardType.entries.map { ItemKey(l.id, it) } }
            .associateWith { stateAt(0.4, s = 20.0) }
        repeat(100) { seed ->
            val with = SessionComposer.compose(ls, states, now, Random(seed), 12, produceAllowed = true)
            assertTrue(with.count { it.item.cardType == CardType.PRODUCE } <= 1)
            val without = SessionComposer.compose(ls, states, now, Random(seed), 12, produceAllowed = false)
            assertTrue(without.none { it.item.cardType == CardType.PRODUCE })
        }
    }
}
