// The Word components for screens built in Java (io.joshuasalcedo.webview4j.plugin.document): each
// reads the node Java sent ({t, p, c}) and sends the user's actions back. Bytes travel as base64: the file comes
// as a data: URL (prop "src"), Save and Download send base64, which the Java side decodes.
import * as React from "react"

import { DocumentViewerSidebar, DocumentViewerSidebarList, type DocumentViewerSidebarEntry } from "./document-viewer-sidebar"
import { DocxAnnotationCard, type DocxAnnotationKind } from "./docx-annotation-card"
import { DocxEditor } from "./docx-editor"
import { DocxLayoutBlocks } from "./docx-layout-blocks"
import { DocxViewer } from "./docx-viewer"
import { toBase64 } from "./load"
import type { DocxBlock } from "./model"
import type { DocxPanel, DocxZoom } from "./types"

type JavaNode = { t: string; p: Record<string, unknown>; c?: unknown[] }
type Send = (handler: unknown, value?: unknown) => void
type JavaProps = { node: JavaNode; send: Send }

const str = (v: unknown) => (typeof v === "string" ? v : undefined)
const num = (v: unknown) => (typeof v === "number" ? v : undefined)
const flag = (v: unknown, otherwise: boolean) => (typeof v === "boolean" ? v : otherwise)

/** Sizing every component takes: grow, width, height and their limits. */
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

const zoomOf = (v: unknown): DocxZoom | undefined =>
  v === "fit-width" || v === "fit-page" ? v : typeof v === "number" ? v : undefined

const panelOf = (v: unknown): DocxPanel | null | undefined =>
  v === "pages" || v === "outline" || v === "sections" || v === "comments" ? v : undefined

/** The zoom Java set, kept here while the person zooms; Java setting another one wins. */
function useJavaZoom(p: Record<string, unknown>, send: Send) {
  const given = zoomOf(p.zoom)
  const [zoom, setZoom] = React.useState<DocxZoom>(given ?? "fit-width")
  React.useEffect(() => {
    if (given !== undefined) setZoom(given)
  }, [given])
  return {
    zoom,
    onZoomChange: (next: DocxZoom) => {
      setZoom(next)
      if (typeof p.onZoomChange === "string") send(p.onZoomChange, next)
    },
  }
}

/** What the viewer and the editor both read from Java. */
function common(p: Record<string, unknown>, send: Send) {
  return {
    src: str(p.src),
    fileName: str(p.fileName),
    "aria-label": str(p.label),
    showToolbar: flag(p.showToolbar, true),
    defaultPanel: panelOf(p.panel) ?? null,
    showComments: flag(p.showComments, true),
    showDownload: flag(p.showDownload, true),
    showUpload: flag(p.showUpload, true),
    style: sizing(p),
    onDownload:
      typeof p.onDownload === "string"
        ? (bytes: Uint8Array, name: string) => send(p.onDownload, { fileName: name, base64: toBase64(bytes) })
        : undefined,
    onUpload:
      typeof p.onUpload === "string"
        ? (name: string, bytes: Uint8Array) => send(p.onUpload, { fileName: name, base64: toBase64(bytes) })
        : undefined,
    onPageChange: typeof p.onPageChange === "string" ? (page: number) => send(p.onPageChange, page) : undefined,
    onError: typeof p.onError === "string" ? (message: string) => send(p.onError, message) : undefined,
  }
}

/** A DocxViewer from Java ("docxViewer"). */
export function JavaDocxViewer({ node, send }: JavaProps) {
  const p = node.p
  const zoom = useJavaZoom(p, send)
  return (
    <DocxViewer
      {...common(p, send)}
      {...zoom}
      showTrackedChanges={flag(p.showTrackedChanges, true)}
      darkPages={typeof p.darkPages === "boolean" ? p.darkPages : undefined}
    />
  )
}

