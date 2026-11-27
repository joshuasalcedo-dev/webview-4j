package io.joshuasalcedo.swaswa.ffi;

/**
 * How far a background {@link Worker} is: {@code fraction} 0 to 1 (-1: it cannot say), and what it does now. At most
 * about 30 a second, and always the one that says it is complete.
 */
public record WorkerProgress(String worker, double fraction, String message) implements WebViewEvent {}
