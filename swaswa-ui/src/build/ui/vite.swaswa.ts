// swaswa-ui's components as ES modules for a swaswa page (bun ../build.ts runs it, Maven does): served from the jar at
// <SHARED_URL>swaswa-ui/ (PageScript.served): ui.js, the chunks it loads when needed (the code editor, the PDF and
// Office viewers, their WASM), and ui.css. React is the page's (swaswa-react's runtime) through swaswa-shims.
import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const shim = (name: string) => path.resolve(__dirname, 'swaswa-shims', name + '.cjs')
// into the build's own folder (never committed: about 90 MB with the code editor's, the viewers' and the grammar's WASM);
// Maven packs it into the jar (swaswa-ui/pom.xml)
const out = process.env.SWASWA_UI_OUT ?? path.resolve(__dirname, '../../../target/generated-resources/swaswa-ui/io/joshuasalcedo/swaswa/ui/web/swaswa-ui')

export default defineConfig({
  plugins: [react()],
  base: './',
  resolve: {
    alias: [
      { find: /^react$/, replacement: shim('react') },
      { find: /^react-dom$/, replacement: shim('react-dom') },
      { find: /^react-dom\/client$/, replacement: shim('react-dom-client') },
      { find: /^react\/jsx-(dev-)?runtime$/, replacement: shim('jsx-runtime') },
      { find: /^@webview4j\/ui\//, replacement: path.resolve(__dirname, 'src') + '/' },
    ],
  },
  define: { 'process.env.NODE_ENV': '"production"' },
  build: {
    outDir: out,
    emptyOutDir: true,
    cssCodeSplit: false,
    sourcemap: false,
    commonjsOptions: { include: [/node_modules/, /swaswa-shims/] },
    lib: { entry: 'src/swaswa-entry.tsx', formats: ['es'], fileName: () => 'ui.js', cssFileName: 'ui' },
    rollupOptions: {
      output: {
        chunkFileNames: 'chunks/[name]-[hash].js',
        // the style sheet at the name the page links (PageScript.served's css: swaswa-ui/ui.css); the rest hashed
        assetFileNames: (asset) => (asset.names?.[0]?.endsWith('.css') ? 'ui.css' : 'assets/[name]-[hash][extname]'),
      },
    },
  },
})
