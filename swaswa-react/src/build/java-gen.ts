// Typed Java for a React component library: one class per exported component, from its TypeScript props.
//
//   bun java-gen.ts <config.json>
//   JAVA_GEN_SKIPPED=1 bun java-gen.ts <config.json>   also lists, on stderr, each prop Java cannot give, and its type
//
// config: {
//   "tsconfig": "ui/tsconfig.app.json",          the library's TypeScript project (paths relative to the config)
//   "components": "ui/src/components/ui",        the folder of its components (every .tsx in it and its folders;
//                                                 exported PascalCase functions)
//   "exclude": ["stories"],                       files whose path has one of these is left out
//   "out": "../main/java",                        where the Java goes
//   "package": "io.joshuasalcedo.swaswa.shadcn",  each component file's classes go in a package of their own under it:
//                                                 alert.tsx -> ...shadcn.alert (Alert, AlertTitle...), alert-dialog.tsx
//                                                 -> ...shadcn.alertdialog, a folder's file -> ...ui.chat.chatwindow
//   "library": "Shadcn",                          a class of the package with bundle(): the library's PageScript
//   "classPrefix": "Shadcn",                      before each class's name (ShadcnAlert): the page's component keeps its own
//   "moduleInfo": "../main/java/module-info.java", its exports between "// java-gen: exports" and "// java-gen: end"
//   "taken": ["../../../swaswa-ffi/src/main/java/io/joshuasalcedo/swaswa/component/Component.java", ...],
//                                                 Java sources whose method names a prop must not take
//   "source": "shadcn/ui"                         named in the classes' docs
// }
//
// A prop becomes a typed setter (and getter) when Java can give it: text, a number, a boolean, one of some words (an
// enum), a list or a record of these (an object of data: a record of the class, { value, label } -> Segment), a
// component (a ReactNode: setIcon(Node), drawn where the component draws it), or a function called on... (an event:
// a listener of what it is called with). What it cannot (a function that must answer at once, a handle) is left out.
// Props of HTML's own elements (React's DOM attributes) are left out but for a few every component wants.
// A value the person changes (value/onValueChange, checked/onCheckedChange, open/onOpenChange, an input's
// value/onChange) is kept in Java at once, and live as a property.
import ts from 'typescript';
import { dirname, join, resolve, basename } from 'node:path';
import { mkdirSync, readFileSync, readdirSync, writeFileSync, existsSync, rmSync } from 'node:fs';

type Config = {
  tsconfig: string; components: string; out: string; package: string; library: string; taken: string[]; source: string;
  noChildren?: string[];   // components that never take children (beyond those of input, textarea...)
  packages?: Record<string, string>;   // a file's package of its own, over its family's ({"alert-dialog": "alertdialog"})
  classPrefix?: string;    // before each Java class's name: "Shadcn" makes Alert ShadcnAlert
  exclude?: string[];
  moduleInfo?: string;
};

const configPath = resolve(process.argv[2] ?? 'java-gen.json');
const base = dirname(configPath);
const config: Config = JSON.parse(readFileSync(configPath, 'utf8'));
const at = (p: string) => resolve(base, p);

// HTML's attributes every component may want, though React declares them for any element
const DOM_KEPT = new Set([
  'children', 'placeholder', 'type', 'name', 'value', 'defaultValue', 'checked', 'defaultChecked', 'disabled',
  'required', 'readOnly', 'rows', 'cols', 'min', 'max', 'step', 'maxLength', 'minLength', 'pattern', 'href', 'target',
  'htmlFor', 'autoComplete', 'onClick', 'onChange', 'onFocus', 'onBlur',
]);
// HTML's attributes of a form field: kept only on a component of an input or a textarea
const FIELD_ONLY = new Set([
  'value', 'defaultValue', 'checked', 'defaultChecked', 'placeholder', 'name', 'required', 'readOnly', 'rows', 'cols', 'min',
  'max', 'step', 'maxLength', 'minLength', 'pattern', 'autoComplete', 'onChange',
]);
// never typed: React's own, or what Java does its own way (classes: cssClass; style: getStyle; id: setId)
const NEVER = new Set(['key', 'ref', 'style', 'className', 'id', 'render', 'dangerouslySetInnerHTML', 'suppressHydrationWarning']);
// a value the person changes, and the event it changes with
const BOUND: Record<string, string> = { value: 'onValueChange', checked: 'onCheckedChange', open: 'onOpenChange', pressed: 'onPressedChange' };

// ---- Java's names ----

const takenNames = new Set<string>(['getComponent', 'onEvent', 'removeEventListener', 'getClass', 'hashCode', 'equals', 'toString']);
for (const file of config.taken) {
  const text = readFileSync(at(file), 'utf8');
  // declared methods only (not names in comments)
  for (const m of text.matchAll(/^\s*(?:public|protected|default)\s[^;=(]*?\b((?:set|get|is|add|on|remove)[A-Z]\w*)\s*\(/gm)) takenNames.add(m[1]);
}
// types those sources nest (Component.Popover...): a component of that name says its own fully
const takenTypes = new Set<string>();
for (const file of config.taken) {
  for (const m of readFileSync(at(file), 'utf8').matchAll(/\b(?:enum|interface|class|record)\s+([A-Z]\w*)/g)) takenTypes.add(m[1]);
}
const JAVA_WORDS = new Set(['default', 'class', 'new', 'switch', 'case', 'public', 'private', 'static', 'final', 'int', 'long',
  'double', 'float', 'boolean', 'char', 'byte', 'short', 'void', 'null', 'true', 'false', 'enum', 'interface', 'package']);
const OBJECT_METHODS = new Set(['notify', 'notifyAll', 'wait', 'hashCode', 'toString', 'getClass', 'clone', 'finalize', 'equals']);
const pascal = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);
const constant = (word: string) => {
  let c = word.replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/[^A-Za-z0-9]+/g, '_').replace(/^_|_$/g, '').toUpperCase();
  if (!c) c = 'NONE';
  if (/^[0-9]/.test(c)) c = '_' + c;
  return c;
};
const quote = (s: string) => JSON.stringify(s);
const doc = (s: string) => s.replace(/\*\//g, '*&#47;').replace(/@/g, '&#64;').replace(/\s+/g, ' ').trim();

// ---- TypeScript's view of the library ----

const parsed = ts.getParsedCommandLineOfConfigFile(at(config.tsconfig), {}, {
  ...ts.sys, onUnRecoverableConfigFileDiagnostic: (d) => { throw new Error(ts.flattenDiagnosticMessageText(d.messageText, '\n')); },
} as ts.ParseConfigFileHost)!;
const folder = at(config.components);
const walk = (dir: string): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)]));
const files = walk(folder)
  .filter((f) => f.endsWith('.tsx') && !f.endsWith('.stories.tsx') && !(config.exclude ?? []).some((x) => f.includes(x)))
  .sort();

