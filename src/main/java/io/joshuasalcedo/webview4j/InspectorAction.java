package io.joshuasalcedo.webview4j;

import java.util.Optional;

/** What the inspector captured from the page: its API calls and DOM events. Codes 3xx. */
public enum InspectorAction implements Action {
    API_CALLS(300),
    DOM_EVENTS(301);

    private final int code;

    InspectorAction(int code) {
        this.code = code;
    }

    @Override public String group() { return "inspector"; }
    @Override public int code()     { return code; }

    public static Optional<InspectorAction> fromCode(int code) {
        for (InspectorAction action : values()) {
            if (action.code == code) return Optional.of(action);
        }
        return Optional.empty();
    }
}
