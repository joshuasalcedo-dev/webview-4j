  // ----- WebSocket capture -----
  // Patch `window.WebSocket` so every text frame on a matching URL is
  // forwarded to Rust. We don't touch outbound (`send`) or binary frames —
  // the only consumer right now is the notification feature, which only
  // cares about server→client JSON.
  const OrigWS = window.WebSocket;
  function PatchedWS(url, protocols) {
    const ws = protocols !== undefined ? new OrigWS(url, protocols) : new OrigWS(url);
    try {
      const targetUrl = String(url || '');
      if (WS_RE.test(targetUrl)) {
        console.log('[__LOG_PREFIX__-ws] hooked', targetUrl);
        ws.addEventListener('message', (ev) => {
          try {
            const data = ev && ev.data;
            if (typeof data !== 'string' || !data) return;
            emit(WS_EVENT, { url: targetUrl, frame: data });
          } catch (err) {
            console.error('[__LOG_PREFIX__-ws] message handler error', err);
          }
        });
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
