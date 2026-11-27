// bun run build: the runtime (React, ReactDOM, the bridge) into the jar's resources, as one script.
import { join } from 'node:path';

const out = join(import.meta.dir, '../main/resources/io/joshuasalcedo/swaswa/react');
const result = await Bun.build({
  entrypoints: [join(import.meta.dir, 'runtime.tsx')],
  format: 'iife',
  minify: true,
  target: 'browser',
  define: { 'process.env.NODE_ENV': '"production"' },
  naming: 'react-runtime.js',
  outdir: out,
});
if (!result.success) {
  for (const log of result.logs) console.error(log);
  process.exit(1);
}
const size = (await Bun.file(join(out, 'react-runtime.js')).arrayBuffer()).byteLength;
console.log(`react-runtime.js: ${(size / 1024).toFixed(0)} KB in ${out}`);
