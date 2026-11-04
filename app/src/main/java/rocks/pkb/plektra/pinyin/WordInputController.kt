package rocks.pkb.plektra.pinyin

import android.view.KeyEvent

/**
 * A language typed word by word from a dictionary (Japanese, Cantonese): the service routes keys,
 * the candidate bar and field events to whichever one is active.
 */
interface WordInputController {
    val active: Boolean
    val isComposing: Boolean

    data class Mods(val alt: Boolean, val ctrlOrMeta: Boolean, val altText: String?, val overlayActive: Boolean)

    fun updateActive(on: Boolean, urlField: Boolean)
    fun onKeyDown(keyCode: Int, event: KeyEvent?, mods: Mods): Boolean
    fun onBarTapped(index: Int)
    fun page(forward: Boolean, wrap: Boolean)
    fun step(forward: Boolean): Boolean
    fun commitHighlighted()
    fun onSelectionUpdate(newSelStart: Int, newSelEnd: Int, candidatesEnd: Int): Boolean
    fun onFinishInput()
}
