package example.notes;

import io.joshuasalcedo.webview4j.Theme;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.annotation.Body;
import io.joshuasalcedo.webview4j.annotation.Put;
import io.joshuasalcedo.webview4j.annotation.WebViewController;
import java.util.Locale;

/**
 * Light or dark, for the window the page is in: the native frame and the page's prefers-color-scheme change
 * together, and @webview4j/ui (theme "system") follows. A route gets the calling page's View by asking for it.
 */
@WebViewController("/appearance")
public class AppearanceController {

    public record Appearance(String theme) {}

    @Put("/theme")
    public void theme(View view, @Body Appearance body) {
        Theme theme = Theme.valueOf(body.theme().toUpperCase(Locale.ROOT));   // system, light or dark
        view.setTheme(theme).join();
    }
}
