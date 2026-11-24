package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Pills in a row: that many, then "+N". */
public final class PillGroup extends Component<PillGroup> {

    public PillGroup(Pill... pills) {
        super("pillGroup");
        add(pills);
    }

    /** How many show before "+N" (3 unless set). */
    public PillGroup max(int max) { return prop("max", max); }
    /** As many as the room allows. */
    public PillGroup fit() { return prop("fit", true); }
}
