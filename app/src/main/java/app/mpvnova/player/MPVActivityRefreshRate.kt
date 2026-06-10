package app.mpvnova.player

import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.View
import androidx.annotation.RequiresApi
import java.util.Locale
import kotlin.math.abs

/**
 * Match the display mode to the playing video on Android TV: refresh rate (to
 * kill 3:2 pulldown judder), resolution (so the TV scales instead of the GPU),
 * or both. Driven by two independent toggles. Reverts to the system default when
 * the player window goes away.
 */

// Generous so 23.976 content folds onto a 23.97602… mode, tight enough
// that 30fps content doesn't fold onto a 60Hz mode.
private const val REFRESH_RATE_MATCH_TOLERANCE_HZ = 0.5f

/** The force flags override the user toggles for decoder fallback tuning. */
internal fun MPVActivity.maybeApplyContentDisplayMode(
    forceResolutionMatch: Boolean = false,
    forceRefreshMatch: Boolean = false,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val forced = forceResolutionMatch || forceRefreshMatch
    // A forced match owns the mode for the rest of the file — the plain
    // toggle pass must not regress it (e.g. res-only re-picking a 120 Hz mode).
    val skipPlainPass = !forced && displayModeForcedByFallback
    if (skipPlainPass || displayMatchDisabled(forced)) return
    // A live match takes ownership of the mode from any earlier forced one.
    displayModeNeedsRevert = false
    val targetFps = (mpvGetPropertyDouble("container-fps")
        ?: mpvGetPropertyDouble("estimated-vf-fps")
        ?: 0.0).toFloat()
    val videoW = mpvGetPropertyInt("video-params/w") ?: mpvGetPropertyInt("width") ?: 0
    val videoH = mpvGetPropertyInt("video-params/h") ?: mpvGetPropertyInt("height") ?: 0
    // mpv event thread → UI thread for the display/window calls.
    runOnUiThread {
        applyBestDisplayMode(targetFps, videoW, videoH, forceResolutionMatch, forceRefreshMatch)
    }
}

/** When matching is fully off, also undoes a mode the previous file's Hi10P
 *  fallback forced — unless this file is Hi10P too (the fallback is about to
 *  retarget the same mode; reverting first would blank HDMI twice). */
private fun MPVActivity.displayMatchDisabled(force: Boolean): Boolean {
    val matchingEnabled = autoRefreshRateSwitch || autoResolutionSwitch || force
    if (!matchingEnabled && displayModeNeedsRevert && !currentFileWillForceDisplayMode()) {
        displayModeNeedsRevert = false
        clearContentRefreshRate()
    }
    return !matchingEnabled
}

private fun MPVActivity.currentFileWillForceDisplayMode(): Boolean {
    return autoDecoderFallback &&
        shieldDecoderModeEnabled &&
        shieldDecoderFallback == MPVView.SHIELD_DECODER_FALLBACK_COPY &&
        isNvidiaShieldDevice() &&
        player.isHi10pH264Video()
}

/** Apply matching if either toggle is on, otherwise revert to the default mode. */
internal fun MPVActivity.applyOrClearDisplayMatch() {
    if (autoRefreshRateSwitch || autoResolutionSwitch)
        maybeApplyContentDisplayMode()
    else
        clearContentRefreshRate()
}

