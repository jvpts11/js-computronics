/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.common.hardware.DiskSpec;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.storage.DiskUsage;
import dev.jsc.jscomputronics.module.computing.storage.DriveVolumes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A storage-disk component item.
 */
public class DiskItem extends SpecItem<DiskSpec> {

    public DiskItem(final Properties properties, final DiskSpec spec) {
        super(properties, spec);
    }

    // A fresh disk exposes nothing to the network until the owner publishes part of it; this keeps a
    // newly placed computer's storage private by default. Tunable.
    public static final int DEFAULT_PUBLIC_PERMILLE = 0;

    /**
     * The public-share permille stored on a disk stack, or the private default if the component is absent or the stack is not a disk.
     */
    public static int publicPermille(final ItemStack stack) {
        if (!(stack.getItem() instanceof DiskItem)) {
            return DEFAULT_PUBLIC_PERMILLE;
        }
        final Integer stored = stack.get(ComputingModule.DISK_PUBLIC_PERMILLE.get());
        return stored == null ? DEFAULT_PUBLIC_PERMILLE
                : dev.jsc.jscomputronics.module.computing.storage.DiskPrivacy.clampPermille(stored);
    }

    /** Writes a clamped public-share permille onto a disk stack (a no-op for a non-disk stack). */
    public static void setPublicPermille(final ItemStack stack, final int permille) {
        if (stack.getItem() instanceof DiskItem) {
            stack.set(ComputingModule.DISK_PUBLIC_PERMILLE.get(),
                    dev.jsc.jscomputronics.module.computing.storage.DiskPrivacy.clampPermille(permille));
        }
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final DiskSpec spec = spec();
        // The nameplate, and the one budget in both units its data comes in: what an item costs on the
        // drive follows from the word size of the era it was made for.
        tooltip.add(Component.literal(DiskSpec.sizeLabel(spec.capacityMb()) + " drive  -  " + spec.era().bits()
                + "-bit: " + spec.era().mbPerItem() + " MB per item").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(DiskUsage.capacityLine(spec.capacityItems())).withStyle(ChatFormatting.GRAY));
        HardwareTooltip.appendEra(tooltip, spec.era());
        tooltip.add(Component.literal(
                spec.tier() + "  -  " + spec.tier().latencyTicks() + "t latency  -  "
                        + spec.tier().speedMultiplier() + "x speed")
                .withStyle(ChatFormatting.DARK_GRAY));
        appendSystem(stack, tooltip);
        // Files on the disk's filesystem (e.g. .iql scripts, .craft recipes) — separate from the
        // item/fluid storage listed below.
        final dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents fs = stack.getOrDefault(
                ComputingModule.FILESYSTEM.get(),
                dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents.EMPTY);
        dev.jsc.jscomputronics.module.computing.os.fs.FilesystemTooltip.append(fs, tooltip);
        appendContents(stack, tooltip, spec.capacityItems());
    }

    /**
     * Names the system installed on this drive, and the desktop and program count it carries. Without
     * it a drive in the hand is anonymous, and pulling one out of a machine is a guess — which is how a
     * player wipes a system they meant to keep.
     */
    private static void appendSystem(final ItemStack stack, final List<Component> tooltip) {
        final net.minecraft.resources.ResourceLocation osId =
                stack.get(ComputingModule.SYSTEM_OS.get());
        if (osId == null) {
            return; // a blank drive says nothing, which is itself the answer
        }
        final dev.jsc.jscomputronics.module.computing.os.OsDef os =
                dev.jsc.jscomputronics.module.computing.os.OsRegistry.getOs(osId);
        tooltip.add(Component.literal("System: " + (os != null ? os.displayName() : osId.getPath()))
                .withStyle(ChatFormatting.AQUA));

        final net.minecraft.nbt.CompoundTag software = stack.get(ComputingModule.DISK_CONSOLE.get());
        if (software == null) {
            return;
        }
        final dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState state =
                new dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState();
        state.load(software);
        final net.minecraft.resources.ResourceLocation desktop =
                dev.jsc.jscomputronics.module.computing.os.OsDisks.installedDesktopId(os, state);
        if (desktop != null) {
            final var def = dev.jsc.jscomputronics.module.computing.os.OsRegistry.getDesktop(desktop);
            tooltip.add(Component.literal("Desktop: "
                            + (def != null ? def.displayName() : desktop.getPath()))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        final int programs = state.installed().size();
        if (programs > 0) {
            tooltip.add(Component.literal(programs + " program" + (programs == 1 ? "" : "s") + " installed")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /**
     * What is on the drive, from the usage summary it carries — items by the piece, fluids and chemicals by
     * the millibucket, and how much of the drive that takes: the contents themselves stay in the volume
     * store and are browsed on a machine, never listed from the hand.
     */
    private static void appendContents(final ItemStack stack, final List<Component> tooltip,
                                       final long capacityItems) {
        final DiskUsage usage = DriveVolumes.usage(stack);
        if (usage.isEmpty()) {
            return;
        }
        tooltip.add(Component.literal(usage.summary(capacityItems)).withStyle(ChatFormatting.AQUA));
    }
}
