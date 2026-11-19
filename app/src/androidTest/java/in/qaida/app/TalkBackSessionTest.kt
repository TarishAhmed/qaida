package `in`.qaida.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.qaida.app.data.db.ReviewStateEntity
import `in`.qaida.app.domain.CardType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ln

/**
 * Completes a session the way a TalkBack user would: finding controls only by what TalkBack speaks
 * (text and content descriptions, never test tags) and activating them with the accessibility click
 * action a double-tap sends. On every card it also checks that nothing clickable is unlabelled, and that
 * the result of each answer is announced through a live region.
 */
@RunWith(AndroidJUnit4::class)
class TalkBackSessionTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as QaidaApp).container
    private fun s(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun nodes(m: SemanticsMatcher) = rule.onAllNodes(m).fetchSemanticsNodes()
    private fun has(m: SemanticsMatcher) = nodes(m).isNotEmpty()
    private fun activate(m: SemanticsMatcher) = rule.onAllNodes(m).onFirst().performSemanticsAction(SemanticsActions.OnClick)

    private fun spokenLabel(n: SemanticsNode): String =
        (n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ").trim()

    private fun assertEveryClickableIsLabelled() {
        val unlabelled = nodes(SemanticsMatcher("clickable") { it.config.getOrNull(SemanticsActions.OnClick) != null })
            .filter { spokenLabel(it).isEmpty() }
        assertTrue("clickable without a spoken label: ${unlabelled.map { it.config }}", unlabelled.isEmpty())
    }

    private val announcement = SemanticsMatcher("live region with text") {
        it.config.getOrNull(SemanticsProperties.LiveRegion) != null &&
            !it.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty()
    }

    @Test
    fun aSessionIsCompletableWithTalkBackLabelsAndActionsAlone() {
        runBlocking {
            container.contentReady.await()
            val dao = container.database.progressDao()
            dao.clearProgress()
            // Every graded type due, so the session mixes glyph, sound and same/different cards.
            val now = System.currentTimeMillis()
            val t = now - (-20.0 * ln(0.6) / ln(2.0) * 3_600_000L).toLong()
            for (i in 1..4) for (type in CardType.entries - CardType.PRODUCE) {
                dao.upsertReviewState(ReviewStateEntity("placeholder_0$i", type.name, 20.0, t))
            }
        }
        val start = hasText(s(R.string.home_start, 90))
        rule.waitUntil(10_000) { has(start) }
        assertEveryClickableIsLabelled()
        activate(start)

        // After an answer the tiles stay on screen, disabled, until the next card: match enabled ones only.
        val option1 = hasContentDescription(s(R.string.session_option_n, 1)) and isEnabled()
        val sound1 = hasContentDescription(s(R.string.session_sound_n, 1)) and isEnabled()
        val same = hasText(s(R.string.session_same)) and isEnabled()
        val skip = hasText(s(R.string.produce_skip))
        val readIt = hasText(s(R.string.boss_done))
        val tapToContinue = hasText(s(R.string.session_tap_to_continue))
        val summary = SemanticsMatcher("summary") { n ->
            n.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(Regex("\\d+ of \\d+ correct")) } == true
        }

        var answered = 0
        while (true) {
            rule.waitUntil(10_000) { listOf(summary, tapToContinue, option1, sound1, same, skip, readIt).any(::has) }
            if (has(summary)) break
            assertEveryClickableIsLabelled()
            when {
                has(tapToContinue) -> { activate(tapToContinue); continue }
                has(readIt) -> { activate(readIt); continue }
                has(skip) -> { activate(skip); continue }
                has(option1) -> activate(option1)
                has(sound1) -> { activate(sound1); activate(hasText(s(R.string.session_choose))) }
                has(same) -> activate(same)
            }
            answered++
            rule.waitUntil(5_000) { has(announcement) || has(summary) }
            assertTrue("session did not end", answered <= 12)
        }
        assertTrue("no cards answered", answered > 0)
    }
}
