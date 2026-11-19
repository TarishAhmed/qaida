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

    fun compose(
        unlocked: List<CatalogLetter>,
        states: Map<ItemKey, ReviewState>,
        cardTypes: List<CardType>,
        now: Long,
        rng: Random,
        maxCards: Int,
    ): List<PlannedCard> {
        val items = unlocked.flatMap { l -> cardTypes.map { ItemKey(l.id, it) } }
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
            List(INTRO_CARDS_PER_LETTER) { i -> PlannedCard(ItemKey(l.id, cardTypes[i % cardTypes.size]), isIntro = true) }
        }

        val planned = (weak + dueRest).map { PlannedCard(it, isIntro = false) } + intro +
            fill.map { PlannedCard(it, isIntro = false) }
        val setsByLetter = unlocked.associate { it.id to it.confusionSets }
        return Interleaver.arrange(planned.take(maxCards), setsByLetter, rng)
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

/** A HEAR_PICK card ready to show: the clip to play and the glyph options, target among them. */
data class HearPickCard(
    val target: CatalogLetter,
    val clip: AudioClip,
    val options: List<CatalogLetter>,
    val isIntro: Boolean,
)

object CardFactory {
    const val OPTIONS = 4

    /**
     * Distractors come only from the target's confusableWith list, never random letters, so a letter
     * with fewer than three confusables gets fewer than four options. The voice differs from
     * [lastVoice] whenever the letter has more than one.
     */
    fun hearPick(
        target: CatalogLetter,
        catalog: Map<String, CatalogLetter>,
        isIntro: Boolean,
        lastVoice: String?,
        rng: Random,
    ): HearPickCard {
        val distractors = target.confusableWith.distinct().mapNotNull { catalog[it] }.shuffled(rng).take(OPTIONS - 1)
        val voices = target.isolatedClips.filter { it.voiceId != lastVoice }.ifEmpty { target.isolatedClips }
        return HearPickCard(
            target = target,
            clip = voices.random(rng),
            options = (distractors + target).shuffled(rng),
            isIntro = isIntro,
        )
    }
}
