/**
 * swaswa-react: real React components in a view's page, used from Java as components: their props typed, their
 * events back as Java events, a value the person changes kept in Java. React is in the jar; an app needs no Node.
 */
module io.joshuasalcedo.swaswa.react {
    requires transitive io.joshuasalcedo.swaswa.ffi;
    exports io.joshuasalcedo.swaswa.react;
}
