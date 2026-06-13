/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.MonitorBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkItemEntry;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for the Monitor terminal — the tabbed interface a Monitor opens onto the computer it is linked to.
 */
public class ComputerTerminalMenu extends AbstractComputerMenu {

    public static final int TAB_LOCAL = 0;
    public static final int TAB_STORAGE = 1;
    public static final int TAB_NETWORK = 2;
    public static final int TAB_OPS = 3;
    public static final int TAB_TASKS = 4;
    public static final int TAB_MAINTENANCE = 5;
    public static final int TAB_CRAFT = 6;
    // A launch-only rail entry: clicking it opens the Command Prompt rather than switching content,
    // so it is never the active tab (the menu's initial-tab clamp stops at TAB_CRAFT).
    public static final int TAB_CONSOLE = 7;

    // Slot layout (relative to the screen's top-left). The screen draws the slot
    // backgrounds and the inventory at these exact positions.
    public static final int STORAGE_COLS = 9;
    public static final int STORAGE_X = 68;
    public static final int STORAGE_Y = 40;
    public static final int INV_X = 41;
    public static final int INV_Y = 148;
    public static final int HOTBAR_Y = 206;
    public static final int MAINFRAME_INV_DROP = 22;

    private static final double MONITOR_REACH = 16.0;

    private static final int DATA_COUNT = 30;
    private static final int DATA_USABLE_SLOTS = 18;
    private static final int DATA_CRAFT_COMPUTERS = 29;

    private final Level level;
    @Nullable
    private final ComputerTerminalHost host;
    @Nullable
    private final ServerPlayer serverPlayer;
    private final BlockPos hostPos;
    private final BlockPos monitorPos;
    private final int storageCount;
    private int refreshTick;
    private boolean initialDataSent;

    private int activeTab;

    private final int invDrop;

    private java.util.List<NetworkItemEntry> networkItems = java.util.List.of();

    private java.util.List<NetworkItemEntry> localItems = java.util.List.of();

    private java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.ServerBreakdownPayload.ServerHolding>
            serverBreakdown = java.util.List.of();

    private java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord>
            operationsLog = java.util.List.of();

    private java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord>
            activeOps = java.util.List.of();

    private java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.NetworkServersPayload.ServerEntry>
            networkServers = java.util.List.of();

    private final int[] clientData = new int[DATA_COUNT];

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(final int index) {
            if (level.isClientSide) {
                return index >= 0 && index < clientData.length ? clientData[index] : 0;
            }
            return serverValue(index);
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

    public ComputerTerminalMenu(final int containerId, final Inventory playerInventory,
                                @Nullable final ComputerTerminalHost host,
                                final BlockPos hostPos, final BlockPos monitorPos, final int initialTab) {
        super(ComputingModule.COMPUTER_TERMINAL_MENU.get(), containerId);
        this.level = playerInventory.player.level();
        this.serverPlayer = playerInventory.player instanceof ServerPlayer sp ? sp : null;
        this.host = host;
        this.hostPos = hostPos.immutable();
        this.monitorPos = monitorPos.immutable();
        // Open on the player's last-used tab; fall back to Network, and never land on the
        // Mainframe-only Task Manager when the host is a plain computer.
        int tab = initialTab >= TAB_LOCAL && initialTab <= TAB_CRAFT ? initialTab : TAB_NETWORK;
        if ((tab == TAB_TASKS || tab == TAB_MAINTENANCE) && (host == null || !host.isMainframeHost())) {
            tab = TAB_NETWORK;
        }
        if (tab == TAB_CRAFT && craftComputerCount() <= 0 && !level.isClientSide) {
            tab = TAB_NETWORK; // the Craft tab vanished since last session (computer removed)
        }
        this.activeTab = tab;
        this.invDrop = host != null && host.isMainframeHost() ? MAINFRAME_INV_DROP : 0;

        // The Storage tab is now a disk-backed quantity view (like the Network tab), not vanilla
        // slots, so the menu holds only the player inventory; local items are synced via snapshot.
        this.storageCount = 0;
        addPlayerInventory(playerInventory, INV_X, INV_Y + invDrop);
        addDataSlots(data);
    }

    public int invY() {
        return INV_Y + invDrop;
    }

    public int hotbarY() {
        return HOTBAR_Y + invDrop;
    }

    public int invDrop() {
        return invDrop;
    }

    @Nullable
    public static ComputerTerminalMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                                   final RegistryFriendlyByteBuf buf) {
        final BlockPos monitorPos = buf.readBlockPos();
        final BlockPos hostPos = buf.readBlockPos();
        final int initialTab = buf.readVarInt();
        final var be = playerInventory.player.level().getBlockEntity(hostPos);
        if (be instanceof ComputerTerminalHost terminalHost) {
            return new ComputerTerminalMenu(containerId, playerInventory, terminalHost, hostPos, monitorPos, initialTab);
        }
        return null;
    }

