package io.joshuasalcedo.webview4j.plugin.settings;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Theme;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.plugin.MissingProviderException;
import io.joshuasalcedo.webview4j.plugin.Provider;
import io.joshuasalcedo.webview4j.plugin.Providers;
import io.joshuasalcedo.webview4j.plugin.theme.Appearance;
import io.joshuasalcedo.webview4j.plugin.theme.Color;
import io.joshuasalcedo.webview4j.plugin.theme.DesignSpec;
import io.joshuasalcedo.webview4j.plugin.theme.DesignSpecProvider;
import io.joshuasalcedo.webview4j.ui.Style;
import io.joshuasalcedo.webview4j.ui.Window;
import io.joshuasalcedo.webview4j.ui.components.Text;
import io.joshuasalcedo.webview4j.ui.components.TextField;
import io.joshuasalcedo.webview4j.ui.components.TextVariant;
import io.joshuasalcedo.webview4j.ui.components.Toggle;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The settings dialog over a real window, and the providers plugins take from the app. */
class SettingsWindowTest {

    static final DesignSpec ACME = DesignSpec.basedOn(DesignSpec.WINDOWS, "Acme")
            .token(Style.Token.ACCENT, Color.rgb(0xE4002B), Color.rgb(0xFF5468)).build();

    /** A provider a plugin cannot work without (as sign-in will be). */
    interface NeededProvider extends Provider {}

    @AfterEach
    void reset() throws Exception {
        Providers.unregister(DesignSpecProvider.class);
        var reset = Appearance.class.getDeclaredMethod("reset");
        reset.setAccessible(true);
        reset.invoke(Appearance.current());
    }

    @Test
    void anAppsDesignSpecProviderReplacesTheBuiltInOnes() {
        assertEquals(List.of(DesignSpec.WINDOWS, DesignSpec.MACOS), Appearance.current().specs(), "without one: webview-4j's own");
        Providers.register(DesignSpecProvider.class, new DesignSpecProvider() {
            public List<DesignSpec> designSpecs() { return List.of(ACME, DesignSpec.MACOS); }
            public DesignSpec initialSpec() { return ACME; }
        });
        assertEquals(List.of(ACME, DesignSpec.MACOS), Appearance.current().specs());
        assertEquals(ACME, Appearance.current().spec(), "the provider's first choice");
    }

    @Test
    void aProviderThatIsNeededAndMissingSaysWhatToDo() {
        MissingProviderException e = assertThrows(MissingProviderException.class, () -> Providers.require(NeededProvider.class, "Sign-in"));
        assertTrue(e.getMessage().startsWith("Sign-in needs a NeededProvider"), e.getMessage());
        assertTrue(e.getMessage().contains("Providers.register(NeededProvider.class"), e.getMessage());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(120)
    void theSettingsButtonOpensTheDialogAndItsChoicesTakeEffect() throws Exception {
        String out = System.getProperty("demo.out");
        Providers.register(DesignSpecProvider.class, () -> List.of(DesignSpec.WINDOWS, DesignSpec.MACOS, ACME));
        Appearance.current().mode(Theme.LIGHT);
        Settings settings = new Settings("Preferences")
                .section("Account", new TextField("Display name"), new Toggle("Email me about orders"));
        Window window = new Window("Settings") {
            {
                setSize(720, 620);
                add(new Text("A shop").variant(TextVariant.TITLE), settings);
            }

            @Override
            protected WindowOptions options(WindowOptions o) {
                return super.options(o).headless(out == null);
            }
        };
        try (App app = App.start()) {
            View view = window.open(app).join().view();
            String clickPreferences = "[...document.querySelectorAll('button')].find(b => b.innerText.trim() === 'Preferences')?.click()";
            for (int i = 0; i < 150 && !settings.isOpen(); i++) {
                if (i % 10 == 0) view.eval(clickPreferences).join();
                Thread.sleep(100);
            }
            assertTrue(settings.isOpen(), "the button opened it");
            until(view, "!!document.querySelector('dialog[open]') && document.querySelector('dialog[open]').innerText.includes('Email me about orders')");
            if (out != null) view.screenshot(Path.of(out).resolve("settings-light.png")).join();

            // Dark, inside the dialog: the whole app follows
            for (int i = 0; i < 150 && Appearance.current().mode() != Theme.DARK; i++) {
                if (i % 10 == 0) view.eval("[...document.querySelectorAll('dialog[open] button, dialog[open] [role=radio]')].find(e => e.innerText.trim() === 'Dark')?.click()").join();
                Thread.sleep(100);
            }
            assertEquals(Theme.DARK, Appearance.current().mode());
            until(view, "document.querySelector('.nt-root')?.dataset.theme === 'dark'");
            if (out != null) {
                Thread.sleep(300);
                view.screenshot(Path.of(out).resolve("settings-dark.png")).join();
            }

            // Close
            for (int i = 0; i < 150 && settings.isOpen(); i++) {
                if (i % 10 == 0) view.eval("[...document.querySelectorAll('dialog[open] button')].find(b => b.innerText.trim() === 'Close')?.click()").join();
                Thread.sleep(100);
            }
            assertFalse(settings.isOpen(), "Close closed it");
        }
    }

    private static void until(View view, String condition) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if ("true".equals(String.valueOf(view.eval(condition).join()))) return;
            Thread.sleep(100);
        }
        fail("never true: " + condition);
    }
}
