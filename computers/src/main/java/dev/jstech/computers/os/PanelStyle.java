/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os;

/**
 * The chrome family a desktop environment draws with: where its panel sits, what its launcher menu looks
 * like, and how its windows behave. The desktop screen renders one of these; an add-on desktop environment
 * picks the closest style and brings its own skin, names and icons.
 */
public enum PanelStyle {

    /** Bottom grey bevelled taskbar, classic side-band Start list (Frames 95). */
    FRAMES_95,

    /** Bottom Luna taskbar, two-column Start menu (Frames XP). */
    FRAMES_XP,

    /** Bottom dark centred taskbar, floating Start with search and a pinned grid (Frames 11). */
    FRAMES_11,

    /** Bottom dark panel with a Kickoff-style two-pane launcher (KDE Plasma). */
    KDE,

    /** Top bar with an Activities overview: search, workspaces, app grid and a dash (GNOME). */
    GNOME,

    /** Bottom panel with a favourites-rail / categories / apps menu (Cinnamon). */
    CINNAMON
}
