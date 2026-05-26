package app.mpvnova.player

import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

internal fun MPVActivity.controlsShouldBeVisible(): Boolean {
    return userIsOperatingSeekbar
}

internal fun MPVActivity.shouldAutoHideControls(): Boolean {
    return controlsDisplayTimeoutMs > 0L &&
            !controlsShouldBeVisible() &&
            !(keepControlsVisibleWhilePaused && psc.pause)
}

internal fun MPVActivity.showControls() {
    val controlsWereVisible = binding.controls.visibility == View.VISIBLE
    fadeHandler.removeCallbacks(fadeRunnable)
    resetControlsAlphaIfNeeded(controlsWereVisible)
    if (!controlsWereVisible) {
        performFirstShowSetup()
    }
    updateClockInfo(force = !controlsWereVisible)
    // The deferred dpad-focus update is only needed on the first show, when
    // the controls subtree was just made VISIBLE and the layout pass hasn't
    // run yet. When controls were already visible the focus state is already
    // correct — skipping the post here is critical during fast dpad
    // navigation, where every ACTION_DOWN/UP otherwise scheduled an extra
    // UI-thread runnable that starved the SW Hi10p decoder of cores.
    if (!controlsWereVisible && btnSelected != -1) {
        binding.controls.post {
            if (btnSelected != -1 && binding.controls.visibility == View.VISIBLE) {
                updateSelectedDpadButton()
            }
        }
    }
    if (shouldAutoHideControls())
        fadeHandler.postDelayed(fadeRunnable, controlsDisplayTimeoutMs)
}

private fun MPVActivity.resetControlsAlphaIfNeeded(controlsWereVisible: Boolean) {
    val needReset = !controlsWereVisible ||
        fadeRunnable.hasStarted ||
        binding.controls.alpha < 1f ||
        binding.topControls.alpha < 1f ||
        binding.playerTitleOverlay.alpha < 1f ||
        binding.controlsScrim.alpha < 1f
    if (!needReset) return
    binding.controls.animate().setListener(null).cancel()
    binding.topControls.animate().setListener(null).cancel()
    binding.playerTitleOverlay.animate().setListener(null).cancel()
    binding.controls.alpha = 1f
    binding.topControls.alpha = 1f
    binding.playerTitleOverlay.alpha = 1f
    binding.controlsScrim.alpha = 1f
    fadeRunnable.hasStarted = false
}

private fun MPVActivity.performFirstShowSetup() {
    // Transition: hidden → visible. Pause first if we should, so the
    // decoder gets all available CPU/GPU for the moment of overlay
    // composition (Hi10p SW + alpha overlay over a SurfaceView is the
    // case that drifted before).
    maybeAutoPauseForControlsOverlay()
    binding.controls.setVisibilityIfChanged(View.VISIBLE)
    binding.topControls.setVisibilityIfChanged(View.VISIBLE)
    binding.controlsScrim.setVisibilityIfChanged(View.VISIBLE)
    binding.timeInfoPanel.setVisibilityIfChanged(View.VISIBLE)
    updatePlayerTitleOverlay()
    if (statsFPS) {
        updateStats()
        binding.statsTextView.setVisibilityIfChanged(View.VISIBLE)
    }
    // TV launchers have no system bars to toggle. Calling
    // insetsController.show/hide there is a no-op semantically but still
    // triggers a window-decor update → SurfaceFlinger micro-hitch, which
    // is enough to make a CPU-starved SW Hi10p decoder underrun every
    // time the controls overlay opens. Skip the call on TV.
    if (!isTvUiMode) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.navigationBars())
    }
    updatePlaybackTimeline(psc.position, forceTextUpdate = true)
    updatePlayerToastPlacement()
    clockHandler.removeCallbacks(clockRunnable)
    clockHandler.post(clockRunnable)
}

internal fun MPVActivity.refreshVisibleControlsTimeout() {
    fadeHandler.removeCallbacks(fadeRunnable)
    if (shouldAutoHideControls())
        fadeHandler.postDelayed(fadeRunnable, controlsDisplayTimeoutMs)
}

internal fun MPVActivity.keepVisibleControlsFresh() {
    val controlsAreVisible = binding.controls.visibility == View.VISIBLE
    val controlsAreOpaque =
        binding.controls.alpha >= 1f &&
        binding.topControls.alpha >= 1f &&
        binding.playerTitleOverlay.alpha >= 1f &&
        binding.controlsScrim.alpha >= 1f
    if (controlsAreVisible && controlsAreOpaque && !fadeRunnable.hasStarted) {
        refreshVisibleControlsTimeout()
    } else {
        showControls()
    }
}

internal fun MPVActivity.hideControls() {
    if (controlsShouldBeVisible())
        return
    // No auto-resume on hide — the overlay autopause requires a manual
    // play press to come back. Just clear our flag so the next overlay
    // open starts clean.
    controlsOverlayAutoPaused = false
    if (btnSelected != -1) {
        btnSelected = -1
        updateSelectedDpadButton()
    }
    binding.playbackSeekbar.clearFocus()
    // use GONE here instead of INVISIBLE (which makes more sense) because of Android bug with surface views
    // see http://stackoverflow.com/a/12655713/2606891
    binding.controls.setVisibilityIfChanged(View.GONE)
    binding.topControls.setVisibilityIfChanged(View.GONE)
    binding.playerTitleOverlay.setVisibilityIfChanged(View.GONE)
    binding.controlsScrim.setVisibilityIfChanged(View.GONE)
    binding.timeInfoPanel.setVisibilityIfChanged(View.GONE)
    binding.statsTextView.setVisibilityIfChanged(View.GONE)
    updatePlayerToastPlacement()
    clockHandler.removeCallbacks(clockRunnable)

    // Skip on TV — system bars don't exist there and the call just costs us
    // a window-decor hitch. See showControls() for the full reasoning.
    if (!isTvUiMode) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}

internal fun MPVActivity.hideControlsFade() {
    fadeHandler.removeCallbacks(fadeRunnable)
    // No auto-resume — if the overlay autopaused playback, the user must
    // explicitly press play to resume. See fadeRunnable for full reasoning.
    fadeHandler.post(fadeRunnable)
}

internal fun MPVActivity.toggleControls(): Boolean {
    return if (controlsShouldBeVisible()) {
        true
    } else if (binding.controls.visibility == View.VISIBLE && !fadeRunnable.hasStarted) {
            hideControlsFade()
            false
    } else {
        showControls()
        true
    }
}
