/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.common.hardware.MotherboardSpec;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.item.MotherboardItem;
import dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for assembling a Server: hardware slots (board, CPU, RAM, GPU, PSU, disks) over the held Server item's hardware, plus the player inventory.
 */
public class ServerAssemblyMenu extends AbstractComputerMenu {

    private static final int HARDWARE_SLOTS = ServerHardwareHandler.SLOTS;

    private final Player owner;
    private final InteractionHand hand;
    private final ServerHardwareHandler hw;

    public ServerAssemblyMenu(final int containerId, final Inventory playerInventory,
                              final InteractionHand hand) {
        super(ComputingModule.SERVER_ASSEMBLY_MENU.get(), containerId);
        this.owner = playerInventory.player;
        this.hand = hand;
        this.hw = new ServerHardwareHandler(owner, hand);

        // The spec readout (tiles + tracks + problems, in a smaller font) sits on top;
        // the bays follow. Left column: board + PSU. No disk slots since the racks rework —
        // a server's drives live in the rack's front-panel hotswap slots, not in the chassis.
        addSlot(new SlotItemHandler(hw, ServerHardwareHandler.MOBO, 8, 96));
        addSlot(new SlotItemHandler(hw, ServerHardwareHandler.PSU, 26, 96));
        // Middle column: CPUs on a row, RAM in 2 rows, GPUs in 2 rows.
        for (int i = 0; i < ServerHardwareHandler.CPU; i++) {
            addSlot(new BoardSlot(hw, ServerHardwareHandler.CPU_START + i, 52 + i * 18, 96, i, this::boardCpuSlots));
        }
        for (int i = 0; i < ServerHardwareHandler.RAM; i++) {
            addSlot(new BoardSlot(hw, ServerHardwareHandler.RAM_START + i,
                    52 + (i % 4) * 18, 126 + (i / 4) * 18, i, this::boardRamSlots));
        }
        for (int i = 0; i < ServerHardwareHandler.GPU; i++) {
            addSlot(new BoardSlot(hw, ServerHardwareHandler.GPU_START + i,
                    52 + (i % 3) * 18, 174 + (i / 3) * 18, i, this::boardGpuSlots));
        }

        addPlayerInventory(playerInventory, 8, 214);
    }

    private MotherboardSpec boardSpec() {
        return hw.getStackInSlot(ServerHardwareHandler.MOBO).getItem() instanceof MotherboardItem board
                ? board.spec() : null;
    }

    private dev.jsc.jscomputronics.module.computing.rack.RackChassis chassis() {
        final var chassis = ServerItem.chassisOf(owner.getItemInHand(hand));
        return chassis != null ? chassis : dev.jsc.jscomputronics.module.computing.rack.RackChassis.SERVER;
    }

    public int boardCpuSlots() {
        // The chassis caps the sockets the board offers: the lower of the two wins.
        final MotherboardSpec spec = boardSpec();
        return spec == null ? 0
                : Math.min(Math.min(ServerHardwareHandler.CPU, spec.cpuSlots()), chassis().maxCpus());
    }

    public int boardRamSlots() {
        final MotherboardSpec spec = boardSpec();
        return spec == null ? 0 : Math.min(ServerHardwareHandler.RAM, spec.ramSlots());
    }

    public int boardGpuSlots() {
        final MotherboardSpec spec = boardSpec();
        return spec == null ? 0
                : Math.min(Math.min(ServerHardwareHandler.GPU, spec.pcieSlots()), chassis().maxPcie());
    }


    public static ServerAssemblyMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                 final RegistryFriendlyByteBuf buf) {
        return new ServerAssemblyMenu(containerId, playerInventory, buf.readEnum(InteractionHand.class));
    }

    @org.jetbrains.annotations.Nullable
    public dev.jsc.jscomputronics.common.hardware.ComputerBuild currentBuild() {
        final java.util.List<ItemStack> parts = new java.util.ArrayList<>(HARDWARE_SLOTS);
        for (int i = 0; i < HARDWARE_SLOTS; i++) {
            parts.add(slots.get(i).getItem());
        }
        return ServerItem.buildFrom(parts);
    }

    @org.jetbrains.annotations.Nullable
    public dev.jstech.core.tier.HardwareEra hardwareEra() {
        final dev.jsc.jscomputronics.common.hardware.ComputerBuild build = currentBuild();
        return build == null ? null : build.motherboard().era();
    }

    @org.jetbrains.annotations.Nullable
    public java.util.UUID nodeUuid() {
        return ServerItem.nodeUuid(owner.getItemInHand(hand));
    }


    public String serverName() {
        return ServerItem.customName(owner.getItemInHand(hand));
    }

    public void setServerName(final String name) {
        final String capped = name.strip();
        ServerItem.setCustomName(owner.getItemInHand(hand),
                capped.length() > dev.jsc.jscomputronics.module.computing.operation.payload.RenameServerPayload.MAX_LEN
                        ? capped.substring(0,
                            dev.jsc.jscomputronics.module.computing.operation.payload.RenameServerPayload.MAX_LEN)
                        : capped);
        broadcastChanges();
    }

    @Override
    public boolean stillValid(final Player player) {
        // Valid only while the player is still holding a Server in that hand.
        return owner == player && owner.getItemInHand(hand).getItem() instanceof ServerItem;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return quickMoveBetweenContainerAndPlayer(player, index, HARDWARE_SLOTS);
    }
}
