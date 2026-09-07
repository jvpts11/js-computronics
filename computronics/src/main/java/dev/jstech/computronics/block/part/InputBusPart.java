/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.block.part;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import dev.jstech.computronics.menu.InputBusMenu;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * An Input Bus part: marks the machine face that crafting operations deliver inputs through, the way to drive
 * a sided machine whose input face differs from the face its Crafting Switch touches. It shares the Export
 * Bus's identity plumbing (menu, filters) but is passive: the crafting engine pushes through it, it never
 * transfers on its own.
 */
public final class InputBusPart extends ExportBusPart {

    @Override
    public CablePartType type() {
        return CablePartType.INPUT;
    }

    @Override
    public void serverTick() {
        // Passive: autonomous pushing would race the crafting engine's measured feeding (and its accounting).
    }

    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory,
                                            final DataCableBlockEntity cable, final Direction mountedFace) {
        return InputBusMenu.create(containerId, inventory, cable, mountedFace);
    }

    @Override
    public ItemStack partItem() {
        return new ItemStack(ComputingModule.INPUT_BUS_ITEM.get());
    }
}
