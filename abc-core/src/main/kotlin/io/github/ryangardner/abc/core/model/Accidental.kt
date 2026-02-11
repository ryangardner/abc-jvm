package io.github.ryangardner.abc.core.model

public enum class Accidental(public val semitones: Int, public val offset: Double = 0.0) {
    SHARP(1),
    FLAT(-1),
    NATURAL(0),
    DOUBLE_SHARP(2),
    DOUBLE_FLAT(-2),
    
    QUARTER_SHARP(0, 0.5),
    THREE_QUARTER_SHARP(1, 0.5),
    QUARTER_FLAT(0, -0.5),
    THREE_QUARTER_FLAT(-1, -0.5)
}
