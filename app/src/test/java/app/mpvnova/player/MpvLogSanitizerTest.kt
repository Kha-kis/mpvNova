package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvLogSanitizerTest {
    @Test
    fun redactsUrlCredentialsPathAndQuery() {
        val sanitized = sanitizeMpvLogText(
            "Opening https://user:secret@example.com/private/video.m3u8?token=abc",
        )

        assertEquals("Opening https://<redacted>@example.com/<redacted>", sanitized)
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("token"))
    }

    @Test
    fun redactsSensitiveHeaders() {
        val sanitized = sanitizeMpvLogText("Authorization: Bearer abc123")

        assertTrue(sanitized.endsWith("Authorization: <redacted>"))
        assertFalse(sanitized.contains("abc123"))
    }
}
