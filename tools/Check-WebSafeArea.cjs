// Run against the actual script emitted by MediaThumbnailUiTest; no app runtime dependency.
const assert = require('assert/strict'), vm = require('vm'), fs = require('fs');
const script = fs.readFileSync(process.argv[2], 'utf8');
const properties = new Map(); let style, appendCount = 0, reads = 0;
const events = [];
const context = {
  document: {
    documentElement: { style: { setProperty: (key, value) => properties.set(key, value) } }, body: {},
    head: { appendChild: value => { style = value; appendCount++; } },
    getElementById: () => style, createElement: () => ({})
  },
  getComputedStyle: () => { reads++; return { getPropertyValue: key => key.startsWith('margin') ? '8px' : '4px' }; },
  window: { innerWidth: 720, dispatchEvent: event => events.push(event) },
  CustomEvent: class { constructor(name, init) { this.type = name; this.detail = init.detail; } }
};
vm.createContext(context);
vm.runInContext(script, context); const first = {...context.window.KigttsSafeArea};
const firstHeight = parseFloat(properties.get('--kigtts-viewport-height'));
assert.ok(firstHeight > first.top + first.bottom);
vm.runInContext(script, context);
assert.equal(appendCount, 1); assert.equal(reads, 1); assert.equal(events.length, 2);
assert.ok(first.top > 0 && first.bottom > 0 && first.left > 0 && first.right > 0);
assert.equal(properties.get('--kigtts-original-padding-top'), 'calc(4px + 8px)');
assert.ok(style.textContent.includes('margin:0!important') && style.textContent.includes('padding-top:calc('));
assert.ok(style.textContent.includes('min-height:var(--kigtts-viewport-height)!important'));
context.window.innerWidth = 360; vm.runInContext(script, context);
for (const side of ['top', 'right', 'bottom', 'left']) assert.equal(context.window.KigttsSafeArea[side], first[side] / 2);
assert.equal(parseFloat(properties.get('--kigtts-viewport-height')), firstHeight / 2);
assert.equal(events[2].type, 'kigtts-safe-area-change');
const resized = script.replace(/\((\d+)\*ratio\)/, (_, n) => `(${Number(n) + 120}*ratio)`);
vm.runInContext(resized, context);
assert.ok(parseFloat(properties.get('--kigtts-viewport-height')) > firstHeight / 2);
assert.equal(appendCount, 1); assert.equal(reads, 1);
console.log('Web safe-area script: idempotent padding, full-card minimum height and viewport scaling passed.');
