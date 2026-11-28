package io.joshuasalcedo.swaswa.ffi;

import java.time.Duration;

/**
 * A child process ended ({@link App#process(String, ProcessBuilder)}): after its last {@link ProcessOutput}.
 *
 * @param process  the name the app gave it
 * @param exitCode what it ended with (0: usually all went well)
 * @param ran      how long it ran
 */
public record ProcessExited(String process, long pid, int exitCode, Duration ran) implements WebViewEvent {}
