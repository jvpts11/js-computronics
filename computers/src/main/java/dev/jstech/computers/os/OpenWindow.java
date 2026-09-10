/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * One program window a machine has open: which program, where it floats, whether it is minimized or
 * maximized, and what the program had open. This is the machine's own state, kept on the server, so a
 * computer that was left running comes back to the same windows for anyone who looks at its monitor,
 * and after the game itself was closed.
 *
 * <p>What a program had open is what it says it had, in a few lines of its own making: a studio names
 * its solution and the files on its tabs, an explorer its folder. It is what a person would expect to
 * find again on a machine that was left on; what a program had half typed and unsaved is not, and a
 * real machine does not hand that back after a restart either.
 *
 * @param key       the launcher key of the program
 * @param x         the floating left edge, in desktop pixels
 * @param y         the floating top edge
 * @param w         the floating width
 * @param h         the floating height
 * @param minimized whether the window sits on the panel only
 * @param maximized whether the window fills the work area (its floating bounds are kept underneath)
 * @param state     what the program had open, as the program wrote it, or empty
 */
public record OpenWindow(String key, int x, int y, int w, int h, boolean minimized, boolean maximized,
                         String state) {

    /** The most windows a machine remembers; more than this is not a desktop anyone left on purpose. */
    public static final int MAX = 32;

    /**
     * The most a program's state may take on the wire. A state is a few paths, and one that grew past
     * this is cut at a line, so what survives is still whole paths and not the front half of one.
     */
    public static final int STATE_MAX = 480;

    public OpenWindow {
        state = clipState(state);
    }

    /** A window with nothing of its own to remember. */
    public OpenWindow(final String key, final int x, final int y, final int w, final int h,
                      final boolean minimized, final boolean maximized) {
        this(key, x, y, w, h, minimized, maximized, "");
    }

    /** {@code state} cut to what the wire carries, at a line boundary; null reads as nothing. */
    public static String clipState(final String state) {
        if (state == null) {
            return "";
        }
        if (state.length() <= STATE_MAX) {
            return state;
        }
        final int cut = state.lastIndexOf('\n', STATE_MAX);
        return cut <= 0 ? "" : state.substring(0, cut);
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Key", key);
        tag.putInt("X", x);
        tag.putInt("Y", y);
        tag.putInt("W", w);
        tag.putInt("H", h);
        tag.putBoolean("Min", minimized);
        tag.putBoolean("Max", maximized);
        if (!state.isEmpty()) {
            tag.putString("State", state);
        }
        return tag;
    }

    public static OpenWindow load(final CompoundTag tag) {
        return new OpenWindow(tag.getString("Key"), tag.getInt("X"), tag.getInt("Y"),
                tag.getInt("W"), tag.getInt("H"), tag.getBoolean("Min"), tag.getBoolean("Max"),
                tag.getString("State"));
    }

    public static ListTag saveAll(final List<OpenWindow> windows) {
        final ListTag list = new ListTag();
        for (final OpenWindow window : windows) {
            list.add(window.save());
        }
        return list;
    }

    public static List<OpenWindow> loadAll(final ListTag list) {
        final List<OpenWindow> out = new ArrayList<>(list.size());
        for (final Tag entry : list) {
            if (entry instanceof CompoundTag tag && out.size() < MAX) {
                out.add(load(tag));
            }
        }
        return out;
    }
}
