package io.joshuasalcedo.webview4j.ui.components;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A file, or a folder (with children), for {@link FileTree}, {@link FileList}, {@link FileGrid} and
 * {@link FileExplorer}. A path is the names from the top, joined by "/".
 */
public record FileNode(String name, List<FileNode> children, Long size, Instant modified) {

    public static FileNode file(String name, long size, Instant modified) {
        return new FileNode(name, null, size, modified);
    }

    public static FileNode file(String name) {
        return new FileNode(name, null, null, null);
    }

    public static FileNode folder(String name, FileNode... children) {
        return new FileNode(name, List.of(children), null, null);
    }

    public boolean isFolder() {
        return children != null;
    }

    static List<Map<String, Object>> json(List<FileNode> nodes) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (FileNode n : nodes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", n.name());
            if (n.children() != null) m.put("children", json(n.children()));
            if (n.size() != null) m.put("size", n.size());
            if (n.modified() != null) m.put("modified", n.modified().toString());
            list.add(m);
        }
        return list;
    }
}
