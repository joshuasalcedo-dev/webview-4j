package io.joshuasalcedo.swaswa.ui;

import io.joshuasalcedo.swaswa.html.PageScript;

/**
 * swaswa-ui in a page: its components, served from the jar (ES modules; the code editor, the PDF and Office viewers
 * load when first shown), and their CSS: the system's look, Windows 11 or macOS, light or dark. Every component of
 * its packages brings it; a page gets it once. Each component sits in a {@code NativeProvider} (the platform, the
 * theme, menus and dialogs): its own, unless one is around it.
 */
public final class Ui {

    private Ui() {}

    private static final PageScript BUNDLE = PageScript.served("react:ui", Ui.class, "io/joshuasalcedo/swaswa/ui/web",
            "swaswa-ui/ui.js", "swaswa-ui/ui.css");

    /** The components and their CSS, served: the page's script {@code "react:ui"}. */
    public static PageScript bundle() {
        return BUNDLE;
    }
}
