package io.joshuasalcedo.webview4j;

import java.util.Optional;

/** What the inspector reports from the page: API calls, clicks, DOM events, WebSocket frames, console. Codes 3xx. */
enum InspectorAction implements Action {
    CAPTURE(300);

    private final int code;

    InspectorAction(int code) {
        this.code = code;
    }

    @Override public String group() { return "inspector"; }
    @Override public int code()     { return code; }

    static Optional<InspectorAction> fromCode(int code) {
        for (InspectorAction action : values()) {
            if (action.code == code) return Optional.of(action);
        }
        return Optional.empty();
    }
}
