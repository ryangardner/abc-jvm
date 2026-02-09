package io.github.ryangardner.abc.parser

import java.util.ArrayDeque

class AbcLexer(private val input: String) : Iterator<Token> {
    private var position = 0
    private var line = 1
    private var column = 1
    private var state = LexerState.HEADER
    private val buffer = ArrayDeque<Token>()
    private var pendingBodyTransition = false
    private var eofEmitted = false

    private enum class LexerState {
        HEADER, BODY
    }

    override fun hasNext(): Boolean {
        if (eofEmitted) return false
        if (buffer.isNotEmpty()) return true
        fillBuffer()
        return buffer.isNotEmpty()
    }

    override fun next(): Token {
        if (buffer.isEmpty()) {
            fillBuffer()
        }
        if (buffer.isEmpty()) {
            throw NoSuchElementException()
        }
        val token = buffer.removeFirst()
        if (token.type == TokenType.EOF) {
            eofEmitted = true
        }
        return token
    }

    fun peekToken(): Token {
        if (buffer.isEmpty()) {
            fillBuffer()
        }
        if (buffer.isEmpty()) {
            throw NoSuchElementException()
        }
        return buffer.first
    }

    private fun fillBuffer() {
        if (position >= input.length) {
            if (buffer.isEmpty() && !eofEmitted) {
                buffer.add(Token(TokenType.EOF, "", line, column))
            }
            return
        }

        skipWhitespace()
        if (position >= input.length) {
             if (buffer.isEmpty() && !eofEmitted) {
                 buffer.add(Token(TokenType.EOF, "", line, column))
             }
             return
        }

        when (state) {
            LexerState.HEADER -> scanHeader()
            LexerState.BODY -> scanBody()
        }
    }

    private fun scanHeader() {
        val char = peek()

        // Comment
        if (char == '%') {
             scanCommentOrDirective()
             return
        }

        // Newline
        if (char == '\n' || char == '\r') {
             scanNewline()
             return
        }

        // Header Field: Letter followed by :
        if (char.isLetter()) {
            val nextChar = peek(1)
            if (nextChar == ':') {
                val startCol = column
                val key = consume().toString() // Letter
                consume() // :

                buffer.add(Token(TokenType.HEADER_KEY, key, line, startCol))

                // Read value until newline
                val valueStartCol = column
                val sb = StringBuilder()
                while (position < input.length) {
                    val c = peek()
                    if (c == '\n' || c == '\r' || c == '%') break
                    sb.append(consume())
                }
                buffer.add(Token(TokenType.HEADER_VALUE, sb.toString().trim(), line, valueStartCol))

                if (key == "K") {
                    pendingBodyTransition = true
                }
                return
            }
        }

        // If not a header field, but we are in HEADER state, it might be a continuation or we should be in BODY?
        // Standard says "The next line [after K:] is the start of the tune body."
        // But if we encounter a line that is NOT a header field before K:?
        // "A line beginning with a letter followed by a colon is a header field. Any other line is part of the tune body."
        // So if it's NOT a header field, we should switch to BODY?
        // But comments and empty lines are allowed in header.

        // If it's not a comment, not a newline, and not a header field:
        // Switch to BODY immediately?
        // Let's assume yes.
        state = LexerState.BODY
        scanBody()
    }

