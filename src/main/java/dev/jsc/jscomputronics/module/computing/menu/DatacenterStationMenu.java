/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.DatacenterStationBlockEntity;
import dev.jsc.jscomputronics.module.computing.datacenter.LoadBalanceMode;
import dev.jsc.jscomputronics.module.computing.operation.payload.DatacenterSnapshotPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkItemEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Menu for the Datacenter Station terminal: it carries the player inventory (so items can be deposited into the section from the cursor) and caches the section snapshot the server pushes — the unified item index, the aggregate header and the per-Server breakdown.
 */
public class DatacenterStationMenu extends AbstractComputerMenu {

    public static final int INV_X = 34;
    public static final int INV_Y = 170;
    public static final int HOTBAR_Y = 228;

    private final BlockPos stationPos;
    private final ContainerLevelAccess access;

    private DatacenterSnapshotPayload snapshot = new DatacenterSnapshotPayload(
            "", 0, 0L, 0L, 0, 0, 0L, 0L, 0, List.of(), List.of(), List.of());

    public DatacenterStationMenu(final int containerId, final Inventory playerInventory,
                                 final DatacenterStationBlockEntity be) {
        super(ComputingModule.DATACENTER_STATION_MENU.get(), containerId);
        this.stationPos = be.getBlockPos();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        addPlayerInventory(playerInventory, INV_X, INV_Y);
    }

    @org.jetbrains.annotations.Nullable
    public static DatacenterStationMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                    final RegistryFriendlyByteBuf buf) {
        if (playerInventory.player.level().getBlockEntity(buf.readBlockPos())
                instanceof DatacenterStationBlockEntity be) {
            return new DatacenterStationMenu(containerId, playerInventory, be);
        }
        return null;
    }

    public BlockPos stationPos() {
        return stationPos;
    }

    public void setSnapshot(final DatacenterSnapshotPayload snapshot) {
        this.snapshot = snapshot;
    }

    public String sectionLabel() {
        return snapshot.sectionLabel();
    }

    public int serverCount() {
        return snapshot.serverCount();
    }

    public long storageUsed() {
        return snapshot.storageUsed();
    }

    public long storageTotal() {
        return snapshot.storageTotal();
    }

    public int availableSectionCount() {
        return snapshot.availableSectionCount();
    }

    public long cpuCapacity() {
        return snapshot.cpuCapacity();
    }

    public long ramBuffer() {
        return snapshot.ramBuffer();
    }

    public int activeOps() {
        return snapshot.activeOps();
    }

    public LoadBalanceMode loadBalanceMode() {
        final LoadBalanceMode[] values = LoadBalanceMode.values();
        final int ord = snapshot.loadBalanceMode();
        return ord >= 0 && ord < values.length ? values[ord] : LoadBalanceMode.ROUND_ROBIN;
    }

    public List<NetworkItemEntry> items() {
        return snapshot.items();
    }

    public List<DatacenterSnapshotPayload.ServerLine> servers() {
        return snapshot.servers();
    }

    public List<DatacenterSnapshotPayload.DestEntry> destinations() {
        return snapshot.destinations();
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, ComputingModule.DATACENTER_STATION.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        // Shift-click in the player inventory deposits into the section via the action payload path,
        // handled by the screen; nothing to move between menu slots here.
        return ItemStack.EMPTY;
    }
}
