// The page's network, for Java (Capture.NETWORK, the dev window's Network tab): every fetch and XMLHttpRequest
// as it starts and as it ends or fails, with its headers, bodies (clipped), status, size and time; and the page's
// other requests (pictures, scripts, styles, fonts...) from the browser's own resource timing. Reports go over
// the webview's IPC as {"event":"network","payload":{...}}; the native side drops them unless the view captures
// the network.
(() => {
  if (window.__webview4jNetwork) return
  window.__webview4jNetwork = true
  const post = (payload) => {
    try {
      window.ipc && window.ipc.postMessage(JSON.stringify({ event: "network", payload }))
    } catch (_) {}
  }
  const MAX_BODY = 32 * 1024
  let next = 0
  const id = () => ++next

  const clip = (text) => (text == null ? null : text.length > MAX_BODY ? text.slice(0, MAX_BODY) + "…" : text)
  const textual = (type) => !type || /^(text\/|application\/(json|xml|javascript|x-www-form-urlencoded|graphql)|[^;]*\+(json|xml))/i.test(type)
  const headers = (h) => {
    const out = {}
    try {
      if (!h) return out
      if (h instanceof Headers) h.forEach((v, k) => (out[k] = v))
      else if (Array.isArray(h)) h.forEach(([k, v]) => (out[String(k).toLowerCase()] = String(v)))
      else Object.keys(h).forEach((k) => (out[k.toLowerCase()] = String(h[k])))
    } catch (_) {}
    return out
  }
  const bodyText = (body) => {
    if (body == null) return null
    if (typeof body === "string") return clip(body)
    if (body instanceof URLSearchParams) return clip(body.toString())
    if (body instanceof FormData) {
      const parts = []
      body.forEach((v, k) => parts.push(`${k}=${typeof v === "string" ? v : `<file ${v.name}, ${v.size} bytes>`}`))
      return clip(parts.join("&"))
    }
    if (body instanceof Blob) return `<binary, ${body.size} bytes>`
    if (body instanceof ArrayBuffer || ArrayBuffer.isView(body)) return `<binary, ${body.byteLength} bytes>`
    try {
      return clip(JSON.stringify(body))
    } catch (_) {
      return clip(String(body))
    }
  }
  const absolute = (url) => {
    try {
      return new URL(String(url), location.href).href
    } catch (_) {
      return String(url)
    }
  }

  // --- fetch ---
  const realFetch = window.fetch
  window.fetch = function (input, init) {
    const n = id()
    const request = input instanceof Request ? input : null
    const method = String((init && init.method) || (request && request.method) || "GET").toUpperCase()
    const url = absolute(request ? request.url : input)
    const t0 = performance.now()
    post({
      phase: "start", id: n, kind: "fetch", method, url, startedAt: Date.now(),
      requestHeaders: headers((init && init.headers) || (request && request.headers)),
      requestBody: bodyText(init && init.body),
    })
    return realFetch.apply(this, arguments).then(
      async (response) => {
        const durationMs = Math.round(performance.now() - t0)
        const type = response.headers.get("content-type") || ""
        let body = null
        let size = Number(response.headers.get("content-length")) || 0
        try {
          if (textual(type)) {
            const text = await response.clone().text()
            size = size || new Blob([text]).size
            body = clip(text)
          } else {
            const bytes = await response.clone().arrayBuffer()
            size = bytes.byteLength
            body = `<binary, ${size} bytes>`
          }
        } catch (_) {}
        post({
          phase: "end", id: n, status: response.status, statusText: response.statusText, durationMs,
          mimeType: type.split(";")[0] || null, size, responseHeaders: headers(response.headers), responseBody: body,
          redirected: response.redirected, finalUrl: response.url || url,
        })
        return response
      },
      (error) => {
        post({ phase: "error", id: n, durationMs: Math.round(performance.now() - t0), error: (error && (error.name === "AbortError" ? "aborted" : error.message)) || String(error) })
        throw error
      }
    )
  }

  // --- XMLHttpRequest ---
  const XHR = XMLHttpRequest.prototype
  const open = XHR.open
  const send = XHR.send
  const setHeader = XHR.setRequestHeader
  XHR.open = function (method, url) {
    this.__wv4j = { method: String(method || "GET").toUpperCase(), url: absolute(url), headers: {} }
    return open.apply(this, arguments)
  }
  XHR.setRequestHeader = function (name, value) {
    if (this.__wv4j) this.__wv4j.headers[String(name).toLowerCase()] = String(value)
    return setHeader.apply(this, arguments)
  }
  XHR.send = function (body) {
    const r = this.__wv4j
    if (r) {
      const n = id()
      const t0 = performance.now()
      post({ phase: "start", id: n, kind: "xhr", method: r.method, url: r.url, startedAt: Date.now(), requestHeaders: r.headers, requestBody: bodyText(body) })
      let done = false
      const finish = (failure) => {
        if (done) return
        done = true
        const durationMs = Math.round(performance.now() - t0)
        if (failure) {
          post({ phase: "error", id: n, durationMs, error: failure })
          return
        }
        const type = this.getResponseHeader("content-type") || ""
        let text = null
        let size = 0
        try {
          if (this.responseType === "" || this.responseType === "text") {
            text = this.responseText
            size = new Blob([text]).size
          } else if (this.response && this.response.byteLength != null) {
            size = this.response.byteLength
            text = `<binary, ${size} bytes>`
          } else if (this.response instanceof Blob) {
            size = this.response.size
            text = `<binary, ${size} bytes>`
          } else if (this.response != null) {
            text = JSON.stringify(this.response)
            size = text.length
          }
        } catch (_) {}
        const responseHeaders = {}
        ;(this.getAllResponseHeaders() || "").trim().split(/[\r\n]+/).forEach((line) => {
          const i = line.indexOf(":")
          if (i > 0) responseHeaders[line.slice(0, i).trim().toLowerCase()] = line.slice(i + 1).trim()
        })
        post({
          phase: "end", id: n, status: this.status, statusText: this.statusText, durationMs, mimeType: type.split(";")[0] || null,
          size, responseHeaders, responseBody: textual(type) ? clip(text) : text, finalUrl: this.responseURL || r.url,
        })
      }
      this.addEventListener("load", () => finish(null))
      this.addEventListener("error", () => finish("network error"))
      this.addEventListener("abort", () => finish("aborted"))
      this.addEventListener("timeout", () => finish("timed out"))
    }
    return send.apply(this, arguments)
  }

  // --- everything else the page loads: pictures, scripts, styles, fonts, documents ---
  try {
    new PerformanceObserver((list) => {
      for (const e of list.getEntries()) {
        // fetch and XHR are reported above, with their bodies
        if (e.initiatorType === "fetch" || e.initiatorType === "xmlhttprequest") continue
        post({
          phase: "resource", id: id(), kind: e.initiatorType || "other", method: "GET", url: e.name,
          startedAt: Math.round(performance.timeOrigin + e.startTime), durationMs: Math.round(e.duration),
          status: e.responseStatus || 0, size: e.transferSize || e.encodedBodySize || 0,
        })
      }
    }).observe({ type: "resource", buffered: true })
  } catch (_) {}
})()
