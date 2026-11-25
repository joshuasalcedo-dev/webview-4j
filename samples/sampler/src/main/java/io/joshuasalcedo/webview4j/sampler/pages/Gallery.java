package io.joshuasalcedo.webview4j.sampler.pages;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Element;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The Sampler's pages, each one a component (or a few that go together) working, the Java that makes it, and the
 * component classes it shows. Each category of components adds its pages from a {@link Demos} of its own.
 */
public final class Gallery {

    /** One page: where it sits in the side bar, its name, the demo, its Java, and the classes it shows. */
    public record Page(String section, String name, Supplier<Component<?>> demo, String java, List<Class<?>> covers) {}

    /** Every category's demos, in the side bar's order. A new category is a new {@link Demos}, added here. */
    static final List<Supplier<Demos>> ALL = List.of(
            ActionsAndInputDemos::new,
            ContentDemos::new,
            LayoutDemos::new,
            FilesAndEditorsDemos::new,
            ValueObjectDemos::new,
            CollectionDemos::new,
            PanesDemos::new,
            AuthenticationDemos::new,
            XlsxDemos::new,
            PdfDemos::new,
            DocxDemos::new,
            ChatDemos::new,
            WindowDemos::new,
            MediaDemos::new,
            ControlsDemos::new,
            ChartDemos::new,
            OverlayDemos::new);

    private final Map<String, Page> pages = new LinkedHashMap<>();

    public Gallery() {
        for (Supplier<Demos> demos : ALL) demos.get().addTo(this);
    }

    /**
     * Adds a page. {@code covers} are the component classes it shows: the Sampler's test fails while a component
     * class has no page covering it.
     */
    public void page(String section, String name, Supplier<Component<?>> demo, String java, Class<?>... covers) {
        if (pages.containsKey(name)) throw new IllegalArgumentException("two gallery pages are named " + name);
        pages.put(name, new Page(section, name, demo, java, List.of(covers)));
    }

    public Collection<Page> pages() {
        return pages.values();
    }

    public Page page(String name) {
        return pages.get(name);
    }

    /** The component classes the pages show. */
    public Set<Class<?>> covered() {
        Set<Class<?>> covered = new LinkedHashSet<>();
        for (Page p : pages.values()) covered.addAll(p.covers());
        return covered;
    }

    /** The component classes no page shows yet. */
    public List<Class<?>> missing() {
        Set<Class<?>> covered = covered();
        return components().stream().filter(c -> !covered.contains(c)).toList();
    }

    /**
     * Every component class webview-4j has: the public, concrete {@link Component}s in {@code ui.components} and the
     * packages under it ({@link Element}, the generic one, aside), read from the core jar or its classes folder.
     */
    public static List<Class<?>> components() {
        String root = "io/joshuasalcedo/webview4j/ui/components";
        List<Class<?>> found = new ArrayList<>();
        try {
            Path base = Path.of(Element.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(base)) {
                collect(base, base.resolve(root), found);
            } else {
                try (FileSystem jar = FileSystems.newFileSystem(base)) {
                    collect(jar.getPath("/"), jar.getPath("/" + root), found);
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("the component classes could not be listed", e);
        }
        found.sort(Comparator.comparing(Class::getName));
        return found;

    }

    private static void collect(Path base, Path dir, List<Class<?>> found) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".class") && !f.getFileName().toString().contains("$")).toList()) {
                String name = base.relativize(file).toString().replace('\\', '/').replace('/', '.').replaceAll("\\.class$", "");
                if (name.startsWith(".")) name = name.substring(1);
                try {
                    Class<?> c = Class.forName(name, false, Gallery.class.getClassLoader());
                    if (Component.class.isAssignableFrom(c) && Modifier.isPublic(c.getModifiers())
                            && !Modifier.isAbstract(c.getModifiers()) && c != Element.class) found.add(c);
                } catch (ClassNotFoundException | LinkageError e) {
                    // not one to show
                }
            }
        }
    }

    /** Where a class sits in the side bar when it has no page yet: its package's last part. */
    static String sectionOf(Class<?> c) {
        String p = c.getPackageName();
        return switch (p.substring(p.lastIndexOf('.') + 1)) {
            case "components" -> "Base";
            case "valueobject" -> "Value objects";
            default -> "Use case: " + p.substring(p.lastIndexOf('.') + 1);
        };
    }
}
