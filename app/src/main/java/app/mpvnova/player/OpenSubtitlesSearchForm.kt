package app.mpvnova.player

import java.io.File
import java.io.IOException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object OpenSubtitlesSearchForm {
    enum class Error { TITLE, EPISODE_PAIR, EPISODE_INVALID }

    data class Parsed(val query: OpenSubtitlesQuery? = null, val error: Error? = null)

    fun parse(title: String, language: String, season: String, episode: String): Parsed {
        val cleanTitle = title.trim()
        val cleanSeason = season.trim()
        val cleanEpisode = episode.trim()
        val seasonNumber = cleanSeason.toIntOrNull()
        val episodeNumber = cleanEpisode.toIntOrNull()
        val seasonValid = seasonNumber != null && seasonNumber >= 0
        val episodeValid = episodeNumber != null && episodeNumber >= 1
        val invalidNumbers = cleanSeason.isNotEmpty() && (!seasonValid || !episodeValid)
        val error = when {
            cleanTitle.isEmpty() -> Error.TITLE
            cleanSeason.isEmpty() != cleanEpisode.isEmpty() -> Error.EPISODE_PAIR
            invalidNumbers -> Error.EPISODE_INVALID
            else -> null
        }
        return if (error != null) Parsed(error = error)
        else Parsed(OpenSubtitlesQuery(cleanTitle, language, seasonNumber, episodeNumber))
    }
}

/** All completions must still belong to the visible dialog's latest operation. */
internal class OpenSubtitlesRequestGate {
    @Volatile private var generation = 0
    @Volatile private var closed = false

    fun next(): Int = ++generation
    fun accepts(ticket: Int): Boolean = !closed && ticket == generation
    fun close() { closed = true; generation++ }
}

internal object OpenSubtitlesDownloadUi {
    fun discard(file: File, cacheDir: File) {
        releaseOpenSubtitlesDownload(file)
        try {
            val root = cacheDir.canonicalPath + File.separator
            if (file.canonicalPath.startsWith(root) && file.isFile) file.delete()
        } catch (_: IOException) {
            // Best effort; never delete outside the download cache or expose a local path.
        } catch (_: SecurityException) {
            // A revoked cache grant must not prevent dismissal.
        }
    }

    fun resetDate(value: String?): Date? {
        if (value == null || !RESET_PATTERN.matches(value)) return null
        val pattern = if (value.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" else "yyyy-MM-dd'T'HH:mm:ss'Z'"
        val parser = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        val parsed = parser.parse(value, position)
        return parsed?.takeIf { position.index == value.length }
    }

    private val RESET_PATTERN = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{3})?Z")
}
