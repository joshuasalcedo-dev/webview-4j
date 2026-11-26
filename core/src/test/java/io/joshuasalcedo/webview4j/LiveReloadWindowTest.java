package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** A WebviewApplication while developing: its source changes, and the open app shows the change, in the same process. */
@EnabledOnOs(OS.WINDOWS)
class LiveReloadWindowTest {

    private static final String SOURCE = """
            package demo;

            import io.joshuasalcedo.webview4j.*;
            import io.joshuasalcedo.webview4j.ui.Window;
            import io.joshuasalcedo.webview4j.ui.components.*;

            public class LiveApp extends WebviewApplication {
                @Override
                public void start(App app) {
                    new Window("Live reload") {
                        {
                            setSize(480, 260);
                            add(new Text("%s").variant(TextVariant.TITLE), new Button("Click me").accent());
                        }
                        @Override
                        protected WindowOptions options(WindowOptions o) {
                            return super.options(o).headless(System.getProperty("demo.out") == null);
                        }
                    }.open(app);
                }
            }
            """;

    @Test
    @Timeout(120)
    void anEditedSourceShowsInTheOpenApp(@TempDir Path project) throws Exception {
        Path source = project.resolve("src/main/java/demo/LiveApp.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, SOURCE.formatted("Version 1"));
        // the app as launched: its classes built once, as the IDE or Gradle would
        Path launched = project.resolve("build/classes");
        Files.createDirectories(launched);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", launched.toString(), "-classpath", System.getProperty("java.class.path"), source.toString()));
        WebviewApplication application;
        try (URLClassLoader loader = new URLClassLoader(new URL[] {launched.toUri().toURL()}, getClass().getClassLoader())) {
            application = (WebviewApplication) loader.loadClass("demo.LiveApp").getDeclaredConstructor().newInstance();
        }

        System.setProperty("webview4j.dev", "true");
        System.setProperty("webview4j.dev.sources", project.resolve("src/main/java").toString());
        int[] code = {-1};
        Thread loop = Thread.ofPlatform().name("live-reload-test-loop").start(() -> code[0] = application.run());
        try {
            App app = until(application::getApp);
            View first = untilShown(app, "Version 1");
            // the person moved and sized it: the reload keeps it there
            first.setPosition(260, 180).join();
            first.setSize(520, 300).join();
            Thread.sleep(300);
            Geometry moved = first.geometry().join();

            Files.writeString(source, SOURCE.formatted("Version 2"));
            View second = untilShown(app, "Version 2");
            Geometry now = second.geometry().join();
            assertEquals(moved.x(), now.x(), 2, "the same place");
            assertEquals(moved.y(), now.y(), 2, "the same place");
            assertEquals(moved.width(), now.width(), 2, "the same size");
            assertEquals(moved.height(), now.height(), 2, "the same size");
            assertNotEquals(first.id(), second.id(), "a new window, from the new classes");
            for (int i = 0; i < 50 && app.views().join().size() != 1; i++) Thread.sleep(100);
            assertEquals(1, app.views().join().size(), "the window from before closed");
            assertNotSame(application, application.current().orElseThrow(), "a new instance of the app");

            String out = System.getProperty("demo.out");
            if (out != null) second.screenshot(Path.of(out).resolve("live-reload.png")).join();

            // the dev window's Reload app: again now, unchanged sources, a new window in the same place
            io.joshuasalcedo.webview4j.plugin.devtools.LiveReload.running().orElseThrow().reloadNow();
            View third = null;
            for (int i = 0; i < 100 && third == null; i++) {
                Thread.sleep(100);
                for (ViewInfo info : app.views().join()) if (!info.id().equals(second.id())) third = app.view(info.id());
            }
            assertNotNull(third, "Reload app opened the app again");
            for (int i = 0; i < 50 && app.views().join().size() != 1; i++) Thread.sleep(100);
            assertEquals(moved.x(), third.geometry().join().x(), 2, "still in the same place");
            second = third;

            // a source that does not compile: the app stays as it was
            Files.writeString(source, SOURCE.formatted("Version 3").replace("public void start", "public void start(("));
            Thread.sleep(LiveReloadTiming.SETTLE);
            assertEquals(List.of(second.id()), app.views().join().stream().map(ViewInfo::id).toList());

            application.quit();
            loop.join(10_000);
            assertEquals(0, code[0], "run() returned once the app quit");
        } finally {
            System.clearProperty("webview4j.dev");
            System.clearProperty("webview4j.dev.sources");
            if (loop.isAlive()) application.quit();
        }
    }

    /** Long enough for the dev server to see a change, wait for quiet, and compile. */
    private static final class LiveReloadTiming {
        static final long SETTLE = 4_000;
    }

    private static <T> T until(java.util.function.Supplier<T> value) throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            T v = value.get();
            if (v != null) return v;
            Thread.sleep(100);
        }
        throw new AssertionError("never came");
    }

    /** The open view that shows {@code text}. */
    private static View untilShown(App app, String text) throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            for (ViewInfo info : app.views().join()) {
                View view = app.view(info.id());
                try {
                    Object body = view.eval("document.body ? document.body.innerText : ''").join();
                    if (String.valueOf(body).contains(text)) return view;
                } catch (RuntimeException closedMeanwhile) {
                    // the view closed between the list and the look
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("no window shows " + text);
    }
}
