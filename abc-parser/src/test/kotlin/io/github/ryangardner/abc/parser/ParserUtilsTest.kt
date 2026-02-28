package io.github.ryangardner.abc.parser

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
    fun testCalculateDurationBasic() {
        val defaultLength = NoteDuration(1, 8)

        // "2" means 2 * defaultLength = 1/4
        val dur1 = ParserUtils.calculateDuration("2", defaultLength)
        assertEquals(NoteDuration(1, 4), dur1)
    }

    @Test
    fun testCalculateDurationImplicitNumerator() {
        val defaultLength = NoteDuration(1, 4)

        // "/" means 1/2 of defaultLength = 1/8
        val dur1 = ParserUtils.calculateDuration("/", defaultLength)
        assertEquals(NoteDuration(1, 8), dur1)

        // "//" means 1/4 of defaultLength = 1/16
        val dur2 = ParserUtils.calculateDuration("//", defaultLength)
        assertEquals(NoteDuration(1, 16), dur2)
    }

    @Test
    fun testCalculateDurationExplicitDenominator() {
        val defaultLength = NoteDuration(1, 4)

        // "3/2" means (3/2) * defaultLength = 3/8
        val dur1 = ParserUtils.calculateDuration("3/2", defaultLength)
        assertEquals(NoteDuration(3, 8), dur1)

        // "/4" means (1/4) * defaultLength = 1/16
        val dur2 = ParserUtils.calculateDuration("/4", defaultLength)
        assertEquals(NoteDuration(1, 16), dur2)
    }
}
