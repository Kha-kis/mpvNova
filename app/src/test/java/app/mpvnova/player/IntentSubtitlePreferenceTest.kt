package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Test

class IntentSubtitlePreferenceTest {
    @Test
    fun selectedForwardedSubtitleIsAutoByDefault() {
        assertEquals(
            "auto",
            intentSubtitleAddFlag(
                isRequestedSelected = true,
                preferExternalForwardedSubtitles = false,
            )
        )
    }

    @Test
    fun selectedForwardedSubtitleIsSelectedWhenExternalIsPreferred() {
        assertEquals(
            "select",
            intentSubtitleAddFlag(
                isRequestedSelected = true,
                preferExternalForwardedSubtitles = true,
            )
        )
    }

    @Test
    fun unselectedForwardedSubtitleIsAuto() {
        assertEquals(
            "auto",
            intentSubtitleAddFlag(
                isRequestedSelected = false,
                preferExternalForwardedSubtitles = true,
            )
        )
    }
}
