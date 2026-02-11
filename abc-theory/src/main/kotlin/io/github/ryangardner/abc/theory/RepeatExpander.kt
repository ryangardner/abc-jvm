package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.*

/**
 * Expands repeats and variants in an ABC tune to produce a linear sequence of elements.
 * This is necessary for MIDI generation to match abcjs's output.
 */
public object RepeatExpander {

    public fun expand(tune: AbcTune): List<MusicElement> {
        val elements = tune.body.elements
        val playingOrder = tune.header.playingOrder
        
        if (playingOrder != null && playingOrder.isNotEmpty()) {
            return expandWithParts(tune, playingOrder)
        }

        // Expand voices individually
        val voiceElements = mutableMapOf<String, MutableList<MusicElement>>()
        var currentVoice = "1"
        elements.forEach { element ->
            if (element is BodyHeaderElement && element.key == "V") {
                currentVoice = element.value.split(" ", "\t").first()
            }
            voiceElements.getOrPut(currentVoice) { mutableListOf() }.add(element)
        }

        val expandedVoices = voiceElements.mapValues { (id, vElements) -> 
            val expanded = expandSingleVoice(vElements)
            expanded
        }
        
        // Re-interleave for PitchInterpreter
        val result = mutableListOf<MusicElement>()
        expandedVoices.forEach { (voiceId, vElements) ->
            result.add(BodyHeaderElement("V", voiceId))
            result.addAll(vElements)
        }
        return result
    }

    private fun expandWithParts(tune: AbcTune, playingOrder: String): List<MusicElement> {
        val bodyElements = tune.body.elements
        
        // 1. Segment body into parts
        val partMap = mutableMapOf<String, MutableList<MusicElement>>()
        var currentPartName = "START" // Elements before the first P:
        
        bodyElements.forEach { el ->
            if (el is PartElement) {
                currentPartName = el.name
            }
            partMap.getOrPut(currentPartName) { mutableListOf() }.add(el)
        }
        
        // 2. Parse playing order
        val sequence = parsePlayingOrder(playingOrder)
        
        // 3. Assemble full sequence
    val assembled = mutableListOf<MusicElement>()
    sequence.forEach { partName ->
        val partElements = partMap[partName]
        if (partElements != null) {
            assembled.addAll(partElements)
        } else if (partMap.size == 1 && partMap.containsKey("START")) {
            // If No parts are defined, but a sequence is given, maybe it's just repeating the whole thing?
            // But if the name is descriptive (like "piffero"), this is dangerous.
            // Requirement for P: parts is usually they match labels.
            // Let's be conservative: if not found, don't add.
        }
    }
    
    // If we assembled nothing (e.g. P: header was just descriptive text), return original body
    if (assembled.isEmpty()) {
        return expand(tune.copy(header = tune.header.copy(playingOrder = null)))
    }

    // 4. Expand repeats within the assembled stream
    // Note: we need to handle voices too. 
    // For simplicity, let's just use the same expand logic on the assembled stream.
    // We'll wrap it in a dummy tune to reuse the existing expand() logic's voice splitting.
    val dummyTune = tune.copy(body = TuneBody(assembled), header = tune.header.copy(playingOrder = null))
    return expand(dummyTune)
}

private fun parsePlayingOrder(order: String): List<String> {
    var i = 0
    
    fun parseInternal(): List<String> {
        val local = mutableListOf<String>()
        while (i < order.length && order[i] != ')') {
            if (order[i] == '(') {
                i++
                val group = parseInternal()
                if (i < order.length && order[i] == ')') i++
                
                if (i < order.length && order[i].isDigit()) {
                    val numStr = StringBuilder()
                    while (i < order.length && order[i].isDigit()) {
                        numStr.append(order[i])
                        i++
                    }
                    val count = numStr.toString().toInt().coerceAtMost(24) // Sanity limit for repeats
                    repeat(count) { local.addAll(group) }
                } else {
                    local.addAll(group)
                }
            } else if (order[i].isLetter()) {
                val part = order[i].toString()
                i++
                if (i < order.length && order[i].isDigit()) {
                    val numStr = StringBuilder()
                    while (i < order.length && order[i].isDigit()) {
                        numStr.append(order[i])
                        i++
                    }
                    val count = numStr.toString().toInt().coerceAtMost(24) // Sanity limit
                    repeat(count) { local.add(part) }
                } else {
                    local.add(part)
                }
            } else {
                i++ // Skip spaces, dots, etc.
            }
        }
        return local
    }
    
    return parseInternal()
}

    private fun expandSingleVoice(elements: List<MusicElement>): List<MusicElement> {
        val output = mutableListOf<MusicElement>()
        var windowStart = 0
        var i = 0
        
        while (i < elements.size) {
            val el = elements[i]
            
            if (el is BarLineElement && (el.type == BarLineType.REPEAT_END || el.type == BarLineType.REPEAT_BOTH)) {
                val block = elements.subList(windowStart, i + 1)
                val passes = if (el.repeatCount > 0) el.repeatCount.coerceAtMost(24) else 2
                
                for (p in 1..passes) {
                    var active = true
                    block.forEach { bEl ->
                        if (bEl is VariantElement) {
                            active = bEl.variants.contains(p)
                        }
                        
                        if (active) {
                            // Don't add structural repeat bars in middle passes
                            if (bEl === el && p < passes) {
                                output.add(BarLineElement(BarLineType.SINGLE))
                            } else {
                                output.add(bEl)
                            }
                        }
                    }
                }
                
                if (el.type == BarLineType.REPEAT_BOTH) {
                    windowStart = i // Section after :|: starts with this shared bar
                } else {
                    windowStart = i + 1
                }
            } else if (el is BarLineElement && el.type == BarLineType.REPEAT_START) {
                // Elements before the |: are added directly
                if (windowStart < i) {
                    output.addAll(elements.subList(windowStart, i))
                }
                windowStart = i
            }
            
            if (output.size > 20000) {
                println("ERROR: Expansion runaway detected! elements.size=${elements.size}, output.size=${output.size} at i=$i")
                break
            }
            
            i++
        }
        
        // Add remaining elements after last repeat
        if (windowStart < elements.size) {
            output.addAll(elements.subList(windowStart, elements.size))
        }
        
        return output
    }
}
