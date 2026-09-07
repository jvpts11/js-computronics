/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.fs;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the item-tooltip lines that describe the files stored on a medium or disk. A few files are
 * listed by name directly; a larger set collapses to a per-type breakdown ({@code "8 .craft · 4 .txt"})
 * with a Shift hint that expands the full list. Used by the removable-media and disk item tooltips so a
 * medium carrying recipe files reads as such instead of as a blank installer.
 *
 * <p>This is a client-side rendering helper (it reads {@link Screen#hasShiftDown()}); the data it
 * formats ({@link FilesystemContents} and {@link StoredFile}) stays pure.
 */
public final class FilesystemTooltip {

    /** Up to this many files are listed by name without holding Shift. */
    private static final int LIST_THRESHOLD = 4;

    /** Hard cap on listed file names so a huge filesystem can never blow up the tooltip. */
    private static final int MAX_LISTED = 16;

    private FilesystemTooltip() {
    }

    /**
     * Appends a description of {@code fs}'s files to {@code tooltip}. Does nothing when the filesystem
     * is empty, so the caller can fall back to its own "blank" text.
     */
    public static void append(final FilesystemContents fs, final List<Component> tooltip) {
        final Map<String, StoredFile> files = fs.files();
        if (files.isEmpty()) {
            return;
        }
        final int total = files.size();
        if (total <= LIST_THRESHOLD || Screen.hasShiftDown()) {
            tooltip.add(Component.literal(total == 1 ? "1 file:" : total + " files:")
                    .withStyle(ChatFormatting.AQUA));
            int shown = 0;
            for (final StoredFile file : files.values()) {
                if (shown >= MAX_LISTED) {
                    tooltip.add(Component.literal("  ...and " + (total - shown) + " more")
                            .withStyle(ChatFormatting.DARK_GRAY));
                    break;
                }
                tooltip.add(Component.literal("  " + baseName(file.path()))
                        .withStyle(ChatFormatting.GRAY));
                shown++;
            }
        } else {
            // Many files: collapse to a per-type breakdown and let Shift expand the full list.
            final Map<FileType, Integer> counts = new EnumMap<>(FileType.class);
            for (final StoredFile file : files.values()) {
                counts.merge(file.type(), 1, Integer::sum);
            }
            final StringBuilder breakdown = new StringBuilder();
            for (final Map.Entry<FileType, Integer> entry : counts.entrySet()) {
                if (breakdown.length() > 0) {
                    breakdown.append(" · ");
                }
                breakdown.append(entry.getValue()).append(" .").append(entry.getKey().extension());
            }
            tooltip.add(Component.literal(total + " files: " + breakdown)
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal("Hold Shift to list").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** The file name without its directory prefix. */
    private static String baseName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
