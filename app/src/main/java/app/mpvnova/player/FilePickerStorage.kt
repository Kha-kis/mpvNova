package app.mpvnova.player

import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.io.File

internal fun FilePickerActivity.openFilePickerAtStorageVolume(
    activeFragment: MPVFilePickerFragment,
    defaultPath: File,
    hasExplicitDefaultPath: Boolean,
) {
    val volumes = loadStorageVolumes()
    val preferredVolume = volumes.find { defaultPath.startsWith(it.path) }
    val targetVolume = preferredVolume ?: volumes.firstOrNull()
    if (preferredVolume == null) {
        Log.w(FilePickerActivity.TAG, "default path set to \"$defaultPath\" but no such storage volume")
    }
    if (targetVolume == null) {
        openFallbackStorage(activeFragment, defaultPath)
        return
    }

    with(activeFragment) {
        root = targetVolume.path
        setRootLabel(targetVolume.description)
        goToDir(if (preferredVolume == null) targetVolume.path else defaultPath)
    }
    if (volumes.size > 1 && !hasExplicitDefaultPath)
        FilePickerMenuActions.showInitialStoragePicker(this, activeFragment, volumes)
}

private fun FilePickerActivity.loadStorageVolumes(): List<Utils.StoragePath> {
    return runCatching {
        Utils.getStorageVolumes(this)
    }.onFailure { error ->
        Log.w(FilePickerActivity.TAG, "FilePickerActivity: failed to enumerate storage volumes", error)
    }.getOrDefault(emptyList())
}

private fun FilePickerActivity.openFallbackStorage(
    activeFragment: MPVFilePickerFragment,
    defaultPath: File,
) {
    val primary = Environment.getExternalStorageDirectory()
    val target = defaultPath.takeIf { it.canRead() } ?: primary.takeIf { it.canRead() }
    if (target == null) {
        Log.e(FilePickerActivity.TAG, "can't find any readable storage volumes")
        Toast.makeText(this, R.string.storage_browser_unavailable, Toast.LENGTH_LONG).show()
        return
    }

    Log.w(FilePickerActivity.TAG, "FilePickerActivity: falling back to primary storage at \"$target\"")
    with(activeFragment) {
        root = if (target.startsWith(primary)) primary else target
        setRootLabel(getString(R.string.tv_tile_storage))
        goToDir(target)
    }
}
