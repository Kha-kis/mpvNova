package app.mpvnova.player

import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
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

internal fun MPVActivity.maybeApplyContentDisplayMode() {
    if (!autoRefreshRateSwitch && !autoResolutionSwitch) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val targetFps = (mpvGetPropertyDouble("container-fps")
        ?: mpvGetPropertyDouble("estimated-vf-fps")
        ?: 0.0).toFloat()
    val videoW = mpvGetPropertyInt("video-params/w") ?: mpvGetPropertyInt("width") ?: 0
    val videoH = mpvGetPropertyInt("video-params/h") ?: mpvGetPropertyInt("height") ?: 0
    // mpv event thread → UI thread for the display/window calls.
    runOnUiThread { applyBestDisplayMode(targetFps, videoW, videoH) }
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
            attrs.preferredDisplayModeId = 0
            window.attributes = attrs
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Suppress("ReturnCount")
private fun MPVActivity.applyBestDisplayMode(targetFps: Float, videoW: Int, videoH: Int) {
    val display: Display = window.decorView.display ?: return
    val current = display.mode
    val matchRes = autoResolutionSwitch && videoW > 0 && videoH > 0
    val matchFps = autoRefreshRateSwitch && targetFps > 0f
    if (!matchRes && !matchFps) return
    val best = pickBestMode(display, current, targetFps, videoW, videoH, matchRes, matchFps) ?: return
    if (best.modeId == current.modeId) return
    Log.v(
        MPV_ACTIVITY_TAG,
        "display-match: switching to #${best.modeId} " +
            "(${best.physicalWidth}x${best.physicalHeight} @ ${best.refreshRate}Hz) " +
            "for ${videoW}x${videoH} ${targetFps}fps"
    )
    val attrs = window.attributes
    attrs.preferredDisplayModeId = best.modeId
    window.attributes = attrs
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
    if (!matchFps)
        return resModes.maxByOrNull { it.refreshRate }
    val byFps = resModes.minByOrNull { abs(it.refreshRate - targetFps) } ?: return null
    // When also switching resolution, take the closest fps at that resolution even
    // if it's outside the tolerance; for fps-only, keep the tolerance gate so we
    // don't pointlessly switch modes for content the panel already handles.
    return if (matchRes) byFps else byFps.takeIf { abs(it.refreshRate - targetFps) <= REFRESH_RATE_MATCH_TOLERANCE_HZ }
}
