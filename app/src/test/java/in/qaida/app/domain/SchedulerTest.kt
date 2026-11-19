package `in`.qaida.app.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SchedulerTest {
    private val hour = 3_600_000L
    private val t0 = 1_700_000_000_000L

    private fun reviewed(s: Double, hoursAgo: Double) = ReviewState(s, t0 - (hoursAgo * hour).toLong())

    @Test
    fun `recall halves every half-life`() {
        assertEquals(1.0, Scheduler.recall(reviewed(10.0, 0.0), t0), 1e-9)
        assertEquals(0.5, Scheduler.recall(reviewed(10.0, 10.0), t0), 1e-9)
        assertEquals(0.25, Scheduler.recall(reviewed(10.0, 20.0), t0), 1e-9)
    }

    @Test
    fun `new item has no recall, is not due, and shows as NEW`() {
        val new = ReviewState()
        assertEquals(0.0, Scheduler.recall(new, t0))
        assertFalse(Scheduler.isDue(new, t0))
        assertEquals(Mastery.NEW, Scheduler.mastery(new, t0))
    }

    @Test
    fun `first answer starts from the initial stability`() {
        assertEquals(2.0 * 1.9, Scheduler.onAnswer(ReviewState(), true, t0).stabilityHours, 1e-9)
        assertEquals(2.0 * 0.4, Scheduler.onAnswer(ReviewState(), false, t0).stabilityHours, 1e-9)
        assertEquals(t0, Scheduler.onAnswer(ReviewState(), true, t0).lastReviewedAt)
    }

    @Test
    fun `correct when recall below 0_95 grows by 1_9`() {
        // p = 2^(-1) = 0.5
        assertEquals(19.0, Scheduler.onAnswer(reviewed(10.0, 10.0), true, t0).stabilityHours, 1e-9)
    }

    @Test
    fun `correct when recall at least 0_95 grows by only 1_05`() {
        // p = 2^(-0.5/10) ~ 0.966
        assertEquals(10.5, Scheduler.onAnswer(reviewed(10.0, 0.5), true, t0).stabilityHours, 1e-9)
    }

    @Test
    fun `incorrect shrinks by 0_4 with a floor of 0_5`() {
        assertEquals(4.0, Scheduler.onAnswer(reviewed(10.0, 10.0), false, t0).stabilityHours, 1e-9)
        assertEquals(0.5, Scheduler.onAnswer(reviewed(1.0, 1.0), false, t0).stabilityHours, 1e-9)
    }

    @Test
    fun `stability is capped at six months`() {
        assertEquals(4320.0, Scheduler.onAnswer(reviewed(4000.0, 4000.0), true, t0).stabilityHours, 1e-9)
    }

    @Test
    fun `due exactly when recall drops to 0_9`() {
        val s = 10.0
        val dueAfterHours = s * Math.log(1 / 0.9) / Math.log(2.0) // ~1.52 h
        assertFalse(Scheduler.isDue(reviewed(s, dueAfterHours - 0.01), t0))
        assertTrue(Scheduler.isDue(reviewed(s, dueAfterHours + 0.01), t0))
    }

    @Test
    fun `mastery buckets`() {
        fun bucketAt(p: Double, s: Double = 10.0) =
            Scheduler.mastery(reviewed(s, -s * Math.log(p) / Math.log(2.0)), t0)
        assertEquals(Mastery.RED, bucketAt(0.49))
        assertEquals(Mastery.AMBER, bucketAt(0.51))
        assertEquals(Mastery.AMBER, bucketAt(0.79))
        assertEquals(Mastery.GREEN, bucketAt(0.81))
        assertEquals(Mastery.GREEN, bucketAt(0.99, s = 168.0)) // fresh but not yet a week of stability
        assertEquals(Mastery.GOLD, bucketAt(0.99, s = 169.0))
        assertEquals(Mastery.GREEN, bucketAt(0.94, s = 500.0)) // gold fades back to green as p falls
    }

    // --- Gate: a 90%-accurate learner should reach gold on a letter in roughly 8-14 sessions. ---

    /**
     * Sessions (that the learner does) until the letter first shows gold. Sessions are [gapHours] apart.
     * With [onlyWhenDue] the letter is only answered in sessions where it is new or due; otherwise every session.
     */
    private fun sessionsToGold(rng: Random, gapHours: Double, accuracy: Double, onlyWhenDue: Boolean, maxSessions: Int = 500): Int {
        var state = ReviewState()
        var now = t0
        for (session in 1..maxSessions) {
            if (!onlyWhenDue || state.lastReviewedAt == null || Scheduler.isDue(state, now)) {
                state = Scheduler.onAnswer(state, rng.nextDouble() < accuracy, now)
                if (Scheduler.mastery(state, now) == Mastery.GOLD) return session
            }
            now += (gapHours * hour).toLong()
        }
        return maxSessions
    }

    private data class Distribution(val p10: Int, val median: Int, val p90: Int)

    private fun distribution(gapHours: Double, onlyWhenDue: Boolean, accuracy: Double = 0.9, learners: Int = 10_000): Distribution {
        val rng = Random(42)
        val runs = IntArray(learners) { sessionsToGold(rng, gapHours, accuracy, onlyWhenDue) }.sorted()
        return Distribution(runs[learners / 10], runs[learners / 2], runs[learners * 9 / 10])
    }

    @Test
    fun `simulated 90 percent learner reaches gold in 8 to 14 daily sessions`() {
        for (onlyWhenDue in listOf(false, true)) {
            for (gap in listOf(1.0, 4.0, 12.0, 24.0, 48.0, 72.0)) {
                val d = distribution(gap, onlyWhenDue)
                println("${if (onlyWhenDue) "due-only " else "every    "} gap ${gap}h: sessions to gold p10=${d.p10} median=${d.median} p90=${d.p90}")
            }
        }

        val daily = distribution(24.0, onlyWhenDue = false)
        assertTrue(daily.median in 8..14, "daily median ${daily.median} outside 8..14: constants need review, do not silently retune")
    }
}
