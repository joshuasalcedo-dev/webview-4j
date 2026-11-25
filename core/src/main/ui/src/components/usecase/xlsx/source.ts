// What the workbook and CSV views share: getting the file's bytes (a URL, a data: URL, or bytes given), naming
// it, saving it as a download, and base64 for Java.
import * as React from "react"

/** A file given as bytes. */
export type FileBytes = ArrayBuffer | Uint8Array

/** The bytes as an ArrayBuffer of their own (a copy: a library may detach or keep what it is given). */
export function toArrayBuffer(data: FileBytes): ArrayBuffer {
  const view = data instanceof Uint8Array ? data : new Uint8Array(data)
  const copy = new Uint8Array(view.byteLength)
  copy.set(view)
  return copy.buffer
}

/** The file's name from its URL (a data: URL has none). */
export function nameFromUrl(url: string | undefined, fallback: string): string {
  if (!url || url.startsWith("data:") || url.startsWith("blob:")) return fallback
  const last = (url.split(/[?#]/)[0] ?? "").split("/").pop() ?? ""
  try {
    return decodeURIComponent(last) || fallback
  } catch {
    return last || fallback
  }
}

/** `name` with `extension` (".xlsx") unless it already ends with one of `allowed`. */
export function withExtension(name: string, extension: string, allowed: string[] = [extension]): string {
  const lower = name.toLowerCase()
  return allowed.some((e) => lower.endsWith(e)) ? name : `${name}${extension}`
}

type Loaded = { bytes: ArrayBuffer | null; error: string | null; loading: boolean }

/**
 * The file's bytes: `data` as given, or fetched from `src` (data: URLs too). Loads again when `src` changes
 * (a Java screen sends the same src with each tree: it is not fetched again).
 */
export function useFileBytes(src: string | undefined, data: FileBytes | undefined): Loaded {
  const [fetched, setFetched] = React.useState<Loaded>({ bytes: null, error: null, loading: false })
  const given = React.useMemo(() => (data ? toArrayBuffer(data) : null), [data])

  React.useEffect(() => {
    if (given || !src) {
      setFetched({ bytes: null, error: null, loading: false })
      return
    }
    let current = true
    setFetched({ bytes: null, error: null, loading: true })
    fetch(src)
      .then((response) => {
        if (!response.ok) throw new Error(`The file could not be loaded (${response.status}).`)
        return response.arrayBuffer()
      })
      .then(
        (bytes) => current && setFetched({ bytes, error: null, loading: false }),
        (error: unknown) =>
          current &&
          setFetched({
            bytes: null,
            error: error instanceof Error ? error.message : "The file could not be loaded.",
            loading: false,
          })
      )
    return () => {
      current = false
    }
  }, [src, given])

  return given ? { bytes: given, error: null, loading: false } : fetched
}

/** Saves `data` as a file the browser downloads (in a webview-4j window: the system's Save dialog). */
export function downloadFile(data: BlobPart, fileName: string, type: string) {
  const url = URL.createObjectURL(new Blob([data], { type }))
  const anchor = document.createElement("a")
  anchor.href = url
  anchor.download = fileName
  anchor.rel = "noopener"
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

/** The bytes as base64 (how a file reaches Java). */
export function toBase64(bytes: Uint8Array): string {
  let binary = ""
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

/** Column `col` (0-based) as Excel names it: A, B, ... Z, AA. */
export function columnName(col: number): string {
  let n = col + 1
  let name = ""
  while (n > 0) {
    const rest = (n - 1) % 26
    name = String.fromCharCode(65 + rest) + name
    n = Math.floor((n - 1) / 26)
  }
  return name
}

/** A cell's A1 address. */
export function cellName(row: number, col: number): string {
  return `${columnName(col)}${row + 1}`
}

/** Runs after `delay` ms of `value` not changing. */
export function useDebounced<T>(value: T, delay: number): T {
  const [settled, setSettled] = React.useState(value)
  React.useEffect(() => {
    const id = window.setTimeout(() => setSettled(value), delay)
    return () => window.clearTimeout(id)
  }, [value, delay])
  return settled
}

