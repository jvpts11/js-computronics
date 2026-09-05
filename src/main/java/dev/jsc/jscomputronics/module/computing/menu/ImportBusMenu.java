/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.part.ImportBusPart;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

/**
 * Menu for configuring an Import Bus part mounted on a data cable. All behavior lives in {@link AbstractBusMenu}; this only binds the import menu type and the create/fromNetwork factories.
 */
public class ImportBusMenu extends AbstractBusMenu {

    public ImportBusMenu(final int containerId, final Inventory playerInventory, final ImportBusPart part,
                         final Level level, final BlockPos cablePos, final Direction face, final String busName) {
        super(ComputingModule.IMPORT_BUS_MENU.get(), containerId, playerInventory, part, level, cablePos, face, busName);
    }

    public static ImportBusMenu create(final int containerId, final Inventory playerInventory,
                                       final DataCableBlockEntity cable, final Direction face) {
        final ImportBusPart part = cable.getPart(face) instanceof ImportBusPart real ? real : new ImportBusPart();
        return new ImportBusMenu(containerId, playerInventory, part, cable.getLevel(), cable.getBlockPos(),
                face, part.name());
    }

    public static ImportBusMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                            final RegistryFriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final Direction face = Direction.from3DDataValue(buf.readByte());
        final String busName = buf.readUtf();
        final Level level = playerInventory.player.level();
        final ImportBusPart part = level.getBlockEntity(pos) instanceof DataCableBlockEntity cable
                && cable.getPart(face) instanceof ImportBusPart real ? real : new ImportBusPart();
        return new ImportBusMenu(containerId, playerInventory, part, level, pos, face, busName);
    }
}
