package app.mpvnova.player

import app.mpvnova.player.databinding.DialogAudioDelayBinding
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible

internal class AudioDelayDialog {
    private lateinit var binding: DialogAudioDelayBinding
    var onAdjust: ((Long) -> Unit)? = null
    var onReset: (() -> Unit)? = null
    var onRemember: (() -> Unit)? = null
    var onClearRemembered: (() -> Unit)? = null
    var onApplyBluetooth: (() -> Unit)? = null
    var onClearBluetooth: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    fun buildView(layoutInflater: LayoutInflater): View {
        if (::binding.isInitialized) {
            binding.root.detachFromParent()
            return binding.root
        }
        binding = DialogAudioDelayBinding.inflate(layoutInflater)
        binding.audioDelayMinusBtn.setOnClickListener { onAdjust?.invoke(-AUDIO_DELAY_STEP_MS) }
        binding.audioDelayPlusBtn.setOnClickListener { onAdjust?.invoke(AUDIO_DELAY_STEP_MS) }
        binding.audioDelayResetBtn.setOnClickListener { onReset?.invoke() }
        binding.audioDelayRememberBtn.setOnClickListener { onRemember?.invoke() }
        binding.audioDelayClearRememberBtn.setOnClickListener { onClearRemembered?.invoke() }
        binding.audioDelayApplyBtBtn.setOnClickListener { onApplyBluetooth?.invoke() }
        binding.audioDelayClearBtBtn.setOnClickListener { onClearBluetooth?.invoke() }
        binding.audioDelayCloseBtn.setOnClickListener { onClose?.invoke() }
        return binding.root
    }

    fun update(
        currentDelayMs: Long,
        rememberedDelayMs: Long,
        bluetoothDelayMs: Long,
        bluetoothOutputActive: Boolean,
    ) {
        val context = binding.root.context
        binding.audioDelayCurrentValue.text = formatAudioDelayMs(currentDelayMs)
        binding.audioDelayRememberSummary.text = if (rememberedDelayMs == 0L) {
            context.getString(R.string.delay_remember_not_set)
        } else {
            context.getString(R.string.delay_remember_saved, formatAudioDelayMs(rememberedDelayMs))
        }
        binding.audioDelayClearRememberBtn.isVisible = rememberedDelayMs != 0L
        binding.audioDelayBtSummary.text = when {
            bluetoothDelayMs == 0L -> context.getString(R.string.audio_delay_bt_not_set)
            bluetoothOutputActive -> context.getString(
                R.string.audio_delay_bt_active,
                formatAudioDelayMs(bluetoothDelayMs),
            )
            else -> context.getString(
                R.string.audio_delay_bt_saved,
                formatAudioDelayMs(bluetoothDelayMs),
            )
        }
        binding.audioDelayClearBtBtn.isVisible = bluetoothDelayMs != 0L
    }
}

internal fun formatAudioDelayMs(valueMs: Long): String {
    return when {
        valueMs > 0L -> "+$valueMs ms"
        else -> "$valueMs ms"
    }
}
