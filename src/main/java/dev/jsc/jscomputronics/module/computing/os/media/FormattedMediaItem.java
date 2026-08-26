/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.media;

import dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A {@link MediaItem} with a fixed physical {@link MediaFormat} (floppy, CD, DVD, USB). The format is
 * the item's identity (one item per format), not a component, so each medium has its own texture and
 * tooltip. The content it carries (OS installer / program installer / data snapshot) is still stored
 * in components and is orthogonal to the format.
 *
 * <p>{@code writable} distinguishes read-only pressed media (CD-ROM, DVD-ROM) from rewritable media
 * (floppy, CD-RW, DVD-RW, USB) for the future data-write flow.
 */
public class FormattedMediaItem extends MediaItem {

    private final MediaFormat format;
    private final boolean writable;

    public FormattedMediaItem(final Properties properties, final MediaFormat format, final boolean writable) {
        super(properties);
        this.format = format;
        this.writable = writable;
    }

    /** The fixed physical format of this medium. */
    public MediaFormat format() {
        return format;
    }

    /** Whether this medium can be written to (false for pressed ROM media). */
    public boolean writable() {
        return writable;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.literal(format.name() + " · " + (writable ? "read/write" : "read-only"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(format.capacityItems() + " item capacity")
                .withStyle(ChatFormatting.DARK_GRAY));

        // Files written on the medium (e.g. .craft recipes from the Pattern Encoder) take priority: a
        // medium carrying files reads as such, not as a blank installer. The installer/data lines only
        // show for a medium with no files of its own.
        final dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents fs = stack.getOrDefault(
                dev.jsc.jscomputronics.module.computing.ComputingModule.FILESYSTEM.get(),
                dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents.EMPTY);
        if (!fs.files().isEmpty()) {
            dev.jsc.jscomputronics.module.computing.os.fs.FilesystemTooltip.append(fs, tooltip);
            super.appendHoverText(stack, context, tooltip, flag);
            return;
        }

        final ResourceLocation payload = MediaItem.payload(stack);
        switch (MediaItem.kind(stack)) {
            case OS_INSTALL -> {
                if (payload != null) {
                    tooltip.add(Component.translatable("os.jsc." + payload.getPath())
                            .withStyle(ChatFormatting.AQUA));
                    tooltip.add(Component.literal("Operating system installer")
                            .withStyle(ChatFormatting.DARK_GRAY));
                    tooltip.addAll(dev.jsc.jscomputronics.module.computing.os.MinSpecTooltip.osMinSpec(payload));
                } else {
                    tooltip.add(Component.literal("blank · no files").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            case PROGRAM_INSTALL -> {
                if (payload != null) {
                    tooltip.add(Component.literal(payload.getPath()).withStyle(ChatFormatting.GOLD));
                    tooltip.add(Component.literal("Program installer").withStyle(ChatFormatting.DARK_GRAY));
                    tooltip.addAll(dev.jsc.jscomputronics.module.computing.os.MinSpecTooltip.programMinSpec(payload));
                } else {
                    tooltip.add(Component.literal("blank · no files").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            case DATA -> {
                final ServerStorageContents data = MediaItem.data(stack);
                if (data.total() > 0) {
                    tooltip.add(Component.literal("Data medium · " + data.total() + " stored")
                            .withStyle(ChatFormatting.GREEN));
                } else {
                    tooltip.add(Component.literal("blank · no files").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
