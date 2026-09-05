/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.logic;

/**
 * Maps data values onto a graph's vertical pixel range.
 */
public record GraphScale(double minValue, double maxValue) {

    public GraphScale {
        if (maxValue < minValue) {
            throw new IllegalArgumentException(
                    "maxValue (" + maxValue + ") must be >= minValue (" + minValue + ")");
        }
    }

    public double normalize(final double value) {
        final double range = maxValue - minValue;
        if (range == 0.0) {
            return 0.0;
        }
        return (value - minValue) / range;
    }

    public double normalizeClamped(final double value) {
        final double t = normalize(value);
        if (t < 0.0) {
            return 0.0;
        }
        if (t > 1.0) {
            return 1.0;
        }
        return t;
    }

    public double valueToY(final double value, final double heightPixels) {
        final double t = normalizeClamped(value);
        return heightPixels * (1.0 - t);
    }

    public static GraphScale fromData(final double[] values) {
        if (values.length == 0) {
            return new GraphScale(0.0, 0.0);
        }
        double min = values[0];
        double max = values[0];
        for (final double v : values) {
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }
        return new GraphScale(min, max);
    }
}