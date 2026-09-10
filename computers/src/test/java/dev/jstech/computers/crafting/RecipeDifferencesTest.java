/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeDifferencesTest {

    private static final RecipeChoice BLAST = new RecipeChoice("Blast", RecipeChoice.KIND_PROCESSING,
            List.of("Blast Furnace"), 1, 400,
            List.of(new RecipeChoice.Input("Iron Ingot", 16, 640, true), new RecipeChoice.Input("Coal", 32, 2112, true)),
            true);

    private static final RecipeChoice IRON_LINE = new RecipeChoice("Iron line", RecipeChoice.KIND_MULTI_STAGE,
            List.of("Macerator", "Blast Furnace"), 2, 680,
            List.of(new RecipeChoice.Input("Raw Iron", 16, 96, false), new RecipeChoice.Input("Coal", 20, 16, true)),
            true);

    @Test
    void describe_namesTheSwappedIngredientTheExtraStageTheTimeAndTheShortfall() {
        final String text = RecipeDifferences.describe(IRON_LINE, BLAST, 16);
        assertEquals("Iron line against Blast: takes Raw Iron instead of Iron Ingot · one more stage"
                + " · 14 s slower for 16 · 4 Coal short, which the network would craft first", text);
    }

    @Test
    void describe_readsTheOtherWayRoundFromTheOtherRecipe() {
        final String text = RecipeDifferences.describe(BLAST, IRON_LINE, 16);
        assertEquals("Blast against Iron line: takes Iron Ingot instead of Raw Iron · one stage fewer"
                + " · 14 s faster for 16 · everything in stock, unlike Iron line", text);
    }

    @Test
    void describe_saysSoWhenNothingDiffers() {
        final RecipeChoice twin = new RecipeChoice("Blast (copy)", BLAST.kind(), BLAST.machines(), BLAST.stages(),
                BLAST.estimateTicks(), BLAST.inputs(), true);
        assertEquals("Blast (copy) against Blast: the same ingredients, stages and time",
                RecipeDifferences.describe(twin, BLAST, 16));
    }

    @Test
    void describe_saysWhenAShortfallCannotBeCovered() {
        final RecipeChoice dry = new RecipeChoice("Dry", RecipeChoice.KIND_BENCH, List.of(), 1, 0,
                List.of(new RecipeChoice.Input("Netherite", 4, 1, false)), false);
        final String text = RecipeDifferences.describe(dry, BLAST, 4);
        assertTrue(text.endsWith("3 Netherite short, and nothing on the network makes it"), text);
        assertTrue(text.contains("takes Netherite instead of Iron Ingot, Coal"), text);
    }

    @Test
    void describe_skipsTheTimeWhenEitherHasNoEstimate() {
        final RecipeChoice noTime = new RecipeChoice("Slow", BLAST.kind(), BLAST.machines(), 3, 0, BLAST.inputs(), true);
        final String text = RecipeDifferences.describe(noTime, BLAST, 16);
        assertEquals("Slow against Blast: 2 more stages", text);
    }

    @Test
    void kindLine_and_stockNote_readAsTheCardShowsThem() {
        assertEquals("multi-stage · Macerator -> Blast Furnace · 2 stages · ~34 s", IRON_LINE.kindLine());
        assertEquals("missing 4 Coal · can be crafted", IRON_LINE.stockNote());
        assertEquals("everything in stock", BLAST.stockNote());
        assertEquals("bench · Bench · 1 stage", new RecipeChoice("Plain", RecipeChoice.KIND_BENCH, List.of(), 1, 0,
                List.of(), true).kindLine());
    }

    @Test
    void seconds_roundsToTheNearestWholeSecondButNeverToZero() {
        assertEquals(0L, RecipeChoice.seconds(0));
        assertEquals(1L, RecipeChoice.seconds(3));
        assertEquals(1L, RecipeChoice.seconds(20));
        assertEquals(2L, RecipeChoice.seconds(30));
        assertEquals(34L, RecipeChoice.seconds(680));
    }
}
