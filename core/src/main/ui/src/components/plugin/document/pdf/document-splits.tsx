import * as React from "react"

import { cn } from "../../../../lib/utils"
import { Button } from "../../../button"
import { useContextMenu, type MenuEntry } from "../../../context-menu"
import { FileThumbnail, PdfThumbnail } from "./file-thumbnail"
import { GripIcon, PlusIcon, TrashIcon } from "./icons"
import "./pdf.css"

/** One document the PDF is split into: its name and its pages (numbers, 1 for the first), in their order. */
type DocumentSplit = { id: string; title: string; pages: number[] }

type DocumentSplitsProps = Omit<React.ComponentProps<"div">, "onChange"> & {
  splits: DocumentSplit[]
  onSplitsChange: (splits: DocumentSplit[]) => void
  /** The PDF, to draw each page's thumbnail (or give thumbnailImages). */
  src?: string
  /** Each page's picture, by page number, when they are made elsewhere. */
  thumbnailImages?: Record<number, string>
  /** A page was clicked (e.g. to show it in a PdfViewer next to it). */
  onSelectPage?: (pageNumber: number) => void
  /** The Add split button (on unless false). */
  canAdd?: boolean
  /** The names can be changed (on unless false). */
  editableTitles?: boolean
}

/** Splits of about a third each: "Pages 1-3", "Pages 4-6", ... */
function createInitialSplits(pageCount: number, parts = 3): DocumentSplit[] {
  if (pageCount <= 0) return [{ id: "split-1", title: "Split 1", pages: [] }]
  const size = Math.max(1, Math.ceil(pageCount / parts))
  const splits: DocumentSplit[] = []
  for (let first = 1; first <= pageCount; first += size) {
    const last = Math.min(pageCount, first + size - 1)
    const pages = Array.from({ length: last - first + 1 }, (_, i) => first + i)
    splits.push({ id: `split-${splits.length + 1}`, title: first === last ? `Page ${first}` : `Pages ${first}-${last}`, pages })
  }
  return splits
}

/** "Pages 1, 2, 5" (or "No pages"). */
function pagesLabel(pages: number[]) {
  return pages.length === 0 ? "No pages" : `${pages.length === 1 ? "Page" : "Pages"} ${pages.join(", ")}`
}

/** The splits with `page` taken out of its split and put into `to` at `index`. */
function movePage(splits: DocumentSplit[], page: number, to: string, index: number): DocumentSplit[] {
  const from = splits.find((s) => s.pages.includes(page))
  const fromIndex = from?.pages.indexOf(page) ?? -1
  // in its own split, the places after it move one down once it is taken out
  const at = from?.id === to && fromIndex < index ? index - 1 : index
  return splits.map((s) => {
    const pages = s.pages.filter((p) => p !== page)
    if (s.id === to) pages.splice(Math.max(0, Math.min(at, pages.length)), 0, page)
    return pages.length === s.pages.length && s.id !== to ? s : { ...s, pages }
  })
}

function moveSplit(splits: DocumentSplit[], id: string, index: number): DocumentSplit[] {
  const from = splits.findIndex((s) => s.id === id)
  if (from < 0) return splits
  const next = splits.slice()
  const [moved] = next.splice(from, 1)
  next.splice(from < index ? index - 1 : index, 0, moved)
  return next
}

const same = (a: DocumentSplit[], b: DocumentSplit[]) => JSON.stringify(a) === JSON.stringify(b)

type Drag = { kind: "page"; page: number } | { kind: "split"; id: string }
type Drop = { kind: "page"; split: string; index: number } | { kind: "split"; index: number }

/**
 * The documents a PDF is split into, each a card of its pages: pages are dragged between and within splits,
 * splits are dragged by their handle to reorder them; Alt+arrow keys do the same from the keyboard (← → in a
 * split, ↑ ↓ to the split before or after), and each page and split has a menu (right-click) with the moves.
 */
