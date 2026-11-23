package io.github.parkiyong.jaybee.api.model;

/**
 * Diagram layout coordinates parsed from visual metadata (e.g., jPDL {@code g="x,y,w,h"}).
 */
public record Coordinates(int x, int y, int width, int height) {
}
