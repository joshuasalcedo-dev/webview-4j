// webview-4j's automation helper, put in a page by Automation (once): finding elements as people point at them (a
// role and a name, a field's label, a text), acting on them as a person does (pointer and key events, React-friendly
// value setting), and, while recording, telling Java what the person does, as steps by role and name.
(() => {
  if (window.__w4jAuto) return;

  const text = (el) => (el ? (el.innerText ?? el.textContent ?? "") : "").replace(/\s+/g, " ").trim();
  const FIELDS = ["input", "textarea", "select"];

  function role(el) {
    const explicit = el.getAttribute && el.getAttribute("role");
    if (explicit) return explicit.split(" ")[0];
    const t = el.tagName ? el.tagName.toLowerCase() : "";
    const type = (el.getAttribute && el.getAttribute("type") || "").toLowerCase();
    if (t === "button" || (t === "input" && ["button", "submit", "reset"].includes(type))) return "button";
    if (t === "a" && el.hasAttribute("href")) return "link";
    if (t === "input" && type === "checkbox") return "checkbox";
    if (t === "input" && type === "radio") return "radio";
    if (t === "input" && type === "range") return "slider";
    if (t === "input" && type === "search") return "searchbox";
    if (t === "textarea" || (t === "input" && !["button", "submit", "reset", "checkbox", "radio", "range", "hidden", "file", "image"].includes(type))) return "textbox";
    if (t === "select") return el.multiple ? "listbox" : "combobox";
    if (/^h[1-6]$/.test(t)) return "heading";
    if (t === "li") return "listitem";
    if (t === "tr") return "row";
    if (t === "td") return "cell";
    if (t === "th") return "columnheader";
    if (t === "img") return "img";
    if (t === "dialog") return "dialog";
    if (t === "option") return "option";
    if (t === "summary") return "button";
    if (el.isContentEditable && el.parentElement && !el.parentElement.isContentEditable) return "textbox";
    return null;
  }

  function name(el) {
    if (!el || !el.getAttribute) return "";
    const aria = el.getAttribute("aria-label");
    if (aria) return aria.trim();
    const by = el.getAttribute("aria-labelledby");
    if (by) return by.split(/\s+/).map((id) => text(document.getElementById(id))).join(" ").trim();
    const t = el.tagName.toLowerCase();
    if (FIELDS.includes(t) || el.isContentEditable) {
      if (el.id) {
        const l = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
        if (l) return text(l);
      }
      const wrap = el.closest("label");
      if (wrap) return text(wrap).replace(el.value || "", "").trim() || text(wrap);
      // the kit's fields: a label element in the field's own box
      let box = el.parentElement;
      for (let i = 0; box && i < 3; i++, box = box.parentElement) {
        const l = box.querySelector(":scope > label, :scope > .nt-label, :scope > [class*=label]");
        if (l && !l.contains(el)) return text(l);
      }
      return (el.getAttribute("placeholder") || el.getAttribute("title") || el.getAttribute("name") || "").trim();
    }
    if (t === "img") return (el.getAttribute("alt") || "").trim();
    return text(el) || (el.getAttribute("title") || "").trim();
  }

  function visible(el) {
    const r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    const s = getComputedStyle(el);
    return s.visibility !== "hidden" && s.display !== "none" && s.opacity !== "0";
  }

  const fits = (n, want, exact) => (exact ? n === want : n.toLowerCase().includes(want.toLowerCase()))

  function candidates(loc) {
    switch (loc.kind) {
      case "css":
        return [...document.querySelectorAll(loc.css)];
      case "testid":
        return [...document.querySelectorAll('[data-testid="' + CSS.escape(loc.name) + '"]')];
      case "text":
        return [...document.querySelectorAll("body *")].filter((e) => {
          const n = text(e);
          return n && (fits(n, loc.name, true) || fits(n, loc.name, false)) && ![...e.children].some((c) => fits(text(c), loc.name, false));
        });
      case "label":
        return [...document.querySelectorAll("input, textarea, select, [contenteditable=true], [role=textbox], [role=combobox], [role=searchbox], [role=checkbox], [role=switch], [role=slider]")];
      default:
        return [...document.querySelectorAll("*")].filter((e) => role(e) === loc.role);
    }
  }

  /** Every visible element the locator points at: exact names first, those that contain it when none is exact. */
  function all(loc) {
    let els = candidates(loc).filter(visible);
    const named = loc.kind === "role" || loc.kind === "label" || loc.kind === "text";
    if (named && loc.name != null) {
      const key = loc.kind === "text" ? text : name;
      const exact = els.filter((e) => key(e) === loc.name);
      els = exact.length || loc.exact ? exact : els.filter((e) => fits(key(e), loc.name, false));
    }
    return els;
  }

  function find(loc) {
    const els = all(loc);
    return els[loc.nth || 0] || null;
  }

  /** What is on the page, for an error that says what was there instead. */
  function inventory() {
    const seen = [];
    for (const e of document.querySelectorAll("*")) {
      const r = role(e);
      if (!r || !visible(e) || !["button", "link", "textbox", "searchbox", "checkbox", "combobox", "tab", "menuitem", "option", "heading", "switch", "radio"].includes(r)) continue;
      const n = name(e);
      if (n) seen.push(r + ' "' + n.slice(0, 40) + '"');
      if (seen.length >= 25) break;
    }
    return seen;
  }

  function center(el) {
    const r = el.getBoundingClientRect();
    return { clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 };
  }

  function pointer(el, types, extra) {
    const at = center(el);
    for (const type of types) {
      const opts = { bubbles: true, cancelable: true, composed: true, view: window, button: extra && extra.button || 0, ...at, ...extra };
      const ev = type.startsWith("pointer") ? new PointerEvent(type, { pointerId: 1, isPrimary: true, ...opts }) : new MouseEvent(type, opts);
      el.dispatchEvent(ev);
    }
  }

  function setValue(el, value) {
    const proto = el.tagName === "TEXTAREA" ? HTMLTextAreaElement.prototype : el.tagName === "SELECT" ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(proto, "value").set;
    setter.call(el, value);
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
  }

  function key(target, combo) {
    const parts = combo.split("+");
    const k = parts.pop();
    const mods = { ctrlKey: parts.includes("Control"), shiftKey: parts.includes("Shift"), altKey: parts.includes("Alt"), metaKey: parts.includes("Meta") };
    const opts = { key: k, bubbles: true, cancelable: true, ...mods };
    const down = target.dispatchEvent(new KeyboardEvent("keydown", opts));
    if (down && k === "Enter" && !mods.ctrlKey && target.form && target.tagName === "INPUT") target.form.requestSubmit();
    target.dispatchEvent(new KeyboardEvent("keyup", opts));
  }

  /** Acts on what the locator points at: {ok, role, name}, or {missing, found, seen}. */
  function act(loc, action, arg) {
    const el = find(loc);
    if (!el) return { missing: true, found: all(loc).length, seen: inventory() };
    el.scrollIntoView({ block: "center", inline: "center" });
    switch (action) {
      case "click":
        el.focus && el.focus({ preventScroll: true });
        pointer(el, ["pointerdown", "mousedown", "pointerup", "mouseup", "click"]);
        break;
      case "dblclick":
        pointer(el, ["pointerdown", "mousedown", "pointerup", "mouseup", "click", "pointerdown", "mousedown", "pointerup", "mouseup", "click"]);
        pointer(el, ["dblclick"], { detail: 2 });
        break;
      case "contextmenu":
        pointer(el, ["pointerdown", "mousedown", "contextmenu"], { button: 2 });
        break;
      case "hover":
        pointer(el, ["pointerover", "pointerenter", "mouseover", "mouseenter", "pointermove", "mousemove"]);
        break;
      case "focus":
        el.focus();
        break;
      case "type": {
        el.focus();
        if (el.isContentEditable) {
          document.execCommand("selectAll", false);
          document.execCommand("insertText", false, arg);
        } else setValue(el, arg);
        break;
      }
      case "press":
        el.focus && el.focus();
        key(el, arg);
        break;
      case "check": {
        const now = el.checked ?? el.getAttribute("aria-checked") === "true";
        if (now !== arg) pointer(el, ["pointerdown", "mousedown", "pointerup", "mouseup", "click"]);
        break;
      }
      case "select": {
        if (el.tagName === "SELECT") {
          const option = [...el.options].find((o) => o.text.trim() === arg || o.value === arg) || [...el.options].find((o) => fits(o.text, arg, false));
          if (!option) return { missing: true, found: 0, seen: [...el.options].map((o) => 'option "' + o.text.trim() + '"') };
          setValue(el, option.value);
        } else {
          pointer(el, ["pointerdown", "mousedown", "pointerup", "mouseup", "click"]);
          return { ok: true, open: true, role: role(el), name: name(el) };
        }
        break;
      }
      case "read":
        return { ok: true, text: text(el), value: el.value ?? null, checked: el.checked ?? null, role: role(el), name: name(el) };
      default:
        return { missing: true, found: 0, seen: ["no action " + action] };
    }
    return { ok: true, role: role(el), name: name(el) };
  }

  /** The page's key, on what has the focus. */
  function press(combo) {
    key(document.activeElement || document.body, combo);
    return { ok: true };
  }

  // ---- recording: what the person does, as steps ----

  const KEYS = ["Enter", "Escape", "Tab", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Delete", "Backspace", "F2", "Home", "End", "PageUp", "PageDown"];
  const POINTED = ["button", "link", "checkbox", "radio", "switch", "tab", "menuitem", "option", "listitem", "row", "treeitem", "combobox", "heading", "cell", "img"];

  /** The step's target for what was touched: the nearest element with a role and a name, else its text. */
  function target(el) {
    for (let e = el; e && e !== document.body && e.nodeType === 1; e = e.parentElement) {
      const r = role(e);
      const n = name(e);
      if (r && n && POINTED.includes(r) && n.length <= 120) {
        const same = all({ kind: "role", role: r, name: n, exact: true });
        return { role: r, name: n, exact: true, nth: Math.max(0, same.indexOf(e)) };
      }
    }
    const t = text(el);
    if (t && t.length <= 80) {
      const same = all({ kind: "text", name: t, exact: true });
      return { text: t, exact: true, nth: Math.max(0, same.indexOf(el)) };
    }
    return null;
  }

  function fieldTarget(el) {
    const n = name(el);
    if (n) return { field: n, exact: true };
    return target(el);
  }

  let recording = null;

  function record(command) {
    if (recording) return { ok: true };
    const send = (step) => window.__webview4j && window.__webview4j.invoke(command, step).catch(() => {});
    const typing = new Map();
    const flush = (el) => {
      if (!typing.has(el)) return;
      const secret = el.type === "password";
      send({ type: typing.get(el), text: secret ? "${PASSWORD}" : el.isContentEditable ? el.innerText : el.value });
      typing.delete(el);
    };
    const typed = (el) => FIELDS.includes(el.tagName && el.tagName.toLowerCase()) && !["checkbox", "radio", "button", "submit", "reset", "file", "range"].includes(el.type) || el.isContentEditable;
    const handlers = {
      click: (ev) => {
        if (ev.detail > 1) return;
        const el = ev.target;
        if (typed(el) || el.tagName === "SELECT" || el.tagName === "OPTION") return;
        for (const f of [...typing.keys()]) flush(f);
        const t = target(el);
        if (t) send({ click: t });
      },
      dblclick: (ev) => {
        const t = target(ev.target);
        if (t) send({ doubleClick: t });
      },
      contextmenu: (ev) => {
        const t = target(ev.target);
        if (t) send({ rightClick: t });
      },
      input: (ev) => {
        const el = ev.target;
        if (typed(el)) typing.set(el, fieldTarget(el));
      },
      change: (ev) => {
        const el = ev.target;
        if (el.tagName === "SELECT") send({ select: fieldTarget(el), option: el.selectedOptions[0] ? el.selectedOptions[0].text.trim() : "" });
        else flush(el);
      },
      focusout: (ev) => flush(ev.target),
      keydown: (ev) => {
        if (!KEYS.includes(ev.key) && !(ev.ctrlKey || ev.metaKey) || ["Control", "Shift", "Alt", "Meta"].includes(ev.key)) return;
        if ((ev.ctrlKey || ev.metaKey) && ev.key.length === 1 && typed(ev.target) && "acvxz".includes(ev.key.toLowerCase())) return;
        flush(ev.target);
        const combo = (ev.ctrlKey ? "Control+" : "") + (ev.metaKey ? "Meta+" : "") + (ev.altKey ? "Alt+" : "") + (ev.shiftKey ? "Shift+" : "") + ev.key;
        send({ press: combo });
      },
    };
    for (const [type, h] of Object.entries(handlers)) document.addEventListener(type, h, true);
    recording = { handlers, typing, flush };
    return { ok: true };
  }

  function stopRecording() {
    if (!recording) return { ok: true };
    for (const f of [...recording.typing.keys()]) recording.flush(f);
    for (const [type, h] of Object.entries(recording.handlers)) document.removeEventListener(type, h, true);
    recording = null;
    return { ok: true };
  }

  window.__w4jAuto = { all, find, act, press, record, stopRecording, role, name, text, inventory, count: (loc) => all(loc).length };
})();
