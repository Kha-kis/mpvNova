package app.mpvnova.player

import androidx.preference.PreferenceManager.getDefaultSharedPreferences

internal fun MPVActivity.applySavedDelayDefaults() {
    mpvSetOptionString("audio-delay", delaySecondsString(savedAudioDelayMs))
    mpvSetOptionString("sub-delay", delaySecondsString(savedSubDelayMs))
    mpvSetOptionString("secondary-sub-delay", delaySecondsString(savedSecondarySubDelayMs))
}

internal fun MPVActivity.saveAudioDelayDefault(delayMs: Long) {
    savedAudioDelayMs = delayMs
    getDefaultSharedPreferences(applicationContext)
        .edit()
        .putLong(PREF_AUDIO_DELAY_MS, delayMs)
        .apply()
    showToast(getString(R.string.audio_delay_remember), formatAudioDelayMs(delayMs))
}

internal fun MPVActivity.clearAudioDelayDefault() {
    savedAudioDelayMs = 0L
    getDefaultSharedPreferences(applicationContext)
        .edit()
        .remove(PREF_AUDIO_DELAY_MS)
        .apply()
    showToast(getString(R.string.audio_delay_clear_remembered), getString(R.string.delay_remember_cleared))
}

internal fun MPVActivity.saveSubDelayDefaults(primaryDelayMs: Long, secondaryDelayMs: Long?) {
    savedSubDelayMs = primaryDelayMs
    if (secondaryDelayMs != null)
        savedSecondarySubDelayMs = secondaryDelayMs
    getDefaultSharedPreferences(applicationContext)
        .edit()
        .putLong(PREF_SUB_DELAY_MS, primaryDelayMs)
        .also { editor ->
            if (secondaryDelayMs != null) {
                editor.putLong(PREF_SECONDARY_SUB_DELAY_MS, secondaryDelayMs)
            }
        }
        .apply()
    showToast(getString(R.string.sub_delay_remember), formatAudioDelayMs(primaryDelayMs))
}

internal fun MPVActivity.clearSubDelayDefaults() {
    savedSubDelayMs = 0L
    savedSecondarySubDelayMs = 0L
    getDefaultSharedPreferences(applicationContext)
        .edit()
        .remove(PREF_SUB_DELAY_MS)
        .remove(PREF_SECONDARY_SUB_DELAY_MS)
        .apply()
    showToast(getString(R.string.sub_delay_clear_remembered), getString(R.string.delay_remember_cleared))
}

private fun delaySecondsString(delayMs: Long): String {
    return (delayMs / MPV_MILLIS_PER_SECOND_DOUBLE).toString()
}
