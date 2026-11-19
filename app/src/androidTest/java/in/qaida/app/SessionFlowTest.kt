package `in`.qaida.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end through the real app: real Room, real content import, real ExoPlayer on the debug stub pack.
 * Answers every card (picking the first option, so both the correct and the incorrect paths occur),
 * reaches the summary, and returns to Home.
 */
@RunWith(AndroidJUnit4::class)
class SessionFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun tagged(prefix: String) = SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun exists(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun existsTag(tag: String) = exists(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag))

    @Test
    fun aSessionRunsFromStartToSummaryAndBackHome() {
        val dao = (rule.activity.application as QaidaApp).container.database.progressDao()
        val before = runBlocking { dao.answerCount() }

        rule.onNodeWithTag("start").performClick()

        var answered = 0
        while (true) {
            rule.waitUntil(10_000) { existsTag("summary") || existsTag("continue") || exists(tagged("option_") and isEnabled()) }
            when {
                existsTag("summary") -> break
                existsTag("continue") -> rule.onNodeWithTag("continue").performClick()
                else -> {
                    rule.onAllNodes(tagged("option_") and isEnabled())[0].performClick()
                    answered++
                    rule.waitUntil(5_000) { !exists(tagged("option_") and isEnabled()) || existsTag("summary") }
                }
            }
            assertTrue("session did not end within 12 cards", answered <= 12)
        }
        assertTrue("no cards were answered", answered > 0)

        // The summary holds for four seconds, then Home returns.
        rule.waitUntil(8_000) { existsTag("start") }

        val after = runBlocking { dao.answerCount() }
        assertEquals("every answer is logged", answered, after - before)
    }
}
