package app.mpvnova.player

import android.util.Log
import java.util.Locale
import kotlin.math.roundToInt

internal fun MPVActivity.buildDrcAresampleFilter(): String {
    // Ghidra shows the native player building this exact stage shape:
    //   aresample=<out_rate>:in_chlayout=<in>:out_chlayout=<out>:out_sample_fmt=<fmt>
    // and only then appending :center_mix_level=3.0 when center boost is enabled.
    val outRate = mpvGetPropertyInt("audio-out-params/samplerate")
        ?.takeIf { it > 0 }
        ?: mpvGetPropertyInt("audio-params/samplerate")
            ?.takeIf { it > 0 }
        ?: DEFAULT_AUDIO_SAMPLE_RATE
    val sourceChannels = currentAudioChannelCount()
    val controlledDownmixActive = isDownmixOn() && sourceChannels >= MIN_SURROUND_CHANNELS
    val inputLayout = if (controlledDownmixActive) {
        "stereo"
    } else {
        mpvGetPropertyString("audio-params/channels")
            ?.takeIf { it.isNotBlank() }
    }
    val outputLayout = when {
        controlledDownmixActive -> "stereo"
        else -> mpvGetPropertyString("audio-out-params/channels")
            ?.takeIf { it.isNotBlank() }
            ?: inputLayout
            ?: "stereo"
    }
    val outputFormat = mapMpvAudioFormatToFfmpeg(
        mpvGetPropertyString("audio-out-params/format")
            ?: mpvGetPropertyString("audio-params/format")
    ) ?: "flt"

    val options = mutableListOf("$outRate")
    inputLayout?.let { options += "in_chlayout=$it" }
    options += "out_chlayout=$outputLayout"
    options += "out_sample_fmt=$outputFormat"
    Log.i(
        MPV_ACTIVITY_TAG,
        if (controlledDownmixActive)
            "DRC using controlled Channel Downmix output: ${sourceChannels}ch -> stereo"
        else
            "DRC active without forced center downmix: ${sourceChannels}ch source"
    )
    return "aresample=${options.joinToString(":")}"
}

internal fun MPVActivity.drcVolumeMultiplier(): String {
    // The native player stores integer gain percentages and its transcoder converts
    // them to a linear multiplier via percent/100.
    val percent = (Math.pow(DB_TO_LINEAR_BASE, volumeBoostDb / DB_POWER_DIVISOR) *
        PERCENT_SCALE_DOUBLE).roundToInt()
    val rounded = percent / PERCENT_SCALE_DOUBLE
    return if (rounded == rounded.toInt().toDouble()) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.3f", rounded)
            .trimEnd('0')
            .trimEnd('.')
    }
}

internal fun MPVActivity.volumeBoostFilter(): String {
    val dynamicsAlreadyManaged = isAudioNormOn() || isNightModeOn()
    if (dynamicsAlreadyManaged) {
        val limit = when {
            volumeBoostDb <= BOOST_LIMIT_LIGHT_DB -> "0.97"
            volumeBoostDb <= BOOST_LIMIT_MODERATE_DB -> "0.95"
            volumeBoostDb <= BOOST_LIMIT_STRONG_DB -> "0.93"
            volumeBoostDb <= BOOST_LIMIT_HIGH_DB -> "0.91"
            else -> "0.89"
        }
        return "$volumeBoostFilterLabel:lavfi=[" +
            "volume=${volumeBoostDb}dB," +
            "alimiter=limit=$limit:attack=2:release=20]"
    }

    val settings = when {
        volumeBoostDb <= BOOST_LIMIT_LIGHT_DB -> Triple("-19dB", "1.6", "0.95")
        volumeBoostDb <= BOOST_LIMIT_MODERATE_DB -> Triple("-20dB", "1.9", "0.93")
        volumeBoostDb <= BOOST_LIMIT_STRONG_DB -> Triple("-21dB", "2.2", "0.91")
        volumeBoostDb <= BOOST_LIMIT_HIGH_DB -> Triple("-22dB", "2.5", "0.89")
        else -> Triple("-23dB", "2.8", "0.87")
    }
    return "$volumeBoostFilterLabel:lavfi=[" +
        "acompressor=threshold=${settings.first}:ratio=${settings.second}:attack=6:" +
        "release=90:knee=3.0:link=average:detection=rms:makeup=1.02," +
        "volume=${volumeBoostDb}dB," +
        "alimiter=limit=${settings.third}:attack=2:release=20]"
}

internal fun MPVActivity.buildDrcAudioStageFilter(): String {
    val stageFilters = mutableListOf<String>()
    if (isVolumeBoostOn())
        stageFilters += "volume=${drcVolumeMultiplier()}"
    stageFilters += drcFilterBody.trimEnd(',')
    stageFilters += buildDrcAresampleFilter()
    return "$drcAudioStageFilterLabel:lavfi=[${stageFilters.joinToString(",")}]"
}

internal fun MPVActivity.getVoiceBoostLabel(): String = getString(voiceBoostPresetLabelIds[voiceBoostLevel])

internal fun MPVActivity.getDownmixBaseLabel(): String = getString(downmixPresetLabelIds[downmixLevel])

internal fun MPVActivity.getDownmixLabel(): String {
    if (!isDownmixOn()) {
        return getString(R.string.filter_value_off)
    }
    val channels = currentAudioChannelCount()
    return if (channels >= MIN_SURROUND_CHANNELS) {
        getString(R.string.format_downmix_active, getDownmixBaseLabel(), channels)
    } else if (channels <= STEREO_CHANNEL_COUNT) {
        getString(R.string.format_downmix_stereo, getDownmixBaseLabel())
    } else {
        getString(R.string.format_downmix_multichannel, getDownmixBaseLabel(), channels)
    }
}

internal fun MPVActivity.getVolumeBoostLabel(): String {
    return if (volumeBoostDb > 0) {
        getString(R.string.format_db, volumeBoostDb)
    } else {
        getString(R.string.filter_value_off)
    }
}
