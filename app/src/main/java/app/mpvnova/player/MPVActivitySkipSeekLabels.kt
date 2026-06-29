package app.mpvnova.player

internal fun MPVActivity.skipSegmentsModeLabel(mode: SkipSegmentsMode): String = getString(
    when (mode) {
        SkipSegmentsMode.OFF -> R.string.skip_mode_off
        SkipSegmentsMode.AUTO -> R.string.skip_mode_auto
        SkipSegmentsMode.BUTTON -> R.string.skip_mode_button
    }
)

internal fun MPVActivity.skipSegmentsModeSummary(mode: SkipSegmentsMode): String = getString(
    when (mode) {
        SkipSegmentsMode.OFF -> R.string.skip_mode_off_summary
        SkipSegmentsMode.AUTO -> R.string.skip_mode_auto_summary
        SkipSegmentsMode.BUTTON -> R.string.skip_mode_button_summary
    }
)

internal fun MPVActivity.skipButtonDisplayModeLabel(
    mode: SkipButtonDisplayMode
): String = getString(
    when (mode) {
        SkipButtonDisplayMode.SEGMENT -> R.string.skip_button_display_segment
        SkipButtonDisplayMode.TEN_SECONDS -> R.string.skip_button_display_10s
        SkipButtonDisplayMode.THIRTY_SECONDS -> R.string.skip_button_display_30s
    }
)

internal fun MPVActivity.skipButtonDisplayModeSummary(
    mode: SkipButtonDisplayMode
): String = getString(
    when (mode) {
        SkipButtonDisplayMode.SEGMENT -> R.string.skip_button_display_segment_summary
        SkipButtonDisplayMode.TEN_SECONDS,
        SkipButtonDisplayMode.THIRTY_SECONDS -> R.string.skip_button_display_timed_summary
    }
)

internal fun MPVActivity.seekStepLabel(stepMs: Long): String =
    getString(R.string.seek_step_seconds_value, (stepMs / MILLIS_PER_SECOND_LONG).toInt())
