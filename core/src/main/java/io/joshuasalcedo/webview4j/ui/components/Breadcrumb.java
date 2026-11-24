package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Where the page is, as a trail of the places above it: each can be gone back to. */
public final class Breadcrumb extends Component<Breadcrumb> {

    public Breadcrumb() {
        super("breadcrumb");
    }

    /** A place in the trail; the last is where the page is. */
    public Breadcrumb crumb(String label, Runnable onSelect) {
        Element crumb = new Element("crumb").set("label", label);
        if (onSelect != null) crumb.handle("onSelect", v -> onSelect.run());
        return add(crumb);
    }

    public Breadcrumb crumb(String label) { return crumb(label, null); }
}
