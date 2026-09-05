/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a program's icon from its texture. Each program ships a real 16x16 pixel-art sprite per Frames edition
 * at {@code textures/gui/program/<program>/<os>.png} — genuinely different artwork per era, not a recolour:
 * Frames 95 is chunky outlined pixel art, Frames XP is glossy Luna pixel art, Frames 11 is flat two-tone pixel
 * art. The blit is keyed by the program id, so an add-on that ships a sprite at this convention path is drawn
 * with no code change here.
 *
 * <p>Every sprite is drawn at its native 16 pixels, 1:1, centered in the requested box — never resampled.
 * The only scaling is the game's own uniform GUI scale, which keeps the pixels crisp exactly like item icons.
 * An unknown or null id falls back to the {@code generic} sprite so a launcher never shows a missing texture.
 */
public final class ProgramIcons {

    private static final String NS = "jsc";
    /** Native sprite size; sprites are authored at this size and drawn 1:1. */
    public static final int SIZE = 16;

    private ProgramIcons() {
    }

    // Whether a sprite file actually exists, cached per path: a program without artwork (or an add-on
    // without a variant for some desktop) must degrade to the generic sprite, never to a missing texture.
    private static final java.util.Map<ResourceLocation, Boolean> PRESENT = new java.util.HashMap<>();

    private static boolean exists(final ResourceLocation tex) {
        return PRESENT.computeIfAbsent(tex, t ->
                net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(t).isPresent());
    }

    /** Marks an icon set drawn for a desktop as it looked on Legacy-era hardware. */
    public static final String PERIOD_SUFFIX = "_legacy";

    private static ResourceLocation sprite(final String program, final String skin) {
        return ResourceLocation.fromNamespaceAndPath(NS, "textures/gui/program/" + program + "/" + skin + ".png");
    }

    /**
     * Draws {@code programId}'s icon in the artwork style of {@code os} (a Frames edition or a Linux desktop
     * environment id), centered in the box. Falls back along program/skin -> program/frames_95 ->
     * generic/skin -> generic/frames_95, so nothing ever renders the missing-texture checker.
     */
    public static void draw(final GuiGraphics g, final int x, final int y, final int w, final int h,
                            final ResourceLocation programId, final String os) {
        final String program = programId == null ? "generic" : programId.getPath();
        final String skin = os == null || os.isEmpty() ? "frames_95" : os;
        ResourceLocation tex = sprite(program, skin);
        // A period icon set falls back to that desktop's modern set before anything else: a Legacy KDE
        // without its own artwork should still look like KDE, not like Frames 95.
        if (!exists(tex) && skin.endsWith(PERIOD_SUFFIX)) {
            tex = sprite(program, skin.substring(0, skin.length() - PERIOD_SUFFIX.length()));
        }
        if (!exists(tex)) {
            tex = sprite(program, "frames_95");
        }
        if (!exists(tex)) {
            tex = sprite("generic", skin);
        }
        if (!exists(tex)) {
            tex = sprite("generic", "frames_95");
        }
        // Fit the sprite to the box, keeping it square. Most call sites hand over a box shorter or
        // narrower than the 16px sprite (a taskbar button, a menu row), and drawing at native size there
        // spilled the icon out of its box and over the text and borders around it.
        final int side = Math.min(SIZE, Math.min(w, h));
        final int ox = x + (w - side) / 2;
        final int oy = y + (h - side) / 2;
        if (side == SIZE) {
            g.blit(tex, ox, oy, 0.0F, 0.0F, SIZE, SIZE, SIZE, SIZE);
        } else {
            g.blit(tex, ox, oy, side, side, 0.0F, 0.0F, SIZE, SIZE, SIZE, SIZE);
        }
    }
}