    private fun scanBody() {
        val char = peek()
        val startCol = column

        if (char == '%') {
             scanCommentOrDirective()
             return
        }
        if (char == '\n' || char == '\r') {
             scanNewline()
             return
        }

        // Bar Lines
        if (char == '|') {
             consume()
             if (peek() == ']') {
                 consume()
                 buffer.add(Token(TokenType.BAR_LINE, "|]", line, startCol))
             } else if (peek() == '|') {
                 consume()
                 buffer.add(Token(TokenType.BAR_LINE, "||", line, startCol))
             } else if (peek() == ':') {
                 consume()
                 buffer.add(Token(TokenType.BAR_LINE, "|:", line, startCol))
             } else {
                 buffer.add(Token(TokenType.BAR_LINE, "|", line, startCol))
             }
             return
        }

        if (char == ':') {
             if (peek(1) == '|') {
                 consume()
                 consume()
                 buffer.add(Token(TokenType.BAR_LINE, ":|", line, startCol))
                 return
             }
             // : inside body? Repeat start |: handled above.
             // Maybe just consume as UNKNOWN?
             consume()
             buffer.add(Token(TokenType.UNKNOWN, ":", line, startCol))
             return
        }

        // Chords or Inline Fields
        if (char == '[') {
            // Check for inline field [K:...]
            if (peek(1).isLetter() && peek(2) == ':') {
                scanInlineField()
                return
            }
            // Chord start
            consume()
            buffer.add(Token(TokenType.CHORD_START, "[", line, startCol))
            return
        }

        if (char == ']') {
            consume()
            buffer.add(Token(TokenType.CHORD_END, "]", line, startCol))
            return
        }

        // Notes
        if (char in "abcdefgABCDEFG") {
            val noteStart = consume().toString()
            var noteText = noteStart
            while (position < input.length) {
                val next = peek()
                if (next == ',' || next == '\'') {
                    noteText += consume()
                } else {
                    break
                }
            }
            buffer.add(Token(TokenType.NOTE, noteText, line, startCol))
            return
        }

        // Rest 'z' or 'Z' or 'x' or 'X'
        if (char in "zZxX") {
             buffer.add(Token(TokenType.REST, consume().toString(), line, startCol))
             return
        }

        // Accidentals
        if (char == '^' || char == '_' || char == '=') {
            var acc = consume().toString()
            if ((peek() == '^' && acc == "^") || (peek() == '_' && acc == "_")) {
                acc += consume()
            }
            buffer.add(Token(TokenType.ACCIDENTAL, acc, line, startCol))
            return
        }

        // Duration
        if (char.isDigit() || char == '/') {
            val sb = StringBuilder()
            while (peek().isDigit() || peek() == '/') {
                sb.append(consume())
            }
            buffer.add(Token(TokenType.DURATION, sb.toString(), line, startCol))
            return
        }

        // Ties -
        if (char == '-') {
            buffer.add(Token(TokenType.TIE, consume().toString(), line, startCol))
            return
        }

        // Slurs ( )
        if (char == '(') {
            buffer.add(Token(TokenType.SLUR_START, consume().toString(), line, startCol))
            return
        }
        if (char == ')') {
            buffer.add(Token(TokenType.SLUR_END, consume().toString(), line, startCol))
            return
        }

        // Decorations !...! or . ~ etc
        if (char == '!') {
            consume()
            val sb = StringBuilder()
            while (position < input.length && peek() != '!' && peek() != '\n') {
                sb.append(consume())
            }
            if (peek() == '!') consume()
            buffer.add(Token(TokenType.DECORATION, sb.toString(), line, startCol))
            return
        }

        if (char in ".~HLMSTuv") {
             buffer.add(Token(TokenType.DECORATION, consume().toString(), line, startCol))
             return
        }

        // Fallback
        consume()
        buffer.add(Token(TokenType.UNKNOWN, char.toString(), line, startCol))
    }

    private fun scanInlineField() {
        val startCol = column
        consume() // [
        buffer.add(Token(TokenType.INLINE_FIELD_START, "[", line, startCol))

        val key = consume().toString() // Letter
        consume() // :
        buffer.add(Token(TokenType.INLINE_FIELD_KEY, key, line, column - 2))

        // Value until ]
        val sb = StringBuilder()
        while (position < input.length && peek() != ']') {
            sb.append(consume())
        }
        buffer.add(Token(TokenType.INLINE_FIELD_VALUE, sb.toString(), line, column - sb.length))

        if (peek() == ']') {
             consume()
             buffer.add(Token(TokenType.INLINE_FIELD_END, "]", line, column - 1))
        }
    }

    private fun scanCommentOrDirective() {
        val startCol = column
        consume() // %
        if (peek() == '%') {
             consume() // %
             val content = readUntilNewline()
             buffer.add(Token(TokenType.DIRECTIVE, content, line, startCol))
        } else {
             val content = readUntilNewline()
             buffer.add(Token(TokenType.COMMENT, content, line, startCol))
        }
    }

    private fun scanNewline() {
        val startCol = column
        val c = consume() // \n or \r
        if (c == '\r' && peek() == '\n') consume()

        buffer.add(Token(TokenType.NEWLINE, "\n", line, startCol))
        line++
        column = 1

        if (state == LexerState.HEADER && pendingBodyTransition) {
            state = LexerState.BODY
            pendingBodyTransition = false
        }
    }

    private fun skipWhitespace() {
        while (position < input.length) {
            val c = peek()
            if (c == ' ' || c == '\t') {
                consume()
            } else {
                break
            }
        }
    }

    private fun readUntilNewline(): String {
        val sb = StringBuilder()
        while (position < input.length) {
             val c = peek()
             if (c == '\n' || c == '\r') break
             sb.append(consume())
        }
        return sb.toString()
    }

    private fun peek(offset: Int = 0): Char {
        if (position + offset >= input.length) return '\u0000'
        return input[position + offset]
    }

    private fun consume(): Char {
        val c = input[position++]
        column++
        return c
    }
}