/** A package segment from a file or folder name: alert-dialog -> alertdialog; a Java keyword gets an underscore (switch_). */
const segment = (name: string) => {
  let s = name.toLowerCase().replace(/[^a-z0-9]/g, '');
  if (/^[0-9]/.test(s)) s = '_' + s;
  return JAVA_WORDS.has(s) || s === 'native' || s === 'goto' || s === 'const' || s === 'this' ? s + '_' : s;
};
/** The package of a component file's classes: the base, its folders, its family (button-group: button). */
const packageOf = (file: string) => {
  const rel = file.slice(folder.length + 1).replace(/\\/g, '/').replace(/\.tsx$/, '').split('/');
  // a folder is a family too: chat/chat-window.tsx and chat/chat-header.tsx are both ...chat
  if (rel.length > 1 && !config.packages?.[rel[rel.length - 1]]) return [config.package, ...rel.slice(0, -1).map(segment)].join('.');
  rel[rel.length - 1] = family(file, rel[rel.length - 1]);
  return [config.package, ...rel.map(segment)].join('.');
};
/**
 * A component file's family: <family>-<more>.tsx goes with <family>.tsx when that is beside it (button-group with
 * button, alert-dialog with alert, input-otp with input), the longest such family; else its own name. "packages" in
 * the config gives a file a package of its own instead ({"alert-dialog": "alertdialog"}).
 */
function family(file: string, stem: string): string {
  const own = config.packages?.[stem];
  if (own) return own;
  let best = stem;
  for (const other of readdirSync(dirname(file))) {
    if (!other.endsWith('.tsx')) continue;
    const name = other.slice(0, -'.tsx'.length);
    if (name !== stem && stem.startsWith(name + '-') && (best === stem || name.length > best.length)) best = name;
  }
  return best;
}
const program = ts.createProgram(files, parsed.options);
const checker = program.getTypeChecker();

// ---- what a TypeScript type is in Java ----

/**
 * A prop's type, for Java: a boolean, a number, text, one of some words (an enum), a list of one of these, a record of
 * named ones (an object of data: { value: string; label: string }), any record of the app's (an object of any fields:
 * a table's row), or one of several of these (number | { top, left }: a setter for each).
 */
type JType =
  | { k: 'boolean' }
  | { k: 'number' }
  | { k: 'text' }
  | { k: 'words'; name: string; words: string[] }
  | { k: 'list'; of: JType }
  | { k: 'record'; name: string; fields: Field[] }
  | { k: 'anyRecord' }
  | { k: 'either'; options: JType[] }
  // what JSON cannot carry, which the base (ReactElement) carries: a day (LocalDate, and an Instant overload), bytes
  // (byte[], and a Path overload: served by the app, an ArrayBuffer on the page), a style (css.Style: React's style)
  | { k: 'date' }
  | { k: 'instant' }
  | { k: 'bytes' }
  | { k: 'path' }
  | { k: 'style' };

const symbolName = (t: ts.Type) => (t.aliasSymbol ?? t.symbol)?.getName() ?? '';
const BYTE_TYPES = new Set(['ArrayBuffer', 'Uint8Array', 'ArrayBufferLike', 'Blob', 'SharedArrayBuffer']);
type Field = { name: string; type: JType; doc: string };

type Prop =
  | {
      kind: 'value';
      name: string;
      doc: string;
      type: JType;
      /** the other kinds it may be, each its own setter (setPadding(double), setPadding(Insets)) */
      also: JType[];
      /** also a component (a ReactNode: setIcon(Node) beside setIcon(String)) */
      node: boolean;
    }
  | { kind: 'node'; name: string; doc: string }
  /** an object of functions the component calls (a chat's adapter): answered by a ReactAdapter in Java */
  | { kind: 'adapter'; name: string; doc: string; methods: string[]; events: string[] }
  /** a ref the component puts its actions in (actionsRef): each action a Java method */
  | { kind: 'handle'; name: string; doc: string; actions: { name: string; params: JType[] }[] }
  /** an item's menu ((row) => entries): made in Java for each item it gave */
  | { kind: 'menu'; name: string; doc: string; item: JType }
  | { kind: 'event'; name: string; doc: string; payload: JType | null };

/** Whether the props' {@code name} is React's DOM attribute (not the component's own). */
const isDomName = (name: string, type: ts.Type) => { const s = type.getProperty(name); return !!s && isDom(s); };
const isDom = (s: ts.Symbol) => (s.declarations ?? []).every((d) => /[\\/]@types[\\/]react[\\/]/.test(d.getSourceFile().fileName));
/** TypeScript's own wrappers of a type (Partial<Query>): named after what they wrap. */
const WRAPPERS = new Set(['Partial', 'Required', 'Readonly', 'Pick', 'Omit', 'NonNullable']);
/** Objects that are not data a component is given as JSON. */
const NOT_DATA = new Set(['Date', 'ArrayBuffer', 'Uint8Array', 'Blob', 'File', 'Map', 'Set', 'WeakMap', 'Promise', 'RegExp',
  'Error', 'Function', 'HTMLElement', 'Element', 'Node', 'Window', 'Document', 'Event']);
