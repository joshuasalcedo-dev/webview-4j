# webview-4j-devtools

A Swing window to keep beside a webview-4j app while you develop it.

![The dev window beside the demo app](dev-window.png)

- **What the app does now:** frames per second between Java and the native side, each way, and
  kilobytes per second; actions and pages' calls per second; how long the latest action took, with
  the mean and the 95th percentile; the same for the pages' calls (commands and routes); each page's
  frames per second.
- **Log:** Java's logging, the native side's log, the pages' console and the pages' events, with a
  filter, pause and follow.
- **Actions:** each action's count and timings (`app.open`, `view.eval`, ...).
- **Pages:** the open views and their frames per second.

```java
try (App app = App.start()) {
    DevWindow.attachIfDev(app);   // only with -Dwebview4j.dev=true or WEBVIEW4J_DEV=true
    // or DevWindow.attach(app) to always open it
}
```

The numbers come from `App.metrics()`, which any app can read. A page's frames per second are
counted by a small fixed script the window evaluates in it once a second; those evaluations show
among the actions as `view.eval`.

Try it: `./gradlew :devtools:runDemo` opens a sample app with the window beside it.
