package `in`.qaida.app.domain

import kotlin.random.Random

/** A letter as the learning engine sees it: identity, teaching position and what it is confused with. */
data class CatalogLetter(
    val id: String,
    val glyph: String,
    val unlockTier: Int,
    /** Position within letters.json: teaching order inside a tier. */
    val ordinal: Int,
    val confusableWith: List<String>,
    /** Indexes of the confusion sets this letter belongs to. */
    val confusionSets: Set<Int>,
    val isolatedClips: List<AudioClip>,
    /** Positional forms from letters.json; defaults to the bare glyph for tests that do not care. */
    val forms: LetterForms = LetterForms(glyph, glyph, glyph, glyph),
    /** Clips per haraka context ("fatha", "kasra", "damma"); empty if the pack has none for this letter. */
    val harakatClips: Map<String, List<AudioClip>> = emptyMap(),
)

data class AudioClip(val file: String, val voiceId: String)

data class ItemKey(val letterId: String, val cardType: CardType)

/** A card chosen for a session, before its options and voice are drawn. */
data class PlannedCard(val item: ItemKey, val isIntro: Boolean)

data class SessionConfig(val durationMs: Long = 90_000, val maxCards: Int = 12)

/**
 * Chooses and orders the cards for one session.
 *
 * Priority, in order:
 *  1. 2 cards from the 5 lowest-recall due items ("weak five")
 *  2. 6 cards from the remaining due items, most overdue first
 *  3. 2 cards introducing the next unlocked, never-reviewed letter (2 letters when the session would
 *     otherwise hold nothing else, so it can still interleave)
 *  then up to 2 first cards of card types a letter has newly qualified for (see CardTypes.available)
 *  4. the remainder from reviewed items that are not yet due, closest to due first, skipping any still
 *     at recall >= 0.95. Answering an item that fresh has negative expected effect on its stability
 *     (see SchedulerTest), so this replaces the spec's "highest-stability maintenance" fill (agreed
 *     2026-11-19). If nothing qualifies, the session is simply shorter, or empty.
 */
object SessionComposer {
    const val WEAK_POOL = 5
    const val WEAK_CARDS = 2
    const val DUE_CARDS = 6
    const val INTRO_CARDS_PER_LETTER = 2
    const val NEW_TYPE_CARDS = 2

    /** Other card types may take at most this share of a session, keeping HEAR_PICK the backbone (~60%). */
    const val MAX_OTHER_SHARE = 0.4

    fun compose(
        unlocked: List<CatalogLetter>,
        states: Map<ItemKey, ReviewState>,
        now: Long,
        rng: Random,
        maxCards: Int,
    ): List<PlannedCard> {
        val items = unlocked.flatMap { l -> CardTypes.available(l, states).map { ItemKey(l.id, it) } }
        val reviewed = items.filter { states[it]?.lastReviewedAt != null }
        val recall = reviewed.associateWith { Scheduler.recall(states.getValue(it), now) }

        val due = reviewed.filter { Scheduler.isDue(states.getValue(it), now) }.sortedBy { recall.getValue(it) }
        val weak = due.take(WEAK_POOL).shuffled(rng).take(WEAK_CARDS)
        val dueRest = (due - weak.toSet()).take(DUE_CARDS)

        val chosen = (weak + dueRest).toSet()
        val fill = reviewed.filter { it !in chosen && !Scheduler.isDue(states.getValue(it), now) && recall.getValue(it) < Scheduler.EASY_RECALL }
            .sortedBy { recall.getValue(it) }

        // One new letter per session, or two when nothing else is on offer (a cold start, or a session
        // straight after another): one letter alone cannot be interleaved.
        val reviewedLetters = reviewed.map { it.letterId }.toSet()
        val newLetters = unlocked.sortedWith(compareBy({ it.unlockTier }, { it.ordinal }))
            .filter { it.id !in reviewedLetters }
        val nothingElse = chosen.isEmpty() && fill.isEmpty()
        val introLetters = newLetters.take(if (reviewedLetters.size < 2 || nothingElse) 2 else 1)
        val intro = introLetters.flatMap { l ->
            List(INTRO_CARDS_PER_LETTER) { PlannedCard(ItemKey(l.id, CardType.HEAR_PICK), isIntro = true) }
        }

        // A letter's other card types, once it qualifies for them, start here: never reviewed, so they are
        // neither due nor fill. Introduced a couple at a time, weakest letters first.
        val newTypes = items.filter { it.letterId in reviewedLetters && states[it]?.lastReviewedAt == null }
            .sortedBy { recall[ItemKey(it.letterId, CardType.HEAR_PICK)] ?: 1.0 }
            .take(NEW_TYPE_CARDS)

        val planned = (weak + dueRest).map { PlannedCard(it, isIntro = false) } + intro +
            newTypes.map { PlannedCard(it, isIntro = true) } + fill.map { PlannedCard(it, isIntro = false) }
        val setsByLetter = unlocked.associate { it.id to it.confusionSets }
        return Interleaver.arrange(capOtherTypes(planned, maxCards), setsByLetter, rng)
    }

