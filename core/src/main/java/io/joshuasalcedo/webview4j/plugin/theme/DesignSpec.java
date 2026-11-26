package io.joshuasalcedo.webview4j.plugin.theme;

import io.joshuasalcedo.webview4j.Theme;
import io.joshuasalcedo.webview4j.ui.Style;
import io.joshuasalcedo.webview4j.ui.components.Platform;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A design system for the app: the shapes of one platform (corners, density, controls) and a colour for each of the
 * kit's tokens ({@link Style.Token}), for light <b>and</b> for dark. Two come with webview-4j: {@link #WINDOWS}
 * (Microsoft's Windows 11, Fluent) and {@link #MACOS} (Apple's macOS). An app makes its own from one of them: what it
 * leaves out stays as the base has it.
 * <pre>{@code
 * DesignSpec brand = DesignSpec.basedOn(DesignSpec.WINDOWS, "Acme")
 *         .token(Style.Token.ACCENT, Color.rgb(0xE4002B), Color.rgb(0xFF5468))      // light, dark
 *         .token(Style.Token.WINDOW, Color.rgb(0xFFFBF7), Color.rgb(0x1B1512))
 *         .build();
 * Appearance.current().register(brand).use(brand);
 * }</pre>
 * Every token a spec sets has its light and its dark colour: a spec never breaks one of the two.
 */
public interface DesignSpec {

    /** Microsoft's Windows 11 (Fluent): the kit's own, as the system draws it. */
    DesignSpec WINDOWS = new Spec("Windows 11", Platform.WINDOWS, Map.of(), Map.of());

    /** Apple's macOS: the kit's own, as the system draws it. */
    DesignSpec MACOS = new Spec("macOS", Platform.MAC, Map.of(), Map.of());

    /** Its name, as the theme switcher lists it. */
    String name();

    /** Whose shapes it has: {@link Platform#WINDOWS} or {@link Platform#MAC}. */
    Platform platform();

    /**
     * Its colours for {@code mode} ({@link Theme#LIGHT} or {@link Theme#DARK}), over the platform's own: a token
     * left out keeps the platform's colour.
     */
    Map<Style.Token, Color> tokens(Theme mode);

    /** A spec of {@code name} starting from {@code base}: its shapes, and its colours where none are given. */
    static Builder basedOn(DesignSpec base, String name) {
        return new Builder(Objects.requireNonNull(base, "base"), Objects.requireNonNull(name, "name"));
    }

    /** Makes a {@link DesignSpec}: each token with its light and its dark colour. */
    final class Builder {
        private final DesignSpec base;
        private final String name;
        private final Map<Style.Token, Color> light = new EnumMap<>(Style.Token.class);
        private final Map<Style.Token, Color> dark = new EnumMap<>(Style.Token.class);

        private Builder(DesignSpec base, String name) {
            this.base = base;
            this.name = name;
            light.putAll(base.tokens(Theme.LIGHT));
            dark.putAll(base.tokens(Theme.DARK));
        }

        /** {@code token}'s colour in light mode and in dark mode. */
        public Builder token(Style.Token token, Color inLight, Color inDark) {
            Objects.requireNonNull(token, "token");
            light.put(token, Objects.requireNonNull(inLight, "light"));
            dark.put(token, Objects.requireNonNull(inDark, "dark"));
            return this;
        }

        /** The same colour in both modes (a brand colour that reads on both). */
        public Builder token(Style.Token token, Color both) {
            return token(token, both, both);
        }

        public DesignSpec build() {
            return new Spec(name, base.platform(), light, dark);
        }
    }

    /** The specs the builder and the built-in ones are. */
    record Spec(String name, Platform platform, Map<Style.Token, Color> light, Map<Style.Token, Color> dark) implements DesignSpec {

        public Spec {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) throw new IllegalArgumentException("a design spec has a name");
            if (platform != Platform.WINDOWS && platform != Platform.MAC) throw new IllegalArgumentException("a spec has the shapes of WINDOWS or MAC: " + platform);
            light = Map.copyOf(light);
            dark = Map.copyOf(dark);
            if (!light.keySet().equals(dark.keySet())) {
                throw new IllegalArgumentException(name + ": every token needs a light and a dark colour");
            }
        }

        @Override
        public Map<Style.Token, Color> tokens(Theme mode) {
            return switch (Objects.requireNonNull(mode, "mode")) {
                case LIGHT -> light;
                case DARK -> dark;
                case SYSTEM -> throw new IllegalArgumentException("a spec's colours are for LIGHT or DARK; SYSTEM picks one of them");
            };
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
