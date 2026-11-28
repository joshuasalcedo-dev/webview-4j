  // ----- WebSocket capture -----
  // Patch `window.WebSocket` so every socket on a matching URL is numbered, kept in
  // `window.__WEB_INSPECTOR_SOCKETS__` (so the host can send on it by number), and reported:
  // `open`, each text frame `in` and `out`, and `close`. Binary frames are not forwarded.
  const OrigWS = window.WebSocket;
  const sockets = new Map();
  let nextSocket = 0;
  Object.defineProperty(window, '__WEB_INSPECTOR_SOCKETS__', { value: sockets });
  function PatchedWS(url, protocols) {
    const ws = protocols !== undefined ? new OrigWS(url, protocols) : new OrigWS(url);
    try {
      const targetUrl = String(url || '');
      if (WS_RE.test(targetUrl)) {
        const socket = ++nextSocket;
        sockets.set(socket, ws);
        const report = (direction, frame) => emit(WS_EVENT, { socket, url: targetUrl, direction, frame });
        console.log('[__LOG_PREFIX__-ws] hooked', socket, targetUrl);
        ws.addEventListener('open', () => report('open', null));
        ws.addEventListener('message', (ev) => {
          try {
            const data = ev && ev.data;
            if (typeof data !== 'string' || !data) return;
            report('in', data);
          } catch (err) {
            console.error('[__LOG_PREFIX__-ws] message handler error', err);
          }
        });
        ws.addEventListener('close', () => {
          sockets.delete(socket);
          report('close', null);
        });
        const send = ws.send;
        ws.send = function (data) {
          const result = send.call(this, data);
          if (typeof data === 'string') report('out', data);
          return result;
        };
      }
    } catch (err) {
      console.error('[__LOG_PREFIX__-ws] hook error', err);
    }
    return ws;
  }
  // Preserve the constructor surface — readyState constants and prototype
  // chain — so app code that touches them keeps working.
  PatchedWS.prototype = OrigWS.prototype;
  PatchedWS.CONNECTING = OrigWS.CONNECTING;
  PatchedWS.OPEN = OrigWS.OPEN;
  PatchedWS.CLOSING = OrigWS.CLOSING;
  PatchedWS.CLOSED = OrigWS.CLOSED;
  window.WebSocket = PatchedWS;
