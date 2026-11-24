package io.joshuasalcedo.webview4j.ui.components;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Host;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Markdown from Java, in a real window: what it shows, what it never runs, and its links back in Java. */
class MarkdownWindowTest {

    static final String TEXT = """
            ## Release notes

            Version **0.2.0**, with `code` and a [link](https://example.com/notes).

            | Part | Where |
            | --- | --- |
            | State | ui.state |

            - [x] tested

            <img src=x onerror="window.ran = true"><script>window.ran = true</script> [bad](javascript:window.ran=true)
            """;

    static final class Notes implements Screen {
        String opened = "";

        @Override
        public Component<?> render() {
            return column(markdown(TEXT).onLink(href -> opened = href), text("opened " + opened));
        }
    }

    @Test
    void onlyWebLinksOpen() {
        assertTrue(Host.openable("https://example.com/a?b=c"));
        assertTrue(Host.openable("mailto:team@example.com"));
        assertFalse(Host.openable("javascript:alert(1)"));
        assertFalse(Host.openable("file:///C:/Windows/System32/calc.exe"));
        assertFalse(Host.openable("https://example.com/\" & calc"));
        assertFalse(Host.openable(null));
        assertFalse(Host.openable("https://example.com/a b"), "no spaces: one argument, one link");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(60)
    void itShowsMarkdownAndNeverRunsWhatIsInIt() throws Exception {
        try (App app = App.start()) {
            Notes screen = new Notes();
            View view = Ui.open(app, screen, o -> o.headless(true).size(800, 600)).join().view();
            for (int i = 0; i < 100 && !String.valueOf(view.eval("document.body.innerText").join()).contains("Release notes"); i++) Thread.sleep(100);
            String shape = String.valueOf(view.eval("""
                    (() => { const m = document.querySelector('.nt-markdown');
                      return [m.querySelector('h2')?.innerText, m.querySelector('strong')?.innerText, m.querySelector('code')?.innerText,
                        m.querySelectorAll('td').length, !!m.querySelector('input[type=checkbox][checked]'), !!m.querySelector('script, img'),
                        [...m.querySelectorAll('a')].map(a => a.getAttribute('href') || '').join(' ')].join('|'); })()
                    """).join());
            assertEquals("Release notes|0.2.0|code|2|true|false|https://example.com/notes ", shape);
            Thread.sleep(300);
            assertNotEquals("true", String.valueOf(view.eval("String(window.ran)").join()), "nothing in it ran");

            view.eval("[...document.querySelectorAll('.nt-markdown a')].find(a => a.innerText === 'link').click()").join();
            for (int i = 0; i < 50 && !screen.opened.equals("https://example.com/notes"); i++) Thread.sleep(100);
            assertEquals("https://example.com/notes", screen.opened, "the link went to Java, not the window");
        }
    }
}
