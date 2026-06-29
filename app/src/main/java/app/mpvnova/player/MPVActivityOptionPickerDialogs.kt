package app.mpvnova.player

import androidx.appcompat.app.AlertDialog

internal fun MPVActivity.optionPickerDialog(
    picker: OptionPickerDialog,
    restore: StateRestoreCallback,
    onDismiss: () -> Unit = { reopenDrawerIfPending() },
): AlertDialog = with(AlertDialog.Builder(this)) {
    setView(picker.buildView(layoutInflater))
    setOnDismissListener {
        restore()
        onDismiss()
    }
    create()
}

internal fun MPVActivity.showOptionPickerDialog(dialog: AlertDialog) {
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.42f,
            maxWidthDp = 520f,
        )
    )
}
