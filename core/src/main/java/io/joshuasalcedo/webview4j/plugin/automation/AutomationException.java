package io.joshuasalcedo.webview4j.plugin.automation;

import java.util.List;

/** An automation step that could not be done: what was looked for, and what the page had instead. */
public final class AutomationException extends RuntimeException {

    private final List<String> seen;

    AutomationException(String message, List<String> seen) {
        super(message + (seen.isEmpty() ? "" : "\n  on the page: " + String.join(", ", seen)));
        this.seen = List.copyOf(seen);
    }

    /** What the page had that could be pointed at (its buttons, fields, links...), when the target was not there. */
    public List<String> seen() {
        return seen;
    }
}
