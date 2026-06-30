package app.mpvnova.player

import app.mpvnova.player.databinding.DialogSubDelayPanelBinding
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible

internal class SubDelayPanelDialog {
    private lateinit var binding: DialogSubDelayPanelBinding
    var onPrimaryAdjust: ((Long) -> Unit)? = null
    var onSecondaryAdjust: ((Long) -> Unit)? = null
    var onReset: (() -> Unit)? = null
    var onRemember: (() -> Unit)? = null
    var onClearRemembered: (() -> Unit)? = null
    var onVoiceHeard: (() -> Unit)? = null
    var onTextSeen: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    fun buildView(layoutInflater: LayoutInflater): View {
        if (::binding.isInitialized) {
            binding.root.detachFromParent()
            return binding.root
        }
        binding = DialogSubDelayPanelBinding.inflate(layoutInflater)
        binding.subDelayPrimaryMinusBtn.setOnClickListener { onPrimaryAdjust?.invoke(-SUB_DELAY_STEP_MS) }
        binding.subDelayPrimaryPlusBtn.setOnClickListener { onPrimaryAdjust?.invoke(SUB_DELAY_STEP_MS) }
        binding.subDelaySecondaryMinusBtn.setOnClickListener { onSecondaryAdjust?.invoke(-SUB_DELAY_STEP_MS) }
        binding.subDelaySecondaryPlusBtn.setOnClickListener { onSecondaryAdjust?.invoke(SUB_DELAY_STEP_MS) }
        binding.subDelayResetBtn.setOnClickListener { onReset?.invoke() }
        binding.subDelayRememberBtn.setOnClickListener { onRemember?.invoke() }
        binding.subDelayClearRememberBtn.setOnClickListener { onClearRemembered?.invoke() }
        binding.subDelayVoiceHeardBtn.setOnClickListener { onVoiceHeard?.invoke() }
        binding.subDelayTextSeenBtn.setOnClickListener { onTextSeen?.invoke() }
        binding.subDelayCloseBtn.setOnClickListener { onClose?.invoke() }
        return binding.root
    }

    fun update(
        primaryDelayMs: Long,
        secondaryDelayMs: Long?,
        rememberedPrimaryDelayMs: Long,
        rememberedSecondaryDelayMs: Long,
        voiceHeardMarked: Boolean,
        textSeenMarked: Boolean,
    ) {
        val context = binding.root.context
        binding.subDelayPrimaryValue.text = formatAudioDelayMs(primaryDelayMs)
        binding.subDelaySecondaryRow.isVisible = secondaryDelayMs != null
        if (secondaryDelayMs != null)
            binding.subDelaySecondaryValue.text = formatAudioDelayMs(secondaryDelayMs)
        binding.subDelayRememberSummary.text = if (rememberedPrimaryDelayMs == 0L &&
            rememberedSecondaryDelayMs == 0L) {
            context.getString(R.string.delay_remember_not_set)
        } else if (rememberedSecondaryDelayMs != 0L) {
            context.getString(
                R.string.sub_delay_remember_saved_dual,
                formatAudioDelayMs(rememberedPrimaryDelayMs),
                formatAudioDelayMs(rememberedSecondaryDelayMs),
            )
        } else {
            context.getString(R.string.delay_remember_saved, formatAudioDelayMs(rememberedPrimaryDelayMs))
        }
        binding.subDelayClearRememberBtn.isVisible = rememberedPrimaryDelayMs != 0L ||
            rememberedSecondaryDelayMs != 0L
        binding.subDelayVoiceHeardBtn.isActivated = voiceHeardMarked
        binding.subDelayTextSeenBtn.isActivated = textSeenMarked
        binding.subDelaySyncSummary.setText(
            when {
                voiceHeardMarked && !textSeenMarked -> R.string.sub_delay_sync_waiting_text
                textSeenMarked && !voiceHeardMarked -> R.string.sub_delay_sync_waiting_voice
                else -> R.string.sub_delay_sync_hint
            }
        )
    }
}
