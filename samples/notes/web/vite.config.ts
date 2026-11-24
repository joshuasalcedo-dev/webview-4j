// The page, built with Vite. Gradle (webBuild) builds it into the jar's web/, which Java opens with
// WindowOptions.app("web"); `bun run dev` serves it with hot reload for ./gradlew run -Pdev.
//
// Here @webview4j/ui and @webview4j/api come from this repository's sources. In your own app, install them instead
// (bun add @webview4j/ui @webview4j/api) and drop the aliases.
import react from "@vitejs/plugin-react"
import { fileURLToPath } from "node:url"
import { defineConfig } from "vite"

const core = fileURLToPath(new URL("../../../core/src/main/", import.meta.url))

export default defineConfig({
  // The page is served from <base>app/ in the jar: its files are found from index.html.
  base: "./",
  plugins: [react()],
  resolve: {
    alias: [
      { find: /^@webview4j\/ui\//, replacement: core + "ui/src/" },
      { find: /^@webview4j\/api$/, replacement: core + "typescript/index.ts" },
    ],
    // One React, though the kit's sources sit in another folder with its own node_modules.
    dedupe: ["react", "react-dom"],
  },
  server: { port: 5173, strictPort: true, fs: { allow: [".", core] } },
  // Two pages: the notes (index.html) and the showcase window (showcase.html, WindowOptions.app("web").page(...)).
  build: {
    rollupOptions: {
      input: {
        index: fileURLToPath(new URL("index.html", import.meta.url)),
        showcase: fileURLToPath(new URL("showcase.html", import.meta.url)),
      },
    },
  },
})
