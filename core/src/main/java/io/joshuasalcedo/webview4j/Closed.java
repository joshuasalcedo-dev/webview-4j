package io.joshuasalcedo.webview4j;

/** A view's window closed, by the user or by {@link View#close()}. */
public record Closed(String view) implements WebViewEvent {}
