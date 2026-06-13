/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.hardware.ComputerBuild;
import dev.jsc.jscomputronics.common.hardware.CraftingCardSpec;
import dev.jsc.jscomputronics.common.hardware.ExpansionCardKind;
import dev.jsc.jscomputronics.common.hardware.ExpansionCardSpec;
import dev.jsc.jscomputronics.common.hardware.FormFactor;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * The Crafting Computer: a Category-C computer that executes crafting recipes for the network.
 */
public class CraftingComputerBlockEntity extends AbstractComputerBlockEntity {

    // Slot layout — an ATX board: one CPU, four RAM, four PCIe (GPU and/or Crafting Card), one PSU,
    // two disks. Kept public so the assembly Menu and Screen address slots by name.
    public static final int MOTHERBOARD_SLOT = 0;
    public static final int CPU_SLOT = 1;
    public static final int RAM_SLOTS_START = 2;
    public static final int RAM_SLOTS = 4;
    public static final int PCIE_SLOTS_START = 6;
    public static final int PCIE_SLOTS = 4;
    public static final int PSU_SLOT = 10;
    public static final int DISK_SLOTS_START = 11;
    public static final int DISK_SLOTS = 2;
    public static final int HARDWARE_SLOTS = 13;

    public static final int RECIPE_ROM_LIMIT = 50;

    private static final ComputerHardwareLayout LAYOUT = new ComputerHardwareLayout(
            MOTHERBOARD_SLOT, CPU_SLOT, 1, RAM_SLOTS_START, RAM_SLOTS,
            PCIE_SLOTS_START, PCIE_SLOTS, PSU_SLOT, DISK_SLOTS_START, DISK_SLOTS, HARDWARE_SLOTS);

