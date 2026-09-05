/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The per-computer settings owned by the Settings app and the MC-DOS {@code config} command: the
 * knobs that live on the computer itself (as opposed to the computer name and wallpaper, which stay
 * on {@link ComputerConsoleState}, or the network share, which lives on the disk).
 *
 * <p>This class is pure and carries no Minecraft dependency, so its validation and the
 * {@code config}-command text can be unit-tested without the game. Persistence is done by
 * {@link ComputerConsoleState} reading and writing these plain fields; a live medium (a GPU, monitor,
 * or disk) never appears here.
 *
 * <p>All setters clamp to the documented range rather than rejecting, matching the config policy: an
 * out-of-range value is pinned to the nearest valid one, never accepted blindly.
 */
public final class ComputerSettings {

    /** Accent colour as an ARGB int; {@code 0} means "use the OS skin's default accent". */
    private int accent;
    /** Whether the taskbar clock shows a 12-hour time; default is 24-hour. */
    private boolean clock12h;
    /** GUI scale 1..4, or {@code 0} for automatic. */
    private int guiScale;
    /** Screen brightness 0..100. */
    private int brightness = 100;
    /** The drive letter files save to by default. */
    private char defaultSaveDrive = 'C';
    /** Whether inserting removable media opens its folder automatically. */
    private boolean removableAutoOpen = true;
    /** The chosen theme preset id; {@code ""} means the system default. */
    private String themePreset = "";
    /** Whether the taskbar app strip is centered (Frames 11 look) rather than left-aligned; default centered. */
    private boolean taskbarCentered = true;
    /** Whether the desktop and its programs use the dark theme (Frames 11 only); default light. */
    private boolean darkMode;
    /** Default program id per lowercase file extension (e.g. {@code "txt" -> "jsc:editor"}). */
    private final Map<String, String> defaultApps = new LinkedHashMap<>();

    public int accent() {
        return accent;
    }

    public void setAccent(final int argb) {
        this.accent = argb;
    }

    public boolean clock12h() {
        return clock12h;
    }

    public void setClock12h(final boolean value) {
        this.clock12h = value;
    }

    public int guiScale() {
        return guiScale;
    }

    public void setGuiScale(final int scale) {
        this.guiScale = clamp(scale, 0, 4);
    }

    public int brightness() {
        return brightness;
    }

    public void setBrightness(final int value) {
        this.brightness = clamp(value, 0, 100);
    }

    public char defaultSaveDrive() {
        return defaultSaveDrive;
    }

    public void setDefaultSaveDrive(final char drive) {
        if (Character.isLetter(drive)) {
            this.defaultSaveDrive = Character.toUpperCase(drive);
        }
    }

    public boolean removableAutoOpen() {
        return removableAutoOpen;
    }

    public void setRemovableAutoOpen(final boolean value) {
        this.removableAutoOpen = value;
    }

    public String themePreset() {
        return themePreset;
    }

    public void setThemePreset(final String preset) {
        this.themePreset = preset == null ? "" : preset;
    }

    public boolean taskbarCentered() {
        return taskbarCentered;
    }

    public void setTaskbarCentered(final boolean value) {
        this.taskbarCentered = value;
    }

    public boolean darkMode() {
        return darkMode;
    }

    public void setDarkMode(final boolean value) {
        this.darkMode = value;
    }

    /** The default program id for {@code ext} (lowercased), or {@code ""} when none is set. */
    public String defaultApp(final String ext) {
        return defaultApps.getOrDefault(ext == null ? "" : ext.toLowerCase(Locale.ROOT), "");
    }

    public void setDefaultApp(final String ext, final String programId) {
        if (ext == null || ext.isBlank()) {
            return;
        }
        final String key = ext.toLowerCase(Locale.ROOT);
        if (programId == null || programId.isBlank()) {
            defaultApps.remove(key);
        } else {
            defaultApps.put(key, programId);
        }
    }

    /** An unmodifiable view of the default-app map for serialisation and display. */
    public Map<String, String> defaultApps() {
        return java.util.Collections.unmodifiableMap(defaultApps);
    }

    /** Replaces the default-app map (used on load). */
    public void putDefaultApps(final Map<String, String> map) {
        defaultApps.clear();
        if (map != null) {
            map.forEach(this::setDefaultApp);
        }
    }

