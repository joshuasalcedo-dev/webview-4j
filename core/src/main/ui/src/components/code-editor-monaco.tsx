// The part of the code editor that brings Monaco in. It is loaded only when an editor or a code block is first
// shown (code-editor.tsx imports it lazily), so apps that never show code never download Monaco.
import Editor, { loader, type OnMount } from "@monaco-editor/react"
import * as monaco from "monaco-editor"
import EditorWorker from "monaco-editor/editor/editor.worker?worker&inline"
import CssWorker from "monaco-editor/language/css/css.worker?worker&inline"
import HtmlWorker from "monaco-editor/language/html/html.worker?worker&inline"
import JsonWorker from "monaco-editor/language/json/json.worker?worker&inline"
import TsWorker from "monaco-editor/language/typescript/ts.worker?worker&inline"
import * as React from "react"

import type { Platform, Theme } from "../lib/platform"
import { type MenuEntry, useContextMenu } from "./context-menu"

// Monaco's helpers (checking JSON, CSS, HTML and TypeScript as you type) run in web workers. They come from
// the installed package, bundled by Vite, so the editor works with no network (a desktop app offline).
self.MonacoEnvironment = {
  getWorker(_id: string, label: string) {
    if (label === "json") return new JsonWorker()
    if (label === "css" || label === "scss" || label === "less")
      return new CssWorker()
    if (label === "html" || label === "handlebars" || label === "razor")
      return new HtmlWorker()
    if (label === "typescript" || label === "javascript") return new TsWorker()
    return new EditorWorker()
  },
}

// @monaco-editor/react fetches Monaco from a CDN unless told otherwise: it uses this copy instead.
loader.config({ monaco })

// TypeScript and JavaScript files are shown on their own, not inside their project: the checker cannot see
// the packages they import, so it would mark every import and type from them as wrong. It still marks
// mistakes in the writing itself (a missing bracket); JSX (.tsx, .jsx) reads as JSX.
for (const defaults of [
  monaco.typescript.typescriptDefaults,
  monaco.typescript.javascriptDefaults,
]) {
  defaults.setDiagnosticsOptions({
    noSemanticValidation: true,
    noSyntaxValidation: false,
  })
  defaults.setCompilerOptions({
    target: monaco.typescript.ScriptTarget.ESNext,
    module: monaco.typescript.ModuleKind.ESNext,
    moduleResolution: monaco.typescript.ModuleResolutionKind.NodeJs,
    jsx: monaco.typescript.JsxEmit.Preserve,
    allowJs: true,
    allowNonTsExtensions: true,
  })
}

// The colours of the words, as VS Code's Light+ and Dark+ themes. The editor's own background, text,
// selection and current-line colours come from ui-native's tokens instead (code-editor.css sets Monaco's
// --vscode-* variables from --nt-*), so the editor matches the window it is in, on each system.
monaco.editor.defineTheme("nt-light", {
  base: "vs",
  inherit: true,
  rules: [
    { token: "keyword", foreground: "0000FF" },
    { token: "keyword.flow", foreground: "AF00DB" },
    { token: "type", foreground: "267F99" },
    { token: "type.identifier", foreground: "267F99" },
    { token: "identifier", foreground: "001080" },
    { token: "string", foreground: "A31515" },
    { token: "string.key.json", foreground: "0451A5" },
    { token: "string.value.json", foreground: "A31515" },
    { token: "number", foreground: "098658" },
    { token: "comment", foreground: "008000" },
    { token: "annotation", foreground: "795E26" },
    { token: "tag", foreground: "800000" },
    { token: "attribute.name", foreground: "E50000" },
    { token: "attribute.value", foreground: "0451A5" },
    { token: "delimiter", foreground: "000000" },
  ],
  colors: {},
})

