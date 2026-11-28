// bun build.ts: shadcn/ui's components (ui/src/components/ui, added with `bunx --bun shadcn@latest add <name>` in ui)
// into the jar as one script (their CSS in it), and their typed Java classes into src/main/java.
//   1. an entry that registers every exported component with the page's React runtime
//   2. vite (Tailwind's plugin compiles their classes; React stays the page's)
//   3. the CSS put inside the script: one resource, shadcn.js
//   4. swaswa-react's java-gen: a Java class per component, from its TypeScript props
import { join } from 'node:path';
import { readdirSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';

const here = import.meta.dir;
const ui = join(here, 'ui');
const components = join(ui, 'src/components/ui');
const files = readdirSync(components).filter((f) => f.endsWith('.tsx')).sort();

// 1. the entry
const imports = files.map((f, i) => `import * as m${i} from './components/ui/${f.replace(/\.tsx$/, '')}';`).join('\n');
writeFileSync(join(ui, 'src/swaswa-entry.tsx'), `// Made by ../build.ts: every component, registered with the page's React (swaswa-react's runtime).
import './index.css';
${imports}

const { register } = (window as any).__swaswaReact;
for (const m of [${files.map((_, i) => 'm' + i).join(', ')}] as Record<string, unknown>[]) {
  for (const [name, value] of Object.entries(m)) {
    if (/^[A-Z]/.test(name) && !name.endsWith('Variants') && (typeof value === 'function' || typeof value === 'object')) register(name, value);
  }
}

// shadcn's dark theme is a class (.dark): it follows the page's colour scheme (Java's theme: light, dark, the system's)
const root = document.documentElement;
const system = matchMedia('(prefers-color-scheme: dark)');
const follow = () => {
  const scheme = root.style.colorScheme;
  root.classList.toggle('dark', scheme === 'dark' || (scheme !== 'light' && system.matches));
};
follow();
system.addEventListener('change', follow);
new MutationObserver(follow).observe(root, { attributes: true, attributeFilter: ['style'] });
`);

// 2. vite
const vite = Bun.spawnSync(['bunx', '--bun', 'vite', 'build', '--config', 'vite.swaswa.ts', '--logLevel', 'warn'], { cwd: ui, stdout: 'inherit', stderr: 'inherit' });
if (vite.exitCode !== 0) process.exit(vite.exitCode ?? 1);

// 3. one script, its CSS inside
const dist = join(ui, 'dist-swaswa');
const js = readFileSync(join(dist, 'shadcn.js'), 'utf8');
const css = readFileSync(join(dist, 'shadcn.css'), 'utf8');
const out = join(here, '../main/resources/io/joshuasalcedo/swaswa/shadcn');
mkdirSync(out, { recursive: true });
writeFileSync(join(out, 'shadcn.js'), `(()=>{const s=document.createElement('style');s.dataset.swaswa='shadcn';s.textContent=${JSON.stringify(css)};document.head.prepend(s);})();\n${js}`);
console.log(`shadcn.js: ${files.length} files, ${(js.length / 1024).toFixed(0)} KB of script, ${(css.length / 1024).toFixed(0)} KB of CSS`);

// 4. the Java
const gen = Bun.spawnSync(['bun', join(here, '../../../swaswa-react/src/build/java-gen.ts'), join(here, 'java-gen.json')], { stdout: 'inherit', stderr: 'inherit' });
process.exit(gen.exitCode ?? 0);
