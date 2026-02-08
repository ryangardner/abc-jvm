package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.AbcTune
import java.io.File

class AbcParser {

    fun parse(input: String): AbcTune {
        val lexer = AbcLexer(input)

        // Parse Header
        val headerParser = HeaderParser(lexer)
        val headerResult = headerParser.parse()
            ?: throw IllegalArgumentException("Invalid ABC file: No valid header found")

        // Parse Body
        val bodyParser = BodyParser(lexer, headerResult.header)
        val body = bodyParser.parse()

        return AbcTune(
            header = headerResult.header,
            body = body,
            metadata = headerResult.metadata
        )
    }

    fun parse(file: File): AbcTune {
        return parse(file.readText())
    }
}
