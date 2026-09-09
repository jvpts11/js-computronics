/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.gui.layout.NetworkInteractorLayout;
import dev.jstech.computers.operation.payload.CraftCatalogPayload;
import dev.jstech.computers.operation.payload.CraftPlanPayload;
import dev.jstech.computers.operation.payload.CraftPlanRequestPayload;
import dev.jstech.computers.operation.payload.CraftSubmitPayload;
import dev.jstech.computers.operation.payload.DesktopShellOutputPayload;
import dev.jstech.computers.operation.payload.DesktopShellRunPayload;
import dev.jstech.computers.operation.payload.NetworkInteractorPayload;
import dev.jstech.computers.operation.payload.NetworkItemEntry;
import dev.jstech.computers.operation.payload.NetworkServersPayload;
import dev.jstech.computers.operation.payload.NiDepositPayload;
import dev.jstech.computers.operation.payload.NiGridClickPayload;
import dev.jstech.computers.operation.payload.NiSelectPayload;
import dev.jstech.computers.operation.payload.NiShiftInsertPayload;
import dev.jstech.computers.operation.payload.OperationRecord;
import dev.jstech.computers.operation.payload.RequestNetworkInteractorPayload;
import dev.jstech.computers.operation.payload.RequestNiOperationsPayload;
import dev.jstech.computers.operation.payload.RequestNiServersPayload;
import dev.jstech.computers.program.OperationPalette;
import dev.jstech.computers.program.cli.CliStyle;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.CellGrid;
import dev.jstech.core.client.gui.component.Checkbox;
import dev.jstech.core.client.gui.component.CommandLine;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.Popup;
import dev.jstech.core.client.gui.component.ScrollBar;
import dev.jstech.core.client.gui.component.SearchField;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.Texts;
import dev.jstech.core.client.gui.component.UiComponent;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.operation.OperationPriority;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The Network Interactor desktop window: the graphical face of the data network for a Frames computer,
 * with the same capabilities as the MC-NET terminal: extract from the network into local storage,
 * withdraw local storage into the inventory, deposit/insert from the player's hotbar, and request
 * crafts, addressing the host by position. The player's full inventory is shown in the window as real
 * container slots inside a fixed, framed band pinned just above the footer: the desktop menu owns the 36
 * slots and the desktop screen positions them over that band, so the vanilla container drives the cursor,
 * drag, and shift-click. This app paints the inventory frame and slot backgrounds; the screen renders the
 * items and cursor on top. The item grid above the band scrolls its items when there are more than fit. An
 * embedded console still runs ad-hoc operations through the same path as the Shell.
 *
 * <p>The content is a tree of the core's components laid out every frame from the pure layout's zones, so
 * the drawn cells, the real container slots and the hit-tests agree at every size.
 */
public final class NetworkInteractorApp implements IInventoryBandApp {

    // Labels kept short so all five tabs fit the strip; "Local"/"Network" abbreviate the longer mock names.
    private static final List<String> TABS = List.of("Status", "Local", "Network", "Crafting", "Operations");
    private static final int TAB_STATUS = 0;
    private static final int TAB_LOCAL = 1;
    private static final int TAB_NETWORK = 2;
    private static final int TAB_CRAFTING = 3;
    private static final int TAB_OPS = 4;

    /*
     * Layout constants and zone math live in the pure NetworkInteractorLayout, shared with the desktop
     * screen so the drawn cells, the real container slots, and the hit-tests all agree at every size.
     */
    private static final int TAB_H = NetworkInteractorLayout.TAB_H;
    private static final int SEARCH_H = NetworkInteractorLayout.SEARCH_H;
    private static final int CELL = NetworkInteractorLayout.CELL;
    private static final int INV_COLS = NetworkInteractorLayout.INV_COLS;
    private static final int INV_ROWS = NetworkInteractorLayout.INV_ROWS;

    private static final int SORT_MODES = 3;
    private static final String[] SORT_LABELS = {"A-Z", "MOST", "LEAST"};
    private static final int OP_ROW_H = 12;
    private static final int ONLINE_GREEN = 0xFF2E8B45;
    private static final int OFFLINE_RED = 0xFF9A4A4A;
    private static final int AMBER = 0xFFE6A93A;
    private static final int LINK_BLUE = 0xFF2F6AC6;
    private static final int SCROLLBAR_W = 3;

    /*
     * Request/storage popup (MC-NET style): clicking an item with an empty cursor opens a quantity dialog
     * instead of extracting a fixed amount.
     */
    private static final int[] POPUP_STEPS = {-1000, -100, -10, -1, 1, 10, 100, 1000};
    private static final int POPUP_W = 188;
    private static final int POPUP_H = 100;
    private static final int POPUP_H_ADV = 172;
    private static final int ADV_ROWS = 4; // visible PULL-FROM rows
    private static final long MAX_TYPED_QTY = 999_999_999L;

    // Craft popup (MC-NET style): a quantity dialog + a live plan (need/have) before crafting.
    private static final int CRAFT_W = 196;
    private static final int CRAFT_H = 150;
    private static final int[] CRAFT_STEPS = {-64, -1, 1, 64};
    private static final long MAX_CRAFT_QTY = 99_999L;

    /*
     * The last tab the player viewed, kept across reopens (reopening the computer or the monitor) so the NI
     * comes back to where they left it instead of snapping to Network every time.
     */
    private static int lastTab = TAB_NETWORK;

    private static NetworkInteractorApp active;

    private record Line(String text, int color) {
    }

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    private int tab = lastTab;

    private final List<NetworkItemEntry> networkItems = new ArrayList<>();
    private final List<NetworkItemEntry> localItems = new ArrayList<>();
    private final List<CraftCatalogPayload.Entry> crafts = new ArrayList<>();
    private boolean online;
    private long usedItems;
    private int serverCount;

    /** How the grid is ordered: 0 by name, 1 most stored first, 2 least stored first. */
    private int sortMode;
    /*
     * The grid's filtered and sorted view, kept between frames: cells, tooltip and hit-tests all ask for it
     * several times a frame, and re-sorting thousands of entries each time was a frame-rate cost.
     */
    private List<NetworkItemEntry> filteredCache = List.of();
    private List<NetworkItemEntry> filteredSource = List.of();
    private String filteredKey = "";
    private List<CraftCatalogPayload.Entry> craftsCache = List.of();
    private String craftsKey = "";
    /** Bumped whenever a snapshot replaces the lists, so a stale filtered view is never shown. */
    private int listVersion;

    private final Deque<Line> output = new ArrayDeque<>();
    private int contentW = 280;
    private int contentH = 188;
    // Geometry of the last frame: where the content sits on the desktop and where the cursor was.
    private int lastX;
    private int lastY;
    private int lastMouseX;
    private int lastMouseY;
    /*
     * Frames since the last live refresh: the Network Interactor re-asks the server for the storage grid and,
     * on the Operations tab, the live operations a few times a second, so stock and craft progress move on their
     * own instead of only when a command is run.
     */
    private int refreshFrames;

    // The request/storage dialog's state; popupEntry is null while it is closed.
    @Nullable
    private NetworkItemEntry popupEntry;
    private long popupQty;
    private boolean popupStorage; // true: Storage-tab popup (TO INVENTORY / TO NETWORK); false: Network REQUEST
    private boolean popupAdvanced;
    private final List<NetworkServersPayload.ServerEntry> servers = new ArrayList<>();
    private final Set<String> popupDeselected = new HashSet<>(); // source server keys turned OFF
    private int popupDestIndex; // 0 = this computer; 1.. = servers.get(i-1)
    private OperationPriority popupPriority = OperationPriority.DEFAULT;

    // The craft dialog's state; craftEntry is null while it is closed.
    @Nullable
    private CraftCatalogPayload.Entry craftEntry;
    private long craftQty = 1;
    /*
     * When the item in the craft popup can be made BOTH by a multi-stage pipeline and by composing its flat
     * patterns, this toggle chooses: true runs the pipeline, false lets the recursive planner build the tree.
     */
    private boolean craftMulti = true;
    private OperationPriority craftPriority = OperationPriority.DEFAULT;
    @Nullable
    private CraftPlanPayload craftPlan;

    // Operations tab (network task manager): the network's recent log and live in-flight Operations.
    private final List<OperationRecord> recentOps = new ArrayList<>();
    private final List<OperationRecord> activeOps = new ArrayList<>();
    private int opSelected = -1;
    // Parallel craft-slot capacity from the network's online supercomputers (used / total), shown in this tab.
    private int scSlotsUsed;
    private int scSlotsTotal;

    // The content tree.
    private final Panel root = new Panel();
    private final TabStrip tabs;
    private final SearchField search;
    private final Button sortButton;
    private final CellGrid grid;
    private final ScrollBar gridBar;
    private final ListView<OperationRecord> opList;
    private final Label usageLabel;
    private final Label statusLabel;
    private final CommandLine console;
    private final RequestPopup requestPopup;
    private final CraftPopup craftPopup;

    public NetworkInteractorApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;

