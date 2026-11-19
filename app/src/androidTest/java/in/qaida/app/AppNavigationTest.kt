package `in`.qaida.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import `in`.qaida.app.data.db.BossAttemptEntity
import `in`.qaida.app.data.db.TierUnlockEntity
import `in`.qaida.app.data.settings.Language
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as QaidaApp).container

    @After
    fun englishAgain() = runBlocking { container.settingsRepository.update { it.copy(uiLanguage = Language.EN) } }

    private fun visitEveryScreen(texts: Map<String, String>) {
        rule.waitUntil(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("cell_placeholder_01")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(texts.getValue("start"), substring = true).assertExists()

        rule.onNodeWithTag("cell_placeholder_01").performClick()
        rule.onNodeWithText(texts.getValue("forms")).assertExists()
        rule.onNodeWithTag("back").performClick()

        rule.onNodeWithTag("progress").performClick()
        rule.onNodeWithText(texts.getValue("progress")).assertExists()
        rule.onNodeWithTag("back").performClick()

        rule.onNodeWithTag("settings").performClick()
        rule.onNodeWithText(texts.getValue("settings")).assertExists()
    }

    @Test
    fun everyScreenIsReachableInEnglishAndMalayalam() {
        runBlocking { container.contentReady.await() }
        val en = mapOf("start" to "Start", "forms" to "Forms", "progress" to "Progress", "settings" to "Settings")
        visitEveryScreen(en)

        // Switch the UI to Malayalam from Settings. The activity recreates and stays on Settings.
        rule.onNodeWithTag("ui_language_1").performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("ക്രമീകരണങ്ങൾ")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("back").performClick()

        val ml = mapOf("start" to "തുടങ്ങുക", "forms" to "രൂപങ്ങൾ", "progress" to "പുരോഗതി", "settings" to "ക്രമീകരണങ്ങൾ")
        visitEveryScreen(ml)
        rule.onNodeWithTag("reset").performScrollTo()
    }

    @Test
    fun exportWipeImportRestoresStateExactly() {
        val dao = container.database.progressDao()
        runBlocking {
            container.contentReady.await()
            dao.clearProgress()
        }
        // Real progress from a real session, plus unlock and boss rows.
        rule.onNodeWithTag("start").performClick()
        SessionDriver(rule).answerUntilSummary()
        runBlocking {
            dao.insertTierUnlocks(listOf(TierUnlockEntity(2, 1_700_000_000_000)))
            dao.insertBossAttempt(BossAttemptEntity(1, 1_700_000_100_000))
            container.settingsRepository.update { it.copy(sessionSeconds = 180, reminderMinuteOfDay = 6 * 60 + 15) }
        }

        fun snapshot() = runBlocking {
            listOf(dao.reviewStates(), dao.answers(), dao.sessions(), dao.tierUnlocks(), dao.bossAttempts(),
                listOf(container.settingsRepository.current())).map { it.toString() }
        }
        val before = snapshot()
        assertTrue("session produced no answers", runBlocking { dao.answerCount() } > 0)

        val json = runBlocking { container.backupRepository.export(System.currentTimeMillis()) }
        runBlocking {
            dao.clearProgress()
            container.settingsRepository.update { it.copy(sessionSeconds = 60, reminderMinuteOfDay = 0) }
        }
        assertTrue(snapshot() != before)

        runBlocking { container.backupRepository.import(json) }
        assertEquals(before, snapshot())
        runBlocking { container.settingsRepository.update { it.copy(sessionSeconds = 90, reminderMinuteOfDay = 19 * 60) } }
    }
}
