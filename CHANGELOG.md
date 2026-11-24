# Changelog

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
