package rocks.pkb.plektra.inputmethod.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import rocks.pkb.plektra.SettingsManager
import rocks.pkb.plektra.inputmethod.TextSelectionHelper
import kotlin.math.abs

/** Intercepts horizontal drags while leaving taps and long presses to the row buttons. */
internal class CursorSwipeLayout(context: Context) : FrameLayout(context) {
    var connection: () -> InputConnection? = { null }
    var onCursorMoved: () -> Unit = {}
    /**
     * When set (pinyin candidates are showing), horizontal drags step through candidates instead
     * of moving the editor cursor. Returns whether the step moved anything.
     */
    var stepOverride: ((right: Boolean) -> Boolean)? = null
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x; startY = event.y; lastX = event.x; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - startX) > slop && abs(event.x - startX) > abs(event.y - startY)) {
                    dragging = stepOverride != null || connection() != null
                    if (dragging) parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            val step = stepOverride
            if (step != null) {
                // Candidates are wider than characters, so take a larger step than the cursor does.
                val candidateThreshold = (CANDIDATE_STEP_DP * resources.displayMetrics.density).coerceAtLeast(1f)
                while (abs(event.x - lastX) >= candidateThreshold) {
                    val right = event.x > lastX
                    lastX += if (right) candidateThreshold else -candidateThreshold
                    if (!step(right)) { lastX = event.x; break }
                }
                return true
            }
            val threshold = (SettingsManager.getSwipeIncrementalThreshold(context) *
                resources.displayMetrics.density).coerceAtLeast(1f)
            val input = connection() ?: return true
            while (abs(event.x - lastX) >= threshold) {
                val right = event.x > lastX
                val moved = if (right) TextSelectionHelper.moveCursorRight(input)
                    else TextSelectionHelper.moveCursorLeft(input)
                lastX += if (right) threshold else -threshold
                if (!moved) { lastX = event.x; break }
                onCursorMoved()
            }
        } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            dragging = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private companion object {
        const val CANDIDATE_STEP_DP = 32f
    }
}
