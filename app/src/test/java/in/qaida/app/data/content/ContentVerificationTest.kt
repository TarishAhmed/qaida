package `in`.qaida.app.data.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentVerificationTest {
    private val clips = setOf("a_isolated_v1.opus", "a_isolated_v2.opus")

    @Test
    fun `no verification file means nothing is verified`() {
        val v = ContentVerification.of(clips, null)
        assertFalse(v.complete)
        assertEquals(clips, v.pending)
    }

    @Test
    fun `complete only when every clip is marked true`() {
        assertFalse(ContentVerification.of(clips, """{"clips":{"a_isolated_v1.opus":true,"a_isolated_v2.opus":false}}""").complete)
        assertFalse(ContentVerification.of(clips, """{"clips":{"a_isolated_v1.opus":true}}""").complete)
        assertTrue(ContentVerification.of(clips, """{"clips":{"a_isolated_v1.opus":true,"a_isolated_v2.opus":true}}""").complete)
    }

    @Test
    fun `an empty pack is never complete`() {
        assertFalse(ContentVerification.of(emptySet(), """{"clips":{}}""").complete)
    }
}
