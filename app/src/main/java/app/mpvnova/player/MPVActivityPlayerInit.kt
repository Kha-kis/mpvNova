package app.mpvnova.player

import android.annotation.SuppressLint
import android.content.Intent
import android.view.View
import java.util.Locale

@SuppressLint("ClickableViewAccessibility")
internal fun MPVActivity.initListeners() {
    bindClickListeners()
    bindLongClickListeners()
    bindSeekbarListeners()
    bindTouchAndInsetsListeners()
    bindActivityCallbacks()
}

internal fun MPVActivity.finishWithResult(code: Int, includeTimePos: Boolean = false) {
    if (isFinishing) // only count first call
        return
    val result = Intent(RESULT_INTENT)
    result.data = if (intent.data?.scheme == "file") null else intent.data
    if (includeTimePos) {
        if (eofWasReached) {
            result.putExtra("end_by", "playback_completion")
        } else {
            val safePosition = psc.position.coerceAtLeast(0L)
            val safeDuration = psc.duration.coerceAtLeast(0L)
            result.putExtra("position", safePosition.toInt())
            result.putExtra("duration", safeDuration.toInt())
            result.putExtra("extra_position", safePosition)
            result.putExtra("extra_duration", safeDuration)
            intent.data?.takeUnless { it.scheme == "file" }?.let {
                result.putExtra("extra_uri", it.toString())
            }
            result.putExtra("end_by", "user")
        }
    }
    setResult(code, result)
    finish()
}

internal fun MPVActivity.resetPlaybackResultState() {
    playbackHasStarted = false
    eofWasReached = false
}

internal fun MPVActivity.isNetworkStreamPath(path: String?): Boolean {
    val normalized = path?.trim()?.lowercase(Locale.US) ?: return false
    return normalized.startsWith("http://") || normalized.startsWith("https://")
}

internal fun MPVActivity.currentMpvPath(): String? {
    return mpvGetPropertyString("stream-open-filename")
        ?: mpvGetPropertyString("path")
        ?: mpvGetPropertyString("filename")
}

internal fun MPVActivity.prepareStreamLoading(path: String?) {
    streamOpenLoading = isNetworkStreamPath(path)
    streamCacheLoading = false
    refreshLoadingOverlay()
}

internal fun MPVActivity.refreshLoadingOverlay() {
    val visible = streamOpenLoading || streamCacheLoading
    binding.loadingText.setText(
        if (streamCacheLoading) R.string.player_buffering_stream
        else R.string.player_loading_stream
    )
    binding.loadingOverlay.animate().cancel()
    if (visible) {
        if (binding.loadingOverlay.visibility != View.VISIBLE) {
            binding.loadingOverlay.alpha = 0f
            binding.loadingOverlay.visibility = View.VISIBLE
        }
        binding.loadingOverlay.animate()
            .alpha(1f)
            .setDuration(LOADING_OVERLAY_FADE_MS)
            .setListener(null)
            .withLayer()
    } else if (binding.loadingOverlay.visibility == View.VISIBLE) {
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(LOADING_OVERLAY_FADE_MS)
            .withLayer()
            .withEndAction { binding.loadingOverlay.visibility = View.GONE }
    }
}

internal fun MPVActivity.updateAudioPresence() {
    val haveAudio = mpvGetPropertyBoolean("current-tracks/audio/selected")
    if (haveAudio == null) {
        // If we *don't know* if there's an active audio track then don't update to avoid
        // spurious UI changes. The property will become available again later.
        return
    }
    isPlayingAudio = (haveAudio && mpvGetPropertyBoolean("mute") != true)
}
