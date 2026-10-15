import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const BASE = 'https://webview4j.localhost/';

/**
 * A page with the bridge, whose fetches wait in `requests` until the test answers them. With
 * `ipc`, the page has the webview's IPC and its posts land in `posted`.
 */
function page({ ipc = false } = {}) {
  const requests = [];
  const posted = [];
  const fetch = (url, init) => new Promise((resolve, reject) => requests.push({ url, init, resolve, reject }));
  const window = ipc ? { ipc: { postMessage: (text) => posted.push(text) } } : {};
  const errors = [];
  const context = { window, fetch, Response, TextDecoder, TextEncoder, Uint8Array, setTimeout, console: { error: (...a) => errors.push(a) } };
  const source = readFileSync(new URL('../src/bridge.js', import.meta.url), 'utf8')
    .replace('__WEBVIEW4J_BASE__', BASE).replace('__WEBVIEW4J_FAST_LIMIT__', '65536');
  vm.runInNewContext(source, context);
  vm.runInNewContext(source, context);   // installing twice must change nothing
  return { bridge: window.__webview4j, requests, posted, errors };
}

/** Frames as Rust sends a batch of emits (PROTOCOL.md §1); an emit may carry its number, `seq`. */
function frames(...emits) {
  return Buffer.concat(emits.map(([topic, data, seq]) => {
    const payload = Buffer.from(JSON.stringify(data));
    const block = Buffer.from((seq ? `Seq: ${seq}\r\n` : '') + 'Content-Type: application/json\r\n\r\n');
    return Buffer.concat([Buffer.from(`MSG view.wv-1.emit.${topic} ${block.length} ${payload.length}\r\n`), block, payload]);
  }));
}

const tick = () => new Promise((resolve) => setImmediate(resolve));
// Values made inside the page's realm have its prototypes: compare them as plain data.
const plain = (value) => JSON.parse(JSON.stringify(value));

test('invoke posts the arguments to the command and resolves with the answer', async () => {
  const { bridge, requests } = page();
  const found = bridge.invoke('users.find', { id: 42 });
  await tick();
  assert.equal(requests[0].url, BASE + 'invoke/users.find');
  assert.equal(requests[0].init.method, 'POST');
  assert.equal(requests[0].init.headers['Content-Type'], 'text/plain', 'a simple request: no preflight per call');
  assert.equal(requests[0].init.body, '{"id":42}');
  requests[0].resolve(new Response('{"name":"Ada"}', { status: 200 }));
  assert.deepEqual(plain(await found), { name: 'Ada' });

  const nothing = bridge.invoke('users.ping');
  await tick();
  assert.equal(requests[1].init.body, 'null');
  requests[1].resolve(new Response('', { status: 200 }));
  assert.equal(await nothing, null);
});

test('a failed command rejects with an InvokeError carrying the command and status', async () => {
  const { bridge, requests } = page();
  const failing = bridge.invoke('users.delete');
  await tick();
  requests[0].resolve(new Response('no command \'users.delete\'', { status: 404 }));
  await assert.rejects(failing, (error) => error.name === 'InvokeError' && error.command === 'users.delete'
    && error.status === 404 && error.message === "no command 'users.delete'");
});

test('invoke refuses a call without a command, before any request', async () => {
  const { bridge, requests } = page();
  await assert.rejects(bridge.invoke(''), (error) => error.name === 'TypeError');
  assert.equal(requests.length, 0);
});

test('listening holds one poll open; each batch reaches its topics, then the next poll goes out', async () => {
  const { bridge, requests, errors } = page();
  const seen = [];
  const unlisten = bridge.listen('orders.updated', (data) => seen.push(['a', data]));
  bridge.listen('orders.updated', () => { throw new Error('broken listener'); });
  bridge.listen('other', (data) => seen.push(['other', data]));
  await tick();
  assert.equal(requests.length, 1, 'one poll, however many listeners');
  assert.equal(requests[0].url, BASE + 'events');

  requests[0].resolve(new Response(frames(['orders.updated', { id: 1 }], ['other', 'x']), { status: 200 }));
  await tick(); await tick();
  assert.deepEqual(plain(seen), [['a', { id: 1 }], ['other', 'x']]);
  assert.equal(errors.length, 1, 'a failing listener is logged, the others still run');
  assert.equal(requests.length, 2, 'the next poll goes out');

  unlisten();
  requests[1].resolve(new Response(frames(['orders.updated', { id: 2 }]), { status: 200 }));
  await tick(); await tick();
  assert.deepEqual(plain(seen), [['a', { id: 1 }], ['other', 'x']]);
});

