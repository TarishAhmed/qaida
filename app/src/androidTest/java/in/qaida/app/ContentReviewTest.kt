package `in`.qaida.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unverified content offers the review screen from Settings, and a reviewer's marks are kept. */
@RunWith(AndroidJUnit4::class)
class ContentReviewTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as QaidaApp).container

    @Test
    fun marksAreKept() {
        runBlocking { container.contentReady.await(); container.reviewStore.save(emptyMap()) }
        assertTrue(runBlocking { !container.contentVerification().complete })

        rule.onNodeWithTag("settings").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("review")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("review").performScrollTo().performClick()

        val okChip = SemanticsMatcher("review ok chip") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("review_ok_") == true }
        rule.waitUntil(10_000) { rule.onAllNodes(okChip).fetchSemanticsNodes().isNotEmpty() }
        val first = rule.onAllNodes(okChip).fetchSemanticsNodes().first().config[SemanticsProperties.TestTag].removePrefix("review_ok_")
        rule.onAllNodes(okChip).onFirst().performClick()

        rule.waitUntil(15_000) { runBlocking { container.reviewStore.load() }[first] == true }
        assertEquals(mapOf(first to true), runBlocking { container.reviewStore.load() })
        runBlocking { container.reviewStore.save(emptyMap()) }
    }
}
