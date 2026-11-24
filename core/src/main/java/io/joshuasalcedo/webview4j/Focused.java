package io.joshuasalcedo.webview4j;

/** A view's window gained ({@code focused}) or lost the keyboard focus. */
public record Focused(String view, boolean focused) implements WebViewEvent {}
