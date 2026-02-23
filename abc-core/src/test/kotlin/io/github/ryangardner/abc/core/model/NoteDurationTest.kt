package io.github.ryangardner.abc.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class NoteDurationTest {
    @Test
    fun testConstants() {
        assertEquals(0, NoteDuration.ZERO.numerator)
        assertEquals(1, NoteDuration.ZERO.denominator)

        assertEquals(1, NoteDuration.WHOLE.numerator)
        assertEquals(1, NoteDuration.WHOLE.denominator)

        assertEquals(1, NoteDuration.HALF.numerator)
        assertEquals(2, NoteDuration.HALF.denominator)

        assertEquals(1, NoteDuration.QUARTER.numerator)
        assertEquals(4, NoteDuration.QUARTER.denominator)

        assertEquals(1, NoteDuration.EIGHTH.numerator)
        assertEquals(8, NoteDuration.EIGHTH.denominator)

        assertEquals(1, NoteDuration.SIXTEENTH.numerator)
        assertEquals(16, NoteDuration.SIXTEENTH.denominator)

        assertEquals(1, NoteDuration.THIRTY_SECOND.numerator)
        assertEquals(32, NoteDuration.THIRTY_SECOND.denominator)

        assertEquals(1, NoteDuration.SIXTY_FOURTH.numerator)
        assertEquals(64, NoteDuration.SIXTY_FOURTH.denominator)
    }
}
