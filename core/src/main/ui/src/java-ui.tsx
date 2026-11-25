import * as React from "react"
import { createRoot } from "react-dom/client"

import { Avatar } from "./components/avatar"
import { Breadcrumb } from "./components/breadcrumb"
import { MessageBubble, TypingIndicator } from "./components/chat/message-bubble"
import { CodeEditor } from "./components/code-editor"
import { Composer } from "./components/composer"
import { Dialog } from "./components/dialog"
import { FileExplorer, FileGrid, FileList } from "./components/file-views"
import { FileChip, FileIcon, FileTree } from "./components/files"
import { InfiniteScroll } from "./components/infinite-scroll"
import { Inspector, InspectorSection } from "./components/inspector"
import { LogView } from "./components/log-view"
import { applyLayout, layoutOf, type MdiLayout, type MdiWindowState, MdiArea, useMdi } from "./components/mdi"
import { MediaPlayer } from "./components/media-player"
import { MoneyField } from "./components/money"
import { PillGroup } from "./components/pill"
import { Quantity, QuantityField } from "./components/quantity"
import { RichTextEditor } from "./components/rich-text"
import { SearchField } from "./components/search-field"
import { Swipeable } from "./components/swipeable"
import { Tabs } from "./components/tabs"
import { allowedText, NumberField, PasswordField, TokenField } from "./components/text-inputs"
import {
  CardNumberField,
  CountrySelect,
  CurrencySelect,
  DateField,
  EmailField,
  IbanField,
  LanguageSelect,
  PercentField,
  PhoneField,
  UrlField,
} from "./components/value-fields"
import { DataTable, type DataColumn, type DataRow } from "./components/usecase/collection/data-table"
import type { CollectionQuery, Paging } from "./components/usecase/collection/query"
import { ErrorState, LoadingBar } from "./components/usecase/collection/states"
import { Markdown } from "./components/markdown"
import { Changelog } from "./components/usecase/autoupdate/changelog"
import { AccountMenu } from "./components/usecase/authentication/account-menu"
import { CodeField } from "./components/usecase/authentication/code-field"
import { PasswordStrength } from "./components/usecase/authentication/password-strength"
import { SessionExpired } from "./components/usecase/authentication/session-expired"
import { SignInForm } from "./components/usecase/authentication/sign-in-form"
import { UpdatePrompt, type UpdateStatus } from "./components/usecase/autoupdate/update-prompt"
import { Anchored, type Anchors, AnchorPane, Card, CardPane, FlowPane, ScrollPane, TilePane } from "./components/layout/more-layout"
import { Carousel } from "./components/carousel"
import { Accordion, TitledPane } from "./components/titled-pane"
import { BorderPane, GridCell, GridPane, HBox, type HPos, type Insets, type Pos, SplitPane, StackItem, StackPane, type TrackSize, VBox, type VPos } from "./components/layout/layout"
import { DataGrid } from "./components/usecase/collection/data-grid"
import { DataList } from "./components/usecase/collection/data-list"
import { DataMap } from "./components/usecase/collection/data-map"
import { DataTree } from "./components/usecase/collection/data-tree"
import { Button } from "./components/button"
import { Checkbox } from "./components/checkbox"
import { CodeBlock } from "./components/code-editor"
import { Content } from "./components/content"
import { useDialogs } from "./components/dialog"
import { Spinner } from "./components/infinite-scroll"
import { List, ListItem } from "./components/list"
import { Money } from "./components/money"
import { Pill } from "./components/pill"
import { ProgressBar } from "./components/progress-bar"
import { NativeProvider } from "./components/provider"
import { SegmentedControl } from "./components/segmented-control"
import { Select } from "./components/select"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarHeader,
  SidebarInput,
  SidebarItem,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarSection,
  SidebarSeparator,
  SidebarTrigger,
} from "./components/sidebar"
import { NamedIcon } from "./lib/named-icons"
import { ContextMenu, type MenuEntry } from "./components/context-menu"
import { DropdownMenu } from "./components/controls"
import { htmlTags, voidTags } from "./html-tags.generated"
import "./html-controls.css"
import { CommandPalette } from "./components/overlays"
import { Menubar } from "./components/structure"
import { renderGenerated } from "./java-ui.generated"
import { GrammarTextArea } from "./components/grammar-text-area"
import { JavaCsvViewer, JavaXlsxEditor, JavaXlsxViewer } from "./components/usecase/xlsx/java"
import { JavaBoundingBoxCitations, JavaDocumentSplits, JavaFileThumbnail, JavaPdfViewer } from "./components/usecase/pdf/java"
import { JavaDocumentViewerSidebar, JavaDocxAnnotationCard, JavaDocxEditor, JavaDocxLayoutBlocks, JavaDocxViewer } from "./components/usecase/docx/java"
import { StatusBar, StatusBarSpacer, StatusDot, StatusItem } from "./components/status-bar"
import { Surface } from "./components/surface"
import { Switch } from "./components/switch"
import { Code as InlineCode, Label, Link, Shortcut, Text } from "./components/text"
import { TextArea } from "./components/text-inputs"
import { TextField } from "./components/text-field"
import { TitleBar } from "./components/title-bar"
import { Toolbar, ToolbarButton, ToolbarSeparator, ToolbarSpacer } from "./components/toolbar"

/**
 * Screens built in Java (io.joshuasalcedo.webview4j.ui): Java sends a tree of components, this shows it with the
 * kit's components, and sends the user's actions back. Every action is answered with the screen's new tree; Java
 * can also push one (UiWindow.refresh). The page itself is core's: UiWindow opens it.
 */

type JavaNode = { t: string; p: Record<string, unknown>; c?: JavaNode[]; k?: string }

type Bridge = {
  invoke(command: string, args?: unknown): Promise<unknown>
  listen(topic: string, handler: (data: unknown) => void): () => void
}

/** The way to Java: a web host's (over its WebSocket, in a browser), else the desktop window's own. */
const bridge = (): Bridge => {
  const w = window as unknown as { __webview4jRemote?: Bridge; __webview4j?: Bridge }
  const found = w.__webview4jRemote ?? w.__webview4j
  if (!found) throw new Error("the Java UI runs in a webview-4j window, or a page of a webview-4j web host")
  return found
}

type Send = (handler: unknown, value?: unknown) => void
const SendContext = React.createContext<Send>(() => {})
/** An action the component waits for (load more, send a message): resolves when Java has answered. */
type SendAndWait = (handler: unknown, value?: unknown) => Promise<void>
const SendAndWaitContext = React.createContext<SendAndWait>(async () => {})

function JavaUi() {
  const [tree, setTree] = React.useState<JavaNode | null>(null)
  const [error, setError] = React.useState<string>()
  // Actions go to Java one at a time, in order; each answer is the newest tree.
  const queue = React.useRef(Promise.resolve())

  const sendAndWait = React.useCallback<SendAndWait>((handler, value) => {
    if (typeof handler !== "string") return Promise.resolve()
    const done = queue.current.then(() =>
      bridge()
        .invoke("webview4j.ui.event", { handler, value: value ?? null })
        .then((next) => setTree(next as JavaNode), (e: Error) => setError(e.message))
    )
    queue.current = done
    return done
  }, [])
  const send = React.useCallback<Send>((handler, value) => void sendAndWait(handler, value), [sendAndWait])

  React.useEffect(() => {
    const stop = bridge().listen("webview4j.ui.tree", (next) => setTree(next as JavaNode))
    bridge().invoke("webview4j.ui.ready").then((next) => setTree(next as JavaNode), (e: Error) => setError(e.message))
    return stop
  }, [])

  if (error) return <pre style={{ padding: 16, color: "crimson", whiteSpace: "pre-wrap" }}>{error}</pre>
  if (!tree) return null
  return (
    <SendContext.Provider value={send}>
      <SendAndWaitContext.Provider value={sendAndWait}>
        <Render node={tree} />
      </SendAndWaitContext.Provider>
    </SendContext.Provider>
  )
}

const str = (v: unknown) => (typeof v === "string" ? v : undefined)
const num = (v: unknown) => (typeof v === "number" ? v : undefined)
const bool = (v: unknown) => v === true

function slot(node: JavaNode, name: string) {
  return node.c?.find((c) => c.p.slot === name)
}

