// The page's half of io.joshuasalcedo.swaswa.html.Document: builds and changes the elements Java made, and sends
// their events back. Java numbers each node; 0 is the body.
if (!window.__swaswaDom) {
  window.__swaswaDom = (() => {
    const nodes = new Map();
    let seq = 0;   // the events' order: Java delivers them in it
    let epoch = 0;   // which drawing of the page from Java this is: a batch made for an earlier one is dropped
    const sheets = new Map();
    const get = (key) => (key === 0 ? document.body : nodes.get(key));
    // where an element's children go: an element of a kind keeps them in its slot (its script places the slot)
    const inside = (el) => el?.__swaswaSlot ?? el;

    // kinds: elements a script of their own runs (swaswa-react's "react"), registered by that script
    const kinds = {};
    const waitingKind = new Map();   // kind -> elements made before its script came
    const scripts = new Set();
    const mountKind = (el) => {
      const k = kinds[el.__swaswaKind];
      if (!k || el.__swaswaMounted) return;
      el.__swaswaMounted = true;
      const key = el.__swaswaKey;
      k.mount(el, el.__swaswaData, el.__swaswaSlot, (name, payload) => post({ key, type: 'kind', name, payload }));
    };
    const kind = (name, impl) => {
      kinds[name] = impl;
      for (const el of waitingKind.get(name) || []) mountKind(el);
      waitingKind.delete(name);
    };
    const unmountKind = (el) => {
      if (el.__swaswaMounted) kinds[el.__swaswaKind]?.unmount?.(el);
      el.__swaswaMounted = false;
    };

    const prop = (el, name, value) => {
      if (name === 'checked' || name === 'open') el[name] = value != null && value !== 'false';
      else el[name] = value ?? '';
    };

    const send = (el, key, type, e) => {
      // listened to from Java: the app does it (a form does not leave, a menu of the app's own shows)
      if (type === 'submit' || type === 'dragover' || type === 'drop' || type === 'contextmenu') e.preventDefault();
      const data = { key, type };
      if ('value' in el && el.tagName !== 'BUTTON' && el.tagName !== 'LI') data.value = el.value;
      if (el.type === 'checkbox' || el.type === 'radio') data.checked = el.checked;
      if (el.tagName === 'DETAILS' || el.tagName === 'DIALOG') data.open = el.open;
      if (e.key !== undefined) Object.assign(data, { pressed: e.key, code: e.code });
      if (e.clientX !== undefined) Object.assign(data, { x: e.clientX, y: e.clientY, button: e.button, ox: e.offsetX, oy: e.offsetY });
      if (e.pressure !== undefined) Object.assign(data, { pressure: e.pressure, pointer: e.pointerType });
      if (e.ctrlKey !== undefined) Object.assign(data, { ctrl: e.ctrlKey, shift: e.shiftKey, alt: e.altKey });
      if (type === 'submit') data.fields = Object.fromEntries(new FormData(el));
      if (e.detail !== undefined && typeof e.detail === 'number') data.detail = e.detail;
      if (type === 'drop') {
        const dragged = Number(e.dataTransfer?.getData('application/x-swaswa'));
        if (dragged) data.dragged = dragged;
      }
      post(data);
    };

    const post = (data) => {
      data.seq = ++seq;
      window.__webview4j?.invoke('swaswa.html', data).catch(() => {});
    };

    // elements whose place Java follows (a native panel over them): their box in the page, told when it changes
    const tracked = new Map();
    let measuring = false;
    const measure = () => {
      measuring = false;
      for (const [el, t] of tracked) {
        if (!el.isConnected) { tracked.delete(el); continue; }
        const r = el.getBoundingClientRect();
        const shown = r.width > 0 && r.height > 0 && el.checkVisibility?.({ visibilityProperty: true }) !== false;
        const box = shown ? [r.left, r.top, r.width, r.height].map(Math.round) : [0, 0, 0, 0];
        const now = box.join();
        if (now === t.last) continue;
        t.last = now;
        post({ key: t.key, type: 'bounds', bx: box[0], by: box[1], bw: box[2], bh: box[3] });
      }
    };
    const soon = () => { if (!measuring && tracked.size) { measuring = true; requestAnimationFrame(measure); } };
    const resized = new ResizeObserver(soon);
    addEventListener('scroll', soon, true);
    addEventListener('resize', soon);
    new MutationObserver(soon).observe(document.documentElement, { childList: true, subtree: true, attributes: true });
    const track = (el, key) => { tracked.set(el, { key, last: null }); resized.observe(el); soon(); };

    const listen = (el, key, type) => {
      el.__swaswaOn ??= {};
      if (el.__swaswaOn[type]) return;
      const f = (e) => send(el, key, type, e);
      el.__swaswaOn[type] = f;
      el.addEventListener(type, f);
    };

    const unlisten = (el, type) => {
      const f = el?.__swaswaOn?.[type];
      if (f) {
        el.removeEventListener(type, f);
        delete el.__swaswaOn[type];
      }
    };

    const build = (t) => {
      if (t.tag === undefined) {
        const n = document.createTextNode(t.text);
        n.__swaswaKey = t.key;
        nodes.set(t.key, n);
        return n;
      }
      const el = document.createElement(t.tag);
      el.__swaswaKey = t.key;
      nodes.set(t.key, el);
      for (const [k, v] of Object.entries(t.attrs || {})) el.setAttribute(k, v);
      for (const type of t.events || []) listen(el, t.key, type);
      if (t.kind) {
        el.__swaswaKind = t.kind;
        el.__swaswaData = t.data;
        el.__swaswaSlot = document.createElement('swaswa-slot');
        el.__swaswaSlot.style.display = 'contents';
        el.__swaswaSlot.__swaswaOwner = el;   // whose children these are, wherever its script places them
      }
      for (const c of t.children || []) inside(el).append(build(c));
      for (const [k, v] of Object.entries(t.props || {})) prop(el, k, v);
      if (t.track) track(el, t.key);
      if (t.kind) {
        if (kinds[t.kind]) mountKind(el);
        else waitingKind.set(t.kind, [...(waitingKind.get(t.kind) || []), el]);
      }
      return el;
    };

    const forget = (n) => {
      if (!n) return;
      if (n.__swaswaKey !== undefined) nodes.delete(n.__swaswaKey);
      if (n.__swaswaKind) {
        unmountKind(n);
        for (const c of n.__swaswaSlot.childNodes) forget(c);
      }
      for (const c of n.childNodes || []) forget(c);
    };

    const apply = (ops, from) => {
      if (from !== undefined && from < epoch) return true;   // made before the page was drawn anew: already in it
      for (const o of ops) {
        switch (o[0]) {
          case 'seq': seq = o[1]; break;
          case 'reset':
            if (o[1] !== undefined) epoch = o[1];
            for (const n of nodes.values()) if (n.__swaswaKind) unmountKind(n);
            for (const n of nodes.values()) if (n.parentNode === document.body) n.remove();
            nodes.clear();
            tracked.clear();
            for (const s of sheets.values()) s.remove();
            sheets.clear();
            for (const type of Object.keys(document.body?.__swaswaOn || {})) unlisten(document.body, type);
            break;
          // an element the page has already (Java added it while the page was drawn anew: the drawing has it): once
          case 'append': if (!nodes.has(o[2].key)) inside(get(o[1]))?.append(build(o[2])); break;
          case 'insert': { if (nodes.has(o[2].key)) break; const p = inside(get(o[1])); p?.insertBefore(build(o[2]), get(o[3]) ?? null); break; }
          case 'remove': { const n = get(o[1]); n?.remove(); forget(n); break; }
          case 'attr': { const el = get(o[1]); if (!el) break; if (o[3] == null) el.removeAttribute(o[2]); else el.setAttribute(o[2], o[3]); break; }
          case 'text': { const n = get(o[1]); if (n) n.data = o[2]; break; }
          case 'prop': { const el = get(o[1]); if (el) prop(el, o[2], o[3]); break; }
          case 'listen': { const el = get(o[1]); if (el) listen(el, o[1], o[2]); break; }
          case 'unlisten': unlisten(get(o[1]), o[2]); break;
          case 'call': get(o[1])?.[o[2]]?.(); break;
          case 'script': {
            if (scripts.has(o[1])) break;
            scripts.add(o[1]);
            const s = document.createElement('script');
            s.dataset.swaswaKind = o[1];
            s.textContent = o[2];
            (document.head || document.documentElement).append(s);
            break;
          }
          case 'kind': {
            const el = get(o[1]);
            if (!el) break;
            el.__swaswaData = o[2];
            if (el.__swaswaMounted) kinds[el.__swaswaKind]?.update?.(el, o[2]);
            break;
          }
          case 'scheme': document.documentElement.style.colorScheme = o[1]; break;
          case 'title': document.title = o[1]; break;
          case 'lang': if (o[1] == null) document.documentElement.removeAttribute('lang'); else document.documentElement.lang = o[1]; break;
          case 'sheet': {
            let s = sheets.get(o[1]);
            if (o[2] == null) { s?.remove(); sheets.delete(o[1]); break; }
            if (!s) {
              s = document.createElement('style');
              s.dataset.swaswaSheet = o[1];
              document.head.append(s);
              sheets.set(o[1], s);
            }
            s.textContent = o[2];
            break;
          }
        }
      }
      return true;
    };

    // drag and drop: a component made draggable is picked up as itself; a drop target shows it is dragged over,
    // and a drop on it tells Java which component it was (Java moves it in)
    const keyOf = (el) => (el === document.body ? 0 : el?.__swaswaKey);
    let over = null;
    const setOver = (t) => {
      if (over === t) return;
      over?.removeAttribute('data-swaswa-drop-over');
      over = t;
      over?.setAttribute('data-swaswa-drop-over', '');
    };
    document.addEventListener('dragstart', (e) => {
      const el = e.target.closest?.('[draggable="true"]');
      const key = keyOf(el);
      if (key === undefined || !e.dataTransfer) return;
      e.dataTransfer.setData('application/x-swaswa', String(key));
      e.dataTransfer.effectAllowed = 'move';
      el.setAttribute('data-swaswa-dragging', '');
    }, true);
    document.addEventListener('dragend', (e) => {
      e.target.closest?.('[data-swaswa-dragging]')?.removeAttribute('data-swaswa-dragging');
      setOver(null);
    }, true);
    document.addEventListener('dragover', (e) => {
      const t = e.target.closest?.('[data-swaswa-drop]');
      if (!t || keyOf(t) === undefined) return setOver(null);
      e.preventDefault();
      if (e.dataTransfer) e.dataTransfer.dropEffect = 'move';
      setOver(t);
    }, true);
    document.addEventListener('dragleave', (e) => { if (e.target === over && !over.contains(e.relatedTarget)) setOver(null); }, true);
    document.addEventListener('drop', (e) => {
      const t = e.target.closest?.('[data-swaswa-drop]');
      setOver(null);
      const key = keyOf(t);
      if (key === undefined) return;
      e.preventDefault();
      if (!t.__swaswaOn?.drop) send(t, key, 'drop', e);   // its own drop listener sends it otherwise
    }, true);
    // the feedback, as a page's default: its own CSS can say otherwise
    const look = document.createElement('style');
    look.textContent = '[draggable="true"]{cursor:grab}[data-swaswa-dragging]{opacity:.5}'
      + '[data-swaswa-drop-over]{outline:2px dashed Highlight;outline-offset:-2px}';
    (document.head || document.documentElement).prepend(look);

    return { apply, kind, size: () => nodes.size };
  })();
}
