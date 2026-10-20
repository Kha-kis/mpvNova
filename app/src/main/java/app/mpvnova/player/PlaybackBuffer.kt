package app.mpvnova.player

internal fun bufferedSeekbarProgress(bufferedMs: Long, durationMs: Long, maximum: Int): Int {
    if (durationMs <= 0L || maximum <= 0) return 0
    return (bufferedMs.coerceIn(0L, durationMs).toDouble() / durationMs * maximum).toInt()
        .coerceIn(0, maximum)
}
