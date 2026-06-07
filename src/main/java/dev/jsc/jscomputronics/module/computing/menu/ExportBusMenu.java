/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.block.part.ExportBusPart;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for configuring an Export Bus part mounted on a data cable: a single ghost filter slot (the item to export), the destination min/max stock window, and the continuous/redstone mode, plus the player inventory.
 */
public class ExportBusMenu extends AbstractContainerMenu {

    public static final int FILTER_SLOT = 0;

    // Stepper button ids: field + direction + step.
    public static final int BTN_MIN_DOWN1 = 0;
    public static final int BTN_MIN_UP1 = 1;
    public static final int BTN_MIN_DOWN16 = 2;
    public static final int BTN_MIN_UP16 = 3;
    public static final int BTN_MAX_DOWN1 = 4;
    public static final int BTN_MAX_UP1 = 5;
    public static final int BTN_MAX_DOWN16 = 6;
    public static final int BTN_MAX_UP16 = 7;
    public static final int BTN_MODE = 8;

    private final ExportBusPart part;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public ExportBusMenu(final int containerId, final Inventory playerInventory,
                         final ExportBusPart part, final Level level, final BlockPos cablePos) {
        super(ComputingModule.EXPORT_BUS_MENU.get(), containerId);
        this.part = part;
        this.data = part.getDataAccess();
        this.access = ContainerLevelAccess.create(level, cablePos);

        addSlot(new SlotItemHandler(part.getFilterHandler(), 0, 12, 30) {
            @Override
            public boolean mayPlace(final ItemStack stack) {
                return false; // the filter is set by a click, never by dropping an item in
            }

            @Override
            public boolean mayPickup(final Player player) {
                return false;
            }
        });
        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    public static ExportBusMenu create(final int containerId, final Inventory playerInventory,
                                       final DataCableBlockEntity cable, final Direction face) {
        final ExportBusPart part = cable.getPart(face) instanceof ExportBusPart real
                ? real : new ExportBusPart();
        return new ExportBusMenu(containerId, playerInventory, part, cable.getLevel(), cable.getBlockPos());
    }

    public static ExportBusMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                            final RegistryFriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final Direction face = Direction.from3DDataValue(buf.readByte());
        final Level level = playerInventory.player.level();
        final ExportBusPart part = level.getBlockEntity(pos) instanceof DataCableBlockEntity cable
                && cable.getPart(face) instanceof ExportBusPart real ? real : new ExportBusPart();
        return new ExportBusMenu(containerId, playerInventory, part, level, pos);
    }

    private void addPlayerInventory(final Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 89 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 147));
        }
    }

    public int min() {
        return data.get(0);
    }

    public int max() {
        return data.get(1);
    }

    public int mode() {
        return data.get(2);
    }

    public boolean linked() {
        return data.get(3) != 0;
    }

    public ItemStack filterStack() {
        return getSlot(FILTER_SLOT).getItem();
    }

    @Override
    public void clicked(final int slotId, final int button, final ClickType type, final Player player) {
        // Clicking the filter slot sets it from the carried item (a copy), or clears
        // it with an empty cursor — the player's item is never consumed.
        if (slotId == FILTER_SLOT) {
            part.setFilter(getCarried());
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        switch (id) {
            case BTN_MIN_DOWN1 -> part.adjustMin(-1);
            case BTN_MIN_UP1 -> part.adjustMin(1);
            case BTN_MIN_DOWN16 -> part.adjustMin(-16);
            case BTN_MIN_UP16 -> part.adjustMin(16);
            case BTN_MAX_DOWN1 -> part.adjustMax(-1);
            case BTN_MAX_UP1 -> part.adjustMax(1);
            case BTN_MAX_DOWN16 -> part.adjustMax(-16);
            case BTN_MAX_UP16 -> part.adjustMax(16);
            case BTN_MODE -> part.toggleMode();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(final Player player) {
        return access.evaluate((level, pos) ->
                level.getBlockState(pos).getBlock() instanceof DataCableBlock
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                true);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        // Only the player inventory holds real items; shift-click just reorganizes it.
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || index == FILTER_SLOT) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        final int invStart = 1;
        final int invEnd = slots.size();
        // Move between the main inventory and the hotbar.
        final int hotbarStart = invEnd - 9;
        if (index < hotbarStart) {
            if (!moveItemStackTo(stack, hotbarStart, invEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, invStart, hotbarStart, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }
}
