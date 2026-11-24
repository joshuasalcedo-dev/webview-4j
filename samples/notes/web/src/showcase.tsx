import * as React from "react"
import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { Avatar } from "@webview4j/ui/components/avatar"
import { Button } from "@webview4j/ui/components/button"
import { Checkbox } from "@webview4j/ui/components/checkbox"
import { Spinner } from "@webview4j/ui/components/infinite-scroll"
import { List, ListItem } from "@webview4j/ui/components/list"
import { LogView, type LogViewLine } from "@webview4j/ui/components/log-view"
import { MdiArea, useMdi, useMdiWindow } from "@webview4j/ui/components/mdi"
import { Money } from "@webview4j/ui/components/money"
import { Pill } from "@webview4j/ui/components/pill"
import { ProgressBar } from "@webview4j/ui/components/progress-bar"
import { NativeProvider } from "@webview4j/ui/components/provider"
import { SegmentedControl } from "@webview4j/ui/components/segmented-control"
import { Select } from "@webview4j/ui/components/select"
import { StatusBar, StatusBarSpacer, StatusDot, StatusItem } from "@webview4j/ui/components/status-bar"
import { Surface } from "@webview4j/ui/components/surface"
import { Switch } from "@webview4j/ui/components/switch"
import { Text } from "@webview4j/ui/components/text"
import { TextField } from "@webview4j/ui/components/text-field"
import { TitleBar } from "@webview4j/ui/components/title-bar"
import { Toolbar, ToolbarButton, ToolbarSpacer } from "@webview4j/ui/components/toolbar"
import { isWebView4j, listen } from "@webview4j/api"
import { appearance, showcase } from "./api.generated"
import type { Look } from "./main"

type ThemeChoice = "system" | "light" | "dark"

/** What the embedded site sent, for the Network window wherever it is. */
const NetworkContext = React.createContext<LogViewLine[]>([])

/**
 * The showcase window: an MDI workspace whose windows show the components, a website embedded by Java (a native
 * webview inside this window), what that site sends over the network, and whether this computer is online.
 */
function Showcase({ look, onLookChange }: { look: Look; onLookChange: (look: Look) => void }) {
  const [theme, setTheme] = React.useState<ThemeChoice>("system")
  const [network, setNetwork] = React.useState<LogViewLine[]>([])
  const online = useOnline()

  React.useEffect(
    () =>
      listen<{ kind: string; level: LogViewLine["level"]; message: string }>("showcase.network", (event) =>
        setNetwork((lines) => [...lines.slice(-499), { id: lines.length + Date.now(), time: Date.now(), level: event.level, source: event.kind, message: event.message }])
      ),
    []
  )

  const mdi = useMdi([
    { id: "1-controls", title: "Controls", content: <Controls />, x: 16, y: 16, width: 560, height: 520 },
    { id: "2-browser", title: "Browser", content: <Browser />, x: 600, y: 16, width: 640, height: 440 },
    { id: "3-network", title: "Network", content: <Network />, x: 600, y: 470, width: 640, height: 220 },
  ])
  // Side by side in whatever room the window has (MdiArea has measured it by now).
  React.useEffect(() => mdi.tile(), []) // eslint-disable-line react-hooks/exhaustive-deps

  async function changeTheme(next: ThemeChoice) {
    setTheme(next)
    if (isWebView4j()) await appearance.theme({ theme: next })
  }

  return (
    <div style={{ display: "grid", gridTemplateRows: "auto auto 1fr auto", height: "100%" }}>
      <TitleBar title="Showcase" />
      <Toolbar aria-label="Showcase">
        <SegmentedControl<Look>
          aria-label="Look"
          value={look}
          onValueChange={onLookChange}
          segments={[
            { value: "auto", label: "This system" },
            { value: "windows", label: "Windows 11" },
            { value: "mac", label: "macOS" },
          ]}
        />
        <SegmentedControl<ThemeChoice>
          aria-label="Theme"
          value={theme}
          onValueChange={(next) => void changeTheme(next)}
          segments={[
            { value: "system", label: "System" },
            { value: "light", label: "Light" },
            { value: "dark", label: "Dark" },
          ]}
        />
        <ToolbarSpacer />
        <ToolbarButton label="Controls" onClick={() => mdi.open({ id: "1-controls", title: "Controls", content: <Controls /> })} />
        <ToolbarButton label="Browser" onClick={() => mdi.open({ id: "2-browser", title: "Browser", content: <Browser /> })} />
        <ToolbarButton label="Network" onClick={() => mdi.open({ id: "3-network", title: "Network", content: <Network /> })} />
        <ToolbarButton label="Cascade" onClick={mdi.cascade} />
        <ToolbarButton label="Tile" onClick={mdi.tile} />
      </Toolbar>
      <NetworkContext.Provider value={network}>
        <MdiArea mdi={mdi} aria-label="Showcase windows" empty="Open a window from the toolbar." />
      </NetworkContext.Provider>
      <StatusBar>
        <StatusItem icon={<StatusDot state={online ? "online" : "offline"} aria-label={online ? "Online" : "Offline"} />}>
          {online ? "Online" : "Offline: this computer has no network"}
        </StatusItem>
        <StatusBarSpacer />
        <StatusItem>{network.length} network events</StatusItem>
      </StatusBar>
    </div>
  )
}

