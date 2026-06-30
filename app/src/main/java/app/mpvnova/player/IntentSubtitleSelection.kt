package app.mpvnova.player

internal fun intentSubtitleAddFlag(
    isRequestedSelected: Boolean,
    preferExternalForwardedSubtitles: Boolean,
): String {
    return if (isRequestedSelected && preferExternalForwardedSubtitles) "select" else "auto"
}
