package io.joshuasalcedo.webview4j.ui.state;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** State in a real window: what the screen reads draws it again when it changes, from anywhere; context included. */
@EnabledOnOs(OS.WINDOWS)
class StateWindowTest {

    static final ContextProperty<String> USER = ContextProperty.of("user");
    static final Store<Integer> COUNT = Store.of("count", Integer.class, () -> 0);
    static final CountDownLatch SERVER = new CountDownLatch(1);
    static final ServerState<List<String>> PEOPLE = ServerState.of("people", Codec.listOf(String.class), () -> {
        SERVER.await(10, TimeUnit.SECONDS);   // the server answers when the test says so
        return List.of("Ana", "Ben");
    });

    static final class Dashboard implements Screen {
        @Override
        public Component<?> render() {
            return column(
                    text("hello " + Context.get(USER)),
                    text("count " + COUNT.get()),
                    button("More").onClick(() -> COUNT.update(n -> n + 1)),
                    text(PEOPLE.isLoading() ? "people loading" : "people " + PEOPLE.data(List.of())));
        }
    }

    @Test
    @Timeout(60)
    void whatTheScreenReadsDrawsItAgain() throws Exception {
        try (App app = App.start()) {
            UiWindow.session(app).storage(StateStorage.memory()).provide(USER, "ana");
            UiWindow window = Ui.open(app, new Dashboard(), o -> o.headless(true).size(600, 400)).join();
            View view = window.view();
            until(view, "hello ana");
            until(view, "people loading");

            // a handler changes the store
            view.eval("[...document.querySelectorAll('button')].find(b => b.innerText === 'More').click()").join();
            until(view, "count 1");

            // a change from outside any handler (a timer, a message) draws the window again by itself
            window.session().call(() -> {
                COUNT.set(41);
                return null;
            });
            until(view, "count 41");

            // the server answers: the window is drawn again with the data
            SERVER.countDown();
            until(view, "people [Ana, Ben]");
        }
    }

    static void until(View view, String part) throws InterruptedException {
        String text = "";
        for (int i = 0; i < 100; i++) {
            text = String.valueOf(view.eval("document.body.innerText").join());
            if (text.contains(part)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("never showed '" + part + "': " + text);
    }
}
