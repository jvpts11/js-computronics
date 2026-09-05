/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.function.IntSupplier;

/**
 * A hardware slot usable only while its relative index is within the count the installed motherboard offers — so the CPU/RAM/PCIe/disk bays appear and accept parts according to the board, and none of them do until a board is installed (the limit is then 0). Shared by every computer-assembly menu so the board-gated slot behaves identically everywhere.
 */
final class BoardSlot extends SlotItemHandler {

    private final int relativeIndex;
    private final IntSupplier boardLimit;

    BoardSlot(final IItemHandler handler, final int index, final int x, final int y,
              final int relativeIndex, final IntSupplier boardLimit) {
        super(handler, index, x, y);
        this.relativeIndex = relativeIndex;
        this.boardLimit = boardLimit;
    }

    @Override
    public boolean isActive() {
        // Within the board's slot count, or already holding a part — so a component is never
        // trapped behind a smaller board swapped in later.
        return relativeIndex < boardLimit.getAsInt() || hasItem();
    }

    @Override
    public boolean mayPlace(final ItemStack stack) {
        return relativeIndex < boardLimit.getAsInt() && super.mayPlace(stack);
    }
}
