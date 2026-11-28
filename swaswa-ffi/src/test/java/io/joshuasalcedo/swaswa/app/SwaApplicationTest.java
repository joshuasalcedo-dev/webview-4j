package io.joshuasalcedo.swaswa.app;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.component.Container;
import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.AppMode;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Html;
import io.joshuasalcedo.swaswa.html.Span;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** An app is the root component of its page: its parts, its document's properties, its life, the same page for the web. */
@Timeout(60)
class SwaApplicationTest {

    static final List<String> life = new CopyOnWriteArrayList<>();
    static final AtomicReference<Object> seen = new AtomicReference<>();

    public static class Notes extends SwaApplication {
        final AtomicInteger clicks = new AtomicInteger();

        @Override
        protected void init() {
            life.add("init " + args());
        }

        @Override
        protected void build() {
            life.add("build " + mode());
            setTitle("swaswa test: Notes").setLang("fil").setTheme(Theme.DARK);
            addStyleSheet(new StyleSheet().rule("main", s -> s.set(CssProperty.PADDING, "13px")));
            getHeader().add(Html.h1("Notes"));
            Button add = Html.button("Add").setId("add");
            add.addActionListener(e -> clicks.incrementAndGet());
            getMain().add(add, Html.span("0").setId("count"));
            getFooter().add(Html.small("v1"));
        }

        @Override
        protected void started(App app, View window) throws Exception {
            life.add("started");
            Object state = window.eval("({title: document.title, lang: document.documentElement.lang,"
                    + " scheme: document.documentElement.style.colorScheme, parts: [...document.body.children].map(e => e.tagName).join(' '),"
                    + " padding: getComputedStyle(document.querySelector('main')).paddingTop})").join();
            seen.set(state);
            window.eval("document.getElementById('add').click()").join();
            for (int i = 0; i < 100 && clicks.get() == 0; i++) Thread.sleep(30);
            ((Span) getMain().lookup("#count").orElseThrow()).setText(String.valueOf(clicks.get()));
            getDocument().flush().join();
            life.add("count " + window.eval("document.getElementById('count').textContent").join());
            window.close();   // the person closes it
        }

        @Override
        protected void stop() {
            life.add("stop");
        }

        static int main(String... args) {
            return launch(args);   // finds Notes, the class calling it
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void itIsItsPageInAWindowFromInitToStop() {
        life.clear();
        Notes.main("--fast");
        assertEquals(List.of("init [--fast]", "build " + AppMode.DEV, "started", "count 1", "stop"), life);
        assertEquals(java.util.Map.of("title", "swaswa test: Notes", "lang", "fil", "scheme", "dark", "parts", "HEADER MAIN FOOTER",
                "padding", "13px"), seen.get(), "its document's properties and parts, in the page");
    }

    public static class Plain extends SwaApplication {
        @Override
        protected void build() {}
    }

    @Test
    void itIsAContainerAndItsPartsKeepHtmlsOrder() {
        Plain app = new Plain();
        assertInstanceOf(Container.class, app, "an app is a component: the root of its page");
        app.getFooter();
        app.getAside();
        app.getHeader();
        app.getNav();
        assertEquals(List.of("header", "nav", "main", "aside", "footer"),
                app.getComponents().stream().map(n -> ((io.joshuasalcedo.swaswa.html.HtmlElement<?>) n).getTag()).toList(),
                "asked for in any order, placed in HTML's");
    }

    @Test
    void theSamePageGoesOnTheWebAsItIs(@TempDir Path dir) throws Exception {
        Plain app = new Plain();
        app.setTitle("Notes & more").setLang("en").setDescription("A notes app").putMeta("author", "swaswa").setTheme(Theme.LIGHT);
        app.addStyleSheet(new StyleSheet().rule("main", s -> s.set(CssProperty.MAX_WIDTH, "60ch")));
        app.getMain().add(Html.h1("Hello"));
        String html = app.toHtml();
        assertTrue(html.startsWith("<!doctype html>\n<html lang=\"en\" style=\"color-scheme: light\">"), html);
        for (String part : List.of("<title>Notes &amp; more</title>", "<meta name=\"description\" content=\"A notes app\">",
                "<meta name=\"author\" content=\"swaswa\">", "<style>main { max-width: 60ch }\n</style>", "<body><main><h1>Hello</h1></main></body>")) {
            assertTrue(html.contains(part), part + " in " + html);
        }
        Path file = app.export(dir.resolve("site"));
        assertEquals(html, Files.readString(file));
    }

    public static class Broken extends SwaApplication {
        @Override
        protected void build() {
            throw new IllegalStateException("no settings");
        }

        @Override
        protected void stop() {
            life.add("stopped anyway");
        }
    }

    @Test
    void aBuildThatFailsSaysWhy() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> SwaApplication.launch(Broken.class));
        assertEquals("no settings", e.getCause().getMessage());
    }

    @Test
    void launchIsCalledFromTheAppsOwnClass() {
        assertThrows(IllegalStateException.class, () -> SwaApplication.launch());
    }
}
