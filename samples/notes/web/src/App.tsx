import * as React from "react"
import { Button } from "@webview4j/ui/components/button"
import { Content } from "@webview4j/ui/components/content"
import { useDialogs } from "@webview4j/ui/components/dialog"
import { List, ListItem } from "@webview4j/ui/components/list"
import { SegmentedControl } from "@webview4j/ui/components/segmented-control"
import { StatusBar, StatusBarSpacer, StatusDot, StatusItem } from "@webview4j/ui/components/status-bar"
import { Text } from "@webview4j/ui/components/text"
import { TextField } from "@webview4j/ui/components/text-field"
import { TitleBar } from "@webview4j/ui/components/title-bar"
import { isWebView4j } from "@webview4j/api"
// Written by the annotation processor from NotesController.java when Java compiles.
import { appearance, notes, showcase, type Note } from "./api.generated"
import type { Look } from "./main"

type ThemeChoice = "system" | "light" | "dark"

/**
 * The app's one window. The TitleBar is the window's own (Java opens it with decorations(false)): dragging it moves
 * the window, double-clicking maximizes it, and its buttons minimize, maximize and close it, with no code here.
 */
export function App({ look, onLookChange }: { look: Look; onLookChange: (look: Look) => void }) {
  const dialogs = useDialogs()
  const [list, setList] = React.useState<Note[]>([])
  const [text, setText] = React.useState("")
  const [error, setError] = React.useState<string>()
  const [theme, setTheme] = React.useState<ThemeChoice>("system")

  // Java sets the window's theme; the page's prefers-color-scheme follows, and so does every control.
  async function changeTheme(next: ThemeChoice) {
    setTheme(next)
    if (isWebView4j()) await appearance.theme({ theme: next })
  }

  const refresh = React.useCallback(() => notes.list().then(setList, (e: Error) => setError(e.message)), [])
  React.useEffect(() => void refresh(), [refresh])

  async function add(event: React.FormEvent) {
    event.preventDefault()
    try {
      await notes.create({ text })
      setText("")
      setError(undefined)
      await refresh()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  async function remove(note: Note) {
    if (await dialogs.confirm({ title: "Delete this note?", message: note.text, confirm: "Delete", destructive: true })) {
      await notes.delete(note.id)
      await refresh()
    }
  }

  return (
    <div style={{ display: "grid", gridTemplateRows: "auto 1fr auto", height: "100%" }}>
      <TitleBar title="Notes" />
      <Content style={{ padding: 24, overflow: "auto" }}>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 20 }}>
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
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 20 }}>
          <Button onClick={() => void showcase.gallery()} disabled={!isWebView4j()}>
            Component gallery (written only in Java)
          </Button>
          <Button onClick={() => void showcase.open()} disabled={!isWebView4j()}>
            MDI workspace: an embedded website and its network
          </Button>
        </div>
        <Text variant="title">Notes</Text>
        <Text tone="secondary">Kept by Java (NotesController), shown with @webview4j/ui.</Text>
        <form onSubmit={add} style={{ display: "flex", gap: 8, alignItems: "flex-end", margin: "16px 0" }}>
          <TextField
            label="New note"
            value={text}
            onChange={(e) => setText(e.target.value)}
            error={error}
            style={{ flex: 1 }}
          />
          <Button type="submit" variant="accent" disabled={!text.trim()}>
            Add
          </Button>
        </form>
        <List aria-label="Notes">
          {list.map((note) => (
            <ListItem
              key={note.id}
              title={note.text}
              subtitle={new Date(note.created).toLocaleString()}
              menu={[{ label: "Delete", onSelect: () => void remove(note) }]}
            />
          ))}
        </List>
      </Content>
      <StatusBar>
        <StatusItem icon={<StatusDot state={isWebView4j() ? "online" : "offline"} aria-label="Java" />}>
          {isWebView4j() ? "Connected to Java" : "Not in a webview-4j window"}
        </StatusItem>
        <StatusBarSpacer />
        <StatusItem>{list.length === 1 ? "1 note" : `${list.length} notes`}</StatusItem>
      </StatusBar>
    </div>
  )
}
