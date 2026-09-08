/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.menu;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.block.part.ExportBusPart;
import dev.jstech.computers.blockentity.DataCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

/**
 * Menu for configuring an Export Bus part mounted on a data cable. All behavior lives in {@link AbstractBusMenu}; this only binds the export menu type and the create/fromNetwork factories.
 */
public class ExportBusMenu extends AbstractBusMenu {

    public ExportBusMenu(final int containerId, final Inventory playerInventory, final ExportBusPart part,
                         final Level level, final BlockPos cablePos, final Direction face, final String busName) {
        super(ComputingModule.EXPORT_BUS_MENU.get(), containerId, playerInventory, part, level, cablePos, face, busName);
    }

    public static ExportBusMenu create(final int containerId, final Inventory playerInventory,
                                       final DataCableBlockEntity cable, final Direction face) {
        final ExportBusPart part = cable.getPart(face) instanceof ExportBusPart real ? real : new ExportBusPart();
        return new ExportBusMenu(containerId, playerInventory, part, cable.getLevel(), cable.getBlockPos(),
                face, part.name());
    }

    public static ExportBusMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                            final RegistryFriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final Direction face = Direction.from3DDataValue(buf.readByte());
        final String busName = buf.readUtf();
        final Level level = playerInventory.player.level();
        final ExportBusPart part = level.getBlockEntity(pos) instanceof DataCableBlockEntity cable
                && cable.getPart(face) instanceof ExportBusPart real ? real : new ExportBusPart();
        return new ExportBusMenu(containerId, playerInventory, part, level, pos, face, busName);
    }
}
