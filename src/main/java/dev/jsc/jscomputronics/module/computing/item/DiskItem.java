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
import dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Map;

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

    private static final int MAX_CONTENT_ROWS = 12;

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final DiskSpec spec = spec();
        tooltip.add(Component.literal(
                spec.capacityItems() + " items  (" + spec.capacityMb() + " MB)")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(
                spec.tier() + "  -  " + spec.tier().latencyTicks() + "t latency  -  "
                        + spec.tier().speedMultiplier() + "x speed")
                .withStyle(ChatFormatting.DARK_GRAY));
        // Files on the disk's filesystem (e.g. .iql scripts, .craft recipes) — separate from the
        // item/fluid storage listed below.
        final dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents fs = stack.getOrDefault(
                ComputingModule.FILESYSTEM.get(),
                dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents.EMPTY);
        dev.jsc.jscomputronics.module.computing.os.fs.FilesystemTooltip.append(fs, tooltip);
        appendContents(stack, tooltip);
    }

    private static void appendContents(final ItemStack stack, final List<Component> tooltip) {
        final ServerStorageContents contents =
                stack.getOrDefault(ComputingModule.DISK_STORAGE.get(), ServerStorageContents.EMPTY);
        final long total = contents.total();
        if (total <= 0L) {
            return;
        }
        if (!net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltip.add(Component.literal(total + " items stored").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal("Hold Shift to list contents").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.literal("Contents (" + total + " items):").withStyle(ChatFormatting.AQUA));
        int shown = 0;
        for (final Map.Entry<StorageKey, Long> entry : contents.items().entrySet()) {
            if (shown >= MAX_CONTENT_ROWS) {
                tooltip.add(Component.literal("  ...and more").withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            // Name in gray, the stored quantity trailing in a dimmer grey (mB for fluids and chemicals).
            final String qty = entry.getKey().isItem() ? "x" + entry.getValue() : entry.getValue() + " mB";
            tooltip.add(Component.literal("  ")
                    .append(entry.getKey().displayName().copy().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("  " + qty).withStyle(ChatFormatting.DARK_GRAY)));
            shown++;
        }
    }
}
