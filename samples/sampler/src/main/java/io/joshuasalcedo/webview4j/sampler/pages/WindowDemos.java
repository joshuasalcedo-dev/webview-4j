package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import io.joshuasalcedo.webview4j.ui.components.*;

/** The Sampler's pages: more windows, a plain one and one owned by the Sampler's (a tool window). */
final class WindowDemos implements Demos {

    private int opened;
    private String said = "Each button opens a real window.";

    /** What a new window shows: its name, and a button that closes it. */
    record Note(String name, String body) implements Screen {
        @Override
        public Component<?> render() {
            return column(title(name), text(body).tone(Tone.SECONDARY),
                    button("Close").onClick(() -> UiWindow.current().ifPresent(w -> w.view().close()))).padding(20).gap(10);
        }
    }

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Window", "More windows", () -> column(
                        row(button("Open a window").accent().onClick(() -> open(false)),
                                button("Open a tool window").onClick(() -> open(true))).gap(8),
                        text(said).tone(Tone.SECONDARY),
                        paragraph("A tool window is owned by this one: it stays above it, is minimized with it, has no "
                                + "taskbar button, and closes when the Sampler closes.")).gap(12),
                """
                View main = UiWindow.current().orElseThrow().view();
                Ui.open(main.app(), new Note("Window 1", "..."), o -> o.title("Window 1").size(420, 260))
                Ui.open(main.app(), new Note("Tool", "..."), o -> o.title("Tool").size(300, 260).owner(main))""");
    }

    private void open(boolean owned) {
        UiWindow here = UiWindow.current().orElseThrow();
        View main = here.view();
        String name = (owned ? "Tool window " : "Window ") + (++opened);
        String text = owned ? "Owned by the Sampler: try minimizing the Sampler." : "A window of its own, with its own taskbar button.";
        Ui.open(main.app(), new Note(name, text), o -> {
            o.title(name).size(owned ? 320 : 440, 240);
            return owned ? o.owner(main) : o;
        });
        said = "Opened " + name + ".";
    }
}
