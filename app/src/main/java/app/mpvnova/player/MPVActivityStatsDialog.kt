package app.mpvnova.player

import app.mpvnova.player.databinding.DialogStatsPickerBinding
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog

private const val STATS_PICKER_WIDTH_FRACTION = 0.38f
private const val STATS_PICKER_MAX_WIDTH_DP = 500f

internal fun MPVActivity.showStatsPage(page: Int) {
    val statsPage = page.coerceIn(STATS_PAGE_FIRST, STATS_PAGE_LAST)
    mpvCommand(arrayOf("script-binding", "stats/display-page-$statsPage"))
}

internal fun MPVActivity.showStatsPickerDialog() {
    val restore = keepPlaybackForDialog()
    lateinit var dialog: AlertDialog

    @Suppress("DEPRECATION")
    dialog = with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        val binding = DialogStatsPickerBinding.inflate(inflater)
        fun View.openStatsPageOnClick(page: Int) = setOnClickListener {
            showStatsPage(page)
            dialog.dismiss()
        }
        binding.statsPage1Row.openStatsPageOnClick(STATS_PAGE_FIRST)
        binding.statsPage2Row.openStatsPageOnClick(STATS_PAGE_FIRST + 1)
        binding.statsPage3Row.openStatsPageOnClick(STATS_PAGE_LAST)
        binding.cancelBtn.setOnClickListener { dialog.cancel() }

        val selected = statsLuaMode.takeIf { it in STATS_PAGE_FIRST..STATS_PAGE_LAST }
        binding.statsPage1Row.isActivated = selected == STATS_PAGE_FIRST
        binding.statsPage2Row.isActivated = selected == STATS_PAGE_FIRST + 1
        binding.statsPage3Row.isActivated = selected == STATS_PAGE_LAST
        binding.statsPage1Row.post { binding.statsPage1Row.requestFocus() }

        setView(binding.root)
        setOnDismissListener { restore(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = STATS_PICKER_WIDTH_FRACTION,
            maxWidthDp = STATS_PICKER_MAX_WIDTH_DP,
        )
    )
}
