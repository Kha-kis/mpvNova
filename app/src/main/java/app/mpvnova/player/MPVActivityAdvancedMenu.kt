package app.mpvnova.player

import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToInt

private const val ADVANCED_MENU_REOPEN_DELAY_MS = 80L

internal fun MPVActivity.advancedMenuItems(restoreState: StateRestoreCallback): MutableList<MenuItem> {
    val reopenAdvancedMenu: StateRestoreCallback = {
        eventUiHandler.postDelayed(
            { openAdvancedMenu(restoreState) },
            ADVANCED_MENU_REOPEN_DELAY_MS
        )
    }
    val buttons = mutableListOf(
        MenuItem(R.id.subSeekPrev) { mpvCommand(arrayOf("sub-seek", "-1")); true },
        MenuItem(R.id.subSeekNext) { mpvCommand(arrayOf("sub-seek", "1")); true },
        MenuItem(R.id.statsBtn) {
            mpvCommand(arrayOf("script-binding", "stats/display-stats-toggle")); true
        },
        MenuItem(R.id.aspectBtn) { openAspectMenu(reopenAdvancedMenu) },
    )
    addStatsPageButtons(buttons)
    addVideoAdjustmentButtons(buttons, reopenAdvancedMenu)
    addDelayButtons(buttons, reopenAdvancedMenu)
    return buttons
}

private fun MPVActivity.addStatsPageButtons(buttons: MutableList<MenuItem>) {
    val statsButtons = arrayOf(R.id.statsBtn1, R.id.statsBtn2, R.id.statsBtn3)
    for (page in STATS_PAGE_FIRST..STATS_PAGE_LAST) {
        buttons.add(MenuItem(statsButtons[page - 1]) {
            mpvCommand(arrayOf("script-binding", "stats/display-page-$page")); true
        })
    }
}

private fun MPVActivity.addVideoAdjustmentButtons(
    buttons: MutableList<MenuItem>,
    restoreState: StateRestoreCallback
) {
    buttons.add(MenuItem(R.id.contrastBtn) {
        openVideoAdjustmentPicker(VIDEO_CONTRAST_ADJUSTMENT, restoreState)
    })
    buttons.add(MenuItem(R.id.brightnessBtn) {
        openPlayerBrightnessPicker(restoreState)
    })
    buttons.add(MenuItem(R.id.gammaBtn) {
        openVideoAdjustmentPicker(VIDEO_GAMMA_ADJUSTMENT, restoreState)
    })
    buttons.add(MenuItem(R.id.saturationBtn) {
        openVideoAdjustmentPicker(VIDEO_SATURATION_ADJUSTMENT, restoreState)
    })
}

private fun MPVActivity.openVideoAdjustmentPicker(
    spec: VideoAdjustmentSpec,
    restoreState: StateRestoreCallback
): Boolean {
    val previousValue = (
        mpvGetPropertyDouble(spec.property)
            ?: VIDEO_ADJUSTMENT_DEFAULT_INT.toDouble()
        )
        .roundToInt()
        .coerceIn(VIDEO_ADJUSTMENT_MIN_INT, VIDEO_ADJUSTMENT_MAX_INT)
    val picker = VideoAdjustmentDialog(
        config = VideoAdjustmentDialogConfig(
            titleRes = spec.titleRes,
            minValue = VIDEO_ADJUSTMENT_MIN_INT,
            maxValue = VIDEO_ADJUSTMENT_MAX_INT,
            defaultValue = VIDEO_ADJUSTMENT_DEFAULT_INT,
            valueFormatRes = R.string.format_fixed_number
        ),
        initialValue = previousValue,
        initialRemember = rememberVideoAdjustment(spec),
        onPreview = { value -> mpvSetPropertyInt(spec.property, value) }
    )
    var accepted = false
    lateinit var dialog: AlertDialog
    dialog = with(AlertDialog.Builder(this)) {
        setView(picker.buildView(
            layoutInflater,
            onOk = {
                accepted = true
                mpvSetPropertyInt(spec.property, picker.value)
                saveVideoAdjustmentChoice(spec, picker.value, picker.rememberValue)
                dialog.dismiss()
            },
            onCancel = { dialog.cancel() }
        ))
        setOnDismissListener {
            if (!accepted) {
                mpvSetPropertyInt(spec.property, previousValue)
            }
            restoreState()
        }
        create()
    }
    showWidePlayerDialog(dialog, videoAdjustmentDialogLayout())
    return false
}

private fun MPVActivity.addDelayButtons(
    buttons: MutableList<MenuItem>,
    restoreState: StateRestoreCallback
) {
    buttons.add(MenuItem(R.id.audioDelayBtn) {
        val picker = DecimalPickerDialog(AUDIO_DELAY_MIN_SEC, AUDIO_DELAY_MAX_SEC)
        genericPickerDialog(picker, R.string.audio_delay, "audio-delay", restoreState)
        false
    })
    buttons.add(MenuItem(R.id.subDelayBtn) {
        openAdvancedSubDelayDialog(restoreState)
        false
    })
}

private fun MPVActivity.openAdvancedSubDelayDialog(restoreState: StateRestoreCallback) {
    showSubDelayPicker(restoreState, advancedSubDelayLayout())
}

private fun advancedSubDelayLayout(): PlayerDialogLayout {
    return PlayerDialogLayout(
        widthFraction = ADVANCED_SUB_DELAY_DIALOG_WIDTH_FRACTION,
        maxWidthDp = ADVANCED_SUB_DELAY_DIALOG_MAX_WIDTH_DP,
        heightFraction = ADVANCED_SUB_DELAY_DIALOG_HEIGHT_FRACTION,
        maxHeightDp = ADVANCED_SUB_DELAY_DIALOG_MAX_HEIGHT_DP,
    )
}

internal fun MPVActivity.advancedHiddenButtons(): MutableSet<Int> {
    val hiddenButtons = mutableSetOf<Int>()
    if (player.vid == -1)
        hiddenButtons.addAll(arrayOf(R.id.rowVideo1, R.id.rowVideo2, R.id.aspectBtn))
    if (player.aid == -1 || player.vid == -1)
        hiddenButtons.add(R.id.audioDelayBtn)
    if (player.sid == -1)
        hiddenButtons.addAll(arrayOf(R.id.subDelayBtn, R.id.rowSubSeek))
    return hiddenButtons
}
