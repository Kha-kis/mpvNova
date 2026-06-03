package app.mpvnova.player

import android.graphics.Color
import android.graphics.Typeface
import java.util.Locale

private const val SUB_PREVIEW_SHADOW_ALPHA = 180
private const val SUB_PREVIEW_SHADOW_DP = 2f
private const val FULLY_OPAQUE_PERCENT = 100

internal fun MPVActivity.subtitleStylePreviewSpec(): SubtitleStylePreviewView.Spec {
    val density = resources.displayMetrics.density
    val bgOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleBgOpacityIndex]
    val bgOn = bgOpacity > 0
    val outlineActive = !bgOn && subStyleEdge != SubtitleEdgeStyle.NONE
    val shadowOn = !bgOn && subStyleEdge == SubtitleEdgeStyle.DROP_SHADOW

    return SubtitleStylePreviewView.Spec(
        text = getString(R.string.sub_style_preview_text),
        textColor = subtitleArgb(
            SUBTITLE_COLOR_OPTIONS[subStyleTextColorIndex].rgb,
            SUBTITLE_OPACITY_PERCENT_STEPS[subStyleTextOpacityIndex],
        ),
        outlineColor = if (outlineActive) {
            subtitleArgb(SUBTITLE_COLOR_OPTIONS[subStyleBorderColorIndex].rgb, FULLY_OPAQUE_PERCENT)
        } else {
            Color.TRANSPARENT
        },
        outlineWidthPx = if (outlineActive) {
            SUBTITLE_BORDER_SIZE_STEPS[subStyleBorderSizeIndex].toFloat() * density
        } else {
            0f
        },
        backgroundColor = if (bgOn) {
            subtitleArgb(SUBTITLE_COLOR_OPTIONS[subStyleBgColorIndex].rgb, bgOpacity)
        } else {
            Color.TRANSPARENT
        },
        shadowColor = if (shadowOn) Color.argb(SUB_PREVIEW_SHADOW_ALPHA, 0, 0, 0) else Color.TRANSPARENT,
        shadowRadiusPx = if (shadowOn) SUB_PREVIEW_SHADOW_DP * density else 0f,
        shadowOffsetPx = if (shadowOn) SUB_PREVIEW_SHADOW_DP * density else 0f,
        typeface = subtitleTypefaceFor(subStyleFontFamily),
    )
}

internal fun MPVActivity.subtitleTypefaceFor(family: String): Typeface = when (family) {
    "", "sans-serif" -> Typeface.SANS_SERIF
    "serif" -> Typeface.SERIF
    "monospace" -> Typeface.MONOSPACE
    else -> userOrBundledTypeface(family) ?: Typeface.DEFAULT
}

private fun MPVActivity.userOrBundledTypeface(family: String): Typeface? {
    val file = subtitleFontsDir()
        .listFiles { candidate ->
            candidate.isFile && candidate.extension.lowercase(Locale.ROOT) in FONT_EXTENSIONS
        }
        ?.firstOrNull { SubtitleFontTable.familyName(it) == family }
        ?: return null
    return runCatching { Typeface.createFromFile(file) }.getOrNull()
}

private fun subtitleArgb(rgb: Int, opacityPercent: Int): Int =
    Color.parseColor(mpvSubtitleColor(rgb, opacityPercent))
