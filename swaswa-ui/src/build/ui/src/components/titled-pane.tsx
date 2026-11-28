import * as React from "react"

import "./carousel.css"

/**
 * A section with a title that opens and closes (JavaFX's TitledPane), and a stack of them where one is open at a time
 * (JavaFX's Accordion): settings in groups, details tucked away.
 */
type TitledPaneProps = {
  title: React.ReactNode
  children?: React.ReactNode
  /** Open (controlled); it keeps its own when left out. */
  expanded?: boolean
  onExpandedChange?: (expanded: boolean) => void
  /** Open at first. */
  defaultExpanded?: boolean
  /** Always open, no way to close it (JavaFX's collapsible false). */
  collapsible?: boolean
  className?: string
  style?: React.CSSProperties
}

function TitledPane({ title, children, expanded, onExpandedChange, defaultExpanded = true, collapsible = true, className, style }: TitledPaneProps) {
  const [own, setOwn] = React.useState(defaultExpanded)
  const open = !collapsible || (expanded ?? own)
  const id = React.useId()
  const toggle = () => {
    if (!collapsible) return
    if (expanded === undefined) setOwn(!open)
    onExpandedChange?.(!open)
  }
  return (
    <section className={["nt-titledpane", className].filter(Boolean).join(" ")} data-open={open ? "" : undefined} style={style}>
      <button type="button" className="nt-titledpane-header" aria-expanded={open} aria-controls={id} onClick={toggle} disabled={!collapsible}>
        {collapsible && <span className="nt-titledpane-chevron" aria-hidden="true">▶</span>}
        <span>{title}</span>
      </button>
      <div id={id} className="nt-titledpane-body" hidden={!open}>
        {children}
      </div>
    </section>
  )
}

type AccordionProps = {
  /** The sections, by title. */
  panes: { title: React.ReactNode; content: React.ReactNode; key?: string }[]
  /** The open one (its index), controlled; -1: none. */
  expanded?: number
  onExpandedChange?: (index: number) => void
  className?: string
  style?: React.CSSProperties
}

/** Sections where one is open at a time: opening one closes the other. */
function Accordion({ panes, expanded, onExpandedChange, className, style }: AccordionProps) {
  const [own, setOwn] = React.useState(0)
  const open = expanded ?? own
  return (
    <div className={["nt-accordion", className].filter(Boolean).join(" ")} style={style}>
      {panes.map((pane, i) => (
        <TitledPane
          key={pane.key ?? i}
          title={pane.title}
          expanded={open === i}
          onExpandedChange={(e) => {
            const next = e ? i : -1
            if (expanded === undefined) setOwn(next)
            onExpandedChange?.(next)
          }}
        >
          {pane.content}
        </TitledPane>
      ))}
    </div>
  )
}

export { Accordion, TitledPane }
export type { AccordionProps, TitledPaneProps }
