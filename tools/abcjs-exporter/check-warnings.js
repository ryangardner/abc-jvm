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
    if (tune.warnings) {
        console.log(`Warnings for tune ${i}:`);
        tune.warnings.forEach(w => console.log(w));
    } else {
        console.log(`No warnings for tune ${i}`);
    }
});
