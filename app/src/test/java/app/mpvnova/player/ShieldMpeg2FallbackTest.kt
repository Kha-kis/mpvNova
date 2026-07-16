package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ShieldMpeg2FallbackTest {
    @Test
    fun codecGuardOnlyRemovesMpeg2WhenEnabled() {
        val configured = "all"
        assertEquals(
            MPV_VIEW_HWDEC_CODECS_WITHOUT_MPEG2,
            shieldMpeg2HwdecCodecs(true, configured),
        )
        assertEquals(configured, shieldMpeg2HwdecCodecs(false, configured))
    }

    @Test
    fun codecGuardPreservesCustomCodecSelection() {
        assertEquals(
            "hevc,vp9",
            shieldMpeg2HwdecCodecs(true, "hevc,mpeg2video,vp9"),
        )
    }

    @Test
    fun codecGuardDisablesHardwareDecodeWhenMpeg2WasTheOnlyCodec() {
        assertEquals("none", shieldMpeg2HwdecCodecs(true, "mpeg2video"))
    }
}
