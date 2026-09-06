/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program;

/**
 * Key routing for a full-screen computer program (the Network Management Studio, and future programs). Pure logic —
 * GLFW key codes are kept as literals so this carries no Minecraft/LWJGL types and is unit-tested directly.
 *
 * <p>The rule exists because of a real bug: a program runs inside a container screen, and the vanilla screen closes
 * itself when it sees the player's <em>inventory key</em> (which defaults to {@code E}). A program that types text
 * must therefore consume EVERY key itself and let only {@code Escape} close it — never fall through to the vanilla
 * handler. {@link #route} encodes exactly that: {@code Escape} closes, {@code F5} runs, everything else is the
 * program's own input.
 */
public final class ProgramKeybinds {

    /** GLFW key codes (literals on purpose, to keep this class free of Minecraft/LWJGL for unit testing). */
    public static final int ESCAPE = 256;
    public static final int ENTER = 257;
    public static final int F5 = 294;

    private ProgramKeybinds() {
    }

    /** What a full-screen program should do with a pressed key. */
    public enum Action {
        /** Leave the program. The ONLY action that closes a program. */
        CLOSE,
        /** Run the current statement. */
        RUN,
        /** The program's own input (typing, the editor's caret keys, the inventory key, ...) — never closes. */
        EDIT
    }

    /**
     * Routes a key for a full-screen program. Only {@link #ESCAPE} closes it; {@link #F5} runs; every other key —
     * including the default inventory key {@code E} — is editing input the program consumes itself.
     */
    public static Action route(final int key) {
        if (key == ESCAPE) {
            return Action.CLOSE;
        }
        if (key == F5) {
            return Action.RUN;
        }
        return Action.EDIT;
    }
}
