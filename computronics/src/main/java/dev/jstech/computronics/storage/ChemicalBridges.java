/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The registered {@link IChemicalBridge}s. With no bridge (no chemical mod present) every query answers "no
 * such chemical", so chemical keys are inert data the network can still store, move and display by id.
 */
public final class ChemicalBridges {

    private static final List<IChemicalBridge> BRIDGES = new CopyOnWriteArrayList<>();
    private static final int DEFAULT_TINT = 0xFF8FA3B7;

    private ChemicalBridges() {
    }

    public static void register(final IChemicalBridge bridge) {
        BRIDGES.add(bridge);
    }

    public static boolean anyRegistered() {
        return !BRIDGES.isEmpty();
    }

    /** The first bridge that offers a chemical port on the block at {@code pos}. */
    public static Optional<IChemicalPort> portFor(final Level level, final BlockPos pos, @Nullable final Direction side) {
        for (final IChemicalBridge bridge : BRIDGES) {
            final Optional<IChemicalPort> port = bridge.portFor(level, pos, side);
            if (port.isPresent()) {
                return port;
            }
        }
        return Optional.empty();
    }

    /** The chemical carried by {@code stack}, from the first bridge that recognises the item. */
    public static Optional<ResourceLocation> chemicalOf(final ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        for (final IChemicalBridge bridge : BRIDGES) {
            final Optional<ResourceLocation> chemical = bridge.chemicalOf(stack);
            if (chemical.isPresent()) {
                return chemical;
            }
        }
        return Optional.empty();
    }

    /**
     * The chemical port of an item that carries chemicals, from the first bridge that recognises it. The port
     * edits {@code stack} in place.
     */
    public static Optional<IChemicalPort> itemPortFor(final ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        for (final IChemicalBridge bridge : BRIDGES) {
            final Optional<IChemicalPort> port = bridge.itemPortFor(stack);
            if (port.isPresent()) {
                return port;
            }
        }
        return Optional.empty();
    }

    /** The chemical a recipe viewer's ingredient object stands for, from the first bridge that recognises it. */
    public static Optional<IChemicalBridge.ChemicalAmount> chemicalIngredient(final Object ingredient) {
        for (final IChemicalBridge bridge : BRIDGES) {
            final Optional<IChemicalBridge.ChemicalAmount> chemical = bridge.chemicalIngredient(ingredient);
            if (chemical.isPresent()) {
                return chemical;
            }
        }
        return Optional.empty();
    }

    /** Whether any bridge knows {@code recipe} to meter its chemical input per tick. */
    public static boolean perTickUsage(final Object recipe) {
        for (final IChemicalBridge bridge : BRIDGES) {
            if (bridge.perTickUsage(recipe)) {
                return true;
            }
        }
        return false;
    }

    public static boolean exists(final ResourceLocation chemical) {
        for (final IChemicalBridge bridge : BRIDGES) {
            if (bridge.exists(chemical)) {
                return true;
            }
        }
        return false;
    }

    public static Component displayName(final ResourceLocation chemical) {
        for (final IChemicalBridge bridge : BRIDGES) {
            if (bridge.exists(chemical)) {
                return bridge.displayName(chemical);
            }
        }
        return Component.literal(chemical.toString());
    }

    public static int tint(final ResourceLocation chemical) {
        for (final IChemicalBridge bridge : BRIDGES) {
            if (bridge.exists(chemical)) {
                return bridge.tint(chemical);
            }
        }
        return DEFAULT_TINT;
    }
}
