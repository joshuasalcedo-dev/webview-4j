/**
 * swaswa-dom: a view's page from Java, typed: what its scripts give back as the app's own types, its functions behind a
 * Java interface, the browser's APIs (clipboard, size, scroll, location, selection, an element's box), and values
 * followed as properties (media queries, online, visible).
 */
module io.joshuasalcedo.swaswa.dom {
    requires transitive io.joshuasalcedo.swaswa.ffi;
    exports io.joshuasalcedo.swaswa.dom;
}
