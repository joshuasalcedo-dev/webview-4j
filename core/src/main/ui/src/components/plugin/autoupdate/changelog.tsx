import * as React from "react"

import { Markdown } from "../../markdown"
import "./autoupdate.css"

/**
 * A CHANGELOG.md, as people read it: each version with its date and what changed. The format is the usual one:
 * `## <version> — <date>` (or `## [<version>] - <date>`), then the version's changes in Markdown.
 */

type Release = { version: string; date?: string; body: string }

const HEADING = /^##\s+\[?v?([0-9][^\]\s]*)\]?\s*(?:[—–-]\s*(.+?))?\s*$/

/** The releases in a changelog, as written (the newest first, usually). */
function parseChangelog(markdown: string): Release[] {
  const releases: Release[] = []
  let current: Release | null = null
  const lines: string[] = []
  const close = () => {
    if (current) releases.push({ ...current, body: lines.join("\n").trim() })
    lines.length = 0
  }
  for (const line of markdown.split(/\r?\n/)) {
    const m = HEADING.exec(line)
    if (m) {
      close()
      current = { version: m[1], date: m[2]?.trim(), body: "" }
    } else if (current) {
      lines.push(line)
    }
  }
  close()
  return releases
}

/** -1, 0 or 1, as versions go: numbers part by part, and a pre-release (1.0.0-rc.1) before its release. */
function compareVersions(a: string, b: string): number {
  const [ma, pa] = a.split("-", 2)
  const [mb, pb] = b.split("-", 2)
  const x = ma.split(".").map((n) => parseInt(n, 10) || 0)
  const y = mb.split(".").map((n) => parseInt(n, 10) || 0)
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (x[i] ?? 0) - (y[i] ?? 0)
    if (d !== 0) return Math.sign(d)
  }
  if (pa && !pb) return -1
  if (!pa && pb) return 1
  return pa && pb ? Math.sign(pa.localeCompare(pb, undefined, { numeric: true })) : 0
}

type ChangelogProps = {
  /** The changelog's Markdown (a CHANGELOG.md). */
  markdown: string
  /** Only the versions after this one (What's new since the version last run). */
  since?: string
  /** Only this version (an update's notes). */
  only?: string
  /** Up to this version (no unreleased entries newer than the app). */
  upTo?: string
  onLink?: (href: string) => void
  empty?: React.ReactNode
  className?: string
  style?: React.CSSProperties
}

function Changelog({ markdown, since, only, upTo, onLink, empty = "No changes written down yet.", className, style }: ChangelogProps) {
  const releases = React.useMemo(() => {
    let all = parseChangelog(markdown)
    if (only) all = all.filter((r) => r.version === only)
    if (since) all = all.filter((r) => isVersion(r.version) && compareVersions(r.version, since) > 0)
    if (upTo) all = all.filter((r) => !isVersion(r.version) || compareVersions(r.version, upTo) <= 0)
    return all
  }, [markdown, since, only, upTo])
  return (
    <div className={["nt-changelog", className].filter(Boolean).join(" ")} style={style}>
      {releases.length === 0 && <div className="nt-changelog-empty">{empty}</div>}
      {releases.map((r) => (
        <section key={r.version} className="nt-changelog-release" aria-label={`Version ${r.version}`}>
          <header>
            <span className="nt-changelog-version">{r.version}</span>
            {r.date && <span className="nt-changelog-date">{r.date}</span>}
          </header>
          <Markdown onLink={onLink} headingOffset={2}>
            {r.body}
          </Markdown>
        </section>
      ))}
    </div>
  )
}

function isVersion(v: string) {
  return /^[0-9]/.test(v)
}

export { Changelog, compareVersions, parseChangelog }
export type { ChangelogProps, Release }
