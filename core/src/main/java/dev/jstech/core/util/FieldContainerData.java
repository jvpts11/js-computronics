/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.util;

import net.minecraft.world.inventory.ContainerData;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A {@link ContainerData} backed by per-index getter/setter pairs, so a block entity can sync a handful of ints (progress, burn time, stored energy, ...) to its menu without hand-writing the same {@code get}/{@code set}/{@code getCount} switch. Out-of-range reads return 0 and out-of-range writes are ignored, matching the lenient behaviour menus expect.
 */
public final class FieldContainerData implements ContainerData {

    private final IntSupplier[] getters;
    private final IntConsumer[] setters;

    public FieldContainerData(final IntSupplier[] getters, final IntConsumer[] setters) {
        if (getters.length != setters.length) {
            throw new IllegalArgumentException(
                    "getters and setters must pair up: " + getters.length + " vs " + setters.length);
        }
        this.getters = getters.clone();
        this.setters = setters.clone();
    }

    @Override
    public int get(final int index) {
        return index >= 0 && index < getters.length ? getters[index].getAsInt() : 0;
    }

    @Override
    public void set(final int index, final int value) {
        if (index >= 0 && index < setters.length) {
            setters[index].accept(value);
        }
    }

    @Override
    public int getCount() {
        return getters.length;
    }
}
