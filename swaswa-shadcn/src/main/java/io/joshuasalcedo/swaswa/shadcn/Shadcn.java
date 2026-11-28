package io.joshuasalcedo.swaswa.shadcn;

import io.joshuasalcedo.swaswa.html.PageScript;

/**
 * shadcn/ui in a page: its components (React, from the page's runtime), and their CSS (Tailwind's, with shadcn's
 * theme: light, and dark when the page is). Every component of this package brings it; a page gets it once.
 */
public final class Shadcn {

    private Shadcn() {}

    private static volatile PageScript bundle;

    /** The components and their CSS, as the page's script {@code "react:shadcn"}. */
    public static PageScript bundle() {
        PageScript b = bundle;
        if (b == null) bundle = b = PageScript.resource("react:shadcn", Shadcn.class, "shadcn.js");
        return b;
    }
}
