package app.mpvnova.player

import app.mpvnova.player.databinding.DialogOptionItemBinding
import app.mpvnova.player.databinding.DialogOptionListBinding
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import kotlin.math.abs

// mpv reports aspect overrides as floats, so a value is "selected" when it lands
// within this tolerance of an array entry (enough to tell 16:9 from 16:10/4:3).
private const val ASPECT_RATIO_MATCH_TOLERANCE = 0.01

internal fun MPVActivity.openAspectMenu(restoreState: StateRestoreCallback): Boolean {
    val ratios = resources.getStringArray(R.array.aspect_ratios)
    val names = resources.getStringArray(R.array.aspect_ratio_names)
    val currentRatio = currentAspectRatioChoice()
    val binding = DialogOptionListBinding.inflate(layoutInflater)
    lateinit var dialog: AlertDialog
    binding.optionTitle.setText(R.string.aspect_ratio)
    binding.cancelBtn.setOnClickListener { dialog.cancel() }
    for (index in names.indices) {
        val itemBinding = DialogOptionItemBinding.inflate(
            layoutInflater,
            binding.optionsContainer,
            false
        )
        val isSelected = ratios[index] == currentRatio
        itemBinding.optionText.text = names[index]
        itemBinding.optionCheck.isVisible = isSelected
        itemBinding.root.isActivated = isSelected
        itemBinding.root.setOnClickListener {
            applyAspectRatioChoice(ratios[index])
            dialog.dismiss()
        }
        binding.optionsContainer.addView(itemBinding.root)
    }
    handleInsetsAsPadding(binding.root)
    dialog = with(AlertDialog.Builder(this)) {
        setView(binding.root)
        setOnDismissListener { restoreState(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(dialog, aspectRatioDialogLayout())
    return false
}

private fun MPVActivity.currentAspectRatioChoice(): String {
    val panscan = mpvGetPropertyDouble("panscan") ?: 0.0
    if (panscan > 0.0)
        return "panscan"
    val override = mpvGetPropertyDouble("video-aspect-override") ?: -1.0
    val ratios = resources.getStringArray(R.array.aspect_ratios)
    return ratios.firstOrNull { entry ->
        val value = aspectRatioEntryValue(entry)
        value != null && override > 0.0 && abs(value - override) < ASPECT_RATIO_MATCH_TOLERANCE
    } ?: "-1"
}

private fun aspectRatioEntryValue(entry: String): Double? {
    if (entry.contains(':')) {
        val parts = entry.split(':')
        val width = parts.getOrNull(0)?.toDoubleOrNull()
        val height = parts.getOrNull(1)?.toDoubleOrNull()
        return if (width != null && height != null && height != 0.0) width / height else null
    }
    return entry.toDoubleOrNull()
}

private fun applyAspectRatioChoice(ratio: String) {
    if (ratio == "panscan") {
        mpvSetPropertyString("video-aspect-override", "-1")
        mpvSetPropertyDouble("panscan", 1.0)
    } else {
        mpvSetPropertyString("video-aspect-override", ratio)
        mpvSetPropertyDouble("panscan", 0.0)
    }
}

private fun aspectRatioDialogLayout(): PlayerDialogLayout {
    return PlayerDialogLayout(
        widthFraction = 0.56f,
        maxWidthDp = 620f,
    )
}
