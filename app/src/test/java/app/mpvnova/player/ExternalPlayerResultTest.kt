package app.mpvnova.player

import org.junit.Assert.assertEquals
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

    @Test
    fun stremioUsesVimuResultContract() {
        assertTrue(shouldUseVimuResultContract("com.stremio.one"))
    }

    @Test
    fun nuvioUsesVimuResultContract() {
        assertTrue(shouldUseVimuResultContract("com.nuvio.tv"))
    }

    @Test
    fun vimuContractReturnsStoppedCodeForResumeProgress() {
        assertEquals(
            VIMU_RESULT_STOPPED,
            externalPlaybackResultCode(
                defaultCode = RESULT_OK,
                includeTimePos = true,
                playbackComplete = false,
                vimuResultContract = true,
            )
        )
    }

    @Test
    fun vimuContractReturnsCompletedCodeAtEnd() {
        assertEquals(
            VIMU_RESULT_COMPLETED,
            externalPlaybackResultCode(
                defaultCode = RESULT_OK,
                includeTimePos = true,
                playbackComplete = true,
                vimuResultContract = true,
            )
        )
    }

    @Test
    fun mpvContractKeepsDefaultResultCode() {
        assertEquals(
            RESULT_OK,
            externalPlaybackResultCode(
                defaultCode = RESULT_OK,
                includeTimePos = true,
                playbackComplete = true,
                vimuResultContract = false,
            )
        )
    }
}