test('polling stops when the window closes', async () => {
  const { bridge, requests } = page();
  bridge.listen('x', () => {});
  await tick();
  requests[0].resolve(new Response('the window closed', { status: 410 }));
  await tick(); await tick();
  assert.equal(requests.length, 1);
});

test('the page cannot replace the bridge', () => {
  const { bridge } = page();
  assert.ok(Object.isFrozen(bridge));
  assert.deepEqual(Object.keys(bridge).sort(), ['deliver', 'invoke', 'listen', 'settle']);
});

// ---- UI actions: the webview's IPC ----

test('a small call goes over IPC as one frame and is settled by the host', async () => {
  const { bridge, requests, posted } = page({ ipc: true });
  const found = bridge.invoke('users.find', { id: 42, name: 'é' });
  assert.equal(requests.length, 0, 'no fetch for a UI action');
  const body = '{"id":42,"name":"é"}';
  assert.equal(posted[0], `MSG invoke.users.find 11 ${Buffer.byteLength(body)}\r\nCall: 1\r\n\r\n${body}`, 'lengths in bytes');
  bridge.settle(1, 200, { name: 'Ada' }, null);
  bridge.settle(1, 200, 'again', null);   // a call settles once
  assert.deepEqual(plain(await found), { name: 'Ada' });

  const failing = bridge.invoke('users.delete');
  bridge.settle(2, 404, "no command 'users.delete'", null);
  await assert.rejects(failing, (error) => error.name === 'InvokeError' && error.status === 404
    && error.command === 'users.delete' && error.message === "no command 'users.delete'");
});

test('a large answer is fetched where the host says', async () => {
  const { bridge, requests } = page({ ipc: true });
  const read = bridge.invoke('files.read');
  bridge.settle(1, 200, null, 'calls/1');
  await tick();
  assert.equal(requests[0].url, BASE + 'calls/1');
  requests[0].resolve(new Response('"lots of data"', { status: 200 }));
  assert.equal(await read, 'lots of data');
});

test('large arguments are data: they go over the custom protocol, not IPC', async () => {
  const { bridge, requests, posted } = page({ ipc: true });
  bridge.invoke('files.write', { text: 'x'.repeat(70000) });
  await tick();
  assert.equal(posted.length, 0);
  assert.equal(requests[0].url, BASE + 'invoke/files.write');
});

test('a command name that cannot be a subject is refused in the page', async () => {
  const { bridge, posted, requests } = page({ ipc: true });
  await assert.rejects(bridge.invoke('users find'), (error) => error.name === 'InvokeError' && error.status === 400);
  assert.equal(posted.length + requests.length, 0);
});

// ---- pushes: small ones by deliver(...), large ones by the poll, in Java's order ----

test('a small emit is delivered by the host at once', async () => {
  const { bridge, requests } = page();
  const seen = [];
  bridge.listen('orders.updated', (data) => seen.push(data));
  bridge.deliver(1, 'orders.updated', { id: 1 });
  bridge.deliver(2, 'other', 'ignored');
  bridge.deliver(3, 'orders.updated', { id: 2 });
  assert.deepEqual(plain(seen), [{ id: 1 }, { id: 2 }]);
  assert.equal(requests.length, 1, 'only the poll, for large emits');
});

test('emits arrive in the order Java sent them, whichever channel each took', async () => {
  const { bridge, requests } = page();
  const seen = [];
  bridge.listen('feed', (data) => seen.push(data));
  bridge.deliver(1, 'feed', 'one');
  bridge.deliver(3, 'feed', 'three');   // sent after a large emit that is still on its way
  assert.deepEqual(plain(seen), ['one'], 'three waits for two');
  await tick();
  requests[0].resolve(new Response(frames(['feed', 'two (large)', 2]), { status: 200 }));
  await tick(); await tick();
  assert.deepEqual(plain(seen), ['one', 'two (large)', 'three']);
});

test('a number that never comes is skipped after a moment', async () => {
  const { bridge } = page();
  const seen = [];
  bridge.listen('feed', (data) => seen.push(data));
  bridge.deliver(5, 'feed', 'five');
  bridge.deliver(7, 'feed', 'seven');   // six was dropped
  assert.deepEqual(plain(seen), ['five']);
  await new Promise((resume) => setTimeout(resume, 150));
  assert.deepEqual(plain(seen), ['five', 'seven']);
});
