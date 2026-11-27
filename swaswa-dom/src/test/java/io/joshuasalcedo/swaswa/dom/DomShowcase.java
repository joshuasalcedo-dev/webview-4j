package io.joshuasalcedo.swaswa.dom;

import io.joshuasalcedo.swaswa.app.SwaApplication;
import io.joshuasalcedo.swaswa.component.FlowLayout;
import io.joshuasalcedo.swaswa.component.Property;
import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Dd;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Dl;
import io.joshuasalcedo.swaswa.html.Dt;
import io.joshuasalcedo.swaswa.html.H1;
import io.joshuasalcedo.swaswa.html.H2;
import io.joshuasalcedo.swaswa.html.Input;
import io.joshuasalcedo.swaswa.html.Node;
import io.joshuasalcedo.swaswa.html.Section;
import java.util.concurrent.CompletableFuture;

/**
 * swaswa-dom: the page, from Java, typed. Not part of the jar. {@code just dom-showcase}.
 */
public final class DomShowcase extends SwaApplication {

    public static void main(String[] args) {
        launch(args);
    }

    /** The page's own functions, as Java sees them. */
    interface Maths {
        long fib(int n);

        CompletableFuture<String> shout(String text);
    }

    /** What a script answers, as a record. */
    record Facts(String language, int cores) {}

    // ---- the parts the app changes ----

    private final Dd dark = new Dd("…");
    private final Dd wide = new Dd("…");
    private final Dd visible = new Dd("…");
    private final Div answer = new Div("Press a button.");
    private final Input text = new Input();

    private Dom page;
    private Maths maths;

    @Override
    protected void build() {
        setTitle("swaswa-dom");
        addStyleSheet(look());

        getHeader().add(new H1("swaswa-dom"));
        getMain().add(liveValues());
        getMain().add(buttons());
        getMain().add(clipboard());
        getMain().add(answerSection());
    }

    @Override
    protected void started(App app, View window) {
        page = Dom.of(window);

        follow(page.media("(prefers-color-scheme: dark)"), dark);
        follow(page.media("(width >= 48rem)"), wide);
        follow(page.visible(), visible);

        // the page's own functions (an app's page has its own; here they are made for the demo)
        window.eval("window.fib = (n) => n < 2 ? n : fib(n - 1) + fib(n - 2);"
                + " window.shout = async (t) => t.toUpperCase() + '!'").join();
        maths = page.bind(Maths.class);
    }

    // ---- the page's parts ----

    private Section liveValues() {
        Dl values = new Dl();
        values.add(new Dt("Dark (try the buttons)"), dark);
        values.add(new Dt("Wider than 48rem (resize)"), wide);
        values.add(new Dt("Visible (minimize the window)"), visible);

        Button light = new Button("Light");
        light.addActionListener(e -> setTheme(Theme.LIGHT));

        Button darkButton = new Button("Dark");
        darkButton.addActionListener(e -> setTheme(Theme.DARK));

        return section("Values Java follows", values, row(light, darkButton));
    }

    private Section buttons() {
        Button facts = new Button("A record from a script");
        facts.addActionListener(e -> showFacts());

        Button fib = new Button("fib(25) in the page");
        fib.addActionListener(e -> showFib());

        Button shout = new Button("An async function");
        shout.addActionListener(e -> showShout());

        Button size = new Button("The page's size");
        size.addActionListener(e -> showSize());

        return section("Asking the page", row(facts, fib, shout, size));
    }

    private Section clipboard() {
        text.setValue("copied from Java");

        Button copy = new Button("Copy");
        copy.addActionListener(e -> copy());

        Button paste = new Button("Paste");
        paste.addActionListener(e -> paste());

        return section("The clipboard", row(text, copy, paste));
    }

    private Section answerSection() {
        answer.setId("answer");
        return section("The answer", answer);
    }

    // ---- what the buttons do ----

    private void showFacts() {
        Facts facts = page.eval("({language: navigator.language, cores: navigator.hardwareConcurrency})", Facts.class).join();
        answer.setText(facts.toString());
    }

    private void showFib() {
        answer.setText("fib(25) = " + maths.fib(25));
    }

    private void showShout() {
        answer.setText(maths.shout("hello").join());
    }

    private void showSize() {
        answer.setText(page.size().join().toString());
    }

    private void copy() {
        page.clipboard().writeText(text.getValue()).join();
        answer.setText("Copied: " + text.getValue());
    }

    private void paste() {
        answer.setText("The clipboard holds: " + page.clipboard().readText().join());
    }

    // ---- small helpers ----

    /** Shows a value Java follows, and again each time it changes. */
    private static void follow(Property<Boolean> value, Dd field) {
        field.setText(String.valueOf(value.get()));
        value.addListener((old, now) -> field.setText(String.valueOf(now)));
    }

    private static Section section(String title, Node... content) {
        Section section = new Section(new H2(title));
        section.add(content);
        return section;
    }

    private static Div row(Node... content) {
        Div row = new Div(content);
        row.setLayout(new FlowLayout(FlowLayout.Align.LEADING, 6, 6));
        return row;
    }

    private static StyleSheet look() {
        return new StyleSheet()
                .rule(":root", s -> s.set(CssProperty.COLOR_SCHEME, "light dark").set(CssProperty.FONT, "14px system-ui"))
                .rule("body", s -> s.set(CssProperty.MARGIN, "0").set(CssProperty.PADDING, "20px 24px"))
                .rule("section", s -> s.set(CssProperty.PADDING, "12px 14px").set(CssProperty.MARGIN_BOTTOM, "12px")
                        .set(CssProperty.BORDER, "1px solid " + Style.lightDark("#e3e5ea", "#333")).set(CssProperty.BORDER_RADIUS, "8px"))
                .rule("h2", s -> s.set(CssProperty.FONT_SIZE, "13px").set(CssProperty.MARGIN, "0 0 8px"))
                .rule("dl", s -> s.set(CssProperty.DISPLAY, "grid").set(CssProperty.GRID_TEMPLATE_COLUMNS, "240px 1fr").set(CssProperty.MARGIN, "0 0 8px"))
                .rule("dd", s -> s.set(CssProperty.MARGIN, "0").set(CssProperty.FONT_WEIGHT, "600"))
                .rule("#answer", s -> s.set(CssProperty.FONT_FAMILY, "ui-monospace, monospace"));
    }
}
