package io.joshuasalcedo.webview4j.sampler;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.sampler.pages.Demos;
import io.joshuasalcedo.webview4j.sampler.pages.Gallery;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.components.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every webview-4j component, working, written only in Java: a sidebar of them by category, and for each one the
 * component itself and the Java that makes it. The pages are in {@code io.joshuasalcedo.webview4j.sampler.pages}, a
 * file per category; a component with no page yet is listed under "No demo yet". Open it with
 * {@code Ui.open(app, new SamplerScreen())}.
 */
public final class SamplerScreen implements Screen {

    /** One entry of the sidebar: a category of pages (its section in the gallery) and its icon. */
    private record Category(String section, String icon) {}

    /** One group of the sidebar: a heading and its categories. */
    private record Group(String label, List<Category> categories) {}

    // The sidebar, written out: the order and icons are chosen here, the pages come from the gallery.
    private static final List<Group> NAVIGATION = List.of(
            new Group("Basics", List.of(
                    new Category("Actions", "cursor"),
                    new Category("Input", "form"),
                    new Category("Content", "text"),
                    new Category("Progress", "gauge"))),
            new Group("Layout", List.of(
                    new Category("Layout", "layout"),
                    new Category("Panes", "columns"),
                    new Category("Window", "window"),
                    new Category("MDI", "windows"))),
            new Group("Data", List.of(
                    new Category("Collections", "table"),
                    new Category("Value objects", "money"),
                    new Category("Files", "folder"),
                    new Category("Editors", "code"))),
            new Group("App", List.of(
                    new Category("Messaging", "chat"),
                    new Category("Logs", "terminal"),
                    new Category("Authentication", "lock"),
                    new Category("Updates", "download"))));

    private final Gallery gallery = new Gallery();
    private final List<Class<?>> missing = gallery.missing();
    private String selected = gallery.pages().iterator().next().name();
    private String search = "";
    private boolean folded;
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
        return frame(split(navigation(), page()))
                .platform(platform)
                .scheme(theme)
                .titleBar(titleBar("webview-4j Sampler"))
                .toolbar(toolbar(selected,
                        sidebarTrigger(() -> folded = !folded),
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

    // ---- the sidebar ----

    private Sidebar navigation() {
        List<Component<?>> groups = new ArrayList<>();
        if (search.isBlank()) {
            for (Group group : NAVIGATION) {
                List<Component<?>> categories = new ArrayList<>();
                for (Category category : group.categories()) categories.add(category(category));
                groups.add(sidebarGroup(group.label(), categories.toArray(Component<?>[]::new)));
            }
            // a category the list above doesn't name yet: still reachable
            List<Component<?>> more = new ArrayList<>();
            for (String section : gallery.pages().stream().map(Gallery.Page::section).distinct().toList()) {
                if (iconOf(section).equals("file")) more.add(category(new Category(section, "file")));
            }
            if (!more.isEmpty()) groups.add(sidebarGroup("More", more.toArray(Component<?>[]::new)));
            if (!missing.isEmpty()) {
                groups.add(sidebarGroup("No demo yet", missing.stream()
                        .map(c -> place(c.getSimpleName()).key("missing " + c.getName()))
                        .toArray(Component<?>[]::new)).collapsible(true).open(false));
            }
        } else {
            groups.add(sidebarGroup("Found", found().toArray(Component<?>[]::new)));
        }

        return sidebar(groups.toArray(Component<?>[]::new))
                .header(
                        sidebarMenuButton("webview-4j").description("Sampler · " + version())
                                .icon("layers").size(SidebarMenuButton.Size.LARGE).onClick(() -> {
                                    search = "";
                                    selected = gallery.pages().iterator().next().name();
                                }),
                        sidebarInput("Search components").value(search).onChange(v -> search = v))
                .footer(sidebarMenuButton(folded ? "Open the sidebar" : "Fold the sidebar")
                        .icon("columns").tooltip("Ctrl+B").onClick(() -> folded = !folded))
                .collapsed(folded)
                .onCollapsedChange(v -> folded = v)
                .label("Components");
    }

    /** A category: its icon and name, opening to its pages (open by itself when one of them is shown). */
    private SidebarMenuButton category(Category category) {
        List<SidebarMenuButton> pages = new ArrayList<>();
        for (Gallery.Page page : gallery.pages()) {
            if (page.section().equals(category.section())) pages.add(place(page.name()));
        }
        return sidebarMenuButton(category.section())
                .key("section " + category.section())
                .icon(category.icon())
                .items(pages.toArray(SidebarMenuButton[]::new));
    }

    /** One page in the sidebar. */
    private SidebarMenuButton place(String name) {
        return sidebarMenuButton(name).key(name).active(name.equals(selected)).onClick(() -> selected = name);
    }

    /** The pages whose name or category has what was typed in the search. */
    private List<Component<?>> found() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Component<?>> found = new ArrayList<>();
        for (Gallery.Page page : gallery.pages()) {
            if (page.name().toLowerCase(Locale.ROOT).contains(q) || page.section().toLowerCase(Locale.ROOT).contains(q)) {
                found.add(place(page.name()).icon(iconOf(page.section())));
            }
        }
        if (found.isEmpty()) found.add(text("Nothing called \"" + search.trim() + "\"").tone(Tone.SECONDARY));
        return found;
    }

    private static String iconOf(String section) {
        for (Group group : NAVIGATION) {
            for (Category category : group.categories()) if (category.section().equals(section)) return category.icon();
        }
        return "file";
    }

    // ---- the page ----

    private Component<?> page() {
        Gallery.Page page = gallery.page(selected);
        if (page == null) {
            return column(
                    text(selected).variant(TextVariant.LARGE_TITLE),
                    text("No demo yet: add a page that covers it in io.joshuasalcedo.webview4j.sampler.pages.").tone(Tone.SECONDARY))
                    .padding(24).gap(16);
        }
        return column(
                text(page.section()).tone(Tone.SECONDARY),
                text(page.name()).variant(TextVariant.LARGE_TITLE),
                surface("Try it", page.demo().get()),
                surface("The Java", code(page.java(), "java"))).padding(24).gap(16).scroll();
    }

    private static String version() {
        String v = SamplerScreen.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    /** Opens the gallery on its own: {@code ./gradlew run}. */
    public static java.util.concurrent.CompletableFuture<io.joshuasalcedo.webview4j.ui.UiWindow> open(io.joshuasalcedo.webview4j.App app) {
        return Ui.open(app, new SamplerScreen(), o -> o.title("webview-4j Sampler").size(1180, 780).minSize(760, 480)
                .decorations(false).remember("sampler"));
    }
}
