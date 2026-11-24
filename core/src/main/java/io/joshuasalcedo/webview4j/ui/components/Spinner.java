package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Something is happening: Windows' ProgressRing, a Mac's spokes. */
public final class Spinner extends Component<Spinner> {

    public Spinner(String label) {
        super("spinner");
        prop("label", label);
    }

    public Spinner size(double size) { return prop("size", size); }
}
