package io.github.ryangardner.abc.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteDurationTest {

    @Test
    fun `test addition`() {
        val quarter = NoteDuration(1, 4)
        val eighth = NoteDuration(1, 8)

        val result = quarter + eighth
        assertEquals(NoteDuration(3, 8), result)

        val half = quarter + quarter
        assertEquals(NoteDuration(1, 2), half)

        val whole = half + half
        assertEquals(NoteDuration(1, 1), whole)

        val zeroAddition = quarter + NoteDuration.ZERO
        assertEquals(quarter, zeroAddition)
    }

    @Test
    fun `test multiplication with NoteDuration`() {
        val half = NoteDuration(1, 2)
        val result = half * half
        assertEquals(NoteDuration(1, 4), result)

        val threeQuarters = NoteDuration(3, 4)
        val combined = threeQuarters * half
        assertEquals(NoteDuration(3, 8), combined)
    }

    @Test
    fun `test multiplication with raw values`() {
        val quarter = NoteDuration(1, 4)
        val result = quarter.times(2, 1)
        assertEquals(NoteDuration(1, 2), result)

        val dotted = quarter.times(3, 2)
        assertEquals(NoteDuration(3, 8), dotted)
    }

    @Test
    fun `test toDouble and wholeNoteDuration`() {
        val quarter = NoteDuration(1, 4)
        assertEquals(0.25, quarter.toDouble(), 0.0001)
        assertEquals(0.25, quarter.wholeNoteDuration, 0.0001)

        val third = NoteDuration(1, 3)
        assertEquals(1.0/3.0, third.toDouble(), 0.0001)
    }

    @Test
    fun `test isZero and ZERO constant`() {
        assertTrue(NoteDuration.ZERO.isZero)
        assertTrue(NoteDuration(0, 4).isZero)
        assertEquals(0, NoteDuration.ZERO.numerator)
        assertEquals(1, NoteDuration.ZERO.denominator)
    }

    @Test
    fun `test scale with exact multipliers`() {
        val quarter = NoteDuration(1, 4)

        assertEquals(NoteDuration(3, 8), quarter.scale(1.5))
        assertEquals(NoteDuration(1, 8), quarter.scale(0.5))
        assertEquals(NoteDuration(7, 16), quarter.scale(1.75))
        assertEquals(NoteDuration(1, 16), quarter.scale(0.25))
        assertEquals(NoteDuration(15, 32), quarter.scale(1.875))
        assertEquals(NoteDuration(1, 32), quarter.scale(0.125))
    }

    @Test
    fun `test scale with approximation`() {
        val quarter = NoteDuration(1, 4)
        // 1/3 is not in the exact list
        val result = quarter.scale(1.0 / 3.0)
        // (1/3 * 1000).toInt() = 333
        // Result: 1/4 * 333 / 1000 = 333 / 4000
        assertEquals(NoteDuration(333, 4000), result)
    }

    @Test
    fun `test equality and hashCode`() {
        val half1 = NoteDuration(1, 2)
        val half2 = NoteDuration(2, 4)
        val half3 = NoteDuration(4, 8)
        val quarter = NoteDuration(1, 4)

        assertEquals(half1, half2)
        assertEquals(half2, half3)
        assertEquals(half1, half3)
        assertNotEquals(half1, quarter)

        assertEquals(half1.hashCode(), half2.hashCode())
        assertEquals(half2.hashCode(), half3.hashCode())
    }

    @Test
    fun `test comparison`() {
        val quarter = NoteDuration(1, 4)
        val half = NoteDuration(1, 2)
        val eighth = NoteDuration(1, 8)

        assertTrue(eighth < quarter)
        assertTrue(quarter < half)
        assertTrue(half > eighth)

        val alsoHalf = NoteDuration(2, 4)
        assertEquals(0, half.compareTo(alsoHalf))
    }

    @Test
    fun `test simplify`() {
        assertEquals(NoteDuration(1, 2), NoteDuration.simplify(2, 4))
        assertEquals(NoteDuration(1, 1), NoteDuration.simplify(10, 10))
        assertEquals(NoteDuration(0, 1), NoteDuration.simplify(0, 5))
        // gcd of 12 and 18 is 6. 12/6=2, 18/6=3.
        assertEquals(NoteDuration(2, 3), NoteDuration.simplify(12, 18))
    }
}
