package `in`.qaida.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.qaida.app.data.db.ReviewStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ln

/** The Home grid against seeded progress: colours follow p, and tier 2 opens only on the unlock rule. */
@RunWith(AndroidJUnit4::class)
class HomeGridTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as QaidaApp).container
    private val hour = 3_600_000L

    private fun seed(vararg rows: Pair<String, Pair<Double, Double>>) = runBlocking {
        container.contentReady.await()
        val dao = container.database.progressDao()
        dao.clearProgress()
        val now = System.currentTimeMillis()
        for ((id, ps) in rows) {
            val (p, s) = ps
            dao.upsertReviewState(ReviewStateEntity(id, "HEAR_PICK", s, now - (-s * ln(p) / ln(2.0) * hour).toLong()))
        }
    }

    /** Leave Home and come back, which re-reads progress exactly as returning from a session does. */
    private fun reshowHome() {
        rule.onNodeWithTag("settings").performClick()
        rule.onNodeWithTag("back").performClick()
        rule.waitForIdle()
    }

    private fun assertCell(id: String, label: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label))
                .fetchSemanticsNodes().any { n -> n.config.getOrNull(SemanticsProperties.TestTag) == "cell_$id" }
        }
        rule.onNodeWithTag("cell_$id").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label))
    }

    @Test
    fun gridColoursTrackRecall() {
        seed(
            "placeholder_01" to (0.3 to 10.0),
            "placeholder_02" to (0.6 to 10.0),
            "placeholder_03" to (0.9 to 10.0),
            "placeholder_04" to (0.97 to 200.0),
        )
        reshowHome()
        assertCell("placeholder_01", "Weak")
        assertCell("placeholder_02", "Building")
        assertCell("placeholder_03", "Strong")
        assertCell("placeholder_04", "Mastered")
        assertCell("placeholder_05", "Locked")
    }

    @Test
    fun tier2UnlocksOnlyWhenTier1IsReady() {
        // Fresh answers: p is high but stability is only hours. Tier 2 stays locked.
        seed(*(1..4).map { "placeholder_0$it" to (0.99 to 3.8) }.toTypedArray())
        reshowHome()
        assertCell("placeholder_05", "Locked")

        // Every tier 1 letter at p >= 0.8 with a day of stability: tier 2 opens.
        seed(*(1..4).map { "placeholder_0$it" to (0.85 to 30.0) }.toTypedArray())
        reshowHome()
        assertCell("placeholder_05", "Not started")
        assertCell("placeholder_09", "Locked")
    }
}