/** A GrammarTextArea from Java: what is typed reaches Java as it is typed, the count of mistakes when checked. */
function JavaGrammarTextArea({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const [value, setValue] = useJavaValue(str(p.value))
  // the count arrives a moment after the text changed, when this render's handlers may be gone: the latest one
  const onMistakes = React.useRef(p.onMistakes)
  onMistakes.current = p.onMistakes
  return (
    <label style={{ display: "grid", gap: 4, ...sizing(p) }}>
      {str(p.label) && <Text variant="caption">{str(p.label)}</Text>}
      <GrammarTextArea
        aria-label={str(p.label)}
        value={value}
        placeholder={str(p.placeholder)}
        rows={num(p.rows)}
        dialect={(str(p.dialect) as "en-US" | undefined) ?? "en-US"}
        check={p.check === undefined ? true : bool(p.check)}
        checkDelay={num(p.checkDelay)}
        onChange={(v) => {
          setValue(v)
          if (typeof p.onChange === "string") send(p.onChange, v)
        }}
        onMistakes={(m) => {
          if (typeof onMistakes.current === "string") send(onMistakes.current, m.length)
        }}
      />
    </label>
  )
}

/** The Java node types that make a sidebar one built from parts. */
const SIDEBAR_PARTS = new Set(["sidebarHeader", "sidebarGroup", "sidebarMenuButton", "sidebarInput", "sidebarSeparator"])

/** A sidebar's parts in order, its menu buttons side by side in one SidebarMenu. */
function SidebarParts({ nodes }: { nodes: JavaNode[] | undefined }) {
  const out: React.ReactNode[] = []
  let run: JavaNode[] = []
  const flush = () => {
    if (!run.length) return
    const items = run
    out.push(
      <SidebarMenu key={`menu-${out.length}`}>
        {items.map((n, i) => (
          <SidebarMenuEntry key={n.k ?? `item-${i}`} node={n} />
        ))}
      </SidebarMenu>
    )
    run = []
  }
  nodes?.forEach((n, i) => {
    if (n.t === "sidebarMenuButton") {
      run.push(n)
      return
    }
    flush()
    out.push(<Render key={n.k ?? `${n.t}-${i}`} node={n} />)
  })
  flush()
  return <>{out}</>
}

/** One menu button from Java, with the places under it (opening on a click) when it has some. */
function SidebarMenuEntry({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const sub = (node.c ?? []).filter((c) => c.t === "sidebarMenuButton")
  const anyActive = sub.some((c) => bool(c.p.active))
  const [open, setOpen] = React.useState(p.open === undefined ? anyActive : bool(p.open))
  React.useEffect(() => {
    if (anyActive) setOpen(true)
  }, [anyActive])
  const icon = str(p.icon)
  const badge = p.badge === undefined ? undefined : typeof p.badge === "number" ? (p.badge > 99 ? "99+" : p.badge) : str(p.badge)
  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        isActive={bool(p.active)}
        icon={icon ? <NamedIcon name={icon} /> : undefined}
        description={str(p.description)}
        size={(str(p.size) as "sm" | "default" | "lg" | undefined) ?? "default"}
        tooltip={str(p.tooltip)}
        disabled={bool(p.disabled)}
        aria-expanded={sub.length ? open : undefined}
        badge={
          sub.length ? (
            <span className="nt-sb-chevron" aria-hidden="true">
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
                <path d="m6 3 5 5-5 5" />
              </svg>
            </span>
          ) : (
            badge
          )
        }
        onClick={() => {
          if (sub.length) setOpen(!open)
          if (typeof p.onClick === "string") send(p.onClick)
        }}
      >
        {str(p.label) ?? ""}
      </SidebarMenuButton>
      {sub.length > 0 && open && (
        <SidebarMenuSub>
          {sub.map((c, i) => (
            <SidebarMenuSubItem key={c.k ?? `sub-${i}`}>
              <SidebarMenuSubButton isActive={bool(c.p.active)} disabled={bool(c.p.disabled)}
                onClick={typeof c.p.onClick === "string" ? () => send(c.p.onClick) : undefined}>
                <span className="nt-sb-label">{str(c.p.label) ?? ""}</span>
              </SidebarMenuSubButton>
            </SidebarMenuSubItem>
          ))}
        </SidebarMenuSub>
      )}
    </SidebarMenuItem>
  )
}

/** The sidebar's search: what is typed reaches Java as it is typed. */
function SidebarSearch({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const [value, setValue] = useJavaValue(str(p.value))
  return (
    <SidebarInput
      placeholder={str(p.placeholder) ?? "Search"}
      aria-label={str(p.placeholder) ?? "Search"}
      value={value}
      onChange={(e) => {
        setValue(e.target.value)
        if (typeof p.onChange === "string") send(p.onChange, e.target.value)
      }}
    />
  )
}

function Children({ nodes }: { nodes: JavaNode[] | undefined }) {
  return <>{nodes?.map((child, i) => <Render key={child.k ?? `${child.t}-${i}`} node={child} />)}</>
}

/** Sizing every component takes: grow, width, height. */
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

/**
 * A Java menu's entries (ContextMenu, DropdownMenu) as the page's menu takes them: each item's Java code by its
 * handler's name, read when the menu opens, so it is always the newest tree's.
 */
function menuEntries(p: Record<string, unknown>, send: (handler: unknown, value?: unknown) => void, list: unknown = p.entries): MenuEntry[] {
  return ((list as unknown[]) ?? []).map((e): MenuEntry => {
    if (e === "separator") return "separator"
    const item = e as { label: string; handler: string; shortcut?: string; icon?: string; checked?: boolean; disabled?: boolean }
    return {
      label: item.label,
      shortcut: item.shortcut,
      icon: item.icon ? <NamedIcon name={item.icon} /> : undefined,
      checked: item.checked,
      disabled: item.disabled,
      onSelect: typeof p[item.handler] === "string" ? () => send(p[item.handler]) : undefined,
    }
  })
}

/** Fields one line tall: as wide as a form's field, not the whole window, unless sized. */
const LINE_FIELDS = new Set([
  "textField", "passwordField", "searchField", "numberField", "tokenField", "moneyField", "quantityField", "select",
  "emailField", "phoneField", "urlField", "dateField", "percentField", "ibanField", "cardNumberField", "countrySelect",
  "currencySelect", "languageSelect", "valueField", "codeField", "inputGroup", "combobox",
])
/** Fields for paragraphs: wider, still not the whole window. */
const AREA_FIELDS = new Set(["textArea", "grammarTextArea"])

/**
 * A field without a width of its own (width, maxWidth, grow) is at most 360 px wide (560 for a text area), as
 * forms are in Windows 11's and macOS's apps: a row or column no longer stretches it across the window.
 */
function withFieldWidth(node: JavaNode): JavaNode {
  const p = node.p
  const line = LINE_FIELDS.has(node.t)
  if (!line && !AREA_FIELDS.has(node.t)) return node
  if (p.grow === true || typeof p.width === "number" || typeof p.maxWidth === "number") return node
  return { ...node, p: { ...p, maxWidth: line ? 360 : 560 } }
}

/** HTML's shape on any component (Component's id, className, attributes, style), as Java sends it. */
type HtmlSettings = {
  id?: string
  className?: string[]
  attrs?: Record<string, string>
  style?: Record<string, string>
  /** hoverStyle, focusStyle, pressedStyle: CSS declarations by state. */
  states?: Record<string, Record<string, string>>
}

const STATE_SELECTOR: Record<string, string> = { hover: ":hover", focus: ":focus-within", pressed: ":active" }

/**
 * A component's state styles as one CSS rule set, under a class made from what they say (the same styles share
 * one): over the kit's own hover look and the component's inline style, so with !important. Values are Java's,
 * already checked (Style.set).
 */
function stateRules(states: HtmlSettings["states"]): { className: string; css: string } | undefined {
  if (!states || !Object.keys(states).length) return undefined
  const text = JSON.stringify(states)
  let hash = 5381
  for (let i = 0; i < text.length; i++) hash = ((hash << 5) + hash + text.charCodeAt(i)) | 0
  const className = `nt-s-${(hash >>> 0).toString(36)}`
  const css = Object.entries(states)
    .filter(([state]) => STATE_SELECTOR[state])
    .map(([state, decl]) => `.${className}${STATE_SELECTOR[state]}{${Object.entries(decl).map(([k, v]) => `${k}:${v} !important`).join(";")}}`)
    .join("\n")
  return { className, css }
}

/**
 * Puts a component's HTML settings on its outermost element in the page, whatever the component: no component
 * needs code for it. The wrapper draws no box of its own (display: contents); it is there only when a component
 * has settings. Applied again after every draw, and taken off when they change.
 */
function WithHtml({ html, onHover, children }: { html: HtmlSettings; onHover?: (hovering: boolean) => void; children: React.ReactNode }) {
  const box = React.useRef<HTMLSpanElement>(null)
  const focused = React.useRef<Element | null>(null)
  const states = stateRules(html.states)
  // the newest onHover: the listeners stay, the handler changes with each draw
  const hover = React.useRef(onHover)
  hover.current = onHover
  React.useLayoutEffect(() => {
    const el = box.current?.firstElementChild as HTMLElement | null
    if (!el || !onHover) return
    const enter = () => hover.current?.(true)
    const leave = () => hover.current?.(false)
    el.addEventListener("pointerenter", enter)
    el.addEventListener("pointerleave", leave)
    return () => {
      el.removeEventListener("pointerenter", enter)
      el.removeEventListener("pointerleave", leave)
    }
  }, [!!onHover]) // eslint-disable-line react-hooks/exhaustive-deps
  React.useLayoutEffect(() => {
    const el = box.current?.firstElementChild as HTMLElement | null
    if (!el) return
    // autofocus works only as an element first appears: the focus goes there once, as the page would have done
    if (html.attrs && "autofocus" in html.attrs && focused.current !== el) {
      focused.current = el
      requestAnimationFrame(() => el.focus())
    }
    const undo: (() => void)[] = []
    if (html.id && !el.id) {
      el.id = html.id
      undo.push(() => el.removeAttribute("id"))
    }
    for (const c of [...(html.className ?? []), ...(states ? [states.className] : [])]) {
      if (!el.classList.contains(c)) {
        el.classList.add(c)
        undo.push(() => el.classList.remove(c))
      }
    }
    for (const [name, value] of Object.entries(html.attrs ?? {})) {
      // names are Java's checked ones: global attributes, data-*, aria-*; never an event
      if (/^on/i.test(name)) continue
      const before = el.getAttribute(name)
      el.setAttribute(name, value)
      undo.push(() => (before === null ? el.removeAttribute(name) : el.setAttribute(name, before)))
    }
    const style = { ...(html.style ?? {}) }
    // the kit's own display rules would show a hidden one anyway: hidden is hidden
    if (html.attrs && "hidden" in html.attrs) style.display = "none"
    for (const [property, value] of Object.entries(style)) {
      const before = el.style.getPropertyValue(property)
      const priority = el.style.getPropertyPriority(property)
      el.style.setProperty(property, value, property === "display" ? "important" : "")
      undo.push(() => el.style.setProperty(property, before, priority))
    }
    return () => undo.reverse().forEach((u) => u())
  })
  return (
    <span ref={box} style={{ display: "contents" }}>
      {children}
      {states && <style>{states.css}</style>}
    </span>
  )
}

/** A URL a page may follow or load: not javascript:, vbscript: or a data: document (Java checked it already). */
function safeUrl(value: unknown): string | undefined {
  if (typeof value !== "string") return undefined
  const v = value.replace(/[\s\u0000-\u001f]/g, "").toLowerCase()
  const scheme = /^[a-z][a-z0-9+.-]*:/.test(v)
  const ok = !scheme || /^(https?|mailto|tel|blob):/.test(v) || /^data:(image|audio|video)\//.test(v)
  return ok ? value : undefined
}

/** The input types that are a text field to look at (the kit's nt-input). */
const TEXT_INPUTS = new Set(["text", "email", "password", "search", "tel", "url", "number", "date", "time", "datetime-local", "month", "week"])

const URL_ATTRIBUTES = new Set(["href", "src", "action", "formAction", "poster", "cite", "data", "ping", "manifest", "codeBase", "itemType"])

/** A form's fields by name, as Java gets them: a name given more than once (checkboxes) as a list. */
function formFields(form: HTMLFormElement): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  new FormData(form).forEach((value, name) => {
    const v = typeof value === "string" ? value : value.name
    out[name] = name in out ? ([] as unknown[]).concat(out[name], v) : v
  })
  return out
}

/** An HTML element from Java (Html.java): only the tags html-gen allows, only Java's events. */
function HtmlNode({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const tag = str(p.tag) ?? ""
  const ref = React.useRef<HTMLElement>(null)
  const given = (p.a as Record<string, unknown> | undefined) ?? {}
  // what an input holds: from Java, but never over what is being typed (Java's answer can be a key behind)
  const field = tag === "input" || tag === "textarea" || tag === "select"
  React.useLayoutEffect(() => {
    const el = ref.current as HTMLInputElement | null
    if (!el || !field || document.activeElement === el) return
    if ("value" in given && el.value !== String(given.value ?? "")) el.value = String(given.value ?? "")
    if ("checked" in given) el.checked = given.checked === true
  })
  if (!htmlTags.has(tag)) return <Text tone="error">No HTML element "{tag}"</Text>
  const attrs: Record<string, unknown> = {}
  for (const [name, value] of Object.entries(given)) {
    if (/^on/i.test(name) || ["dangerouslySetInnerHTML", "ref", "key", "children", "style", "className"].includes(name)) continue
    if (field && (name === "value" || name === "checked")) {
      attrs[name === "value" ? "defaultValue" : "defaultChecked"] = value
      continue
    }
    attrs[name] = URL_ATTRIBUTES.has(name) ? safeUrl(value) : value
  }
  const id = (name: string) => (typeof p[name] === "string" ? p[name] : undefined)
  const valueOf = (t: EventTarget) => {
    const el = t as HTMLInputElement
    return el.type === "checkbox" || el.type === "radio" ? String(el.checked) : (el.value ?? el.textContent ?? "")
  }
  const events: Record<string, unknown> = {}
  if (id("onClick")) events.onClick = () => send(p.onClick)
  if (id("onDoubleClick")) events.onDoubleClick = () => send(p.onDoubleClick)
  const hoverId = id("onHover")
  if (id("onPointerEnter") || hoverId)
    events.onPointerEnter = () => {
      if (id("onPointerEnter")) send(p.onPointerEnter)
      if (hoverId) send(p.onHover, true)
    }
  if (id("onPointerLeave") || hoverId)
    events.onPointerLeave = () => {
      if (id("onPointerLeave")) send(p.onPointerLeave)
      if (hoverId) send(p.onHover, false)
    }
  if (id("onFocus")) events.onFocus = () => send(p.onFocus)
  if (id("onBlur")) events.onBlur = () => send(p.onBlur)
  if (id("onKeyDown")) events.onKeyDown = (e: React.KeyboardEvent) => send(p.onKeyDown, e.key)
  if (id("onInput")) events.onInput = (e: React.FormEvent) => send(p.onInput, valueOf(e.target))
  if (id("onChange")) events.onChange = (e: React.ChangeEvent) => send(p.onChange, valueOf(e.target))
  if (id("onToggle")) events.onToggle = (e: React.SyntheticEvent) => send(p.onToggle, (e.currentTarget as HTMLDetailsElement).open === true)
  // a form never leaves the app's page: its fields go to Java
  if (tag === "form") {
    events.onSubmit = (e: React.FormEvent<HTMLFormElement>) => {
      e.preventDefault()
      if (id("onSubmit")) send(p.onSubmit, formFields(e.currentTarget))
    }
  }
  const children = voidTags.has(tag)
    ? []
    : (node.c ?? []).map((c, i) => (c.t === "htmlText" ? str(c.p.text) ?? "" : <Render key={c.k ?? i} node={c} />))
  // its global attributes, classes and style straight on it: no wrapper, so the app's CSS (main > h1) still meets it
  const html = (p.html as HtmlSettings | undefined) ?? {}
  const style: Record<string, string> = {}
  for (const [k, v] of Object.entries(html.style ?? {})) style[k.startsWith("--") ? k : k.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())] = v
  const global: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(html.attrs ?? {})) if (!/^on/i.test(k)) global[k] = v
  if ("hidden" in global) style.display = "none"
  const states = stateRules(html.states)
  // the kit's look by default: its own classes on what it has them for (html-controls.css for the rest)
  const kit: string[] = []
  const look: Record<string, string> = {}
  if (p.browser !== true) {
    kit.push("nt-html")
    const type = String(given.type ?? "text")
    if (tag === "input" && TEXT_INPUTS.has(type)) kit.push("nt-input")
    if (tag === "textarea") kit.push("nt-input", "nt-textarea")
    if (tag === "select") kit.push("nt-input")
    if (tag === "button") {
      kit.push("nt-button")
      // a form's submit is its main action
      if (given.type === "submit") look["data-variant"] = "accent"
    }
  }
  const classes = [...kit, ...(html.className ?? []), ...(states ? [states.className] : [])]
  const element = React.createElement(
    tag,
    {
      ...look,
      ...global,
      ...attrs,
      ...events,
      ref,
      id: html.id,
      className: classes.join(" ") || undefined,
      style: { ...sizing(p), ...style },
    },
    ...children
  )
  return states ? (
    <>
      {element}
      <style>{states.css}</style>
    </>
  ) : (
    element
  )
}

