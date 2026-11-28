package io.joshuasalcedo.swaswa.html;

import io.joshuasalcedo.swaswa.ffi.App;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A script a kind of element needs in its page ({@link HtmlElement#kindScripts()}): put in a page once, by its id,
 * and again when the page loads anew. Either its source itself ({@link #resource}), or a library served as files
 * ({@link #served}): ES modules, the chunks they load when needed, their WASM and fonts, from the jar.
 *
 * @param id        what it is, once per page ({@code "react"}, {@code "react:ui"})
 * @param source    its JavaScript (for a served library: what loads it)
 * @param directory for a served library, the class path directory its files are in; null otherwise
 * @param loader    for a served library, what reads them; null otherwise
 */
public record PageScript(String id, String source, String directory, ClassLoader loader) {

    public PageScript {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        if ((directory == null) != (loader == null)) throw new IllegalArgumentException("a directory and its loader, or neither");
    }

    public PageScript(String id, String source) {
        this(id, source, null, null);
    }

    /** A script from a resource beside {@code owner} (its class's package), read once by the caller and kept. */
    public static PageScript resource(String id, Class<?> owner, String name) {
        try (InputStream in = owner.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException(name + " is not beside " + owner.getName());
            return new PageScript(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A library served as files: {@code directory} (on {@code owner}'s class path) is served with the app's shared
     * files ({@link App#SHARED_URL}) when a page first needs it, and the page imports {@code entry} from there (an ES
     * module: what it imports later comes from there too), after adding {@code css}'s style sheet, if any.
     *
     * @param entry its module, under {@code directory}: {@code "swaswa-ui/ui.js"}
     * @param css   its style sheet, under {@code directory}, or null
     */
    public static PageScript served(String id, Class<?> owner, String directory, String entry, String css) {
        Objects.requireNonNull(owner, "owner");
        String base = App.SHARED_URL;
        // the files' version in their URLs: a page never gets an older build's from the webview's cache
        String version = "?v=" + version(owner.getClassLoader(), directory, entry, css);
        StringBuilder js = new StringBuilder("(() => {\n");
        if (css != null) {
            js.append("  if (!document.querySelector('link[data-swaswa-library=").append(quote(id)).append("]')) {\n")
                    .append("    const l = document.createElement('link'); l.rel = 'stylesheet'; l.dataset.swaswaLibrary = ")
                    .append(quote(id)).append(";\n")
                    .append("    l.href = ").append(quote(base + css + version)).append("; (document.head || document.documentElement).append(l);\n  }\n");
        }
        js.append("  import(").append(quote(base + entry + version)).append(").catch((e) => console.error('swaswa: ")
                .append(id).append(" did not load', e));\n})();\n");
        return new PageScript(id, js.toString(), directory, owner.getClassLoader());
    }

    /** A short hash of the library's entry and style sheet (what changes with every build of it). */
    private static String version(ClassLoader loader, String directory, String entry, String css) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        for (String file : new String[] {entry, css}) {
            if (file == null) continue;
            try (InputStream in = loader.getResourceAsStream(directory + "/" + file)) {
                if (in == null) throw new IllegalStateException(directory + "/" + file + " is not on the class path: the library's files were not built");
                crc.update(in.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return Long.toHexString(crc.getValue());
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
