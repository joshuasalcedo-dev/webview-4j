import type { Meta, StoryObj } from "@storybook/react-vite"

import { bothPlatforms, dark } from "../stories/side-by-side"
import { ProgressBar } from "./progress-bar"

const meta = {
  title: "Native/ProgressBar",
  component: ProgressBar,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
  args: { "aria-label": "Sending" },
} satisfies Meta<typeof ProgressBar>
export default meta
type Story = StoryObj<typeof meta>

const Values = () => (
  <>
    <ProgressBar aria-label="Not started" value={0} />
    <ProgressBar aria-label="Sending" value={40} />
    <ProgressBar aria-label="Sent" value={100} />
  </>
)

export const Known: Story = { render: Values }
export const KnownDark: Story = {
  render: Values,
  parameters: { nativeStretch: true, ...dark },
}
/** How long it takes is not known: it runs back and forth. */
export const Indeterminate: Story = {}
