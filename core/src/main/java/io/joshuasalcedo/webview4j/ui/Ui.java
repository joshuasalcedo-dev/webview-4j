package io.joshuasalcedo.webview4j.ui;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.ui.components.*;
import io.joshuasalcedo.webview4j.ui.components.layout.Box;
import io.joshuasalcedo.webview4j.ui.components.layout.Spacer;
import io.joshuasalcedo.webview4j.ui.components.layout.Split;
import io.joshuasalcedo.webview4j.ui.components.usecase.mdi.Mdi;
import io.joshuasalcedo.webview4j.ui.components.usecase.mdi.MdiWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

/**
 * Screens built in Java, from {@code @webview4j/ui}'s components: no frontend code, no bundler. A {@link Screen}
 * returns what to show from {@code render()}; the user's actions run Java code ({@code onClick}, {@code onChange}),
 * and the screen shows again with what changed. Each component is a class of
 * {@link io.joshuasalcedo.webview4j.ui.components}; these methods are the short way to make them.
 * <pre>{@code
 * import static io.joshuasalcedo.webview4j.ui.Ui.*;
 * import io.joshuasalcedo.webview4j.ui.components.*;
 *
 * class Counter implements Screen {
 *     int count;
 *     public Component<?> render() {
 *         return column(
 *                 title("Clicked " + count + " times"),
 *                 button("Click me").accent().onClick(() -> count++)).padding(24).gap(12);
 *     }
 * }
 *
 * App.run(app -> Ui.open(app, new Counter(), options -> options.title("Counter").size(400, 300)));
 * }</pre>
 *
 * <p>The components look like Windows 11 or macOS ({@link Frame#platform} picks, or the system it runs on), light or
 * dark as the system is set.
 */
public final class Ui {

    private Ui() {}

    // ---- opening ----

    /** Opens a window showing {@code screen}. */
    public static CompletableFuture<UiWindow> open(App app, Screen screen) {
        return open(app, screen, UnaryOperator.identity());
    }

    /** Opens a window showing {@code screen}; {@code options} sets its title, size and the rest. */
    public static CompletableFuture<UiWindow> open(App app, Screen screen, UnaryOperator<WindowOptions> options) {
        return UiWindow.open(app, screen, options);
    }

    /**
     * How an exception that is not a {@link UiException} is said to the person when a handler throws it: for a
     * library's own exceptions whose messages are written for people.
     * <pre>{@code
     * Ui.explain(ValueObjectException.class, e -> new UiException("Check what you typed", e.getMessage(), UiException.Level.WARNING));
     * }</pre>
     */
    public static <E extends Throwable> void explain(Class<E> type, java.util.function.Function<? super E, UiException> how) {
        UiWindow.explain(Objects.requireNonNull(type, "type"), Objects.requireNonNull(how, "how"));
    }

    // ---- the window ----

    /** The whole window: the body, with {@link Frame#titleBar}, {@link Frame#toolbar} and {@link Frame#statusBar}. */
    public static Frame frame(Component<?> body) { return new Frame(body); }

    /** The window's own title bar, for a window without the system's frame. */
    public static TitleBar titleBar(String title) { return new TitleBar(title); }

    /** A bar of commands under the title bar; {@code title} is the page's name. */
    public static Toolbar toolbar(String title, Component<?>... children) { return new Toolbar(title).with(children); }

    public static ToolbarButton toolbarButton(String label) { return new ToolbarButton(label); }

    public static ToolbarSeparator toolbarSeparator() { return new ToolbarSeparator(); }

    /** A flexible space in a toolbar or status bar: what follows goes to the far end. */
    public static Spacer spacer() { return new Spacer(); }

    public static StatusBar statusBar(Component<?>... items) { return new StatusBar(items); }

    public static StatusItem statusItem(String text) { return new StatusItem(text); }

    public static StatusDot statusDot(DotState state) { return new StatusDot(state); }

    public static Sidebar sidebar(Component<?>... items) { return new Sidebar(items); }

    public static SidebarSection sidebarSection(String title, Component<?>... items) { return new SidebarSection(title, items); }

    public static SidebarItem sidebarItem(String label) { return new SidebarItem(label); }

    /** A side bar on the left and the {@code content} filling the rest. */
    public static Split split(Sidebar sidebar, Component<?> content) { return new Split(sidebar, content); }

    /** Where the page is, as a trail of the places above it. */
    public static Breadcrumb breadcrumb() { return new Breadcrumb(); }

    public static Tabs tabs(String label) { return new Tabs(label); }

    // ---- layout ----

