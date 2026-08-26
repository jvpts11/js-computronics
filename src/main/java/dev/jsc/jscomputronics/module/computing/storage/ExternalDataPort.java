/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link DataSink} + {@link DataSource} over an external block's item, fluid AND chemical capabilities at
 * once — the one place where "everything is data" meets a real machine.
 */
public final class ExternalDataPort implements DataPort {

    @Nullable
    private final IItemHandler items;
    @Nullable
    private final IFluidHandler fluids;
    @Nullable
    private final ChemicalPort chemicals;

    public ExternalDataPort(@Nullable final IItemHandler items, @Nullable final IFluidHandler fluids) {
        this(items, fluids, null);
    }

    public ExternalDataPort(@Nullable final IItemHandler items, @Nullable final IFluidHandler fluids,
                            @Nullable final ChemicalPort chemicals) {
        this.items = items;
        this.fluids = fluids;
        this.chemicals = chemicals;
    }

    @Override
    public boolean isEmpty() {
        return items == null && fluids == null && chemicals == null;
    }

    private static IFluidHandler.FluidAction action(final boolean simulate) {
        return simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
    }

    @Override
    public long insert(final StorageKey key, final long amount, final boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        switch (key.kind()) {
            case FLUID -> {
                if (fluids == null) {
                    return 0L;
                }
                final int want = (int) Math.min(amount, Integer.MAX_VALUE);
                return fluids.fill(key.fluidStack(want), action(simulate));
            }
            case CHEMICAL -> {
                return chemicals == null ? 0L
                        : chemicals.fill(Objects.requireNonNull(key.chemicalId()), amount, simulate);
            }
            default -> { }
        }
        if (items == null) {
            return 0L;
        }
        // Items insert in vanilla-sized stacks; EXECUTE mutates the handler so each batch sees the
        // remaining room. (Simulation is best-effort — the operation path always executes.)
        final int batch = Math.max(1, key.stack(1).getMaxStackSize());
        long inserted = 0L;
        long remaining = amount;
        while (remaining > 0L) {
            final int chunk = (int) Math.min(remaining, batch);
            final ItemStack leftover = ItemHandlerHelper.insertItem(items, key.stack(chunk), simulate);
            final int accepted = chunk - leftover.getCount();
            if (accepted <= 0) {
                break;
            }
            inserted += accepted;
            remaining -= accepted;
            if (simulate) {
                break; // can't loop a non-mutating simulate; report one batch
            }
        }
        return inserted;
    }

    @Override
    public long extract(final StorageKey key, final long amount, final boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        switch (key.kind()) {
            case FLUID -> {
                if (fluids == null) {
                    return 0L;
                }
                final int want = (int) Math.min(amount, Integer.MAX_VALUE);
                return fluids.drain(key.fluidStack(want), action(simulate)).getAmount();
            }
            case CHEMICAL -> {
                return chemicals == null ? 0L
                        : chemicals.drain(Objects.requireNonNull(key.chemicalId()), amount, simulate);
            }
            default -> { }
        }
        if (items == null) {
            return 0L;
        }
        long extracted = 0L;
        for (int slot = 0; slot < items.getSlots() && extracted < amount; slot++) {
            final ItemStack inSlot = items.getStackInSlot(slot);
            if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, key.stack(1))) {
                continue;
            }
            final int got = items.extractItem(slot, (int) Math.min(amount - extracted, Integer.MAX_VALUE),
                    simulate).getCount();
            extracted += got;
        }
        return extracted;
    }

    @Override
    public long count(final StorageKey key) {
        long total = 0L;
        switch (key.kind()) {
            case FLUID -> {
                if (fluids != null) {
                    for (int tank = 0; tank < fluids.getTanks(); tank++) {
                        final FluidStack inTank = fluids.getFluidInTank(tank);
                        if (FluidStack.isSameFluidSameComponents(inTank, key.fluidPrototype())) {
                            total += inTank.getAmount();
                        }
                    }
                }
            }
            case CHEMICAL -> {
                if (chemicals != null) {
                    total = chemicals.count(Objects.requireNonNull(key.chemicalId()));
                }
            }
            case ITEM -> {
                if (items != null) {
                    for (int slot = 0; slot < items.getSlots(); slot++) {
                        final ItemStack inSlot = items.getStackInSlot(slot);
                        if (ItemStack.isSameItemSameComponents(inSlot, key.stack(1))) {
                            total += inSlot.getCount();
                        }
                    }
                }
            }
        }
        return total;
    }

    @Override
    public List<StorageKey> available() {
        final Set<StorageKey> keys = new LinkedHashSet<>();
        if (items != null) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                final ItemStack inSlot = items.getStackInSlot(slot);
                if (!inSlot.isEmpty()) {
                    keys.add(StorageKey.of(inSlot));
                }
            }
        }
        if (fluids != null) {
            for (int tank = 0; tank < fluids.getTanks(); tank++) {
                final FluidStack inTank = fluids.getFluidInTank(tank);
                if (!inTank.isEmpty()) {
                    keys.add(StorageKey.of(inTank));
                }
            }
        }
        if (chemicals != null) {
            for (final ResourceLocation chemical : chemicals.available()) {
                keys.add(StorageKey.chemical(chemical));
            }
        }
        return new ArrayList<>(keys);
    }
}
