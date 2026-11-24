package example.notes;

import io.joshuasalcedo.webview4j.ApiCall;
import io.joshuasalcedo.webview4j.Capture;
import io.joshuasalcedo.webview4j.Closed;
import io.joshuasalcedo.webview4j.LogRecord;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WebSocketFrame;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.annotation.Body;
import io.joshuasalcedo.webview4j.annotation.Delete;
import io.joshuasalcedo.webview4j.annotation.Post;
import io.joshuasalcedo.webview4j.annotation.Put;
import io.joshuasalcedo.webview4j.annotation.WebViewController;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The showcase window, and the website it embeds: {@code view.embed(...)} opens a site inside the showcase's own
 * window, {@code place(...)} keeps it over the page's browser panel, and the site's network traffic (fetch/XHR,
 * WebSockets) and console come back as events, which go on to the page as {@code showcase.network}.
 */
@WebViewController("/showcase")
public class ShowcaseController {

    /** Where the page's browser panel is: CSS pixels of the page, and the page's devicePixelRatio. */
    public record Panel(double x, double y, double width, double height, double pixelRatio, boolean visible) {}

    public record Browse(String url, Panel panel) {}

    public record Opened(String view) {}

    /** The site each showcase window embeds, by the showcase's view id. */
    private final Map<String, View> sites = new ConcurrentHashMap<>();

    /** Opens the showcase in a window of its own. */
    @Post("/open")
    public void open(View caller) {
        caller.app().open(WindowOptions.app("web").page("showcase.html").title("Showcase").size(1280, 820)
                .minSize(720, 480).decorations(false)).join();
    }

    /** Opens the component gallery: a window written only in Java (GalleryScreen). */
    @Post("/gallery")
    public void gallery(View caller) {
        GalleryScreen.open(caller.app());
    }

    /** Shows {@code url} in the page's browser panel: embeds it the first time, navigates it after. */
    @Post("/browse")
    public Opened browse(View page, @Body Browse body) {
        String url = body.url().contains("://") ? body.url() : "https://" + body.url();
        View site = sites.get(page.id());
        if (site != null) {
            site.navigate(url).join();
        } else {
            Rect r = logical(page, body.panel());
            // Someone else's site: no bridge for it (no origins), and its traffic reported.
            site = page.embed(WindowOptions.url(url).capture(Capture.API, Capture.WEBSOCKET, Capture.CONSOLE),
                    r.x, r.y, r.width, r.height).join();
            sites.put(page.id(), site);
            report(page, site);
        }
        if (!body.panel().visible()) site.hide().join();
        return new Opened(site.id());
    }

    /** The panel moved or changed size, or was covered or uncovered. */
    @Put("/browse/panel")
    public void panel(View page, @Body Panel panel) {
        View site = sites.get(page.id());
        if (site == null) return;
        if (!panel.visible()) {
            site.hide().join();
            return;
        }
        Rect r = logical(page, panel);
        site.place(r.x, r.y, r.width, r.height).join();
        site.show().join();
    }

    @Delete("/browse")
    public void close(View page) {
        View site = sites.remove(page.id());
        if (site != null) site.close().join();
    }

    private void report(View page, View site) {
        site.subscribe(ApiCall.class, call -> page.emit("showcase.network", Map.of(
                "kind", "http", "level", call.status() >= 400 ? "warn" : "info",
                "message", call.method() + " " + call.url() + " → " + call.status() + " (" + call.durationMs() + " ms)")));
        site.subscribe(WebSocketFrame.class, frame -> page.emit("showcase.network", Map.of(
                "kind", "websocket", "level", "debug",
                "message", "WebSocket " + frame.direction().name().toLowerCase() + " " + frame.url()
                        + (frame.data() == null ? "" : ": " + abbreviate(frame.data())))));
        site.subscribe(LogRecord.class, log -> page.emit("showcase.network", Map.of(
                "kind", "console", "level", log.level().name().toLowerCase(), "message", "console: " + abbreviate(log.message()))));
        site.subscribe(Closed.class, closed -> sites.remove(page.id(), site));
    }

    private static String abbreviate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 199) + "…";
    }

    private record Rect(double x, double y, double width, double height) {}

    /**
     * The panel in the window's logical pixels: the page measures CSS pixels, which are logical pixels scaled by the
     * page's zoom (devicePixelRatio over the display's scale factor, e.g. Windows' text size).
     */
    private static Rect logical(View page, Panel panel) {
        double zoom = panel.pixelRatio() / page.geometry().join().scaleFactor();
        return new Rect(panel.x() * zoom, panel.y() * zoom, Math.max(1, panel.width() * zoom), Math.max(1, panel.height() * zoom));
    }
}
