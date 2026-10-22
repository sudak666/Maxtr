// Plain-node test for js/ui-widgets.js's safeColor(): a color from synced data
// (a shared-profile editor controls it) must never break out of style="".
// Run: node tests/safe-color.mjs
import assert from 'node:assert/strict';
// ui-widgets.js imports state.js, which reads window.currentLang at load.
globalThis.window = { currentLang: 'uk' };
const { safeColor } = await import('../js/ui-widgets.js');

assert.equal(safeColor('#8b5cf6'), '#8b5cf6');
assert.equal(safeColor('#ABC'), '#ABC');
assert.equal(safeColor('#11223344'), '#11223344');
assert.equal(safeColor('red"><b>x</b>'), '#8b5cf6');
assert.equal(safeColor('#fff;background:url(https://evil)'), '#8b5cf6');
assert.equal(safeColor(undefined), '#8b5cf6');
assert.equal(safeColor(42, '#000'), '#000');
console.log('safe-color: OK');
