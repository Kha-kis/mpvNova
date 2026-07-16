package app.mpvnova.player.preferences

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import app.mpvnova.player.AppearanceTheme
import app.mpvnova.player.BuildConfig
import app.mpvnova.player.MpvLogLevel
import app.mpvnova.player.MpvLogObserver
import app.mpvnova.player.MpvLogRingBuffer
import app.mpvnova.player.MpvRuntimeOwnership
import app.mpvnova.player.R
import app.mpvnova.player.addMpvLogObserver
import app.mpvnova.player.databinding.ActivityAboutBinding
import app.mpvnova.player.mpvCreate
import app.mpvnova.player.mpvDestroy
import app.mpvnova.player.mpvInit
import app.mpvnova.player.removeMpvLogObserver

class AboutActivity : AppCompatActivity(), MpvLogObserver {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(app.mpvnova.player.UiScale.wrap(newBase))
    }

    private lateinit var binding: ActivityAboutBinding
    private var logs = ""
    private var ownsMpv = false

    @Suppress("TooGenericExceptionCaught")
    override fun onCreate(savedInstanceState: Bundle?) {
        AppearanceTheme.applyPreferences(this)
        super.onCreate(savedInstanceState)

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (preferences.getBoolean("material_you_theming", false))
            DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        logs = "mpvNova ${BuildConfig.VERSION_NAME} / ${BuildConfig.VERSION_CODE} (${BuildConfig.BUILD_TYPE})\n"

        if (MpvRuntimeOwnership.tryAcquire(this)) {
            ownsMpv = true
            try {
                mpvCreate(applicationContext)
                addMpvLogObserver(this)
                mpvInit()
            } catch (error: Throwable) {
                removeMpvLogObserver(this)
                runCatching { mpvDestroy() }
                ownsMpv = false
                MpvRuntimeOwnership.release(this)
                throw error
            }
        } else {
            logs += MpvLogRingBuffer.latestEnabledFeatures()
                ?: getString(R.string.about_features_unavailable_during_playback)
            updateLog()
        }
    }

    private fun updateLog() {
        runOnUiThread {
            binding.logs.text = logs
        }
    }

    override fun onDestroy() {
        // logMessage removes the observer once the features line arrives, but the
        // user can back out before that — without this the destroyed activity
        // stays registered as a log observer for the rest of the process.
        removeMpvLogObserver(this)
        if (ownsMpv) {
            try {
                mpvDestroy()
            } finally {
                ownsMpv = false
                MpvRuntimeOwnership.release(this)
            }
        }
        super.onDestroy()
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (prefix != "cplayer")
            return
        if (level == MpvLogLevel.MPV_LOG_LEVEL_V)
            logs += text

        if (text.startsWith("List of enabled features:", true)) {
            removeMpvLogObserver(this)
            updateLog()
        }
    }
}
