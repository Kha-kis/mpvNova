package app.mpvnova.player

// Off = disabled; Dim = fade to a translucent screen and keep the clock visible;
// Logo = the bouncing-logo screensaver. Dim is the default.
internal enum class ScreensaverMode(val pref: String) {
    OFF("off"),
    DIM("dim"),
    LOGO("logo"),
    ;

    companion object {
        fun fromPref(value: String?): ScreensaverMode =
            entries.firstOrNull { it.pref == value } ?: DIM
    }
}
