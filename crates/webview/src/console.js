// Forwards the page's console and its uncaught errors to the host as `console` messages.
// Installed before the inspector, whose own `[webview4j-...]` lines are not forwarded.
(() => {
  if (window.__WEBVIEW4J_CONSOLE__) return;
  Object.defineProperty(window, '__WEBVIEW4J_CONSOLE__', { value: true });
  function post(level, message) {
    try {
      window.ipc.postMessage(JSON.stringify({ event: 'console', payload: { level, message } }));
    } catch {}
  }
  function text(value) {
    if (typeof value === 'string') return value;
    if (value instanceof Error) return value.stack || String(value);
    try {
      const json = JSON.stringify(value);
      return json === undefined ? String(value) : json;
    } catch { return String(value); }
  }
  const levels = { error: 'error', warn: 'warn', info: 'info', log: 'info', debug: 'debug', trace: 'trace' };
  for (const [method, level] of Object.entries(levels)) {
    const original = console[method];
    if (typeof original !== 'function') continue;
    console[method] = function (...args) {
      original.apply(this, args);
      const message = args.map(text).join(' ');
      if (!message.startsWith('[webview4j-')) post(level, message);
    };
  }
  window.addEventListener('error', (event) => post('error', event.error ? text(event.error) : String(event.message)));
  window.addEventListener('unhandledrejection', (event) => post('error', 'unhandled rejection: ' + text(event.reason)));
})();
