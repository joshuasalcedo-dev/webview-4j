import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { Avatar } from "../components/avatar"
import { Button } from "../components/button"
import { Checkbox } from "../components/checkbox"
import { List, ListItem } from "../components/list"
import { ProgressBar } from "../components/progress-bar"
import { NativeProvider, useBusy } from "../components/provider"
import { SegmentedControl } from "../components/segmented-control"
import { Select } from "../components/select"
import { Surface } from "../components/surface"
import { Switch } from "../components/switch"
import { TextField } from "../components/text-field"
import { TitleBar } from "../components/title-bar"
import type { Platform, Theme } from "../lib/platform"

const chats = [
  { id: "1", name: "Ana Reyes", last: "Thank you so much!! 💕", time: "9:41" },
  { id: "2", name: "Marco Silva", last: "Sent you a tip", time: "9:12" },
  { id: "3", name: "Jules", last: "When is the next live?", time: "Yesterday" },
  { id: "4", name: "Kim Park", last: "Typing…", time: "Mon" },
]

/** Every control in one window, the way a chat app would use them. */
function ChatWindow({
  platform,
  theme,
}: {
  platform: Platform | "auto"
  theme: Theme | "system"
}) {
  return (
    <NativeProvider
      platform={platform}
      theme={theme}
      style={{
        display: "grid",
        gridTemplateRows: "auto 1fr",
        height: 520,
        overflow: "hidden",
        borderRadius: 10,
        boxShadow: "0 0 0 1px rgba(0,0,0,0.12), 0 12px 32px rgba(0,0,0,0.18)",
      }}
    >
      <ChatWindowContent />
    </NativeProvider>
  )
}

/** Inside the provider, so useBusy() reaches it. */
function ChatWindowContent() {
  const busy = useBusy()
  const [selected, setSelected] = React.useState("1")
  const [show, setShow] = React.useState("all")
  const [draft, setDraft] = React.useState("")
  const chat = chats.find((c) => c.id === selected)
  return (
    <>
      <TitleBar title="Subscribers Chat">
        <Button variant="subtle">New chat</Button>
      </TitleBar>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "250px 1fr",
          minHeight: 0,
        }}
      >
        <aside
          style={{
            display: "grid",
            gridTemplateRows: "auto 1fr",
            minHeight: 0,
            borderRight: "1px solid rgba(127,127,127,0.18)",
          }}
        >
          <div style={{ padding: "8px 12px" }}>
            <SegmentedControl
              aria-label="Show"
              value={show}
              onValueChange={setShow}
              segments={[
                { value: "all", label: "All" },
                { value: "unread", label: "Unread" },
                { value: "tipped", label: "Tipped" },
              ]}
            />
          </div>
          <List aria-label="Chats" style={{ overflow: "auto" }}>
            {chats.map((c) => (
              <ListItem
                key={c.id}
                title={c.name}
                subtitle={c.last}
                trailing={c.time}
                leading={<Avatar name={c.name} />}
                selected={selected === c.id}
                onSelect={() => setSelected(c.id)}
                menu={[
                  { label: "Pin", shortcut: "Mod+P" },
                  { label: "Mark as unread", shortcut: "Mod+U" },
                  { label: "Mute notifications", checked: false },
                  "separator",
                  { label: "Archive" },
                  { label: "Delete chat…", shortcut: "Delete" },
                ]}
              />
            ))}
          </List>
        </aside>
        <main
          style={{
            display: "grid",
            gridTemplateRows: "1fr auto",
            gap: 12,
            padding: 16,
            minHeight: 0,
            overflow: "auto",
          }}
        >
          <div style={{ display: "grid", alignContent: "start", gap: 12 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <Avatar name={chat?.name ?? ""} size={40} />
              <div>
                <div style={{ fontWeight: 600 }}>{chat?.name}</div>
                <div className="nt-secondary">Subscribed since March</div>
              </div>
            </div>
            <Surface title="This chat" style={{ display: "grid", gap: 10 }}>
              <Switch label="Notify me" defaultChecked />
              <Checkbox label="Pin to the top" />
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <span>Label</span>
                <Select
                  aria-label="Label"
                  defaultValue="fan"
                  options={[
                    { value: "fan", label: "Fan" },
                    { value: "vip", label: "VIP" },
                    { value: "new", label: "New" },
                  ]}
                />
              </div>
              <div style={{ display: "grid", gap: 6 }}>
                <span className="nt-secondary">Sending the video… 60%</span>
                <ProgressBar aria-label="Sending the video" value={60} />
              </div>
            </Surface>
          </div>
          <form
            style={{ display: "flex", alignItems: "end", gap: 8 }}
            onSubmit={async (event) => {
              event.preventDefault()
              // The pointer shows the app is sending, and still answers, until it is sent.
              await busy(new Promise((sent) => setTimeout(sent, 1500)))
              setDraft("")
            }}
          >
            <TextField
              aria-label="Message"
              placeholder={`Message ${chat?.name ?? ""}`}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              style={{ flex: 1 }}
            />
            <Button type="button">Attach</Button>
            <Button type="submit" variant="accent" disabled={!draft.trim()}>
              Send
            </Button>
          </form>
        </main>
      </div>
    </>
  )
}

function Both({ theme }: { theme: Theme }) {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(620px, 1fr))",
        gap: 24,
      }}
    >
      {(["windows", "mac"] as const).map((platform) => (
        <div key={platform}>
          <div
            style={{
              marginBottom: 8,
              font: "600 11px system-ui, sans-serif",
              letterSpacing: 0.4,
              textTransform: "uppercase",
              color: "#8a8a8a",
            }}
          >
            {platform === "windows" ? "Windows 11" : "macOS"} · {theme}
          </div>
          <ChatWindow platform={platform} theme={theme} />
        </div>
      ))}
    </div>
  )
}

const meta = {
  title: "Native/Gallery",
  component: Both,
  parameters: { layout: "padded" },
} satisfies Meta<typeof Both>
export default meta
type Story = StoryObj<typeof meta>

/** A chat window from these controls only, as Windows 11 and as macOS draw it. Everything in it works. */
export const Light: Story = { args: { theme: "light" } }
export const Dark: Story = { args: { theme: "dark" } }
/** As the app will be: the look of the system you are on, light or dark as it is set. */
export const ThisSystem: Story = {
  args: { theme: "light" },
  render: () => <ChatWindow platform="auto" theme="system" />,
}
