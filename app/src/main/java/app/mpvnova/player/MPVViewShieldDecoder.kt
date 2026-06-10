package app.mpvnova.player

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

// Shield MediaCodec can't decode 10-bit H.264. Two fallback flavors:
//   DEFAULT: the standard G-NEXT path (gpu-next + mediacodec-copy) with zero
//     tweaks — lavc software-decodes when the codec rejects the stream.
//   COPY ("light tuning"): same path plus skiploopfilter=nonref, a 1 s audio
//     buffer, an EWA Lanczos-sharp upscale, and a forced full display match.
internal fun MPVView.applyShieldHi10pFallback(fallback: String) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    applyShieldHi10pFallback(sharedPreferences, fallback)
}

internal fun MPVView.applyShieldHi10pFallback(sharedPreferences: SharedPreferences) {
    applyShieldHi10pFallback(
        sharedPreferences,
        sharedPreferences.getString(
            "shield_decoder_fallback",
            MPVView.SHIELD_DECODER_FALLBACK_DEFAULT
        ).toShieldDecoderFallback()
    )
}

private fun MPVView.applyShieldHi10pFallback(sharedPreferences: SharedPreferences, fallback: String) {
    when (fallback.toShieldDecoderFallback()) {
        MPVView.SHIELD_DECODER_FALLBACK_COPY -> applyShieldHi10pCopyFallback(sharedPreferences)
        else -> applyShieldHi10pDefaultFallback(sharedPreferences)
    }
}

// Strictly stock: the standard G-NEXT path with zero extra tweaks. MediaCodec
// rejects Hi10P so lavc software-decodes, exactly as a plain G-NEXT session would.
private fun MPVView.applyShieldHi10pDefaultFallback(sharedPreferences: SharedPreferences) {
    applyStandardDecoderTuning(sharedPreferences, MPV_VIEW_VO_GPU_NEXT)
    setRuntimeOption("hwdec", MPV_VIEW_HWDEC_MEDIACODEC_COPY)
}

private fun MPVView.applyShieldHi10pCopyFallback(sharedPreferences: SharedPreferences) {
    applyStandardDecoderTuning(sharedPreferences, MPV_VIEW_VO_GPU_NEXT)
    setRuntimeOption("hwdec", MPV_VIEW_HWDEC_MEDIACODEC_COPY)
    setRuntimeOption("vd-lavc-skiploopfilter", "nonref")
    setRuntimeOption("audio-buffer", "1.0")
    // Sharp upscale for whatever scaling remains after the resolution match
    // (the activity side forces the display to the video's resolution).
    setRuntimeOption("scale", "ewa_lanczossharp")
}

// The Copy flavor's vo/hwdec are indistinguishable from plain G-NEXT — its
// Shield-only tunings (nonref loopfilter + 1s audio buffer) are the tell,
// so the picker can highlight Shield Anime instead of G-NEXT when active.
internal fun MPVView.isShieldH10pCopyModeActive(): Boolean {
    return isNvidiaShieldDevice() &&
        matchesShieldOption("vo", MPV_VIEW_VO_GPU_NEXT) &&
        matchesShieldOption("hwdec", MPV_VIEW_HWDEC_MEDIACODEC_COPY) &&
        matchesShieldOption("vd-lavc-skiploopfilter", "nonref") &&
        matchesShieldOption("audio-buffer", "1.0", "1.000000", "1")
}

private fun MPVView.matchesShieldOption(name: String, vararg expected: String): Boolean {
    val value = getOptionString(name).trim().lowercase()
    return expected.any { value == it.lowercase() }
}

// Unknown values (including the removed legacy "g_next_sw") map to DEFAULT.
internal fun String?.toShieldDecoderFallback(): String {
    return when (this) {
        MPVView.SHIELD_DECODER_FALLBACK_COPY -> MPVView.SHIELD_DECODER_FALLBACK_COPY
        else -> MPVView.SHIELD_DECODER_FALLBACK_DEFAULT
    }
}

// Shield's gpu-next direct (aimagereader) path never works — only copy does.
// Skip the doomed direct-first auto chain at startup so the render fallback
// doesn't have to rescue (and toast) once per session. Anything other than
// Shield + gpu-next + the default auto chain passes through untouched.
internal fun shieldGpuNextStartupHwdec(vo: String?, hwdec: String?): String? {
    val applies = isNvidiaShieldDevice() &&
        vo?.startsWith(MPV_VIEW_VO_GPU_NEXT) == true &&
        hwdec == MPV_VIEW_HWDECS
    return if (applies) MPV_VIEW_HWDEC_MEDIACODEC_COPY else hwdec
}
