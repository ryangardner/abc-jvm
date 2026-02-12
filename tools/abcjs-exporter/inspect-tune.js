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
const renderedTunes = abcjs.renderAbc(div, abc);
const tune = renderedTunes[0];

tune.lines.forEach((line, lIdx) => {
    if (!line.staff) return;
    line.staff.forEach((staff, sIdx) => {
        if (!staff.voices) return;
        staff.voices.forEach((voice, vIdx) => {
            voice.forEach(el => {
                let desc = el.el_type;
                if (el.pitches) {
                    desc += " pitch=" + el.pitches.map(p => p.name).join(",");
                }
                console.log(`Voice ${vIdx} Elem at char ${el.startChar}-${el.endChar}: ${desc} dur=${el.duration}`);
            });
        });
    });
});

const midiData = tune.setUpAudio ? tune.setUpAudio() : null;
if (midiData && midiData.tracks) {
    midiData.tracks.forEach((track, tIdx) => {
        track.forEach((event, eIdx) => {
            if (event.cmd === 'note') {
                console.log(`MIDI Track ${tIdx} Event ${eIdx}: pitch=${event.pitch} start=${event.start} dur=${event.duration}`);
            }
        });
    });
}
