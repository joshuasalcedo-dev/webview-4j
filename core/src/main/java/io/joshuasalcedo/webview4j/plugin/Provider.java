package io.joshuasalcedo.webview4j.plugin;

/**
 * What an app implements for a plugin: the design specs it offers ({@code DesignSpecProvider}), how people sign in,
 * where updates come from. Each plugin that needs one has its own interface extending this one; a plugin with a
 * sensible default works without it, one that cannot throws {@link MissingProviderException} saying what to
 * implement.
 * <p>
 * An app gives its providers in any of three ways, the first that has one wins:
 * <ol>
 *   <li>{@link Providers#register} in code ({@code Providers.register(DesignSpecProvider.class, () -> List.of(...))});</li>
 *   <li>its {@link io.joshuasalcedo.webview4j.WebviewApplication} implements the interface (registered as it starts);</li>
 *   <li>a jar lists it in {@code META-INF/services/<the interface>} (Java's {@link java.util.ServiceLoader}).</li>
 * </ol>
 */
public interface Provider {}