/** Reset to the system default mode (toggle-off path). */
internal fun MPVActivity.clearContentRefreshRate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    runOnUiThread {
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId != 0) {
            Log.v(MPV_ACTIVITY_TAG, "display-match: clearing preferredDisplayModeId")
            showDisplayModeSwitchCover()
            attrs.preferredDisplayModeId = 0
            window.attributes = attrs
            // Reverting re-syncs HDMI too — same becoming-noisy suppression.
            lastDisplayModeSwitchMs = SystemClock.uptimeMillis()
            displayModeForcedByFallback = false
            displayModeNeedsRevert = false
            refreshDrawerRowsIfVisible(DrawerTab.VIDEO)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Suppress("ReturnCount")
private fun MPVActivity.applyBestDisplayMode(
    targetFps: Float,
    videoW: Int,
    videoH: Int,
    forceResolutionMatch: Boolean = false,
    forceRefreshMatch: Boolean = false,
) {
    val display: Display = window.decorView.display ?: return
    val current = display.mode
    val matchRes = (autoResolutionSwitch || forceResolutionMatch) && videoW > 0 && videoH > 0
    val matchFps = (autoRefreshRateSwitch || forceRefreshMatch) && targetFps > 0f
    if (!matchRes && !matchFps) return
    if (forceResolutionMatch || forceRefreshMatch) {
        displayModeForcedByFallback = true
        refreshDrawerRowsIfVisible(DrawerTab.VIDEO)
    }
    val best = pickBestMode(display, current, targetFps, videoW, videoH, matchRes, matchFps) ?: return
    if (best.modeId == current.modeId) return
    Log.v(
        MPV_ACTIVITY_TAG,
        "display-match: switching to #${best.modeId} " +
            "(${best.physicalWidth}x${best.physicalHeight} @ ${best.refreshRate}Hz) " +
            "for ${videoW}x${videoH} ${targetFps}fps"
    )
    showDisplayModeSwitchCover()
    prepareVideoOutputForModeSwitch()
    val attrs = window.attributes
    attrs.preferredDisplayModeId = best.modeId
    window.attributes = attrs
    lastDisplayModeSwitchMs = SystemClock.uptimeMillis()
    showToast(
        getString(R.string.toast_display_mode_title),
        getString(
            R.string.toast_display_mode_detail,
            best.physicalWidth,
            best.physicalHeight,
            formatRefreshRateHz(best.refreshRate),
        ),
        durationMs = DISPLAY_MODE_TOAST_MS,
    )
}

/** 23.976025 → "23.976", 120.00001 → "120", 59.94006 → "59.94" */
internal fun formatRefreshRateHz(hz: Float): String =
    String.format(Locale.US, "%.3f", hz).trimEnd('0').trimEnd('.')

/**
 * Shield's gpu-next direct (aimagereader) path doesn't work at all — only
 * mediacodec-copy does; other devices handle direct fine. When a mode switch
 * is about to blank the screen anyway, move the Shield to copy up front so
 * the render fallback doesn't have to rescue dead frames after the switch.
 */
private fun MPVActivity.prepareVideoOutputForModeSwitch() {
    if (!isNvidiaShieldDevice())
        return
    val requestedVo = player.requestedVideoOutput.trim().lowercase(Locale.US)
    val activeHwdec = player.hwdecActive.trim().lowercase(Locale.US)
    if (!requestedVo.startsWith(MPV_VIEW_VO_GPU_NEXT) || activeHwdec != MPV_VIEW_HWDEC_MEDIACODEC)
        return
    Log.v(MPV_ACTIVITY_TAG, "display-match: moving gpu-next to mediacodec-copy for the mode change")
    player.fallbackGpuNextToCopyHwdec()
    updateDecoderButton()
}

internal fun MPVActivity.showDisplayModeSwitchCoverForExit() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        return
    if (window.attributes.preferredDisplayModeId != 0)
        showDisplayModeSwitchCover(autoHide = false)
}

private fun MPVActivity.showDisplayModeSwitchCover(autoHide: Boolean = true) {
    fadeHandler.removeCallbacks(displayModeSwitchCoverHideRunnable)
    binding.displayModeSwitchCover.animate().cancel()
    binding.displayModeSwitchCover.alpha = 1f
    binding.displayModeSwitchCover.visibility = View.VISIBLE
    if (autoHide)
        fadeHandler.postDelayed(displayModeSwitchCoverHideRunnable, DISPLAY_MODE_SWITCH_COVER_MS)
}

@RequiresApi(Build.VERSION_CODES.M)
@Suppress("ReturnCount")
private fun pickBestMode(
    display: Display,
    current: Display.Mode,
    targetFps: Float,
    videoW: Int,
    videoH: Int,
    matchRes: Boolean,
    matchFps: Boolean,
): Display.Mode? {
    val modes = display.supportedModes
    // Pick the resolution to live at: the supported one closest to the video when
    // matching resolution, otherwise whatever the display is already at.
    val resModes = if (matchRes) {
        val target = modes.minByOrNull {
            abs(it.physicalWidth - videoW) + abs(it.physicalHeight - videoH)
        } ?: return null
        modes.filter { it.physicalWidth == target.physicalWidth && it.physicalHeight == target.physicalHeight }
    } else {
        modes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
    }
    if (resModes.isEmpty())
        return null
    // Res-only match: keep the refresh closest to the current one. Changing
    // both axes at once (e.g. 2160p59.94 → 1080p120) forces the harshest HDMI
    // re-negotiation; 1080p at the same refresh re-syncs much more gently.
    if (!matchFps)
        return resModes.minByOrNull { abs(it.refreshRate - current.refreshRate) }
    val byFps = resModes.minByOrNull { abs(it.refreshRate - targetFps) } ?: return null
    // When also switching resolution, take the closest fps at that resolution even
    // if it's outside the tolerance; for fps-only, keep the tolerance gate so we
    // don't pointlessly switch modes for content the panel already handles.
    return if (matchRes) byFps else byFps.takeIf { abs(it.refreshRate - targetFps) <= REFRESH_RATE_MATCH_TOLERANCE_HZ }
}
