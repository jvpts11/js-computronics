/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterManagerActionPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterManagerStatePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterManagerStatePayload.Detail;
import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterManagerStatePayload.WireCluster;
import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterManagerStatePayload.WireCraft;
import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterManagerStatePayload.WireDest;
import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterManagerStatePayload.WireJob;
import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterManagerStatePayload.WireLane;
import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterManagerStatePayload.WireNode;
import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterMoveOutPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkItemEntry;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestClusterManagerPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Cluster Manager: the Cluster Management Computer's front for every cluster on its network. Three
 * tabs — supercomputers, datacenters, AI — a list of clusters on the left and the selected one on the
 * right: its nodes with a power switch each, its craft queue or its inventory, the bulk install and
 * power actions, and the install job as it runs. Every click is an action the server answers with a
 * fresh state; nothing here decides anything on its own.
 */
public final class ClusterManagerApp implements DesktopApp {

    private static final String[] LADDER = {"x8", "x16", "x32", "x64", "x128", "x256"};
    private static final String[] MODELS = {"5100", "7120", "7290", "9000"};
    private static final int TAB_H = 13;
    private static final int LIST_W = 100;
    /** The node popup's width: four buttons ("POWER OFF" the widest) side by side without clipping. */
    private static final int POPUP_W = 260;
    private static final int ROW_H = 11;
    private static final int PAD = 4;
    /** Clearance between two columns of a table, so neighbouring words never touch. */
    private static final int GAP = 6;
    private static final int BTN_H = 12;
    private static final int REFRESH_TICKS = 40;

    private static ClusterManagerApp active;

    private final BlockPos host;
    private OsSkin skin = OsSkin.fallback();
    private int text = 0xFF1A2230;
    private int dim = 0xFF60687A;
    private int accent = 0xFF2563C0;
    private int panel = 0xFFFFFFFF;
    private int header = 0xFFE6E8EF;
    private int edge = 0xFF6E7686;
    private int field = 0xFFF3F4F7;
    private static final int GREEN = 0xFF2A9D4A;
    private static final int AMBER = 0xFFB5781A;
    private static final int RED = 0xFFC0392B;
    private static final int WARN_BG = 0xFFFCE3A1;
    private static final int WARN_TEXT = 0xFF6B4E00;

    private ClusterManagerStatePayload state;
    private int tab;          // 0 supercomputers, 1 datacenters, 2 AI
    private int selIndex = -1;
    private int subTab;       // supercomputers: 0 nodes, 1 map, 2 queue; datacenters: 0 servers, 1 inventory
    private int scroll;
    private int frames;
    private int lastX;
    private int lastY;
    private int lastW;
    private int lastH;
    private WireNode popupNode;
    private NetworkItemEntry moveItem;
    private int moveQty = 64;
    private int moveDest;
    // The rename dialog's text while it is open (null = closed), and the header button that opens it.
    private String renameText;
    private int[] renameHit;

    public ClusterManagerApp(final BlockPos host) {
        this.host = host;
        active = this;
        request();
    }

