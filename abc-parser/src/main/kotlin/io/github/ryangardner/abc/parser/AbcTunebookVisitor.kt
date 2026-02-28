package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.antlr.ABCLexer
import io.github.ryangardner.abc.antlr.ABCParser
import io.github.ryangardner.abc.antlr.ABCParserBaseVisitor
import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.core.model.KeySignature
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.core.model.TimeSignature
import io.github.ryangardner.abc.core.model.TuneBody
import io.github.ryangardner.abc.core.model.TuneHeader
import io.github.ryangardner.abc.core.model.TuneMetadata
import io.github.ryangardner.abc.theory.util.KeyParserUtil
import org.antlr.v4.runtime.tree.TerminalNode

internal class AbcTunebookVisitor : ABCParserBaseVisitor<List<AbcTune>>() {
    override fun visitTunebook(ctx: ABCParser.TunebookContext): List<AbcTune> {
        val preambleVisitor = AbcPreambleVisitor()
        val globalPreamble = ctx.tune_preamble()?.accept(preambleVisitor) ?: emptyList()

        return ctx.tune()?.mapIndexed { index, tuneCtx ->
            val headerCtx = tuneCtx.tune_header()
            val header = buildTuneHeader(headerCtx)

            val bodyVisitor = AbcTuneBodyVisitor(header)
            tuneCtx.tune_body()?.accept(bodyVisitor)

            AbcTune(
                header = header,
                body = TuneBody(bodyVisitor.elements),
                metadata = TuneMetadata(),
                preamble = if (index == 0) globalPreamble else emptyList(),
            )
        } ?: emptyList()
    }

    private fun buildTuneHeader(ctx: ABCParser.Tune_headerContext): TuneHeader {
        val xRef =
            ctx
                .x_ref()
                ?.children
                ?.filter {
                    it is TerminalNode &&
                        (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH)
                }?.joinToString("") { it.text }
                ?.trim()
                ?.toIntOrNull() ?: 1
        val titles = mutableListOf<String>()
        var key: KeySignature? = null
        var meter: TimeSignature? = null
        var length: NoteDuration? = null
        var playingOrder: String? = null
        val unknownHeaders = mutableMapOf<String, String>()
        val allHeaders = mutableListOf<Pair<String, String>>()
        var version = "2.0"
        ctx.children?.forEach { child ->
            when (child) {
                is ABCParser.FieldContext -> {
                    if (child.FIELD_ID() != null) {
                        val id = child.FIELD_ID().text.removeSuffix(":")
                        val value =
                            child.children
                                ?.filter {
                                    it is TerminalNode &&
                                        (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH)
                                }?.joinToString("") { it.text }
                                ?.trim() ?: ""
                        allHeaders.add(id to value)
                        when (id) {
                            "T" -> titles.add(value)
                            "M" -> meter = ParserUtils.parseMeter(value)
                            "L" -> length = ParserUtils.parseLength(value)
                            "P" -> playingOrder = value
                            else -> unknownHeaders[id] = value
                        }
                    }
                }

                is TerminalNode -> {
                    if (child.symbol.type == ABCLexer.STYLESHEET) {
                        val content = child.text.trim().removePrefix("%%")
                        allHeaders.add("%%" to content)
                        if (content.startsWith("abc-version", ignoreCase = true)) {
                            version = content.substringAfter("abc-version").trim()
                        }
                    }
                }
            }
        }

        val actualMeter = meter ?: TimeSignature.NONE
        val actualLength =
            length ?: run {
                if (actualMeter.isNone) {
                    NoteDuration(1, 8)
                } else if (actualMeter.toDouble() < 0.75) {
                    NoteDuration(1, 16)
                } else {
                    NoteDuration(1, 8)
                }
            }

        val keyValue =
            ctx
                .key_field()
                ?.children
                ?.filter { it is TerminalNode && (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH) }
                ?.joinToString("") { it.text }
                ?.trim() ?: "C"
        allHeaders.add("K" to keyValue)
        key = KeyParserUtil.parse(keyValue)

        return TuneHeader(
            reference = xRef,
            title = titles.ifEmpty { listOf("Unknown") },
            key = key,
            meter = actualMeter,
            length = actualLength,
            headers = allHeaders,
            unknownHeaders = unknownHeaders,
            version = version,
            playingOrder = playingOrder,
        )
    }
}
