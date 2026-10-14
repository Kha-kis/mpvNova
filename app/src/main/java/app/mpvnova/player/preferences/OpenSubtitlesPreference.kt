package app.mpvnova.player.preferences

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import app.mpvnova.player.OpenSubtitlesClient
import app.mpvnova.player.OpenSubtitlesException
import app.mpvnova.player.OpenSubtitlesSettings
import app.mpvnova.player.R
import app.mpvnova.player.databinding.DialogOpensubtitlesLoginBinding
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.Executors
import java.util.concurrent.Future

class OpenSubtitlesPreference : PreferenceActivity.StyledPreferenceFragment(R.xml.pref_opensubtitles) {
    override fun onPreferencesLoaded() {
        val controller = OpenSubtitlesPreferenceController(this)
        lifecycle.addObserver(controller)
        controller.bind()
    }
}

private class OpenSubtitlesPreferenceController(
    private val fragment: OpenSubtitlesPreference,
) : DefaultLifecycleObserver {
    private val context = fragment.requireContext().applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val client = OpenSubtitlesClient()
    private var pending: Future<*>? = null
    private var generation = 0
    private var closed = false
    private var loginDialog: AlertDialog? = null
    private val keyPreference = requireNotNull(fragment.findPreference<Preference>("opensubtitles_api_key"))
    private val accountPreference = requireNotNull(fragment.findPreference<Preference>("opensubtitles_account"))
    private val signOutPreference = requireNotNull(fragment.findPreference<Preference>("opensubtitles_sign_out"))
    private val enabledPreference = requireNotNull(
        fragment.findPreference<SwitchPreferenceCompat>(OpenSubtitlesSettings.ENABLED),
    )

    fun bind() {
        keyPreference.setOnPreferenceClickListener { editKey(); true }
        accountPreference.setOnPreferenceClickListener { signIn(); true }
        signOutPreference.setOnPreferenceClickListener { signOut(); true }
        enabledPreference.setOnPreferenceChangeListener { _, _ ->
            OpenSubtitlesSettings.credentials(context) != null
        }
        fragment.findPreference<Preference>("opensubtitles_setup")?.setOnPreferenceClickListener {
            try {
                fragment.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.opensubtitles.com/en/consumers")))
            } catch (_: ActivityNotFoundException) {
                toast(R.string.os_settings_browser_unavailable)
            }
            true
        }
        refresh()
    }

    private fun refresh() {
        val credentials = OpenSubtitlesSettings.credentials(context)
        val busy = pending != null
        keyPreference.isEnabled = !busy
        keyPreference.setSummary(
            if (credentials == null) R.string.os_settings_key_missing else R.string.os_settings_key_saved,
        )
        enabledPreference.isEnabled = credentials != null && !busy
        accountPreference.isEnabled = credentials != null && !busy
        signOutPreference.isVisible = credentials?.token?.isNotBlank() == true
        signOutPreference.isEnabled = !busy
        val username = OpenSubtitlesSettings.username(context)
        accountPreference.summary = if (username.isBlank()) fragment.getString(R.string.os_settings_guest)
            else fragment.getString(R.string.os_settings_signed_in, username)
    }

    private fun editKey() {
        fragment.showSettingsInputDialog(
            title = fragment.getString(R.string.os_settings_api_key),
            message = fragment.getString(R.string.os_settings_key_help),
            initialValue = OpenSubtitlesSettings.credentials(context)?.apiKey.orEmpty(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            secure = true,
            showClearAll = true,
            validate = { if (it.isBlank() || OpenSubtitlesSettings.validApiKey(it.trim())) null
                else fragment.getString(R.string.os_settings_key_invalid) },
        ) { value -> verifyKey(value.trim()) }
    }

    private fun verifyKey(key: String) {
        if (key.isEmpty()) {
            cancel()
            try {
                OpenSubtitlesSettings.clear(context)
            } catch (_: IOException) {
                toast(R.string.os_settings_remove_failed)
            }
            enabledPreference.isChecked = false
            refresh()
            return
        }
        keyPreference.setSummary(R.string.os_settings_checking)
        runOperation(
            operation = { client.languages(app.mpvnova.player.OpenSubtitlesCredentials(key)); key },
            success = {
                if (OpenSubtitlesSettings.credentials(context)?.apiKey != it)
                    OpenSubtitlesSettings.saveVerifiedKey(context, it)
                toast(R.string.os_settings_key_saved)
            },
            failure = { toast(if ((it as? OpenSubtitlesException)?.status in
                listOf(HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN))
                R.string.os_settings_key_rejected else failureMessage(it)) },
        )
    }

    private fun signIn() {
        val credentials = OpenSubtitlesSettings.credentials(context) ?: return
        val binding = DialogOpensubtitlesLoginBinding.inflate(fragment.layoutInflater)
        binding.osUsername.setText(OpenSubtitlesSettings.username(context))
        val dialog = AlertDialog.Builder(fragment.requireContext()).setView(binding.root).create()
        loginDialog = dialog
        binding.osLoginCancel.setOnClickListener { dialog.dismiss() }
        binding.osLoginClear.setOnClickListener {
            cancel()
            binding.osUsername.text?.clear()
            binding.osPassword.text?.clear()
            binding.osUsername.error = null
            binding.osPassword.error = null
            binding.osLoginStatus.text = ""
            binding.osLoginConfirm.isEnabled = true
            binding.osUsername.requestFocus()
            refresh()
        }
        binding.osLoginConfirm.setOnClickListener {
            val username = binding.osUsername.text.toString().trim()
            val password = binding.osPassword.text.toString()
            if (username.isEmpty() || password.isEmpty()) {
                binding.osLoginStatus.setText(R.string.os_settings_credentials_required)
            } else {
                binding.osLoginConfirm.isEnabled = false
                binding.osLoginStatus.setText(R.string.os_settings_signing_in)
                runOperation(
                    operation = { client.login(credentials.apiKey, username, password) },
                    success = {
                        OpenSubtitlesSettings.saveSession(context, it)
                        binding.osPassword.text?.clear()
                        dialog.dismiss()
                    },
                    failure = {
                        binding.osPassword.text?.clear()
                        binding.osLoginConfirm.isEnabled = true
                        val unauthorized = (it as? OpenSubtitlesException)?.status ==
                            HttpURLConnection.HTTP_UNAUTHORIZED
                        binding.osLoginStatus.setText(if (unauthorized)
                            R.string.os_settings_login_failed else failureMessage(it))
                    },
                    saveFailure = {
                        binding.osPassword.text?.clear()
                        binding.osLoginConfirm.isEnabled = true
                        binding.osLoginStatus.setText(R.string.os_settings_save_failed)
                    },
                )
            }
        }
        dialog.setOnDismissListener {
            binding.osPassword.text?.clear()
            loginDialog = null
            cancel()
            if (!closed) refresh()
        }
        dialog.show()
        dialog.styleAsTvPanel()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding.osUsername.requestFocus()
    }

    private fun signOut() {
        val credentials = OpenSubtitlesSettings.credentials(context) ?: return
        try {
            OpenSubtitlesSettings.clearSession(context)
        } catch (_: IOException) {
            toast(R.string.os_settings_save_failed)
            return
        }
        refresh()
        runOperation(operation = { client.logout(credentials) }, success = {}, failure = {})
    }

    private fun <T> runOperation(
        operation: () -> T,
        success: (T) -> Unit,
        failure: (IOException) -> Unit,
        saveFailure: () -> Unit = { toast(R.string.os_settings_save_failed) },
    ) {
        cancel()
        val requestGeneration = generation
        pending = worker.submit {
            val result = try { Result.success(operation()) } catch (error: IOException) { Result.failure(error) }
            handler.post {
                if (closed || generation != requestGeneration || !fragment.isAdded) return@post
                pending = null
                result.fold(
                    onSuccess = { value ->
                        try { success(value) } catch (_: IOException) { saveFailure() }
                    },
                    onFailure = { failure(it as IOException) },
                )
                refresh()
            }
        }
        enabledPreference.isEnabled = false
        keyPreference.isEnabled = false
        accountPreference.isEnabled = false
        signOutPreference.isEnabled = false
    }

    private fun failureMessage(error: IOException): Int = when ((error as? OpenSubtitlesException)?.status) {
        HTTP_TOO_MANY_REQUESTS -> R.string.os_settings_rate_limit
        else -> R.string.os_settings_unavailable
    }

    private fun toast(resource: Int) = Toast.makeText(context, resource, Toast.LENGTH_LONG).show()

    private fun cancel() {
        generation++
        client.cancel()
        pending?.cancel(true)
        pending = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        closed = true
        loginDialog?.dismiss()
        cancel()
        worker.shutdownNow()
        handler.removeCallbacksAndMessages(null)
    }
}

private const val HTTP_TOO_MANY_REQUESTS = 429
