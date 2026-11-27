// Typed Java for a React component library: one class per exported component, from its TypeScript props.
//
//   bun java-gen.ts <config.json>
//
// config: {
//   "tsconfig": "ui/tsconfig.app.json",          the library's TypeScript project (paths relative to the config)
//   "components": "ui/src/components/ui",        the folder of its components (every .tsx; exported PascalCase functions)
//   "out": "../main/java",                        where the Java goes
//   "package": "io.joshuasalcedo.swaswa.shadcn",
//   "library": "Shadcn",                          a class of the package with bundle(): the library's PageScript
//   "taken": ["../../../swaswa-ffi/src/main/java/io/joshuasalcedo/swaswa/component/Component.java", ...],
//                                                 Java sources whose method names a prop must not take
//   "source": "shadcn/ui"                         named in the classes' docs
// }
//
// A prop becomes a typed setter (and getter) when its type is simple: text, a number, a boolean, one of some words (an
// enum), or a function called on... (an event: a listener of what it is called with). Props of HTML's own elements
// (React's DOM attributes) are left out but for a few every component wants (placeholder, type, name, value...).
// A value the person changes (value/onValueChange, checked/onCheckedChange, open/onOpenChange, an input's
// value/onChange) is kept in Java at once, and live as a property.
import ts from 'typescript';
import { dirname, join, resolve, basename } from 'node:path';
import { mkdirSync, readFileSync, readdirSync, writeFileSync, existsSync, rmSync } from 'node:fs';

type Config = {
  tsconfig: string; components: string; out: string; package: string; library: string; taken: string[]; source: string;
  noChildren?: string[];   // components that never take children (beyond those of input, textarea...)
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
const JAVA_WORDS = new Set(['default', 'class', 'new', 'switch', 'case', 'public', 'private', 'static', 'final', 'int', 'long',
  'double', 'float', 'boolean', 'char', 'byte', 'short', 'void', 'null', 'true', 'false', 'enum', 'interface', 'package']);
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
const files = readdirSync(folder).filter((f) => f.endsWith('.tsx')).sort().map((f) => join(folder, f));
const program = ts.createProgram(files, parsed.options);
const checker = program.getTypeChecker();

type Prop =
  | { kind: 'text' | 'number' | 'boolean'; name: string; doc: string }
  | { kind: 'words'; name: string; doc: string; words: string[] }
  | { kind: 'event'; name: string; doc: string; payload: 'Boolean' | 'String' | 'Double' | null };

/** Whether the props' {@code name} is React's DOM attribute (not the component's own). */
const isDomName = (name: string, type: ts.Type) => { const s = type.getProperty(name); return !!s && isDom(s); };
const isDom = (s: ts.Symbol) => (s.declarations ?? []).every((d) => /[\\/]@types[\\/]react[\\/]/.test(d.getSourceFile().fileName));

function parts(t: ts.Type): ts.Type[] {
  return t.isUnion() ? t.types : [t];
}

/** What a prop's type is, for Java; null when not simple. */
function classify(name: string, type: ts.Type): Prop | null {
  const d = '';
  const all = parts(checker.getNonNullableType(type)).filter((t) => !(t.flags & (ts.TypeFlags.Undefined | ts.TypeFlags.Null | ts.TypeFlags.Void)));
  if (!all.length) return null;
  if (/^on[A-Z]/.test(name)) {
    const sig = all.flatMap((t) => t.getCallSignatures())[0];
    if (!sig) return null;
    const first = sig.getParameters()[0];
    let payload: 'Boolean' | 'String' | 'Double' | null = null;
    if (first) {
      const p = parts(checker.getNonNullableType(checker.getTypeOfSymbol(first)));
      if (p.every((t) => t.flags & ts.TypeFlags.BooleanLike)) payload = 'Boolean';
      else if (p.every((t) => t.flags & ts.TypeFlags.StringLike)) payload = 'String';
      else if (p.every((t) => t.flags & ts.TypeFlags.NumberLike)) payload = 'Double';
    }
    return { kind: 'event', name, doc: d, payload };
  }
  if (all.every((t) => t.flags & ts.TypeFlags.BooleanLike)) return { kind: 'boolean', name, doc: d };
  const literals = all.filter((t) => t.isStringLiteral()) as ts.StringLiteralType[];
  if (literals.length && literals.length === all.length) return { kind: 'words', name, doc: d, words: literals.map((l) => l.value) };
  if (all.some((t) => t.flags & ts.TypeFlags.String) || all.some((t) => t.flags & (ts.TypeFlags.Any | ts.TypeFlags.Unknown))) {
    return { kind: 'text', name, doc: d };   // text, or anything (Base UI's values): text from Java
  }
  if (all.every((t) => t.flags & ts.TypeFlags.NumberLike)) return { kind: 'number', name, doc: d };
  return null;
}

type Component = { name: string; file: string; props: Prop[]; children: boolean; binds: [string, string][]; field: boolean };

function component(name: string, decl: ts.FunctionDeclaration, file: string): Component | null {
  const param = decl.parameters[0];
  if (!param) return { name, file, props: [], children: false, binds: [], field: false };
  const type = checker.getTypeAtLocation(param);
  const props: Prop[] = [];
  let children = false;
  // HTML's attributes give every element children: not an input's, a textarea's (React refuses them)
  const shape = checker.typeToString(type, undefined, ts.TypeFormatFlags.NoTruncation);
  const field = /"(input|textarea)"|HTML(Input|TextArea)Element|(Input|Textarea)HTMLAttributes/.test(shape);   // an input's own: value and onChange, the person's
  for (const s of checker.getPropertiesOfType(type)) {
    const n = s.getName();
    if (n === 'children') { children = true; continue; }
    if (NEVER.has(n) || n.startsWith('aria-') || n.startsWith('data-')) continue;
    if (isDom(s) && (!DOM_KEPT.has(n) || (FIELD_ONLY.has(n) && !field))) continue;
    const p = classify(n, checker.getTypeOfSymbolAtLocation(s, param));
    if (!p) continue;
    p.doc = doc(ts.displayPartsToString(s.getDocumentationComment(checker)));
    props.push(p);
  }
  if (field || /"(img|hr|br)"|HTML(Image|HR|BR)Element/.test(shape) || (config.noChildren ?? []).includes(name)) children = false;
  const names = new Set(props.map((p) => p.name));
  const binds: [string, string][] = [];
  for (const [prop, event] of Object.entries(BOUND)) if (names.has(prop) && names.has(event)) binds.push([prop, event]);
  if (field && names.has('value') && names.has('onChange') && !binds.some(([p]) => p === 'value')) binds.push(['value', 'onChange']);
  return { name, file, props, children, binds, field };
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
        if (c) found.push(c);
      }
    }
  });
}

