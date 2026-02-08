package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.*

data class HeaderResult(
    val header: TuneHeader,
    val metadata: TuneMetadata
)

class HeaderParser(private val lexer: Iterator<Token>) {

    fun parse(): HeaderResult? {
        var reference: Int? = null
        val titles = mutableListOf<String>()
        var key: KeySignature? = null
        var meter: TimeSignature? = null
        var length: NoteDuration? = null
        var tempo: Tempo? = null
        val unknownHeaders = mutableMapOf<String, String>()
        var metadata = TuneMetadata()

        while (lexer.hasNext()) {
            val token = lexer.next()

            when (token.type) {
                TokenType.HEADER_KEY -> {
                    val keyText = token.text
                    // We expect a value next.
                    if (lexer.hasNext()) {
                        val valToken = lexer.next()
                        if (valToken.type == TokenType.HEADER_VALUE) {
                            val valueText = valToken.text.trim()
                            when (keyText) {
                                "X" -> reference = valueText.toIntOrNull()
                                "T" -> titles.add(valueText)
                                "M" -> meter = parseMeter(valueText)
                                "L" -> length = parseLength(valueText)
                                "Q" -> tempo = parseTempo(valueText)
                                "K" -> {
                                    key = parseKey(valueText)
                                    // K is the last header. We are done with header parsing.
                                    return HeaderResult(
                                        TuneHeader(
                                            reference = reference ?: 0,
                                            title = titles,
                                            key = key,
                                            meter = meter ?: TimeSignature(4, 4),
                                            length = length ?: NoteDuration(1, 8),
                                            tempo = tempo,
                                            unknownHeaders = unknownHeaders
                                        ),
                                        metadata
                                    )
                                }
                                else -> unknownHeaders[keyText] = valueText
                            }
                        }
                    }
                }
                TokenType.DIRECTIVE -> {
                    metadata = DirectiveParser.parse(token.text, metadata)
                }
                TokenType.COMMENT, TokenType.NEWLINE, TokenType.WHITESPACE -> {
                    // Skip
                }
                TokenType.EOF -> return null
                else -> {
                    // Unexpected token in header state.
                    return null
                }
            }
        }
        return null
    }

    private fun parseKey(text: String): KeySignature {
        val parts = text.split("\\s+".toRegex())
        var tonic = parts.getOrElse(0) { "C" }
        var mode = if (parts.size > 1) parts[1] else "Major"

        if (parts.size == 1 && tonic.length > 1 && tonic.endsWith("m")) {
            mode = "minor"
            tonic = tonic.substring(0, tonic.length - 1)
        }
        return KeySignature(tonic, mode)
    }

    private fun parseMeter(text: String): TimeSignature {
        return when (text) {
            "C" -> TimeSignature(4, 4, "C")
            "C|" -> TimeSignature(2, 2, "C|")
            else -> {
                val parts = text.split("/")
                if (parts.size == 2) {
                    TimeSignature(parts[0].toIntOrNull() ?: 4, parts[1].toIntOrNull() ?: 4)
                } else {
                    TimeSignature(4, 4)
                }
            }
        }
    }

    private fun parseLength(text: String): NoteDuration {
        val parts = text.split("/")
        return if (parts.size == 2) {
            NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
        } else {
            NoteDuration(1, 8)
        }
    }

    private fun parseTempo(text: String): Tempo {
        // "1/4=120" or "120"
        return if (text.contains("=")) {
            val parts = text.split("=")
            val beatStr = parts[0]
            val bpmStr = parts[1]
            val beatParts = beatStr.split("/")
            val beat = if (beatParts.size == 2) NoteDuration(beatParts[0].toInt(), beatParts[1].toInt()) else null
            Tempo(bpmStr.trim().toIntOrNull() ?: 120, beat)
        } else {
            Tempo(text.trim().toIntOrNull() ?: 120)
        }
    }
}
