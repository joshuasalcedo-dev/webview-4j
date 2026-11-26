package io.joshuasalcedo.swaswa.ffi;

import java.util.Map;

/**
 * An event of the page's Chrome DevTools Protocol that Java listens to ({@link View#listenDevTools(String)}), e.g.
 * {@code Page.screencastFrame}: its name and its parameters as JSON values. Windows only, for now.
 */
public record DevToolsEvent(String view, String event, Map<String, Object> params) implements WebViewEvent {}
