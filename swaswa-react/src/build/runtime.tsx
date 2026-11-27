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
  const props: Record<string, unknown> = { ...data.props, ...local };
  for (const name of data.events) {
    props[name] = (...args: unknown[]) => {
      const p = payload(args);
      const changed: Record<string, unknown> = {};
      for (const [prop, event] of Object.entries(data.binds)) if (event === name) changed[prop] = boundValue(prop, p);
      if (Object.keys(changed).length) setLocal((l) => ({ ...l, ...changed }));
      el.__swaswaReactSend(name, p);
    };
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

window.__swaswaReact = { React, ReactDOM, ReactDOMClient, JsxRuntime, register, version: React.version };
