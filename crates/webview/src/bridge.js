// The page's bridge to Java, as window.__webview4j. @webview4j/api wraps it with types.
//   invoke(command, args) -> Promise   calls a command Java registered with App.handle
//   listen(topic, handler) -> unlisten  receives what Java sends with View.emit
// resolve and emit are called by the host only.
(() => {
  if (window.__webview4j) return;
  const pending = new Map();
  const listeners = new Map();
  let nextCall = 0;

  function invoke(command, args) {
    if (typeof command !== 'string' || !command) {
      return Promise.reject(new TypeError('invoke needs a command name'));
    }
    const id = ++nextCall;
    return new Promise((resolve, reject) => {
      pending.set(id, { command, resolve, reject });
      try {
        const payload = { id, command, args: args === undefined ? null : args };
        window.ipc.postMessage(JSON.stringify({ event: 'invoke', payload }));
      } catch (error) {
        pending.delete(id);
        reject(error);
      }
    });
  }

  function resolve(id, ok, value) {
    const call = pending.get(id);
    if (!call) return;
    pending.delete(id);
    if (ok) {
      call.resolve(value);
    } else {
      const error = new Error(String(value));
      error.name = 'InvokeError';
      error.command = call.command;
      call.reject(error);
    }
  }

  function listen(topic, handler) {
    if (typeof handler !== 'function') throw new TypeError('listen needs a handler');
    let handlers = listeners.get(topic);
    if (!handlers) listeners.set(topic, handlers = new Set());
    const entry = (data) => handler(data);   // its own entry, so the same handler can listen twice
    handlers.add(entry);
    return () => {
      handlers.delete(entry);
      if (handlers.size === 0 && listeners.get(topic) === handlers) listeners.delete(topic);
    };
  }

  function emit(topic, data) {
    for (const handler of Array.from(listeners.get(topic) || [])) {
      try {
        handler(data);
      } catch (error) {
        console.error('[webview4j-bridge] a listener of', topic, 'failed', error);
      }
    }
  }

  Object.defineProperty(window, '__webview4j', { value: Object.freeze({ invoke, listen, resolve, emit }) });
})();