/** Declared by React or the browser (an element, an event, a CSS object): never a record of the app's data. */
const isPlatform = (t: ts.Type) => {
  const symbol = t.symbol ?? t.aliasSymbol;
  if (NOT_DATA.has(symbol?.getName() ?? '')) return true;
  const decls = symbol?.declarations ?? [];
  return decls.length > 0 && decls.every((d) => /[\\/]@types[\\/]react|[\\/]lib\.dom|[\\/]lib\.webworker/.test(d.getSourceFile().fileName));
};
/** A record's Java name: its type's own, what a wrapper wraps (Partial<CollectionQuery>: CollectionQuery), or the prop's. */
const recordName = (t: ts.Type, hint: string) => {
  const alias = t.aliasSymbol?.getName();
  if (alias && WRAPPERS.has(alias)) {
    const wrapped = t.aliasTypeArguments?.[0];
    const n = (wrapped?.aliasSymbol ?? wrapped?.symbol)?.getName();
    if (n && !n.startsWith('__')) return n;
  }
  for (const n of [alias, t.symbol?.getName()]) if (n && !n.startsWith('__') && !WRAPPERS.has(n)) return n;
  return pascal(hint);
};

function parts(t: ts.Type): ts.Type[] {
  return t.isUnion() ? t.types : [t];
}

const present = (t: ts.Type) =>
  parts(checker.getNonNullableType(t)).filter((x) => !(x.flags & (ts.TypeFlags.Undefined | ts.TypeFlags.Null | ts.TypeFlags.Void)));
const isFunction = (t: ts.Type) => t.getCallSignatures().length > 0;
/** React's nodes: an element, a portal, ReactNode itself (text is ReactNode too: that part is text). */
const isNode = (t: ts.Type) => /^(ReactElement|ReactPortal|ReactNode|Element|JSXElementConstructor)$/.test((t.aliasSymbol ?? t.symbol)?.getName() ?? '')
  || /ReactElement|ReactPortal|ReactNode/.test(checker.typeToString(t));
const isTextLike = (t: ts.Type) =>
  !!(t.flags & (ts.TypeFlags.String | ts.TypeFlags.TemplateLiteral | ts.TypeFlags.Any | ts.TypeFlags.Unknown | ts.TypeFlags.TypeParameter))
  || (t.isIntersection() && t.types.some((x) => !!(x.flags & ts.TypeFlags.String)));   // string & {} (any text, with hints)
const singular = (s: string) => s.replace(/ies$/, 'y').replace(/(s|x|ch|sh)es$/, '$1').replace(/s$/, '');

/** What {@code type} is in Java; null when it is not data (a function, an element, a class instance...). */
function mapType(type: ts.Type, hint: string, depth = 0, seen = new Set<ts.Type>()): JType | null {
  if (depth > 4) return null;
  // what of it Java can give: not its functions (number | OffsetFunction: a number), not its React nodes
  const all = present(type).filter((t) => !isFunction(t) && !isNode(t));
  if (!all.length) return null;
  if (all.every((t) => BYTE_TYPES.has(symbolName(t)))) return { k: 'bytes' };
  if (all.every((t) => symbolName(t) === 'Date')) return { k: 'date' };
  if (all.length === 1 && symbolName(all[0]) === 'CSSProperties') return { k: 'style' };
  if (all.every((t) => t.flags & ts.TypeFlags.BooleanLike)) return { k: 'boolean' };
  const literals = all.filter((t) => t.isStringLiteral()) as ts.StringLiteralType[];
  if (literals.length && literals.length === all.length) return { k: 'words', name: pascal(hint), words: literals.map((l) => l.value) };
  if (all.some(isTextLike)) return { k: 'text' };   // text, or anything (Base UI's values, a T): text from Java
  if (all.every((t) => t.flags & ts.TypeFlags.NumberLike)) return { k: 'number' };
  if (all.length === 1) return mapObject(all[0], hint, depth, seen);
  // several kinds: one setter for each Java can give (number | "fit-width" | "fit-page": a number, or the words)
  const options: JType[] = [];
  if (all.some((t) => t.flags & ts.TypeFlags.BooleanLike)) options.push({ k: 'boolean' });
  if (all.some((t) => t.flags & ts.TypeFlags.NumberLike)) options.push({ k: 'number' });
  if (literals.length) options.push({ k: 'words', name: pascal(hint), words: literals.map((l) => l.value) });
  if (all.some((t) => symbolName(t) === 'Date')) options.push({ k: 'date' });   // Date | string: a day, or text
  const objects = all.filter((t) => !(t.flags & (ts.TypeFlags.BooleanLike | ts.TypeFlags.NumberLike | ts.TypeFlags.StringLiteral))
    && symbolName(t) !== 'Date');
  if (objects.length === 1) {
    // the object of a named choice (Insets = number | { top...}) takes the choice's name
    const o = mapObject(objects[0], type.aliasSymbol?.getName() ?? hint, depth, seen);
    if (o) options.push(o);
  }
  if (!options.length) return null;
  return options.length === 1 ? options[0] : { k: 'either', options };
}

/** An object type: a list, a record of fields, any record (a row of any fields); null when not data. */
function mapObject(t: ts.Type, hint: string, depth: number, seen: Set<ts.Type>): JType | null {
  if (checker.isArrayType(t) || checker.isTupleType(t)) {
    const element = checker.getTypeArguments(t as ts.TypeReference)[0];
    const of = element && mapType(element, singular(hint), depth + 1, seen);
    return of ? { k: 'list', of } : null;
  }
  if (!(t.flags & ts.TypeFlags.Object) || isPlatform(t) || seen.has(t)) return null;
  const properties = checker.getPropertiesOfType(t);
  if (properties.length > 40) return null;   // a bag of attributes (an SVG element's, a chart's), not data of the app's
  // Record<string, unknown>: any fields at all (a table's row): the app's own records
  if (!properties.length && checker.getIndexInfosOfType(t).some((i) => !!(i.keyType.flags & ts.TypeFlags.String))) return { k: 'anyRecord' };
  const inner = new Set(seen).add(t);
  const fields: Field[] = [];
  for (const s of properties) {
    const n = s.getName();
    // a Java name, as JSON writes it; not one of Object's methods (a record component cannot be: notify, wait...)
    if (!/^[a-z][A-Za-z0-9]*$/.test(n) || JAVA_WORDS.has(n) || OBJECT_METHODS.has(n)) continue;
    const decl = s.valueDeclaration ?? s.declarations?.[0];
    const ft = decl ? checker.getTypeOfSymbolAtLocation(s, decl) : checker.getTypeOfSymbol(s);
    const f = mapType(ft, n, depth + 1, inner);
    // a field that may be several kinds holds its first (a record has one type per field)
    if (f) fields.push({ name: n, type: f.k === 'either' ? f.options[0] : f, doc: doc(ts.displayPartsToString(s.getDocumentationComment(checker))) });
  }
  if (!fields.length) return null;
  return { k: 'record', name: recordName(t, hint), fields };
}

