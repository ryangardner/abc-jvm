package io.github.ryangardner.abc.antlr

import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ConsoleErrorListener
import org.antlr.v4.runtime.tree.ParseTree

public class AntlrAbcParser {

    public fun parse(input: String, errorListener: ANTLRErrorListener? = null): ParseTree {
        val charStream = CharStreams.fromString(input)
        val lexer = ABCLexer(charStream)
        
        lexer.removeErrorListeners()
        if (errorListener != null) {
            lexer.addErrorListener(errorListener)
        } else {
            lexer.addErrorListener(ConsoleErrorListener.INSTANCE)
        }

        val tokenStream = CommonTokenStream(lexer)
        val parser = ABCParser(tokenStream)
        
        parser.removeErrorListeners()
        if (errorListener != null) {
            parser.addErrorListener(errorListener)
        } else {
            parser.addErrorListener(ConsoleErrorListener.INSTANCE)
        }

        return parser.tunebook()
    }
}
