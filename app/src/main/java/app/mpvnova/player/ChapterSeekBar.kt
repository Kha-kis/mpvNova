package app.mpvnova.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat

/**
 * A SeekBar that draws small tick marks at chapter boundaries on the progress track.
 *
 * Call [setChapters] whenever the chapter list or media duration changes.
 * Chapter times at t=0 are skipped (no marker at the very start of the track).
 */
class ChapterSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyle) {

    // Chapter positions as fractions of duration, in [0, 1], excluding 0.0
    private var chapterFractions: FloatArray = FloatArray(0)
    private var dpadSelected = false

    private val markerPaint = Paint().apply {
        color = MARKER_COLOR
        style = Paint.Style.FILL
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppearanceTheme.resolveColor(
            context,
            R.attr.mpvAccentHot,
            ContextCompat.getColor(context, R.color.tv_purple_hot)
        )
        style = Paint.Style.STROKE
    }

    private val density: Float get() = resources.displayMetrics.density
    private val trackHeightPx = TRACK_HEIGHT_DP * density
    private val selectionStrokePx = SELECTION_STROKE_DP * density
    private val selectionInsetPx = SELECTION_INSET_DP * density
    private val selectionCornerRadiusPx = SELECTION_CORNER_RADIUS_DP * density
    private val markerWidthPx = MARKER_WIDTH_DP * density
    private val markerHeightPx = MARKER_HEIGHT_DP * density

    /**
     * Update the chapter markers drawn on the track.
     *
     * @param chapterTimes  list of chapter start times in seconds
     * @param duration      total media duration in seconds (> 0)
     */
    fun setChapters(chapterTimes: List<Double>, duration: Double) {
        if (duration <= 0.0 || chapterTimes.isEmpty()) {
            updateChapterFractions(EMPTY_CHAPTER_FRACTIONS)
            return
        }

        val fractions = FloatArray(chapterTimes.size)
        var count = 0
        for (time in chapterTimes) {
            if (time > EDGE_CHAPTER_SKIP_SECONDS && time < duration - EDGE_CHAPTER_SKIP_SECONDS) {
                fractions[count] = (time / duration).toFloat()
                count++
            }
        }
        updateChapterFractions(if (count == 0) EMPTY_CHAPTER_FRACTIONS else fractions.copyOf(count))
    }

    /** Remove all chapter markers (e.g. when a new file is loaded). */
    fun clearChapters() {
        updateChapterFractions(EMPTY_CHAPTER_FRACTIONS)
    }

    fun setDpadSelected(selected: Boolean) {
        if (dpadSelected == selected) return
        dpadSelected = selected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Track spans from paddingLeft to (width - paddingRight).
        // AppCompatSeekBar pads the view by thumbOffset so the thumb isn't clipped.
        val trackLeft  = paddingLeft.toFloat()
        val trackRight = (width - paddingRight).toFloat()
        val trackSpan  = trackRight - trackLeft
        if (trackSpan <= 0f) return

        val centerY     = height / 2f
        val trackHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            maxOf(maxHeight.toFloat(), trackHeightPx)
        else
            trackHeightPx
        val trackHalfH  = trackHeight / 2f
        if (dpadSelected) {
            selectionPaint.strokeWidth = selectionStrokePx
            canvas.drawRoundRect(
                trackLeft - selectionInsetPx,
                centerY - trackHalfH - selectionInsetPx,
                trackRight + selectionInsetPx,
                centerY + trackHalfH + selectionInsetPx,
                selectionCornerRadiusPx,
                selectionCornerRadiusPx,
                selectionPaint
            )
        }

        if (chapterFractions.isEmpty()) return

        val halfW       = markerWidthPx / 2f
        val halfH       = markerHeightPx / 2f

        for (fraction in chapterFractions) {
            val cx = trackLeft + fraction * trackSpan
            canvas.drawRect(
                cx - halfW,
                centerY - halfH,
                cx + halfW,
                centerY + halfH,
                markerPaint
            )
        }
    }

    private fun updateChapterFractions(fractions: FloatArray) {
        if (chapterFractions.contentEquals(fractions))
            return
        chapterFractions = fractions
        invalidate()
    }

    companion object {
        private val EMPTY_CHAPTER_FRACTIONS = FloatArray(0)
        private const val MARKER_COLOR = 0xCCFFFFFF.toInt()
        private const val EDGE_CHAPTER_SKIP_SECONDS = 0.5
        private const val TRACK_HEIGHT_DP = 8f
        private const val SELECTION_STROKE_DP = 2f
        private const val SELECTION_INSET_DP = 3f
        private const val SELECTION_CORNER_RADIUS_DP = 10f
        private const val MARKER_WIDTH_DP = 3f
        private const val MARKER_HEIGHT_DP = 12f
    }
}
