package `in`.qaida.app.domain

/** How a letter stands right now, for the Home grid, tier unlocking and the review count. */
data class LetterStatus(
    val letter: CatalogLetter,
    val unlocked: Boolean,
    val mastery: Mastery,
    /** Recall of the letter's weakest reviewed graded item; 0 if never reviewed. */
    val recall: Double,
    val due: Boolean,
)

object Progression {
    const val UNLOCK_RECALL = 0.8
    /**
     * The spec unlocks the next tier at p >= 0.8, but p is 1.0 straight after any answer, so on its own
     * that would unlock a tier after one lucky session. Requiring a day of stability as well means
     * three or four spaced, successful reviews (agreed 2026-11-19).
     */
    const val UNLOCK_STABILITY_HOURS = 24.0

    /** PRODUCE is self-rated and never graded, so it does not count toward mastery. */
    val GRADED = CardType.entries - CardType.PRODUCE

    /** The letter's reviewed graded item with the lowest recall: a letter is only as strong as its weakest skill. */
    fun weakest(letterId: String, states: Map<ItemKey, ReviewState>, now: Long): ReviewState? =
        GRADED.mapNotNull { states[ItemKey(letterId, it)]?.takeIf { s -> s.lastReviewedAt != null } }
            .minByOrNull { Scheduler.recall(it, now) }

    fun status(
        catalog: List<CatalogLetter>,
        states: Map<ItemKey, ReviewState>,
        unlockedTiers: Set<Int>,
        now: Long,
    ): List<LetterStatus> = catalog.sortedWith(compareBy({ it.unlockTier }, { it.ordinal })).map { l ->
        val w = weakest(l.id, states, now)
        val unlocked = l.unlockTier in unlockedTiers
        LetterStatus(
            letter = l,
            unlocked = unlocked,
            mastery = w?.let { Scheduler.mastery(it, now) } ?: Mastery.NEW,
            recall = w?.let { Scheduler.recall(it, now) } ?: 0.0,
            due = unlocked && GRADED.any { t -> states[ItemKey(l.id, t)]?.let { Scheduler.isDue(it, now) } == true },
        )
    }

    /** The number Home shows. It rises on its own as recall decays and falls only by answering cards. */
    fun lettersNeedingReview(statuses: List<LetterStatus>) = statuses.count { it.due }

    private fun ready(letterId: String, states: Map<ItemKey, ReviewState>, now: Long): Boolean {
        val w = weakest(letterId, states, now) ?: return false
        return Scheduler.recall(w, now) >= UNLOCK_RECALL && w.stabilityHours >= UNLOCK_STABILITY_HOURS
    }

    /**
     * Tiers that should now be unlocked but are not yet. Tier 1 is always open; tier n+1 opens when every
     * letter of tier n is ready. Unlocks are permanent, so callers persist the result.
     */
    fun newlyUnlocked(
        catalog: List<CatalogLetter>,
        states: Map<ItemKey, ReviewState>,
        unlockedTiers: Set<Int>,
        now: Long,
    ): List<Int> {
        val byTier = catalog.groupBy { it.unlockTier }
        val open = unlockedTiers + 1
        val added = mutableListOf<Int>()
        var tier = 1
        while (tier + 1 in byTier) {
            val next = tier + 1
            if (next !in open && tier in (open + added) && byTier.getValue(tier).all { ready(it.id, states, now) }) {
                added += next
            }
            tier = next
        }
        return listOfNotNull(1.takeIf { it !in unlockedTiers }) + added
    }
}
