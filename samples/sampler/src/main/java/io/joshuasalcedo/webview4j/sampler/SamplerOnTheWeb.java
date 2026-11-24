package io.joshuasalcedo.webview4j.sampler;

import io.joshuasalcedo.webview4j.ui.WebHost;

/**
 * The Sampler on the web: the same Java screen, in a browser, each tab a screen of its own.
 * {@code ./gradlew runWeb} (or {@code just sampler-web}), then open http://localhost:8080/.
 */
public final class SamplerOnTheWeb {

    public static void main(String[] args) throws InterruptedException {
        int port = Integer.getInteger("sampler.port", 8080);
        try (WebHost host = WebHost.of(SamplerScreen::new).title("webview-4j Sampler").port(port).start()) {
            System.out.println("The Sampler is on the web at " + host.url() + " (Ctrl+C stops it)");
            Thread.currentThread().join();
        }
    }
}
