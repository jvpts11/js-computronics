/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.logic;

/**
 * Immutable scroll state for a row-based scrollable list.
 */
public record ScrollState(int totalItems, int visibleRows, int offset) {

    public ScrollState {
        if (totalItems < 0) {
            throw new IllegalArgumentException(
                    "totalItems must be >= 0; got " + totalItems);
        }
        if (visibleRows < 1) {
            throw new IllegalArgumentException(
                    "visibleRows must be >= 1; got " + visibleRows);
        }
        final int max = Math.max(0, totalItems - visibleRows);
        if (offset < 0) {
            offset = 0;
        } else if (offset > max) {
            offset = max;
        }
    }

    public static ScrollState of(final int totalItems, final int visibleRows) {
        return new ScrollState(totalItems, visibleRows, 0);
    }

    public int maxOffset() {
        return Math.max(0, totalItems - visibleRows);
    }

    public ScrollState scrolledBy(final int delta) {
        return new ScrollState(totalItems, visibleRows, offset + delta);
    }

    public ScrollState scrolledToTop() {
        return new ScrollState(totalItems, visibleRows, 0);
    }

    public ScrollState scrolledToBottom() {
        return new ScrollState(totalItems, visibleRows, maxOffset());
    }

    public ScrollState withTotalItems(final int newTotal) {
        return new ScrollState(newTotal, visibleRows, offset);
    }

    public boolean canScrollUp() {
        return offset > 0;
    }

    public boolean canScrollDown() {
        return offset < maxOffset();
    }

    public int firstVisibleIndex() {
        return offset;
    }

    public int lastVisibleIndexExclusive() {
        return Math.min(offset + visibleRows, totalItems);
    }

    public int visibleCount() {
        return lastVisibleIndexExclusive() - firstVisibleIndex();
    }

    public boolean isScrollable() {
        return totalItems > visibleRows;
    }

    public double thumbSize() {
        if (totalItems <= visibleRows) {
            return 1.0;
        }
        return (double) visibleRows / totalItems;
    }

    public double thumbPosition() {
        final int max = maxOffset();
        if (max == 0) {
            return 0.0;
        }
        return (double) offset / max;
    }
}