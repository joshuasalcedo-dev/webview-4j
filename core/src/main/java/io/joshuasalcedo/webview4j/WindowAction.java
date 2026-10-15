package io.joshuasalcedo.webview4j;

import java.util.Optional;

/** The native window: its life, place and visibility. Codes 1xx. */
enum WindowAction implements Action {
    OPEN(100),
    LIST(101),
    SET_TITLE(102),
    SET_SIZE(103),
    SET_POSITION(104),
    SET_BOUNDS(105),
    SHOW(106),
    HIDE(107),
    FOCUS(108),
    CLOSE(109);

    private final int code;

    WindowAction(int code) {
        this.code = code;
    }

    @Override public String group() { return "window"; }
    @Override public int code()     { return code; }

    static Optional<WindowAction> fromCode(int code) {
        for (WindowAction action : values()) {
            if (action.code == code) return Optional.of(action);
        }
        return Optional.empty();
    }
}
