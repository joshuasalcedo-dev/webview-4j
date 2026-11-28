package io.joshuasalcedo.swaswa.react;

import io.joshuasalcedo.swaswa.html.PageScript;

/**
 * React in a page: the runtime every React component needs (React and ReactDOM, the bridge to Java), put in a page
 * once, before any component library. A library's bundle registers its components with it:
 * {@code __swaswaReact.register("Button", Button)}, sharing its React ({@code window.__swaswaReact.React}).
 */
public final class React {

    private React() {}

    /** The React version in the jar. */
    public static final String VERSION = "19.3.1";

    private static volatile PageScript runtime;

    /** The runtime: React, ReactDOM and the bridge, as the page's script {@code "react"}. */
    public static PageScript runtime() {
        PageScript r = runtime;
        if (r == null) runtime = r = PageScript.resource("react", React.class, "react-runtime.js");
        return r;
    }
}
