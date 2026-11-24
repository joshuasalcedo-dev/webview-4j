# Notes: a sample webview-4j app

Java behind a React page made with `@webview4j/ui`: a frameless window whose title bar is the page's own, notes kept
by a Java controller, and a switcher for the look (Windows 11 or macOS) and the theme (system, light or dark).
It is a Gradle build of its own: copy this folder to start your app.

```shell
./gradlew run          # builds web/ with Vite into the jar's web/ and opens the app
./gradlew run -Pdev    # the Vite dev server instead: run `bun run dev` in web/ first, then edit and save
```

Needs Java 25 and [bun](https://bun.sh).

| Where | What |
|---|---|
| `src/main/java/example/notes/NotesApp.java` | opens the window (`WindowOptions.app("web")`, no frame, asks before closing) |
| `src/main/java/example/notes/NotesController.java` | `GET/POST /notes`, `DELETE /notes/{id}`: the page's API |
| `src/main/java/example/notes/AppearanceController.java` | `PUT /appearance/theme`: light or dark, for the window and the page |
| `web/src/App.tsx` | the page: `TitleBar`, `SegmentedControl`, `TextField`, `List`, `StatusBar` |
| `web/src/api.generated.ts` | written by webview-4j's annotation processor from the controllers when Java compiles |

**In webview-4j's repository** it builds against webview-4j's own sources: `settings.gradle.kts` includes the
repository's build, and `web/vite.config.ts` points `@webview4j/ui` and `@webview4j/api` at their sources. **In a
copy of your own**, delete the `includeBuild` block (the dependencies then come from Maven Central), and replace the
two aliases with the packages.
