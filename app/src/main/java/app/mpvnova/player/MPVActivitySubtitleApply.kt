package app.mpvnova.player


internal fun MPVActivity.applySubScaleProperty() {
    mpvSetPropertyDouble("sub-scale", subScaleSteps[subScaleLevel])
}

internal fun MPVActivity.applySubPosProperty() {
    mpvSetPropertyInt("sub-pos", subPosSteps[subPosLevel])
}

internal fun MPVActivity.applySecondaryPosProperty() {
    mpvSetPropertyInt("secondary-sub-pos", secondaryPosSteps[secondaryPosLevel])
}

internal fun MPVActivity.adjustSubScale(delta: Int): MediaPickerDialog.ValueState {
    val maxLevel = subScaleSteps.lastIndex
    subScaleLevel = (subScaleLevel + delta).coerceIn(0, maxLevel)
    applySubScaleProperty()
    writeSettings()
    showToast(
        getString(R.string.btn_sub_scale),
        getSubScaleLabel()
    )
    return currentSubScaleState()
}

internal fun MPVActivity.adjustSubPos(delta: Int): MediaPickerDialog.ValueState {
    val maxLevel = subPosSteps.lastIndex
    subPosLevel = (subPosLevel + delta).coerceIn(0, maxLevel)
    applySubPosProperty()
    writeSettings()
    showToast(getString(R.string.btn_sub_pos), getSubPosLabel())
    return currentSubPosState()
}

internal fun MPVActivity.adjustSecondaryPos(delta: Int): MediaPickerDialog.ValueState {
    // Defensive: should be greyed out by canDecrease/canIncrease but the
    // value would be meaningless without a secondary track on screen.
    if (player.secondarySid == -1) return currentSecondaryPosState()
    val maxLevel = secondaryPosSteps.lastIndex
    secondaryPosLevel = (secondaryPosLevel + delta).coerceIn(0, maxLevel)
    applySecondaryPosProperty()
    writeSettings()
    showToast(getString(R.string.btn_secondary_pos), getSecondaryPosLabel())
    return currentSecondaryPosState()
}

internal fun MPVActivity.adjustSecondarySub(delta: Int): MediaPickerDialog.ValueState {
    val available = availableSecondarySubTracks()
    if (available.isEmpty()) {
        return currentSecondarySubState()
    }
    // Cycle through: Off → track1 → track2 → ... → trackN → Off → ...
    // -1 represents the Off slot in this cycle. This lets the user step
    // forward/backward through every non-primary track instead of being
    // stuck with whatever mpv auto-picked when secondary first turned on.
    val cycle = listOf(-1) + available.map { it.mpvId }
    val current = player.secondarySid
    val currentIdx = cycle.indexOf(current).let { if (it < 0) 0 else it }
    // Modular arithmetic that handles negative deltas correctly.
    val step = if (delta == 0) 0 else delta
    val nextIdx = ((currentIdx + step) % cycle.size + cycle.size) % cycle.size
    val nextSid = cycle[nextIdx]
    player.secondarySid = nextSid

    val toastValue = if (nextSid == -1) {
        getString(R.string.status_off)
    } else {
        // Use the friendly track name in the toast so the user can tell
        // which language they just landed on, rather than just an id.
        available.firstOrNull { it.mpvId == nextSid }?.name ?: "#$nextSid"
    }
    showToast(getString(R.string.btn_secondary_sub), toastValue)
    return currentSecondarySubState()
}

internal fun MPVActivity.swapPrimaryAndSecondarySub() {
    val primary = player.sid
    val secondary = player.secondarySid
    // Nothing meaningful to swap if there's no secondary track active.
    if (secondary == -1) return
    // Clear secondary first so mpv doesn't briefly see the same track set
    // as both primary and secondary (it auto-rejects that state).
    player.secondarySid = -1
    player.sid = secondary
    if (primary != -1) {
        player.secondarySid = primary
    }
    showToast(
        getString(R.string.btn_secondary_sub),
        getString(R.string.status_swapped)
    )
}

internal fun MPVActivity.applySavedSubFilterDefaults() {
    if (!persistSubFilters) return
    mpvSetOptionString("sub-scale", subScaleSteps[subScaleLevel].toString())
    mpvSetOptionString("sub-pos", subPosSteps[subPosLevel].toString())
    mpvSetOptionString("secondary-sub-pos", secondaryPosSteps[secondaryPosLevel].toString())
}
