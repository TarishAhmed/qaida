package `in`.qaida.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.qaida.app.data.db.ReviewStateEntity
import `in`.qaida.app.domain.CardType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ln

/**
 * End to end through the real app: real Room, real content import, real ExoPlayer on the debug stub pack.
 * Answers every card (picking the first option, so both the correct and the incorrect paths occur),
 * reaches the summary, and returns to Home.
 */
@RunWith(AndroidJUnit4::class)
class SessionFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as QaidaApp).container
    private val driver by lazy { SessionDriver(rule) }

    private fun resetProgress() = runBlocking {
        container.contentReady.await()
        container.database.progressDao().clearProgress()
    }

    @Test
    fun aSessionRunsFromStartToSummaryAndBackHome() {
        resetProgress()
        val dao = container.database.progressDao()
        val before = runBlocking { dao.answerCount() }

        rule.onNodeWithTag("start").performClick()
        val answered = driver.answerUntilSummary().size
        assertTrue("no cards were answered", answered > 0)

        // The summary holds for four seconds, then Home returns.
        rule.waitUntil(8_000) { driver.existsTag("start") }
        assertEquals("every answer is logged", answered, runBlocking { dao.answerCount() } - before)
    }

    @Test
    fun allFiveGradedCardTypesAppear() {
        val ctx = rule.activity
        val expected = listOf(
            R.string.card_hear_pick, R.string.card_see_pick, R.string.card_ab_same_diff,
            R.string.card_form_pick, R.string.card_harakah_pick,
        ).map(ctx::getString).toSet()
        val seen = mutableSetOf<String>()

        repeat(8) {
            if (seen.containsAll(expected)) return@repeat
            // Tier 1 established and every graded item due, so the composer draws from all five types.
            resetProgress()
            runBlocking {
                val dao = container.database.progressDao()
                val now = System.currentTimeMillis()
                val s = 20.0
                val t = now - (-s * ln(0.6) / ln(2.0) * 3_600_000L).toLong()
                for (i in 1..4) for (type in CardType.entries - CardType.PRODUCE) {
                    dao.upsertReviewState(ReviewStateEntity("placeholder_0$i", type.name, s, t))
                }
            }
            rule.onNodeWithTag("start").performClick()
            seen += driver.answerUntilSummary()
            rule.waitUntil(8_000) { driver.existsTag("start") }
        }
        assertEquals("card types seen", expected, seen.intersect(expected))
    }
}
