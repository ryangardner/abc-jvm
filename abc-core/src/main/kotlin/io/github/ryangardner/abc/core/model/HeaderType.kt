package io.github.ryangardner.abc.core.model

public enum class HeaderType(public val key: String) {
    REFERENCE("X"), 
    TITLE("T"), 
    KEY("K"), 
    METER("M"), 
    LENGTH("L"), 
    TEMPO("Q"), 
    VOICE("V"),
    PARTS("P"),
    WORDS("W"),
    COMPOSER("C"),
    RHYTHM("R"),
    ORIGIN("O"),
    SOURCE("S"),
    TRANSCRIPTION("Z"),
    BOOK("B"),
    NOTES("N"),
    HISTORY("H"),
    DISCOGRAPHY("D"),
    FILE("F"),
    GROUP("G"),
    REMARK("r"),
    MACRO("m"),
    INSTRUCTION("I"),
    UNKNOWN("")
}