    /** Its children one under the other. */
    /** Children side by side (JavaFX's HBox), {@code spacing} CSS pixels apart. */
    public static io.joshuasalcedo.webview4j.ui.components.layout.HBox hbox(double spacing, Component<?>... children) {
        return new io.joshuasalcedo.webview4j.ui.components.layout.HBox(spacing, children);
    }

    public static io.joshuasalcedo.webview4j.ui.components.layout.HBox hbox(Component<?>... children) {
        return new io.joshuasalcedo.webview4j.ui.components.layout.HBox(children);
    }

    /** Children one under the other (JavaFX's VBox), {@code spacing} CSS pixels apart. */
    public static io.joshuasalcedo.webview4j.ui.components.layout.VBox vbox(double spacing, Component<?>... children) {
        return new io.joshuasalcedo.webview4j.ui.components.layout.VBox(spacing, children);
    }

    public static io.joshuasalcedo.webview4j.ui.components.layout.VBox vbox(Component<?>... children) {
        return new io.joshuasalcedo.webview4j.ui.components.layout.VBox(children);
    }

    /** Children in rows and columns (JavaFX's GridPane). */
    public static io.joshuasalcedo.webview4j.ui.components.layout.GridPane gridPane() {
        return new io.joshuasalcedo.webview4j.ui.components.layout.GridPane();
    }

    /** A center with what goes around it (JavaFX's BorderPane). */
    public static io.joshuasalcedo.webview4j.ui.components.layout.BorderPane borderPane(Component<?> center) {
        return new io.joshuasalcedo.webview4j.ui.components.layout.BorderPane(center);
    }

    /** Children on top of each other (JavaFX's StackPane). */
    public static io.joshuasalcedo.webview4j.ui.components.layout.StackPane stackPane(Component<?>... children) {
        return new io.joshuasalcedo.webview4j.ui.components.layout.StackPane(children);
    }

    /** Panes with dividers to drag (JavaFX's SplitPane). */
    public static io.joshuasalcedo.webview4j.ui.components.layout.SplitPane splitPane(Component<?>... panes) {
        return new io.joshuasalcedo.webview4j.ui.components.layout.SplitPane(panes);
    }

    public static Box column(Component<?>... children) { return new Box("column", children); }

    public static Box column(List<? extends Component<?>> children) { return new Box("column").with(children); }

    /** Its children side by side. */
    public static Box row(Component<?>... children) { return new Box("row", children); }

    public static Box row(List<? extends Component<?>> children) { return new Box("row").with(children); }

    /** A card of related controls under a heading. */
    public static Surface surface(String title, Component<?>... children) { return new Surface(title, children); }

    /** A panel at the side with the details of what is chosen. */
    public static Inspector inspector(String title, Component<?>... children) { return new Inspector(title, children); }

    public static InspectorSection inspectorSection(String title, Component<?>... children) { return new InspectorSection(title, children); }

    /** A dialog over the window, made of components. */
    public static Dialog dialog(String title) { return new Dialog(title); }

    /** Windows inside the app's window (MDI). */
    public static Mdi mdi(String label, MdiWindow... windows) { return new Mdi(label, windows); }

    public static MdiWindow mdiWindow(String id, String title, Component<?>... children) { return new MdiWindow(id, title, children); }

    /** A scrolling area that asks for more before its end ({@link InfiniteScroll#onLoadMore}). */
    public static InfiniteScroll infiniteScroll(Component<?>... children) { return new InfiniteScroll(children); }

    /** {@code content} that is swiped aside to reveal actions. */
    public static Swipeable swipeable(Component<?> content) { return new Swipeable(content); }

    // ---- text ----

    public static Text text(String text) { return new Text(text); }

    /** A title: {@code text(...).variant(TextVariant.TITLE)}. */
    public static Text title(String text) { return new Text(text).variant(TextVariant.TITLE); }

    public static Label label(String text) { return new Label(text); }

    public static InlineCode inlineCode(String code) { return new InlineCode(code); }

    /** "Mod+K": ⌘K on a Mac, Ctrl+K on Windows. */
    public static Shortcut shortcut(String keys) { return new Shortcut(keys); }

    public static Link link(String text, String url) { return new Link(text, url); }

    /** A coloured, copyable snippet of code. */
    public static CodeBlock code(String code, String language) { return new CodeBlock(code, language); }

    // ---- controls ----

    public static Button button(String label) { return new Button(label); }

    public static TextField textField(String label) { return new TextField(label); }

    public static PasswordField passwordField(String label) { return new PasswordField(label); }

    public static SearchField searchField(String label) { return new SearchField(label); }

    public static TextArea textArea(String label) { return new TextArea(label); }

