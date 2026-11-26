/**
 * swaswa-ffi: native webview windows driven from Java, with a typed bridge to the page.
 * The native layer and the wire protocol stay inside.
 * Run with {@code --enable-native-access=io.joshuasalcedo.swaswa.ffi}.
 */
module io.joshuasalcedo.swaswa.ffi {
    // The dev logger shows Java's own logging (java.util.logging, where System.Logger goes by default).
    requires java.logging;
    exports io.joshuasalcedo.swaswa.ffi;
}
