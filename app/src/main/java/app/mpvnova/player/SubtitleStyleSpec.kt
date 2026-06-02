package app.mpvnova.player

/**
 * Pure data model and mpv encoding for the in-player subtitle style customizer.
 *
 * Deliberately free of Android/mpv dependencies so the colour math and the
 * value tables stay unit-testable (mirrors the other pure-logic specs in this
 * package). The live property writes that consume these values live in
 * MPVActivitySubtitleStyleApply.kt.
 *
 * IMPORTANT: these styles only affect plain-text subtitles (SRT/VTT) while mpv
 * keeps its default `sub-ass-override`. They reach ASS/SSA subtitles only when
 * the user turns on the override toggle, which flips `sub-ass-override=force`.
 */

internal data class SubtitleColorOption(val id: String, val label: String, val rgb: Int)

/**
 * Swatch palette shown as a D-pad grid. Couch-friendly: a fixed set the user
 * arrows through rather than a freeform colour wheel. RGB only — opacity is a
 * separate stepper and gets folded into the mpv colour string at apply time.
 */
internal val SUBTITLE_COLOR_OPTIONS = listOf(
    SubtitleColorOption("white", "White", 0xFFFFFF),
    SubtitleColorOption("black", "Black", 0x000000),
    SubtitleColorOption("yellow", "Yellow", 0xFFD60A),
    SubtitleColorOption("amber", "Amber", 0xFF9F0A),
    SubtitleColorOption("red", "Red", 0xFF453A),
    SubtitleColorOption("magenta", "Magenta", 0xFF2D92),
    SubtitleColorOption("violet", "Violet", 0xBF5AF2),
    SubtitleColorOption("blue", "Blue", 0x0A84FF),
    SubtitleColorOption("cyan", "Cyan", 0x64D2FF),
    SubtitleColorOption("green", "Green", 0x30D158),
    SubtitleColorOption("lime", "Lime", 0xB5F23A),
    SubtitleColorOption("gray", "Gray", 0x8E8E93),
)

internal const val SUBTITLE_TEXT_COLOR_DEFAULT_ID = "white"
internal const val SUBTITLE_BORDER_COLOR_DEFAULT_ID = "black"
internal const val SUBTITLE_BG_COLOR_DEFAULT_ID = "black"

/** 0..100 in 10% steps, shared by text opacity and background-box opacity. */
internal val SUBTITLE_OPACITY_PERCENT_STEPS = (0..100 step 10).toList().toIntArray()
internal const val DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT = 100
internal const val DEFAULT_SUBTITLE_BG_OPACITY_PERCENT = 0

/** Outline thickness in mpv `sub-border-size` units. 3.0 is mpv's own default. */
internal val SUBTITLE_BORDER_SIZE_STEPS = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
internal const val DEFAULT_SUBTITLE_BORDER_INDEX = 3

/**
 * Named edge looks. Each resolves to a border-size + shadow-offset combination
 * at apply time.
 *
 * Android's caption styles also include "raised" and "depressed", but those
 * need a directional drop shadow and mpv/libass only expose a single
 * non-directional `sub-shadow-offset`, so they're intentionally omitted rather
 * than faked.
 */
internal enum class SubtitleEdgeStyle {
    OUTLINE,
    DROP_SHADOW,
    NONE,
}

internal val DEFAULT_SUBTITLE_EDGE_STYLE = SubtitleEdgeStyle.OUTLINE

/**
 * A selectable font. [family] is what gets handed to mpv's `sub-font`; an empty
 * family means "leave mpv's default font alone". Bundled and user-imported fonts
 * are appended at runtime (see MPVActivitySubtitleFonts.kt), keyed by the real
 * family name read out of each font file so `sub-font` actually matches.
 */
internal data class SubtitleFontChoice(val label: String, val family: String)

/** Family selected when nothing custom is chosen. */
internal const val SUBTITLE_FONT_DEFAULT_FAMILY = ""

/**
 * Generic families resolved by fontconfig from the device's own system fonts,
 * so they're always available with no bundled assets.
 */
internal val SUBTITLE_GENERIC_FONTS = listOf(
    SubtitleFontChoice("Default", SUBTITLE_FONT_DEFAULT_FAMILY),
    SubtitleFontChoice("Sans serif", "sans-serif"),
    SubtitleFontChoice("Serif", "serif"),
    SubtitleFontChoice("Monospace", "monospace"),
)

internal fun subtitleColorOptionIndex(id: String): Int =
    SUBTITLE_COLOR_OPTIONS.indexOfFirst { it.id == id }.coerceAtLeast(0)

internal fun nearestOpacityIndex(percent: Int): Int =
    SUBTITLE_OPACITY_PERCENT_STEPS.indices.minBy {
        kotlin.math.abs(SUBTITLE_OPACITY_PERCENT_STEPS[it] - percent)
    }

/**
 * Encode an mpv colour string as `#AARRGGBB`.
 *
 * mpv's `<color>` type uses the standard alpha convention: `FF` is fully opaque
 * and `00` is fully transparent. So a UI opacity of 100% maps to alpha `FF`,
 * and 0% maps to alpha `00`. (Note: this is the opposite of ASS's own `\alpha`
 * tag, which is where the "mpv alpha is backwards" myth comes from.)
 *
 * The "off" states (text fully hidden, background box disabled) are handled by
 * the enable toggle and by not setting the box colour at all, so we never rely
 * on alpha `00` for transparency — which also sidesteps mpv treating a literal
 * alpha of 0 as "use default".
 */
private const val RGB_MASK = 0xFFFFFF
private const val ALPHA_MAX = 255
private const val PERCENT_MAX = 100
private const val PERCENT_HALF = 50

internal fun mpvSubtitleColor(rgb: Int, opacityPercent: Int): String {
    val opacity = opacityPercent.coerceIn(0, PERCENT_MAX)
    val alpha = (opacity * ALPHA_MAX + PERCENT_HALF) / PERCENT_MAX
    return "#%02X%06X".format(alpha, rgb and RGB_MASK)
}
