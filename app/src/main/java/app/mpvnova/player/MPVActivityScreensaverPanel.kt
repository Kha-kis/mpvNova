package app.mpvnova.player

import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import app.mpvnova.player.databinding.DialogScreensaverBinding
import app.mpvnova.player.databinding.DialogScreensaverLogoBinding
import app.mpvnova.player.databinding.DialogScreensaverTimeInputBinding

private const val ROW_TITLE_MAX_LINES = 2
private const val ROW_DETAIL_MAX_LINES = 3

// The in-player screensaver panel: mode (off/dim/logo), idle time, and the logo options
// in one scrollable card, mirroring the save-preset panel. Selections apply live.
internal fun MPVActivity.openScreensaverPanel() {
    val restore = keepPlaybackForDialog()
    val view = DialogScreensaverBinding.inflate(layoutInflater)
    buildScreensaverModeRows(view)
    buildScreensaverTimeoutRows(view)
    buildScreensaverLogoSection(view)
    val dialog = with(AlertDialog.Builder(this)) {
        setView(view.root)
        setOnDismissListener {
            restore()
            reopenDrawerIfPending()
        }
        create()
    }
    view.screensaverDoneBtn.setOnClickListener { dialog.dismiss() }
    showWidePlayerDialog(dialog, SCREENSAVER_DIALOG_LAYOUT)
    val selected = ScreensaverMode.entries.indexOf(screensaverMode)
    view.screensaverModeRows.post { view.screensaverModeRows.getChildAt(selected)?.requestFocus() }
}

private fun MPVActivity.buildScreensaverModeRows(view: DialogScreensaverBinding) {
    val container = view.screensaverModeRows
    val checks = mutableListOf<Pair<ScreensaverMode, ImageView>>()
    for (mode in ScreensaverMode.entries) {
        val row = inflateScreensaverRow(
            container, screensaverModeTitle(mode), screensaverModeDetail(mode), screensaverMode == mode
        )
        checks += mode to row.findViewById(R.id.optionCheck)
        row.setOnClickListener {
            setScreensaverMode(mode)
            checks.forEach { (m, check) -> check.visibility = if (m == mode) View.VISIBLE else View.INVISIBLE }
        }
        container.addView(row)
    }
}

private fun MPVActivity.buildScreensaverTimeoutRows(view: DialogScreensaverBinding) {
    val container = view.screensaverTimeoutRows
    container.removeAllViews()
    val currentSec = (screensaverTimeoutMs / MILLIS_PER_SECOND_LONG).toInt()
    val isPreset = currentSec in SCREENSAVER_CHOICES_SEC
    val checks = mutableListOf<Pair<Int, ImageView>>()
    lateinit var customCheck: ImageView
    for (seconds in SCREENSAVER_CHOICES_SEC) {
        val row = inflateScreensaverRow(container, screensaverChoiceLabel(seconds), null, currentSec == seconds)
        val check = row.findViewById<ImageView>(R.id.optionCheck)
        checks += seconds to check
        row.setOnClickListener {
            setScreensaverTimeoutSeconds(seconds)
            checks.forEach { (s, c) -> c.visibility = if (s == seconds) View.VISIBLE else View.INVISIBLE }
            customCheck.visibility = View.INVISIBLE
        }
        container.addView(row)
    }
    val customDetail = if (isPreset) null else screensaverChoiceLabel(currentSec)
    val customRow = inflateScreensaverRow(
        container, getString(R.string.pref_screensaver_custom_entry), customDetail, !isPreset
    )
    customCheck = customRow.findViewById(R.id.optionCheck)
    customRow.setOnClickListener { pickCustomScreensaverTime(view) }
    container.addView(customRow)
}

// Custom idle time as a nested numeric-input dialog over the panel; on OK it applies and
// rebuilds the idle-time rows so the custom value shows as selected.
private fun MPVActivity.pickCustomScreensaverTime(view: DialogScreensaverBinding) {
    val input = DialogScreensaverTimeInputBinding.inflate(layoutInflater)
    initScreensaverTimeInput(input, (screensaverTimeoutMs / MILLIS_PER_SECOND_LONG).toInt())
    val dialog = AlertDialog.Builder(this).setView(input.root).create()
    input.timeCancelBtn.setOnClickListener { dialog.dismiss() }
    input.timeOkBtn.setOnClickListener {
        setScreensaverTimeoutSeconds(screensaverInputToSeconds(input))
        dialog.dismiss()
        buildScreensaverTimeoutRows(view)
    }
    showWidePlayerDialog(dialog, SCREENSAVER_LOGO_DIALOG_LAYOUT)
    input.minutesInput.requestFocus()
}