    private int serverValue(final int index) {
        if (host == null) {
            return 0;
        }
        return switch (index) {
            case 0 -> host.computerRunning() ? 1 : 0;
            case 1 -> host.computerBuildValid() ? 1 : 0;
            case 2 -> host.networkLinkState();
            case 3 -> clampInt(host.orchestrationCapacity());
            case 4 -> host.computerQueues();
            case 5 -> clampInt(host.computerRamBuffer());
            case 6 -> host.networkServerCount();
            case 7 -> host.installedCpus();
            case 8 -> host.cpuSlots();
            case 9 -> host.installedRam();
            case 10 -> host.ramSlots();
            case 11 -> host.installedGpus();
            case 12 -> host.gpuSlots();
            case 13 -> host.installedDisks();
            case 14 -> host.diskSlots();
            case 15 -> clampInt(host.localStorageUsed());
            case 16 -> clampInt(host.localStorageCapacity());
            case 17 -> host.isMainframeHost() ? 1 : 0;
            case DATA_USABLE_SLOTS -> host.usableStorageSlots();
            case 19 -> host.pendingOperations();
            case 20 -> host.runningOperations();
            case 21 -> host.completedOperations();
            case 22 -> host.networkPcCount();
            case 23 -> host.networkSubframeCount();
            case 24 -> host.indexedTypes();
            case 25 -> host.indexedServers();
            case 26 -> host.activeLocks();
            case 27 -> clampInt(host.networkStorageUsed());
            case 28 -> clampInt(host.networkStorageTotal());
            case DATA_CRAFT_COMPUTERS -> craftComputerCount();
            default -> 0;
        };
    }

    private int craftComputerCount() {
        if (host == null || host.networkUuid() == null || !(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        return dev.jsc.jscomputronics.common.network.NetworkSystem.get(serverLevel)
                .craftingComputersOf(host.networkUuid()).size();
    }

    public boolean craftAvailable() {
        return data.get(DATA_CRAFT_COMPUTERS) > 0;
    }

    private static int clampInt(final long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, value));
    }

    // Tabs

    public int activeTab() {
        return activeTab;
    }

    public void setActiveTab(final int tab) {
        this.activeTab = tab;
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        if (id >= TAB_LOCAL && id <= TAB_CRAFT) {
            this.activeTab = id;
            // Remember the tab on the Monitor so reopening this terminal lands here again.
            if (level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitor) {
                monitor.setLastTab(id);
            }
            if (player instanceof ServerPlayer sp && level instanceof ServerLevel serverLevel) {
                dispatchTabData(id, sp, serverLevel);
            }
            return true;
        }
        return false;
    }

