// The page's bridge to Java, as window.__webview4j. @webview4j/api wraps it with types.
//   invoke(command, args) -> Promise   a call to a command Java registered with App.handle:
//                                       small (UI actions) over the webview's IPC, settled by the
//                                       host's settle(...); large (data) over the custom protocol
//   listen(topic, handler) -> unlisten  receives View.emit, from a held POST <base>events
//   settle(...)                         called by the host only, with values it wrote as JSON
(() => {
  if (window.__webview4j) return;
  const BASE = '__WEBVIEW4J_BASE__';
  const FAST_LIMIT = __WEBVIEW4J_FAST_LIMIT__;
  const COMMAND = /^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$/;
  const encoder = new TextEncoder();
  const listeners = new Map();
  const pending = new Map();
  let nextCall = 0;
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
    if (!COMMAND.test(command)) throw new InvokeError(command, 400, `'${command}' is not a valid command name`);
    const body = JSON.stringify(args === undefined ? null : args);
    // Every character is at least a byte: a long string is data without measuring its bytes.
    if (body.length > FAST_LIMIT || !(window.ipc && typeof window.ipc.postMessage === 'function')) return viaFetch(command, body);
    const size = encoder.encode(body).length;
    return size <= FAST_LIMIT ? viaIpc(command, body, size) : viaFetch(command, body);
  }

  // A UI action: one frame over IPC (PROTOCOL.md §1), answered by the host's settle(...).
  function viaIpc(command, body, size) {
    const id = ++nextCall;
    return new Promise((resolve, reject) => {
      pending.set(id, { command, resolve, reject });
      const block = 'Call: ' + id + '\r\n\r\n';
      try {
        window.ipc.postMessage('MSG invoke.' + command + ' ' + block.length + ' ' + size + '\r\n' + block + body);
      } catch (error) {
        pending.delete(id);
        reject(error);
      }
    });
  }

  // Data: the custom protocol, arguments and answer as bytes.
  async function viaFetch(command, body) {
    const response = await fetch(BASE + 'invoke/' + command, {
      method: 'POST',
      // text/plain keeps this a "simple" request: no CORS preflight per call.
      headers: { 'Content-Type': 'text/plain' },
      body,
    });
    return answer(command, response.status, await response.text());
  }

  function answer(command, status, text) {
    if (status !== 200) throw new InvokeError(command, status, text);
    return text === '' ? null : JSON.parse(text);
  }

  // Called by the host: call `id` answered with `status` and `value`, or, for an answer too large
  // for a script, where to fetch it.
  function settle(id, status, value, fetchPath) {
    const call = pending.get(id);
    if (!call) return;
    pending.delete(id);
    if (fetchPath) {
      fetch(BASE + fetchPath, { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: '' })
        .then(async (response) => answer(call.command, response.status, await response.text()))
        .then(call.resolve, call.reject);
    } else if (status === 200) {
      call.resolve(value);
    } else {
      call.reject(new InvokeError(call.command, status, String(value)));
    }
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

  Object.defineProperty(window, '__webview4j', { value: Object.freeze({ invoke, listen, settle }) });
})();
