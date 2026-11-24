package io.joshuasalcedo.webview4j.ui.components.layout;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import io.joshuasalcedo.webview4j.ui.components.Accordion;
import io.joshuasalcedo.webview4j.ui.components.Carousel;
import io.joshuasalcedo.webview4j.ui.components.layout.GridPane.ColumnConstraints;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The panes in a real window, measured: where things are, and what comes back to Java. */
@EnabledOnOs(OS.WINDOWS)
class LayoutWindowTest {

    static final class Panes implements Screen {
        String card = "one";
        double[] split = {0.25};
        int slide;
        int open;

        @Override
        public Component<?> render() {
            return new VBox(16,
                    new HBox(10, text("A").key("a").width(50), HBox.hgrow(text("grows").key("grow")), text("C").key("c").width(50)).key("hbox"),
                    new GridPane().hgap(10).columns(ColumnConstraints.fixed(100), ColumnConstraints.grow())
                            .addRow(0, text("Name").key("label"), textField("Name").key("field")),
                    new SplitPane(text("left"), text("right")).dividerPositions(split).onDividerPositionsChange(p -> split = p).height(80).key("split"),
                    new CardPane().card("one", text("Card one")).card("two", text("Card two")).show(card).transition(CardPane.Transition.NONE),
                    new StackPane(text("base")).add(text("corner"), Pos.TOP_RIGHT).width(200).height(100),
                    new Carousel("Pictures", text("Slide A"), text("Slide B")).index(slide).onIndexChange(i -> slide = i).height(60),
                    new Accordion().pane("General", text("general things")).pane("About", text("about things")).expanded(open).onExpandedChange(i -> open = i),
                    text("card " + card + " split " + Math.round(split[0] * 100) + " slide " + slide + " open " + open)).padding(16);
        }
    }

    @Test
    @Timeout(120)
    void thePanesLayOutAsJavaFxsDo() throws Exception {
        try (App app = App.start()) {
            Panes screen = new Panes();
            UiWindow window = Ui.open(app, screen, o -> o.headless(true).size(900, 1000)).join();
            View view = window.view();
            until(view, "card one");

            // HBox: side by side, 10 apart, the middle one takes the rest
            String hbox = js(view, """
                    (() => { const box = [...document.querySelectorAll('.nt-box[data-direction=row]')].find(b => b.innerText.includes('grows'));
                      const [a, g, c] = [...box.children].map(e => e.getBoundingClientRect());
                      return [Math.round(g.left - a.right), Math.round(c.left - g.right), Math.round(g.width > box.clientWidth - 140 ? 1 : 0), Math.round(a.top - c.top)].join(','); })()
                    """);
            assertEquals("10,10,1,0", hbox, "gaps, the grown child, one line");

            // GridPane: the first column 100 wide, the field beside it from 110
            String grid = js(view, """
                    (() => { const g = document.querySelector('.nt-gridpane'); const cells = [...g.children].map(e => e.getBoundingClientRect());
                      const left = g.getBoundingClientRect().left; return [Math.round(cells[0].width), Math.round(cells[1].left - left)].join(','); })()
                    """);
            assertEquals("100,110", grid);

            // SplitPane: the divider at a quarter; moved with the keyboard, Java hears it once let go
            String at = js(view, """
                    (() => { const s = document.querySelector('.nt-splitpane'); const d = s.querySelector('.nt-splitpane-divider');
                      return Math.round((d.getBoundingClientRect().left - s.getBoundingClientRect().left) / s.clientWidth * 100); })()
                    """);
            assertEquals("25", at);
            view.eval("""
                    (() => { const d = document.querySelector('.nt-splitpane-divider'); d.focus();
                      d.dispatchEvent(new KeyboardEvent('keydown', {key: 'ArrowRight', shiftKey: true, bubbles: true}));
                      d.dispatchEvent(new KeyboardEvent('keyup', {key: 'ArrowRight', bubbles: true})); })()
                    """).join();
            until(view, "split 35");

            // CardPane: one card shown; Java shows another
            assertEquals("true,false", js(view, "[...document.querySelectorAll('.nt-card')].map(c => c.hasAttribute('data-shown')).join(',')"));
            screen.card = "two";
            window.refresh().join();
            until(view, "card two");
            assertEquals("false,true", js(view, "[...document.querySelectorAll('.nt-card')].map(c => c.hasAttribute('data-shown')).join(',')"));

            // StackPane: the corner child at the top right
            String corner = js(view, """
                    (() => { const s = document.querySelector('.nt-stackpane'); const r = s.getBoundingClientRect();
                      const c = [...s.querySelectorAll('*')].find(e => e.innerText === 'corner' && e.children.length === 0).getBoundingClientRect();
                      return [Math.round(r.right - c.right) <= 1, Math.round(c.top - r.top) <= 1].join(','); })()
                    """);
            assertEquals("true,true", corner);

            // Carousel: Next goes on, Java hears which page
            view.eval("document.querySelector('.nt-carousel button[aria-label=Next]').click()").join();
            until(view, "slide 1");

            // Accordion: opening the second closes the first
            view.eval("[...document.querySelectorAll('.nt-titledpane-header')].find(b => b.innerText.includes('About')).click()").join();
            until(view, "open 1");
            assertEquals("false,true", js(view, "[...document.querySelectorAll('.nt-titledpane-header')].map(b => b.getAttribute('aria-expanded')).join(',')"));
        }
    }

    static String js(View view, String script) {
        return String.valueOf(view.eval(script).join());
    }

    static void until(View view, String part) throws InterruptedException {
        String text = "";
        for (int i = 0; i < 100; i++) {
            text = js(view, "document.body.innerText");
            if (text.contains(part)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("never showed '" + part + "': " + text);
    }
}
