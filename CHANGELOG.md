# Changelog

## 0.2.0 — unreleased

- File dialogs: `app.openFile`, `openFiles`, `saveFile` and `pickFolder`, with filters, a start directory and a parent window
- Message boxes: `app.message` (info, warning or error; OK, OK/Cancel, Yes/No, Yes/No/Cancel) and `app.confirm`
- A dialog never holds the app up: other windows keep working while it is open
- Window controls: `minimize`, `maximize`, `restore`, `setFullscreen`, `setAlwaysOnTop`, `setResizable`, `setMinSize`
- `view.geometry()`: where a window is and how it is shown, to save its place and restore it next time
- Window events: `Resized`, `Moved` (the last of each per round, not dozens a second while dragging) and `Focused`
- `WindowOptions.confirmClose(true)`: the close button sends `CloseRequested` and the app decides, such as after asking about unsaved work
- `WindowOptions.fileDrop(true)`: files dropped on a window arrive as `FileDrop` events with their paths
- `WindowOptions.minSize`, `maximized`, `fullscreen` and `alwaysOnTop`
- README: on macOS, run the app with `App.run` from `main` and `-XstartOnFirstThread`

## 0.1.0 — 2026-10-21

- First public release on Maven Central: `io.joshuasalcedo:webview-4j-core`, `-processor` and `-devtools`
- A native webview (WebView2 on Windows, WebKit on macOS and Linux) driven from Java 25 over FFM, no JNI
- The native library is inside the jar for Windows x64, Linux x64, macOS x64 and Apple silicon
- Java and the native side speak a NATS-style protocol (PROTOCOL.md), with conformance fixtures
- The page calls Java and Java pushes to the page: `invoke`, `listen`, `emit`, over the webview4j custom protocol
- `@WebViewController` routes with an annotation processor and a typed TypeScript client, no reflection
- `HttpApi`: the same routes as a local HTTP API, with a bearer token and allowed origins
- `WindowOptions.app(...)`: the app's own page from the class path, and a window icon
- `App.playSound`: MP3, WAV, Ogg Vorbis and FLAC, played natively
- Captured console logs, clicks, fetches and WebSockets as events
- Devtools: a Swing window beside the app with its log and live metrics
