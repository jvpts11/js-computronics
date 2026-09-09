/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.operation.payload.CancelSetupPayload;
import dev.jstech.computers.operation.payload.SetupProgressPayload;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.ProgressBar;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The window a program's setup shows while the machine copies it.
 *
 * <p>It owns nothing: the job lives on the machine and this only draws what the machine last said,
 * which is why closing it changes nothing and why Cancel is a request rather than an act. It wears the
 * desktop it opens on, the way a setup did in each decade: a blue strip beside the text on Frames 95, a
 * white wizard page on XP, a flat card on Frames 11 and the Linux desktops.
 */
public final class SetupApp implements IDesktopApp {

    /** The window key, one per desktop: a machine sets one thing up at a time. */
    public static final String KEY = "Setup";

    private static final int STRIP_W = 42;
    private static final int HEADER_H = 24;
    private static final int BUTTON_W = 44;
    private static final int BUTTON_H = 12;
    private static final int BAR_H = 9;
    /** How long a finished setup keeps its window up before going on its own, in milliseconds. */
    private static final long LINGER_MS = 2500;

    private final BlockPos host;
    private final String os;
    private OsSkin skin = OsSkin.fallback();

    @Nullable
    private SetupProgressPayload state;
    private long overSince;

    private final Panel root = new Panel();
    private final Label headline;
    private final Label line;
    private final Label detail;
    private final ProgressBar bar;
    private final Label percent;
    private final Button cancel;
    private final Button close;

    public SetupApp(final BlockPos host, final String os) {
        this.host = host;
        this.os = os;
        this.headline = this.root.add(new Label(this::headlineText));
        this.line = this.root.add(new Label(this::lineText));
        this.detail = this.root.add(new Label(this::detailText, Label.Tone.DIM));
        this.bar = this.root.add(new ProgressBar(() -> this.state == null ? 0 : this.state.permille() / 10));
        this.percent = this.root.add(new Label(this::percentText, Label.Tone.DIM));
        this.cancel = this.root.add(new Button("Cancel", () ->
                PacketDistributor.sendToServer(new CancelSetupPayload(this.host))));
        this.close = this.root.add(new Button("Close", () -> DesktopScreen.requestClose(KEY)).setPrimary(true));
        this.close.setVisible(false);
    }

    /** Takes the machine's latest word on the job. */
    public void accept(final SetupProgressPayload payload) {
        final boolean wasOver = this.state != null && this.state.over();
        this.state = payload;
        if (payload.over() && !wasOver) {
            this.overSince = System.currentTimeMillis();
        }
        this.cancel.setVisible(!payload.over());
        this.close.setVisible(payload.over());
        this.bar.setVisible(!payload.over() || payload.state() == SetupProgressPayload.STATE_DONE);
        this.percent.setVisible(this.bar.visible());
    }

    /* What the labels say */

    private String verb() {
        return this.state != null && this.state.removing() ? "Removing" : "Installing";
    }

    private String headlineText() {
        if (this.state == null) {
            return "Setup";
        }
        return switch (this.state.state()) {
            case SetupProgressPayload.STATE_DONE -> this.state.name()
                    + (this.state.removing() ? " was removed." : " is installed.");
            case SetupProgressPayload.STATE_REFUSED -> "Setup cannot " + (this.state.removing() ? "remove " : "install ")
                    + this.state.name() + ".";
            case SetupProgressPayload.STATE_CANCELLED -> "Setup was cancelled.";
            default -> this.skin.form() == OsSkin.Form.LUNA ? verb() + " " + this.state.name() : this.state.name();
        };
    }

    private String lineText() {
        if (this.state == null) {
            return "";
        }
        return switch (this.state.state()) {
            case SetupProgressPayload.STATE_DONE -> this.state.removing()
                    ? "Its files are gone from the disk." : "You will find it in the Start menu.";
            case SetupProgressPayload.STATE_REFUSED -> this.state.message();
            case SetupProgressPayload.STATE_CANCELLED -> "Nothing was " + (this.state.removing() ? "removed." : "installed.");
            default -> this.state.removing()
                    ? "Removing " + this.state.name() + " from your computer."
                    : "Copying files to your computer.";
        };
    }

    private String detailText() {
        if (this.state == null || this.state.over()) {
            return "";
        }
        return this.state.phase() + "...";
    }

    private String percentText() {
        return this.state == null ? "" : (this.state.permille() / 10) + "%";
    }

