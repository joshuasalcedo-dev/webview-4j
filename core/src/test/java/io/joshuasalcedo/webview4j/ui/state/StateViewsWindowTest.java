package io.joshuasalcedo.webview4j.ui.state;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.DataTable;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Server state's states on screen, with nothing written by hand: loadable(...) and a collection's .state(...). */
@EnabledOnOs(OS.WINDOWS)
class StateViewsWindowTest {

    record Order(String id, String customer) {}

    static final AtomicBoolean DOWN = new AtomicBoolean(true);
    static final AtomicInteger TRIES = new AtomicInteger();
    static final ServerState<String> PROFILE = ServerState.of("profile", String.class, () -> {
        TRIES.incrementAndGet();
        if (DOWN.get()) throw new IllegalStateException("down");
        return "Ana Reyes";
    }).property(Property.of().noRetry());
    static final ServerState<List<Order>> ORDERS = ServerState.of("orders", Codec.listOf(Order.class), () -> {
        Thread.sleep(800);
        return List.of(new Order("1", "Grace Hopper"));
    });

    static final class Page implements Screen {
        @Override
        public Component<?> render() {
            return column(
                    loadable(PROFILE, name -> text("profile of " + name)),
                    DataTable.of("Orders", Order.class, ORDERS.data(List.of())).state(ORDERS).height(240));
        }
    }

    @Test
    @Timeout(60)
    void loadingErrorsAndTryAgainCome() throws Exception {
        try (App app = App.start()) {
            UiWindow.session(app).storage(StateStorage.memory());
            View view = Ui.open(app, new Page(), o -> o.headless(true).size(700, 600)).join().view();

            // the table loads: grey rows first, then its rows
            StateWindowTest.until(view, "");
            for (int i = 0; i < 50 && !Boolean.TRUE.equals(view.eval("!!document.querySelector('.nt-skeleton')").join()); i++) Thread.sleep(50);
            assertEquals(true, view.eval("!!document.querySelector('.nt-skeleton')").join(), "grey rows while it first loads");
            StateWindowTest.until(view, "Grace Hopper");

            // the profile failed: its error, Try again, and then it shows
            StateWindowTest.until(view, "This could not be loaded");
            DOWN.set(false);
            view.eval("[...document.querySelectorAll('button')].find(b => b.innerText === 'Try again').click()").join();
            StateWindowTest.until(view, "profile of Ana Reyes");
            assertEquals(2, TRIES.get());
        }
    }
}
