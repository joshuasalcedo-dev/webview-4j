package io.joshuasalcedo.swaswa.state;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.app.SwaApplication;
import io.joshuasalcedo.swaswa.component.Property;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Div;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class StateTest {

    record Cart(List<String> items) {}

    static final Store<Cart> CART = Store.of("cart", Cart.class, () -> new Cart(List.of()));
    static final Store<Cart> KEPT_CART = Store.of("kept-cart", Cart.class, () -> new Cart(List.of())).lifetime(Lifetime.of().persist());

    @Test
    void aStoreIsALivePropertyPerSession() {
        Session one = new Session();
        Session two = new Session();
        List<Integer> told = new ArrayList<>();
        CART.select(one, c -> c.items().size()).addListener((old, now) -> told.add(now));

        CART.update(one, c -> new Cart(List.of("apple")));
        CART.in(one).set(new Cart(List.of("pear")));                 // same size: the count's listeners are not told
        CART.update(one, c -> new Cart(List.of("pear", "fig")));

        assertEquals(List.of(1, 2), told);
        assertEquals(List.of("pear", "fig"), CART.get(one).items());
        assertEquals(List.of(), CART.get(two).items(), "another session has its own");
        assertSame(CART.in(one), CART.in(one), "one property per session");
        assertThrows(IllegalStateException.class, () -> CART.select(one, c -> c.items().size()).set(9), "a part cannot be set");
    }

    @Test
    void aPersistedStoreComesBackInTheNextSessionAndResetTakesItOff() {
        StateStorage disk = StateStorage.memory();
        Session before = new Session().storage(disk);
        KEPT_CART.set(before, new Cart(List.of("tea")));
        before.flush();

        Session after = new Session().storage(disk);
        assertEquals(List.of("tea"), KEPT_CART.get(after).items());

        KEPT_CART.reset(after);
        after.flush();
        assertEquals(List.of(), KEPT_CART.get(after).items());
        assertTrue(disk.get("store.kept-cart").isEmpty(), "off the disk");

        CART.set(after, new Cart(List.of("x")));
        after.flush();
        assertTrue(disk.get("store.cart").isEmpty(), "a store not persisted is never written");
    }

    @Test
    void savedIsOnDiskByDefaultAndPatternsGiveLifetimes() {
        StateStorage disk = StateStorage.memory();
        Saved<String> tab = Saved.of("orders.tab", String.class, () -> "open");
        Session s = new Session().storage(disk);
        tab.set(s, "shipped");
        s.flush();
        assertEquals("shipped", tab.get(new Session().storage(disk)));

        Store<Integer> ref = Store.of("reference.rates", Integer.class, () -> 0);
        Session p = new Session().storage(disk).lifetime("reference.>", Lifetime.of().persist());
        ref.set(p, 7);
        p.flush();
        assertEquals(7, ref.get(new Session().storage(disk).lifetime("reference.>", Lifetime.of().persist())));
        assertEquals(0, ref.get(new Session().storage(disk)), "without the pattern it is not read from disk");
    }

    @Test
    void serverStateFetchesOnceCachesAndRefetchesWhenInvalidated() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ServerState<Integer> count = ServerState.of("count", Integer.class, calls::incrementAndGet);
        Session s = new Session();

        Property<Integer> data = count.data(s);
        List<Integer> seen = new CopyOnWriteArrayList<>();
        data.addListener((old, now) -> seen.add(now));        // shown: an invalidation fetches at once
        waitFor(() -> data.get() != null);
        assertEquals(1, data.get());
        assertEquals(ServerState.Status.SUCCESS, count.status(s).get());

        count.data(s);                                          // fresh 0: stale, asked for again: fetched again
        waitFor(() -> calls.get() == 2 && !count.fetching(s).get());

        s.invalidate("cou*");                                   // no match
        s.invalidate("count");
        waitFor(() -> data.get() == 3);
        assertEquals(List.of(2, 3), seen.subList(seen.size() - 2, seen.size()), "the refetch and the invalidation, in order");
        assertThrows(IllegalStateException.class, () -> data.set(9), "read-only: the state is set through ServerState");
    }

    @Test
    void aFailingFetchRetriesThenShowsTheError() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ServerState<String> broken = ServerState.<String>of("broken", String.class, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("down");
        }).lifetime(Lifetime.of().retry(2, Duration.ofMillis(5)));
        Session s = new Session();

        broken.data(s);
        waitFor(() -> broken.status(s).get() == ServerState.Status.ERROR);
        assertEquals(3, calls.get(), "the first try and two retries");
        assertEquals("down", broken.error(s).get().getMessage());
    }

    @Test
    void anUnchangedAnswerKeepsTheData() throws Exception {
        List<String> tags = new CopyOnWriteArrayList<>();
        ServerState<String> doc = ServerState.conditional("doc", Codec.of(String.class), etag -> {
            tags.add(String.valueOf(etag));
            return etag == null ? ServerState.Answer.of("v1", "e1") : ServerState.Answer.notModified();
        });
        Session s = new Session();
        Property<String> data = doc.data(s);
        waitFor(() -> "v1".equals(data.get()) && !doc.fetching(s).get());
        doc.data(s);                                            // asked for again, stale: "changed since e1?" 304
        waitFor(() -> tags.size() == 2 && !doc.fetching(s).get());
        assertEquals(List.of("null", "e1"), tags);
        assertEquals("v1", data.get());
    }

    @Test
    void aMutationIsPendingInvalidatesAndPutsBackAnOptimisticChange() throws Exception {
        AtomicInteger server = new AtomicInteger(10);
        ServerState<Integer> stock = ServerState.of("stock.apples", Integer.class, server::get)
                .lifetime(Lifetime.of().fresh(Duration.ofHours(1)));
        Session s = new Session();
        Property<Integer> shown = stock.data(s);
        shown.addListener((o, n) -> {});
        waitFor(() -> shown.get() != null);

        Mutation<Integer> sell = Mutation.of((Integer n) -> server.addAndGet(-n)).invalidates("stock.>")
                .optimistic(stock, (now, n) -> now - n);
        Object answer = sell.run(s, 3).get(5, TimeUnit.SECONDS);
        assertEquals(7, answer);
        waitFor(() -> shown.get() == 7 && !stock.fetching(s).get());
        assertFalse(sell.pending(s).get());

        Mutation<Integer> fail = Mutation.<Integer>of(n -> { throw new IllegalStateException("no"); })
                .optimistic(stock, (now, n) -> now - n);
        assertThrows(Exception.class, () -> fail.run(s, 5).get(5, TimeUnit.SECONDS));
        assertEquals(7, shown.get(), "put back");
        assertEquals("no", fail.error(s).get().getMessage());
    }

    static final ContextProperty<String> ACCENT = ContextProperty.of("accent", String.class, "blue");
    static final ContextProperty<String> USER = ContextProperty.of("user", String.class);

    static final class Page extends SwaApplication {
        final Div sidebar = new Div();
        final Button save = new Button("Save");

        @Override
        protected void build() {
            sidebar.add(save);
            getMain().add(sidebar);
        }
    }

    @Test
    void contextIsTheNearestFromTheComponentUpToTheSession() {
        Page page = new Page();
        page.toHtml();   // made
        page.session().provide(USER, "ana");

        assertEquals("ana", Context.get(page.save, USER), "from the session, at the top");
        assertEquals("blue", Context.get(page.save, ACCENT), "the fallback");
        Context.provide(page.sidebar, ACCENT, "red");
        assertEquals("red", Context.get(page.save, ACCENT), "the sidebar's, nearer");
        Context.provide(page.save, USER, null);
        assertTrue(Context.find(page.save, USER).isEmpty(), "provided as null: hides the session's");
        Context.remove(page.save, USER);
        assertEquals("ana", Context.get(page.save, USER));
        assertSame(page.session(), Context.session(page.save).orElseThrow());
        assertTrue(Context.session(new Div()).isEmpty(), "in no page");
        page.session().close();
    }

    @Test
    void anAppHandsOutItsStateLive() {
        Page page = new Page();
        page.session().storage(StateStorage.memory());
        Property<Cart> cart = page.state(CART);
        cart.set(new Cart(List.of("milk")));
        assertEquals(List.of("milk"), CART.get(page.session()).items());
        page.session().close();
        assertTrue(page.session().isClosed());
    }

    @Test
    void keysMatchAsSubjects() {
        assertTrue(Keys.matches("orders.>", "orders.page.2"));
        assertFalse(Keys.matches("orders.>", "orders"));
        assertTrue(Keys.matches("orders.*", "orders.open"));
        assertFalse(Keys.matches("orders.*", "orders.open.2"));
        assertEquals("orders.page_two", Keys.of("orders", "page two"));
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > until) fail("not in 5 seconds");
            Thread.sleep(5);
        }
    }
}
