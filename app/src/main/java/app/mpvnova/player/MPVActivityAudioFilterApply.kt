package app.mpvnova.player


internal fun MPVActivity.refreshAllFilterTints() {
    refreshFilterTint(binding.voiceBoostBtn, isVoiceBoostOn())
    refreshFilterTint(binding.volumeBoostBtn, isVolumeBoostOn())
    refreshFilterTint(binding.nightModeBtn, isNightModeOn())
    refreshFilterTint(binding.audioNormBtn, isAudioNormOn())
}

internal fun MPVActivity.buildAudioFilterChain(): String {
    val filters = mutableListOf<String>()
    if (isNightModeOn()) {
        if (isDownmixOn())
            surroundDialogueDownmixFilter()?.let { filters += it }
        filters += buildDrcAudioStageFilter()
        if (isVoiceBoostOn())
            filters += drcVoiceBoostPresets[voiceBoostLevel]
    } else {
        if (isDownmixOn())
            surroundDialogueDownmixFilter()?.let { filters += it }
        if (isAudioNormOn())
            filters += audioNormPresets[audioNormLevel]
        if (isVoiceBoostOn())
            filters += voiceBoostPresets[voiceBoostLevel]
        if (isVolumeBoostOn())
            filters += volumeBoostFilter()
    }
    return filters.joinToString(",")
}

internal fun MPVActivity.applySavedAudioFilterDefaults() {
    val filterChain = if (persistAudioFilters) buildAudioFilterChain() else ""
    mpvSetOptionString("af", filterChain)
}

internal fun MPVActivity.applyAudioFilterState() {
    mpvSetPropertyString("af", buildAudioFilterChain())
}

internal fun MPVActivity.rebuildAudioFilters() {
    applyAudioFilterState()
}

internal fun MPVActivity.adjustVoiceBoost(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = voiceBoostPresets.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                voiceBoostLevel + delta > maxLevel -> 0
                voiceBoostLevel + delta < 0 -> maxLevel
                else -> voiceBoostLevel + delta
            }
        }
        else -> (voiceBoostLevel + delta).coerceIn(0, maxLevel)
    }
    voiceBoostLevel = nextLevel
    rebuildAudioFilters()
    refreshAllFilterTints()
    writeSettings()
    showToast(
        getString(R.string.btn_voice_boost),
        if (isVoiceBoostOn()) getVoiceBoostLabel() else getString(R.string.status_off)
    )
    return currentVoiceBoostState()
}

internal fun MPVActivity.adjustVolumeBoost(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val currentIndex = volumeBoostStepsDb.indexOf(volumeBoostDb).takeIf { it >= 0 } ?: 0
    val maxIndex = volumeBoostStepsDb.lastIndex
    val nextIndex = when {
        wrap -> {
            when {
                currentIndex + delta > maxIndex -> 0
                currentIndex + delta < 0 -> maxIndex
                else -> currentIndex + delta
            }
        }
        else -> (currentIndex + delta).coerceIn(0, maxIndex)
    }
    volumeBoostDb = volumeBoostStepsDb[nextIndex]
    rebuildAudioFilters()
    refreshAllFilterTints()
    writeSettings()
    showToast(
        getString(R.string.btn_volume_boost),
        if (isVolumeBoostOn()) getVolumeBoostLabel() else getString(R.string.status_off)
    )
    return currentVolumeBoostState()
}

internal fun MPVActivity.adjustDownmix(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = downmixPresetLabelIds.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                downmixLevel + delta > maxLevel -> 0
                downmixLevel + delta < 0 -> maxLevel
                else -> downmixLevel + delta
            }
        }
        else -> (downmixLevel + delta).coerceIn(0, maxLevel)
    }
    downmixLevel = nextLevel
    rebuildAudioFilters()
    writeSettings()
    showToast(
        getString(R.string.btn_dialogue_downmix),
        getDownmixLabel()
    )
    return currentDownmixState()
}
