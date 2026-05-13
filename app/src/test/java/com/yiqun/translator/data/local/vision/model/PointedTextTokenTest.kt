package com.yiqun.translator.data.local.vision.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PointedTextTokenTest {

    @Test
    fun tokenAt_returnsLeftTokenWhenOcrJoinsWordsWithHyphen() {
        val token = PointedTextToken.tokenAt("twist-we", 2)

        assertEquals("twist", token.text)
        assertEquals(0, token.start)
        assertEquals(5, token.end)
    }

    @Test
    fun tokenAt_returnsRightTokenWhenPointerIsAfterHyphen() {
        val token = PointedTextToken.tokenAt("twist-we", 7)

        assertEquals("we", token.text)
        assertEquals(6, token.start)
        assertEquals(8, token.end)
    }

    @Test
    fun tokenAt_keepsApostropheInsideContraction() {
        val token = PointedTextToken.tokenAt("It's", 2)

        assertEquals("It's", token.text)
        assertEquals(0, token.start)
        assertEquals(4, token.end)
    }
}
