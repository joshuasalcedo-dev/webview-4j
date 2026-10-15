(() => {
  if (window.__SWASWA_DOM__) return;
  const eventStyles = ['color', 'background-color', 'border-color', 'display', 'visibility',
    'opacity', 'font-family', 'font-size', 'font-weight', 'width', 'height'];
  function selector(element) {
    const parts = [];
    for (let node = element; node && node.nodeType === 1; node = node.parentElement) {
      if (node.id) { parts.unshift('#' + CSS.escape(node.id)); break; }
      let part = node.tagName.toLowerCase();
      const siblings = node.parentElement
        ? Array.from(node.parentElement.children).filter(other => other.tagName === node.tagName) : [];
      if (siblings.length > 1) part += ':nth-of-type(' + (siblings.indexOf(node) + 1) + ')';
      parts.unshift(part);
    }
    return parts.join(' > ');
  }
  function snapshot(element, allStyles = false) {
    if (!element || element.nodeType !== 1) return null;
    const computed = getComputedStyle(element);
    const styles = {};
    for (const name of allStyles ? Array.from(computed) : eventStyles) {
      styles[name] = computed.getPropertyValue(name);
    }
    const rect = element.getBoundingClientRect();
    return { selector: selector(element), tag: element.tagName.toLowerCase(), id: element.id || '',
      classes: Array.from(element.classList), text: (element.textContent || '').slice(0, 4096),
      value: element.type === 'password' ? null : ('value' in element ? String(element.value) : null),
      attributes: Object.fromEntries(Array.from(element.attributes)
        .filter(attr => element.type !== 'password' || attr.name !== 'value')
        .map(attr => [attr.name, attr.value])),
      styles, bounds: { x: rect.x, y: rect.y, width: rect.width, height: rect.height } };
  }
  function query(css, first) {
    try {
      const elements = first ? [document.querySelector(css)].filter(Boolean)
        : Array.from(document.querySelectorAll(css));
      return { ok: true, elements: elements.map(element => snapshot(element, true)) };
    } catch (error) { return { ok: false, error: String(error) }; }
  }
  Object.defineProperty(window, '__SWASWA_DOM__', { value: { query, snapshot } });
  for (const [nativeType, type] of Object.entries({ click: 'click', dblclick: 'doubleClick',
    pointerover: 'hover', pointerout: 'hoverEnd', input: 'input', change: 'change',
    focusin: 'focus', focusout: 'blur', keydown: 'keyDown', keyup: 'keyUp', contextmenu: 'contextMenu' })) {
    document.addEventListener(nativeType, event => {
      const target = event.composedPath?.()[0] || event.target;
      if (!target || target.nodeType !== 1) return;
      if ((type === 'hover' || type === 'hoverEnd') && target.contains(event.relatedTarget)) return;
      const isPassword = target.type === 'password';
      const payload = { type, element: snapshot(target), x: event.clientX ?? 0,
        y: event.clientY ?? 0, button: event.button ?? 0, key: isPassword ? null : event.key ?? null };
      window.ipc.postMessage(JSON.stringify({ event: 'dom-event', payload }));
    }, true);
  }
})();
