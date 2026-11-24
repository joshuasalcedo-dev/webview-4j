// The browser bundle core's jar carries: src/bundle.ts with React and every dependency inside, as ES modules
// (ui.js, and chunks it loads when needed, such as the code editor), and all the styles in ui.css. Gradle runs it
// (:core:uiBundle) into build/generated/ui/webview4j-ui/, which the jar serves at <base>ui/.
import react from "@vitejs/plugin-react"
import { resolve } from "node:path"
import { defineConfig } from "vite"
import { here } from "./vite.lib.ts"

const dir = here(import.meta.url)
const out = process.env.WEBVIEW4J_UI_OUT ?? resolve(dir, "../../../build/generated/ui/webview4j-ui")

export default defineConfig({
  // Chunks and assets are found from ui.js itself, whatever page loads it.
  base: "./",
  plugins: [react()],
  define: { "process.env.NODE_ENV": JSON.stringify("production") },
  resolve: { alias: [{ find: /^@webview4j\/ui\//, replacement: resolve(dir, "src") + "/" }] },
  build: {
    outDir: out,
    emptyOutDir: true,
    minify: true,
    sourcemap: false,
    cssCodeSplit: false,
    // An app build (fully minified), whose one entry keeps every export: a page imports from it.
    rollupOptions: {
      input: resolve(dir, "src/bundle.ts"),
      preserveEntrySignatures: "strict",
      output: {
        format: "es",
        entryFileNames: "ui.js",
        chunkFileNames: "chunks/[name]-[hash].js",
        assetFileNames: (asset) => (asset.names?.[0]?.endsWith(".css") ? "ui.css" : "assets/[name]-[hash][extname]"),
      },
    },
  },
})
