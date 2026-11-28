/**
 * Stable application module that makes swaswa's supported feature modules available from one dependency.
 *
 * <p>This module contains no implementation classes. The native boundary remains in
 * {@code io.joshuasalcedo.swaswa.ffi}; application authors should depend on this module for the complete toolkit.</p>
 */
module io.joshuasalcedo.swaswa.core {
    requires transitive io.joshuasalcedo.swaswa.ffi;
    requires transitive io.joshuasalcedo.swaswa.webview;
    requires transitive io.joshuasalcedo.swaswa.tailwind;
    requires transitive io.joshuasalcedo.swaswa.dom;
    requires transitive io.joshuasalcedo.swaswa.react;
    requires transitive io.joshuasalcedo.swaswa.shadcn;
    requires transitive io.joshuasalcedo.swaswa.ui;
}
