// React in a swaswa page: React and ReactDOM (shared by every component library, as window.__swaswaReact), and the
// "react" kind of element (io.joshuasalcedo.swaswa.react.ReactElement): a component drawn into its element from Java's
// data, its events sent back to Java, its children (Java's) placed where it puts its children.
//
// A React element inside another (Tabs > TabsList > TabsTrigger, each a Java component) is not a React root of its
// own: it renders through a portal inside its parent's tree, so React's context reaches it (Tabs tells its triggers
// which one is active), while its DOM stays where Java put it.
import * as React from 'react';
import * as ReactDOM from 'react-dom';
import * as ReactDOMClient from 'react-dom/client';
import * as JsxRuntime from 'react/jsx-runtime';

type Data = {
  component: string;
  props: Record<string, unknown>;
  events: string[];
  binds: Record<string, string>; // prop -> the event the page changes it with
  children: boolean;
  slots?: string[];              // props that are Java components (an icon, actions): their holders are among its children
  adapters?: Record<string, { methods: string[]; events: string[] }>;   // props of functions Java answers
  handles?: string[];            // ref props Java calls the methods of (actionsRef)
  menus?: Record<string, Record<string, unknown[]>>;   // a menu's entries, by prop and item key
};
type Send = (name: string, payload: unknown) => void;

/** What draws one element, and the React elements nested in it. */
type Ctl = { version: number; nested: Set<El>; watchers: Set<() => void>; changed: () => void };

type El = HTMLElement & {
  __swaswaKind?: string;
  __swaswaKey?: number;
  __swaswaCtl?: Ctl;
  __swaswaRoot?: ReactDOMClient.Root;
  __swaswaParentEl?: El;
  __swaswaReactData: Data;
  __swaswaReactSlot: HTMLElement;
  __swaswaReactSend: Send;
};

declare global {
  interface Window {
    __swaswaDom: { kind: (name: string, impl: unknown) => void };
    __swaswaReact: unknown;
  }
}

// ---- the components libraries registered ----

const components = new Map<string, React.ComponentType<any>>();
let registered = 0;
const registryWatchers = new Set<() => void>();

function register(name: string, component: React.ComponentType<any>) {
  components.set(name, component);
  registered++;
  for (const w of registryWatchers) w();
}

const ctl = (): Ctl => {
  const c: Ctl = {
    version: 0,
    nested: new Set(),
    watchers: new Set(),
    changed: () => { c.version++; for (const w of c.watchers) w(); },
  };
  return c;
};

// ---- Java's children, where the component puts its children ----

function Slot({ node, nested }: { node: HTMLElement; nested: React.ReactNode }) {
  const ref = React.useRef<HTMLSpanElement>(null);
  React.useLayoutEffect(() => {
    const host = ref.current!;
    host.append(node);
    return () => { if (node.parentNode === host) host.removeChild(node); };
  }, [node]);
  return <span ref={ref} style={{ display: 'contents' }}>{nested}</span>;
}

// ---- Java's components given as props (an icon, a label, actions), where the component draws that prop ----

type Named = El & { __swaswaNamed?: Record<string, HTMLElement> };

/** Prop {@code name}'s holder: among Java's children (just given), or where it was put the last time. */
function holderOf(el: Named, name: string): HTMLElement | null {
  const fresh = [...el.__swaswaReactSlot.children].find((c) => (c as HTMLElement).dataset.swaswaSlot === name) as HTMLElement | undefined;
  const kept = el.__swaswaNamed?.[name];
  const found = fresh ?? (kept && kept.isConnected ? kept : undefined);
  (el.__swaswaNamed ??= {})[name] = found!;
  return found ?? null;
}

function NamedSlot({ node }: { node: HTMLElement }) {
  const ref = React.useRef<HTMLSpanElement>(null);
  React.useLayoutEffect(() => {
    const host = ref.current!;
    host.append(node);
  }, [node]);
  return <span ref={ref} style={{ display: 'contents' }} />;
}

// a holder not placed yet (its prop not drawn) shows nowhere: not among the children; a React element's own element
// lays out as if it were its parent's (its style is the component's: React's style prop)
const hide = document.createElement('style');
hide.textContent = 'swaswa-slot > [data-swaswa-slot]{display:none !important}[data-swaswa-react]{display:contents !important}';
(document.head || document.documentElement).append(hide);

// ---- Java's values the JSON cannot carry: a date, bytes ----

/** The bytes fetched from Java, by URL: an ArrayBuffer once here. */
const fetched = new Map<string, ArrayBuffer | Promise<void>>();

