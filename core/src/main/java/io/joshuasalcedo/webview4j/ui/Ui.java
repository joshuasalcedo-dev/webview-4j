package io.joshuasalcedo.webview4j.ui;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.WindowOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Screens built in Java, from {@code @webview4j/ui}'s components: no frontend code, no bundler. A {@link Screen}
 * returns what to show from {@code render()}; the user's actions run Java code ({@code onClick}, {@code onChange}),
 * and the screen shows again with what changed.
 * <pre>{@code
 * import static io.joshuasalcedo.webview4j.ui.Ui.*;
 *
 * class Counter implements Screen {
 *     int count;
 *     public Component<?> render() {
 *         return column(
 *                 text("Clicked " + count + " times").variant(TextVariant.TITLE),
 *                 button("Click me").accent().onClick(() -> count++)).padding(24).gap(12);
 *     }
 * }
 *
 * App.run(app -> Ui.open(app, new Counter(), options -> options.title("Counter").size(400, 300)));
 * }</pre>
 *
 * <p>The components are the ones a page with {@code @webview4j/ui} has: they look like Windows 11 or macOS
 * ({@link #frame} picks, or the system it runs on), light or dark as the system is set.
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

    // ---- the window ----

    /**
     * The whole window: an optional title bar and toolbar at the top, the body filling the middle, an optional
     * status bar at the bottom. {@code platform} and {@code theme} set the look; by default the system's.
     */
    public static Frame frame(Component<?> body) {
        return new Frame().body(body);
    }

    /** The window's own title bar, for a window without the system's frame (WindowOptions.decorations(false)). */
    public static TitleBar titleBar(String title) {
        return new TitleBar().prop("title", title);
    }

    /** A bar of commands under the title bar; {@code title} is the page's name (shown as the system shows one). */
    public static Toolbar toolbar(String title, Component<?>... children) {
        return new Toolbar().prop("title", title).add(children);
    }

    public static ToolbarButton toolbarButton(String label) {
        return new ToolbarButton().prop("label", label);
    }

    /** A flexible space in a toolbar or status bar: what follows goes to the far end. */
    public static Spacer spacer() {
        return new Spacer();
    }

    public static StatusBar statusBar(Component<?>... items) {
        return new StatusBar().add(items);
    }

    /** One piece of the status bar; {@link StatusItem#dot} puts a coloured light before it. */
    public static StatusItem statusItem(String text) {
        return new StatusItem().prop("text", text);
    }

    /** A side bar of places (sections of the app) beside the {@code content}. */
    public static Sidebar sidebar(Component<?>... items) {
        return new Sidebar().add(items);
    }

    public static SidebarItem sidebarItem(String label) {
        return new SidebarItem().prop("label", label);
    }

    /** A group of side bar items under a heading (or none). */
    public static SidebarSection sidebarSection(String title, Component<?>... items) {
        return new SidebarSection().prop("title", title).add(items);
    }

    /** A sidebar on the left and the {@code content} filling the rest. */
    public static Split split(Sidebar sidebar, Component<?> content) {
        return new Split().add(sidebar, content);
    }

    // ---- layout ----

    /** Its children one under the other. */
    public static Box column(Component<?>... children) {
        return new Box("column").add(children);
    }

    public static Box column(List<? extends Component<?>> children) {
        return new Box("column").add(children);
    }

    /** Its children side by side. */
    public static Box row(Component<?>... children) {
        return new Box("row").add(children);
    }

    public static Box row(List<? extends Component<?>> children) {
        return new Box("row").add(children);
    }

    /** A card of related controls under a heading: Windows' card, macOS' group box. */
    public static Surface surface(String title, Component<?>... children) {
        return new Surface().prop("title", title).add(children);
    }

    // ---- text ----

    public static Text text(String text) {
        return new Text().prop("text", text);
    }

    /** A title: {@code text(...).variant(TextVariant.TITLE)}. */
    public static Text title(String text) {
        return text(text).variant(TextVariant.TITLE);
    }

    public static Link link(String text, String url) {
        return new Link().prop("text", text).prop("href", url);
    }

    // ---- controls ----

    public static Button button(String label) {
        return new Button().prop("label", label);
    }

    public static TextField textField(String label) {
        return new TextField().prop("label", label);
    }

    public static TextArea textArea(String label) {
        return new TextArea().prop("label", label);
    }

    public static Checkbox checkbox(String label) {
        return new Checkbox().prop("label", label);
    }

    /** An on/off switch ({@code switch} is a Java word). */
    public static Toggle toggle(String label) {
        return new Toggle().prop("label", label);
    }

    /** A dropdown of {@code options}: each a value and what is shown for it. */
    public static Select select(String label, Map<String, String> options) {
        return new Select().prop("label", label).prop("options", choices(options));
    }

    /** One of a few choices, side by side. */
    public static Segmented segmented(String label, Map<String, String> options) {
        return new Segmented().prop("label", label).prop("options", choices(options));
    }

    /** {@code value} from 0 to 100; {@link ProgressBar#indeterminate} when it is not known. */
    public static ProgressBar progress(String label, double value) {
        return new ProgressBar().prop("label", label).prop("value", value);
    }

    public static Spinner spinner(String label) {
        return new Spinner().prop("label", label);
    }

    // ---- content ----

    public static Avatar avatar(String name) {
        return new Avatar().prop("name", name);
    }

    public static Pill pill(String text) {
        return new Pill().prop("text", text);
    }

    /** An amount in the currency's smallest unit (49900 is ₱499.00) and its ISO code ("PHP"). */
    public static Money money(long amount, String currency) {
        return new Money().prop("amount", amount).prop("currency", currency);
    }

    public static ListView list(String label, Component<?>... items) {
        return new ListView().prop("label", label).add(items);
    }

    public static ListView list(String label, List<? extends Component<?>> items) {
        return new ListView().prop("label", label).add(items);
    }

    public static ListItem listItem(String title) {
        return new ListItem().prop("title", title);
    }

    /** A coloured, copyable snippet of code. */
    public static CodeBlock code(String code, String language) {
        return new CodeBlock().prop("code", code).prop("language", language);
    }

    private static List<Map<String, String>> choices(Map<String, String> options) {
        List<Map<String, String>> list = new ArrayList<>();
        options.forEach((value, label) -> list.add(Map.of("value", value, "label", label)));
        return list;
    }

    // ---- the components ----

    public enum Platform { AUTO, WINDOWS, MAC }

    public enum Theme { SYSTEM, LIGHT, DARK }

    public enum TextVariant { DISPLAY, LARGE_TITLE, TITLE, SUBTITLE, HEADLINE, BODY, CAPTION, FOOTNOTE }

    public enum Tone { DEFAULT, SECONDARY, TERTIARY, ACCENT, SUCCESS, WARNING, ERROR }

    public enum DotState { ONLINE, CONNECTING, OFFLINE, ERROR }

    public enum PillColor { GREY, RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, PURPLE, PINK }

    static String camel(Enum<?> e) {
        String[] words = e.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder s = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) s.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        return s.toString();
    }

    /** What every component has: no settings of its own. */
    public static final class Plain extends Component<Plain> {
        Plain(String type) { super(type); }
    }

    public static final class Frame extends Component<Frame> {
        Frame() { super("frame"); }
        public Frame platform(Platform platform) { return prop("platform", camel(platform)); }
        public Frame theme(Theme theme) { return prop("theme", camel(theme)); }
        /** Another accent colour than the system's blue, as CSS ("#7c3aed"). */
        public Frame accent(String color) { return prop("accent", color); }
        public Frame titleBar(TitleBar titleBar) { return prop("hasTitleBar", true).slot("titleBar", titleBar); }
        public Frame toolbar(Toolbar toolbar) { return slot("toolbar", toolbar); }
        public Frame statusBar(StatusBar statusBar) { return slot("statusBar", statusBar); }
        Frame body(Component<?> body) { return slot("body", body); }
        private Frame slot(String name, Component<?> component) {
            children.removeIf(c -> name.equals(c.props.get("slot")));
            if (component != null) {
                component.props.put("slot", name);
                children.add(component);
            }
            return this;
        }
    }

    public static final class TitleBar extends Component<TitleBar> {
        TitleBar() { super("titleBar"); }
        /** What goes in the bar's free space: a search field, buttons. */
        public TitleBar with(Component<?>... children) { return add(children); }
    }

    public static final class Toolbar extends Component<Toolbar> {
        Toolbar() { super("toolbar"); }
        public Toolbar with(Component<?>... children) { return add(children); }
    }

    public static final class ToolbarButton extends Component<ToolbarButton> {
        ToolbarButton() { super("toolbarButton"); }
        public ToolbarButton pressed(boolean pressed) { return prop("pressed", pressed); }
        public ToolbarButton disabled(boolean disabled) { return prop("disabled", disabled); }
        public ToolbarButton onClick(Runnable action) { return on("onClick", v -> action.run()); }
    }

    public static final class Spacer extends Component<Spacer> {
        Spacer() { super("spacer"); }
    }

    public static final class StatusBar extends Component<StatusBar> {
        StatusBar() { super("statusBar"); }
    }

    public static final class StatusItem extends Component<StatusItem> {
        StatusItem() { super("statusItem"); }
        public StatusItem dot(DotState state) { return prop("dot", camel(state)); }
    }

    public static final class Sidebar extends Component<Sidebar> {
        Sidebar() { super("sidebar"); }
        /** Items at the bottom, such as Settings. */
        public Sidebar footer(Component<?>... items) {
            Plain footer = new Plain("sidebarFooter");
            footer.add(items);
            return add(footer);
        }
    }

    public static final class SidebarSection extends Component<SidebarSection> {
        SidebarSection() { super("sidebarSection"); }
    }

    public static final class SidebarItem extends Component<SidebarItem> {
        SidebarItem() { super("sidebarItem"); }
        public SidebarItem selected(boolean selected) { return prop("selected", selected); }
        /** A count after the label, such as unread messages. */
        public SidebarItem badge(long badge) { return prop("badge", badge); }
        public SidebarItem onSelect(Runnable action) { return on("onSelect", v -> action.run()); }
    }

    public static final class Split extends Component<Split> {
        Split() { super("split"); }
    }

    public static final class Box extends Component<Box> {
        Box(String direction) {
            super("box");
            prop("direction", direction);
        }
        /** Space between the children, CSS pixels. */
        public Box gap(double gap) { return prop("gap", gap); }
        /** Space inside the edges, CSS pixels. */
        public Box padding(double padding) { return prop("padding", padding); }
        /** Children that do not fit go on to the next line (a row) or column. */
        public Box wrap() { return prop("wrap", true); }
        /** Lines the children up across: "start", "center", "end" or "stretch". */
        public Box align(String align) { return prop("align", align); }
        /** Scrolls when its children do not fit. */
        public Box scroll() { return prop("scroll", true); }
        public Box with(Component<?>... children) { return add(children); }
    }

    public static final class Surface extends Component<Surface> {
        Surface() { super("surface"); }
        public Surface with(Component<?>... children) { return add(children); }
    }

    public static final class Text extends Component<Text> {
        Text() { super("text"); }
        public Text variant(TextVariant variant) { return prop("variant", camel(variant)); }
        public Text tone(Tone tone) { return prop("tone", camel(tone)); }
        public Text strong() { return prop("strong", true); }
        public Text mono() { return prop("mono", true); }
        /** Cut off after this many lines, with the whole text as a tooltip. */
        public Text lines(int lines) { return prop("lines", lines); }
    }

    public static final class Link extends Component<Link> {
        Link() { super("link"); }
        /** Runs Java instead of opening the address. */
        public Link onClick(Runnable action) { return on("onClick", v -> action.run()); }
    }

    public static final class Button extends Component<Button> {
        Button() { super("button"); }
        /** The window's main action: one per window. */
        public Button accent() { return prop("variant", "accent"); }
        /** No fill until hovered: toolbars, icon buttons. */
        public Button subtle() { return prop("variant", "subtle"); }
        public Button disabled(boolean disabled) { return prop("disabled", disabled); }
        public Button onClick(Runnable action) { return on("onClick", v -> action.run()); }
    }

    public static final class TextField extends Component<TextField> {
        TextField() { super("textField"); }
        public TextField value(String value) { return prop("value", value == null ? "" : value); }
        public TextField placeholder(String placeholder) { return prop("placeholder", placeholder); }
        /** One line under it about what to type. */
        public TextField hint(String hint) { return prop("hint", hint); }
        /** What is wrong with what was typed: shown instead of the hint, and the field is marked. */
        public TextField error(String error) { return prop("error", error); }
        public TextField password() { return prop("password", true); }
        public TextField disabled(boolean disabled) { return prop("disabled", disabled); }
        /** Each change as the user types: the whole text. */
        public TextField onChange(Consumer<String> action) { return on("onChange", v -> action.accept(String.valueOf(v))); }
        /** Enter pressed in the field. */
        public TextField onSubmit(Consumer<String> action) { return on("onSubmit", v -> action.accept(String.valueOf(v))); }
    }

    public static final class TextArea extends Component<TextArea> {
        TextArea() { super("textArea"); }
        public TextArea value(String value) { return prop("value", value == null ? "" : value); }
        public TextArea placeholder(String placeholder) { return prop("placeholder", placeholder); }
        public TextArea rows(int rows) { return prop("rows", rows); }
        public TextArea onChange(Consumer<String> action) { return on("onChange", v -> action.accept(String.valueOf(v))); }
    }

    public static final class Checkbox extends Component<Checkbox> {
        Checkbox() { super("checkbox"); }
        public Checkbox checked(boolean checked) { return prop("checked", checked); }
        public Checkbox disabled(boolean disabled) { return prop("disabled", disabled); }
        public Checkbox onChange(Consumer<Boolean> action) { return on("onChange", v -> action.accept(Boolean.TRUE.equals(v))); }
    }

    public static final class Toggle extends Component<Toggle> {
        Toggle() { super("switch"); }
        public Toggle checked(boolean checked) { return prop("checked", checked); }
        public Toggle disabled(boolean disabled) { return prop("disabled", disabled); }
        public Toggle onChange(Consumer<Boolean> action) { return on("onChange", v -> action.accept(Boolean.TRUE.equals(v))); }
    }

    public static final class Select extends Component<Select> {
        Select() { super("select"); }
        public Select value(String value) { return prop("value", value); }
        public Select disabled(boolean disabled) { return prop("disabled", disabled); }
        public Select onChange(Consumer<String> action) { return on("onChange", v -> action.accept(String.valueOf(v))); }
    }

    public static final class Segmented extends Component<Segmented> {
        Segmented() { super("segmented"); }
        public Segmented value(String value) { return prop("value", value); }
        public Segmented onChange(Consumer<String> action) { return on("onChange", v -> action.accept(String.valueOf(v))); }
    }

    public static final class ProgressBar extends Component<ProgressBar> {
        ProgressBar() { super("progress"); }
        /** Runs back and forth: how long it takes is not known. */
        public ProgressBar indeterminate() { return prop("value", null); }
    }

    public static final class Spinner extends Component<Spinner> {
        Spinner() { super("spinner"); }
        public Spinner size(double size) { return prop("size", size); }
    }

    public static final class Avatar extends Component<Avatar> {
        Avatar() { super("avatar"); }
        /** A picture's address; without one, the initials. */
        public Avatar src(String src) { return prop("src", src); }
        public Avatar size(double size) { return prop("size", size); }
    }

    public static final class Pill extends Component<Pill> {
        Pill() { super("pill"); }
        public Pill color(PillColor color) { return prop("color", camel(color)); }
        public Pill filled() { return prop("variant", "filled"); }
        public Pill outline() { return prop("variant", "outline"); }
    }

    public static final class Money extends Component<Money> {
        Money() { super("money"); }
        /** Below zero in brackets, as statements write it: (₱5.00). */
        public Money accounting() { return prop("negative", "accounting"); }
        /** A plus on amounts above zero too: +₱5.00. */
        public Money signed() { return prop("signed", true); }
    }

    public static final class ListView extends Component<ListView> {
        ListView() { super("list"); }
        public ListView with(Component<?>... items) { return add(items); }
    }

    public static final class ListItem extends Component<ListItem> {
        ListItem() { super("listItem"); }
        /** A second, quieter line. */
        public ListItem subtitle(String subtitle) { return prop("subtitle", subtitle); }
        /** After the text: a time, a count. */
        public ListItem trailing(String trailing) { return prop("trailing", trailing); }
        /** Before the text: an avatar, usually. */
        public ListItem leading(Component<?> leading) {
            leading.props.put("slot", "leading");
            return add(leading);
        }
        public ListItem selected(boolean selected) { return prop("selected", selected); }
        public ListItem onSelect(Runnable action) { return on("onSelect", v -> action.run()); }
        /** A right-click menu entry; call again for each. */
        public ListItem menu(String label, Runnable action) {
            Plain entry = new Plain("menuItem").prop("label", label).on("onSelect", v -> action.run());
            entry.props.put("slot", "menu");
            return add(entry);
        }
    }

    public static final class CodeBlock extends Component<CodeBlock> {
        CodeBlock() { super("code"); }
    }

    /** For a component the renderer knows by another name; its props as JSON values. */
    static Plain node(String type, Map<String, Object> props) {
        Plain plain = new Plain(type);
        plain.props.putAll(new LinkedHashMap<>(Objects.requireNonNull(props, "props")));
        return plain;
    }
}
