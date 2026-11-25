# @webview4j/ui

Two ways to use it:

- **In a React app with a bundler (Vite):** `bun add @webview4j/ui react react-dom`, then import each component,
  as below. Only what you import ends up in your build.
- **With no bundler at all:** core's jar carries the whole kit, built, and every webview-4j window can load it from
  `<base>ui/` (`App.UI_URL` in Java): `ui.css` and `ui.js`, which exports every component and React itself.

```html
<link rel="stylesheet" href="https://webview4j.localhost/ui/ui.css">   <!-- webview4j://localhost/ui/ on macOS and Linux -->
<div id="root"></div>
<script type="module">
  import { React, createRoot, NativeProvider, Button } from "https://webview4j.localhost/ui/ui.js"
  createRoot(document.getElementById("root")).render(
    React.createElement(NativeProvider, null, React.createElement(Button, { variant: "accent" }, "Send")))
</script>
```

webview-4j's native components: desktop controls that look like the system's own: Windows 11 (Fluent) on Windows,
macOS on a Mac (anything else gets the Windows look), light or dark as the system is set, and they change
when it does. Underneath they are the browser's own controls (`<button>`, `<input>`, `<select>`), so
keyboard use, screen readers and the dropdown lists behave like the system's.

```tsx
import { NativeProvider } from "@webview4j/ui/components/provider"
import { Button } from "@webview4j/ui/components/button"

<NativeProvider>                      {/* platform="auto", theme="system" */}
  <Button variant="accent">Send</Button>
</NativeProvider>
```

`NativeProvider` brings the styles (`native.css`) with it and sets `data-platform` and `data-theme`; every
control inside reads its look from that. `platform` / `theme` force one (the stories do), `accent` sets
another accent colour.

Components: `Button`, `TextField`, `Checkbox`, `Switch`, `Select`, `SegmentedControl`, `List` / `ListItem`,
`Avatar`, `Surface`, `TitleBar` (for a window without the system's frame: `WindowOptions.decorations(false)`; its
buttons and dragging work by themselves in a webview-4j window; `icon` for the app's logo on Windows), `ProgressBar`, `ContextMenu`, `Sidebar`, `Toolbar`,
`StatusBar`, `Tabs` (also inside a `TitleBar`), `Breadcrumb`, `SearchField`, `Content`, `Dialog` and
`useDialogs()` (message, confirm, choose, error, prompt, progress), `useSound()` (`sounds` on the provider),
`FileIcon`, `FileTree`, `FileGrid`, `FileList`, `FileExplorer`, `FileChip` (card or inline). Every file in them gets a
right-click menu by itself (Open, Copy name, Copy path; Rename, Delete, Show in Explorer/Reveal in Finder,
Properties/Get Info and New file/folder when their handlers are given), with F2 and Delete. Several can be
chosen (Ctrl/⌘+click, Shift+click, Shift+arrows, Ctrl+A; `selected` + `onSelectionChange`); the menu and Delete
then act on all of them (`onDeleteMany` asks once).

Text: `Text` (`variant` display, largeTitle, title, subtitle, headline, body, caption, footnote, each at the
system's own size: Windows 11's type ramp, the Mac's text styles; `tone`, `lines` to cut off with a tooltip,
`selectable` (on by default: text is content people copy), `mono`), `Label` (text that belongs to the controls: not
selectable, focuses or ticks its control), `Link` (`subtle` for clickable text in the text's colour), `Code`, `Shortcut`
(Mod+K written ⌘K or Ctrl+K). Inputs: `TextArea` (grows, counts),
`PasswordField` (show while held on Windows, a toggle on a Mac; Caps Lock warning), `NumberField` (Windows' arrows
inside, a Mac's stepper; ↑ ↓ Page Up/Down; min/max; the person's number format), `TokenField` (entries as chips,
suggestions, a check per entry), `Composer` (a chat's message box: Enter sends, Shift+Enter a new line, attach,
paste or drop files, keeps the message when sending fails). Money: `Money` (an amount as text, each currency its way:
₱499.00, ¥4,990, accounting (₱500.00), +₱1,250.00, ₱1.2M) and `MoneyField` (symbol inside, the currency's decimals,
a currency picker that keeps the typed amount); the value is a whole number of the smallest unit (centavos),
never a fraction. Units: `Quantity` (2.5 kg, 37 °C, 1.2 GB, in the person's language, `as` converts) and
`QuantityField` (a unit after the number, or a picker that converts: 2.5 kg → 5.51 lb); `convertUnit`.
`components/chat/*`: a general chat kit on ports and adapters (`ChatProvider` + `ChatConfig` + `createMemoryChatAdapter`;
see `chat/README.md`). Also general: `Pill`, `Swipeable`, `Inspector`, `ComposerToolbar` / `defaultComposerTools`,
`IconPicker` (any icon source).
`LogView` (a log that grows as things happen: time, level, source, message; a line opens to show its technical
details; follows the newest line unless scrolled up). `InfiniteScroll` and `useInfiniteScroll` (the next page loads before the end is reached; Try again when it fails;
`direction="up"` for histories, keeping the view still), `Spinner` (Windows' ProgressRing, a Mac's spokes). `CodeEditor` (VS Code's Monaco: about 80 languages,
picked from `fileName`; offline, loads only when first shown; the window's colours and the native right-click
menu) and `CodeBlock` (a coloured, copyable snippet). `RichTextEditor` (TipTap: bold/italic/underline/strike/code,
headings, lists, quote, code block, links via a dialog; a WordPad-style bar on Windows, TextEdit's on a Mac;
value as HTML, `onChange` gives HTML, JSON and text).

What every app gets from the provider, with nothing to set up:
- the systems' pointers: the arrow on controls, the I-beam on text that can be selected or typed in, the hand
  only on links, resize arrows on `data-resize`;
- clicks that grow a selection: two the word, three the sentence, four the paragraph, five everything (in text
  boxes, editors and selectable text; code editors keep their own);
- the systems' scroll bars (thin, no arrows);
- dialogs and menus in the browser's top layer: a `Dialog` is the native modal `<dialog>`, so it always covers
  the whole window and is never cut off by what it is written inside; a menu opened in a dialog is drawn in it.
  Nothing relies on the browser's default styles (an app's CSS reset can't move or squash them).

## File icons

`FileIcon` (and `FileTree`, `FileChip`) use the icons of [Material Icon Theme](https://github.com/material-extensions/vscode-material-icon-theme)
(npm `material-icon-theme`, MIT licence, Copyright (c) 2025 Material Extensions), VS Code's most used icon
theme, with its own map from file names and extensions to icons. Each icon loads when it is first shown.
To update: change its version in package.json; nothing is copied by hand.

Stories: `script.jar storybook open`, under **Native** (each component on both systems, light and dark; **Gallery** is
a whole chat window).

## Canvas components (egui-4j)

`EguiCanvas` and `DevLogger` draw with [egui-4j](../../../../crates/egui-4j) (Rust, built for the page as wasm):
charts that stream, the monitor, the dev logger. `bun run egui` builds it into `src/egui4j/` (not in git); Gradle's
`:core:uiEgui` runs it before the bundle. It needs, once per machine:

```sh
rustup target add wasm32-unknown-unknown
cargo install wasm-bindgen-cli --version <wasm-bindgen's version in Cargo.lock>
```

Each component is run in Rust first (`cargo run -p egui-4j --example <component>`, `cargo test -p egui-4j`).