monaco.editor.defineTheme("nt-dark", {
  base: "vs-dark",
  inherit: true,
  rules: [
    { token: "keyword", foreground: "569CD6" },
    { token: "keyword.flow", foreground: "C586C0" },
    { token: "type", foreground: "4EC9B0" },
    { token: "type.identifier", foreground: "4EC9B0" },
    { token: "identifier", foreground: "9CDCFE" },
    { token: "string", foreground: "CE9178" },
    { token: "string.key.json", foreground: "9CDCFE" },
    { token: "string.value.json", foreground: "CE9178" },
    { token: "number", foreground: "B5CEA8" },
    { token: "comment", foreground: "6A9955" },
    { token: "annotation", foreground: "DCDCAA" },
    { token: "tag", foreground: "569CD6" },
    { token: "attribute.name", foreground: "9CDCFE" },
    { token: "attribute.value", foreground: "CE9178" },
    { token: "delimiter", foreground: "D4D4D4" },
  ],
  colors: {},
})

const themeName = (theme: Theme) => (theme === "dark" ? "nt-dark" : "nt-light")

/** The fonts each system's code editors use: Cascadia on Windows (as Windows Terminal, VS Code), SF Mono on a Mac. */
const codeFont = (platform: Platform) =>
  platform === "mac"
    ? '"SF Mono", SFMono-Regular, Menlo, Monaco, monospace'
    : '"Cascadia Code", "Cascadia Mono", Consolas, "Courier New", monospace'

/**
 * The language Monaco knows a file as, from its name: first a whole name it lists (Dockerfile, makefile), then
 * the longest extension it lists (.d.ts before .ts). "plaintext" when it knows none.
 */
function languageForFile(fileName: string): string {
  const name = fileName.slice(fileName.lastIndexOf("/") + 1).toLowerCase()
  const languages = monaco.languages.getLanguages()
  const whole = languages.find((l) =>
    l.filenames?.some((f) => f.toLowerCase() === name)
  )
  if (whole) return whole.id
  let best: { id: string; length: number } | undefined
  for (const language of languages) {
    for (const extension of language.extensions ?? []) {
      const lower = extension.toLowerCase()
      if (name.endsWith(lower) && lower.length > (best?.length ?? 0))
        best = { id: language.id, length: lower.length }
    }
  }
  return best?.id ?? "plaintext"
}

/** What a language is called for people (TypeScript, Rust), from Monaco's own list. */
function languageName(id: string): string {
  const language = monaco.languages.getLanguages().find((l) => l.id === id)
  return language?.aliases?.[0] ?? id
}

type MonacoEditorProps = {
  value?: string
  defaultValue?: string
  onChange?: (value: string) => void
  language: string
  /** Each file keeps its own text and undo history when the editor switches between files. */
  path?: string
  readOnly: boolean
  lineNumbers: boolean
  minimap: boolean
  wordWrap: boolean
  ariaLabel: string
  platform: Platform
  theme: Theme
  placeholder: React.ReactNode
  onReady?: (editor: monaco.editor.IStandaloneCodeEditor) => void
}

/** The editor itself (Monaco), with ui-native's look. */
function MonacoEditor({
  value,
  defaultValue,
  onChange,
  language,
  path,
  readOnly,
  lineNumbers,
  minimap,
  wordWrap,
  ariaLabel,
  platform,
  theme,
  placeholder,
  onReady,
}: MonacoEditorProps) {
  const editorRef = React.useRef<monaco.editor.IStandaloneCodeEditor | null>(
    null
  )
  const { onContextMenu } = useContextMenu(() =>
    editorRef.current ? editorMenu(editorRef.current, readOnly, platform) : []
  )
  // Monaco calls back with the menu of the latest render.
  const openMenu = React.useRef(onContextMenu)
  React.useEffect(() => {
    openMenu.current = onContextMenu
  })
  const onMount: OnMount = (editor) => {
    editorRef.current = editor
    // The system's own kind of menu on a right-click (Shift+F10, the menu key), not Monaco's drawn one.
    editor.onContextMenu((event) =>
      openMenu.current(event.event.browserEvent as unknown as React.MouseEvent)
    )
    onReady?.(editor)
  }
  return (
    <Editor
      value={value}
      defaultValue={defaultValue}
      onChange={(text) => onChange?.(text ?? "")}
      language={language}
      path={path}
      theme={themeName(theme)}
      loading={placeholder}
      onMount={onMount}
      options={{
        ariaLabel,
        readOnly,
        domReadOnly: readOnly,
        lineNumbers: lineNumbers ? "on" : "off",
        // Without line numbers the gutter would still keep room for folding arrows: keep it narrow.
        lineDecorationsWidth: lineNumbers ? 10 : 4,
        folding: lineNumbers,
        glyphMargin: false,
        minimap: { enabled: minimap },
        wordWrap: wordWrap ? "on" : "off",
        fontFamily: codeFont(platform),
        fontSize: 13,
        lineHeight: 20,
        fontLigatures: platform === "windows",
        scrollBeyondLastLine: false,
        renderLineHighlight: readOnly ? "none" : "line",
        automaticLayout: true,
        padding: { top: 8, bottom: 8 },
        scrollbar: {
          verticalScrollbarSize: 10,
          horizontalScrollbarSize: 10,
          useShadows: false,
        },
        overviewRulerBorder: false,
        hideCursorInOverviewRuler: true,
        // The menus and hovers may reach outside a small editor (e.g. in a dialog) without being cut off.
        fixedOverflowWidgets: true,
        // ui-native shows the right-click menu instead (editorMenu), as every other menu in the app.
        contextmenu: false,
      }}
    />
  )
}

