import * as React from "react"
import { createRoot } from "react-dom/client"

import { Avatar } from "./components/avatar"
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
import { Link, Text } from "./components/text"
import { TextArea } from "./components/text-inputs"
import { TextField } from "./components/text-field"
import { TitleBar } from "./components/title-bar"
import { Toolbar, ToolbarButton, ToolbarSpacer } from "./components/toolbar"

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

function JavaUi() {
  const [tree, setTree] = React.useState<JavaNode | null>(null)
  const [error, setError] = React.useState<string>()
  // Actions go to Java one at a time, in order; each answer is the newest tree.
  const queue = React.useRef(Promise.resolve())

  const send = React.useCallback<Send>((handler, value) => {
    if (typeof handler !== "string") return
    queue.current = queue.current.then(() =>
      bridge()
        .invoke("webview4j.ui.event", { handler, value: value ?? null })
        .then((next) => setTree(next as JavaNode), (e: Error) => setError(e.message))
    )
  }, [])

  React.useEffect(() => {
    const stop = bridge().listen("webview4j.ui.tree", (next) => setTree(next as JavaNode))
    bridge().invoke("webview4j.ui.ready").then((next) => setTree(next as JavaNode), (e: Error) => setError(e.message))
    return stop
  }, [])

  if (error) return <pre style={{ padding: 16, color: "crimson", whiteSpace: "pre-wrap" }}>{error}</pre>
  if (!tree) return null
  return (
    <SendContext.Provider value={send}>
      <Render node={tree} />
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