    public static NumberField numberField(String label) { return new NumberField(label); }

    public static TokenField tokenField(String label) { return new TokenField(label); }

    /** An amount of money in {@code currency} (an ISO code), in its smallest unit. */
    public static MoneyField moneyField(String label, String currency) { return new MoneyField(label, currency); }

    /** A quantity in {@code unit}. */
    public static QuantityField quantityField(String label, Unit unit) { return new QuantityField(label, unit); }

    public static Checkbox checkbox(String label) { return new Checkbox(label); }

    /** An on/off switch ({@code switch} is a Java word). */
    public static Toggle toggle(String label) { return new Toggle(label); }

    /** A dropdown of {@code options}: each a value and what is shown for it, in their order. */
    public static Select select(String label, Map<String, String> options) { return new Select(label, options); }

    /** One of a few choices, side by side. */
    public static Segmented segmented(String label, Map<String, String> options) { return new Segmented(label, options); }

    /** Formatted text; its value is HTML. */
    public static RichTextEditor richText(String label) { return new RichTextEditor(label); }

    /** VS Code's editor. */
    public static CodeEditor codeEditor(String label) { return new CodeEditor(label); }

    /** A chat's message box. */
    public static Composer composer(String label) { return new Composer(label); }

    // ---- progress ----

    /** {@code value} from 0 to 100; {@link ProgressBar#indeterminate} when it is not known. */
    public static ProgressBar progress(String label, double value) { return new ProgressBar(label, value); }

    public static Spinner spinner(String label) { return new Spinner(label); }

    // ---- content ----

    public static Avatar avatar(String name) { return new Avatar(name); }

    public static Pill pill(String text) { return new Pill(text); }

    public static PillGroup pillGroup(Pill... pills) { return new PillGroup(pills); }

    /** An amount in the currency's smallest unit (49900 is ₱499.00) and its ISO code ("PHP"). */
    public static Money money(long amount, String currency) { return new Money(amount, currency); }

    public static Quantity quantity(double value, Unit unit) { return new Quantity(value, unit); }

    public static ListView list(String label, Component<?>... items) { return new ListView(label, items); }

    public static ListView list(String label, List<? extends Component<?>> items) { return new ListView(label).with(items); }

    public static ListItem listItem(String title) { return new ListItem(title); }

    public static LogView logView(String label, List<LogView.LogLine> lines) { return new LogView(label, lines); }

    public static MessageBubble messageBubble(String text, boolean mine) { return new MessageBubble(text, mine); }

    /** Who is typing: the three dots, and their names. */
    public static TypingIndicator typing(String... names) { return new TypingIndicator(names); }

    // ---- files ----

    public static FileIcon fileIcon(String name) { return new FileIcon(name); }

    public static FileChip fileChip(String name) { return new FileChip(name); }

    public static FileTree fileTree(String label, List<FileNode> items) { return new FileTree(label, items); }

    public static FileList fileList(String label, List<FileNode> items) { return new FileList(label, items); }

    public static FileGrid fileGrid(String label, List<FileNode> items) { return new FileGrid(label, items); }

    public static FileExplorer fileExplorer(String label, List<FileNode> items) { return new FileExplorer(label, items); }

    // ---- pages ----

    /**
     * Pages of a long list: Previous, the page numbers (the first, the last and those around {@code page}), Next.
     * {@code page} counts from 1. With a {@link Router}, keep the page in the query:
     * <pre>{@code
     * int n = page.queryInt("page", 1);
     * pagination(n, pages, next -> page.navigate(page.withQuery("page", next)))
     * }</pre>
     */
    public static Box pagination(int page, int pages, IntConsumer go) {
        Objects.requireNonNull(go, "go");
        int last = Math.max(1, pages);
        int current = Math.max(1, Math.min(page, last));
        List<Component<?>> parts = new ArrayList<>();
        parts.add(button("Previous").subtle().disabled(current <= 1).onClick(() -> go.accept(current - 1)).key("previous"));
        boolean gap = false;
        for (int n = 1; n <= last; n++) {
            if (!(n == 1 || n == last || Math.abs(n - current) <= 2)) {
                if (!gap) parts.add(text("…").tone(Tone.SECONDARY).key("gap" + n));
                gap = true;
                continue;
            }
            gap = false;
            int target = n;
            Button b = button(String.valueOf(n)).key("page" + n).onClick(() -> go.accept(target));
            parts.add(n == current ? b.accent() : b.subtle());
        }
        parts.add(button("Next").subtle().disabled(current >= last).onClick(() -> go.accept(current + 1)).key("next"));
        return row(parts).gap(4).attribute("role", "navigation");
    }
}
