package rocks.pkb.plektra.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CantoneseInputTest {
    private val text = listOf(
        "#nh\t21494\t你好/諗起",
        "hai\t8784349\t係/喺",
        "mgoi\t110344\t唔該",
        "nei\t7032018\t你/呢",
        "neidei\t300000\t你哋",
        "neihou\t21494\t你好/您好",
        "ngo\t12831922\t我/哦",
        "ngodei\t55261\t我哋",
    ).joinToString("\n", postfix = "\n")
    private val dictionary = CantoneseDictionary.fromText(text)

    @Test fun exactLookup() {
        assertEquals(listOf("你好", "您好"), dictionary.lookup("neihou"))
        assertEquals(listOf("唔該"), dictionary.lookup("mgoi"))
        assertEquals(listOf("你好", "諗起"), dictionary.lookup("#nh"))
        assertTrue(dictionary.lookup("neih").isEmpty())
        assertTrue(dictionary.lookup("zzz").isEmpty())
        assertTrue(dictionary.lookup("a").isEmpty())
    }

    @Test fun completionsByWeight() {
        // 你哋 is more frequent than 你好, and "nei" itself is not a completion.
        assertEquals(listOf("你哋", "你好"), dictionary.completions("nei", 5))
        assertEquals(listOf("你好"), dictionary.completions("neih", 5))
        assertTrue(dictionary.completions("x", 5).isEmpty())
    }

    @Test fun prefixes() {
        assertTrue(dictionary.hasPrefix("ngod"))
        assertFalse(dictionary.hasPrefix("ngox"))
    }
}
