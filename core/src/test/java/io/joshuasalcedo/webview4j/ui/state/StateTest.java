package io.joshuasalcedo.webview4j.ui.state;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.valueobject.financial.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Context, Property, Store, Saved, ServerState and Mutation, in sessions of their own (no window). */
class StateTest {

    enum Status { NEW, PAID }

    record Order(String id, Money total, LocalDate placed, Status status, List<String> tags, Optional<String> note, BigDecimal rate) {}

    record Cart(List<String> items) {
        Cart add(String item) {
            return new Cart(java.util.stream.Stream.concat(items.stream(), java.util.stream.Stream.of(item)).toList());
        }
    }

    static final ContextProperty<String> USER = ContextProperty.of("user");
    static final ContextProperty<String> ACCENT = ContextProperty.of("accent", "blue");

    @AfterEach
    void reset() {
        Property.reset();
    }

    static Session session(StateStorage storage) {
        return Session.of(new Object()).storage(storage);
    }

    static <T> T in(Session s, Supplier<T> run) {
        return s.call(run);
    }

    static void until(Supplier<Boolean> done) throws InterruptedException {
        for (int i = 0; i < 200 && !done.get(); i++) Thread.sleep(10);
        assertTrue(done.get(), "never happened");
    }

    // ---- context ----

    @Test
    void contextIsNearestFirstAndKeptByHandlers() throws Exception {
        Session ana = session(StateStorage.memory()).provide(USER, "ana");
        Session ben = session(StateStorage.memory()).provide(USER, "ben");
        assertEquals("ana", in(ana, () -> Context.get(USER)));
        assertEquals("ben", in(ben, () -> Context.get(USER)), "each session its own");
        assertEquals("blue", in(ana, () -> Context.get(ACCENT)), "the fallback");
        assertEquals("red", in(ana, () -> Context.with(ACCENT, "red", () -> Context.get(ACCENT))), "the nearest wins");
        assertEquals("blue", in(ana, () -> Context.get(ACCENT)), "only inside");

        // a handler made inside keeps it, run later on another thread
        java.util.function.Consumer<Object> handler = in(ana, () -> Context.with(ACCENT, "red",
                () -> Context.bind(v -> assertEquals("ana red", Context.get(USER) + " " + Context.get(ACCENT)))));
        CompletableFuture.runAsync(() -> handler.accept(null)).get();
        assertThrows(IllegalStateException.class, () -> Context.get(USER), "outside any session");
    }

    // ---- property ----

    @Test
    void propertyComesFromTheValueThenAPatternThenTheDefaults() {
        Property reference = Property.of().fresh(Duration.ofHours(12)).persist(Duration.ofDays(30));
        Property.forKeys("reference.>", reference);
        assertEquals(reference, Property.resolve("reference.countries", null));
        assertEquals(Property.DEFAULT, Property.resolve("orders", null));
        Property own = Property.of().noRetry();
        assertEquals(own, Property.resolve("reference.countries", own));
        assertTrue(reference.onDisk());
        assertFalse(reference.sensitive().onDisk(), "sensitive: never on disk");
        assertTrue(Keys.matches("orders.*", "orders.2") && !Keys.matches("orders.*", "orders.2.x") && Keys.matches("orders.>", "orders.2.x"));
    }

    // ---- codec ----

    @Test
    void recordsValueObjectsAndCollectionsComeBackAsTheyWere() {
        Order order = new Order("7", Money.php("1499.50"), LocalDate.of(2026, 11, 25), Status.PAID, List.of("rush", "gift"),
                Optional.of("ring twice"), new BigDecimal("0.125"));
        Codec<List<Order>> codec = Codec.listOf(Order.class);
        List<Order> back = codec.decode(codec.encode(List.of(order, new Order("8", Money.usd(5), LocalDate.of(2026, 1, 1), Status.NEW,
                List.of(), Optional.empty(), BigDecimal.ONE))));
        assertEquals(order, back.getFirst());
        assertEquals(Optional.empty(), back.get(1).note());
        Codec<Map<String, Cart>> maps = Codec.of(new Codec.Type<Map<String, Cart>>() {});
        assertEquals(Map.of("a", new Cart(List.of("x"))), maps.decode(maps.encode(Map.of("a", new Cart(List.of("x"))))));
    }