function Render({ node }: { node: JavaNode }): React.ReactNode {
  const send = React.useContext(SendContext)
  const html = node.p.html as HtmlSettings | undefined
  const onHover = typeof node.p.onHover === "string" ? (hovering: boolean) => send(node.p.onHover, hovering) : undefined
  // an HTML element takes its own (HtmlNode); a kit component through the wrapper
  if ((!html && !onHover) || node.t === "html") return <RenderNode node={node} />
  return (
    <WithHtml html={html ?? {}} onHover={onHover}>
      <RenderNode node={node} />
    </WithHtml>
  )
}

function RenderNode({ node: given }: { node: JavaNode }): React.ReactNode {
  const send = React.useContext(SendContext)
  const sendAndWait = React.useContext(SendAndWaitContext)
  const node = withFieldWidth(given)
  const p = node.p
  const style = sizing(p)
  switch (node.t) {
    case "frame": {
      const titleBar = slot(node, "titleBar")
      const toolbar = slot(node, "toolbar")
      const statusBar = slot(node, "statusBar")
      const body = slot(node, "body")
      const sidebar = slot(node, "sidebar")
      const platform = (str(p.platform) ?? "auto") as "auto" | "windows" | "mac"
      const theme = (str(p.theme) ?? "system") as "system" | "light" | "dark"
      const main = (
        <>
          {toolbar && <Render node={toolbar} />}
          <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "auto" }}>{body && <Render node={body} />}</div>
          {statusBar && <Render node={statusBar} />}
        </>
      )
      return (
        <NativeProvider platform={platform} theme={theme} accent={str(p.accent)} style={{ height: "100%", display: "flex", flexDirection: "column" }}>
          <Notice notice={p.notice as NoticeData | undefined} />
          {titleBar && <Render node={titleBar} />}
          {sidebar ? (
            // The side bar as tall as the window under the title bar; the toolbar and status bar beside it.
            // The side bar is the row's own child: stretched to the row's height, it scrolls inside itself (a wrapper
            // let it grow to its whole list, and the window scrolled instead).
            <div style={{ flex: 1, minHeight: 0, display: "flex", alignItems: "stretch", overflow: "hidden" }}>
              <Render node={sidebar} />
              <div style={{ flex: 1, minWidth: 0, minHeight: 0, display: "flex", flexDirection: "column" }}>{main}</div>
            </div>
          ) : (
            main
          )}
        </NativeProvider>
      )
    }
    case "titleBar": {
      const leading = (node.c ?? []).filter((c) => c.p.slot === "leading")
      return (
        <TitleBar title={str(p.title)} style={style} leading={leading.length ? <Children nodes={leading} /> : undefined}>
          <Children nodes={node.c?.filter((c) => c.p.slot !== "leading")} />
        </TitleBar>
      )
    }
    case "toolbar":
      return (
        <Toolbar aria-label={str(p.title) ?? "Toolbar"} title={str(p.title)} style={style}>
          <Children nodes={node.c} />
        </Toolbar>
      )
    case "toolbarButton":
      return <ToolbarButton label={str(p.label) ?? ""} pressed={bool(p.pressed)} disabled={bool(p.disabled)} onClick={() => send(p.onClick)} />
    case "spacer":
      return <ToolbarSpacer />
    case "statusBar":
      return (
        <StatusBar style={style}>
          {(node.c ?? []).map((child, i) =>
            child.t === "spacer" ? <StatusBarSpacer key={i} /> : <Render key={child.k ?? i} node={child} />
          )}
        </StatusBar>
      )
    case "statusItem": {
      const dot = str(p.dot) as "online" | "connecting" | "offline" | "error" | undefined
      return <StatusItem icon={dot && <StatusDot state={dot} aria-label={dot} />}>{str(p.text)}</StatusItem>
    }
    case "split": {
      const [sidebar, content] = node.c ?? []
      return (
        <div style={{ display: "flex", flex: 1, minHeight: 0, ...style }}>
          {sidebar && <Render node={sidebar} />}
          <Content style={{ flex: 1, minWidth: 0, overflow: "auto" }}>{content && <Render node={content} />}</Content>
        </div>
      )
    }
    case "sidebar": {
      const footer = node.c?.find((c) => c.t === "sidebarFooter")
      if (node.c?.some((c) => SIDEBAR_PARTS.has(c.t))) {
        const header = node.c.find((c) => c.t === "sidebarHeader")
        const body = node.c.filter((c) => c.t !== "sidebarHeader" && c.t !== "sidebarFooter")
        return (
          <Sidebar
            composed
            aria-label={str(p.label) ?? "Sections"}
            collapsed={bool(p.collapsed)}
            onCollapsedChange={typeof p.onCollapsedChange === "string" ? (v) => send(p.onCollapsedChange, v) : undefined}
            collapsible={(str(p.collapsible) as "icon" | "offcanvas" | "none" | undefined) ?? "icon"}
            side={(str(p.side) as "left" | "right" | undefined) ?? "left"}
            variant={(str(p.variant) as "sidebar" | "floating" | "inset" | undefined) ?? "sidebar"}
            style={style}
          >
            {header && (
              <SidebarHeader>
                <SidebarParts nodes={header.c} />
              </SidebarHeader>
            )}
            <SidebarContent>
              <SidebarParts nodes={body} />
            </SidebarContent>
            {footer && (
              <SidebarFooter>
                <SidebarParts nodes={footer.c} />
              </SidebarFooter>
            )}
          </Sidebar>
        )
      }
      return (
        <Sidebar aria-label="Sections" footer={footer && <Children nodes={footer.c} />} style={style}>
          <Children nodes={node.c?.filter((c) => c.t !== "sidebarFooter")} />
        </Sidebar>
      )
    }
    // ---- documents (Excel, PDF, Word): each library loads the first time one is shown ----
    case "xlsxViewer":
      return <JavaXlsxViewer node={node} send={send} />
    case "xlsxEditor":
      return <JavaXlsxEditor node={node} send={send} />
    case "csvViewer":
      return <JavaCsvViewer node={node} send={send} />
    case "pdfViewer":
      return <JavaPdfViewer node={node} send={send} />
    case "fileThumbnail":
      return <JavaFileThumbnail node={node} send={send} />
    case "boundingBoxCitations":
      return <JavaBoundingBoxCitations node={node} send={send} />
    case "documentSplits":
      return <JavaDocumentSplits node={node} send={send} />
    case "docxViewer":
      return <JavaDocxViewer node={node} send={send} />
    case "docxEditor":
      return <JavaDocxEditor node={node} send={send} />
    case "docxAnnotationCard":
      return <JavaDocxAnnotationCard node={node} send={send} />
    case "documentViewerSidebar":
      return (
        <JavaDocumentViewerSidebar node={node} send={send}>
          <Children nodes={node.c} />
        </JavaDocumentViewerSidebar>
      )
    case "docxLayoutBlocks":
      return <JavaDocxLayoutBlocks node={node} send={send} />
    case "grammarTextArea":
      return <JavaGrammarTextArea node={node} />
    case "sidebarGroup":
      return (
        <SidebarGroup label={str(p.label)} collapsible={bool(p.collapsible)} defaultOpen={p.open === undefined ? true : bool(p.open)} style={style}>
          <SidebarParts nodes={node.c} />
        </SidebarGroup>
      )
    case "sidebarMenuButton":
      return (
        <SidebarMenu>
          <SidebarMenuEntry node={node} />
        </SidebarMenu>
      )
    case "sidebarInput":
      return <SidebarSearch node={node} />
    case "sidebarSeparator":
      return <SidebarSeparator />
    case "sidebarTrigger":
      return <SidebarTrigger onClick={(e) => { e.preventDefault(); send(p.onClick) }} style={style} />
    case "sidebarSection":
      return (
        <SidebarSection title={str(p.title)}>
          <Children nodes={node.c} />
        </SidebarSection>
      )
    case "sidebarItem":
      return <SidebarItem label={str(p.label) ?? ""} badge={num(p.badge)} selected={bool(p.selected)} onSelect={() => send(p.onSelect)} />
    case "box": {
      const row = p.direction === "row"
      return (
        <div
          style={{
            display: "flex",
            flexDirection: row ? "row" : "column",
            gap: num(p.gap) ?? 8,
            padding: num(p.padding),
            flexWrap: bool(p.wrap) ? "wrap" : undefined,
            alignItems: str(p.align) === "stretch" ? "stretch" : str(p.align) ? `flex-${p.align}`.replace("flex-center", "center") : row ? "center" : undefined,
            overflow: bool(p.scroll) ? "auto" : undefined,
            minHeight: 0,
            ...style,
          }}
        >
          <Children nodes={node.c} />
        </div>
      )
    }
    case "surface":
      return (
        <Surface title={str(p.title)} style={style}>
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            <Children nodes={node.c} />
          </div>
        </Surface>
      )
    case "text":
      return (
        <Text
          variant={str(p.variant) as never}
          tone={str(p.tone) as never}
          strong={bool(p.strong)}
          mono={bool(p.mono)}
          lines={num(p.lines)}
          style={style}
        >
          {str(p.text)}
        </Text>
      )
    case "link":
      return (
        <Link
          href={str(p.href)}
          onClick={
            typeof p.onClick === "string"
              ? (e: React.MouseEvent) => {
                  e.preventDefault()
                  send(p.onClick)
                }
              : undefined
          }
        >
          {str(p.text)}
        </Link>
      )
    case "button":
      return (
        <Button variant={(str(p.variant) as "accent" | "subtle") ?? "default"} disabled={bool(p.disabled)} onClick={() => send(p.onClick)} style={style}>
          {str(p.label)}
        </Button>
      )
    case "textField":
      return <JavaTextField node={node} />
    case "textArea":
      return <JavaTextArea node={node} />
    case "checkbox":
      return <Checkbox label={str(p.label)} checked={bool(p.checked)} disabled={bool(p.disabled)} onChange={(e) => send(p.onChange, e.target.checked)} />
    case "switch":
      return <Switch label={str(p.label)} checked={bool(p.checked)} disabled={bool(p.disabled)} onChange={(e) => send(p.onChange, e.target.checked)} />
    case "select":
      return (
        <label style={{ display: "grid", gap: 4, ...style }}>
          {str(p.label) && <Text variant="caption">{str(p.label)}</Text>}
          <Select
            options={(p.options as { value: string; label: string }[]) ?? []}
            value={str(p.value)}
            disabled={bool(p.disabled)}
            onChange={(e) => send(p.onChange, e.target.value)}
          />
        </label>
      )
    case "segmented": {
      const options = (p.options as { value: string; label: string }[]) ?? []
      return (
        <SegmentedControl<string>
          aria-label={str(p.label) ?? "Choice"}
          value={str(p.value) ?? options[0]?.value ?? ""}
          onValueChange={(value) => send(p.onChange, value)}
          segments={options}
        />
      )
    }
    case "progress":
      return <ProgressBar aria-label={str(p.label) ?? "Progress"} value={num(p.value)} style={style} />
    case "contextMenu":
      return (
        <ContextMenu entries={() => menuEntries(p, send)}>
          <Children nodes={node.c} />
        </ContextMenu>
      )
    case "menubar":
      return (
        <Menubar
          menus={((p.menus as { label: string; entries: unknown[] }[]) ?? []).map((m) => ({ label: m.label, entries: () => menuEntries(p, send, m.entries) }))}
          style={style}
        />
      )
    case "commandPalette":
      return (
        <CommandPalette
          open={bool(p.open)}
          onOpenChange={(open) => send(p.onOpenChange, open)}
          shortcut={str(p.shortcut)}
          placeholder={str(p.placeholder)}
          emptyText={str(p.emptyText)}
          commands={((p.commands as { id: string; label: string; group?: string; shortcut?: string; icon?: string }[]) ?? []).map((c) => ({
            ...c,
            onSelect: typeof p[c.id] === "string" ? () => send(p[c.id]) : undefined,
          }))}
        />
      )
    case "dropdownMenu":
      return (
        <DropdownMenu
          label={str(p.label) ?? ""}
          icon={str(p.icon)}
          variant={(str(p.variant) as "subtle" | "accent" | undefined) ?? "default"}
          disabled={bool(p.disabled)}
          entries={() => menuEntries(p, send)}
          style={style}
        />
      )
    case "mediaPlayer": {
      const handler = (name: string) => (typeof p[name] === "string" ? (value?: unknown) => send(p[name], value) : undefined)
      return (
        <MediaPlayer
          src={str(p.src) ?? ""}
          kind={str(p.kind) as "audio" | "video" | undefined}
          title={str(p.title)}
          poster={str(p.poster)}
          captions={str(p.captions)}
          captionsLanguage={str(p.captionsLanguage)}
          autoPlay={bool(p.autoPlay)}
          loop={bool(p.loop)}
          muted={bool(p.muted)}
          volume={num(p.volume)}
          rate={num(p.rate)}
          showRate={p.showRate !== false}
          onPlay={handler("onPlay")}
          onPause={handler("onPause")}
          onEnded={handler("onEnded")}
          onSeek={handler("onSeek")}
          onError={handler("onError")}
          style={style}
        />
      )
    }
    case "html":
      return <HtmlNode node={node} />
    case "htmlText":
      return str(p.text) ?? ""
    case "styleSheet":
      // the app's own CSS, for its className()es; text, never markup
      return <style>{str(p.css) ?? ""}</style>
    case "spinner":
      return <Spinner label={str(p.label)} size={num(p.size)} />
    case "avatar":
      return <Avatar name={str(p.name) ?? "?"} src={str(p.src)} size={num(p.size)} />
    case "pill":
      return (
        <Pill color={str(p.color) as never} variant={str(p.variant) as never}>
          {str(p.text)}
        </Pill>
      )
    case "money":
      return <Money amount={num(p.amount) ?? 0} currency={str(p.currency) ?? "USD"} negative={str(p.negative) as never} signed={bool(p.signed)} />
    case "list":
      return (
        <List aria-label={str(p.label) ?? "List"} style={style}>
          <Children nodes={node.c} />
        </List>
      )
    case "listItem": {
      const leading = slot(node, "leading")
      const menu = (node.c ?? []).filter((c) => c.p.slot === "menu")
      return (
        <ListItem
          title={str(p.title) ?? ""}
          subtitle={str(p.subtitle)}
          trailing={str(p.trailing)}
          leading={leading && <Render node={leading} />}
          selected={bool(p.selected)}
          onSelect={typeof p.onSelect === "string" ? () => send(p.onSelect) : undefined}
          onDoubleClick={typeof p.onDoubleClick === "string" ? () => send(p.onDoubleClick) : undefined}
          onKeyDown={typeof p.onDoubleClick === "string" ? (e: React.KeyboardEvent) => { if (e.key === "Enter") send(p.onDoubleClick) } : undefined}
          menu={menu.length ? menu.map((m) => ({ label: str(m.p.label) ?? "", onSelect: () => send(m.p.onSelect) })) : undefined}
        />
      )
    }
    case "code":
      return <CodeBlock code={str(p.code) ?? ""} language={str(p.language)} style={style} />
    // ---- navigation ----
    case "toolbarSeparator":
      return <ToolbarSeparator />
    case "statusDot":
      return <StatusDot state={(str(p.state) ?? "online") as "online"} aria-label={str(p.state) ?? "state"} />
    case "breadcrumb":
      return (
        <Breadcrumb
          items={(node.c ?? []).map((c) => ({
            label: str(c.p.label) ?? "",
            onSelect: typeof c.p.onSelect === "string" ? () => send(c.p.onSelect) : undefined,
          }))}
        />
      )
    case "tabs": {
      const tabs = (node.c ?? []).map((c) => ({ value: str(c.p.value) ?? "", label: str(c.p.label) ?? "", closable: bool(c.p.closable) }))
      return (
        <Tabs<string>
          aria-label={str(p.label) ?? "Tabs"}
          tabs={tabs}
          value={str(p.value) ?? tabs[0]?.value ?? ""}
          onValueChange={(v) => send(p.onChange, v)}
          onClose={typeof p.onClose === "string" ? (v) => send(p.onClose, v) : undefined}
          onAdd={typeof p.onAdd === "string" ? () => send(p.onAdd) : undefined}
        />
      )
    }
    // ---- layout ----
    case "inspector":
      return (
        <Inspector
          open={p.open !== false}
          onOpenChange={typeof p.onOpenChange === "string" ? (open) => send(p.onOpenChange, open) : undefined}
          title={str(p.title) ?? ""}
          defaultWidth={num(p.defaultWidth)}
        >
          <Children nodes={node.c} />
        </Inspector>
      )
    case "inspectorSection":
      return (
        <InspectorSection title={str(p.title) ?? ""} count={num(p.count)} defaultOpen={p.defaultOpen !== false}>
          <Children nodes={node.c} />
        </InspectorSection>
      )
    case "dialog": {
      const actions = (node.c ?? [])
        .filter((c) => c.p.slot === "action")
        .map((c) => ({
          label: str(c.p.label) ?? "",
          onSelect: () => send(c.p.onSelect),
          primary: bool(c.p.primary),
          cancel: bool(c.p.cancel),
          destructive: bool(c.p.destructive),
        }))
      return (
        <Dialog open={bool(p.open)} title={str(p.title) ?? ""} message={str(p.message)} kind={str(p.kind) as never} actions={actions}>
          <Children nodes={(node.c ?? []).filter((c) => c.p.slot !== "action")} />
        </Dialog>
      )
    }
    case "mdi":
      return <JavaMdi node={node} />
    case "infiniteScroll":
      return (
        <InfiniteScroll
          hasMore={bool(p.hasMore)}
          direction={str(p.direction) === "up" ? "up" : "down"}
          endText={str(p.endText)}
          onLoadMore={() => sendAndWait(p.onLoadMore)}
          style={style}
        >
          <Children nodes={node.c} />
        </InfiniteScroll>
      )
    case "swipeable": {
      const actions = (side: string) =>
        (node.c ?? [])
          .filter((c) => c.p.slot === side)
          .map((c) => ({ label: str(c.p.label) ?? "", tone: str(c.p.tone) as never, onAction: () => send(c.p.onAction) }))
      const content = slot(node, "content")
      return (
        <Swipeable swipeRight={actions("right")} swipeLeft={actions("left")}>
          {content && <Render node={content} />}
        </Swipeable>
      )
    }
    // ---- text ----
    case "label":
      return (
        <Label tone={str(p.tone) as never} strong={bool(p.strong)}>
          {str(p.text)}
        </Label>
      )
    case "inlineCode":
      return <InlineCode>{str(p.text)}</InlineCode>
    case "shortcut":
      return <Shortcut keys={str(p.keys) ?? ""} />
    // ---- inputs ----
    case "passwordField":
      return <JavaPasswordField node={node} />
    case "searchField":
      return <JavaSearchField node={node} />
    case "numberField":
      return (
        <NumberField
          label={str(p.label)}
          hint={str(p.hint)}
          error={str(p.error)}
          value={num(p.value) ?? null}
          min={num(p.min)}
          max={num(p.max)}
          step={num(p.step)}
          fractionDigits={num(p.fractionDigits)}
          before={str(p.before)}
          after={str(p.after)}
          onValueChange={(v) => send(p.onChange, v)}
          style={style}
        />
      )
    case "tokenField":
      return (
        <TokenField
          label={str(p.label)}
          placeholder={str(p.placeholder)}
          value={(p.value as string[] | undefined) ?? []}
          suggestions={p.suggestions as string[] | undefined}
          onValueChange={(tokens) => send(p.onChange, tokens)}
          style={style}
        />
      )
    case "moneyField":
      return (
        <MoneyField
          label={str(p.label)}
          hint={str(p.hint)}
          error={str(p.error)}
          currency={str(p.currency) ?? "USD"}
          currencies={p.currencies as string[] | undefined}
          value={num(p.value) ?? null}
          min={num(p.min)}
          max={num(p.max)}
          onValueChange={(v) => send(p.onChange, v)}
          onCurrencyChange={typeof p.onCurrencyChange === "string" ? (c) => send(p.onCurrencyChange, c) : undefined}
          style={style}
        />
      )
    case "quantityField":
      return (
        <QuantityField
          label={str(p.label)}
          hint={str(p.hint)}
          error={str(p.error)}
          unit={(str(p.unit) ?? "kilogram") as never}
          units={p.units as never}
          convert={bool(p.convert)}
          value={num(p.value) ?? null}
          onValueChange={(v) => send(p.onChange, v)}
          onUnitChange={typeof p.onUnitChange === "string" ? (u) => send(p.onUnitChange, u) : undefined}
          style={style}
        />
      )
    case "richText":
      return (
        <RichTextEditor
          aria-label={str(p.label) ?? "Text"}
          value={str(p.value)}
          placeholder={str(p.placeholder)}
          readOnly={bool(p.readOnly)}
          minHeight={num(p.minHeight)}
          onChange={(v) => send(p.onChange, v.html)}
          style={style}
        />
      )
    case "codeEditor":
      return (
        <CodeEditor
          aria-label={str(p.label) ?? "Code"}
          value={str(p.value)}
          language={str(p.language)}
          fileName={str(p.fileName)}
          readOnly={bool(p.readOnly)}
          lineNumbers={p.lineNumbers as boolean | undefined}
          minimap={p.minimap as boolean | undefined}
          wordWrap={p.wordWrap as boolean | undefined}
          height={num(p.height) ?? 320}
          onChange={(v) => send(p.onChange, v)}
          style={style}
        />
      )
    case "composer":
      return (
        <Composer
          aria-label={str(p.label)}
          placeholder={str(p.placeholder)}
          sending={bool(p.sending)}
          disabled={bool(p.disabled)}
          sendKey={str(p.sendKey) === "mod+enter" ? "mod+enter" : "enter"}
          maxLength={num(p.maxLength)}
          onSend={(message) => sendAndWait(p.onSend, message.text)}
          style={style}
        />
      )
    // ---- content ----
    case "pillGroup":
      return (
        <PillGroup max={num(p.max)} fit={bool(p.fit)}>
          <Children nodes={node.c} />
        </PillGroup>
      )
    case "quantity":
      return (
        <Quantity
          value={num(p.value) ?? 0}
          unit={(str(p.unit) ?? "kilogram") as never}
          as={str(p.as) as never}
          digits={num(p.digits)}
          display={str(p.display) as never}
        />
      )
    case "logView":
      return (
        <LogView
          aria-label={str(p.label) ?? "Log"}
          lines={(p.lines as never[]) ?? []}
          empty={str(p.empty)}
          follow={p.follow as boolean | undefined}
          style={{ minHeight: 200, ...style }}
        />
      )
    case "messageBubble": {
      const mine = bool(p.mine)
      const sender = { id: mine ? "me" : (str(p.sender) ?? "them"), name: str(p.sender) ?? (mine ? "Me" : "Them") }
      const bubble = (
        <MessageBubble
          message={{
            id: node.k ?? str(p.text) ?? "",
            senderId: sender.id,
            text: str(p.text),
            sentAt: str(p.sentAt) ?? new Date().toISOString(),
            state: str(p.state) as never,
          }}
          mine={mine}
          sender={sender}
          showName={!mine && str(p.sender) !== undefined}
          showMeta={str(p.sentAt) !== undefined}
        />
      )
      // Its size from Java (width, grow) on a box around it: the bubble sizes itself to its text.
      return Object.keys(style).length ? <div style={style}>{bubble}</div> : bubble
    }
    case "typing":
      return <TypingIndicator people={((p.people as string[] | undefined) ?? []).map((name) => ({ id: name, name }))} />
    // ---- files ----
    case "fileIcon":
      return <FileIcon name={str(p.name) ?? ""} folder={bool(p.folder)} size={num(p.size)} />
    case "fileChip":
      return (
        <FileChip
          name={str(p.name) ?? ""}
          size={num(p.size)}
          variant={str(p.variant) === "inline" ? "inline" : "card"}
          progress={num(p.progress)}
          status={str(p.status)}
          error={bool(p.error)}
          onOpen={typeof p.onOpen === "string" ? () => send(p.onOpen) : undefined}
          onRemove={typeof p.onRemove === "string" ? () => send(p.onRemove) : undefined}
        />
      )
    case "fileTree":
    case "fileList":
    case "fileGrid":
    case "fileExplorer":
      return <JavaFiles node={node} />
    case "signInForm": {
      const on = (name: string) => typeof p[name] === "string"
      return (
        <SignInForm
          title={str(p.title)}
          subtitle={str(p.subtitle)}
          identifier={str(p.identifier) === "username" ? "username" : "email"}
          password={p.password === false ? false : true}
          providers={(p.providers as { id: string; label: string }[] | undefined) ?? []}
          remember={typeof p.remember === "boolean" ? p.remember : undefined}
          onRememberChange={on("onRememberChange") ? (r) => send(p.onRememberChange, r) : undefined}
          onSubmit={on("onSubmit") ? (identifier, password) => send(p.onSubmit, { identifier, password }) : undefined}
          onProvider={on("onProvider") ? (id) => send(p.onProvider, id) : undefined}
          onForgot={on("onForgot") ? () => send(p.onForgot) : undefined}
          busy={bool(p.busy)}
          error={str(p.error)}
          style={style}
        />
      )
    }
    case "codeField":
      return (
        <CodeField aria-label={str(p.label)} length={num(p.length)} value={str(p.value)} disabled={bool(p.disabled)} error={str(p.error)}
          onChange={typeof p.onChange === "string" ? (c) => send(p.onChange, c) : undefined}
          onComplete={typeof p.onComplete === "string" ? (c) => send(p.onComplete, c) : undefined} />
      )
    case "accountMenu": {
      const entries = (node.c ?? []).filter((c) => c.p.slot === "entry").map((c) => ({ label: str(c.p.label) ?? "", onSelect: () => send(c.p.onSelect) }))
      return (
        <AccountMenu name={str(p.name) ?? ""} email={str(p.email)} picture={str(p.picture)} compact={bool(p.compact)} entries={entries}
          onSwitch={typeof p.onSwitch === "string" ? () => send(p.onSwitch) : undefined}
          onSignOut={typeof p.onSignOut === "string" ? () => send(p.onSignOut) : undefined} />
      )
    }
    case "sessionExpired":
      return (
        <SessionExpired open={bool(p.open)} name={str(p.name)} busy={bool(p.busy)}
          onSignIn={typeof p.onSignIn === "string" ? () => send(p.onSignIn) : undefined}
          onSignOut={typeof p.onSignOut === "string" ? () => send(p.onSignOut) : undefined} />
      )
    case "passwordStrength":
      return <PasswordStrength password={str(p.password) ?? ""} />
    case "changelog":
      return (
        <Changelog markdown={str(p.markdown) ?? ""} since={str(p.since)} only={str(p.only)} upTo={str(p.upTo)} empty={str(p.empty)}
          onLink={typeof p.onLink === "string" ? (href) => send(p.onLink, href) : undefined} style={style} />
      )
    case "updatePrompt": {
      const on = (name: string) => (typeof p[name] === "string" ? () => send(p[name]) : undefined)
      return (
        <UpdatePrompt app={str(p.app) ?? "The app"} version={str(p.version) ?? ""} current={str(p.current)} notes={str(p.notes)}
          status={(str(p.status) as UpdateStatus | undefined) ?? "available"} progress={num(p.progress)} error={str(p.error)}
          onUpdate={on("onUpdate")} onInstall={on("onInstall")} onLater={on("onLater")} onSkip={on("onSkip")}
          onLink={typeof p.onLink === "string" ? (href) => send(p.onLink, href) : undefined} style={style} />
      )
    }
    case "markdown":
      return (
        <Markdown headingOffset={num(p.headingOffset)} onLink={typeof p.onLink === "string" ? (href) => send(p.onLink, href) : undefined} style={style}>
          {str(p.text) ?? ""}
        </Markdown>
      )
    case "loadable": {
      const status = str(p.status)
      if (status === "loading" && !node.c?.length)
        return (
          <div className="nt-state" style={style} role="status" aria-busy="true">
            <Spinner label="Loading" />
          </div>
        )
      if (status === "error" && !node.c?.length)
        return <ErrorState title={str(p.title)} message={str(p.message)} onRetry={typeof p.onRetry === "string" ? () => send(p.onRetry) : undefined} />
      return (
        <div style={{ position: "relative", ...style }} data-reloading={bool(p.fetching) ? "" : undefined}>
          {bool(p.fetching) && <LoadingBar />}
          <Children nodes={node.c} />
        </div>
      )
    }
    // ---- inline text ----
    case "paragraph":
      return (
        <Text variant={str(p.variant) as never} tone={str(p.tone) as never} style={{ display: "block", ...style }}>
          {(node.c ?? []).map((child, i) =>
            child.t === "span" ? (
              <span key={i} style={{ fontWeight: bool(child.p.strong) ? 600 : undefined, fontStyle: bool(child.p.italic) ? "italic" : undefined }}>
                {str(child.p.text)}
              </span>
            ) : (
              <Render key={child.k ?? i} node={child} />
            )
          )}
        </Text>
      )
    // ---- value objects ----
    case "emailField":
    case "phoneField":
    case "urlField":
    case "dateField":
    case "percentField":
    case "ibanField":
    case "cardNumberField":
      return <JavaValueField node={node} />
    case "countrySelect":
    case "currencySelect":
    case "languageSelect": {
      const props = {
        label: str(p.label),
        value: str(p.value),
        optional: bool(p.optional),
        disabled: bool(p.disabled),
        options: p.options as { value: string; label: string }[] | undefined,
        onValueChange: (v: string) => send(p.onChange, v),
        style,
      }
      if (node.t === "countrySelect") return <CountrySelect {...props} />
      if (node.t === "currencySelect") return <CurrencySelect {...props} />
      return <LanguageSelect {...props} />
    }
    // ---- use cases ----
    case "dataTable":
      return <JavaDataTable node={node} />
    case "hbox":
    case "vbox": {
      const Box = node.t === "hbox" ? HBox : VBox
      return (
        <Box
          spacing={num(p.spacing)}
          padding={p.padding as Insets | undefined}
          alignment={str(p.alignment) as Pos | undefined}
          fill={p.fill === false ? false : undefined}
          wrap={bool(p.wrap)}
          scroll={bool(p.scroll)}
          style={style}
        >
          <Children nodes={node.c} />
        </Box>
      )
    }
    case "gridpane":
      return (
        <GridPane
          hgap={num(p.hgap)}
          vgap={num(p.vgap)}
          padding={p.padding as Insets | undefined}
          alignment={str(p.alignment) as Pos | undefined}
          columns={p.columns as TrackSize[] | undefined}
          rows={(p.rows as TrackSize[] | undefined)?.map((r) => ({ ...r }))}
          style={style}
        >
          {(node.c ?? []).map((cell, i) => (
            <GridCell
              key={cell.k ?? i}
              column={num(cell.p.column) ?? 0}
              row={num(cell.p.row) ?? 0}
              columnSpan={num(cell.p.columnSpan)}
              rowSpan={num(cell.p.rowSpan)}
              halignment={str(cell.p.halignment) as HPos | undefined}
              valignment={str(cell.p.valignment) as VPos | undefined}
            >
              <Children nodes={cell.c} />
            </GridCell>
          ))}
        </GridPane>
      )
    case "borderpane": {
      const area = (name: string) => {
        const child = slot(node, name)
        return child ? <Render node={child} /> : undefined
      }
      return (
        <BorderPane
          top={area("top")}
          left={area("left")}
          center={area("center")}
          right={area("right")}
          bottom={area("bottom")}
          padding={p.padding as Insets | undefined}
          spacing={num(p.spacing)}
          style={style}
        />
      )
    }
    case "stackpane":
      return (
        <StackPane alignment={str(p.alignment) as Pos | undefined} padding={p.padding as Insets | undefined} style={style}>
          {(node.c ?? []).map((child, i) =>
            child.t === "stackItem" ? (
              <StackItem key={child.k ?? i} alignment={str(child.p.alignment) as Pos | undefined} margin={child.p.margin as Insets | undefined}>
                <Children nodes={child.c} />
              </StackItem>
            ) : (
              <Render key={child.k ?? i} node={child} />
            )
          )}
        </StackPane>
      )
    case "splitpane":
      return <JavaSplitPane node={node} />
    case "flowpane":
      return (
        <FlowPane orientation={str(p.orientation) === "vertical" ? "vertical" : "horizontal"} hgap={num(p.hgap)} vgap={num(p.vgap)}
          padding={p.padding as Insets | undefined} alignment={str(p.alignment) as Pos | undefined} style={style}>
          <Children nodes={node.c} />
        </FlowPane>
      )
    case "tilepane":
      return (
        <TilePane tileWidth={num(p.tileWidth)} tileHeight={num(p.tileHeight)} columns={num(p.columns)} hgap={num(p.hgap)} vgap={num(p.vgap)}
          padding={p.padding as Insets | undefined} tileAlignment={str(p.tileAlignment) as Pos | undefined} style={style}>
          {(node.c ?? []).map((child, i) => <Render key={child.k ?? i} node={child} />)}
        </TilePane>
      )
    case "anchorpane":
      return (
        <AnchorPane padding={p.padding as Insets | undefined} style={style}>
          {(node.c ?? []).map((child, i) => (
            <Anchored key={child.k ?? i} anchors={(child.p.anchors as Anchors | undefined) ?? {}}>
              <Children nodes={child.c} />
            </Anchored>
          ))}
        </AnchorPane>
      )
    case "scrollpane":
      return (
        <ScrollPane fitToWidth={p.fitToWidth === false ? false : true} fitToHeight={bool(p.fitToHeight)}
          hbarPolicy={str(p.hbarPolicy) as "as-needed" | "always" | "never" | undefined} vbarPolicy={str(p.vbarPolicy) as "as-needed" | "always" | "never" | undefined}
          padding={p.padding as Insets | undefined} style={style}>
          <Children nodes={node.c} />
        </ScrollPane>
      )
    case "cardpane":
      return (
        <CardPane shown={str(p.shown)} transition={(str(p.transition) as "none" | "fade" | "slide" | undefined) ?? "fade"} style={style}>
          {(node.c ?? []).map((card, i) => (
            <Card key={str(card.p.name) ?? i} name={str(card.p.name) ?? String(i)}>
              <Children nodes={card.c} />
            </Card>
          ))}
        </CardPane>
      )
    case "carousel":
      return (
        <Carousel aria-label={str(p.label) ?? "Pages"} index={num(p.index)} interval={num(p.interval)} loop={p.loop === false ? false : true}
          arrows={p.arrows === false ? false : true} dots={p.dots === false ? false : true}
          onIndexChange={typeof p.onIndexChange === "string" ? (i) => send(p.onIndexChange, i) : undefined} style={style}>
          {(node.c ?? []).map((child, i) => <Render key={child.k ?? i} node={child} />)}
        </Carousel>
      )
    case "titledPane":
      return (
        <TitledPane title={str(p.title) ?? ""} expanded={typeof p.expanded === "boolean" ? p.expanded : undefined}
          collapsible={p.collapsible === false ? false : true}
          onExpandedChange={typeof p.onExpandedChange === "string" ? (e) => send(p.onExpandedChange, e) : undefined} style={style}>
          <Children nodes={node.c} />
        </TitledPane>
      )
    case "accordion":
      return (
        <Accordion expanded={num(p.expanded)} onExpandedChange={typeof p.onExpandedChange === "string" ? (i) => send(p.onExpandedChange, i) : undefined} style={style}
          panes={(node.c ?? []).map((pane, i) => ({ key: pane.k ?? String(i), title: str(pane.p.title) ?? "", content: <Children nodes={pane.c} /> }))} />
      )
    case "dataList":
    case "dataGrid":
    case "dataTree":
    case "dataMap":
      return <JavaCollection node={node} />
    default: {
      // Components java-gen made from their props (java-gen.json): asked only for types with no case above.
      const generated = renderGenerated(node, { send, style, child: (c, i) => <Render key={c.k ?? i} node={c} /> })
      if (generated !== undefined) return generated
      return (
        <Text tone="error" style={style}>
          No component "{node.t}"
        </Text>
      )
    }
  }
}

