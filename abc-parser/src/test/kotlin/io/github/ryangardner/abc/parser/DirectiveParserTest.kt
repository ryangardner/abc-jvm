package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.TuneMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

public class DirectiveParserTest {
    @Test
    public fun `test visualTranspose parsing`(): Unit {
        val initialMetadata = TuneMetadata()
        val directive = "visualTranspose 2"
        val updatedMetadata = DirectiveParser.parse(directive, initialMetadata)

        assertEquals(2, updatedMetadata.visualTranspose)
    }

    @Test
    public fun `test visualTranspose with negative value`(): Unit {
        val initialMetadata = TuneMetadata()
        val directive = "visualTranspose -3"
        val updatedMetadata = DirectiveParser.parse(directive, initialMetadata)

        assertEquals(-3, updatedMetadata.visualTranspose)
    }

    @Test
    public fun `test invalid visualTranspose`(): Unit {
        val initialMetadata = TuneMetadata()
        val directive = "visualTranspose invalid"
        val updatedMetadata = DirectiveParser.parse(directive, initialMetadata)

        assertNull(updatedMetadata.visualTranspose)
    }
}
