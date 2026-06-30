package app.mpvnova.player

import app.mpvnova.player.databinding.DialogPlayerPickerBinding
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog

internal fun MPVActivity.showPlayerPickerDialog(
    @StringRes titleRes: Int,
    contentView: View,
    restoreState: StateRestoreCallback,
    layout: PlayerDialogLayout,
    onOk: () -> Unit
) {
    lateinit var dialog: AlertDialog
    val binding = playerPickerBinding ?: DialogPlayerPickerBinding.inflate(layoutInflater).also {
        handleInsetsAsPadding(it.root)
        playerPickerBinding = it
    }
    binding.root.detachFromParent()
    contentView.detachFromParent()
    binding.pickerTitle.setText(titleRes)
    binding.pickerContent.removeAllViews()
    binding.pickerContent.addView(contentView)
    binding.cancelBtn.setOnClickListener { dialog.cancel() }
    binding.okBtn.setOnClickListener {
        onOk()
        dialog.dismiss()
    }
    dialog = with(AlertDialog.Builder(this)) {
        setView(binding.root)
        setOnDismissListener { restoreState(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(dialog, layout)
}
