const fs = require('fs');
const path = require('path');
const { JSDOM } = require("jsdom");

// Shim the DOM for abcjs
const { window } = new JSDOM("");
const { document } = window;
global.window = window;
global.document = document;
global.navigator = window.navigator;

const abcjs = require('abcjs');

function exportAbc(abcString) {
    // parseOnly returns an array of tunes with full semantic data (lines, etc.)
    // but skips the SVG/DOM generation path.
    const tunes = abcjs.parseOnly(abcString);
    const result = tunes.map(tune => {
        // setUpAudio returns absolute MIDI events (pitch, duration, start)
        const midiData = tune.setUpAudio ? tune.setUpAudio() : null;

        return {
            metaText: tune.metaText,
            formatting: tune.formatting,
            lines: tune.lines,
            midiEvents: midiData ? midiData.tracks : null
        };
    });

    if (result.length > 0) {
        console.error('Parsed tunes:', result.length);
    }

    // We want to avoid circular references in the JSON
    const cache = new Set();
    return JSON.stringify(result, (key, value) => {
        // High-level filter to keep the JSON manageable and avoid circularity
        if (key === 'abcregs' || key === 'svg' || key === 'engraver' || key === 'elem') return undefined;

        if (typeof value === 'object' && value !== null) {
            if (cache.has(value)) return;
            cache.add(value);
        }
        return value;
    }, 2);
}

const args = process.argv.slice(2);
if (args.length < 1) {
    console.error('Usage: node export-abc.js <abcfile_or_string>');
    process.exit(1);
}

let abcInput = args[0];
if (fs.existsSync(abcInput)) {
    abcInput = fs.readFileSync(abcInput, 'utf8');
}

try {
    const json = exportAbc(abcInput);
    console.log(json);
} catch (e) {
    console.error('Error parsing ABC:', e);
    process.exit(1);
}
