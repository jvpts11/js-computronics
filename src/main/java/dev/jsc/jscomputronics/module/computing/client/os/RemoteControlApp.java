/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.operation.payload.RemoteControlPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RemoteHostsPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Remote Control: the graphical route to the network's other machines. It lists what is reachable —
 * the rack servers above all, which have no screen of their own — and takes over the one you pick,
 * putting its session (POST, firmware, terminal or full desktop) on this monitor. The shell route is
 * {@code ssh}; this is the same reach for players who would rather point and click.
 */
public final class RemoteControlApp implements DesktopApp {

    private static final int ROW_H = 22;
    private static final int REFRESH_FRAMES = 60;

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    private List<RemoteHostsPayload.Entry> hosts = List.of();
    private int selected = -1;
    private int frame;
    // The content rectangle of the last frame, so clicks hit what the player actually saw.
    private int lastX;
    private int lastY;
    private int lastW;
    private int lastH;

    private static RemoteControlApp active;

    public RemoteControlApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
        active = this;
        request();
    }

    /** Receives the reachable-host list the server sent for the open window. */
    public static void accept(final RemoteHostsPayload payload) {
        if (active != null) {
            active.hosts = payload.hosts();
            if (active.selected >= active.hosts.size()) {
                active.selected = -1;
            }
        }
    }

    private void request() {
        PacketDistributor.sendToServer(new RemoteControlPayload(host, monitorPos, 0L,
                RemoteControlPayload.ACTION_LIST));
    }

    @Override
    public String title() {
        return "Remote Control";
    }

    @Override
    public int defaultWidth() {
        return 260;
    }

    @Override
    public int defaultHeight() {
        return 168;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        if (++frame % REFRESH_FRAMES == 0) {
            request();
        }
        lastX = x;
        lastY = y;
        lastW = width;
        lastH = height;
        g.drawString(font, "Machines on this network", x + 4, y + 4, skin.dim(), false);

        final int listY = y + 16;
        final int listH = height - 16 - 24;
        skin.panel(g, x + 2, listY, width - 4, listH);
        if (hosts.isEmpty()) {
            g.drawString(font, "No other machine is reachable.", x + 8, listY + 8, skin.dim(), false);
        }
        for (int i = 0; i < hosts.size(); i++) {
            final int rowY = listY + 2 + i * ROW_H;
            if (rowY + ROW_H > listY + listH) {
                break; // the rest waits for a bigger window
            }
            final RemoteHostsPayload.Entry entry = hosts.get(i);
            final boolean hovered = mouseX >= x + 4 && mouseX < x + width - 6
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (i == selected || hovered) {
                g.fill(x + 4, rowY, x + width - 6, rowY + ROW_H, skin.listHover());
            }
            g.drawString(font, entry.hostname(), x + 8, rowY + 3, skin.listRowText(i == selected), false);
            final String detail = entry.type() + (entry.os().isEmpty() ? "" : "  -  " + entry.os());
            g.drawString(font, detail, x + 8, rowY + 12, skin.dim(), false);
            final String state = entry.running() ? "up" : "off";
            g.drawString(font, state, x + width - 10 - font.width(state), rowY + 7,
                    entry.running() ? 0xFF3FA34D : 0xFFC04A3E, false);
        }

        final boolean canConnect = selected >= 0 && selected < hosts.size() && hosts.get(selected).running();
        skin.button(g, font, x + width - 90, y + height - 20, 86, 16,
                canConnect ? "Take over" : "Select a machine",
                mouseX >= x + width - 90 && mouseX < x + width - 4
                        && mouseY >= y + height - 20 && mouseY < y + height - 4,
                false, canConnect);
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        final int x = lastX;
        final int y = lastY;
        final int width = lastW;
        final int height = lastH;
        final int listY = y + 16;
        for (int i = 0; i < hosts.size(); i++) {
            final int rowY = listY + 2 + i * ROW_H;
            if (mouseX >= x + 4 && mouseX < x + width - 6 && mouseY >= rowY && mouseY < rowY + ROW_H) {
                selected = i;
                return;
            }
        }
        if (mouseX >= x + width - 90 && mouseX < x + width - 4
                && mouseY >= y + height - 20 && mouseY < y + height - 4
                && selected >= 0 && selected < hosts.size() && hosts.get(selected).running()) {
            PacketDistributor.sendToServer(new RemoteControlPayload(host, monitorPos,
                    hosts.get(selected).pos(), RemoteControlPayload.ACTION_CONNECT));
        }
    }
}
