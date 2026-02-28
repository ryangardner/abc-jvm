package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.antlr.ABCLexer
import io.github.ryangardner.abc.antlr.ABCParser
import io.github.ryangardner.abc.antlr.ABCParserBaseVisitor
import io.github.ryangardner.abc.core.model.BodyHeaderElement
import io.github.ryangardner.abc.core.model.DirectiveElement
import io.github.ryangardner.abc.core.model.MusicElement
import io.github.ryangardner.abc.core.model.SpacerElement
import io.github.ryangardner.abc.core.model.TextBlockElement
import org.antlr.v4.runtime.tree.TerminalNode

internal class AbcPreambleVisitor : ABCParserBaseVisitor<List<MusicElement>>() {
    private val elements = mutableListOf<MusicElement>()

    override fun visitTunebook(ctx: ABCParser.TunebookContext): List<MusicElement> = ctx.tune_preamble()?.accept(this) ?: emptyList()

    override fun visitTune_preamble(ctx: ABCParser.Tune_preambleContext): List<MusicElement> {
        ctx.children?.forEach { child ->
            when (child) {
                is ABCParser.FieldContext -> {
                    val id = child.FIELD_ID().text.removeSuffix(":")
                    val value =
                        child.children
                            ?.filter {
                                it is TerminalNode &&
                                    (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH)
                            }?.joinToString("") { it.text }
                            ?.trim() ?: ""
                    elements.add(BodyHeaderElement(id, value, child.start.line, child.start.charPositionInLine))
                }

                is ABCParser.Text_block_defaultContext -> {
                    val lines =
                        child.children?.filter { it is TerminalNode && it.symbol.type == ABCLexer.TEXT_BLOCK_CONTENT }?.map { it.text }
                            ?: emptyList()
                    elements.add(TextBlockElement(lines, child.start.line, child.start.charPositionInLine))
                }

                is TerminalNode -> {
                    when (child.symbol.type) {
                        ABCLexer.STYLESHEET -> {
                            elements.add(
                                DirectiveElement(child.text.removePrefix("%%"), child.symbol.line, child.symbol.charPositionInLine),
                            )
                        }

                        ABCLexer.NEWLINE, ABCLexer.WS_DEFAULT, ABCLexer.FREE_TEXT, ABCLexer.UNRECOGNIZED, ABCLexer.DEFAULT_COMMENT -> {
                            elements.add(SpacerElement(child.text, child.symbol.line, child.symbol.charPositionInLine))
                        }
                    }
                }
            }
        }
        return elements
    }
}
