package app.mpvnova.player

import android.content.Intent

internal const val EXTERNAL_END_BY_USER = "user"
internal const val EXTERNAL_END_BY_PLAYBACK_COMPLETION = "playback_completion"
internal const val VIMU_RESULT_INTENT = "net.gtvbox.videoplayer.result"
internal const val VIMU_RESULT_STOPPED = 0
internal const val VIMU_RESULT_COMPLETED = 1

internal fun MPVActivity.capturePlaybackResultSnapshot(updateCompletion: Boolean = false) {
    resultPositionMs = currentPlaybackPositionForResult().coerceAtLeast(0L)
    resultDurationMs = currentPlaybackDurationForResult().coerceAtLeast(0L)
    if (updateCompletion)
        playbackCompletionReached = isPlaybackCompleteForResult(resultPositionMs, resultDurationMs)
}

internal fun isPlaybackCompleteForResult(positionMs: Long, durationMs: Long): Boolean {
    return durationMs > 0L && positionMs >= durationMs - RESUME_NEAR_END_MS
}

internal fun MPVActivity.buildExternalPlaybackResultIntent(endBy: String): Intent {
    val callerPackage = externalCallerPackage()
    return if (shouldUseVimuResultContract(callerPackage)) {
        buildVimuPlaybackResultIntent()
    } else {
        buildMpvPlaybackResultIntent(endBy)
    }
}

internal fun MPVActivity.externalPlaybackResultCode(defaultCode: Int, includeTimePos: Boolean): Int {
    return externalPlaybackResultCode(
        defaultCode = defaultCode,
        includeTimePos = includeTimePos,
        playbackComplete = playbackCompletionReached,
        vimuResultContract = shouldUseVimuResultContract(externalCallerPackage()),
    )
}

private fun MPVActivity.buildVimuPlaybackResultIntent(): Intent {
    val safePosition = externalResultPosition().coerceAtLeast(0L)
    val safeDuration = externalResultDuration().coerceAtLeast(0L)
    return Intent(VIMU_RESULT_INTENT).apply {
        data = intent.data
        putExtra("position", safePosition.toInt())
        intent.data?.let { putExtra("url", it.toString()) }
        putExtra("duration", safeDuration.toInt())
    }
}

private fun MPVActivity.buildMpvPlaybackResultIntent(endBy: String): Intent {
    val safePosition = externalResultPosition().coerceAtLeast(0L)
    val safeDuration = externalResultDuration().coerceAtLeast(0L)
    return Intent(RESULT_INTENT).apply {
        data = if (intent.data?.scheme == "file") null else intent.data
        putExtra("position", safePosition.toInt())
        putExtra("duration", safeDuration.toInt())
        putExtra("extra_position", safePosition)
        putExtra("extra_duration", safeDuration)
        intent.data?.takeUnless { it.scheme == "file" }?.let {
            putExtra("extra_uri", it.toString())
        }
        putExtra("end_by", endBy)
    }
}

private fun MPVActivity.externalCallerPackage(): String? {
    return intent.getStringExtra(EXTRA_EXTERNAL_CALLER_PACKAGE)
        ?: callingPackage
        ?: callingActivity?.packageName
}

private fun MPVActivity.externalResultPosition(): Long {
    return resultPositionMs.takeIf { it >= 0L } ?: psc.position
}

private fun MPVActivity.externalResultDuration(): Long {
    return resultDurationMs.takeIf { it > 0L } ?: psc.duration
}

private fun MPVActivity.currentPlaybackPositionForResult(): Long {
    return mpvGetPropertyDouble("time-pos/full")
        ?.times(MPV_MILLIS_PER_SECOND_DOUBLE)
        ?.toLong()
        ?: psc.position
}

private fun MPVActivity.currentPlaybackDurationForResult(): Long {
    return mpvGetPropertyDouble("duration/full")
        ?.times(MPV_MILLIS_PER_SECOND_DOUBLE)
        ?.toLong()
        ?: psc.duration
}
