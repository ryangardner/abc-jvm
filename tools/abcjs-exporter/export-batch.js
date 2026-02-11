
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
    const tunes = abcjs.parseOnly(abcString);
    const result = tunes.map(tune => {
        // Capture expanded events via TimingCallbacks
        const expandedEvents = [];
        // Note: TimingCallbacks needs a midiContext or just works in 6.x if we shim enough
        // If TimingCallbacks is not available or hard to use in JSDOM, 
        // we can use tune.setUpAudio() then iterate over tracks.
        const midiData = tune.setUpAudio ? tune.setUpAudio() : null;

        // We also want to capture the lines but in a way that allows us to find the notes
        // However, as identified, lines is unexpanded.

        return {
            metaText: tune.metaText,
            formatting: tune.formatting,
            lines: tune.lines,
            midiData: midiData, // Linear MIDI sequence (expanded)
            warnings: tune.warnings
        };
    });

    const cache = new Set();
    return JSON.stringify(result, (key, value) => {
        if (key === 'abcregs' || key === 'svg' || key === 'engraver' || key === 'elem' || key === 'parentStaff') return undefined;
        // Don't skip if it's a note or rest, even if we've seen it, to allow uncompressed linear sequences
        if (value && (value.el_type === 'note' || value.el_type === 'rest')) {
            // Deep clone without circular refs to break sharing
            return {
                el_type: value.el_type,
                duration: value.duration,
                pitches: value.pitches,
                midiPitches: value.midiPitches,
                rest: value.rest,
                currentTrackMilliseconds: value.currentTrackMilliseconds,
                currentTrackWholeNotes: value.currentTrackWholeNotes,
                startChar: value.startChar,
                endChar: value.endChar
            };
        }
        if (typeof value === 'object' && value !== null) {
            if (cache.has(value)) return;
            cache.add(value);
        }
        return value;
    }, 2);
}

const args = process.argv.slice(2);
if (args.length < 1) {
    console.error('Usage: node export-batch.js <batch_dir>');
    process.exit(1);
}

const batchDir = args[0];
const abcFilesDir = path.join(batchDir, 'abc_files');
const outputDir = path.join(batchDir, 'midi_json');

if (!fs.existsSync(abcFilesDir)) {
    console.error('Batch directory must contain abc_files/:', abcFilesDir);
    process.exit(1);
}

if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir);
}

const files = fs.readdirSync(abcFilesDir).filter(f => f.endsWith('.abc'));
console.log(`Processing ${files.length} files...`);

files.forEach((file, index) => {
    const filePath = path.join(abcFilesDir, file);
    const outputFilePath = path.join(outputDir, file.replace('.abc', '.json'));

    // Force re-process for debugging
    // if (fs.existsSync(outputFilePath)) return;

    try {
        const abcInput = fs.readFileSync(filePath, 'utf8');
        const json = exportAbc(abcInput);
        fs.writeFileSync(outputFilePath, json);
        if (index % 100 === 0) console.log(`Processed ${index}/${files.length}...`);
    } catch (e) {
        console.error(`Error processing ${file}:`, e);
    }
});

console.log('Batch processing complete.');
