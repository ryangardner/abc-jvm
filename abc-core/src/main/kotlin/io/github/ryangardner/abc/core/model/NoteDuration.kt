package io.github.ryangardner.abc.core.model

/**
 * Represents a note duration as a rational number (numerator/denominator).
 */
public data class NoteDuration(
    public val numerator: Int,
    public val denominator: Int
) : Comparable<NoteDuration> {

    public operator fun plus(other: NoteDuration): NoteDuration {
        val newNum = this.numerator.toLong() * other.denominator + other.numerator.toLong() * this.denominator
        val newDen = this.denominator.toLong() * other.denominator
        return simplify(newNum, newDen)
    }

    public operator fun times(other: NoteDuration): NoteDuration {
        return simplify(this.numerator.toLong() * other.numerator, this.denominator.toLong() * other.denominator)
    }

    public fun times(num: Int, den: Int): NoteDuration {
        return simplify(this.numerator.toLong() * num, this.denominator.toLong() * den)
    }

    public fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    public val isZero: Boolean get() = numerator == 0

    override fun compareTo(other: NoteDuration): Int {
        val left = this.numerator.toLong() * other.denominator
        val right = other.numerator.toLong() * this.denominator
        return left.compareTo(right)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoteDuration) return false
        return this.numerator.toLong() * other.denominator == other.numerator.toLong() * this.denominator
    }

    override fun hashCode(): Int {
        val common = gcd(numerator.toLong(), denominator.toLong())
        val sn = (numerator / common).toInt()
        val sd = (denominator / common).toInt()
        return 31 * sn + sd
    }

    public fun scale(multiplier: Double): NoteDuration {
        // For common ABC multipliers, use exact rational arithmetic
        val (mNum, mDen) = when (multiplier) {
            1.5 -> 3 to 2
            0.5 -> 1 to 2
            1.75 -> 7 to 4
            0.25 -> 1 to 4
            1.875 -> 15 to 8
            0.125 -> 1 to 8
            else -> {
                // For other cases, approximate with 1000 as denominator
                (multiplier * 1000).toInt() to 1000
            }
        }
        return times(mNum, mDen)
    }

    public companion object {
        public val ZERO: NoteDuration = NoteDuration(0, 1)

        public fun simplify(num: Long, den: Long): NoteDuration {
            val common = gcd(num, den)
            return NoteDuration((num / common).toInt(), (den / common).toInt())
        }

        private fun gcd(a: Long, b: Long): Long {
            var x = Math.abs(a)
            var y = Math.abs(b)
            while (y != 0L) {
                val temp = y
                y = x % y
                x = temp
            }
            return if (x == 0L) 1L else x
        }
    }
}