    /** Takes cards in priority order, skipping non-HEAR_PICK cards once they would exceed their share. */
    private fun capOtherTypes(planned: List<PlannedCard>, maxCards: Int): List<PlannedCard> {
        val maxOther = (maxCards * MAX_OTHER_SHARE).toInt()
        var others = 0
        val out = ArrayList<PlannedCard>(maxCards)
        for (c in planned) {
            if (out.size == maxCards) break
            if (c.item.cardType != CardType.HEAR_PICK) {
                if (others == maxOther) continue
                others++
            }
            out += c
        }
        return out
    }
}

/**
 * Orders cards so that the same letter never appears twice in a row and, where the mix allows it,
 * adjacent cards do not share a confusion set. Blocked practice feels better and teaches worse.
 */
object Interleaver {
    private const val ATTEMPTS = 60

    fun arrange(cards: List<PlannedCard>, setsByLetter: Map<String, Set<Int>>, rng: Random): List<PlannedCard> {
        var pool = cards
        while (pool.isNotEmpty()) {
            attempt(pool, setsByLetter, rng, strictSets = true)?.let { return it }
            attempt(pool, setsByLetter, rng, strictSets = false)?.let { return it }
            // Infeasible (one letter fills more than half the session): drop one of its cards.
            val crowded = pool.groupingBy { it.item.letterId }.eachCount().maxBy { it.value }.key
            pool = pool - pool.last { it.item.letterId == crowded }
        }
        return pool
    }

    private fun attempt(
        cards: List<PlannedCard>,
        setsByLetter: Map<String, Set<Int>>,
        rng: Random,
        strictSets: Boolean,
    ): List<PlannedCard>? {
        fun sharesSet(a: PlannedCard, b: PlannedCard) =
            setsByLetter[a.item.letterId].orEmpty().any { it in setsByLetter[b.item.letterId].orEmpty() }

        repeat(ATTEMPTS) {
            val remaining = cards.shuffled(rng).toMutableList()
            val out = ArrayList<PlannedCard>(cards.size)
            while (remaining.isNotEmpty()) {
                val prev = out.lastOrNull()
                val candidates = remaining.filter { c ->
                    prev == null || (c.item.letterId != prev.item.letterId && (!strictSets || !sharesSet(c, prev)))
                }
                if (candidates.isEmpty()) break
                // Place the most crowded letter first so the tail does not run out of alternatives.
                val counts = remaining.groupingBy { it.item.letterId }.eachCount()
                val pick = candidates.maxBy { counts.getValue(it.item.letterId) + rng.nextDouble() }
                out += pick
                remaining.remove(pick)
            }
            if (remaining.isEmpty()) return out
        }
        return null
    }
}
