package io.joshuasalcedo.webview4j;

import java.util.Optional;

/** The page inside a window: what it shows and what runs in it. Codes 2xx. */
public enum WebViewAction implements Action {
    NAVIGATE(200),
    LOAD_HTML(201),
    EVAL(202),
    COOKIES(203),
    WEBSOCKET_SEND(204),
    RESOLVE(205),
    EMIT(206);

    private final int code;

    WebViewAction(int code) {
        this.code = code;
    }

    @Override public String group() { return "webview"; }
    @Override public int code()     { return code; }

    public static Optional<WebViewAction> fromCode(int code) {
        for (WebViewAction action : values()) {
            if (action.code == code) return Optional.of(action);
        }
        return Optional.empty();
    }
}
