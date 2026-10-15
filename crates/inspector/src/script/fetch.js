  // ----- fetch -----
  const origFetch = window.fetch;
  window.fetch = async function (...args) {
    const startedAt = performance.now();
    const firstArg = args[0];
    const url0 = typeof firstArg === 'string' || firstArg instanceof URL
      ? String(firstArg) : firstArg && firstArg.url;
    const init = args[1] || (typeof firstArg === 'object' ? firstArg : null);
    const method = ((init && init.method) || (firstArg && firstArg.method) || 'GET').toUpperCase();
    const requestBody = clipBody(init && init.body);
    // Headers may live on the Request (first arg) or on the init (second
    // arg). `init` wins when both are supplied — matches fetch's own
    // precedence rules.
    const requestHeaders = headersToObject(
      (init && init.headers) || (firstArg && firstArg.headers) || null
    );
    // Snapshot cookies BEFORE the request so any Set-Cookie the response
    // installs doesn't pollute the request-side capture.
    const cookies = captureCookies();
    let response;
    try {
      response = await origFetch.apply(this, args);
    } catch (error) {
      const resolved = resolve(url0);
      if (isApiCall(resolved)) {
        trace(method, resolved, 0, Math.round(performance.now() - startedAt),
          requestBody, null, requestHeaders, null, cookies);
      }
      throw error;
    }
    try {
      const resolved = resolve(url0);
      if (isApiCall(resolved)) {
        const ms = Math.round(performance.now() - startedAt);
        let rawBody = null;
        try { rawBody = await response.clone().text(); } catch (_) {}
        const responseHeaders = headersToObject(response.headers);
        trace(
          method, resolved, response.status, ms,
          requestBody, clipBody(rawBody),
          requestHeaders, responseHeaders, cookies,
        );
        if (isDeepCapture(resolved) && rawBody != null) {
          emit(CAPTURE_EVENT, {
            url: resolved,
            status: response.status,
            body: rawBody,
            requestHeaders,
            responseHeaders,
            cookies,
            // `User-Agent` is a "forbidden header name" in fetch — JS can't
            // read it from init.headers / Request.headers. Snapshot it from
            // navigator instead so consumers don't have to guess.
            userAgent: (typeof navigator !== 'undefined' && navigator.userAgent) || null,
          });
        }
      }
    } catch (e) {
      console.error('[__LOG_PREFIX__-intercept] fetch hook error', e);
    }
    return response;
  };
