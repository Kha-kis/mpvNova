package app.mpvnova.player

import app.mpvnova.player.databinding.DialogSubtitleStyleBinding
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView

/**
 * View layer for the subtitle style customizer, built from the same row
 * components as the other player panels (icon chips, card rows, stepper icon
 * buttons, check toggles). Pure UI: each control reports a [Control] + delta to
 * [onAdjust] and re-renders from the [State] the caller returns. mpv writes and
 * persistence live on MPVActivity (MPVActivitySubtitleStyle*.kt).
 */
internal class SubtitleStyleDialog {

    enum class Control {
        MASTER,
        TEXT_COLOR,
        TEXT_OPACITY,
        EDGE,
        OUTLINE_COLOR,
        OUTLINE_SIZE,
        BG_OPACITY,
        BG_COLOR,
        FONT,
        OVERRIDE_ASS,
    }

    /** One stepper row's display state. [chipRgb] non-null draws a colour swatch chip. */
    data class Row(val value: String, val enabled: Boolean = true, val chipRgb: Int? = null)

    data class State(
        val masterOn: Boolean,
        val textColor: Row,
        val textOpacity: Row,
        val edge: Row,
        val outlineColor: Row,
        val outlineSize: Row,
        val bgOpacity: Row,
        val bgColor: Row,
        val font: Row,
        val overrideOn: Boolean,
        val overrideEnabled: Boolean,
    )

    private lateinit var binding: DialogSubtitleStyleBinding

    /** Adjust a control by delta (-1/+1); toggles ignore delta and flip. Returns the new full state. */
    var onAdjust: ((Control, Int) -> State)? = null
    var stateProvider: (() -> State)? = null

    /** Open the file picker to import a user font. */
    var onAddFont: (() -> Unit)? = null

    /** Open the list of imported fonts to remove one. */
    var onRemoveFont: (() -> Unit)? = null

    fun buildView(layoutInflater: LayoutInflater): View {
        binding = DialogSubtitleStyleBinding.inflate(layoutInflater)
        bindControls()
        stateProvider?.invoke()?.let { render(it) }
        return binding.root
    }

    private fun bindControls() {
        val b = binding
        b.masterRow.setOnClickListener { adjust(Control.MASTER, 1) }
        b.textColorMinusBtn.setOnClickListener { adjust(Control.TEXT_COLOR, -1) }
        b.textColorPlusBtn.setOnClickListener { adjust(Control.TEXT_COLOR, 1) }
        b.textOpacityMinusBtn.setOnClickListener { adjust(Control.TEXT_OPACITY, -1) }
        b.textOpacityPlusBtn.setOnClickListener { adjust(Control.TEXT_OPACITY, 1) }
        b.edgeMinusBtn.setOnClickListener { adjust(Control.EDGE, -1) }
        b.edgePlusBtn.setOnClickListener { adjust(Control.EDGE, 1) }
        b.outlineColorMinusBtn.setOnClickListener { adjust(Control.OUTLINE_COLOR, -1) }
        b.outlineColorPlusBtn.setOnClickListener { adjust(Control.OUTLINE_COLOR, 1) }
        b.outlineSizeMinusBtn.setOnClickListener { adjust(Control.OUTLINE_SIZE, -1) }
        b.outlineSizePlusBtn.setOnClickListener { adjust(Control.OUTLINE_SIZE, 1) }
        b.bgOpacityMinusBtn.setOnClickListener { adjust(Control.BG_OPACITY, -1) }
        b.bgOpacityPlusBtn.setOnClickListener { adjust(Control.BG_OPACITY, 1) }
        b.bgColorMinusBtn.setOnClickListener { adjust(Control.BG_COLOR, -1) }
        b.bgColorPlusBtn.setOnClickListener { adjust(Control.BG_COLOR, 1) }
        b.fontMinusBtn.setOnClickListener { adjust(Control.FONT, -1) }
        b.fontPlusBtn.setOnClickListener { adjust(Control.FONT, 1) }
        b.addFontRow.setOnClickListener { onAddFont?.invoke() }
        b.removeFontRow.setOnClickListener { onRemoveFont?.invoke() }
        b.overrideAssRow.setOnClickListener { adjust(Control.OVERRIDE_ASS, 1) }
    }

    private fun adjust(control: Control, delta: Int) {
        onAdjust?.invoke(control, delta)?.let { render(it) }
    }

    private fun render(state: State) {
        val b = binding
        b.masterCheck.visibility = checkVisibility(state.masterOn)

        renderStepper(
            b.textColorRow, b.textColorValue, b.textColorMinusBtn, b.textColorPlusBtn, state.textColor,
        )
        applyChip(b.textColorChip, state.textColor)
        renderStepper(
            b.textOpacityRow, b.textOpacityValue, b.textOpacityMinusBtn, b.textOpacityPlusBtn, state.textOpacity,
        )
        renderStepper(
            b.edgeRow, b.edgeValue, b.edgeMinusBtn, b.edgePlusBtn, state.edge,
        )
        renderStepper(
            b.outlineColorRow, b.outlineColorValue, b.outlineColorMinusBtn, b.outlineColorPlusBtn, state.outlineColor,
        )
        applyChip(b.outlineColorChip, state.outlineColor)
        renderStepper(
            b.outlineSizeRow, b.outlineSizeValue, b.outlineSizeMinusBtn, b.outlineSizePlusBtn, state.outlineSize,
        )
        renderStepper(
            b.bgOpacityRow, b.bgOpacityValue, b.bgOpacityMinusBtn, b.bgOpacityPlusBtn, state.bgOpacity,
        )
        renderStepper(
            b.bgColorRow, b.bgColorValue, b.bgColorMinusBtn, b.bgColorPlusBtn, state.bgColor,
        )
        applyChip(b.bgColorChip, state.bgColor)
        renderStepper(
            b.fontRow, b.fontValue, b.fontMinusBtn, b.fontPlusBtn, state.font,
        )

        b.overrideAssRow.alpha = rowAlpha(state.overrideEnabled)
        b.overrideAssRow.isClickable = state.overrideEnabled
        b.overrideAssCheck.visibility = checkVisibility(state.overrideOn && state.overrideEnabled)
    }

    private fun renderStepper(row: View, value: TextView, minus: ImageButton, plus: ImageButton, state: Row) {
        value.text = state.value
        row.alpha = rowAlpha(state.enabled)
        // Keep buttons focusable so D-pad focus doesn't jump when a row dims.
        minus.isClickable = state.enabled
        plus.isClickable = state.enabled
    }

    private fun applyChip(chip: View, state: Row) {
        val rgb = state.chipRgb
        if (rgb == null) {
            chip.visibility = View.GONE
            return
        }
        chip.visibility = View.VISIBLE
        chip.setBackgroundColor(OPAQUE_ALPHA or (rgb and RGB_MASK))
    }

    private fun checkVisibility(on: Boolean) = if (on) View.VISIBLE else View.INVISIBLE

    private fun rowAlpha(enabled: Boolean) = if (enabled) 1f else DISABLED_ALPHA

    companion object {
        private const val DISABLED_ALPHA = 0.4f
        private const val OPAQUE_ALPHA = -0x1000000 // 0xFF000000 as Int
        private const val RGB_MASK = 0xFFFFFF
    }
}