/** The functions of an object a component calls (an adapter), by path; null when it has no Promise of an answer. */
function adapterOf(type: ts.Type): { methods: string[]; events: string[] } | null {
  const methods: string[] = [];
  const events: string[] = [];
  let promises = 0;
  let others = 0;
  const walk = (t: ts.Type, prefix: string, depth: number) => {
    if (depth > 3) return;
    for (const s of checker.getPropertiesOfType(t)) {
      const decl = s.valueDeclaration ?? s.declarations?.[0];
      const ft = checker.getNonNullableType(decl ? checker.getTypeOfSymbolAtLocation(s, decl) : checker.getTypeOfSymbol(s));
      const sig = ft.getCallSignatures()[0];
      const path = prefix + s.getName();
      if (sig) {
        const first = sig.getParameters()[0];
        const firstType = first && checker.getNonNullableType(checker.getTypeOfSymbol(first));
        if (firstType && firstType.getCallSignatures().length) events.push(path);   // subscribe(listener)
        else methods.push(path);
        if (symbolName(checker.getNonNullableType(sig.getReturnType())) === 'Promise') promises++;
        else if (!(firstType && firstType.getCallSignatures().length)) others++;   // answers at once: not a port's
      } else if (ft.flags & ts.TypeFlags.Object && !checker.isArrayType(ft) && !isPlatform(ft)) {
        walk(ft, path + '.', depth + 1);
      }
    }
  };
  const all = present(type);
  if (all.length !== 1 || !(all[0].flags & ts.TypeFlags.Object) || isFunction(all[0]) || isPlatform(all[0])) return null;
  walk(all[0], '', 0);
  // a port: every function answers later (a Promise) or listens (subscribe); a config of renderers is not one
  return promises > 0 && others === 0 ? { methods, events } : null;
}

/** A ref's actions (actionsRef: RefObject<DialogRootActions>): each method's simple parameters; null when not one. */
function handleOf(type: ts.Type): { name: string; params: JType[] }[] | null {
  const ref = present(type).find((t) => /^(RefObject|MutableRefObject|Ref)$/.test(symbolName(t)));
  if (!ref) return null;
  const target = (checker.getTypeArguments(ref as ts.TypeReference)[0] ?? (ref.aliasTypeArguments ?? [])[0]);
  if (!target) return null;
  const actual = present(target).find((t) => !!(t.flags & ts.TypeFlags.Object));
  if (!actual || isPlatform(actual)) return null;
  const actions: { name: string; params: JType[] }[] = [];
  for (const s of checker.getPropertiesOfType(actual)) {
    const decl = s.valueDeclaration ?? s.declarations?.[0];
    const ft = decl ? checker.getTypeOfSymbolAtLocation(s, decl) : checker.getTypeOfSymbol(s);
    const sig = checker.getNonNullableType(ft).getCallSignatures()[0];
    if (!sig || !/^[a-z][A-Za-z0-9]*$/.test(s.getName())) continue;
    const params: JType[] = [];
    let ok = true;
    for (const p of sig.getParameters()) {
      const pt = mapType(checker.getTypeOfSymbol(p), p.getName());
      if (!pt || pt.k === 'either' || pt.k === 'list' || pt.k === 'anyRecord') { ok = false; break; }
      params.push(pt);
    }
    if (ok) actions.push({ name: s.getName(), params });
  }
  return actions.length ? actions : null;
}

/** An item's menu ((row: Row) => MenuEntry[]): the item's type; null when not a menu. */
function menuOf(name: string, type: ts.Type): JType | null {
  if (/^on[A-Z]/.test(name)) return null;
  const sig = present(type).flatMap((t) => t.getCallSignatures())[0];
  if (!sig) return null;
  const ret = checker.getNonNullableType(sig.getReturnType());
  const elementOf = (t: ts.Type) => (checker.isArrayType(t) ? checker.getTypeArguments(t as ts.TypeReference)[0] : undefined);
  const element = elementOf(ret) ?? present(ret).map(elementOf).find((e) => e);
  if (!element) return null;
  const kinds = present(element);
  const entries = kinds.some((t) => t.isStringLiteral() && (t as ts.StringLiteralType).value === 'separator')
    || kinds.some((t) => !!t.getProperty('label') && !!t.getProperty('onSelect'));
  if (!entries) return null;
  for (const p of sig.getParameters()) {
    const item = mapType(checker.getTypeOfSymbol(p), p.getName());
    if (item && (item.k === 'record' || item.k === 'anyRecord')) return item;
  }
  return null;
}

