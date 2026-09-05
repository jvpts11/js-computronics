/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.industrial.blockentity;

import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * A machine's internal Forge Energy (FE) buffer.
 */
public class MachineEnergyStorage extends EnergyStorage {

    private final Runnable onChanged;

    public MachineEnergyStorage(final int capacity, final int maxReceive,
                                final int maxExtract, final Runnable onChanged) {
        super(capacity, maxReceive, maxExtract);
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(final int toReceive, final boolean simulate) {
        final int received = super.receiveEnergy(toReceive, simulate);
        if (received > 0 && !simulate) {
            onChanged.run();
        }
        return received;
    }

    @Override
    public int extractEnergy(final int toExtract, final boolean simulate) {
        final int extracted = super.extractEnergy(toExtract, simulate);
        if (extracted > 0 && !simulate) {
            onChanged.run();
        }
        return extracted;
    }

    public boolean consume(final int amount) {
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        onChanged.run();
        return true;
    }

    public int generate(final int amount) {
        final int stored = Math.min(amount, capacity - energy);
        if (stored > 0) {
            energy += stored;
            onChanged.run();
        }
        return stored;
    }

    public void setEnergyStored(final int amount) {
        energy = Math.max(0, Math.min(capacity, amount));
    }
}
