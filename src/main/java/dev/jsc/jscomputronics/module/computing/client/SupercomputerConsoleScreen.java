/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.common.hardware.PhiCoprocessorSpec;
import dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity;
import dev.jsc.jscomputronics.module.computing.menu.SupercomputerConsoleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Supercomputer Console: the cluster survey.
 */
public class SupercomputerConsoleScreen extends AbstractContainerScreen<SupercomputerConsoleMenu> {

    private static final String[] LADDER = {"x8", "x16", "x32", "x64", "x128", "x256"};
    private static final String[] MODELS = {"PHI 5100", "PHI 7120", "PHI 7290", "PHI 9000"};

    public SupercomputerConsoleScreen(final SupercomputerConsoleMenu menu, final Inventory inventory,
                                      final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 152;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, imageWidth - 12);
        JscOsTheme.panel(g, x + 8, y + 26, 90, 20);   // PARALLEL CRAFTS
        JscOsTheme.panel(g, x + 102, y + 26, 90, 20); // IN USE
        for (int i = 0; i < PhiCoprocessorSpec.SLOT_COUNT; i++) {
            g.fill(x + 8, y + 58 + i * 13, x + imageWidth - 8, y + 69 + i * 13, JscOsTheme.panel());
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "SUPERCOMPUTER", 12, 11, JscOsTheme.text());
        final String status;
        final int color;
        if (!menu.interfaceFound()) {
            status = "NO CLUSTER";
            color = JscOsTheme.red();
        } else if (menu.clusterOnline()) {
            status = "ONLINE";
            color = JscOsTheme.green();
        } else {
            status = "OFFLINE";
            color = JscOsTheme.amber();
        }
        JscOsTheme.textRight(g, font, status, imageWidth - 12, 11, color);

        JscOsTheme.tileText(g, font, 8, 26, "PARALLEL CRAFTS",
                JscOsTheme.fmt(menu.budget()), "", JscOsTheme.accent());
        JscOsTheme.tileText(g, font, 102, 26, "IN USE",
                menu.inUse() + " / " + menu.budget(), "", JscOsTheme.text());

        JscOsTheme.textS(g, font, "SLOTS - place nodes against the HBW Interface in order", 8, 50,
                JscOsTheme.dim());
        for (int i = 0; i < PhiCoprocessorSpec.SLOT_COUNT; i++) {
            final int rowY = 60 + i * 13;
            JscOsTheme.textS(g, font, (i + 1) + "", 12, rowY, JscOsTheme.dim());
            JscOsTheme.textS(g, font, LADDER[i], 22, rowY, JscOsTheme.accent());
            final int code = menu.slotCode(i);
            final String label;
            final int labelColor;
            if (code == HbwInterfaceBlockEntity.SLOT_NO_NODE) {
                label = "no node";
                labelColor = JscOsTheme.dim();
            } else if (code == HbwInterfaceBlockEntity.SLOT_EMPTY) {
                label = "empty bay";
                labelColor = JscOsTheme.amber();
            } else if (code == HbwInterfaceBlockEntity.SLOT_UNDER_RATED) {
                label = "rating too low for this slot";
                labelColor = JscOsTheme.red();
            } else if (code == HbwInterfaceBlockEntity.SLOT_OFFLINE) {
                label = "node offline";
                labelColor = JscOsTheme.amber();
            } else {
                // An OK slot encodes its Phi model as SLOT_OK_BASE + modelIndex; clamp BOTH ends so any
                // unexpected code can never index past the model list (a -1 here once crashed the screen).
                label = MODELS[Math.max(0, Math.min(MODELS.length - 1,
                        code - HbwInterfaceBlockEntity.SLOT_OK_BASE))];
                labelColor = JscOsTheme.green();
            }
            JscOsTheme.textS(g, font, label, 56, rowY, labelColor);
            if (code >= HbwInterfaceBlockEntity.SLOT_OK_BASE) {
                JscOsTheme.textSRight(g, font, "+" + LADDER[i].substring(1), imageWidth - 12, rowY,
                        JscOsTheme.green());
            }
        }
        if (menu.unslotted() > 0) {
            JscOsTheme.textS(g, font, menu.unslotted() + " node(s) past the six slots - inert", 8, 141,
                    JscOsTheme.amber());
        } else if (!menu.interfaceFound()) {
            JscOsTheme.textS(g, font, "place this console against a node or the HBW Interface", 8, 141,
                    JscOsTheme.dim());
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
