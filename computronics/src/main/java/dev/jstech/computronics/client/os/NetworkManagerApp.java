/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.client.JscOsTheme;
import dev.jstech.computronics.operation.payload.NetworkManagerPayload;
import dev.jstech.computronics.operation.payload.NetworkNodeInfo;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.operation.payload.RequestNetworkManagerPayload;
import dev.jstech.computronics.operation.payload.RequestNiOperationsPayload;
import dev.jstech.computronics.program.OperationPalette;
import dev.jstech.core.gui.layout.DesktopZ;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Network Manager desktop app, exclusive to the Mainframe (the node that holds the network index).
 * It is a windowed task manager for the whole data network, with five tabs: Devices (every node),
 * Processes (live Operations), Hardware (the network's compute and storage totals), Map (the topology),
 * and Log (the recent Operations feed). Nodes carry the computer's name and specs; a Map node shows a
 * full tooltip on hover, and a logged Operation opens a detail dialog with its sub-operations.
 */
public final class NetworkManagerApp implements DesktopApp {

    private static final String[] TABS = {"Devices", "Processes", "Hardware", "Map", "Log"};
    private static final int TAB_DEVICES = 0;
    private static final int TAB_PROCESSES = 1;
    private static final int TAB_HARDWARE = 2;
    private static final int TAB_MAP = 3;
    private static final int TAB_LOG = 4;

    private static final int OPS_REFRESH_FRAMES = 40;

    private static final int C_MAINFRAME = 0xFF3A6AE0;
    private static final int C_SERVER = 0xFF12A26F;
    private static final int C_SUBFRAME = 0xFF7B52C9;
    private static final int C_PC = 0xFF1C9C9C;
    private static final int C_CRAFTING = 0xFFD98A3A;
    private static final int C_SUPERCOMPUTER = 0xFFC94FB0;
    private static final int C_CLUSTER_MANAGEMENT = 0xFFA9B23C;

    private static final int C_GREEN = 0xFF2EA043;
    private static final int C_AMBER = 0xFFE0A020;
    private static final int C_RED = 0xFFD1495B;

    private record Hit(int x, int y, int w, int h, Runnable onClick) {
        boolean contains(final double mx, final double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record NodeRect(int x, int y, int w, int h, NetworkNodeInfo node) {
        boolean contains(final double mx, final double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record OpRect(int x, int y, int w, int h, OperationRecord op) {
        boolean contains(final double mx, final double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    private NetworkManagerPayload data;
    private List<OperationRecord> activeOps = List.of();
    private List<OperationRecord> opsLog = List.of();
    private int scSlotsUsed;
    private int scSlotsTotal;
    private int tab;
    private int frame;
    private int logScroll;
    private int procScroll;
    private final List<Hit> hits = new ArrayList<>();
    private final List<NodeRect> mapNodes = new ArrayList<>();
    private final List<OpRect> logRows = new ArrayList<>();
    private OperationRecord detailOp;

    // Per-node drag offsets on the Map (kept only for this session, keyed by the node's short id), so the
    // player can pull crowded nodes apart. A node with no entry sits at its computed ring position.
    private final java.util.Map<String, int[]> nodeOffsets = new java.util.HashMap<>();
    private String draggingNode;
    private boolean panning;
    private double lastDragX;
    private double lastDragY;
    // Map view transform: pan (middle-drag) and zoom (wheel), so a large network can be explored.
    private int mapPanX;
    private int mapPanY;
    private double mapZoom = 1.0;
    private static final double MAP_ZOOM_MIN = 0.4;
    private static final double MAP_ZOOM_MAX = 2.5;

    private static NetworkManagerApp active;

    public NetworkManagerApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
        active = this;
        PacketDistributor.sendToServer(new RequestNetworkManagerPayload(host));
        requestOps();
    }

    /** Routes a network snapshot reply to the open Network Manager window. */
    public static void accept(final NetworkManagerPayload payload) {
        if (active != null && active.host.equals(payload.hostPos())) {
            active.data = payload;
        }
    }

    /** Routes the live in-flight Operations (and craft-slot capacity) to the open window. */
    public static void acceptActiveOps(final List<OperationRecord> ops, final int slotsUsed, final int slotsTotal) {
        if (active != null) {
            active.activeOps = ops;
            active.scSlotsUsed = slotsUsed;
            active.scSlotsTotal = slotsTotal;
        }
    }

    /** Routes the recent Operations log to the open window. */
    public static void acceptOpsLog(final List<OperationRecord> ops) {
        if (active != null) {
            active.opsLog = ops;
        }
    }

    private void requestOps() {
        PacketDistributor.sendToServer(new RequestNiOperationsPayload(host, monitorPos));
    }

    @Override
    public void onRestored() {
        active = this;
        PacketDistributor.sendToServer(new RequestNetworkManagerPayload(host));
        requestOps();
    }

    @Override public String title() {
        return "Network Manager";
    }

    @Override public int defaultWidth() {
        return 300;
    }

    @Override public int defaultHeight() {
        return 196;
    }

    @Override public int minWidth() {
        return 250;
    }

    @Override public int minHeight() {
        return 150;
    }

    @Override public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        active = this;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        hits.clear();
        mapNodes.clear();
        logRows.clear();
        g.fill(x, y, x + width, y + height, skin.windowBg());

        frame++;
        if ((tab == TAB_PROCESSES || tab == TAB_LOG) && frame % OPS_REFRESH_FRAMES == 0) {
            requestOps();
        }

        int tx = x + 2;
        for (int i = 0; i < TABS.length; i++) {
            final int tw = font.width(TABS[i]) + 14;
            skin.tab(g, font, tx, y + 2, tw, 15, TABS[i], tab == i);
            final int target = i;
            hits.add(new Hit(tx, y + 2, tw, 15, () -> selectTab(target)));
            tx += tw + 1;
        }
        g.fill(x, y + 17, x + width, y + 18, skin.edge());

        final int px = x + 6;
        final int py = y + 22;
        final int pw = width - 12;
        final int ph = height - 26;
        if (data == null) {
            g.drawString(font, "Loading network...", px, py + 4, skin.dim(), false);
            return;
        }
        g.drawString(font, "Network " + (data.networkId().isEmpty() ? "(none)" : data.networkId())
                + "   -   " + data.nodes().size() + " node(s)", px, py, skin.dim(), false);
        switch (tab) {
            case TAB_DEVICES -> devices(g, font, px, py + 12, pw, ph - 12, mouseX, mouseY);
            case TAB_PROCESSES -> processes(g, font, px, py + 12, pw, ph - 12, mouseX, mouseY);
            case TAB_HARDWARE -> hardware(g, font, px, py + 12, pw, ph - 12);
            case TAB_MAP -> map(g, font, px, py + 12, pw, ph - 12);
            case TAB_LOG -> log(g, font, px, py + 12, pw, ph - 12, mouseX, mouseY);
            default -> { }
        }
    }

    private void selectTab(final int target) {
        tab = target;
        detailOp = null;
        if (target == TAB_PROCESSES || target == TAB_LOG) {
            requestOps();
        }
    }

    private void devices(final GuiGraphics g, final Font font, final int x, final int top, final int w,
                         final int h, final int mouseX, final int mouseY) {
        final int nameX = x + 10;
        final int typeX = x + (int) (w * 0.52);
        final int statusX = x + w - 48;
        final List<NetworkNodeInfo> nodes = data.nodes();
        final int rowH = 12;
        final int maxRows = Math.max(1, (h - 11) / rowH);
        final int shown = Math.min(nodes.size(), maxRows);
        final int bandBottom = top + 11 + shown * rowH;

        g.drawString(font, "NODE", nameX, top, skin.dim(), false);
        g.drawString(font, "TYPE", typeX, top, skin.dim(), false);
        g.drawString(font, "STATUS", statusX, top, skin.dim(), false);
        g.fill(x, top + 10, x + w, top + 11, skin.edge());
        // Column separators so each column reads as its own lane.
        g.fill(typeX - 5, top, typeX - 4, bandBottom, skin.edge());
        g.fill(statusX - 5, top, statusX - 4, bandBottom, skin.edge());

        int y = top + 11;
        for (int i = 0; i < shown; i++) {
            final NetworkNodeInfo n = nodes.get(i);
            final boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + rowH;
            skin.listRow(g, x, y, w, rowH, hov, false);
            g.fill(x + 2, y + 4, x + 6, y + 8, kindColor(n.kind()));
            final boolean named = !n.name().isEmpty();
            final String nm = named ? n.name() : "unnamed";
            final int idW = font.width(n.id());
            final String nmClipped = trim(font, nm, typeX - 6 - nameX - idW - 4);
            g.drawString(font, nmClipped, nameX, y + 2, named ? skin.text() : skin.dim(), false);
            g.drawString(font, n.id(), nameX + font.width(nmClipped) + 4, y + 2, skin.dim(), false);
            g.drawString(font, trim(font, n.kindLabel(), statusX - 6 - typeX), typeX, y + 2, skin.dim(), false);
            final String status = n.online() ? "online" : "offline";
            g.drawString(font, status, x + w - font.width(status), y + 2, n.online() ? C_GREEN : skin.dim(), false);
            y += rowH;
        }
        if (nodes.size() > maxRows) {
            g.drawString(font, "+ " + (nodes.size() - maxRows) + " more", x + 2, y + 1, skin.dim(), false);
        }
    }

    private void processes(final GuiGraphics g, final Font font, final int x, final int top, final int w,
                           final int h, final int mouseX, final int mouseY) {
        final String slots = "Craft slots  " + scSlotsUsed + " / " + scSlotsTotal;
        g.drawString(font, slots, x + 2, top, skin.dim(), false);
        final String live = activeOps.size() + " running";
        g.drawString(font, live, x + w - font.width(live), top, skin.dim(), false);
        final int listTop = top + 12;
        if (activeOps.isEmpty()) {
            g.drawString(font, "No Operations in flight.", x + 2, listTop + 2, skin.dim(), false);
            return;
        }
        final int rowH = 13;
        final int barW = 3;
        final int listW = w - barW;
        final int maxRows = Math.max(1, (h - 12) / rowH);
        final int maxScroll = Math.max(0, activeOps.size() - maxRows);
        procScroll = Math.max(0, Math.min(procScroll, maxScroll));
        int y = listTop;
        for (int i = 0; i < maxRows && procScroll + i < activeOps.size(); i++) {
            final OperationRecord op = activeOps.get(procScroll + i);
            final boolean hov = mouseX >= x && mouseX < x + listW && mouseY >= y && mouseY < y + rowH;
            skin.listRow(g, x, y, listW, rowH, hov, false);
            final String type = OperationPalette.labelFor(op.type());
            g.drawString(font, type, x + 4, y + 3, OperationPalette.colorFor(op.type()), false);
            final int nameX = x + 4 + font.width(type) + 4;
            final int barX = x + listW / 2 + 4;
            g.drawString(font, trim(font, op.name().getString(), barX - nameX - 4), nameX, y + 3, skin.text(), false);
            final int barLen = listW / 2 - 40;
            final double frac = op.requested() > 0 ? Math.min(1.0, (double) op.moved() / op.requested()) : 0.0;
            g.fill(barX, y + 4, barX + barLen, y + rowH - 3, skin.fieldBg());
            g.fill(barX, y + 4, barX + (int) (barLen * frac), y + rowH - 3, statusColor(op.status()));
            final String st = statusLabel(op.status());
            g.drawString(font, st, x + listW - font.width(st), y + 3, statusColor(op.status()), false);
            y += rowH;
        }
        scrollbar(g, x + w - barW, listTop, barW, maxRows * rowH, activeOps.size(), maxRows, procScroll);
    }

    private void hardware(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                          final int h) {
        final NetworkManagerPayload.Hardware hw = data.hardware();
        int row = y + 2;
        row = hwRow(g, font, x, row, w, "Orchestration capacity", JscOsTheme.fmt(hw.capacity()) + " it/t");
        row = hwRow(g, font, x, row, w, "Parallel queues", String.valueOf(hw.queues()));
        row = hwRow(g, font, x, row, w, "RAM buffer", JscOsTheme.fmt(hw.ramBuffer()) + " it");
        row = hwRow(g, font, x, row, w, "Network storage", JscOsTheme.fmt(hw.storageItems()) + " items");
        row += 6;
        g.fill(x, row, x + w, row + 1, skin.edge());
        row += 6;
        g.drawString(font, "NODES", x + 2, row, skin.dim(), false);
        row += 12;
        row = hwRow(g, font, x, row, w, "Mainframes", String.valueOf(countKind(NetworkNodeInfo.KIND_MAINFRAME)));
        row = hwRow(g, font, x, row, w, "Servers", String.valueOf(countKind(NetworkNodeInfo.KIND_SERVER)));
        row = hwRow(g, font, x, row, w, "Subframes", String.valueOf(countKind(NetworkNodeInfo.KIND_SUBFRAME)));
        row = hwRow(g, font, x, row, w, "Supercomputers",
                String.valueOf(countKind(NetworkNodeInfo.KIND_SUPERCOMPUTER)));
        row = hwRow(g, font, x, row, w, "Crafting computers",
                String.valueOf(countKind(NetworkNodeInfo.KIND_CRAFTING)));
        row = hwRow(g, font, x, row, w, "Personal computers", String.valueOf(countKind(NetworkNodeInfo.KIND_PC)));
        hwRow(g, font, x, row, w, "Cluster managers",
                String.valueOf(countKind(NetworkNodeInfo.KIND_CLUSTER_MANAGEMENT)));
    }

    private int hwRow(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                      final String key, final String value) {
        g.drawString(font, key, x + 2, y, skin.text(), false);
        g.drawString(font, value, x + w - font.width(value), y, skin.text(), false);
        return y + 12;
    }

    private int countKind(final int kind) {
        int n = 0;
        for (final NetworkNodeInfo node : data.nodes()) {
            if (node.kind() == kind) {
                n++;
            }
        }
        return n;
    }

    private void log(final GuiGraphics g, final Font font, final int x, final int top, final int w, final int h,
                     final int mouseX, final int mouseY) {
        if (opsLog.isEmpty()) {
            g.drawString(font, "No Operations logged yet.", x + 2, top + 2, skin.dim(), false);
            return;
        }
        final int rowH = 12;
        final int barW = 3;
        final int listW = w - barW;
        final int maxRows = Math.max(1, h / rowH);
        final int maxScroll = Math.max(0, opsLog.size() - maxRows);
        logScroll = Math.max(0, Math.min(logScroll, maxScroll));
        // The log arrives oldest-first; show the most recent at the top, offset by the scroll position.
        int y = top;
        for (int i = 0; i < maxRows && logScroll + i < opsLog.size(); i++) {
            final OperationRecord op = opsLog.get(opsLog.size() - 1 - (logScroll + i));
            final boolean hov = mouseX >= x && mouseX < x + listW && mouseY >= y && mouseY < y + rowH;
            skin.listRow(g, x, y, listW, rowH, hov, false);
            final String type = OperationPalette.labelFor(op.type());
            g.drawString(font, type, x + 4, y + 2, OperationPalette.colorFor(op.type()), false);
            final int nameX = x + 4 + font.width(type) + 4;
            final String st = statusLabel(op.status());
            g.drawString(font, trim(font, op.name().getString(), listW - (nameX - x) - font.width(st) - 8),
                    nameX, y + 2, skin.text(), false);
            g.drawString(font, st, x + listW - font.width(st), y + 2, statusColor(op.status()), false);
            logRows.add(new OpRect(x, y, listW, rowH, op));
            y += rowH;
        }
        scrollbar(g, x + w - barW, top, barW, maxRows * rowH, opsLog.size(), maxRows, logScroll);
    }

    /** A thin scrollbar thumb down the right edge of a scrollable list. */
    private void scrollbar(final GuiGraphics g, final int x, final int y, final int w, final int trackH,
                           final int total, final int visible, final int scroll) {
        if (w <= 0 || total <= visible) {
            return;
        }
        g.fill(x, y, x + w, y + trackH, skin.fieldBg());
        final int thumbH = Math.max(8, trackH * visible / total);
        final int maxScroll = total - visible;
        final int thumbY = y + (trackH - thumbH) * scroll / Math.max(1, maxScroll);
        g.fill(x, thumbY, x + w, thumbY + thumbH, skin.accent());
    }

    private void map(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + h, skin.fieldBg());
        OsSkin.outline(g, x, y, w, h, skin.edge());
        g.drawString(font, "drag nodes  -  middle-drag to pan  -  wheel to zoom", x + 4, y + h - 10,
                skin.dim(), false);
        final List<NetworkNodeInfo> nodes = data.nodes();
        int mainframe = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).kind() == NetworkNodeInfo.KIND_MAINFRAME) {
                mainframe = i;
                break;
            }
        }
        // The Mainframe sits at the centre; the rest ring around it. Adjacent nodes alternate between two
        // radii so labels don't collide, the ring spread scales with the zoom, and pan plus per-node drag
        // offsets (both in screen pixels) let the player explore and arrange a large network.
        final int vcx = x + w / 2;
        final int vcy = y + h / 2;
        int mcx = vcx + mapPanX;
        int mcy = vcy + mapPanY;
        if (mainframe >= 0) {
            final int[] off = nodeOffsets.get(nodes.get(mainframe).id());
            if (off != null) {
                mcx += off[0];
                mcy += off[1];
            }
        }
        final int baseRadius = Math.max(28, Math.min(w, h) / 2 - 30);
        final int others = nodes.size() - (mainframe >= 0 ? 1 : 0);
        int placed = 0;
        for (int i = 0; i < nodes.size(); i++) {
            if (i == mainframe) {
                continue;
            }
            final NetworkNodeInfo n = nodes.get(i);
            final double a = others > 0 ? (2 * Math.PI * placed / others) - Math.PI / 2 : 0;
            final int r = baseRadius - (placed % 2) * 14;
            int nx = vcx + mapPanX + (int) (r * Math.cos(a) * mapZoom);
            int ny = vcy + mapPanY + (int) (r * Math.sin(a) * mapZoom);
            final int[] off = nodeOffsets.get(n.id());
            if (off != null) {
                nx += off[0];
                ny += off[1];
            }
            drawLink(g, mcx, mcy, nx, ny, 0xFF9FB4E6);
            node(g, font, nx, ny, n);
            placed++;
        }
        if (mainframe >= 0) {
            node(g, font, mcx, mcy, nodes.get(mainframe));
        }
    }

    private void node(final GuiGraphics g, final Font font, final int cx, final int cy, final NetworkNodeInfo n) {
        final String label = n.displayName();
        final int tw = font.width(label) + 8;
        final int nx = cx - tw / 2;
        final int ny = cy - 6;
        g.fill(nx, ny, nx + tw, ny + 13, skin.windowBg());
        OsSkin.outline(g, nx, ny, tw, 13, kindColor(n.kind()));
        g.drawString(font, label, nx + 4, ny + 3, skin.text(), false);
        mapNodes.add(new NodeRect(nx, ny, tw, 13, n));
    }

    /** A thin link drawn as a horizontal leg then a vertical leg (the rect drawer has no diagonals). */
    private static void drawLink(final GuiGraphics g, final int x1, final int y1, final int x2, final int y2,
                                 final int color) {
        g.fill(Math.min(x1, x2), y1, Math.max(x1, x2), y1 + 1, color);
        g.fill(x2, Math.min(y1, y2), x2 + 1, Math.max(y1, y2), color);
    }

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY) {
        if (tab != TAB_MAP || detailOp != null) {
            return;
        }
        for (final NodeRect r : mapNodes) {
            if (r.contains(mouseX, mouseY)) {
                drawNodeTooltip(g, font, r.node(), mouseX, mouseY, x, y, width, height);
                return;
            }
        }
    }

    private void drawNodeTooltip(final GuiGraphics g, final Font font, final NetworkNodeInfo n,
                                 final int mouseX, final int mouseY, final int cx, final int cy,
                                 final int cw, final int ch) {
        final List<String> lines = new ArrayList<>();
        lines.add(n.name().isEmpty() ? "unnamed" : n.name());
        lines.add(n.kindLabel() + "  -  " + (n.online() ? "online" : "offline"));
        if (n.cpuMhz() > 0) {
            lines.add("CPU " + cpuClock(n.cpuMhz()) + (n.vramMb() > 0 ? "   VRAM " + JscOsTheme.fmt(n.vramMb())
                    + " MB" : ""));
        }
        if (!n.osLabel().isEmpty()) {
            lines.add("OS " + n.osLabel());
        }
        if (n.storageTotalMb() > 0) {
            lines.add("Storage " + JscOsTheme.fmt(n.storageFreeMb()) + " / "
                    + JscOsTheme.fmt(n.storageTotalMb()) + " MB free");
        } else if (n.storageFreeMb() > 0) {
            lines.add("Storage " + JscOsTheme.fmt(n.storageFreeMb()) + " MB free");
        }
        if (n.publicPermille() >= 0) {
            lines.add("Private " + (100 - n.publicPermille() / 10) + "%");
        }
        lines.add("id " + n.id());

        int tw = 0;
        for (final String l : lines) {
            tw = Math.max(tw, font.width(l));
        }
        final int boxW = tw + 8;
        final int boxH = lines.size() * 10 + 4;
        int bx = mouseX + 10;
        int by = mouseY + 6;
        bx = Math.min(bx, cx + cw - boxW - 1);
        by = Math.min(by, cy + ch - boxH - 1);
        bx = Math.max(bx, cx);
        by = Math.max(by, cy);
        // The tooltip pass runs at the base pose; without lifting to the tooltip depth the box would draw
        // behind the window body and never be seen.
        g.pose().pushPose();
        g.pose().translate(0, 0, DesktopZ.TOOLTIP);
        g.fill(bx, by, bx + boxW, by + boxH, 0xF00E0E12);
        OsSkin.outline(g, bx, by, boxW, boxH, kindColor(n.kind()));
        int ly = by + 3;
        for (int i = 0; i < lines.size(); i++) {
            final int color = i == 0 ? 0xFFFFFFFF : (i == 1 ? kindColor(n.kind()) : 0xFFB7BCCB);
            g.drawString(font, lines.get(i), bx + 4, ly, color, false);
            ly += 10;
        }
        g.pose().popPose();
    }

    // --- op detail dialog (a logged Operation and its sub-operations) ---

    @Override
    public boolean modalActive() {
        return detailOp != null;
    }

    @Override
    public void renderModal(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final int height, final int mouseX, final int mouseY) {
        if (detailOp == null) {
            return;
        }
        g.fill(x, y, x + width, y + height, 0xB0000000);
        final int dw = Math.min(width - 12, 240);
        final int dh = Math.min(height - 12, 150);
        final int dx = x + (width - dw) / 2;
        final int dy = y + (height - dh) / 2;
        skin.panel(g, dx, dy, dw, dh);
        OsSkin.outline(g, dx, dy, dw, dh, skin.edge());

        final OperationRecord op = detailOp;
        final String type = OperationPalette.labelFor(op.type());
        g.drawString(font, type, dx + 6, dy + 6, OperationPalette.colorFor(op.type()), false);
        g.drawString(font, trim(font, op.name().getString(), dw - 16 - font.width(type)),
                dx + 6 + font.width(type) + 4, dy + 6, skin.text(), false);

        final String reqLabel = op.requested() >= 1_000_000_000L ? "all" : JscOsTheme.fmt(op.requested());
        g.drawString(font, JscOsTheme.fmt(op.moved()) + " of " + reqLabel + "   " + statusLabel(op.status()),
                dx + 6, dy + 18, statusColor(op.status()), false);
        g.fill(dx + 6, dy + 30, dx + dw - 6, dy + 31, skin.edge());

        int ly = dy + 34;
        final int lineMax = dy + dh - 24;
        if (!op.subs().isEmpty()) {
            g.drawString(font, "STAGES", dx + 6, ly, skin.dim(), false);
            ly += 11;
            for (final OperationRecord.SubRow s : op.subs()) {
                if (ly > lineMax) {
                    break;
                }
                g.drawString(font, trim(font, s.server(), dw - 70), dx + 8, ly, skin.text(), false);
                g.drawString(font, s.moved() + "/" + s.planned(),
                        dx + dw - 6 - font.width(s.moved() + "/" + s.planned()), ly, subStateColor(s.state()), false);
                ly += 10;
            }
        } else if (!op.moves().isEmpty()) {
            g.drawString(font, "SOURCES", dx + 6, ly, skin.dim(), false);
            ly += 11;
            for (final OperationRecord.MoveRow m : op.moves()) {
                if (ly > lineMax) {
                    break;
                }
                g.drawString(font, trim(font, m.from() + " -> " + m.to(), dw - 60), dx + 8, ly, skin.text(), false);
                g.drawString(font, JscOsTheme.fmt(m.qty()), dx + dw - 6 - font.width(JscOsTheme.fmt(m.qty())), ly,
                        skin.dim(), false);
                ly += 10;
            }
        } else {
            g.drawString(font, "No sub-operations.", dx + 6, ly, skin.dim(), false);
        }

        // Close hint / button along the bottom.
        final String close = "Close";
        final int cwd = font.width(close) + 12;
        final int cbx = dx + dw - cwd - 4;
        final int cby = dy + dh - 15;
        final boolean chov = mouseX >= cbx && mouseX < cbx + cwd && mouseY >= cby && mouseY < cby + 13;
        skin.button(g, font, cbx, cby, cwd, 13, close, chov, false, false);
    }

    private int subStateColor(final byte state) {
        return switch (state) {
            case OperationRecord.SubRow.SUB_COMPLETED -> C_GREEN;
            case OperationRecord.SubRow.SUB_STREAMING -> C_GREEN;
            case OperationRecord.SubRow.SUB_READING -> C_AMBER;
            default -> skin.dim();
        };
    }

    private static String trim(final Font font, final String s, final int maxWidth) {
        if (maxWidth <= 0 || font.width(s) <= maxWidth) {
            return s;
        }
        String out = s;
        while (!out.isEmpty() && font.width(out + "...") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    private static String cpuClock(final int mhz) {
        return mhz >= 1000 ? String.format(Locale.ROOT, "%.2f GHz", mhz / 1000.0) : mhz + " MHz";
    }

    private static String statusLabel(final byte status) {
        return switch (status) {
            case OperationRecord.STATUS_COMPLETED -> "done";
            case OperationRecord.STATUS_PARTIAL -> "partial";
            case OperationRecord.STATUS_FAILED -> "failed";
            case OperationRecord.STATUS_PROCESSING -> "running";
            case OperationRecord.STATUS_WAITING -> "waiting";
            case OperationRecord.STATUS_RESOURCE_LOCKED -> "locked";
            case OperationRecord.STATUS_PENDING -> "queued";
            case OperationRecord.STATUS_DISCARDED -> "discarded";
            default -> "";
        };
    }

    private int statusColor(final byte status) {
        return switch (status) {
            case OperationRecord.STATUS_PROCESSING, OperationRecord.STATUS_COMPLETED -> C_GREEN;
            case OperationRecord.STATUS_PARTIAL, OperationRecord.STATUS_WAITING,
                    OperationRecord.STATUS_PENDING -> C_AMBER;
            case OperationRecord.STATUS_FAILED, OperationRecord.STATUS_RESOURCE_LOCKED,
                    OperationRecord.STATUS_DISCARDED -> C_RED;
            default -> skin.text();
        };
    }

    private static int kindColor(final int kind) {
        return switch (kind) {
            case NetworkNodeInfo.KIND_SERVER -> C_SERVER;
            case NetworkNodeInfo.KIND_SUBFRAME -> C_SUBFRAME;
            case NetworkNodeInfo.KIND_PC -> C_PC;
            case NetworkNodeInfo.KIND_CRAFTING -> C_CRAFTING;
            case NetworkNodeInfo.KIND_SUPERCOMPUTER -> C_SUPERCOMPUTER;
            case NetworkNodeInfo.KIND_CLUSTER_MANAGEMENT -> C_CLUSTER_MANAGEMENT;
            default -> C_MAINFRAME;
        };
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        if (detailOp != null) {
            // Any click while the detail dialog is up dismisses it.
            detailOp = null;
            return;
        }
        if (tab == TAB_MAP && button == 2) {
            // Middle-drag pans the map.
            panning = true;
            lastDragX = mouseX;
            lastDragY = mouseY;
            return;
        }
        if (button != 0) {
            return;
        }
        for (final Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                hit.onClick().run();
                return;
            }
        }
        if (tab == TAB_MAP) {
            // Pressing a node arms a drag so the player can pull crowded nodes apart.
            for (final NodeRect r : mapNodes) {
                if (r.contains(mouseX, mouseY)) {
                    draggingNode = r.node().id();
                    lastDragX = mouseX;
                    lastDragY = mouseY;
                    return;
                }
            }
        }
        if (tab == TAB_LOG) {
            for (final OpRect r : logRows) {
                if (r.contains(mouseX, mouseY)) {
                    detailOp = r.op();
                    return;
                }
            }
        }
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        if (panning) {
            mapPanX += (int) Math.round(mouseX - lastDragX);
            mapPanY += (int) Math.round(mouseY - lastDragY);
            lastDragX = mouseX;
            lastDragY = mouseY;
            return;
        }
        if (draggingNode == null) {
            return;
        }
        final int[] off = nodeOffsets.computeIfAbsent(draggingNode, k -> new int[2]);
        off[0] += (int) Math.round(mouseX - lastDragX);
        off[1] += (int) Math.round(mouseY - lastDragY);
        lastDragX = mouseX;
        lastDragY = mouseY;
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY,
                              final int button) {
        panning = false;
        draggingNode = null;
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        if (tab == TAB_MAP) {
            final double factor = delta > 0 ? 1.1 : 1.0 / 1.1;
            mapZoom = Math.max(MAP_ZOOM_MIN, Math.min(MAP_ZOOM_MAX, mapZoom * factor));
            return true;
        }
        if (tab == TAB_LOG) {
            logScroll = Math.max(0, logScroll - (int) Math.signum(delta));
            return true;
        }
        if (tab == TAB_PROCESSES) {
            procScroll = Math.max(0, procScroll - (int) Math.signum(delta));
            return true;
        }
        return false;
    }
}
