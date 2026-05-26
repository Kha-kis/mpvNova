package app.mpvnova.player

import app.mpvnova.player.databinding.PlayerBinding
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.media.AudioFocusRequestCompat
import java.text.SimpleDateFormat

typealias ActivityResultCallback = (Int, Intent?) -> Unit
typealias StateRestoreCallback = () -> Unit

class MPVActivity : AppCompatActivity() {
    internal val eventUiHandler = Handler(Looper.getMainLooper())
    internal val fadeHandler = Handler(Looper.getMainLooper())
    internal val stopServiceHandler = Handler(Looper.getMainLooper())
    internal val clockHandler = Handler(Looper.getMainLooper())
    internal val periodicSaveHandler = Handler(Looper.getMainLooper())
    internal val periodicSaveRunnable = object : Runnable {
        override fun run() {
            // Both writes are no-ops when there's nothing to save (no file
            // loaded yet, paused at 0, EOF reached, etc.).
            savePosition()
            saveResumePosition()
            periodicSaveHandler.postDelayed(this, PERIODIC_SAVE_INTERVAL_MS)
        }
    }
    // ms to seek to on file-load if we restored from our resume table; 0 = no
    // restore happened. Drives the "Resumed from X:XX" toast.
    internal var pendingResumeToastMs = 0L
    // The start position we asked mpv to seek to (from intent or resume table).
    // Checked at FILE_LOADED against the actual duration so we can catch
    // near-end positions that slipped through parseIntentExtras.
    internal var pendingStartPositionMs = 0L
    // Source URL/path for the currently loaded file. Do not read Activity.intent
    // directly for resume saves because onNewIntent() can load another episode
    // while the Activity instance stays alive.
    internal var currentResumeSource: String? = null

    internal var activityIsStopped = false

    internal var activityIsForeground = true
    internal var didResumeBackgroundPlayback = false
    internal var userIsOperatingSeekbar = false
    internal var pendingSeekbarSeekMs: Long? = null
    internal var pendingDpadSeekPreviewMs: Long? = null
    internal var lastDisplayedPlaybackSecond = Int.MIN_VALUE
    internal var lastSeekbarProgress = Int.MIN_VALUE
    internal var lastSeekbarUiUpdateMs = 0L
    internal var lastDpadSeekApplyMs = 0L
    internal var lastAppliedSeekMs = Long.MIN_VALUE
    internal var lastClockInfoTick = Long.MIN_VALUE
    internal var lastDisplayedSpeed = Float.NaN
    @DrawableRes
    internal var lastPlayButtonIconRes = 0

    // mpv fires `time-pos/full` at video framerate (~60Hz) from its native
    // event thread. Posting a fresh lambda to the UI handler each time burns
    // allocations and main-thread cycles even though updatePlaybackTimeline()
    // throttles its real work to PLAYER_SEEKBAR_UI_INTERVAL_MS. Coalesce to a
    // single pending Runnable: subsequent events become no-ops until the UI
    // thread drains the pending update and clears the flag.
    @Volatile internal var timePosUiPending = false
    internal val timePosUiRunnable = Runnable {
        timePosUiPending = false
        if (!activityIsForeground) return@Runnable
        // When the controls overlay is hidden (the common case during
        // playback) every per-frame seekbar / text update is invisible work.
        // showControls() forces a full refresh, so stale state catches up
        // the instant the user surfaces the UI.
        if (binding.controls.visibility != View.VISIBLE) return@Runnable
        if (!userIsOperatingSeekbar && pendingSeekbarSeekMs == null && pendingDpadSeekPreviewMs == null)
            updatePlaybackTimeline(psc.position)
    }

    // True when the current device has no system bars to toggle, i.e. running
    // as a leanback / TV launcher. The insetsController.show/hide calls we'd
    // otherwise make in show/hideControls() are semantically no-ops there, but
    // they still propagate through the framework and trigger a window-decor
    // hitch — which is enough to make a CPU-starved SW Hi10p decoder underrun
    // every time the player UI opens. Computed once in onCreate.
    internal var isTvUiMode = false

