package app.mpvnova.player

import android.os.SystemClock
import java.util.Locale

internal enum class GpuNextFallbackAction {
    RetryWithCopyHwdec,
    WaitForCopyRetry,
    KeepGpuNext,
    FallbackToGpu,
}

// A single transient libplacebo error (e.g. "failed creating pass" when
// the OSD overlay is added on UI open) must not flip the renderer mid-
// playback — that rebuilds the VO while audio keeps draining its buffer,
// causing the audio/video/sub desync users see on Hi10p+g-next.
private const val GPU_NEXT_ERROR_WINDOW_MS = 1500L
private const val GPU_NEXT_ERROR_WINDOW_THRESHOLD = 3

internal fun MPVActivity.canApplyGpuNextRenderFallback(level: Int): Boolean {
    // Cheap, ordered gates: auto-fallback enabled, error-level log line, the
    // current VO is actually gpu-next, and the user has not explicitly picked
    // g-next or the Shield Hi10P mode (otherwise the fallback would silently
    // switch their chosen renderer every time the OSD logs a transient error).
    val chosen = sessionDecoderMode ?: preferredDecoderMode
    val userPickedGpuNextMode =
        chosen == MPVView.DECODER_MODE_GNEXT || chosen == MPVView.DECODER_MODE_SHIELD_H10P
    val gatesPassed = autoDecoderFallback &&
        level <= MpvLogLevel.MPV_LOG_LEVEL_ERROR &&
        player.requestedVideoOutput.trim().lowercase(Locale.US).startsWith("gpu-next") &&
        !userPickedGpuNextMode
    if (!gatesPassed) return false
    // Sliding error-count window: require ≥ THRESHOLD errors inside WINDOW_MS
    // before treating it as a sustained failure. Single OSD-related blips
    // (very common on Tegra when controls fade in) won't trip the rebuild.
    val now = SystemClock.uptimeMillis()
    if (now - gpuNextErrorWindowStartMs > GPU_NEXT_ERROR_WINDOW_MS) {
        gpuNextErrorWindowStartMs = now
        gpuNextErrorWindowCount = 0
    }
    gpuNextErrorWindowCount += 1
    return gpuNextErrorWindowCount >= GPU_NEXT_ERROR_WINDOW_THRESHOLD
}

internal fun MPVActivity.gpuNextFallbackAction(): GpuNextFallbackAction {
    val activeHwdec = player.hwdecActive.trim().lowercase(Locale.US)
    val requestedHwdec = normalizedHwdecOption()
    val shouldRetryWithCopyHwdec = gpuNextRenderFallbackStage == 0 &&
        activeHwdec != "mediacodec-copy" &&
        requestedHwdec != "mediacodec-copy"
    val copyRetryFinished = gpuNextCopyRetryConfirmed && gpuNextCopyRetryDisplayedFrame
    return when {
        shouldRetryWithCopyHwdec -> GpuNextFallbackAction.RetryWithCopyHwdec
        gpuNextRenderFallbackStage == 1 && !copyRetryFinished -> GpuNextFallbackAction.WaitForCopyRetry
        gpuNextRenderFallbackStage in GPU_NEXT_RETRY_STAGES && copyRetryFinished -> GpuNextFallbackAction.KeepGpuNext
        else -> GpuNextFallbackAction.FallbackToGpu
    }
}
