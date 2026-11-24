package io.joshuasalcedo.webview4j.ui.components;

/** A text's size and weight, as the system's type ramp names them. */
public enum TextVariant {
    DISPLAY, LARGE_TITLE, TITLE, SUBTITLE, HEADLINE, BODY, CAPTION, FOOTNOTE;

    /** As the page's renderer names it: {@code largeTitle} for LARGE_TITLE. */
    public String key() {
        return Keys.camel(this);
    }
}