/** {@code value} for the component: a tagged date as a Date, tagged bytes as an ArrayBuffer (fetched, then drawn again). */
function decode(value: unknown, again: () => void): unknown {
  if (Array.isArray(value)) return value.map((v) => decode(v, again));
  if (!value || typeof value !== 'object') return value;
  const v = value as Record<string, any>;
  if (v.$swaswa === 'date') {
    if (v.day) { const [y, m, d] = String(v.day).split('-').map(Number); return new Date(y, m - 1, d); }
    return new Date(v.at ?? v.local);
  }
  if (v.$swaswa === 'bytes') {
    const known = fetched.get(v.url);
    if (known instanceof ArrayBuffer) return known;
    if (!known) {
      fetched.set(v.url, fetch(v.url).then((r) => r.arrayBuffer()).then((b) => { fetched.set(v.url, b); again(); },
        (e) => { fetched.delete(v.url); console.error('swaswa: bytes did not come', v.url, e); }));
    }
    return undefined;   // drawn again once here
  }
  const out: Record<string, unknown> = {};
  for (const [k, x] of Object.entries(v)) out[k] = decode(x, again);
  return out;
}

// ---- an event's payload, as JSON: its first argument (a DOM event: its element's value) ----

const json = (v: unknown): unknown => {
  if (v === undefined) return null;
  try { return JSON.parse(JSON.stringify(v)); } catch { return null; }
};

function payload(args: unknown[]): unknown {
  const first = args[0] as any;
  if (first && typeof first === 'object' && ('nativeEvent' in first || first instanceof Event)) {
    const t = first.target as any;
    return t ? { value: t.value ?? null, checked: t.checked ?? null } : null;
  }
  return json(first);
}

const boundValue = (prop: string, p: any) =>
  p && typeof p === 'object' && ('value' in p || 'checked' in p) ? (prop === 'checked' ? p.checked : p.value) : p;

// ---- calls between the component and Java ----

/** The React elements by their number in the page (Java finds them by it). */
const byKey = new Map<number, El>();
/** The component's calls waiting for Java's answer, by id. */
const waiting = new Map<number, { resolve: (v: unknown) => void; reject: (e: Error) => void }>();
let nextCall = 0;

/** Java answers call {@code id}: its value, or (ok false) why it failed. */
function answer(id: number, ok: boolean, value: unknown) {
  const call = waiting.get(id);
  if (!call) return;
  waiting.delete(id);
  if (ok) call.resolve(decode(value, () => {}));
  else call.reject(new Error(String(value)));
}

/** What the component listens with (an adapter's subscribe), by element, prop and function. */
type Listeners = Map<string, Set<(event: unknown) => void>>;

/** Java tells the component what happened: every listener of that adapter function gets it. */
function emit(key: number, prop: string, method: string, value: unknown) {
  const el = byKey.get(key) as (El & { __swaswaListeners?: Listeners }) | undefined;
  for (const listener of el?.__swaswaListeners?.get(prop + '.' + method) ?? []) listener(decode(value, () => {}));
}

/** Java calls a method of what the component put in its ref prop (its actions): what it returns. */
function invoke(key: number, prop: string, method: string, args: unknown[]) {
  const el = byKey.get(key) as (El & { __swaswaRefs?: Record<string, { current: any }> }) | undefined;
  const target = el?.__swaswaRefs?.[prop]?.current;
  const fn = target?.[method];
  if (typeof fn !== 'function') throw new Error(`no ${method} on ${prop}`);
  const result = fn.apply(target, args.map((a) => decode(a, () => {})));
  return result instanceof Promise ? undefined : json(result);
}

/** Prop {@code prop}'s adapter: each function calls Java (a promise of its answer), each event function listens to it. */
function adapterOf(el: El & { __swaswaAdapters?: Record<string, unknown>; __swaswaListeners?: Listeners }, prop: string,
                   spec: { methods: string[]; events: string[] }) {
  const made = (el.__swaswaAdapters ??= {});
  if (made[prop]) return made[prop];
  const root: Record<string, any> = {};
  const at = (path: string, fn: unknown) => {
    const parts = path.split('.');
    let o = root;
    for (const part of parts.slice(0, -1)) o = (o[part] ??= {});
    o[parts[parts.length - 1]] = fn;
  };
  for (const method of spec.methods) {
    at(method, (...args: unknown[]) => new Promise((resolve, reject) => {
      const id = ++nextCall;
      waiting.set(id, { resolve, reject });
      el.__swaswaReactSend('$call', { id, prop, method, args: args.map((a) => (typeof a === 'function' ? null : json(a))) });
    }));
  }
  for (const method of spec.events) {
    at(method, (listener: (event: unknown) => void) => {
      const listeners = (el.__swaswaListeners ??= new Map());
      const name = prop + '.' + method;
      let set = listeners.get(name);
      if (!set) {
        listeners.set(name, (set = new Set()));
        el.__swaswaReactSend('$subscribe', { prop, method });   // Java tells this element from now on
      }
      set.add(listener);
      return () => set!.delete(listener);
    });
  }
  return (made[prop] = root);
}

/** An item's key, as Java made it: its id, else its JSON. */
const itemKey = (item: any) => (item && typeof item === 'object' && 'id' in item ? String(item.id) : JSON.stringify(item));

// ---- one element's component ----

