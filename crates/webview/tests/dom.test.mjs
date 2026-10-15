import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

function page() {
  const listeners = {}, events = [];
  const element = { nodeType: 1, tagName: 'BUTTON', id: 'submit', type: 'button',
    classList: ['primary'], textContent: 'Submit', value: '', parentElement: null,
    attributes: [{ name: 'id', value: 'submit' }],
    getBoundingClientRect: () => ({ x: 1, y: 2, width: 100, height: 40 }),
    contains: other => other === element };
  const styles = Object.assign(['color', 'background-color', 'border-color'], {
    getPropertyValue: key => ({ color: 'rgb(1, 2, 3)', 'background-color': 'rgb(4, 5, 6)' }[key] || '') });
  const window = { ipc: { postMessage: raw => events.push(JSON.parse(raw)) } };
  const document = { addEventListener: (type, fn) => { listeners[type] = fn; },
    querySelector: css => { if (css === '[') throw new Error('Invalid selector'); return css === '#submit' ? element : null; },
    querySelectorAll: css => css === 'button' ? [element] : [] };
  vm.runInNewContext(readFileSync(new URL('../src/dom.js', import.meta.url), 'utf8'),
    { window, document, CSS: { escape: value => value }, getComputedStyle: () => styles });
  return { window, element, listeners, events };
}

test('click and hover events carry element snapshots and pointer coordinates', () => {
  const { element, listeners, events } = page();
  for (const type of ['click', 'pointerover']) listeners[type]({ target: element, clientX: 10, clientY: 20 });
  assert.equal(events.length, 2);
  assert.equal(events[0].payload.type, 'click');
  assert.equal(events[1].payload.type, 'hover');
  assert.equal(events[0].payload.element.selector, '#submit');
  assert.equal(events[0].payload.element.styles.color, 'rgb(1, 2, 3)');
  assert.equal(events[0].payload.x, 10);
  listeners.pointerover({ target: element, relatedTarget: element });
  assert.equal(events.length, 2);
});

test('queries return full styles and report invalid or missing selectors', () => {
  const { window } = page();
  const result = window.__SWASWA_DOM__.query('#submit', true);
  assert.equal(result.ok, true);
  assert.equal(result.elements[0].styles['background-color'], 'rgb(4, 5, 6)');
  assert.equal(result.elements[0].bounds.width, 100);
  assert.equal(window.__SWASWA_DOM__.query('.missing', true).elements.length, 0);
  assert.equal(window.__SWASWA_DOM__.query('[', true).ok, false);
  assert.equal(window.__SWASWA_DOM__.query('button', false).elements.length, 1);
});

test('password input does not include the typed value or key', () => {
  const { element, listeners, events } = page();
  element.type = 'password'; element.value = 'secret';
  element.attributes.push({ name: 'value', value: 'secret' });
  listeners.keydown({ target: element, key: 's' });
  assert.equal(events[0].payload.key, null);
  assert.equal(events[0].payload.element.value, null);
  assert.equal(events[0].payload.element.attributes.value, undefined);
});