    private void dispatchTabData(final int id, final ServerPlayer serverPlayer, final ServerLevel serverLevel) {
        if (host == null) {
            return;
        }
        if (id == TAB_STORAGE) {
            ComputingPayloads.dispatchLocalSnapshot(serverPlayer, host);
        } else if (host.networkUuid() != null) {
            if (id == TAB_NETWORK) {
                ComputingPayloads.dispatchTerminalQuery(serverPlayer, host.networkUuid(), serverLevel);
            } else if (id == TAB_OPS || id == TAB_TASKS) {
                ComputingPayloads.dispatchTerminalOpsLog(serverPlayer, host.networkUuid(), serverLevel);
                ComputingPayloads.dispatchActiveOperations(serverPlayer, host.networkUuid(), serverLevel);
            } else if (id == TAB_MAINTENANCE) {
                // The DROP popup needs the network's data types (the TYPES grid) and the list of
                // Servers it can wipe (the SERVER picker); the index stats arrive via ContainerData.
                ComputingPayloads.dispatchTerminalQuery(serverPlayer, host.networkUuid(), serverLevel);
                ComputingPayloads.dispatchNetworkServers(serverPlayer, host.networkUuid(), serverLevel);
            } else if (id == TAB_CRAFT) {
                // The Craft tab needs the catalog plus the live/logged Operations for its
                // RUNNING and RECENT panels.
                ComputingPayloads.dispatchCraftCatalog(serverPlayer, host.networkUuid(), serverLevel);
                ComputingPayloads.dispatchTerminalOpsLog(serverPlayer, host.networkUuid(), serverLevel);
                ComputingPayloads.dispatchActiveOperations(serverPlayer, host.networkUuid(), serverLevel);
            }
        }
    }

    private java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload
            .CraftCatalogPayload.Entry> craftCatalog = java.util.List.of();

    @Nullable
    private dev.jsc.jscomputronics.module.computing.operation.payload.CraftPlanPayload craftPlan;

