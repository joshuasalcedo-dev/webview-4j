import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const BASE = 'https://webview4j.localhost/';

/** A page with the bridge, whose fetches wait in `requests` until the test answers them. */
function page() {
  const requests = [];
  const fetch = (url, init) => new Promise((resolve, reject) => requests.push({ url, init, resolve, reject }));
  const window = {};
  const errors = [];
  const context = { window, fetch, Response, TextDecoder, Uint8Array, setTimeout, console: { error: (...a) => errors.push(a) } };
  const source = readFileSync(new URL('../src/bridge.js', import.meta.url), 'utf8').replace('__WEBVIEW4J_BASE__', BASE);
  vm.runInNewContext(source, context);
  vm.runInNewContext(source, context);   // installing twice must change nothing
  return { bridge: window.__webview4j, requests, errors };
}

/** Frames as Rust sends a batch of emits (PROTOCOL.md §1). */
function frames(...emits) {
  return Buffer.concat(emits.map(([topic, data]) => {
    const payload = Buffer.from(JSON.stringify(data));
    const block = Buffer.from('Content-Type: application/json\r\n\r\n');
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

test('the page cannot replace the bridge, and it runs nothing it is sent', () => {
  const { bridge } = page();
  assert.ok(Object.isFrozen(bridge));
  assert.deepEqual(Object.keys(bridge).sort(), ['invoke', 'listen'], 'no resolve or emit for the host to call');
});
