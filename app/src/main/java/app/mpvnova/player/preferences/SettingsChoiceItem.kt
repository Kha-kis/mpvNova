package app.mpvnova.player.preferences

internal data class SettingsChoiceItem(
    val title: CharSequence,
    val detail: CharSequence? = null,
    val checked: Boolean = false,
    val onClick: () -> Unit,
)
