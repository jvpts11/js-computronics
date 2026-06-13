/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternReaderBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.item.PatternDiscItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Menu for the Pattern Reader: a media slot plus the pattern list read straight from the synced disc stack.
 */
public class PatternReaderMenu extends AbstractComputerMenu {

    public static final int BUTTON_LOAD_SELECTED = 0;
    public static final int BUTTON_LOAD_ALL = 1;
    public static final int BUTTON_EXPORT_SELECTED = 2;
    public static final int BUTTON_EXPORT_ALL = 3;
    public static final int BUTTON_TOGGLE_BASE = 100;
    public static final int BUTTON_ROM_TOGGLE_BASE = 300;
    public static final int BUTTON_ROM_REMOVE_BASE = 500;

    public static final int MEDIA_SLOT = 0;
    private static final int PLAYER_START = 1;

    // ContainerData wire layout.
    public static final int DATA_ROM_USED = 0;
    public static final int DATA_HAS_COMPUTER = 1;
    public static final int DATA_LAST_LOADED = 2;
    private static final int DATA_COUNT = 3;

    private final PatternReaderBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    private final Set<Integer> selection = new LinkedHashSet<>();
    private final Set<Integer> romSelection = new LinkedHashSet<>();

    // Client-side cache of the adjacent computer's Recipe ROM, pushed by the server (RomSnapshotPayload).
    private List<CraftingPattern> romPatterns = List.of();

    public PatternReaderMenu(final int containerId, final Inventory playerInventory,
                             final PatternReaderBlockEntity be) {
        super(ComputingModule.PATTERN_READER_MENU.get(), containerId);
        this.blockEntity = be;
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.data = new SimpleContainerData(DATA_COUNT);

        addSlot(new SlotItemHandler(be.media(), 0, 12, 30));
        addPlayerInventory(playerInventory, 8, 138);
        addDataSlots(data);
    }

    @org.jetbrains.annotations.Nullable
    public static PatternReaderMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof PatternReaderBlockEntity be) {
            return new PatternReaderMenu(containerId, playerInventory, be);
        }
        return null;
    }


    @Override
    public void broadcastChanges() {
        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide()) {
            final CraftingComputerBlockEntity cc = blockEntity.adjacentComputer();
            data.set(DATA_ROM_USED, cc == null ? 0 : cc.romUsed());
            data.set(DATA_HAS_COMPUTER, cc == null ? 0 : 1);
        }
        super.broadcastChanges();
    }

    public List<CraftingPattern> discPatterns() {
        return PatternDiscItem.patterns(slots.get(MEDIA_SLOT).getItem());
    }

    public boolean hasComputer() {
        return data.get(DATA_HAS_COMPUTER) != 0;
    }

    public int romUsed() {
        return data.get(DATA_ROM_USED);
    }

    public int romLimit() {
        return CraftingComputerBlockEntity.RECIPE_ROM_LIMIT;
    }

    public int lastLoaded() {
        return data.get(DATA_LAST_LOADED);
    }

    public Set<Integer> selection() {
        return selection;
    }

    public Set<Integer> romSelection() {
        return romSelection;
    }

    public net.minecraft.core.BlockPos readerPos() {
        return blockEntity.getBlockPos();
    }

    public List<CraftingPattern> romPatterns() {
        return romPatterns;
    }

    public void setRomPatterns(final List<CraftingPattern> patterns) {
        this.romPatterns = List.copyOf(patterns);
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        // Remove a single pattern from the adjacent computer's ROM (ROM tab, right-click a row).
        if (id >= BUTTON_ROM_REMOVE_BASE) {
            final int index = id - BUTTON_ROM_REMOVE_BASE;
            blockEntity.removeFromRom(index);
            romSelection.clear();
            syncRom(player);
            return true;
        }
        // Toggle a ROM row's selection (ROM tab, left-click a row).
        if (id >= BUTTON_ROM_TOGGLE_BASE) {
            final int index = id - BUTTON_ROM_TOGGLE_BASE;
            if (index < blockEntity.romUsed() && !romSelection.remove(index)) {
                romSelection.add(index);
            }
            return true;
        }
        // Toggle a disc row's selection (Read tab).
        if (id >= BUTTON_TOGGLE_BASE) {
            final int index = id - BUTTON_TOGGLE_BASE;
            if (index < discPatterns().size() && !selection.remove(index)) {
                selection.add(index);
            }
            return true;
        }
        if (id == BUTTON_LOAD_SELECTED) {
            data.set(DATA_LAST_LOADED, blockEntity.loadSelected(new ArrayList<>(selection)));
            selection.clear();
            syncRom(player);
            return true;
        }
        if (id == BUTTON_LOAD_ALL) {
            data.set(DATA_LAST_LOADED, blockEntity.loadAll());
            selection.clear();
            syncRom(player);
            return true;
        }
        if (id == BUTTON_EXPORT_SELECTED) {
            data.set(DATA_LAST_LOADED, blockEntity.exportToDisc(new ArrayList<>(romSelection)));
            romSelection.clear();
            return true;
        }
        if (id == BUTTON_EXPORT_ALL) {
            data.set(DATA_LAST_LOADED, blockEntity.exportAll());
            romSelection.clear();
            return true;
        }
        return false;
    }

    /**
     * Pushes a fresh copy of the adjacent computer's Recipe ROM to the viewing player after an action
     * that may have changed it, so the ROM tab stays in sync without polling every tick.
     */
    private void syncRom(final Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads
                    .sendRomSnapshot(serverPlayer, blockEntity);
        }
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, ComputingModule.PATTERN_READER.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();

        if (index == MEDIA_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_START, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof PatternDiscItem) {
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
