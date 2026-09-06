/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The fluid tanks of a block face, as a data channel; amounts are millibuckets. */
public record FluidChannel(IFluidHandler fluids) implements DataChannel {

    private static IFluidHandler.FluidAction action(final boolean simulate) {
        return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
    }

    @Override
    public StorageKey.Kind kind() {
        return StorageKey.Kind.FLUID;
    }

    @Override
    public long insert(final StorageKey key, final long amount, final boolean simulate) {
        if (amount <= 0L || !key.isFluid()) {
            return 0L;
        }
        return fluids.fill(key.fluidStack((int) Math.min(amount, Integer.MAX_VALUE)), action(simulate));
    }

    @Override
    public long extract(final StorageKey key, final long amount, final boolean simulate) {
        if (amount <= 0L || !key.isFluid()) {
            return 0L;
        }
        return fluids.drain(key.fluidStack((int) Math.min(amount, Integer.MAX_VALUE)), action(simulate)).getAmount();
    }

    @Override
    public long count(final StorageKey key) {
        if (!key.isFluid()) {
            return 0L;
        }
        long total = 0L;
        for (int tank = 0; tank < fluids.getTanks(); tank++) {
            final FluidStack inTank = fluids.getFluidInTank(tank);
            if (FluidStack.isSameFluidSameComponents(inTank, key.fluidPrototype())) {
                total += inTank.getAmount();
            }
        }
        return total;
    }

    @Override
    public List<StorageKey> available() {
        final Set<StorageKey> keys = new LinkedHashSet<>();
        for (int tank = 0; tank < fluids.getTanks(); tank++) {
            final FluidStack inTank = fluids.getFluidInTank(tank);
            if (!inTank.isEmpty()) {
                keys.add(StorageKey.of(inTank));
            }
        }
        return new ArrayList<>(keys);
    }
}
