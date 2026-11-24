import type { Meta, StoryObj } from "@storybook/react-vite"
import * as React from "react"

import type { FileNode } from "../lib/files"
import { bothPlatforms, dark } from "../stories/side-by-side"
import { CodeBlock, CodeEditor } from "./code-editor"
import { FileTree } from "./files"
import { SegmentedControl } from "./segmented-control"
import { StatusBar, StatusBarSpacer, StatusItem } from "./status-bar"

const SAMPLES: Record<string, string> = {
  "chat-list.tsx": `import * as React from "react"

type Chat = { id: string; name: string; unread: number }

/** The chats down the side, newest first. */
export function ChatList({ chats }: { chats: Chat[] }) {
  const [open, setOpen] = React.useState<string>()
  return (
    <ul>
      {chats.map((chat) => (
        <li key={chat.id} onClick={() => setOpen(chat.id)}>
          {chat.name} {chat.unread > 0 && <b>{chat.unread}</b>}
        </li>
      ))}
    </ul>
  )
}
`,
  "main.rs": `use std::collections::HashMap;

/// Counts how often each word appears.
fn count_words(text: &str) -> HashMap<String, usize> {
    let mut counts = HashMap::new();
    for word in text.split_whitespace() {
        *counts.entry(word.to_lowercase()).or_insert(0) += 1;
    }
    counts
}

fn main() {
    let counts = count_words("the cat and the hat");
    println!("{:?}", counts.get("the")); // Some(2)
}
`,
  "ChatService.java": `package example.chat;

import java.util.List;

/** Sends and lists a chat's messages. */
public final class ChatService {
    private final MessageRepository messages;

    public ChatService(MessageRepository messages) {
        this.messages = messages;
    }

    public List<Message> latest(String chatId, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("Ask for at least one message.");
        return messages.findLatest(chatId, limit);
    }
}
`,
  "tauri.conf.json": `{
  "productName": "Subscribers Chat",
  "version": "0.1.0",
  "app": {
    "windows": [{ "title": "Subscribers Chat", "width": 1100, "height": 720, "decorations": false }]
  },
  "bundle": { "active": true, "targets": ["msi", "dmg"] }
}
`,
  "ci.yml": `name: CI
on:
  push:
    branches: [main]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: bun install
      - run: bun run typecheck # every package
`,
  "README.md": `# Subscribers Chat

A desktop chat app, **native** on Windows and macOS.

- Sign in with your account
- Chats, files and voice notes
- Works offline; sends when you're back

\`\`\`sh
bun run desktop
\`\`\`
`,
  "report.py": `from dataclasses import dataclass


@dataclass
class Month:
    name: str
    subscribers: int


def growth(months: list[Month]) -> float:
    """How much the last month grew over the first, in percent."""
    first, last = months[0].subscribers, months[-1].subscribers
    return (last - first) / first * 100


print(f"{growth([Month('Sep', 120), Month('Oct', 150)]):.1f}%")
`,
  "top-fans.sql": `-- The ten fans who sent the most this month.
SELECT f.name, SUM(t.amount) AS total
FROM fans f
JOIN tips t ON t.fan_id = f.id
WHERE t.sent_at >= DATE_TRUNC('month', NOW())
GROUP BY f.name
ORDER BY total DESC
LIMIT 10;
`,
  Dockerfile: `FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/chat-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
`,
}

const files: FileNode[] = [
  {
    name: "src",
    children: [
      { name: "chat-list.tsx" },
      { name: "main.rs" },
      { name: "report.py" },
    ],
  },
  {
    name: "service",
    children: [
      { name: "ChatService.java" },
      { name: "top-fans.sql" },
      { name: "Dockerfile" },
    ],
  },
  { name: ".github", children: [{ name: "ci.yml" }] },
  { name: "tauri.conf.json" },
  { name: "README.md" },
]

const NAMES = Object.keys(SAMPLES)

