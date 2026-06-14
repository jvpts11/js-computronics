/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure utility that maps an ordered list of recipe ingredients to a 9-cell
 * pattern grid, filling missing cells with a caller-supplied fallback.
 *
 * This class has no dependency on Minecraft or JEI types so its behaviour
 * can be verified with plain JUnit without a game bootstrap.
 */
public final class PatternGridFiller {

    public static final int GRID_SIZE = 9;

    private PatternGridFiller() {
    }

    /**
     * Maps {@code ingredientsInOrder} to a fixed-length list of {@link #GRID_SIZE} elements.
     *
     * Each position corresponds to one ghost-grid cell (row-major, top-left = 0).
     * Positions beyond the length of {@code ingredientsInOrder} are set to {@code fallback}.
     * If {@code ingredientsInOrder} contains more than {@link #GRID_SIZE} elements, the excess
     * is silently truncated.
     *
     * @param ingredientsInOrder ordered ingredient items, one per recipe slot
     * @param fallback           value used for empty or absent cells
     * @return unmodifiable list of exactly {@link #GRID_SIZE} elements
     */
    public static <T> List<T> fillGrid(final List<T> ingredientsInOrder, final T fallback) {
        final List<T> grid = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            grid.add(i < ingredientsInOrder.size() ? ingredientsInOrder.get(i) : fallback);
        }
        return Collections.unmodifiableList(grid);
    }
}
