import type { PdfDocumentObject, PdfEngine } from "@embedpdf/models"
// The PDFium build EmbedPDF runs (WebAssembly, ~4 MB): a file of the bundle (assets/pdfium-*.wasm), never a CDN's.
import wasmUrl from "@embedpdf/pdfium/pdfium.wasm?url"

/*
 * The PDF engine every PDF component shares: PDFium (WebAssembly) in a Web Worker, as EmbedPDF runs it. It loads
 * the first time a PDF is shown; this module itself is only imported then (pdf-viewer.tsx and file-thumbnail.tsx
 * import it lazily), so an app that never shows a PDF never loads it.
 *
 * The worker is made from a blob (EmbedPDF's own code, inlined), so it cannot resolve the bundle's URLs itself:
 * the wasm is fetched here, on the page, and handed to it as a blob URL. That works whatever serves the bundle
 * (Vite in development, the jar's https://webview4j.localhost/ui/ or webview4j://localhost/ui/).
 */

let engine: Promise<PdfEngine> | undefined

/**
 * Whether this page can start a worker from a blob. A webview-4j window's page is about:blank (its origin is
 * "null"), where a blob: worker fails to start and nothing says so; a page served from an origin can.
 */
function blobWorkers(): Promise<boolean> {
  return new Promise((resolve) => {
    let worker: Worker | undefined
    const done = (ok: boolean) => {
      window.clearTimeout(timer)
      worker?.terminate()
      resolve(ok)
    }
    const timer = window.setTimeout(() => done(false), 2000)
    try {
      const url = URL.createObjectURL(new Blob(["postMessage(1)"], { type: "text/javascript" }))
      worker = new Worker(url, { type: "module" })
      worker.onmessage = () => done(true)
      worker.onerror = () => done(false)
    } catch {
      done(false)
    }
  })
}

/** The shared engine; a failure is not kept, so the next PDF shown tries again. */
export function loadPdfEngine(): Promise<PdfEngine> {
  engine ??= (async () => {
    const [wasm, inWorker] = await Promise.all([
      fetch(new URL(wasmUrl, location.href)).then((response) => {
        if (!response.ok) throw new Error(`the PDF engine could not be loaded (${response.status})`)
        return response.arrayBuffer()
      }),
      blobWorkers(),
    ])
    const local = URL.createObjectURL(new Blob([wasm], { type: "application/wasm" }))
    // No font fallback: it would fetch fonts from a CDN. PDFium's own standard fonts cover PDFs that embed none.
    if (inWorker) {
      const { createPdfiumEngine } = await import("@embedpdf/engines/pdfium-worker-engine")
      return createPdfiumEngine(local, { fontFallback: null })
    }
    // No workers (a webview-4j window): PDFium on the page's own thread, its images encoded there too.
    const { createPdfiumEngine } = await import("@embedpdf/engines/pdfium-direct-engine")
    return createPdfiumEngine(local, { fontFallback: null, encoderPoolSize: 0 })
  })()
  engine.catch(() => {
    engine = undefined
  })
  return engine
}

/** Whether `src` is a PDF's bytes as base64 (no "data:" in front), not a URL: every PDF starts "%PDF", "JVBER" in base64. */
export function isBase64(src: string): boolean {
  return /^\s*JVBER[A-Za-z0-9+/=\s]*$/.test(src.slice(0, 512))
}

/** The bytes of a PDF given as a URL (http(s), relative, blob:, data:) or as base64. */
export async function readPdf(src: string): Promise<ArrayBuffer> {
  if (isBase64(src)) {
    const binary = atob(src.replace(/\s+/g, ""))
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    return bytes.buffer
  }
  const response = await fetch(src)
  if (!response.ok) throw new Error(`the PDF could not be read (${response.status})`)
  return response.arrayBuffer()
}

// Each PDF opened once for thumbnails, by its src; each page image once, by src, page, width and pixel ratio.
const documents = new Map<string, Promise<PdfDocumentObject>>()
const images = new Map<string, Promise<string | null>>()
let opened = 0

/** A PDF opened in the shared engine, for thumbnails (the viewer opens its own). */
export function openPdf(src: string): Promise<PdfDocumentObject> {
  let found = documents.get(src)
  if (!found) {
    found = Promise.all([loadPdfEngine(), readPdf(src)]).then(([pdf, content]) =>
      pdf.openDocumentBuffer({ id: `thumbnail-${++opened}`, content }).toPromise()
    )
    found.catch(() => documents.delete(src))
    documents.set(src, found)
  }
  return found
}

/** How many pages a PDF has. */
export async function pdfPageCount(src: string): Promise<number> {
  return (await openPdf(src)).pageCount
}

/** A page as an image (a blob: URL of a PNG) `width` CSS pixels wide; null when it has no such page. */
export function pdfPageImage({ src, page, width, dpr = window.devicePixelRatio || 1 }: { src: string; page: number; width: number; dpr?: number }) {
  const key = `${src}#${page}@${width}x${dpr}`
  let found = images.get(key)
  if (!found) {
    found = (async () => {
      const [pdf, doc] = await Promise.all([loadPdfEngine(), openPdf(src)])
      const pageObject = doc.pages[page - 1]
      if (!pageObject) return null
      const blob = await pdf
        .renderThumbnail(doc, pageObject, { dpr, imageType: "image/png", scaleFactor: width / pageObject.size.width, withAnnotations: true })
        .toPromise()
      return URL.createObjectURL(blob)
    })()
    found.catch(() => images.delete(key))
    images.set(key, found)
  }
  return found
}
