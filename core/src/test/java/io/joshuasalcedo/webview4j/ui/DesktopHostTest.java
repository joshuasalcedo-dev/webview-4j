package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A desktop window's screen has its host too: the system's dialogs, folders, the native window. */
@EnabledOnOs(OS.WINDOWS)
class DesktopHostTest {

    static final class Where implements Screen {
        @Override
        public Component<?> render() {
            Host host = Host.current();
            return text("web " + host.onWeb() + " folders " + host.supports(Host.Feature.FOLDERS) + " window "
                    + host.supports(Host.Feature.NATIVE_WINDOW) + " tray " + host.supports(Host.Feature.TRAY));
        }
    }

    @Test
    @Timeout(60)
    void aDesktopScreensHostIsTheSystems() throws Exception {
        try (App app = App.start()) {
            View view = Ui.open(app, new Where(), o -> o.headless(true)).join().view();
            WebHostBrowserTest.until(view, "web false folders true window true tray false");
        }
    }
}