/** A DocxEditor from Java ("docxEditor"): Save sends the .docx as base64, which Java gets as bytes. */
export function JavaDocxEditor({ node, send }: JavaProps) {
  const p = node.p
  const zoom = useJavaZoom(p, send)
  return (
    <DocxEditor
      {...common(p, send)}
      {...zoom}
      showTrackedChanges={flag(p.showTrackedChanges, false)}
      readOnly={typeof p.readOnly === "boolean" ? p.readOnly : undefined}
      showReadOnlyToggle={flag(p.showReadOnlyToggle, true)}
      onReadOnlyChange={typeof p.onReadOnlyChange === "string" ? (v) => send(p.onReadOnlyChange, v) : undefined}
      onSave={typeof p.onSave === "string" ? (bytes, name) => send(p.onSave, { fileName: name, base64: toBase64(bytes) }) : undefined}
      onChange={typeof p.onChange === "string" ? () => send(p.onChange) : undefined}
      onDirtyChange={typeof p.onDirtyChange === "string" ? (dirty) => send(p.onDirtyChange, dirty) : undefined}
    />
  )
}

const KINDS = new Set<DocxAnnotationKind>([
  "comment", "reply", "resolved", "insertion", "deletion", "move-from", "move-to", "format-change", "paragraph-format-change",
])

/** A DocxAnnotationCard from Java ("docxAnnotationCard"). */
export function JavaDocxAnnotationCard({ node, send }: JavaProps) {
  const p = node.p
  const kind = str(p.kind) as DocxAnnotationKind | undefined
  return (
    <DocxAnnotationCard
      kind={kind && KINDS.has(kind) ? kind : "comment"}
      author={str(p.author)}
      date={str(p.date)}
      anchorText={str(p.anchorText)}
      text={str(p.text) ?? ""}
      badge={str(p.badge)}
      active={p.active === true}
      onClick={typeof p.onClick === "string" ? () => send(p.onClick) : undefined}
      style={sizing(p)}
    />
  )
}

/**
 * A DocumentViewerSidebar from Java ("documentViewerSidebar"): its entries (prop "entries") as a list, the chosen
 * one sent back by id. Java components put in it (node.c) come as `children`, which the caller renders.
 */
export function JavaDocumentViewerSidebar({ node, send, children }: JavaProps & { children?: React.ReactNode }) {
  const p = node.p
  const entries: DocumentViewerSidebarEntry[] = Array.isArray(p.entries)
    ? (p.entries as Record<string, unknown>[]).map((e, i) => ({
        id: str(e.id) ?? String(i),
        label: str(e.label) ?? "",
        description: str(e.description),
        level: num(e.level),
        badge: typeof e.badge === "number" || typeof e.badge === "string" ? e.badge : undefined,
      }))
    : []
  return (
    <DocumentViewerSidebar
      aria-label={str(p.label) ?? "Document"}
      open={flag(p.open, true)}
      inline={flag(p.inline, true)}
      width={num(p.width) ?? 220}
      side={p.side === "right" ? "right" : "left"}
      title={str(p.title)}
      style={{ ...sizing(p), ...(typeof p.width === "number" ? { width: undefined } : {}) }}
    >
      <div className="nt-docx-sidebar-scroll">
        {entries.length > 0 && (
          <DocumentViewerSidebarList
            entries={entries}
            active={str(p.active)}
            onSelect={typeof p.onSelect === "string" ? (id) => send(p.onSelect, id) : undefined}
          />
        )}
        {children}
        {!entries.length && !children && <div className="nt-docx-sidebar-empty">{str(p.empty) ?? "Nothing here"}</div>}
      </div>
    </DocumentViewerSidebar>
  )
}

const blockValue = (block: DocxBlock) => ({
  id: block.id,
  type: block.type,
  text: block.text,
  nodeIndex: block.nodeIndex ?? -1,
  level: block.level ?? 0,
})

/** A DocxLayoutBlocks from Java ("docxLayoutBlocks"): the block chosen goes back as {id, type, text, nodeIndex, level}. */
export function JavaDocxLayoutBlocks({ node, send }: JavaProps) {
  const p = node.p
  return (
    <DocxLayoutBlocks
      src={str(p.src)}
      activeBlockId={str(p.active)}
      showFilters={flag(p.showFilters, true)}
      height={num(p.height) ?? (p.grow === true ? "100%" : 420)}
      style={sizing(p)}
      aria-label={str(p.label)}
      onBlockSelect={typeof p.onSelect === "string" ? (b) => send(p.onSelect, blockValue(b)) : undefined}
      onBlockFocus={typeof p.onFocus === "string" ? (b) => send(p.onFocus, blockValue(b)) : undefined}
      onError={typeof p.onError === "string" ? (m) => send(p.onError, m) : undefined}
    />
  )
}
