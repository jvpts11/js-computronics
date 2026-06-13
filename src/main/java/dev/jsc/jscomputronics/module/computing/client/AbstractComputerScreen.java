/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Base for the computing container screens, holding the two hand-rolled helpers every one of them repeated: a hit-test against a rectangle in this screen's local space, and a menu-button send to the server.
 */
public abstract class AbstractComputerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected AbstractComputerScreen(final T menu, final Inventory playerInventory, final Component title) {
        super(menu, playerInventory, title);
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
