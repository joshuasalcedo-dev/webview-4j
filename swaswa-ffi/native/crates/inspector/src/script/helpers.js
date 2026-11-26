  function tag(payload) {
    return SESSION_KEY ? Object.assign({ sessionKey: SESSION_KEY }, payload) : payload;
  }

  function emit(eventName, payload) {
    const tagged = tag(payload);
    if (window.ipc && typeof window.ipc.postMessage === 'function') {
      window.ipc.postMessage(JSON.stringify({ event: eventName, payload: tagged }));
      return Promise.resolve();
    }
    const internals = window.__TAURI_INTERNALS__;
    if (internals && typeof internals.invoke === 'function') {
      return internals
        .invoke('plugin:event|emit', { event: eventName, payload: tagged })
        .catch((err) => console.error('[__LOG_PREFIX__-intercept] emit failed:', err));
    }
    if (window.__TAURI__ && window.__TAURI__.event) {
      return window.__TAURI__.event
        .emit(eventName, tagged)
        .catch((err) => console.error('[__LOG_PREFIX__-intercept] hi emit failed:', err));
    }
    console.error('[__LOG_PREFIX__-intercept] no Tauri IPC bridge; dropping', eventName);
  }

  function resolve(url) {
    if (!url) return null;
    try { return new URL(url, location.href).href; }
    catch { return null; }
  }

  function isApiCall(url) {
    return !!url && HOST_RE.test(url) && API_RE.test(url);
  }
  function isDeepCapture(url) {
    return !!url && HOST_RE.test(url) && ME_RE.test(url);
  }

  const MAX_BODY_CHARS = 2048;
  const MAX_HEADER_VALUE_CHARS = 512;
  const MAX_HEADERS_TOTAL_CHARS = 4096;

  function clipHeaders(obj) {
    if (!obj || typeof obj !== 'object') return null;
    const out = {};
    let total = 0;
    for (const k of Object.keys(obj)) {
      let v = obj[k];
      if (v == null) continue;
      v = String(v);
      if (v.length > MAX_HEADER_VALUE_CHARS) v = v.slice(0, MAX_HEADER_VALUE_CHARS) + '…';
      out[k] = v;
      total += k.length + v.length + 4;
      if (total > MAX_HEADERS_TOTAL_CHARS) {
        out['__truncated__'] = '1';
        break;
      }
    }
    return Object.keys(out).length ? out : null;
  }

  // Headers may be a `Headers` instance, a plain `{name: value}` object, or
  // an iterable of `[name, value]` pairs (per fetch spec). Normalize to a
  // lowercase-keyed object so downstream consumers don't care.
  function headersToObject(h) {
    if (!h) return null;
    const out = {};
    try {
      if (typeof Headers !== 'undefined' && h instanceof Headers) {
        h.forEach((v, k) => { out[String(k).toLowerCase()] = String(v); });
      } else if (Array.isArray(h) || typeof h[Symbol.iterator] === 'function') {
        for (const pair of h) {
          if (pair && pair.length >= 2) {
            out[String(pair[0]).toLowerCase()] = String(pair[1]);
          }
        }
      } else if (typeof h === 'object') {
        for (const k of Object.keys(h)) {
          if (h[k] != null) out[String(k).toLowerCase()] = String(h[k]);
        }
      } else {
        return null;
      }
    } catch { return null; }
    return clipHeaders(out);
  }

  // `XMLHttpRequest.getAllResponseHeaders()` returns a CRLF-separated
  // `name: value` block. Parse it back into a lowercase-keyed object so
  // the wire shape matches the fetch path.
  function parseRawHeaders(raw) {
    if (!raw || typeof raw !== 'string') return null;
    const out = {};
    raw.split(/\r?\n/).forEach((line) => {
      const idx = line.indexOf(':');
      if (idx <= 0) return;
      const k = line.slice(0, idx).trim().toLowerCase();
      const v = line.slice(idx + 1).trim();
      if (k) out[k] = v;
    });
    return clipHeaders(out);
  }

  // Snapshot of `document.cookie` (a `"; "`-separated `name=value` string).
  // Spec restricts this to non-`HttpOnly` cookies for the current origin —
  // i.e. exactly the set the page itself can see and attribute requests to.
  // Cookies the browser appends silently (HttpOnly, third-party) won't show.
  function captureCookies() {
    try {
      const raw = (typeof document !== 'undefined' && document.cookie) || '';
      if (!raw) return null;
      const out = {};
      raw.split(/;\s*/).forEach((pair) => {
        if (!pair) return;
        const idx = pair.indexOf('=');
        if (idx <= 0) return;
        const k = pair.slice(0, idx).trim();
        const v = pair.slice(idx + 1).trim();
        if (k) out[k] = v;
      });
      return clipHeaders(out);
    } catch { return null; }
  }

  function clipBody(v) {
    if (v == null) return null;
    let s;
    if (typeof v === 'string') s = v;
    else if (v instanceof FormData || v instanceof Blob || v instanceof ArrayBuffer) {
      return '<binary>';
    } else {
      try { s = JSON.stringify(v); } catch { return String(v); }
    }
    if (s.length > MAX_BODY_CHARS) {
      return s.slice(0, MAX_BODY_CHARS) + '…';
    }
    return s;
  }

  function trace(method, url, status, durationMs, requestBody, responseBody, requestHeaders, responseHeaders, cookies) {
    console.log('[__LOG_PREFIX__-trace]', method, status, url, durationMs + 'ms');
    emit(TRACE_EVENT, {
      method,
      url,
      status,
      durationMs,
      requestBody: requestBody ?? null,
      responseBody: responseBody ?? null,
      requestHeaders: requestHeaders ?? null,
      responseHeaders: responseHeaders ?? null,
      cookies: cookies ?? null,
    });
  }
