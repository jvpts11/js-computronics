/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An address as a trail of crumbs in a field: each crumb but the last is a click target that navigates to
 * what it names, and a trail wider than the field ends in "..".
 */
public final class Breadcrumbs extends UiComponent {

    /** One crumb: what it shows and where a click on it goes. */
    public record Crumb(String label, String target) {
    }

    private static final String SEPARATOR = " > ";
    private static final int INSET = 3;

    private final Supplier<List<Crumb>> crumbs;
    private final Consumer<String> onNavigate;
    @Nullable
    private Font lastFont;

    public Breadcrumbs(final Supplier<List<Crumb>> crumbs, final Consumer<String> onNavigate) {
        this.crumbs = crumbs;
        this.onNavigate = onNavigate;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        lastFont = ctx.font();
        ctx.skin().field(g, x(), y(), width(), height(), false);
        final List<Crumb> trail = crumbs.get();
        final int cy = y() + (height() - 7) / 2;
        final int maxX = x() + width() - INSET;
        int px = x() + INSET;
        for (int i = 0; i < trail.size(); i++) {
            final String label = trail.get(i).label();
            final int w = ctx.font().width(label);
            if (px + w > maxX) {
                // As much of the crumb as fits, so a long volume name still reads, then the dots.
                final int room = maxX - px - ctx.font().width("..");
                final String head = room > 0 ? ctx.font().plainSubstrByWidth(label, room) : "";
                g.drawString(ctx.font(), head + "..", px, cy, ctx.skin().dim(), false);
                break;
            }
            final boolean last = i == trail.size() - 1;
            final boolean hover = !last && ctx.over(px - 1, y() + 1, w + 2, height() - 2);
            g.drawString(ctx.font(), label, px, cy, hover ? ctx.skin().accent() : (last ? ctx.skin().text() : ctx.skin().dim()), false);
            px += w;
            if (!last) {
                g.drawString(ctx.font(), SEPARATOR, px, cy, ctx.skin().dim(), false);
                px += ctx.font().width(SEPARATOR);
            }
        }
    }

    /** The target of the crumb under {@code mx}, or null when the point is not on a crumb that navigates. */
    @Nullable
    public String crumbAt(final double mx) {
        if (lastFont == null) {
            return null;
        }
        final List<Crumb> trail = crumbs.get();
        int px = x() + INSET;
        for (int i = 0; i < trail.size() - 1; i++) {
            final int w = lastFont.width(trail.get(i).label());
            if (mx >= px && mx < px + w) {
                return trail.get(i).target();
            }
            px += w + lastFont.width(SEPARATOR);
        }
        return null;
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        final String target = crumbAt(mx);
        if (target != null) {
            onNavigate.accept(target);
        }
        return true;
    }
}
