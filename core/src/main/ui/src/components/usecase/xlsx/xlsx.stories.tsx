import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../../../stories/side-by-side"
import { Text } from "../../text"
import { CsvViewer } from "./csv-viewer"
import { XlsxEditor, XlsxViewer } from "./xlsx-viewer"

// The sample files, served by Vite next to the story.
const WORKBOOK = new URL("./sample/quarterly.xlsx", import.meta.url).href
const CSV = new URL("./sample/customers.csv", import.meta.url).href

/** Excel workbooks and CSV files: the viewer, the editor (Save gives the bytes), the CSV table. */
const meta = {
  title: "Native/Use cases/Excel and CSV",
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta

export default meta
type Story = StoryObj<typeof meta>

export const Viewer: Story = {
  render: () => <XlsxViewer src={WORKBOOK} fileName="quarterly.xlsx" height={460} />,
}

export const ViewerDark: Story = { ...Viewer, parameters: dark }

function EditorExample() {
  const [saved, setSaved] = React.useState<string>()
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <XlsxEditor
        src={WORKBOOK}
        fileName="quarterly.xlsx"
        height={480}
        onSave={(bytes, name) => setSaved(`${name}: ${bytes.byteLength.toLocaleString()} bytes saved`)}
      />
      <Text variant="caption" tone="secondary">
        {saved ?? "Edit a cell, then Save (or Ctrl+S / ⌘S)."}
      </Text>
    </div>
  )
}

export const Editor: Story = { render: () => <EditorExample /> }

export const EditorDark: Story = { ...Editor, parameters: dark }

export const EditorReadOnly: Story = {
  render: () => <XlsxEditor src={WORKBOOK} fileName="quarterly.xlsx" height={420} readOnly />,
}

export const WithoutToolbar: Story = {
  render: () => <XlsxViewer src={WORKBOOK} fileName="quarterly.xlsx" height={320} showToolbar={false} />,
}

export const Csv: Story = {
  render: () => <CsvViewer src={CSV} fileName="customers.csv" height={420} />,
}

export const CsvDark: Story = { ...Csv, parameters: dark }

export const CsvFromText: Story = {
  render: () => (
    <CsvViewer
      fileName="scores.tsv"
      height={220}
      data={"name\tscore\tnote\nAna\t92\tfirst\nMarco\t87\t\"quoted, with a comma\"\nJules\t78\t\n"}
    />
  ),
}

export const NoFile: Story = {
  render: () => <XlsxViewer height={200} />,
}
