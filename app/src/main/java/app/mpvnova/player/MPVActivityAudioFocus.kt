package app.mpvnova.player

import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat

/**
 * Audio focus + dialog pause + controls-overlay autopause.
 *
 * - [handleAudioFocus] / [requestAudioFocus] / [onAudioFocusChange]: own
 *   the AudioFocus lifecycle and the becoming-noisy receiver.
 * - [keepPlaybackForDialog] / [pauseForDialog]: dialog-open helpers that
 *   either keep video running with keep-open or pause based on the user's
 *   "Pause playback when UI dialogs open" preference.
 * - [shouldAutoPauseForControlsOverlay] / [maybeAutoPauseForControlsOverlay]:
 *   the player-UI-specific autopause that pauses while the controls overlay
 *   is visible (default on for Shield Hi10p, opt-in for everything else).
 */

/**
 * Requests or abandons audio focus and noisy receiver depending on the playback state.
 * @warning Call from event thread, not UI thread
 */
internal fun MPVActivity.handleAudioFocus() {
    if ((psc.pause && !psc.cachePause) || !isPlayingAudio) {
        if (becomingNoisyReceiverRegistered)
            unregisterReceiver(becomingNoisyReceiver)
        becomingNoisyReceiverRegistered = false
    } else {
        if (!becomingNoisyReceiverRegistered)
            registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
        becomingNoisyReceiverRegistered = true
        // (re-)request audio focus
        // Note that this will actually request focus every time the user unpauses, refer to discussion in #1066
        if (requestAudioFocus()) {
            onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, "request")
        } else {
            onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, "request")
        }
    }
}

internal fun MPVActivity.requestAudioFocus(): Boolean {
    val manager = audioManager
    val req = audioFocusRequest ?:
        with(AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)) {
        setAudioAttributes(with(AudioAttributesCompat.Builder()) {
            // N.B.: libmpv may use different values in ao_audiotrack, but here we always pretend to be music.
            setUsage(AudioAttributesCompat.USAGE_MEDIA)
            setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            build()
        })
        setOnAudioFocusChangeListener {
            onAudioFocusChange(it, "callback")
        }
        build()
    }
    val res = manager?.let { AudioManagerCompat.requestAudioFocus(it, req) }
    return if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        audioFocusRequest = req
        true
    } else {
        false
    }
}

internal fun MPVActivity.onAudioFocusChange(type: Int, source: String) {
    Log.v(MPV_ACTIVITY_TAG, "Audio focus changed: $type ($source)")
    if (ignoreAudioFocus || isFinishing)
        return
    when (type) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            // loss can occur in addition to ducking, so remember the old callback
            val oldRestore = audioFocusRestore
            val wasPlayerPaused = player.paused ?: false
            player.paused = true
            audioFocusRestore = {
                oldRestore()
                if (!wasPlayerPaused) player.paused = false
            }
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            mpvCommand(arrayOf("multiply", "volume", AUDIO_FOCUS_DUCKING.toString()))
            audioFocusRestore = {
                val inv = 1f / AUDIO_FOCUS_DUCKING
                mpvCommand(arrayOf("multiply", "volume", inv.toString()))
            }
        }
        AudioManager.AUDIOFOCUS_GAIN -> {
            audioFocusRestore()
            audioFocusRestore = {}
        }
    }
}

internal fun MPVActivity.keepPlaybackForDialog(): StateRestoreCallback {
    val oldValue = mpvGetPropertyString("keep-open")
    mpvSetPropertyBoolean("keep-open", true)
    return {
        oldValue?.also { mpvSetPropertyString("keep-open", it) }
    }
}

internal fun MPVActivity.pauseForDialog(): StateRestoreCallback {
    val useKeepOpen = when (noUIPauseMode) {
        "always" -> true
        "audio-only" -> isPlayingAudioOnly()
        else -> false // "never"
    }
    if (useKeepOpen) {
        // don't pause but set keep-open so mpv doesn't exit while the user is doing stuff
        return keepPlaybackForDialog()
    }

    // Pause playback during UI dialogs
    val wasPlayerPaused = player.paused ?: true
    player.paused = true
    return {
        if (!wasPlayerPaused)
            player.paused = false
    }
}

// Player UI auto-pause for the controls overlay. Two independent toggles can
// turn it on:
//   - autoPauseControlsOverlayEnabled (general): pause for any file.
//   - autoPauseShieldHi10pEnabled (specific): pause for Hi10p H.264 on Shield,
//     where the SW decoder is so close to real-time that any concurrent UI
//     work accumulates A/V drift. Defaults on because that hardware case is
//     a known problem, but the user can opt out from the Player UI settings.
internal fun MPVActivity.shouldAutoPauseForControlsOverlay(): Boolean {
    val shieldHi10pCase = autoPauseShieldHi10pEnabled &&
        isNvidiaShieldDevice() &&
        player.isHi10pH264Video()
    return autoPauseControlsOverlayEnabled || shieldHi10pCase
}

internal fun MPVActivity.maybeAutoPauseForControlsOverlay() {
    val alreadyPausedOrUnknown = player.paused != false
    val shouldPause = !controlsOverlayAutoPaused &&
        shouldAutoPauseForControlsOverlay() &&
        !alreadyPausedOrUnknown
    if (shouldPause) {
        controlsOverlayAutoPaused = true
        mpvSetPropertyBoolean("pause", true)
    }
}
