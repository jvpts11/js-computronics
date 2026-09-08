/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The editors that take a terminal instead of opening a window, by the word that starts them.
 *
 * <p>They are not desktop programs and have no window factory, so this is their equivalent: the verb a
 * player types, and how the editor behind it reads a keyboard. An addon adds its own the same way.
 */
public final class TtyEditors {

    /** A fresh reader of the keyboard per editor opened, since each keeps its own state. */
    private static final Map<String, Supplier<TtyEditor.IKeys>> FLAVOURS = new LinkedHashMap<>();

    static {
        register("vim", VimKeys::new);
        register("emacs", EmacsKeys::new);
    }

    private TtyEditors() {
    }

    /** Registers the editor a verb starts. Safe to call from any mod's client setup. */
    public static void register(final String verb, final Supplier<TtyEditor.IKeys> flavour) {
        FLAVOURS.put(verb.toLowerCase(Locale.ROOT), flavour);
    }

    /** How the editor that verb starts reads a keyboard, or null when no editor answers to it. */
    public static TtyEditor.IKeys flavourOf(final String verb) {
        final Supplier<TtyEditor.IKeys> flavour = FLAVOURS.get(verb.toLowerCase(Locale.ROOT));
        return flavour == null ? null : flavour.get();
    }
}
