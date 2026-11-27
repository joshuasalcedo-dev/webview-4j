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
})();