    // ---- store and saved ----

    @Test
    void aStoreIsEachSessionsAndTellsItsReaders() throws Exception {
        Store<Cart> cart = Store.of("cart", Cart.class, () -> new Cart(List.of()));
        Session one = session(StateStorage.memory());
        Session two = session(StateStorage.memory());
        in(one, () -> { cart.update(c -> c.add("tea")); return null; });
        assertEquals(List.of("tea"), in(one, () -> cart.get().items()));
        assertEquals(List.of(), in(two, () -> cart.get().items()), "another session: its own cart");

        // a reader of the count is told when the count changes, not when the same value is set again
        AtomicInteger told = new AtomicInteger();
        Tracking.Reader reader = new Tracking.Reader(told::incrementAndGet, null);
        in(one, () -> Tracking.reading(reader, () -> cart.select(c -> c.items().size())));
        in(one, () -> { cart.set(new Cart(List.of("tea"))); return null; });
        assertEquals(0, told.get(), "the same value: nobody told");
        in(one, () -> { cart.update(c -> c.add("milk")); return null; });
        assertEquals(1, told.get(), "the count changed");
        in(one, () -> { cart.set(new Cart(List.of("coffee", "milk"))); return null; });
        assertEquals(1, told.get(), "items changed, the count did not");
    }

    @Test
    void persistedStateComesBackFromDisk() throws Exception {
        StateStorage disk = StateStorage.memory();
        Store<Cart> cart = Store.of("cart", Cart.class, () -> new Cart(List.of())).property(Property.of().persist());
        Saved<String> tab = Saved.of("orders.tab", String.class, () -> "open");
        Session first = session(disk);
        in(first, () -> {
            cart.update(c -> c.add("tea"));
            tab.set("shipped");
            return null;
        });
        first.flush();
        Session again = session(disk);   // the next run: a new session, the same disk
        assertEquals(List.of("tea"), in(again, () -> cart.get().items()));
        assertEquals("shipped", in(again, () -> tab.get()));
        assertTrue(disk.keys("saved.").contains("saved.orders.tab"));
    }

    // ---- server state ----

    @Test
    void serverStateFetchesOnceWhileFreshAndAgainWhenInvalidated() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        // the first fetch waits until the test has seen it loading: under load it could otherwise be done already
        java.util.concurrent.CountDownLatch seenLoading = new java.util.concurrent.CountDownLatch(1);
        ServerState<List<String>> people = ServerState.of("people", Codec.listOf(String.class), () -> {
            fetches.incrementAndGet();
            try {
                seenLoading.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return List.of("ana", "ben");
        }).property(Property.of().fresh(Duration.ofMinutes(1)));
        Session s = session(StateStorage.memory());

        assertEquals(ServerState.Status.LOADING, in(s, () -> { people.data(); return people.status(); }));
        seenLoading.countDown();
        until(() -> in(s, () -> people.data().isPresent()));
        assertEquals(List.of("ana", "ben"), in(s, () -> people.data(List.of())));
        for (int i = 0; i < 5; i++) in(s, () -> people.data());
        assertEquals(1, fetches.get(), "fresh: not fetched again");

        in(s, () -> { ServerState.invalidate("people"); return null; });
        in(s, () -> people.data());   // shown again after the invalidation
        until(() -> fetches.get() == 2);
    }

