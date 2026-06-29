package app.mpvnova.player

/** How a skippable intro/outro/recap segment is presented to the user. */
internal enum class SkipSegmentsMode {
    /** Ignore segments entirely. */
    OFF,

    /** Seek past the segment automatically. */
    AUTO,

    /** Show a pre-highlighted "Skip" button the user confirms with OK. */
    BUTTON;

    /** The value persisted in SharedPreferences. */
    val prefValue: String
        get() = when (this) {
            OFF -> "off"
            AUTO -> "auto"
            BUTTON -> "button"
        }

    companion object {
        /** Parse the stored pref value; falls back to [AUTO] (the historical default). */
        fun fromPref(value: String?): SkipSegmentsMode = when (value) {
            "off" -> OFF
            "button" -> BUTTON
            else -> AUTO
        }
    }
}
