package app.mpvnova.player

import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes

/**
 * Picture-in-picture activity-side glue:
 *
 *   - [onPiPModeChangedImpl] handles enter/exit transitions, including the
 *     Android-12-and-older quirk where PiP exit doesn't fire a clean signal.
 *   - [updatePiPParams] / [buildPiPParams] rebuild the PiP overlay actions
 *     (play/pause + optional prev/next) and pin the aspect ratio.
 *   - [makeRemoteAction] is the RemoteAction factory the PiP buttons use to
 *     fire back into [NotificationButtonReceiver].
 */

internal fun MPVActivity.onPiPModeChangedImpl(state: Boolean) {
    Log.v(MPV_ACTIVITY_TAG, "onPiPModeChanged($state)")
    if (state) {
        hideControls()
        return
    }

    // For whatever stupid reason Android provides no good detection for when PiP is exited
    // so we have to do this shit <https://stackoverflow.com/questions/43174507/#answer-56127742>
    // If we don't exit the activity here it will stick around and not be retrievable from the
    // recents screen, or react to onNewIntent().
    if (activityIsStopped) {
        // Note: On Android 12 or older there's another bug with this: the result will not
        // be delivered to the calling activity and is instead instantly returned the next
        // time, which makes it looks like the file picker is broken.
        finishWithResult(RESULT_OK, true)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun MPVActivity.makeRemoteAction(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    intentAction: String
): RemoteAction {
    val intent = NotificationButtonReceiver.createIntent(this, intentAction)
    return RemoteAction(
        Icon.createWithResource(this, icon),
        getString(title),
        REMOTE_ACTION_EMPTY_TEXT,
        intent
    )
}

internal fun MPVActivity.updatePiPParams(force: Boolean = false) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        return
    if (!isInPictureInPictureMode && !force)
        return

    try {
        setPictureInPictureParams(buildPiPParams())
    } catch (ignored: IllegalArgumentException) {
        // Android has some limits of what the aspect ratio can be
        setPictureInPictureParams(buildPiPParams(Rational(SQUARE_ASPECT_RATIO, SQUARE_ASPECT_RATIO)))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun MPVActivity.buildPiPParams(fallbackAspectRatio: Rational? = null): PictureInPictureParams {
    val playPauseAction = if (psc.pause)
        makeRemoteAction(R.drawable.ic_play_arrow_black_24dp, R.string.btn_play, "PLAY_PAUSE")
    else
        makeRemoteAction(R.drawable.ic_pause_black_24dp, R.string.btn_pause, "PLAY_PAUSE")
    val actions = mutableListOf<RemoteAction>()
    if (psc.playlistCount > 1) {
        actions.add(makeRemoteAction(
            R.drawable.ic_skip_previous_black_24dp, R.string.dialog_prev, "ACTION_PREV"
        ))
        actions.add(playPauseAction)
        actions.add(makeRemoteAction(
            R.drawable.ic_skip_next_black_24dp, R.string.dialog_next, "ACTION_NEXT"
        ))
    } else {
        actions.add(playPauseAction)
    }

    return with(PictureInPictureParams.Builder()) {
        val aspect = fallbackAspectRatio ?: Rational(
            (player.getVideoAspect() ?: 0.0).times(PIP_ASPECT_RATIO_SCALE).toInt(),
            PIP_ASPECT_RATIO_SCALE
        )
        setAspectRatio(aspect)
        setActions(actions)
        build()
    }
}
