/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.client.gui.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Base for industrial-machine container screens.
 *
 * <p>Industrial machines do not carry a hardware era, so this class intentionally omits the
 * {@code EraTheme} binding present in computing screens. It inherits the standard dark panel
 * background and correct render ordering from {@link CoreContainerScreen} and adds lightweight
 * utility methods shared by all machine GUIs: a local-space hover test and a menu-button
 * send helper.
 *
 * <p>Subclasses implement {@link #renderBg} and, when needed, {@link #renderLabels}.
 */
public abstract class AbstractMachineScreen<T extends AbstractContainerMenu>
        extends CoreContainerScreen<T> {

    protected AbstractMachineScreen(
            final T menu,
            final Inventory playerInventory,
            final Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * Returns {@code true} when the mouse cursor is inside the rectangle at {@code (rx, ry)}
     * (in this screen's local coordinate space, relative to its top-left corner) with the given
     * dimensions. Delegates to the inherited {@link #leftPos}/{@link #topPos} offsets so callers
     * do not have to subtract the screen origin themselves.
     */
    protected boolean hover(
            final int mouseX, final int mouseY,
            final int rx, final int ry,
            final int w, final int h) {
        final int mx = mouseX - leftPos;
        final int my = mouseY - topPos;
        return mx >= rx && mx < rx + w && my >= ry && my < ry + h;
    }

    /**
     * Tells the server the player clicked the menu button with the given id. Used to trigger
     * server-side operations (mode toggles, side-config changes, etc.) without an additional
     * custom packet.
     */
    protected void sendButton(final int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
