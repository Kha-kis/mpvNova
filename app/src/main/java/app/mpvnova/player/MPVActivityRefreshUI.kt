package app.mpvnova.player

import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import java.io.File

internal fun MPVActivity.openAdvancedMenu(restoreState: StateRestoreCallback) {
    genericMenu(
        R.layout.dialog_advanced_menu,
        advancedMenuItems(restoreState),
        advancedHiddenButtons(),
        restoreState
    )
}

internal fun MPVActivity.openFilePickerFor(title: String, skip: Int?, callback: ActivityResultCallback) {
    if (skip == null) {
        openFileSourceDialog(title, callback)
        return
    }
    val intent = Intent(this, FilePickerActivity::class.java)
    intent.putExtra("title", title)
    intent.putExtra("allow_document", true)
    intent.putExtra("skip", skip)
    // start file picker at directory of current file
    val path = mpvGetPropertyString("path") ?: ""
    if (path.startsWith('/'))
        intent.putExtra("default_path", File(path).parent)

    pendingActivityResultCallback = callback
    filePickerResultLauncher.launch(intent)
}

internal fun MPVActivity.openFilePickerFor(@StringRes titleRes: Int, callback: ActivityResultCallback) {
    openFilePickerFor(getString(titleRes), null, callback)
}

private fun MPVActivity.openFileSourceDialog(title: String, callback: ActivityResultCallback) {
    lateinit var dialog: AlertDialog
    var launchedPicker = false
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_file_source, null)
    view.findViewById<TextView>(R.id.sourceTitle).text = title
    view.findViewById<Button>(R.id.fileBtn).setOnClickListener {
        launchedPicker = true
        dialog.dismiss()
        openFilePickerFor(title, FilePickerActivity.FILE_PICKER, callback)
    }
    view.findViewById<Button>(R.id.urlBtn).setOnClickListener {
        launchedPicker = true
        dialog.dismiss()
        openFilePickerFor(title, FilePickerActivity.URL_DIALOG, callback)
    }
    view.findViewById<Button>(R.id.docBtn).setOnClickListener {
        launchedPicker = true
        dialog.dismiss()
        pendingActivityResultCallback = callback
        documentResultLauncher.launch(arrayOf("*/*"))
    }
    dialog = with(AlertDialog.Builder(this)) {
        setView(view)
        setOnDismissListener {
            if (!launchedPicker)
                callback(RESULT_CANCELED, null)
        }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.56f,
            maxWidthDp = 620f,
        )
    )
}

internal fun MPVActivity.refreshUi() {
    // forces update of entire UI, used when resuming the activity
    updatePlaybackStatus(psc.pause)
    updatePlaybackTimeline(psc.position, forceTextUpdate = true)
    updatePlaybackDuration(psc.duration)
    updateAudioUI()
    refreshAllFilterTints()
    updateMetadataDisplay()
    updateDecoderButton()
    updateSpeedButton()
    updatePlaylistButtons()
    player.loadTracks()
    updateChapterMarkers()
}

internal fun MPVActivity.updateAudioUI() {
    // Note: prev/next now live in the button group at all times (TV redesign).
    // For the audio-only UI we just reorder the button group; for video mode
    // we use the full button row including the new audio-filter buttons.
    val audioButtons = arrayOf(R.id.prevBtn, R.id.cycleAudioBtn, R.id.playBtn,
            R.id.nextChapterBtn, R.id.cycleSpeedBtn, R.id.nextBtn)
    val videoButtons = arrayOf(R.id.playBtn, R.id.nextChapterBtn, R.id.prevBtn, R.id.nextBtn,
            R.id.cycleSubsBtn, R.id.cycleAudioBtn,
            R.id.cycleSpeedBtn, R.id.cycleDecoderBtn, R.id.statsToggleBtn,
            R.id.voiceBoostBtn, R.id.volumeBoostBtn, R.id.nightModeBtn, R.id.audioNormBtn)

    val shouldUseAudioUI = isPlayingAudioOnly()
    if (shouldUseAudioUI == useAudioUI)
        return
    useAudioUI = shouldUseAudioUI
    Log.v(MPV_ACTIVITY_TAG, "Audio UI: $useAudioUI")

    val buttonGroup = binding.controlsButtonGroup

    if (useAudioUI) {
        Utils.viewGroupReorder(buttonGroup, audioButtons)

        binding.controlsTitleGroup.visibility = View.VISIBLE
        Utils.viewGroupReorder(binding.controlsTitleGroup, arrayOf(R.id.titleTextView, R.id.minorTitleTextView))
        updateMetadataDisplay()

        showControls()
    } else {
        Utils.viewGroupReorder(buttonGroup, videoButtons)
        binding.controlsTitleGroup.visibility = View.GONE
        updateMetadataDisplay()

        hideControls()
    }

    updatePlaylistButtons()
}

internal fun MPVActivity.updateMetadataDisplay() {
    if (useAudioUI) {
        updatePlayerTitleOverlay()
        binding.titleTextView.setTextIfChanged(psc.meta.formatTitle())
        binding.minorTitleTextView.setTextIfChanged(psc.meta.formatArtistAlbum())
    } else if (showMediaTitle) {
        currentVideoTitle = resolveVlcStyleVideoTitle()
        updatePlayerTitleOverlay()
        binding.fullTitleTextView.setTextIfChanged(currentVideoTitle)
    } else {
        updatePlayerTitleOverlay()
    }
}

internal fun MPVActivity.updatePlayerTitleOverlay() {
    val title = currentVideoTitle?.trim().orEmpty()
    val shouldShow = !useAudioUI &&
        showMediaTitle &&
        title.isNotBlank() &&
        binding.controls.isVisible

    if (!shouldShow) {
        val wasVisible = binding.playerTitleOverlay.visibility == View.VISIBLE
        binding.playerTitleOverlay.setVisibilityIfChanged(View.GONE)
        if (wasVisible)
            updatePlayerToastPlacement()
        return
    }

    binding.playerTitlePrimary.setTextIfChanged(title)
    binding.playerTitleSecondary.setVisibilityIfChanged(View.GONE)
    updatePlayerTitleWidth()
    if (binding.playerTitleOverlay.alpha != 1f)
        binding.playerTitleOverlay.alpha = 1f
    val wasHidden = binding.playerTitleOverlay.visibility != View.VISIBLE
    binding.playerTitleOverlay.setVisibilityIfChanged(View.VISIBLE)
    if (wasHidden)
        updatePlayerToastPlacement()
}
