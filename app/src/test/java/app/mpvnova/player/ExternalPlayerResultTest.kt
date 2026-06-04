package app.mpvnova.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlayerResultTest {
    @Test
    fun playbackIsCompleteAtDuration() {
        assertTrue(isPlaybackCompleteForResult(positionMs = 600_000L, durationMs = 600_000L))
    }

    @Test
    fun playbackIsCompleteInsideNearEndWindow() {
        assertTrue(isPlaybackCompleteForResult(positionMs = 575_000L, durationMs = 600_000L))
    }

    @Test
    fun playbackIsNotCompleteWhenStreamEndsEarly() {
        assertFalse(isPlaybackCompleteForResult(positionMs = 600_000L, durationMs = 7_200_000L))
    }

    @Test
    fun playbackIsNotCompleteWithoutKnownDuration() {
        assertFalse(isPlaybackCompleteForResult(positionMs = 600_000L, durationMs = 0L))
    }
}
