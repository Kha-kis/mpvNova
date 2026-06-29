package app.mpvnova.player

import android.view.KeyEvent
import android.view.View

internal fun MPVActivity.skipButtonVerticalTarget(
    ev: KeyEvent,
    controls: List<View>,
    current: View?,
    seekbarSelected: Boolean,
): Int? {
    val isUp = ev.keyCode == KeyEvent.KEYCODE_DPAD_UP
    var target: Int? = null
    if (current === binding.skipSegmentBtn) {
        target = if (isUp) {
            -1
        } else {
            controls.indexOf(binding.playbackSeekbar).takeIf { it >= 0 }
                ?: firstControlButtonIndex(controls)
        }
    } else if (seekbarSelected && isUp && skipButtonVisible) {
        target = SKIP_BUTTON_SELECTION_INDEX
    }
    return target
}
