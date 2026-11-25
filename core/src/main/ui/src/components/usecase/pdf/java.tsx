import * as React from "react"

import { BoundingBoxCitations, type CitationField, type JsonValue } from "./bounding-box-citations"
import { DocumentSplits, type DocumentSplit } from "./document-splits"
import { FileThumbnail, PdfThumbnail } from "./file-thumbnail"
import { PdfViewer, type PdfViewerHandle, type PdfZoom } from "./pdf-viewer"

/*
 * The PDF components as Java's screens give them (io.joshuasalcedo.webview4j.ui.components.usecase.pdf): a node's
 * props in, what the person does back to Java through send. One per node type: pdfViewer, fileThumbnail,
 * boundingBoxCitations, documentSplits.
 */

type Node = { t: string; p: Record<string, unknown>; c?: unknown[] }
type Send = (handler: unknown, value?: unknown) => void
type JavaProps = { node: Node; send: Send }

const str = (v: unknown) => (typeof v === "string" ? v : undefined)
const num = (v: unknown) => (typeof v === "number" ? v : undefined)
/** A flag that is on unless Java set it false. */
const on = (v: unknown) => v !== false
const handler = (v: unknown) => typeof v === "string"

/** The sizing every Java component takes: grow, width, height and their limits. */
function sizing(p: Record<string, unknown>): React.CSSProperties {
  return {
    ...(p.grow === true ? { flex: 1, minWidth: 0, minHeight: 0 } : {}),
    ...(typeof p.width === "number" ? { width: p.width } : {}),
    ...(typeof p.height === "number" ? { height: p.height } : {}),
    ...(typeof p.minWidth === "number" ? { minWidth: p.minWidth } : {}),
    ...(typeof p.maxWidth === "number" ? { maxWidth: p.maxWidth } : {}),
    ...(typeof p.minHeight === "number" ? { minHeight: p.minHeight } : {}),
    ...(typeof p.maxHeight === "number" ? { maxHeight: p.maxHeight } : {}),
  }
}

/** A file's bytes as base64, for Java. */
function base64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result).replace(/^data:[^,]*,/, ""))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
}

function zoomOf(v: unknown): PdfZoom | undefined {
  if (typeof v === "number") return v
  return v === "fit-page" || v === "fit-width" || v === "automatic" ? v : undefined
}

/** A value that changes only when Java's changes (compared as JSON), so the page's own copy is kept between. */
function useFromJava<T>(value: T): [T, React.Dispatch<React.SetStateAction<T>>] {
  const [own, setOwn] = React.useState(value)
  const given = JSON.stringify(value)
  React.useEffect(() => setOwn(value), [given]) // eslint-disable-line react-hooks/exhaustive-deps
  return [own, setOwn]
}

/** A PdfViewer from Java: the page in view and the page count go back; a page given scrolls there. */
export function JavaPdfViewer({ node, send }: JavaProps) {
  const p = node.p
  const viewer = React.useRef<PdfViewerHandle>(null)
  const page = num(p.page)
  React.useEffect(() => {
    if (page) viewer.current?.scrollToPage(page)
  }, [page])
  const onPage = React.useCallback((n: number) => handler(p.onPageChange) && send(p.onPageChange, n), [p.onPageChange, send])
  return (
    <PdfViewer
      ref={viewer}
      src={str(p.src)}
      fileName={str(p.fileName)}
      defaultZoom={zoomOf(p.zoom)}
      showToolbar={on(p.toolbar)}
      showDownload={on(p.download)}
      showRotateControls={on(p.rotate)}
      showUpload={p.upload === true}
      defaultShowThumbnails={p.thumbnails === true}
      initialSearchQuery={str(p.search)}
      aria-label={str(p.label)}
      onActivePageChange={onPage}
      onDocumentLoadSuccess={handler(p.onLoad) ? (n) => send(p.onLoad, n) : undefined}
      onDocumentLoadError={handler(p.onError) ? (message) => send(p.onError, message) : undefined}
      onPdfUpload={handler(p.onOpen) ? (file) => void base64(file).then((data) => send(p.onOpen, { name: file.name, data })) : undefined}
      onDownload={handler(p.onDownload) ? (blob, name) => void base64(blob).then((data) => send(p.onDownload, { name, data })) : undefined}
      style={{ minHeight: 320, ...sizing(p) }}
    />
  )
}

/** A file's thumbnail from Java: a PDF's page drawn by the engine, else the src as an image. */
export function JavaFileThumbnail({ node, send }: JavaProps) {
  const p = node.p
  const src = str(p.src) ?? ""
  const name = str(p.fileName) ?? ""
  const pdf =
    p.pdf === true ||
    name.toLowerCase().endsWith(".pdf") ||
    src.startsWith("data:application/pdf") ||
    /^\s*JVBER/.test(src.slice(0, 16)) ||
    /\.pdf([?#].*)?$/i.test(src)
  const common = {
    previewAspectRatio: num(p.aspectRatio),
    fit: (str(p.fit) === "cover" ? "cover" : "contain") as "cover" | "contain",
    style: { width: 160, ...sizing(p) },
  }
  if (pdf && src) {
    return (
      <PdfThumbnail
        src={src}
        page={num(p.page) ?? 1}
        fileName={name || undefined}
        onLoad={handler(p.onLoad) ? (n) => send(p.onLoad, n) : undefined}
        {...common}
      />
    )
  }
  return <FileThumbnail role="img" aria-label={name || "File"} file={{ name: name || "file" }} previewImageUrl={src || null} {...common} />
}

/** BoundingBoxCitations from Java: the field chosen and each corrected value go back. */
export function JavaBoundingBoxCitations({ node, send }: JavaProps) {
  const p = node.p
  const [active, setActive] = useFromJava(str(p.active))
  const fields = React.useMemo(() => (Array.isArray(p.fields) ? (p.fields as CitationField[]) : []), [JSON.stringify(p.fields)]) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <BoundingBoxCitations
      src={str(p.src)}
      fileName={str(p.fileName)}
      fields={fields}
      activeKey={active ?? ""}
      onActiveChange={(key) => {
        setActive(key)
        if (handler(p.onActiveChange)) send(p.onActiveChange, key ?? null)
      }}
      showExpected={p.review === true}
      showAllBoxes={on(p.allBoxes)}
      dividerPosition={num(p.dividerPosition)}
      onExpectedChange={handler(p.onExpectedChange) ? (key: string, value: JsonValue) => send(p.onExpectedChange, { key, value }) : undefined}
      viewerProps={{ defaultZoom: zoomOf(p.zoom), showUpload: false }}
      aria-label={str(p.label)}
      style={{ minHeight: 420, ...sizing(p) }}
    />
  )
}

/** DocumentSplits from Java: kept here while pages are dragged, each change sent to Java. */
export function JavaDocumentSplits({ node, send }: JavaProps) {
  const p = node.p
  const [splits, setSplits] = useFromJava(Array.isArray(p.splits) ? (p.splits as DocumentSplit[]) : [])
  return (
    <DocumentSplits
      src={str(p.src)}
      splits={splits}
      onSplitsChange={(next) => {
        setSplits(next)
        if (handler(p.onChange)) send(p.onChange, next)
      }}
      onSelectPage={handler(p.onSelectPage) ? (n) => send(p.onSelectPage, n) : undefined}
      canAdd={on(p.canAdd)}
      editableTitles={on(p.editableTitles)}
      aria-label={str(p.label) ?? "Splits"}
      style={{ minHeight: 240, ...sizing(p) }}
    />
  )
}
