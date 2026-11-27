// The page's bridge to Java, as window.__webview4j. @webview4j/api wraps it with types.
//   invoke(command, args) -> Promise   a call to a command Java registered with App.handle:
//                                       small (UI actions) over the webview's IPC, settled by the
//                                       host's settle(...); large (data) over the custom protocol
//   request(method, path, query, body)  a route on a Java @WebViewController: data, over the custom
//                                       protocol (<base>api/<path>); the generated client calls it
//   listen(topic, handler) -> unlisten  receives View.emit: small ones by deliver(...), large ones
//                                       from a held POST <base>events, in the order Java sent them
//   settle(...), deliver(...)           called by the host only, with values it wrote as JSON:
//                                       the answer to a small call, and a small emit
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

  // A route on a Java controller (@WebViewController): data, over the custom protocol.
  const METHODS = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE']);

  class RequestError extends Error {
    constructor(method, path, status, message) {
      super(message);
      this.name = 'RequestError';
      this.method = method;
      this.path = path;
      this.status = status;
    }
  }

  async function request(method, path, query, body) {
    if (!METHODS.has(method)) throw new TypeError('not a route method: ' + method);
    if (typeof path !== 'string' || !path.startsWith('/')) throw new TypeError('a route path starts with /');
    const search = [];
    for (const [name, value] of Object.entries(query || {})) {
      if (value === undefined || value === null) continue;
      for (const item of Array.isArray(value) ? value : [value]) {
        search.push(encodeURIComponent(name) + '=' + encodeURIComponent(String(item)));
      }
    }
    const init = { method };
    if (body !== undefined && method !== 'GET') {
      init.body = JSON.stringify(body);
      init.headers = { 'Content-Type': 'text/plain' };
    }
    const response = await fetch(BASE + 'api' + path + (search.length ? '?' + search.join('&') : ''), init);
    const text = await response.text();
    if (response.status >= 400) throw new RequestError(method, path, response.status, text);
    return response.status === 204 || text === '' ? null : JSON.parse(text);
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
      for (const message of frames(new Uint8Array(await response.arrayBuffer()))) fromPoll(message);
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
      const blockStart = end + 2;
      const payloadStart = blockStart + Number(headerLength);
      const payloadEnd = payloadStart + Number(payloadLength);
      const headers = {};
      for (const line of decoder.decode(bytes.subarray(blockStart, payloadStart)).split('\r\n')) {
        const colon = line.indexOf(':');
        if (colon > 0) headers[line.slice(0, colon).toLowerCase()] = line.slice(colon + 1).trim();
      }
      yield { subject, headers, payload: decoder.decode(bytes.subarray(payloadStart, payloadEnd)) };
      at = payloadEnd;
    }
  }

  // A large emit, from the poll.
  function fromPoll({ subject, headers, payload }) {
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
    arrive(Number(headers.seq), topic, data);
  }

  // Called by the host with a small emit: its number, topic and data, written by the host as JSON.
  function deliver(seq, topic, data) {
    arrive(seq, topic, data);
  }

  // Emits are numbered by the host across both channels; hand them to listeners in that order.
  // A number that never comes (dropped while the page reloaded) is skipped after GAP_MS.
  const GAP_MS = 100;
  const early = new Map();
  let expected = 0;
  let gapTimer = null;

  function arrive(seq, topic, data) {
    if (!Number.isInteger(seq) || seq < 1) return dispatch(topic, data);
    if (expected === 0 || seq < expected) expected = seq;   // the first one seen, or the host started over
    early.set(seq, [topic, data]);
    drain();
  }

  function drain() {
    while (early.has(expected)) {
      const [topic, data] = early.get(expected);
      early.delete(expected);
      expected++;
      dispatch(topic, data);
    }
    if (early.size > 0 && gapTimer === null) {
      gapTimer = setTimeout(() => {
        gapTimer = null;
        if (early.size > 0) {
          expected = Math.min(...early.keys());
          drain();
        }
      }, GAP_MS);
    }
  }

  function dispatch(topic, data) {
    for (const handler of Array.from(listeners.get(topic) || [])) {
      try {
        handler(data);
      } catch (error) {
        console.error('[webview4j-bridge] a listener of', topic, 'failed', error);
      }
    }
  }

  // The page's own window, for a window without the system's frame (a title bar of its own): only a page
  // the window's bridge allows is heard. Dragging an empty part of a drag region moves the window; a
  // double click on it maximizes or restores it, as on a system title bar.
  function windowAction(action) {
    if (window.ipc && typeof window.ipc.postMessage === 'function') window.ipc.postMessage('WIN ' + action);
  }
  // a floating embedded view (an MDI child) comes above the others in its window when clicked, as a window would
  addEventListener('pointerdown', () => { if (window.__webview4j_floating) windowAction('front'); }, true);
  const currentWindow = Object.freeze({
    minimize: () => windowAction('minimize'),
    toggleMaximize: () => windowAction('toggle-maximize'),
    close: () => windowAction('close'),
    startDrag: () => windowAction('drag'),
  });
  const DRAG_REGION = '[data-webview4j-drag-region], [data-tauri-drag-region]';
  const NOT_DRAGGED = 'button, a, input, select, textarea, label, [role="button"], [role="tab"], [contenteditable=""], [contenteditable="true"], [data-no-drag]';
  // A window without the system's frame (window.__webview4j_frameless, set natively) has no edges of its own to
  // grab: the page's outer few pixels show the resize arrows and resize it when dragged, as a frame would.
  const EDGE = 5;
  const EDGE_CURSORS = { n: 'ns-resize', s: 'ns-resize', e: 'ew-resize', w: 'ew-resize',
    ne: 'nesw-resize', sw: 'nesw-resize', nw: 'nwse-resize', se: 'nwse-resize' };
  let edgeStyle = null;
  let shownEdge = '';
  function edgeAt(x, y) {
    // a window without the system's frame, or a floating embedded view (an MDI child): its edges size it
    if (!window.__webview4j_frameless && !window.__webview4j_floating) return '';
    const vertical = y < EDGE ? 'n' : y >= window.innerHeight - EDGE ? 's' : '';
    const horizontal = x < EDGE ? 'w' : x >= window.innerWidth - EDGE ? 'e' : '';
    return vertical + horizontal;
  }
  function showEdge(edge) {
    if (edge === shownEdge) return;
    shownEdge = edge;
    if (!edgeStyle) {
      edgeStyle = document.createElement('style');
      (document.head || document.documentElement).appendChild(edgeStyle);
    }
    edgeStyle.textContent = edge ? '* { cursor: ' + EDGE_CURSORS[edge] + ' !important; }' : '';
  }

  // What can be dragged shows it: a drag region's cursor is a hand (closed while held), unless the page says another
  // (--swaswa-drag-cursor, --swaswa-dragging-cursor). And the window's state is on the page's <html> element, for its
  // own styles: data-swaswa-active, -floating, -dragging, -dock-target (set by the host: state(...)).
  function dragCursors() {
    if (document.getElementById('swaswa-drag-cursors')) return;
    const style = document.createElement('style');
    style.id = 'swaswa-drag-cursors';
    const regions = DRAG_REGION.split(',').map((s) => s.trim());
    const held = regions.flatMap((r) => ['html[data-swaswa-dragging] ' + r, 'html[data-swaswa-holding] ' + r]).join(', ');
    style.textContent = regions.join(', ') + ' { cursor: var(--swaswa-drag-cursor, grab); } '
      + held + ' { cursor: var(--swaswa-dragging-cursor, grabbing); }';
    (document.head || document.documentElement).appendChild(style);
  }
  function state(name, value) {
    const root = document.documentElement;
    if (!root) return;
    const attribute = 'data-swaswa-' + name;
    if (value === false || value === null || value === undefined) root.removeAttribute(attribute);
    else root.setAttribute(attribute, value === true ? '' : String(value));
  }

  if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', dragCursors);
    else dragCursors();
    // held on a drag region: the closed hand at once, before the system's move starts
    document.addEventListener('mousedown', (e) => { if (e.button === 0 && e.target.closest && e.target.closest(DRAG_REGION)) state('holding', true); }, true);
    addEventListener('mouseup', () => state('holding', false), true);
    // the system's move of the window takes the button's release: the next move with no button held says it is up
    document.addEventListener('mousemove', (e) => { if (e.buttons === 0 && document.documentElement.hasAttribute('data-swaswa-holding')) state('holding', false); }, true);
    document.addEventListener('mousemove', (event) => showEdge(edgeAt(event.clientX, event.clientY)), true);
    document.addEventListener('mouseleave', () => showEdge(''), true);
    document.addEventListener('mousedown', (event) => {
      const edge = event.button === 0 ? edgeAt(event.clientX, event.clientY) : '';
      if (!edge) return;
      // The edge belongs to the window, not to whatever the page drew there.
      event.preventDefault();
      event.stopImmediatePropagation();
      windowAction('resize-' + edge);
    }, true);
    document.addEventListener('mousedown', (event) => {
      if (event.button !== 0 || !(event.target instanceof Element)) return;
      const region = event.target.closest(DRAG_REGION);
      if (!region || event.target.closest(NOT_DRAGGED)) return;
      if (event.detail === 2) currentWindow.toggleMaximize();
      else currentWindow.startDrag();
    });
  }

  // Pictures Java draws on the page's canvases (View.drawFrame): told a canvas has a new one, the page fetches it
  // (binary, over the custom protocol) and draws it, then says so. Only the latest: a slow page skips to it.
  const FRAME_FORMATS = ['rgba', 'image/png', 'image/jpeg'];
  const canvases = new Map();
  function drawn(seq, canvas, error) {
    if (window.ipc && typeof window.ipc.postMessage === 'function') {
      window.ipc.postMessage('FRM ' + seq + ' ' + canvas + (error ? ' ' + error : ''));
    }
  }
  function frameReady(canvas, seq) {
    let state = canvases.get(canvas);
    if (!state) canvases.set(canvas, (state = { busy: false, wanted: 0 }));
    state.wanted = Math.max(state.wanted, seq);
    if (!state.busy) drawFrames(canvas, state);
  }
  // A shown page puts a picture on its canvas at the display's next refresh, and only then says it is drawn: a Java
  // loop that waits for each runs at the display's rate, in step with it. A hidden page gets no refreshes: at once.
  function nextRefresh() {
    return document.visibilityState === 'visible' ? new Promise((done) => requestAnimationFrame(done)) : Promise.resolve();
  }
  async function drawFrames(canvas, state) {
    state.busy = true;
    let seq = state.wanted;
    try {
      while (true) {
        const response = await fetch(BASE + 'frame/' + canvas, { cache: 'no-store' });
        if (!response.ok) throw new Error('no picture (' + response.status + ')');
        const buffer = await response.arrayBuffer();
        const head = new DataView(buffer, 0, 20);
        seq = Number(head.getBigUint64(0, true));
        const width = head.getUint32(8, true), height = head.getUint32(12, true), format = FRAME_FORMATS[head.getUint32(16, true)];
        const element = document.getElementById(canvas);
        if (!(element instanceof HTMLCanvasElement)) {
          drawn(seq, canvas, 'the page has no <canvas id="' + canvas + '">');
        } else {
          const context = element.getContext('2d');
          if (!context) throw new Error('the canvas has another context than 2d');
          if (format === 'rgba') {
            const pixels = new ImageData(new Uint8ClampedArray(buffer, 20, width * height * 4), width, height);
            await nextRefresh();
            if (element.width !== width || element.height !== height) { element.width = width; element.height = height; }
            context.putImageData(pixels, 0, 0);
          } else {
            const bitmap = await createImageBitmap(new Blob([new Uint8Array(buffer, 20)], { type: format }));
            await nextRefresh();
            if (element.width !== bitmap.width || element.height !== bitmap.height) { element.width = bitmap.width; element.height = bitmap.height; }
            context.drawImage(bitmap, 0, 0);
            bitmap.close();
          }
          drawn(seq, canvas);
        }
        if (state.wanted <= seq) break;
      }
    } catch (error) {
      console.error('[webview4j-bridge] could not draw on', canvas, error);
      drawn(Math.max(seq, state.wanted), canvas, String(error && error.message || error));
    } finally {
      state.busy = false;
    }
  }

  Object.defineProperty(window, '__webview4j', { value: Object.freeze({ invoke, request, listen, settle, deliver, frameReady, state, window: currentWindow }) });
})();
