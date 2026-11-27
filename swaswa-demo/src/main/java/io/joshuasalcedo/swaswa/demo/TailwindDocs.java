package io.joshuasalcedo.swaswa.demo;

import io.joshuasalcedo.swaswa.app.SwaApplication;
import io.joshuasalcedo.swaswa.css.CssClasses;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Code;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.H1;
import io.joshuasalcedo.swaswa.html.H2;
import io.joshuasalcedo.swaswa.html.H3;
import io.joshuasalcedo.swaswa.html.Input;
import io.joshuasalcedo.swaswa.html.Nav;
import io.joshuasalcedo.swaswa.html.P;
import io.joshuasalcedo.swaswa.html.Table;
import io.joshuasalcedo.swaswa.html.Td;
import io.joshuasalcedo.swaswa.html.Th;
import io.joshuasalcedo.swaswa.html.Tr;
import io.joshuasalcedo.swaswa.tailwind.Basic;
import io.joshuasalcedo.swaswa.tailwind.Tailwind;
import io.joshuasalcedo.swaswa.tailwind.Utility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every Tailwind class, as Tailwind's docs show them (tailwindcss.com/docs): a family at a time, each class with its
 * Java name ({@code Tw.Bg.BG_RED_500}), its CSS and a preview. Made from the same data as {@code Tw}. Its look is
 * {@code TailwindDocs.css}, beside it. Not deployed (swaswa-demo). {@code just tailwind-docs}.
 */
public class TailwindDocs extends SwaApplication {

    public static void main(String[] args) {
        launch(args);
    }

    /** The docs' own classes, made in TailwindDocs.css. */
    enum Styles implements CssClasses { DOCS, SIDEBAR, CATEGORY, FAMILY, FAMILY_ON, CONTENT, CLASSES, JAVA_NAME, CLASS_NAME, SHOWCASE, CSS }

    /** At most this many classes on a page: a family's first ones (the search finds the rest). */
    static final int MOST = 300;