function DocumentSplits({
  splits,
  onSplitsChange,
  src,
  thumbnailImages,
  onSelectPage,
  canAdd = true,
  editableTitles = true,
  className,
  ...props
}: DocumentSplitsProps) {
  const [drag, setDrag] = React.useState<Drag | null>(null)
  const [drop, setDrop] = React.useState<Drop | null>(null)
  const focusPage = React.useRef<number | null>(null)
  const root = React.useRef<HTMLDivElement>(null)

  const change = (next: DocumentSplit[]) => {
    if (!same(next, splits)) onSplitsChange(next)
  }

  // the page moved from the keyboard keeps the focus
  React.useLayoutEffect(() => {
    if (focusPage.current == null) return
    root.current?.querySelector<HTMLElement>(`[data-split-page="${focusPage.current}"]`)?.focus()
    focusPage.current = null
  })

  const end = () => {
    setDrag(null)
    setDrop(null)
  }

  const finish = () => {
    if (drag?.kind === "page" && drop?.kind === "page") change(movePage(splits, drag.page, drop.split, drop.index))
    if (drag?.kind === "split" && drop?.kind === "split") change(moveSplit(splits, drag.id, drop.index))
    end()
  }

  const add = () =>
    change([...splits, { id: `split-${Date.now().toString(36)}`, title: `Split ${splits.length + 1}`, pages: [] }])

  return (
    <div ref={root} className={cn("nt-pdf-splits", className)} {...props}>
      {canAdd && (
        <div className="nt-pdf-splits-bar">
          <Button onClick={add}>
            <PlusIcon />
            Add split
          </Button>
        </div>
      )}
      <div className="nt-pdf-splits-list" role="list" aria-label="Splits">
        {splits.map((split, splitIndex) => (
          <SplitCard
            key={split.id}
            split={split}
            splits={splits}
            index={splitIndex}
            src={src}
            thumbnailImages={thumbnailImages}
            editableTitle={editableTitles}
            drag={drag}
            drop={drop}
            onDrag={setDrag}
            onDrop={setDrop}
            onFinish={finish}
            onEnd={end}
            onChange={change}
            onSelectPage={onSelectPage}
            onKeyboardMove={(next, page) => {
              focusPage.current = page
              change(next)
            }}
          />
        ))}
        {drag?.kind === "split" && drop?.kind === "split" && drop.index === splits.length && <div className="nt-pdf-split-drop-line" />}
      </div>
    </div>
  )
}

type SplitCardProps = {
  split: DocumentSplit
  splits: DocumentSplit[]
  index: number
  src?: string
  thumbnailImages?: Record<number, string>
  editableTitle: boolean
  drag: Drag | null
  drop: Drop | null
  onDrag: (drag: Drag | null) => void
  onDrop: (drop: Drop | null) => void
  onFinish: () => void
  onEnd: () => void
  onChange: (splits: DocumentSplit[]) => void
  onSelectPage?: (page: number) => void
  onKeyboardMove: (splits: DocumentSplit[], page: number) => void
}

function SplitCard({ split, splits, index, src, thumbnailImages, editableTitle, drag, drop, onDrag, onDrop, onFinish, onEnd, onChange, onSelectPage, onKeyboardMove }: SplitCardProps) {
  const card = React.useRef<HTMLDivElement>(null)
  const canRemove = splits.length > 1
  const remove = () => canRemove && onChange(splits.filter((s) => s.id !== split.id))
  const menu = useContextMenu((): MenuEntry[] => [
    { label: "Move up", disabled: index === 0, onSelect: () => onChange(moveSplit(splits, split.id, index - 1)) },
    { label: "Move down", disabled: index === splits.length - 1, onSelect: () => onChange(moveSplit(splits, split.id, index + 2)) },
    "separator",
    { label: "Remove", disabled: !canRemove, onSelect: remove },
  ])
  const pageTarget = drop?.kind === "page" && drop.split === split.id ? drop.index : null
  const lineBefore = drag?.kind === "split" && drop?.kind === "split" && drop.index === index

  return (
    <>
      {lineBefore && <div className="nt-pdf-split-drop-line" />}
      <section
        ref={card}
        role="listitem"
        aria-label={split.title}
        className="nt-pdf-split"
        data-dragging={(drag?.kind === "split" && drag.id === split.id) || undefined}
        onDragOver={(e) => {
          if (drag?.kind !== "split") return
          e.preventDefault()
          const box = e.currentTarget.getBoundingClientRect()
          onDrop({ kind: "split", index: e.clientY < box.top + box.height / 2 ? index : index + 1 })
        }}
        onDrop={(e) => {
          if (drag?.kind !== "split") return
          e.preventDefault()
          onFinish()
        }}
      >
        <header className="nt-pdf-split-head" {...menu}>
          <span
            className="nt-pdf-split-grip"
            draggable
            title="Drag to reorder"
            aria-hidden="true"
            onDragStart={(e) => {
              e.dataTransfer.effectAllowed = "move"
              e.dataTransfer.setData("text/plain", split.title)
              if (card.current) e.dataTransfer.setDragImage(card.current, 16, 16)
              onDrag({ kind: "split", id: split.id })
            }}
            onDragEnd={onEnd}
          >
            <GripIcon />
          </span>
          <span className="nt-pdf-split-names">
            {editableTitle ? (
              <input
                className="nt-pdf-split-title"
                aria-label="Name"
                value={split.title}
                onChange={(e) => onChange(splits.map((s) => (s.id === split.id ? { ...s, title: e.target.value } : s)))}
              />
            ) : (
              <span className="nt-pdf-split-title">{split.title}</span>
            )}
            <span className="nt-pdf-split-pages">{pagesLabel(split.pages)}</span>
          </span>
          <span className="nt-pdf-split-count" aria-label={`${split.pages.length} pages`}>
            {split.pages.length}
          </span>
          <Button variant="subtle" className="nt-pdf-icon-button" aria-label={`Remove ${split.title}`} title="Remove" disabled={!canRemove} onClick={remove}>
            <TrashIcon />
          </Button>
        </header>
        <div
          className="nt-pdf-split-strip"
          data-over={pageTarget != null || undefined}
          onDragOver={(e) => {
            if (drag?.kind !== "page") return
            e.preventDefault()
            e.dataTransfer.dropEffect = "move"
            // over the strip itself, not a page: at its end
            if (e.target === e.currentTarget) onDrop({ kind: "page", split: split.id, index: split.pages.length })
          }}
          onDrop={(e) => {
            if (drag?.kind !== "page") return
            e.preventDefault()
            onFinish()
          }}
        >
          {split.pages.length === 0 && <span className="nt-pdf-split-empty">Drop pages here</span>}
          {split.pages.map((page, pageIndex) => (
            <SplitPage
              key={page}
              page={page}
              split={split}
              splits={splits}
              index={pageIndex}
              src={src}
              image={thumbnailImages?.[page]}
              dragging={drag?.kind === "page" && drag.page === page}
              lineBefore={pageTarget === pageIndex}
              lineAfter={pageTarget === split.pages.length && pageIndex === split.pages.length - 1}
              onDrag={onDrag}
              onDrop={onDrop}
              onEnd={onEnd}
              dragKind={drag?.kind}
              onChange={onChange}
              onSelect={onSelectPage}
              onKeyboardMove={onKeyboardMove}
            />
          ))}
        </div>
      </section>
    </>
  )
}

