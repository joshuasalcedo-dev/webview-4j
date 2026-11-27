import * as React from "react"

import { cn } from "../lib/utils"
import { formatShortcut } from "./context-menu"
import { useNative } from "./provider"

/**
 * The roles text plays, each drawn at the system's own size and weight:
 *
 * | variant    | Windows 11 (type ramp)        | macOS (text styles)      |
 * |------------|-------------------------------|--------------------------|
 * | display    | Display 68/92 semibold        | 34/41 (large heading)    |
 * | largeTitle | Title large 40/52 semibold    | Large Title 26/32        |
 * | title      | Title 28/36 semibold          | Title 1 22/26            |
 * | subtitle   | Subtitle 20/28 semibold       | Title 2 17/22            |
 * | headline   | Body strong 14/20 semibold    | Headline 13/16 bold      |
 * | body       | Body 14/20                    | Body 13/16               |
 * | caption    | Caption 12/16                 | Subheadline 11/14        |
 * | footnote   | Caption 12/16, quieter        | Footnote 10/13           |
 */
type TextVariant =
  | "display"
  | "largeTitle"
  | "title"
  | "subtitle"
  | "headline"
  | "body"
  | "caption"
  | "footnote"

/** The colour: the text's own, quieter ones, or one that says something. */
type TextTone =
  | "default"
  | "secondary"
  | "tertiary"
  | "accent"
  | "error"
  | "success"
  | "warning"

const ELEMENT: Record<TextVariant, React.ElementType> = {
  display: "h1",
  largeTitle: "h1",
  title: "h2",
  subtitle: "h3",
  headline: "h4",
  body: "p",
  caption: "span",
  footnote: "small",
}

type TextProps = React.HTMLAttributes<HTMLElement> & {
  variant?: TextVariant
  tone?: TextTone
  /** The element to draw (headings get h1–h4, body a p, by default). */
  as?: React.ElementType
  /** Heavier than the variant's own weight (e.g. a name in a body sentence). */
  strong?: boolean
  /**
   * Cut off with "…" after this many lines; the whole text then shows in a tooltip on hover (only when it was
   * cut). 1 keeps it on one line.
   */
  lines?: number
  /**
   * People can select and copy it: the I-beam pointer, drag or click to highlight (two clicks a word, three
   * a sentence, ...), Copy on a right-click. True by default (text is content); false for text that is part
   * of the controls, or use Label.
   */
  selectable?: boolean
  /** In the system's monospace font (code, ids, numbers that line up). */
  mono?: boolean
}

/**
 * Text in the system's type: Windows 11's type ramp (Segoe UI Variable) on Windows, the Mac's text styles
 * (SF Pro) on a Mac, in the theme's colours. The same variant says the same thing on both; the sizes are each
 * system's own.
 */
function Text({
  variant = "body",
  tone = "default",
  as,
  strong = false,
  lines,
  selectable = true,
  mono = false,
  className,
  style,
  onMouseEnter,
  ...props
}: TextProps) {
  const Element = as ?? ELEMENT[variant]
  return (
    <Element
      data-variant={variant}
      data-tone={tone === "default" ? undefined : tone}
      data-strong={strong || undefined}
      data-mono={mono || undefined}
      data-lines={lines}
      className={cn("nt-text", selectable && "nt-selectable", className)}
      style={
        lines
          ? ({ ...style, "--nt-lines": lines } as React.CSSProperties)
          : style
      }
      onMouseEnter={(event: React.MouseEvent<HTMLElement>) => {
        // The tooltip says the whole text only when it was cut off.
        if (lines) {
          const element = event.currentTarget
          const cut =
            element.scrollWidth > element.clientWidth ||
            element.scrollHeight > element.clientHeight
          if (cut) element.title = element.textContent ?? ""
          else element.removeAttribute("title")
        }
        onMouseEnter?.(event)
      }}
      {...props}
    />
  )
}

type LinkProps = React.AnchorHTMLAttributes<HTMLAnchorElement> & {
  /** Opens outside the app (the system browser): marked with ↗ and opened in a new window. */
  external?: boolean
  /**
   * Clickable text in the text's own colour, underlined while the pointer is on it ("Show more", "Mark all as
   * read"); without it, a link is in the accent colour.
   */
  subtle?: boolean
}

/**
 * A link in text: the accent colour, the hand pointer, underlined while the pointer is on it (Windows'
 * HyperlinkButton, a Mac's link). With onClick and no href it is a link-looking button.
 */
function Link({
  external = false,
  subtle = false,
  className,
  children,
  href,
  target,
  rel,
  ...props
}: LinkProps) {
  return (
    <a
      href={href}
      target={external ? "_blank" : target}
      rel={external ? "noreferrer noopener" : rel}
      role={href ? undefined : "button"}
      tabIndex={href ? undefined : 0}
      data-subtle={subtle || undefined}
      className={cn("nt-link", className)}
      onKeyDown={(event) => {
        if (!href && (event.key === "Enter" || event.key === " ")) {
          event.preventDefault()
          event.currentTarget.click()
        }
      }}
      {...props}
    >
      {children}
      {external && (
        <svg
          className="nt-link-external"
          viewBox="0 0 12 12"
          aria-label="(opens outside the app)"
        >
          <path
            d="M5 2.5H2.5v7h7V7M7 2.5h2.5V5M9.5 2.5 5.5 6.5"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.1"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      )}
    </a>
  )
}

/** Code inside a sentence: monospace, on a faint background. */
function Code({ className, ...props }: React.HTMLAttributes<HTMLElement>) {
  return <code className={cn("nt-code", className)} {...props} />
}

/**
 * A keyboard shortcut written the system's way: "Mod+Shift+K" is ⇧⌘K on a Mac and Ctrl+Shift+K on Windows,
 * in key caps.
 */
function Shortcut({
  keys,
  className,
  ...props
}: Omit<React.HTMLAttributes<HTMLElement>, "children"> & { keys: string }) {
  const { platform } = useNative()
  const written = formatShortcut(keys, platform)
  const caps = platform === "mac" ? Array.from(written) : written.split("+")
  return (
    <kbd
      className={cn("nt-shortcut", className)}
      aria-label={written}
      {...props}
    >
      {caps.map((cap, i) => (
        <kbd key={i}>{cap}</kbd>
      ))}
    </kbd>
  )
}

type LabelProps = React.LabelHTMLAttributes<HTMLLabelElement> & {
  variant?: TextVariant
  tone?: TextTone
  strong?: boolean
  /** The control it names is off: the label is greyed too. */
  disabled?: boolean
}

/**
 * Text that is part of the controls, as the systems' labels are (Windows' TextBlock, a Mac's label): not
 * selectable, the arrow pointer, and with htmlFor (or around its control) a click on it focuses or toggles
 * the control. For text people read and copy, use Text.
 */
function Label({
  variant = "body",
  tone = "default",
  strong = false,
  disabled = false,
  className,
  ...props
}: LabelProps) {
  return (
    <label
      data-variant={variant}
      data-tone={disabled ? "tertiary" : tone === "default" ? undefined : tone}
      data-strong={strong || undefined}
      data-disabled={disabled || undefined}
      className={cn("nt-text nt-label", className)}
      {...props}
    />
  )
}

export { Code, Label, Link, Shortcut, Text }
export type { LabelProps, LinkProps, TextProps, TextTone, TextVariant }
