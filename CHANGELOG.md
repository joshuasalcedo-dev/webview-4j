# Changelog

## 0.1.0 — 2026-11-27

- swaswa-ffi: the window, the page and its typed HTML and CSS (`HtmlElement`, `CssProperty`, `Style`), components
  (`Component`, `Container`, `Control`) and their roles (`ButtonBase`, `TextInput`, `Toggle`, `Choice`, `ListView`,
  `DialogBase`...), `SwaApplication` (one class: a desktop window or a web page), app state (`swaswa.state`: `Store`,
  `LocalState`, `Lifetime`), development and production modes (`AppMode`), SVG for images.
- swaswa-webview, swaswa-dom: the webview and the page's document from Java, in batches.
- swaswa-tailwind: Tailwind classes, typed, with each page's CSS made in Java.
- swaswa-react: real React components from Java (`ReactElement`): props (records, dates, bytes, files, styles, other
  components), two-way binds, events; adapters (`ReactAdapter`: the component calls Java, Java answers and pushes
  events), handles (Java calls a component's actions) and menus made in Java (`MenuItem`); the Java generator
  (`java-gen.ts`).
- swaswa-shadcn: every shadcn/ui component, one package per family, `Shadcn*`.
- swaswa-ui: native-looking components (`Ui*`): buttons, fields, lists, tables, toolbars, status bars, code editor,
  documents (PDF, Word, Excel), file views and the chat (`UiChatWindow` with a typed `ChatService`).
- swaswa-cli: the `swaswa` command: `swaswa new <app>`, `swaswa run [--watch]`, `swaswa build [--type ...]`
  (the jar, its libraries and a jpackage installer).
- Published to swaswa's Nexus: `https://repository.swaswa.cloud/repository/maven-releases/`, group
  `io.joshuasalcedo.swaswa`.
