// bun add-all.ts: every component shadcn/ui has, into ui/src/components/ui (with what they need installed), then
// build.ts: the bundle, its CSS and a Java package per component. `just shadcn-add-all`.
//
// bun add-all.ts <name>...: only those (`bun add-all.ts calendar sonner`).
import { join } from 'node:path';

const here = import.meta.dir;
const ui = join(here, 'ui');
const names = process.argv.slice(2);

const run = (cmd: string[], cwd: string) => {
  console.log('> ' + cmd.join(' '));
  const r = Bun.spawnSync(cmd, { cwd, stdout: 'inherit', stderr: 'inherit' });
  if (r.exitCode !== 0) process.exit(r.exitCode ?? 1);
};

// shadcn's own CLI: it knows the registry, each component's dependencies, and this project's style (components.json)
run(['bunx', '--bun', 'shadcn@latest', 'add', ...(names.length ? names : ['--all']), '--yes', '--overwrite'], ui);
run(['bun', join(here, 'build.ts')], here);