/** What a prop is, for Java; null when Java cannot give it. */
function classify(name: string, type: ts.Type): Prop | null {
  const d = '';
  const all = present(type);
  if (!all.length) return null;
  const menu = menuOf(name, type);
  if (menu) return { kind: 'menu', name, doc: d, item: menu };
  const actions = handleOf(type);
  if (actions) return { kind: 'handle', name, doc: d, actions };
  const adapter = adapterOf(type);
  if (adapter) return { kind: 'adapter', name, doc: d, ...adapter };
  if (/^on[A-Z]/.test(name)) {
    const sig = all.flatMap((t) => t.getCallSignatures())[0];
    if (!sig) return null;
    const first = sig.getParameters()[0];
    let payload: JType | null = null;
    if (first) {
      const p = mapType(checker.getTypeOfSymbol(first), name.slice(2) + 'Value');
      // a DOM event is not data: its element's value comes as a ReactEvent
      payload = p && p.k !== 'list' && p.k !== 'either' && p.k !== 'anyRecord' ? p : null;
    }
    return { kind: 'event', name, doc: d, payload };
  }
  const node = all.some(isNode);
  const mapped = mapType(type, name);
  if (!mapped) return node ? { kind: 'node', name, doc: d } : null;
  const [first, ...also] = mapped.k === 'either' ? mapped.options : [mapped];
  // a day may be given as a moment too; bytes as a file
  if (first.k === 'date' || also.some((o) => o.k === 'date')) also.push({ k: 'instant' });
  if (first.k === 'bytes') also.push({ k: 'path' });
  return { kind: 'value', name, doc: d, type: first, also, node };
}

type Component = { name: string; file: string; pkg: string; props: Prop[]; children: boolean; binds: [string, string][]; field: boolean };

function component(name: string, decl: ts.FunctionDeclaration, file: string): Component | null {
  const param = decl.parameters[0];
  if (!param) return { name, file, pkg: '', props: [], children: false, binds: [], field: false };
  const type = checker.getTypeAtLocation(param);
  const props: Prop[] = [];
  let children = false;
  // HTML's attributes give every element children: not an input's, a textarea's (React refuses them)
  const shape = checker.typeToString(type, undefined, ts.TypeFormatFlags.NoTruncation);
  // an input's own (value and onChange, the person's): its props are React's for <input> or <textarea>, whatever its
  // props' type is called (SwitchProps = Omit<ComponentProps<"input">, ...> & {...})
  const fieldProp = (n: string) => (type.getProperty(n)?.declarations ?? [])
    .some((d) => /^(Input|Textarea)HTMLAttributes$/.test(((d.parent as ts.InterfaceDeclaration | undefined)?.name?.getText()) ?? ''));
  const field = /"(input|textarea)"|HTML(Input|TextArea)Element|(Input|Textarea)HTMLAttributes/.test(shape)
    || fieldProp('value') || fieldProp('checked') || fieldProp('placeholder');
  for (const s of checker.getPropertiesOfType(type)) {
    const n = s.getName();
    if (n === 'children') { children = true; continue; }
    if (NEVER.has(n) || n.startsWith('aria-') || n.startsWith('data-')) continue;
    if (isDom(s) && (!DOM_KEPT.has(n) || (FIELD_ONLY.has(n) && !field))) continue;
    const p = classify(n, checker.getTypeOfSymbolAtLocation(s, param));
    if (!p) {
      if (process.env.JAVA_GEN_SKIPPED) {
        const t = checker.typeToString(checker.getNonNullableType(checker.getTypeOfSymbolAtLocation(s, param)), undefined, ts.TypeFormatFlags.NoTruncation);
        console.error(`SKIPPED\t${name}\t${n}\t${t.slice(0, 160)}`);
      }
      continue;
    }
    p.doc = doc(ts.displayPartsToString(s.getDocumentationComment(checker)));
    props.push(p);
  }
  if (field || /"(img|hr|br)"|HTML(Image|HR|BR)Element/.test(shape) || (config.noChildren ?? []).includes(name)) children = false;
  const names = new Set(props.map((p) => p.name));
  const binds: [string, string][] = [];
  for (const [prop, event] of Object.entries(BOUND)) if (names.has(prop) && names.has(event)) binds.push([prop, event]);
  // an input's change: a checkbox-like one's is checked (a switch, a checkbox, a radio), any other's its text
  const ticks = /check|switch|radio|toggle/i.test(name);
  if (field && ticks && names.has('checked') && names.has('onChange') && !binds.some(([p]) => p === 'checked')) binds.push(['checked', 'onChange']);
  else if (field && names.has('value') && names.has('onChange') && !binds.some(([p]) => p === 'value')) binds.push(['value', 'onChange']);
  return { name, file, pkg: '', props, children, binds, field };
}

const found: Component[] = [];
for (const file of files) {
  const sf = program.getSourceFile(file)!;
  const exported = new Set<string>();
  sf.forEachChild((n) => {
    if (ts.isExportDeclaration(n) && n.exportClause && ts.isNamedExports(n.exportClause)) {
      for (const e of n.exportClause.elements) exported.add(e.name.text);
    }
  });
  sf.forEachChild((n) => {
    if (ts.isFunctionDeclaration(n) && n.name && /^[A-Z]/.test(n.name.text)) {
      const exportedHere = n.modifiers?.some((m) => m.kind === ts.SyntaxKind.ExportKeyword);
      if (exportedHere || exported.has(n.name.text)) {
        const c = component(n.name.text, n, basename(file));
        if (c) found.push({ ...c, pkg: packageOf(file) });
      }
    }
  });
}

// ---- the Java ----

const out = join(at(config.out), ...config.package.split('.'));
mkdirSync(out, { recursive: true });
// what the last run made (its classes say so) goes: a component taken out of the library goes from Java too
for (const f of walk(out)) {
  if (f.endsWith('.java') && readFileSync(f, 'utf8').includes('Generated by swaswa-react java-gen')) rmSync(f);
}

/**
 * The roles every library's controls share (io.joshuasalcedo.swaswa.component), after HTML's own elements, by the
 * component's name: a component of the kind is one of them, whichever library drew it. Toggle, TextField, TextArea
 * and Choice go by what it does instead (a live checked; a field's live value; another live value).
 */
