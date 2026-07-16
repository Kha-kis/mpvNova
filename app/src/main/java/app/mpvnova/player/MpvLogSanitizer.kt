package app.mpvnova.player

private val MPV_LOG_URL = Regex("(?i)https?://[^\\s\\\"'<>]+")
private val MPV_LOG_HEADER = Regex(
    "(?i)(authorization|proxy-authorization|cookie|set-cookie):\\s*[^\\r\\n]+",
)
private const val URL_SCHEME_DELIMITER_LENGTH = 3

internal fun sanitizeMpvLogText(text: String): String {
    val redactedHeaders = MPV_LOG_HEADER.replace(text) { match ->
        "${match.groupValues[1]}: <redacted>"
    }
    return MPV_LOG_URL.replace(redactedHeaders) { match -> sanitizeUrl(match.value) }
}

private fun sanitizeUrl(url: String): String {
    val schemeEnd = url.indexOf("://") + URL_SCHEME_DELIMITER_LENGTH
    if (schemeEnd < URL_SCHEME_DELIMITER_LENGTH)
        return "<redacted-url>"
    val authorityEnd = sequenceOf(
        url.indexOf('/', schemeEnd),
        url.indexOf('?', schemeEnd),
        url.indexOf('#', schemeEnd),
    ).filter { it >= 0 }.minOrNull() ?: url.length
    var authority = url.substring(schemeEnd, authorityEnd)
    if ('@' in authority)
        authority = "<redacted>@${authority.substringAfterLast('@')}"
    val hasPathOrParameters = authorityEnd < url.length
    return buildString {
        append(url, 0, schemeEnd)
        append(authority)
        if (hasPathOrParameters)
            append("/<redacted>")
    }
}
