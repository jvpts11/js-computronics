/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A line of text in one of the skin's tones, clipped to its width. The text is a supplier, so a label that
 * shows a live value never has to be told it changed.
 */
public final class Label extends UiComponent {

    /** Which of the skin's text colours the label takes. */
    public enum Tone { TEXT, DIM, ACCENT }

    public enum Align { LEFT, CENTER, RIGHT }

    private Supplier<String> text;
    private Supplier<Tone> tone;
    private Align align = Align.LEFT;
    @Nullable
    private IntSupplier color;
    private float scale = 1f;

    public Label(final String text) {
        this(() -> text, Tone.TEXT);
    }

    public Label(final String text, final Tone tone) {
        this(() -> text, tone);
    }

    public Label(final Supplier<String> text) {
        this(text, Tone.TEXT);
    }

    public Label(final Supplier<String> text, final Tone tone) {
        this.text = text;
        this.tone = () -> tone;
    }

    public Label setText(final String value) {
        text = () -> value;
        return this;
    }

    public Label setText(final Supplier<String> value) {
        text = value;
        return this;
    }

    public String text() {
        return text.get();
    }

    public Label setTone(final Tone value) {
        tone = () -> value;
        return this;
    }

    /** A tone decided each frame, for a status line that turns to a warning. */
    public Label setTone(final Supplier<Tone> value) {
        tone = value;
        return this;
    }

    /** A colour outside the skin's tones (an error red) that wins over the tone; {@code 0} means the tone. */
    public Label setColor(final int argb) {
        color = () -> argb;
        return this;
    }

    /** A colour read each frame; {@code 0} means the tone. */
    public Label setColor(final IntSupplier argb) {
        color = argb;
        return this;
    }

    public Label setAlign(final Align value) {
        align = value;
        return this;
    }

    /** Draws the text smaller than the font, for a dense panel; {@code 1} is the font's own size. */
    public Label setScale(final float value) {
        scale = value;
        return this;
    }

    /** The colour the label draws in right now. */
    public int color(final UiContext ctx) {
        if (color != null) {
            final int fixed = color.getAsInt();
            if (fixed != 0) {
                return fixed;
            }
        }
        return switch (tone.get()) {
            case DIM -> ctx.skin().dim();
            case ACCENT -> ctx.skin().accent();
            default -> ctx.skin().text();
        };
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        final String shown = Texts.clip(ctx.font(), text.get(), (int) (width() / scale));
        final int tw = Math.round(ctx.font().width(shown) * scale);
        final int tx = switch (align) {
            case CENTER -> x() + (width() - tw) / 2;
            case RIGHT -> x() + width() - tw;
            default -> x();
        };
        // A label the height of a line sits on its y; a taller one centres its text vertically.
        final int lineHeight = Math.round(ctx.font().lineHeight * scale);
        final int ty = height() <= lineHeight ? y() : y() + (height() - lineHeight + 2) / 2;
        if (scale == 1f) {
            g.drawString(ctx.font(), shown, tx, ty, color(ctx), false);
        } else {
            Texts.scaled(g, ctx.font(), shown, tx, ty, scale, color(ctx));
        }
    }
}
