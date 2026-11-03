package rocks.pkb.plektra

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText

/**
 * Debug-only host for scripts/emulator-pinyin-test.sh: one focused text field whose content is
 * logged under the tag QingshuTest after every change.
 */
class QingshuTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The Titan Fn test holds Fn (key code 403) and needs Ctrl-hold navigation, as on the device.
        SettingsManager.setNavModeCtrlHoldEnabled(this, true)
        // A phrase for the custom-phrase test: typing "yx" in Chinese offers it.
        if (rocks.pkb.plektra.pinyin.QingshuPhrases.list(this).none { it.code == "yx" }) {
            rocks.pkb.plektra.pinyin.QingshuPhrases.add(this, "test@example.com", "yx")
        }
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES // like chat apps, so auto-cap is exercised
            textSize = 24f
            hint = "Qingshu test"
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    Log.i(TAG, "text=[${s?.toString().orEmpty()}]")
                }
            })
        }
        // The test presses Back to dismiss associations; never let it close this screen.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { Log.i(TAG, "back ignored") }
        }
        setContentView(field)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        field.requestFocus()
    }

    private companion object {
        const val TAG = "QingshuTest"
    }
}
