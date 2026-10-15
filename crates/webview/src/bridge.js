// The page's bridge to Java, as window.__webview4j, over the webview4j custom protocol.
// @webview4j/api wraps it with types.
//   invoke(command, args) -> Promise   POST <base>invoke/<command>: Java's App.handle answers
//   listen(topic, handler) -> unlisten  receives View.emit, from a held POST <base>events
// Nothing here runs code it is sent: answers and emits are data, read as JSON.
(() => {
  if (window.__webview4j) return;
  const BASE = '__WEBVIEW4J_BASE__';
  const listeners = new Map();
  let polling = false;

  class InvokeError extends Error {
    constructor(command, status, message) {
      super(message);
      this.name = 'InvokeError';
      this.command = command;
      this.status = status;
    }
  }

  async function invoke(command, args) {
    if (typeof command !== 'string' || !command) throw new TypeError('invoke needs a command name');
    const response = await fetch(BASE + 'invoke/' + command, {
      method: 'POST',
      // text/plain keeps this a "simple" request: no CORS preflight per call.
      headers: { 'Content-Type': 'text/plain' },
      body: JSON.stringify(args === undefined ? null : args),
    });
    const text = await response.text();
    if (response.status !== 200) throw new InvokeError(command, response.status, text);
    return text === '' ? null : JSON.parse(text);
  }

  function listen(topic, handler) {
    if (typeof handler !== 'function') throw new TypeError('listen needs a handler');
    let handlers = listeners.get(topic);
    if (!handlers) listeners.set(topic, handlers = new Set());
    const entry = (data) => handler(data);   // its own entry, so the same handler can listen twice
    handlers.add(entry);
    if (!polling) {
      polling = true;
      poll();
    }
    return () => {
      handlers.delete(entry);
      if (handlers.size === 0 && listeners.get(topic) === handlers) listeners.delete(topic);
    };
  }

  // One request held open at a time; each answer is a batch of frames, then the next request.
  async function poll() {
    let wait = 1000;
    while (true) {
      let response;
      try {
        response = await fetch(BASE + 'events', { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: '' });
      } catch (error) {
        // Also what a page whose origin is not allowed sees: the browser hides the 403. Back off.
        await new Promise((resume) => setTimeout(resume, wait));
        wait = Math.min(wait * 2, 30000);
        continue;
      }
      wait = 1000;
      if (response.status === 410) return;   // the window closed
      if (response.status !== 200) continue;
      for (const message of frames(new Uint8Array(await response.arrayBuffer()))) deliver(message);
    }
  }

  // PROTOCOL.md §1: MSG <subject> <header-length> <payload-length>\r\n, headers, payload.
  function* frames(bytes) {
    const decoder = new TextDecoder();
    let at = 0;
    while (at < bytes.length) {
      let end = at;
      while (end + 1 < bytes.length && !(bytes[end] === 13 && bytes[end + 1] === 10)) end++;
      const [verb, subject, headerLength, payloadLength] = decoder.decode(bytes.subarray(at, end)).split(' ');
      if (verb !== 'MSG') return;
      const payloadStart = end + 2 + Number(headerLength);
      const payloadEnd = payloadStart + Number(payloadLength);
      yield { subject, payload: decoder.decode(bytes.subarray(payloadStart, payloadEnd)) };
      at = payloadEnd;
    }
  }

  function deliver({ subject, payload }) {
    const tokens = subject.split('.');   // view.<view>.emit.<topic...>
    if (tokens[0] !== 'view' || tokens[2] !== 'emit') return;
    const topic = tokens.slice(3).join('.');
    let data;
    try {
      data = payload === '' ? null : JSON.parse(payload);
    } catch (error) {
      console.error('[webview4j-bridge] an emit on', topic, 'is not JSON', error);
      return;
    }
    for (const handler of Array.from(listeners.get(topic) || [])) {
      try {
        handler(data);
      } catch (error) {
        console.error('[webview4j-bridge] a listener of', topic, 'failed', error);
      }
    }
  }

  Object.defineProperty(window, '__webview4j', { value: Object.freeze({ invoke, listen }) });
})();
