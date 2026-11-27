package io.joshuasalcedo.swaswa.ffi;

/**
 * The app went over its memory budget ({@link App#memoryBudget(long)}), or came back under it: {@code over} says
 * which. Over it, free what can be made again (caches, pictures, closed documents' data); swaswa has asked the engine
 * to use less in the windows not shown. Told once each way, not every check.
 *
 * @param memory what was used when it was checked
 * @param budget the budget, in bytes
 * @param over   true when it went over; false when it came back under
 */
public record MemoryPressure(Memory memory, long budget, boolean over) implements WebViewEvent {}
