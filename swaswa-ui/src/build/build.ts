// bun build.ts: swaswa-ui's components (ui/src/components: desktop controls that look like the system's own) into
// the jar as files a page loads (ES modules, chunks loaded when needed, their CSS and WASM), and their typed Java
// classes into src/main/java (a package per component file). `just ui-build`.
//   1. an entry that registers every exported component with the page's React runtime, each inside a
//      NativeProvider unless one is around it already (the platform's look, light or dark, menus, dialogs)
//   2. vite: ui.js, chunks/, assets/, ui.css under target/generated-resources/swaswa-ui (Maven packs it; never committed)
//   3. swaswa-react's java-gen
import { join, relative } from 'node:path';
import { existsSync, readdirSync, writeFileSync } from 'node:fs';

const here = import.meta.dir;
const ui = join(here, 'ui');
const components = join(ui, 'src/components');
const walk = (dir: string): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)]));
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
// what a component loads only when first shown (import('./code-editor-monaco')): never imported up front
const lazy = new Set<string>();
for (const f of walk(components)) {
  if (!/\.(tsx?)$/.test(f)) continue;
  for (const m of readFileSync(f, 'utf8').matchAll(/import\(\s*["'](\.[^"']+)["']\s*\)/g)) {
    lazy.add(resolve(dirname(f), m[1]).replace(/\.tsx?$/, '') + '.tsx');
  }
}
const files = walk(components).filter((f) => f.endsWith('.tsx') && !f.endsWith('.stories.tsx') && !lazy.has(resolve(f))).sort();
console.log(`swaswa-ui: loaded when first shown: ${[...lazy].map((f) => relative(components, f)).join(', ')}`);   // java-gen.json's "exclude" too

// 1. the entry
const imports = files
  .map((f, i) => `import * as m${i} from './${relative(join(ui, 'src'), f).replace(/\\/g, '/').replace(/\.tsx$/, '')}';`)
  .join('\n');
writeFileSync(join(ui, 'src/swaswa-entry.tsx'), `// Made by ../build.ts: every component, registered with the page's React (swaswa-react's runtime).
import * as React from 'react';
import './html-controls.css';
import { NativeContext, NativeProvider } from './components/provider';
${imports}

const { register } = (window as any).__swaswaReact;

/** A component in a NativeProvider: the one around it, or its own when it is the first of the page. */
const native = (C: React.ComponentType<any>) => {
  const Native = (props: any) =>
    React.useContext(NativeContext) ? <C {...props} /> : <NativeProvider style={{ display: 'contents' }}><C {...props} /></NativeProvider>;
  Native.displayName = C.displayName ?? C.name;
  return Native;
};

const seen = new Set<string>();
for (const m of [${files.map((_, i) => 'm' + i).join(', ')}] as Record<string, unknown>[]) {
  for (const [name, value] of Object.entries(m)) {
    if (!/^[A-Z]/.test(name) || seen.has(name) || name.endsWith('Context')) continue;
    const component = typeof value === 'function' || (typeof value === 'object' && value !== null && '$$typeof' in value);
    if (!component) continue;
    seen.add(name);
    register(name, name === 'NativeProvider' ? value : native(value as React.ComponentType<any>));
  }
}
`);

// --web: only the served files (Maven's step); --java: only the classes. Unchanged sources since the last build: nothing.
const only = process.argv[2];
const out = join(here, '../../target/generated-resources/swaswa-ui/io/joshuasalcedo/swaswa/ui/web/swaswa-ui');
const hasher = new Bun.CryptoHasher('sha256');
for (const f of [...walk(join(ui, 'src')), join(ui, 'package.json'), join(ui, 'vite.swaswa.ts'), join(here, 'build.ts')].sort()) {
  hasher.update(f);
  hasher.update(readFileSync(f));
}
const stamp = hasher.digest('hex');
const stampFile = join(out, '.from');
const fresh = existsSync(stampFile) && readFileSync(stampFile, 'utf8') === stamp;

// 2. vite
if (only !== '--java' && fresh) console.log('swaswa-ui: the served files are up to date');
const vite = only === '--java' || fresh ? { exitCode: 0 } : Bun.spawnSync(['bunx', '--bun', 'vite', 'build', '--config', 'vite.swaswa.ts', '--logLevel', 'warn'], { cwd: ui, stdout: 'inherit', stderr: 'inherit' });
if (vite.exitCode !== 0) process.exit(vite.exitCode ?? 1);
if (only !== '--java' && !fresh) {
  writeFileSync(stampFile, stamp);
  console.log(`swaswa-ui: ${files.length} component files built into ${out}`);
}
if (only === '--web') process.exit(0);

// 3. the Java
const gen = Bun.spawnSync(['bun', join(here, '../../../swaswa-react/src/build/java-gen.ts'), join(here, 'java-gen.json')], { stdout: 'inherit', stderr: 'inherit' });
process.exit(gen.exitCode ?? 0);
