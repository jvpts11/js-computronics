/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.PatternDiscItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for the Pattern Encoder: a ghost 3x3 recipe grid (clicks set count-1 copies, the player's items are never consumed), a live result preview resolved server-side from the recipe book, a real media slot, and the player inventory.
 */
public class PatternEncoderMenu extends AbstractComputerMenu {

    public static final int BUTTON_WRITE = 0;
    public static final int BUTTON_ERASE = 1;

    public static final int GRID_START = 0;
    public static final int PREVIEW_SLOT = 9;
    public static final int MEDIA_SLOT = 10;
    private static final int PLAYER_START = 11;

    private final PatternEncoderBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    private final ItemStackHandler previewMirror = new ItemStackHandler(1);

    public PatternEncoderMenu(final int containerId, final Inventory playerInventory,
                              final PatternEncoderBlockEntity be) {
        super(ComputingModule.PATTERN_ENCODER_MENU.get(), containerId);
        this.blockEntity = be;
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        // Ghost grid 3x3 — clicks are intercepted in clicked(); items never move.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new GhostSlot(be.ghostGrid(), col + row * 3, 26 + col * 18, 32 + row * 18));
            }
        }
        // Result preview — read-only, written by broadcastChanges from the BE's resolver.
        addSlot(new SlotItemHandler(previewMirror, 0, 100, 50) {
            @Override
            public boolean mayPlace(final ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(final Player player) {
                return false;
            }
        });
        // Media bay — a real slot restricted to pattern discs.
        addSlot(new SlotItemHandler(be.media(), 0, 138, 32));

        addPlayerInventory(playerInventory, 8, 138);
    }

    @org.jetbrains.annotations.Nullable
    public static PatternEncoderMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                 final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof PatternEncoderBlockEntity be) {
            return new PatternEncoderMenu(containerId, playerInventory, be);
        }
        return null;
    }


    /**
     * A ghost cell: never holds a real item — a click records a copy of the carried stack.
     */
    private static final class GhostSlot extends SlotItemHandler {
        private GhostSlot(final ItemStackHandler handler, final int index, final int x, final int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(final ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(final Player player) {
            return false;
        }
    }

    @Override
    public void clicked(final int slotId, final int button, final ClickType type, final Player player) {
        // Ghost grid: set the cell from the carried item (a copy), or clear with an empty cursor.
        if (slotId >= GRID_START && slotId < GRID_START + 9) {
            blockEntity.setGhost(slotId - GRID_START, getCarried());
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (id == BUTTON_WRITE) {
            return blockEntity.writePattern();
        }
        if (id == BUTTON_ERASE) {
            return blockEntity.eraseMedia();
        }
        return false;
    }

    @Override
    public void broadcastChanges() {
        // Refresh the preview mirror from the BE before the container diffs the slots,
        // so the client sees the resolved result without a dedicated payload.
        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide()) {
            blockEntity.refreshPreview();
            previewMirror.setStackInSlot(0, blockEntity.preview().copy());
        }
        super.broadcastChanges();
    }

    public ItemStack preview() {
        return slots.get(PREVIEW_SLOT).getItem();
    }

    public ItemStack mediaStack() {
        return slots.get(MEDIA_SLOT).getItem();
    }

    public boolean canErase() {
        final ItemStack disc = mediaStack();
        return disc.getItem() instanceof PatternDiscItem item && item.canErase(disc);
    }

    public boolean canWrite() {
        return !preview().isEmpty() && !mediaStack().isEmpty();
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, ComputingModule.PATTERN_ENCODER.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || index < PLAYER_START) {
            if (index == MEDIA_SLOT && slot != null && slot.hasItem()) {
                // Shift-click the disc back to the inventory.
                final ItemStack stack = slot.getItem();
                final ItemStack original = stack.copy();
                if (!moveItemStackTo(stack, PLAYER_START, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
                slot.onTake(player, stack);
                return original;
            }
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        // From the inventory: discs go to the media bay; everything else stays put
        // (the recipe grid is ghost-only and never receives real items).
        if (stack.getItem() instanceof PatternDiscItem) {
            if (!moveItemStackTo(stack, MEDIA_SLOT, MEDIA_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
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
