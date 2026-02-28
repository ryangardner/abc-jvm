package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.antlr.ABCLexer
import io.github.ryangardner.abc.antlr.ABCParser
import io.github.ryangardner.abc.core.model.Accidental
import io.github.ryangardner.abc.core.model.BarLineType
import io.github.ryangardner.abc.core.model.Decoration
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.core.model.TimeSignature
import org.antlr.v4.runtime.tree.TerminalNode

internal object ParserUtils {
    fun parseMeter(text: String): TimeSignature {
        val cleanText = text.substringBefore("%").trim()
        return when (cleanText) {
            "C" -> {
                TimeSignature(4, 4, "C")
            }

            "C|" -> {
                TimeSignature(2, 2, "C|")
            }

            "none" -> {
                TimeSignature.NONE
            }

            else -> {
                val parts = cleanText.split("/")
                if (parts.size >= 2) {
                    TimeSignature(parts[0].trim().toIntOrNull() ?: 4, parts[1].trim().toIntOrNull() ?: 4)
                } else {
                    TimeSignature.NONE
                }
            }
        }
    }

    fun parseLength(text: String): NoteDuration {
        val cleanText = text.substringBefore("%").trim()
        val parts = cleanText.split("/")
        return if (parts.size == 2) {
            NoteDuration(parts[0].trim().toIntOrNull() ?: 1, parts[1].trim().toIntOrNull() ?: 8)
        } else {
            NoteDuration(1, 8)
        }
    }

    fun parseAccidental(tokenType: Int): Accidental? =
        when (tokenType) {
            ABCLexer.ACC_SHARP -> Accidental.SHARP
            ABCLexer.ACC_SHARP_DBL -> Accidental.DOUBLE_SHARP
            ABCLexer.ACC_SHARP_HALF -> Accidental.QUARTER_SHARP
            ABCLexer.ACC_SHARP_DBL_HALF -> Accidental.THREE_QUARTER_SHARP
            ABCLexer.ACC_SHARP_QUART_3 -> Accidental.THREE_QUARTER_SHARP
            ABCLexer.ACC_FLAT -> Accidental.FLAT
            ABCLexer.ACC_FLAT_DBL -> Accidental.DOUBLE_FLAT
            ABCLexer.ACC_FLAT_HALF -> Accidental.QUARTER_FLAT
            ABCLexer.ACC_FLAT_DBL_HALF -> Accidental.THREE_QUARTER_FLAT
            ABCLexer.ACC_FLAT_QUART_3 -> Accidental.THREE_QUARTER_FLAT
            ABCLexer.ACC_NATURAL -> Accidental.NATURAL
            else -> null
        }

    fun parseBarLineType(tokenType: Int): BarLineType =
        when (tokenType) {
            ABCLexer.BAR_SINGLE -> BarLineType.SINGLE
            ABCLexer.BAR_THIN_DOUBLE -> BarLineType.DOUBLE
            ABCLexer.BAR_THIN_THICK -> BarLineType.FINAL
            ABCLexer.BAR_THICK_THIN -> BarLineType.DOUBLE
            ABCLexer.BAR_REP_START -> BarLineType.REPEAT_START
            ABCLexer.BAR_REP_END -> BarLineType.REPEAT_END
            ABCLexer.BAR_REP_END_ALT -> BarLineType.REPEAT_END
            ABCLexer.BAR_REP_END_TUNE -> BarLineType.REPEAT_END
            ABCLexer.BAR_REP_DBL_ALT -> BarLineType.REPEAT_BOTH
            ABCLexer.BAR_REP_DBL -> BarLineType.REPEAT_BOTH
            ABCLexer.BAR_REP_DBL_TUNE -> BarLineType.REPEAT_BOTH
            ABCLexer.BAR_THICK_THICK -> BarLineType.DOUBLE
            else -> BarLineType.SINGLE
        }

    fun calculateDuration(
        text: String,
        defaultLength: NoteDuration,
    ): NoteDuration {
        val num: Int
        val den: Int
        val slashCount = text.count { it == '/' }
        if (slashCount > 0) {
            val parts = text.split("/")
            num = if (parts[0].isEmpty()) 1 else parts[0].toIntOrNull() ?: 1
            val explicitDen = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toIntOrNull() else null
            den =
                if (explicitDen != null) {
                    explicitDen * Math.pow(2.0, (slashCount - 1).toDouble()).toInt()
                } else {
                    Math.pow(2.0, slashCount.toDouble()).toInt()
                }
        } else {
            num = text.toIntOrNull() ?: 1
            den = 1
        }
        return NoteDuration.simplify(num.toLong() * defaultLength.numerator, den.toLong() * defaultLength.denominator)
    }

    fun parseDecoration(ctx: ABCParser.Decoration_altContext): Decoration? {
        val firstChild = ctx.getChild(0)
        if (firstChild is TerminalNode) {
            val tokenType = firstChild.symbol.type
            val text = firstChild.text ?: ""

            val deco =
                when (tokenType) {
                    ABCLexer.ROLL -> Decoration("~")
                    ABCLexer.PLUS -> Decoration("+")
                    ABCLexer.UPBOW -> Decoration("u")
                    ABCLexer.DOWNBOW -> Decoration("v")
                    ABCLexer.USER_DEF_SYMBOL -> Decoration(text)
                    ABCLexer.STACCATO -> Decoration(".")
                    else -> null
                }
            if (deco != null) return deco
        }
        val content =
            ctx.children
                ?.filter {
                    it is TerminalNode &&
                        (
                            it.symbol.type == ABCLexer.BANG_DECO_CONTENT || it.symbol.type == ABCLexer.SPACE ||
                                it.symbol.type == ABCLexer.BROKEN_RHYTHM_LEFT ||
                                it.symbol.type == ABCLexer.BROKEN_RHYTHM_RIGHT
                        )
                }?.joinToString("") { it.text }
                ?.trim() ?: ""
        return if (content.isNotEmpty()) Decoration(content) else null
    }
}
