/**
 * webview-4j: native webview windows driven from Java, with a typed bridge to the page.
 * Only the API package is exported; the native layer and the wire protocol stay inside.
 * Run with {@code --enable-native-access=io.joshuasalcedo.webview4j}.
 */
module io.joshuasalcedo.webview4j {
    requires jdk.httpserver;
    exports io.joshuasalcedo.webview4j;
    exports io.joshuasalcedo.webview4j.annotation;
}
