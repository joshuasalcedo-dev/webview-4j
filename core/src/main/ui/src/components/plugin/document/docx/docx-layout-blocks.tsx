import { useVirtualizer } from "@tanstack/react-virtual"
import * as React from "react"

import { cn } from "../../../../lib/utils"
import { Pill, type PillColor } from "../../../pill"
import { ProgressBar } from "../../../progress-bar"
import { Blocks, FooterIcon, Heading, HeaderIcon, ImageIcon, ListIcon, Paragraph, TableIcon } from "./icons"
import { loadDocx, readDocx } from "./load"
import { type DocxBlock, type DocxBlockType, docxBlocks } from "./model"
import "./docx.css"

const STYLE: Record<DocxBlockType, { label: string; color: PillColor; icon: () => React.ReactNode }> = {
  title: { label: "Title", color: "purple", icon: Heading },
  heading: { label: "Heading", color: "blue", icon: Heading },
  paragraph: { label: "Paragraph", color: "grey", icon: Paragraph },
  list: { label: "List", color: "teal", icon: ListIcon },
  table: { label: "Table", color: "orange", icon: TableIcon },
  figure: { label: "Figure", color: "pink", icon: ImageIcon },
  header: { label: "Header", color: "yellow", icon: HeaderIcon },
  footer: { label: "Footer", color: "yellow", icon: FooterIcon },
}

const ORDER: DocxBlockType[] = ["title", "heading", "paragraph", "list", "table", "figure", "header", "footer"]

type DocxLayoutBlocksProps = Omit<React.ComponentProps<"div">, "onError"> & {
  /** The .docx to list: a URL (or a data: URL), or its bytes. Or give `blocks`. */
  src?: string
  data?: ArrayBuffer | Uint8Array
  /** Blocks found already (docxBlocks(model), or a layout reader's). */
  blocks?: DocxBlock[]
  /** The block chosen (else the first). */
  activeBlockId?: string
  /** The pointer or the focus is on a block. */
  onBlockFocus?: (block: DocxBlock) => void
  /** A block was clicked (or Enter pressed on it). */
  onBlockSelect?: (block: DocxBlock) => void
  /** Pills on top that show and hide each kind of block (on unless false). */
  showFilters?: boolean
  /** Its height (e.g. 420 or "60vh"); left out, it fills its parent. */
  height?: number | string
  onError?: (message: string) => void
}

/**
 * A Word document's layout blocks, as a layout reader (OCR) lists a page's: its title, headings, paragraphs, list
 * items, tables, pictures, header and footer, each as a card with a coloured badge for what it is. A long document
 * scrolls smoothly (only the cards in view are drawn). Pills on top show and hide each kind.
 */
