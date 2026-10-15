package io.joshuasalcedo.webview4j;

import java.nio.file.Path;

/** An open view as {@link App#views()} reports it. {@code dataDirectory} is null without a profile. */
public record ViewInfo(String id, String title, String url, Path dataDirectory) {}
