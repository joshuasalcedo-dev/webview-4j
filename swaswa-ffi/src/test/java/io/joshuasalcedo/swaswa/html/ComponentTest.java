package io.joshuasalcedo.swaswa.html;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.component.BorderLayout;
import io.joshuasalcedo.swaswa.component.BoxLayout;
import io.joshuasalcedo.swaswa.component.CardLayout;
import io.joshuasalcedo.swaswa.component.Component;
import io.joshuasalcedo.swaswa.component.Container;
import io.joshuasalcedo.swaswa.component.FlowLayout;
import io.joshuasalcedo.swaswa.component.GridLayout;
import io.joshuasalcedo.swaswa.component.Property;
import io.joshuasalcedo.swaswa.component.StackLayout;
import io.joshuasalcedo.swaswa.css.CssProperty;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The components' contract, as HTML's elements keep it: Swing's names, JavaFX's properties, HTML's attributes, layouts. */
class ComponentTest {

    @Test
    void everyElementIsAContainerOfComponents() {
        Container<?, Node> panel = Html.div();   // a Div is a JPanel
        Component<?> button = Html.button("OK");
        assertInstanceOf(Container.class, button, "any element can hold others, as a JComponent can");
        panel.add((Node) button);
        assertSame(panel, button.getParent());
        assertEquals(1, panel.getComponentCount());
    }

    @Test
    void swingsNamesWriteHtml() {
        Button b = Html.button("Save").setToolTipText("Ctrl+S").setEnabled(false).setBackground("#3157d5")
                .setPreferredSize(120, 32).setCursor("pointer");
        assertEquals("<button style=\"background: #3157d5; width: 120.0px; height: 32.0px; cursor: pointer\" title=\"Ctrl+S\" disabled>Save</button>",
                b.toHtml());
        io.joshuasalcedo.swaswa.component.ClientKey<Integer> row = io.joshuasalcedo.swaswa.component.ClientKey.of("row", Integer.class);
        b.putClientProperty(row, 4);
        assertEquals(4, b.getClientProperty(row), "typed: an Integer back");
        assertFalse(b.toHtml().contains("row"), "the app's own data: never written");
    }

    @Test
    void htmlsGlobalAttributesAreTheBases() {
        Div d = Html.div().setLang("fil").setDir(Component.Dir.RTL).setTabIndex(2).setInert(true).setRole("region")
                .setAria("label", "Totals").setData("row", "4").setSpellcheck(false).setTranslate(false)
                .setInputMode(Component.InputMode.NUMERIC).setPopover(Component.Popover.AUTO).setAccessKey("t");
        String html = d.toHtml();
        for (String a : List.of("lang=\"fil\"", "dir=\"rtl\"", "tabindex=\"2\"", " inert", "role=\"region\"", "aria-label=\"Totals\"",
                "data-row=\"4\"", "spellcheck=\"false\"", "translate=\"no\"", "inputmode=\"numeric\"", "popover=\"auto\"", "accesskey=\"t\"")) {
            assertTrue(html.contains(a), a + " in " + html);
        }
        assertEquals(2, d.getTabIndex());
        d.setHidden(true);
        assertFalse(d.isVisible());
        assertThrows(IllegalArgumentException.class, () -> d.setAttribute("onclick", "x()"));
    }

    @Test
    void propertiesBindAsJavaFxs() {
        Button save = Html.button("Save");
        Property<Boolean> valid = new Property<>(false);
        save.enabledProperty().bind(valid);
        assertFalse(save.isEnabled(), "follows: not valid, not enabled");
        valid.set(true);
        assertTrue(save.isEnabled());
        assertThrows(IllegalStateException.class, () -> save.setEnabled(false), "bound: it follows another");

        Input a = Html.input(), b = Html.input();
        a.valueProperty().bindBidirectional(b.valueProperty());
        b.setValue("Ada");
        assertEquals("Ada", a.getValue(), "each follows the other");
        a.setValue("Grace");
        assertEquals("Grace", b.getValue());
        List<String> seen = new ArrayList<>();
        a.valueProperty().addListener((old, now) -> seen.add(old + "->" + now));
        a.setValue("Linus");
        assertEquals(List.of("Grace->Linus"), seen);
    }

    @Test
    void layoutsAreCssOnTheContainerAndItsChildren() {
        Div frame = Html.div().setLayout(new BorderLayout(4, 4));
        Div toolbar = Html.div(), editor = Html.div();
        frame.add(toolbar, BorderLayout.NORTH);
        frame.add(editor);   // no place: the centre
        assertEquals("grid", frame.getStyle().get(CssProperty.DISPLAY));
        assertEquals("north", toolbar.getStyle().get(CssProperty.GRID_AREA));
        assertEquals("center", editor.getStyle().get(CssProperty.GRID_AREA));

        frame.setLayout(BoxLayout.column(8));   // another layout: the old one's CSS goes
        assertEquals("column", frame.getStyle().get(CssProperty.FLEX_DIRECTION));
        assertNull(frame.getStyle().get(CssProperty.GRID_TEMPLATE_AREAS));
        assertNull(toolbar.getStyle().get(CssProperty.GRID_AREA));

        Div grid = Html.div().setLayout(new GridLayout(0, 2, 8, 8));
        Div notes = Html.div();
        grid.add(notes, GridLayout.cell(0, 3).span(2, 1));
        assertEquals("repeat(2, minmax(0, 1fr))", grid.getStyle().get(CssProperty.GRID_TEMPLATE_COLUMNS));
        assertEquals("1 / span 2", notes.getStyle().get(CssProperty.GRID_COLUMN));
        assertEquals("4 / span 1", notes.getStyle().get(CssProperty.GRID_ROW));

        Div row = Html.div().setLayout(new FlowLayout(FlowLayout.Align.LEADING));
        assertEquals("wrap", row.getStyle().get(CssProperty.FLEX_WRAP));
        Div stack = Html.div().setLayout(new StackLayout());
        Div under = Html.div(), over = Html.div();
        stack.add(under, over);
        assertEquals("1 / 1", over.getStyle().get(CssProperty.GRID_AREA), "all in one place, the last on top");
        assertThrows(IllegalArgumentException.class, () -> frame.setLayout(new BorderLayout()).add(Html.div(), "Up"));
    }

    @Test
    void aCardLayoutShowsOneAtATime() {
        CardLayout cards = new CardLayout();
        Div pages = Html.div().setLayout(cards);
        Div home = Html.div(), settings = Html.div();
        pages.add(home, "home");
        pages.add(settings, "settings");
        assertNull(home.getStyle().get(CssProperty.DISPLAY), "the first shows");
        assertEquals("none", settings.getStyle().get(CssProperty.DISPLAY));
        cards.show("settings");
        assertEquals("none", home.getStyle().get(CssProperty.DISPLAY));
        assertNull(settings.getStyle().get(CssProperty.DISPLAY));
        cards.next();
        assertEquals("home", cards.getShown());
    }

    @Test
    void lookupFindsAsJavaFxsDoes() {
        Button save = Html.button("Save").setId("save").cssClass("primary");
        Div root = Html.div(Html.div(Html.span("x"), save), Html.button("Cancel"));
        assertSame(save, root.lookup("#save").orElseThrow());
        assertSame(save, root.lookup("button.primary").orElseThrow());
        assertEquals(2, root.lookupAll("button").size());
        assertTrue(root.lookup(".none").isEmpty());
    }
}
