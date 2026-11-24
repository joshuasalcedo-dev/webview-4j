import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import type { FileNode } from "../lib/files"
import { bothPlatforms, dark } from "../stories/side-by-side"
import { useDialogs } from "./dialog"
import type { FileActions } from "./file-menu"
import { FileExplorer, FileGrid, FileList } from "./file-views"
import { FileChip, FileIcon, FileTree } from "./files"
import { useSound } from "./sound"

const day = (daysAgo: number, hour = 10) => {
  const d = new Date()
  d.setDate(d.getDate() - daysAgo)
  d.setHours(hour, 24, 0, 0)
  return d
}

const project: FileNode[] = [
  {
    name: "src",
    modified: day(0),
    children: [
      {
        name: "components",
        modified: day(0),
        children: [
          { name: "chat-list.tsx", size: 4_210, modified: day(0, 9) },
          { name: "chat-list.stories.tsx", size: 1_830, modified: day(1) },
          { name: "message.tsx", size: 6_904, modified: day(3) },
        ],
      },
      {
        name: "lib",
        modified: day(2),
        children: [
          { name: "api.ts", size: 12_400, modified: day(2) },
          { name: "format.test.ts", size: 2_100, modified: day(5) },
        ],
      },
      {
        name: "assets",
        modified: day(9),
        children: [
          { name: "logo.svg", size: 3_300, modified: day(9) },
          { name: "hero.png", size: 482_000, modified: day(9) },
        ],
      },
      { name: "main.tsx", size: 940, modified: day(1) },
      { name: "styles.css", size: 7_700, modified: day(4) },
    ],
  },
  {
    name: "src-tauri",
    modified: day(6),
    children: [
      { name: "Cargo.toml", size: 1_200, modified: day(6) },
      { name: "tauri.conf.json", size: 2_800, modified: day(6) },
      { name: "build.rs", size: 120, modified: day(30) },
    ],
  },
  {
    name: "docs",
    modified: day(12),
    children: [
      { name: "October report.pdf", size: 2_483_200, modified: day(12) },
      { name: "brand.pptx", size: 8_400_000, modified: day(40) },
      { name: "budget.xlsx", size: 96_000, modified: day(15) },
    ],
  },
  { name: "public", modified: day(60), children: [] },
  { name: "package.json", size: 1_640, modified: day(1) },
  { name: "tsconfig.json", size: 512, modified: day(20) },
  { name: "vite.config.ts", size: 780, modified: day(20) },
  { name: "Dockerfile", size: 410, modified: day(33) },
  { name: "README.md", size: 5_100, modified: day(2) },
  { name: ".gitignore", size: 90, modified: day(90) },
]

// The stories change their own copy of the files, so Rename, Delete and New folder can be tried.
function change(
  items: FileNode[],
  path: string,
  edit: (siblings: FileNode[], index: number) => FileNode[]
): FileNode[] {
  const parts = path.split("/")
  const name = parts.pop() ?? ""
  const walk = (nodes: FileNode[], depth: number): FileNode[] => {
    if (depth === parts.length)
      return edit(
        nodes,
        nodes.findIndex((n) => n.name === name)
      )
    return nodes.map((n) =>
      n.name === parts[depth]
        ? { ...n, children: walk(n.children ?? [], depth + 1) }
        : n
    )
  }
  return walk(items, 0)
}

