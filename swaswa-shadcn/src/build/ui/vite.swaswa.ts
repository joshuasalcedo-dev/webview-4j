// The components as one script for a swaswa page (bun ../build.ts runs it): React from the page's runtime
// (window.__swaswaReact, through swaswa-shims: imports and requires alike), the components' CSS compiled by
// Tailwind, fonts inlined.
import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const shim = (name: string) => path.resolve(__dirname, 'swaswa-shims', name + '.cjs')

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: [
      { find: /^react$/, replacement: shim('react') },
      { find: /^react-dom$/, replacement: shim('react-dom') },
      { find: /^react-dom\/client$/, replacement: shim('react-dom-client') },
      { find: /^react\/jsx-(dev-)?runtime$/, replacement: shim('jsx-runtime') },
      { find: '@', replacement: path.resolve(__dirname, './src') },
    ],
  },
  define: { 'process.env.NODE_ENV': '"production"' },
  build: {
    outDir: 'dist-swaswa',
    emptyOutDir: true,
    assetsInlineLimit: 100_000_000,
    cssCodeSplit: false,
    commonjsOptions: { include: [/node_modules/, /swaswa-shims/] },
    lib: { entry: 'src/swaswa-entry.tsx', formats: ['iife'], name: 'swaswaShadcn', fileName: () => 'shadcn.js', cssFileName: 'shadcn' },
  },
})
