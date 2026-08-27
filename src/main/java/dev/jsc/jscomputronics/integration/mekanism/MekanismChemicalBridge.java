/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.mekanism;

import dev.jsc.jscomputronics.module.computing.storage.ChemicalBridge;
import dev.jsc.jscomputronics.module.computing.storage.ChemicalPort;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.recipes.ItemStackChemicalToObjectRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Mekanism's chemicals (gases, infusions, pigments, slurries — one unified registry since 10.7) as network
 * data. The block capability is recreated by its registered name, so the bridge depends on the Mekanism API
 * jar alone and on nothing from the mod's internals.
 */
final class MekanismChemicalBridge implements ChemicalBridge {

    /** Mekanism's sided block capability for chemical handlers; capabilities are interned by name. */
    // The item form of the same capability: what a filled tank item or a hohlraum holds.
    private static final ItemCapability<IChemicalHandler, Void> ITEM_CHEMICAL_HANDLER =
            ItemCapability.createVoid(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class);
    private static final BlockCapability<IChemicalHandler, @Nullable Direction> CHEMICAL_HANDLER =
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"),
                    IChemicalHandler.class);

    @Override
    public Optional<ChemicalPort> portFor(final Level level, final BlockPos pos, @Nullable final Direction side) {
        final IChemicalHandler handler = level.getCapability(CHEMICAL_HANDLER, pos, side);
        return handler == null ? Optional.empty() : Optional.of(new HandlerPort(handler));
    }

    @Override
    public boolean exists(final ResourceLocation chemical) {
        return MekanismAPI.CHEMICAL_REGISTRY.containsKey(chemical);
    }

    @Override
    public Component displayName(final ResourceLocation chemical) {
        return lookup(chemical).getTextComponent();
    }

    @Override
    public Optional<ResourceLocation> chemicalOf(final ItemStack stack) {
        final IChemicalHandler handler = stack.getCapability(ITEM_CHEMICAL_HANDLER);
        if (handler == null) {
            return Optional.empty();
        }
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            final ChemicalStack inTank = handler.getChemicalInTank(tank);
            if (!inTank.isEmpty()) {
                return Optional.ofNullable(HandlerPort.idOf(inTank.getChemical()));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ChemicalAmount> chemicalIngredient(final Object ingredient) {
        if (ingredient instanceof ChemicalStack stack && !stack.isEmpty()) {
            final ResourceLocation id = HandlerPort.idOf(stack.getChemical());
            return id == null ? Optional.empty() : Optional.of(new ChemicalAmount(id, stack.getAmount()));
        }
        return Optional.empty();
    }

    @Override
    public boolean perTickUsage(final Object recipe) {
        return recipe instanceof ItemStackChemicalToObjectRecipe<?> metered && metered.perTickUsage();
    }

    @Override
    public int tint(final ResourceLocation chemical) {
        return 0xFF000000 | (lookup(chemical).getTint() & 0xFFFFFF);
    }

    private static Chemical lookup(final ResourceLocation chemical) {
        return MekanismAPI.CHEMICAL_REGISTRY.get(chemical);
    }

    /** A chemical port over one handler; amounts are millibuckets on both sides. */
    private record HandlerPort(IChemicalHandler handler) implements ChemicalPort {

        private static Action action(final boolean simulate) {
            return simulate ? Action.SIMULATE : Action.EXECUTE;
        }

        @Override
        public long fill(final ResourceLocation chemical, final long amount, final boolean simulate) {
            if (amount <= 0 || !MekanismAPI.CHEMICAL_REGISTRY.containsKey(chemical)) {
                return 0L;
            }
            final ChemicalStack offered = new ChemicalStack(lookup(chemical).getAsHolder(), amount);
            final ChemicalStack remainder = handler.insertChemical(offered, action(simulate));
            return amount - remainder.getAmount();
        }

        @Override
        public long drain(final ResourceLocation chemical, final long amount, final boolean simulate) {
            if (amount <= 0 || !MekanismAPI.CHEMICAL_REGISTRY.containsKey(chemical)) {
                return 0L;
            }
            final ChemicalStack wanted = new ChemicalStack(lookup(chemical).getAsHolder(), amount);
            return handler.extractChemical(wanted, action(simulate)).getAmount();
        }

        @Override
        public long count(final ResourceLocation chemical) {
            long total = 0L;
            for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
                final ChemicalStack inTank = handler.getChemicalInTank(tank);
                if (!inTank.isEmpty() && chemical.equals(idOf(inTank.getChemical()))) {
                    total += inTank.getAmount();
                }
            }
            return total;
        }

        @Override
        public List<ResourceLocation> available() {
            final Set<ResourceLocation> ids = new LinkedHashSet<>();
            for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
                final ChemicalStack inTank = handler.getChemicalInTank(tank);
                if (!inTank.isEmpty()) {
                    final ResourceLocation id = idOf(inTank.getChemical());
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
            return new ArrayList<>(ids);
        }

        @Nullable
        private static ResourceLocation idOf(final Chemical chemical) {
            return MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
        }
    }
}
