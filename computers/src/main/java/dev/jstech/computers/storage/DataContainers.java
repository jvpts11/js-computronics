/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.storage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import java.util.Optional;

/**
 * An item that CARRIES data (a filled bucket, a tank item, anything with a fluid or a chemical inside) gives
 * up what it holds when it is deposited, and the emptied container comes back; the container itself is never
 * stored. This is the one place that knows how to take data out of such an item and how to put data back, so
 * every deposit route (terminal or desktop; cursor, menu slot or inventory slot) treats a container the same
 * way, and none of them can store a bucket as an item again.
 */
public final class DataContainers {

    private DataContainers() {
    }

    /** What one container gave up: the data, its amount in millibuckets, and the container left behind. */
    public record Drained(StorageKey key, long amount, ItemStack container) {
    }

    /** Whether one of {@code stack} holds fluid or chemical data it could give up. */
    public static boolean holdsData(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        final ItemStack one = stack.copyWithCount(1);
        final Optional<IFluidHandlerItem> fluids = FluidUtil.getFluidHandler(one);
        if (fluids.isPresent()
                && !fluids.get().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
            return true;
        }
        return ChemicalBridges.itemPortFor(one).map(port -> !port.available().isEmpty()).orElse(false);
    }

    /**
     * Takes up to {@code roomWeight} mB of data out of ONE container from {@code stack}, leaving {@code stack}
     * itself untouched: the result carries a copy of that container with the data removed. Empty when the item
     * carries no data, or when nothing could come out, since a bucket only ever gives up its whole 1 000 mB, so with
     * less room than that it stays full, while a tank item gives up exactly what fits.
     */
    public static Optional<Drained> drain(final ItemStack stack, final long roomWeight) {
        if (stack.isEmpty() || roomWeight <= 0L) {
            return Optional.empty();
        }
        final ItemStack one = stack.copyWithCount(1);
        final int room = (int) Math.min(roomWeight, Integer.MAX_VALUE);
        final Optional<IFluidHandlerItem> fluids = FluidUtil.getFluidHandler(one);
        if (fluids.isPresent()) {
            final IFluidHandlerItem handler = fluids.get();
            /*
             * What one drain hands out is the deposit: a tank item may cap that at a bucket's worth per
             * operation, and then a full tank empties a bucket at a time, one click each.
             */
            final FluidStack held = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (!held.isEmpty()) {
                final FluidStack taken = handler.drain(Math.min(room, held.getAmount()),
                        IFluidHandler.FluidAction.EXECUTE);
                return taken.isEmpty() ? Optional.empty()
                        : Optional.of(new Drained(StorageKey.of(taken), taken.getAmount(), handler.getContainer()));
            }
        }
        final Optional<IChemicalPort> chemicals = ChemicalBridges.itemPortFor(one);
        if (chemicals.isPresent()) {
            final IChemicalPort port = chemicals.get();
            for (final ResourceLocation chemical : port.available()) {
                final long taken = port.drain(chemical, Math.min(roomWeight, port.count(chemical)), false);
                if (taken > 0L) {
                    return Optional.of(new Drained(StorageKey.chemical(chemical), taken, one));
                }
            }
        }
        return Optional.empty();
    }

    /** What went into a container: the container as it is afterwards, and how much it took. */
    public record Filled(ItemStack container, long taken) {
    }

    /**
     * How much of {@code key} ONE container from {@code stack} would take, out of {@code available}: nothing
     * for an item that is no container or that holds something else, a full bucket or nothing for a bucket,
     * and whatever fits for a tank item.
     */
    public static long roomFor(final ItemStack stack, final StorageKey key, final long available) {
        if (stack.isEmpty() || available <= 0L) {
            return 0L;
        }
        final ItemStack one = stack.copyWithCount(1);
        if (key.isFluid()) {
            return FluidUtil.getFluidHandler(one)
                    .map(handler -> (long) handler.fill(key.fluidStack((int) Math.min(available, Integer.MAX_VALUE)),
                            IFluidHandler.FluidAction.SIMULATE))
                    .orElse(0L);
        }
        final ResourceLocation chemical = key.chemicalId();
        if (chemical == null) {
            return 0L;
        }
        return ChemicalBridges.itemPortFor(one).map(port -> port.fill(chemical, available, true)).orElse(0L);
    }

    /** Whether one of {@code stack} could take any of {@code key} at all. */
    public static boolean canTake(final ItemStack stack, final StorageKey key) {
        return roomFor(stack, key, Long.MAX_VALUE) > 0L;
    }

    /**
     * Puts {@code amount} of {@code key} into {@code container} and returns the container as it is afterwards
     * with how much went in; a bucket takes nothing short of a full bucket. The same call refills an emptied
     * container with what the network could not take after all.
     */
    public static Filled fill(final ItemStack container, final StorageKey key, final long amount) {
        if (container.isEmpty() || amount <= 0L) {
            return new Filled(container, 0L);
        }
        if (key.isFluid()) {
            final Optional<IFluidHandlerItem> fluids = FluidUtil.getFluidHandler(container);
            if (fluids.isEmpty()) {
                return new Filled(container, 0L);
            }
            final int taken = fluids.get().fill(key.fluidStack((int) Math.min(amount, Integer.MAX_VALUE)),
                    IFluidHandler.FluidAction.EXECUTE);
            return new Filled(fluids.get().getContainer(), taken);
        }
        final ResourceLocation chemical = key.chemicalId();
        if (chemical == null) {
            return new Filled(container, 0L);
        }
        final long taken = ChemicalBridges.itemPortFor(container)
                .map(port -> port.fill(chemical, amount, false)).orElse(0L);
        return new Filled(container, taken);
    }
}
