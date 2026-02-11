package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.AbcTune
import java.io.File

public class AbcParser {

    public fun parse(input: String): AbcTune {
        return parseBook(input).firstOrNull() ?: throw IllegalArgumentException("Invalid ABC file: No valid tune found")
    }

    public fun parseBook(input: String): List<AbcTune> {
        val lexer = AbcLexer(input)
        val tunes = mutableListOf<AbcTune>()

        while (lexer.hasNext() && lexer.peekToken().type != TokenType.EOF) {
            // Skip any whitespace or newlines before a tune
            while (lexer.hasNext() && (lexer.peekToken().type == TokenType.WHITESPACE || lexer.peekToken().type == TokenType.NEWLINE)) {
                lexer.next()
            }
            if (!lexer.hasNext() || lexer.peekToken().type == TokenType.EOF) break

            // Parse Header
            val headerParser = HeaderParser(lexer)
            val headerResult = headerParser.parse() ?: break

            // Parse Body
            val bodyParser = BodyParser(lexer, headerResult.header)
            val body = bodyParser.parse()

            tunes.add(AbcTune(
                header = headerResult.header,
                body = body,
                metadata = headerResult.metadata
            ))
        }
        return tunes
    }

    public fun parse(file: File): AbcTune {
        return parse(file.readText())
    }
}
