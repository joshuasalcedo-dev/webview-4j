package io.joshuasalcedo.webview4j;

import java.util.Objects;

/**
 * One route: a method, a path pattern such as {@code /users/{id}}, and what answers it. The
 * webview-4j annotation processor writes these for {@code @WebViewController} classes; they can
 * also be built by hand and given to {@link App#register(Routes, Object)}.
 */
public record Route(HttpMethod method, String pattern, Handler handler) {

    /** Answers a request: a value written as JSON, null for no content, or a CompletionStage of either. */
    @FunctionalInterface
    public interface Handler {
        Object handle(RouteRequest request) throws Exception;
    }

    public Route {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(handler, "handler");
        if (!pattern.startsWith("/")) throw new IllegalArgumentException("a route pattern starts with /: " + pattern);
    }
}
