/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A rigorous guard on the desktop's render-layer depths. The icon-label-over-window bug returns whenever a
 * layer ends up at the wrong Z, so these tests assert the whole ordering is strict, gap-respecting, and that
 * no Z constant can be added without being placed in the order.
 */
class DesktopZTest {

    @Test
    void ordered_isStrictlyIncreasingBackToFront() {
        final int[] z = DesktopZ.ordered();
        for (int i = 1; i < z.length; i++) {
            assertTrue(z[i] > z[i - 1],
                    "layer " + i + " (z=" + z[i] + ") must sit strictly in front of the previous (z=" + z[i - 1] + ")");
        }
    }

    @Test
    void ordered_hasNoDuplicateDepths() {
        final Set<Integer> seen = new HashSet<>();
        for (final int v : DesktopZ.ordered()) {
            assertTrue(seen.add(v), "duplicate Z " + v + ": two layers at the same depth can z-fight");
        }
    }

    @Test
    void ordered_hasNoNegativeDepths() {
        for (final int v : DesktopZ.ordered()) {
            assertTrue(v >= 0, "Z " + v + " is negative; the GUI depth range starts at 0");
        }
    }

    @Test
    void wallpaperIsTheFloor_andPopupIsTheCeiling() {
        final int[] z = DesktopZ.ordered();
        assertEquals(DesktopZ.WALLPAPER, Arrays.stream(z).min().getAsInt(), "wallpaper must be the back-most layer");
        assertEquals(DesktopZ.POPUP, Arrays.stream(z).max().getAsInt(), "a modal popup must be the front-most layer");
        assertEquals(0, DesktopZ.WALLPAPER, "the wallpaper must sit at the base depth");
    }

    @Test
    void itemLayers_eachLeavesItemDepthHeadroomBeforeTheNextLayer() {
        // A renderItem model occupies ~ITEM_DEPTH of depth; an item layer must not let its models poke into
        // the layer drawn in front of it, or items would render over a higher layer.
        for (final int layer : DesktopZ.itemLayers()) {
            final int next = DesktopZ.nextAbove(layer);
            assertTrue(next - layer >= DesktopZ.ITEM_DEPTH,
                    "item layer z=" + layer + " has only " + (next - layer) + " before the next layer (z="
                            + next + "); needs at least " + DesktopZ.ITEM_DEPTH);
        }
    }

    @Test
    void itemLayers_areAllRealLayersInOrdered() {
        final List<Integer> ordered = Arrays.stream(DesktopZ.ordered()).boxed().toList();
        for (final int layer : DesktopZ.itemLayers()) {
            assertTrue(ordered.contains(layer), "item layer z=" + layer + " is not a declared render layer");
        }
    }

    @Test
    void tooltipMatchesTheVanillaInternalDepth() {
        // The vanilla tooltip renderer translates +400 internally; the desktop draws tooltips at the base pose
        // and relies on this exact value landing them above the windows and taskbar.
        assertEquals(400, DesktopZ.TOOLTIP);
    }

    @Test
    void everyDepthConstant_appearsExactlyOnceInOrdered() throws IllegalAccessException {
        // Reflection guard: every public Z constant (all but ITEM_DEPTH) must be in ordered() exactly once, so
        // a newly added layer can never be drawn at an unmanaged depth — the exact way this bug kept returning.
        final List<Integer> ordered = Arrays.stream(DesktopZ.ordered()).boxed().toList();
        for (final Field f : DesktopZ.class.getDeclaredFields()) {
            if (f.getType() == int.class && Modifier.isStatic(f.getModifiers())
                    && !f.getName().equals("ITEM_DEPTH")) {
                final int value = f.getInt(null);
                assertEquals(1, Collections.frequency(ordered, value),
                        "Z constant " + f.getName() + " (=" + value + ") must appear exactly once in ordered()");
            }
        }
    }

    @Test
    void orderedSize_matchesTheNumberOfDepthConstants() throws IllegalAccessException {
        int constants = 0;
        for (final Field f : DesktopZ.class.getDeclaredFields()) {
            if (f.getType() == int.class && Modifier.isStatic(f.getModifiers())
                    && !f.getName().equals("ITEM_DEPTH")) {
                constants++;
            }
        }
        assertEquals(constants, DesktopZ.ordered().length, "ordered() must list every layer constant and no more");
    }

    @Test
    void nextAbove_returnsTheImmediateFrontLayer() {
        assertEquals(DesktopZ.ICONS, DesktopZ.nextAbove(DesktopZ.WALLPAPER));
        assertEquals(DesktopZ.WINDOWS, DesktopZ.nextAbove(DesktopZ.ICONS));
        assertEquals(DesktopZ.INVENTORY, DesktopZ.nextAbove(DesktopZ.WINDOWS));
        assertEquals(DesktopZ.POPUP, DesktopZ.nextAbove(DesktopZ.CURSOR));
    }

    @Test
    void nextAbove_returnsItselfForTheFrontMostLayer() {
        assertEquals(DesktopZ.POPUP, DesktopZ.nextAbove(DesktopZ.POPUP));
    }
}