const INVALID = /[\\/:*?"<>|]/

/** Actions that change the files, the dialogs asking first; what happened shows underneath. */
function useLiveFiles() {
  const dialogs = useDialogs()
  const sound = useSound()
  const [items, setItems] = React.useState(project)
  const [last, setLast] = React.useState(
    "Right-click anything (or press F2, Delete)."
  )
  const actions: FileActions = {
    onOpen: (path) => setLast(`Opened ${path}`),
    onRename: async (path, node) => {
      const name = await dialogs.prompt({
        title: `Rename ${node.name}`,
        label: "New name",
        value: node.name,
        confirm: "Rename",
        validate: (v) =>
          v.trim() === ""
            ? "Type a name."
            : INVALID.test(v)
              ? "A name can't have \\ / : * ? \" < > |."
              : undefined,
      })
      if (name && name !== node.name) {
        setItems((now) =>
          change(now, path, (s, i) =>
            s.map((n, j) => (j === i ? { ...n, name } : n))
          )
        )
        setLast(`Renamed ${path} to ${name}`)
      }
    },
    onDelete: async (path, node) => {
      const sure = await dialogs.confirm({
        title: `Delete “${node.name}”?`,
        message: node.children
          ? "The folder and everything in it are deleted."
          : "It's deleted for good.",
        kind: "warning",
        confirm: "Delete",
        destructive: true,
      })
      if (sure) {
        setItems((now) =>
          change(now, path, (s, i) => s.filter((_, j) => j !== i))
        )
        sound.play("tap")
        setLast(`Deleted ${path}`)
      }
    },
    onDeleteMany: async (chosen) => {
      const sure = await dialogs.confirm({
        title: `Delete these ${chosen.length} items?`,
        message: chosen.map((c) => c.node.name).join(", "),
        kind: "warning",
        confirm: "Delete",
        destructive: true,
      })
      if (!sure) return
      setItems((now) =>
        chosen.reduce(
          (left, c) =>
            change(left, c.path, (s, i) => s.filter((_, j) => j !== i)),
          now
        )
      )
      sound.play("tap")
      setLast(`Deleted ${chosen.length} items`)
    },
    onNewFolder: async (folder) => {
      const name = await dialogs.prompt({
        title: "New folder",
        label: "Name",
        value: "New folder",
        confirm: "Create",
      })
      if (!name) return
      const add = (nodes: FileNode[]) => [
        ...nodes,
        { name, children: [], modified: new Date() },
      ]
      setItems((now) =>
        folder === ""
          ? add(now)
          : change(now, folder, (s, i) =>
              s.map((n, j) =>
                j === i ? { ...n, children: add(n.children ?? []) } : n
              )
            )
      )
      setLast(`Made ${folder ? `${folder}/` : ""}${name}`)
    },
    onNewFile: (folder) => setLast(`New file in ${folder || "the top"}`),
    onReveal: (path) => setLast(`Showed ${path} in the system's file manager`),
    onProperties: (path) => setLast(`Properties of ${path}`),
    menu: () => [{ label: "Share…", onSelect: () => setLast("Share") }],
  }
  return { items, actions, last }
}

function Explorer() {
  const { items, actions, last } = useLiveFiles()
  return (
    <div style={{ display: "grid", gap: 8, width: "100%" }}>
      <FileExplorer
        items={items}
        rootName="chat-app"
        defaultFolder="src"
        style={{ height: 420 }}
        {...actions}
      />
      <span className="nt-secondary">{last}</span>
    </div>
  )
}

function Tree() {
  const { items, actions, last } = useLiveFiles()
  const [selected, setSelected] = React.useState<string[]>(["src/main.tsx"])
  const [find, setFind] = React.useState("")
  return (
    <div style={{ display: "grid", gap: 8, width: "100%" }}>
      <input
        className="nt-input"
        aria-label="Find"
        placeholder="Find (try “tsx” or “chat list”)"
        value={find}
        onChange={(e) => setFind(e.target.value)}
        style={{ maxWidth: 260 }}
      />
      <FileTree
        aria-label="Files"
        items={items}
        selected={selected}
        onSelectionChange={setSelected}
        defaultExpanded={["src", "src/components"]}
        find={find}
        style={{ height: 340 }}
        {...actions}
      />
      <span className="nt-secondary">{last}</span>
    </div>
  )
}

function View({ kind }: { kind: "grid" | "list" }) {
  const { items, actions, last } = useLiveFiles()
  const [folder, setFolder] = React.useState("src")
  const [selected, setSelected] = React.useState<string[]>([])
  const contents =
    folder === ""
      ? items
      : folder
          .split("/")
          .reduce<FileNode[]>(
            (nodes, part) => nodes.find((n) => n.name === part)?.children ?? [],
            items
          )
  const Shown = kind === "grid" ? FileGrid : FileList
  return (
    <div style={{ display: "grid", gap: 8, width: "100%" }}>
      <span className="nt-secondary">
        In: {folder || "the top"}{" "}
        {folder && (
          <button
            type="button"
            className="nt-button"
            onClick={() => setFolder("")}
          >
            Back to the top
          </button>
        )}
      </span>
      <Shown
        aria-label="Files"
        items={contents}
        folder={folder}
        selected={selected}
        onSelectionChange={setSelected}
        onOpenFolder={(path) => setFolder(path)}
        style={{ height: 300 }}
        {...actions}
      />
      <span className="nt-secondary">{last}</span>
    </div>
  )
}

const kinds = [
  "report.pdf",
  "photo.jpg",
  "clip.mp4",
  "voice-note.m4a",
  "notes.docx",
  "budget.xlsx",
  "slides.pptx",
  "archive.zip",
  "data.csv",
  "page.html",
  "script.py",
  "Main.java",
  "app.tsx",
  "lib.rs",
  "Dockerfile",
  "package.json",
  "unknown.xyz",
]

function Icons() {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(130px, 1fr))",
        gap: 10,
        width: "100%",
      }}
    >
      {kinds.map((name) => (
        <span
          key={name}
          style={{ display: "flex", alignItems: "center", gap: 8 }}
        >
          <FileIcon name={name} size={20} />
          {name}
        </span>
      ))}
      {["src", "components", "node_modules", ".git", "images", "docs"].map(
        (name) => (
          <span
            key={name}
            style={{ display: "flex", alignItems: "center", gap: 8 }}
          >
            <FileIcon name={name} folder size={20} />
            {name}/
          </span>
        )
      )}
    </div>
  )
}

