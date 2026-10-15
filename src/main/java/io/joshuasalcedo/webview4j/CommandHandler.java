package io.joshuasalcedo.webview4j;

/**
 * Answers a command the page invokes. Runs on a virtual thread of its own, so it may block.
 * Returns the result: anything JSON can carry ({@code Map}, {@code List}, records, {@code String},
 * numbers, {@code Boolean}, enums, null), or a {@code CompletionStage} of it. A thrown exception
 * rejects the page's promise with the exception's message.
 */
@FunctionalInterface
public interface CommandHandler {

    Object handle(Invocation call) throws Exception;
}
