package io.github.ryangardner.abc.core.model

/**
 * Represents a note duration.
 *
 * @property value The duration value (e.g., 1/8).
 */
public data class NoteDuration(
    public val numerator: Int,
    public val denominator: Int
) {
    public operator fun plus(other: NoteDuration): NoteDuration {
        val newNum = this.numerator * other.denominator + other.numerator * this.denominator
        val newDen = this.denominator * other.denominator
        return simplify(newNum, newDen)
    }

    public operator fun times(other: NoteDuration): NoteDuration {
        return simplify(this.numerator * other.numerator, this.denominator * other.denominator)
    }

    public fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    public fun scale(multiplier: Double): NoteDuration {
        // multiplier is usually 1.5, 0.5, 1.75, 0.25, etc.
        // Convert to fraction
        val (mNum, mDen) = when (multiplier) {
            1.5 -> 3 to 2
            0.5 -> 1 to 2
            1.75 -> 7 to 4
            0.25 -> 1 to 4
            1.875 -> 15 to 8
            0.125 -> 1 to 8
            else -> {
                // Approximate if needed, but for broken rhythm these are the standard values
                (multiplier * 1000).toInt() to 1000
            }
        }
        return simplify(this.numerator * mNum, this.denominator * mDen)
    }

    public companion object {
        public fun simplify(num: Int, den: Int): NoteDuration {
            val common = gcd(num, den)
            return NoteDuration(num / common, den / common)
        }

        private fun gcd(a: Int, b: Int): Int {
            var x = a
            var y = b
            while (y != 0) {
                val temp = y
                y = x % y
                x = temp
            }
            return x
        }
    }
}
