package io.github.ryangardner.abc.core.model

/**
 * Represents a fully parsed ABC tune.
 * Immutable and thread-safe.
 */
public data class AbcTune(
    public val header: TuneHeader,
    public val body: TuneBody,
    public val metadata: TuneMetadata
)
