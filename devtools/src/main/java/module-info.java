/**
 * webview-4j devtools: a Swing window beside an app while developing it, with its log and its
 * metrics. Attach it with {@code DevWindow.attach(app)}, or {@code DevWindow.attachIfDev(app)} to
 * open it only when {@code -Dwebview4j.dev=true} (or {@code WEBVIEW4J_DEV=true}).
 */
module io.joshuasalcedo.webview4j.devtools {
    requires transitive io.joshuasalcedo.webview4j;
    requires java.desktop;
    requires java.logging;
    exports io.joshuasalcedo.webview4j.devtools;
}
