// Delimited text (CSV, TSV, semicolons) read and written as RFC 4180 does: quoted fields, "" for a quote inside
// one, line breaks inside quotes.

/** The table in the text: its headers (the first row, or Column 1, 2...) and its rows, all as text. */
export type ParsedCsv = { headers: string[]; rows: string[][]; delimiter: string }

const CANDIDATES = [",", "\t", ";", "|"]

/** The delimiter the first lines use most evenly (outside quotes). */
export function guessDelimiter(text: string): string {
  const sample = text.slice(0, 64 * 1024)
  let best = ","
  let bestScore = 0
  for (const d of CANDIDATES) {
    const counts: number[] = []
    let quoted = false
    let count = 0
    for (let i = 0; i < sample.length && counts.length < 20; i++) {
      const ch = sample[i]
      if (ch === '"') quoted = !quoted
      else if (!quoted && ch === d) count++
      else if (!quoted && ch === "\n") {
        counts.push(count)
        count = 0
      }
    }
    if (count) counts.push(count)
    if (!counts.length || counts[0] === 0) continue
    const even = counts.filter((c) => c === counts[0]).length
    const score = even * counts[0]
    if (score > bestScore) {
      best = d
      bestScore = score
    }
  }
  return best
}

/** Every row of the text, each a list of fields. */
export function parseRows(text: string, delimiter: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let field = ""
  let quoted = false
  let i = text.charCodeAt(0) === 0xfeff ? 1 : 0
  for (; i < text.length; i++) {
    const ch = text[i]
    if (quoted) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"'
          i++
        } else quoted = false
      } else field += ch
    } else if (ch === '"' && field === "") quoted = true
    else if (ch === delimiter) {
      row.push(field)
      field = ""
    } else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && text[i + 1] === "\n") i++
      row.push(field)
      rows.push(row)
      row = []
      field = ""
    } else field += ch
  }
  if (field !== "" || row.length) {
    row.push(field)
    rows.push(row)
  }
  // blank lines say nothing
  return rows.filter((r) => r.length > 1 || r[0] !== "")
}

/** The table in `text`; with `header` (the default) its first row names the columns. */
export function parseCsv(text: string, options: { delimiter?: string; header?: boolean } = {}): ParsedCsv {
  const delimiter = options.delimiter ?? guessDelimiter(text)
  const all = parseRows(text, delimiter)
  const header = options.header ?? true
  const first = header ? (all[0] ?? []) : []
  const rows = header ? all.slice(1) : all
  const width = rows.reduce((max, r) => Math.max(max, r.length), first.length)
  const headers = Array.from({ length: width }, (_, i) => first[i]?.trim() || `Column ${i + 1}`)
  return { headers, rows, delimiter }
}

/** The table as delimited text again, fields quoted where they need it. */
export function writeCsv(headers: string[], rows: string[][], delimiter = ","): string {
  const quote = (value: string) =>
    value.includes(delimiter) || value.includes('"') || value.includes("\n") || value.includes("\r")
      ? `"${value.replaceAll('"', '""')}"`
      : value
  return [headers, ...rows].map((r) => r.map((v) => quote(v ?? "")).join(delimiter)).join("\r\n")
}
