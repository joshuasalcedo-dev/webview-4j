// How @webview4j/ui is built for npm: each source file becomes its own module in dist/ (so an app only carries what
// it imports), the package's dependencies stay outside (the app installs them), and CSS files are copied as they
// are, with their imports kept, so the app's bundler (and Tailwind, for @swaswa/ui) handles them.
// Vite-only features (import.meta.glob, ?worker) are resolved here, so the published code needs none of them.
import react from "@vitejs/plugin-react"
import { cpSync, globSync, readFileSync } from "node:fs"
import { dirname, resolve } from "node:path"
import { defineConfig, type Plugin } from "vite"

/** Copies src/**\/*.css to dist/ and keeps every relative CSS import in the code as it is. */
function cssAsIs(dir: string): Plugin {
  return {
    name: "swaswa-css-as-is",
    enforce: "pre",
    resolveId(source) {
      if (source.endsWith(".css") && source.startsWith(".")) return { id: source, external: true }
      return null
    },
    closeBundle() {
      for (const file of globSync("src/**/*.css", { cwd: dir })) {
        const to = resolve(dir, "dist", file.replace(/^src[\\/]/, ""))
        cpSync(resolve(dir, file), to, { recursive: true })
      }
    },
  }
}

/** The Vite config of the package in `dir`, published as `name` (e.g. "@swaswa/ui"). */
export function library(dir: string, name: string) {
  const pkg = JSON.parse(readFileSync(resolve(dir, "package.json"), "utf8"))
  const outside = [...Object.keys(pkg.dependencies ?? {}), ...Object.keys(pkg.peerDependencies ?? {})]
  const entry = Object.fromEntries(
    globSync("src/**/*.{ts,tsx}", { cwd: dir })
      .map((f) => f.replaceAll("\\", "/"))
      // stories and the demo pieces they use (src/stories) are for Storybook only
      .filter((f) => !/\.(stories|test)\.tsx?$/.test(f) && !f.endsWith(".d.ts") && !f.startsWith("src/stories/"))
      .map((f) => [f.replace(/^src\//, "").replace(/\.tsx?$/, ""), resolve(dir, f)])
  )
  return defineConfig({
    plugins: [react(), cssAsIs(dir)],
    resolve: { alias: [{ find: new RegExp(`^${name}/`), replacement: resolve(dir, "src") + "/" }] },
    build: {
      outDir: resolve(dir, "dist"),
      emptyOutDir: true,
      minify: false,
      sourcemap: false,
      lib: { entry, formats: ["es"] },
      rollupOptions: {
        external: (id) => !id.includes("?") && outside.some((d) => id === d || id.startsWith(d + "/")),
        makeAbsoluteExternalsRelative: false,
        output: {
          preserveModules: true,
          preserveModulesRoot: resolve(dir, "src"),
          entryFileNames: (chunk) => {
            const path = chunk.facadeModuleId ?? ""
            // modules from outside src (icon SVGs, workers) go under dist/_assets
            return path.startsWith(resolve(dir, "src").replaceAll("\\", "/")) ? "[name].js" : "_assets/[name].js"
          },
        },
      },
    },
  })
}

export const here = (url: string) => dirname(new URL(url).pathname.replace(/^\/([A-Za-z]:)/, "$1"))
