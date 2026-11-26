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
    // The appearance (plugin.theme) remembers the design spec and light or dark between runs.
    requires java.prefs;
    // The annotation processor for @WebViewController (processor package): javac has java.compiler, an app needs none.
    requires static java.compiler;
    // Dev tools (plugin.devtools): a Swing window beside the app; its API explorer reads JUnit reports.
    requires java.desktop;
    requires java.xml;
    // Value objects as first-class fields and displays (ui.components.valueobject).
    requires transitive io.joshuasalcedo.valueobject;
    exports io.joshuasalcedo.webview4j;
    exports io.joshuasalcedo.webview4j.annotation;
    exports io.joshuasalcedo.webview4j.plugin.automation;
    exports io.joshuasalcedo.webview4j.ui;
    exports io.joshuasalcedo.webview4j.ui.event;
    exports io.joshuasalcedo.webview4j.ui.style;
    exports io.joshuasalcedo.webview4j.ui.icons;
    exports io.joshuasalcedo.webview4j.ui.components;
    exports io.joshuasalcedo.webview4j.ui.components.layout;
    exports io.joshuasalcedo.webview4j.ui.components.egui;
    exports io.joshuasalcedo.webview4j.ui.state;
    exports io.joshuasalcedo.webview4j.ui.components.valueobject;
    exports io.joshuasalcedo.webview4j.plugin.mdi;
    exports io.joshuasalcedo.webview4j.plugin.autoupdate;
    exports io.joshuasalcedo.webview4j.plugin.authentication;
    exports io.joshuasalcedo.webview4j.plugin.document;
    exports io.joshuasalcedo.webview4j.plugin.collection;
    exports io.joshuasalcedo.webview4j.plugin.devtools;
    exports io.joshuasalcedo.webview4j.plugin.chat;
    exports io.joshuasalcedo.webview4j.plugin;
    exports io.joshuasalcedo.webview4j.plugin.theme;
    exports io.joshuasalcedo.webview4j.plugin.settings;
    // Providers found in jars (META-INF/services), one line per provider interface
    uses io.joshuasalcedo.webview4j.plugin.theme.DesignSpecProvider;
}
