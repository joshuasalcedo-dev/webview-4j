package io.joshuasalcedo.swaswa.ffi;

import java.nio.file.Path;

/** An open view as {@link App#views()} reports it. {@code dataDirectory}: its browser profile. */
public record ViewInfo(String id, String title, String url, Path dataDirectory) {}