/** Whether this computer is on a network, as the system says: it changes when the cable or Wi-Fi does. */
function useOnline(): boolean {
  const [online, setOnline] = React.useState(navigator.onLine)
  React.useEffect(() => {
    const update = () => setOnline(navigator.onLine)
    window.addEventListener("online", update)
    window.addEventListener("offline", update)
    return () => {
      window.removeEventListener("online", update)
      window.removeEventListener("offline", update)
    }
  }, [])
  return online
}

function Controls() {
  const [name, setName] = React.useState("Ada")
  const [show, setShow] = React.useState<"all" | "unread">("all")
  const [sync, setSync] = React.useState(true)
  return (
    <div style={{ padding: 16, display: "grid", gap: 16 }}>
      <Text variant="title">Controls</Text>
      <Surface title="Buttons">
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <Button variant="accent">Accent</Button>
          <Button>Default</Button>
          <Button variant="subtle">Subtle</Button>
          <Button disabled>Disabled</Button>
          <Spinner label="Working" />
        </div>
      </Surface>
      <Surface title="Input">
        <div style={{ display: "grid", gap: 12 }}>
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} hint="As people see it" />
          <TextField label="Email" defaultValue="ada@" error="That is not an email address" />
          <Select
            defaultValue="ph"
            options={[
              { value: "ph", label: "Philippines" },
              { value: "jp", label: "Japan" },
              { value: "us", label: "United States" },
            ]}
          />
          <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
            <Checkbox label="Remember me" defaultChecked />
            <Switch label="Sync" checked={sync} onChange={(e) => setSync(e.target.checked)} />
          </div>
          <SegmentedControl<"all" | "unread">
            aria-label="Show"
            value={show}
            onValueChange={setShow}
            segments={[
              { value: "all", label: "All" },
              { value: "unread", label: "Unread" },
            ]}
          />
        </div>
      </Surface>
      <Surface title="Progress">
        <div style={{ display: "grid", gap: 8 }}>
          <ProgressBar aria-label="Upload" value={64} />
          <ProgressBar aria-label="Waiting" />
        </div>
      </Surface>
      <Surface title="Content">
        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 8 }}>
          <Avatar name={name || "?"} />
          <Pill color="green">Paid</Pill>
          <Pill color="orange" variant="outline">Pending</Pill>
          <Money amount={49900} currency="PHP" />
          <Money amount={-1250} currency="USD" negative="accounting" />
        </div>
        <List aria-label="People">
          <ListItem title="Ana Reyes" subtitle="Thank you so much!" leading={<Avatar name="Ana Reyes" />} trailing="9:41" />
          <ListItem title="Marco Silva" subtitle="Sent you a tip" leading={<Avatar name="Marco Silva" />} trailing="Mon" />
        </List>
      </Surface>
    </div>
  )
}

/**
 * A website, embedded by Java in this window (view.embed): a native webview over this panel. It follows the
 * panel as the MDI window moves and resizes, and hides while its window is not the active one (a native view draws
 * over everything in the page, including windows in front of it).
 */
function Browser() {
  const [address, setAddress] = React.useState("https://example.com")
  const [opened, setOpened] = React.useState(false)
  const [error, setError] = React.useState<string>()
  const panel = React.useRef<HTMLDivElement>(null)
  const mdiWindow = useMdiWindow()
  const visible = mdiWindow ? mdiWindow.active && mdiWindow.state !== "minimized" : true

  const measure = React.useCallback(() => {
    const r = panel.current!.getBoundingClientRect()
    return { x: r.left, y: r.top, width: r.width, height: r.height, pixelRatio: devicePixelRatio, visible }
  }, [visible])

  async function go(event?: React.FormEvent) {
    event?.preventDefault()
    if (!isWebView4j()) return setError("Open the showcase from the app: embedding needs Java.")
    try {
      await showcase.browse({ url: address, panel: measure() })
      setOpened(true)
      setError(undefined)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  // Keep the site over the panel: checked each frame (moving an MDI window changes no size an observer would see).
  React.useEffect(() => {
    if (!opened) return
    let last = ""
    let frame = 0
    const tick = () => {
      const now = measure()
      const key = JSON.stringify(now)
      if (key !== last) {
        last = key
        void showcase.panel(now)
      }
      frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [opened, measure])

  // The site goes when its window does.
  React.useEffect(() => () => void (isWebView4j() && showcase.close()), [])

  return (
    <div style={{ display: "grid", gridTemplateRows: "auto 1fr", height: "100%" }}>
      <form onSubmit={go} style={{ display: "flex", gap: 8, padding: 8 }}>
        <TextField aria-label="Address" value={address} onChange={(e) => setAddress(e.target.value)} error={error} style={{ flex: 1 }} />
        <Button type="submit" variant="accent">Go</Button>
      </form>
      <div ref={panel} style={{ position: "relative", minHeight: 0 }}>
        {!opened && (
          <div style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center", textAlign: "center", padding: 16 }}>
            <Text tone="secondary">
              Type an address and press Go: Java opens the site inside this window, as an embedded view.
              <br />
              Its requests show in the Network window.
            </Text>
          </div>
        )}
      </div>
    </div>
  )
}

function Network() {
  const lines = React.useContext(NetworkContext)
  return (
    <LogView
      aria-label="The embedded site's network"
      lines={lines}
      empty="Nothing yet: open a site in the Browser window."
      style={{ height: "100%" }}
    />
  )
}

function Root() {
  const [look, setLook] = React.useState<Look>("auto")
  return (
    <NativeProvider platform={look} theme="system" style={{ height: "100%" }}>
      <Showcase look={look} onLookChange={setLook} />
    </NativeProvider>
  )
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <Root />
  </StrictMode>,
)
