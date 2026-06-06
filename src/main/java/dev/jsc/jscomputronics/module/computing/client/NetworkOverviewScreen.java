/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkNodeInfo;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkNodesPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A read-only overview of every node currently on a Mainframe's network: the Mainframe, its Servers and any Subframes, each with a short id, a capacity/storage figure and an online indicator.
 */
public final class NetworkOverviewScreen extends Screen {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int DIVIDER = 0xFF9A9A9A;
    private static final int LABEL = 0xFF404040;
    private static final int SUB = 0xFF5A5A5A;
    private static final int MAINFRAME = 0xFF3D6DA5;
    private static final int SERVER = 0xFF3DA53D;
    private static final int SUBFRAME = 0xFFA53DA5;
    private static final int LED_ON = 0xFF3DA53D;
    private static final int LED_OFF = 0xFF777777;
    private static final int W = 220;
    private static final int H = 200;
    private static final int ROW_H = 14;

    private final String networkId;
    private final List<NetworkNodeInfo> nodes;

    private NetworkOverviewScreen(final String networkId, final List<NetworkNodeInfo> nodes) {
        super(Component.translatable("menu.jsc.network_overview"));
        this.networkId = networkId;
        this.nodes = nodes;
    }

    public static void open(final NetworkNodesPayload payload) {
        Minecraft.getInstance().setScreen(new NetworkOverviewScreen(payload.networkId(), payload.nodes()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // dims the world behind

        final int x = (width - W) / 2;
        final int y = (height - H) / 2;
        g.fill(x, y, x + W, y + H, PANEL);
        g.fill(x, y, x + W, y + 1, BEVEL_LIGHT);
        g.fill(x, y, x + 1, y + H, BEVEL_LIGHT);
        g.fill(x, y + H - 1, x + W, y + H, BEVEL_DARK);
        g.fill(x + W - 1, y, x + W, y + H, BEVEL_DARK);

        g.drawString(font, "Network", x + 8, y + 7, LABEL, false);
        g.drawString(font, "net " + (networkId.isEmpty() ? "--" : networkId) + "   ·   "
                + nodes.size() + " node(s)", x + 8, y + 19, SUB, false);
        g.fill(x + 8, y + 31, x + W - 8, y + 32, DIVIDER);
        g.drawString(font, "TYPE", x + 8, y + 35, LABEL, false);
        g.drawString(font, "ID", x + 70, y + 35, LABEL, false);
        g.drawString(font, "INFO", x + 128, y + 35, LABEL, false);
        g.fill(x + 8, y + 45, x + W - 8, y + 46, DIVIDER);

        final int maxRows = (H - 56) / ROW_H;
        final int shown = Math.min(nodes.size(), maxRows);
        for (int i = 0; i < shown; i++) {
            final NetworkNodeInfo n = nodes.get(i);
            final int ry = y + 50 + i * ROW_H;
            g.drawString(font, n.kindLabel(), x + 8, ry, kindColor(n.kind()), false);
            g.drawString(font, n.id(), x + 70, ry, LABEL, false);
            g.drawString(font, n.detail(), x + 128, ry, LABEL, false);
            led(g, x + W - 16, ry, n.online());
        }
        if (nodes.size() > shown) {
            g.drawString(font, "+ " + (nodes.size() - shown) + " more",
                    x + 8, y + 50 + shown * ROW_H, SUB, false);
        }
    }

    private static int kindColor(final int kind) {
        return switch (kind) {
            case NetworkNodeInfo.KIND_MAINFRAME -> MAINFRAME;
            case NetworkNodeInfo.KIND_SUBFRAME -> SUBFRAME;
            default -> SERVER;
        };
    }

    private static void led(final GuiGraphics g, final int x, final int y, final boolean on) {
        final int c = on ? LED_ON : LED_OFF;
        g.fill(x + 1, y, x + 6, y + 7, c);
        g.fill(x, y + 1, x + 7, y + 6, c);
    }
}
