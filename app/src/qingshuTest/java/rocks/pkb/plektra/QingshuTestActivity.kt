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
 * Test host for scripts/emulator-pinyin-test.sh and scripts/emulator-screenshots.sh (debug and
 * QA builds only): one focused text field whose content is logged under the tag QingshuTest after
 * every change.
 */
class QingshuTestActivity : Activity() {
    private lateinit var field: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The Titan Fn test holds Fn (key code 403) and needs Ctrl-hold navigation, as on the device.
        SettingsManager.setNavModeCtrlHoldEnabled(this, true)
        // A phrase for the custom-phrase test: typing "yx" in Chinese offers it.
        if (rocks.pkb.plektra.pinyin.QingshuPhrases.list(this).none { it.code == "yx" }) {
            rocks.pkb.plektra.pinyin.QingshuPhrases.add(this, "test@example.com", "yx")
        }
        field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES // like chat apps, so auto-cap is exercised
            textSize = 20f
            hint = "Type here"
            setPadding(40, 40, 40, 40)
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

    /** `am start -f 0x20000000 ... --es traditional taiwan` switches the output form mid-test. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("traditional")?.let {
            rocks.pkb.plektra.pinyin.QingshuChineseSettings.setTraditional(this, it)
            Log.i(TAG, "traditional=$it")
        }
        intent.getStringExtra("languages")?.let {
            rocks.pkb.plektra.pinyin.QingshuMode.setExtraLanguages(this, it.split(',').filter(String::isNotBlank).toSet())
            Log.i(TAG, "languages=$it")
        }
        intent.getStringExtra("corner")?.let {
            rocks.pkb.plektra.pinyin.QingshuChineseSettings.setCornerQuotes(this, it == "on")
            Log.i(TAG, "corner=$it")
        }
        if (intent.getBooleanExtra("clear", false)) field.setText("")
        intent.getStringExtra("hint")?.let { field.hint = it }
        when (intent.getStringExtra("open")) {
            "chinese_settings" -> startActivity(android.content.Intent(this, rocks.pkb.plektra.pinyin.QingshuChineseSettingsActivity::class.java))
            "phrases" -> startActivity(android.content.Intent(this, rocks.pkb.plektra.pinyin.QingshuPhrasesActivity::class.java))
        }
        intent.getStringExtra("url")?.let {
            // Like a browser address bar; setInputType restarts input, so the keyboard re-reads it.
            field.inputType = InputType.TYPE_CLASS_TEXT or
                if (it == "on") InputType.TYPE_TEXT_VARIATION_URI
                else InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            field.setSelection(field.text.length)
            Log.i(TAG, "url=$it")
        }
        intent.getStringExtra("fuzzy")?.let {
            rocks.pkb.plektra.pinyin.QingshuChineseSettings.setFuzzy(this, it.split(',').filter(String::isNotBlank).toSet())
            Log.i(TAG, "fuzzy=$it")
        }
    }

    private companion object {
        const val TAG = "QingshuTest"
    }
}
