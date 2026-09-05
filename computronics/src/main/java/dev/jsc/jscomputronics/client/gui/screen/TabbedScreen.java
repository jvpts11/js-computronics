/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link JscScreen} that hosts a row of {@link GuiTab}s with a content area below the tab bar.
 */
public abstract class TabbedScreen extends JscScreen {

    private final List<GuiTab> tabs = new ArrayList<>();
    private int activeTabIndex = 0;

    protected int tabBarHeight = 20;

    protected TabbedScreen(final Component title) {
        super(title);
    }

    protected void addTab(final GuiTab tab) {
        tabs.add(tab);
    }

    public int activeTabIndex() {
        return activeTabIndex;
    }

    public int tabCount() {
        return tabs.size();
    }

    public void selectTab(final int index) {
        if (index < 0 || index >= tabs.size() || index == activeTabIndex) {
            return;
        }
        if (activeTabIndex < tabs.size()) {
            tabs.get(activeTabIndex).onDeselected();
        }
        activeTabIndex = index;
        tabs.get(activeTabIndex).onSelected();
    }

    @Override
    protected void renderContent(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float partialTick) {
        renderTabBar(graphics, mouseX, mouseY, partialTick);
        if (activeTabIndex < tabs.size()) {
            final int contentX = 0;
            final int contentY = tabBarHeight;
            final int contentWidth = this.width;
            final int contentHeight = this.height - tabBarHeight;
            tabs.get(activeTabIndex).renderContent(
                    graphics,
                    contentX, contentY, contentWidth, contentHeight,
                    mouseX, mouseY, partialTick);
        }
    }

    protected void renderTabBar(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float partialTick) {
        if (tabs.isEmpty()) {
            return;
        }
        final int tabWidth = this.width / tabs.size();
        for (int i = 0; i < tabs.size(); i++) {
            final int tabX = i * tabWidth;
            final boolean active = i == activeTabIndex;
            // Background tint: brighter for the active tab. ARGB colors.
            final int bg = active ? 0xFF3A3A3A : 0xFF1E1E1E;
            graphics.fill(tabX, 0, tabX + tabWidth, tabBarHeight, bg);
            graphics.drawCenteredString(
                    this.font,
                    tabs.get(i).title(),
                    tabX + tabWidth / 2,
                    (tabBarHeight - this.font.lineHeight) / 2,
                    0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        // Clicking the tab bar switches tabs.
        if (mouseY < tabBarHeight && !tabs.isEmpty()) {
            final int tabWidth = this.width / tabs.size();
            final int clicked = (int) (mouseX / tabWidth);
            if (clicked >= 0 && clicked < tabs.size()) {
                selectTab(clicked);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}