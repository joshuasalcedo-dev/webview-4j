import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { NativeProvider } from "../../provider"
import { DataGrid } from "./data-grid"
import { DataList } from "./data-list"
import { DataMap } from "./data-map"
import type { DataRow } from "./data-table"
import { DataTree } from "./data-tree"

const PEOPLE: DataRow[] = ["Ana Reyes", "Marco Silva", "Jules", "Kim Park", "Grace Hopper", "Ada Lovelace"].map((name, i) => ({
  id: String(i + 1),
  name,
  email: `${name.split(" ")[0].toLowerCase()}@example.com`,
  role: ["Admin", "Chatter", "Assistant"][i % 3],
  seen: ["9:41", "Yesterday", "Mon"][i % 3],
}))

const ORG: DataRow[] = [
  {
    id: "eng",
    name: "Engineering",
    people: 12,
    budget: 1200000,
    children: [
      { id: "web", name: "Web", people: 5, budget: 400000 },
      { id: "desktop", name: "Desktop", people: 4, budget: 380000, children: [{ id: "java", name: "Java", people: 2, budget: 150000 }] },
      { id: "infra", name: "Infrastructure", people: 3, budget: 420000, hasChildren: true },
    ],
  },
  { id: "sales", name: "Sales", people: 8, budget: 600000, children: [{ id: "ph", name: "Philippines", people: 5, budget: 300000 }] },
]

function Collections({ platform, theme }: { platform: "windows" | "mac"; theme: "light" | "dark" }) {
  const [settings, setSettings] = React.useState<Record<string, unknown>>({
    name: "Office",
    port: 8080,
    debug: false,
    owner: null,
    tags: ["desktop", "java"],
    database: { url: "jdbc:postgresql://localhost/office", pool: { size: 10, timeoutSeconds: 30 } },
  })
  const [tree, setTree] = React.useState(ORG)
  return (
    <NativeProvider platform={platform} theme={theme} style={{ padding: 24, display: "grid", gap: 24 }}>
      <h3>DataList</h3>
      <DataList aria-label="People" items={PEOPLE} title="name" subtitle="email" avatar="name" pill="role" trailing="seen" search multiple
        itemMenu={(p) => [{ label: `Message ${p.name}` }]} style={{ maxHeight: 320 }} />
      <h3>DataTree</h3>
      <DataTree aria-label="Teams" selectable
        columns={[{ key: "name", header: "Team" }, { key: "people", header: "People", type: "number" }, { key: "budget", header: "Budget", type: "money", currency: "PHP" }]}
        rows={tree}
        onExpand={(id) => setTimeout(() => setTree((all) => all.map((t) => ({ ...t, children: (t.children as DataRow[] | undefined)?.map((c) => (c.id === id ? { ...c, children: [{ id: "k8s", name: "Kubernetes", people: 1, budget: 90000 }] } : c)) }))), 600)} />
      <h3>DataMap</h3>
      <DataMap aria-label="Settings" value={settings} depth={2}
        onEdit={(path, value) => setSettings((s) => ({ ...s, [path.split(".")[0]]: path.includes(".") ? s[path.split(".")[0]] : value }))}
        onAdd={(key, value) => setSettings((s) => ({ ...s, [key]: value }))}
        onRemove={(path) => setSettings((s) => Object.fromEntries(Object.entries(s).filter(([k]) => k !== path)))} />
      <h3>DataGrid</h3>
      <DataGrid aria-label="People" items={PEOPLE} title="name" subtitle="email" pill="role" footer="seen" multiple />
    </NativeProvider>
  )
}

const meta = { title: "Native/Use cases/Collections", component: Collections, args: { platform: "windows", theme: "light" } } satisfies Meta<typeof Collections>
export default meta
type Story = StoryObj<typeof meta>

export const Windows: Story = {}
export const Mac: Story = { args: { platform: "mac" } }
export const Dark: Story = { args: { theme: "dark" } }
