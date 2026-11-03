package rocks.pkb.plektra.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HangulComposerTest {
    private fun type(keys: String): String {
        val composer = HangulComposer()
        val out = StringBuilder()
        for (k in keys) out.append(composer.feed(k, k.isUpperCase()))
        return out.append(composer.finish()).toString()
    }

    @Test fun commonWords() {
        assertEquals("안녕하세요", type("dkssudgktpdy"))
        assertEquals("감사합니다", type("rkatkgkqslek"))
        assertEquals("한국어", type("gksrnrdj"))
        assertEquals("사랑해", type("tkfkdgo"))
    }

    @Test fun compoundVowelsAndFinals() {
        assertEquals("과", type("rhk"))      // ㅗ+ㅏ
        assertEquals("의사", type("dmltk"))   // ㅡ+ㅣ
        assertEquals("닭", type("ekfr"))      // ㄹ+ㄱ final
        assertEquals("없어", type("djqtdj"))  // ㅂ+ㅅ final, ㅅ moves on
        assertEquals("값이", type("rkqtdl"))
    }

    @Test fun doubledConsonantsWithShift() {
        assertEquals("빵", type("Qkd"))
        assertEquals("쓰다", type("Tmek"))
    }

    @Test fun loneJamo() {
        assertEquals("ㅋㅋㅋ", type("zzz"))
        assertEquals("ㅏ", type("k"))
    }

    @Test fun backspaceRemovesOneStroke() {
        val composer = HangulComposer()
        "ekfr".forEach { composer.feed(it, false) }
        assertEquals("닭", composer.composing)
        composer.backspace(); assertEquals("달", composer.composing)
        composer.backspace(); assertEquals("다", composer.composing)
        composer.backspace(); assertEquals("ㄷ", composer.composing)
        composer.backspace(); assertEquals("", composer.composing)
        assertFalse(composer.backspace())
    }
}
