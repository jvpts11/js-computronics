/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client;

import dev.jstech.core.client.gui.theme.EraTheme;
import dev.jstech.core.client.gui.theme.EraThemes;
import dev.jstech.core.client.gui.theme.JsTechTheme;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/**
 * Base for the computing container screens, holding the two hand-rolled helpers every one of them repeated: a hit-test against a rectangle in this screen's local space, and a menu-button send to the server. It also resolves the screen's per-era skin and binds it for the render pass, so every computing screen paints in the host computer's hardware-era theme. A screen with no host era (a topology element, a board-less assembly, a program with no era source) falls back to the frozen STANDARD skin, which renders exactly as before.
 */
public abstract class AbstractComputerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    /** The skin this screen paints with for the current render pass; STANDARD until {@link #init} resolves it. */
    protected EraTheme theme = EraThemes.STANDARD;

    protected AbstractComputerScreen(final T menu, final Inventory playerInventory, final Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * The hardware era whose skin this screen should wear, or {@code null} to use the STANDARD default. The base
     * returns {@code null}; a screen running on a host computer overrides this to report its host's era so the GUI
     * adopts that era's skin. Resolved fresh every {@link #containerTick}, so inserting or removing a board repaints
     * the GUI in the new era live.
     */
    @Nullable
    protected HardwareEra screenEra() {
        return null;
    }

    /**
     * Outer bounds of the monitor body drawn around this screen's glass (the bezel and its chin), in screen
     * coordinates. A recipe viewer placing its panel beside the monitor reads this so the panel sits next to the
     * bezel rather than over it.
     */
    public dev.jstech.computers.client.theme.MonitorFrameStyle.Geometry frameBounds() {
        final HardwareEra era = screenEra();
        return dev.jstech.computers.client.theme.MonitorFrameStyle
                .forEra(era == null ? HardwareEra.STANDARD : era)
                .geometry(leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void init() {
        super.init();
        this.theme = EraThemes.ofNullable(screenEra());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Re-resolve so a board swap (which changes the host era) updates the skin without reopening the GUI.
        this.theme = EraThemes.ofNullable(screenEra());
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        /*
         * Bind this screen's era skin for the render pass (background, widgets, labels) and always restore the
         * default afterwards, so any unthemed draw stays on the frozen STANDARD look. A subclass that overrides
         * render still routes through here via super.render(), so its background and widgets get the bound skin;
         * its post-super draws are tooltips (vanilla-styled, palette-agnostic) so they are unaffected by the skin.
         */
        JsTechTheme.bind(theme);
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
            renderTooltip(graphics, mouseX, mouseY);
        } finally {
            JsTechTheme.unbind();
        }
    }

    /**
     * Whether the mouse is inside the rectangle at {@code (rx, ry)} sized {@code w x h}, with the rectangle given in this screen's local coordinates (relative to its top-left).
     */
    protected boolean hover(final int mouseX, final int mouseY, final int rx, final int ry, final int w, final int h) {
        final int mx = mouseX - leftPos;
        final int my = mouseY - topPos;
        return mx >= rx && mx < rx + w && my >= ry && my < ry + h;
    }

    /** Tells the server the player clicked the menu button with the given id. */
    protected void sendButton(final int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
