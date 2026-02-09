package io.github.ryangardner.abc.core.model

enum class KeyMode {
    MAJOR, MINOR, IONIAN, DORIAN, PHRYGIAN, LYDIAN, MIXOLYDIAN, AEOLIAN, LOCRIAN;

    companion object {
        fun fromString(mode: String?): KeyMode {
            return when (mode?.lowercase()) {
                "m", "min", "minor", "aeolian" -> AEOLIAN
                "maj", "major", "ionian" -> IONIAN
                "dor", "dorian" -> DORIAN
                "phr", "phrygian" -> PHRYGIAN
                "lyd", "lydian" -> LYDIAN
                "mix", "mixolydian" -> MIXOLYDIAN
                "loc", "locrian" -> LOCRIAN
                else -> IONIAN
            }
        }
    }
}
