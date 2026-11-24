package io.joshuasalcedo.webview4j;

import java.nio.file.Path;
import java.util.List;

/**
 * Files dragged onto a view's window opened with {@link WindowOptions#fileDrop(boolean)
 * fileDrop(true)}: {@link Kind#ENTER} as they come over it, then {@link Kind#DROP} with the files
 * dropped, or {@link Kind#LEAVE} if they were taken away. {@code x} and {@code y} are where, in
 * physical pixels from the page's top left; {@code paths} is empty on {@code LEAVE}.
 */
public record FileDrop(String view, Kind kind, List<Path> paths, double x, double y) implements WebViewEvent {

    public enum Kind { ENTER, DROP, LEAVE }

    public FileDrop {
        paths = List.copyOf(paths);
    }
}
