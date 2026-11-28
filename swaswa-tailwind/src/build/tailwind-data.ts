// tailwind-data: everything swaswa-tailwind needs from Tailwind, so apps need no Node and no Tailwind build:
//   classes   every utility class Tailwind has with its default theme, and the CSS inside its rule (nested rules kept)
//   base      what the classes need once in a page: the theme's variables (all of them), the @property defaults
//             utilities rely on (--tw-shadow...), and the keyframes (spin, ping...)
//   preflight Tailwind's reset, for a page that wants it
// into ../main/resources/io/joshuasalcedo/swaswa/tailwind/tailwind.json.gz (committed: run again only to update Tailwind).
//
//   cd src/build && bun install && bun tailwind-data.ts

import * as fs from "node:fs"
import * as path from "node:path"
import * as zlib from "node:zlib"

const HERE = import.meta.dirname
const TW = path.join(HERE, "node_modules/tailwindcss")
const OUT = path.resolve(HERE, "../main/resources/io/joshuasalcedo/swaswa/tailwind/tailwind.json.gz")
const version: string = JSON.parse(fs.readFileSync(path.join(TW, "package.json"), "utf8")).version

const tw = await import(path.join(TW, "dist/lib.mjs"))
const loadStylesheet = async (id: string, base: string) => {
  const file = id === "tailwindcss" ? path.join(TW, "index.css")
    : id.startsWith("tailwindcss/") ? path.join(TW, id.slice("tailwindcss/".length)) : path.resolve(base, id)
  return { path: file, base: path.dirname(file), content: fs.readFileSync(file, "utf8") }
}

// the classes: Tailwind's own list, each with its CSS as Tailwind writes it
const design = await tw.__unstable__loadDesignSystem('@import "tailwindcss";', { base: HERE, loadStylesheet })
const names: string[] = design.getClassList().map(([name]: [string]) => name)
const css: (string | null)[] = design.candidatesToCss(names)

/** The body of the rule for `.name` (top-level): the declarations and nested rules inside its braces. */
function body(rule: string): string | null {
  const open = rule.indexOf("{")
  if (open < 0 || !rule.trimStart().startsWith(".")) return null
  let depth = 0
  for (let i = open; i < rule.length; i++) {
    if (rule[i] === "{") depth++
    else if (rule[i] === "}" && --depth === 0) return rule.slice(open + 1, i).trim()
  }
  return null
}

/** The top-level blocks of a stylesheet: each a selector or an at-rule with its braces. */
function blocks(sheet: string): string[] {
  const out: string[] = []
  let depth = 0, start = 0
  for (let i = 0; i < sheet.length; i++) {
    if (sheet[i] === "{") depth++
    else if (sheet[i] === "}" && --depth === 0) { out.push(sheet.slice(start, i + 1).trim()); start = i + 1 }
    else if (sheet[i] === ";" && depth === 0) { out.push(sheet.slice(start, i + 1).trim()); start = i + 1 }
  }
  return out.filter(Boolean)
}

/** A class name as a CSS selector writes it: what is not a letter, digit, - or _ escaped (as Java's Tailwind does). */
const escape = (name: string) => name.replace(/[^a-zA-Z0-9_-]/g, "\\$&").replace(/^(-?)(\d)/, "$1\\3$2 ")

// b: the body of its `.name { }` rule; r: a rule whose selector has the class elsewhere (`:where(.space-x-4 > ...)`),
// its class written @@ (Java puts the class, its variants' too, in its place)
const classes: { n: string; b?: string; r?: string }[] = []
const extras = new Set<string>()
names.forEach((name, i) => {
  const text = css[i]
  if (!text) return
  const selector = "." + escape(name)
  let entry: { n: string; b?: string; r?: string } | null = null
  for (const block of blocks(text)) {
    const own = block.startsWith(selector + " ") || block.startsWith(selector + "{")
    if (entry === null && own) entry = { n: name, b: body(block)!.replace(/\s+/g, " ") }
    else if (entry === null && block.includes(selector) && !block.startsWith("@")) {
      entry = { n: name, r: block.split(selector).join("@@").replace(/\s+/g, " ") }
    } else if (!block.includes(selector)) extras.add(block)   // @property defaults, @keyframes: once per page, in base
  }
  if (entry) classes.push(entry)
})

// the base: every theme variable (static: all of them, not only those a scan found), the keyframes, the @property
// defaults; and the reset on its own
const compile = async (source: string) => (await tw.compile(source, { base: HERE, loadStylesheet })).build([])
const theme = await compile('@import "tailwindcss/theme.css" theme(static);')
const preflight = await compile('@import "tailwindcss/preflight.css";')

const data = { version, base: theme + "\n" + [...extras].join("\n"), preflight, classes }
fs.mkdirSync(path.dirname(OUT), { recursive: true })
fs.writeFileSync(OUT, zlib.gzipSync(JSON.stringify(data), { level: 9 }))
// @import "tailwindcss"; in an app's CSS file: Tailwind's reset, as in a Tailwind project (its variables come with any class)
fs.writeFileSync(path.resolve(HERE, "../main/resources/tailwindcss.css"),
  `/* Tailwind ${version}'s reset (preflight), as @import "tailwindcss"; brings it in a Tailwind project.
`
  + `   Made by src/build/tailwind-data.ts with the rest of the data: do not edit. */
` + preflight)
console.log(`tailwind-data: Tailwind ${version}, ${classes.length} classes, ${extras.size} base rules -> ${path.relative(HERE, OUT)}`
  + ` (${Math.round(fs.statSync(OUT).size / 1024)} KB)`)
