package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.TuneMetadata

object DirectiveParser {
    fun parse(directiveText: String, currentMetadata: TuneMetadata): TuneMetadata {
        val trimmed = directiveText.trim()
        if (trimmed.startsWith("visualTranspose")) {
            val value = trimmed.substringAfter("visualTranspose").trim().toIntOrNull()
            return if (value != null) {
                currentMetadata.copy(visualTranspose = value)
            } else {
                currentMetadata
            }
        }
        // TODO: Handle staffwidth, MIDI
        // For now, only visualTranspose is explicitly requested in plan for Task 2.3
        // But architecture mentions MIDI too.
        // Let's implement visualTranspose first as it's directly mapped to TuneMetadata

        return currentMetadata
    }
}
