package app.mpvnova.player

import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

internal class MpvActivityLifecycleObserver(private val activity: MPVActivity) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        activity.activityIsStopped = false
    }

    override fun onStop(owner: LifecycleOwner) {
        activity.activityIsStopped = true
    }
}

/**
 * Wraps an [MPVActivity] for mpv's native event callbacks. Two concerns
 * live here:
 *
 *   1. State sync — every property update folds into the playback-state
 *      cache and (when meta changed) refreshes the media session.
 *   2. UI dispatch — UI updates for foreground activity are posted to the
 *      UI handler via the typed [eventLongPropertyUi] / `eventDoublePropertyUi`
 *      / `eventStringPropertyUi` / `eventBooleanPropertyUi` /
 *      `eventMetadataPropertyUi` helpers, which each route through a
 *      property → handler table so adding or renaming a property only
 *      requires updating one place.
 */
internal class MpvActivityEventObserver(private val activity: MPVActivity) : MpvEventObserver {

    override fun eventProperty(property: String): Unit = with(activity) {
        val metaUpdated = psc.update(property)
        if (metaUpdated) updateMediaSession()
        dispatchEventThreadMetadata(property)
        if (!activityIsForeground) return
        eventUiHandler.post { eventMetadataPropertyUi(property, metaUpdated) }
    }

    override fun eventProperty(property: String, value: Boolean): Unit = with(activity) {
        val metaUpdated = psc.update(property, value)
        if (metaUpdated) updateMediaSession()
        dispatchEventThreadBoolean(property, value, metaUpdated)
        if (!activityIsForeground) return
        eventUiHandler.post { eventBooleanPropertyUi(property, value) }
    }

    override fun eventProperty(property: String, value: Long): Unit = with(activity) {
        if (psc.update(property, value)) updateMediaSession()
        if (!activityIsForeground) return
        eventUiHandler.post { eventLongPropertyUi(property) }
    }

    override fun eventProperty(property: String, value: Double): Unit = with(activity) {
        if (psc.update(property, value)) updateMediaSession()
        if (!activityIsForeground) return
        // time-pos/full is special: mpv fires it at video frame rate, so
        // posting a fresh Runnable per event would wake the UI thread ~60×/s
        // and starve the SW decoder on Hi10p. Route it through the coalesced
        // runnable instead, which natural-batches into ~5 UI updates/sec.
        if (property == "time-pos/full") {
            if (!timePosUiPending) {
                timePosUiPending = true
                eventUiHandler.postDelayed(timePosUiRunnable, TIME_POS_UI_COALESCE_DELAY_MS)
            }
        } else {
            eventUiHandler.post { eventDoublePropertyUi(property) }
        }
    }

    override fun eventProperty(property: String, value: String): Unit = with(activity) {
        val metaUpdated = psc.update(property, value)
        if (metaUpdated) updateMediaSession()
        if (!activityIsForeground) return
        eventUiHandler.post { eventStringPropertyUi(property, metaUpdated) }
    }

    override fun event(eventId: Int) {
        activity.handleMpvEvent(eventId)
    }

    /**
     * Event-thread side-effects for boolean property updates: things that
     * must run regardless of foreground state and shouldn't be deferred to
     * the UI handler (audio focus reacquire, shuffle sync, etc.).
     */
    private fun MPVActivity.dispatchEventThreadBoolean(property: String, value: Boolean, metaUpdated: Boolean) {
        when (property) {
            "shuffle" -> mediaSession?.setShuffleMode(
                if (value) PlaybackStateCompat.SHUFFLE_MODE_ALL
                else PlaybackStateCompat.SHUFFLE_MODE_NONE
            )
            "mute" -> updateAudioPresence()
        }
        if (metaUpdated || property == "mute")
            handleAudioFocus()
    }

    /**
     * Event-thread side-effects for FORMAT_NONE / metadata-string updates.
     * loop-* drive the MediaSession repeat mode, audio-track changes feed
     * audio focus + filter persistence, pause cycles audio focus.
     */
    private fun MPVActivity.dispatchEventThreadMetadata(property: String) {
        when (property) {
            "loop-file", "loop-playlist" -> {
                mediaSession?.setRepeatMode(when (player.getRepeat()) {
                    2 -> PlaybackStateCompat.REPEAT_MODE_ONE
                    1 -> PlaybackStateCompat.REPEAT_MODE_ALL
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                })
            }
            "current-tracks/audio/selected" -> {
                updateAudioPresence()
                if (persistAudioFilters) {
                    rebuildAudioFilters()
                    eventUiHandler.post { refreshAllFilterTints() }
                }
            }
        }
        if (property == "pause" || property == "current-tracks/audio/selected")
            handleAudioFocus()
    }
}

internal class MpvActivityLogObserver(private val activity: MPVActivity) : MpvLogObserver {
    override fun logMessage(prefix: String, level: Int, text: String) = activity.run {
        updateGpuNextRetryFrameConfirmation(prefix, text)
        maybeApplyGpuNextRenderFallback(prefix, level, text)
        maybeShowAudioNormUnderrunHint(text)
    }

    /**
     * mpv emits "Audio device underrun detected" when the AO can't keep
     * up. If the user has a non-downmixed surround track AND has audio
     * normalisation enabled, the most likely cause is that normalisation
     * is pushing levels past what the surround stack can sustain. Surface
     * a one-shot hint pointing them at the downmix toggle.
     */
    private fun MPVActivity.maybeShowAudioNormUnderrunHint(text: String) {
        val shouldShowHint = !audioNormUnderrunHintShown &&
            activityIsForeground &&
            text.contains("Audio device underrun detected", ignoreCase = true) &&
            isAudioNormOn() &&
            !isDownmixOn() &&
            currentAudioChannelCount() >= MIN_SURROUND_CHANNELS
        if (!shouldShowHint) return
        audioNormUnderrunHintShown = true
        eventUiHandler.post {
            showToast(
                getString(R.string.btn_audio_norm),
                getString(R.string.toast_audio_norm_surround_hint)
            )
        }
    }
}
