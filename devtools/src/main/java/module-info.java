/**
 * webview-4j devtools: a Swing window beside an app while developing it, with its log and its
 * metrics. Attach it with {@code DevWindow.attach(app)}, or {@code DevWindow.attachIfDev(app)} to
 * open it only when {@code -Dwebview4j.dev=true} (or {@code WEBVIEW4J_DEV=true}). {@code ApiExplorer} looks at
 * and tries an HTTP API from its OpenAPI description, runs a mock server from it, and runs the project's tests.
 */
module io.joshuasalcedo.webview4j.devtools {
    requires transitive io.joshuasalcedo.webview4j;
    requires java.desktop;
    requires java.logging;
    // the API explorer: requests (java.net.http), the mock server (jdk.httpserver), JUnit reports (java.xml)
    requires java.net.http;
    requires jdk.httpserver;
    requires java.xml;
    exports io.joshuasalcedo.webview4j.devtools;
}
