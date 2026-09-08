/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.menu;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.blockentity.MonitorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The server-side menu the Frames desktop opens on. It carries the bound monitor and host positions plus
 * the installed OS id and the machine name, so the client can rebuild the desktop shell. Being a real
 * menu (rather than a client-only screen) gives the desktop a synchronised carried cursor and lets the
 * inventory slots a window shows be true container slots, the basis for drag, shift-click and moving
 * items for real.
 *
 * <p>It holds the player's 36 inventory slots ({@link NetworkInteractorSlot}, indices 0-35 in vanilla
 * order: 27 main + 9 hotbar). Their on-screen position is rewritten every tick by the desktop screen to
 * the focused Network Interactor window's inventory zone, and they are gated by {@link #slotsActive()} so
 * they only render and accept input while that window is the front, non-minimized one. The vanilla
 * container then drives the cursor, drag, and shift-click for free.
 */
public class DesktopMenu extends AbstractContainerMenu {

    /** Count of player inventory slots: 27 main + 9 hotbar. */
    public static final int INVENTORY_SLOTS = 36;
    /** The vanilla inventory index for a menu slot: menu slots 0-26 are main (inv 9-35), 27-35 the hotbar (inv 0-8). */
    private static int inventoryIndexFor(final int menuSlot) {
        return menuSlot < 27 ? menuSlot + 9 : menuSlot - 27;
    }

    private final Inventory playerInventory;
    private final BlockPos monitorPos;
    private final BlockPos hostPos;
    private final ResourceLocation osId;
    private final String name;
    /** The host computer's RAM in megabytes, and what its system, desktop and services already hold of it. */
    private final int ramTotalMb;
    private final int ramReservedMb;

    /** Whether the 36 inventory slots are live this frame (true only while a Network Interactor window is focused). */
    private boolean slotsActive;
    /** The slot-zone origin/viewport the slots were last laid out at, so a relayout only happens when it moves. */
    private int laidOutX = Integer.MIN_VALUE;
    private int laidOutY = Integer.MIN_VALUE;
    private int laidOutVpTop = Integer.MIN_VALUE;
    private int laidOutVpBottom = Integer.MIN_VALUE;

    // The desktop environment the screen draws (the OS's bundled one, or the Linux package installed).
    private final ResourceLocation desktopId;

    public DesktopMenu(final int containerId, final Inventory playerInventory, final BlockPos monitorPos,
                       final BlockPos hostPos, final ResourceLocation osId, final String name,
                       final int ramTotalMb, final int ramReservedMb) {
        this(containerId, playerInventory, monitorPos, hostPos, osId, osId, name, ramTotalMb, ramReservedMb);
    }

    public DesktopMenu(final int containerId, final Inventory playerInventory, final BlockPos monitorPos,
                       final BlockPos hostPos, final ResourceLocation osId, final ResourceLocation desktopId,
                       final String name, final int ramTotalMb, final int ramReservedMb) {
        super(ComputingModule.DESKTOP_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.monitorPos = monitorPos;
        this.hostPos = hostPos;
        this.osId = osId;
        this.desktopId = desktopId == null ? osId : desktopId;
        this.name = name;
        this.ramTotalMb = ramTotalMb;
        this.ramReservedMb = ramReservedMb;
        /*
         * The player's 36 inventory slots in vanilla order: 27 main (indices 9-35) then 9 hotbar (0-8). The
         * x/y are placeholders, and the client recreates them with real positions when a window shows them
         * (Slot.x/y are final in 1.21.1, so following a moving window means rebuilding the slot at the new spot).
         */
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            addSlot(new NetworkInteractorSlot(playerInventory, inventoryIndexFor(i), 0, 0, this::slotsActive, true));
        }
    }

    /** Whether the inventory slots are currently live; the desktop screen sets this each tick. */
    public boolean slotsActive() {
        return slotsActive;
    }

    /** Lets the desktop screen turn the inventory slots on (front Network Interactor window) or off. */
    public void setSlotsActive(final boolean value) {
        if (!value) {
            /*
             * Invalidate the layout cache while the slots are inactive, so the next activation always rebuilds
             * the slot objects, even if a new window opens at exactly the same origin the last one used.
             */
            laidOutX = Integer.MIN_VALUE;
            laidOutY = Integer.MIN_VALUE;
            laidOutVpTop = Integer.MIN_VALUE;
            laidOutVpBottom = Integer.MIN_VALUE;
        }
        this.slotsActive = value;
    }