    /**
     * Applies one {@code key=value} setting, clamping as needed. Returns {@code true} when the key is
     * one this class owns and the value parsed; {@code false} for an unknown key or an unparseable
     * value (so the caller can route the key elsewhere or report an error).
     *
     * <p>Keys handled here: {@code clock} ({@code 12h}/{@code 24h}), {@code theme}, {@code taskbar}
     * ({@code center}/{@code left}), {@code darkmode} ({@code on}/{@code off}), {@code guiscale},
     * {@code brightness}, {@code savedrive}, {@code autoopen} ({@code on}/{@code off}), {@code accent}
     * (six hex digits), and {@code defaultapp:<ext>}. The computer name, wallpaper, and network share are
     * owned elsewhere and are not handled here.
     *
     * @param key   the setting key (case-insensitive)
     * @param value the raw value
     * @return true if this class recognised and applied the key
     */
    public boolean applySetting(final String key, final String value) {
        if (key == null) {
            return false;
        }
        final String k = key.toLowerCase(Locale.ROOT).trim();
        final String v = value == null ? "" : value.trim();
        if (k.startsWith("defaultapp:")) {
            setDefaultApp(k.substring("defaultapp:".length()), v);
            return true;
        }
        switch (k) {
            case "clock" -> {
                if (v.equalsIgnoreCase("12h") || v.equals("12")) {
                    setClock12h(true);
                } else if (v.equalsIgnoreCase("24h") || v.equals("24")) {
                    setClock12h(false);
                } else {
                    return false;
                }
                return true;
            }
            case "theme" -> {
                setThemePreset(v.equalsIgnoreCase("system") ? "" : v);
                return true;
            }
            case "taskbar" -> {
                if (v.equalsIgnoreCase("center") || v.equalsIgnoreCase("centre") || v.equalsIgnoreCase("centered")) {
                    setTaskbarCentered(true);
                } else if (v.equalsIgnoreCase("left")) {
                    setTaskbarCentered(false);
                } else {
                    return false;
                }
                return true;
            }
            case "darkmode" -> {
                if (v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("dark")) {
                    setDarkMode(true);
                } else if (v.equalsIgnoreCase("off") || v.equalsIgnoreCase("false") || v.equalsIgnoreCase("light")) {
                    setDarkMode(false);
                } else {
                    return false;
                }
                return true;
            }
            case "guiscale" -> {
                final Integer n = parseInt(v);
                if (n == null) {
                    return false;
                }
                setGuiScale(n);
                return true;
            }
            case "brightness" -> {
                final Integer n = parseInt(v);
                if (n == null) {
                    return false;
                }
                setBrightness(n);
                return true;
            }
            case "savedrive" -> {
                if (v.isEmpty() || !Character.isLetter(v.charAt(0))) {
                    return false;
                }
                setDefaultSaveDrive(v.charAt(0));
                return true;
            }
            case "autoopen" -> {
                if (v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true")) {
                    setRemovableAutoOpen(true);
                } else if (v.equalsIgnoreCase("off") || v.equalsIgnoreCase("false")) {
                    setRemovableAutoOpen(false);
                } else {
                    return false;
                }
                return true;
            }
            case "accent" -> {
                final Integer argb = parseAccent(v);
                if (argb == null) {
                    return false;
                }
                setAccent(argb);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Human-readable {@code key   value} lines for the {@code config} command's listing. */
    public List<String> summaryLines() {
        final List<String> lines = new ArrayList<>();
        lines.add(pad("clock") + (clock12h ? "12h" : "24h"));
        lines.add(pad("theme") + (themePreset.isEmpty() ? "system" : themePreset));
        lines.add(pad("taskbar") + (taskbarCentered ? "center" : "left"));
        lines.add(pad("darkmode") + (darkMode ? "on" : "off"));
        lines.add(pad("guiscale") + (guiScale == 0 ? "auto" : Integer.toString(guiScale)));
        lines.add(pad("brightness") + brightness + "%");
        lines.add(pad("savedrive") + defaultSaveDrive + ":");
        lines.add(pad("autoopen") + (removableAutoOpen ? "on" : "off"));
        lines.add(pad("accent") + (accent == 0 ? "default" : String.format(Locale.ROOT, "#%06X", accent & 0xFFFFFF)));
        return lines;
    }

    private static String pad(final String key) {
        return String.format(Locale.ROOT, "  %-12s", key);
    }

    private static Integer parseInt(final String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /** Parses six hex digits (optionally {@code #}-prefixed) into an opaque ARGB int, or null. */
    private static Integer parseAccent(final String v) {
        String hex = v.startsWith("#") ? v.substring(1) : v;
        if (hex.length() != 6) {
            return null;
        }
        try {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static int clamp(final int value, final int lo, final int hi) {
        return Math.max(lo, Math.min(hi, value));
    }
}
