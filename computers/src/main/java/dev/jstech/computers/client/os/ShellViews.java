/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.operation.payload.DesktopShellOutputPayload;
import java.util.ArrayList;
import java.util.List;

/**
 * Every window currently looking at the machine's console.
 *
 * <p>A computer has one console and the server answers it, not a window, so what it says goes to all of
 * them: the terminal window and the terminal panel inside an editor show the same session, which is
 * what having one console means. A view says here that it exists while it is open, and stops when it
 * closes, so nothing is drawn into a window that is gone.
 */
public final class ShellViews {

    private static final List<ShellView> OPEN = new ArrayList<>();

    private ShellViews() {
    }

    /** Says a view is open and wants what the console says. */
    static void register(final ShellView view) {
        if (!OPEN.contains(view)) {
            OPEN.add(view);
        }
    }

    /** Says a view is gone. */
    static void forget(final ShellView view) {
        OPEN.remove(view);
    }

    /** Whether any window is looking at the console. */
    public static boolean anyOpen() {
        return !OPEN.isEmpty();
    }

    /** Hands what the console said to every window looking at it. */
    public static void accept(final DesktopShellOutputPayload payload) {
        if (OPEN.isEmpty()) {
            return;
        }
        for (final ShellView view : List.copyOf(OPEN)) {
            view.accept(payload);
        }
        /*
         * Any command may have installed or removed a program (apt install, uninstall, ...), so the
         * desktop's launcher state is refreshed once for all of them rather than once per view. And any
         * command may have changed the disk (del, mkdir, a program writing a file), so the explorers ask
         * again too, once the command is done rather than on every line a running program prints.
         */
        if (!payload.busy()) {
            FilesApps.refreshAll();
        }
        DesktopScreen.refreshActive();
    }
}
