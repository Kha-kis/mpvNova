package app.mpvnova.player

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToInt

internal fun MPVActivity.addExternalThing(cmd: String, result: Int, data: Intent?) {
    if (result != RESULT_OK)
        return
    val path = data!!.getStringExtra("path")!!
    val resolvedPath = if (path.startsWith("content://")) translateContentUri(Uri.parse(path)) else path
    mpvCommand(arrayOf(cmd, resolvedPath, "cached"))
}

internal fun MPVActivity.topMenuItems(restoreState: StateRestoreCallback): MutableList<MenuItem> {
    return mutableListOf(
        MenuItem(R.id.audioBtn) { openExternalAudio(restoreState) },
        MenuItem(R.id.subBtn) { openExternalSubtitle(restoreState) },
        MenuItem(R.id.playlistBtn) { openPlaylistMenu(restoreState); false },
        MenuItem(R.id.backgroundBtn) { sendPlaybackToBackground(restoreState) },
        MenuItem(R.id.chapterBtn) { openChapterMenu(restoreState) },
        MenuItem(R.id.chapterPrev) { seekChapterRelative(-1); true },
        MenuItem(R.id.chapterNext) { seekChapterRelative(1); true },
        MenuItem(R.id.advancedBtn) { openAdvancedMenu(restoreState); false }
    )
}

private fun MPVActivity.openExternalAudio(restoreState: StateRestoreCallback): Boolean {
    eventUiHandler.post {
        openFilePickerFor(R.string.open_external_audio) { result, data ->
            addExternalThing("audio-add", result, data)
            restoreState()
        }
    }
    return false
}

private fun MPVActivity.openExternalSubtitle(restoreState: StateRestoreCallback): Boolean {
    eventUiHandler.post {
        openFilePickerFor(R.string.open_external_sub) { result, data ->
            addExternalThing("sub-add", result, data)
            restoreState()
        }
    }
    return false
}

private fun MPVActivity.sendPlaybackToBackground(restoreState: StateRestoreCallback): Boolean {
    restoreState()
    backgroundPlayMode = "always"
    player.paused = false
    moveTaskToBack(true)
    return false
}

private fun MPVActivity.openChapterMenu(restoreState: StateRestoreCallback): Boolean {
    val chapters = player.loadChapters()
    if (chapters.isEmpty())
        return true
    val items = chapters.map {
        val timecode = Utils.prettyTime(it.time.roundToInt())
        val title = it.title?.takeIf { chapterTitle -> chapterTitle.isNotBlank() }
            ?: "${getString(R.string.chapter_button)} ${it.index + 1}"
        ChapterPickerDialog.Item(it.index, title, timecode)
    }
    val selected = mpvGetPropertyInt("chapter") ?: 0
    val impl = ChapterPickerDialog(items, selected)
    lateinit var dialog: AlertDialog
    impl.onItemPicked = { item ->
        mpvSetPropertyInt("chapter", item.index)
        dialog.dismiss()
    }
    impl.onCancelClick = { dialog.cancel() }
    dialog = with(AlertDialog.Builder(this)) {
        val inflater = layoutInflater
        setView(impl.buildView(inflater))
        setOnDismissListener { restoreState() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.46f,
            maxWidthDp = 560f,
            heightFraction = 0.62f,
            maxHeightDp = 540f,
        )
    )
    return false
}

internal fun MPVActivity.topMenuHiddenButtons(): MutableSet<Int> {
    val hiddenButtons = mutableSetOf<Int>()
    if (!isPlayingAudio)
        hiddenButtons.add(R.id.backgroundBtn)
    if ((mpvGetPropertyInt("chapter-list/count") ?: 0) == 0)
        hiddenButtons.add(R.id.rowChapter)
    return hiddenButtons
}
