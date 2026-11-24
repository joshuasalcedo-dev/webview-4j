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
import { MdiArea, useMdi } from "./components/mdi"
import { MoneyField } from "./components/money"
import { PillGroup } from "./components/pill"
import { Quantity, QuantityField } from "./components/quantity"
import { RichTextEditor } from "./components/rich-text"
import { SearchField } from "./components/search-field"
import { Swipeable } from "./components/swipeable"
import { Tabs } from "./components/tabs"
import { NumberField, PasswordField, TokenField } from "./components/text-inputs"
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
import { Sidebar, SidebarItem, SidebarSection } from "./components/sidebar"
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

const bridge = (): Bridge => {
  const found = (window as unknown as { __webview4j?: Bridge }).__webview4j
  if (!found) throw new Error("the Java UI runs in a webview-4j window")
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

function Children({ nodes }: { nodes: JavaNode[] | undefined }) {
  return <>{nodes?.map((child, i) => <Render key={child.k ?? `${child.t}-${i}`} node={child} />)}</>
}

/** Sizing every component takes: grow, width, height. */
function sizing(p: Record<string, unknown>): React.CSSProperties {
  return {
    ...(p.grow === true ? { flex: 1, minWidth: 0, minHeight: 0 } : {}),
    ...(typeof p.width === "number" ? { width: p.width } : {}),
    ...(typeof p.height === "number" ? { height: p.height } : {}),
  }
}

function Render({ node }: { node: JavaNode }): React.ReactNode {
  const send = React.useContext(SendContext)
  const sendAndWait = React.useContext(SendAndWaitContext)
  const p = node.p
  const style = sizing(p)
  switch (node.t) {
    case "frame": {
      const titleBar = slot(node, "titleBar")
      const toolbar = slot(node, "toolbar")
      const statusBar = slot(node, "statusBar")
      const body = slot(node, "body")
      const platform = (str(p.platform) ?? "auto") as "auto" | "windows" | "mac"
      const theme = (str(p.theme) ?? "system") as "system" | "light" | "dark"
      return (
        <NativeProvider platform={platform} theme={theme} accent={str(p.accent)} style={{ height: "100%", display: "flex", flexDirection: "column" }}>
          <Notice notice={p.notice as NoticeData | undefined} />
          {titleBar && <Render node={titleBar} />}
          {toolbar && <Render node={toolbar} />}
          <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "auto" }}>{body && <Render node={body} />}</div>
          {statusBar && <Render node={statusBar} />}
        </NativeProvider>
      )
    }
    case "titleBar":
      return (
        <TitleBar title={str(p.title)} style={style}>
          <Children nodes={node.c} />
        </TitleBar>
      )
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
      return (
        <Sidebar aria-label="Sections" footer={footer && <Children nodes={footer.c} />} style={style}>
          <Children nodes={node.c?.filter((c) => c.t !== "sidebarFooter")} />
        </Sidebar>
      )
    }
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
      return (
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
    case "fileExplorer": {
      const common = {
        "aria-label": str(p.label) ?? "Files",
        items: (p.items as never[]) ?? [],
        selected: p.selected as string[] | undefined,
        onSelectionChange: typeof p.onSelectionChange === "string" ? (paths: string[]) => send(p.onSelectionChange, paths) : undefined,
        onOpen: typeof p.onOpen === "string" ? (path: string) => send(p.onOpen, path) : undefined,
        onRename: typeof p.onRename === "string" ? (path: string) => send(p.onRename, path) : undefined,
        onDelete: typeof p.onDelete === "string" ? (path: string) => send(p.onDelete, path) : undefined,
        style: { minHeight: 240, ...style },
      }
      if (node.t === "fileTree") return <FileTree {...common} />
      if (node.t === "fileList") return <FileList {...common} />
      if (node.t === "fileGrid") return <FileGrid {...common} />
      return <FileExplorer {...common} />
    }
    default:
      return (
        <Text tone="error" style={style}>
          No component "{node.t}"
        </Text>
      )
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
  React.useEffect(() => {
    const present = new Set(windows.map((w) => str(w.p.id)))
    for (const w of windows) {
      if (!mdi.windows.some((m) => m.id === str(w.p.id))) {
        const at = num(w.p.x) !== undefined ? { x: num(w.p.x), y: num(w.p.y), width: num(w.p.width), height: num(w.p.height) } : {}
        mdi.open({ id: str(w.p.id) ?? "", title: str(w.p.title) ?? "", content: null, ...at })
      }
    }
    for (const m of mdi.windows) if (!present.has(m.id)) mdi.close(m.id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ids])
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
    <MdiArea mdi={live} aria-label={str(node.p.label) ?? "Windows"} empty={str(node.p.empty)} style={{ flex: 1, minHeight: 320, ...sizing(node.p) }} />
  )
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
