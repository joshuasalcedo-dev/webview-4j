package io.joshuasalcedo.swaswa.ffi;

import io.joshuasalcedo.swaswa.component.BoxLayout;
import io.joshuasalcedo.swaswa.component.FlowLayout;
import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Html;
import io.joshuasalcedo.swaswa.html.Node;
import io.joshuasalcedo.swaswa.html.Section;
import io.joshuasalcedo.swaswa.html.Span;

/** What the showcase's pages share: their look (CSS, light and dark), and the small parts they are made of. */
final class ShowcaseUi {

    private ShowcaseUi() {}

    /** The showcase's look: every page has it. */
    static final StyleSheet LOOK = new StyleSheet()
            .rule(":root", s -> s.set(CssProperty.COLOR_SCHEME, "light dark").set(CssProperty.FONT, "13px system-ui")
                    .set("--bg", Style.lightDark("#f6f7f9", "#18191c"))
                    .set("--card", Style.lightDark("#ffffff", "#222327"))
                    .set("--line", Style.lightDark("#e3e5ea", "#33353b"))
                    .set("--text", Style.lightDark("#111", "#e8e8e8"))
                    .set("--accent", Style.lightDark("#3157d5", "#8ab4ff")))
            .rule("body", s -> s.set(CssProperty.MARGIN, "0").set(CssProperty.BACKGROUND, Style.var("bg")).set(CssProperty.COLOR, Style.var("text")))
            .rule("button, input, select, textarea", s -> s.set(CssProperty.FONT, "inherit").set(CssProperty.COLOR, "inherit"))
            .rule("button", s -> s.set(CssProperty.PADDING, "5px 10px").set(CssProperty.BORDER_RADIUS, "6px")
                    .set(CssProperty.BORDER, "1px solid " + Style.lightDark("#cfd3da", "#444")).set(CssProperty.BACKGROUND, Style.lightDark("#fafbfc", "#2b2c31"))
                    .set(CssProperty.CURSOR, "pointer"))
            .rule("button:hover", s -> s.set(CssProperty.BACKGROUND, Style.lightDark("#eef1f6", "#34363c")))
            .rule("input, select", s -> s.set(CssProperty.PADDING, "4px 8px").set(CssProperty.BORDER_RADIUS, "6px")
                    .set(CssProperty.BORDER, "1px solid " + Style.lightDark("#cfd3da", "#444")).set(CssProperty.BACKGROUND, Style.lightDark("#fff", "#1a1b1e")))
            .rule("section", s -> s.set(CssProperty.BACKGROUND, Style.var("card")).set(CssProperty.BORDER, "1px solid " + Style.var("line"))
                    .set(CssProperty.BORDER_RADIUS, "8px").set(CssProperty.PADDING, "12px 14px").set(CssProperty.MARGIN_BOTTOM, "12px"))
            .rule("h2", s -> s.set(CssProperty.FONT_SIZE, "13px").set(CssProperty.TEXT_TRANSFORM, "uppercase").set(CssProperty.LETTER_SPACING, ".06em")
                    .set(CssProperty.OPACITY, ".65").set(CssProperty.MARGIN, "0 0 8px"))
            .rule(".row + .row", s -> s.set(CssProperty.MARGIN_TOP, "6px"))
            .rule(".hint", s -> s.set(CssProperty.OPACITY, ".6").set(CssProperty.FONT_SIZE, "12px"))
            .rule(".mono", s -> s.set(CssProperty.FONT_FAMILY, "ui-monospace, monospace"));

    /** A window of the app's whose page is made of components: its {@link Document}, the look already in it. */
    static Document window(App app, WindowOptions options) {
        View view = app.open(options).join();
        return Document.of(view).addStyleSheet(LOOK);
    }

    /** An embedded view whose page is made of components (the bridge on for it: its page is the app's own). */
    static Document embedded(View host, double x, double y, double width, double height) {
        View view = host.embed(WindowOptions.html("").origins("null"), x, y, width, height).join();
        return Document.of(view).addStyleSheet(LOOK);
    }

    /** A titled card: {@code <section><h2>title</h2>...rows</section>}. */
    static Section section(String title, Node... rows) {
        Section s = Html.section(Html.h2(title));
        s.add(rows);
        return s;
    }

    /** A row of controls, wrapping as the window narrows. */
    static Div row(Node... controls) {
        return Html.div(controls).cssClass("row").setLayout(new FlowLayout(FlowLayout.Align.LEADING, 6, 6));
    }

    static Span hint(String text) {
        return Html.span(text).cssClass("hint");
    }

    /** A plain page of the app's own: a heading and a sentence, padded. */
    static Div plain(String title, String text) {
        return Html.div(Html.h2(title).style(CssProperty.TEXT_TRANSFORM, "none").style(CssProperty.OPACITY, "1"), Html.p(text))
                .setPadding("24px").setFont("14px system-ui");
    }

    /** A column, its children {@code gap} apart. */
    static Div column(double gap, Node... children) {
        return Html.div(children).setLayout(BoxLayout.column(gap));
    }

    /** A button that does something when pressed. */
    static Button button(String label, Runnable pressed) {
        Button b = Html.button(label);
        b.addActionListener(e -> pressed.run());
        return b;
    }
}
