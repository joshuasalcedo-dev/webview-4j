/**
 * swaswa-ffi: native webview windows driven from Java, with a typed bridge to the page.
 * The native layer and the wire protocol stay inside.
 * Run with {@code --enable-native-access=io.joshuasalcedo.swaswa.ffi}.
 */
module io.joshuasalcedo.swaswa.ffi {
    // The dev logger shows Java's own logging (java.util.logging, where System.Logger goes by default).
    requires java.logging;
    exports io.joshuasalcedo.swaswa.ffi;
    // HTML's elements and CSS, typed, live in a view's page
    // the components' contract: Component, Container, Layout, Event, Property
    exports io.joshuasalcedo.swaswa.component;
    // CSS, typed: Style, StyleSheet, CssProperty
    exports io.joshuasalcedo.swaswa.css;
    exports io.joshuasalcedo.swaswa.html;
    // inside swaswa: its own modules only (JSON, the hooks between packages); never the API
    exports io.joshuasalcedo.swaswa.internal to io.joshuasalcedo.swaswa.tailwind, io.joshuasalcedo.swaswa.dom;
    // an app's life: SwaApplication (init, start, stop, launch)
    exports io.joshuasalcedo.swaswa.app;
    // CSS processors (swaswa-tailwind's): what a style sheet goes through when it is loaded
    uses io.joshuasalcedo.swaswa.css.CssProcessor;
}