const NAME_ROLES: [string, RegExp][] = [
  ['ButtonBase', /Button$/],                                                            // <button>
  ['Labeled', /^(Label|FieldLabel)$/],                                                  // <label>
  ['ChoiceOption', /^(SelectItem|NativeSelectOption|ComboboxItem|CommandItem|RadioGroupItem)$/],   // <option>
  ['ComboBox', /^(Combobox|TokenField|CommandPalette)$/],                               // <datalist>
  ['DataValue', /^(Money|Quantity|CellValue)$/],                                        // <data>
  ['DialogBase', /^(Dialog|AlertDialog|CommandDialog|Sheet|Drawer)$/],                  // <dialog>
  ['Disclosure', /^(Collapsible|Accordion|AccordionItem|TraceCollapsible|SidebarMenuCollapsible|TitledPane)$/],   // <details>
  ['ProgressIndicator', /^(Progress|ProgressBar|Spinner|LoadingBar|PasswordStrength)$/],   // <progress>, <meter>
  ['TableView', /^(Table|DataTable|DataGrid)$/],                                        // <table>
  ['MenuBase', /^(DropdownMenu|ContextMenu|Menubar)$/],                                 // <menu>
  ['Navigation', /^(NavigationMenu|Breadcrumb|Pagination|Sidebar)$/],                   // <nav>
  ['Separator', /Separator$/],                                                          // <hr>
  ['ImageView', /^(Avatar|AvatarImage|FileThumbnail|PdfThumbnail)$/],                   // <img>
  ['KeyboardKey', /^Kbd$|Shortcut$/],                                                   // <kbd>
  ['CodeView', /^(Code|CodeBlock|CodeEditor)$/],                                        // <code>, <pre>
  ['ListView', /^(List|DataList|ItemGroup|ConversationList|MessageList|FileList)$/],    // <ul>, <ol>
  ['ListItem', /^(ListItem|Item)$/],                                                    // <li>
  ['Hyperlink', /^(Link|BreadcrumbLink|PaginationLink|NavigationMenuLink)$/],           // <a>
  ['FormBase', /^(SignInForm|Questionnaire)$/],                                         // <form>
  ['FieldGroup', /^FieldSet$/],                                                         // <fieldset>
  ['MediaView', /^MediaPlayer$/],                                                       // <audio>, <video>
];

// names a nested type must not take: what the class imports or Java has
const RESERVED_TYPES = new Set(['List', 'Node', 'Text', 'Property', 'PageScript', 'ReactElement', 'ReactListener', 'HtmlLiteral',
  'Consumer', 'String', 'Double', 'Boolean', 'Object', 'Map', 'Record', 'Override', 'Integer', 'Long', 'Class', 'Enum', 'Void']);

