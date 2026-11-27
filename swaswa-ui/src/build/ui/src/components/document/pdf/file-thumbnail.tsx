import * as React from "react"

import { cn } from "../../../lib/utils"
import "./pdf.css"

/** What a thumbnail is of: its name (its extension is shown while there is no picture) and its media type. */
type ThumbnailFile = { name: string; type?: string }

type FileThumbnailProps = Omit<React.ComponentProps<"div">, "children"> & {
  file?: ThumbnailFile | File
  /** The picture: an image URL (a blob:, a data: URL, a page drawn by PdfThumbnail). */
  previewImageUrl?: string | null
  /** Drawn instead of a picture. */
  previewContent?: React.ReactNode
  /** Width over height of the preview (1, a square, unless set). */
  previewAspectRatio?: number
  /** "cover" fills the box, cutting the picture's edges; "contain" shows all of it. */
  fit?: "cover" | "contain"
  /** Still being made: a shimmer instead. */
  isLoading?: boolean
  hasError?: boolean
}

// Pictures already shown once: a thumbnail shown again (a tab switched back to) does not fade in again.
const revealed = new Set<string>()

/** "PDF" for report.pdf: what shows while there is no picture. */
function extensionOf(file: ThumbnailFile | File | undefined): string {
  const name = file?.name ?? ""
  const dot = name.lastIndexOf(".")
  if (dot > 0 && dot < name.length - 1) return name.slice(dot + 1).toUpperCase().slice(0, 4)
  const type = file?.type ?? ""
  return type.includes("/") ? type.split("/")[1].toUpperCase().slice(0, 4) : ""
}

/**
 * A file's preview: its picture (or anything drawn) in a box of a set shape, a shimmer while it is made, and the
 * file's extension when there is none. The picture fades in the first time it is shown.
 */
function FileThumbnail({
  file,
  previewImageUrl,
  previewContent,
  previewAspectRatio,
  fit = "cover",
  isLoading = false,
  hasError = false,
  className,
  style,
  ...props
}: FileThumbnailProps) {
  const [loaded, setLoaded] = React.useState<string | null>(() => (previewImageUrl && revealed.has(previewImageUrl) ? previewImageUrl : null))
  const [failed, setFailed] = React.useState<string | null>(null)
  const image = React.useRef<HTMLImageElement>(null)
  const imageFailed = Boolean(previewImageUrl && failed === previewImageUrl)
  const imageLoading = Boolean(previewImageUrl && loaded !== previewImageUrl && !imageFailed && !revealed.has(previewImageUrl))
  const loading = isLoading || imageLoading
  const none = !loading && (hasError || imageFailed || (!previewImageUrl && !previewContent))

  const done = React.useCallback((img: HTMLImageElement, url: string) => {
    if (img.naturalWidth > 0) {
      revealed.add(url)
      setFailed(null)
      requestAnimationFrame(() => setLoaded(url))
    } else {
      setFailed(url)
    }
  }, [])

  // an image from the cache is complete before React sees its load event
  React.useEffect(() => {
    if (image.current?.complete && previewImageUrl) done(image.current, previewImageUrl)
  }, [previewImageUrl, done])

  return (
    <div className={cn("nt-pdf-file-thumb", className)} style={style} data-fit={fit} {...props}>
      <div className="nt-pdf-file-thumb-preview" style={{ aspectRatio: String(previewAspectRatio ?? 1) }}>
        {previewImageUrl && (
          <img
            ref={image}
            src={previewImageUrl}
            alt=""
            draggable={false}
            decoding="async"
            data-shown={!loading || undefined}
            onLoad={(e) => done(e.currentTarget, previewImageUrl)}
            onError={() => {
              revealed.delete(previewImageUrl)
              setFailed(previewImageUrl)
            }}
          />
        )}
        {previewContent && (
          <div className="nt-pdf-file-thumb-content" data-shown={!loading || undefined}>
            {previewContent}
          </div>
        )}
        {loading && <div className="nt-pdf-file-thumb-loading" aria-hidden="true" />}
        {none && (
          <div className="nt-pdf-file-thumb-none" aria-hidden="true">
            {extensionOf(file)}
          </div>
        )}
      </div>
    </div>
  )
}

type PdfThumbnailProps = Omit<FileThumbnailProps, "previewImageUrl" | "previewContent" | "isLoading" | "hasError" | "file"> & {
  /** The PDF: a URL, a data: URL, or its bytes as base64. */
  src: string
  /** Which page (1, the first, unless set). */
  page?: number
  /** Its name (shown while there is no picture, and to screen readers). */
  fileName?: string
  /** How wide the page is drawn, CSS pixels (the box's own width is set with className or style). */
  renderWidth?: number
  /** The PDF is open: how many pages it has. */
  onLoad?: (pages: number) => void
  onError?: (message: string) => void
}

/**
 * A page of a PDF as a picture (the first, unless set): drawn by the PDF engine, which loads the first time one
 * is shown. Its box takes the page's shape unless previewAspectRatio is given.
 */
function PdfThumbnail({ src, page = 1, fileName, renderWidth = 240, onLoad, onError, previewAspectRatio, fit = "contain", ...props }: PdfThumbnailProps) {
  const [image, setImage] = React.useState<{ key: string; url: string | null; ratio?: number; error?: string }>()
  const key = `${src.slice(0, 128)}:${src.length}#${page}@${renderWidth}`
  const latest = React.useRef({ onLoad, onError })
  latest.current = { onLoad, onError }

  React.useEffect(() => {
    let gone = false
    import("./engine")
      .then(async (engine) => {
        const doc = await engine.openPdf(src)
        if (gone) return
        latest.current.onLoad?.(doc.pageCount)
        const size = doc.pages[page - 1]?.size
        const url = await engine.pdfPageImage({ src, page, width: renderWidth })
        if (!gone) setImage({ key, url, ratio: size ? size.width / size.height : undefined })
      })
      .catch((e: unknown) => {
        if (gone) return
        const message = e instanceof Error ? e.message : String(e)
        setImage({ key, url: null, error: message })
        latest.current.onError?.(message)
      })
    return () => {
      gone = true
    }
  }, [key]) // eslint-disable-line react-hooks/exhaustive-deps

  const current = image?.key === key ? image : undefined
  return (
    <FileThumbnail
      role="img"
      aria-label={fileName ? `${fileName}, page ${page}` : `Page ${page}`}
      file={{ name: fileName ?? "document.pdf", type: "application/pdf" }}
      previewImageUrl={current?.url}
      isLoading={!current}
      hasError={Boolean(current?.error) || (current != null && !current.url)}
      previewAspectRatio={previewAspectRatio ?? current?.ratio ?? 8.5 / 11}
      fit={fit}
      {...props}
    />
  )
}

export { FileThumbnail, PdfThumbnail }
export type { FileThumbnailProps, PdfThumbnailProps, ThumbnailFile }
