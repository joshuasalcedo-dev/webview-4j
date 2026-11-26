package io.joshuasalcedo.webview4j.ui.components.egui;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An egui-4j component (Rust, run in the page as WebAssembly, drawn on a canvas) embedded in a screen like any other
 * component: in a {@code VBox}, a split pane, a tab. Its props go to the Rust component as JSON whenever they change;
 * what the user does comes back as {@link EguiEvent}s.
 *
 * @param <C> the component's own type, so its settings chain
 */
public abstract class EguiComponent<C extends EguiComponent<C>> extends Component<C> {

    private final Map<String, Object> eguiProps = new LinkedHashMap<>();
    private final List<Consumer<EguiEvent>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** {@code name}: the component in egui-4j's registry ("line-chart"); {@code label}: what it shows, for screen readers. */
    protected EguiComponent(String name, String label) {
        super("egui");
        prop("component", Objects.requireNonNull(name, "name"));
        prop("label", Objects.requireNonNull(label, "label"));
        // every event: to the listeners, and to the subclass's own reading of it
        listen("onEvent", this, value -> {
            EguiEvent event = EguiEvent.of(value);
            if (event == null) return;
            received(event);
            for (Consumer<EguiEvent> l : listeners) l.accept(event);
        });
    }

    /** Its height in CSS pixels (egui draws on a canvas, which takes the room it is given). */
    @Override
    public C height(double height) {
        return super.height(height);
    }

    /** Runs for everything the user does on it: a click, a zoom, a search (the Rust component's events). */
    public C addEventListener(Consumer<EguiEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return self();
    }

    public C removeEventListener(Consumer<EguiEvent> listener) {
        listeners.remove(listener);
        return self();
    }

    /** Runs when the component fails in the page (it refused its props, or its WebAssembly did not load). */
    public C onError(Consumer<String> handler) {
        return on("onError", v -> handler.accept(pageText(v)));
    }

    /** For subclasses: an event from the page, before the listeners get it. */
    protected void received(EguiEvent event) {}

    /** Sets one of the Rust component's props ({@code null} takes it out); sent to the page as the window draws again. */
    protected C eguiProp(String name, Object value) {
        synchronized (eguiProps) {
            if (value == null) eguiProps.remove(name);
            else eguiProps.put(name, value);
            return prop("props", new LinkedHashMap<>(eguiProps));
        }
    }

    /** A prop set before, or null. */
    protected Object eguiProp(String name) {
        synchronized (eguiProps) {
            return eguiProps.get(name);
        }
    }
}
