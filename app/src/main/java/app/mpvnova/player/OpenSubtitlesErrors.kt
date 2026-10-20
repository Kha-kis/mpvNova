package app.mpvnova.player

import java.net.HttpURLConnection
import org.json.JSONException

// Keep only fixed diagnostic categories, never server bodies or exception messages.
@Suppress("SwallowedException")
internal inline fun <T> atOpenSubtitlesStage(stage: OpenSubtitlesFailureStage, action: () -> T): T = try {
    action()
} catch (error: OpenSubtitlesException) {
    if (error.stage != null) throw error
    throw OpenSubtitlesException(error.reason, error.status, stage)
} catch (_: JSONException) {
    throw OpenSubtitlesException(OpenSubtitlesFailureReason.INVALID_RESPONSE, stage = stage)
} catch (_: IllegalArgumentException) {
    throw OpenSubtitlesException(OpenSubtitlesFailureReason.INVALID_RESPONSE, stage = stage)
}

internal fun openSubtitlesSearchFailureMessage(
    failure: OpenSubtitlesException?, signedIn: Boolean, fallback: Int,
): Int = when (failure?.status) {
    HttpURLConnection.HTTP_UNAUTHORIZED -> if (signedIn)
        R.string.os_search_auth_expired else R.string.os_search_auth_required
    HttpURLConnection.HTTP_FORBIDDEN -> R.string.os_search_forbidden
    HttpURLConnection.HTTP_NOT_ACCEPTABLE -> R.string.os_search_quota
    HTTP_RATE_LIMIT -> R.string.os_settings_rate_limit
    else -> openSubtitlesReasonMessage(failure?.reason, signedIn, fallback)
}

private fun openSubtitlesReasonMessage(
    reason: OpenSubtitlesFailureReason?, signedIn: Boolean, fallback: Int,
): Int = when (reason) {
    OpenSubtitlesFailureReason.AUTHENTICATION -> if (signedIn)
        R.string.os_search_auth_expired else R.string.os_search_auth_required
    OpenSubtitlesFailureReason.QUOTA -> R.string.os_search_quota
    OpenSubtitlesFailureReason.RATE_LIMIT -> R.string.os_settings_rate_limit
    OpenSubtitlesFailureReason.CACHE -> R.string.os_search_cache_full
    OpenSubtitlesFailureReason.UNSUPPORTED_SUBTITLE -> R.string.os_search_unsupported_subtitle
    OpenSubtitlesFailureReason.UNSAFE_HOST -> R.string.os_search_download_address
    OpenSubtitlesFailureReason.SIZE_LIMIT -> R.string.os_search_response_too_large
    OpenSubtitlesFailureReason.INVALID_RESPONSE -> R.string.os_search_invalid_response
    OpenSubtitlesFailureReason.NETWORK -> R.string.os_search_network_error
    OpenSubtitlesFailureReason.TIMEOUT -> R.string.os_search_timeout
    else -> fallback
}

private const val HTTP_RATE_LIMIT = 429
