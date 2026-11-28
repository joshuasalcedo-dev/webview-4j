package io.joshuasalcedo.swaswa.ffi;

/**
 * Answers the pages' requests to Java ({@link App#onPageRequest}): what a web app's own page asks for, as a server
 * would. Runs on a virtual thread of its own, so it may block.
 * <p>
 * Its answer: a value, sent as JSON with status 200 ({@code null}: 204, nothing); a {@code CompletionStage} of one,
 * sent when it completes; an {@link HttpException} thrown for another status ({@code 404}), an
 * {@code IllegalArgumentException} for 400, anything else for 500. swaswa-webview's controllers are one such handler.
 */
@FunctionalInterface
public interface PageRequestHandler {

    Object handle(PageRequest request) throws Exception;
}