// ---- the Java ----

const out = join(at(config.out), ...config.package.split('.'));
mkdirSync(out, { recursive: true });
// what the last run made (its classes say so) goes: a component taken out of the library goes from Java too
for (const f of readdirSync(out)) {
  if (f.endsWith('.java') && readFileSync(join(out, f), 'utf8').includes('Generated by swaswa-react java-gen')) rmSync(join(out, f));
}

function java(c: Component): string {
  const cls = c.name;
  const L: string[] = [];
  const takenHere = new Set(takenNames);
  const method = (prefix: string, prop: string) => {
    let m = prefix + pascal(prop);
    if (takenHere.has(m) || JAVA_WORDS.has(m)) m = prefix + pascal(prop) + 'Prop';
    takenHere.add(m);
    return m;
  };
  const imports = new Set(['io.joshuasalcedo.swaswa.html.PageScript', 'io.joshuasalcedo.swaswa.react.ReactElement', 'java.util.List']);
  const bound = new Set(c.binds.map(([p]) => p));

  L.push(`    /** A ${cls}. */`);
  L.push(`    public ${cls}() {`);
  L.push(`        super(${quote(cls)}, ${c.children});`);
  for (const [p, e] of c.binds) L.push(`        bind(${quote(p)}, ${quote(e)});`);
  // controlled from the start (a component chooses controlled or not once, when it is first drawn): a live
  // boolean starts false, an input's text empty; a live value of anything else is Java's to give
  for (const [p] of c.binds) {
    const kind = c.props.find((x) => x.name === p)?.kind;
    if (kind === 'boolean') L.push(`        prop(${quote(p)}, false);`);
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
        L.push(`    /** Told when it calls {@code ${p.name}}, with what it calls it with.${about} */`);
        L.push(`    public ${cls} ${m}(Consumer<${p.payload}> listener) {`);
        L.push(`        return onEvent(${quote(p.name)}, e -> listener.accept(e.payload(${p.payload}.class)));`);
        L.push('    }');
      } else {
        imports.add('io.joshuasalcedo.swaswa.react.ReactListener');
        L.push(`    /** Told when it calls {@code ${p.name}}.${about} */`);
        L.push(`    public ${cls} ${m}(ReactListener listener) {`);
        L.push(`        return onEvent(${quote(p.name)}, listener);`);
        L.push('    }');
      }
      continue;
    }
    const J = p.kind === 'text' ? 'String' : p.kind === 'number' ? 'double' : p.kind === 'boolean' ? 'boolean' : pascal(p.name);
    const boxed = J === 'double' ? 'Double' : J === 'boolean' ? 'Boolean' : J;
    if (p.kind === 'words') {
      imports.add('io.joshuasalcedo.swaswa.html.HtmlLiteral');
      L.push(`    /** {@code ${p.name}}'s words. */`);
      L.push(`    public enum ${J} implements HtmlLiteral {`);
      const seen = new Map<string, string>();
      for (const w of p.words) if (!seen.has(constant(w))) seen.set(constant(w), w);
      L.push([...seen].map(([k, w]) => `        ${k}(${quote(w)})`).join(',\n') + ';');
      L.push('');
      L.push('        private final String literal;');
      L.push(`        ${J}(String literal) { this.literal = literal; }`);
      L.push('        @Override public String literal() { return literal; }');
      L.push('    }');
      L.push('');
    }
    const setter = method('set', p.name);
    L.push(`    /** {@code ${p.name}}.${about} */`);
    L.push(`    public ${cls} ${setter}(${J} value) {`);
    L.push(`        return prop(${quote(p.name)}, value);`);
    L.push('    }');
    L.push('');
    if (p.kind === 'words') {
      const getter = method('get', p.name);
      L.push(`    public ${J} ${getter}() {`);
      L.push(`        String now = getProp(${quote(p.name)}, String.class);`);
      L.push(`        for (${J} w : ${J}.values()) if (w.literal().equals(now)) return w;`);
      L.push('        return null;');
      L.push('    }');
    } else if (p.kind === 'boolean') {
      const getter = method('is', p.name);
      L.push(`    public boolean ${getter}() {`);
      L.push(`        return Boolean.TRUE.equals(getProp(${quote(p.name)}, Boolean.class));`);
      L.push('    }');
    } else {
      const getter = method('get', p.name);
      L.push(`    public ${boxed} ${getter}() {`);
      L.push(`        return getProp(${quote(p.name)}, ${boxed}.class);`);
      L.push('    }');
    }
    if (bound.has(p.name)) {
      imports.add('io.joshuasalcedo.swaswa.component.Property');
      const pm = p.name + 'Property';
      L.push('');
      L.push(`    /** {@code ${p.name}}, live: what the person sets it to, at once; set it, or bind a state to it. */`);
      L.push(`    public Property<${boxed}> ${pm}() {`);
      L.push(`        return propProperty(${quote(p.name)}, ${boxed}.class);`);
      L.push('    }');
    }
  }
  L.push('');
  L.push('    @Override');
  L.push('    protected List<PageScript> libraries() {');
  L.push(`        return List.of(${config.library}.bundle());`);
  L.push('    }');

  return `package ${config.package};

${[...imports].sort().map((i) => `import ${i};`).join('\n')}

/**
 * ${config.source}'s {@code ${cls}} (${c.file}), a real React component, from Java: create it, set it, add it.
 * Generated by swaswa-react java-gen from its TypeScript props: do not edit.
 */
public final class ${cls} extends ReactElement<${cls}> {

${L.join('\n')}
}
`;
}

for (const c of found) writeFileSync(join(out, c.name + '.java'), java(c).replace(/\n{3,}/g, '\n\n'));
console.log(`java-gen: ${found.length} components into ${out}`);
for (const c of found) console.log(`  ${c.name}: ${c.props.length} props${c.children ? ', children' : ''}${c.binds.length ? ', live ' + c.binds.map(([p]) => p).join(' ') : ''}`);
