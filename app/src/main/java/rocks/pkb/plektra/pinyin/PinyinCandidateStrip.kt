package rocks.pkb.plektra.pinyin

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The candidate row shown in the keyboard bar while composing pinyin.
 *
 * Shows one page of [PinyinController.PAGE_SIZE] candidates numbered 1–9 (the numbers match
 * Alt+W/E/R/S/D/F/X/C/V on the Titan 2), marks the highlighted one, and has ‹ › buttons when
 * there are more pages. Tapping a candidate selects it.
 */
class PinyinCandidateStrip(context: Context) : LinearLayout(context) {

    /** Called with the absolute candidate index (the engine's select() index). */
    var onCandidateTapped: ((Int) -> Unit)? = null
    var onPageRequested: ((forward: Boolean) -> Unit)? = null

    private var textColor: Int = Color.WHITE
    private var highlightColor: Int = 0x40FFFFFF

    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
    }
    private val row = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    private val previousButton = arrowButton("‹") { onPageRequested?.invoke(false) }
    private val nextButton = arrowButton("›") { onPageRequested?.invoke(true) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        scroller.addView(row)
        addView(previousButton)
        addView(scroller)
        addView(nextButton)
    }

    fun setColors(text: Int, highlight: Int = (text and 0x00FFFFFF) or 0x40000000) {
        textColor = text
        highlightColor = highlight
        previousButton.setTextColor(text)
        nextButton.setTextColor(text)
    }

    /**
     * Renders [candidates] (the whole list) starting at [pageStart], with [highlighted] as the
     * absolute index of the highlighted candidate.
     */
    fun render(candidates: List<String>, pageStart: Int, highlighted: Int) {
        row.removeAllViews()
        val pageEnd = minOf(candidates.size, pageStart + PinyinController.PAGE_SIZE)
        var highlightedView: View? = null
        for (index in pageStart until pageEnd) {
            val view = candidateView(index - pageStart + 1, candidates[index], index == highlighted, index)
            if (index == highlighted) highlightedView = view
            row.addView(view)
        }
        previousButton.visibility = if (pageStart > 0) View.VISIBLE else View.INVISIBLE
        nextButton.visibility = if (pageEnd < candidates.size) View.VISIBLE else View.INVISIBLE
        scroller.post {
            val target = highlightedView
            if (target == null) {
                scroller.scrollTo(0, 0)
            } else {
                val left = target.left - dp(16)
                val right = target.right + dp(16) - scroller.width
                when {
                    left < scroller.scrollX -> scroller.smoothScrollTo(left.coerceAtLeast(0), 0)
                    right > scroller.scrollX -> scroller.smoothScrollTo(right, 0)
                }
            }
        }
    }

    private fun candidateView(number: Int, text: String, highlighted: Boolean, index: Int): TextView {
        val label = SpannableStringBuilder().apply {
            append(number.toString())
            setSpan(RelativeSizeSpan(0.6f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan((textColor and 0x00FFFFFF) or 0x99000000.toInt()),
                0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            append(text)
        }
        return TextView(context).apply {
            this.text = label
            setTextColor(textColor)
            // Small enough that a page of nine usually fits the Titan 2's square screen.
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            includeFontPadding = false
            maxLines = 1
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = dp(1) }
            background = if (highlighted) {
                GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(highlightColor)
                }
            } else {
                null
            }
            contentDescription = "$number $text"
            setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCandidateTapped?.invoke(index)
            }
        }
    }

    private fun arrowButton(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            setPadding(dp(3), 0, dp(3), 0)
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.INVISIBLE
            setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
