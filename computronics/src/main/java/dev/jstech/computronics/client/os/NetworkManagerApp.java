/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.core.client.gui.theme.JsTechTheme;
import dev.jstech.computronics.operation.payload.CancelOperationPayload;
import dev.jstech.computronics.operation.payload.NetworkManagerPayload;
import dev.jstech.computronics.operation.payload.NetworkNodeInfo;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.operation.payload.RequestNetworkManagerPayload;
import dev.jstech.computronics.operation.payload.RequestNiOperationsPayload;
import dev.jstech.computronics.operation.payload.SetOperationPriorityPayload;
import dev.jstech.computronics.program.OperationPalette;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.ColumnHeader;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.Popup;
import dev.jstech.core.client.gui.component.ScrollBar;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.Texts;
import dev.jstech.core.client.gui.component.UiComponent;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.gui.layout.DesktopZ;
import dev.jstech.core.operation.OperationPriority;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The Network Manager desktop app, exclusive to the Mainframe (the node that holds the network index).
 * It is a windowed task manager for the whole data network, with five tabs: Devices (every node),
 * Processes (live Operations), Hardware (the network's compute and storage totals), Map (the topology),
 * and Log (the recent Operations feed). Nodes carry the computer's name and specs; a Map node shows a
 * full tooltip on hover, and a logged Operation opens a detail dialog with its sub-operations.
 *
 * <p>The tabs, the tables, the hardware readout, the scrollbars and the detail dialog are components; the
 * map is a canvas of its own, since its nodes are dragged, panned and zoomed rather than listed.
 */
public final class NetworkManagerApp implements DesktopApp {

    private static final List<String> TABS = List.of("Devices", "Processes", "Hardware", "Map", "Log", "Stats");
    private static final int TAB_DEVICES = 0;
    private static final int TAB_PROCESSES = 1;
    private static final int TAB_HARDWARE = 2;
    private static final int TAB_MAP = 3;
    private static final int TAB_LOG = 4;
    private static final int TAB_STATS = 5;
    private static final int STAT_ROW_H = 12;
    private static final int STATS_REFRESH_FRAMES = 100;

    private static final int OPS_REFRESH_FRAMES = 40;
    private static final int DEV_ROW_H = 12;
    private static final int PROC_ROW_H = 13;
    private static final int LOG_ROW_H = 12;
    private static final int HW_ROW_H = 12;
    private static final int BAR_W = 3;
    private static final int DETAIL_W = 240;
    private static final int DETAIL_H = 150;

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
    private static final int C_LINK = 0xFF9FB4E6;

    private static final double MAP_ZOOM_MIN = 0.4;
    private static final double MAP_ZOOM_MAX = 2.5;

    private record NodeRect(int x, int y, int w, int h, NetworkNodeInfo node) {
        boolean contains(final double mx, final double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** One line of the hardware readout: what it counts, and the count read live from the snapshot. */
    private record HardwareRow(String key, Supplier<String> value) {
    }

    /** One line of the detail dialog: what the stage or the source was, and how much went through it. */
    private record DetailRow(String left, String right, int color) {
    }

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    @Nullable
    private NetworkManagerPayload data;
    private List<OperationRecord> activeOps = List.of();
    private List<OperationRecord> logNewestFirst = List.of();
    private int scSlotsUsed;
    private int scSlotsTotal;
    private int tab;
    private int frame;
    private int lastX;
    private int lastY;
    private int lastW;
    private int lastH;
    private int lastMouseX;
    private int lastMouseY;
    @Nullable
    private Font lastFont;
    @Nullable
    private OperationRecord detailOp;
    private List<DetailRow> detailRows = List.of();

    // Per-node drag offsets on the Map (kept only for this session, keyed by the node's short id), so the
    // player can pull crowded nodes apart. A node with no entry sits at its computed ring position.
    private final Map<String, int[]> nodeOffsets = new HashMap<>();
    @Nullable
    private String draggingNode;
    private boolean panning;
    private double lastDragX;
    private double lastDragY;
    // Map view transform: pan (middle-drag) and zoom (wheel), so a large network can be explored.
    private int mapPanX;
    private int mapPanY;
    private double mapZoom = 1.0;
    private final List<NodeRect> mapNodes = new ArrayList<>();

    private static NetworkManagerApp active;

    // ---- components ----
    private final Panel root = new Panel();
    private final TabStrip tabs;
    private final Label loadingLabel;
    private final Label netLabel;
    private final ColumnHeader devColumns;
    private final ListView<NetworkNodeInfo> devList;
    private final Label slotsLabel;
    private final Label liveLabel;
    private final Label noProcLabel;
    private final ListView<OperationRecord> procList;
    private final ScrollBar procBar;
    private final List<HardwareRow> hardwareRows = new ArrayList<>();
    private final List<Label> hwKeys = new ArrayList<>();
    private final List<Label> hwValues = new ArrayList<>();
    private final Label nodesHeader;
    private final MapCanvas map;
    private final Label noLogLabel;
    private final ListView<OperationRecord> logList;
    private final ScrollBar logBar;
    private final Label statsHeader;
    private final Label noStatsLabel;
    private final ColumnHeader statsColumns;
    private final ListView<NetworkManagerPayload.TypeStat> statsList;
    private final Popup detailPopup;
    private final Label detailType;
    private final Label detailName;
    private final Label detailAmount;
    private final Label detailSection;
    private final Label detailTiming;
    private final ListView<DetailRow> detailList;
    private final Button detailClose;
    private final Label detailPrioLabel;
    private final Button detailPrioDown;
    private final Label detailPrioValue;
    private final Button detailPrioUp;
    private final Button detailCancel;

    public NetworkManagerApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;

        tabs = root.add(new TabStrip(TABS).fitToLabels(14).setOnSelect(this::selectTab));
        loadingLabel = root.add(new Label("Loading network...", Label.Tone.DIM));
        netLabel = root.add(new Label(this::networkText, Label.Tone.DIM));

        devColumns = root.add(new ColumnHeader(List.of("NODE", "TYPE", "STATUS")).setSortable(false));
        devList = root.add(new ListView<NetworkNodeInfo>(this::nodes, DEV_ROW_H, this::renderDeviceRow));

        slotsLabel = root.add(new Label(() -> "Craft slots  " + scSlotsUsed + " / " + scSlotsTotal, Label.Tone.DIM));
        liveLabel = root.add(new Label(() -> activeOps.size() + " running", Label.Tone.DIM).setAlign(Label.Align.RIGHT));
        noProcLabel = root.add(new Label("No Operations in flight.", Label.Tone.DIM));
        procList = root.add(new ListView<OperationRecord>(() -> activeOps, PROC_ROW_H, this::renderProcessRow)
                .setOnClick(this::processClicked));
        procBar = root.add(new ScrollBar(() -> Math.max(0, activeOps.size() - procList.visibleRows()), procList::scroll,
                v -> procList.setScroll(v)));

        hardwareRows.add(new HardwareRow("Orchestration capacity", () -> JsTechTheme.fmt(hardware().capacity()) + " it/t"));
        hardwareRows.add(new HardwareRow("Parallel queues", () -> String.valueOf(hardware().queues())));
        hardwareRows.add(new HardwareRow("RAM buffer", () -> JsTechTheme.fmt(hardware().ramBuffer()) + " it"));
        hardwareRows.add(new HardwareRow("Network storage", () -> JsTechTheme.fmt(hardware().storageItems()) + " items"));
        hardwareRows.add(new HardwareRow("Mainframes", () -> countKind(NetworkNodeInfo.KIND_MAINFRAME)));
        hardwareRows.add(new HardwareRow("Servers", () -> countKind(NetworkNodeInfo.KIND_SERVER)));
        hardwareRows.add(new HardwareRow("Subframes", () -> countKind(NetworkNodeInfo.KIND_SUBFRAME)));
        hardwareRows.add(new HardwareRow("Supercomputers", () -> countKind(NetworkNodeInfo.KIND_SUPERCOMPUTER)));
        hardwareRows.add(new HardwareRow("Crafting computers", () -> countKind(NetworkNodeInfo.KIND_CRAFTING)));
        hardwareRows.add(new HardwareRow("Personal computers", () -> countKind(NetworkNodeInfo.KIND_PC)));
        hardwareRows.add(new HardwareRow("Cluster managers", () -> countKind(NetworkNodeInfo.KIND_CLUSTER_MANAGEMENT)));
        for (final HardwareRow row : hardwareRows) {
            hwKeys.add(root.add(new Label(row.key())));
            hwValues.add(root.add(new Label(row.value()).setAlign(Label.Align.RIGHT)));
        }
        nodesHeader = root.add(new Label("NODES", Label.Tone.DIM));

        map = root.add(new MapCanvas());

        noLogLabel = root.add(new Label("No Operations logged yet.", Label.Tone.DIM));
        logList = root.add(new ListView<OperationRecord>(() -> logNewestFirst, LOG_ROW_H, this::renderLogRow)
                .setOnClick(this::logClicked));
        logBar = root.add(new ScrollBar(() -> Math.max(0, logNewestFirst.size() - logList.visibleRows()), logList::scroll,
                v -> logList.setScroll(v)));

        statsHeader = root.add(new Label(this::statsHeaderText, Label.Tone.DIM));
        noStatsLabel = root.add(new Label("No Operations settled in the last hour.", Label.Tone.DIM));
        statsColumns = root.add(new ColumnHeader(List.of("TYPE", "OPS/H", "WAIT", "RUN", "FAIL")).setSortable(false));
        statsList = root.add(new ListView<NetworkManagerPayload.TypeStat>(this::statRows, STAT_ROW_H, this::renderStatRow));

        detailPopup = new Popup("", DETAIL_W, DETAIL_H).setDim(0xB0000000).setLayouter(this::layoutDetail);
        detailType = detailPopup.add(new Label(() -> detailOp == null ? "" : OperationPalette.labelFor(detailOp.type()))
                .setColor(() -> detailOp == null ? 0 : OperationPalette.colorFor(detailOp.type())));
        detailName = detailPopup.add(new Label(() -> detailOp == null ? "" : detailOp.name().getString()));
        detailAmount = detailPopup.add(new Label(this::detailAmountText)
                .setColor(() -> detailOp == null ? 0 : statusColor(detailOp.status())));
        detailSection = detailPopup.add(new Label(this::detailSectionText, Label.Tone.DIM));
        detailTiming = detailPopup.add(new Label(this::detailTimingText, Label.Tone.DIM).setAlign(Label.Align.RIGHT));
        detailList = detailPopup.add(new ListView<DetailRow>(() -> detailRows, 10, this::renderDetailRow));
        detailClose = detailPopup.add(new Button("Close", detailPopup::close));
        // A live Operation can be re-prioritised from its detail; a logged one only shows the level it ran at.
        detailPrioLabel = detailPopup.add(new Label("PRIORITY", Label.Tone.DIM));
        detailPrioDown = detailPopup.add(new Button("<", () -> stepDetailPriority(-1)));
        detailPrioValue = detailPopup.add(new Label(() -> detailOp == null ? "" : detailOp.priority().label())
                .setAlign(Label.Align.CENTER));
        detailPrioUp = detailPopup.add(new Button(">", () -> stepDetailPriority(1)));
        detailCancel = detailPopup.add(new Button("Cancel", this::cancelDetail));

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
            active.refreshLiveDetail();
        }
    }

    /** Keeps an open detail of a live Operation current with the latest snapshot (progress, status, level). */
    private void refreshLiveDetail() {
        if (detailOp == null || !detailOp.hasId() || !detailPopup.isOpen()) {
            return;
        }
        for (final OperationRecord op : activeOps) {
            if (op.id().equals(detailOp.id())) {
                showDetail(op);
                return;
            }
        }
    }

    /** Routes the recent Operations log to the open window; it arrives oldest-first and is shown newest-first. */
    public static void acceptOpsLog(final List<OperationRecord> ops) {
        if (active != null) {
            final List<OperationRecord> reversed = new ArrayList<>(ops);
            java.util.Collections.reverse(reversed);
            active.logNewestFirst = reversed;
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

    @Override
    public String title() {
        return "Network Manager";
    }

    @Override
    public int defaultWidth() {
        return 300;
    }

    @Override
    public int defaultHeight() {
        return 196;
    }

    @Override
    public int minWidth() {
        return 250;
    }

    @Override
    public int minHeight() {
        return 150;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        active = this;
    }

    // ---- state readers ----

    private List<NetworkNodeInfo> nodes() {
        return data == null ? List.of() : data.nodes();
    }

    private NetworkManagerPayload.Hardware hardware() {
        return data == null ? NetworkManagerPayload.Hardware.EMPTY : data.hardware();
    }

    private NetworkManagerPayload.Statistics statistics() {
        return data == null ? NetworkManagerPayload.Statistics.EMPTY : data.statistics();
    }

    private List<NetworkManagerPayload.TypeStat> statRows() {
        return statistics().types();
    }

    private String statsHeaderText() {
        final NetworkManagerPayload.Statistics stats = statistics();
        return "Last hour: " + JsTechTheme.fmt(stats.movedLastHour()) + " items moved   -   peak "
                + stats.peakConcurrent() + " in flight today";
    }

    /** Ticks as a short duration: whole seconds past a minute's worth, else ticks. */
    private static String ticksLabel(final int ticks) {
        return ticks >= 1200 ? (ticks / 20) + "s" : ticks + "t";
    }

    private String networkText() {
        if (data == null) {
            return "";
        }
        return "Network " + (data.networkId().isEmpty() ? "(none)" : data.networkId()) + "   -   " + data.nodes().size() + " node(s)";
    }

    private String countKind(final int kind) {
        int n = 0;
        for (final NetworkNodeInfo node : nodes()) {
            if (node.kind() == kind) {
                n++;
            }
        }
        return String.valueOf(n);
    }

    private void selectTab(final int target) {
        tab = target;
        detailPopup.close();
        if (target == TAB_PROCESSES || target == TAB_LOG) {
            requestOps();
        }
        if (target == TAB_STATS) {
            PacketDistributor.sendToServer(new RequestNetworkManagerPayload(host));
        }
    }

    // ---- rendering ----

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        lastX = x;
        lastY = y;
        lastW = width;
        lastH = height;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastFont = font;
        frame++;
        if ((tab == TAB_PROCESSES || tab == TAB_LOG) && frame % OPS_REFRESH_FRAMES == 0) {
            requestOps();
        }
        if (tab == TAB_STATS && frame % STATS_REFRESH_FRAMES == 0) {
            PacketDistributor.sendToServer(new RequestNetworkManagerPayload(host));
        }
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());
        layout(x, y, width, height);
        root.render(g, ctx);
        if (devList.visible()) {
            // Column separators, so each column reads as its own lane.
            for (int i = 1; i < 3; i++) {
                final int sx = devColumns.columnX(i) - 5;
                g.fill(sx, devColumns.y(), sx + 1, devList.y() + Math.min(nodes().size(), devList.visibleRows()) * DEV_ROW_H,
                        skin.edge());
            }
        }
    }

    /** Places the tab's components from the content rectangle; the other tabs' components are hidden. */
    private void layout(final int x, final int y, final int width, final int height) {
        tabs.setBounds(x + 2, y + 2, width - 4, 15);
        tabs.setSelected(tab);
        final int px = x + 6;
        final int py = y + 22;
        final int pw = width - 12;
        final int ph = height - 26;
        final boolean ready = data != null;
        loadingLabel.setVisible(!ready);
        loadingLabel.setBounds(px, py + 4, pw, 8);
        netLabel.setVisible(ready);
        netLabel.setBounds(px, py, pw, 8);
        final int top = py + 12;
        final int h = ph - 12;

        final boolean devices = ready && tab == TAB_DEVICES;
        devColumns.setVisible(devices);
        devList.setVisible(devices);
        if (devices) {
            final int typeX = px + (int) (pw * 0.52);
            final int statusX = px + pw - 48;
            devColumns.setBounds(px, top - 1, pw, 12);
            devColumns.setColumnX(px + 10, typeX, statusX);
            devList.setBounds(px, top + 11, pw, Math.max(DEV_ROW_H, h - 11));
        }

        final boolean processes = ready && tab == TAB_PROCESSES;
        slotsLabel.setVisible(processes);
        liveLabel.setVisible(processes);
        noProcLabel.setVisible(processes && activeOps.isEmpty());
        procList.setVisible(processes && !activeOps.isEmpty());
        procBar.setVisible(procList.visible() && activeOps.size() > procList.visibleRows());
        if (processes) {
            slotsLabel.setBounds(px + 2, top, pw / 2, 8);
            liveLabel.setBounds(px + pw / 2, top, pw / 2, 8);
            noProcLabel.setBounds(px + 2, top + 14, pw, 8);
            final int listW = pw - BAR_W;
            procList.setBounds(px, top + 12, listW, Math.max(PROC_ROW_H, h - 12));
            procBar.setBounds(px + pw - BAR_W, top + 12, BAR_W, procList.visibleRows() * PROC_ROW_H);
        }

        final boolean hardware = ready && tab == TAB_HARDWARE;
        nodesHeader.setVisible(hardware);
        int row = top + 2;
        for (int i = 0; i < hardwareRows.size(); i++) {
            if (i == 4) {
                // A rule and a heading split the totals from the node counts.
                row += 6;
                nodesHeader.setBounds(px + 2, row + 7, pw, 8);
                row += 19;
            }
            hwKeys.get(i).setVisible(hardware);
            hwValues.get(i).setVisible(hardware);
            hwKeys.get(i).setBounds(px + 2, row, pw / 2, 8);
            hwValues.get(i).setBounds(px + pw / 2, row, pw / 2, 8);
            row += HW_ROW_H;
        }

        map.setVisible(ready && tab == TAB_MAP);
        map.setBounds(px, top, pw, h);

        final boolean log = ready && tab == TAB_LOG;
        noLogLabel.setVisible(log && logNewestFirst.isEmpty());
        logList.setVisible(log && !logNewestFirst.isEmpty());
        logBar.setVisible(logList.visible() && logNewestFirst.size() > logList.visibleRows());
        if (log) {
            noLogLabel.setBounds(px + 2, top + 2, pw, 8);
            logList.setBounds(px, top, pw - BAR_W, Math.max(LOG_ROW_H, h));
            logBar.setBounds(px + pw - BAR_W, top, BAR_W, logList.visibleRows() * LOG_ROW_H);
        }

        final boolean stats = ready && tab == TAB_STATS;
        statsHeader.setVisible(stats);
        noStatsLabel.setVisible(stats && statRows().isEmpty());
        statsColumns.setVisible(stats && !statRows().isEmpty());
        statsList.setVisible(stats && !statRows().isEmpty());
        if (stats) {
            statsHeader.setBounds(px + 2, top, pw, 8);
            noStatsLabel.setBounds(px + 2, top + 14, pw, 8);
            statsColumns.setBounds(px, top + 11, pw, 12);
            // Five lanes: the type takes the left third, the four figures share the rest, right-aligned.
            final int lane = (pw - pw / 3) / 4;
            final int figuresX = px + pw / 3;
            statsColumns.setColumnX(px + 4, figuresX, figuresX + lane, figuresX + 2 * lane, figuresX + 3 * lane);
            statsList.setBounds(px, top + 23, pw, Math.max(STAT_ROW_H, h - 23));
        }
    }

    private void renderStatRow(final GuiGraphics g, final UiContext ctx, final NetworkManagerPayload.TypeStat stat,
                               final int index, final int x, final int y, final int w, final int h,
                               final boolean hovered, final boolean selected) {
        final Font font = ctx.font();
        ctx.skin().listRow(g, x, y, w, h, hovered, false);
        final byte type = stat.type();
        g.drawString(font, OperationPalette.labelFor(type), statsColumns.columnX(0), y + 2,
                OperationPalette.colorFor(type), false);
        final String[] figures = {
                String.valueOf(stat.count()),
                ticksLabel(stat.averageWait()),
                ticksLabel(stat.averageRun()),
                stat.shortfallPercent() + "%"};
        for (int i = 0; i < figures.length; i++) {
            // Each figure sits right-aligned in its lane, so the columns read as a table.
            final int laneRight = i == figures.length - 1 ? x + w - 4 : statsColumns.columnX(i + 2) - 6;
            final int color = i == 3 && stat.shortfallPercent() > 0 ? C_AMBER : ctx.skin().text();
            g.drawString(font, figures[i], laneRight - font.width(figures[i]), y + 2, color, false);
        }
    }

    private void renderDeviceRow(final GuiGraphics g, final UiContext ctx, final NetworkNodeInfo n, final int index,
                                 final int x, final int y, final int w, final int h, final boolean hovered,
                                 final boolean selected) {
        final Font font = ctx.font();
        ctx.skin().listRow(g, x, y, w, h, hovered, false);
        g.fill(x + 2, y + 4, x + 6, y + 8, kindColor(n.kind()));
        final int nameX = devColumns.columnX(0);
        final int typeX = devColumns.columnX(1);
        final int statusX = devColumns.columnX(2);
        final boolean named = !n.name().isEmpty();
        final String nm = named ? n.name() : "unnamed";
        final String nmClipped = Texts.clip(font, nm, typeX - 6 - nameX - font.width(n.id()) - 4);
        g.drawString(font, nmClipped, nameX, y + 2, named ? ctx.skin().text() : ctx.skin().dim(), false);
        g.drawString(font, n.id(), nameX + font.width(nmClipped) + 4, y + 2, ctx.skin().dim(), false);
        g.drawString(font, Texts.clip(font, n.kindLabel(), statusX - 6 - typeX), typeX, y + 2, ctx.skin().dim(), false);
        final String status = n.online() ? "online" : "offline";
        g.drawString(font, status, x + w - font.width(status), y + 2, n.online() ? C_GREEN : ctx.skin().dim(), false);
    }

    private void renderProcessRow(final GuiGraphics g, final UiContext ctx, final OperationRecord op, final int index,
                                  final int x, final int y, final int w, final int h, final boolean hovered,
                                  final boolean selected) {
        final Font font = ctx.font();
        ctx.skin().listRow(g, x, y, w, h, hovered, false);
        final String type = OperationPalette.labelFor(op.type());
        g.drawString(font, type, x + 4, y + 3, OperationPalette.colorFor(op.type()), false);
        int nameX = x + 4 + font.width(type) + 4;
        // A level other than the default is worth a tag: raised in amber, lowered dimmed.
        if (op.priority() != OperationPriority.DEFAULT) {
            final String tag = op.priority().label();
            g.drawString(font, tag, nameX, y + 3,
                    op.priority().ordinal() > OperationPriority.DEFAULT.ordinal() ? C_AMBER : ctx.skin().dim(), false);
            nameX += font.width(tag) + 4;
        }
        final int barX = x + w / 2 + 4;
        g.drawString(font, Texts.clip(font, op.name().getString(), barX - nameX - 4), nameX, y + 3, ctx.skin().text(), false);
        final int barLen = w / 2 - 40;
        final double frac = op.requested() > 0 ? Math.min(1.0, (double) op.moved() / op.requested()) : 0.0;
        g.fill(barX, y + 4, barX + barLen, y + h - 3, ctx.skin().fieldBg());
        g.fill(barX, y + 4, barX + (int) (barLen * frac), y + h - 3, statusColor(op.status()));
        final String st = statusLabel(op.status());
        g.drawString(font, st, x + w - font.width(st), y + 3, statusColor(op.status()), false);
    }

    private void renderLogRow(final GuiGraphics g, final UiContext ctx, final OperationRecord op, final int index,
                              final int x, final int y, final int w, final int h, final boolean hovered,
                              final boolean selected) {
        final Font font = ctx.font();
        ctx.skin().listRow(g, x, y, w, h, hovered, false);
        final String type = OperationPalette.labelFor(op.type());
        g.drawString(font, type, x + 4, y + 2, OperationPalette.colorFor(op.type()), false);
        final int nameX = x + 4 + font.width(type) + 4;
        final String st = statusLabel(op.status());
        g.drawString(font, Texts.clip(font, op.name().getString(), w - (nameX - x) - font.width(st) - 8), nameX, y + 2,
                ctx.skin().text(), false);
        g.drawString(font, st, x + w - font.width(st), y + 2, statusColor(op.status()), false);
    }

    private void logClicked(final int index, final int button, final double mx, final double my) {
        if (button != 0 || index < 0 || index >= logNewestFirst.size()) {
            return;
        }
        openDetail(logNewestFirst.get(index));
    }

    private void processClicked(final int index, final int button, final double mx, final double my) {
        if (button != 0 || index < 0 || index >= activeOps.size()) {
            return;
        }
        openDetail(activeOps.get(index));
    }

    /** Whether the detail shows an Operation still in flight (its level can be changed). */
    private boolean detailIsLive() {
        if (detailOp == null || !detailOp.hasId()) {
            return false;
        }
        for (final OperationRecord op : activeOps) {
            if (op.id().equals(detailOp.id())) {
                return true;
            }
        }
        return false;
    }

    private void stepDetailPriority(final int direction) {
        if (!detailIsLive()) {
            return;
        }
        final OperationPriority next = direction > 0 ? detailOp.priority().raise() : detailOp.priority().lower();
        if (next == detailOp.priority()) {
            return;
        }
        // Show the new level at once; the server's next live snapshot confirms it (or reverts it if the
        // Operation settled in the meantime).
        detailOp = detailOp.withPriority(next);
        PacketDistributor.sendToServer(new SetOperationPriorityPayload(host, monitorPos, detailOp.id(), next));
    }

    /** Stops the Operation the detail shows; the next snapshot lists it in the log as DISCARDED. */
    private void cancelDetail() {
        if (!detailIsLive()) {
            return;
        }
        PacketDistributor.sendToServer(new CancelOperationPayload(host, monitorPos, detailOp.id()));
        detailPopup.close();
    }

    // ---- the map ----

    /**
     * The topology as a canvas: the Mainframe at the centre, the other nodes ringed around it, each a click
     * target that can be dragged apart from its neighbours; the middle button pans and the wheel zooms.
     */
    private final class MapCanvas extends UiComponent {

        @Override
        public void render(final GuiGraphics g, final UiContext ctx) {
            mapNodes.clear();
            final Font font = ctx.font();
            final int x = x();
            final int y = y();
            final int w = width();
            final int h = height();
            g.fill(x, y, x + w, y + h, ctx.skin().fieldBg());
            Draw.outline(g, x, y, w, h, ctx.skin().edge());
            g.drawString(font, "drag nodes  -  middle-drag to pan  -  wheel to zoom", x + 4, y + h - 10, ctx.skin().dim(), false);
            final List<NetworkNodeInfo> nodes = nodes();
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
                drawLink(g, mcx, mcy, nx, ny);
                node(g, font, ctx, nx, ny, n);
                placed++;
            }
            if (mainframe >= 0) {
                node(g, font, ctx, mcx, mcy, nodes.get(mainframe));
            }
        }

        private void node(final GuiGraphics g, final Font font, final UiContext ctx, final int cx, final int cy,
                          final NetworkNodeInfo n) {
            final String label = n.displayName();
            final int tw = font.width(label) + 8;
            final int nx = cx - tw / 2;
            final int ny = cy - 6;
            g.fill(nx, ny, nx + tw, ny + 13, ctx.skin().windowBg());
            Draw.outline(g, nx, ny, tw, 13, kindColor(n.kind()));
            g.drawString(font, label, nx + 4, ny + 3, ctx.skin().text(), false);
            mapNodes.add(new NodeRect(nx, ny, tw, 13, n));
        }

        /** A thin link drawn as a horizontal leg then a vertical leg (the rect drawer has no diagonals). */
        private void drawLink(final GuiGraphics g, final int x1, final int y1, final int x2, final int y2) {
            g.fill(Math.min(x1, x2), y1, Math.max(x1, x2), y1 + 1, C_LINK);
            g.fill(x2, Math.min(y1, y2), x2 + 1, Math.max(y1, y2), C_LINK);
        }

        @Override
        public boolean mouseClicked(final double mx, final double my, final int button) {
            lastDragX = mx;
            lastDragY = my;
            if (button == 2) {
                panning = true;
                return true;
            }
            if (button != 0) {
                return false;
            }
            // Pressing a node arms a drag so the player can pull crowded nodes apart.
            for (final NodeRect r : mapNodes) {
                if (r.contains(mx, my)) {
                    draggingNode = r.node().id();
                    break;
                }
            }
            return true;
        }

        @Override
        public boolean mouseDragged(final double mx, final double my, final int button) {
            if (panning) {
                mapPanX += (int) Math.round(mx - lastDragX);
                mapPanY += (int) Math.round(my - lastDragY);
            } else if (draggingNode != null) {
                final int[] off = nodeOffsets.computeIfAbsent(draggingNode, k -> new int[2]);
                off[0] += (int) Math.round(mx - lastDragX);
                off[1] += (int) Math.round(my - lastDragY);
            } else {
                return false;
            }
            lastDragX = mx;
            lastDragY = my;
            return true;
        }

        @Override
        public boolean mouseReleased(final double mx, final double my, final int button) {
            panning = false;
            draggingNode = null;
            return true;
        }

        @Override
        public boolean mouseScrolled(final double mx, final double my, final double delta) {
            final double factor = delta > 0 ? 1.1 : 1.0 / 1.1;
            mapZoom = Math.max(MAP_ZOOM_MIN, Math.min(MAP_ZOOM_MAX, mapZoom * factor));
            return true;
        }
    }

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY) {
        if (tab != TAB_MAP || detailPopup.isOpen()) {
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
            lines.add("CPU " + cpuClock(n.cpuMhz()) + (n.vramMb() > 0 ? "   VRAM " + JsTechTheme.fmt(n.vramMb()) + " MB" : ""));
        }
        if (!n.osLabel().isEmpty()) {
            lines.add("OS " + n.osLabel());
        }
        if (n.storageTotalMb() > 0) {
            lines.add("Storage " + JsTechTheme.fmt(n.storageFreeMb()) + " / " + JsTechTheme.fmt(n.storageTotalMb()) + " MB free");
        } else if (n.storageFreeMb() > 0) {
            lines.add("Storage " + JsTechTheme.fmt(n.storageFreeMb()) + " MB free");
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
        Draw.outline(g, bx, by, boxW, boxH, kindColor(n.kind()));
        int ly = by + 3;
        for (int i = 0; i < lines.size(); i++) {
            final int color = i == 0 ? 0xFFFFFFFF : (i == 1 ? kindColor(n.kind()) : 0xFFB7BCCB);
            g.drawString(font, lines.get(i), bx + 4, ly, color, false);
            ly += 10;
        }
        g.pose().popPose();
    }

    // ---- the detail dialog (a logged Operation and its sub-operations) ----

    private void openDetail(final OperationRecord op) {
        showDetail(op);
        detailList.setScroll(0);
        detailPopup.open();
        detailPopup.placeIn(lastX, lastY, lastW, lastH);
    }

    /** Points the detail at {@code op} and rebuilds its rows, leaving the dialog's scroll and place alone. */
    private void showDetail(final OperationRecord op) {
        detailOp = op;
        final List<DetailRow> rows = new ArrayList<>();
        if (!op.subs().isEmpty()) {
            for (final OperationRecord.SubRow s : op.subs()) {
                rows.add(new DetailRow(s.server(), s.moved() + "/" + s.planned(), subStateColor(s.state())));
            }
        } else {
            for (final OperationRecord.MoveRow m : op.moves()) {
                rows.add(new DetailRow(m.from() + " -> " + m.to(), JsTechTheme.fmt(m.qty()), skin.dim()));
            }
        }
        detailRows = rows;
    }

    private String detailAmountText() {
        if (detailOp == null) {
            return "";
        }
        final String reqLabel = detailOp.requested() >= 1_000_000_000L ? "all" : JsTechTheme.fmt(detailOp.requested());
        return JsTechTheme.fmt(detailOp.moved()) + " of " + reqLabel + "   " + statusLabel(detailOp.status());
    }

    /** How long the Operation waited and ran, on the section row's right; blank before its first tick. */
    private String detailTimingText() {
        if (detailOp == null || detailOp.waitedTicks() + detailOp.ranTicks() == 0) {
            return "";
        }
        return "waited " + ticksLabel(detailOp.waitedTicks()) + ", ran " + ticksLabel(detailOp.ranTicks());
    }

    private String detailSectionText() {
        if (detailOp == null) {
            return "";
        }
        return !detailOp.subs().isEmpty() ? "STAGES" : !detailOp.moves().isEmpty() ? "SOURCES" : "No sub-operations.";
    }

    private void layoutDetail(final Popup p) {
        final int typeW = lastFont == null ? 30 : lastFont.width(detailType.text());
        detailType.setBounds(p.x() + 6, p.y() + 6, typeW, 8);
        detailName.setBounds(p.x() + 6 + typeW + 4, p.y() + 6, p.width() - 16 - typeW, 8);
        // The level sits at the right end of the amount row: a label, then < value > for a live Operation.
        final boolean live = detailIsLive();
        final int prioRight = p.right() - 6;
        final int prioW = live ? 12 + 34 + 12 : 34;
        final int prioLabelW = lastFont == null ? 40 : lastFont.width("PRIORITY") + 4;
        detailAmount.setBounds(p.x() + 6, p.y() + 18, p.width() - 12 - prioW - prioLabelW - 4, 8);
        detailPrioLabel.setBounds(prioRight - prioW - prioLabelW, p.y() + 18, prioLabelW, 8);
        detailPrioDown.setBounds(prioRight - prioW, p.y() + 16, 12, 11);
        detailPrioValue.setBounds(prioRight - (live ? 46 : 34), p.y() + 18, 34, 8);
        detailPrioUp.setBounds(prioRight - 12, p.y() + 16, 12, 11);
        detailPrioDown.setVisible(live);
        detailPrioUp.setVisible(live);
        detailSection.setBounds(p.x() + 6, p.y() + 34, p.width() / 2, 8);
        detailTiming.setBounds(p.x() + p.width() / 2, p.y() + 34, p.width() / 2 - 6, 8);
        detailList.setBounds(p.x() + 8, p.y() + 45, p.width() - 14, Math.max(10, p.height() - 45 - 24));
        final int cw = (lastFont == null ? 30 : lastFont.width("Close")) + 12;
        detailClose.setBounds(p.right() - cw - 4, p.bottom() - 15, cw, 13);
        final int xw = (lastFont == null ? 36 : lastFont.width("Cancel")) + 12;
        detailCancel.setBounds(p.x() + 6, p.bottom() - 15, xw, 13);
        detailCancel.setVisible(live);
    }

    private void renderDetailRow(final GuiGraphics g, final UiContext ctx, final DetailRow row, final int index,
                                 final int x, final int y, final int w, final int h, final boolean hovered,
                                 final boolean selected) {
        final Font font = ctx.font();
        final int rightW = font.width(row.right());
        g.drawString(font, Texts.clip(font, row.left(), w - rightW - 6), x, y, ctx.skin().text(), false);
        g.drawString(font, row.right(), x + w - rightW, y, row.color(), false);
    }

    @Override
    public boolean modalActive() {
        return detailPopup.isOpen();
    }

    @Override
    public void renderModal(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final int height, final int mouseX, final int mouseY) {
        if (!detailPopup.isOpen()) {
            return;
        }
        detailPopup.renderIn(g, new UiContext(skin, font, mouseX, mouseY, 0f), x, y, width, height);
        // The rule between the header and the stages.
        g.fill(detailPopup.x() + 6, detailPopup.y() + 30, detailPopup.right() - 6, detailPopup.y() + 31, skin.edge());
    }

    private int subStateColor(final byte state) {
        return switch (state) {
            case OperationRecord.SubRow.SUB_COMPLETED, OperationRecord.SubRow.SUB_STREAMING -> C_GREEN;
            case OperationRecord.SubRow.SUB_READING -> C_AMBER;
            default -> skin.dim();
        };
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
            case OperationRecord.STATUS_PARTIAL, OperationRecord.STATUS_WAITING, OperationRecord.STATUS_PENDING -> C_AMBER;
            case OperationRecord.STATUS_FAILED, OperationRecord.STATUS_RESOURCE_LOCKED, OperationRecord.STATUS_DISCARDED -> C_RED;
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

    // ---- input ----

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (detailPopup.isOpen()) {
            detailPopup.mouseClicked(mouseX, mouseY, button);
            return;
        }
        root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (detailPopup.isOpen()) {
            detailPopup.mouseDragged(mouseX, mouseY, button);
        } else {
            root.mouseDragged(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (detailPopup.isOpen()) {
            detailPopup.mouseReleased(mouseX, mouseY, button);
        } else {
            root.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        if (detailPopup.isOpen()) {
            return detailPopup.mouseScrolled(lastMouseX, lastMouseY, delta);
        }
        if (root.mouseScrolled(lastMouseX, lastMouseY, delta)) {
            return true;
        }
        // The wheel anywhere in the window moves the tab's list.
        final int step = delta > 0 ? -1 : 1;
        if (tab == TAB_LOG) {
            logList.setScroll(logList.scroll() + step);
            return true;
        }
        if (tab == TAB_PROCESSES) {
            procList.setScroll(procList.scroll() + step);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        return detailPopup.isOpen() && detailPopup.keyPressed(key, scanCode, modifiers);
    }
}
