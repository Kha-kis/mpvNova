package app.mpvnova.player

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

internal fun MPVActivity.prepareDecoderForFileLoad(filepath: String) {
    if (!canPreloadShieldHi10pFallback()) {
        restoreDecoderAfterShieldHi10pPreload()
        return
    }

    val localFile = filepath.toCanonicalLocalFile()?.takeIf { it.isFile && it.canRead() }
    if (localFile?.isH264TenBitVideoFile() != true) {
        restoreDecoderAfterShieldHi10pPreload()
        return
    }

    Log.v(MPV_ACTIVITY_TAG, "shield fallback: preloading software decode for local H.264 Hi10P")
    player.applyShieldHi10pFallback(shieldDecoderFallback)
    shieldHi10pPreloadApplied = true
    updateDecoderButton()
}

internal fun MPVActivity.maybeApplyPreloadedShieldHi10pDisplayMatch() {
    if (!shieldHi10pPreloadApplied ||
        shieldDecoderFallback != MPVView.SHIELD_DECODER_FALLBACK_COPY ||
        !player.isShieldH10pFallbackModeActive()
    ) {
        return
    }
    maybeApplyContentDisplayMode(forceResolutionMatch = true)
}

private fun MPVActivity.canPreloadShieldHi10pFallback(): Boolean =
    autoDecoderFallback &&
        shieldDecoderModeEnabled &&
        sessionDecoderMode == null &&
        isNvidiaShieldDevice()

private fun MPVActivity.restoreDecoderAfterShieldHi10pPreload() {
    if (!shieldHi10pPreloadApplied)
        return
    shieldHi10pPreloadApplied = false

    val mode = sessionDecoderMode ?: preferredDecoderMode.takeIf { !autoDecoderFallback && it.isNotBlank() }
    val blockedShieldMode = mode == MPVView.DECODER_MODE_SHIELD_H10P && !shieldDecoderModeEnabled
    when {
        mode == null || blockedShieldMode -> player.applyDefaultDecoderForFileLoad()
        mode == MPVView.DECODER_MODE_MPV_CONF -> player.applyMpvConfDecoderOptions()
        else -> player.applyDecoderMode(mode)
    }
    updateDecoderButton()
}

private fun MPVView.applyDefaultDecoderForFileLoad() {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val startupDecoderMode = startupPreferredDecoderMode(sharedPreferences)
    if (startupDecoderMode == MPVView.DECODER_MODE_MPV_CONF) {
        applyMpvConfDecoderOptions()
        return
    }

    val vo = startupVo(sharedPreferences, startupDecoderMode)
    vo?.let { applyStandardDecoderTuning(sharedPreferences, it) }
    val hwdec = shieldGpuNextStartupHwdec(vo, startupHwdec(sharedPreferences, startupDecoderMode))
    if (hwdec != null)
        setRuntimeOption("hwdec", hwdec)
}

private fun File.isH264TenBitVideoFile(): Boolean {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(absolutePath)
        (0 until extractor.trackCount).any { index ->
            val format = extractor.getTrackFormat(index)
            format.getString(MediaFormat.KEY_MIME) == H264_MIME &&
                format.csd0Bytes()?.let(::h264CsdIndicatesTenBit) == true
        }
    } catch (e: IOException) {
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: could not inspect local video headers", e)
        false
    } catch (e: IllegalArgumentException) {
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: could not inspect local video headers", e)
        false
    } catch (e: IllegalStateException) {
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: could not inspect local video headers", e)
        false
    } catch (e: SecurityException) {
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: could not inspect local video headers", e)
        false
    } finally {
        extractor.release()
    }
}

private fun MediaFormat.csd0Bytes(): ByteArray? =
    if (containsKey(CSD_0_KEY)) {
        runCatching { getByteBuffer(CSD_0_KEY) }
            .getOrNull()
            ?.toByteArrayFromStart()
    } else {
        null
    }

private fun ByteBuffer.toByteArrayFromStart(): ByteArray {
    val duplicate = duplicate()
    duplicate.position(0)
    val bytes = ByteArray(duplicate.remaining())
    duplicate.get(bytes)
    return bytes
}

private const val H264_MIME = "video/avc"
private const val CSD_0_KEY = "csd-0"
