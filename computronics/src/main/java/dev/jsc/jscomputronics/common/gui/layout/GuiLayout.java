/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.gui.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * A pure, Minecraft-free model of a screen's layout, so the layout can be unit-tested. A screen builds
 * its element rectangles here and the validators catch the layout bugs that previously only surfaced
 * in-game: solid elements overlapping, anything spilling past the panel, and text running wider than the
 * space it sits in. It lives in {@code common} (not {@code client}) because both the Menu — which places
 * the real slots — and the Screen — which draws their frames — consume the same layout, so the slot
 * positions are validated too.
 *
 * <p>Elements come in two kinds, because not every overlap is a bug:
 * <ul>
 *   <li>{@link #box} — a <em>solid</em> element (slot, button, panel, icon). Two solids must never
 *       overlap, and a solid must stay inside the panel.</li>
 *   <li>{@link #text} — a line of text. Text is routinely drawn <em>on top of</em> a panel or centered
 *       in a button, so it is excluded from the overlap check; it is only required to stay inside the
 *       panel (a label that runs off the edge is the real bug).</li>
 * </ul>
 *
 * <p>Font metrics are approximated: a vanilla glyph is about {@value #GLYPH_WIDTH} px wide and a line
 * about {@value #LINE_HEIGHT} px tall at scale 1.0. The exact rendered width of a string still needs the
 * real font in-client, but the estimate reliably catches gross overflow. The font SIZE enters the check
 * through the {@code scale} on each text line.
 */
public final class GuiLayout {

    /** Approximate width, in pixels, of one vanilla glyph at font scale 1.0. */
    public static final float GLYPH_WIDTH = 6.0f;

    /** Approximate height, in pixels, of one vanilla text line at font scale 1.0. */
    public static final float LINE_HEIGHT = 9.0f;

    /**
     * A named rectangle in the screen's local space (origin at the panel's top-left corner).
     * {@code solid} elements take part in the overlap check; text lines ({@code solid == false}) do not.
     */
    public record Box(String name, int x, int y, int width, int height, boolean solid) {
        public Box {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("layout element '" + name + "' has negative size");
            }
        }

        boolean intersects(final Box other) {
            // Edge-to-edge (one ends exactly where the next begins) is NOT an overlap.
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }

        boolean within(final int panelWidth, final int panelHeight) {
            return x >= 0 && y >= 0 && x + width <= panelWidth && y + height <= panelHeight;
        }
    }

    private final int panelWidth;
    private final int panelHeight;
    private final List<Box> boxes = new ArrayList<>();

    public GuiLayout(final int panelWidth, final int panelHeight) {
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
    }

    /** Records a solid element (a widget, slot, button, panel or icon). Returns {@code this} for chaining. */
    public GuiLayout box(final String name, final int x, final int y, final int width, final int height) {
        boxes.add(new Box(name, x, y, width, height, true));
        return this;
    }

    /**
     * Records one line of text as a non-solid box whose width is the estimated rendered width
     * ({@code chars * GLYPH_WIDTH * scale}) and whose height is one scaled line. Text is excluded from
     * {@link #overlaps()} (it is meant to sit on panels/buttons) but is included in {@link #outOfBounds()}
     * so a label that runs off the panel is caught. This is how font size enters the check: a label drawn
     * at a larger scale takes proportionally more width and is flagged if it then overflows the panel.
     */
    public GuiLayout text(final String name, final int x, final int y, final int chars, final float scale) {
        final int w = Math.round(chars * GLYPH_WIDTH * scale);
        final int h = Math.round(LINE_HEIGHT * scale);
        boxes.add(new Box(name, x, y, w, h, false));
        return this;
    }

    /**
     * Names of every pair of <em>solid</em> elements that overlap, formatted "a x b"; empty when no two
     * solids collide. Text lines are not considered here.
     */
    public List<String> overlaps() {
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                final Box a = boxes.get(i);
                final Box b = boxes.get(j);
                if (a.solid() && b.solid() && a.intersects(b)) {
                    out.add(a.name() + " x " + b.name());
                }
            }
        }
        return out;
    }

    /**
     * Adds the standard player inventory — a 3×9 grid with its top-left at {@code (x, invY)} plus the
     * 9-slot hotbar 58px below the grid top — on the vanilla 18px slot pitch. A convenience for the many
     * screens that carry the player inventory, so a layout need not re-list 36 slots by hand.
     */
    public GuiLayout playerInventory(final int x, final int invY) {
        final int pitch = 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                box("inv_" + row + "_" + col, x + col * pitch, invY + row * pitch, pitch, pitch);
            }
        }
        for (int col = 0; col < 9; col++) {
            box("hotbar_" + col, x + col * pitch, invY + 58, pitch, pitch);
        }
        return this;
    }

    /** Names of every element (solid or text) that spills outside the panel; empty when everything fits. */
    public List<String> outOfBounds() {
        final List<String> out = new ArrayList<>();
        for (final Box b : boxes) {
            if (!b.within(panelWidth, panelHeight)) {
                out.add(b.name());
            }
        }
        return out;
    }

    /**
     * Names of every <em>solid</em> element with zero width or height — a slot, button, or panel that draws
     * nothing occupies no space and almost always signals a layout computed from a wrong (often negative,
     * then clamped to zero) dimension. Text lines are exempt: an empty caption legitimately has zero width.
     */
    public List<String> zeroSizedSolids() {
        final List<String> out = new ArrayList<>();
        for (final Box b : boxes) {
            if (b.solid() && (b.width() == 0 || b.height() == 0)) {
                out.add(b.name());
            }
        }
        return out;
    }

    /** True only when no two solids overlap and nothing (solid or text) spills past the panel. */
    public boolean isClean() {
        return overlaps().isEmpty() && outOfBounds().isEmpty();
    }

    /** The recorded elements, in insertion order. */
    public List<Box> elements() {
        return List.copyOf(boxes);
    }

    /** The panel width this layout was built against. */
    public int width() {
        return panelWidth;
    }

    /** The panel height this layout was built against. */
    public int height() {
        return panelHeight;
    }
}
