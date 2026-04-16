package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.DurationMultiplier
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.core.model.TimeSignature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParserUtilsTest {
    @Test
    fun testParseMeterCutTime() {
        val meter = ParserUtils.parseMeter("C|")
        assertEquals(TimeSignature(2, 2, "C|"), meter)
    }

    @Test
    fun testParseMeterCommonTime() {
        val meter = ParserUtils.parseMeter("C")
        assertEquals(TimeSignature(4, 4, "C"), meter)
    }

    @Test
    fun testParseMeterNone() {
        val meter = ParserUtils.parseMeter("none")
        assertEquals(TimeSignature.NONE, meter)
    }

    @Test
    fun testParseMeterFractional() {
        val meter = ParserUtils.parseMeter("3/4")
        assertEquals(TimeSignature(3, 4), meter)

        val compoundMeter = ParserUtils.parseMeter("12/8")
        assertEquals(TimeSignature(12, 8), compoundMeter)
    }

    @Test
    fun testParseLengthFractional() {
        val length = ParserUtils.parseLength("1/4")
        assertEquals(NoteDuration(1, 4), length)

        val oddLength = ParserUtils.parseLength("3/8")
        assertEquals(NoteDuration(3, 8), oddLength)
    }

    @Test
    fun testParseDurationMultiplierBasic() {
        val dur1 = ParserUtils.parseDurationMultiplier("2")
        assertEquals(DurationMultiplier(2, 1), dur1)
    }

    @Test
    fun testParseDurationMultiplierImplicitNumerator() {
        val dur1 = ParserUtils.parseDurationMultiplier("/")
        assertEquals(DurationMultiplier(1, 2), dur1)

        val dur2 = ParserUtils.parseDurationMultiplier("//")
        assertEquals(DurationMultiplier(1, 4), dur2)
    }

    @Test
    fun testParseDurationMultiplierExplicitDenominator() {
        val dur1 = ParserUtils.parseDurationMultiplier("3/2")
        assertEquals(DurationMultiplier(3, 2), dur1)

        val dur2 = ParserUtils.parseDurationMultiplier("/4")
        assertEquals(DurationMultiplier(1, 4), dur2)

        val dur3 = ParserUtils.parseDurationMultiplier("3")
        assertEquals(DurationMultiplier(3, 1), dur3)

        val dur4 = ParserUtils.parseDurationMultiplier("3/")
        assertEquals(DurationMultiplier(3, 2), dur4)

        val dur5 = ParserUtils.parseDurationMultiplier("3//")
        assertEquals(DurationMultiplier(3, 4), dur5)
    }
}