    private String subtitle() {
        if (this.state == null) {
            return "";
        }
        final String size = this.state.sizeMb() > 0 ? this.state.sizeMb() + " MB" : "";
        final String from = this.state.source().isEmpty() ? "" : "from " + this.state.source();
        final StringBuilder out = new StringBuilder(this.state.house());
        for (final String part : new String[] {size, from}) {
            if (!part.isEmpty()) {
                out.append(out.isEmpty() ? "" : "  ").append(part);
            }
        }
        return out.toString();
    }

    /* IDesktopApp */

    @Override
    public String title() {
        return this.state == null ? "Setup" : this.state.name() + " Setup";
    }

    @Override
    public int defaultWidth() {
        return 198;
    }

    @Override
    public int defaultHeight() {
        return 96;
    }

    @Override
    public int minWidth() {
        return 160;
    }

    @Override
    public int minHeight() {
        return 84;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        if (this.state != null && this.state.state() == SetupProgressPayload.STATE_DONE
                && System.currentTimeMillis() - this.overSince > LINGER_MS) {
            DesktopScreen.requestClose(KEY);
        }
        this.skin.panel(g, x, y, width, height);
        final int textX;
        int rowY;
        final ResourceLocation icon = this.state == null ? null
                : ResourceLocation.fromNamespaceAndPath("jsc", this.state.programId().contains(":")
                        ? this.state.programId().substring(this.state.programId().indexOf(':') + 1)
                        : this.state.programId());
        switch (this.skin.form()) {
            case BEVEL, GNOME1 -> {
                // The blue strip a setup of the nineties kept beside its text, darker at the top.
                g.fillGradient(x, y, x + STRIP_W, y + height, 0xFF000080, 0xFF1084D0);
                if (icon != null) {
                    ProgramIcons.draw(g, x + (STRIP_W - 16) / 2, y + 8, 16, 16, icon, this.os);
                }
                textX = x + STRIP_W + 6;
                rowY = y + 6;
            }
            case LUNA -> {
                // The white header band of a wizard page, the icon at its left and the name beside it.
                g.fill(x, y, x + width, y + HEADER_H, 0xFFFFFFFF);
                g.fill(x, y + HEADER_H, x + width, y + HEADER_H + 1, 0xFFA9B4CC);
                if (icon != null) {
                    ProgramIcons.draw(g, x + 5, y + 4, 16, 16, icon, this.os);
                }
                textX = x + 6;
                rowY = y + HEADER_H + 6;
            }
            default -> {
                if (icon != null) {
                    ProgramIcons.draw(g, x + 6, y + 6, 16, 16, icon, this.os);
                }
                textX = x + 6;
                rowY = y + 26;
            }
        }
        final int right = x + width - 6;
        final int textW = right - textX;
        if (this.skin.form() == OsSkin.Form.LUNA) {
            // The header carries the headline and the publisher; the body starts below it.
            this.headline.setBounds(x + 26, y + 4, width - 32, 10);
            g.drawString(font, font.plainSubstrByWidth(subtitle(), width - 32), x + 26, y + 14, 0xFF4E5C78, false);
        } else if (this.skin.form() == OsSkin.Form.BEVEL || this.skin.form() == OsSkin.Form.GNOME1) {
            this.headline.setBounds(textX, rowY, textW, 10);
            rowY += 12;
        } else {
            this.headline.setBounds(x + 26, y + 6, width - 32, 10);
            g.drawString(font, font.plainSubstrByWidth(subtitle(), width - 32), x + 26, y + 15, this.skin.isDark()
                    ? 0xFF8891A2 : 0xFF4E5C78, false);
        }
        this.line.setBounds(textX, rowY, textW, 10);
        rowY += 11;
        this.detail.setBounds(textX, rowY, textW, 10);
        rowY += 12;
        // The bar stops short of the button's column, so the two never sit on top of each other.
        this.bar.setBounds(textX, rowY, textW - BUTTON_W - 6, BAR_H);
        rowY += BAR_H + 3;
        this.percent.setBounds(textX, rowY, 40, 10);
        final int buttonY = y + height - BUTTON_H - 5;
        this.cancel.setBounds(right - BUTTON_W, buttonY, BUTTON_W, BUTTON_H);
        this.close.setBounds(right - BUTTON_W, buttonY, BUTTON_W, BUTTON_H);
        this.root.render(g, new UiContext(this.skin, font, mouseX, mouseY, partialTick));
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        this.root.mouseClicked(mouseX, mouseY, button);
    }
}
