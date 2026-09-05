/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.menu;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.block.part.InputBusPart;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

/**
 * Menu for configuring an Input Bus part. All behavior lives in {@link AbstractBusMenu}; this only binds the
 * input menu type and the create/fromNetwork factories.
 */
public class InputBusMenu extends AbstractBusMenu {

    public InputBusMenu(final int containerId, final Inventory playerInventory, final InputBusPart part,
                        final Level level, final BlockPos cablePos, final Direction face, final String busName) {
        super(ComputingModule.INPUT_BUS_MENU.get(), containerId, playerInventory, part, level, cablePos, face, busName);
    }

    public static InputBusMenu create(final int containerId, final Inventory playerInventory,
                                      final DataCableBlockEntity cable, final Direction face) {
        final InputBusPart part = cable.getPart(face) instanceof InputBusPart real ? real : new InputBusPart();
        return new InputBusMenu(containerId, playerInventory, part, cable.getLevel(), cable.getBlockPos(),
                face, part.name());
    }

    public static InputBusMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                           final RegistryFriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final Direction face = Direction.from3DDataValue(buf.readByte());
        final String busName = buf.readUtf();
        final Level level = playerInventory.player.level();
        final InputBusPart part = level.getBlockEntity(pos) instanceof DataCableBlockEntity cable
                && cable.getPart(face) instanceof InputBusPart real ? real : new InputBusPart();
        return new InputBusMenu(containerId, playerInventory, part, level, pos, face, busName);
    }
}
