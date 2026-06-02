package app.mpvnova.player

import android.content.SharedPreferences
import androidx.preference.PreferenceManager.getDefaultSharedPreferences

/**
 * Live + persisted application of the custom subtitle style profile.
 *
 * The design values are always persisted (see [writeSubtitleStyleSettings]); this
 * file decides whether and how they reach mpv. While [MPVActivity.customSubStyleEnabled]
 * is off nothing here touches rendering, and ASS/SSA subtitles keep their own
 * styling unless the user also turns on the override.
 */

private val SUB_STYLE_PROPS = listOf(
    "sub-color",
    "sub-border-color",
    "sub-border-size",
    "sub-back-color",
    "sub-border-style",
    "sub-shadow-offset",
    "sub-shadow-color",
    "sub-font",
    "sub-ass-override",
)

private const val SUB_SHADOW_OFFSET_ON = 2.0
private const val SUB_SHADOW_OFFSET_OFF = 0.0
private const val SUB_TRANSPARENT = 0x000000
private const val FULLY_OPAQUE_PERCENT = 100

/** Master entry point. Applies the profile when enabled, restores the baseline when not. */
internal fun MPVActivity.applyCustomSubtitleStyle() {
    if (customSubStyleEnabled) {
        snapshotSubStyleBaselineIfNeeded()
        writeCustomSubtitleStyle()
    } else {
        restoreSubStyleBaseline()
    }
}

/**
 * Re-assert on each new file. Custom styling only carries between files when
 * "Persist subtitle settings" is on; otherwise it applies to the file the user
 * enabled it on and the next file starts clean. The design itself stays saved
 * either way, so re-enabling later restores it without redoing anything.
 */
internal fun MPVActivity.applyCustomSubtitleStyleOnFileLoad() {
    if (!persistSubFilters && customSubStyleEnabled)
        customSubStyleEnabled = false
    applyCustomSubtitleStyle()
}

private fun MPVActivity.snapshotSubStyleBaselineIfNeeded() {
    if (subStyleSavedDefaults != null) return
    subStyleSavedDefaults = SUB_STYLE_PROPS.associateWith { mpvGetPropertyString(it) }
}

private fun MPVActivity.restoreSubStyleBaseline() {
    val saved = subStyleSavedDefaults ?: return
    for ((key, value) in saved) {
        if (value != null)
            mpvSetPropertyString(key, value)
    }
}

private fun MPVActivity.writeCustomSubtitleStyle() {
    val textColor = SUBTITLE_COLOR_OPTIONS[subStyleTextColorIndex]
    val textOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleTextOpacityIndex]
    mpvSetPropertyString("sub-color", mpvSubtitleColor(textColor.rgb, textOpacity))

    val borderColor = SUBTITLE_COLOR_OPTIONS[subStyleBorderColorIndex]
    mpvSetPropertyString("sub-border-color", mpvSubtitleColor(borderColor.rgb, FULLY_OPAQUE_PERCENT))
    mpvSetPropertyDouble("sub-border-size", SUBTITLE_BORDER_SIZE_STEPS[subStyleBorderSizeIndex])

    val bgOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleBgOpacityIndex]
    if (bgOpacity > 0) {
        // Background box mode: a solid panel behind the line. The edge/shadow
        // controls don't apply in this mode, so flatten the shadow.
        val bgColor = SUBTITLE_COLOR_OPTIONS[subStyleBgColorIndex]
        mpvSetPropertyString("sub-border-style", "background-box")
        mpvSetPropertyString("sub-back-color", mpvSubtitleColor(bgColor.rgb, bgOpacity))
        mpvSetPropertyDouble("sub-shadow-offset", SUB_SHADOW_OFFSET_OFF)
    } else {
        mpvSetPropertyString("sub-border-style", "outline-and-shadow")
        mpvSetPropertyString("sub-back-color", mpvSubtitleColor(SUB_TRANSPARENT, 0))
        applySubEdge()
    }

    applySubFont()
    mpvSetPropertyString("sub-ass-override", if (subStyleOverrideAss) "force" else "scale")
}

private fun MPVActivity.applySubEdge() {
    when (subStyleEdge) {
        SubtitleEdgeStyle.NONE -> {
            mpvSetPropertyDouble("sub-border-size", 0.0)
            mpvSetPropertyDouble("sub-shadow-offset", SUB_SHADOW_OFFSET_OFF)
        }
        SubtitleEdgeStyle.OUTLINE -> {
            mpvSetPropertyDouble("sub-shadow-offset", SUB_SHADOW_OFFSET_OFF)
        }
        SubtitleEdgeStyle.DROP_SHADOW -> {
            mpvSetPropertyString("sub-shadow-color", mpvSubtitleColor(SUB_TRANSPARENT, FULLY_OPAQUE_PERCENT))
            mpvSetPropertyDouble("sub-shadow-offset", SUB_SHADOW_OFFSET_ON)
        }
    }
}

