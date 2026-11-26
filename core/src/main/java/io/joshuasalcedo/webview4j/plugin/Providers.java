package io.joshuasalcedo.webview4j.plugin;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The providers an app gave its plugins (see {@link Provider}): registered in code, by its
 * {@code WebviewApplication}, or found in {@code META-INF/services}.
 * <pre>{@code
 * Providers.register(DesignSpecProvider.class, () -> List.of(DesignSpec.WINDOWS, brand));
 *
 * // in a plugin:
 * DesignSpecProvider specs = Providers.get(DesignSpecProvider.class, DesignSpecProvider.BUILT_IN);   // a default
 * AuthenticationProvider auth = Providers.require(AuthenticationProvider.class, "Sign-in");          // none: throws
 * }</pre>
 */
public final class Providers {

    private static final Map<Class<?>, Object> REGISTERED = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Object>> FOUND = new ConcurrentHashMap<>();
    private static final java.util.List<Consumer<Class<? extends Provider>>> LISTENERS = new CopyOnWriteArrayList<>();

    private Providers() {}

    /** {@code provider} is the app's {@code type} from now on (in place of one given before). */
    public static <P extends Provider> void register(Class<P> type, P provider) {
        Objects.requireNonNull(type, "type");
        if (!type.isInterface()) throw new IllegalArgumentException("a provider type is the plugin's interface: " + type.getName());
        REGISTERED.put(type, type.cast(Objects.requireNonNull(provider, "provider")));
        for (Consumer<Class<? extends Provider>> l : LISTENERS) l.accept(type);
    }

    /** Takes back the app's {@code type}: the plugin falls back to its default, or to a jar's. */
    public static void unregister(Class<? extends Provider> type) {
        if (REGISTERED.remove(type) != null) for (Consumer<Class<? extends Provider>> l : LISTENERS) l.accept(type);
    }

    /**
     * Registers {@code app} for every provider interface it implements (a {@code WebviewApplication} that
     * implements {@code DesignSpecProvider}, say).
     */
    @SuppressWarnings("unchecked")
    public static void registerAll(Object app) {
        for (Class<?> c = app.getClass(); c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                if (Provider.class.isAssignableFrom(i) && i != Provider.class) register((Class<Provider>) i, (Provider) app);
            }
        }
    }

    /** The app's {@code type}: registered, else from a jar's {@code META-INF/services}; empty when it has none. */
    public static <P extends Provider> Optional<P> find(Class<P> type) {
        Object registered = REGISTERED.get(Objects.requireNonNull(type, "type"));
        if (registered != null) return Optional.of(type.cast(registered));
        return FOUND.computeIfAbsent(type, t -> {
            try {
                return ServiceLoader.load(t).findFirst().map(Object.class::cast);
            } catch (java.util.ServiceConfigurationError e) {
                // a module that does not declare it uses the type: nothing found that way
                return Optional.empty();
            }
        }).map(type::cast);
    }

    /** The app's {@code type}, else {@code fallback} (the plugin's built-in one). */
    public static <P extends Provider> P get(Class<P> type, P fallback) {
        return find(type).orElse(fallback);
    }

    /** The app's {@code type}, else the one {@code fallback} makes. */
    public static <P extends Provider> P get(Class<P> type, Supplier<? extends P> fallback) {
        return find(type).orElseGet(fallback);
    }

    /**
     * The app's {@code type}, which {@code plugin} cannot work without.
     *
     * @throws MissingProviderException when the app gave none, saying what to implement and how to give it
     */
    public static <P extends Provider> P require(Class<P> type, String plugin) {
        return find(type).orElseThrow(() -> new MissingProviderException(type, plugin));
    }

    /** Runs when a provider is registered or taken back: a plugin shows the new one. */
    public static void addChangeListener(Consumer<Class<? extends Provider>> listener) {
        LISTENERS.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Forgets every provider (tests). */
    static void clear() {
        REGISTERED.clear();
        FOUND.clear();
    }
}
