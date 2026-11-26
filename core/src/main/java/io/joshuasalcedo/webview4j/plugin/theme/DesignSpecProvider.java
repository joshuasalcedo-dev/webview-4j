package io.joshuasalcedo.webview4j.plugin.theme;

import io.joshuasalcedo.webview4j.plugin.Provider;
import java.util.List;

/**
 * The design specs an app offers (its {@link ThemeSwitcher} lists them), and which one it starts with. Without one,
 * the app has webview-4j's own, {@link #BUILT_IN}: Windows 11 and macOS.
 * <pre>{@code
 * public class ShopApp extends WebviewApplication implements DesignSpecProvider {
 *     public List<DesignSpec> designSpecs() { return List.of(ACME, DesignSpec.WINDOWS, DesignSpec.MACOS); }
 *     public DesignSpec initialSpec()      { return ACME; }
 *     ...
 * }
 * }</pre>
 */
@FunctionalInterface
public interface DesignSpecProvider extends Provider {

    /** webview-4j's own: Microsoft's Windows 11 and Apple's macOS, each on its own system first. */
    DesignSpecProvider BUILT_IN = () -> List.of(DesignSpec.WINDOWS, DesignSpec.MACOS);

    /** The specs to offer, in the order to list them (at least one). */
    List<DesignSpec> designSpecs();

    /**
     * The spec the app starts with, until the person picks another: null for the system's own when it is among
     * {@link #designSpecs()} (Windows 11 on Windows, macOS on a Mac), else the first.
     */
    default DesignSpec initialSpec() {
        return null;
    }
}
