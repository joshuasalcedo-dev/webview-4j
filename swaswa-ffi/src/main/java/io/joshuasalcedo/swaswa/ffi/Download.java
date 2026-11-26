package io.joshuasalcedo.swaswa.ffi;

import java.nio.file.Path;

/** A page's download: started (where it goes), then finished or failed. Refused with {@code downloads(false)}. */
public record Download(String view, State state, String url, Path path) implements WebViewEvent {

    public enum State { STARTED, FINISHED, FAILED }
}
