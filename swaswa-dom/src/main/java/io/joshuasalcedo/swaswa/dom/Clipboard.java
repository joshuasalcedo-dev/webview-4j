package io.joshuasalcedo.swaswa.dom;

import java.util.concurrent.CompletableFuture;

/** The system's clipboard: text. Read: "" when it holds no text. */
public interface Clipboard {

    CompletableFuture<Void> writeText(String text);

    CompletableFuture<String> readText();
}
