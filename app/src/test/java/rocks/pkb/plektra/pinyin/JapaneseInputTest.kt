package rocks.pkb.plektra.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseInputTest {
    private fun kana(romaji: String): String = RomajiKana.reading(romaji)

    @Test fun romajiToHiragana() {
        assertEquals("にほん", kana("nihon"))
        assertEquals("こんにちは", kana("konnnichiha"))
        assertEquals("きって", kana("kitte"))
        assertEquals("しんぶん", kana("shinbun"))
        assertEquals("きょうと", kana("kyouto"))
        assertEquals("らーめん", kana("ra-men"))
        assertEquals("つくえ", kana("tsukue"))
        assertEquals("ちゃ", kana("cha"))
        assertEquals("しゃしん", kana("syasin"))
        assertEquals("ぁ", kana("xa"))
        assertEquals("かんい", kana("kan'i"))
    }

    @Test fun pendingLettersStayPending() {
        assertEquals("に" to "h", RomajiKana.convert("nih"))
        assertEquals("" to "ky", RomajiKana.convert("ky"))
        assertEquals("" to "n", RomajiKana.convert("n"))
    }

    @Test fun katakana() {
        assertEquals("ニホン", RomajiKana.toKatakana("にほん"))
    }

    @Test fun dictionaryLookupIsExact() {
        val text = "かk\t書/掛\nかんじ\t漢字/感じ\nにほん\t日本/二本\nにほんご\t日本語\n"
        val dictionary = JapaneseDictionary.fromText(text)
        assertEquals(listOf("日本", "二本"), dictionary.lookup("にほん"))
        assertEquals(listOf("日本語"), dictionary.lookup("にほんご"))
        assertEquals(listOf("書", "掛"), dictionary.lookup("かk"))
        assertTrue(dictionary.lookup("にほ").isEmpty())
        assertTrue(dictionary.lookup("ん").isEmpty())
    }

    @Test fun okuriganaLetters() {
        assertEquals('k', JapaneseController.okuriLetter('く'))
        assertEquals('t', JapaneseController.okuriLetter('っ'))
        assertEquals('i', JapaneseController.okuriLetter('い'))
    }
}
