package app.mpvnova.player

import android.view.View

internal fun MPVActivity.selectedDpadView(controls: List<View>): View? {
    return if (btnSelected == SKIP_BUTTON_SELECTION_INDEX) {
        binding.skipSegmentBtn
    } else {
        controls.getOrNull(btnSelected)
    }
}
