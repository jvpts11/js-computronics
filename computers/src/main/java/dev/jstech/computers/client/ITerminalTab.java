/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * One content tab within the {@link ComputerTerminalScreen}. Each implementation holds the
 * tab-specific state and draws into the content area that sits to the right of the side rail.
 *
 * <p>Two render passes are required because {@code AbstractContainerScreen} draws slots between
 * {@code renderBg} and {@code renderLabels}; merging them into one call would place text below
 * the item-stack z layer. {@link ComputerTerminalScreen} invokes each pass at the right moment.
 */
interface ITerminalTab {

    /**
     * Draws the background for this tab. Called from {@code renderBg}, before slots are rendered.
     *
     * @param g       graphics context with world-coordinate origin
     * @param x       screen left edge in world coords ({@code leftPos})
     * @param y       screen top edge in world coords ({@code topPos})
     * @param cx      content area left edge ({@code x + CONTENT_X})
     * @param cy      content area top edge ({@code y + 6})
     * @param cw      content area width
     * @param mouseX  cursor X in world coords
     * @param mouseY  cursor Y in world coords
     */
    void renderTabBg(GuiGraphics g, int x, int y, int cx, int cy, int cw, int mouseX, int mouseY);

    /**
     * Draws text labels for this tab. Called from {@code renderLabels}, after slots are rendered.
     *
     * @param g  graphics context with screen-relative origin ({@code leftPos/topPos} already applied)
     * @param cx content area left edge relative to screen origin ({@code CONTENT_X})
     * @param cy content area top edge relative to screen origin ({@code 6})
     * @param cw content area width
     */
    void renderTabLabels(GuiGraphics g, int cx, int cy, int cw);

    /**
     * Handles a mouse press routed from the parent screen's {@code mouseClicked}, after any
     * open popup has already claimed the event. Returns {@code true} if consumed.
     */
    default boolean onMouseClicked(final double mouseX, final double mouseY, final int button) {
        return false;
    }

    /**
     * Handles a mouse scroll routed from the parent screen's {@code mouseScrolled}. Returns
     * {@code true} if consumed.
     */
    default boolean onMouseScrolled(final double mouseX, final double mouseY, final double dy) {
        return false;
    }

    /**
     * Handles a mouse release routed from the parent screen's {@code mouseReleased}. Returns
     * {@code true} if consumed.
     */
    default boolean onMouseReleased(final double mouseX, final double mouseY, final int button) {
        return false;
    }

    /** Called each game tick from {@code containerTick} so the tab can sync its state. */
    default void onContainerTick() {
    }
}
