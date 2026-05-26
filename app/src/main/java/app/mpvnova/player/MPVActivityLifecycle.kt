package app.mpvnova.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.util.Log
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media.AudioManagerCompat

/**
 * Lifecycle-stage helpers for [MPVActivity]. Splits the substantial
 * onCreate / onDestroy bodies into named phases so the override is a
 * short orchestrator and each phase is grep-able by intent rather than
 * by line range.
 *
 * onPause / onResume / onNewIntent / onConfigurationChanged still live
 * in MPVActivity directly — they're short enough already and tied to
 * the override signatures.
 */

internal fun MPVActivity.setupRootView() {
    binding = app.mpvnova.player.databinding.PlayerBinding.inflate(layoutInflater)
    setContentView(binding.root)
    // setSelected fires AccessibilityEvent.TYPE_VIEW_SELECTED, which on
    // Shield with TV-launcher a11y services runs a main-thread subtree
    // walk on every button-bar dpad press. Scope the opt-out to JUST
    // the bottom controls subtree (where setSelected actually fires
    // during dpad navigation) — the rest of the overlay (toast, title,
    // loading spinner, top pill) stays normally accessible so screen
    // readers can still announce player-level events.
    binding.controls.importantForAccessibility =
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    isTvUiMode = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
    hideControls()
}

internal fun MPVActivity.setupImmersiveWindow() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
        binding.topPiPBtn.visibility = View.GONE
}

internal fun MPVActivity.startPlayerForFile(filepath: String) {
    player.addObserver(mpvEventObserver)
    addMpvLogObserver(mpvLogObserver)
    player.initialize(filesDir.path, cacheDir.path)
    applySavedAudioFilterDefaults()
    applySavedSubFilterDefaults()
    prepareStreamLoading(filepath)
    player.playFile(filepath)
    mediaSession = initMediaSession()
    updateMediaSessionNow()
    BackgroundPlaybackService.mediaToken = mediaSession?.sessionToken
    setupAudioSessionId()
    volumeControlStream = STREAM_TYPE
}

private fun MPVActivity.setupAudioSessionId() {
    val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager = manager
    val audioSessionId = manager.generateAudioSessionId()
    if (audioSessionId != AudioManager.ERROR)
        player.setAudioSessionId(audioSessionId)
    else
        Log.w(MPV_ACTIVITY_TAG, "AudioManager.generateAudioSessionId() returned error")
}

/**
 * onDestroy phase: drop every scheduled callback we own and clear all the
 * coalescing flags so nothing tries to fire against a torn-down activity.
 */
internal fun MPVActivity.cancelAllScheduledWork() {
    periodicSaveHandler.removeCallbacks(periodicSaveRunnable)
    eventUiHandler.removeCallbacks(commitSeekbarSeekRunnable)
    eventUiHandler.removeCallbacks(timePosUiRunnable)
    eventUiHandler.removeCallbacks(metadataUiRunnable)
    eventUiHandler.removeCallbacks(mediaSessionUpdateRunnable)
    eventUiHandler.removeCallbacks(shieldFallbackResyncRunnable)
    timePosUiPending = false
    metadataUiPending = false
    mediaSessionUpdatePending = false
}

/**
 * onDestroy phase: release the media session and abandon audio focus
 * cleanly. Both are independently nullable / optional, so guard each.
 */
internal fun MPVActivity.releaseMediaAndAudioFocus() {
    BackgroundPlaybackService.mediaToken = null
    mediaSession?.let {
        it.isActive = false
        it.release()
    }
    mediaSession = null
    audioFocusRequest?.let { request ->
        audioManager?.let { manager ->
            AudioManagerCompat.abandonAudioFocusRequest(manager, request)
        }
    }
    audioFocusRequest = null
}

/**
 * onNewIntent phase for the case where the activity is already in the
 * foreground (or we're configured to replace the current file regardless
 * of state). Updates the resume source / metadata for the new intent and
 * parses any embedded extras.
 */
internal fun MPVActivity.applyNewIntentReplacement(intent: Intent, filepath: String, nextResumeSource: String?) {
    currentResumeSource = nextResumeSource
    prepareMediaTitleFromIntent(intent, filepath)
    parseIntentExtras(intent.extras)
}