    /**
     * Client-only: lays the 36 inventory slots out with their top-left at {@code (originX, originY)} in screen
     * space, using the standard 9-wide, 4-row grid with an 18px pitch (3 main rows then the hotbar). Because
     * {@link net.minecraft.world.inventory.Slot}'s {@code x}/{@code y} are final, following a moving window means
     * rebuilding each slot at its new spot; this only runs when the origin or viewport actually changed, so a
     * still window (and any in-progress drag) keeps its slot instances. The vanilla index is preserved, so server
     * sync (which is purely by slot index) is unaffected.
     *
     * <p>{@code viewportTop}/{@code viewportBottom} are the screen-space bounds of the window's scrollable band:
     * a row whose 18px cell does not sit fully inside it is built {@code visible == false}, so a window shrunk
     * small enough to scroll the inventory does not render or hit-test rows past its edges.
     */
    public void layoutInventory(final int originX, final int originY, final int viewportTop,
                                final int viewportBottom) {
        if (originX == laidOutX && originY == laidOutY
                && viewportTop == laidOutVpTop && viewportBottom == laidOutVpBottom) {
            return;
        }
        laidOutX = originX;
        laidOutY = originY;
        laidOutVpTop = viewportTop;
        laidOutVpBottom = viewportBottom;
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            final int row = i < 27 ? i / 9 : 3;
            final int col = i < 27 ? i % 9 : i - 27;
            /*
             * Use the SAME row offset the app draws the slot backgrounds with (which adds the hotbar gap before
             * row 3), so the real slots line up with their backgrounds instead of diverging on the hotbar.
             */
            final int cellTop = originY
                    + dev.jstech.computers.gui.layout.NetworkInteractorLayout.rowYOffset(row);
            final boolean visible = cellTop >= viewportTop && cellTop + 18 <= viewportBottom;
            final NetworkInteractorSlot slot =
                    new NetworkInteractorSlot(playerInventory, inventoryIndexFor(i),
                            originX + col * 18, cellTop, this::slotsActive, visible);
            slot.index = i;
            slots.set(i, slot);
        }
    }

    public static DesktopMenu fromNetwork(final int containerId, final Inventory playerInventory,
                                          final RegistryFriendlyByteBuf buf) {
        final BlockPos monitor = buf.readBlockPos();
        final BlockPos host = buf.readBlockPos();
        final ResourceLocation os = buf.readResourceLocation();
        final ResourceLocation desktop = buf.readResourceLocation();
        final String machineName = buf.readUtf();
        final int ramTotalMb = buf.readVarInt();
        final int ramReservedMb = buf.readVarInt();
        return new DesktopMenu(containerId, playerInventory, monitor, host, os, desktop, machineName,
                ramTotalMb, ramReservedMb);
    }

    /** Writes the open buffer the client reconstructs from: the positions, the OS id, the name, and the RAM. */
    public static void writeOpenBuffer(final RegistryFriendlyByteBuf buf, final BlockPos monitorPos,
                                       final BlockPos hostPos, final ResourceLocation osId, final String name,
                                       final int ramTotalMb, final int ramReservedMb) {
        writeOpenBuffer(buf, monitorPos, hostPos, osId, osId, name, ramTotalMb, ramReservedMb);
    }

    /** The full open buffer: positions, OS id, desktop environment id, name, the RAM and what is already held. */
    public static void writeOpenBuffer(final RegistryFriendlyByteBuf buf, final BlockPos monitorPos,
                                       final BlockPos hostPos, final ResourceLocation osId,
                                       final ResourceLocation desktopId, final String name,
                                       final int ramTotalMb, final int ramReservedMb) {
        buf.writeBlockPos(monitorPos);
        buf.writeBlockPos(hostPos);
        buf.writeResourceLocation(osId);
        buf.writeResourceLocation(desktopId == null ? osId : desktopId);
        buf.writeUtf(name);
        buf.writeVarInt(ramTotalMb);
        buf.writeVarInt(ramReservedMb);
    }

    /** The desktop environment the screen draws (equals the OS id for the Frames editions). */
    public ResourceLocation desktopId() {
        return desktopId;
    }

    /** The host computer's RAM in megabytes. */
    public int ramTotalMb() {
        return ramTotalMb;
    }

    /** What the system, its desktop and its services held when the desktop opened, in megabytes. */
    public int ramReservedMb() {
        return ramReservedMb;
    }

    public BlockPos monitorPos() {
        return monitorPos;
    }

    public BlockPos hostPos() {
        return hostPos;
    }

    public ResourceLocation osId() {
        return osId;
    }

    public String machineName() {
        return name;
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        /*
         * Every slot belongs to the player's own inventory, so shift-click has no foreign container to push
         * to: returning empty leaves the stack where it is (vanilla still lets the cursor and drag reorganize
         * the inventory). Depositing a shift-clicked stack onto the network arrives in a later step.
         */
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(final Player player) {
        /*
         * Reach is to the monitor, and the monitor must still exist and link to this computer, so the
         * desktop closes the moment the monitor is broken or the peripheral link is severed.
         */
        if (!(player.level().getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitor)) {
            return false;
        }
        /*
         * The screen may be showing this machine because it is cabled to it, or because a Remote
         * Control session put it there.
         */
        return monitor.shows(hostPos)
                && player.distanceToSqr(monitorPos.getX() + 0.5, monitorPos.getY() + 0.5,
                monitorPos.getZ() + 0.5) <= 64.0
                // The desktop dies with its machine: powering off or pulling the system disk closes it.
                && CommandPromptMenu.sessionAlive(player.level(), hostPos);
    }
}
