const fs = require('fs');
const { JSDOM } = require("jsdom");
const { window } = new JSDOM("");
const { document } = window;
global.window = window;
global.document = document;
global.navigator = window.navigator;
const abcjs = require('abcjs');

const abc = fs.readFileSync(process.argv[2], 'utf8');
const tunes = abcjs.parseOnly(abc);
tunes.forEach((tune, i) => {
    const midiData = tune.setUpAudio();
    console.log(`Tune ${i} Notation check:`);
    tune.lines.forEach((line, lineIdx) => {
        if (!line.staff) return;
        line.staff.forEach(staff => {
            staff.voices.forEach((voice, voiceIdx) => {
                voice.forEach(el => {
                    if (el.el_type === 'note') {
                        console.log(`Notation: Line ${lineIdx} Voice ${voiceIdx} Note at ${el.startChar}: dur=${el.duration}`);
                    }
                });
            });
        });
    });
    console.log(`Tune ${i} MIDI check:`);
    if (midiData && midiData.tracks) {
        midiData.tracks.forEach((track, tIdx) => {
            track.forEach((event, eIdx) => {
                if (event.cmd === 'note') {
                    console.log(`MIDI Track ${tIdx} Event ${eIdx}: pitch=${event.pitch} start=${event.start} dur=${event.duration}`);
                }
            });
        });
    }
});
