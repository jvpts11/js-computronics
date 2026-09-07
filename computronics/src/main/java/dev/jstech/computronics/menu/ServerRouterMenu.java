/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.menu;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.ServerRouterBlockEntity;
import dev.jstech.computronics.datacenter.LoadBalanceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Server Router's config GUI: no item slots; it exposes the live topology summary the router writes into its synced data (input face, managed-rack budget, and one row per output-face section with its rack/server counts and load-balance mode), and routes a row's mode-cycle click back to the router.
 */
public class ServerRouterMenu extends AbstractContainerMenu {

    private final ServerRouterBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final BlockPos routerPos;
    private final String initialName;

    public ServerRouterMenu(final int containerId, final Inventory playerInventory,
                            final ServerRouterBlockEntity be, final String initialName) {
        super(ComputingModule.SERVER_ROUTER_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.getDataAccess();
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.routerPos = be.getBlockPos();
        this.initialName = initialName;
        addDataSlots(data);
    }

    @org.jetbrains.annotations.Nullable
    public static ServerRouterMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                               final RegistryFriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final String name = buf.readUtf();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof ServerRouterBlockEntity be) {
            return new ServerRouterMenu(containerId, playerInventory, be, name);
        }
        return null;
    }

    public BlockPos routerPos() {
        return routerPos;
    }

    public String initialName() {
        return initialName;
    }

    @org.jetbrains.annotations.Nullable
    public Direction inputFace() {
        final int v = data.get(ServerRouterBlockEntity.DATA_INPUT_FACE);
        return v < 0 ? null : Direction.from3DDataValue(v);
    }

    public int managedRacks() {
        return data.get(ServerRouterBlockEntity.DATA_MANAGED_RACKS);
    }

    public int maxRacks() {
        return data.get(ServerRouterBlockEntity.DATA_MAX_RACKS);
    }

    public boolean overCapacity() {
        return data.get(ServerRouterBlockEntity.DATA_OVER_CAPACITY) != 0;
    }

    public int sectionCount() {
        return data.get(ServerRouterBlockEntity.DATA_SECTION_COUNT);
    }

    private int sectionField(final int section, final int field) {
        return data.get(ServerRouterBlockEntity.DATA_SECTION_BASE
                + section * ServerRouterBlockEntity.DATA_PER_SECTION + field);
    }

    @org.jetbrains.annotations.Nullable
    public Direction sectionFace(final int i) {
        final int v = sectionField(i, 0);
        return v < 0 ? null : Direction.from3DDataValue(v);
    }

    public int sectionRacks(final int i) {
        return sectionField(i, 1);
    }

    public int sectionServers(final int i) {
        return sectionField(i, 2);
    }

    public LoadBalanceMode sectionMode(final int i) {
        final int ord = sectionField(i, 3);
        final LoadBalanceMode[] values = LoadBalanceMode.values();
        return ord >= 0 && ord < values.length ? values[ord] : LoadBalanceMode.ROUND_ROBIN;
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        // The button id is the output face's 3D data value; cycle that section's load-balance mode.
        if (id >= 0 && id < Direction.values().length) {
            blockEntity.cycleLoadBalanceMode(Direction.from3DDataValue(id));
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(final Player player) {
        return stillValid(access, player, ComputingModule.SERVER_ROUTER.get());
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        return ItemStack.EMPTY; // no slots to shift between
    }
}
