// A component library for the tests, written without a build: it shares the page's React (window.__swaswaReact).
(() => {
  const { React, register } = window.__swaswaReact;
  const h = React.createElement;

  // a button that counts: its count from Java, onIncrement with the next count
  register('Counter', ({ label, count = 0, onIncrement }) =>
    h('button', { id: 'counter', onClick: () => onIncrement?.(count + 1) }, `${label}: ${count}`));

  // a controlled text field: its value from Java, onChange with React's DOM event
  register('TextField', ({ value = '', placeholder, onChange }) =>
    h('input', { id: 'field', value, placeholder, onChange }));

  // a switch: checked from Java, onCheckedChange with a boolean (as Radix's)
  register('Switch', ({ checked = false, onCheckedChange }) =>
    h('button', { id: 'switch', role: 'switch', 'aria-checked': String(checked), onClick: () => onCheckedChange?.(!checked) },
      checked ? 'on' : 'off'));

  // a card: its title, and Java's children where it puts its children
  register('Card', ({ title, children }) =>
    h('section', { id: 'card' }, h('h3', null, title), h('div', { className: 'body' }, children)));

  // context across Java's components: a group gives its name to the items inside it, however deep
  const Group = React.createContext('none');
  register('Group', ({ name, children }) => h(Group.Provider, { value: name }, h('div', { id: 'group' }, children)));
  register('Item', ({ itemId }) => h('span', { id: itemId, className: 'item' }, React.useContext(Group)));

  // what JSON cannot carry: a day, a moment, bytes; and its own style
  const pad = (n) => String(n).padStart(2, '0');
  register('Stamp', ({ day, at, bytes, style }) =>
    h('div', { id: 'stamp', style },
      h('span', { id: 'stamp-day' }, day instanceof Date ? `${day.getFullYear()}-${pad(day.getMonth() + 1)}-${pad(day.getDate())}` : 'none'),
      h('span', { id: 'stamp-at' }, at instanceof Date ? at.toISOString() : 'none'),
      h('span', { id: 'stamp-bytes' }, bytes instanceof ArrayBuffer ? String(new Uint8Array(bytes).reduce((a, b) => a + b, 0)) : 'none')));

  // an adapter: a query answered by Java, events Java pushes
  register('Inbox', ({ service }) => {
    const [title, setTitle] = React.useState('loading');
    const [last, setLast] = React.useState('none');
    const [error, setError] = React.useState('none');
    React.useEffect(() => {
      service.queries.load('inbox').then((page) => setTitle(page.title + ':' + page.count), (e) => setError(e.message));
      service.queries.load('broken').catch((e) => setError(e.message));
      return service.events.subscribe((event) => setLast(event.text));
    }, [service]);
    return h('div', { id: 'inbox' }, h('span', { id: 'inbox-title' }, title), h('span', { id: 'inbox-last' }, last),
      h('span', { id: 'inbox-error' }, error));
  });

  // a handle: Java calls its actions
  register('Counter2', ({ actionsRef }) => {
    const [n, setN] = React.useState(0);
    React.useImperativeHandle(actionsRef, () => ({ add: (by) => setN((x) => x + by), twice: (x) => x * 2 }), []);
    return h('span', { id: 'counter2' }, String(n));
  });

  // a menu per row: the first entry chosen when a row is clicked
  register('Rows', ({ rows = [], rowMenu }) =>
    h('ul', { id: 'rows' }, rows.map((row) => h('li', { key: row.id, id: 'row-' + row.id,
      'data-menu': (rowMenu?.(row) ?? []).filter((e) => e !== 'separator').map((e) => e.label).join(','),
      onClick: () => rowMenu?.(row).find((e) => e !== 'separator')?.onSelect() }, row.name))));
})();
