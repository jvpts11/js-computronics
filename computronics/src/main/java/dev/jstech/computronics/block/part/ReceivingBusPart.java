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
import dev.jstech.computronics.menu.ReceivingBusMenu;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * A Receiving Bus part: marks the machine face that crafting operations collect finished outputs from, the
 * way to drive a sided machine whose output face differs from the face its Crafting Switch touches. It shares
 * the Import Bus's identity plumbing (menu, filters) but is passive: the crafting engine pulls through it, it
 * never transfers on its own.
 */
public final class ReceivingBusPart extends ImportBusPart {

    @Override
    public CablePartType type() {
        return CablePartType.RECEIVING;
    }

    @Override
    public void serverTick() {
        /*
         * Passive: autonomous pulling would steal a craft's outputs into the bus buffer while the operation
         * is trying to collect and account for them.
         */
    }

    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory,
                                            final DataCableBlockEntity cable, final Direction mountedFace) {
        return ReceivingBusMenu.create(containerId, inventory, cable, mountedFace);
    }

    @Override
    public ItemStack partItem() {
        return new ItemStack(ComputingModule.RECEIVING_BUS_ITEM.get());
    }
}
