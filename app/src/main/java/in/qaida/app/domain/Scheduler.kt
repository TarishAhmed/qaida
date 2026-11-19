package `in`.qaida.app.domain

import kotlin.math.pow

/** Review state of one (letterId, cardType) item. [lastReviewedAt] is null until the first answer. */
data class ReviewState(
    val stabilityHours: Double = Scheduler.INITIAL_STABILITY,
    val lastReviewedAt: Long? = null,
)

enum class Mastery { NEW, RED, AMBER, GREEN, GOLD }

/** The learner's own judgement of a PRODUCE attempt. The app never grades recitation. */
enum class SelfRating(val code: Int) { NEEDS_WORK(0), GETTING_THERE(1), SOUNDS_RIGHT(2) }

/**
 * Half-life scheduler. Recall probability after t hours is p = 2^(-t/s) for stability s.
 * Constants are fixed by the product spec; do not retune without sign-off.
 */
object Scheduler {
    const val INITIAL_STABILITY = 2.0
    const val MIN_STABILITY = 0.5
    const val MAX_STABILITY = 4320.0 // 6 months
    const val GROWTH = 1.9
    const val EASY_GROWTH = 1.05
    const val LAPSE = 0.4
    const val EASY_RECALL = 0.95
    const val DUE_RECALL = 0.9
    const val GOLD_STABILITY = 168.0 // one week

    private const val MS_PER_HOUR = 3_600_000.0

    /** Recall probability at [now]; 0 for an item that has never been reviewed. */
    fun recall(state: ReviewState, now: Long): Double {
        val last = state.lastReviewedAt ?: return 0.0
        val hours = ((now - last) / MS_PER_HOUR).coerceAtLeast(0.0)
        return 2.0.pow(-hours / state.stabilityHours)
    }

    /** Never-reviewed items are not due: they enter sessions through the new-letter slot. */
    fun isDue(state: ReviewState, now: Long) =
        state.lastReviewedAt != null && recall(state, now) <= DUE_RECALL

    fun onAnswer(state: ReviewState, correct: Boolean, now: Long): ReviewState {
        val s = state.stabilityHours
        val next = when {
            !correct -> maxOf(s * LAPSE, MIN_STABILITY)
            recall(state, now) < EASY_RECALL -> s * GROWTH
            else -> s * EASY_GROWTH // answered while still fresh: no learning happened
        }
        return ReviewState(next.coerceIn(MIN_STABILITY, MAX_STABILITY), now)
    }

    /**
     * PRODUCE items are scheduled from the learner's self-rating: needs work is a lapse, sounds right a
     * success, and getting there keeps the stability where it is and restarts the clock.
     */
    fun onSelfRating(state: ReviewState, rating: SelfRating, now: Long): ReviewState = when (rating) {
        SelfRating.NEEDS_WORK -> onAnswer(state, correct = false, now)
        SelfRating.GETTING_THERE -> ReviewState(state.stabilityHours, now)
        SelfRating.SOUNDS_RIGHT -> onAnswer(state, correct = true, now)
    }

    fun mastery(state: ReviewState, now: Long): Mastery {
        if (state.lastReviewedAt == null) return Mastery.NEW
        val p = recall(state, now)
        return when {
            p > EASY_RECALL && state.stabilityHours > GOLD_STABILITY -> Mastery.GOLD
            p >= 0.8 -> Mastery.GREEN
            p >= 0.5 -> Mastery.AMBER
            else -> Mastery.RED
        }
    }
}
