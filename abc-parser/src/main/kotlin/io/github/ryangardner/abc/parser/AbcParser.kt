package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.antlr.ABCLexer
import io.github.ryangardner.abc.antlr.ABCParser
import io.github.ryangardner.abc.core.model.AbcTune
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.misc.ParseCancellationException

@Suppress("MaxLineLength")
public class AbcParser {
    public fun parse(input: String): AbcTune = parseBook(input).firstOrNull() ?: throw IllegalArgumentException("No tunes found in input")

    public fun parseBook(input: String): List<AbcTune> {
        val lexer = ABCLexer(CharStreams.fromString(input))
        val tokens = CommonTokenStream(lexer)
        val parser = ABCParser(tokens)

        val errorListener =
            object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>?,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String?,
                    e: RecognitionException?,
                ): Unit = throw ParseCancellationException("line $line:$charPositionInLine $msg")
            }
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        val tunebookContext = parser.tunebook()
        val visitor = AbcTunebookVisitor()
        return visitor.visitTunebook(tunebookContext)
    }
}
