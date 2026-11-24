import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { NumberField, PasswordField, TextArea, TokenField } from "./text-inputs"

const people = [
  "Ana Reyes",
  "Marco Silva",
  "Kim Park",
  "Lea Cruz",
  "Tom Lee",
  "Sara Diaz",
  "Ben Ortiz",
]

function Everything({ invalid = false }: { invalid?: boolean }) {
  const [note, setNote] = React.useState(
    "Thanks for the photos! They came out great. Could you send the rest by Friday?\n\nAlso, the ones by the window are my favourite."
  )
  const [price, setPrice] = React.useState<number | null>(499)
  const [tags, setTags] = React.useState(["vip", "monthly"])
  const [to, setTo] = React.useState(["Ana Reyes"])
  return (
    <div style={{ display: "grid", gap: 14, width: "100%", maxWidth: 360 }}>
      <TextArea
        label="Note"
        hint="Grows as you type. Triple-click selects a sentence."
        autoGrow
        minRows={2}
        maxRows={6}
        maxLength={300}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        error={invalid ? "Write at least 10 characters." : undefined}
      />
      <PasswordField
        label="Password"
        hint="Turn on Caps Lock to see the warning."
        defaultValue="correct horse battery"
        error={
          invalid
            ? "That password is too short: use 12 characters or more."
            : undefined
        }
      />
      <NumberField
        label="Price (PHP)"
        value={price}
        onValueChange={setPrice}
        min={0}
        max={10000}
        step={50}
        format={{ style: "currency", currency: "PHP" }}
        hint="↑ ↓ change it by 50, Page Up/Down by 500."
        error={invalid ? "The price can't be more than ₱10,000." : undefined}
      />
      <TokenField
        label="Tags"
        value={tags}
        onValueChange={setTags}
        placeholder="Add a tag"
        hint="Enter or a comma ends a tag; Backspace takes the last one back."
      />
      <TokenField
        label="Send to"
        value={to}
        onValueChange={setTo}
        suggestions={people}
        placeholder="Type a name"
        validate={(name) =>
          people.includes(name)
            ? undefined
            : `${name} isn't one of your subscribers.`
        }
      />
    </div>
  )
}

const meta = {
  title: "Native/TextInputs",
  component: Everything,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Everything>
export default meta
type Story = StoryObj<typeof meta>

export const Filled: Story = {}
export const FilledDark: Story = {
  parameters: { nativeStretch: true, ...dark },
}
export const WithErrors: Story = { args: { invalid: true } }
export const Empty: Story = {
  render: () => (
    <div style={{ display: "grid", gap: 14, width: "100%", maxWidth: 360 }}>
      <TextArea label="Note" placeholder="Write a note" />
      <PasswordField label="Password" placeholder="Password" />
      <NumberField label="Quantity" min={1} placeholder="1" />
      <TokenField label="Tags" placeholder="Add a tag" />
    </div>
  ),
}
export const Disabled: Story = {
  render: () => (
    <div style={{ display: "grid", gap: 14, width: "100%", maxWidth: 360 }}>
      <TextArea label="Note" defaultValue="Can't be changed" disabled />
      <PasswordField label="Password" defaultValue="secret" disabled />
      <NumberField label="Quantity" defaultValue={3} disabled />
      <TokenField label="Tags" defaultValue={["vip"]} disabled />
    </div>
  ),
}
