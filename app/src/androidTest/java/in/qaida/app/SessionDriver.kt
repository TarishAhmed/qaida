package `in`.qaida.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.assertTrue

/** Answers every card of a running session (always the first option) until the summary shows. */
class SessionDriver(private val rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {

    private fun tagged(prefix: String) = SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun exists(m: SemanticsMatcher) = rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    fun existsTag(tag: String) = exists(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag))

    fun instructionText(): String? = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "instruction"), useUnmergedTree = true)
        .fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString()

    /** Returns the instruction line of every card answered, in order. */
    fun answerUntilSummary(maxCards: Int = 12): List<String> {
        val seen = mutableListOf<String>()
        while (true) {
            rule.waitUntil(10_000) {
                existsTag("summary") || existsTag("continue") || existsTag("produce_skip") || existsTag("boss_done") ||
                    exists(tagged("option_") and isEnabled())
            }
            when {
                existsTag("summary") -> return seen
                existsTag("continue") -> rule.onNodeWithTag("continue").performClick()
                existsTag("boss_done") -> rule.onNodeWithTag("boss_done").performClick()
                existsTag("produce_skip") -> {
                    instructionText()?.let { seen += it }
                    rule.onNodeWithTag("produce_skip").performClick()
                }
                else -> {
                    instructionText()?.let { seen += it }
                    rule.onAllNodes(tagged("option_") and isEnabled())[0].performClick()
                    // A sound option only selects; Choose submits it.
                    if (existsTag("confirm")) rule.onNodeWithTag("confirm").performClick()
                    rule.waitUntil(5_000) { !exists(tagged("option_") and isEnabled()) || existsTag("summary") }
                }
            }
            assertTrue("session did not end within $maxCards cards", seen.size <= maxCards)
        }
    }

    @Suppress("unused") private val activity: ComponentActivity get() = rule.activity
}
