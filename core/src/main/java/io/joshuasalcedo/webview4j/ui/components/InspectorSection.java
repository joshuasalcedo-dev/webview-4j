package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A part of an inspector, under a heading that folds it. */
public final class InspectorSection extends Component<InspectorSection> {

    public InspectorSection(String title, Component<?>... children) {
        super("inspectorSection");
        prop("title", title);
        add(children);
    }

    public InspectorSection count(long count) { return prop("count", count); }
    public InspectorSection folded() { return prop("defaultOpen", false); }
}
