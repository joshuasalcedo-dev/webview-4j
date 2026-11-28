// For a component library's bundle (swaswa-shadcn's, an app's): React comes from the page's runtime
// (window.__swaswaReact), not from the bundle, so every library shares the one React.
//
//   import { reactGlobals } from '<swaswa-react>/src/build/globals.ts';
//   await Bun.build({ entrypoints: ['library.tsx'], plugins: [reactGlobals], format: 'iife', ... });
import type { BunPlugin } from 'bun';

const shared: Record<string, string> = {
  'react': 'React',
  'react-dom': 'ReactDOM',
  'react-dom/client': 'ReactDOMClient',
  'react/jsx-runtime': 'JsxRuntime',
  'react/jsx-dev-runtime': 'JsxRuntime',
};

export const reactGlobals: BunPlugin = {
  name: 'swaswa-react-globals',
  setup(build) {
    const filter = new RegExp('^(' + Object.keys(shared).map((k) => k.replace(/[/-]/g, '\\$&')).join('|') + ')$');
    build.onResolve({ filter }, (args) => ({ path: args.path, namespace: 'swaswa-react' }));
    build.onLoad({ filter: /.*/, namespace: 'swaswa-react' }, (args) => ({
      contents: `module.exports = window.__swaswaReact.${shared[args.path]};`,
      loader: 'js',
    }));
  },
};
