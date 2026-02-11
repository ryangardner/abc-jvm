package io.github.ryangardner.abc.parser

import java.util.ArrayDeque

public class AbcLexer(private val input: String) : Iterator<Token> {
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

    public fun peekToken(): Token {
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

        // If we are at column 1, check for header field.
        if (column == 1) {
            val char = peek()
            if ((char.isLetter() || char == 'w' || char == 'W') && peek(1) == ':') {
                if (char == 'X') {
                    state = LexerState.HEADER
                    scanHeaderField()
                } else if (state == LexerState.BODY) {
                    scanHeaderInBody(column)
                } else {
                    scanHeaderField()
                }
                return
            }
        }

        // Standard whitespace skipping
        val nextChar = peek()
        if (nextChar == ' ' || nextChar == '\t') {
            skipWhitespace()
        }
        
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

    private fun scanHeaderField() {
        val startCol = column
        val key = consume().toString() // Letter
        consume() // :

        buffer.add(Token(TokenType.HEADER_KEY, key, line, startCol))

        // Skip any whitespace immediately after the colon
        while (peek() == ' ' || peek() == '\t') {
            consume()
        }

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
    }

    private fun scanHeader() {
        val char = peek()

        // Whitespace
        if (char == ' ' || char == '\t') {
            consume()
            buffer.add(Token(TokenType.WHITESPACE, char.toString(), line, column - 1))
            return
        }

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

        // If it's not a comment, not a newline, and not a header field:
        // Switch to BODY immediately?
        state = LexerState.BODY
        scanBody()
    }

    private fun scanBody() {
        val char = peek()
        val startCol = column

        if (char == ' ' || char == '\t') {
            consume()
            buffer.add(Token(TokenType.WHITESPACE, char.toString(), line, startCol))
            return
        }
        if (char == '%') {
             scanCommentOrDirective()
             return
        }
        if (char == '\n' || char == '\r') {
             scanNewline()
             return
        }

        when (char) {
            '"' -> scanAnnotation(startCol)
            '|' -> scanBarLine(startCol)
            ':' -> scanColon(startCol)
            '[' -> scanSquareBracket(startCol)
            ']' -> {
                consume()
                buffer.add(Token(TokenType.CHORD_END, "]", line, startCol))
            }
            in "abcdefgABCDEFG", ',', '\'' -> scanNote(startCol)
            in "zZxX" -> {
                buffer.add(Token(TokenType.REST, consume().toString(), line, startCol))
            }
            '^', '_', '=' -> scanAccidental(startCol)
            '>', '<' -> scanBrokenRhythm(startCol)
            in '0'..'9' -> {
                // If it's a digit and the previous token was a TIE, it might be a note continuation (e.g. c-4)
                if (buffer.isNotEmpty() && buffer.last.type == TokenType.TIE) {
                    scanNote(startCol)
                } else {
                    scanDuration(startCol)
                }
            }
            '/' -> scanDuration(startCol)
            '-' -> {
                buffer.add(Token(TokenType.TIE, consume().toString(), line, startCol))
            }
            '(' -> scanLeftParenthesis(startCol)
            ')' -> {
                buffer.add(Token(TokenType.SLUR_END, consume().toString(), line, startCol))
            }
            '{' -> {
                buffer.add(Token(TokenType.GRACE_START, consume().toString(), line, startCol))
            }
            '}' -> {
                buffer.add(Token(TokenType.GRACE_END, consume().toString(), line, startCol))
            }
            '!' -> scanDecorationExclamation(startCol)
            in ".~HLMSTuv" -> {
                buffer.add(Token(TokenType.DECORATION, consume().toString(), line, startCol))
            }
            else -> {
                consume()
                buffer.add(Token(TokenType.UNKNOWN, char.toString(), line, startCol))
            }
        }
    }

    private fun scanHeaderInBody(startCol: Int) {
        val key = consume().toString()
        consume() // :
        buffer.add(Token(TokenType.HEADER_KEY, key, line, startCol))
        
        // Skip spaces after colon
        while (peek() == ' ' || peek() == '\t') {
            consume()
        }
        
        val valueStartCol = column
        val value = readUntilNewline()
        buffer.add(Token(TokenType.HEADER_VALUE, value.trim(), line, valueStartCol))
    }

    private fun scanAnnotation(startCol: Int) {
        consume()
        val sb = StringBuilder()
        while (position < input.length && peek() != '"' && peek() != '\n') {
            sb.append(consume())
        }
        if (peek() == '"') consume()
        buffer.add(Token(TokenType.UNKNOWN, sb.toString(), line, startCol))
    }

    private fun scanBarLine(startCol: Int) {
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
    }

    private fun scanColon(startCol: Int) {
        if (peek(1) == '|') {
            consume()
            consume()
            if (peek() == ':') {
                consume()
                buffer.add(Token(TokenType.BAR_LINE, ":|:", line, startCol))
            } else {
                buffer.add(Token(TokenType.BAR_LINE, ":|", line, startCol))
            }
            return
        }
        if (peek(1) == ':') {
            consume()
            consume()
            buffer.add(Token(TokenType.BAR_LINE, "::", line, startCol))
            return
        }
        consume()
        buffer.add(Token(TokenType.UNKNOWN, ":", line, startCol))
    }

    private fun scanSquareBracket(startCol: Int) {
        if (peek(1).isLetter() && peek(2) == ':') {
            scanInlineField()
        } else {
            consume()
            buffer.add(Token(TokenType.CHORD_START, "[", line, startCol))
        }
    }

    private fun scanLeftParenthesis(startCol: Int) {
        if (peek(1).isDigit()) {
            consume() // (
            val sb = StringBuilder()
            while (peek().isDigit() || peek() == ':') {
                sb.append(consume())
            }
            buffer.add(Token(TokenType.TUPLET, "(" + sb.toString(), line, startCol))
        } else {
            consume()
            buffer.add(Token(TokenType.SLUR_START, "(", line, startCol))
        }
    }

    private fun scanBrokenRhythm(startCol: Int) {
        val char = consume()
        val sb = StringBuilder().append(char)
        while (peek() == char) {
            sb.append(consume())
        }
        buffer.add(Token(TokenType.BROKEN_RHYTHM, sb.toString(), line, startCol))
    }

    private fun scanNote(startCol: Int) {
        val sb = StringBuilder()
        var hasLetter = false
        
        while (position < input.length) {
            val c = peek()
            if (!hasLetter && c.isLetter() && c.uppercaseChar() in "ABCDEFG") {
                sb.append(consume())
                hasLetter = true
            } else if (c == ',' || c == '\'') {
                sb.append(consume())
            } else if (!hasLetter && (c == ',' || c == '\'')) {
                // Modifiers before letter (not strictly standard but happens)
                sb.append(consume())
            } else {
                break
            }
        }
        buffer.add(Token(TokenType.NOTE, sb.toString(), line, startCol))
    }

    private fun scanAccidental(startCol: Int) {
        var acc = consume().toString()
        if ((peek() == '^' && acc == "^") || (peek() == '_' && acc == "_")) {
            acc += consume()
        }
        buffer.add(Token(TokenType.ACCIDENTAL, acc, line, startCol))
    }

    private fun scanNoteLength(startCol: Int) {
        val sb = StringBuilder()
        while (peek().isDigit() || peek() == '/') {
            sb.append(consume())
        }
        buffer.add(Token(TokenType.DURATION, sb.toString(), line, startCol))
    }

    private fun scanDuration(startCol: Int) {
        val sb = StringBuilder()
        while (peek().isDigit() || peek() == '/') {
            sb.append(consume())
        }
        buffer.add(Token(TokenType.DURATION, sb.toString(), line, startCol))
    }

    private fun scanDecorationExclamation(startCol: Int) {
        consume()
        val sb = StringBuilder()
        while (position < input.length && peek() != '!' && peek() != '\n') {
            sb.append(consume())
        }
        if (peek() == '!') consume()
        buffer.add(Token(TokenType.DECORATION, sb.toString(), line, startCol))
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
        val c = peek()
        if (c == '\r') {
            position++
            if (peek() == '\n') {
                position++
            }
        } else if (c == '\n') {
            position++
        }

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
