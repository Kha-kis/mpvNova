package app.mpvnova.player

import android.view.ViewGroup
import androidx.core.view.isVisible
import app.mpvnova.player.databinding.DialogMediaPickerBinding

internal fun DialogMediaPickerBinding.positionDelayRow(options: MediaPickerDialog.Options) {
    if (options.showDelay) {
        val parent = delayRow.parent as? ViewGroup
        if (parent != null) {
            val anchor = when {
                options.showFilters -> persistFiltersRow
                onlineSubtitleActions.isVisible -> onlineSubtitleActions
                else -> persistSubFiltersRow
            }
            val targetIndex = parent.indexOfChild(anchor) + 1
            if (parent.indexOfChild(delayRow) != targetIndex) {
                parent.removeView(delayRow)
                parent.addView(delayRow, parent.indexOfChild(anchor) + 1)
            }
        }
    }
}
