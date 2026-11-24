package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Windows inside the app's window (MDI): each moves, resizes, minimizes and maximizes there. */
public final class Mdi extends Component<Mdi> {

    public Mdi(String label, MdiWindow... windows) {
        super("mdi");
        prop("label", label);
        add(windows);
    }

    public Mdi with(MdiWindow... windows) { return add(windows); }
    /** Shown when no window is open. */
    public Mdi empty(String text) { return prop("empty", text); }
}