    // mpv fires a burst of metadata properties (media-title, metadata,
    // artist, album, …) within a few ms when a file loads. Each one rebuilds
    // the whole title / artist / album text strip. Coalesce: many bursts
    // collapse into one updateMetadataDisplay() at the end of the queue.
    @Volatile internal var metadataUiPending = false
    internal val metadataUiRunnable = Runnable {
        metadataUiPending = false
        if (!activityIsForeground) return@Runnable
        updateMetadataDisplay()
    }

    // Same coalescing for MediaSession writes — each write builds a fresh
    // MediaMetadata + PlaybackState Parcel and ships it across IPC to
    // system_server. A burst of property updates during file load can fire
    // 5-10 of these; collapse them to a single end-of-queue update.
    @Volatile internal var mediaSessionUpdatePending = false
    internal val mediaSessionUpdateRunnable = Runnable {
        mediaSessionUpdatePending = false
        updateMediaSessionNow()
    }

    // Applying the Shield Hi10p decoder fallback rebuilds the VO + decoder.
    // The cascade takes ~3s during which audio would drain and underrun,
    // leaving A/V/subs misaligned forever (the symptom users describe as
    // "everything desyncs and only a rewind fixes it"). We pause around the
    // swap so audio can't drain, then wait for mpv's playback-restart event
    // (which signals the decoder is actually back online — a fixed delay
    // doesn't work because the cascade length is unpredictable) and only
    // then unpause + issue a tiny exact self-seek to flush both pipelines.
    internal var pendingShieldFallbackResync = false
    internal var shieldFallbackResumeAfter = false
    internal val shieldFallbackResyncRunnable = Runnable {
        if (!activityIsForeground && !didResumeBackgroundPlayback) return@Runnable
        val pos = mpvGetPropertyDouble("time-pos/full") ?: return@Runnable
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: realigning A/V at $pos")
        mpvCommand(arrayOf("seek", pos.toString(), "absolute+exact"))
        if (shieldFallbackResumeAfter) {
            shieldFallbackResumeAfter = false
            mpvSetPropertyBoolean("pause", false)
        }
    }

    internal var audioManager: AudioManager? = null
    internal var audioFocusRequest: AudioFocusRequestCompat? = null
    internal var audioFocusRestore: () -> Unit = {}

    internal val psc = Utils.PlaybackStateCache()
    internal var mediaSession: MediaSessionCompat? = null

    internal lateinit var binding: PlayerBinding
    internal val lifecycleObserver = MpvActivityLifecycleObserver(this)
    internal val mpvEventObserver = MpvActivityEventObserver(this)
    internal val mpvLogObserver = MpvActivityLogObserver(this)

    internal val player get() = binding.player