    public CraftingComputerBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.CRAFTING_COMPUTER_BE.get(), pos, state, LAYOUT);
    }

    @Override
    protected Set<FormFactor> acceptedFormFactors() {
        // A Crafting Computer is a PC-class machine: it takes the consumer ATX board of every era and the
        // Singularity Socket Q board.
        return Set.of(FormFactor.ATX, FormFactor.SOCKET_Q);
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final CraftingComputerBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tickNode(serverLevel);
        }
    }

    @Override
    protected void registerNode(final NetworkSystem system, final NetworkUuid network) {
        system.registerCraftingComputer(new NetworkSystem.CraftingComputerNode(
                nodeUuid(), network, capacity(), worldPosition.asLong()));
    }

    @Override
    protected void unregisterNode(final NetworkSystem system, final NetworkUuid network) {
        system.unregisterCraftingComputer(network, nodeUuid());
    }

    // Crafting hardware

    public double craftingCardFactor() {
        final ComputerBuild build = currentBuild();
        if (build == null) {
            return 0.0;
        }
        double factor = 0.0;
        for (final ExpansionCardSpec card : build.cardsOfKind(ExpansionCardKind.CRAFTING)) {
            if (card instanceof CraftingCardSpec craftingCard) {
                factor += craftingCard.cpuFactor();
            }
        }
        return factor;
    }

    public long craftingThroughput() {
        return (long) (capacity() * craftingCardFactor());
    }

    public boolean canCraft() {
        return isRunning() && craftingCardFactor() > 0.0;
    }

    // Craft execution claim — one craft at a time without a Supercomputer

    private java.util.UUID activeCraftId;

    public boolean craftBusy() {
        return activeCraftId != null;
    }

    public boolean tryClaimCraft(final java.util.UUID operationId) {
        if (activeCraftId != null && !activeCraftId.equals(operationId)) {
            return false;
        }
        activeCraftId = operationId;
        return true;
    }

    public void releaseCraft(final java.util.UUID operationId) {
        if (operationId.equals(activeCraftId)) {
            activeCraftId = null;
        }
    }

    // Recipe ROM — the computer's pattern store, hard-capped at 50

    private final java.util.List<dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern> rom =
            new java.util.ArrayList<>();

    public int romUsed() {
        return rom.size();
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern> romPatterns() {
        return java.util.Collections.unmodifiableList(rom);
    }

    public boolean romContains(final dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern pattern) {
        for (final dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern existing : rom) {
            if (existing.sameRecipe(pattern)) {
                return true;
            }
        }
        return false;
    }

    public boolean loadPattern(final dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern pattern) {
        if (rom.size() >= RECIPE_ROM_LIMIT || romContains(pattern)) {
            return false;
        }
        rom.add(pattern);
        setChanged();
        return true;
    }

    public void removePattern(final int index) {
        if (index >= 0 && index < rom.size()) {
            rom.remove(index);
            setChanged();
        }
    }

    @Override
    protected void saveExtra(final net.minecraft.nbt.CompoundTag tag,
                             final net.minecraft.core.HolderLookup.Provider registries) {
        if (!rom.isEmpty()) {
            final var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registries);
            dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern.CODEC.listOf()
                    .encodeStart(ops, rom)
                    .resultOrPartial(error -> JsComputronics.LOGGER.warn("Failed to save Recipe ROM: {}", error))
                    .ifPresent(encoded -> tag.put("RecipeRom", encoded));
        }
    }

    @Override
    protected void loadExtra(final net.minecraft.nbt.CompoundTag tag,
                             final net.minecraft.core.HolderLookup.Provider registries) {
        rom.clear();
        if (tag.contains("RecipeRom")) {
            final var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registries);
            dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern.CODEC.listOf()
                    .parse(ops, tag.get("RecipeRom"))
                    .resultOrPartial(error -> JsComputronics.LOGGER.warn("Failed to load Recipe ROM: {}", error))
                    .ifPresent(rom::addAll);
        }
    }

    // Screen sync (ContainerData wire layout — single source of truth shared with the Menu)

    public static final int DATA_RUNNING = 0;
    public static final int DATA_BUILD_VALID = 1;
    public static final int DATA_CAPACITY = 2;
    public static final int DATA_RAM_BUFFER = 3;
    public static final int DATA_AUTOSTART = 4;
    public static final int DATA_ON_NETWORK = 5;
    public static final int DATA_CRAFT_FACTOR_X100 = 6;
    public static final int DATA_CRAFT_THROUGHPUT = 7;
    public static final int DATA_ROM_USED = 8;
    public static final int DATA_COUNT = DATA_ROM_USED + 1;

    private final int[] clientData = new int[DATA_COUNT];

    private int computeData(final int index) {
        return switch (index) {
            case DATA_RUNNING -> isRunning() ? 1 : 0;
            case DATA_BUILD_VALID -> buildValid() ? 1 : 0;
            case DATA_CAPACITY -> (int) Math.min(Integer.MAX_VALUE, capacity());
            case DATA_RAM_BUFFER -> (int) Math.min(Integer.MAX_VALUE, ramBuffer());
            case DATA_AUTOSTART -> isAutoStart() ? 1 : 0;
            case DATA_ON_NETWORK -> networkUuid != null ? 1 : 0;
            case DATA_CRAFT_FACTOR_X100 -> (int) Math.round(craftingCardFactor() * 100.0);
            case DATA_CRAFT_THROUGHPUT -> (int) Math.min(Integer.MAX_VALUE, craftingThroughput());
            case DATA_ROM_USED -> romUsed();
            default -> 0;
        };
    }

    private final net.minecraft.world.inventory.ContainerData dataAccess =
            new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(final int index) {
            if (level != null && level.isClientSide) {
                return index >= 0 && index < clientData.length ? clientData[index] : 0;
            }
            return computeData(index);
        }

        @Override
        public void set(final int index, final int value) {
            if (index >= 0 && index < clientData.length) {
                clientData[index] = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public net.minecraft.world.inventory.ContainerData getDataAccess() {
        return dataAccess;
    }
}