function Host({ el }: { el: El }) {
  const c = el.__swaswaCtl!;
  React.useSyncExternalStore((f) => { registryWatchers.add(f); return () => registryWatchers.delete(f); }, () => registered);
  React.useSyncExternalStore((f) => { c.watchers.add(f); return () => c.watchers.delete(f); }, () => c.version);
  const data = el.__swaswaReactData;
  // what the person changed, at once (Java has it too; Java's own changes come as new data and win)
  const [local, setLocal] = React.useState<Record<string, unknown>>({});
  const [seen, setSeen] = React.useState(data);
  if (seen !== data) { setSeen(data); setLocal({}); }

  const C = components.get(data.component);
  if (!C) return null;   // its library has not registered it yet: drawn when it does
  const props: Record<string, unknown> = { ...(decode(data.props, c.changed) as Record<string, unknown>), ...local };
  for (const name of data.events) {
    props[name] = (...args: unknown[]) => {
      const p = payload(args);
      const changed: Record<string, unknown> = {};
      for (const [prop, event] of Object.entries(data.binds)) if (event === name) changed[prop] = boundValue(prop, p);
      if (Object.keys(changed).length) setLocal((l) => ({ ...l, ...changed }));
      el.__swaswaReactSend(name, p);
    };
  }
  for (const [prop, spec] of Object.entries(data.adapters ?? {})) props[prop] = adapterOf(el as any, prop, spec);
  const refs = ((el as any).__swaswaRefs ??= {}) as Record<string, { current: unknown }>;
  for (const prop of data.handles ?? []) props[prop] = (refs[prop] ??= { current: null });
  for (const [prop, table] of Object.entries(data.menus ?? {})) {
    props[prop] = (...args: unknown[]) => {
      const item = args.find((a) => a && typeof a === 'object') ?? args[0];
      const key = itemKey(item);
      return (table[key] ?? []).map((entry, index) => (entry === 'separator' ? entry
        : { ...(entry as object), onSelect: () => el.__swaswaReactSend('$menu', { prop, key, index }) }));
    };
  }
  for (const name of data.slots ?? []) {
    const holder = holderOf(el as Named, name);
    if (holder) props[name] = <NamedSlot node={holder} />;
  }
  const nested = [...c.nested].map((child) => ReactDOM.createPortal(<Host el={child} />, child, String(child.__swaswaKey)));
  if (data.children) return <C {...props}><Slot node={el.__swaswaReactSlot} nested={nested} /></C>;
  return <><C {...props} />{nested}</>;
}

// ---- where an element is: in another React element (nested), or a root of its own ----

/** The React element it is inside, through Java's slots; null when none. */
function ownerOf(el: El): El | null {
  let n: any = el.parentNode;
  while (n) {
    if (n.__swaswaOwner) {
      const o = n.__swaswaOwner as El;
      if (o.__swaswaKind === 'react') return o;
      n = o.parentNode;
      continue;
    }
    if (n.__swaswaKind === 'react') return n as El;
    n = n.parentNode;
  }
  return null;
}

const depth = (el: El) => { let d = 0; for (let o = ownerOf(el); o; o = ownerOf(o)) d++; return d; };

let pending: El[] = [];
const flush = () => {
  const now = pending.sort((a, b) => depth(a) - depth(b));
  pending = [];
  for (const el of now) {
    if (el.__swaswaCtl) continue;
    el.__swaswaCtl = ctl();
    const owner = ownerOf(el);
    if (owner?.__swaswaCtl) {
      el.__swaswaParentEl = owner;
      owner.__swaswaCtl.nested.add(el);
      owner.__swaswaCtl.changed();
    } else {
      el.__swaswaRoot = ReactDOMClient.createRoot(el);
      el.__swaswaRoot.render(<Host el={el} />);
    }
  }
};

window.__swaswaDom.kind('react', {
  mount(el: El, data: Data, slot: HTMLElement, send: Send) {
    byKey.set(el.__swaswaKey!, el);
    el.__swaswaReactData = data;
    el.__swaswaReactSlot = slot;
    el.__swaswaReactSend = send;
    if (!pending.length) queueMicrotask(flush);   // once the page's changes are all in: parents before children
    pending.push(el);
  },
  update(el: El, data: Data) {
    el.__swaswaReactData = data;
    el.__swaswaCtl?.changed();
  },
  unmount(el: El) {
    if (byKey.get(el.__swaswaKey!) === el) byKey.delete(el.__swaswaKey!);
    pending = pending.filter((p) => p !== el);
    const parent = el.__swaswaParentEl;
    if (parent) {
      parent.__swaswaCtl?.nested.delete(el);
      parent.__swaswaCtl?.changed();
    }
    const root = el.__swaswaRoot;
    el.__swaswaRoot = undefined;
    el.__swaswaCtl = undefined;
    el.__swaswaParentEl = undefined;
    if (root) queueMicrotask(() => root.unmount());   // not while React renders
  },
});

window.__swaswaReact = { React, ReactDOM, ReactDOMClient, JsxRuntime, register, answer, emit, invoke, version: React.version };
