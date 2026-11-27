package io.joshuasalcedo.swaswa.html;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A script a kind of element needs in its page ({@link HtmlElement#kindScripts()}): put in a page once, by its id,
 * and again when the page loads anew.
 *
 * @param id     what it is, once per page ({@code "react"}, {@code "react:shadcn"})
 * @param source its JavaScript
 */
public record PageScript(String id, String source) {

    public PageScript {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
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
}
