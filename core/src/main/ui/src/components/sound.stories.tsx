import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { Button } from "./button"
import { type SoundName, useSound } from "./sound"

const NAMES: { name: SoundName; label: string }[] = [
  { name: "error", label: "Error" },
  { name: "warning", label: "Warning" },
  { name: "info", label: "Info (Windows only)" },
  { name: "message", label: "Message in" },
  { name: "sent", label: "Message sent" },
  { name: "notify", label: "Notify" },
  { name: "done", label: "Done" },
  { name: "tap", label: "Tap" },
]

/** Click each to hear it, as this system's kind of sound. The dialogs play theirs when they open. */
function Sounds() {
  const sound = useSound()
  return (
    <>
      {NAMES.map(({ name, label }) => (
        <Button key={name} onClick={() => sound.play(name)}>
          {label}
        </Button>
      ))}
    </>
  )
}

const meta = {
  title: "Native/Sound",
  component: Sounds,
  decorators: [bothPlatforms],
} satisfies Meta<typeof Sounds>
export default meta
type Story = StoryObj<typeof meta>

export const Listen: Story = {}
export const ListenDark: Story = { parameters: dark }
