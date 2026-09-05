/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.multiblock;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Declarative description of a multiblock structure: a 3D char matrix of "slots" plus a mapping from each char to a {@link BlockMatcher}.
 */
public final class MultiblockPattern {

    public static final char CONTROLLER_CHAR = '#';

    public static final char IGNORE_CHAR = ' ';

    private final String name;
    private final char[][][] layers; // layers[y][z][x]
    private final Map<Character, BlockMatcher> mapping;
    private final int controllerX;
    private final int controllerY;
    private final int controllerZ;

    private MultiblockPattern(
            String name,
            char[][][] layers,
            Map<Character, BlockMatcher> mapping,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        this.name = name;
        this.layers = layers;
        this.mapping = mapping;
        this.controllerX = controllerX;
        this.controllerY = controllerY;
        this.controllerZ = controllerZ;
    }

    public String name() { return name; }
    public int sizeY() { return layers.length; }
    public int sizeZ() { return layers[0].length; }
    public int sizeX() { return layers[0][0].length; }

    public char charAt(int x, int y, int z) {
        return layers[y][z][x];
    }

    public Map<Character, BlockMatcher> mapping() {
        return java.util.Collections.unmodifiableMap(mapping);
    }

    public int controllerX() { return controllerX; }

    public int controllerY() { return controllerY; }

    public int controllerZ() { return controllerZ; }

    // Builder

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * DSL for declarative pattern construction.
     */
    public static final class Builder {
        private final String name;
        private final java.util.List<String[]> layers = new java.util.ArrayList<>();
        private final Map<Character, BlockMatcher> mapping = new HashMap<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
        }

        public Builder layer(String... rows) {
            Objects.requireNonNull(rows, "rows must not be null");
            if (rows.length == 0) {
                throw new IllegalArgumentException("Layer must have at least one row");
            }
            int width = rows[0].length();
            if (width == 0) {
                throw new IllegalArgumentException("Rows must have at least one column");
            }
            for (String row : rows) {
                if (row.length() != width) {
                    throw new IllegalArgumentException(
                            "Inconsistent row width: expected " + width + ", got " + row.length()
                                    + " in row '" + row + "'");
                }
            }
            // First layer fixes the (sizeZ, sizeX) shape.
            if (!layers.isEmpty()) {
                String[] first = layers.get(0);
                if (rows.length != first.length || width != first[0].length()) {
                    throw new IllegalArgumentException(
                            "All layers must have the same dimensions; first layer is "
                                    + first[0].length() + "x" + first.length
                                    + " but new layer is " + width + "x" + rows.length);
                }
            }
            layers.add(rows);
            return this;
        }

        public Builder where(char c, BlockMatcher matcher) {
            Objects.requireNonNull(matcher, "matcher must not be null");
            if (c == CONTROLLER_CHAR || c == IGNORE_CHAR) {
                throw new IllegalArgumentException(
                        "Char '" + c + "' is reserved (controller or ignore) "
                                + "and must not be mapped");
            }
            mapping.put(c, matcher);
            return this;
        }

        public MultiblockPattern build() {
            if (layers.isEmpty()) {
                throw new IllegalStateException("Pattern must have at least one layer");
            }
            int sizeY = layers.size();
            int sizeZ = layers.get(0).length;
            int sizeX = layers.get(0)[0].length();

            // Convert to 3D char array.
            char[][][] grid = new char[sizeY][sizeZ][sizeX];
            int controllerX = -1, controllerY = -1, controllerZ = -1;
            int controllerCount = 0;
            var unmappedChars = new java.util.HashSet<Character>();

            for (int y = 0; y < sizeY; y++) {
                String[] layer = layers.get(y);
                for (int z = 0; z < sizeZ; z++) {
                    String row = layer[z];
                    for (int x = 0; x < sizeX; x++) {
                        char c = row.charAt(x);
                        grid[y][z][x] = c;
                        if (c == CONTROLLER_CHAR) {
                            controllerCount++;
                            controllerX = x;
                            controllerY = y;
                            controllerZ = z;
                        } else if (c != IGNORE_CHAR && !mapping.containsKey(c)) {
                            unmappedChars.add(c);
                        }
                    }
                }
            }

            if (controllerCount == 0) {
                throw new IllegalStateException(
                        "Pattern has no controller (no '" + CONTROLLER_CHAR + "' char)");
            }
            if (controllerCount > 1) {
                throw new IllegalStateException(
                        "Pattern has " + controllerCount + " controllers; exactly one '"
                                + CONTROLLER_CHAR + "' is required");
            }
            if (!unmappedChars.isEmpty()) {
                throw new IllegalStateException(
                        "Pattern contains unmapped chars: " + unmappedChars
                                + " — call .where(c, matcher) for each");
            }

            return new MultiblockPattern(
                    name, grid, Map.copyOf(mapping),
                    controllerX, controllerY, controllerZ
            );
        }
    }
}
