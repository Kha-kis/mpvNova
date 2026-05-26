package app.mpvnova.player

import android.view.KeyEvent
import android.view.View

internal fun MPVActivity.interceptDpadWithoutControls(ev: KeyEvent): Boolean {
    return when (ev.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
            if (ev.action == KeyEvent.ACTION_DOWN) {
                showControls()
                val controls = dpadButtons()
                if (controls.isNotEmpty()) {
                    activateDpadSelection(ev, controls)
                    requestFirstControlFocusIfNeeded()
                }
            }
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
            when (ev.action) {
                KeyEvent.ACTION_DOWN -> {
                    showControls()
                    btnSelected = 0
                    // updateSelectedDpadButton highlights the seekbar via
                    // ChapterSeekBar.setDpadSelected — no framework
                    // requestFocus needed (which would trigger a full window
                    // traversal that starves the SW decoder during dpad
                    // seeking on Hi10p).
                    updateSelectedDpadButton()
                    seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
                }
                KeyEvent.ACTION_UP -> commitPendingSeekbarSeek()
            }
            true
        }
        else -> false
    }
}

internal fun MPVActivity.activateDpadSelection(ev: KeyEvent, controls: List<View>) {
    btnSelected = if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) firstControlButtonIndex(controls) else 0
    updateSelectedDpadButton()
}

internal fun MPVActivity.requestFirstControlFocusIfNeeded() {
    // Was: requestFocus on the first control button when controls open via
    // DPAD_DOWN. Now: the visual "selected" state is driven entirely by
    // updateSelectedDpadButton → isSelected → state_selected in the
    // button drawable. The framework focus pointer can stay wherever it
    // was; we never need to move it during player UI navigation, and
    // skipping these requestFocus calls eliminates a window-wide layout
    // traversal at every controls-open. We still post a deferred selection
    // refresh so the highlight settles after the layout pass that the
    // visibility change triggers.
    binding.controls.post {
        if (btnSelected != -1 && binding.controls.visibility == View.VISIBLE) {
            updateSelectedDpadButton()
        }
    }
}

internal fun MPVActivity.interceptDpadActivation(ev: KeyEvent, controls: List<View>): Boolean {
    if (ev.keyCode != KeyEvent.KEYCODE_DPAD_UP && ev.keyCode != KeyEvent.KEYCODE_DPAD_DOWN)
        return false
    if (ev.action == KeyEvent.ACTION_DOWN) {
        activateDpadSelection(ev, controls)
        requestFirstControlFocusIfNeeded()
        showControls()
    }
    return true
}

internal fun MPVActivity.interceptActiveDpad(ev: KeyEvent, controls: List<View>): Boolean {
    val selectedView = controls.getOrNull(btnSelected)
    val seekbarSelected = selectedView === binding.playbackSeekbar
    return when (ev.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN ->
            handleVerticalDpad(ev, seekbarSelected, controls)
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT ->
            handleHorizontalDpad(ev, seekbarSelected, controls)
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
            handleCenterDpad(ev, seekbarSelected, controls)
        else -> false
    }
}

internal fun MPVActivity.handleVerticalDpad(
    ev: KeyEvent,
    seekbarSelected: Boolean,
    controls: List<View>
): Boolean {
    if (ev.action == KeyEvent.ACTION_DOWN) {
        if (seekbarSelected)
            commitPendingSeekbarSeek()
        when {
            ev.keyCode == KeyEvent.KEYCODE_DPAD_UP && !seekbarSelected -> btnSelected = 0
            ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && seekbarSelected && controls.size > 1 -> btnSelected = 1
            else -> btnSelected = -1
        }
        updateSelectedDpadButton()
        if (btnSelected == -1) hideControlsFade() else showControls()
    }
    return true
}

internal fun MPVActivity.handleHorizontalDpad(
    ev: KeyEvent,
    seekbarSelected: Boolean,
    controls: List<View>
): Boolean {
    when (ev.action) {
        KeyEvent.ACTION_DOWN -> {
            if (seekbarSelected) {
                seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
                keepVisibleControlsFresh()
            } else {
                val direction = if (ev.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                val count = controls.size
                btnSelected = (count + btnSelected + direction) % count
                updateSelectedDpadButton()
                // Controls are already visible here (we're navigating their
                // buttons), so showControls() would only refresh the auto-hide
                // timer. Skip the rest of the function and just bump the
                // timer directly — this avoids re-running the alpha checks,
                // updateClockInfo, and the deferred binding.controls.post for
                // dpad selection that each showControls() call schedules.
                // At ~10 presses/sec while scrolling buttons, that work
                // adds up to enough UI-thread CPU to chronically starve
                // the SW Hi10p decoder.
                refreshVisibleControlsTimeout()
            }
        }
        KeyEvent.ACTION_UP -> {
            if (seekbarSelected) {
                commitPendingSeekbarSeek()
                keepVisibleControlsFresh()
            } else {
                // ACTION_UP follows the matching ACTION_DOWN with no state
                // change in between — the selection and visibility are
                // already correct, just refresh the auto-hide timer.
                refreshVisibleControlsTimeout()
            }
        }
    }
    return true
}

internal fun MPVActivity.handleCenterDpad(
    ev: KeyEvent,
    seekbarSelected: Boolean,
    controls: List<View>
): Boolean {
    if (seekbarSelected)
        return false

    return when (ev.action) {
        KeyEvent.ACTION_DOWN -> {
            if (ev.repeatCount == 0)
                scheduleDpadLongClick(controls.getOrNull(btnSelected))
            showControls()
            true
        }
        KeyEvent.ACTION_UP -> {
            val view = controls.getOrNull(btnSelected)
            cancelPendingDpadLongClick()
            if (!dpadLongClickPerformed)
                view?.performClick()
            dpadLongClickPerformed = false
            showControls()
            true
        }
        else -> true
    }
}

private fun MPVActivity.scheduleDpadLongClick(view: View?) {
    cancelPendingDpadLongClick()
    dpadLongClickPerformed = false
    if (view == null || !view.isLongClickable)
        return

    val runnable = Runnable {
        if (pendingDpadLongClickView === view && view.performLongClick()) {
            dpadLongClickPerformed = true
            showControls()
        }
        pendingDpadLongClickView = null
        pendingDpadLongClickRunnable = null
    }
    pendingDpadLongClickView = view
    pendingDpadLongClickRunnable = runnable
    view.postDelayed(runnable, DPAD_LONG_PRESS_MS)
}

private fun MPVActivity.cancelPendingDpadLongClick() {
    val view = pendingDpadLongClickView
    val runnable = pendingDpadLongClickRunnable
    if (view != null && runnable != null)
        view.removeCallbacks(runnable)
    pendingDpadLongClickView = null
    pendingDpadLongClickRunnable = null
}
