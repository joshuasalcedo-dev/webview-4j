package io.joshuasalcedo.webview4j.plugin;

/**
 * A plugin was used without the provider it cannot work without: the message says which interface to implement and
 * the ways to give it.
 */
public final class MissingProviderException extends IllegalStateException {

    private final Class<? extends Provider> type;

    public MissingProviderException(Class<? extends Provider> type, String plugin) {
        super(plugin + " needs a " + type.getSimpleName() + ". Implement " + type.getName()
                + " and give it in one of three ways: Providers.register(" + type.getSimpleName() + ".class, yours);"
                + " let your WebviewApplication implement it; or list it in META-INF/services/" + type.getName() + ".");
        this.type = type;
    }

    /** The provider interface that is missing. */
    public Class<? extends Provider> type() {
        return type;
    }
}