export function DocxLayoutBlocks({
  src,
  data,
  blocks: given,
  activeBlockId,
  onBlockFocus,
  onBlockSelect,
  showFilters = true,
  height,
  onError,
  className,
  style,
  ...props
}: DocxLayoutBlocksProps) {
  const [read, setRead] = React.useState<DocxBlock[]>()
  const [error, setError] = React.useState<string>()
  const [loading, setLoading] = React.useState(false)
  const onErrorRef = React.useRef(onError)
  onErrorRef.current = onError

  React.useEffect(() => {
    if (given || (!src && !data)) return
    let current = true
    setLoading(true)
    setError(undefined)
    Promise.all([loadDocx(), readDocx(src, data)])
      .then(async ([lib, bytes]) => {
        if (!bytes) return
        const { model } = await lib.buildDocModelFromBytes(bytes)
        if (current) setRead(docxBlocks(model))
      })
      .catch((e: unknown) => {
        if (!current) return
        const message = e instanceof Error ? e.message : String(e)
        setError(message)
        onErrorRef.current?.(message)
      })
      .finally(() => current && setLoading(false))
    return () => {
      current = false
    }
  }, [given, src, data])

  const all = React.useMemo(() => given ?? read ?? [], [given, read])
  const [hidden, setHidden] = React.useState<Set<DocxBlockType>>(() => new Set())
  const shown = React.useMemo(() => all.filter((block) => !hidden.has(block.type)), [all, hidden])
  const counts = React.useMemo(() => {
    const map = new Map<DocxBlockType, number>()
    for (const block of all) map.set(block.type, (map.get(block.type) ?? 0) + 1)
    return map
  }, [all])

  const [localActive, setLocalActive] = React.useState<string>()
  const active = activeBlockId ?? localActive ?? shown[0]?.id
  const activeRef = React.useRef(active)
  activeRef.current = active
  const focus = React.useCallback(
    (block: DocxBlock) => {
      if (block.id === activeRef.current) return
      activeRef.current = block.id
      setLocalActive(block.id)
      onBlockFocus?.(block)
    },
    [onBlockFocus]
  )

  const scroller = React.useRef<HTMLDivElement>(null)
  const virtualizer = useVirtualizer({
    count: shown.length,
    estimateSize: (index) => estimate(shown[index]),
    getItemKey: (index) => shown[index]?.id ?? index,
    getScrollElement: () => scroller.current,
    overscan: 6,
  })

  return (
    <div
      className={cn("nt-docx-blocks", className)}
      style={{ height: height ?? "100%", ...style }}
      aria-busy={loading || undefined}
      {...props}
    >
      {showFilters && all.length > 0 && (
        <div className="nt-docx-blocks-filters" role="group" aria-label="Show blocks">
          {ORDER.filter((type) => counts.has(type)).map((type) => {
            const Icon = STYLE[type].icon
            return (
              <Pill
                key={type}
                color={STYLE[type].color}
                icon={<Icon />}
                pressed={!hidden.has(type)}
                onPressedChange={(on) =>
                  setHidden((current) => {
                    const next = new Set(current)
                    if (on) next.delete(type)
                    else next.add(type)
                    return next
                  })
                }
              >
                {`${STYLE[type].label} ${counts.get(type)}`}
              </Pill>
            )
          })}
        </div>
      )}
      {loading && <ProgressBar aria-label="Reading the document" className="nt-docx-blocks-progress" />}
      <div ref={scroller} className="nt-docx-blocks-scroll" role="list" aria-label="Layout blocks">
        {error ? (
          <div className="nt-docx-sidebar-empty" role="alert">{error}</div>
        ) : shown.length ? (
          <div style={{ position: "relative", height: virtualizer.getTotalSize() + 24 }}>
            {virtualizer.getVirtualItems().map((row) => {
              const block = shown[row.index]
              if (!block) return null
              return (
                <div
                  key={row.key}
                  ref={virtualizer.measureElement}
                  data-index={row.index}
                  role="listitem"
                  className="nt-docx-blocks-row"
                  style={{ transform: `translateY(${row.start + 12}px)` }}
                >
                  <DocxBlockCard
                    block={block}
                    active={block.id === active}
                    onFocusBlock={focus}
                    onSelectBlock={onBlockSelect}
                  />
                </div>
              )
            })}
          </div>
        ) : (
          !loading && (
            <div className="nt-docx-sidebar-empty">
              <Blocks />
              {all.length ? "Every kind of block is hidden" : "No layout blocks"}
            </div>
          )
        )}
      </div>
    </div>
  )
}

function estimate(block: DocxBlock | undefined): number {
  if (!block) return 92
  if (block.type === "table") return 62 + Math.min(5, block.rows ?? 1) * 22
  const lines = Math.min(6, Math.max(1, Math.ceil(block.text.length / 46)))
  return 62 + lines * 20
}

const DocxBlockCard = React.memo(function DocxBlockCard({
  block,
  active,
  onFocusBlock,
  onSelectBlock,
}: {
  block: DocxBlock
  active: boolean
  onFocusBlock: (block: DocxBlock) => void
  onSelectBlock?: (block: DocxBlock) => void
}) {
  const style = STYLE[block.type]
  const Icon = style.icon
  const detail =
    block.type === "heading"
      ? `H${block.level ?? 1}`
      : block.type === "table"
        ? `${block.rows ?? 0} × ${block.columns ?? 0}`
        : block.type === "list" && block.level
          ? `Level ${block.level + 1}`
          : undefined
  return (
    <button
      type="button"
      className="nt-docx-block"
      data-type={block.type}
      data-active={active || undefined}
      onMouseEnter={() => onFocusBlock(block)}
      onFocus={() => onFocusBlock(block)}
      onClick={() => onSelectBlock?.(block)}
    >
      <span className="nt-docx-block-head">
        <Pill color={style.color} icon={<Icon />}>
          {style.label}
        </Pill>
        {detail && <span className="nt-docx-block-detail">{detail}</span>}
        {block.nodeIndex !== undefined && (
          <span className="nt-docx-block-where" title="Its place in the document">
            ¶ {block.nodeIndex + 1}
          </span>
        )}
      </span>
      {block.type === "table" ? (
        <span className="nt-docx-block-table">
          <table>
            <tbody>
              {block.text
                .split("\n")
                .slice(0, 5)
                .map((row, r) => (
                  <tr key={r}>
                    {row.split("\t").map((value, c) => (r === 0 ? <th key={c}>{value}</th> : <td key={c}>{value}</td>))}
                  </tr>
                ))}
            </tbody>
          </table>
        </span>
      ) : (
        <span className="nt-docx-block-text" data-type={block.type}>
          {block.text || (block.type === "figure" ? "A picture" : "")}
        </span>
      )}
    </button>
  )
})
