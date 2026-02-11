
const fs = require('fs');
const { JSDOM } = require("jsdom");
const { window } = new JSDOM("");
const { document } = window;
global.window = window;
global.document = document;
global.navigator = window.navigator;
const abcjs = require('abcjs');

const abc = fs.readFileSync(process.argv[2], 'utf8');
const div = document.createElement("div");
abcjs.renderAbc(div, abc);
const tunes = abcjs.parseOnly(abc); // We might need the one from renderAbc?
// renderAbc returns an array of tune objects that have the engraver attached.
const renderedTunes = abcjs.renderAbc(div, abc);
const tune = renderedTunes[0];

const midiData = tune.setUpAudio ? tune.setUpAudio() : null;
console.log("MidiData tracking success:", !!midiData);

tune.lines.forEach((line, lIdx) => {
    if (!line.staff) return;
    line.staff.forEach((staff, sIdx) => {
        if (!staff.voices) return;
        staff.voices.forEach((voice, vIdx) => {
            voice.forEach(el => {
                if (el.el_type === 'note' && el.midiPitches) {
                    if (el.midiPitches.length > 1) {
                        console.log(`Note at char ${el.startChar} has ${el.midiPitches.length} midiPitches!`);
                    }
                }
            });
        });
    });
});
if (tune.makeVoicesArray) {
    const voices = tune.makeVoicesArray();
    console.log("Voices array length:", voices.length);
    if (voices.length > 0) {
        console.log("First voice length:", voices[0].length);
        voices[0].forEach((el, i) => {
            if (el.elem) {
                console.log(`Elem ${i} type: ${el.elem.type}, dur: ${el.elem.duration}`);
                if (el.elem.heads && el.elem.heads.length > 0) {
                    const pitch = el.elem.heads[0].pitch;
                    console.log(`  pitch: ${pitch}`);
                }
            }
        });
    }
}

// Look for anything that might be an expanded list
for (const key of Object.keys(tune)) {
    if (Array.isArray(tune[key]) && tune[key].length > 100) {
        console.log(`Potential expanded array: ${key}, length: ${tune[key].length}`);
    }
}
