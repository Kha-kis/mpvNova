package app.mpvnova.player

import android.view.View

internal fun MPVActivity.shouldShowClockWhileControlsHidden(): Boolean {
    return showClockOnPause && psc.pause
}

internal fun MPVActivity.refreshTimeInfoPanelVisibility() {
    val shouldShow = shouldShowTimeInfoPanel()
    if (shouldShow) {
        binding.timeInfoPanel.animate().cancel()
        binding.timeInfoPanel.alpha = 1f
        updateClockInfo(force = true)
    }
    binding.timeInfoPanel.setVisibilityIfChanged(if (shouldShow) View.VISIBLE else View.GONE)
    clockHandler.removeCallbacks(clockRunnable)
    if (shouldShow)
        clockHandler.post(clockRunnable)
}

private fun MPVActivity.shouldShowTimeInfoPanel(): Boolean {
    return (binding.controls.visibility == View.VISIBLE && showClockOverlay) ||
        shouldShowClockWhileControlsHidden()
}
