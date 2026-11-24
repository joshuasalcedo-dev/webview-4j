import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import { Markdown } from "./markdown"
import { NativeProvider } from "./provider"

const TEXT = `# Release notes

Version **0.2.0** brings *layouts*, ~~old~~ new state, and the web.

- Layout panes named as JavaFX's: \`HBox\`, \`GridPane\`, \`SplitPane\`
- State that is kept on disk
  - per session
  - per browser on the web
- [x] Tested in a real window
- [ ] Shipped

| Part | Where |
| --- | --- |
| Layouts | \`ui.components.layout\` |
| State | \`ui.state\` |

\`\`\`java
WebHost.of(Office::new).port(8080).start();
\`\`\`

> Links open in the browser: https://github.com/joshuasalcedo-dev/webview-4j

<script>alert("never run")</script> [and this is dropped](javascript:alert(1))
`

function Story({ platform, theme }: { platform: "windows" | "mac"; theme: "light" | "dark" }) {
  const [clicked, setClicked] = React.useState("")
  return (
    <NativeProvider platform={platform} theme={theme} style={{ padding: 24, maxWidth: 720 }}>
      <Markdown onLink={setClicked}>{TEXT}</Markdown>
      {clicked && <p>Asked to open {clicked}</p>}
    </NativeProvider>
  )
}

const meta = { title: "Native/Markdown", component: Story, args: { platform: "windows", theme: "light" } } satisfies Meta<typeof Story>
export default meta
type S = StoryObj<typeof meta>

export const Windows: S = {}
export const Mac: S = { args: { platform: "mac" } }
export const Dark: S = { args: { theme: "dark" } }
