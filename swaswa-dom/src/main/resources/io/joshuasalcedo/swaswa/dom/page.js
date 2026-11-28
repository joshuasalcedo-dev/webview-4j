// The page's half of io.joshuasalcedo.swaswa.dom.Dom: calls of the page's functions and the browser's (their answer,
// a promise's too, back to Java), and values Java follows (told when they change).
if (!window.__swaswaPage) {
  window.__swaswaPage = (() => {
    const tell = (data) => window.__webview4j?.invoke('swaswa.dom', data).catch(() => {});
    const answer = (call, run) => {
      Promise.resolve().then(run).then(
        (value) => tell({ call, value: value === undefined ? null : value }),
        (e) => tell({ call, error: String(e?.message ?? e) }));
    };
    const watching = new Map();
    const kinds = {
      media: (id, query) => {
        const mq = matchMedia(query);
        const f = () => tell({ watch: id, value: mq.matches });
        mq.addEventListener('change', f);
        return [mq.matches, () => mq.removeEventListener('change', f)];
      },
      online: (id) => {
        const f = () => tell({ watch: id, value: navigator.onLine });
        addEventListener('online', f); addEventListener('offline', f);
        return [navigator.onLine, () => { removeEventListener('online', f); removeEventListener('offline', f); }];
      },
      visible: (id) => {
        const f = () => tell({ watch: id, value: document.visibilityState === 'visible' });
        document.addEventListener('visibilitychange', f);
        return [document.visibilityState === 'visible', () => document.removeEventListener('visibilitychange', f)];
      },
    };
    return {
      /** A function of the page's, by its name on window (a.b.c: a path), with its arguments. */
      call: (call, name, args) => answer(call, () => {
        let fn = window, self = window;
        for (const part of name.split('.')) { self = fn; fn = fn?.[part]; }
        if (typeof fn !== 'function') throw new Error('the page has no function ' + name);
        return fn.apply(self, args);
      }),
      watch: (id, kind, arg) => {
        watching.get(id)?.[1]();
        const w = kinds[kind](id, arg);
        watching.set(id, w);
        return w[0];
      },
      unwatch: (id) => { watching.get(id)?.[1](); watching.delete(id); },
    };
  })();
}
true
