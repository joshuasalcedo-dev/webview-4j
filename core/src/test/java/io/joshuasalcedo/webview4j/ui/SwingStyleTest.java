package io.joshuasalcedo.webview4j.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.ui.components.Button;
import io.joshuasalcedo.webview4j.ui.components.Checkbox;
import io.joshuasalcedo.webview4j.ui.components.Slider;
import io.joshuasalcedo.webview4j.ui.components.Text;
import io.joshuasalcedo.webview4j.ui.components.TextField;
import io.joshuasalcedo.webview4j.ui.components.layout.VBox;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Components used as Swing's are: made with new, kept in fields, added to panes, with listeners. */
class SwingStyleTest {

    /** The window's side: the tree drawn, then the page's event for {@code component}'s {@code event}. */
    private static void send(Component<?> root, Component<?> component, String event, Object value) {
        Map<String, Consumer<Object>> registry = new LinkedHashMap<>();
        Map<String, Object> tree = root.toTree(registry);
        String id = find(tree, component, root, event);
        assertNotNull(id, "the tree names a handler for " + event);
        registry.get(id).accept(value);
    }

    @SuppressWarnings("unchecked")
    private static String find(Map<String, Object> node, Component<?> target, Component<?> at, String event) {
        if (at == target) return (String) ((Map<String, Object>) node.get("p")).get(event);
        List<Object> c = (List<Object>) node.get("c");
        if (c == null) return null;
        List<Component<?>> children = at.children();
        for (int i = 0; i < children.size(); i++) {
            String id = find((Map<String, Object>) c.get(i), target, children.get(i), event);
            if (id != null) return id;
        }
        return null;
    }

    @Test
    void aTextFieldKeepsWhatIsTypedWithoutAListener() {
        TextField name = new TextField("Name");
        VBox panel = new VBox(12);
        panel.add(name);
        send(panel, name, "onChange", "Ana");
        assertEquals("Ana", name.getText());
        name.setText("Marco");
        assertEquals("Marco", name.getText());
    }

    @Test
    void listenersRunInOrderAfterTheHandlerAndCanBeRemoved() {
        Button save = new Button("Save");
        List<String> ran = new ArrayList<>();
        save.onClick(() -> ran.add("handler"));
        io.joshuasalcedo.webview4j.ui.event.ActionListener first = e -> ran.add("first " + (e.getSource() == save));
        save.addActionListener(first);
        save.addActionListener(e -> ran.add("second"));
        VBox panel = new VBox(save);
        send(panel, save, "onClick", null);
        assertEquals(List.of("handler", "first true", "second"), ran);

        ran.clear();
        save.removeActionListener(first);
        send(panel, save, "onClick", null);
        assertEquals(List.of("handler", "second"), ran);
    }

    @Test
    void aChangeListenerSeesTheValueTheComponentAlreadyHolds() {
        Checkbox agree = new Checkbox("I agree");
        Text said = new Text("");
        agree.addChangeListener(e -> said.setText(e.getValue() + " / " + agree.isSelected()));
        send(new VBox(agree, said), agree, "onChange", true);
        assertEquals("true / true", said.getText());
    }

    @Test
    void generatedComponentsKeepTheirValueToo() {
        Slider volume = new Slider("Volume", 40);
        List<Double> heard = new ArrayList<>();
        volume.addChangeListener(e -> heard.add(e.getValue()));
        send(new VBox(volume), volume, "onChange", 70);
        assertEquals(70.0, volume.getValue());
        assertEquals(List.of(70.0), heard);
    }

    @Test
    void panesAddAndRemoveAsSwingDoes() {
        Text a = new Text("a"), b = new Text("b"), c = new Text("c");
        VBox panel = new VBox();
        panel.add(a, c);
        panel.add(b, 1);
        assertEquals(List.of(a, b, c), panel.getComponents());
        panel.remove(b);
        assertEquals(2, panel.getComponentCount());
        panel.removeAll();
        assertEquals(0, panel.getComponentCount());
    }

    @Test
    void enabledVisibleAndToolTip() {
        Button b = new Button("Go");
        assertTrue(b.isEnabled() && b.isVisible());
        b.setEnabled(false).setVisible(false).setToolTipText("Not yet");
        assertFalse(b.isEnabled());
        assertFalse(b.isVisible());
        assertEquals("Not yet", b.getToolTipText());
    }

    @Test
    void aWindowRendersItsContentPane() {
        class Counter extends Window {
            final Text label = new Text("Clicked 0 times");
            final Button button = new Button("Click me");
            int count;

            Counter() {
                super("Counter");
                setSize(400, 300);
                button.addActionListener(e -> label.setText("Clicked " + ++count + " times"));
                add(label, button);
            }
        }
        Counter counter = new Counter();
        assertSame(counter.getContentPane(), counter.render());
        send(counter.render(), counter.button, "onClick", null);
        assertEquals("Clicked 1 times", counter.label.getText());
        assertEquals("Counter", counter.getTitle());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPartIsStyledByItsOwnEnum() {
        Button b = new Button("Go").style(io.joshuasalcedo.webview4j.ui.style.ButtonPart.ROOT, Style.of().radius(20));
        Map<String, Object> tree = ((Component<?>) b).toTree(new LinkedHashMap<>());
        Map<String, Object> html = (Map<String, Object>) ((Map<String, Object>) tree.get("p")).get("html");
        assertEquals(Map.of("nt-button", Map.of("border-radius", "20px")), html.get("parts"));
        // b.style(io.joshuasalcedo.webview4j.ui.style.DialogPart.TITLE, ...) does not compile: a Button takes ButtonPart only
    }

    @Test
    void aStyleSheetRuleIsTypedCss() {
        var sheet = new io.joshuasalcedo.webview4j.ui.components.StyleSheet()
                .rule(io.joshuasalcedo.webview4j.ui.style.DialogPart.TITLE, Style.of().fontWeight(700));
        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) ((Component<?>) sheet).toTree(new LinkedHashMap<>()).get("p");
        assertEquals(".nt-dialog-title { font-weight: 700 !important; }", p.get("css"));
    }
}
