package app.mpvnova.player

import android.os.Bundle
import android.util.Log
import org.json.JSONArray
import org.json.JSONException

// Stop skipping a hair before the segment end so playback lands cleanly past it.
private const val SKIP_SEGMENT_END_GUARD_SEC = 1.0

/**
 * Reads the `skip_segments` launch extra (a JSON array of `{type,start,end}`, seconds) set by the
 * launching app and stores it for auto-skipping. Tolerant of missing/garbage data.
 */
internal fun MPVActivity.parseSkipSegments(extras: Bundle?) {
    skipSegments = emptyList()
    autoSkippedSegmentKeys.clear()

    val json = extras?.getString("skip_segments")?.takeIf { it.isNotBlank() } ?: return
    skipSegments = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val start = o.optDouble("start", Double.NaN)
            val end = o.optDouble("end", Double.NaN)
            if (start.isNaN() || end.isNaN() || end <= start) return@mapNotNull null
            SkipSegment(o.optString("type", "intro"), start, end)
        }
    } catch (e: JSONException) {
        Log.w(MPV_ACTIVITY_TAG, "Failed to parse skip_segments", e)
        emptyList()
    }

    if (skipSegments.isNotEmpty())
        Log.d(MPV_ACTIVITY_TAG, "Loaded ${skipSegments.size} skip segment(s)")
}

/**
 * Called on each playback time tick. When playback is inside a segment we haven't handled yet,
 * seek past it once (so seeking back in won't re-trigger) and flash a toast.
 */
internal fun MPVActivity.maybeAutoSkipSegments(posSec: Double) {
    if (!autoSkipSegmentsEnabled || skipSegments.isEmpty()) return
    val seg = skipSegments.firstOrNull { segment ->
        posSec >= segment.start &&
            posSec < segment.end - SKIP_SEGMENT_END_GUARD_SEC &&
            segment.key() !in autoSkippedSegmentKeys
    } ?: return

    autoSkippedSegmentKeys.add(seg.key()) // each segment is auto-skipped only once
    // Exact seek: lands precisely at the segment end (a keyframe seek overshoots into the
    // episode). The latency here is the network re-buffer of the jump, not the decode.
    mpvCommand(arrayOf("seek", seg.end.toString(), "absolute+exact"))
    // Chapter-style toast: segment label as the title, the resume timecode as the detail.
    val detail = getString(R.string.toast_skip_segment_detail, Utils.prettyTime(seg.end.toInt()))
    eventUiHandler.post { showToast(skipSegmentLabel(seg.type), detail, cancel = false) }
    Log.d(MPV_ACTIVITY_TAG, "Auto-skipped ${seg.key()} -> ${seg.end}")
}

private fun SkipSegment.key(): String = "$type:$start"

private fun MPVActivity.skipSegmentLabel(type: String): String = when (type.lowercase()) {
    "recap" -> getString(R.string.skip_segment_recap)
    "ed", "mixed-ed", "outro", "credits", "ending" -> getString(R.string.skip_segment_outro)
    else -> getString(R.string.skip_segment_intro)
}
