package io.joshuasalcedo.swaswa.ffi;

/**
 * Where the pointer is on the screen ({@link App#cursor()}): in logical pixels (as a window's {@link Geometry} is),
 * and in the screen's physical pixels with the scale of the monitor it is on.
 */
public record Cursor(double x, double y, double physicalX, double physicalY, double scale) {}