    @Test
    void aWindowStartingToShowStaleDataChecksItWithTheServersTag() throws Exception {
        AtomicInteger full = new AtomicInteger();
        java.util.List<String> tagsAsked = new java.util.concurrent.CopyOnWriteArrayList<>();
        ServerState<String> motd = ServerState.conditional("motd", Codec.of(String.class), etag -> {
            tagsAsked.add(String.valueOf(etag));
            if ("v1".equals(etag)) return ServerState.Answer.notModified();
            full.incrementAndGet();
            return ServerState.Answer.of("hello", "v1");
        });   // fresh 0: checked each time a window starts showing it
        Session s = session(StateStorage.memory());
        Tracking.Reader first = new Tracking.Reader(() -> {}, null);
        in(s, () -> Tracking.reading(first, motd::data));
        until(() -> in(s, () -> motd.data().isPresent()));

        // redrawn: the same window still shows it, so it is not checked again
        Tracking.Reader redraw = new Tracking.Reader(() -> {}, first);
        in(s, () -> Tracking.reading(redraw, motd::data));
        Thread.sleep(50);
        assertEquals(List.of("null"), tagsAsked);

        // another window starts showing it: checked with the tag, the server says 304, nothing downloaded
        Tracking.Reader other = new Tracking.Reader(() -> {}, null);
        in(s, () -> Tracking.reading(other, motd::data));
        until(() -> tagsAsked.size() == 2);
        assertEquals(List.of("null", "v1"), tagsAsked);
        assertEquals(1, full.get());
        assertEquals("hello", in(s, () -> motd.data("")));
    }

    @Test
    void letGoFromMemoryItIsReadBackFromDiskWithoutAFetch() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        StateStorage disk = StateStorage.memory();
        ServerState<String> report = ServerState.of("report", String.class, () -> "report " + fetches.incrementAndGet())
                .property(Property.of().fresh(Duration.ofHours(1)).keepInMemory(Duration.ZERO).persist());
        Session s = session(disk);
        in(s, report::data);
        until(() -> in(s, () -> report.data().isPresent()));
        s.flush();
        SessionState.of(s).sweep();   // no window shows it: let go from memory
        Session nextRun = session(disk);
        assertEquals("report 1", in(nextRun, () -> report.data("")), "from disk, at once");
        Thread.sleep(50);
        assertEquals(1, fetches.get(), "fresh on disk: not fetched");
    }

    @Test
    void failuresAreRetriedThenKept() throws Exception {
        AtomicInteger tries = new AtomicInteger();
        ServerState<String> flaky = ServerState.of("flaky", String.class, () -> {
            throw new IllegalStateException("down, try " + tries.incrementAndGet());
        }).property(Property.of().retry(2, Duration.ofMillis(5)));
        Session s = session(StateStorage.memory());
        in(s, flaky::data);
        until(() -> in(s, flaky::status) == ServerState.Status.ERROR);
        assertEquals(3, tries.get(), "the first try and 2 retries");
        assertEquals("down, try 3", in(s, () -> flaky.error().orElseThrow().getMessage()));
    }

    // ---- mutation ----

    @Test
    void aMutationInvalidatesAndPutsBackWhatItChangedWhenItFails() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        ServerState<List<String>> orders = ServerState.of("orders.open", Codec.listOf(String.class), () -> {
            fetches.incrementAndGet();
            return List.of("1", "2");
        }).property(Property.of().fresh(Duration.ofHours(1)));
        Session s = session(StateStorage.memory());
        Tracking.Reader shown = new Tracking.Reader(() -> {}, null);
        in(s, () -> Tracking.reading(shown, orders::data));
        until(() -> in(s, () -> orders.data().isPresent()));

        Mutation<String> ship = Mutation.of((String id) -> "shipped " + id).invalidates("orders.>");
        assertEquals("shipped 1", in(s, () -> ship.run("1")).get());
        until(() -> fetches.get() == 2);   // shown, so fetched again at once

        Mutation<String> remove = Mutation.of((String id) -> { throw new IllegalStateException("no"); })
                .optimistic(orders, (List<String> list, String id) -> list.stream().filter(x -> !x.equals(id)).toList());
        CompletableFuture<Object> failed = in(s, () -> remove.run("1"));
        assertThrows(Exception.class, failed::get);
        assertEquals(List.of("1", "2"), in(s, () -> orders.data(List.of())), "put back");
        assertEquals("no", in(s, () -> remove.error().orElseThrow().getMessage()));
    }
}