// Logo picker + colour-shift toggle, only relevant to (and shown for) the bouncing-logo mode.
private fun MPVActivity.buildScreensaverLogoSection(view: DialogScreensaverBinding) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val container = view.screensaverLogoRows
    container.removeAllViews()

    val hasCustom = !prefs.getString(PREF_SCREENSAVER_LOGO_URI, null).isNullOrBlank()
    val logoDetail = getString(
        if (hasCustom) R.string.pref_screensaver_logo_custom else R.string.pref_screensaver_logo_default
    )
    val logoRow = inflateScreensaverRow(container, getString(R.string.pref_screensaver_logo_title), logoDetail, false)
    logoRow.findViewById<ImageView>(R.id.optionCheck).visibility = View.GONE
    logoRow.setOnClickListener { showScreensaverLogoChooser(view) }
    container.addView(logoRow)

    val tintOn = prefs.getBoolean(PREF_SCREENSAVER_TINT, !hasCustom)
    val tintRow = inflateScreensaverRow(
        container,
        getString(R.string.pref_screensaver_tint_title),
        getString(R.string.pref_screensaver_tint_summary),
        tintOn,
    )
    val tintCheck = tintRow.findViewById<ImageView>(R.id.optionCheck)
    tintRow.setOnClickListener {
        val next = tintCheck.visibility != View.VISIBLE
        setScreensaverTintPref(next)
        tintCheck.visibility = if (next) View.VISIBLE else View.INVISIBLE
    }
    container.addView(tintRow)
}

private fun MPVActivity.showScreensaverLogoChooser(view: DialogScreensaverBinding) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val hasCustom = !prefs.getString(PREF_SCREENSAVER_LOGO_URI, null).isNullOrBlank()
    val logo = DialogScreensaverLogoBinding.inflate(layoutInflater)
    logo.logoMessage.setText(
        if (hasCustom) R.string.pref_screensaver_logo_custom else R.string.pref_screensaver_logo_default
    )
    logo.logoResetBtn.isVisible = hasCustom
    val dialog = AlertDialog.Builder(this).setView(logo.root).create()
    logo.logoChooseBtn.setOnClickListener {
        dialog.dismiss()
        pendingActivityResultCallback = { code, data ->
            val uriString = data?.getStringExtra("path")
            if (code == RESULT_OK && uriString != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                setScreensaverCustomLogo(uriString, tintDefault = false)
                buildScreensaverLogoSection(view)
            }
        }
        documentResultLauncher.launch(arrayOf("image/*"))
    }
    logo.logoResetBtn.setOnClickListener {
        dialog.dismiss()
        setScreensaverCustomLogo(null, tintDefault = true)
        buildScreensaverLogoSection(view)
    }
    logo.logoCancelBtn.setOnClickListener { dialog.dismiss() }
    showWidePlayerDialog(dialog, SCREENSAVER_LOGO_DIALOG_LAYOUT)
    logo.logoChooseBtn.requestFocus()
}

private fun MPVActivity.inflateScreensaverRow(
    parent: ViewGroup,
    title: String,
    detail: String?,
    selected: Boolean,
): View {
    val row = layoutInflater.inflate(R.layout.dialog_setting_option_item, parent, false)
    val titleView = row.findViewById<TextView>(R.id.optionTitleText)
    titleView.text = title
    titleView.maxLines = ROW_TITLE_MAX_LINES
    val detailView = row.findViewById<TextView>(R.id.optionDetailText)
    if (detail.isNullOrEmpty()) {
        detailView.visibility = View.GONE
    } else {
        detailView.text = detail
        detailView.maxLines = ROW_DETAIL_MAX_LINES
    }
    row.findViewById<ImageView>(R.id.optionCheck).visibility = if (selected) View.VISIBLE else View.INVISIBLE
    return row
}

private fun MPVActivity.screensaverModeTitle(mode: ScreensaverMode): String = getString(
    when (mode) {
        ScreensaverMode.OFF -> R.string.pref_screensaver_mode_off
        ScreensaverMode.DIM -> R.string.pref_screensaver_mode_dim
        ScreensaverMode.LOGO -> R.string.pref_screensaver_mode_logo
    }
)

private fun MPVActivity.screensaverModeDetail(mode: ScreensaverMode): String = getString(
    when (mode) {
        ScreensaverMode.OFF -> R.string.pref_screensaver_mode_off_detail
        ScreensaverMode.DIM -> R.string.pref_screensaver_mode_dim_detail
        ScreensaverMode.LOGO -> R.string.pref_screensaver_mode_logo_detail
    }
)