    internal val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser)
                return
            val positionMs = millisFromSeekbarProgress(progress)
            scheduleSeekbarSeek(positionMs)
            updatePlaybackTimeline(positionMs, forceTextUpdate = true)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = false
            commitPendingSeekbarSeek()
            showControls() // re-trigger display timeout
        }
    }

    internal val commitSeekbarSeekRunnable = Runnable {
        commitPendingSeekbarSeek()
    }

    internal var becomingNoisyReceiverRegistered = false
    internal val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, "noisy")
            }
        }
    }

    internal val fadeRunnable: ControlsFadeRunnable = object : ControlsFadeRunnable() {
        override var hasStarted = false
        private val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) { hasStarted = true }

            override fun onAnimationCancel(animation: Animator) { hasStarted = false }

            override fun onAnimationEnd(animation: Animator) {
                if (hasStarted)
                    hideControls()
                hasStarted = false
            }
        }

        override fun run() {
            // NOTE: we deliberately do NOT resume playback here. If the
            // controls overlay autopaused playback, the user must manually
            // press play to resume — autohide doesn't sneak it back into
            // a "playing while overlay still visible" state, and the user
            // stays in control of when playback actually resumes.

            // withLayer() promotes the view to LAYER_TYPE_HARDWARE for the
            // duration of the animation. Without it, each frame of the fade
            // forces the entire overlay (text, icons, drawables) to redraw
            // and alpha-blend onto the SurfaceView in software — brutal on
            // Tegra-class hardware. With it, the view rasterizes once to a
            // GPU texture and the per-frame work is a single compositing op.
            binding.topControls.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION).withLayer()
            binding.playerTitleOverlay.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION).withLayer()
            binding.controls.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION).setListener(listener).withLayer()
        }
    }

    internal val playerToastHideRunnable = Runnable {
        binding.playerToast.animate()
            .alpha(0f)
            .setDuration(PLAYER_TOAST_FADE_OUT_MS)
            .withLayer()
            .withEndAction { binding.playerToast.visibility = View.GONE }
    }

    internal val stopServiceRunnable = Runnable {
        val intent = Intent(this, BackgroundPlaybackService::class.java)
        applicationContext.stopService(intent)
    }

    internal val clockRunnable = object : Runnable {
        override fun run() {
            updateClockInfo()
            val now = System.currentTimeMillis()
            val delay = CLOCK_TICK_INTERVAL_MS - (now % CLOCK_TICK_INTERVAL_MS)
            clockHandler.postDelayed(this, delay.coerceAtLeast(MIN_CLOCK_TICK_DELAY_MS))
        }
    }

    internal var statsFPS = false
    internal var statsLuaMode = 0

    internal var backgroundPlayMode = ""
    internal var noUIPauseMode = ""

    internal var shouldSavePosition = false

    internal var controlsAtBottom = true
    internal var showMediaTitle = false
    internal var controlsDisplayTimeoutMs = DEFAULT_CONTROLS_DISPLAY_TIMEOUT
    internal var keepControlsVisibleWhilePaused = false
    internal var remoteNextChapterKeyCode: Int? = null
    internal var playerScreenBrightnessActive = false
    internal var rememberPlayerScreenBrightness = false
    internal var playerScreenBrightnessPercent = DEFAULT_PLAYER_SCREEN_BRIGHTNESS_PERCENT
    internal var rememberVideoContrast = false
    internal var videoContrastValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var rememberVideoGamma = false
    internal var videoGammaValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var rememberVideoSaturation = false
    internal var videoSaturationValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var useTimeRemaining = false
    internal var pendingItemTitle: String? = null
    internal var pendingFileName: String? = null
    internal var currentItemTitle: String? = null
    internal var currentVideoTitle: String? = null
    internal var cachedActiveFilterColor: Int? = null

    internal var ignoreAudioFocus = false
    internal var playlistExitWarning = true
    internal var newIntentReplace = false

    internal var persistAudioFilters = false
    internal var persistSubFilters = false
    // subScaleSteps index; default=1.0 at index 3
    internal var subScaleLevel = DEFAULT_SUB_SCALE_INDEX
    // subPosSteps index; default=100% at index 25 (the array spans -25%..125%)
    internal var subPosLevel = DEFAULT_SUB_POSITION_INDEX
    // secondaryPosSteps index; default=0% at index 5
    internal var secondaryPosLevel = DEFAULT_SECONDARY_SUB_POSITION_INDEX
    internal var sessionDecoderMode: String? = null
    internal var autoDecoderFallback = true
    internal var shieldDecoderModeEnabled = true
    internal var shieldDecoderFallback = MPVView.SHIELD_DECODER_FALLBACK_COPY
    internal var preferredDecoderMode = ""
    // User preference: auto-pause playback while the controls overlay is
    // visible. Applies to all files.
    internal var autoPauseControlsOverlayEnabled = false
    // User preference: also auto-pause on Shield when the file is Hi10p
    // H.264. Defaults on because the SW decoder there is too tight to share
    // CPU with the UI without drifting; can be turned off if the user
    // would rather keep playback running and accept the drift.
    internal var autoPauseShieldHi10pEnabled = true
    // True when we paused for the controls overlay AND the player was
    // playing at the time — restore to playing on hide.
    internal var controlsOverlayAutoPaused = false
    internal var audioNormUnderrunHintShown = false
    internal var gpuNextRenderFallbackStage = 0
    internal var gpuNextCopyRetryConfirmed = false
    internal var gpuNextCopyRetryDisplayedFrame = false
    // Sustained-error detector for gpu-next: a single transient libplacebo
    // log line (e.g. "failed creating pass" when the OSD overlay is added
    // on UI open) must not trip a renderer fallback — that fallback rebuilds
    // the VO mid-playback, which desyncs audio/video/subs because audio keeps
    // draining its buffer while video stalls.
    internal var gpuNextErrorWindowStartMs = 0L
    internal var gpuNextErrorWindowCount = 0


    internal var playbackHasStarted = false
    // Set true once mpv reports MPV_EVENT_END_FILE for the current file. Some
    // launchers, including Stremio's mpv parser, treat an OK result without
    // position/duration extras as completed playback.
    internal var eofWasReached = false
    internal var onloadCommands = mutableListOf<Array<String>>()
    internal var streamOpenLoading = false
    internal var streamCacheLoading = false
    internal var cachedChapters: List<MPVView.Chapter> = emptyList()
    internal var pendingChapterSeekTime: Double? = null
    internal val clearPendingChapterSeek = Runnable { pendingChapterSeekTime = null }

    // Activity lifetime

    override fun onCreate(icicle: Bundle?) {
        AppearanceTheme.applyPlayer(this)
        super.onCreate(icicle)
        lifecycle.addObserver(lifecycleObserver)

        // mpv can be launched directly from a file browser without going
        // through MainActivity, so the one-time setup that lives there
        // (asset copy, notification channel) has to be re-run here.
        Utils.copyAssets(this)
        createBackgroundPlaybackNotificationChannel(this)

        setupRootView()
        initListeners()
        readSettings()
        applyPlayerScreenBrightnessPreference()
        onConfigurationChanged(resources.configuration)
        setupImmersiveWindow()

        // Best-effort cleanup: drop stale resume entries before we add a
        // new one for this session. Runs in O(table size) which is bounded.
        pruneResumeTable()
        // Periodic position save during playback. Both savePosition() and
        // saveResumePosition() are no-ops when there's nothing meaningful
        // to persist, so it's safe to start the timer right away.
        periodicSaveHandler.postDelayed(periodicSaveRunnable, PERIODIC_SAVE_INTERVAL_MS)

        val filepath = parsePathFromIntent(intent)
        currentResumeSource = resumeSourceFromIntent(intent, filepath)
        prepareMediaTitleFromIntent(intent, filepath)
        if (intent.action == Intent.ACTION_VIEW) {
            parseIntentExtras(intent.extras)
        }
        addAutomaticSubtitleOptions(filepath)

        if (filepath == null) {
            Log.e(MPV_ACTIVITY_TAG, "No file given, exiting")
            showToast(getString(R.string.error_no_file))
            finishWithResult(RESULT_CANCELED)
            return
        }
        startPlayerForFile(filepath)
    }


    override fun onDestroy() {
        Log.v(MPV_ACTIVITY_TAG, "Exiting.")
        activityIsForeground = false
        cancelAllScheduledWork()
        if (becomingNoisyReceiverRegistered) {
            unregisterReceiver(becomingNoisyReceiver)
            becomingNoisyReceiverRegistered = false
        }
        releaseMediaAndAudioFocus()
        stopServiceRunnable.run()
        player.removeObserver(mpvEventObserver)
        removeMpvLogObserver(mpvLogObserver)
        player.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        Log.v(MPV_ACTIVITY_TAG, "onNewIntent($intent)")
        super.onNewIntent(intent)
        if (intent != null)
            setIntent(intent)
        pendingResumeToastMs = 0L

        val filepath = intent?.let { parsePathFromIntent(it) }
        if (filepath == null) {
            return
        }
        resetPlaybackResultState()
        val nextResumeSource = resumeSourceFromIntent(intent, filepath)
        val willReplaceCurrentFile = activityIsForeground || !didResumeBackgroundPlayback || this.newIntentReplace
        if (willReplaceCurrentFile) {
            applyNewIntentReplacement(intent, filepath, nextResumeSource)
        } else {
            onloadCommands.clear()
        }
        addAutomaticSubtitleOptions(filepath)

        if (!activityIsForeground && didResumeBackgroundPlayback) {
            applySavedAudioFilterDefaults()
            applySavedSubFilterDefaults()
            prepareStreamLoading(filepath)
            if (this.newIntentReplace) {
                mpvCommand(arrayOf("loadfile", filepath, "replace"))
                showToast(getString(R.string.notice_file_play))
            } else {
                mpvCommand(arrayOf("loadfile", filepath, "append"))
                showToast(getString(R.string.notice_file_appended))
            }
            moveTaskToBack(true)
        } else {
            applySavedAudioFilterDefaults()
            applySavedSubFilterDefaults()
            prepareStreamLoading(filepath)
            mpvCommand(arrayOf("loadfile", filepath))
        }
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isInPictureInPictureMode) {
                Log.v(MPV_ACTIVITY_TAG, "Playback continuing in picture-in-picture")
                super.onPause()
                return
            }
        }

        onPauseImpl()
    }

    internal fun onPauseImpl() {
        val fmt = mpvGetPropertyString("video-format")
        val shouldBackground = shouldBackground()
        if (shouldBackground && !fmt.isNullOrEmpty())
            BackgroundPlaybackService.thumbnail = mpvGrabThumbnail(THUMB_SIZE)
        else
            BackgroundPlaybackService.thumbnail = null
        // media session uses the same thumbnail. Flush synchronously because
        // we're about to purge the UI handler queue below.
        updateMediaSessionNow()

        activityIsForeground = false
        eventUiHandler.removeCallbacksAndMessages(null)
        timePosUiPending = false
        metadataUiPending = false
        mediaSessionUpdatePending = false
        if (isFinishing) {
            savePosition()
            saveResumePosition()
            // tell mpv to shut down so that any other property changes or such are ignored,
            // preventing useless busywork
            mpvCommand(arrayOf("stop"))
        } else if (!shouldBackground) {
            player.paused = true
        }
        writeSettings()
        super.onPause()

        didResumeBackgroundPlayback = shouldBackground
        if (shouldBackground) {
            Log.v(MPV_ACTIVITY_TAG, "Resuming playback in background")
            stopServiceHandler.removeCallbacks(stopServiceRunnable)
            val serviceIntent = Intent(this, BackgroundPlaybackService::class.java)
            if (!tryStartForegroundService(serviceIntent)) {
                didResumeBackgroundPlayback = false
                player.paused = true
            }
        }
    }

    override fun onResume() {
        // If we never actually left the foreground, don't reinitialize playback state.
        if (activityIsForeground) {
            super.onResume()
            return
        }

        hideControls()
        readSettings()
        applyPlayerScreenBrightnessPreference()

        activityIsForeground = true
        stopServiceHandler.removeCallbacks(stopServiceRunnable)
        stopServiceHandler.postDelayed(stopServiceRunnable, BACKGROUND_SERVICE_STOP_DELAY_MS)

        refreshUi()

        super.onResume()
    }

    // UI

    /** dpad navigation */
    internal var btnSelected = -1
    internal val dpadControlsScratch = ArrayList<View>(DPAD_CONTROLS_SCRATCH_CAPACITY)
    internal var pendingDpadLongClickView: View? = null
    internal var pendingDpadLongClickRunnable: Runnable? = null
    internal var dpadLongClickPerformed = false

    internal var mightWantToToggleControls = false

    /** true if we're actually outputting any audio (includes the mute state, but not pausing) */
    internal var isPlayingAudio = false

    internal var useAudioUI = false

    internal var clockFormatter: SimpleDateFormat? = null
    internal var clockFormatterIs24: Boolean? = null

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        // try built-in event handler first, forward all other events to libmpv
        val handled = interceptDpad(ev) ||
            interceptRemoteNextChapterButton(ev) ||
            (ev.action == KeyEvent.ACTION_DOWN && interceptKeyDown(ev)) ||
            player.onKey(ev)
        return handled || super.dispatchKeyEvent(ev)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        binding.controls.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = if (!controlsAtBottom) {
                Utils.convertDp(this@MPVActivity, FLOATING_CONTROLS_BOTTOM_MARGIN_DP)
            } else {
                0
            }
            leftMargin = if (!controlsAtBottom) {
                Utils.convertDp(
                    this@MPVActivity,
                    FLOATING_CONTROLS_SIDE_MARGIN_LANDSCAPE_DP
                )
            } else {
                0
            }
            rightMargin = leftMargin
        }
    }

    // ========================================================================
    // Audio filter toggles (Voice Boost / DRC / Audio Normalization)
    // DRC mirrors the recovered native dynaudnorm stage as closely as we can in
    // mpv. It is treated as a primary dynamics stage and kept mutually
    // exclusive with Audio Normalization so the UI matches the active filter
    // chain instead of silently suppressing one stage behind the scenes.
    // ========================================================================
    internal var voiceBoostLevel = 0
    internal var volumeBoostDb = 0
    internal var nightModeLevel = 0
    internal var audioNormLevel = 0
    internal var downmixLevel = 0

    // Filter slot labels + preset chain arrays moved to AudioFilterPresets.kt
    // — they're constant strings with no per-activity state. The mutable
    // level vars above (voiceBoostLevel, volumeBoostDb, etc.) stay here
    // because they change during a playback session.

    // ===== Subtitle filter presets & state =====

    // Default (1.0x) is at index 3.
    internal val subScaleSteps = SUB_SCALE_STEPS

    // -25..125 range in 5% steps. The on-screen range is 0..100% but we let
    // the user keep clicking past those edges (mpv soft-clamps `sub-pos` to the
    // visible range) so they can dial in extreme values without the buttons
    // bouncing focus on them. Index 5 = 0% (top edge), index 25 = 100% (bottom
    // edge). Same array drives both primary and secondary positions.
    internal val subPosSteps = SUB_POSITION_STEPS
    internal val secondaryPosSteps = subPosSteps


    // Track memory (subtitle / audio reapply on file load) lives in
    // MPVActivityTrackMemory.kt and the pure scoring logic in
    // TrackTitleMatching.kt.

    internal var pendingActivityResultCallback: ActivityResultCallback? = null
    internal val filePickerResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            pendingActivityResultCallback?.invoke(it.resultCode, it.data)
            pendingActivityResultCallback = null
        }
    internal val documentResultLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val result = uri?.let { Intent().putExtra("path", it.toString()) }
            pendingActivityResultCallback?.invoke(
                if (uri != null) RESULT_OK else RESULT_CANCELED,
                result
            )
            pendingActivityResultCallback = null
        }

    // Chapter handling lives in MPVActivityChapters.kt; PiP handling in
    // MPVActivityPiP.kt.

    // Media Session handling

    internal val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPause() {
            player.paused = true
        }
        override fun onPlay() {
            player.paused = false
        }
        override fun onSeekTo(pos: Long) {
            player.timePos = (pos / MPV_MILLIS_PER_SECOND_DOUBLE)
        }
        override fun onSkipToNext() = playlistNext()
        override fun onSkipToPrevious() = playlistPrev()
        override fun onSetRepeatMode(repeatMode: Int) {
            mpvSetPropertyString("loop-playlist",
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) "inf" else "no")
            mpvSetPropertyString("loop-file",
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) "inf" else "no")
        }
        override fun onSetShuffleMode(shuffleMode: Int) {
            player.changeShuffle(false, shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
        }
    }
}
