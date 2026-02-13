package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.theory.MeasureError
import java.io.File

public object FidelityReporter {

    public fun reportMeasureErrors(file: File, tuneIndex: Int, errors: List<MeasureError>, originalAbc: String): String {
        if (errors.isEmpty()) return ""
        
        val lines = originalAbc.lines()
        val sb = StringBuilder()
        sb.append("\n--- SEMANTIC FIDELITY FAILURE ---\n")
        sb.append("File: ${file.path}\n")
        sb.append("Tune Index: $tuneIndex\n")
        sb.append("Total Errors: ${errors.size}\n")
        sb.append("\nDiscrepancies found in measure durations:\n")
        
        errors.take(10).forEach { error ->
            sb.append("  [Voice: ${error.voice}] Measure ${error.measureIndex}: Expected ${error.expectedDuration}, but got ${error.actualDuration}\n")
        }
        if (errors.size > 10) {
            sb.append("  ... and ${errors.size - 10} more errors\n")
        }
        
        val tuneTitle = lines.find { it.startsWith("T:") } ?: ""
        sb.append("\nContext (Tune Title: $tuneTitle):\n")
        val bodyLines = lines.filter { it.isNotEmpty() && !it.startsWith("%") && it.contains(Regex("[a-gA-Gz]")) }.take(5)
        bodyLines.forEach { sb.append("  > $it\n") }
        
        sb.append("---------------------------------\n")
        return sb.toString()
    }

    public fun reportRoundTripFailure(
        file: File, 
        tuneIndex: Int, 
        original: AbcTune, 
        roundTripped: AbcTune, 
        message: String, 
        originalAbc: String? = null,
        serializedAbc: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append("\n--- ROUND-TRIP FIDELITY FAILURE ---\n")
        sb.append("File: ${file.path}\n")
        sb.append("Tune Index: $tuneIndex\n")
        sb.append("Reason: $message\n")
        
        val origElements = original.body.elements
        val rtElements = roundTripped.body.elements
        val size = minOf(origElements.size, rtElements.size)
        
        var firstDiffIdx = -1
        for (i in 0 until size) {
            // Compare without location for the report to be clear about WHAT changed, not WHERE
            if (origElements[i].withoutLocation() != rtElements[i].withoutLocation()) {
                firstDiffIdx = i
                break
            }
        }
        
        if (firstDiffIdx != -1) {
            val element = origElements[firstDiffIdx]
            val rtElement = rtElements[firstDiffIdx]
            sb.append("\nFirst structural difference at element index $firstDiffIdx:\n")
            sb.append("  EXPECTED: ${element.withoutLocation()}\n")
            sb.append("  ACTUAL:   ${rtElement.withoutLocation()}\n")
            
            if (originalAbc != null && element.line > 0) {
                val lines = originalAbc.lines()
                val lineIdx = element.line - 1
                if (lineIdx in lines.indices) {
                    sb.append("\nOriginal ABC at Line ${element.line}:\n")
                    sb.append("  ${lines[lineIdx]}\n")
                    val pointer = " ".repeat(maxOf(0, element.column)) + "^"
                    sb.append("  $pointer\n")
                }
            }

            if (serializedAbc != null && rtElement.line > 0) {
                val lines = serializedAbc.lines()
                val lineIdx = rtElement.line - 1
                if (lineIdx in lines.indices) {
                    sb.append("\nSerialized ABC at Line ${rtElement.line}:\n")
                    sb.append("  ${lines[lineIdx]}\n")
                    val pointer = " ".repeat(maxOf(0, rtElement.column)) + "^"
                    sb.append("  $pointer\n")
                }
            }

            val start = maxOf(0, firstDiffIdx - 2)
            val end = minOf(size - 1, firstDiffIdx + 2)
            sb.append("\nSurrounding AST Context (Expected):\n")
            for (i in start..end) {
                val prefix = if (i == firstDiffIdx) " -> " else "    "
                sb.append("$prefix[$i] ${origElements[i].withoutLocation()}\n")
            }
        } else if (rtElements.size != origElements.size) {
            sb.append("\nElement count mismatch: Expected ${origElements.size}, got ${rtElements.size}\n")
            if (rtElements.size > origElements.size) {
                sb.append("Extra elements in ACTUAL:\n")
                rtElements.subList(size, rtElements.size).take(5).map { it.withoutLocation() }.forEach { sb.append("  + $it\n") }
            } else {
                sb.append("Missing elements in ACTUAL (present in EXPECTED):\n")
                origElements.subList(size, origElements.size).take(5).map { it.withoutLocation() }.forEach { sb.append("  - $it\n") }
            }
        }
        
        sb.append("------------------------------------\n")
        return sb.toString()
    }

    public fun reportUnrecognizedCharacters(file: File, tuneIndex: Int, tune: AbcTune): String {
        // We only report characters that are definitely not supposed to be there.
        // 'y', '$', ':', '`', ']', '0'-'9' and broken rhythm symbols (>, <) are handled as spacers, so we ignore them here.
        val unrecognized = tune.body.elements.filter { 
            it is io.github.ryangardner.abc.core.model.SpacerElement && 
            (it.text.length == 1 && !it.text[0].isWhitespace() && it.text[0] != 'y' && it.text[0] != '$' && it.text[0] != '>' && it.text[0] != '<' && it.text[0] != '`' && it.text[0] != ':' && it.text[0] != ']' && it.text[0] != '!' && !it.text[0].isDigit()) 
        }
        if (unrecognized.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("\n--- UNRECOGNIZED CHARACTERS ---\n")
        sb.append("File: ${file.path}\n")
        sb.append("Tune Index: $tuneIndex\n")
        sb.append("Found ${unrecognized.size} unrecognized characters that were captured as spacers:\n")
        
        unrecognized.take(10).forEach { 
            sb.append("  '${(it as io.github.ryangardner.abc.core.model.SpacerElement).text}' at Line ${it.line}, Col ${it.column}\n")
        }
        if (unrecognized.size > 10) sb.append("  ... and ${unrecognized.size - 10} more\n")
        sb.append("-------------------------------\n")
        return sb.toString()
    }
}
