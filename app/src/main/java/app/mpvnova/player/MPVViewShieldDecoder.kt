package app.mpvnova.player

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

internal fun MPVView.applyShieldHi10pFallback(fallback: String) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    applyShieldHi10pFallback(sharedPreferences, fallback)
}

internal fun MPVView.applyShieldHi10pFallback(sharedPreferences: SharedPreferences) {
    applyShieldHi10pFallback(
        sharedPreferences,
        sharedPreferences.getString(
            "shield_decoder_fallback",
            MPVView.SHIELD_DECODER_FALLBACK_COPY
        ).toShieldDecoderFallback()
    )
}

private fun MPVView.applyShieldHi10pFallback(sharedPreferences: SharedPreferences, fallback: String) {
    when (fallback.toShieldDecoderFallback()) {
        MPVView.SHIELD_DECODER_FALLBACK_SW -> applyShieldHi10pSoftwareFallback()
        else -> {
            applyStandardDecoderTuning(sharedPreferences, "gpu-next")
            setRuntimeOption("hwdec", "mediacodec-copy")
        }
    }
}

private fun MPVView.applyShieldHi10pSoftwareFallback() {
    setRuntimeVo("gpu-next")
    setRuntimeOption("hwdec", "no")
    setRuntimeOption("vd-lavc-threads", "6")
    setRuntimeOption("vd-lavc-fast", "yes")
    setRuntimeOption("vd-lavc-skiploopfilter", "nonref")
    setRuntimeOption("framedrop", "vo")
    setRuntimeOption("gpu-api", "opengl")
    setRuntimeOption("video-sync", "display-resample")
    setRuntimeOption("cache", "no")
    setRuntimeOption("demuxer-max-bytes", MPV_VIEW_SHIELD_H10P_DEMUXER_BYTES.toString())
    setRuntimeOption("demuxer-max-back-bytes", MPV_VIEW_SHIELD_H10P_DEMUXER_BYTES.toString())
}

internal fun MPVView.isShieldH10pSoftwareModeActive(): Boolean {
    return matchesShieldOption("vd-lavc-threads", "6") &&
        matchesShieldOption("vd-lavc-fast", "yes") &&
        matchesShieldOption("vd-lavc-skiploopfilter", "nonref") &&
        matchesShieldOption("framedrop", "vo") &&
        matchesShieldOption("gpu-api", "opengl") &&
        matchesShieldOption("video-sync", "display-resample") &&
        matchesShieldOption("cache", "no") &&
        matchesShieldOption("demuxer-max-bytes", MPV_VIEW_SHIELD_H10P_DEMUXER_BYTES.toString(), "50mib")
}

private fun MPVView.matchesShieldOption(name: String, vararg expected: String): Boolean {
    val value = getOptionString(name).trim().lowercase()
    return expected.any { value == it.lowercase() }
}

internal fun String?.toShieldDecoderFallback(): String {
    return when (this) {
        MPVView.SHIELD_DECODER_FALLBACK_SW -> MPVView.SHIELD_DECODER_FALLBACK_SW
        else -> MPVView.SHIELD_DECODER_FALLBACK_COPY
    }
}
