package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** How far something is: 0 to 100, or {@link #indeterminate} when it is not known. */
public final class ProgressBar extends Component<ProgressBar> {

    public ProgressBar(String label, double value) {
        super("progress");
        prop("label", label);
        prop("value", value);
    }

    /** Runs back and forth: how long it takes is not known. */
    public ProgressBar indeterminate() { return prop("value", null); }
}
