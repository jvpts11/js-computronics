/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.RemoteControlPayload;
import dev.jstech.computronics.operation.payload.RemoteHostsPayload;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Remote Control: the graphical route to the network's other machines. It lists what is reachable, the
 * rack servers above all, which have no screen of their own, and takes over the one you pick, putting its
 * session (POST, firmware, terminal or full desktop) on this monitor. The shell route is {@code ssh}; this
 * is the same reach for players who would rather point and click.
 */
public final class RemoteControlApp implements DesktopApp {

    private static final int ROW_H = 22;
    private static final int REFRESH_FRAMES = 60;
    private static final int C_UP = 0xFF3FA34D;
    private static final int C_DOWN = 0xFFC04A3E;

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    private List<RemoteHostsPayload.Entry> hosts = List.of();
    private int selected = -1;
    private int frame;
    private int lastMouseX;
    private int lastMouseY;

    private static RemoteControlApp active;

    private final Panel root = new Panel();
    private final Label header;
    private final ListView<RemoteHostsPayload.Entry> hostList;
    private final Label emptyLabel;
    private final Button connect;

    public RemoteControlApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
        header = root.add(new Label("Machines on this network", Label.Tone.DIM));
        hostList = root.add(new ListView<RemoteHostsPayload.Entry>(() -> hosts, ROW_H, this::renderHostRow)
                .setOnClick((index, button, mx, my) -> {
                    if (index >= 0) {
                        selected = index;
                    }
                }));
        emptyLabel = root.add(new Label("No other machine is reachable.", Label.Tone.DIM));
        connect = root.add(new Button(() -> canConnect() ? "Take over" : "Select a machine", this::takeOver));
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
        PacketDistributor.sendToServer(new RemoteControlPayload(host, monitorPos, 0L, RemoteControlPayload.ACTION_LIST));
    }

    private boolean canConnect() {
        return selected >= 0 && selected < hosts.size() && hosts.get(selected).running();
    }

    private void takeOver() {
        if (canConnect()) {
            PacketDistributor.sendToServer(new RemoteControlPayload(host, monitorPos, hosts.get(selected).pos(),
                    RemoteControlPayload.ACTION_CONNECT));
        }
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
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        header.setBounds(x + 4, y + 4, width - 8, 8);
        final int listY = y + 16;
        final int listH = height - 16 - 24;
        skin.panel(g, x + 2, listY, width - 4, listH);
        hostList.setVisible(!hosts.isEmpty());
        hostList.setBounds(x + 4, listY + 2, width - 10, Math.max(ROW_H, listH - 4));
        emptyLabel.setVisible(hosts.isEmpty());
        emptyLabel.setBounds(x + 8, listY + 8, width - 16, 8);
        connect.setBounds(x + width - 90, y + height - 20, 86, 16);
        connect.setEnabled(canConnect());
        connect.setPrimary(canConnect());
        root.render(g, ctx);
    }

    private void renderHostRow(final GuiGraphics g, final UiContext ctx, final RemoteHostsPayload.Entry entry, final int index,
                               final int x, final int y, final int w, final int h, final boolean hovered, final boolean selectedRow) {
        final Font font = ctx.font();
        final boolean sel = index == selected;
        if (sel || hovered) {
            g.fill(x, y, x + w, y + h, ctx.skin().listHover());
        }
        g.drawString(font, entry.hostname(), x + 4, y + 3, ctx.skin().listRowText(sel), false);
        final String detail = entry.type() + (entry.os().isEmpty() ? "" : "  -  " + entry.os());
        g.drawString(font, detail, x + 4, y + 12, ctx.skin().dim(), false);
        final String state = entry.running() ? "up" : "off";
        g.drawString(font, state, x + w - 4 - font.width(state), y + 7, entry.running() ? C_UP : C_DOWN, false);
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        return root.mouseScrolled(lastMouseX, lastMouseY, delta);
    }
}
