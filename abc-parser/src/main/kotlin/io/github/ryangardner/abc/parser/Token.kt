package io.github.ryangardner.abc.parser

public enum class TokenType {
    // Header
    HEADER_KEY,     // X, T, M, etc.
    HEADER_VALUE,   // The content of the header field

    // Body
    NOTE,           // C, d, etc.
    REST,           // z, Z, x, X
    ACCIDENTAL,     // ^, _, =
    DURATION,       // 2, /2, 3/4
    BAR_LINE,       // |, ||, |], :|, |:
    CHORD_START,    // [
    CHORD_END,      // ]
    TIE,            // -
    SLUR_START,     // (
    SLUR_END,       // )
    TUPLET,         // (2, (3, etc.
    DECORATION,     // !...! or symbol like . ~
    BROKEN_RHYTHM,  // >, <, >>, <<, etc.
    GRACE_START,    // {
    GRACE_END,      // }

    // Inline field
    INLINE_FIELD_START, // [
    INLINE_FIELD_KEY,   // K, M, etc. inside []
    INLINE_FIELD_VALUE,
    INLINE_FIELD_END,   // ]

    // Common
    WHITESPACE,
    NEWLINE,
    COMMENT,        // % ...
    DIRECTIVE,      // %% ...
    EOF,

    UNKNOWN
}

public data class Token(
    public val type: TokenType,
    public val text: String,
    public val line: Int,
    public val column: Int
)
