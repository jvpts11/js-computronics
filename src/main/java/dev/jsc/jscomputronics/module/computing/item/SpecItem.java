/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import net.minecraft.world.item.Item;

/**
 * Base for component items that each carry one immutable hardware spec record (CPU, RAM, GPU, PSU, disk, motherboard, etc.). It holds the spec and exposes it through {@link #spec()} so the wrapper boilerplate lives in one place; subclasses only supply their tooltip.
 *
 * @param <S> the immutable spec record this item wraps
 */
public abstract class SpecItem<S> extends Item {

    private final S spec;

    protected SpecItem(final Properties properties, final S spec) {
        super(properties);
        this.spec = spec;
    }

    public S spec() {
        return spec;
    }
}
