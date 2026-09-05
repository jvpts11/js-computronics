/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jstech.core.peripheral.PeripheralCableType;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.os.OpenWindow;
import dev.jsc.jscomputronics.module.computing.os.OsDef;
import dev.jsc.jscomputronics.module.computing.os.OsHost;
import dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * One machine in a rack, addressed as a host of its own. A rack answers machine questions for the
 * unit its monitor's channel is showing; this view pins every such call to {@code row} instead, so a
 * caller that must reach a specific node — the Supercomputer Console installing a system on all of
 * them — does not have to touch the KVM. Stateless: it holds nothing but the rack and the row, and
 * every call runs through the rack's own code path.
 */
public record RackUnitHost(ServerRackBlockEntity rack, int row) implements OsHost {

    @Override
    public boolean isRunning() {
        return rack.asUnit(row, rack::isRunning);
    }

    @Override
    public void setPowered(final boolean on) {
        rack.asUnit(row, () -> {
            rack.setPowered(on);
            return null;
        });
    }

    @Override
    public boolean needsPost() {
        return rack.asUnit(row, rack::needsPost);
    }

    @Override
    public void setNeedsPost(final boolean value) {
        rack.asUnit(row, () -> {
            rack.setNeedsPost(value);
            return null;
        });
    }

    @Override
    public int pendingInstallSlot() {
        return rack.asUnit(row, rack::pendingInstallSlot);
    }

    @Override
    public void setPendingInstallSlot(final int slot) {
        rack.asUnit(row, () -> {
            rack.setPendingInstallSlot(slot);
            return null;
        });
    }

    @Override
    @Nullable
    public ResourceLocation bootedDesktopId() {
        return rack.asUnit(row, rack::bootedDesktopId);
    }

    @Override
    public void setBootedDesktopId(@Nullable final ResourceLocation id) {
        rack.asUnit(row, () -> {
            rack.setBootedDesktopId(id);
            return null;
        });
    }

    @Override
    public List<OpenWindow> openWindows() {
        return rack.asUnit(row, rack::openWindows);
    }

    @Override
    public void setOpenWindows(final List<OpenWindow> windows) {
        rack.asUnit(row, () -> {
            rack.setOpenWindows(windows);
            return null;
        });
    }

    @Override
    public long ramBuffer() {
        return rack.asUnit(row, rack::ramBuffer);
    }

    @Override
    public int maxCpuMhz() {
        return rack.asUnit(row, rack::maxCpuMhz);
    }

    @Override
    public int totalVramMb() {
        return rack.asUnit(row, rack::totalVramMb);
    }

    @Override
    public long systemDiskFreeMb() {
        return rack.asUnit(row, rack::systemDiskFreeMb);
    }

    @Override
    public int diskSlots() {
        return rack.asUnit(row, rack::diskSlots);
    }

    @Override
    public ItemStack diskInSlot(final int slot) {
        return rack.asUnit(row, () -> rack.diskInSlot(slot));
    }

    @Override
    public ItemStack systemDisk() {
        return rack.asUnit(row, rack::systemDisk);
    }

    @Override
    public int bootDiskSlot() {
        return rack.asUnit(row, rack::bootDiskSlot);
    }

    @Override
    public void setBootDiskSlot(final int slot) {
        rack.asUnit(row, () -> {
            rack.setBootDiskSlot(slot);
            return null;
        });
    }

    @Override
    public int defaultInstallSlot() {
        return rack.asUnit(row, rack::defaultInstallSlot);
    }

    @Override
    public boolean formatDisk(final int slot) {
        return rack.asUnit(row, () -> rack.formatDisk(slot));
    }

    @Override
    public boolean installOs(final ResourceLocation osId, final int preferredSlot) {
        return rack.asUnit(row, () -> rack.installOs(osId, preferredSlot));
    }

    @Override
    public boolean installOs(final ResourceLocation osId) {
        return rack.asUnit(row, () -> rack.installOs(osId));
    }

    @Override
    public boolean hasOs() {
        return rack.asUnit(row, rack::hasOs);
    }

    @Override
    @Nullable
    public ResourceLocation installedOsId() {
        return rack.asUnit(row, rack::installedOsId);
    }

    @Override
    @Nullable
    public OsDef installedOs() {
        return rack.asUnit(row, rack::installedOs);
    }

    @Override
    @Nullable
    public ResourceLocation installedDesktopId() {
        return rack.asUnit(row, rack::installedDesktopId);
    }

    @Override
    public boolean validateOsSession() {
        return rack.asUnit(row, rack::validateOsSession);
    }

    @Override
    public ComputerConsoleState console() {
        return rack.asUnit(row, rack::console);
    }

    @Override
    public NodeUuid nodeUuid() {
        return rack.asUnit(row, rack::nodeUuid);
    }

    @Override
    @Nullable
    public NetworkUuid networkUuid() {
        return rack.asUnit(row, rack::networkUuid);
    }

    @Override
    public String customName() {
        return rack.asUnit(row, rack::customName);
    }

    @Override
    public void setCustomName(final String name) {
        rack.asUnit(row, () -> {
            rack.setCustomName(name);
            return null;
        });
    }

    @Override
    public int installedCpus() {
        return rack.asUnit(row, rack::installedCpus);
    }

    @Override
    public List<ItemStack> diskStacks() {
        return rack.asUnit(row, rack::diskStacks);
    }

    @Override
    public long systemDiskFreeWeight() {
        return rack.asUnit(row, rack::systemDiskFreeWeight);
    }

    @Override
    public long reservedByOs() {
        return rack.asUnit(row, rack::reservedByOs);
    }

    @Override
    public void setChanged() {
        rack.setChanged();
    }

    @Override
    @Nullable
    public HardwareEra installedEra() {
        return rack.asUnit(row, rack::installedEra);
    }

    @Override
    @Nullable
    public HardwareEra displayEra() {
        return rack.asUnit(row, rack::displayEra);
    }

    @Override
    @Nullable
    public dev.jsc.jscomputronics.module.computing.crafting.PatternWorkbench studio() {
        return rack.asUnit(row, rack::studio);
    }

    // Peripheral links belong to the rack as a whole: every unit shares the cabinet's readers and monitors.

    @Override
    public PeripheralCableType cableType() {
        return rack.cableType();
    }

    @Override
    public List<Long> linkedEndpoints() {
        return rack.linkedEndpoints();
    }

    @Override
    public int maxEndpoints() {
        return rack.asUnit(row, rack::maxEndpoints);
    }

    @Override
    public void onEndpointLinked(final long endpointPos) {
        rack.onEndpointLinked(endpointPos);
    }

    @Override
    public void onEndpointUnlinked(final long endpointPos) {
        rack.onEndpointUnlinked(endpointPos);
    }

    @Override
    public Set<Long> occupiedPositions(final long ownerPos) {
        return rack.occupiedPositions(ownerPos);
    }
}