type SplitPageProps = {
  page: number
  split: DocumentSplit
  splits: DocumentSplit[]
  index: number
  src?: string
  image?: string
  dragging: boolean
  lineBefore: boolean
  lineAfter: boolean
  dragKind?: Drag["kind"]
  onDrag: (drag: Drag | null) => void
  onDrop: (drop: Drop | null) => void
  onEnd: () => void
  onChange: (splits: DocumentSplit[]) => void
  onSelect?: (page: number) => void
  onKeyboardMove: (splits: DocumentSplit[], page: number) => void
}

function SplitPage({ page, split, splits, index, src, image, dragging, lineBefore, lineAfter, dragKind, onDrag, onDrop, onEnd, onChange, onSelect, onKeyboardMove }: SplitPageProps) {
  const at = splits.findIndex((s) => s.id === split.id)
  const menu = useContextMenu((): MenuEntry[] => [
    { label: "Move left", disabled: index === 0, onSelect: () => onChange(movePage(splits, page, split.id, index - 1)) },
    { label: "Move right", disabled: index === split.pages.length - 1, onSelect: () => onChange(movePage(splits, page, split.id, index + 2)) },
    ...(splits.length > 1 ? (["separator"] as MenuEntry[]) : []),
    ...splits
      .filter((s) => s.id !== split.id)
      .map((s): MenuEntry => ({ label: `Move to ${s.title}`, onSelect: () => onChange(movePage(splits, page, s.id, s.pages.length)) })),
  ])
  return (
    <button
      type="button"
      className="nt-pdf-split-page"
      data-split-page={page}
      data-dragging={dragging || undefined}
      data-line={lineBefore ? "before" : lineAfter ? "after" : undefined}
      aria-label={`Page ${page}`}
      aria-keyshortcuts="Alt+ArrowLeft Alt+ArrowRight Alt+ArrowUp Alt+ArrowDown"
      draggable
      onClick={() => onSelect?.(page)}
      onDragStart={(e) => {
        e.dataTransfer.effectAllowed = "move"
        e.dataTransfer.setData("text/plain", `Page ${page}`)
        onDrag({ kind: "page", page })
      }}
      onDragEnd={onEnd}
      onDragOver={(e) => {
        if (dragKind !== "page") return
        e.preventDefault()
        e.stopPropagation()
        const box = e.currentTarget.getBoundingClientRect()
        onDrop({ kind: "page", split: split.id, index: e.clientX < box.left + box.width / 2 ? index : index + 1 })
      }}
      onKeyDown={(e) => {
        if (!e.altKey) return
        let next: DocumentSplit[] | null = null
        if (e.key === "ArrowLeft" && index > 0) next = movePage(splits, page, split.id, index - 1)
        else if (e.key === "ArrowRight" && index < split.pages.length - 1) next = movePage(splits, page, split.id, index + 2)
        else if (e.key === "ArrowUp" && at > 0) next = movePage(splits, page, splits[at - 1].id, splits[at - 1].pages.length)
        else if (e.key === "ArrowDown" && at < splits.length - 1) next = movePage(splits, page, splits[at + 1].id, 0)
        if (!next) return
        e.preventDefault()
        onKeyboardMove(next, page)
      }}
      {...menu}
    >
      {image || !src ? (
        <FileThumbnail className="nt-pdf-split-image" file={{ name: `page-${page}.pdf` }} previewImageUrl={image} isLoading={false} previewAspectRatio={72 / 92} fit="contain" />
      ) : (
        <PdfThumbnail className="nt-pdf-split-image" src={src} page={page} renderWidth={144} previewAspectRatio={72 / 92} />
      )}
      <span className="nt-pdf-split-page-number" aria-hidden="true">
        {page}
      </span>
    </button>
  )
}

export { createInitialSplits, DocumentSplits }
export type { DocumentSplit, DocumentSplitsProps }