    /** Tailwind's docs' sections, and the families in each. */
    static final Map<String, List<String>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("Layout", List.of("aspect", "columns", "box", "block", "inline", "hidden", "float", "clear", "isolate", "object",
                "overflow", "overscroll", "static", "fixed", "absolute", "relative", "sticky", "inset", "top", "right", "bottom", "left",
                "visible", "invisible", "z", "container"));
        CATEGORIES.put("Flexbox & Grid", List.of("flex", "grow", "shrink", "basis", "order", "grid", "col", "row", "gap", "justify",
                "content", "items", "self", "place"));
        CATEGORIES.put("Spacing", List.of("p", "px", "py", "pt", "pr", "pb", "pl", "ps", "pe", "m", "mx", "my", "mt", "mr", "mb",
                "ml", "ms", "me", "space"));
        CATEGORIES.put("Sizing", List.of("w", "h", "size", "min", "max"));
        CATEGORIES.put("Typography", List.of("font", "text", "tracking", "leading", "list", "decoration", "underline", "line",
                "truncate", "uppercase", "lowercase", "capitalize", "italic", "indent", "align", "whitespace", "break", "hyphens"));
        CATEGORIES.put("Backgrounds", List.of("bg", "from", "via", "to"));
        CATEGORIES.put("Borders", List.of("rounded", "border", "divide", "outline", "ring"));
        CATEGORIES.put("Effects", List.of("shadow", "opacity", "mix", "mask"));
        CATEGORIES.put("Filters", List.of("blur", "brightness", "contrast", "grayscale", "hue", "invert", "saturate", "sepia",
                "drop", "backdrop"));
        CATEGORIES.put("Transitions & Animation", List.of("transition", "duration", "ease", "delay", "animate"));
        CATEGORIES.put("Transforms", List.of("scale", "rotate", "translate", "skew", "transform", "origin", "perspective"));
        CATEGORIES.put("Interactivity", List.of("cursor", "pointer", "resize", "scroll", "snap", "touch", "select", "appearance",
                "caret", "accent", "will"));
    }

    // ---- the parts that change ----

    private final Input search = new Input();
    private final Div content = new Div();
    private final Map<String, List<String>> families = new TreeMap<>();
    private Button current;
    private final String first;

    public TailwindDocs() {
        this(System.getenv("DOCS_FAMILY"));   // the family to open on: DOCS_FAMILY=box
    }

    /** Its docs, open on {@code family} (null: bg). */
    public TailwindDocs(String family) {
        this.first = family;
    }

    @Override
    protected WindowOptions window() {
        return WindowOptions.html("").size(1280, 860);
    }

    @Override
    protected void build() {
        setTitle("Tailwind " + Tailwind.version() + " docs, from Java");
        cssClass(Basic.PAGE);
        for (String name : Tailwind.names()) families.computeIfAbsent(family(name), f -> new ArrayList<>()).add(name);

        Div docs = new Div(sidebar(), content);
        docs.cssClass(Styles.DOCS);
        content.cssClass(Styles.CONTENT);
        getMain().add(docs);

        showFamily(first != null && families.containsKey(first) ? first : "bg");
    }

    // ---- the sidebar: a search, then each section's families ----

    private Nav sidebar() {
        H1 title = new H1("Tailwind " + Tailwind.version());
        title.cssClass(Basic.SUBTITLE);

        P count = new P(Tailwind.names().size() + " classes, typed");
        count.cssClass(Basic.MUTED);

        search.setPlaceholder("Search classes…");
        search.cssClass(Basic.INPUT);
        search.addChangeListener(e -> showSearch());

        Button light = new Button("Light");
        light.cssClass(Basic.BTN);
        light.addActionListener(e -> setTheme(Theme.LIGHT));

        Button dark = new Button("Dark");
        dark.cssClass(Basic.BTN);
        dark.addActionListener(e -> setTheme(Theme.DARK));

        Div themes = new Div(light, dark);
        themes.cssClass(Basic.ROW);

        Nav sidebar = new Nav(title, count, search, themes);
        sidebar.cssClass(Styles.SIDEBAR);
        for (Map.Entry<String, List<String>> category : CATEGORIES.entrySet()) {
            H3 heading = new H3(category.getKey());
            heading.cssClass(Styles.CATEGORY);
            sidebar.add(heading);
            for (String family : category.getValue()) {
                if (families.containsKey(family)) sidebar.add(familyButton(family));
            }
        }
        return sidebar;
    }

    private Button familyButton(String family) {
        Button button = new Button(family + "-*  (" + families.get(family).size() + ")");
        button.cssClass(Styles.FAMILY);
        button.addActionListener(e -> {
            select(button);
            showFamily(family);
        });
        return button;
    }

    private void select(Button button) {
        if (current != null) current.removeCssClass(Styles.FAMILY_ON);
        button.cssClass(Styles.FAMILY_ON);
        current = button;
    }

    // ---- the content: a family's classes, or what the search found ----

    private void showFamily(String family) {
        List<String> names = families.getOrDefault(family, List.of());
        show(family + "-*", names.size() + " classes. In Java: Tw." + pascal(family) + ".…", names);

        String page = OFFICIAL.get(family);
        if (page != null) {
            Button official = new Button("Tailwind's own page ↗");
            official.cssClass(Basic.BTN);
            official.addActionListener(e -> openOfficial(page));
            content.add(official, 2);
        }
    }

    /** Tailwind's own docs page for a family, as it is (their site, in a window of its own). */
    private void openOfficial(String page) {
        app().open(WindowOptions.url("https://tailwindcss.com/docs/" + page).title("tailwindcss.com: " + page).size(1200, 900));
    }

    /** The families' pages on tailwindcss.com/docs. */
    static final Map<String, String> OFFICIAL = Map.ofEntries(
            Map.entry("bg", "background-color"), Map.entry("text", "color"), Map.entry("font", "font-weight"),
            Map.entry("p", "padding"), Map.entry("px", "padding"), Map.entry("py", "padding"), Map.entry("m", "margin"),
            Map.entry("mx", "margin"), Map.entry("my", "margin"), Map.entry("w", "width"), Map.entry("h", "height"),
            Map.entry("size", "width#setting-both-width-and-height"), Map.entry("min", "min-width"), Map.entry("max", "max-width"),
            Map.entry("rounded", "border-radius"), Map.entry("border", "border-width"), Map.entry("ring", "box-shadow#adding-a-ring"),
            Map.entry("outline", "outline-width"), Map.entry("shadow", "box-shadow"), Map.entry("opacity", "opacity"),
            Map.entry("blur", "filter-blur"), Map.entry("flex", "flex"), Map.entry("grid", "grid-template-columns"),
            Map.entry("gap", "gap"), Map.entry("justify", "justify-content"), Map.entry("items", "align-items"),
            Map.entry("tracking", "letter-spacing"), Map.entry("leading", "line-height"), Map.entry("z", "z-index"),
            Map.entry("inset", "top-right-bottom-left"), Map.entry("overflow", "overflow"), Map.entry("cursor", "cursor"),
            Map.entry("transition", "transition-property"), Map.entry("duration", "transition-duration"),
            Map.entry("animate", "animation"), Map.entry("scale", "scale"), Map.entry("rotate", "rotate"),
            Map.entry("translate", "translate"), Map.entry("aspect", "aspect-ratio"), Map.entry("columns", "columns"),
            Map.entry("object", "object-fit"), Map.entry("space", "margin#adding-space-between-children"),
            Map.entry("divide", "border-width#between-children"), Map.entry("mask", "mask-image"));

    private void showSearch() {
        String typed = search.getValue() == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        if (typed.isEmpty()) return;
        List<String> found = Tailwind.names().stream().filter(n -> n.contains(typed)).toList();
        show("“" + typed + "”", found.size() + " classes", found);
    }

    private void show(String heading, String about, List<String> names) {
        H2 title = new H2(heading);
        title.cssClass(Basic.TITLE);

        P text = new P(about + (names.size() > MOST ? " (the first " + MOST + " shown)" : ""));
        text.cssClass(Basic.MUTED);

        Table table = new Table(new Tr(new Th("Java"), new Th("Class"), new Th("Showcase"), new Th("CSS")));
        table.cssClass(Styles.CLASSES);
        for (String name : names.subList(0, Math.min(MOST, names.size()))) table.add(row(name));

        content.removeAll();
        content.add(title, text, table);
        content.scrollIntoView();
    }

    private Tr row(String name) {
        Utility utility = Utility.of(name);

        Code java = new Code(javaName(name));
        java.cssClass(Styles.JAVA_NAME);

        Code css = new Code(declarations(utility.css()));
        css.cssClass(Styles.CSS);

        Div preview = new Div(Previews.of(name));   // an example where what the class does can be seen
        preview.cssClass(Styles.SHOWCASE);

        Code className = new Code(name);
        className.cssClass(Styles.CLASS_NAME);

        return new Tr(new Td(java), new Td(className), new Td(preview), new Td(css));
    }

    // ---- names, as Tw has them ----

    /** A class's family: its first word ({@code bg} of {@code bg-red-500}, {@code m} of {@code -m-4}). */
    static String family(String name) {
        String n = name.startsWith("-") ? name.substring(1) : name;
        if (n.startsWith("@")) n = n.substring(1);
        int dash = n.indexOf('-');
        return dash > 0 ? n.substring(0, dash) : n;
    }

    /** Its Java name, as Tw writes it: {@code Tw.Bg.BG_RED_500}. */
    static String javaName(String name) {
        String n = name, prefix = "";
        if (n.startsWith("-")) { prefix = "NEG_"; n = n.substring(1); }
        if (n.startsWith("@")) { prefix += "AT_"; n = n.substring(1); }
        String constant = prefix + n.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", "").toUpperCase(Locale.ROOT);
        if (constant.isEmpty() || Character.isDigit(constant.charAt(0))) constant = "_" + constant;
        String family = family(name);
        String inner = family.equals("mask") ? "." + pascal(second(name)) : "";   // mask-* is split by its next word
        return "Tw." + pascal(family) + inner + "." + constant;
    }

    private static String second(String name) {
        String[] parts = (name.startsWith("-") ? name.substring(1) : name).split("-");
        return parts.length > 1 ? parts[1].replaceAll("[^A-Za-z0-9]", "") : "base";
    }

    private static String pascal(String word) {
        StringBuilder b = new StringBuilder();
        for (String part : word.split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.isEmpty() || Character.isDigit(b.charAt(0)) ? "X" + b : b.toString();
    }

    /** A rule's declarations, one a line: what the class does. */
    private static String declarations(String rule) {
        int open = rule.indexOf('{'), close = rule.lastIndexOf('}');
        String body = open >= 0 && close > open ? rule.substring(open + 1, close).strip() : rule;
        return body.replace("; ", ";\n");
    }
}