/**
 * The editor's right-click menu, with the commands of VS Code's own (and its keys), in the system's words.
 * A read-only editor offers only what does not change the text.
 */
function editorMenu(
  editor: monaco.editor.IStandaloneCodeEditor,
  readOnly: boolean,
  platform: Platform
): MenuEntry[] {
  const run = (id: string) => () => {
    // The menu took the focus: give it back, so the command acts on the editor and its selection.
    editor.focus()
    editor.trigger("menu", id, null)
  }
  const supported = (id: string) => editor.getAction(id)?.isSupported() ?? false
  const mac = platform === "mac"
  const entries: MenuEntry[] = []
  if (!readOnly) {
    entries.push({
      label: "Change All Occurrences",
      shortcut: "Mod+F2",
      onSelect: run("editor.action.changeAll"),
    })
    if (supported("editor.action.formatDocument"))
      entries.push({
        label: "Format Document",
        shortcut: "Shift+Alt+F",
        onSelect: run("editor.action.formatDocument"),
      })
    entries.push("separator")
    entries.push({
      label: "Cut",
      shortcut: "Mod+X",
      onSelect: run("editor.action.clipboardCutAction"),
    })
  }
  entries.push({
    label: "Copy",
    shortcut: "Mod+C",
    onSelect: run("editor.action.clipboardCopyAction"),
  })
  if (!readOnly)
    entries.push({
      label: "Paste",
      shortcut: "Mod+V",
      onSelect: run("editor.action.clipboardPasteAction"),
    })
  entries.push("separator", {
    label: mac ? "Select All" : "Select all",
    shortcut: "Mod+A",
    onSelect: run("editor.action.selectAll"),
  })
  entries.push("separator", {
    label: "Command Palette…",
    shortcut: "F1",
    onSelect: run("editor.action.quickCommand"),
  })
  return entries
}

/** Code shown with its colours, not editable (e.g. in a chat message): Monaco colours it, nothing else loads. */
function MonacoCodeBlock({
  code,
  language,
  theme,
}: {
  code: string
  language: string
  theme: Theme
}) {
  const [html, setHtml] = React.useState<string>()
  React.useEffect(() => {
    let current = true
    // The word colours are page-wide in Monaco: choosing the theme also writes its colour classes.
    monaco.editor.setTheme(themeName(theme))
    void monaco.editor
      .colorize(code, language, { tabSize: 2 })
      .then((colored) => current && setHtml(colored))
    return () => {
      current = false
    }
  }, [code, language, theme])
  // Monaco escapes the code itself and returns only its own <span>s; until then the plain text shows.
  return html ? (
    <code dangerouslySetInnerHTML={{ __html: html }} />
  ) : (
    <code>{code}</code>
  )
}

export { languageForFile, languageName, MonacoCodeBlock, MonacoEditor }
export type { MonacoEditorProps }