    public void setCraftCatalog(final java.util.List<
            dev.jsc.jscomputronics.module.computing.operation.payload.CraftCatalogPayload.Entry> entries) {
        this.craftCatalog = entries;
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload
            .CraftCatalogPayload.Entry> craftCatalog() {
        return craftCatalog;
    }

    public void setCraftPlan(
            @Nullable final dev.jsc.jscomputronics.module.computing.operation.payload.CraftPlanPayload plan) {
        this.craftPlan = plan;
    }

    @Nullable
    public dev.jsc.jscomputronics.module.computing.operation.payload.CraftPlanPayload craftPlan() {
        return craftPlan;
    }

    public void setNetworkItems(final java.util.List<NetworkItemEntry> items) {
        this.networkItems = items;
    }

    public java.util.List<NetworkItemEntry> networkItems() {
        return networkItems;
    }

    public void setLocalItems(final java.util.List<NetworkItemEntry> items) {
        this.localItems = items;
    }

    public java.util.List<NetworkItemEntry> localItems() {
        return localItems;
    }

    public void setServerBreakdown(
            final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.ServerBreakdownPayload.ServerHolding> rows) {
        this.serverBreakdown = rows;
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.ServerBreakdownPayload.ServerHolding> serverBreakdown() {
        return serverBreakdown;
    }

    public void setNetworkServers(
            final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.NetworkServersPayload.ServerEntry> servers) {
        this.networkServers = servers;
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.NetworkServersPayload.ServerEntry> networkServers() {
        return networkServers;
    }

    public void setOperationsLog(
            final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> ops) {
        this.operationsLog = ops;
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> operationsLog() {
        return operationsLog;
    }

    public void setActiveOps(
            final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> ops) {
        this.activeOps = ops;
    }

    public java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> activeOps() {
        return activeOps;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (serverPlayer == null || host == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Populate the first-shown tab once, right after the terminal opens. clickMenuButton only
        if (!initialDataSent) {
            initialDataSent = true;
            dispatchTabData(activeTab, serverPlayer, serverLevel);
        }
        if (host.networkUuid() == null) {
            return;
        }
        if (++refreshTick < 10) {
            return;
        }
        refreshTick = 0;
        // The Task Manager always re-syncs (so finished ops drop off); the Network grid re-queries
        // only while something is in flight (its snapshot is already pushed on deposit/withdraw/settle).
        if (activeTab == TAB_TASKS || activeTab == TAB_OPS) {
            ComputingPayloads.dispatchActiveOperations(serverPlayer, host.networkUuid(), serverLevel);
        }
        if (activeTab == TAB_CRAFT
                && ComputingPayloads.networkHasActiveOps(serverLevel, host.networkUuid())) {
            // Keep the RUNNING bars moving and settle finished crafts into RECENT.
            ComputingPayloads.dispatchActiveOperations(serverPlayer, host.networkUuid(), serverLevel);
            ComputingPayloads.dispatchTerminalOpsLog(serverPlayer, host.networkUuid(), serverLevel);
        }
        if (activeTab == TAB_NETWORK
                && ComputingPayloads.networkHasActiveOps(serverLevel, host.networkUuid())) {
            ComputingPayloads.dispatchTerminalQuery(serverPlayer, host.networkUuid(), serverLevel);
        }
    }

    public BlockPos monitorPos() {
        return monitorPos;
    }

    public BlockPos hostPos() {
        return hostPos;
    }

    // Screen accessors (read from the synced data on the client)

    public boolean running() {
        return data.get(0) != 0;
    }

    public boolean buildValid() {
        return data.get(1) != 0;
    }

    public int networkLinkState() {
        return data.get(2);
    }

    public long capacity() {
        return data.get(3);
    }

    public int queues() {
        return data.get(4);
    }

    public long ramBuffer() {
        return data.get(5);
    }

    public int serverCount() {
        return data.get(6);
    }

    public int installedCpus() {
        return data.get(7);
    }

    public int cpuSlots() {
        return data.get(8);
    }

    public int installedRam() {
        return data.get(9);
    }

    public int ramSlots() {
        return data.get(10);
    }

    public int installedGpus() {
        return data.get(11);
    }

    public int gpuSlots() {
        return data.get(12);
    }

    public int installedDisks() {
        return data.get(13);
    }

    public int diskSlots() {
        return data.get(14);
    }

    public long storageUsed() {
        return data.get(15);
    }

    public long storageCapacity() {
        return data.get(16);
    }

    public boolean mainframeHost() {
        return data.get(17) != 0;
    }

    public int indexedTypes() {
        return data.get(24);
    }

    public int indexedServers() {
        return data.get(25);
    }

    public int activeLocks() {
        return data.get(26);
    }

    public long networkStorageUsed() {
        return data.get(27);
    }

    public long networkStorageTotal() {
        return data.get(28);
    }

    public int usableStorageSlots() {
        return data.get(DATA_USABLE_SLOTS);
    }

    public int pendingOps() {
        return data.get(19);
    }

    public int runningOps() {
        return data.get(20);
    }

    public int completedOps() {
        return data.get(21);
    }

    public int pcCount() {
        return data.get(22);
    }

    public int subframeCount() {
        return data.get(23);
    }

    public int storageSlotCount() {
        return storageCount;
    }

    @Override
    public boolean stillValid(final Player player) {
        // Reach is to the Monitor, and the Monitor must still link to this computer.
        if (!(level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitor)) {
            return false;
        }
        final BlockPos owner = monitor.ownerPos();
        return owner != null && owner.equals(hostPos)
                && player.distanceToSqr(monitorPos.getX() + 0.5, monitorPos.getY() + 0.5,
                monitorPos.getZ() + 0.5) <= MONITOR_REACH * MONITOR_REACH;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack stack = slot.getItem();
        final ItemStack original = stack.copy();
        final int invStart = storageCount;
        final int invEnd = storageCount + 36;

        if (index < invStart) {
            // storage -> player inventory
            if (!moveItemStackTo(stack, invStart, invEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // player inventory -> storage, but only while the Storage tab is open
            final int limit = activeTab == TAB_STORAGE ? Math.min(storageCount, usableStorageSlots()) : 0;
            if (limit <= 0 || !moveItemStackTo(stack, 0, limit, false)) {
                return ItemStack.EMPTY;
            }
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