        tabs = root.add(new TabStrip(TABS).fitToLabels(12).setOnSelect(this::selectTab));
        tabs.setSelected(tab);
        search = root.add(new SearchField(48));
        search.setOnEdit(this::searchEdited);
        sortButton = root.add(new Button(this::sortLabel, this::cycleSort));
        grid = root.add(new CellGrid(INV_COLS, 1, 1, CELL)
                .setInset(2)
                .setRenderer(this::renderGridCell)
                .setOnClick(this::gridCellClicked));
        gridBar = root.add(new ScrollBar(grid::maxScroll, grid::scroll, v -> grid.setScroll(v)));
        opList = root.add(new ListView<OperationRecord>(this::allOps, OP_ROW_H, this::renderOpRow).setOnClick(this::opClicked));
        usageLabel = root.add(new Label(this::usageText, Label.Tone.DIM).setAlign(Label.Align.RIGHT));
        statusLabel = root.add(new Label(this::statusText).setColor(this::statusColor));
        console = root.add(new CommandLine(DesktopShellRunPayload.MAX_LEN - 1, this::runCommand)
                .setIdle(this::lastOutputText, this::lastOutputColor));
        root.focus(console);
        requestPopup = new RequestPopup();
        craftPopup = new CraftPopup();

        active = this;
        request();
    }

    /**
     * Marks this window as the active Network Interactor, the one that receives network snapshots and
     * console output. The desktop screen calls this whenever this window becomes the focused one, so the
     * static routing follows focus instead of pointing at the most recently constructed instance.
     */
    public void markActive() {
        active = this;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestNetworkInteractorPayload(host, monitorPos));
        if (tab == TAB_OPS) {
            requestOps();
        }
    }

    /** Asks the server for the network's recent + active Operations (for the Operations tab). */
    private void requestOps() {
        PacketDistributor.sendToServer(new RequestNiOperationsPayload(host, monitorPos));
    }

    // what the server sends

    /** Routes a storage snapshot to the open Network Interactor window. */
    public static void accept(final NetworkInteractorPayload payload) {
        if (active == null) {
            return;
        }
        active.networkItems.clear();
        active.networkItems.addAll(payload.networkItems());
        active.localItems.clear();
        active.localItems.addAll(payload.localItems());
        active.crafts.clear();
        active.crafts.addAll(payload.crafts());
        active.listVersion++;
        active.online = payload.mainframeOnline();
        active.usedItems = payload.usedItems();
        active.serverCount = payload.serverCount();
    }

    /** Routes an embedded-console output reply to the open Network Interactor window. */
    public static void acceptConsole(final DesktopShellOutputPayload payload) {
        if (active == null) {
            return;
        }
        // A reply to another window's line is not this box's to show.
        if (payload.session() != 0 && payload.session() != active.session) {
            return;
        }
        if (payload.clear()) {
            active.output.clear();
        }
        for (final DesktopShellOutputPayload.WireLine line : payload.lines()) {
            active.pushOutput(line.text(), colorOf(line.style()));
        }
        active.request(); // an operation may have changed the network; refresh the grid
    }

    /** Delivers a craft plan (need/have rows) to the open NI's craft popup. */
    public static void acceptCraftPlan(final CraftPlanPayload plan) {
        if (active != null && active.craftEntry != null
                && ItemStack.isSameItemSameComponents(active.craftEntry.result(), plan.result())) {
            active.craftPlan = plan;
        }
    }

    /** Delivers the network's computer list (for the advanced popup) to the open NI. */
    public static void acceptServers(final List<NetworkServersPayload.ServerEntry> list) {
        if (active != null) {
            active.servers.clear();
            active.servers.addAll(list);
            active.requestPopup.rebuildSources();
        }
    }

    /** Delivers the network's recent Operations log to the open NI's Operations tab. */
    public static void acceptOps(final List<OperationRecord> ops) {
        if (active != null) {
            active.recentOps.clear();
            active.recentOps.addAll(ops);
        }
    }

    /** Delivers the network's live (in-flight) Operations and supercomputer slot capacity to the NI's Operations tab. */
    public static void acceptActiveOps(final List<OperationRecord> ops, final int scSlotsUsed, final int scSlotsTotal) {
        if (active != null) {
            active.activeOps.clear();
            active.activeOps.addAll(ops);
            active.scSlotsUsed = scSlotsUsed;
            active.scSlotsTotal = scSlotsTotal;
        }
    }

    private void pushOutput(final String text, final int color) {
        output.addLast(new Line(text, color));
        while (output.size() > 64) {
            output.removeFirst();
        }
    }

    // the window

    @Override
    public String title() {
        return "Network Interactor";
    }

    @Override
    public int defaultWidth() {
        /*
         * Left column (grid + framed inventory) + details panel + gaps, compact and just above minWidth() so the
         * window opens tidy and never below its own minimum (which squashes the content and clips the panel).
         */
        return 330;
    }

    @Override
    public int defaultHeight() {
        return 226;
    }

    @Override
    public int minWidth() {
        return NetworkInteractorLayout.minContentWidth() + 8;
    }

    @Override
    public int minHeight() {
        return NetworkInteractorLayout.minContentHeight() + DesktopWindow.TITLE_H + 8;
    }

    /*
     * Layout: every zone comes from the pure NetworkInteractorLayout, derived from the LIVE content size, so
     * the drawn cells, the real container slots, and the hit-tests agree at any size. The inventory band is a
     * fixed-height panel pinned above the footer; only the grid scrolls (its items, not its pixels).
     */
    private NetworkInteractorLayout.Zones zones() {
        return NetworkInteractorLayout.resolve(contentW, contentH);
    }

    /*
     * Inventory zone, in content-local coordinates (relative to the app content's top-left). The desktop
     * screen reads these to place the menu's 36 inventory slots over this window each frame.
     */

    /** The content-local x of a slot cell's top-left, where the vanilla item is drawn. */
    @Override
    public int invCellContentX(final int col) {
        return zones().invX() + col * CELL;
    }

    /**
     * The content-local y of a slot cell's top-left for the given content height. The inventory band is a
     * fixed-height panel pinned just above the footer, so the row position is derived from the live height,
     * never from a cached field, so the item lines up with its slot background from the very first frame.
     */
    @Override
    public int invCellContentY(final int row, final int contentHeight) {
        return NetworkInteractorLayout.resolve(contentW, contentHeight).invY()
                + NetworkInteractorLayout.rowYOffset(row);
    }

    /**
     * The content-local y just past the bottom row of inventory slots, for the given content height, the
     * desktop screen uses it as the band's lower visibility bound so all 36 slots are always counted visible.
     */
    @Override
    public int invBandBottom(final int contentHeight) {
        return NetworkInteractorLayout.resolve(contentW, contentHeight).invY()
                + NetworkInteractorLayout.rowYOffset(INV_ROWS - 1) + CELL;
    }

    /** Whether a content-local point falls within an inventory slot cell (the framed, always-visible band). */
    private boolean inInventoryZone(final double lx, final double ly) {
        return NetworkInteractorLayout.inventorySlotAt((int) lx, (int) ly, zones()) >= 0;
    }

    private boolean gridTab() {
        return tab == TAB_NETWORK || tab == TAB_LOCAL || tab == TAB_CRAFTING;
    }

    //  Rendering: lay the components out from the zones, draw the rest by hand, then the tree

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        contentW = width;
        contentH = height;
        lastX = x;
        lastY = y;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        /*
         * Keep the view live: a few times a second, re-ask for the storage grid (and the live operations on the
         * Operations tab) so stock counts and craft progress update on their own, without a manual refresh.
         */
        if (++refreshFrames >= 20) {
            refreshFrames = 0;
            request();
        }
        final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(width, height);
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());

        // Tab strip (fixed header).
        tabs.setBounds(x, y, width, TAB_H);
        tabs.setSelected(tab);

        // Search + sort (fixed header, grid tabs only).
        final boolean onGrid = gridTab();
        search.setBounds(x + z.searchX(), y + z.searchY(), z.searchW(), SEARCH_H);
        search.setVisible(onGrid);
        sortButton.setBounds(x + z.sortX(), y + z.searchY(), z.sortW(), SEARCH_H);
        sortButton.setVisible(onGrid);

        /*
         * The grid zone: the tab body between the fixed header and the inventory band. The grid scrolls its
         * ITEMS, not its pixels: the rows shown change.
         */
        final int gridTop = y + z.gridY();
        final int gridRows = Math.max(1, z.gridRows());
        grid.setColumns(z.gridCols()).setVisibleRows(gridRows).setTotalRows(totalItemRows(z)).setCellCount(gridCount());
        grid.place(x + z.gridX(), gridTop);
        grid.setVisible(onGrid && z.gridH() > 0);
        gridBar.setBounds(x + width - SCROLLBAR_W, gridTop, SCROLLBAR_W, z.gridH());
        gridBar.setVisible(onGrid && z.gridH() > 0 && grid.maxScroll() > 0);
        opList.setBounds(x + z.gridX(), gridTop, z.detailsX() - 2 - z.gridX(), Math.max(OP_ROW_H, z.gridH()));
        opList.setVisible(tab == TAB_OPS && z.gridH() > 0 && !allOps().isEmpty());

        // What the body says when there are no cells or rows to show, clipped to the zone.
        if (z.gridH() > 0) {
            Draw.pushScissor(g, x, gridTop, x + width, gridTop + z.gridH());
            switch (tab) {
                case TAB_STATUS -> renderStatus(g, font, x + 4, gridTop + 2);
                case TAB_CRAFTING -> {
                    if (filteredCrafts().isEmpty()) {
                        // Two lines, each wrapped to the grid width so the hint never truncates mid-word.
                        final String msg = !search.query().isEmpty()
                                ? "No crafts match \"" + search.edit() + "\"."
                                : "No patterns on the network. Load .craft files on a Crafting Computer.";
                        drawWrapped(g, font, msg, x + 6, gridTop + 2, width - 12, skin.dim());
                    }
                }
                case TAB_OPS -> renderOpsHeader(g, font, x + z.gridX(), gridTop);
                default -> {
                    if (filtered(tab == TAB_NETWORK ? networkItems : localItems).isEmpty()) {
                        g.drawString(font, "(empty)", x + 6, gridTop + 2, skin.dim(), false);
                    }
                }
            }
            Draw.popScissor(g);
        }

        /*
         * The framed inventory band: a pinned panel with the 36 slot backgrounds; the desktop screen draws
         * the real container items and the cursor over it. Always fully visible, never clipped.
         */
        renderInventoryBand(g, font, x, y, z);

        // Item details panel (right column): the hovered grid item, or the selected Operation on the Ops tab.
        if (tab == TAB_OPS) {
            renderOpDetails(g, font, x, y, z);
        } else {
            renderDetails(g, font, x, y, z, mouseX, mouseY);
        }

        // Status line (fixed footer): usage right-aligned, the left label clipped so it never overruns.
        final int statusY = y + z.statusY();
        final int usageW = font.width(usageText());
        usageLabel.setBounds(x + width - 3 - usageW, statusY + 1, usageW, 8);
        statusLabel.setBounds(x + 3, statusY + 1, width - 3 - usageW - 4 - 3, 8);

        // Embedded console (fixed footer).
        final int cy = y + z.consoleY();
        console.setBounds(x, cy - 1, width, y + height - (cy - 1));

        root.render(g, ctx);
    }

    private void selectTab(final int index) {
        tab = index;
        lastTab = index; // remember it so the next reopen lands here
        root.focus(console);
        grid.setScroll(0);
        if (index == TAB_OPS) {
            opSelected = -1;
            opList.setScroll(0);
            requestOps();
        }
    }

    private void searchEdited() {
        grid.setScroll(0);
    }

    private String sortLabel() {
        // The button always names the order it is in, so the player can see the mode without clicking it.
        return SORT_LABELS[sortMode];
    }

    private void cycleSort() {
        sortMode = (sortMode + 1) % SORT_MODES;
    }

    /** The number of cells the active grid tab has, for the grid to draw and click that many. */
    private int gridCount() {
        return switch (tab) {
            case TAB_NETWORK -> filtered(networkItems).size();
            case TAB_LOCAL -> filtered(localItems).size();
            case TAB_CRAFTING -> filteredCrafts().size();
            default -> 0;
        };
    }

    /** The number of item rows the visible (and scrollable) source has, for the active grid tab. */
    private int totalItemRows(final NetworkInteractorLayout.Zones z) {
        final int cols = Math.max(1, z.gridCols());
        return (gridCount() + cols - 1) / cols;
    }

    private void renderGridCell(final GuiGraphics g, final UiContext ctx, final int index, final int cx, final int cy,
                                final int size, final int cellHeight, final boolean hovered) {
        if (tab == TAB_CRAFTING) {
            final List<CraftCatalogPayload.Entry> list = filteredCrafts();
            if (index >= list.size()) {
                return;
            }
            final CraftCatalogPayload.Entry e = list.get(index);
            DesktopItems.item(g, e.result(), cx + 1, cy + 1);
            // Availability dot (green/amber/red), top-right.
            final int dot = switch (e.availability()) {
                case CraftCatalogPayload.DOT_GREEN -> 0xFF3CC75A;
                case CraftCatalogPayload.DOT_AMBER -> AMBER;
                default -> 0xFFD05050;
            };
            g.fill(cx + size - 4, cy + 1, cx + size, cy + 5, dot);
            return;
        }
        final List<NetworkItemEntry> items = filtered(tab == TAB_NETWORK ? networkItems : localItems);
        if (index >= items.size()) {
            return;
        }
        final NetworkItemEntry e = items.get(index);
        // The count rides just in front of the model, both inside this window's depth band.
        DesktopItems.data(g, ctx.font(), e.key(), cx + 1, cy + 1, formatCount(e.total()));
    }

    private void gridCellClicked(final int index, final int button, final boolean shift) {
        if (tab == TAB_CRAFTING) {
            final List<CraftCatalogPayload.Entry> list = filteredCrafts();
            if (index < list.size()) {
                openCraftPopup(list.get(index));
            }
            return;
        }
        final List<NetworkItemEntry> items = filtered(tab == TAB_NETWORK ? networkItems : localItems);
        if (index < items.size()) {
            // Open the request/storage quantity dialog (MC-NET style) instead of pulling a fixed amount.
            openPopup(items.get(index), tab == TAB_LOCAL);
        }
    }

    private void renderInventoryBand(final GuiGraphics g, final Font font, final int x, final int y,
                                     final NetworkInteractorLayout.Zones z) {
        // The band panel: a raised surface with a bevel, so the inventory reads as a distinct, bounded area.
        final int bx = x + z.invBandX();
        final int by = y + z.invBandY();
        final int bw = z.invBandW();
        final int bh = z.invBandH();
        raisedPanel(g, bx, by, bw, bh);

        // "Inventory" label tucked into the band's top frame, so the panel is clearly the player inventory.
        g.drawString(font, "Inventory", bx + 3, by - 9, skin.dim(), false);

        /*
         * Slot backgrounds inside the frame (the desktop screen draws the real items and cursor over these),
         * using the shared rowYOffset so the 3-rows + gap + hotbar lines up exactly with the real slots.
         */
        for (int r = 0; r < INV_ROWS; r++) {
            for (int c = 0; c < INV_COLS; c++) {
                final int cx = x + z.invX() + c * CELL;
                final int cy = y + z.invY() + NetworkInteractorLayout.rowYOffset(r);
                g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, skin.fieldBg());
                Draw.outline(g, cx, cy, CELL - 2, CELL - 2, skin.edge());
            }
        }
    }

    /** A raised surface: the panel fill with a light top-left and a dark bottom-right edge. */
    private void raisedPanel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + h, skin.panelBg());
        g.fill(x, y, x + w, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, skin.edge());
        g.fill(x + w - 1, y, x + w, y + h, skin.edge());
    }

    // details panel

    /**
     * The right-hand item details panel: the hovered grid item's icon, name, mod, id, weight, durability and
     * network total, filling what used to be empty space.
     */
    private void renderDetails(final GuiGraphics g, final Font font, final int x, final int y,
                               final NetworkInteractorLayout.Zones z, final int mouseX, final int mouseY) {
        final int dx = x + z.detailsX();
        final int dy = y + z.detailsY();
        final int dw = z.detailsW();
        final int dh = z.detailsH();
        if (dw <= 6 || dh <= 6) {
            return;
        }
        raisedPanel(g, dx, dy, dw, dh);
        Draw.pushScissor(g, dx + 1, dy + 1, dx + dw - 1, dy + dh - 1);
        final NetworkItemEntry e = hoveredGridEntry(mouseX, mouseY);
        final int px = dx + 5;
        int py = dy + 5;
        if (e == null) {
            Texts.small(g, font, "Hover an item to", px, py, skin.dim());
            Texts.small(g, font, "see its details.", px, py + 9, skin.dim());
            Draw.popScissor(g);
            return;
        }
        final StorageKey key = e.key();
        final ItemStack stack = e.icon(); // empty for a fluid or a chemical
        final ResourceLocation id = dataId(key);
        DesktopItems.data(g, font, key, px, py, null);
        Texts.small(g, font, Texts.trim(font, e.name().getString(), Texts.smallFits(dw - 28)), px + 20, py, skin.text());
        Texts.small(g, font, Texts.trim(font, modName(id.getNamespace()), Texts.smallFits(dw - 28)), px + 20, py + 9, LINK_BLUE);
        py += 22;
        py = detail(g, font, px, py, dw, "ID", id.toString());
        py = detail(g, font, px, py, dw, "KIND", key.isItem() ? "Item" : key.isFluid() ? "Fluid" : "Chemical");
        py = detail(g, font, px, py, dw, "WEIGHT", dataLabel(key.weight(e.total())));
        if (key.isItem() && stack.isDamageableItem()) {
            py = detail(g, font, px, py, dw, "DURABILITY",
                    (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage());
        }
        py = detailList(g, font, px, py, dw, "STORED", storedLines(e));
        if (key.isItem()) {
            py = detailList(g, font, px, py, dw, "TAGS", itemTags(stack));
            detailList(g, font, px, py, dw, "COMPONENTS", componentNames(stack));
        }
        Draw.popScissor(g);
    }

    /** The STORED section: one line per server/storage that holds the type (label + amount), or a single
     *  network-total line when no per-server breakdown was sent (e.g. the Local Storage tab). */
    private List<String> storedLines(final NetworkItemEntry e) {
        if (e.shares().isEmpty()) {
            return List.of(amount(e.key(), e.total()) + " on the network");
        }
        final List<String> out = new ArrayList<>();
        for (final NetworkItemEntry.StorageShare s : e.shares()) {
            out.add(s.label() + ": " + amount(e.key(), s.qty()));
        }
        return out;
    }

    /** Draws a labelled list section (a dim caption, then each entry on its own line; "(none)" if empty). */
    private int detailList(final GuiGraphics g, final Font font, final int px, final int py, final int dw,
                           final String label, final List<String> values) {
        Texts.small(g, font, label, px, py, skin.dim());
        int vy = py + 8;
        if (values.isEmpty()) {
            Texts.small(g, font, "(none)", px + 2, vy, skin.dim());
            return vy + 10;
        }
        for (final String v : values) {
            Texts.small(g, font, Texts.trim(font, v, Texts.smallFits(dw - 8)), px + 2, vy, skin.text());
            vy += 8;
        }
        return vy + 2;
    }

    /** The item's tags as namespaced ids (sorted), for the details panel TAGS section. */
    private static List<String> itemTags(final ItemStack stack) {
        return stack.getItemHolder().tags()
                .map(t -> t.location().toString())
                .sorted()
                .toList();
    }

    /** The data components present on the stack as namespaced ids (sorted), for the COMPONENTS section. */
    private static List<String> componentNames(final ItemStack stack) {
        final List<String> out = new ArrayList<>();
        for (final TypedDataComponent<?> c : stack.getComponents()) {
            final ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type());
            if (key != null) {
                out.add(key.toString());
            }
        }
        Collections.sort(out);
        return out;
    }

    /** Draws a labelled detail line (a dim caption, then the value wrapped to the panel width). Returns new y. */
    private int detail(final GuiGraphics g, final Font font, final int px, final int py, final int dw,
                       final String label, final String value) {
        Texts.small(g, font, label, px, py, skin.dim());
        int vy = py + 8;
        for (final String line : wrap(font, value, Texts.smallFits(dw - 12))) {
            Texts.small(g, font, line, px + 2, vy, skin.text());
            vy += 8;
        }
        return vy + 2;
    }

    /** Greedy width-based wrap, for ids/values too long for the narrow details panel. */
    private static List<String> wrap(final Font font, final String s, final int maxW) {
        final List<String> out = new ArrayList<>();
        String rest = s;
        while (!rest.isEmpty() && out.size() < 6) {
            int n = rest.length();
            while (n > 1 && font.width(rest.substring(0, n)) > maxW) {
                n--;
            }
            out.add(rest.substring(0, n));
            rest = rest.substring(n);
        }
        return out;
    }

    /** A readable mod name for a namespace (Minecraft for vanilla, otherwise the title-cased namespace). */
    private static String modName(final String ns) {
        if (ns.equals("minecraft")) {
            return "Minecraft";
        }
        return Character.toUpperCase(ns.charAt(0)) + ns.substring(1).replace('_', ' ');
    }

    /** The grid entry under a desktop-local point, or null when the cursor isn't over a grid item. */
    @Nullable
    private NetworkItemEntry hoveredGridEntry(final double mx, final double my) {
        if (tab != TAB_NETWORK && tab != TAB_LOCAL) {
            return null;
        }
        final int idx = grid.cellAt(mx, my);
        if (idx < 0) {
            return null;
        }
        final List<NetworkItemEntry> items = filtered(tab == TAB_NETWORK ? networkItems : localItems);
        return idx < items.size() ? items.get(idx) : null;
    }

    private void renderStatus(final GuiGraphics g, final Font font, final int x, final int y) {
        int ry = y + 2;
        Texts.small(g, font, online ? "Mainframe: online" : "Mainframe: offline", x, ry, online ? ONLINE_GREEN : OFFLINE_RED);
        ry += 10;
        Texts.small(g, font, "Item types on network: " + networkItems.size(), x, ry, skin.text());
        ry += 10;
        Texts.small(g, font, "Local item types: " + localItems.size(), x, ry, skin.text());
        ry += 10;
        Texts.small(g, font, "Network stored: " + dataLabel(usedItems), x, ry, skin.text());
        ry += 10;
        Texts.small(g, font, "Servers: " + serverCount, x, ry, skin.text());
        ry += 10;
        Texts.small(g, font, "Craftable: " + crafts.size(), x, ry, skin.text());
    }

    /** The crafts shown after the search filter (by result name); the full list when the search is empty. */
    private List<CraftCatalogPayload.Entry> filteredCrafts() {
        final String q = search.query();
        if (q.isEmpty()) {
            return crafts;
        }
        final String key = listVersion + "|" + q;
        if (key.equals(craftsKey)) {
            return craftsCache;
        }
        final List<CraftCatalogPayload.Entry> out = new ArrayList<>();
        for (final CraftCatalogPayload.Entry e : crafts) {
            if (e.title().toLowerCase(Locale.ROOT).contains(q)
                    || e.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        craftsCache = out;
        craftsKey = key;
        return out;
    }

    private List<NetworkItemEntry> filtered(final List<NetworkItemEntry> source) {
        final String q = search.query();
        final String key = listVersion + "|" + sortMode + "|" + q;
        if (source == filteredSource && key.equals(filteredKey)) {
            return filteredCache;
        }
        final List<NetworkItemEntry> out = new ArrayList<>();
        for (final NetworkItemEntry e : source) {
            if (q.isEmpty() || e.name().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        switch (sortMode) {
            case 1 -> out.sort((a, b) -> Long.compare(b.total(), a.total()));
            case 2 -> out.sort((a, b) -> Long.compare(a.total(), b.total()));
            default -> out.sort((a, b) -> a.name().getString().compareToIgnoreCase(b.name().getString()));
        }
        filteredCache = out;
        filteredSource = source;
        filteredKey = key;
        return out;
    }

    // Operations tab (network task manager)

    private List<OperationRecord> allOps() {
        final List<OperationRecord> all = new ArrayList<>(activeOps.size() + recentOps.size());
        all.addAll(activeOps); // live ops first, then the recent log
        all.addAll(recentOps);
        return all;
    }

    private void renderOpsHeader(final GuiGraphics g, final Font font, final int listLeft, final int gridTop) {
        // Supercomputer parallel craft-slot capacity, in the free strip above the list (amber when saturated).
        if (scSlotsTotal > 0) {
            Texts.small(g, font, "Supercomputer: " + scSlotsUsed + " / " + scSlotsTotal + " parallel crafts",
                    listLeft + 2, gridTop - 9, scSlotsUsed >= scSlotsTotal ? AMBER : skin.dim());
        }
        if (allOps().isEmpty()) {
            Texts.small(g, font, "No operations on the network.", listLeft + 2, gridTop + 4, skin.dim());
        }
    }

    private void renderOpRow(final GuiGraphics g, final UiContext ctx, final OperationRecord op, final int index,
                             final int x, final int y, final int w, final int h, final boolean hovered,
                             final boolean selected) {
        final boolean live = index < activeOps.size();
        if (index == opSelected) {
            g.fill(x, y, x + w, y + h, 0x552F6AC6);
        } else if (hovered) {
            g.fill(x, y, x + w, y + h, 0x22000000);
        }
        g.fill(x + 1, y + 4, x + 4, y + 7, live ? 0xFF49E07A : 0xFF8A93A4);
        final Font font = ctx.font();
        final String type = OperationPalette.labelFor(op.type());
        g.drawString(font, type, x + 7, y + 2, OperationPalette.colorFor(op.type()), false);
        final int nameX = x + 8 + font.width(type) + 3;
        final String st = opStatusShort(op.status());
        final int stW = font.width(st);
        g.drawString(font, Texts.trim(font, op.name().getString(), x + w - nameX - stW - 6), nameX, y + 2,
                ctx.skin().text(), false);
        g.drawString(font, st, x + w - stW - 2, y + 2, opStatusColor(op.status()), false);
    }

    private void opClicked(final int index, final int button, final double mx, final double my) {
        opSelected = index < 0 || opSelected == index ? -1 : index;
    }

    /** The selected Operation's detail in the right panel: amounts, status, and per-source/sub rows. */
    private void renderOpDetails(final GuiGraphics g, final Font font, final int x, final int y,
                                 final NetworkInteractorLayout.Zones z) {
        final int dx = x + z.detailsX();
        final int dy = y + z.detailsY();
        final int dw = z.detailsW();
        final int dh = z.detailsH();
        if (dw <= 6 || dh <= 6) {
            return;
        }
        raisedPanel(g, dx, dy, dw, dh);
        Draw.pushScissor(g, dx + 1, dy + 1, dx + dw - 1, dy + dh - 1);
        final List<OperationRecord> all = allOps();
        final int px = dx + 5;
        int py = dy + 5;
        if (opSelected < 0 || opSelected >= all.size()) {
            Texts.small(g, font, "Select an operation", px, py, skin.dim());
            Texts.small(g, font, "to see its details.", px, py + 9, skin.dim());
            Draw.popScissor(g);
            return;
        }
        final OperationRecord op = all.get(opSelected);
        DesktopItems.data(g, font, op.key(), px, py, null);
        Texts.small(g, font, Texts.trim(font, op.name().getString(), Texts.smallFits(dw - 28)), px + 20, py + 1, skin.text());
        Texts.small(g, font, OperationPalette.labelFor(op.type()), px + 20, py + 10, OperationPalette.colorFor(op.type()));
        py += 22;
        Texts.small(g, font, "moved " + formatCount(op.moved()) + " / " + formatCount(op.requested()), px, py, skin.text());
        py += 10;
        Texts.small(g, font, opStatusLong(op.status()), px, py, opStatusColor(op.status()));
        py += 12;
        if (!op.subs().isEmpty()) {
            Texts.small(g, font, "SUBOPERATIONS", px, py, skin.dim());
            py += 10;
            for (final var sub : op.subs()) {
                if (py > dy + dh - 9) {
                    break;
                }
                Texts.small(g, font, Texts.trim(font, sub.server() + ": " + sub.moved() + "/" + sub.planned()
                        + " " + subStateLabel(sub.state()), Texts.smallFits(dw - 10)), px, py, skin.text());
                py += 9;
            }
        } else if (!op.moves().isEmpty()) {
            Texts.small(g, font, "SOURCES", px, py, skin.dim());
            py += 10;
            for (final var mv : op.moves()) {
                if (py > dy + dh - 9) {
                    break;
                }
                Texts.small(g, font, Texts.trim(font, mv.from() + " " + mv.qty() + " -> " + mv.to(),
                        Texts.smallFits(dw - 10)), px, py, skin.text());
                py += 9;
            }
        }
        Draw.popScissor(g);
    }

    private static String opStatusShort(final byte status) {
        return switch (status) {
            case 0 -> "done";
            case 1 -> "part";
            case 2 -> "fail";
            case 3 -> "run";
            case 4 -> "wait";
            case 5 -> "lock";
            case 6 -> "pend";
            default -> "drop";
        };
    }

    private static String opStatusLong(final byte status) {
        return switch (status) {
            case 0 -> "Completed";
            case 1 -> "Completed (partial)";
            case 2 -> "Failed";
            case 3 -> "Processing";
            case 4 -> "Waiting";
            case 5 -> "Resource locked";
            case 6 -> "Pending";
            default -> "Discarded";
        };
    }

    private static int opStatusColor(final byte status) {
        return switch (status) {
            case 0 -> ONLINE_GREEN;       // completed
            case 1, 3, 4, 6 -> 0xFFB8860B; // partial / processing / waiting / pending: amber
            case 2, 5, 7 -> 0xFFB23A3A; // failed / locked / discarded: red
            default -> 0xFF6A7280;
        };
    }

    private static String subStateLabel(final byte state) {
        return switch (state) {
            case 1 -> "reading";
            case 2 -> "streaming";
            case 3 -> "done";
            default -> "queued";
        };
    }

    // footer

    private String usageText() {
        return dataLabel(usedItems) + " stored";
    }

    private String statusText() {
        return (online ? "● Mainframe online" : "○ Mainframe offline")
                + "  ·  " + networkItems.size() + " types  ·  " + serverCount + " servers";
    }

    private int statusColor() {
        return online ? ONLINE_GREEN : OFFLINE_RED;
    }

    private String lastOutputText() {
        return output.isEmpty() ? "" : output.peekLast().text();
    }

    private int lastOutputColor() {
        return output.isEmpty() ? 0xFF40C060 : output.peekLast().color();
    }

    /** This box's own shell session on the machine, so its replies are its own. */
    private final int session = ShellViews.newSession();

    private void runCommand(final String line) {
        pushOutput("> " + line, 0xFF40C060);
        PacketDistributor.sendToServer(new DesktopShellRunPayload(host, line, this.session));
    }

    //  Input

    private Panel inputTarget() {
        if (requestPopup.isOpen()) {
            return requestPopup;
        }
        if (craftPopup.isOpen()) {
            return craftPopup;
        }
        return root;
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        // A modal dialog, when open, takes the click before anything else.
        if (hasPopup()) {
            inputTarget().mouseClicked(mouseX, mouseY, button);
            return;
        }
        /*
         * Inventory band: real container slots handled by the desktop screen (cursor, drag, shift-click);
         * the app simply ignores clicks that land there so it never misreads them as grid/console input.
         */
        if (inInventoryZone(mouseX - lastX, mouseY - lastY)) {
            return;
        }
        root.mouseClicked(mouseX, mouseY, button);
        if (root.focusedChild() == null) {
            // Typing goes to the console whenever no field holds the keyboard.
            root.focus(console);
        }
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        inputTarget().mouseDragged(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        inputTarget().mouseReleased(mouseX, mouseY, button);
    }

    /**
     * The deposit target when the player clicks the grid zone of a grid tab while holding a stack, or
     * {@code -1} when the click is not over a depositable grid. The desktop screen (which owns the
     * cursor) calls this to route a cursor deposit to the network or to local storage.
     */
    public int cursorDepositTarget(final double lx, final double ly) {
        if (tab != TAB_NETWORK && tab != TAB_LOCAL) {
            return -1;
        }
        final NetworkInteractorLayout.Zones z = zones();
        if (ly >= z.gridY() && ly < z.gridY() + z.gridH() && lx >= 0 && lx < contentW) {
            return tab == TAB_NETWORK ? NiDepositPayload.TARGET_NETWORK : NiDepositPayload.TARGET_STORAGE;
        }
        return -1;
    }

    /**
     * The grid entry under the cursor on a grid tab: the data a held empty container would fill with on a
     * right-click, or empty when the click is not on an entry.
     */
    public Optional<StorageKey> cursorDepositEntry(final double lx, final double ly) {
        if (tab != TAB_NETWORK && tab != TAB_LOCAL) {
            return Optional.empty();
        }
        final int idx = NetworkInteractorLayout.gridIndexAt((int) lx, (int) ly, grid.scroll(), zones());
        final List<NetworkItemEntry> items = filtered(tab == TAB_NETWORK ? networkItems : localItems);
        return idx >= 0 && idx < items.size() ? Optional.of(items.get(idx).key()) : Optional.empty();
    }

    /** Whether any modal dialog (request/storage or craft) is open, in which case the desktop routes every click to the app. */
    public boolean hasPopup() {
        return requestPopup.isOpen() || craftPopup.isOpen();
    }

    @Override
    public boolean modalActive() {
        return hasPopup();
    }

    @Override
    public void renderModal(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                            final int height, final int mouseX, final int mouseY) {
        /*
         * The desktop draws this in a late pass above every item icon, so the dialog's own dim covers and
         * darkens the grid/craft/inventory icons instead of them piercing through at their blit depth.
         */
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, 0f);
        if (requestPopup.isOpen()) {
            requestPopup.renderIn(g, ctx, x, y, width, height);
        } else if (craftPopup.isOpen()) {
            craftPopup.renderIn(g, ctx, x, y, width, height);
        }
    }

    /**
     * Where a shift-click on an inventory slot inserts: the network on the Network tab, local storage on the
     * Local tab, or -1 (not applicable) on the other tabs. Matches the deposit targets the cursor drop uses.
     */
    public int shiftInsertTarget() {
        if (tab == TAB_NETWORK) {
            return NiShiftInsertPayload.TARGET_NETWORK;
        }
        if (tab == TAB_LOCAL) {
            return NiShiftInsertPayload.TARGET_STORAGE;
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        final Panel target = inputTarget();
        if (target.mouseScrolled(lastMouseX, lastMouseY, delta)) {
            return true;
        }
        if (hasPopup()) {
            return true;
        }
        /*
         * The wheel anywhere in the window moves the tab's list: the Operations tab its rows, the grid tabs
         * their items; nothing on Status.
         */
        final int step = delta > 0 ? -1 : 1;
        if (tab == TAB_OPS) {
            opList.setScroll(opList.scroll() + step);
            return true;
        }
        if (grid.maxScroll() <= 0) {
            return false;
        }
        grid.scrollBy(step);
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        return inputTarget().charTyped(c);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        return inputTarget().keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY) {
        /*
         * The inventory band is real container slots; the desktop screen renders their item tooltips, so the
         * app stays out of that area to avoid a double tooltip.
         */
        if (inInventoryZone(mouseX - x, mouseY - y)) {
            return;
        }
        if (tab == TAB_NETWORK || tab == TAB_LOCAL) {
            // Name + the true on-network total (which a count badge can't show fully).
            final NetworkItemEntry e = hoveredGridEntry(mouseX, mouseY);
            if (e != null) {
                g.renderComponentTooltip(font, List.of(e.name(),
                        Component.literal(amountLabel(e.key(), e.total())).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            }
        } else if (tab == TAB_CRAFTING) {
            final int idx = grid.cellAt(mouseX, mouseY);
            final List<CraftCatalogPayload.Entry> list = filteredCrafts();
            if (idx >= 0 && idx < list.size()) {
                g.renderTooltip(font, list.get(idx).result(), mouseX, mouseY);
            }
        }
    }

    //  The request/storage dialog

    private void openPopup(final NetworkItemEntry e, final boolean storage) {
        craftPopup.close();
        popupEntry = e;
        popupStorage = storage;
        // Default to one stack, clamped to what is available.
        popupQty = Math.max(1L, Math.min(e.total(), e.key().batch()));
        popupAdvanced = false;
        popupDeselected.clear();
        popupDestIndex = 0;
        popupPriority = OperationPriority.DEFAULT;
        requestPopup.setPreferredSize(POPUP_W, POPUP_H);
        requestPopup.rebuildSources();
        requestPopup.open();
        requestPopup.placeIn(lastX, lastY, contentW, contentH);
        if (!storage) {
            // Fetch the network's computers so advanced mode can list sources and destinations.
            PacketDistributor.sendToServer(new RequestNiServersPayload(host, monitorPos));
        }
    }

    private void closePopup() {
        requestPopup.close();
    }

    /** Whether the advanced sections are shown (only the Network request popup has them). */
    private boolean advancedShown() {
        return popupAdvanced && !popupStorage;
    }

    private int popupHeight() {
        return advancedShown() ? POPUP_H_ADV : POPUP_H;
    }

    private void toggleAdvanced() {
        popupAdvanced = !popupAdvanced;
        requestPopup.setPreferredSize(POPUP_W, popupHeight());
        requestPopup.placeIn(lastX, lastY, contentW, contentH);
    }

    private void stepQty(final int step) {
        if (popupEntry != null) {
            popupQty = Math.max(1L, Math.min(popupEntry.total(), popupQty + step));
        }
    }

    private void maxQty() {
        if (popupEntry != null) {
            popupQty = Math.max(1L, popupEntry.total());
        }
    }

    private void toggleSource(final String key) {
        if (!popupDeselected.remove(key)) {
            popupDeselected.add(key);
        }
    }

    private void cycleDest(final int direction) {
        final int destCount = servers.size() + 1; // index 0 = this computer
        popupDestIndex = (popupDestIndex + direction + destCount) % destCount;
    }

    private String destLabel() {
        return popupDestIndex == 0 ? "This computer"
                : (popupDestIndex - 1 < servers.size() ? servers.get(popupDestIndex - 1).name() : "This computer");
    }

    private void popupAction(final int mode) {
        if (popupEntry != null && popupQty > 0) {
            PacketDistributor.sendToServer(new NiGridClickPayload(host, monitorPos, popupEntry.key(), popupQty, mode,
                    popupPriority));
        }
        closePopup();
    }

    private void stepPopupPriority(final int direction) {
        popupPriority = direction > 0 ? popupPriority.raise() : popupPriority.lower();
    }

    private void cycleCraftPriority() {
        // Wraps from HIGH back to LOW so one button walks every level.
        craftPriority = craftPriority == OperationPriority.HIGH ? OperationPriority.LOW : craftPriority.raise();
    }

    /** Sends the advanced request: the selected source Servers and the chosen destination. */
    private void sendAdvancedRequest() {
        if (popupEntry == null || popupQty <= 0) {
            closePopup();
            return;
        }
        // Sources: the servers NOT deselected. An empty list means "all sources" server-side.
        final List<String> sources = new ArrayList<>();
        for (final NetworkServersPayload.ServerEntry srv : servers) {
            if (!popupDeselected.contains(srv.key())) {
                sources.add(srv.key());
            }
        }
        final boolean allSelected = sources.size() == servers.size();
        final String destKey = (popupDestIndex > 0 && popupDestIndex - 1 < servers.size())
                ? servers.get(popupDestIndex - 1).key() : "";
        PacketDistributor.sendToServer(new NiSelectPayload(host, monitorPos, popupEntry.key(), popupQty,
                allSelected ? List.of() : sources, destKey, popupPriority));
        closePopup();
    }

    private static String stepLabel(final int step) {
        final String sign = step > 0 ? "+" : "-";
        final int mag = Math.abs(step);
        return sign + (mag >= 1000 ? mag / 1000 + "k" : Integer.toString(mag));
    }

    /** A number readout drawn as a field: the quantity a dialog is about. */
    private final class QuantityBox extends UiComponent {
        private final java.util.function.LongSupplier value;
        private final String prefix;

        private QuantityBox(final String prefix, final java.util.function.LongSupplier value) {
            this.prefix = prefix;
            this.value = value;
        }

        @Override
        public void render(final GuiGraphics g, final UiContext ctx) {
            ctx.skin().field(g, x(), y(), width(), height(), false);
            g.drawString(ctx.font(), prefix + value.getAsLong(), x() + 4, y() + (height() - 7) / 2, ctx.skin().text(), false);
        }
    }

    /** The request/storage dialog: item, availability, quantity, and where the items go. */
    private final class RequestPopup extends Popup {
        private final UiComponent header = add(new UiComponent() {
            @Override
            public void render(final GuiGraphics g, final UiContext ctx) {
                if (popupEntry == null) {
                    return;
                }
                DesktopItems.data(g, ctx.font(), popupEntry.key(), x(), y(), null);
                final int nameW = popupStorage ? POPUP_W - 30 : POPUP_W - 64;
                g.drawString(ctx.font(), Texts.trim(ctx.font(), popupEntry.name().getString(), nameW), x() + 20, y() + 1,
                        ctx.skin().text(), false);
                g.drawString(ctx.font(), amount(popupEntry.key(), popupEntry.total()) + " available", x() + 20, y() + 11,
                        ctx.skin().dim(), false);
            }
        });
        private final Button adv = add(new Button("Adv", NetworkInteractorApp.this::toggleAdvanced).setLabelScale(Texts.SMALL));
        private final QuantityBox qty = add(new QuantityBox("x", () -> popupQty));
        private final Button max = add(new Button("Max", NetworkInteractorApp.this::maxQty).setLabelScale(Texts.SMALL));
        private final Button[] steps = new Button[POPUP_STEPS.length];
        private final Label prioLabel = add(new Label("PRIORITY", Label.Tone.DIM).setScale(Texts.SMALL));
        private final Button prioDown = add(new Button("<", () -> stepPopupPriority(-1)).setLabelScale(Texts.SMALL));
        private final UiComponent prioBox = add(new UiComponent() {
            @Override
            public void render(final GuiGraphics g, final UiContext ctx) {
                ctx.skin().field(g, x(), y(), width(), height(), false);
                final String text = popupPriority.label();
                Texts.small(g, ctx.font(), text, x() + (width() - Texts.smallWidth(ctx.font(), text)) / 2, y() + 2,
                        ctx.skin().text());
            }
        });
        private final Button prioUp = add(new Button(">", () -> stepPopupPriority(1)).setLabelScale(Texts.SMALL));
        private final Label pullLabel = add(new Label("PULL FROM (servers)", Label.Tone.DIM).setScale(Texts.SMALL));
        private final Panel sources = add(new Panel());
        private final Label noSources = add(new Label("all sources", Label.Tone.DIM).setScale(Texts.SMALL));
        private final Label sendTo = add(new Label("SEND TO", Label.Tone.DIM).setScale(Texts.SMALL));
        private final Button prevDest = add(new Button("<", () -> cycleDest(-1)).setLabelScale(Texts.SMALL));
        private final UiComponent destBox = add(new UiComponent() {
            @Override
            public void render(final GuiGraphics g, final UiContext ctx) {
                ctx.skin().field(g, x(), y(), width(), height(), false);
                Texts.small(g, ctx.font(), Texts.trim(ctx.font(), destLabel(), Texts.smallFits(width() - 6)), x() + 3, y() + 2,
                        ctx.skin().text());
            }
        });
        private final Button nextDest = add(new Button(">", () -> cycleDest(1)).setLabelScale(Texts.SMALL));
        private final Button action = add(new Button(() -> popupDestIndex == 0 ? "Request" : "Send",
                NetworkInteractorApp.this::sendAdvancedRequest).setLabelScale(Texts.SMALL));
        private final Button toInventory = add(new Button("To Inventory",
                () -> popupAction(NiGridClickPayload.MODE_LOCAL_TO_INV)).setLabelScale(Texts.SMALL));
        private final Button toNetwork = add(new Button("To Network",
                () -> popupAction(NiGridClickPayload.MODE_LOCAL_TO_NET)).setLabelScale(Texts.SMALL));
        private final Button request = add(new Button("Request",
                () -> popupAction(NiGridClickPayload.MODE_NET_TO_LOCAL)).setLabelScale(Texts.SMALL));

        private RequestPopup() {
            super("", POPUP_W, POPUP_H);
            for (int i = 0; i < POPUP_STEPS.length; i++) {
                final int step = POPUP_STEPS[i];
                steps[i] = add(new Button(stepLabel(step), () -> stepQty(step)).setLabelScale(Texts.SMALL));
            }
            setDim(0x99000000);
            setLayouter(p -> layout());
            setOnClose(() -> {
                popupEntry = null;
                popupQty = 0;
            });
        }

        /** One checkbox per server the network reported, for the advanced PULL FROM list. */
        private void rebuildSources() {
            sources.clear();
            for (final NetworkServersPayload.ServerEntry srv : servers) {
                sources.add(new Checkbox(srv::name, () -> !popupDeselected.contains(srv.key()),
                        () -> toggleSource(srv.key())).setLabelScale(Texts.SMALL));
            }
        }

        private void layout() {
            final int px = x();
            final int py = y();
            header.setBounds(px + 4, py + 4, POPUP_W - 8, 20);
            adv.setBounds(px + POPUP_W - 34, py + 4, 30, 11);
            adv.setVisible(!popupStorage);
            qty.setBounds(px + 4, py + 27, POPUP_W - 44, 12);
            max.setBounds(px + POPUP_W - 36, py + 27, 32, 12);
            for (int i = 0; i < steps.length; i++) {
                steps[i].setBounds(px + 4 + i * 23, py + 44, 22, 12);
            }
            // The scheduling level, on its own row under the quantity steppers, in both dialog modes.
            prioLabel.setBounds(px + 4, py + 62, 50, 8);
            prioDown.setBounds(px + 56, py + 60, 12, 12);
            prioBox.setBounds(px + 70, py + 60, 40, 12);
            prioUp.setBounds(px + 112, py + 60, 12, 12);
            final boolean advanced = advancedShown();
            pullLabel.setBounds(px + 4, py + 74, POPUP_W - 8, 8);
            pullLabel.setVisible(advanced);
            final int listY = py + 83;
            sources.setBounds(px + 6, listY, POPUP_W - 12, ADV_ROWS * 10);
            sources.setVisible(advanced && !servers.isEmpty());
            final List<UiComponent> rows = sources.children();
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setBounds(px + 6, listY + i * 10, POPUP_W - 12, 9);
                rows.get(i).setVisible(i < ADV_ROWS);
            }
            noSources.setBounds(px + 17, listY, POPUP_W - 24, 8);
            noSources.setVisible(advanced && servers.isEmpty());
            final int destY = listY + ADV_ROWS * 10 + 2;
            sendTo.setBounds(px + 4, destY, 60, 8);
            prevDest.setBounds(px + 4, destY + 9, 12, 12);
            destBox.setBounds(px + 18, destY + 9, POPUP_W - 36, 12);
            nextDest.setBounds(px + POPUP_W - 16, destY + 9, 12, 12);
            action.setBounds(px + 4, py + height() - 20, POPUP_W - 8, 16);
            sendTo.setVisible(advanced);
            prevDest.setVisible(advanced);
            destBox.setVisible(advanced);
            nextDest.setVisible(advanced);
            action.setVisible(advanced);
            final int bw = (POPUP_W - 12) / 2;
            toInventory.setBounds(px + 4, py + 76, bw, 18);
            toNetwork.setBounds(px + 4 + bw + 4, py + 76, bw, 18);
            request.setBounds(px + 4, py + 76, POPUP_W - 8, 18);
            toInventory.setVisible(!advanced && popupStorage);
            toNetwork.setVisible(!advanced && popupStorage);
            request.setVisible(!advanced && !popupStorage);
        }

        @Override
        public boolean mouseClicked(final double mx, final double my, final int button) {
            // A right-click, or a click outside the dialog box, dismisses it.
            if (button == 1) {
                close();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean charTyped(final char c) {
            if (c >= '0' && c <= '9') {
                final long v = popupQty * 10 + (c - '0');
                if (v <= MAX_TYPED_QTY) {
                    popupQty = v;
                }
            }
            return true; // the dialog captures all typing while it is open
        }

        @Override
        public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE -> popupQty /= 10;
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> popupAction(popupStorage // Enter = primary action
                        ? NiGridClickPayload.MODE_LOCAL_TO_INV : NiGridClickPayload.MODE_NET_TO_LOCAL);
                case GLFW.GLFW_KEY_ESCAPE -> close();
                default -> { }
            }
            return true;
        }
    }

    //  The craft dialog

    private void openCraftPopup(final CraftCatalogPayload.Entry entry) {
        craftEntry = entry;
        craftQty = 1;
        craftMulti = true; // default to the pipeline when the item has one; the toggle lets the player switch
        craftPriority = OperationPriority.DEFAULT;
        craftPlan = null;
        closePopup();
        craftPopup.open();
        craftPopup.placeIn(lastX, lastY, contentW, contentH);
        requestCraftPlan();
    }

    /** Asks the server to plan the current craft at the current quantity (need vs have rows). */
    private void requestCraftPlan() {
        if (craftEntry != null) {
            PacketDistributor.sendToServer(new CraftPlanRequestPayload(monitorPos, host, craftEntry.result(), craftQty));
        }
    }

    private void setCraftQty(final long value) {
        craftQty = Math.max(1, Math.min(MAX_CRAFT_QTY, value));
        requestCraftPlan();
    }

    /** Submits the craft (full or partial up to what is currently feasible) and closes the popup. */
    private void submitCraft(final boolean partial) {
        if (craftEntry != null) {
            // A multi-stage choice only bites when the entry actually has a pipeline; otherwise it is ignored.
            final boolean multi = !craftEntry.multiStage() || craftMulti;
            PacketDistributor.sendToServer(new CraftSubmitPayload(monitorPos, host, craftEntry.result(), craftQty, partial,
                    multi, craftPriority));
        }
        craftPopup.close();
    }

    /** The MC-NET-style craft popup: result + quantity steppers + a live plan (need vs have) + Craft/Partial/Close. */
    private final class CraftPopup extends Popup {
        private final UiComponent header = add(new UiComponent() {
            @Override
            public void render(final GuiGraphics g, final UiContext ctx) {
                if (craftEntry == null) {
                    return;
                }
                DesktopItems.item(g, craftEntry.result(), x(), y() - 1);
                g.drawString(ctx.font(), "Craft " + Texts.trim(ctx.font(), craftEntry.title(), CRAFT_W - 40), x() + 20, y() + 2,
                        ctx.skin().text(), false);
            }
        });
        private final QuantityBox qty = add(new QuantityBox("", () -> craftQty));
        private final Button[] steps = new Button[CRAFT_STEPS.length];
        private final Label planLabel = add(new Label("PLAN - raw ingredients", Label.Tone.DIM).setScale(Texts.SMALL));
        private final Button priority = add(new Button(() -> "Prio: " + craftPriority.label(),
                NetworkInteractorApp.this::cycleCraftPriority).setLabelScale(Texts.SMALL));
        private final UiComponent plan = add(new UiComponent() {
            @Override
            public void render(final GuiGraphics g, final UiContext ctx) {
                renderPlan(g, ctx, x(), y());
            }
        });
        private final Label estimate = add(new Label(this::estimateText, Label.Tone.DIM).setScale(Texts.SMALL));
        private final Label feasible = add(new Label(this::feasibleText).setColor(0xFFB8860B).setScale(Texts.SMALL));
        private final Button recipeToggle = add(new Button(() -> craftMulti ? "Multi-stage" : "Flat",
                () -> craftMulti = !craftMulti).setLabelScale(Texts.SMALL));
        private final Button craft = add(new Button("Craft", () -> submitCraft(false)).setLabelScale(Texts.SMALL));
        private final Button partial = add(new Button("Partial", () -> submitCraft(true)).setLabelScale(Texts.SMALL));
        private final Button closeButton = add(new Button("Close", this::close).setLabelScale(Texts.SMALL));

        private CraftPopup() {
            super("", CRAFT_W, CRAFT_H);
            for (int i = 0; i < CRAFT_STEPS.length; i++) {
                final int step = CRAFT_STEPS[i];
                steps[i] = add(new Button((step > 0 ? "+" : "") + step, () -> setCraftQty(craftQty + step))
                        .setLabelScale(Texts.SMALL));
            }
            setDim(0xB0000000);
            setLayouter(p -> layout());
            setOnClose(() -> {
                craftEntry = null;
                craftPlan = null;
            });
        }

        private void layout() {
            final int px = x();
            final int py = y();
            header.setBounds(px + 4, py + 4, CRAFT_W - 8, 16);
            qty.setBounds(px + 5, py + 22, 61, 14);
            for (int i = 0; i < steps.length; i++) {
                steps[i].setBounds(px + 70 + i * 31, py + 22, 29, 14);
            }
            planLabel.setBounds(px + 5, py + 41, CRAFT_W - 70, 8);
            // The scheduling level shares the plan header's row, on the right.
            priority.setBounds(px + CRAFT_W - 61, py + 38, 56, 11);
            plan.setBounds(px + 4, py + 51, CRAFT_W - 8, CRAFT_H - 51 - 34);
            estimate.setBounds(px + 5, py + CRAFT_H - 32, 60, 8);
            estimate.setVisible(craftPlan != null);
            feasible.setBounds(px + 70, py + CRAFT_H - 32, 44, 8);
            feasible.setVisible(craftPlan != null && !craftPlan.feasible());
            /*
             * The recipe toggle only shows when the item can be made as a multi-stage pipeline (and thus also
             * flat): it picks which recipe the craft runs.
             */
            recipeToggle.setBounds(px + 118, py + CRAFT_H - 33, CRAFT_W - 118 - 5, 12);
            recipeToggle.setVisible(craftEntry != null && craftEntry.multiStage());
            final int by = py + CRAFT_H - 19;
            craft.setBounds(px + 5, by, 58, 16);
            partial.setBounds(px + 67, by, 66, 16);
            closeButton.setBounds(px + 137, by, 54, 16);
        }

        /** Plan rows (need vs have): green when the network has enough, red otherwise. */
        private void renderPlan(final GuiGraphics g, final UiContext ctx, final int px, final int top) {
            final Font font = ctx.font();
            int ry = top;
            if (craftPlan == null) {
                Texts.small(g, font, "planning...", px + 1, ry, ctx.skin().dim());
                return;
            }
            final int shown = Math.min(5, craftPlan.rows().size());
            for (int i = 0; i < shown; i++) {
                final CraftPlanPayload.Row row = craftPlan.rows().get(i);
                DesktopItems.item(g, row.item(), px, ry - 2);
                Texts.small(g, font, Texts.trim(font, row.item().getHoverName().getString(), Texts.smallFits(96)),
                        px + 18, ry, ctx.skin().text());
                final String counts = formatCount(row.have()) + " / " + formatCount(row.need());
                Texts.small(g, font, counts, px + CRAFT_W - 10 - Texts.smallWidth(font, counts), ry,
                        row.satisfied() ? ONLINE_GREEN : 0xFFB23A3A);
                ry += 12;
            }
            if (craftPlan.rows().size() > shown) {
                Texts.small(g, font, "+" + (craftPlan.rows().size() - shown) + " more", px + 18, ry, ctx.skin().dim());
            }
        }

        private String estimateText() {
            if (craftPlan == null) {
                return "";
            }
            return craftPlan.estimateTicks() > 0 ? "EST ~" + Math.max(1, craftPlan.estimateTicks() / 20) + "s" : "EST --";
        }

        private String feasibleText() {
            return craftPlan == null ? "" : "max " + formatCount(craftPlan.maxFeasible());
        }

        @Override
        public boolean charTyped(final char c) {
            if (c >= '0' && c <= '9') {
                setCraftQty(craftQty * 10 + (c - '0'));
            }
            return true;
        }

        @Override
        public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
            switch (key) {
                case GLFW.GLFW_KEY_BACKSPACE -> setCraftQty(craftQty / 10);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> submitCraft(false);
                case GLFW.GLFW_KEY_ESCAPE -> close();
                default -> { }
            }
            return true;
        }
    }

    //  Helpers

    private static String formatCount(final long n) {
        if (n < 1000) {
            return Long.toString(n);
        }
        if (n < 1_000_000) {
            return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        }
        return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
    }

    /** A short amount with its unit where the unit is not obvious: items by the count, data by the millibucket. */
    private static String amount(final StorageKey key, final long n) {
        return key.isItem() ? formatCount(n) : formatCount(n) + " mB";
    }

    /** The exact amount with its unit, for a tooltip. */
    private static String amountLabel(final StorageKey key, final long n) {
        return key.isItem()
                ? String.format(Locale.ROOT, "%,d item%s", n, n == 1L ? "" : "s")
                : String.format(Locale.ROOT, "%,d mB", n);
    }

    /** The registry id of the data behind a key: the item's, the fluid's, or the chemical's. */
    private static ResourceLocation dataId(final StorageKey key) {
        if (key.isFluid()) {
            return BuiltInRegistries.FLUID.getKey(key.fluidPrototype().getFluid());
        }
        if (key.isChemical()) {
            return Objects.requireNonNull(key.chemicalId());
        }
        return BuiltInRegistries.ITEM.getKey(key.item());
    }

    /** The data a weight amounts to, at 4 MB the item (1 000 mB-eq): a bucket of fluid weighs as much as an item. */
    private static String dataLabel(final long weight) {
        final long mb = weight * 4L / StorageKey.MB_EQ_PER_ITEM;
        if (mb < 1024L) {
            return mb + " MB";
        }
        if (mb < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f TB", mb / (1024.0 * 1024.0));
    }

    /**
     * Draws {@code text} word-wrapped to {@code maxWidth}, one line per row, so a hint never truncates in the
     * middle of a word. A single word longer than the line is left as-is on its own row.
     */
    private static void drawWrapped(final GuiGraphics g, final Font font, final String text,
                                    final int x, final int y, final int maxWidth, final int color) {
        int ry = y;
        final StringBuilder line = new StringBuilder();
        for (final String word : text.split(" ")) {
            final String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && line.length() > 0) {
                g.drawString(font, line.toString(), x, ry, color, false);
                ry += 11;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (line.length() > 0) {
            g.drawString(font, line.toString(), x, ry, color, false);
        }
    }

    private static int colorOf(final int ordinal) {
        final CliStyle[] values = CliStyle.values();
        final CliStyle style = ordinal >= 0 && ordinal < values.length ? values[ordinal] : CliStyle.PLAIN;
        return switch (style) {
            case ACCENT, HEADER -> 0xFF39D6C4;
            case OK -> 0xFF5FE07A;
            case ERROR -> 0xFFEF6A5A;
            case WARN -> 0xFFF0B23A;
            case INFO -> 0xFF2AA7E0;
            case DIM -> 0xFF7D8A9C;
            // The extended palette: brand-tinted terminal colors (screenfetch logos and the like).
            case ORANGE -> 0xFFE95420;
            case MAGENTA -> 0xFFE0447C;
            case BLUE -> 0xFF5A8FD6;
            case CYAN -> 0xFF2FA6E8;
            case PURPLE -> 0xFF9E8FD6;
            default -> 0xFFCDD6E2;
        };
    }

    //  Inspection (client tests): content-local points of the controls, from the last frame's layout

    private int[] local(final int[] c) {
        return new int[] {c[0] - lastX, c[1] - lastY};
    }

    public int activeTab() {
        return tab;
    }

    public boolean isCraftPopupOpen() {
        return craftPopup.isOpen();
    }

    public long craftQuantity() {
        return craftQty;
    }

    /** The Crafting tab's entries as listed (after the search filter), by display name. */
    public List<String> craftableNames() {
        final List<String> out = new ArrayList<>();
        for (final CraftCatalogPayload.Entry e : filteredCrafts()) {
            out.add(e.title());
        }
        return out;
    }

    public int[] craftingTabCenter() {
        return local(tabs.tabCenter(TAB_CRAFTING));
    }

    /** Content-local centre of the Network tab: the grid a carried stack is deposited into. */
    public int[] networkTabCenter() {
        return local(tabs.tabCenter(TAB_NETWORK));
    }

    /** The centre of the grid cell showing craftable {@code index} (must be scrolled into view). */
    public int[] craftableCellCenter(final int index) {
        return local(grid.cellCenter(index));
    }

    /** The centre of quantity stepper {@code index} of the craft popup (0: -64, 1: -1, 2: +1, 3: +64). */
    public int[] craftPopupStepCenter(final int index) {
        return local(craftPopup.steps[index].center());
    }

    /** Content-local centre of inventory band slot {@code index} (rows 0-2 main inventory, row 3 hotbar). */
    public int[] inventoryBandSlotCenter(final int index) {
        final NetworkInteractorLayout.Zones z = zones();
        final int col = index % INV_COLS;
        final int row = index / INV_COLS;
        return new int[] {z.invX() + col * CELL + CELL / 2, z.invY() + NetworkInteractorLayout.rowYOffset(row) + CELL / 2};
    }

    /** Content-local centre of the first grid cell (where a carried stack is deposited on a grid tab). */
    public int[] gridFirstCellCenter() {
        final NetworkInteractorLayout.Zones z = zones();
        return new int[] {z.gridX() + CELL / 2, z.gridY() + CELL / 2};
    }

    /** The centre of the craft popup's full-request button. */
    public int[] craftPopupSubmitCenter() {
        return local(craftPopup.craft.center());
    }

    /** The centre of the craft popup's priority button (each click walks one level up, wrapping to LOW). */
    public int[] craftPopupPriorityCenter() {
        return local(craftPopup.priority.center());
    }

    /** The level the craft popup will submit at. */
    public OperationPriority craftPriority() {
        return craftPriority;
    }
}