function java(c: Component): string {
  const cls = (config.classPrefix ?? '') + c.name;   // its Java name: ShadcnAlert for Alert
  // its own name, where an inherited nested type of that name would be meant instead (Component.Popover)
  const self = takenTypes.has(cls) ? `${c.pkg}.${cls}` : cls;
  const L: string[] = [];
  const takenHere = new Set(takenNames);
  const method = (prefix: string, prop: string) => {
    let m = prefix + pascal(prop);
    if (takenHere.has(m) || JAVA_WORDS.has(m)) m = prefix + pascal(prop) + 'Prop';
    takenHere.add(m);
    return m;
  };
  const imports = new Set(['io.joshuasalcedo.swaswa.html.PageScript', 'io.joshuasalcedo.swaswa.react.ReactElement', 'java.util.List',
    `${config.package}.${config.library}`]);
  const bound = new Set(c.binds.map(([p]) => p));

  // ---- its nested types: an enum per set of words, a record per object of data, each named once ----
  const nested: string[] = [];
  const typeNames = new Map<string, string>();   // a type's shape -> its Java name
  const usedNames = new Set<string>([cls, ...takenTypes, ...RESERVED_TYPES]);
  const shapeOf = (t: JType): string => JSON.stringify(t);
  /** The Java type of {@code t}, declaring the enums and records it needs; boxed: in a list, a record, a property. */
  const jt = (t: JType, boxed = false): string => {
    switch (t.k) {
      case 'boolean': return boxed ? 'Boolean' : 'boolean';
      case 'number': return boxed ? 'Double' : 'double';
      case 'text': return 'String';
      case 'list': return t.of.k === 'anyRecord' ? 'List<? extends Record>' : `List<${jt(t.of, true)}>`;
      case 'anyRecord': return 'Record';
      case 'either': return jt(t.options[0], boxed);
      case 'date': imports.add('java.time.LocalDate'); return 'LocalDate';
      case 'instant': imports.add('java.time.Instant'); return 'Instant';
      case 'bytes': return 'byte[]';
      case 'path': imports.add('java.nio.file.Path'); return 'Path';
      case 'style': imports.add('io.joshuasalcedo.swaswa.css.Style'); return 'Style';
      case 'words':
      case 'record': {
        const shape = shapeOf(t);
        const known = typeNames.get(shape);
        if (known) return known;
        let name = pascal(t.name.replace(/[^A-Za-z0-9]/g, '')) || 'Value';
        if (/^[0-9]/.test(name)) name = 'V' + name;
        for (let i = 2; usedNames.has(name); i++) name = pascal(t.name.replace(/[^A-Za-z0-9]/g, '')) + i;
        usedNames.add(name);
        typeNames.set(shape, name);
        if (t.k === 'words') {
          imports.add('io.joshuasalcedo.swaswa.html.HtmlLiteral');
          const seen = new Map<string, string>();
          for (const w of t.words) if (!seen.has(constant(w))) seen.set(constant(w), w);
          nested.push('');
          nested.push(`    /** {@code ${t.name}}'s words. */`);
          nested.push(`    public enum ${name} implements HtmlLiteral {`);
          nested.push([...seen].map(([k, w]) => `        ${k}(${quote(w)})`).join(',\n') + ';');
          nested.push('');
          nested.push('        private final String literal;');
          nested.push(`        ${name}(String literal) { this.literal = literal; }`);
          nested.push('        @Override public String literal() { return literal; }');
          nested.push('    }');
        } else {
          const fields = t.fields.map((f) => `${jt(f.type, true)} ${f.name}`);
          const docs = t.fields.filter((f) => f.doc).map((f) => `     * @param ${f.name} ${f.doc}`);
          nested.push('');
          nested.push('    /**');
          nested.push(`     * {@code ${t.name}}: its fields as the component reads them (null: left out).`);
          if (docs.length) nested.push('     *', ...docs);
          nested.push('     */');
          nested.push(`    public record ${name}(${fields.join(', ')}) {}`);
        }
        return name;
      }
    }
  };

  L.push(`    /** A ${cls}. */`);
  L.push(`    public ${cls}() {`);
  L.push(`        super(${quote(c.name)}, ${c.children});`);   // the page's component: its own name
  for (const [p, e] of c.binds) L.push(`        bind(${quote(p)}, ${quote(e)});`);
  for (const p of c.props) if (p.kind === 'handle') L.push(`        handleProp(${quote(p.name)});`);   // its actions, callable any time
  // controlled from the start (a component chooses controlled or not once, when it is first drawn): a live
  // boolean starts false, an input's text empty; a live value of anything else is Java's to give
  for (const [p] of c.binds) {
    const prop = c.props.find((x) => x.name === p);
    const k = prop?.kind === 'value' ? prop.type.k : null;
    if (k === 'boolean') L.push(`        prop(${quote(p)}, false);`);
    else if (c.field && p === 'value') L.push(`        prop(${quote(p)}, "");`);
  }
  L.push('    }');
  if (c.children) {
    imports.add('io.joshuasalcedo.swaswa.html.Node');
    imports.add('io.joshuasalcedo.swaswa.html.Text');
    L.push('');
    L.push(`    /** A ${cls} showing {@code text}. */`);
    L.push(`    public ${cls}(String text) {`);
    L.push('        this();');
    L.push('        add(new Text(text));');
    L.push('    }');
    L.push('');
    L.push(`    /** A ${cls} holding {@code children}: its children, where it shows them. */`);
    L.push(`    public ${cls}(Node... children) {`);
    L.push('        this();');
    L.push('        for (Node child : children) add(child);');
    L.push('    }');
  }

  for (const p of c.props) {
    const about = p.doc ? ` ${p.doc}` : '';
    L.push('');
    if (p.kind === 'event') {
      const m = method('on', p.name.slice(2));
      if (p.payload) {
        imports.add('java.util.function.Consumer');
        const T = jt(p.payload, true);
        L.push(`    /** Told when it calls {@code ${p.name}}, with what it calls it with.${about} */`);
        L.push(`    public ${self} ${m}(Consumer<${T}> listener) {`);
        L.push(`        return onEvent(${quote(p.name)}, e -> listener.accept(e.payload(${T}.class)));`);
        L.push('    }');
      } else {
        imports.add('io.joshuasalcedo.swaswa.react.ReactListener');
        L.push(`    /** Told when it calls {@code ${p.name}}.${about} */`);
        L.push(`    public ${self} ${m}(ReactListener listener) {`);
        L.push(`        return onEvent(${quote(p.name)}, listener);`);
        L.push('    }');
      }
      continue;
    }
    if (p.kind === 'adapter') {
      imports.add('io.joshuasalcedo.swaswa.react.ReactAdapter');
      const setter = method('set', p.name);
      L.push(`    /** {@code ${p.name}}: what the component calls ({@code ${p.methods.join('}, {@code ')}}), answered by {@code adapter}${p.events.length ? `; what it listens with ({@code ${p.events.join('}, {@code ')}})` : ''}.${about} */`);
      L.push(`    public ${self} ${setter}(ReactAdapter adapter) {`);
      L.push(`        return adapter(${quote(p.name)}, List.of(${p.methods.map(quote).join(', ')}), List.of(${p.events.map(quote).join(', ')}), adapter);`);
      L.push('    }');
      continue;
    }
    if (p.kind === 'menu') {
      imports.add('io.joshuasalcedo.swaswa.react.MenuItem');
      imports.add('java.util.function.Function');
      const setter = method('set', p.name);
      L.push(`    /** {@code ${p.name}}: each item's menu, made in Java for each item it gave; a chosen entry's action runs in Java.${about} */`);
      if (p.item.k === 'anyRecord') {
        L.push(`    public <R extends Record> ${self} ${setter}(Class<R> type, Function<R, List<MenuItem>> menu) {`);
        L.push(`        return menu(${quote(p.name)}, type, menu);`);
      } else {
        const R = jt(p.item, true);
        L.push(`    public ${self} ${setter}(Function<${R}, List<MenuItem>> menu) {`);
        L.push(`        return menu(${quote(p.name)}, ${R}.class, menu);`);
      }
      L.push('    }');
      continue;
    }
    if (p.kind === 'handle') {
      imports.add('java.util.concurrent.CompletableFuture');
      p.actions.forEach((a, i) => {
        let m = a.name;
        if (takenHere.has(m) || JAVA_WORDS.has(m) || ['wait', 'notify', 'notifyAll', 'getClass', 'hashCode', 'equals', 'toString', 'clone', 'finalize'].includes(m)) m = a.name + 'Action';
        takenHere.add(m);
        const params = a.params.map((t, j) => `${jt(t)} a${j}`).join(', ');
        const args = a.params.map((_, j) => `, a${j}`).join('');
        if (i > 0) L.push('');
        L.push(`    /** Its {@code ${p.name}}'s {@code ${a.name}}: what it returns, when the page gives it. */`);
        L.push(`    public CompletableFuture<Object> ${m}(${params}) {`);
        L.push(`        return handle(${quote(p.name)}, ${quote(a.name)}${args});`);
        L.push('    }');
      });
      continue;
    }
    if (p.kind === 'node' || (p.kind === 'value' && p.node)) {
      // a component where the prop is drawn: setIcon(new UiIcon(...)), setActions(new Div(a, b))
      imports.add('io.joshuasalcedo.swaswa.html.Node');
      const setter = method('set', p.name);
      L.push(`    /** {@code ${p.name}}: {@code node} (a component, or several in one), where the component draws it.${about} */`);
      L.push(`    public ${self} ${setter}(Node node) {`);
      L.push(`        return slot(${quote(p.name)}, node);`);
      L.push('    }');
      takenHere.delete(setter);   // its value's setter overloads it
      if (p.kind === 'node') continue;
      L.push('');
    }
    const t = p.type;
    const J = jt(t);
    const boxed = jt(t, true);
    const setter = method('set', p.name);
    L.push(`    /** {@code ${p.name}}.${about} */`);
    L.push(`    public ${self} ${setter}(${J} value) {`);
    L.push(`        return prop(${quote(p.name)}, value);`);
    L.push('    }');
    for (const other of p.also) {
      // the same prop as another kind: its own overload (setPadding(double), setPadding(Insets))
      L.push('');
      L.push(`    /** {@code ${p.name}}, as ${other.k === 'record' ? 'its fields' : other.k === 'words' ? 'one of its words' : 'a ' + other.k}.${about} */`);
      L.push(`    public ${self} ${setter}(${jt(other)} value) {`);
      L.push(`        return prop(${quote(p.name)}, value);`);
      L.push('    }');
    }
    L.push('');
    if (t.k === 'words') {
      const getter = method('get', p.name);
      L.push(`    public ${J} ${getter}() {`);
      L.push(`        return getProp(${quote(p.name)}, ${J}.class);`);
      L.push('    }');
    } else if (t.k === 'boolean') {
      const getter = method('is', p.name);
      L.push(`    public boolean ${getter}() {`);
      L.push(`        return Boolean.TRUE.equals(getProp(${quote(p.name)}, Boolean.class));`);
      L.push('    }');
    } else if (!['list', 'anyRecord', 'date', 'bytes', 'style'].includes(t.k) && !p.also.length) {   // what Java gave: it has it already
      const getter = method('get', p.name);
      L.push(`    public ${boxed} ${getter}() {`);
      L.push(`        return getProp(${quote(p.name)}, ${boxed}.class);`);
      L.push('    }');
    }
    if (bound.has(p.name) && t.k !== 'list' && t.k !== 'anyRecord') {
      imports.add('io.joshuasalcedo.swaswa.component.Property');
      L.push('');
      L.push(`    /** {@code ${p.name}}, live: what the person sets it to, at once; set it, or bind a state to it. */`);
      L.push(`    public Property<${boxed}> ${p.name}Property() {`);
      L.push(`        return propProperty(${quote(p.name)}, ${boxed}.class);`);
      L.push('    }');
    }
  }
  // ---- its roles: what every library's control of its kind is (component.ButtonBase, Toggle, TextInput, Choice) ----
  const body = L.join('\n');
  const has = (signature: string) => body.includes(`    public ${self} ${signature} {`);
  const live = (prop: string) => body.includes(`    public Property<String> ${prop}Property() {`);
  const roles: string[] = [];
  for (const [role, names] of NAME_ROLES) if (names.test(c.name)) roles.push(role);
  const toggle = c.binds.map(([p]) => p).find((p) => (p === 'checked' || p === 'pressed') && body.includes(`    public Property<Boolean> ${p}Property() {`));
  if (toggle) {
    roles.push('Toggle');
    imports.add('io.joshuasalcedo.swaswa.component.Property');
    L.push('');
    L.push(`    /** On or off: its {@code ${toggle}}, live (the Toggle's selected). */`);
    L.push('    @Override');
    L.push('    public Property<Boolean> selectedProperty() {');
    L.push(`        return ${toggle}Property();`);
    L.push('    }');
  }
  if (live('value') && has('setValue(String value)')) {
    if (c.field && has('setPlaceholder(String value)')) roles.push(/Textarea$|TextArea$/.test(c.name) ? 'TextArea' : 'TextField');
    else if (!c.field) roles.push('Choice');
  }
  for (const r of roles) imports.add(`io.joshuasalcedo.swaswa.component.${r}`);
  const implementsRoles = roles.length ? ` implements ${roles.map((r) => `${r}<${self}>`).join(', ')}` : '';

  L.push('');
  L.push('    @Override');
  L.push('    protected List<PageScript> libraries() {');
  L.push(`        return List.of(${config.library}.bundle());`);
  L.push('    }');

  return `package ${c.pkg};

${[...imports].sort().map((i) => `import ${i};`).join('\n')}

/**
 * ${config.source}'s {@code ${c.name}} (${c.file}), a real React component, from Java: create it, set it, add it.
 * Generated by swaswa-react java-gen from its TypeScript props: do not edit.
 */
public final class ${cls} extends ReactElement<${self}>${implementsRoles} {
${nested.join('\n')}

${L.join('\n')}
}
`;
}

