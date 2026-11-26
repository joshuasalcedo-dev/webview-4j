package io.joshuasalcedo.webview4j.plugin.theme;

import io.joshuasalcedo.webview4j.Theme;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Segmented;
import io.joshuasalcedo.webview4j.ui.components.Select;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Light, dark or as the system is, and which design spec: the app's {@link Appearance}, as two controls side by
 * side. Put it in a settings page, a toolbar, a status bar; it follows changes made elsewhere too.
 * <pre>{@code
 * add(new ThemeSwitcher());
 * add(new ThemeSwitcher().withoutDesigns());   // light, dark, system only
 * }</pre>
 */
public final class ThemeSwitcher extends Component<ThemeSwitcher> {

    private final Appearance appearance = Appearance.current();
    private final Segmented mode = new Segmented("Appearance", modes());
    private final Select design = new Select("Design", designs());

    public ThemeSwitcher() {
        super("box");
        prop("direction", "row");
        prop("gap", 12.0);
        prop("align", "end");
        prop("wrap", true);
        mode.setSelectedValue(appearance.mode().name());
        design.setSelectedValue(appearance.spec().name());
        mode.addChangeListener(e -> appearance.mode(Theme.valueOf(e.getValue())));
        design.addChangeListener(e -> appearance.specs().stream()
                .filter(s -> s.name().equals(e.getValue())).findFirst().ifPresent(appearance::use));
        // a change from elsewhere (code, another switcher, a new spec registered): shown here
        appearance.addChangeListener(a -> {
            mode.setSelectedValue(a.mode().name());
            design.setOptions(designs());
            design.setSelectedValue(a.spec().name());
        });
        add(mode, design);
    }

    /** Only light, dark and system: no choice of design. */
    public ThemeSwitcher withoutDesigns() {
        remove(design);
        return this;
    }

    private static Map<String, String> modes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(Theme.LIGHT.name(), "Light");
        m.put(Theme.DARK.name(), "Dark");
        m.put(Theme.SYSTEM.name(), "System");
        return m;
    }

    private Map<String, String> designs() {
        Map<String, String> m = new LinkedHashMap<>();
        for (DesignSpec s : appearance.specs()) m.put(s.name(), s.name());
        return m;
    }
}
