  // ----- XHR -----
  const OrigXHR = window.XMLHttpRequest;
  function PatchedXHR() {
    const xhr = new OrigXHR();
    let _url = '';
    let _method = 'GET';
    let _requestBody = null;
    let _requestHeaders = {};
    let _cookies = null;
    let _startedAt = 0;
    const origOpen = xhr.open;
    const origSend = xhr.send;
    const origSetRequestHeader = xhr.setRequestHeader;
    xhr.open = function (method, url, ...rest) {
      _url = url;
      _method = (method || 'GET').toUpperCase();
      // setRequestHeader() is only valid between open() and send(); reset
      // here so we don't carry stale headers from a previous use of the xhr.
      _requestHeaders = {};
      return origOpen.call(this, method, url, ...rest);
    };
    xhr.setRequestHeader = function (name, value) {
      try {
        const key = String(name).toLowerCase();
        // XHR appends duplicates with a comma; mirror that so what we
        // emit matches what the server actually sees.
        _requestHeaders[key] = key in _requestHeaders
          ? _requestHeaders[key] + ', ' + String(value)
          : String(value);
      } catch (_) {}
      return origSetRequestHeader.call(this, name, value);
    };
    xhr.send = function (...args) {
      _startedAt = performance.now();
      _requestBody = clipBody(args[0]);
      _cookies = captureCookies();
      return origSend.apply(this, args);
    };
    xhr.addEventListener('loadend', () => {
      try {
        const resolved = resolve(_url);
        if (isApiCall(resolved)) {
          const ms = Math.round(performance.now() - _startedAt);
          let rawBody = null;
          try { rawBody = xhr.responseText; } catch (_) {}
          const requestHeaders = clipHeaders(_requestHeaders);
          const responseHeaders = parseRawHeaders(xhr.getAllResponseHeaders());
          trace(
            _method, resolved, xhr.status, ms,
            _requestBody, clipBody(rawBody),
            requestHeaders, responseHeaders, _cookies,
          );
          if (isDeepCapture(resolved)) {
            emit(CAPTURE_EVENT, {
              url: resolved,
              status: xhr.status,
              body: rawBody,
              requestHeaders,
              responseHeaders,
              cookies: _cookies,
              userAgent: (typeof navigator !== 'undefined' && navigator.userAgent) || null,
            });
          }
        }
      } catch (e) {
        console.error('[__LOG_PREFIX__-intercept] xhr hook error', e);
      }
    });
    return xhr;
  }
  PatchedXHR.prototype = OrigXHR.prototype;
  window.XMLHttpRequest = PatchedXHR;
