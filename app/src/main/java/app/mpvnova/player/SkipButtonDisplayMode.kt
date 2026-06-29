package app.mpvnova.player

internal const val SKIP_BUTTON_DISPLAY_TEN_SECONDS = 10
internal const val SKIP_BUTTON_DISPLAY_THIRTY_SECONDS = 30
private const val MILLIS_PER_SECOND = 1000L

internal enum class SkipButtonDisplayMode(
    val prefValue: String,
    val autoHideMs: Long?,
) {
    SEGMENT("segment", null),
    TEN_SECONDS("10000", SKIP_BUTTON_DISPLAY_TEN_SECONDS * MILLIS_PER_SECOND),
    THIRTY_SECONDS("30000", SKIP_BUTTON_DISPLAY_THIRTY_SECONDS * MILLIS_PER_SECOND);

    companion object {
        fun fromPref(value: String?): SkipButtonDisplayMode = when (value) {
            TEN_SECONDS.prefValue -> TEN_SECONDS
            THIRTY_SECONDS.prefValue -> THIRTY_SECONDS
            else -> SEGMENT
        }
    }
}
