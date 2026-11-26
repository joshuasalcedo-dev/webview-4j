package io.joshuasalcedo.webview4j.plugin.theme;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Theme;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.ui.Style;
import io.joshuasalcedo.webview4j.ui.Window;
import io.joshuasalcedo.webview4j.ui.components.Button;
import io.joshuasalcedo.webview4j.ui.components.Checkbox;
import io.joshuasalcedo.webview4j.ui.components.Text;
import io.joshuasalcedo.webview4j.ui.components.TextField;
import io.joshuasalcedo.webview4j.ui.components.TextVariant;
import io.joshuasalcedo.webview4j.ui.components.layout.HBox;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A design spec on a real window: its platform's shapes, its colours in light and in dark, switched live. */
class DesignSpecWindowTest {

    static final DesignSpec BRAND = DesignSpec.basedOn(DesignSpec.WINDOWS, "Acme")
            .token(Style.Token.ACCENT, Color.rgb(0xE4002B), Color.rgb(0xFF5468))
            .token(Style.Token.WINDOW, Color.rgb(0xFFF8F3), Color.rgb(0x1E1412))
            .build();

    @AfterEach
    void reset() {
        Appearance.current().reset();
    }

    @Test
    void aSpecHasEveryTokenInLightAndDark() {
        assertEquals(BRAND.tokens(Theme.LIGHT).keySet(), BRAND.tokens(Theme.DARK).keySet());
        assertEquals("#e4002b", BRAND.tokens(Theme.LIGHT).get(Style.Token.ACCENT).css());
        assertEquals("rgba(0,0,0,.08)", Color.rgba(0, 0.08).css());
        assertThrows(IllegalArgumentException.class, () -> new DesignSpec.Spec("Half", io.joshuasalcedo.webview4j.ui.components.Platform.WINDOWS,
                java.util.Map.of(Style.Token.ACCENT, Color.rgb(0)), java.util.Map.of()), "light without dark is refused");
        assertEquals(io.joshuasalcedo.webview4j.ui.components.Platform.WINDOWS, BRAND.platform(), "a brand keeps its base's shapes");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(120)
    void theWindowTakesTheSpecAndTheModeLive() throws Exception {
        String out = System.getProperty("demo.out");
        Appearance appearance = Appearance.current().register(BRAND);
        appearance.use(DesignSpec.WINDOWS).mode(Theme.LIGHT);
        Window window = new Window("Design specs") {
            {
                setSize(640, 380);
                add(new Text("One app, any design").variant(TextVariant.TITLE),
                        new ThemeSwitcher(),
                        new TextField("Name"), new Checkbox("Remember me"),
                        new HBox(8, new Button("Save").accent(), new Button("Cancel")));
            }

            @Override
            protected WindowOptions options(WindowOptions o) {
                return super.options(o).headless(out == null);
            }
        };
        try (App app = App.start()) {
            View view = window.open(app).join().view();
            until(view, "document.querySelector('.nt-root')?.dataset.platform", "windows");
            until(view, "document.querySelector('.nt-root')?.dataset.theme", "light");

            String accent = "getComputedStyle(document.querySelector('.nt-root')).getPropertyValue('--nt-accent').trim().toLowerCase()";
            appearance.use(BRAND);
            until(view, accent, "#e4002b");
            if (out != null) view.screenshot(Path.of(out).resolve("design-brand-light.png")).join();

            appearance.mode(Theme.DARK);
            until(view, "document.querySelector('.nt-root')?.dataset.theme", "dark");
            until(view, accent, "#ff5468");
            if (out != null) view.screenshot(Path.of(out).resolve("design-brand-dark.png")).join();

            appearance.use(DesignSpec.MACOS);
            until(view, "document.querySelector('.nt-root')?.dataset.platform", "mac");
            if (out != null) view.screenshot(Path.of(out).resolve("design-macos-dark.png")).join();

            // the switcher in the page: Light, back to Java
            // (again until it takes: under load the click can land while the page still redraws after the macOS switch)
            for (int i = 0; i < 150 && appearance.mode() != Theme.LIGHT; i++) {
                if (i % 10 == 0) view.eval("[...document.querySelectorAll('button, [role=radio], label')].find(e => e.innerText.trim() === 'Light')?.click()").join();
                Thread.sleep(100);
            }
            assertEquals(Theme.LIGHT, appearance.mode(), "the page's switcher set the mode");
            until(view, "document.querySelector('.nt-root')?.dataset.theme", "light");
        }
    }

    private static void until(View view, String script, String expected) throws InterruptedException {
        String got = null;
        for (int i = 0; i < 100; i++) {
            got = String.valueOf(view.eval(script).join());
            if (expected.equals(got)) return;
            Thread.sleep(100);
        }
        fail(script + ": expected " + expected + " but was " + got);
    }
}
