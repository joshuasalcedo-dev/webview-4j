import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

function browser(fetch) {
  const calls = [];
  class Xhr {
    listeners = {};
    status = 0;
    responseText = '';
    open() {}
    send() {}
    setRequestHeader() {}
    getAllResponseHeaders() { return ''; }
    addEventListener(event, callback) { this.listeners[event] = callback; }
  }
  const window = { fetch, XMLHttpRequest: Xhr,
    ipc: { postMessage(raw) { calls.push(JSON.parse(raw)); } } };
  const context = { window, URL, Headers, FormData, Blob, ArrayBuffer,
    location: { href: 'https://example.com/' },
    document: { cookie: '' }, performance: { now: () => 10 },
    console: { log() {}, error() {} } };
  let source = ['intro', 'helpers', 'fetch', 'xhr']
    .map(name => readFileSync(new URL(`../src/script/${name}.js`, import.meta.url), 'utf8'))
    .join('\n') + '\n})();';
  for (const [key, value] of Object.entries({ LOG_PREFIX: 'test', HOST_PATTERN: '.*',
    API_PATTERN: '.*', CAPTURE_PATTERN: '(?!)', WS_PATTERN: '(?!)',
    TRACE_EVENT: 'api-call', CLICK_EVENT: 'click', CAPTURE_EVENT: 'capture', WS_EVENT: 'ws' })) {
    source = source.replaceAll(`__${key}__`, value);
  }
  vm.runInNewContext(source, context);
  return { window, calls };
}

test('fetch emits one Wry API event and preserves its response', async () => {
  const response = { status: 201, headers: new Headers({ 'content-type': 'text/plain' }),
    clone: () => ({ text: async () => 'created' }) };
  const { window, calls } = browser(async () => response);
  assert.equal(await window.fetch(new URL('https://example.com/api'),
    { method: 'POST', body: 'hello' }), response);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].event, 'api-call');
  assert.equal(calls[0].payload.method, 'POST');
  assert.equal(calls[0].payload.requestBody, 'hello');
  assert.equal(calls[0].payload.responseBody, 'created');
  assert.equal(calls[0].payload.status, 201);
});

test('failed fetch is reported without swallowing the original error', async () => {
  const error = new Error('offline');
  const { window, calls } = browser(async () => { throw error; });
  await assert.rejects(window.fetch('/api'), value => value === error);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].payload.status, 0);
  assert.equal(calls[0].payload.url, 'https://example.com/api');
});

test('XHR loadend reports failures and handles non-text responses', () => {
  const { window, calls } = browser(async () => {});
  const xhr = new window.XMLHttpRequest();
  xhr.open('GET', '/api');
  xhr.send();
  Object.defineProperty(xhr, 'responseText', { get() { throw new Error('binary'); } });
  xhr.listeners.loadend();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].payload.status, 0);
  assert.equal(calls[0].payload.responseBody, null);
});
