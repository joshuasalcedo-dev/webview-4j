/**
 * webview-4j: native webview windows driven from Java, with a typed bridge to the page.
 * Only the API package is exported; the native layer and the wire protocol stay inside.
 * Run with {@code --enable-native-access=io.joshuasalcedo.webview4j}.
 */
module io.joshuasalcedo.webview4j {
    requires jdk.httpserver;
    // A server's invalidations (ui.state.Invalidations): server-sent events.
    requires java.net.http;
    // The dev logger shows Java's own logging (java.util.logging, where System.Logger goes by default).
    requires java.logging;
    // Value objects as first-class fields and displays (ui.components.valueobject).
    requires transitive io.joshuasalcedo.valueobject;
    exports io.joshuasalcedo.webview4j;
    exports io.joshuasalcedo.webview4j.annotation;
    exports io.joshuasalcedo.webview4j.automation;
    exports io.joshuasalcedo.webview4j.ui;
    exports io.joshuasalcedo.webview4j.ui.components;
    exports io.joshuasalcedo.webview4j.ui.components.layout;
    exports io.joshuasalcedo.webview4j.ui.state;
    exports io.joshuasalcedo.webview4j.ui.components.valueobject;
    exports io.joshuasalcedo.webview4j.ui.components.usecase.mdi;
    exports io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate;
    exports io.joshuasalcedo.webview4j.ui.components.usecase.authentication;
    exports io.joshuasalcedo.webview4j.ui.components.usecase.collection;
}
