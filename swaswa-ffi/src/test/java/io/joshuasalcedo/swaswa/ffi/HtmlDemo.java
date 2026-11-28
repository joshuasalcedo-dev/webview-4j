package io.joshuasalcedo.swaswa.ffi;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Form;
import io.joshuasalcedo.swaswa.html.Html;
import io.joshuasalcedo.swaswa.html.Input;
import io.joshuasalcedo.swaswa.html.Li;
import io.joshuasalcedo.swaswa.html.Span;
import io.joshuasalcedo.swaswa.html.Ul;

/** The showcase's page of HTML's elements and CSS, made in Java: a counter, a form, a list, light and dark. */
final class HtmlDemo {

    private HtmlDemo() {}

    static StyleSheet sheet() {
        return new StyleSheet()
                .rule(":root", s -> s.set(CssProperty.FONT, "14px system-ui")
                        .set("--accent", Style.lightDark("#3157d5", "#8aa4ff"))
                        .set("--card", Style.lightDark("#ffffff", "#25282f"))
                        .set("--line", Style.lightDark("#d5dae3", "#3a3d45")))
                .rule("body", s -> s.set(CssProperty.MARGIN, "0").set(CssProperty.PADDING, "18px")
                        .set(CssProperty.BACKGROUND, Style.lightDark("#eef1f6", "#1b1d22"))
                        .set(CssProperty.COLOR, Style.lightDark("#1b1d22", "#e4e8ef")))
                .rule(".card", s -> s.set(CssProperty.BACKGROUND, Style.var("card")).set(CssProperty.BORDER, "1px solid " + Style.var("line"))
                        .set(CssProperty.BORDER_RADIUS, "10px").set(CssProperty.PADDING, "14px").set(CssProperty.MARGIN_BOTTOM, "12px"))
                .rule(".row", s -> s.set(CssProperty.DISPLAY, "flex").set(CssProperty.GAP, "8px").set(CssProperty.ALIGN_ITEMS, "center"))
                .rule(".count", s -> s.set(CssProperty.FONT, "600 28px ui-monospace, monospace").set(CssProperty.COLOR, Style.var("accent"))
                        .set(CssProperty.MIN_WIDTH, "3ch").set(CssProperty.TEXT_ALIGN, "center"))
                .rule("button.on", s -> s.set(CssProperty.OUTLINE, "2px solid " + Style.var("accent")))
                .rule("li.done", s -> s.set(CssProperty.TEXT_DECORATION, "line-through").set(CssProperty.OPACITY, ".6"))
                .dark(d -> d.rule(".scheme", s -> s.set(CssProperty.FONT_STYLE, "italic")));
    }

    static Div page(Document doc) {
        // a counter: Java holds the number, the page shows it
        int[] count = {0};
        Span number = Html.span("0").cssClass("count");
        Button minus = Html.button("−"), plus = Html.button("+");
        minus.addActionListener(e -> number.setText(String.valueOf(--count[0])));
        plus.addActionListener(e -> number.setText(String.valueOf(++count[0])));

        // a form and a list: what is typed comes to Java, Java adds to the list
        Input task = Html.input().setPlaceholder("A task, then Enter").setName("task").setRequired(true);
        Ul list = Html.ul();
        Span left = Html.span("nothing to do");
        Form form = Html.form(Html.div(task, Html.button("Add").setType(Button.Type.SUBMIT)).cssClass("row"));
        form.addSubmitListener(e -> {
            String text = String.valueOf(e.fields().getOrDefault("task", "")).strip();
            if (text.isEmpty()) return;
            Input done = Html.input().setType(Input.Type.CHECKBOX);
            Li item = Html.li(done, Html.text(" " + text));
            done.addItemListener(c -> {
                if (done.isChecked()) item.cssClass("done");
                else item.removeCssClass("done");
            });
            list.add(item);
            task.setValue("");
            left.setText(list.getComponentCount() + " to do");
        });

        // light, dark or the system's
        Span scheme = Html.span("the system's").cssClass("scheme");
        Div schemes = Html.div().cssClass("row");
        for (Theme theme : Theme.values()) {
            Button b = Html.button(theme.name().charAt(0) + theme.name().substring(1).toLowerCase());
            b.addActionListener(e -> {
                doc.setTheme(theme);
                scheme.setText(theme == Theme.SYSTEM ? "the system's" : theme.name().toLowerCase());
                for (var other : schemes.getComponents()) if (other instanceof Button o) o.removeCssClass("on");
                b.cssClass("on");
            });
            schemes.add(b);
        }

        return Html.div(
                Html.h1("HTML and CSS from Java"),
                Html.div(Html.h3("A counter"), Html.div(minus, number, plus).cssClass("row")).cssClass("card"),
                Html.div(Html.h3("A form"), form, list, left).cssClass("card"),
                Html.div(Html.h3("Light and dark"), schemes, Html.p(Html.text("Now: "), scheme)).cssClass("card"));
    }
}
