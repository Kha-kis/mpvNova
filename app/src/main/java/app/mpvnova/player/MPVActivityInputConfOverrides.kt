package app.mpvnova.player

import android.view.KeyEvent
import java.io.File
import java.util.Locale

private const val INPUT_CONF_FILE_NAME = "input.conf"

internal data class InputConfFileState(
    val lastModified: Long,
    val length: Long,
)

internal data class InputConfOverrideKey(
    val eventKey: String,
    val dispatchKey: String,
)

internal fun MPVActivity.reloadInputConfOverrideKeys() {
    if (!seekKeysUseInputConf) {
        inputConfOverrideKeys = emptySet()
        inputConfOverrideState = null
        return
    }

    val file = File(filesDir, INPUT_CONF_FILE_NAME)
    val state = if (file.isFile) {
        InputConfFileState(file.lastModified(), file.length())
    } else {
        null
    }
    if (state == inputConfOverrideState) return

    inputConfOverrideState = state
    inputConfOverrideKeys = if (file.isFile) parseInputConfKeys(file) else emptySet()
}

internal fun MPVActivity.inputConfDispatchKey(event: KeyEvent): String? {
    val shouldCheckInputConf = seekKeysUseInputConf && !playerUiOwnsKeyInput()
    val key = if (shouldCheckInputConf) {
        reloadInputConfOverrideKeys()
        event.mpvInputConfKey()
    } else {
        null
    }
    return key?.let { eventKey ->
        inputConfOverrideKeys.firstOrNull { it.eventKey == eventKey }?.dispatchKey
    }
}

private fun MPVActivity.playerUiOwnsKeyInput(): Boolean {
    return skipButtonVisible || binding.controls.visibility == android.view.View.VISIBLE
}

private fun parseInputConfKeys(file: File): Set<InputConfOverrideKey> {
    return runCatching {
        file.useLines { lines -> parseInputConfOverrideKeys(lines) }
    }.getOrDefault(emptySet())
}

internal fun parseInputConfOverrideKeys(lines: Sequence<String>): Set<InputConfOverrideKey> =
    lines.mapNotNull(::inputConfKeyToken)
        .mapNotNull(::inputConfOverrideKey)
        .toSet()

private fun inputConfKeyToken(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) return null
    return trimmed.takeWhile { !it.isWhitespace() }
        .takeIf { it.isNotBlank() && !it.startsWith("#") }
}

private fun inputConfOverrideKey(token: String): InputConfOverrideKey? {
    val eventKey = normalizeMpvEventKey(token)
    return if (eventKey.isEmpty()) null else InputConfOverrideKey(eventKey, token)
}

private fun KeyEvent.mpvInputConfKey(): String? {
    val key = mpvKeyNameForEvent(this) ?: return null
    val parts = mutableListOf<String>()
    if (isShiftPressed) parts += "SHIFT"
    if (isCtrlPressed) parts += "CTRL"
    if (isAltPressed) parts += "ALT"
    if (isMetaPressed) parts += "META"
    parts += key
    return normalizeMpvEventKey(parts.joinToString("+"))
}

private fun normalizeMpvEventKey(raw: String): String {
    val parts = raw.split('+')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (parts.isEmpty()) return ""

    val key = normalizeMpvEventKeyName(parts.last())
    val modifiers = parts.dropLast(1)
        .mapNotNull(::normalizeMpvModifier)
        .distinct()
        .sortedBy { MODIFIER_ORDER.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
    return (modifiers + key).joinToString("+")
}

private fun normalizeMpvModifier(raw: String): String? {
    return when (raw.uppercase(Locale.US)) {
        "SHIFT" -> "SHIFT"
        "CTRL", "CONTROL" -> "CTRL"
        "ALT" -> "ALT"
        "META", "SUPER", "WIN" -> "META"
        else -> null
    }
}

private fun normalizeMpvEventKeyName(raw: String): String {
    return when (raw.uppercase(Locale.US)) {
        "DPAD_LEFT", "0X10001" -> "LEFT"
        "DPAD_RIGHT", "0X10003" -> "RIGHT"
        "DPAD_UP", "0X10000" -> "UP"
        "DPAD_DOWN", "0X10002" -> "DOWN"
        "DPAD_CENTER", "0X10004" -> "ENTER"
        else -> raw.uppercase(Locale.US)
    }
}

private val MODIFIER_ORDER = listOf("SHIFT", "CTRL", "ALT", "META")