    /** Routes a state from the server to the open window. */
    public static void accept(final ClusterManagerStatePayload payload) {
        if (active != null) {
            active.state = payload;
            if (payload.detail().kind() >= 0) {
                active.tab = payload.detail().kind();
                active.selIndex = payload.detail().index();
            }
        }
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestClusterManagerPayload(host, tab, selIndex));
    }

    @Override
    public void onRestored() {
        active = this;
        request();
    }

    private void act(final int action) {
        PacketDistributor.sendToServer(ClusterManagerActionPayload.bulk(host, action, tab, selIndex));
    }

    private void actNode(final int action, final WireNode node) {
        PacketDistributor.sendToServer(new ClusterManagerActionPayload(host, action, tab, selIndex, node.rackPos(), node.row()));
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        this.text = osSkin.text();
        this.dim = osSkin.dim();
        this.accent = osSkin.accent();
        this.panel = osSkin.windowBg();
        this.header = osSkin.panelBg();
        this.edge = osSkin.edge();
        this.field = osSkin.fieldBg();
    }

    @Override
    public String title() {
        return "Cluster Manager";
    }

    // Wide enough for the three footer actions to read unclipped beside the cluster list.
    @Override
    public int defaultWidth() {
        return 340;
    }

    @Override
    public int defaultHeight() {
        return 210;
    }

    @Override
    public int minWidth() {
        return 330;
    }

    @Override
    public int minHeight() {
        return 150;
    }

    // ---- rendering ----

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                              final int height, final int mouseX, final int mouseY, final float partialTick) {
        lastX = x;
        lastY = y;
        lastW = width;
        lastH = height;
        if (++frames % REFRESH_TICKS == 0) {
            request();
        }
        g.fill(x, y, x + width, y + height, panel);
        drawTabs(g, font, x, y, width);
        final int top = y + TAB_H;
        final int bodyH = height - TAB_H;
        if (state == null) {
            g.drawString(font, "Reaching the network ...", x + PAD, top + PAD, dim, false);
            return;
        }
        if (!state.head().hasCard()) {
            g.fill(x + 1, top, x + width - 1, top + ROW_H + 2, WARN_BG);
            g.drawString(font, "A Cluster Interface Card is required.", x + PAD, top + 2, WARN_TEXT, false);
        }
        drawList(g, font, x, top, bodyH, mouseX, mouseY);
        drawDetail(g, font, x + LIST_W, top, width - LIST_W, bodyH, mouseX, mouseY);
    }

    private void drawTabs(final GuiGraphics g, final Font font, final int x, final int y, final int width) {
        final String[] labels = {"Supercomputers", "Datacenters", "AI"};
        final int[] widths = {width * 2 / 5, width * 2 / 5, width - 2 * (width * 2 / 5)};
        int tx = x;
        for (int i = 0; i < labels.length; i++) {
            final boolean on = tab == i;
            g.fill(tx, y, tx + widths[i], y + TAB_H, on ? panel : header);
            if (on) {
                g.fill(tx, y, tx + widths[i], y + 2, accent);
            }
            g.drawString(font, labels[i], tx + (widths[i] - font.width(labels[i])) / 2, y + 3, on ? text : dim, false);
            tx += widths[i];
        }
        g.fill(x, y + TAB_H - 1, x + width, y + TAB_H, edge);
    }

    private List<WireCluster> clustersOfTab() {
        final List<WireCluster> out = new ArrayList<>();
        if (state != null) {
            for (final WireCluster c : state.clusters()) {
                if (c.kind() == tab) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    private void drawList(final GuiGraphics g, final Font font, final int x, final int top, final int h,
                          final int mouseX, final int mouseY) {
        g.fill(x + LIST_W - 1, top, x + LIST_W, top + h, edge);
        final List<WireCluster> list = clustersOfTab();
        g.fill(x, top, x + LIST_W - 1, top + ROW_H, header);
        g.drawString(font, clip(font, (tab == 0 ? "SUPERCOMPUTERS" : tab == 1 ? "SECTIONS" : "AI CLUSTERS")
                + " · " + list.size(), LIST_W - PAD * 2), x + PAD, top + 2, dim, false);
        int ry = top + ROW_H;
        if (list.isEmpty()) {
            g.drawString(font, tab == 2 ? "none yet" : "none on this network", x + PAD, ry + 2, dim, false);
            return;
        }
        for (final WireCluster c : list) {
            if (ry + ROW_H * 2 > top + h) {
                break;
            }
            final boolean sel = c.index() == selIndex;
            final boolean hover = mouseX >= x && mouseX < x + LIST_W - 1 && mouseY >= ry && mouseY < ry + ROW_H * 2;
            if (sel || hover) {
                g.fill(x, ry, x + LIST_W - 1, ry + ROW_H * 2, sel ? field : header);
            }
            if (sel) {
                g.fill(x, ry, x + 2, ry + ROW_H * 2, accent);
            }
            g.fill(x + 5, ry + 3, x + 8, ry + 6, c.reachable() ? (c.online() ? GREEN : RED) : dim);
            g.drawString(font, clip(font, c.name(), LIST_W - 16), x + 11, ry + 1, sel ? text : text, false);
            g.drawString(font, clip(font, c.sub(), LIST_W - 8), x + 5, ry + ROW_H + 1, dim, false);
            ry += ROW_H * 2;
        }
    }

    private void drawDetail(final GuiGraphics g, final Font font, final int x, final int top, final int w, final int h,
                            final int mouseX, final int mouseY) {
        final Detail d = state.detail();
        if (d.kind() != tab || d.index() < 0) {
            renameHit = null;
            g.drawString(font, tab == 2 ? "No AI clusters on this network." : "Select a cluster on the left.",
                    x + PAD, top + PAD, dim, false);
            return;
        }
        int y = top + PAD;
        final String pill = d.online() ? "ONLINE" : "OFFLINE";
        g.drawString(font, pill, x + w - PAD - font.width(pill), y, d.online() ? GREEN : RED, false);
        // The name is the player's to change: a small button beside the state pill opens the rename dialog.
        final int rw = font.width("RENAME") + 6;
        final int rx = x + w - PAD - font.width(pill) - 6 - rw;
        renameHit = new int[] {rx, y - 1, rw, 10};
        final boolean renameHover = in(renameHit, mouseX, mouseY);
        g.fill(rx, y - 1, rx + rw, y + 9, renameHover ? header : field);
        OsSkin.outline(g, rx, y - 1, rw, 10, edge);
        g.drawString(font, "RENAME", rx + 3, y, renameHover ? text : dim, false);
        g.drawString(font, clip(font, d.name(), rx - 6 - (x + PAD)), x + PAD, y, text, false);
        y += ROW_H;
        g.drawString(font, clip(font, d.sub(), w - PAD * 2), x + PAD, y, dim, false);
        y += ROW_H;
        final WireJob job = state.job();
        if (job.active() && job.clusterKind() == tab && job.clusterIndex() == selIndex) {
            y = drawJob(g, font, x, y, w, job);
        }
        y = drawSubTabs(g, font, x, y, w);
        final int bottom = top + h - BTN_H - PAD * 2;
        if (tab == 0) {
            switch (subTab) {
                case 1 -> drawClusterMap(g, font, x, y, w, bottom, d);
                case 2 -> drawQueue(g, font, x, y, w, bottom, d);
                default -> drawNodes(g, font, x, y, w, bottom, d, mouseX, mouseY);
            }
        } else if (subTab == 1) {
            drawInventory(g, font, x, y, w, bottom, mouseX, mouseY);
        } else {
            drawNodes(g, font, x, y, w, bottom, d, mouseX, mouseY);
        }
        drawButtons(g, font, x, top + h - BTN_H - PAD, w, mouseX, mouseY, job.active());
    }

    private int drawJob(final GuiGraphics g, final Font font, final int x, int y, final int w, final WireJob job) {
        g.fill(x + PAD, y, x + w - PAD, y + ROW_H * 2 + 6, WARN_BG);
        final String lanes = job.lanes().isEmpty() ? "" : " · " + job.lanes().get(0).name() + " " + (job.lanes().get(0).permille() / 10) + "%";
        g.drawString(font, clip(font, "Installing " + job.label() + " · " + job.done() + " of " + job.total()
                + (job.cancelled() ? " · cancelling" : "") + lanes, w - PAD * 4), x + PAD * 2, y + 2, WARN_TEXT, false);
        final int barX = x + PAD * 2;
        final int barW = w - PAD * 4;
        g.fill(barX, y + ROW_H + 2, barX + barW, y + ROW_H + 6, field);
        final int done = job.total() == 0 ? 0 : barW * job.done() / job.total();
        g.fill(barX, y + ROW_H + 2, barX + done, y + ROW_H + 6, accent);
        if (!job.lanes().isEmpty()) {
            final int laneW = barW / Math.max(1, job.total());
            int lx = barX + done;
            for (final WireLane lane : job.lanes()) {
                g.fill(lx, y + ROW_H + 2, lx + laneW * lane.permille() / 1000, y + ROW_H + 6, AMBER);
                lx += laneW;
            }
        }
        return y + ROW_H * 2 + 8;
    }

    private int drawSubTabs(final GuiGraphics g, final Font font, final int x, final int y, final int w) {
        final String[] labels = tab == 0 ? new String[] {"NODES", "CLUSTER MAP", "QUEUE"} : new String[] {"SERVERS", "INVENTORY"};
        int tx = x + PAD;
        for (int i = 0; i < labels.length; i++) {
            final boolean on = subTab == i;
            g.drawString(font, labels[i], tx, y, on ? text : dim, false);
            if (on) {
                g.fill(tx, y + 9, tx + font.width(labels[i]), y + 10, accent);
            }
            tx += font.width(labels[i]) + 10;
        }
        g.fill(x + PAD, y + 11, x + w - PAD, y + 12, edge);
        return y + 14;
    }

    private void drawNodes(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int bottom,
                           final Detail d, final int mouseX, final int mouseY) {
        // Columns are measured from both edges so nothing runs into its neighbour: the unit and the metric
        // take fixed room, the status takes what its longest word needs, and the name and system share the
        // rest. The old fixed offsets ran "RACK U" straight into "NODE" the moment a name was more than a
        // few letters long.
        final int switchW = 18;
        final int rightEdge = x + w - PAD - switchW;
        final int metricW = 32;
        final int statusW = font.width("INSTALLING") + 6;
        final int c0 = x + PAD;
        final int c1 = c0 + font.width("R00 U0") + 8;
        final int c4 = rightEdge - metricW;
        final int c3 = c4 - statusW;
        final int c2 = c1 + Math.max(30, (c3 - c1 - GAP) / 2);
        g.drawString(font, "RACK/U", c0, y, dim, false);
        g.drawString(font, "NODE", c1, y, dim, false);
        g.drawString(font, "SYSTEM", c2, y, dim, false);
        g.drawString(font, "STATUS", c3, y, dim, false);
        g.drawString(font, tab == 0 ? "PHI" : "USED", c4, y, dim, false);
        int ry = y + ROW_H;
        final List<WireNode> nodes = d.nodes();
        if (nodes.isEmpty()) {
            g.drawString(font, "no nodes seated", c0, ry, dim, false);
            return;
        }
        for (int i = scroll; i < nodes.size() && ry + ROW_H <= bottom; i++) {
            final WireNode n = nodes.get(i);
            final boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW_H;
            if (hover) {
                g.fill(x + 2, ry - 1, x + w - 2, ry + ROW_H - 1, header);
            }
            final int col = n.bayOn() ? text : dim;
            g.drawString(font, "R" + n.rackIndex() + " U" + (n.row() + 1), c0, ry, col, false);
            g.drawString(font, clip(font, n.name(), c2 - c1 - GAP), c1, ry, col, false);
            if (n.osLabel().isEmpty()) {
                g.drawString(font, "-", c2, ry, dim, false);
            } else {
                g.drawString(font, clip(font, n.osLabel(), c3 - c2 - GAP), c2, ry, col, false);
            }
            g.drawString(font, clip(font, stateLabel(n.state()), statusW - GAP), c3, ry, stateColor(n.state()), false);
            final String right = tab == 0 ? (n.phiModel() < 0 ? "-" : MODELS[Math.min(n.phiModel(), MODELS.length - 1)])
                    : (n.total() <= 0 ? "-" : (100 * n.used() / Math.max(1, n.total())) + "%");
            g.drawString(font, clip(font, right, metricW - GAP), c4, ry, accent, false);
            // the bay switch
            final int sx = rightEdge + 2;
            g.fill(sx, ry, sx + 14, ry + 8, field);
            g.fill(sx + (n.bayOn() ? 8 : 1), ry + 1, sx + (n.bayOn() ? 13 : 6), ry + 7, n.bayOn() ? GREEN : dim);
            ry += ROW_H;
        }
    }

    /** The one word for what a machine is doing, matching the state the server sent. */
    private static String stateLabel(final int state) {
        return switch (state) {
            case ClusterManagerStatePayload.STATE_INCOMPLETE -> "INCOMPLETE";
            case ClusterManagerStatePayload.STATE_BAY_OFF -> "BAY OFF";
            case ClusterManagerStatePayload.STATE_INSTALLING -> "INSTALLING";
            case ClusterManagerStatePayload.STATE_NO_COPROCESSOR -> "NO PHI";
            case ClusterManagerStatePayload.STATE_UNDER_RATED -> "PHI LOW";
            case ClusterManagerStatePayload.STATE_UNSLOTTED -> "INERT";
            case ClusterManagerStatePayload.STATE_NO_SYSTEM -> "NO SYSTEM";
            default -> "ONLINE";
        };
    }

    private int stateColor(final int state) {
        return switch (state) {
            case ClusterManagerStatePayload.STATE_ONLINE -> GREEN;
            case ClusterManagerStatePayload.STATE_INCOMPLETE -> RED;
            case ClusterManagerStatePayload.STATE_INSTALLING -> accent;
            case ClusterManagerStatePayload.STATE_BAY_OFF, ClusterManagerStatePayload.STATE_UNSLOTTED -> dim;
            default -> AMBER;
        };
    }

    private void drawClusterMap(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                                final int bottom, final Detail d) {
        g.drawString(font, "SLOT", x + PAD, y, dim, false);
        g.drawString(font, "CRAFTS", x + PAD + 30, y, dim, false);
        g.drawString(font, "NODE", x + PAD + 66, y, dim, false);
        g.drawString(font, "STATE", x + w - PAD - 60, y, dim, false);
        int ry = y + ROW_H;
        for (int slot = 0; slot < LADDER.length && ry + ROW_H <= bottom; slot++) {
            WireNode node = null;
            for (final WireNode n : d.nodes()) {
                if (n.slotIndex() == slot) {
                    node = n;
                }
            }
            g.drawString(font, String.valueOf(slot + 1), x + PAD, ry, dim, false);
            g.drawString(font, LADDER[slot], x + PAD + 30, ry, accent, false);
            if (node == null) {
                g.drawString(font, "no node", x + PAD + 66, ry, dim, false);
            } else {
                g.drawString(font, clip(font, "R" + node.rackIndex() + " U" + (node.row() + 1) + " " + node.name(), w - 140), x + PAD + 66, ry, text, false);
                final String st = node.code() >= 16 ? "ONLINE" : node.code() == 3 ? "BAY OFF" : node.code() == 2 ? "RATING LOW" : node.code() == 1 ? "NO PHI CARD" : "NO NODE";
                g.drawString(font, st, x + w - PAD - 60, ry, node.code() >= 16 ? GREEN : AMBER, false);
            }
            ry += ROW_H;
        }
        final int past = Math.max(0, d.nodes().size() - LADDER.length);
        if (past > 0 && ry + ROW_H <= bottom) {
            g.drawString(font, past + " node(s) past the six slots · inert", x + PAD, ry, AMBER, false);
        }
    }

    private void drawQueue(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int bottom,
                           final Detail d) {
        g.drawString(font, "OPERATION", x + PAD, y, dim, false);
        g.drawString(font, "BY", x + w / 2, y, dim, false);
        g.drawString(font, "SLOTS", x + w - PAD - 60, y, dim, false);
        int ry = y + ROW_H;
        if (d.queue().isEmpty()) {
            g.drawString(font, "no crafts in this queue", x + PAD, ry, dim, false);
            return;
        }
        for (int i = scroll; i < d.queue().size() && ry + ROW_H <= bottom; i++) {
            final WireCraft c = d.queue().get(i);
            final int col = c.waiting() ? dim : text;
            g.drawString(font, clip(font, "CRAFT " + c.label(), w / 2 - PAD * 2), x + PAD, ry, col, false);
            g.drawString(font, clip(font, c.requester(), w / 2 - 70), x + w / 2, ry, dim, false);
            g.drawString(font, c.waiting() ? "WAITING" : String.valueOf(c.slots()), x + w - PAD - 60, ry,
                    c.waiting() ? AMBER : accent, false);
            ry += ROW_H;
        }
    }

    private void drawInventory(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int bottom,
                               final int mouseX, final int mouseY) {
        final List<NetworkItemEntry> items = state.items();
        g.drawString(font, clip(font, items.size() + " kinds · click to move out · drop a stack to deposit", w - PAD * 2),
                x + PAD, y, dim, false);
        final int cell = 18;
        final int cols = Math.max(1, (w - PAD * 2) / cell);
        int gx = x + PAD;
        int gy = y + ROW_H;
        for (int i = scroll * cols; i < items.size() && gy + cell <= bottom; i++) {
            final NetworkItemEntry entry = items.get(i);
            final boolean hover = mouseX >= gx && mouseX < gx + cell && mouseY >= gy && mouseY < gy + cell;
            g.fill(gx, gy, gx + cell, gy + cell, hover ? header : field);
            DesktopItems.itemWithCount(g, font, entry.key().stack(1), gx + 1, gy + 1, shortCount(entry.total()));
            gx += cell;
            if (gx + cell > x + w - PAD) {
                gx = x + PAD;
                gy += cell;
            }
        }
        if (items.isEmpty()) {
            g.drawString(font, "the section is empty", x + PAD, y + ROW_H, dim, false);
        }
    }

    private void drawButtons(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                             final int mouseX, final int mouseY, final boolean jobRunning) {
        final String[] labels = jobRunning ? new String[] {"CANCEL JOB"}
                : tab == 1 && subTab == 1 ? new String[] {"BALANCE: " + balanceName()}
                : new String[] {"SYSTEM ALL", "PROGRAM ALL", allOn() ? "ALL OFF" : "ALL ON"};
        final int gap = 3;
        final int bw = (w - PAD * 2 - gap * (labels.length - 1)) / labels.length;
        int bx = x + PAD;
        for (final String label : labels) {
            final boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= y && mouseY < y + BTN_H;
            final boolean primary = label.startsWith("SYSTEM") && !state.head().mediumSystem().isEmpty()
                    || label.startsWith("PROGRAM") && !state.head().mediumProgram().isEmpty();
            g.fill(bx, y, bx + bw, y + BTN_H, primary ? accent : hover ? header : field);
            OsSkin.outline(g, bx, y, bw, BTN_H, edge);
            g.drawString(font, clip(font, label, bw - 4), bx + (bw - Math.min(bw - 4, font.width(label))) / 2, y + 2,
                    primary ? 0xFFFFFFFF : text, false);
            bx += bw + gap;
        }
    }

    private boolean allOn() {
        if (state == null || state.detail().nodes().isEmpty()) {
            return false;
        }
        for (final WireNode n : state.detail().nodes()) {
            if (!n.bayOn()) {
                return false;
            }
        }
        return true;
    }

    private String balanceName() {
        return switch (state == null ? 0 : state.detail().balance()) {
            case 0 -> "MANUAL";
            case 1 -> "ROUND-ROBIN";
            default -> "LEAST-LOADED";
        };
    }

    // ---- popups ----

    @Override
    public boolean modalActive() {
        return popupNode != null || moveItem != null || renameText != null;
    }

    @Override
    public void renderModal(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                            final int height, final int mouseX, final int mouseY) {
        final int pw = Math.min(width - 12, POPUP_W);
        final int ph = 80;
        final int px = x + (width - pw) / 2;
        final int py = y + (height - ph) / 2;
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, edge);
        g.fill(px, py, px + pw, py + ph, panel);
        g.fill(px, py, px + pw, py + ROW_H + 1, accent);
        if (popupNode != null) {
            final WireNode n = popupNode;
            g.drawString(font, clip(font, "NODE · " + n.name() + "  R" + n.rackIndex() + " U" + (n.row() + 1), pw - 8), px + 4, py + 2, 0xFFFFFFFF, false);
            g.drawString(font, n.osLabel().isEmpty() ? "No system installed" : n.osLabel(), px + 4, py + ROW_H + 4, text, false);
            g.drawString(font, clip(font, n.programs().isEmpty() ? "No programs" : n.programs(), pw - 8), px + 4, py + ROW_H * 2 + 4, dim, false);
            g.drawString(font, n.slotIndex() >= 0 ? "Cluster slot " + (n.slotIndex() + 1) : "Bay " + (n.bayOn() ? "on" : "off"),
                    px + 4, py + ROW_H * 3 + 4, dim, false);
            drawModalButtons(g, font, px, py + ph - BTN_H - 4, pw, mouseX, mouseY,
                    n.bayOn() ? "POWER OFF" : "POWER ON", "SYSTEM", "PROGRAM", "CLOSE");
        } else if (moveItem != null) {
            g.drawString(font, clip(font, "MOVE OUT · " + moveItem.key().displayName().getString(), pw - 8), px + 4, py + 2, 0xFFFFFFFF, false);
            g.drawString(font, "QUANTITY  (" + moveItem.total() + " available)", px + 4, py + ROW_H + 4, dim, false);
            final int[] presets = {1, 16, 64, 256, -1};
            final int cw = (pw - 8 - 4 * 2) / 5;
            for (int i = 0; i < presets.length; i++) {
                final int cx = px + 4 + i * (cw + 2);
                final boolean on = moveQty == presets[i];
                g.fill(cx, py + ROW_H * 2 + 4, cx + cw, py + ROW_H * 3 + 4, on ? accent : field);
                final String label = presets[i] < 0 ? "MAX" : String.valueOf(presets[i]);
                g.drawString(font, label, cx + (cw - font.width(label)) / 2, py + ROW_H * 2 + 6, on ? 0xFFFFFFFF : text, false);
            }
            final String dest = state.dests().isEmpty() ? "no destination" : state.dests().get(Math.min(moveDest, state.dests().size() - 1)).name();
            g.drawString(font, clip(font, "TO  ‹ " + dest + " ›", pw - 8), px + 4, py + ROW_H * 3 + 8, text, false);
            drawModalButtons(g, font, px, py + ph - BTN_H - 4, pw, mouseX, mouseY, "MOVE", "CANCEL");
        } else if (renameText != null) {
            g.drawString(font, "RENAME CLUSTER", px + 4, py + 2, 0xFFFFFFFF, false);
            g.drawString(font, "Type a name; empty goes back to the default.", px + 4, py + ROW_H + 4, dim, false);
            g.fill(px + 4, py + ROW_H * 2 + 6, px + pw - 4, py + ROW_H * 3 + 8, field);
            OsSkin.outline(g, px + 4, py + ROW_H * 2 + 6, pw - 8, ROW_H + 2, accent);
            g.drawString(font, clip(font, renameText, pw - 20) + "_", px + 8, py + ROW_H * 2 + 8, text, false);
            drawModalButtons(g, font, px, py + ph - BTN_H - 4, pw, mouseX, mouseY, "APPLY", "CANCEL");
        }
    }

    private void drawModalButtons(final GuiGraphics g, final Font font, final int px, final int by, final int pw,
                                  final int mouseX, final int mouseY, final String... labels) {
        final int bw = (pw - 8 - 2 * (labels.length - 1)) / labels.length;
        int bx = px + 4;
        for (final String label : labels) {
            final boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BTN_H;
            g.fill(bx, by, bx + bw, by + BTN_H, hover ? header : field);
            OsSkin.outline(g, bx, by, bw, BTN_H, edge);
            g.drawString(font, clip(font, label, bw - 2), bx + (bw - Math.min(bw - 2, font.width(label))) / 2, by + 2, text, false);
            bx += bw + 2;
        }
    }

    // ---- input ----

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (button != 0) {
            return;
        }
        final int x = lastX;
        final int y = lastY;
        final int width = lastW;
        final int height = lastH;
        if (modalActive()) {
            modalClick(x, y, width, height, mouseX, mouseY);
            return;
        }
        // Tabs.
        if (mouseY >= y && mouseY < y + TAB_H) {
            final int w25 = width * 2 / 5;
            final int t = mouseX < x + w25 ? 0 : mouseX < x + 2 * w25 ? 1 : 2;
            if (t != tab) {
                tab = t;
                selIndex = -1;
                subTab = 0;
                scroll = 0;
                request();
            }
            return;
        }
        final int top = y + TAB_H;
        // Cluster list.
        if (mouseX < x + LIST_W - 1) {
            final List<WireCluster> list = clustersOfTab();
            final int i = (int) ((mouseY - top - ROW_H) / (ROW_H * 2));
            if (mouseY >= top + ROW_H && i >= 0 && i < list.size()) {
                selIndex = list.get(i).index();
                scroll = 0;
                request();
            }
            return;
        }
        if (state == null || state.detail().kind() != tab || state.detail().index() < 0) {
            return;
        }
        final int dx = x + LIST_W;
        final int dw = width - LIST_W;
        if (in(renameHit, mouseX, mouseY)) {
            renameText = state.detail().name();
            return;
        }
        // Footer buttons.
        final int by = top + (height - TAB_H) - BTN_H - PAD;
        if (mouseY >= by && mouseY < by + BTN_H) {
            final boolean jobRunning = state.job().active() && state.job().clusterKind() == tab && state.job().clusterIndex() == selIndex;
            final int n = jobRunning ? 1 : tab == 1 && subTab == 1 ? 1 : 3;
            final int bw = (dw - PAD * 2 - 3 * (n - 1)) / n;
            final int i = (int) ((mouseX - dx - PAD) / (bw + 3));
            if (jobRunning) {
                act(ClusterManagerActionPayload.ACTION_CANCEL_JOB);
            } else if (tab == 1 && subTab == 1) {
                act(ClusterManagerActionPayload.ACTION_CYCLE_BALANCE);
            } else if (i == 0) {
                act(ClusterManagerActionPayload.ACTION_INSTALL_SYSTEM_ALL);
            } else if (i == 1) {
                act(ClusterManagerActionPayload.ACTION_INSTALL_PROGRAM_ALL);
            } else if (i == 2) {
                act(allOn() ? ClusterManagerActionPayload.ACTION_POWER_ALL_OFF : ClusterManagerActionPayload.ACTION_POWER_ALL_ON);
            }
            return;
        }
        // Sub-tabs sit under the header lines (and the job panel, when shown).
        int sy = top + PAD + ROW_H * 2;
        if (state.job().active() && state.job().clusterKind() == tab && state.job().clusterIndex() == selIndex) {
            sy += ROW_H * 2 + 8;
        }
        if (mouseY >= sy && mouseY < sy + 12) {
            final String[] labels = tab == 0 ? new String[] {"NODES", "CLUSTER MAP", "QUEUE"} : new String[] {"SERVERS", "INVENTORY"};
            int tx = dx + PAD;
            final Font font = net.minecraft.client.Minecraft.getInstance().font;
            for (int i = 0; i < labels.length; i++) {
                final int tw = font.width(labels[i]);
                if (mouseX >= tx && mouseX < tx + tw + 6) {
                    subTab = i;
                    scroll = 0;
                    return;
                }
                tx += tw + 10;
            }
            return;
        }
        final int rowsTop = sy + 14 + ROW_H;
        if (tab == 1 && subTab == 1) {
            // Inventory grid: an item opens the move-out; an empty click with a stack in hand deposits.
            final int cell = 18;
            final int cols = Math.max(1, (dw - PAD * 2) / cell);
            final int gx = (int) ((mouseX - dx - PAD) / cell);
            final int gy = (int) ((mouseY - rowsTop) / cell);
            final int idx = scroll * cols + gy * cols + gx;
            if (gx >= 0 && gx < cols && gy >= 0 && idx < state.items().size() && mouseY >= rowsTop) {
                moveItem = state.items().get(idx);
                moveQty = 64;
                moveDest = 0;
            } else if (mouseY >= rowsTop) {
                act(ClusterManagerActionPayload.ACTION_DEPOSIT);
            }
            return;
        }
        if (subTab == 0) {
            final int i = scroll + (int) ((mouseY - rowsTop) / ROW_H);
            if (mouseY >= rowsTop && i >= 0 && i < state.detail().nodes().size()) {
                final WireNode node = state.detail().nodes().get(i);
                if (mouseX >= dx + dw - PAD - 16) {
                    actNode(ClusterManagerActionPayload.ACTION_TOGGLE_NODE, node);
                } else {
                    popupNode = node;
                }
            }
        }
    }

    private void modalClick(final int x, final int y, final int width, final int height, final double mouseX, final double mouseY) {
        final int pw = Math.min(width - 12, POPUP_W);
        final int ph = 80;
        final int px = x + (width - pw) / 2;
        final int py = y + (height - ph) / 2;
        final int by = py + ph - BTN_H - 4;
        if (popupNode != null) {
            final int bw = (pw - 8 - 2 * 3) / 4;
            final int i = (int) ((mouseX - px - 4) / (bw + 2));
            if (mouseY >= by && mouseY < by + BTN_H && i >= 0 && i < 4) {
                switch (i) {
                    case 0 -> actNode(ClusterManagerActionPayload.ACTION_TOGGLE_NODE, popupNode);
                    case 1 -> actNode(ClusterManagerActionPayload.ACTION_INSTALL_SYSTEM_NODE, popupNode);
                    case 2 -> actNode(ClusterManagerActionPayload.ACTION_INSTALL_PROGRAM_NODE, popupNode);
                    default -> {
                    }
                }
                popupNode = null;
            } else if (mouseX < px || mouseX >= px + pw || mouseY < py || mouseY >= py + ph) {
                popupNode = null;
            }
            return;
        }
        if (renameText != null) {
            final int bw = (pw - 8 - 2) / 2;
            if (mouseY >= by && mouseY < by + BTN_H) {
                if (mouseX < px + 4 + bw) {
                    commitRename();
                } else {
                    renameText = null;
                }
            } else if (mouseX < px || mouseX >= px + pw || mouseY < py || mouseY >= py + ph) {
                renameText = null;
            }
            return;
        }
        if (moveItem != null) {
            final int cw = (pw - 8 - 4 * 2) / 5;
            if (mouseY >= py + ROW_H * 2 + 4 && mouseY < py + ROW_H * 3 + 4) {
                final int i = (int) ((mouseX - px - 4) / (cw + 2));
                final int[] presets = {1, 16, 64, 256, -1};
                if (i >= 0 && i < presets.length) {
                    moveQty = presets[i];
                }
                return;
            }
            if (mouseY >= py + ROW_H * 3 + 8 && mouseY < py + ROW_H * 4 + 8 && !state.dests().isEmpty()) {
                moveDest = mouseX < px + pw / 2 ? Math.floorMod(moveDest - 1, state.dests().size())
                        : (moveDest + 1) % state.dests().size();
                return;
            }
            final int bw = (pw - 8 - 2) / 2;
            if (mouseY >= by && mouseY < by + BTN_H) {
                final boolean move = mouseX < px + 4 + bw;
                if (move && !state.dests().isEmpty()) {
                    final WireDest dest = state.dests().get(Math.min(moveDest, state.dests().size() - 1));
                    final long qty = moveQty < 0 ? moveItem.total() : Math.min(moveQty, moveItem.total());
                    PacketDistributor.sendToServer(new ClusterMoveOutPayload(host, selIndex, moveItem.key(), qty, dest.pos()));
                }
                moveItem = null;
            } else if (mouseX < px || mouseX >= px + pw || mouseY < py || mouseY >= py + ph) {
                moveItem = null;
            }
        }
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (renameText != null) {
            switch (key) {
                case 256 -> renameText = null;      // escape
                case 257, 335 -> commitRename();    // enter
                case 259 -> {                       // backspace
                    if (!renameText.isEmpty()) {
                        renameText = renameText.substring(0, renameText.length() - 1);
                    }
                }
                default -> {
                }
            }
            return true;
        }
        if (key == 256 && modalActive()) { // escape closes a popup
            popupNode = null;
            moveItem = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(final char c) {
        if (renameText == null) {
            return false;
        }
        if (c >= 32 && c != 127 && renameText.length()
                < dev.jsc.jscomputronics.module.computing.operation.payload.ClusterRenamePayload.MAX_NAME) {
            renameText += c;
        }
        return true; // the dialog captures all typing while it is open
    }

    private void commitRename() {
        if (renameText != null && state != null) {
            PacketDistributor.sendToServer(new dev.jsc.jscomputronics.module.computing.operation.payload
                    .ClusterRenamePayload(host, tab, selIndex, renameText.strip()));
        }
        renameText = null;
    }

    private static boolean in(final int[] r, final double mx, final double my) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    // ---- helpers ----

    private static String clip(final Font font, final String s, final int width) {
        if (font.width(s) <= width) {
            return s;
        }
        String out = s;
        while (!out.isEmpty() && font.width(out + "..") > width) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }

    private static String shortCount(final long n) {
        if (n >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        }
        if (n >= 10_000L) {
            return (n / 1000) + "k";
        }
        if (n >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        }
        return String.valueOf(n);
    }
}
