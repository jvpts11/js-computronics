/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StepperMathTest {

    @Test
    public void increment_and_decrement_stayInsideTheRange() {
        assertEquals(2, StepperMath.increment(1, 1, 10));
        assertEquals(10, StepperMath.increment(10, 1, 10));
        assertEquals(1, StepperMath.decrement(2, 1, 10));
        assertEquals(1, StepperMath.decrement(1, 1, 10));
    }

    @Test
    public void doubled_stopsAtTheMaximumWithoutOverflowing() {
        assertEquals(8, StepperMath.doubled(4, 1, 10));
        assertEquals(10, StepperMath.doubled(6, 1, 10));
        assertEquals(Long.MAX_VALUE / 4, StepperMath.doubled(Long.MAX_VALUE / 4, 1, Long.MAX_VALUE / 4));
    }

    @Test
    public void halved_roundsDownAndNeverGoesBelowTheMinimum() {
        assertEquals(3, StepperMath.halved(7, 1, 10));
        assertEquals(1, StepperMath.halved(1, 1, 10));
        assertEquals(5, StepperMath.halved(4, 5, 10));
    }

    @Test
    public void clamp_keepsAValueInsideItsBounds() {
        assertEquals(5, StepperMath.clamp(5, 1, 10));
        assertEquals(1, StepperMath.clamp(-3, 1, 10));
        assertEquals(10, StepperMath.clamp(11, 1, 10));
    }
}