type NoticeData = { id: number; title: string; message: string; details?: string; kind: "info" | "warning" | "error" }

/**
 * What Java has to tell the person (a UiException from a handler, or UiWindow.notify): in the kit's dialog, once.
 * Problems in the error dialog (the human message, the technical one folded under its details); news in a message.
 */
function Notice({ notice }: { notice: NoticeData | undefined }) {
  const dialogs = useDialogs()
  const shown = React.useRef<number>(0)
  React.useEffect(() => {
    if (!notice || notice.id === shown.current) return
    shown.current = notice.id
    if (notice.kind === "info") void dialogs.message({ title: notice.title, message: notice.message, kind: "info" })
    else void dialogs.error({ title: notice.title, message: notice.message, details: notice.details })
  }, [notice, dialogs])
  return null
}

/**
 * Windows inside the window (MDI): where each is, its size and state stay here as the user moves them; which
 * windows there are, their titles and what is in them come from Java. A window Java no longer sends closes; one the
 * user closes tells Java (onClose).
 */
function JavaMdi({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const windows = (node.c ?? []).filter((c) => c.t === "mdiWindow")
  const mdi = useMdi()
  const ids = windows.map((w) => str(w.p.id) ?? "").join("|")
  const layout = node.p.layout as MdiLayout | undefined
  React.useEffect(() => {
    const present = new Set(windows.map((w) => str(w.p.id)))
    for (const w of windows) {
      if (!mdi.windows.some((m) => m.id === str(w.p.id))) {
        const saved = layout?.find((l) => l.id === str(w.p.id))
        const at = saved
          ? { x: saved.x, y: saved.y, width: saved.width, height: saved.height, state: saved.state }
          : num(w.p.x) !== undefined
            ? { x: num(w.p.x), y: num(w.p.y), width: num(w.p.width), height: num(w.p.height) }
            : {}
        const state = (str(w.p.state) as MdiWindowState | undefined) ?? undefined
        mdi.open({ id: str(w.p.id) ?? "", title: str(w.p.title) ?? "", content: null, ...(state ? { state } : {}), ...at })
      }
    }
    for (const m of mdi.windows) if (!present.has(m.id)) mdi.close(m.id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ids])
  // A layout Java gives that differs from where the windows are (restored, or set by Java): put them there.
  const given = JSON.stringify(layout ?? null)
  const reported = React.useRef<string | null>(null)
  React.useEffect(() => {
    if (!layout || given === reported.current) return
    if (JSON.stringify(layoutOf(mdi.windows).filter((w) => layout.some((l) => l.id === w.id))) === given) return
    applyLayout(mdi, layout)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [given])
  // Where the windows are, told to Java once it settles (not for every pixel of a drag).
  const now = JSON.stringify(layoutOf(mdi.windows))
  React.useEffect(() => {
    if (typeof node.p.onLayoutChange !== "string" || mdi.windows.length === 0) return
    const timer = setTimeout(() => {
      reported.current = now
      send(node.p.onLayoutChange, JSON.parse(now))
    }, 400)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [now])
  // The content and titles are always Java's newest; where each is, the hook's.
  const live = {
    ...mdi,
    windows: mdi.windows.map((m) => {
      const w = windows.find((x) => str(x.p.id) === m.id)
      return w ? { ...m, title: str(w.p.title) ?? m.title, content: <Children nodes={w.c} /> } : m
    }),
    close: (id: string) => {
      const w = windows.find((x) => str(x.p.id) === id)
      mdi.close(id)
      if (w && typeof w.p.onClose === "string") send(w.p.onClose)
    },
  }
  return (
    <MdiArea mdi={live} aria-label={str(node.p.label) ?? "Windows"} empty={str(node.p.empty)} arrange={bool(node.p.arrange)}
      style={{ flex: 1, minHeight: 320, ...sizing(node.p) }} />
  )
}

/** A value object's field: the text is kept here as it is typed, Java's check comes back as its error. */
function JavaValueField({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const props = {
    label: str(p.label),
    hint: str(p.hint),
    error: str(p.error),
    disabled: bool(p.disabled),
    value: str(p.value),
    onValueChange: (text: string) => send(p.onChange, text),
    style: sizing(p),
  }
  switch (node.t) {
    case "emailField":
      return <EmailField {...props} />
    case "phoneField":
      return <PhoneField {...props} />
    case "urlField":
      return <UrlField {...props} />
    case "dateField":
      return <DateField {...props} />
    case "percentField":
      return <PercentField {...props} />
    case "ibanField":
      return <IbanField {...props} />
    default:
      return <CardNumberField {...props} />
  }
}

/** A data table: its rows, columns and state from Java; what the person does goes back as events. */
/** A SplitPane: dragged in the page, told to Java once a divider is let go (not for every pixel). */
function JavaSplitPane({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const fromJava = p.dividerPositions as number[] | undefined
  const [positions, setPositions] = React.useState<number[] | undefined>(fromJava)
  const given = JSON.stringify(fromJava)
  React.useEffect(() => setPositions(fromJava), [given]) // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <SplitPane
      orientation={str(p.orientation) === "vertical" ? "vertical" : "horizontal"}
      dividerPositions={positions}
      onDividerPositionsChange={setPositions}
      onDividerPositionsCommit={typeof p.onDividerPositionsChange === "string" ? (next) => send(p.onDividerPositionsChange, next) : undefined}
      minSize={num(p.minSize)}
      style={{ minHeight: 0, ...sizing(p) }}
    >
      {(node.c ?? []).map((child, i) => (
        <Render key={child.k ?? i} node={child} />
      ))}
    </SplitPane>
  )
}

/** The file views; a rename asks for the new name here, and Java gets the path and the name. */
function JavaFiles({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const [renaming, setRenaming] = React.useState<string | null>(null)
  const [name, setName] = React.useState("")
  const common = {
    "aria-label": str(p.label) ?? "Files",
    items: (p.items as never[]) ?? [],
    selected: p.selected as string[] | undefined,
    onSelectionChange: typeof p.onSelectionChange === "string" ? (paths: string[]) => send(p.onSelectionChange, paths) : undefined,
    onOpen: typeof p.onOpen === "string" ? (path: string) => send(p.onOpen, path) : undefined,
    onRename:
      typeof p.onRename === "string"
        ? (path: string) => {
            setName(path.split("/").pop() ?? path)
            setRenaming(path)
          }
        : undefined,
    onDelete: typeof p.onDelete === "string" ? (path: string) => send(p.onDelete, path) : undefined,
    style: { minHeight: 240, ...sizing(p) },
  }
  const done = (rename: boolean) => {
    if (rename && renaming && name.trim() && name.trim() !== renaming.split("/").pop()) send(p.onRename, { path: renaming, name: name.trim() })
    setRenaming(null)
  }
  const view =
    node.t === "fileTree" ? <FileTree {...common} /> : node.t === "fileList" ? <FileList {...common} /> : node.t === "fileGrid" ? <FileGrid {...common} /> : <FileExplorer {...common} />
  return (
    <>
      {view}
      <Dialog
        open={renaming !== null}
        title="Rename"
        message={renaming ? `A new name for ${renaming.split("/").pop()}` : undefined}
        actions={[
          { label: "Rename", primary: true, onSelect: () => done(true), disabled: !name.trim() },
          { label: "Cancel", cancel: true, onSelect: () => done(false) },
        ]}
      >
        <TextField
          aria-label="New name"
          value={name}
          autoFocus
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") done(true)
          }}
        />
      </Dialog>
    </>
  )
}

/** What every collection gets from Java: the query and its pages, and its states (loading, error, empty). */
function collectionStates(p: Record<string, unknown>, send: Send) {
  const has = (name: string) => typeof p[name] === "string"
  const empty = p.emptyState as { title?: string; message?: string; action?: string } | undefined
  return {
    pageSize: num(p.pageSize),
    query: p.query as Partial<CollectionQuery> | undefined,
    paging: p.paging as Paging | undefined,
    onQueryChange: has("onQueryChange") ? (q: CollectionQuery) => send(p.onQueryChange, q) : undefined,
    loading: bool(p.loading),
    error: (p.error as { title?: string; message?: string } | undefined) ?? null,
    onRetry: has("onRetry") ? () => send(p.onRetry) : undefined,
    empty: empty?.title
      ? {
          title: empty.title,
          message: empty.message,
          action: empty.action && has("onEmptyAction") ? { label: empty.action, onClick: () => send(p.onEmptyAction) } : undefined,
        }
      : str(p.empty),
  }
}

function JavaDataTable({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const menu = (node.c ?? []).filter((c) => c.p.slot === "rowMenu")
  const actions = (node.c ?? []).filter((c) => c.p.slot === "action")
  const has = (name: string) => typeof p[name] === "string"
  const sort = p.sort as { key: string; desc: boolean } | null | undefined
  return (
    <DataTable
      aria-label={str(p.label) ?? "Table"}
      columns={(p.columns as DataColumn[]) ?? []}
      rows={(p.rows as DataRow[]) ?? []}
      rowKey={str(p.rowKey)}
      selectable={bool(p.selectable)}
      selected={p.selected as string[] | undefined}
      onSelectionChange={has("onSelectionChange") ? (ids) => send(p.onSelectionChange, ids) : undefined}
      sort={has("onSortChange") ? (sort ?? null) : undefined}
      onSortChange={has("onSortChange") ? (s) => send(p.onSortChange, s) : undefined}
      search={bool(p.search)}
      filter={has("onFilterChange") ? (str(p.filter) ?? "") : undefined}
      onFilterChange={has("onFilterChange") ? (t) => send(p.onFilterChange, t) : undefined}
      {...collectionStates(p, send)}
      page={num(p.page)}
      total={num(p.total)}
      onPageChange={has("onPageChange") ? (n) => send(p.onPageChange, n) : undefined}
      onRowOpen={has("onRowOpen") ? (id) => send(p.onRowOpen, id) : undefined}
      rowMenu={menu.length ? (row) => menu.map((m) => ({ label: str(m.p.label) ?? "", onSelect: () => send(m.p.onSelect, String(row[str(p.rowKey) ?? "id"])) })) : undefined}
      onCellEdit={has("onCellEdit") ? (id, key, value) => send(p.onCellEdit, { id, key, value }) : undefined}
      actions={actions.map((a) => ({ label: str(a.p.label) ?? "", onSelect: (ids) => send(a.p.onSelect, ids) }))}
      style={{ minHeight: 0, ...sizing(p) }}
    />
  )
}

/** DataList, DataGrid, DataTree and DataMap: their data from Java; what the person does goes back as events. */
function JavaCollection({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const sendAndWait = React.useContext(SendAndWaitContext)
  const p = node.p
  const has = (name: string) => typeof p[name] === "string"
  const keyOf = str(p.itemKey) ?? str(p.rowKey) ?? "id"
  const menu = (node.c ?? []).filter((c) => c.p.slot === "menu")
  const itemMenu = menu.length
    ? (item: DataRow) => menu.map((m) => ({ label: str(m.p.label) ?? "", onSelect: () => send(m.p.onSelect, String(item[keyOf])) }))
    : undefined
  const common = {
    "aria-label": str(p.label) ?? "Items",
    selected: p.selected as string[] | undefined,
    onSelectionChange: has("onSelectionChange") ? (ids: string[]) => send(p.onSelectionChange, ids) : undefined,
    ...collectionStates(p, send),
    style: { minHeight: 0, ...sizing(p) },
  }
  switch (node.t) {
    case "dataList":
      return (
        <DataList
          {...common}
          items={(p.items as DataRow[]) ?? []}
          itemKey={keyOf}
          title={str(p.title) ?? "title"}
          subtitle={str(p.subtitle)}
          avatar={str(p.avatar)}
          pill={str(p.pill)}
          trailing={str(p.trailing)}
          search={bool(p.search)}
          multiple={bool(p.multiple)}
          hasMore={bool(p.hasMore)}
          onLoadMore={has("onLoadMore") ? () => sendAndWait(p.onLoadMore) : undefined}
          onOpen={has("onOpen") ? (id) => send(p.onOpen, id) : undefined}
          itemMenu={itemMenu}
        />
      )
    case "dataGrid":
      return (
        <DataGrid
          {...common}
          items={(p.items as DataRow[]) ?? []}
          itemKey={keyOf}
          title={str(p.title) ?? "title"}
          subtitle={str(p.subtitle)}
          image={str(p.image)}
          pill={str(p.pill)}
          footer={str(p.footer)}
          minWidth={num(p.minWidth)}
          multiple={bool(p.multiple)}
          onOpen={has("onOpen") ? (id) => send(p.onOpen, id) : undefined}
          itemMenu={itemMenu}
        />
      )
    case "dataTree":
      return (
        <DataTree
          {...common}
          columns={(p.columns as DataColumn[]) ?? []}
          rows={(p.rows as DataRow[]) ?? []}
          rowKey={keyOf}
          expanded={p.expanded as string[] | undefined}
          onExpandedChange={has("onExpandedChange") ? (ids) => send(p.onExpandedChange, ids) : undefined}
          onExpand={has("onExpand") ? (id) => send(p.onExpand, id) : undefined}
          selectable={bool(p.selectable)}
          onRowOpen={has("onRowOpen") ? (id) => send(p.onRowOpen, id) : undefined}
          rowMenu={itemMenu}
        />
      )
    default:
      return (
        <DataMap
          aria-label={common["aria-label"]}
          value={(p.value as Record<string, unknown>) ?? {}}
          depth={num(p.depth)}
          onEdit={has("onEdit") ? (path, value) => send(p.onEdit, { path, value }) : undefined}
          onAdd={has("onAdd") ? (key, value) => send(p.onAdd, { key, value }) : undefined}
          onRemove={has("onRemove") ? (path) => send(p.onRemove, path) : undefined}
          style={common.style}
        />
      )
  }
}

function JavaPasswordField({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const [value, setValue] = useJavaValue(str(p.value))
  return (
    <PasswordField
      label={str(p.label)}
      hint={str(p.hint)}
      error={str(p.error)}
      placeholder={str(p.placeholder)}
      disabled={bool(p.disabled)}
      value={value}
      onChange={(e) => {
        setValue(e.target.value)
        send(p.onChange, e.target.value)
      }}
      style={sizing(p)}
    />
  )
}

function JavaSearchField({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const [value, setValue] = useJavaValue(str(p.value))
  return (
    <SearchField
      aria-label={str(p.label) ?? "Search"}
      placeholder={str(p.placeholder)}
      value={value}
      onChange={(e) => {
        setValue(e.target.value)
        send(p.onChange, e.target.value)
      }}
      onKeyDown={(e) => {
        if (e.key === "Enter" && typeof p.onSubmit === "string") send(p.onSubmit, (e.target as HTMLInputElement).value)
      }}
      style={sizing(p)}
    />
  )
}

/**
 * A text field Java fills in: what the user types shows at once (kept here), each change goes to Java, and Java's
 * value wins only when Java changes it (so the cursor never jumps while a change is on its way).
 */
function JavaTextField({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const [value, setValue] = useJavaValue(str(p.value))
  return (
    <TextField
      label={str(p.label)}
      hint={str(p.hint)}
      error={str(p.error)}
      placeholder={str(p.placeholder)}
      type={bool(p.password) ? "password" : "text"}
      disabled={bool(p.disabled)}
      maxLength={num(p.maxLength)}
      value={value}
      onChange={(e) => {
        // what allow() and maxLength() refuse does not appear: a letter in a code field, the 11th character
        const kept = allowedText(e.target.value, str(p.allow), num(p.maxLength))
        if (kept === value) return
        setValue(kept)
        send(p.onChange, kept)
      }}
      onKeyDown={(e) => {
        if (e.key === "Enter" && typeof p.onSubmit === "string") send(p.onSubmit, (e.target as HTMLInputElement).value)
      }}
      style={sizing(p)}
    />
  )
}

function JavaTextArea({ node }: { node: JavaNode }) {
  const send = React.useContext(SendContext)
  const p = node.p
  const [value, setValue] = useJavaValue(str(p.value))
  return (
    <TextArea
      label={str(p.label)}
      placeholder={str(p.placeholder)}
      rows={num(p.rows)}
      value={value}
      onChange={(e) => {
        setValue(e.target.value)
        send(p.onChange, e.target.value)
      }}
      style={sizing(p)}
    />
  )
}

/** The text shown: Java's, until the user types; Java's again whenever Java sends a different one. */
function useJavaValue(fromJava: string | undefined): [string, (v: string) => void] {
  const [value, setValue] = React.useState(fromJava ?? "")
  const last = React.useRef(fromJava)
  if (fromJava !== last.current) {
    last.current = fromJava
    if (fromJava !== undefined && fromJava !== value) setValue(fromJava)
  }
  return [value, setValue]
}

/** Shows the Java screen of this window in {@code element}. */
export function mountJavaUi(element: HTMLElement) {
  createRoot(element).render(<JavaUi />)
}