/** One file at a time; the segmented control switches files, and the language follows each file's name. */
function Languages() {
  const [file, setFile] = React.useState("chat-list.tsx")
  const [language, setLanguage] = React.useState("")
  return (
    <div style={{ display: "grid", gap: 8, width: "100%" }}>
      <SegmentedControl
        aria-label="File"
        value={file}
        onValueChange={setFile}
        segments={NAMES.slice(0, 8).map((n) => ({
          value: n,
          label: n.slice(n.lastIndexOf(".") + 1) || n,
        }))}
      />
      <CodeEditor
        aria-label={`Contents of ${file}`}
        fileName={file}
        defaultValue={SAMPLES[file]}
        onLanguageChange={(_id, name) => setLanguage(name)}
        height={300}
      />
      <span className="nt-secondary">
        {file}: {language || "…"}
      </span>
    </div>
  )
}

/** A tree of files on the left opens a file into the editor on the right, as an IDE. */
function WithFiles() {
  const [open, setOpen] = React.useState("src/chat-list.tsx")
  const [language, setLanguage] = React.useState("")
  const [line, setLine] = React.useState(0)
  const name = open.slice(open.lastIndexOf("/") + 1)
  return (
    <div
      style={{
        display: "grid",
        gridTemplateRows: "1fr auto",
        height: 380,
        width: "100%",
      }}
    >
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "190px 1fr",
          minHeight: 0,
        }}
      >
        <FileTree
          aria-label="Files"
          items={files}
          multiple={false}
          selected={open}
          defaultExpanded={["src", "service"]}
          onSelect={(path, node) =>
            node.children === undefined && setOpen(path)
          }
          style={{ borderRight: "1px solid var(--nt-separator)" }}
        />
        <CodeEditor
          aria-label={`Contents of ${name}`}
          fileName={open}
          defaultValue={SAMPLES[name] ?? ""}
          onLanguageChange={(_id, label) => setLanguage(label)}
          onChange={(text) => setLine(text.split("\n").length)}
          frameless
        />
      </div>
      <StatusBar>
        <StatusItem>{open}</StatusItem>
        <StatusBarSpacer />
        {line > 0 && <StatusItem>{line} lines</StatusItem>}
        <StatusItem>{language || "…"}</StatusItem>
      </StatusBar>
    </div>
  )
}

function Block() {
  return (
    <div style={{ display: "grid", gap: 10, width: "100%" }}>
      <p style={{ margin: 0 }}>Ana sent:</p>
      <CodeBlock
        fileName="top-fans.sql"
        caption="top-fans.sql"
        code={SAMPLES["top-fans.sql"].trimEnd()}
      />
      <CodeBlock
        language="typescript"
        code={`const total = tips.reduce((sum, t) => sum + t.amount, 0)`}
      />
    </div>
  )
}

const meta = {
  title: "Native/CodeEditor",
  component: Languages,
  decorators: [bothPlatforms],
  parameters: { nativeStretch: true },
} satisfies Meta<typeof Languages>
export default meta
type Story = StoryObj<typeof meta>

const darkToo = { parameters: { nativeStretch: true, ...dark } }

export const ManyLanguages: Story = {}
export const ManyLanguagesDark: Story = darkToo
export const OpenFromFiles: Story = { render: () => <WithFiles /> }
export const OpenFromFilesDark: Story = {
  render: () => <WithFiles />,
  ...darkToo,
}
export const ReadOnly: Story = {
  render: () => (
    <CodeEditor
      aria-label="Contents of main.rs"
      fileName="main.rs"
      value={SAMPLES["main.rs"]}
      readOnly
      height={260}
    />
  ),
}
export const Empty: Story = {
  render: () => (
    <CodeEditor
      aria-label="New file"
      fileName="untitled.ts"
      defaultValue=""
      height={200}
    />
  ),
}
export const WithMinimapAndWrap: Story = {
  render: () => (
    <CodeEditor
      aria-label="README.md"
      fileName="README.md"
      defaultValue={SAMPLES["README.md"]}
      minimap
      wordWrap
      height={260}
    />
  ),
}
export const SnippetInAMessage: Story = { render: () => <Block /> }
export const SnippetInAMessageDark: Story = {
  render: () => <Block />,
  ...darkToo,
}
