package io.joshuasalcedo.webview4j.ui.components.egui;

import java.util.Map;
import java.util.Objects;

/**
 * Any egui-4j component by its name, with its props as they are in Rust: egui-4j's own ({@code "monitor"},
 * {@code "dev-logger"}) or one an app registered in its own build of egui-4j. For the built-in chart and table, the
 * typed {@link LineChart} and {@link LiveCsv} are simpler.
 * <pre>{@code
 * EguiView monitor = new EguiView("monitor", "Messages").height(400)
 *         .set("config", Map.of("columns", List.of("time", "subject")));
 * monitor.addEventListener(e -> { if (e.name().equals("select")) show(e.text("id")); });
 * }</pre>
 */
public final class EguiView extends EguiComponent<EguiView> {

    public EguiView(String component, String label) {
        super(component, label);
    }

    /** One prop of the Rust component ({@code null} takes it out): numbers, text, true/false, lists, maps, records. */
    public EguiView set(String prop, Object value) {
        return eguiProp(Objects.requireNonNull(prop, "prop"), value);
    }

    /** Several props at once. */
    public EguiView set(Map<String, ?> props) {
        props.forEach(this::set);
        return this;
    }

    /** A prop set before, or null. */
    public Object getProp(String prop) {
        return eguiProp(prop);
    }
}
