import * as React from "react"

import { cn } from "../lib/utils"
import "./code-editor.css"
import { useNative } from "./provider"

// Monaco (VS Code's editor) is large: it loads the first time a CodeEditor or CodeBlock is shown, not before.
const loadMonaco = () => import("./code-editor-monaco")

type CodeEditorProps = Omit<
  React.ComponentProps<"div">,
  "onChange" | "defaultValue"
> & {
  /** The text (controlled); or defaultValue to let the editor keep it. */
  value?: string
  defaultValue?: string
  onChange?: (value: string) => void
  /** Monaco's name for the language (typescript, rust, json, yaml, markdown, python, sql, ...). */
  language?: string
  /**
   * The file's name or path ("src/app.tsx", "Dockerfile"): picks the language from it the way Monaco knows
   * files (when no language is given), and keeps each file's own text and undo history when it switches.
   */
  fileName?: string
  /** The language it chose (Monaco's id and its name for people, e.g. "rust", "Rust"): for a status bar. */
  onLanguageChange?: (id: string, name: string) => void
  readOnly?: boolean
  /** Line numbers down the left. On by default. */
  lineNumbers?: boolean
  /** The small overview of the whole file on the right, as in VS Code. Off by default. */
  minimap?: boolean
  /** Long lines wrap instead of scrolling sideways. */
  wordWrap?: boolean
  /** Its height (e.g. 320 or "50vh"); left out, it fills its parent, which needs a height. */
  height?: number | string
  /** What it holds, for screen readers (e.g. "Contents of app.tsx"). */
  "aria-label": string
  /** No frame around it (e.g. filling a pane that has its own edges). */
  frameless?: boolean
}

const LazyEditor = React.lazy(async () => {
  const m = await loadMonaco()
  function Loaded(
    props: Omit<React.ComponentProps<typeof m.MonacoEditor>, "language"> & {
      language?: string
      fileName?: string
      onLanguageChange?: (id: string, name: string) => void
    }
  ) {
    const { fileName, language, onLanguageChange, ...rest } = props
    const chosen =
      language ?? (fileName ? m.languageForFile(fileName) : "plaintext")
    React.useEffect(() => {
      onLanguageChange?.(chosen, m.languageName(chosen))
      // Only when the language itself changes.
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [chosen])
    return <m.MonacoEditor {...rest} language={chosen} path={fileName} />
  }
  return { default: Loaded }
})

/** What shows while Monaco loads: the editor's own frame, empty, at its size. */
function Loading({ label }: { label: string }) {
  return (
    <div
      className="nt-codeedit-loading"
      role="status"
      aria-label={`Loading ${label}`}
    >
      <span />
      <span />
      <span />
    </div>
  )
}

/**
 * A code editor: VS Code's own (Monaco), with its colours for about 80 languages, picked from the file's name
 * or given; line numbers, folding, find (Ctrl+F / ⌘F), multiple cursors, and its own right-click menu. It
 * looks like the system around it: the window's colours and accent, Cascadia on Windows and SF Mono on a Mac,
 * light or dark with the NativeProvider. It works offline (Monaco comes with the app, not from the internet).
 */
function CodeEditor({
  value,
  defaultValue,
  onChange,
  language,
  fileName,
  onLanguageChange,
  readOnly = false,
  lineNumbers = true,
  minimap = false,
  wordWrap = false,
  height,
  frameless = false,
  "aria-label": ariaLabel,
  className,
  style,
  ...props
}: CodeEditorProps) {
  const { platform, theme } = useNative()
  const placeholder = <Loading label={ariaLabel} />
  return (
    <div
      className={cn("nt-codeedit", className)}
      data-frameless={frameless || undefined}
      data-readonly={readOnly || undefined}
      style={{ height: height ?? "100%", ...style }}
      {...props}
    >
      <React.Suspense fallback={placeholder}>
        <LazyEditor
          value={value}
          defaultValue={defaultValue}
          onChange={onChange}
          language={language}
          fileName={fileName}
          onLanguageChange={onLanguageChange}
          readOnly={readOnly}
          lineNumbers={lineNumbers}
          minimap={minimap}
          wordWrap={wordWrap}
          ariaLabel={ariaLabel}
          platform={platform}
          theme={theme}
          placeholder={placeholder}
        />
      </React.Suspense>
    </div>
  )
}

const LazyBlock = React.lazy(async () => {
  const m = await loadMonaco()
  function Loaded({
    code,
    language,
    fileName,
    theme,
  }: {
    code: string
    language?: string
    fileName?: string
    theme: "light" | "dark"
  }) {
    return (
      <m.MonacoCodeBlock
        code={code}
        language={
          language ?? (fileName ? m.languageForFile(fileName) : "plaintext")
        }
        theme={theme}
      />
    )
  }
  return { default: Loaded }
})

type CodeBlockProps = Omit<React.ComponentProps<"figure">, "children"> & {
  code: string
  /** Monaco's name for the language; or fileName to pick it from a file's name. */
  language?: string
  fileName?: string
  /** A line above the code (e.g. the file's name). */
  caption?: React.ReactNode
}

/**
 * Code shown with its colours, not editable: a snippet in a chat message or a help page. A Copy button
 * copies it all; the text can also be selected.
 */
function CodeBlock({
  code,
  language,
  fileName,
  caption,
  className,
  ...props
}: CodeBlockProps) {
  const { theme } = useNative()
  const [copied, setCopied] = React.useState(false)
  React.useEffect(() => {
    if (!copied) return
    const timer = window.setTimeout(() => setCopied(false), 1500)
    return () => window.clearTimeout(timer)
  }, [copied])
  return (
    <figure className={cn("nt-codeblock", className)} {...props}>
      {caption != null && (
        <figcaption className="nt-codeblock-caption">{caption}</figcaption>
      )}
      <button
        type="button"
        className="nt-codeblock-copy"
        onClick={() => {
          void navigator.clipboard?.writeText(code).then(
            () => setCopied(true),
            () => undefined
          )
        }}
      >
        {copied ? "Copied" : "Copy"}
      </button>
      <pre className="nt-codeblock-code nt-selectable">
        <React.Suspense fallback={<code>{code}</code>}>
          <LazyBlock
            code={code}
            language={language}
            fileName={fileName}
            theme={theme}
          />
        </React.Suspense>
      </pre>
    </figure>
  )
}

/** The language Monaco knows a file as (e.g. "rust" for main.rs), loading Monaco if it has not loaded yet. */
async function languageForFile(fileName: string): Promise<string> {
  return (await loadMonaco()).languageForFile(fileName)
}

export { CodeBlock, CodeEditor, languageForFile }
export type { CodeBlockProps, CodeEditorProps }
