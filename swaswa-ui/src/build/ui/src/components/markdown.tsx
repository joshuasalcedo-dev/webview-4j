import * as React from "react"
import ReactMarkdown, { type Components, defaultUrlTransform } from "react-markdown"
import remarkGfm from "remark-gfm"

import "./markdown.css"

/**
 * Markdown, in the kit's look: CommonMark with GitHub's tables, task lists, strikethrough and autolinks. Safe for text
 * from anywhere: HTML in it is shown as text, never run, and only http, https and mailto links are kept.
 *
 * A link does not take the window away from the app: `onLink` gets it (Java opens it in the browser), or it opens in
 * a new tab.
 */
type MarkdownProps = {
  children: string
  /** A link was clicked: where it goes. Without, it opens in a new tab (the system browser in a desktop window). */
  onLink?: (href: string) => void
  /** Headings start at this level (2: a "# Title" is shown as an h2), for Markdown inside a page with its own title. */
  headingOffset?: number
  className?: string
  style?: React.CSSProperties
}

const SAFE = /^(https?:|mailto:|#)/i

function Markdown({ children, onLink, headingOffset = 0, className, style }: MarkdownProps) {
  const components = React.useMemo<Components>(() => {
    const heading =
      (level: number) =>
      ({ children: c }: { children?: React.ReactNode }) => {
        const Tag = `h${Math.min(6, level + headingOffset)}` as "h1"
        return <Tag>{c}</Tag>
      }
    return {
      h1: heading(1),
      h2: heading(2),
      h3: heading(3),
      h4: heading(4),
      h5: heading(5),
      h6: heading(6),
      a: ({ href, children: c }) => (
        <a
          href={href}
          className="nt-link"
          target="_blank"
          rel="noopener noreferrer"
          onClick={(e) => {
            if (!href || href.startsWith("#")) return
            if (onLink) {
              e.preventDefault()
              onLink(href)
            }
          }}
        >
          {c}
        </a>
      ),
      // A table scrolls on its own rather than widening the page.
      table: ({ children: c }) => (
        <div className="nt-markdown-table">
          <table>{c}</table>
        </div>
      ),
    }
  }, [onLink, headingOffset])
  return (
    <div className={["nt-markdown", className].filter(Boolean).join(" ")} style={style}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        skipHtml
        urlTransform={(url) => (SAFE.test(url) ? defaultUrlTransform(url) : "")}
        components={components}
      >
        {children}
      </ReactMarkdown>
    </div>
  )
}

export { Markdown }
export type { MarkdownProps }
