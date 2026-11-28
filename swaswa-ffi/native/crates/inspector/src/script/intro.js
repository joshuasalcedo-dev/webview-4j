(() => {
  // Idempotency guard: the same script may be injected twice (once as a
  // window initialization script, once via WebviewWindow::eval). Double
  // installation would wrap our own wrappers and emit each event twice.
  if (window.__WEB_INSPECTOR_INSTALLED__) return;
  window.__WEB_INSPECTOR_INSTALLED__ = true;

  const ME_RE = /__CAPTURE_PATTERN__/;
  const API_RE = /__API_PATTERN__/;
  const HOST_RE = /__HOST_PATTERN__/;
  const WS_RE = /__WS_PATTERN__/;
  const CAPTURE_EVENT = '__CAPTURE_EVENT__';
  const TRACE_EVENT = '__TRACE_EVENT__';
  const CLICK_EVENT = '__CLICK_EVENT__';
  const WS_EVENT = '__WS_EVENT__';

  // Set by the host before this script runs (see window.rs).
  // Lets the Rust listener attribute every emit back to its source.
  const SESSION_KEY = (typeof window.__INSPECTOR_SESSION_KEY__ === 'string')
    ? window.__INSPECTOR_SESSION_KEY__
    : null;

  console.log('[__LOG_PREFIX__-intercept] script entered. tauri:', {
    hasTauri: typeof window.__TAURI__ !== 'undefined',
    hasInternals: typeof window.__TAURI_INTERNALS__ !== 'undefined',
    sessionKey: SESSION_KEY,
  });
