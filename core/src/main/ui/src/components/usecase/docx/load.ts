// What the Word components share and that needs no DOCX library: loading the library (the first time a document
// is shown), reading the file given as a URL or as bytes, saving bytes, base64 for Java.
import type * as ReactDocx from "@extend-ai/react-docx"

/** The DOCX library (@extend-ai/react-docx), once loaded. */
export type DocxLibrary = typeof ReactDocx

export const DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

let loading: Promise<DocxLibrary> | undefined

/**
 * Loads the DOCX library and its WebAssembly (about 2.5 MB), once: an app that never shows a document never loads
 * them. The binary is fetched from where the bundler put it and handed over as a Response, so documents are read
 * on the page's own thread (the library's import worker is not needed, which a bundled page cannot always serve).
 */
export function loadDocx(): Promise<DocxLibrary> {
  loading ??= Promise.all([
    import("@extend-ai/react-docx"),
    import("@extend-ai/react-docx/docx_wasm_bg.wasm?url"),
  ]).then(async ([lib, wasm]) => {
    const response = await fetch(wasm.default)
    if (!response.ok) throw new Error(`The DOCX reader did not load (${response.status} ${response.statusText})`)
    try {
      lib.setWasmSource(response)
    } catch {
      // already started by an earlier load: it keeps its source
    }
    return lib
  })
  loading.catch(() => {
    loading = undefined
  })
  return loading
}

/** The file given: `data` (copied), else `src` fetched (a URL, or a data: URL as Java sends one). */
export async function readDocx(src?: string, data?: ArrayBuffer | Uint8Array): Promise<ArrayBuffer | undefined> {
  if (data) {
    const bytes = data instanceof Uint8Array ? data : new Uint8Array(data)
    return bytes.slice().buffer
  }
  if (!src) return undefined
  const response = await fetch(src)
  if (!response.ok) throw new Error(`The document did not load (${response.status})`)
  return response.arrayBuffer()
}

/** The name to show: `fileName`, else the URL's last part, else "document.docx". */
export function docxName(fileName: string | undefined, src: string | undefined): string {
  if (fileName?.trim()) return fileName
  if (!src || src.startsWith("data:") || src.startsWith("blob:")) return "document.docx"
  const last = src.split(/[?#]/)[0].split("/").pop() || "document.docx"
  try {
    return decodeURIComponent(last)
  } catch {
    return last
  }
}

export function withDocxExtension(name: string): string {
  return /\.docx$/i.test(name) ? name : `${name.replace(/\.doc$/i, "")}.docx`
}

/** Saves bytes as a file, through the browser's (the webview's) download. */
export function downloadBytes(bytes: Uint8Array | ArrayBuffer, name: string) {
  const blob = new Blob([bytes instanceof Uint8Array ? bytes.slice() : bytes], { type: DOCX_MIME })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement("a")
  anchor.href = url
  anchor.download = withDocxExtension(name)
  anchor.rel = "noopener"
  anchor.style.display = "none"
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Bytes as base64 (what the page sends Java). */
export function toBase64(bytes: Uint8Array): string {
  let binary = ""
  const step = 0x8000
  for (let i = 0; i < bytes.length; i += step) binary += String.fromCharCode(...bytes.subarray(i, i + step))
  return btoa(binary)
}

/** The base64 part of a data: URL, or undefined. */
export function base64Of(src: string | undefined): string | undefined {
  if (!src?.startsWith("data:")) return undefined
  const at = src.indexOf("base64,")
  return at === -1 ? undefined : src.slice(at + 7)
}

/** Lets the person pick a file of the types in `accept`; undefined when nothing was picked. */
export function pickFile(accept: string): Promise<File | undefined> {
  return new Promise((resolve) => {
    const input = document.createElement("input")
    input.type = "file"
    input.accept = accept
    input.style.display = "none"
    input.addEventListener("change", () => {
      const file = input.files?.[0]
      input.remove()
      resolve(file)
    })
    input.addEventListener("cancel", () => {
      input.remove()
      resolve(undefined)
    })
    document.body.append(input)
    input.click()
  })
}

/** Lets the person pick a .docx: the file's name and bytes, or undefined when nothing was picked. */
export async function pickDocx(): Promise<{ name: string; bytes: ArrayBuffer } | undefined> {
  const file = await pickFile(".docx,.doc," + DOCX_MIME + ",application/msword")
  return file ? { name: file.name, bytes: await file.arrayBuffer() } : undefined
}
