/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * One tab within a {@link TabbedScreen}.
 */
public interface IGuiTab {

    Component title();

    void renderContent(
            GuiGraphics graphics,
            int contentX, int contentY, int contentWidth, int contentHeight,
            int mouseX, int mouseY, float partialTick);

    default void onSelected() {
    }

    default void onDeselected() {
    }
}