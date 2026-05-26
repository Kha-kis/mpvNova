package app.mpvnova.player

import android.util.Log
import android.support.v4.media.session.MediaSessionCompat

internal fun MPVActivity.initMediaSession(): MediaSessionCompat {
    /*
        https://developer.android.com/guide/topics/media-apps/working-with-a-media-session
        https://developer.android.com/guide/topics/media-apps/audio-app/mediasession-callbacks
        https://developer.android.com/reference/android/support/v4/media/session/MediaSessionCompat
     */
    val session = MediaSessionCompat(this, MPV_ACTIVITY_TAG)
    session.setFlags(0)
    session.setCallback(mediaSessionCallback)
    return session
}

internal fun MPVActivity.updateMediaSession() {
    // Coalesce: many property updates within the same event-loop turn
    // collapse to a single MediaSession IPC. Lifecycle paths that need an
    // immediate write (onPause, onload, etc.) can call updateMediaSessionNow().
    if (mediaSessionUpdatePending) return
    mediaSessionUpdatePending = true
    eventUiHandler.post(mediaSessionUpdateRunnable)
}

internal fun MPVActivity.updateMediaSessionNow() {
    synchronized (psc) {
        mediaSession?.let { psc.write(it) }
    }
}

// Property → UI-thread handler tables. Centralised so adding a new
// observed property in MPVView.observeProperties() lines up with adding
// a handler here. If a property is observed but has no entry it gets
// silently dropped on the UI side (which is fine — many properties are
// observed only for their event-thread side-effects like media-session
// sync, not UI updates).

private val METADATA_UI_HANDLERS: Map<String, MPVActivity.() -> Unit> = mapOf(
    "track-list" to {
        player.loadTracks()
        maybeApplyShieldHi10pFallback()
    },
    "current-tracks/audio/selected" to {
        updateAudioUI()
        maybeApplyShieldHi10pFallback()
    },
    "current-tracks/video/image" to {
        updateAudioUI()
        maybeApplyShieldHi10pFallback()
    },
    "hwdec-current" to {
        updateDecoderButton()
        updateGpuNextRetryConfirmation()
    },
)

private val LONG_UI_HANDLERS: Map<String, MPVActivity.() -> Unit> = mapOf(
    "playlist-pos" to { updatePlaylistButtons() },
    "playlist-count" to { updatePlaylistButtons() },
)

private val DOUBLE_UI_HANDLERS: Map<String, MPVActivity.() -> Unit> = mapOf(
    // time-pos/full is intentionally absent — it goes through the coalesced
    // timePosUiRunnable to keep the SW decoder from being preempted by a
    // per-frame UI dispatch.
    "duration/full" to { updatePlaybackDuration(psc.duration) },
    "video-params/aspect" to { updatePiPParams() },
    "video-params/rotate" to { updatePiPParams() },
)

private val STRING_UI_HANDLERS: Map<String, MPVActivity.() -> Unit> = mapOf(
    "speed" to { updateSpeedButton() },
    "current-vo" to {
        updateDecoderButton()
        updateGpuNextRetryConfirmation()
    },
)

internal fun MPVActivity.eventMetadataPropertyUi(property: String, metaUpdated: Boolean) {
    if (!activityIsForeground) return
    METADATA_UI_HANDLERS[property]?.invoke(this)
    if (metaUpdated) scheduleMetadataUiRefresh()
}

internal fun MPVActivity.eventBooleanPropertyUi(property: String, value: Boolean) {
    if (!activityIsForeground) return
    // pause has value-dependent behavior (auto-close overlay on unpause)
    // so it lives outside the simple map. Everything else is a no-arg
    // handler.
    when (property) {
        "pause" -> handlePauseUi(value)
        "paused-for-cache" -> {
            streamCacheLoading = value
            refreshLoadingOverlay()
        }
        "mute" -> updateAudioUI()
    }
}

internal fun MPVActivity.eventLongPropertyUi(property: String) {
    if (!activityIsForeground) return
    LONG_UI_HANDLERS[property]?.invoke(this)
}

internal fun MPVActivity.eventDoublePropertyUi(property: String) {
    if (!activityIsForeground) return
    DOUBLE_UI_HANDLERS[property]?.invoke(this)
}

internal fun MPVActivity.eventStringPropertyUi(property: String, metaUpdated: Boolean) {
    if (!activityIsForeground) return
    STRING_UI_HANDLERS[property]?.invoke(this)
    if (metaUpdated) scheduleMetadataUiRefresh()
}

internal fun MPVActivity.scheduleMetadataUiRefresh() {
    if (metadataUiPending) return
    metadataUiPending = true
    eventUiHandler.post(metadataUiRunnable)
}

internal fun MPVActivity.maybeApplyGpuNextRenderFallback(prefix: String, level: Int, text: String) {
    if (!canApplyGpuNextRenderFallback(level) || !isGpuNextRenderFailure(prefix, text))
        return
    when (gpuNextFallbackAction()) {
        GpuNextFallbackAction.RetryWithCopyHwdec -> retryGpuNextWithCopyHwdec(prefix, text)
        GpuNextFallbackAction.WaitForCopyRetry -> Log.w(
                MPV_ACTIVITY_TAG,
                "Ignoring gpu-next failure log while mediacodec-copy retry is still stabilizing ($prefix: $text)"
            )
        GpuNextFallbackAction.KeepGpuNext -> keepGpuNextAfterRetry(prefix, text)
        GpuNextFallbackAction.FallbackToGpu -> fallbackGpuNextToGpu(prefix, text)
    }
}