private fun MPVActivity.applySubFont() {
    if (subStyleFontFamily.isNotEmpty()) {
        mpvSetPropertyString("sub-font", subStyleFontFamily)
    } else {
        // "Default": put mpv's own font back rather than forcing a family.
        subStyleSavedDefaults?.get("sub-font")?.let { mpvSetPropertyString("sub-font", it) }
    }
}

internal fun MPVActivity.readSubtitleStyleSettings(prefs: SharedPreferences) {
    // The design below is always restored. Whether it's auto-applied carries
    // only when "Persist subtitle settings" is on (read in readSubFilterSettings
    // first), matching how sub scale/position and the audio filters behave.
    customSubStyleEnabled = persistSubFilters && prefs.getBoolean("custom_sub_style_enabled", false)
    subStyleTextColorIndex = subtitleColorOptionIndex(
        prefs.getString("sub_style_text_color", SUBTITLE_TEXT_COLOR_DEFAULT_ID) ?: SUBTITLE_TEXT_COLOR_DEFAULT_ID
    )
    subStyleTextOpacityIndex = nearestOpacityIndex(
        prefs.getInt("sub_style_text_opacity", DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT)
    )
    subStyleBorderColorIndex = subtitleColorOptionIndex(
        prefs.getString("sub_style_border_color", SUBTITLE_BORDER_COLOR_DEFAULT_ID) ?: SUBTITLE_BORDER_COLOR_DEFAULT_ID
    )
    subStyleBorderSizeIndex = prefs.getInt("sub_style_border_size", DEFAULT_SUBTITLE_BORDER_INDEX)
        .coerceIn(0, SUBTITLE_BORDER_SIZE_STEPS.lastIndex)
    subStyleBgColorIndex = subtitleColorOptionIndex(
        prefs.getString("sub_style_bg_color", SUBTITLE_BG_COLOR_DEFAULT_ID) ?: SUBTITLE_BG_COLOR_DEFAULT_ID
    )
    subStyleBgOpacityIndex = nearestOpacityIndex(
        prefs.getInt("sub_style_bg_opacity", DEFAULT_SUBTITLE_BG_OPACITY_PERCENT)
    )
    subStyleEdge = runCatching {
        SubtitleEdgeStyle.valueOf(prefs.getString("sub_style_edge", DEFAULT_SUBTITLE_EDGE_STYLE.name)!!)
    }.getOrDefault(DEFAULT_SUBTITLE_EDGE_STYLE)
    subStyleFontFamily = prefs.getString("sub_style_font_family", SUBTITLE_FONT_DEFAULT_FAMILY)
        ?: SUBTITLE_FONT_DEFAULT_FAMILY
    subStyleOverrideAss = prefs.getBoolean("sub_style_override_ass", false)
}

/**
 * Persist the whole design unconditionally — the profile is never discarded,
 * even while the master toggle (or persistSubFilters) is off.
 */
internal fun MPVActivity.writeSubtitleStyleSettings() {
    val prefs = getDefaultSharedPreferences(applicationContext)
    with(prefs.edit()) {
        putBoolean("custom_sub_style_enabled", customSubStyleEnabled)
        putString("sub_style_text_color", SUBTITLE_COLOR_OPTIONS[subStyleTextColorIndex].id)
        putInt("sub_style_text_opacity", SUBTITLE_OPACITY_PERCENT_STEPS[subStyleTextOpacityIndex])
        putString("sub_style_border_color", SUBTITLE_COLOR_OPTIONS[subStyleBorderColorIndex].id)
        putInt("sub_style_border_size", subStyleBorderSizeIndex)
        putString("sub_style_bg_color", SUBTITLE_COLOR_OPTIONS[subStyleBgColorIndex].id)
        putInt("sub_style_bg_opacity", SUBTITLE_OPACITY_PERCENT_STEPS[subStyleBgOpacityIndex])
        putString("sub_style_edge", subStyleEdge.name)
        putString("sub_style_font_family", subStyleFontFamily)
        putBoolean("sub_style_override_ass", subStyleOverrideAss)
        apply()
    }
}
