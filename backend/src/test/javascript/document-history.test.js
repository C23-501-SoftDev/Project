const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

class Element {
    constructor(tagName) {
        this.tagName = tagName;
        this.children = [];
        this.className = '';
        this.textContent = '';
        this.attributes = {};
    }

    append(child) {
        this.children.push(child);
    }

    replaceChildren() {
        this.children = [];
    }

    setAttribute(name, value) {
        this.attributes[name] = String(value);
    }
}

global.window = {};
global.document = {
    createElement: tagName => new Element(tagName),
    addEventListener: () => {},
    getElementById: () => null
};

require(path.resolve(__dirname, '../../main/resources/static/js/document-history.js'));

const result = new Element('section');
window.documentHistoryDiff.renderDiff(result, [
    {type: 'REMOVED', beforeLineNumber: 3, afterLineNumber: null, content: '<script>alert(1)</script>'},
    {type: 'ADDED', beforeLineNumber: null, afterLineNumber: 3, content: 'safe text'}
]);

const rows = result.children[0].children[0].children;
assert.equal(rows[0].className, 'diff-line diff-line-removed');
assert.equal(rows[0].children[3].children[0].textContent, '<script>alert(1)</script>');
assert.equal(rows[1].className, 'diff-line diff-line-added');
assert.equal(rows[1].children[3].children[0].textContent, 'safe text');

window.documentHistoryDiff.renderDiff(result, []);
assert.equal(result.children[0].textContent, 'Версии не отличаются.');

const sideBySide = new Element('section');
window.documentHistoryDiff.renderDiff(sideBySide, [
    {type: 'REMOVED', beforeLineNumber: 1, afterLineNumber: null, content: 'old'},
    {type: 'ADDED', beforeLineNumber: null, afterLineNumber: 1, content: 'new'},
    {type: 'CONTEXT', beforeLineNumber: 2, afterLineNumber: 2, content: 'same'}
], 'side-by-side');
const sideRows = sideBySide.children[0].children[0].children;
assert.equal(sideRows[0].children[1].children[0].textContent, 'old');
assert.equal(sideRows[0].children[5].children.length, 0);
assert.equal(sideRows[1].children[1].children.length, 0);
assert.equal(sideRows[1].children[5].children[0].textContent, 'new');
assert.equal(sideRows[2].children[1].children[0].textContent, 'same');
assert.equal(sideRows[2].children[5].children[0].textContent, 'same');

const segmented = new Element('section');
window.documentHistoryDiff.renderDiff(segmented, [{
    type: 'REMOVED', beforeLineNumber: 1, afterLineNumber: null, content: 'colour',
    segments: [
        {type: 'UNCHANGED', content: 'colo'},
        {type: 'REMOVED', content: 'u'},
        {type: 'UNCHANGED', content: 'r'}
    ]
}]);
const segmentedContent = segmented.children[0].children[0].children[0].children[3];
assert.equal(segmentedContent.children[1].className, 'diff-segment-removed');
assert.equal(segmentedContent.children[1].textContent, 'u');

const replacement = new Element('section');
window.documentHistoryDiff.renderDiff(replacement, [{
    type: 'MODIFIED', beforeLineNumber: 1, afterLineNumber: 1,
    segments: [
        {type: 'UNCHANGED', content: 'Чаш'},
        {type: 'REMOVED', content: 'а'},
        {type: 'ADDED', content: 'и'}
    ]
}], 'side-by-side');
const replacementRows = replacement.children[0].children[0].children;
assert.equal(replacementRows.length, 1);
assert.equal(replacementRows[0].children[2].textContent, '−');
assert.equal(replacementRows[0].children[3].textContent, '+');
assert.equal(replacementRows[0].children[1].children.map(child => child.textContent).join(''), 'Чаша');
assert.equal(replacementRows[0].children[5].children.map(child => child.textContent).join(''), 'Чаши');

const select = new Element('select');
window.documentHistoryDiff.addVersionOptions(select, [
    {gitHash: 'a'.repeat(40), comment: 'Initial', createdAt: null},
    {gitHash: 'b'.repeat(40), comment: 'Update', createdAt: null}
]);
assert.equal(select.disabled, false);
assert.equal(select.children.length, 3);
assert.match(select.children[1].textContent, /^aaaaaaa — Initial/);

const oneVersionSelect = new Element('select');
window.documentHistoryDiff.addVersionOptions(oneVersionSelect, [
    {gitHash: 'c'.repeat(40), comment: 'Only version', createdAt: null}
]);
assert.equal(oneVersionSelect.disabled, false);


const historyTemplate = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/pages/document-history.html'), 'utf8');
assert.match(historyTemplate, /value="CHARACTER" selected/);
assert.match(historyTemplate, /value="LINE"/);
assert.doesNotMatch(historyTemplate, /value="HYBRID"|value="WORD"/);
assert.doesNotMatch(historyTemplate, /id="diffFrom" required disabled|id="diffTo" required disabled/);
assert.match(historyTemplate, /id="diffModeToggle" class="diff-secondary-button"/);
assert.match(historyTemplate, /id="diffContextToggle" class="diff-secondary-button"/);

const documentViewTemplate = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/pages/document-view.html'), 'utf8');
assert.match(documentViewTemplate, /id="historyLink"/);

const editorTemplate = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/pages/document-edit.html'), 'utf8');
assert.match(editorTemplate, /id="rollbackVersionLink"/);
assert.match(editorTemplate, /rollback=true/);
assert.match(historyTemplate, /id="rollbackSave"/);

const historyScript = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/document-history.js'), 'utf8');
assert.match(historyScript, /get\('rollback'\) === 'true'/);
assert.match(historyScript, /fromInput\.disabled = true/);
assert.match(historyScript, /versions\/\$\{encodeURIComponent\(targetHash\)\}\/restore/);
