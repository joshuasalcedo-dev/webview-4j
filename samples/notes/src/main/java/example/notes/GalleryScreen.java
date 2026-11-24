package example.notes;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import example.notes.gallery.Demos;
import example.notes.gallery.Gallery;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.components.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every webview-4j component, working, written only in Java: a side bar of them by category, and for each one the
 * component itself and the Java that makes it. The pages are in {@code example.notes.gallery}, a file per category;
 * a component with no page yet is listed under "No demo yet". Open it with {@code Ui.open(app, new GalleryScreen())}.
 */
public final class GalleryScreen implements Screen {

    private final Gallery gallery = new Gallery();
    private final List<Class<?>> missing = gallery.missing();
    private String selected = gallery.pages().iterator().next().name();
    private Platform platform = Platform.AUTO;
    private ColorScheme theme = ColorScheme.SYSTEM;

    public Gallery gallery() {
        return gallery;
    }

    /** Shows a page by its name (or a missing component by its simple name). */
    public void show(String name) {
        selected = name;
    }

    @Override
    public Component<?> render() {
        // The side bar: one section per category, then what has no demo yet.
        Map<String, List<Component<?>>> sections = new LinkedHashMap<>();
        for (Gallery.Page p : gallery.pages()) {
            sections.computeIfAbsent(p.section(), s -> new ArrayList<>())
                    .add(sidebarItem(p.name()).key(p.name()).selected(p.name().equals(selected)).onSelect(() -> selected = p.name()));
        }
        for (Class<?> c : missing) {
            sections.computeIfAbsent("No demo yet", s -> new ArrayList<>())
                    .add(sidebarItem(c.getSimpleName()).key("missing " + c.getName()).selected(c.getSimpleName().equals(selected))
                            .onSelect(() -> selected = c.getSimpleName()));
        }
        Sidebar sidebar = sidebar(sections.entrySet().stream()
                .map(s -> sidebarSection(s.getKey(), s.getValue().toArray(Component<?>[]::new)))
                .toArray(Component<?>[]::new));

        Gallery.Page page = gallery.page(selected);
        Component<?> content = page != null
                ? column(
                        text(page.name()).variant(TextVariant.LARGE_TITLE),
                        surface("Try it", page.demo().get()),
                        surface("The Java", code(page.java(), "java"))).padding(24).gap(16).scroll()
                : column(
                        text(selected).variant(TextVariant.LARGE_TITLE),
                        text("No demo yet: add a page that covers it in example.notes.gallery.").tone(Tone.SECONDARY)).padding(24).gap(16);

        return frame(split(sidebar, content))
                .platform(platform)
                .scheme(theme)
                .titleBar(titleBar("Component gallery"))
                .toolbar(toolbar(selected,
                        spacer(),
                        segmented("Look", Demos.ordered("AUTO", "This system", "WINDOWS", "Windows 11", "MAC", "macOS"))
                                .value(platform.name()).onChange(v -> platform = Platform.valueOf(v)),
                        segmented("Theme", Demos.ordered("SYSTEM", "System", "LIGHT", "Light", "DARK", "Dark"))
                                .value(theme.name()).onChange(v -> theme = ColorScheme.valueOf(v))))
                .statusBar(statusBar(
                        statusItem("Written in Java only").dot(DotState.ONLINE),
                        spacer(),
                        statusItem(gallery.pages().size() + " pages · " + gallery.covered().size() + " of "
                                + (gallery.covered().size() + missing.size()) + " components")));
    }

    /** Opens the gallery on its own: {@code ./gradlew run -Pgallery}. */
    static void open(io.joshuasalcedo.webview4j.App app) {
        Ui.open(app, new GalleryScreen(), o -> o.title("Component gallery").size(1180, 780).minSize(760, 480).decorations(false));
    }
}
