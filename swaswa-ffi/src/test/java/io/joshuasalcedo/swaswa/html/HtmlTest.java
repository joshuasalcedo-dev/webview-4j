package io.joshuasalcedo.swaswa.html;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** HTML's elements and CSS, typed, written out: no page needed. */
class HtmlTest {

    @Test
    void elementsWriteAsHtml() {
        Div card = Html.div(Html.h2("Total"), Html.p(Html.text("Paid on "), Html.strong("Monday")))
                .setId("card").cssClass("card", "wide").style(CssProperty.PADDING, "12px");
        assertEquals("<div class=\"card wide\" style=\"padding: 12px\" id=\"card\"><h2>Total</h2><p>Paid on <strong>Monday</strong></p></div>",
                card.toHtml());
        assertEquals("TotalPaid on Monday", card.getText());
    }

    @Test
    void attributesAreTypedAndWrittenAsHtmlNamesThem() {
        Input email = Html.input().setType(Input.Type.EMAIL).setRequired(true).setMaxLength(40).setAutoComplete(Input.AutoComplete.EMAIL);
        assertEquals(Input.Type.EMAIL, email.getType());
        assertTrue(email.isRequired());
        assertEquals(40, email.getMaxLength());
        String html = email.toHtml();
        assertTrue(html.contains("type=\"email\"") && html.contains(" required") && html.contains("maxlength=\"40\""), html);
        Label label = Html.label("Email").setHtmlFor("email");
        assertTrue(label.toHtml().contains("for=\"email\""), "htmlFor is HTML's for: " + label.toHtml());
        email.setRequired(false);
        assertFalse(email.toHtml().contains("required"));
    }

    @Test
    void textIsEscapedAndUnsafeThingsAreRefused() {
        assertEquals("<p>&lt;script&gt;x&lt;/script&gt; &amp; &quot;q&quot;</p>", Html.p("<script>x</script> & \"q\"").toHtml());
        assertThrows(IllegalArgumentException.class, () -> Html.a("x").setHref("javascript:alert(1)"));
        for (String scripted : List.of("data:text/html,<script>x</script>", "DATA:image/svg+xml;base64,AAAA", "data:application/xhtml+xml,x",
                "data:,<b>no type</b>", " data:text/xml,x")) {
            assertThrows(IllegalArgumentException.class, () -> Html.a("x").setHref(scripted), scripted);
        }
        for (String fine : List.of("data:text/plain,hello", "data:application/json,{}", "data:image/png;base64,AAAA", "https://example.com/")) {
            assertEquals(fine, Html.a("x").setHref(fine).getHref(), "fine: " + fine);
        }
        // an SVG shown as an image runs no script: fine where a URL is only loaded as one, refused where it is a document
        String svg = "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg'/>";
        assertEquals(svg, new Img().setSrc(svg).getSrc());
        assertEquals(svg, new Video().setPoster(svg).getPoster());
        assertEquals(svg, new Input().setSrc(svg).getSrc());
        assertThrows(IllegalArgumentException.class, () -> Html.a("x").setHref(svg));
        assertThrows(IllegalArgumentException.class, () -> new Form().setAction(svg));
        assertThrows(IllegalArgumentException.class, () -> new Img().setSrc("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> new Img().setSrc("data:text/html,<script>x</script>"));
        assertThrows(IllegalArgumentException.class, () -> Html.div().setAttribute("onclick", "x()"), "a handler is on(...), not an attribute");
        assertThrows(IllegalArgumentException.class, () -> Html.div().cssClass("a b"));
        assertThrows(IllegalArgumentException.class, () -> Html.div().style("color", "red; background: url(x)"));
        assertThrows(UnsupportedOperationException.class, () -> Html.br().add(Html.span("x")), "a void element holds nothing");
        Div outer = Html.div();
        Div inner = Html.div();
        outer.add(inner);
        assertThrows(IllegalArgumentException.class, () -> inner.add(outer), "no element holds what it is in");
    }

    @Test
    void childrenMoveLikeSwings() {
        Div a = Html.div(), b = Html.div();
        Span s = Html.span("x");
        a.add(s);
        b.add(s);
        assertEquals(0, a.getComponentCount(), "added elsewhere: out of where it was");
        assertSame(b, s.getParent());
        b.add(Html.span("first"), 0);
        assertEquals("firstx", b.getText());
        b.setText("only");
        assertEquals(1, b.getComponentCount());
    }

    @Test
    void stylesAndSheetsWriteAsCss() {
        StyleSheet sheet = new StyleSheet()
                .rule(":root", s -> s.set(CssProperty.COLOR_SCHEME, "light dark").set("--gap", "8px"))
                .rule(".card", s -> s.set(CssProperty.GAP, Style.var("gap")).set(CssProperty.BACKGROUND, Style.lightDark("#fff", "#25282f")))
                .dark(d -> d.rule(".logo", s -> s.set(CssProperty.FILTER, "invert(1)")))
                .media("(max-width: 600px)", m -> m.rule(".side", s -> s.set(CssProperty.DISPLAY, "none")));
        assertEquals("""
                :root { color-scheme: light dark; --gap: 8px }
                .card { gap: var(--gap); background: light-dark(#fff, #25282f) }
                @media (prefers-color-scheme: dark) {
                  .logo { filter: invert(1) }
                }
                @media (max-width: 600px) {
                  .side { display: none }
                }
                """, sheet.toCss());
        assertThrows(IllegalArgumentException.class, () -> new StyleSheet().rule("a { } b", s -> {}));
        assertThrows(IllegalArgumentException.class, () -> new StyleSheet().css("</style><script>"));
    }
}
