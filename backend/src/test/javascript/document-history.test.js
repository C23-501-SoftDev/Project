const assert = require('node:assert/strict');
const path = require('node:path');

class Element {
    constructor(tagName) {
        this.tagName = tagName;
        this.children = [];
        this.className = '';
        this.textContent = '';
    }

    append(child) {
        this.children.push(child);
    }

    replaceChildren() {
        this.children = [];
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
assert.equal(rows[0].children[3].textContent, '<script>alert(1)</script>');
assert.equal(rows[1].className, 'diff-line diff-line-added');
assert.equal(rows[1].children[3].textContent, 'safe text');

window.documentHistoryDiff.renderDiff(result, []);
assert.equal(result.children[0].textContent, 'Версии не отличаются.');

const sideBySide = new Element('section');
window.documentHistoryDiff.renderDiff(sideBySide, [
    {type: 'REMOVED', beforeLineNumber: 1, afterLineNumber: null, content: 'old'},
    {type: 'ADDED', beforeLineNumber: null, afterLineNumber: 1, content: 'new'},
    {type: 'CONTEXT', beforeLineNumber: 2, afterLineNumber: 2, content: 'same'}
], 'side-by-side');
const sideRows = sideBySide.children[0].children[0].children;
assert.equal(sideRows[0].children[1].textContent, 'old');
assert.equal(sideRows[0].children[5].textContent, '');
assert.equal(sideRows[1].children[1].textContent, '');
assert.equal(sideRows[1].children[5].textContent, 'new');
assert.equal(sideRows[2].children[1].textContent, 'same');
assert.equal(sideRows[2].children[5].textContent, 'same');

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
