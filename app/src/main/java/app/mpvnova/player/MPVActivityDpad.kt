package app.mpvnova.player

import androidx.appcompat.app.AlertDialog
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible

internal fun MPVActivity.dpadButtons(): List<View> {
    if (binding.controls.visibility != View.VISIBLE || binding.topControls.visibility != View.VISIBLE) {
        dpadControlsScratch.clear()
        return emptyList()
    }
    val views = dpadControlsScratch
    views.clear()
    if (binding.playbackSeekbar.isEnabled) {
        views += binding.playbackSeekbar
    }
    views.addFocusableChildren(binding.controlsButtonGroup)
    views.addFocusableChildren(binding.topControls)
    return views
}

private fun MutableList<View>.addFocusableChildren(group: ViewGroup) {
    for (i in 0 until group.childCount) {
        val view = group.getChildAt(i)
        if (view.isEnabled && view.isVisible && view.isFocusable) {
            this += view
        }
    }
}

internal fun MPVActivity.firstControlButtonIndex(controls: List<View>): Int {
    val firstNonSeekbar = controls.indexOfFirst { it !== binding.playbackSeekbar }
    return if (firstNonSeekbar >= 0) firstNonSeekbar else 0
}

internal fun MPVActivity.firstControlButtonView(): View? {
    findFirstFocusableChild(binding.controlsButtonGroup)?.let { return it }
    return findFirstFocusableChild(binding.topControls)
}

private fun findFirstFocusableChild(group: ViewGroup): View? {
    for (i in 0 until group.childCount) {
        val child = group.getChildAt(i)
        if (child.isEnabled && child.isVisible && child.isFocusable) {
            return child
        }
    }
    return null
}

internal fun MPVActivity.interceptDpad(ev: KeyEvent): Boolean {
    val controls = dpadButtons()
    return when {
        btnSelected == -1 && controls.isEmpty() -> interceptDpadWithoutControls(ev)
        controls.isEmpty() -> false
        btnSelected == -1 -> interceptDpadActivation(ev, controls)
        else -> interceptActiveDpad(ev, controls)
    }
}

internal fun MPVActivity.updateSelectedDpadButton() {
    // The dpad selection model lives entirely on `btnSelected`, not on
    // framework focus — interceptDpad runs at Activity.dispatchKeyEvent
    // before any focus dispatch, and DPAD_CENTER calls performClick()
    // directly. The button backgrounds light up via `state_selected` in
    // their drawable selectors, so isSelected alone is all we need for the
    // visual feedback. We deliberately do NOT call requestFocus() here:
    // every focus change fires AccessibilityManager.sendAccessibilityEvent
    // AND triggers ViewRootImpl.scheduleTraversals() — a full window
    // layout/measure/draw pass on the next frame. At ~10 button presses/sec
    // while scrolling, that's the spike that lets the SW Hi10p decoder
    // fall behind real-time and accumulates the A-V drift the user sees.
    val controls = dpadButtons()
    controls.forEachIndexed { i, child ->
        val selected = i == btnSelected
        if (child.isSelected != selected) {
            child.isSelected = selected
        }
        if (child is ChapterSeekBar) {
            child.setDpadSelected(selected)
        }
    }
}

internal fun MPVActivity.interceptKeyDown(event: KeyEvent): Boolean {
    // intercept some keys to provide functionality native to
    // mpvNova even if libmpv already implements these
    var unhandled = 0

    when (event.unicodeChar.toChar()) {
        // (overrides a default binding)
        'j' -> cycleSub()
        '#' -> cycleAudio()

        else -> unhandled++
    }
    // Note: dpad center is bound according to how Android TV apps should generally behave,
    // see <https://developer.android.com/docs/quality-guidelines/tv-app-quality>.
    // Due to implementation inconsistencies enter and numpad enter need to perform the same
    // function (issue #963).
    when (event.keyCode) {
        // (no default binding)
        KeyEvent.KEYCODE_CAPTIONS -> cycleSub()
        KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> cycleAudio()
        KeyEvent.KEYCODE_INFO -> toggleControls()
        KeyEvent.KEYCODE_MENU -> openTopMenu()
        KeyEvent.KEYCODE_GUIDE -> openTopMenu()
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> player.cyclePause()

        // (overrides a default binding)
        KeyEvent.KEYCODE_ENTER -> player.cyclePause()

        else -> unhandled++
    }

    return unhandled < 2
}

internal fun MPVActivity.onBackPressedImpl() {
    val notYetPlayed = psc.playlistCount - psc.playlistPos - 1
    if (notYetPlayed <= 0 || !playlistExitWarning) {
        finishWithResult(RESULT_OK, true)
        return
    }

    val restore = pauseForDialog()
    with (AlertDialog.Builder(this)) {
        setMessage(getString(R.string.exit_warning_playlist, notYetPlayed))
        setPositiveButton(R.string.dialog_yes) { dialog, _ ->
            dialog.dismiss()
            finishWithResult(RESULT_OK, true)
        }
        setNegativeButton(R.string.dialog_no) { dialog, _ ->
            dialog.dismiss()
            restore()
        }
        create().show()
    }
}
