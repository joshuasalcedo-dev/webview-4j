package io.joshuasalcedo.swaswa.demo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The Tailwind docs as web pages, with no window: one HTML file per family ({@code box.html}...), each the page
 * {@link TailwindDocs} shows for it. For a browser, or a headless one's screenshots. {@code just tailwind-pages}.
 */
public final class DocsPages {

    private DocsPages() {}

    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args.length > 0 ? args[0] : "target/docs-pages");
        Files.createDirectories(folder);
        int pages = 0;
        for (List<String> families : TailwindDocs.CATEGORIES.values()) {
            for (String family : families) {
                TailwindDocs docs = new TailwindDocs(family);
                Files.writeString(folder.resolve(family + ".html"), docs.toHtml(), StandardCharsets.UTF_8);
                pages++;
            }
        }
        System.out.println(pages + " pages in " + folder.toAbsolutePath());
    }
}