function Chips() {
  const [files, setFiles] = React.useState([
    { name: "October-subscriber-report-final-version.pdf", size: 2_483_200 },
    { name: "beach.jpg", size: 845_000 },
    { name: "voice-note.m4a", size: 312_400 },
  ])
  const [last, setLast] = React.useState("Right-click a chip.")
  return (
    <div style={{ display: "grid", gap: 14, width: "100%" }}>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
        {files.map((f) => (
          <FileChip
            key={f.name}
            name={f.name}
            size={f.size}
            onOpen={() => setLast(`Opened ${f.name}`)}
            onDownload={() => setLast(`Saved ${f.name}`)}
            onRemove={() => setFiles(files.filter((x) => x !== f))}
          />
        ))}
        <FileChip
          name="clip.mp4"
          status="Uploading… 40%"
          progress={40}
          onRemove={() => {}}
        />
        <FileChip
          name="huge-video.mov"
          status="Too large to send: the limit is 100 MB."
          error
        />
      </div>
      <p className="nt-selectable" style={{ margin: 0, lineHeight: "26px" }}>
        Ana sent{" "}
        <FileChip
          variant="inline"
          name="October report.pdf"
          onOpen={() => setLast("Opened October report.pdf")}
        />{" "}
        and{" "}
        <FileChip
          variant="inline"
          name="beach.jpg"
          onOpen={() => setLast("Opened beach.jpg")}
        />
        ; the build uses{" "}
        <FileChip
          variant="inline"
          name="vite.config.ts"
          path="chat-app/vite.config.ts"
        />{" "}
        and{" "}
        <FileChip
          variant="inline"
          name="Dockerfile"
          onRemove={() => setLast("Removed Dockerfile")}
        />
        .
      </p>
      <span className="nt-secondary">{last}</span>
    </div>
  )
}

const meta = {
  title: "Native/Files",
  component: Explorer,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Explorer>
export default meta
type Story = StoryObj<typeof meta>

const darkToo = { parameters: { nativeStretch: true, ...dark } }

export const ExplorerView: Story = {}
export const ExplorerViewDark: Story = darkToo
export const TreeWithFind: Story = { render: () => <Tree /> }
export const TreeWithFindDark: Story = { render: () => <Tree />, ...darkToo }
export const Grid: Story = { render: () => <View kind="grid" /> }
export const GridDark: Story = {
  render: () => <View kind="grid" />,
  ...darkToo,
}
export const List: Story = { render: () => <View kind="list" /> }
export const ListDark: Story = {
  render: () => <View kind="list" />,
  ...darkToo,
}
export const EmptyFolder: Story = {
  render: () => (
    <FileTree
      aria-label="Files"
      items={[{ name: "public", children: [] }]}
      defaultExpanded={["public"]}
    />
  ),
}
export const Chip: Story = { render: () => <Chips /> }
export const ChipDark: Story = { render: () => <Chips />, ...darkToo }
export const IconsByKind: Story = { render: () => <Icons /> }
export const IconsByKindDark: Story = { render: () => <Icons />, ...darkToo }
