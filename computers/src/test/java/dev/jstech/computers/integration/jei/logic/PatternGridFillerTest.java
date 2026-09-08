/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.integration.jei.logic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.jstech.computers.integration.jei.logic.PatternGridFiller.GRID_SIZE;
import static org.junit.jupiter.api.Assertions.*;

class PatternGridFillerTest {

    @Test
    void fillGrid_exactlyNineIngredients_returnsAllNine() {
        final List<Integer> ingredients = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
        final List<Integer> grid = PatternGridFiller.fillGrid(ingredients, -1);
        assertEquals(GRID_SIZE, grid.size());
        for (int i = 0; i < GRID_SIZE; i++) {
            assertEquals(i, grid.get(i));
        }
    }

    @Test
    void fillGrid_fewerThanNineIngredients_fillsRemainingWithFallback() {
        final List<String> ingredients = List.of("a", "b", "c");
        final List<String> grid = PatternGridFiller.fillGrid(ingredients, "");
        assertEquals(GRID_SIZE, grid.size());
        assertEquals("a", grid.get(0));
        assertEquals("b", grid.get(1));
        assertEquals("c", grid.get(2));
        for (int i = 3; i < GRID_SIZE; i++) {
            assertEquals("", grid.get(i));
        }
    }

    @Test
    void fillGrid_emptyIngredients_returnsAllFallback() {
        final List<Integer> grid = PatternGridFiller.fillGrid(List.of(), -1);
        assertEquals(GRID_SIZE, grid.size());
        grid.forEach(v -> assertEquals(-1, v));
    }

    @Test
    void fillGrid_moreThanNineIngredients_truncatesAtNine() {
        final List<Integer> ingredients = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        final List<Integer> grid = PatternGridFiller.fillGrid(ingredients, -1);
        assertEquals(GRID_SIZE, grid.size());
        for (int i = 0; i < GRID_SIZE; i++) {
            assertEquals(i, grid.get(i));
        }
    }

    @Test
    void fillGrid_returnedListIsUnmodifiable() {
        final List<Integer> grid = PatternGridFiller.fillGrid(List.of(1), -1);
        assertThrows(UnsupportedOperationException.class, () -> grid.set(0, 99));
    }
}
