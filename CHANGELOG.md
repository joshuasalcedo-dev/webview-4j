# Changelog

## 0.2.0 — unreleased

- Collections (`ui.components.usecase.collection`), React first and then Java: `DataTable`, `DataList`, `DataTree`, `DataMap` and `DataGrid`, from records and maps
- One query model for every collection: `Query` (page, size, sorts, search, column filters) and `Paging`; with `onQueryChange` the server answers it, and `Query.apply` answers one in memory
- Column filters in `DataTable`: text contains, one of some options (an enum), a range (numbers, money, dates), yes/no; Shift-click sorts by more than one column
- States shared by the collections: grey rows on the first load, a running bar after, an error with Try again, an empty state with an action, and "No matches" with Clear filters
- Spring Data in the starter: `SpringPaging.pageable(query)` and `SpringPaging.result(page)`
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
- `@webview4j/ui`: native-looking React components (Windows 11 or macOS, light or dark), from `core/src/main/ui`; an npm library for apps with a bundler
- Core's jar carries the kit built (`ui.js` with React, `ui.css`): every window loads it from `<base>ui/` (`App.UI_URL`), no bundler needed
- The page's own window: `data-webview4j-drag-region` moves it, a double click maximizes it, and `currentWindow` (`@webview4j/api`) minimizes, maximizes and closes it; `TitleBar` uses them by itself
- Building core needs bun, for the kit
- A window without the system's frame resizes from its edges, with the system's resize pointers
- `WindowOptions.theme` and `view.setTheme`: light or dark for the window and the page's `prefers-color-scheme` together
- `samples/notes`: a whole sample app, its own Gradle build, with a look (Windows 11 / macOS) and theme switcher
- Embedded views: `view.embed(options, x, y, width, height)` opens a website inside a window (Windows, macOS), `place` moves it; no bridge for it unless Java names its origins; it closes with its window
- `MdiArea` and `useMdi` in `@webview4j/ui`: windows inside the app's window (move, resize, minimize, maximize, cascade, tile, Ctrl+F6)
- `WindowOptions.app(...).page("other.html")`: another page of the app's own files
- `TitleBar dragsWindow={false}`: a title bar that is not the window's own
- `io.joshuasalcedo.webview4j.ui`: screens built only in Java from the native components (`Ui`, `Screen`, `UiWindow`): Java sends what to show as data, core's jar draws it, the user's actions run Java handlers; nothing is generated
- The sample's component gallery, written only in Java (`./gradlew run -Pgallery`)
- `UiException`: a handler's problem said for people (title, message, level) in the kit's dialog, the technical message under "Show details"; any other exception shows a calm generic message and is logged; `Ui.explain` for a library's own exceptions; `UiWindow.notify`
- devtools `ApiExplorer`: an API from its OpenAPI description (Spring's /v3/api-docs, any server, local or not): every endpoint with example values, sent from Java; every endpoint at once; a `MockServer` that answers from the description; the project's JUnit tests run from a button (`TestRunner`)
- `Json` is public: a small JSON reader and writer
- `webview-4j-spring-boot-starter`: a Spring Boot app opens in a native window when it is ready, with no code: titled after the app, its page at `/`, the API explorer found by itself (as the window, for an API with no page); `webview4j.*` overrides; quiet while tests run; `App` and `WebView4jWindow` beans
- `Router`, `PageView`, `LayoutView`, `PageContext`: apps of several pages the way Next.js does them: pages at paths with slugs (`/users/{id}`, `/docs/{*rest}`), nested layouts, query parameters, back and forward, `load(...)` for slow data, each page's errors kept to it, "not found"; a default frame with the navigation; `pagination(...)`
- `@WebViewApplication` (with `WebViewApplications.run`): a desktop client of a server; its `@Page` and `@Layout` beans are the main window; the `@HttpExchange` interfaces it imports (`@ImportHttpServices`, `webview4j.client-url`) are its clients, and in development a second window shows each endpoint as a form, checked with Jakarta Validation and sent for real
- The build reuses a native library already built from the current Rust sources instead of running cargo again (`-PrebuildNative` forces it)

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
