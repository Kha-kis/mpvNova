package app.mpvnova.player

import android.view.KeyEvent
import android.view.View

/**
 * Skip prompt key routing follows Nuvio's behavior: hidden controls let OK skip and Back dismiss,
 * while visible controls only select the prompt from the seekbar and DOWN returns to the seekbar.
 */
internal fun MPVActivity.handleSkipButtonKey(ev: KeyEvent): Boolean {
    if (!skipButtonCanHandle(ev)) return false
    val controlsVisible = binding.controls.visibility == View.VISIBLE
    return if (controlsVisible) {
        handleSelectedSkipButtonKey(ev)
    } else {
        handleHiddenSkipButtonKey(ev)
    }
}

private fun MPVActivity.skipButtonCanHandle(ev: KeyEvent): Boolean {
    if (!skipButtonVisible || ev.action != KeyEvent.ACTION_DOWN) return false
    return binding.controls.visibility != View.VISIBLE ||
        btnSelected == SKIP_BUTTON_SELECTION_INDEX
}

private fun MPVActivity.handleHiddenSkipButtonKey(ev: KeyEvent): Boolean {
    return when (ev.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            skipFromButton()
            true
        }
        KeyEvent.KEYCODE_BACK -> {
            dismissSkipButton()
            true
        }
        else -> false
    }
}

private fun MPVActivity.handleSelectedSkipButtonKey(ev: KeyEvent): Boolean {
    if (btnSelected != SKIP_BUTTON_SELECTION_INDEX) return false
    return when (ev.keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            skipFromButton()
            true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            selectPlaybackSeekbarFromSkipButton()
            true
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            btnSelected = -1
            hideControlsFade()
            syncSkipButtonHighlight()
            true
        }
        else -> false
    }
}
