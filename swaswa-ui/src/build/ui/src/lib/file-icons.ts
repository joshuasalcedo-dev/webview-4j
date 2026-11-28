// Which icon a file or folder gets, from Material Icon Theme (VS Code's most used icon theme, MIT licence,
// npm package material-icon-theme). Its own manifest says which name and which extension get which icon; this
// reads it the way VS Code does. The SVGs load one by one, when shown, so the app does not carry all 1,251.
import {
  fileExtensions,
  fileNames,
  folderNames,
  folderNamesExpanded,
  light,
} from "material-icon-theme/dist/material-icons.json"

import type { Theme } from "./platform"

type Lookup = Record<string, string>

const lightFiles = (light as { fileNames?: Lookup }).fileNames ?? {}
const lightExtensions = (light as { fileExtensions?: Lookup }).fileExtensions ?? {}
const lightFolders = (light as { folderNames?: Lookup }).folderNames ?? {}
const lightFoldersOpen = (light as { folderNamesExpanded?: Lookup }).folderNamesExpanded ?? {}

// Each icon's URL, loaded only when asked for (a small chunk each).
const loaders = import.meta.glob<string>("../../node_modules/material-icon-theme/icons/*.svg", {
  query: "?url",
  import: "default",
})

const byName = new Map<string, () => Promise<string>>()
for (const [path, load] of Object.entries(loaders)) {
  byName.set(path.slice(path.lastIndexOf("/") + 1, -".svg".length), load)
}

function pick(key: string, base: Lookup, lighter: Lookup, theme: Theme): string | undefined {
  return (theme === "light" ? lighter[key] : undefined) ?? base[key]
}

/** The name of the icon for a file or folder (e.g. "typescript", "folder-src-open"), as VS Code picks it. */
function fileIconName(name: string, options: { folder?: boolean; open?: boolean; theme?: Theme } = {}): string {
  const { folder = false, open = false, theme = "light" } = options
  const lower = name.toLowerCase()

  if (folder) {
    const named = open
      ? pick(lower, folderNamesExpanded as Lookup, lightFoldersOpen, theme)
      : pick(lower, folderNames as Lookup, lightFolders, theme)
    return named ?? (open ? "folder-open" : "folder")
  }

  // A whole name first (package.json, Dockerfile), then the longest extension (d.ts before ts).
  const whole = pick(lower, fileNames as Lookup, lightFiles, theme)
  if (whole) return whole
  for (let dot = lower.indexOf("."); dot !== -1; dot = lower.indexOf(".", dot + 1)) {
    const icon = pick(lower.slice(dot + 1), fileExtensions as Lookup, lightExtensions, theme)
    if (icon) return icon
  }
  return "file"
}

const urls = new Map<string, Promise<string | undefined>>()

/** The URL of an icon's SVG, or undefined when the theme has no icon of that name. Loaded once, then kept. */
function fileIconUrl(icon: string): Promise<string | undefined> {
  let url = urls.get(icon)
  if (!url) {
    const load = byName.get(icon) ?? byName.get("file")
    url = load ? load() : Promise.resolve(undefined)
    urls.set(icon, url)
  }
  return url
}

/** How the systems write a file's size: Windows counts in 1024s (KB), a Mac in 1000s (kB, as Finder). */
function formatFileSize(bytes: number, platform: "windows" | "mac" = "windows"): string {
  const step = platform === "mac" ? 1000 : 1024
  const units = platform === "mac" ? ["bytes", "kB", "MB", "GB", "TB"] : ["bytes", "KB", "MB", "GB", "TB"]
  let value = bytes
  let unit = 0
  while (value >= step && unit < units.length - 1) {
    value /= step
    unit++
  }
  if (unit === 0) return `${bytes} ${bytes === 1 ? "byte" : "bytes"}`
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`
}

export { fileIconName, fileIconUrl, formatFileSize }
