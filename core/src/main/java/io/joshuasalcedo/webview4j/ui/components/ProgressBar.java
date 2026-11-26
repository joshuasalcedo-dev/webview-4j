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

    /** 0 to 100 now; a window showing it draws again. */
    public ProgressBar value(double value) { return prop("value", value); }

    // --- as Swing writes it ---

    /** 0 to 100, or -1 while {@link #indeterminate}. */
    public double getValue() { return get("value") instanceof Number n ? n.doubleValue() : -1; }
    public ProgressBar setValue(double value) { return value(value); }
    /** Runs back and forth ({@code true}), or shows its value again ({@code false}, at 0). */
    public ProgressBar setIndeterminate(boolean indeterminate) { return indeterminate ? indeterminate() : value(0); }
    public boolean isIndeterminate() { return get("value") == null; }
}
