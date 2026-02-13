package io.github.ryangardner.abc.core.model

public data class TuneBody(
    public val elements: List<MusicElement>
) {
    public fun withoutLocation(): TuneBody = copy(elements = elements.map { it.withoutLocation() })
}