const packages = new Set<string>();
for (const c of found) {
  const dir = join(at(config.out), ...c.pkg.split('.'));
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, (config.classPrefix ?? '') + c.name + '.java'), java(c).replace(/\n{3,}/g, '\n\n'));
  packages.add(c.pkg);
}
console.log(`java-gen: ${found.length} components in ${packages.size} packages under ${out}`);

// a package no component is in any more (button-group's, now in button's) goes
const emptied = (dir: string): boolean => {
  let empty = true;
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    if (e.isDirectory() && emptied(join(dir, e.name))) rmSync(join(dir, e.name), { recursive: true });
    else empty = false;
  }
  return empty;
};
emptied(out);

// the module exports each package
if (config.moduleInfo) {
  const file = at(config.moduleInfo);
  const text = readFileSync(file, 'utf8');
  const start = text.indexOf('// java-gen: exports');
  const end = text.indexOf('// java-gen: end');
  if (start < 0 || end < start) throw new Error(file + ' has no "// java-gen: exports" ... "// java-gen: end" block');
  const indent = text.slice(text.lastIndexOf('\n', start) + 1, start);
  const lines = [...packages].sort().map((p) => `${indent}exports ${p};`).join('\n');
  writeFileSync(file, text.slice(0, start) + '// java-gen: exports (one package per component file)\n' + lines + '\n' + indent + text.slice(end));
}
for (const c of found) console.log(`  ${c.name}: ${c.props.length} props${c.children ? ', children' : ''}${c.binds.length ? ', live ' + c.binds.map(([p]) => p).join(' ') : ''}`);
