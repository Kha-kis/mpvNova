package app.mpvnova.player

import androidx.appcompat.app.AlertDialog
import android.util.Log
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible

internal fun MPVActivity.clampSubFilterState() {
    subScaleLevel = subScaleLevel.coerceIn(0, subScaleSteps.lastIndex)
    subPosLevel = subPosLevel.coerceIn(0, subPosSteps.lastIndex)
    secondaryPosLevel = secondaryPosLevel.coerceIn(0, secondaryPosSteps.lastIndex)
}

internal fun MPVActivity.pickSpeed() {
    val picker = SpeedPickerDialog()

    val restore = keepPlaybackForDialog()
    genericPickerDialog(picker, R.string.title_speed_dialog, "speed") {
        restore()
    }
}

internal fun MPVActivity.goIntoPiP() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        return
    enterPictureInPictureMode(buildPiPParams())
}

internal fun MPVActivity.genericMenu(
        @LayoutRes layoutRes: Int, buttons: List<MenuItem>, hiddenButtons: Set<Int>,
        restoreState: StateRestoreCallback) {
    lateinit var dialog: AlertDialog

    val builder = AlertDialog.Builder(this)
    val dialogView = LayoutInflater.from(builder.context).inflate(layoutRes, null)

    for (button in buttons) {
        val buttonView = dialogView.findViewById<Button>(button.idRes)
        buttonView.setOnClickListener {
            val ret = button.handler()
            if (ret) {
                restoreState()
            }
            if (ret || !button.keepMenuOpen) {
                dialog.dismiss()
            }
        }
    }

    hiddenButtons.forEach { dialogView.findViewById<View>(it).isVisible = false }

    if (visibleChildren(dialogView) == 0) {
        Log.w(MPV_ACTIVITY_TAG, "Not showing menu because it would be empty")
        restoreState()
        return
    }

    handleInsetsAsPadding(dialogView)

    with (builder) {
        setView(dialogView)
        setOnCancelListener { restoreState() }
        dialog = create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.56f,
            maxWidthDp = 620f,
        )
    )
}

internal fun MPVActivity.openTopMenu() {
    val restoreState = keepPlaybackForDialog()
    genericMenu(
        R.layout.dialog_top_menu,
        topMenuItems(restoreState),
        topMenuHiddenButtons(),
        restoreState
    )
}

internal fun MPVActivity.genericPickerDialog(
    picker: PickerDialog, @StringRes titleRes: Int, property: String,
    restoreState: StateRestoreCallback
) {
    val pickerView = picker.buildView(layoutInflater)
    picker.number = mpvGetPropertyDouble(property)
    showPlayerPickerDialog(
        titleRes = titleRes,
        contentView = pickerView,
        restoreState = restoreState,
        layout = PlayerDialogLayout(
            widthFraction = 0.56f,
            maxWidthDp = 620f,
            heightFraction = 0.62f,
            maxHeightDp = 520f,
        ),
    ) {
        picker.number?.let {
            if (picker.isInteger())
                mpvSetPropertyInt(property, it.toInt())
            else
                mpvSetPropertyDouble(property, it)
        }
    }
}
