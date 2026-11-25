import * as React from "react"

/*
 * Spelling and grammar in the page: Harper (crates/grammar) as WebAssembly, offline, about a millisecond a message.
 * The module is large (its dictionary): it loads the first time something is checked. Build it with
 * `bun run grammar` (cargo + wasm-bindgen, into src/grammar/).
 */

/** One thing to fix: where (UTF-16, as JavaScript counts), which rule, what kind, what it says, what could go there. */
export type Mistake = {
  from: number
  to: number
  rule: string
  /** "Spelling", "Grammar", "Punctuation", "Capitalization", "Style", "WordChoice", "Miscellaneous"... */
  kind: string
  message: string
  /** Each the whole text for from..to; an empty one removes it. */
  suggestions: string[]
}

/** Which English: en-US (the default), en-GB, en-AU, en-CA, en-IN. */
export type Dialect = "en-US" | "en-GB" | "en-AU" | "en-CA" | "en-IN"

type Module = typeof import("../grammar/grammar")
type Checker = import("../grammar/grammar").Checker

let loading: Promise<Module> | undefined
const checkers = new Map<string, Checker>()

function loadGrammar(): Promise<Module> {
  loading ??= import("../grammar/grammar").then(async (m) => {
    await m.default()
    return m
  })
  return loading
}

/** Loads the checker ahead of time (it takes a moment, once), e.g. when a form with checked fields opens. */
export async function preloadGrammar(dialect: Dialect = "en-US") {
  await checkerFor(dialect)
}

async function checkerFor(dialect: Dialect): Promise<Checker> {
  const m = await loadGrammar()
  let checker = checkers.get(dialect)
  if (!checker) {
    checker = new m.Checker(dialect)
    checkers.set(dialect, checker)
  }
  return checker
}

/** The mistakes in `text`, in the order they appear. */
export async function checkGrammar(text: string, dialect: Dialect = "en-US"): Promise<Mistake[]> {
  if (!text.trim()) return []
  const checker = await checkerFor(dialect)
  return JSON.parse(checker.check(text)) as Mistake[]
}

/** `text` with one mistake fixed by `suggestion`. */
export function applySuggestion(text: string, mistake: Mistake, suggestion: string): string {
  return text.slice(0, mistake.from) + suggestion + text.slice(mistake.to)
}

/**
 * The mistakes in `text`, checked again once it has not changed for `delay` ms (500 unless set): typing does not
 * check on every key, only when it pauses. `ignored` words are left alone.
 */
export function useGrammar(text: string, options: { dialect?: Dialect; delay?: number; enabled?: boolean; ignored?: string[] } = {}) {
  const { dialect = "en-US", enabled = true, ignored } = options
  const delay = options.delay ?? 500
  const [mistakes, setMistakes] = React.useState<Mistake[]>([])
  const [ready, setReady] = React.useState(false)
  const ignoredKey = ignored?.join("\u0000") ?? ""
  React.useEffect(() => {
    if (!enabled) {
      setMistakes([])
      return
    }
    let stale = false
    const timer = window.setTimeout(() => {
      checkGrammar(text, dialect)
        .then((found) => {
          if (stale) return
          const skip = new Set(ignoredKey ? ignoredKey.split("\u0000") : [])
          setMistakes(skip.size ? found.filter((m) => !skip.has(text.slice(m.from, m.to))) : found)
          setReady(true)
        })
        .catch((error) => console.warn("grammar: the checker did not load", error))
    }, delay)
    return () => {
      stale = true
      window.clearTimeout(timer)
    }
  }, [text, dialect, delay, enabled, ignoredKey])
  return { mistakes, ready }
}
