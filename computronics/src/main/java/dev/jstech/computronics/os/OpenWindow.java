/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * One program window a machine has open: which program, where it floats, and whether it is
 * minimized or maximized. This is the machine's own state, kept on the server, so a computer that
 * was left running comes back to the same windows for anyone who looks at its monitor, and after
 * the game itself was closed. It deliberately holds the layout only: what each program had inside
 * (a terminal's scrollback, an unsaved query) is session convenience, not machine state, and a real
 * machine does not hand that back after a restart either.
 *
 * @param key       the launcher key of the program
 * @param x         the floating left edge, in desktop pixels
 * @param y         the floating top edge
 * @param w         the floating width
 * @param h         the floating height
 * @param minimized whether the window sits on the panel only
 * @param maximized whether the window fills the work area (its floating bounds are kept underneath)
 */
public record OpenWindow(String key, int x, int y, int w, int h, boolean minimized, boolean maximized) {

    /** The most windows a machine remembers; more than this is not a desktop anyone left on purpose. */
    public static final int MAX = 32;

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Key", key);
        tag.putInt("X", x);
        tag.putInt("Y", y);
        tag.putInt("W", w);
        tag.putInt("H", h);
        tag.putBoolean("Min", minimized);
        tag.putBoolean("Max", maximized);
        return tag;
    }

    public static OpenWindow load(final CompoundTag tag) {
        return new OpenWindow(tag.getString("Key"), tag.getInt("X"), tag.getInt("Y"),
                tag.getInt("W"), tag.getInt("H"), tag.getBoolean("Min"), tag.getBoolean("Max"));
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
