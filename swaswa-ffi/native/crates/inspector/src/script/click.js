  // ----- click capture -----
  function describeElement(el) {
    if (!el || el.nodeType !== 1) return null;
    const tag = (el.tagName || '').toLowerCase();
    const id = el.id || null;
    const cls = (typeof el.className === 'string' ? el.className : '')
      .split(/\s+/).filter(Boolean).slice(0, 6);
    const role = el.getAttribute && el.getAttribute('role');
    const ariaLabel = el.getAttribute && el.getAttribute('aria-label');
    const href = el.getAttribute && el.getAttribute('href');
    const title = el.getAttribute && el.getAttribute('title');
    const text = ((el.innerText || el.textContent || '').trim() || '').slice(0, 80);
    const dataAttrs = {};
    if (el.attributes) {
      for (let i = 0; i < el.attributes.length; i++) {
        const a = el.attributes[i];
        if (a.name.indexOf('data-') === 0 && Object.keys(dataAttrs).length < 6) {
          dataAttrs[a.name] = String(a.value).slice(0, 60);
        }
      }
    }
    return {
      tag,
      id: id || null,
      classes: cls,
      role: role || null,
      ariaLabel: ariaLabel || null,
      href: href || null,
      title: title || null,
      text: text || null,
      dataAttrs,
    };
  }

  function buildSelector(el) {
    if (!el || el.nodeType !== 1) return '';
    if (el.id) return '#' + CSS.escape(el.id);
    const parts = [];
    let cur = el;
    while (cur && cur.nodeType === 1 && cur !== document.body && parts.length < 6) {
      let s = cur.tagName.toLowerCase();
      if (cur.id) {
        parts.unshift(s + '#' + CSS.escape(cur.id));
        break;
      }
      const parent = cur.parentNode;
      if (parent) {
        const same = [];
        for (let i = 0; i < parent.children.length; i++) {
          if (parent.children[i].tagName === cur.tagName) same.push(parent.children[i]);
        }
        if (same.length > 1) {
          s += ':nth-of-type(' + (same.indexOf(cur) + 1) + ')';
        }
      }
      parts.unshift(s);
      cur = cur.parentNode;
    }
    return parts.join(' > ');
  }

  document.addEventListener('click', (e) => {
    try {
      const target = e.target;
      const ancestor = target && target.closest
        ? (target.closest('button, a, [role="button"], [role="link"], [data-action], [data-testid]') || target)
        : target;
      const desc = describeElement(ancestor);
      const sel = buildSelector(ancestor);
      console.log('[__LOG_PREFIX__-click]', sel, desc);
      emit(CLICK_EVENT, {
        selector: sel,
        targetSelector: buildSelector(target),
        x: e.clientX,
        y: e.clientY,
        button: e.button,
        ...desc,
      });
    } catch (err) {
      console.error('[__LOG_PREFIX__-intercept] click hook error', err);
    }
  }, true);
