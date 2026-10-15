import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

function page() {
  const posted = [];
  const window = { ipc: { postMessage: (raw) => posted.push(JSON.parse(raw)) } };
  const errors = [];
  const console = { error: (...args) => errors.push(args) };
  const source = readFileSync(new URL('../src/bridge.js', import.meta.url), 'utf8');
  vm.runInNewContext(source, { window, console });
  vm.runInNewContext(source, { window, console });   // installing twice must change nothing
  return { bridge: window.__webview4j, posted, errors };
}

test('invoke posts the call and settles when the host resolves it', async () => {
  const { bridge, posted } = page();
  const found = bridge.invoke('users.find', { id: 42 });
  const failing = bridge.invoke('users.delete');
  assert.deepEqual(posted, [
    { event: 'invoke', payload: { id: 1, command: 'users.find', args: { id: 42 } } },
    { event: 'invoke', payload: { id: 2, command: 'users.delete', args: null } },
  ]);
  bridge.resolve(1, true, { name: 'Ada' });
  bridge.resolve(2, false, "no command 'users.delete'");
  bridge.resolve(1, true, 'again');   // a call settles once
  assert.deepEqual(await found, { name: 'Ada' });
  await assert.rejects(failing, (error) => error.name === 'InvokeError'
    && error.command === 'users.delete' && error.message === "no command 'users.delete'");
});

test('invoke refuses a call without a command', async () => {
  const { bridge, posted } = page();
  await assert.rejects(bridge.invoke(''), (error) => error.name === 'TypeError');
  assert.equal(posted.length, 0);
});

test('listeners get what the host emits until they unlisten, and one failing does not stop the rest', () => {
  const { bridge, errors } = page();
  const seen = [];
  const unlisten = bridge.listen('orders', (data) => seen.push(['a', data]));
  bridge.listen('orders', () => { throw new Error('broken listener'); });
  bridge.listen('orders', (data) => seen.push(['c', data]));
  bridge.emit('orders', 1);
  unlisten();
  bridge.emit('orders', 2);
  bridge.emit('other', 3);
  assert.deepEqual(seen, [['a', 1], ['c', 1], ['c', 2]]);
  assert.equal(errors.length, 2);
});

test('the page cannot replace the bridge', () => {
  const { bridge } = page();
  assert.ok(Object.isFrozen(bridge));
});
