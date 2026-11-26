import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../../../../stories/side-by-side"
import { DocumentViewerSidebar, DocumentViewerSidebarList } from "./document-viewer-sidebar"
import { DocxAnnotationCard } from "./docx-annotation-card"
import { DocxEditor } from "./docx-editor"
import { DocxLayoutBlocks } from "./docx-layout-blocks"
import { DocxViewer } from "./docx-viewer"
import sample from "./sample/sample.docx?url"

/** Every Word component: the viewer, the editor, their side panel, the comment cards, the layout blocks. */
function Example({ show = "viewer" as "viewer" | "editor" | "readOnly" | "empty" | "outline" | "cards" | "sidebar" | "blocks" }) {
  const [saved, setSaved] = React.useState<string>()
  const [page, setPage] = React.useState("2")
  switch (show) {
    case "viewer":
      return <DocxViewer src={sample} fileName="Quarterly report.docx" height={620} style={{ width: "100%" }} />
    case "outline":
      return <DocxViewer src={sample} fileName="Quarterly report.docx" defaultPanel="outline" defaultZoom="fit-page" height={620} style={{ width: "100%" }} />
    case "empty":
      return <DocxViewer height={360} style={{ width: "100%" }} />
    case "editor":
    case "readOnly":
      return (
        <div style={{ display: "grid", gap: 8, width: "100%" }}>
          <DocxEditor
            src={sample}
            fileName="Quarterly report.docx"
            readOnly={show === "readOnly"}
            height={620}
            onSave={(bytes, name) => setSaved(`${name}: ${bytes.length.toLocaleString()} bytes`)}
          />
          <span style={{ color: "var(--nt-text-secondary)" }}>{saved ? `Saved ${saved}` : "Ctrl+S (⌘S) saves: the bytes come to onSave."}</span>
        </div>
      )
    case "cards":
      return (
        <div style={{ display: "grid", gap: 8, width: 260 }}>
          <DocxAnnotationCard kind="comment" author="Jules Park" date="Nov 22, 2026, 10:00 AM" anchorText="Revenue grew by 12% over the quarter" text="Is this against the same quarter last year?" />
          <DocxAnnotationCard kind="reply" author="Ana Reyes" text="Yes, Q3 2025." onClick={() => {}} />
          <DocxAnnotationCard kind="insertion" author="Ana Reyes" date="Nov 20, 2026" text="Support tickets fell by a third." />
          <DocxAnnotationCard kind="deletion" author="Marco Silva" text="Costs were flat." active />
          <DocxAnnotationCard kind="resolved" author="Kim" text="Fixed the chart." />
        </div>
      )
    case "sidebar":
      return (
        <div style={{ display: "flex", height: 320, width: "100%", border: "1px solid var(--nt-separator)", borderRadius: 8, overflow: "hidden" }}>
          <DocumentViewerSidebar aria-label="Outline" open title="Outline" width={220}>
            <div className="nt-docx-sidebar-scroll">
              <DocumentViewerSidebarList
                active={page}
                onSelect={setPage}
                entries={[
                  { id: "1", label: "Quarterly Report" },
                  { id: "2", label: "1. Summary", level: 1 },
                  { id: "3", label: "1.1 Highlights", level: 2 },
                  { id: "4", label: "2. Numbers", level: 1, badge: 3 },
                  { id: "5", label: "Appendix", description: "Landscape", level: 1 },
                ]}
              />
            </div>
          </DocumentViewerSidebar>
          <div style={{ flex: 1, padding: 16, color: "var(--nt-text-secondary)" }}>Chosen: {page}</div>
        </div>
      )
    case "blocks":
      return <DocxLayoutBlocks src={sample} height={460} style={{ width: "100%" }} />
  }
}

const meta = {
  title: "Use case/Documents/Word",
  component: Example,
  decorators: bothPlatforms,
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Example>

export default meta
type Story = StoryObj<typeof meta>

export const Viewer: Story = {}
export const ViewerDark: Story = { parameters: dark }
export const ViewerWithOutline: Story = { args: { show: "outline" } }
export const ViewerEmpty: Story = { args: { show: "empty" } }
export const Editor: Story = { args: { show: "editor" } }
export const EditorDark: Story = { args: { show: "editor" }, parameters: dark }
export const EditorReadOnly: Story = { args: { show: "readOnly" } }
export const AnnotationCards: Story = { args: { show: "cards" } }
export const AnnotationCardsDark: Story = { args: { show: "cards" }, parameters: dark }
export const Sidebar: Story = { args: { show: "sidebar" } }
export const LayoutBlocks: Story = { args: { show: "blocks" } }
export const LayoutBlocksDark: Story = { args: { show: "blocks" }, parameters: dark }
