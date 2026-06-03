package app.mpvnova.player

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog

internal fun MPVActivity.openSubtitleStyleDialog() {
    val restore = keepPlaybackForDialog()
    val impl = SubtitleStyleDialog()
    impl.stateProvider = { subtitleStyleState() }
    impl.onAdjust = { control, delta -> adjustSubtitleStyle(control, delta) }
    lateinit var dialog: AlertDialog
    impl.onAddFont = {
        dialog.dismiss()
        pickAndImportSubtitleFont()
    }
    impl.onRemoveFont = {
        dialog.dismiss()
        showRemoveSubtitleFontDialog()
    }

    dialog = with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(impl.buildView(inflater))
        setOnDismissListener { restore(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.6f,
            maxWidthDp = 820f,
            heightFraction = 0.9f,
            maxHeightDp = 880f,
        )
    )
}

internal fun MPVActivity.adjustSubtitleStyle(
    control: SubtitleStyleDialog.Control,
    delta: Int,
): SubtitleStyleDialog.State {
    when (control) {
        SubtitleStyleDialog.Control.MASTER -> customSubStyleEnabled = !customSubStyleEnabled
        SubtitleStyleDialog.Control.TEXT_COLOR ->
            subStyleTextColorIndex = wrapIndex(subStyleTextColorIndex, delta, SUBTITLE_COLOR_OPTIONS.size)
        SubtitleStyleDialog.Control.TEXT_OPACITY ->
            subStyleTextOpacityIndex = clampIndex(subStyleTextOpacityIndex, delta, SUBTITLE_OPACITY_PERCENT_STEPS.size)
        SubtitleStyleDialog.Control.EDGE ->
            subStyleEdge = SubtitleEdgeStyle.entries[
                wrapIndex(subStyleEdge.ordinal, delta, SubtitleEdgeStyle.entries.size)
            ]
        SubtitleStyleDialog.Control.OUTLINE_COLOR ->
            subStyleBorderColorIndex = wrapIndex(subStyleBorderColorIndex, delta, SUBTITLE_COLOR_OPTIONS.size)
        SubtitleStyleDialog.Control.OUTLINE_SIZE ->
            subStyleBorderSizeIndex = clampIndex(subStyleBorderSizeIndex, delta, SUBTITLE_BORDER_SIZE_STEPS.size)
        SubtitleStyleDialog.Control.BG_OPACITY ->
            subStyleBgOpacityIndex = clampIndex(subStyleBgOpacityIndex, delta, SUBTITLE_OPACITY_PERCENT_STEPS.size)
        SubtitleStyleDialog.Control.BG_COLOR ->
            subStyleBgColorIndex = wrapIndex(subStyleBgColorIndex, delta, SUBTITLE_COLOR_OPTIONS.size)
        SubtitleStyleDialog.Control.FONT -> {
            val choices = subtitleFontChoices()
            val current = choices.indexOfFirst { it.family == subStyleFontFamily }.coerceAtLeast(0)
            subStyleFontFamily = choices[wrapIndex(current, delta, choices.size)].family
        }
        SubtitleStyleDialog.Control.OVERRIDE_ASS -> subStyleOverrideAss = !subStyleOverrideAss
    }
    applyCustomSubtitleStyle()
    writeSubtitleStyleSettings()
    return subtitleStyleState()
}

private fun MPVActivity.pickAndImportSubtitleFont() {
    openFilePickerFor(R.string.sub_style_add_font) { result, data ->
        val family = importSubtitleFont(result, data)
        if (!family.isNullOrEmpty()) {
            subStyleFontFamily = family
            if (customSubStyleEnabled)
                applyCustomSubtitleStyle()
            writeSubtitleStyleSettings()
            showToast(getString(R.string.sub_style_font_added, family))
        }
        openSubtitleStyleDialog()
    }
}

private fun MPVActivity.showRemoveSubtitleFontDialog() {
    val families = removableFontFamilies()
    if (families.isEmpty()) {
        showToast(getString(R.string.sub_style_no_user_fonts))
        openSubtitleStyleDialog()
        return
    }
    val restore = keepPlaybackForDialog()
    val items = families.toTypedArray()
    with(AlertDialog.Builder(this)) {
        setTitle(R.string.sub_style_remove_font)
        setItems(items) { d, which ->
            d.dismiss()
            removeSubtitleFontFamily(families[which])
            showToast(getString(R.string.sub_style_font_removed, families[which]))
        }
        setOnDismissListener { restore(); openSubtitleStyleDialog() }
        create().show()
    }
}

private fun MPVActivity.subtitleStyleState(): SubtitleStyleDialog.State {
    val on = customSubStyleEnabled
    val bgOpacity = SUBTITLE_OPACITY_PERCENT_STEPS[subStyleBgOpacityIndex]
    val bgOn = bgOpacity > 0
    // Outline/edge and background box are mutually exclusive rendering modes.
    val edgeApplies = on && !bgOn

    return SubtitleStyleDialog.State(
        masterOn = on,
        textColor = colorRow(subStyleTextColorIndex, on),
        textOpacity = SubtitleStyleDialog.Row(
            percentLabel(SUBTITLE_OPACITY_PERCENT_STEPS[subStyleTextOpacityIndex]),
            enabled = on,
        ),
        edge = SubtitleStyleDialog.Row(edgeLabel(subStyleEdge), enabled = edgeApplies),
        outlineColor = colorRow(subStyleBorderColorIndex, edgeApplies && subStyleEdge != SubtitleEdgeStyle.NONE),
        outlineSize = SubtitleStyleDialog.Row(
            "%.0f".format(SUBTITLE_BORDER_SIZE_STEPS[subStyleBorderSizeIndex]),
            enabled = edgeApplies && subStyleEdge != SubtitleEdgeStyle.NONE,
        ),
        bgOpacity = SubtitleStyleDialog.Row(
            if (bgOn) percentLabel(bgOpacity) else getString(R.string.status_off),
            enabled = on,
        ),
        bgColor = colorRow(subStyleBgColorIndex, on && bgOn),
        font = SubtitleStyleDialog.Row(subtitleFontLabel(subStyleFontFamily), enabled = on),
        overrideOn = subStyleOverrideAss,
        overrideEnabled = on,
        preview = subtitleStylePreviewSpec(),
    )
}

private fun MPVActivity.colorRow(index: Int, enabled: Boolean): SubtitleStyleDialog.Row {
    val option = SUBTITLE_COLOR_OPTIONS[index]
    return SubtitleStyleDialog.Row(option.label, enabled = enabled, chipRgb = option.rgb)
}

private fun percentLabel(percent: Int): String = "$percent%"

private fun MPVActivity.edgeLabel(edge: SubtitleEdgeStyle): String = getString(
    when (edge) {
        SubtitleEdgeStyle.OUTLINE -> R.string.sub_style_edge_outline
        SubtitleEdgeStyle.DROP_SHADOW -> R.string.sub_style_edge_shadow
        SubtitleEdgeStyle.NONE -> R.string.sub_style_edge_none
    }
)

private fun wrapIndex(current: Int, delta: Int, size: Int): Int = ((current + delta) % size + size) % size

private fun clampIndex(current: Int, delta: Int, size: Int): Int = (current + delta).coerceIn(0, size - 1)
