// The npm library @webview4j/ui: one module per source file, React and the other dependencies left to the app.
import { here, library } from "./vite.lib.ts"

export default library(here(import.meta.url), "@webview4j/ui")
