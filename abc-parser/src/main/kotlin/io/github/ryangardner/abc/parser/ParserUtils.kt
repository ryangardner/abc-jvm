package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.antlr.ABCLexer
import io.github.ryangardner.abc.antlr.ABCParser
import io.github.ryangardner.abc.core.model.Accidental
import io.github.ryangardner.abc.core.model.Decoration
import io.github.ryangardner.abc.core.model.DurationMultiplier
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

    fun parseDurationMultiplier(ctx: ABCParser.Note_lengthContext?): DurationMultiplier {
        if (ctx == null) return DurationMultiplier.DEFAULT

        // Use the labels defined in the grammar: num=DIGIT+, slashes=SLASH+, den=DIGIT*
        val numText = ctx.num?.text
        val slashesText = ctx.slashes?.text
        val denText = ctx.den?.text

        val num = numText?.toIntOrNull() ?: 1
        val slashCount = slashesText?.length ?: 0

        if (slashCount == 0) return DurationMultiplier(num, 1)

        val explicitDen = denText?.toIntOrNull()
        val den =
            if (explicitDen != null) {
                explicitDen * Math.pow(2.0, (slashCount - 1).toDouble()).toInt()
            } else {
                Math.pow(2.0, slashCount.toDouble()).toInt()
            }

        return DurationMultiplier(num, den)
    }

    fun parseDurationMultiplier(text: String): DurationMultiplier {
        if (text.isEmpty()) return DurationMultiplier.DEFAULT

        // Count slashes to determine power-of-two denominators, e.g. // -> 4, /// -> 8
        val slashCount = text.count { it == '/' }

        if (slashCount == 0) {
            // No slashes: simply parsing the numerator
            val num = text.toIntOrNull() ?: 1
            return DurationMultiplier(num, 1)
        }

        // At this point we know there's at least one slash
        val parts = text.split("/", limit = 2)
        val numStr = parts[0].trim()
        val num = if (numStr.isEmpty()) 1 else (numStr.toIntOrNull() ?: 1)

        // Parts[1] contains whatever is after the first slash.
        // It might be empty, might be more slashes, might be a number.
        val remainderStr = if (parts.size > 1) parts[1].replace("/", "").trim() else ""

        val explicitDen = remainderStr.toIntOrNull()

        val den =
            if (explicitDen != null) {
                explicitDen * Math.pow(2.0, (slashCount - 1).toDouble()).toInt()
            } else {
                Math.pow(2.0, slashCount.toDouble()).toInt()
            }

        return DurationMultiplier(num, den)
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
