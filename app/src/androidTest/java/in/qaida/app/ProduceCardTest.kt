package `in`.qaida.app

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.qaida.app.data.db.ReviewStateEntity
import `in`.qaida.app.domain.CardType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ln

/**
 * A PRODUCE card end to end: record on the device microphone, analyse, self-rate, and log. Also checks the
 * product rule that a recording is never shown a score, a percentage, or the word wrong.
 */
@RunWith(AndroidJUnit4::class)
class ProduceCardTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as QaidaApp).container
    private val driver by lazy { SessionDriver(rule) }

    @Test
    fun recordAnalyseAndSelfRate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.RECORD_AUDIO)

        runBlocking {
            container.contentReady.await()
            container.settingsRepository.update { it.copy(micDeclined = false) }
            val dao = container.database.progressDao()
            dao.clearProgress()
            // Every tier 1 item freshly reviewed (too fresh to draw) except PRODUCE, which is due.
            val now = System.currentTimeMillis()
            fun at(p: Double, s: Double) = now - (-s * ln(p) / ln(2.0) * 3_600_000L).toLong()
            for (i in 1..4) for (type in CardType.entries) {
                val (p, s) = if (type == CardType.PRODUCE) 0.5 to 20.0 else 0.99 to 20.0
                dao.upsertReviewState(ReviewStateEntity("placeholder_0$i", type.name, s, at(p, s)))
            }
        }

        rule.onNodeWithTag("start").performClick()
        rule.waitUntil(10_000) { driver.existsTag("produce_record") || driver.existsTag("boss_done") }
        if (driver.existsTag("boss_done")) rule.onNodeWithTag("boss_done").performClick()
        rule.waitUntil(10_000) { driver.existsTag("produce_record") }

        rule.onNodeWithTag("produce_record").performClick()
        Thread.sleep(1_500)
        rule.onNodeWithTag("produce_record").performClick()
        rule.waitUntil(10_000) { driver.existsTag("rate_GETTING_THERE") }

        // Whatever the microphone heard, the screen holds facts or the low-confidence line, never a verdict.
        val texts = rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any text") {
            it.config.getOrNull(SemanticsProperties.Text) != null
        }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.Text].map { t -> t.text } }
        assertFalse("verdict-like text on a PRODUCE card: $texts", texts.any { t ->
            "%" in t || t.contains("wrong", ignoreCase = true) || t.contains("score", ignoreCase = true)
        })
        assertTrue(driver.existsTag("produce_low") || driver.existsTag("produce_held"))

        rule.onNodeWithTag("rate_GETTING_THERE").performClick()
        driver.answerUntilSummary()

        val logged = runBlocking { container.database.openHelper.readableDatabase }
            .query("SELECT selfRating, correct FROM answer_log WHERE cardType = 'PRODUCE'").use { c ->
                assertTrue("no PRODUCE answer logged", c.moveToFirst())
                c.getInt(0) to c.isNull(1)
            }
        assertEquals(1 to true, logged) // getting there, and never graded
    }
}
