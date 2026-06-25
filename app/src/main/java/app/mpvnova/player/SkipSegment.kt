package app.mpvnova.player

/** An intro/outro segment to auto-skip. Times are in seconds. */
internal data class SkipSegment(val type: String, val start: Double, val end: Double)
