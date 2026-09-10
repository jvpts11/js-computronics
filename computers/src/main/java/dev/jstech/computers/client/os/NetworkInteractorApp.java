/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.crafting.RecipeChoice;
import dev.jstech.computers.crafting.RecipeDifferences;
import dev.jstech.computers.gui.layout.NetworkInteractorLayout;
import dev.jstech.computers.operation.payload.CraftCatalogPayload;
import dev.jstech.computers.operation.payload.CraftPlanPayload;
import dev.jstech.computers.operation.payload.CraftPlanRequestPayload;
import dev.jstech.computers.operation.payload.CraftSubmitPayload;
import dev.jstech.computers.operation.payload.ItemRecipesPayload;
import dev.jstech.computers.operation.payload.NetworkInteractorPayload;
import dev.jstech.computers.operation.payload.NetworkItemEntry;
import dev.jstech.computers.operation.payload.NetworkServersPayload;
import dev.jstech.computers.operation.payload.NiDepositPayload;
import dev.jstech.computers.operation.payload.NiGridClickPayload;
import dev.jstech.computers.operation.payload.NiSelectPayload;
import dev.jstech.computers.operation.payload.NiShiftInsertPayload;
import dev.jstech.computers.operation.payload.OperationRecord;
import dev.jstech.computers.operation.payload.RequestItemRecipesPayload;
import dev.jstech.computers.operation.payload.RequestNetworkInteractorPayload;
import dev.jstech.computers.operation.payload.RequestNiOperationsPayload;
import dev.jstech.computers.operation.payload.RequestNiServersPayload;
import dev.jstech.computers.operation.payload.SetSettingPayload;
import dev.jstech.computers.program.OperationPalette;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.CellGrid;
import dev.jstech.core.client.gui.component.Checkbox;
import dev.jstech.core.client.gui.component.ContextMenu;
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
import dev.jstech.core.gui.layout.DesktopZ;
import dev.jstech.core.operation.OperationPriority;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Network Interactor desktop window: the graphical face of the data network for a Frames computer,
 * with the same capabilities as the MC-NET terminal: extract from the network into local storage,
 * withdraw local storage into the inventory, deposit/insert from the player's hotbar, and request
 * crafts, addressing the host by position. The player's full inventory is shown in the window as real
 * container slots inside a fixed, framed band pinned just above the footer: the desktop menu owns the 36
 * slots and the desktop screen positions them over that band, so the vanilla container drives the cursor,
 * drag, and shift-click. This app paints the inventory frame and slot backgrounds; the screen renders the
 * items and cursor on top. The item grid above the band scrolls its items when there are more than fit.
 *
 * <p>Every zone has a boundary of its own, drawn by the desktop's skin: the toolbar on its band, the grid in
 * a sunken well of drawn cells under its caption, the inventory in a second well, the details in a framed
 * panel with a header strip (and the network's own card when nothing is chosen), and a status bar with a
 * storage gauge.
 *
 * <p>The grid is also driven from the keyboard (a dotted cell marks where it is), filtered by the mod that
 * made the item and by its category, and starred into a Favourites tab the machine keeps. Two grips reshape
 * the window's insides, and the details panel says what makes the item and what it is used in. A craft of an
 * item the network makes more than one way offers the recipes side by side and says what differs.
 *
 * <p>The content is a tree of the core's components laid out every frame from the pure layout's zones, so
 * the drawn cells, the real container slots and the hit-tests agree at every size.
 */
public final class NetworkInteractorApp implements IInventoryBandApp {

    // Labels kept short so all the tabs fit the strip; "Local"/"Network" abbreviate the longer mock names.
    private static final List<String> BASE_TABS = List.of("Status", "Local", "Network", "Crafting", "Operations");
    private static final int TAB_STATUS = 0;
    private static final int TAB_LOCAL = 1;
    private static final int TAB_NETWORK = 2;
    private static final int TAB_CRAFTING = 3;
    private static final int TAB_OPS = 4;
    private static final int TAB_FAV = 5;
    private static final String STAR = "★";
    /** Below this content width the Favourites tab goes by its star alone. */
    private static final int FAV_LABEL_MIN_W = 372;

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
    private static final int STAR_GOLD = 0xFFE0A800;
    private static final int SHORT_RED = 0xFFB23A3A;
    private static final int SCROLLBAR_W = 3;
    private static final String ANY_MOD = "Any mod";
    private static final String ANY_CATEGORY = "Any category";
    private static final String HINT = "Arrows move  ·  Enter request  ·  C craft  ·  F favourite  ·  / search";

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
    /** The wider, taller craft popup, with the recipe cards and the differences strip. */
    private static final int CRAFT_W_CHOICE = 260;
    private static final int CRAFT_H_CHOICE = 199;
    private static final int CARD_H = 52;
    private static final int CARDS_SHOWN = 3;
    private static final int[] CRAFT_STEPS = {-64, -1, 1, 64};
    private static final long MAX_CRAFT_QTY = 99_999L;
    /** Two clicks on the same cell this close together open it; one click selects it. */
    private static final long DOUBLE_CLICK_MS = 300L;

    /*
     * What the player was looking at, kept across reopens (reopening the computer or the monitor) so the NI
     * comes back where they left it instead of snapping to Network with an empty search every time.
     */
    private static int lastTab = TAB_NETWORK;
    private static String lastSearch = "";
    private static String lastMod = "";
    private static String lastCategory = "";
    private static int lastSort;
    private static int lastExtraCols;
    private static int lastInvRows = INV_ROWS;

    private static NetworkInteractorApp active;

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    private int tab = lastTab;

    private final List<NetworkItemEntry> networkItems = new ArrayList<>();
    private final List<NetworkItemEntry> localItems = new ArrayList<>();
    private final List<CraftCatalogPayload.Entry> crafts = new ArrayList<>();
    /** The data this machine keeps starred, by {@link StorageKey#id()}. */
    private final Set<String> favourites = new LinkedHashSet<>();
    private boolean online;
    private long usedItems;
    private long capacityItems;
    private int serverCount;
    /** Whether the last frame showed the network's card, which wants the live operations too. */
    private boolean cardShown;

    /** How the grid is ordered: 0 by name, 1 most stored first, 2 least stored first. */
    private int sortMode = lastSort;
    /** The mod namespace the grid is narrowed to, or {@code ""} for every mod. */
    private String modFilter = lastMod;
    /** The category the grid is narrowed to, or {@code ""} for every category. */
    private String categoryFilter = lastCategory;
    /*
     * The grid's filtered and sorted view, kept between frames: cells, tooltip and hit-tests all ask for it
     * several times a frame, and re-sorting thousands of entries each time was a frame-rate cost.
     */
    private List<NetworkItemEntry> filteredCache = List.of();
    private List<NetworkItemEntry> filteredSource = List.of();
    private String filteredKey = "";
    private List<CraftCatalogPayload.Entry> craftsCache = List.of();
    private String craftsKey = "";
    private List<NetworkItemEntry> favouritesCache = List.of();
    private int favouritesVersion = -1;
    /** Bumped whenever a snapshot replaces the lists, so a stale filtered view is never shown. */
    private int listVersion;

    // The grips' places: columns beyond the inventory's nine, and the inventory rows shown.
    private int extraCols = lastExtraCols;
    private int invRows = lastInvRows;
    /** The grip being dragged: 0 none, 1 the vertical one, 2 the horizontal one. */
    private int dragGrip;

    /** The grid cell the keyboard is on, an index into the active grid's list, or -1. */
    private int keyCell = -1;
    /** The cell a Shift-extended selection grows from, or -1. */
    private int anchorCell = -1;
    /** The data selected on the grid, by key, so a selection survives the list being re-sorted or filtered. */
    private final Set<StorageKey> selected = new LinkedHashSet<>();
    /** The last cell clicked and when, for telling a double click (which opens) from a click (which selects). */
    private int lastClickCell = -1;
    private long lastClickAt;
    /*
     * The rubber band: pressing on the grid and dragging selects every cell the rectangle touches. The press
     * point is kept from the click; the band opens once the cursor has moved a few pixels from it.
     */
    private boolean gridPressed;
    /** Whether the click being handled landed on a cell (set by the grid's click), as opposed to empty grid. */
    private boolean cellHit;
    private boolean marquee;
    private int pressX;
    private int pressY;
    private int bandX;
    private int bandY;

    /** What makes and uses each item, as the server answered, for the details panel. */
    private final Map<StorageKey, ItemRecipesPayload> recipes = new HashMap<>();
    private final Set<StorageKey> recipesAsked = new HashSet<>();

    private int contentW = 280;
    private int contentH = 188;
    // Geometry of the last frame: where the content sits on the desktop and where the cursor was.
    private int lastX;
    private int lastY;
    private int lastMouseX;
    private int lastMouseY;
    @Nullable
    private Font lastFont;
    /*
     * Frames since the last live refresh: the Network Interactor re-asks the server for the storage grid and,
     * on the Operations tab, the live operations a few times a second, so stock and craft progress move on their
     * own instead of only when a command is run.
     */
    private int refreshFrames;

    // The request/storage dialog's state; popupEntry is null while it is closed, popupEntries every item it is for.
    @Nullable
    private NetworkItemEntry popupEntry;
    private final List<NetworkItemEntry> popupEntries = new ArrayList<>();
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
    /** The recipe the popup plans and crafts with, an index into the plan's options; ANY until a plan lands. */
    private int craftRecipe = CraftPlanRequestPayload.ANY;
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
    private final Button modButton;
    private final Button categoryButton;
    private final Button sortButton;
    private final CellGrid grid;
    private final ScrollBar gridBar;
    private final ListView<OperationRecord> opList;
    private final Label hintLabel;
    private final Button detailRequest;
    private final Button detailCraft;
    private final Button detailStar;
    private final ContextMenu filterMenu = new ContextMenu(72, 10);
    private final RequestPopup requestPopup;
    private final CraftPopup craftPopup;

    public NetworkInteractorApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;

        tabs = root.add(new TabStrip(this::tabLabels).fitToLabels(12).setTrailing(TAB_FAV).setOnSelect(this::selectTab));
        tabs.setSelected(tab);
        search = root.add(new SearchField(48));
        search.set(lastSearch);
        search.setOnEdit(this::searchEdited);
        modButton = root.add(new Button(this::modLabel, this::openModFilter).setLabelScale(Texts.SMALL));
        categoryButton = root.add(new Button(this::categoryLabel, this::openCategoryFilter).setLabelScale(Texts.SMALL));
        sortButton = root.add(new Button(this::sortLabel, this::cycleSort).setLabelScale(Texts.SMALL));
        /*
         * The cells are drawn by the renderer over the skin's row backgrounds (hover and selection in the
         * era's colours), not as wells: the well is the whole field the grid sits in.
         */
        grid = root.add(new CellGrid(INV_COLS, 1, 1, CELL)
                .setInset(0)
                .setWells(false)
                .setSelected(this::isSelectedIndex)
                .setRenderer(this::renderGridCell)
                .setOnClick(this::gridCellClicked));
        gridBar = root.add(new ScrollBar(grid::maxScroll, grid::scroll, v -> grid.setScroll(v)));
        opList = root.add(new ListView<OperationRecord>(this::allOps, OP_ROW_H, this::renderOpRow).setOnClick(this::opClicked));
        hintLabel = root.add(new Label(this::hintText, Label.Tone.DIM).setScale(Texts.SMALL));
        detailRequest = root.add(new Button("Request", this::detailRequestPressed).setLabelScale(Texts.SMALL));
        detailCraft = root.add(new Button("Craft", this::detailCraftPressed).setLabelScale(Texts.SMALL));
        detailStar = root.add(new Button(STAR, this::detailStarPressed).setLabelScale(Texts.SMALL));
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
        if (tab == TAB_OPS || cardShown) {
            requestOps();
        }
    }

    /** Asks the server for the network's recent + active Operations (for the Operations tab). */
    private void requestOps() {
        PacketDistributor.sendToServer(new RequestNiOperationsPayload(host, monitorPos));
    }

    // what the machine keeps with the window

    @Override
    public String saveState() {
        return "tab=" + tab + ";sort=" + sortMode + ";cols=" + extraCols + ";rows=" + invRows
                + ";mod=" + modFilter + ";cat=" + categoryFilter + ";q=" + search.edit();
    }

    @Override
    public void restoreState(final String state) {
        for (final String part : state.split(";")) {
            final int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            final String key = part.substring(0, eq);
            final String value = part.substring(eq + 1);
            switch (key) {
                case "tab" -> selectTab(parseInt(value, tab));
                case "sort" -> sortMode = Math.max(0, Math.min(SORT_MODES - 1, parseInt(value, sortMode)));
                case "cols" -> extraCols = Math.max(0, parseInt(value, extraCols));
                case "rows" -> invRows = Math.max(1, Math.min(INV_ROWS, parseInt(value, invRows)));
                case "mod" -> modFilter = value;
                case "cat" -> categoryFilter = value;
                case "q" -> search.set(value);
                default -> { }
            }
        }
        tabs.setSelected(tab);
        rememberView();
    }

    private static int parseInt(final String text, final int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    /** Keeps what this window shows for the next one opened on this client. */
    private void rememberView() {
        lastTab = tab;
        lastSearch = search.edit();
        lastMod = modFilter;
        lastCategory = categoryFilter;
        lastSort = sortMode;
        lastExtraCols = extraCols;
        lastInvRows = invRows;
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
        active.favourites.clear();
        active.favourites.addAll(payload.favourites());
        active.listVersion++;
        active.recipesAsked.clear();
        active.online = payload.mainframeOnline();
        active.usedItems = payload.usedItems();
        active.capacityItems = payload.capacityItems();
        active.serverCount = payload.serverCount();
    }

    /** Delivers a craft plan (need/have rows, and the recipes to choose from) to the open NI's craft popup. */
    public static void acceptCraftPlan(final CraftPlanPayload plan) {
        if (active != null && active.craftEntry != null
                && ItemStack.isSameItemSameComponents(active.craftEntry.result(), plan.result())) {
            active.craftPlan = plan;
            active.craftRecipe = plan.recipe();
            // A choice of recipe grows the popup by the cards and the differences strip; it is placed again on render.
            if (plan.hasChoice()) {
                active.craftPopup.setPreferredSize(CRAFT_W_CHOICE, CRAFT_H_CHOICE);
            } else {
                active.craftPopup.setPreferredSize(CRAFT_W, CRAFT_H);
            }
        }
    }

    /** Delivers what makes an item and what uses it, for the open NI's details panel. */
    public static void acceptItemRecipes(final ItemRecipesPayload payload) {
        if (active != null) {
            active.recipes.put(payload.key(), payload);
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
        // Three grid rows above the whole inventory band, with the hint line and the grip strip in between.
        return 240;
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
     * Layout: every zone comes from the pure NetworkInteractorLayout, derived from the LIVE content size and
     * the grips' places, so the drawn cells, the real container slots, and the hit-tests agree at any size.
     * The inventory band is pinned above the footer; only the grid scrolls (its items, not its pixels).
     */
    private NetworkInteractorLayout.Zones zones() {
        return NetworkInteractorLayout.resolve(contentW, contentH, extraCols, invRows);
    }

    private NetworkInteractorLayout.Zones zones(final int contentHeight) {
        return NetworkInteractorLayout.resolve(contentW, contentHeight, extraCols, invRows);
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
     * panel pinned just above the footer, so the row position is derived from the live height, never from a
     * cached field, so the item lines up with its slot background from the very first frame. A row the band
     * has folded away lands above the band, where the desktop makes its slots inert.
     */
    @Override
    public int invCellContentY(final int row, final int contentHeight) {
        return NetworkInteractorLayout.slotRowY(zones(contentHeight), row);
    }

    /** The content-local y where the band's shown slots begin: rows placed above it are folded away. */
    @Override
    public int invBandTop(final int contentHeight) {
        return zones(contentHeight).invY();
    }

    /**
     * The content-local y just past the bottom row of inventory slots, for the given content height, the
     * desktop screen uses it as the band's lower visibility bound so the shown slots are all counted visible.
     */
    @Override
    public int invBandBottom(final int contentHeight) {
        return NetworkInteractorLayout.slotRowY(zones(contentHeight), INV_ROWS - 1) + CELL;
    }

    /** Whether a content-local point falls within an inventory slot cell (the framed, always-visible band). */
    private boolean inInventoryZone(final double lx, final double ly) {
        return NetworkInteractorLayout.inventorySlotAt((int) lx, (int) ly, zones()) >= 0;
    }

    private boolean gridTab() {
        return tab == TAB_NETWORK || tab == TAB_LOCAL || tab == TAB_CRAFTING || tab == TAB_FAV;
    }

    /** The tabs that list network entries (as opposed to craftable results). */
    private boolean entryTab() {
        return tab == TAB_NETWORK || tab == TAB_LOCAL || tab == TAB_FAV;
    }

    private List<String> tabLabels() {
        final List<String> out = new ArrayList<>(BASE_TABS);
        out.add(contentW >= FAV_LABEL_MIN_W ? STAR + " Favourites" : STAR);
        return out;
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
        lastFont = font;
        /*
         * Keep the view live: a few times a second, re-ask for the storage grid (and the live operations on the
         * Operations tab) so stock counts and craft progress update on their own, without a manual refresh.
         */
        if (++refreshFrames >= 20) {
            refreshFrames = 0;
            request();
        }
        final NetworkInteractorLayout.Zones z = zones();
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());

        // Tab strip (fixed header).
        tabs.setBounds(x, y, width, TAB_H);
        tabs.setSelected(tab);

        // The toolbar band under the tabs: search + filters + sort sit on it (grid tabs only).
        final boolean onGrid = gridTab();
        g.fill(x, y + TAB_H, x + width, y + NetworkInteractorLayout.HEADER_H, skin.panelBg());
        g.fill(x, y + NetworkInteractorLayout.HEADER_H - 1, x + width, y + NetworkInteractorLayout.HEADER_H, skin.edge());
        search.setBounds(x + z.searchX(), y + z.searchY(), z.searchW(), SEARCH_H);
        search.setVisible(onGrid);
        modButton.setBounds(x + z.modX(), y + z.searchY(), z.modW(), SEARCH_H);
        modButton.setVisible(onGrid);
        modButton.setPrimary(!modFilter.isEmpty());
        categoryButton.setBounds(x + z.catX(), y + z.searchY(), z.catW(), SEARCH_H);
        categoryButton.setVisible(onGrid);
        categoryButton.setPrimary(!categoryFilter.isEmpty());
        sortButton.setBounds(x + z.sortX(), y + z.searchY(), z.sortW(), SEARCH_H);
        sortButton.setVisible(onGrid);

        /*
         * The grid zone: a captioned, sunken well between the toolbar and the grip strip. Every cell that fits
         * is drawn, empty ones too, so the field reads as a storage grid; the grid scrolls its ITEMS, not its
         * pixels: the rows shown change.
         */
        final int gridTop = y + z.gridY();
        final int gridRows = Math.max(1, z.gridRows());
        final int shownCells = z.gridRows() * z.gridCols();
        grid.setColumns(z.gridCols()).setVisibleRows(gridRows).setTotalRows(totalItemRows(z))
                .setCellCount(Math.max(gridCount(), (grid.scroll() + z.gridRows()) * z.gridCols()));
        grid.place(x + z.gridX(), gridTop);
        grid.setVisible(onGrid && z.gridH() > 0);
        gridBar.setBounds(x + z.wellX() + z.wellW() - NetworkInteractorLayout.INV_PAD + 1, gridTop, SCROLLBAR_W, z.gridH());
        gridBar.setVisible(onGrid && z.gridH() > 0 && grid.maxScroll() > 0);
        // The list stops short of the vertical grip, so a press on the grip drags it instead of picking a row.
        opList.setBounds(x + z.gridX(), gridTop, z.gridW(), Math.max(OP_ROW_H, z.gridH()));
        opList.setVisible(tab == TAB_OPS && z.gridH() > 0 && !allOps().isEmpty());
        clampKeyCell();

        if (tab == TAB_STATUS) {
            // The Status tab is the network's card at full width, the same card the details show when idle.
            renderStatusTab(g, font, x, y, z);
        } else {
            renderCaption(g, font, x, y, z);
            skin.field(g, x + z.wellX(), y + z.wellY(), z.wellW(), z.wellH(), false);
            // What the well says when there are no cells or rows to show, clipped to it.
            if (z.gridH() > 0) {
                Draw.pushScissor(g, x + z.gridX(), gridTop, x + z.gridX() + z.gridW(), gridTop + z.gridH());
                final String msg = emptyMessage();
                if (msg != null && shownCells > 0) {
                    drawWrapped(g, font, msg, x + z.gridX() + 2, gridTop + 2, z.gridW() - 4, skin.dim());
                }
                if (tab == TAB_OPS) {
                    renderOpsHeader(g, font, x + z.gridX(), gridTop);
                }
                Draw.popScissor(g);
            }
        }

        /*
         * The inventory well: a pinned, sunken field with the shown slot backgrounds; the desktop screen draws
         * the real container items and the cursor over it. Always fully visible, never clipped.
         */
        renderGrips(g, font, x, y, z);
        renderInventoryBand(g, font, x, y, z);

        // The details panel (right column): the item in view, the selection, the network's card, or the
        // selected Operation on the Ops tab.
        final boolean detailButtons = tab != TAB_OPS && tab != TAB_STATUS;
        detailRequest.setVisible(false);
        detailCraft.setVisible(false);
        detailStar.setVisible(false);
        cardShown = false;
        if (tab == TAB_OPS) {
            renderOpDetails(g, font, x, y, z);
        } else if (tab != TAB_STATUS) {
            renderDetails(g, font, x, y, z, mouseX, mouseY, detailButtons);
        }

        // The status bar (fixed footer), then the hint line under it.
        renderStatusBar(g, font, x, y + z.statusY(), width);
        hintLabel.setBounds(x + 3, y + z.hintY(), width - 6, NetworkInteractorLayout.HINT_H);

        root.render(g, ctx);
        renderToolbarMarks(g);
        if (marquee) {
            // The rubber band, over the grid: a faint fill with the accent around it.
            final int x0 = Math.min(pressX, bandX);
            final int y0 = Math.min(pressY, bandY);
            final int x1 = Math.max(pressX, bandX);
            final int y1 = Math.max(pressY, bandY);
            g.fill(x0, y0, x1, y1, 0x334A90E2);
            Draw.outline(g, x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0), skin.accent());
        }
    }

    /** What the well says when the active tab has nothing to list, or null when it has. */
    @Nullable
    private String emptyMessage() {
        return switch (tab) {
            case TAB_CRAFTING -> filteredCrafts().isEmpty()
                    ? (!search.query().isEmpty() || filtering()
                            ? "No crafts match the search and filters."
                            : "No patterns on the network. Load .craft files on a Crafting Computer.")
                    : null;
            case TAB_FAV -> gridEntries().isEmpty()
                    ? (favourites.isEmpty()
                            ? "Nothing starred yet. Press F on an item, or its star in the details."
                            : "No favourites match the search and filters.")
                    : null;
            case TAB_NETWORK, TAB_LOCAL -> gridEntries().isEmpty()
                    ? (!search.query().isEmpty() || filtering() ? "Nothing matches the search and filters."
                            : (tab == TAB_LOCAL ? "Nothing on this computer's disks." : "Nothing on the network."))
                    : null;
            default -> null;
        };
    }

    /** The caption strip over the well: what the tab lists and how much of it. */
    private void renderCaption(final GuiGraphics g, final Font font, final int x, final int y,
                               final NetworkInteractorLayout.Zones z) {
        final int cx = x + z.wellX();
        final int cy = y + z.capY();
        Texts.small(g, font, gridCaption(), cx + 1, cy, skin.dim());
        final String right = captionCount();
        Texts.small(g, font, right, cx + z.wellW() - 1 - Texts.smallWidth(font, right), cy, skin.dim());
    }

    /** The caption's left side: the tab's name for what it lists. */
    private String gridCaptionText() {
        return switch (tab) {
            case TAB_LOCAL -> "LOCAL STORAGE";
            case TAB_CRAFTING -> "CRAFTABLE";
            case TAB_FAV -> "FAVOURITES";
            case TAB_OPS -> "OPERATIONS";
            default -> "NETWORK STORAGE";
        };
    }

    /** The caption's right side: the count of what the tab lists. */
    private String captionCount() {
        return switch (tab) {
            case TAB_LOCAL -> localItems.size() + " types";
            case TAB_CRAFTING -> crafts.size() + (crafts.size() == 1 ? " recipe" : " recipes");
            case TAB_FAV -> favourites.size() + " starred";
            case TAB_OPS -> activeOps.size() + " live · " + recentOps.size() + " recent";
            default -> networkItems.size() + " types · " + dataLabel(usedItems);
        };
    }

    /** The status bar: the Mainframe, the counts, and the storage gauge, in segments. */
    private void renderStatusBar(final GuiGraphics g, final Font font, final int x, final int y, final int width) {
        final int h = NetworkInteractorLayout.STATUS_H;
        skin.statusBar(g, x, y, width, h);
        final int ty = y + 2;
        int sx = x + 3;
        sx = statusSegment(g, font, sx, ty, online ? "● Mainframe online" : "○ Mainframe offline",
                online ? ONLINE_GREEN : OFFLINE_RED, y, h);
        sx = statusSegment(g, font, sx, ty, networkItems.size() + " types", skin.text(), y, h);
        statusSegment(g, font, sx, ty, serverCount + (serverCount == 1 ? " server" : " servers"), skin.text(), y, h);
        // The storage gauge, right: a small bar and the figures.
        final String figures = capacityItems > 0 ? dataLabel(usedItems) + " / " + dataLabel(capacityItems)
                : dataLabel(usedItems) + " stored";
        final int fw = Texts.smallWidth(font, figures);
        final int fx = x + width - 3 - fw;
        Texts.small(g, font, figures, fx, ty, skin.dim());
        if (capacityItems > 0) {
            final int bw = 30;
            final int bx = fx - 4 - bw;
            g.fill(bx, ty + 1, bx + bw, ty + 6, skin.fieldBg());
            Draw.outline(g, bx, ty + 1, bw, 5, skin.edge());
            final int fill = (int) Math.min(bw - 2, Math.round((bw - 2) * (double) usedItems / capacityItems));
            g.fill(bx + 1, ty + 2, bx + 1 + fill, ty + 5, gaugeColor());
        }
    }

    /** Draws one status segment and returns where the next begins; a thin rule closes it. */
    private int statusSegment(final GuiGraphics g, final Font font, final int sx, final int ty, final String text,
                              final int color, final int barY, final int barH) {
        Texts.small(g, font, text, sx, ty, color);
        final int end = sx + Texts.smallWidth(font, text) + 5;
        g.fill(end, barY + 2, end + 1, barY + barH - 1, halfEdge());
        return end + 5;
    }

    /** The gauge's colour: green with room, amber past three quarters, red when nearly full. */
    private int gaugeColor() {
        if (capacityItems <= 0) {
            return ONLINE_GREEN;
        }
        final double share = (double) usedItems / capacityItems;
        return share >= 0.95 ? SHORT_RED : share >= 0.75 ? AMBER : ONLINE_GREEN;
    }

    /** The skin's edge at half strength: the rules inside a panel, the cell separators. */
    private int halfEdge() {
        return (skin.edge() & 0x00FFFFFF) | 0x60000000;
    }

    /** The toolbar's small marks, drawn after the tree: the search's magnifier and the drop-downs' carets. */
    private void renderToolbarMarks(final GuiGraphics g) {
        if (!gridTab()) {
            return;
        }
        if (!search.isFocused() && search.edit().isEmpty()) {
            // A magnifier at the field's right end: a ring and a handle, in the placeholder's tone.
            final int mx = search.right() - 10;
            final int my = search.y() + 3;
            Draw.outline(g, mx, my, 5, 5, skin.dim());
            g.fill(mx + 4, my + 4, mx + 5, my + 5, skin.dim());
            g.fill(mx + 5, my + 5, mx + 7, my + 7, skin.dim());
        }
        caret(g, modButton);
        caret(g, categoryButton);
    }

    /** A small downward caret at a drop-down button's right end. */
    private void caret(final GuiGraphics g, final Button button) {
        final int cx = button.right() - 7;
        final int cy = button.y() + button.height() / 2 - 1;
        g.fill(cx, cy, cx + 5, cy + 1, skin.dim());
        g.fill(cx + 1, cy + 1, cx + 4, cy + 2, skin.dim());
        g.fill(cx + 2, cy + 2, cx + 3, cy + 3, skin.dim());
    }

    private void selectTab(final int index) {
        tab = index;
        lastTab = index; // remember it so the next reopen lands here
        root.focus(null);
        grid.setScroll(0);
        keyCell = -1;
        anchorCell = -1;
        selected.clear();
        if (index == TAB_OPS) {
            opSelected = -1;
            opList.setScroll(0);
            requestOps();
        }
    }

    private void searchEdited() {
        grid.setScroll(0);
        keyCell = -1;
        lastSearch = search.edit();
    }

    private String sortLabel() {
        // The button always names the order it is in, so the player can see the mode without clicking it.
        return SORT_LABELS[sortMode];
    }

    private void cycleSort() {
        sortMode = (sortMode + 1) % SORT_MODES;
        lastSort = sortMode;
    }

    private String hintText() {
        return gridTab() ? HINT : "";
    }

    // filters

    private boolean filtering() {
        return !modFilter.isEmpty() || !categoryFilter.isEmpty();
    }

    private String modLabel() {
        final String text = modFilter.isEmpty() ? "Mod" : modName(modFilter);
        return lastFont == null ? text : Texts.clip(lastFont, text, Texts.smallFits(NetworkInteractorLayout.MOD_W - 4));
    }

    private String categoryLabel() {
        final String text = categoryFilter.isEmpty() ? "Category" : categoryFilter;
        return lastFont == null ? text : Texts.clip(lastFont, text, Texts.smallFits(NetworkInteractorLayout.CAT_W - 4));
    }

    /** The mods that made what the active tab lists, as namespaces, sorted by their readable names. */
    private List<String> modOptions() {
        final TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, String> byName = new HashMap<>();
        for (final StorageKey key : sourceKeys()) {
            final String ns = key.registryId().getNamespace();
            final String name = modName(ns);
            names.add(name);
            byName.put(name, ns);
        }
        final List<String> out = new ArrayList<>();
        for (final String name : names) {
            out.add(byName.get(name));
        }
        return out;
    }

    /** The categories what the active tab lists falls into, in the filter's fixed order. */
    private List<String> categoryOptions() {
        final Set<String> present = new HashSet<>();
        for (final StorageKey key : sourceKeys()) {
            present.add(ItemCategories.of(key));
        }
        final List<String> out = new ArrayList<>();
        for (final String category : ItemCategories.ALL) {
            if (present.contains(category)) {
                out.add(category);
            }
        }
        return out;
    }

    /** Every key the active tab could list before the filters, so the drop-downs offer what is there. */
    private List<StorageKey> sourceKeys() {
        final List<StorageKey> out = new ArrayList<>();
        if (tab == TAB_CRAFTING) {
            for (final CraftCatalogPayload.Entry e : crafts) {
                out.add(StorageKey.of(e.result()));
            }
        } else {
            for (final NetworkItemEntry e : sourceEntries()) {
                out.add(e.key());
            }
        }
        return out;
    }

    private void openModFilter() {
        final List<ContextMenu.Item> items = new ArrayList<>();
        items.add(new ContextMenu.Item(ANY_MOD, true, () -> setModFilter("")));
        for (final String ns : modOptions()) {
            items.add(new ContextMenu.Item(modName(ns), true, () -> setModFilter(ns)));
        }
        filterMenu.open(items, modButton.x(), modButton.bottom(), lastX, lastY, contentW, contentH);
    }

    private void openCategoryFilter() {
        final List<ContextMenu.Item> items = new ArrayList<>();
        items.add(new ContextMenu.Item(ANY_CATEGORY, true, () -> setCategoryFilter("")));
        for (final String category : categoryOptions()) {
            items.add(new ContextMenu.Item(category, true, () -> setCategoryFilter(category)));
        }
        filterMenu.open(items, categoryButton.x(), categoryButton.bottom(), lastX, lastY, contentW, contentH);
    }

    private void setModFilter(final String ns) {
        modFilter = ns;
        lastMod = ns;
        grid.setScroll(0);
        keyCell = -1;
    }

    private void setCategoryFilter(final String category) {
        categoryFilter = category;
        lastCategory = category;
        grid.setScroll(0);
        keyCell = -1;
    }

    /** Whether a key passes the mod and category filters (the search is applied by name elsewhere). */
    private boolean passesFilters(final StorageKey key) {
        if (!modFilter.isEmpty() && !key.registryId().getNamespace().equals(modFilter)) {
            return false;
        }
        return categoryFilter.isEmpty() || ItemCategories.of(key).equals(categoryFilter);
    }

    // the grid's lists

    /** The unfiltered entries behind the active entry tab. */
    private List<NetworkItemEntry> sourceEntries() {
        return switch (tab) {
            case TAB_LOCAL -> localItems;
            case TAB_FAV -> favouriteEntries();
            default -> networkItems;
        };
    }

    /** The entries the active entry tab lists, after the search, the filters and the sort. */
    private List<NetworkItemEntry> gridEntries() {
        return filtered(sourceEntries());
    }

    /**
     * The starred data: what the network holds of it, and a craftable result not in stock as a zero-count
     * entry, so a favourite the network can make is still there to craft.
     */
    private List<NetworkItemEntry> favouriteEntries() {
        if (favouritesVersion == listVersion) {
            return favouritesCache;
        }
        final List<NetworkItemEntry> out = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        for (final NetworkItemEntry e : networkItems) {
            if (favourites.contains(e.key().id()) && seen.add(e.key().id())) {
                out.add(e);
            }
        }
        for (final CraftCatalogPayload.Entry e : crafts) {
            final StorageKey key = StorageKey.of(e.result());
            if (favourites.contains(key.id()) && seen.add(key.id())) {
                out.add(new NetworkItemEntry(key, 0L));
            }
        }
        favouritesCache = out;
        favouritesVersion = listVersion;
        return out;
    }

    /** The number of cells the active grid tab has, for the grid to draw and click that many. */
    private int gridCount() {
        return switch (tab) {
            case TAB_NETWORK, TAB_LOCAL, TAB_FAV -> gridEntries().size();
            case TAB_CRAFTING -> filteredCrafts().size();
            default -> 0;
        };
    }

    /** The number of item rows the visible (and scrollable) source has, for the active grid tab. */
    private int totalItemRows(final NetworkInteractorLayout.Zones z) {
        final int cols = Math.max(1, z.gridCols());
        return (gridCount() + cols - 1) / cols;
    }

    /** Whether the cell at {@code index} holds a selected key, for the grid's row backgrounds. */
    private boolean isSelectedIndex(final int index) {
        final StorageKey key = keyAt(index);
        return key != null && selected.contains(key);
    }

    /**
     * One cell of the well: a light separator around it (drawn for empty cells too, so the field reads as a
     * grid), then the item with its count in the corner, the star, the craftable's availability mark, and
     * the keyboard's dotted frame.
     */
    private void renderGridCell(final GuiGraphics g, final UiContext ctx, final int index, final int cx, final int cy,
                                final int size, final int cellHeight, final boolean hovered) {
        Draw.outline(g, cx, cy, size, cellHeight, halfEdge());
        final StorageKey key;
        String count = null;
        int mark = 0;
        if (tab == TAB_CRAFTING) {
            final List<CraftCatalogPayload.Entry> list = filteredCrafts();
            if (index >= list.size()) {
                return;
            }
            final CraftCatalogPayload.Entry e = list.get(index);
            DesktopItems.item(g, e.result(), cx + 1, cy + 1);
            mark = switch (e.availability()) {
                case CraftCatalogPayload.DOT_GREEN -> 0xFF3CC75A;
                case CraftCatalogPayload.DOT_AMBER -> AMBER;
                default -> 0xFFD05050;
            };
            key = StorageKey.of(e.result());
        } else {
            final List<NetworkItemEntry> items = gridEntries();
            if (index >= items.size()) {
                return;
            }
            final NetworkItemEntry e = items.get(index);
            DesktopItems.data(g, ctx.font(), e.key(), cx + 1, cy + 1, null);
            count = e.total() > 0 ? formatCount(e.total()) : null;
            key = e.key();
        }
        final boolean chosen = selected.contains(key);
        // What sits in front of the model rides at the count's depth, like the vanilla count does.
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, DesktopZ.countOffset());
        if (count != null) {
            final int w = Texts.smallWidth(ctx.font(), count);
            final int tx = cx + size - w - 1;
            final int ty = cy + cellHeight - 8;
            g.fill(tx - 1, ty - 1, tx + w + 1, ty + 7, chosen ? 0x00000000 : (skin.fieldBg() & 0x00FFFFFF) | 0xC0000000);
            Texts.small(g, ctx.font(), count, tx, ty, skin.listRowText(chosen));
        }
        if (mark != 0) {
            // The availability mark: a small colour tab in the count's corner.
            g.fill(cx + size - 6, cy + cellHeight - 4, cx + size - 1, cy + cellHeight - 1, mark);
        }
        if (favourites.contains(key.id())) {
            Texts.small(g, ctx.font(), STAR, cx + 1, cy, STAR_GOLD);
        }
        if (index == keyCell) {
            dottedOutline(g, cx + 1, cy + 1, size - 2, cellHeight - 2, STAR_GOLD);
        }
        g.pose().popPose();
    }

    /** A dotted rectangle: the keyboard's cell, told from the mouse's lit one. */
    private static void dottedOutline(final GuiGraphics g, final int x, final int y, final int w, final int h, final int color) {
        for (int i = 0; i < w; i += 2) {
            g.fill(x + i, y, x + i + 1, y + 1, color);
            g.fill(x + i, y + h - 1, x + i + 1, y + h, color);
        }
        for (int i = 0; i < h; i += 2) {
            g.fill(x, y + i, x + 1, y + i + 1, color);
            g.fill(x + w - 1, y + i, x + w, y + i + 1, color);
        }
    }

    /**
     * A click on a cell selects it (with Control it joins or leaves the selection, with Shift the cells from
     * the anchor join); a second click on the same cell within the double-click time opens it.
     */
    private void gridCellClicked(final int index, final int button, final boolean shift) {
        if (keyAt(index) == null) {
            // A drawn but empty cell: the press is on the well's empty part, and is treated as such.
            cellHit = false;
            return;
        }
        final boolean control = Screen.hasControlDown();
        final long now = System.currentTimeMillis();
        final boolean second = button == 0 && !shift && !control && index == lastClickCell
                && now - lastClickAt <= DOUBLE_CLICK_MS;
        lastClickCell = index;
        lastClickAt = now;
        cellHit = true;
        if (second) {
            lastClickCell = -1;
            activateCell(index);
            return;
        }
        selectCell(index, shift, control);
        gridPressed = true;
    }

    /** The key of the cell at {@code index} on the active grid tab, or null past the list. */
    @Nullable
    private StorageKey keyAt(final int index) {
        if (index < 0) {
            return null;
        }
        if (tab == TAB_CRAFTING) {
            final List<CraftCatalogPayload.Entry> list = filteredCrafts();
            return index < list.size() ? StorageKey.of(list.get(index).result()) : null;
        }
        final List<NetworkItemEntry> items = gridEntries();
        return index < items.size() ? items.get(index).key() : null;
    }

    /** Puts the keyboard on {@code index} and shapes the selection the way the modifiers ask. */
    private void selectCell(final int index, final boolean extend, final boolean toggle) {
        final StorageKey key = keyAt(index);
        if (key == null) {
            return;
        }
        keyCell = index;
        if (toggle) {
            if (!selected.remove(key)) {
                selected.add(key);
            }
            anchorCell = index;
            return;
        }
        if (extend && anchorCell >= 0) {
            selected.clear();
            for (int i = Math.min(anchorCell, index); i <= Math.max(anchorCell, index); i++) {
                final StorageKey k = keyAt(i);
                if (k != null) {
                    selected.add(k);
                }
            }
            return;
        }
        selected.clear();
        selected.add(key);
        anchorCell = index;
    }

    /** Selects every cell the active grid tab lists. */
    private void selectAll() {
        selected.clear();
        final int count = gridCount();
        for (int i = 0; i < count; i++) {
            final StorageKey k = keyAt(i);
            if (k != null) {
                selected.add(k);
            }
        }
        if (keyCell < 0 && count > 0) {
            keyCell = 0;
        }
    }

    /** Opens what the cell holds: the request dialog for an entry, the craft dialog for a craftable. */
    private void activateCell(final int index) {
        if (tab == TAB_CRAFTING) {
            final List<CraftCatalogPayload.Entry> list = filteredCrafts();
            if (index >= 0 && index < list.size()) {
                openCraftPopup(list.get(index));
            }
            return;
        }
        final List<NetworkItemEntry> items = gridEntries();
        if (index >= 0 && index < items.size()) {
            activateEntry(items.get(index));
        }
    }

    /** The selected entries of the active entry tab, in the order the grid lists them. */
    private List<NetworkItemEntry> selectedEntries() {
        final List<NetworkItemEntry> out = new ArrayList<>();
        if (!entryTab()) {
            return out;
        }
        for (final NetworkItemEntry e : gridEntries()) {
            if (selected.contains(e.key())) {
                out.add(e);
            }
        }
        return out;
    }

    /** Opens the selection: one entry the usual way, several as one request dialog for all of them. */
    private void activateSelection() {
        final List<NetworkItemEntry> entries = selectedEntries();
        if (entries.size() > 1) {
            final List<NetworkItemEntry> stocked = new ArrayList<>();
            for (final NetworkItemEntry e : entries) {
                if (e.total() > 0) {
                    stocked.add(e);
                }
            }
            if (!stocked.isEmpty()) {
                openPopup(stocked, tab == TAB_LOCAL);
            }
            return;
        }
        activateCell(keyCell);
    }

    /** The keys F and the star act on: the selection, else the keyboard's cell. */
    private List<StorageKey> actionKeys() {
        if (!selected.isEmpty()) {
            return new ArrayList<>(selected);
        }
        final StorageKey key = keyAt(keyCell);
        return key == null ? List.of() : List.of(key);
    }

    /** Stars every key given, or unstars them all when every one is starred already. */
    private void toggleFavourites(final List<StorageKey> keys) {
        if (keys.isEmpty()) {
            return;
        }
        boolean allStarred = true;
        for (final StorageKey key : keys) {
            if (!favourites.contains(key.id())) {
                allStarred = false;
                break;
            }
        }
        for (final StorageKey key : keys) {
            if (favourites.contains(key.id()) == allStarred) {
                toggleFavourite(key);
            }
        }
    }

    /** Opens the entry's dialog: the request/storage one, or the craft one for a starred result not in stock. */
    private void activateEntry(final NetworkItemEntry entry) {
        if (entry.total() <= 0) {
            final CraftCatalogPayload.Entry craftable = craftEntryFor(entry.key());
            if (craftable != null) {
                openCraftPopup(craftable);
            }
            return;
        }
        // Open the request/storage quantity dialog (MC-NET style) instead of pulling a fixed amount.
        openPopup(List.of(entry), tab == TAB_LOCAL);
    }

    /** The catalog entry whose result is {@code key}, or null when the network does not make it. */
    @Nullable
    private CraftCatalogPayload.Entry craftEntryFor(final StorageKey key) {
        for (final CraftCatalogPayload.Entry e : crafts) {
            if (key.equals(StorageKey.of(e.result()))) {
                return e;
            }
        }
        return null;
    }

    // the grips and the inventory band

    private void renderGrips(final GuiGraphics g, final Font font, final int x, final int y,
                             final NetworkInteractorLayout.Zones z) {
        // The horizontal grip strip: the well's caption at the left, the grip mark in the middle.
        final int sy = y + z.gripY();
        Texts.small(g, font, "INVENTORY", x + z.invBandX() + 1, sy + 1, skin.dim());
        final int hx = x + z.invBandX() + z.invBandW() / 2 - 10;
        final int hover = dragGrip == 2 || NetworkInteractorLayout.onHorizontalGrip(lastMouseX - x, lastMouseY - y, z)
                ? skin.accent() : skin.edge();
        g.fill(hx, sy + 3, hx + 20, sy + 4, hover);
        g.fill(hx, sy + 5, hx + 20, sy + 6, hover);
        // The vertical grip: between the left column and the details panel, its mark half way down.
        final int vx = x + z.gripX() + NetworkInteractorLayout.GAP / 2 - 1;
        final int vy = y + z.capY() + (z.invBandY() + z.invBandH() - z.capY()) / 2 - 10;
        final int vHover = dragGrip == 1 || NetworkInteractorLayout.onVerticalGrip(lastMouseX - x, lastMouseY - y, z)
                ? skin.accent() : skin.edge();
        g.fill(vx, vy, vx + 1, vy + 20, vHover);
        g.fill(vx + 2, vy, vx + 3, vy + 20, vHover);
    }

    private void renderInventoryBand(final GuiGraphics g, final Font font, final int x, final int y,
                                     final NetworkInteractorLayout.Zones z) {
        // The band is a sunken well of the era, so the inventory reads as a distinct, bounded area.
        final int bx = x + z.invBandX();
        final int by = y + z.invBandY();
        final int bw = z.invBandW();
        final int bh = z.invBandH();
        skin.field(g, bx, by, bw, bh, false);

        /*
         * Slot backgrounds inside the frame (the desktop screen draws the real items and cursor over these),
         * using the shared slotRowY so the rows shown, the gap and the hotbar line up exactly with the real slots.
         */
        for (int r = z.invFirstRow(); r < INV_ROWS; r++) {
            for (int c = 0; c < INV_COLS; c++) {
                final int cx = x + z.invX() + c * CELL;
                final int cy = y + NetworkInteractorLayout.slotRowY(z, r);
                g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, skin.panelBg());
                Draw.outline(g, cx, cy, CELL - 2, CELL - 2, halfEdge());
            }
        }
    }

    /** The framed panel every detail view sits in: the era's group panel. */
    private void framedPanel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        skin.panel(g, x, y, w, h);
    }

    /**
     * The panel's header strip: an icon, a name and a line under it, on a band of its own with a rule
     * beneath. Returns the y where the panel's body begins.
     */
    private int panelHeader(final GuiGraphics g, final Font font, final int dx, final int dy, final int dw,
                            @Nullable final StorageKey icon, final String name, final String sub, final int subColor,
                            final boolean starred) {
        final int hh = 22;
        g.fill(dx + 1, dy + 1, dx + dw - 1, dy + hh, skin.panelBg());
        g.fill(dx + 1, dy + hh, dx + dw - 1, dy + hh + 1, halfEdge());
        final int px = dx + 5;
        int tx = px;
        if (icon != null) {
            DesktopItems.data(g, font, icon, px, dy + 3, null);
            tx = px + 20;
        }
        final int starW = starred ? Texts.smallWidth(font, STAR) + 2 : 0;
        final String shown = Texts.clip(font, name, Texts.smallFits(dw - (tx - dx) - 6 - starW));
        Texts.small(g, font, shown, tx, dy + 4, skin.text());
        if (starred) {
            Texts.small(g, font, STAR, tx + Texts.smallWidth(font, shown) + 2, dy + 4, STAR_GOLD);
        }
        Texts.small(g, font, Texts.clip(font, sub, Texts.smallFits(dw - (tx - dx) - 6)), tx, dy + 13, subColor);
        return dy + hh + 5;
    }

    /**
     * The network's card: the Mainframe, the storage gauge, what is on the network and what is running. The
     * details panel shows it when nothing is chosen, and the Status tab shows it at full width.
     */
    private void renderNetworkCard(final GuiGraphics g, final Font font, final int dx, final int dy, final int dw,
                                   final int dh) {
        cardShown = true;
        final int px = dx + 5;
        int py = panelHeader(g, font, dx, dy, dw, null, "This network",
                online ? "● Mainframe online" : "○ Mainframe offline", online ? ONLINE_GREEN : OFFLINE_RED, false);
        py = sectionRule(g, font, px, py, dw, "STORAGE");
        final int gw = dw - 12;
        g.fill(px, py, px + gw, py + 5, skin.fieldBg());
        Draw.outline(g, px, py, gw, 5, skin.edge());
        if (capacityItems > 0) {
            final int fill = (int) Math.min(gw - 2, Math.round((gw - 2) * (double) usedItems / capacityItems));
            g.fill(px + 1, py + 1, px + 1 + fill, py + 4, gaugeColor());
        }
        py += 8;
        py = cardRow(g, font, px, py, dw, "used", capacityItems > 0
                ? dataLabel(usedItems) + " of " + dataLabel(capacityItems) : dataLabel(usedItems));
        py = sectionRule(g, font, px, py + 2, dw, "ON THE NETWORK");
        py = cardRow(g, font, px, py, dw, "Item types", Integer.toString(networkItems.size()));
        py = cardRow(g, font, px, py, dw, "Servers", Integer.toString(serverCount));
        py = cardRow(g, font, px, py, dw, "Craftable", Integer.toString(crafts.size()));
        py = cardRow(g, font, px, py, dw, "Favourites", Integer.toString(favourites.size()));
        py = sectionRule(g, font, px, py + 2, dw, "RUNNING");
        py = cardRow(g, font, px, py, dw, "Operations", activeOps.size() + " live");
        if (py + 10 < dy + dh) {
            Texts.small(g, font, "Select an item to see its details.", px, py + 4, skin.dim());
        }
    }

    /** A caption with a thin rule under it; returns the y of the first line below. */
    private int sectionRule(final GuiGraphics g, final Font font, final int px, final int py, final int dw,
                            final String caption) {
        Texts.small(g, font, caption, px, py, skin.dim());
        g.fill(px, py + 8, px + dw - 12, py + 9, halfEdge());
        return py + 11;
    }

    /** A "label ... value" row of the card. */
    private int cardRow(final GuiGraphics g, final Font font, final int px, final int py, final int dw,
                        final String label, final String value) {
        Texts.small(g, font, label, px, py, skin.dim());
        Texts.small(g, font, value, px + dw - 12 - Texts.smallWidth(font, value), py, skin.text());
        return py + 8;
    }

    /** The Status tab: the network's card across the whole body, in its own framed panel. */
    private void renderStatusTab(final GuiGraphics g, final Font font, final int x, final int y,
                                 final NetworkInteractorLayout.Zones z) {
        final int px = x + z.wellX();
        final int py = y + z.capY();
        final int pw = contentW - 2 * NetworkInteractorLayout.INSET;
        final int ph = z.statusY() - z.capY() - 2;
        if (pw <= 6 || ph <= 6) {
            return;
        }
        framedPanel(g, px, py, pw, ph);
        Draw.pushScissor(g, px + 1, py + 1, px + pw - 1, py + ph - 1);
        renderNetworkCard(g, font, px, py, Math.min(pw, 180), ph);
        Draw.popScissor(g);
    }

    // details panel

    /**
     * The right-hand item details panel for the item in view (under the mouse, else under the keyboard):
     * its name and star, what the network holds and who made it, what makes it and what it is used in, the
     * three actions, and then its id, kind, weight, durability, tags and components.
     */
    private void renderDetails(final GuiGraphics g, final Font font, final int x, final int y,
                               final NetworkInteractorLayout.Zones z, final int mouseX, final int mouseY,
                               final boolean withButtons) {
        final int dx = x + z.detailsX();
        final int dy = y + z.detailsY();
        final int dw = z.detailsW();
        final int dh = z.detailsH();
        if (dw <= 6 || dh <= 6) {
            return;
        }
        framedPanel(g, dx, dy, dw, dh);
        Draw.pushScissor(g, dx + 1, dy + 1, dx + dw - 1, dy + dh - 1);
        final int px = dx + 5;
        if (hoveredGridEntry(mouseX, mouseY) == null && selected.size() > 1) {
            renderSelectionDetails(g, font, dx, dy, dw, withButtons);
            Draw.popScissor(g);
            return;
        }
        final NetworkItemEntry e = detailEntry(mouseX, mouseY);
        if (e == null) {
            renderNetworkCard(g, font, dx, dy, dw, dh);
            Draw.popScissor(g);
            return;
        }
        final StorageKey key = e.key();
        final ItemStack stack = e.icon(); // empty for a fluid or a chemical
        final ResourceLocation id = key.registryId();
        final boolean starred = favourites.contains(key.id());
        int py = panelHeader(g, font, dx, dy, dw, key, e.name().getString(), modName(id.getNamespace()), LINK_BLUE, starred);
        // Where it is: the network total and the servers that hold it, on one line.
        Texts.small(g, font, Texts.clip(font, storedLine(e), Texts.smallFits(dw - 10)), px, py, skin.dim());
        py += 10;
        final ItemRecipesPayload known = recipesFor(key);
        py = detailList(g, font, px, py, dw, "MADE BY", known == null ? List.of("...") : known.madeBy());
        py = detailList(g, font, px, py, dw, "USED IN", known == null ? List.of("...") : known.usedIn());
        if (withButtons) {
            final int bw = Math.max(30, (dw - 10 - 8 - 14) / 2);
            detailRequest.setBounds(px, py, bw, 12);
            detailRequest.setVisible(true);
            detailRequest.setEnabled(e.total() > 0);
            detailCraft.setBounds(px + bw + 2, py, bw, 12);
            detailCraft.setVisible(true);
            detailCraft.setEnabled(craftEntryFor(key) != null);
            detailStar.setBounds(px + 2 * bw + 4, py, 14, 12);
            detailStar.setVisible(true);
            detailStar.setPrimary(starred);
            py += 16;
        }
        py = detail(g, font, px, py, dw, "ID", id.toString());
        py = detail(g, font, px, py, dw, "KIND", key.isItem() ? "Item" : key.isFluid() ? "Fluid" : "Chemical");
        py = detail(g, font, px, py, dw, "WEIGHT", dataLabel(key.weight(e.total())));
        if (key.isItem() && stack.isDamageableItem()) {
            py = detail(g, font, px, py, dw, "DURABILITY",
                    (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage());
        }
        if (key.isItem()) {
            py = detailList(g, font, px, py, dw, "TAGS", itemTags(stack));
            detailList(g, font, px, py, dw, "COMPONENTS", componentNames(stack));
        }
        Draw.popScissor(g);
    }

    /**
     * The details panel for a selection of several items: how many, each by name and amount, what they
     * weigh together, and the actions that take them all: Request and the star.
     */
    private void renderSelectionDetails(final GuiGraphics g, final Font font, final int dx, final int dy, final int dw,
                                        final boolean withButtons) {
        final int px = dx + 5;
        final List<NetworkItemEntry> entries = selectedEntries();
        final int count = tab == TAB_CRAFTING ? selected.size() : entries.size();
        long weight = 0L;
        long items = 0L;
        int stocked = 0;
        final List<String> lines = new ArrayList<>();
        final StringBuilder names = new StringBuilder();
        for (final NetworkItemEntry e : entries) {
            weight += e.key().weight(e.total());
            items += e.total();
            if (e.total() > 0) {
                stocked++;
            }
            lines.add(e.name().getString() + " · " + amount(e.key(), e.total()));
            names.append(names.length() > 0 ? ", " : "").append(e.name().getString());
        }
        if (tab == TAB_CRAFTING) {
            for (final StorageKey key : selected) {
                lines.add(key.displayName().getString());
                names.append(names.length() > 0 ? ", " : "").append(key.displayName().getString());
            }
        }
        int py = panelHeader(g, font, dx, dy, dw, null, count + " items selected", names.toString(), skin.dim(), false);
        py = detailList(g, font, px, py, dw, "SELECTED", lines.size() > 12 ? lines.subList(0, 12) : lines);
        if (tab != TAB_CRAFTING) {
            py = detail(g, font, px, py, dw, "TOGETHER", formatCount(items) + " items · " + dataLabel(weight));
        }
        if (lines.size() > 12) {
            Texts.small(g, font, "+" + (lines.size() - 12) + " more", px + 2, py - 2, skin.dim());
            py += 8;
        }
        if (withButtons) {
            final int bw = Math.max(30, (dw - 10 - 8 - 14) / 2);
            detailRequest.setBounds(px, py, bw, 12);
            detailRequest.setVisible(true);
            detailRequest.setEnabled(stocked > 0);
            detailCraft.setBounds(px + bw + 2, py, bw, 12);
            detailCraft.setVisible(true);
            detailCraft.setEnabled(false);
            detailStar.setBounds(px + 2 * bw + 4, py, 14, 12);
            detailStar.setVisible(true);
            detailStar.setPrimary(false);
        }
    }

    /** "1,248 in the network · Server 1, 2, 3 #1, ..." or "not in stock" for a craftable result. */
    private String storedLine(final NetworkItemEntry e) {
        if (e.total() <= 0) {
            return "not in stock" + (craftEntryFor(e.key()) != null ? " · craftable" : "");
        }
        final StringBuilder out = new StringBuilder(amountLabel(e.key(), e.total()) + " in the network");
        if (!e.shares().isEmpty()) {
            out.append(" · ");
            for (int i = 0; i < e.shares().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(e.shares().get(i).label());
            }
        }
        return out.toString();
    }

    /** What the server said makes and uses the key, asking once per snapshot when it has not said yet. */
    @Nullable
    private ItemRecipesPayload recipesFor(final StorageKey key) {
        final ItemRecipesPayload known = recipes.get(key);
        if (known == null && recipesAsked.add(key)) {
            PacketDistributor.sendToServer(new RequestItemRecipesPayload(host, monitorPos, key));
        }
        return known;
    }

    private void detailRequestPressed() {
        if (selected.size() > 1) {
            activateSelection();
            return;
        }
        final NetworkItemEntry e = detailEntry(lastMouseX, lastMouseY);
        if (e != null && e.total() > 0) {
            openPopup(List.of(e), tab == TAB_LOCAL);
        }
    }

    private void detailCraftPressed() {
        final NetworkItemEntry e = detailEntry(lastMouseX, lastMouseY);
        final CraftCatalogPayload.Entry craftable = e == null ? null : craftEntryFor(e.key());
        if (craftable != null) {
            openCraftPopup(craftable);
        }
    }

    private void detailStarPressed() {
        if (selected.size() > 1) {
            toggleFavourites(actionKeys());
            return;
        }
        final NetworkItemEntry e = detailEntry(lastMouseX, lastMouseY);
        if (e != null) {
            toggleFavourite(e.key());
        }
    }

    /** Stars or unstars a key: the machine keeps it, and the grid shows it at once. */
    private void toggleFavourite(final StorageKey key) {
        final String id = key.id();
        final boolean starred = favourites.contains(id);
        if (starred) {
            favourites.remove(id);
        } else {
            favourites.add(id);
        }
        favouritesVersion = -1;
        PacketDistributor.sendToServer(new SetSettingPayload(host, starred ? "unfavourite" : "favourite", id));
    }

    /** Draws a labelled list section (a ruled caption, then each entry on its own line; "(none)" if empty). */
    private int detailList(final GuiGraphics g, final Font font, final int px, final int py, final int dw,
                           final String label, final List<String> values) {
        int vy = sectionRule(g, font, px, py, dw, label);
        if (values.isEmpty()) {
            Texts.small(g, font, "(none)", px + 2, vy, skin.dim());
            return vy + 10;
        }
        for (final String v : values) {
            Texts.small(g, font, Texts.clip(font, v, Texts.smallFits(dw - 14)), px + 2, vy, skin.text());
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

    /** Draws a labelled detail line (a ruled caption, then the value wrapped to the panel width). Returns new y. */
    private int detail(final GuiGraphics g, final Font font, final int px, final int py, final int dw,
                       final String label, final String value) {
        int vy = sectionRule(g, font, px, py, dw, label);
        for (final String line : wrap(font, value, Texts.smallFits(dw - 14))) {
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

    /** Word-based wrap at the small scale, at most {@code lines} lines, the last one cut with an ellipsis. */
    private static List<String> wrapWords(final Font font, final String text, final int maxW, final int lines) {
        final List<String> out = new ArrayList<>();
        final StringBuilder line = new StringBuilder();
        for (final String word : text.split(" ")) {
            final String candidate = line.length() == 0 ? word : line + " " + word;
            if (Texts.smallWidth(font, candidate) > maxW && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
                if (out.size() == lines) {
                    break;
                }
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (out.size() < lines && line.length() > 0) {
            out.add(line.toString());
        }
        if (out.size() == lines) {
            out.set(lines - 1, Texts.clip(font, out.get(lines - 1), Texts.smallFits(maxW)));
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

    /** The entry the details panel is about: under the mouse, else under the keyboard's cell. */
    @Nullable
    private NetworkItemEntry detailEntry(final double mx, final double my) {
        final NetworkItemEntry hovered = hoveredGridEntry(mx, my);
        if (hovered != null) {
            return hovered;
        }
        return keyCell >= 0 ? entryAt(keyCell) : null;
    }

    /** The grid entry at an index of the active grid tab (a craftable as a zero-or-stock entry), or null. */
    @Nullable
    private NetworkItemEntry entryAt(final int index) {
        if (index < 0) {
            return null;
        }
        if (tab == TAB_CRAFTING) {
            final List<CraftCatalogPayload.Entry> list = filteredCrafts();
            return index < list.size() ? entryForResult(list.get(index)) : null;
        }
        if (!entryTab()) {
            return null;
        }
        final List<NetworkItemEntry> items = gridEntries();
        return index < items.size() ? items.get(index) : null;
    }

    /** A craftable result as a network entry: what the network holds of it, or nothing. */
    private NetworkItemEntry entryForResult(final CraftCatalogPayload.Entry e) {
        final StorageKey key = StorageKey.of(e.result());
        for (final NetworkItemEntry item : networkItems) {
            if (item.key().equals(key)) {
                return item;
            }
        }
        return new NetworkItemEntry(key, 0L);
    }

    /** The grid entry under a desktop-local point, or null when the cursor isn't over a grid item. */
    @Nullable
    private NetworkItemEntry hoveredGridEntry(final double mx, final double my) {
        if (!gridTab()) {
            return null;
        }
        return entryAt(grid.cellAt(mx, my));
    }

    /** The crafts shown after the search and the filters (by result); the full list when none apply. */
    private List<CraftCatalogPayload.Entry> filteredCrafts() {
        final String q = search.query();
        if (q.isEmpty() && !filtering()) {
            return crafts;
        }
        final String key = listVersion + "|" + q + "|" + modFilter + "|" + categoryFilter;
        if (key.equals(craftsKey)) {
            return craftsCache;
        }
        final List<CraftCatalogPayload.Entry> out = new ArrayList<>();
        for (final CraftCatalogPayload.Entry e : crafts) {
            final boolean named = q.isEmpty() || e.title().toLowerCase(Locale.ROOT).contains(q)
                    || e.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q);
            if (named && passesFilters(StorageKey.of(e.result()))) {
                out.add(e);
            }
        }
        craftsCache = out;
        craftsKey = key;
        return out;
    }

    private List<NetworkItemEntry> filtered(final List<NetworkItemEntry> source) {
        final String q = search.query();
        final String key = listVersion + "|" + sortMode + "|" + q + "|" + modFilter + "|" + categoryFilter + "|" + favouritesVersion;
        if (source == filteredSource && key.equals(filteredKey)) {
            return filteredCache;
        }
        final List<NetworkItemEntry> out = new ArrayList<>();
        for (final NetworkItemEntry e : source) {
            if ((q.isEmpty() || e.name().getString().toLowerCase(Locale.ROOT).contains(q)) && passesFilters(e.key())) {
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

    // the keyboard on the grid

    /** Keeps the keyboard's cell inside the list it indexes, dropping it when the list shrank away. */
    private void clampKeyCell() {
        if (!gridTab()) {
            keyCell = -1;
            return;
        }
        final int count = gridCount();
        if (keyCell >= count) {
            keyCell = count - 1;
        }
    }

    /** Whether typing goes to the grid: no text field holds the keyboard, no dialog is up. */
    private boolean keyboardOnGrid() {
        return gridTab() && root.focusedChild() == null && !hasPopup() && !filterMenu.isOpen();
    }

    /**
     * Moves the keyboard's cell by {@code delta}, scrolling the grid so the cell stays in view. The cell
     * becomes the selection, or joins it from the anchor when {@code extend} (Shift is held).
     */
    private void moveKeyCell(final int delta, final boolean extend) {
        final int count = gridCount();
        if (count <= 0) {
            keyCell = -1;
            return;
        }
        final int target = keyCell < 0 ? 0 : Math.max(0, Math.min(count - 1, keyCell + delta));
        selectCell(target, extend, false);
        final int cols = Math.max(1, grid.columns());
        final int row = keyCell / cols;
        if (row < grid.scroll()) {
            grid.setScroll(row);
        } else if (row >= grid.scroll() + grid.visibleRows()) {
            grid.setScroll(row - grid.visibleRows() + 1);
        }
    }

    /** What a key does on the grid; true when it was one of the grid's keys. */
    private boolean gridKey(final int key, final int modifiers) {
        final boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        final boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> moveKeyCell(-1, shift);
            case GLFW.GLFW_KEY_RIGHT -> moveKeyCell(1, shift);
            case GLFW.GLFW_KEY_UP -> moveKeyCell(-Math.max(1, grid.columns()), shift);
            case GLFW.GLFW_KEY_DOWN -> moveKeyCell(Math.max(1, grid.columns()), shift);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (keyCell < 0 && selected.isEmpty()) {
                    moveKeyCell(0, false);
                } else {
                    activateSelection();
                }
            }
            case GLFW.GLFW_KEY_C -> {
                final NetworkItemEntry e = entryAt(keyCell);
                final CraftCatalogPayload.Entry craftable = e == null ? null : craftEntryFor(e.key());
                if (craftable != null) {
                    openCraftPopup(craftable);
                }
            }
            case GLFW.GLFW_KEY_F -> toggleFavourites(actionKeys());
            case GLFW.GLFW_KEY_A -> {
                if (!control) {
                    return false;
                }
                selectAll();
            }
            case GLFW.GLFW_KEY_SLASH, GLFW.GLFW_KEY_KP_DIVIDE -> root.focus(search);
            default -> {
                return false;
            }
        }
        return true;
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
        framedPanel(g, dx, dy, dw, dh);
        Draw.pushScissor(g, dx + 1, dy + 1, dx + dw - 1, dy + dh - 1);
        final List<OperationRecord> all = allOps();
        final int px = dx + 5;
        if (opSelected < 0 || opSelected >= all.size()) {
            renderNetworkCard(g, font, dx, dy, dw, dh);
            Draw.popScissor(g);
            return;
        }
        final OperationRecord op = all.get(opSelected);
        int py = panelHeader(g, font, dx, dy, dw, op.key(), op.name().getString(), OperationPalette.labelFor(op.type()),
                OperationPalette.colorFor(op.type()), false);
        Texts.small(g, font, "moved " + formatCount(op.moved()) + " / " + formatCount(op.requested()), px, py, skin.text());
        py += 10;
        Texts.small(g, font, opStatusLong(op.status()), px, py, opStatusColor(op.status()));
        py += 12;
        if (!op.subs().isEmpty()) {
            py = sectionRule(g, font, px, py, dw, "SUBOPERATIONS");
            for (final var sub : op.subs()) {
                if (py > dy + dh - 9) {
                    break;
                }
                Texts.small(g, font, Texts.trim(font, sub.server() + ": " + sub.moved() + "/" + sub.planned()
                        + " " + subStateLabel(sub.state()), Texts.smallFits(dw - 10)), px, py, skin.text());
                py += 9;
            }
        } else if (!op.moves().isEmpty()) {
            py = sectionRule(g, font, px, py, dw, "SOURCES");
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
        // An open filter drop-down takes the click first, wherever it lands; then a modal dialog.
        if (filterMenu.isOpen()) {
            filterMenu.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (hasPopup()) {
            inputTarget().mouseClicked(mouseX, mouseY, button);
            return;
        }
        final double lx = mouseX - lastX;
        final double ly = mouseY - lastY;
        final NetworkInteractorLayout.Zones z = zones();
        // The grips, on every tab: pressing one starts its drag; the columns and rows follow the cursor until it lets go.
        if (button == 0 && NetworkInteractorLayout.onVerticalGrip((int) lx, (int) ly, z)) {
            dragGrip = 1;
            root.focus(null);
            return;
        }
        if (button == 0 && NetworkInteractorLayout.onHorizontalGrip((int) lx, (int) ly, z)) {
            dragGrip = 2;
            root.focus(null);
            return;
        }
        /*
         * Inventory band: real container slots handled by the desktop screen (cursor, drag, shift-click);
         * the app simply ignores clicks that land there so it never misreads them as grid input.
         */
        if (inInventoryZone(lx, ly)) {
            return;
        }
        gridPressed = false;
        cellHit = false;
        pressX = (int) mouseX;
        pressY = (int) mouseY;
        final boolean onGridZone = gridTab() && button == 0 && z.gridH() > 0
                && ly >= z.gridY() && ly < z.gridY() + z.gridH() && lx >= z.gridX() && lx < z.gridX() + z.gridW();
        root.mouseClicked(mouseX, mouseY, button);
        if (onGridZone && !cellHit) {
            // A press on the grid's empty part drops the selection; dragging from here opens the rubber band.
            if (!Screen.hasControlDown()) {
                selected.clear();
            }
            gridPressed = true;
        }
        // A click that no field took leaves the keyboard on the grid.
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (dragGrip == 1) {
            setExtraCols(NetworkInteractorLayout.extraColsForGrip((int) (mouseX - lastX)));
            return;
        }
        if (dragGrip == 2) {
            setInvRows(NetworkInteractorLayout.invRowsForGrip((int) (mouseY - lastY), contentH));
            return;
        }
        if (gridPressed && !hasPopup()) {
            bandX = (int) mouseX;
            bandY = (int) mouseY;
            if (!marquee && (Math.abs(bandX - pressX) > 3 || Math.abs(bandY - pressY) > 3)) {
                marquee = true;
                if (!Screen.hasControlDown()) {
                    selected.clear();
                }
            }
            if (marquee) {
                selectUnderBand();
            }
            return;
        }
        inputTarget().mouseDragged(mouseX, mouseY, button);
    }

    /** Selects every visible cell the rubber band touches (a Control drag adds to what was selected). */
    private void selectUnderBand() {
        final int x0 = Math.min(pressX, bandX);
        final int y0 = Math.min(pressY, bandY);
        final int x1 = Math.max(pressX, bandX);
        final int y1 = Math.max(pressY, bandY);
        final int count = gridCount();
        final int cols = Math.max(1, grid.columns());
        final int first = grid.scroll() * cols;
        final int last = Math.min(count, (grid.scroll() + grid.visibleRows()) * cols);
        for (int i = first; i < last; i++) {
            final int[] r = grid.cellRect(i);
            final StorageKey key = keyAt(i);
            if (r == null || key == null) {
                continue;
            }
            final boolean touched = r[0] < x1 && r[0] + r[2] > x0 && r[1] < y1 && r[1] + r[3] > y0;
            if (touched) {
                selected.add(key);
                keyCell = i;
            } else if (!Screen.hasControlDown()) {
                selected.remove(key);
            }
        }
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (dragGrip != 0) {
            dragGrip = 0;
            return;
        }
        if (gridPressed) {
            gridPressed = false;
            marquee = false;
            return;
        }
        inputTarget().mouseReleased(mouseX, mouseY, button);
    }

    private void setExtraCols(final int value) {
        extraCols = Math.max(0, Math.min(NetworkInteractorLayout.maxExtraCols(contentW), value));
        lastExtraCols = extraCols;
    }

    private void setInvRows(final int value) {
        invRows = Math.max(1, Math.min(INV_ROWS, value));
        lastInvRows = invRows;
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
        final List<NetworkItemEntry> items = gridEntries();
        return idx >= 0 && idx < items.size() ? Optional.of(items.get(idx).key()) : Optional.empty();
    }

    /** Whether any modal dialog (request/storage or craft) is open, in which case the desktop routes every click to the app. */
    public boolean hasPopup() {
        return requestPopup.isOpen() || craftPopup.isOpen();
    }

    @Override
    public boolean modalActive() {
        // A filter drop-down is drawn in the late pass too, so it sits above the item icons instead of under them.
        return hasPopup() || filterMenu.isOpen();
    }

    @Override
    public boolean wantsEscape() {
        /*
         * A dialog or drop-down to close, a field to put the keyboard down from: Escape is the app's. A selection
         * is not: Escape leaves the desktop the way it always did, and a click on empty grid drops the selection.
         */
        return hasPopup() || filterMenu.isOpen() || root.focusedChild() != null;
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
        } else if (filterMenu.isOpen()) {
            filterMenu.render(g, ctx);
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
        if (filterMenu.isOpen()) {
            return true;
        }
        if (keyboardOnGrid()) {
            // The grid's letters arrive as keys; a stray character has nowhere else to go.
            return true;
        }
        return inputTarget().charTyped(c);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (filterMenu.isOpen()) {
            return filterMenu.keyPressed(key, scanCode, modifiers);
        }
        if (!hasPopup() && key == GLFW.GLFW_KEY_ESCAPE && root.focusedChild() != null) {
            // Escape puts the keyboard down from the search, back on the grid.
            root.focus(null);
            return true;
        }
        if (keyboardOnGrid() && gridKey(key, modifiers)) {
            return true;
        }
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
        if (entryTab()) {
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

    /**
     * Opens the request/storage dialog for one entry, or for several at once: the quantity is then per item,
     * and each item is asked for up to what the network holds of it.
     */
    private void openPopup(final List<NetworkItemEntry> entries, final boolean storage) {
        if (entries.isEmpty()) {
            return;
        }
        craftPopup.close();
        popupEntries.clear();
        popupEntries.addAll(entries);
        popupEntry = entries.get(0);
        popupStorage = storage;
        // Default to one stack, clamped to what is available.
        popupQty = Math.max(1L, Math.min(popupMax(), popupEntry.key().batch()));
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

    /** The most the dialog's quantity can be: the largest amount held of any item it is for. */
    private long popupMax() {
        long max = 0L;
        for (final NetworkItemEntry e : popupEntries) {
            max = Math.max(max, e.total());
        }
        return max;
    }

    private void stepQty(final int step) {
        if (popupEntry != null) {
            popupQty = Math.max(1L, Math.min(popupMax(), popupQty + step));
        }
    }

    private void maxQty() {
        if (popupEntry != null) {
            popupQty = Math.max(1L, popupMax());
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
        if (popupQty > 0) {
            // One request per item, each for the quantity or what there is of it, whichever is less.
            for (final NetworkItemEntry e : popupEntries) {
                final long qty = Math.min(popupQty, e.total());
                if (qty > 0) {
                    PacketDistributor.sendToServer(new NiGridClickPayload(host, monitorPos, e.key(), qty, mode,
                            popupPriority));
                }
            }
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
        for (final NetworkItemEntry e : popupEntries) {
            final long qty = Math.min(popupQty, e.total());
            if (qty > 0) {
                PacketDistributor.sendToServer(new NiSelectPayload(host, monitorPos, e.key(), qty,
                        allSelected ? List.of() : sources, destKey, popupPriority));
            }
        }
        closePopup();
    }

    private static String stepLabel(final int step) {
        final String sign = step > 0 ? "+" : "-";
        final int mag = Math.abs(step);
        return sign + (mag >= 1000 ? mag / 1000 + "k" : Integer.toString(mag));
    }

    /** A number readout drawn as a field: the quantity a dialog is about. */
    private final class QuantityBox extends UiComponent {
        private final java.util.function.Supplier<String> text;

        private QuantityBox(final java.util.function.Supplier<String> text) {
            this.text = text;
        }

        @Override
        public void render(final GuiGraphics g, final UiContext ctx) {
            ctx.skin().field(g, x(), y(), width(), height(), false);
            g.drawString(ctx.font(), text.get(), x() + 4, y() + (height() - 7) / 2, ctx.skin().text(), false);
        }
    }

    /** The request dialog's title: the one item's name, or how many items the dialog is for. */
    private String requestTitle() {
        if (popupEntry == null) {
            return "";
        }
        return popupEntries.size() > 1 ? "Request " + popupEntries.size() + " items" : popupEntry.name().getString();
    }

    /** The request dialog's second line: what is available of the one item, or the items' names. */
    private String requestSubtitle() {
        if (popupEntry == null) {
            return "";
        }
        if (popupEntries.size() <= 1) {
            return amount(popupEntry.key(), popupEntry.total()) + " available";
        }
        final StringBuilder names = new StringBuilder();
        for (final NetworkItemEntry e : popupEntries) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(e.name().getString());
        }
        return names.toString();
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
                g.drawString(ctx.font(), Texts.clip(ctx.font(), requestTitle(), nameW), x() + 20, y() + 1,
                        ctx.skin().text(), false);
                g.drawString(ctx.font(), Texts.clip(ctx.font(), requestSubtitle(), POPUP_W - 30), x() + 20, y() + 11,
                        ctx.skin().dim(), false);
            }
        });
        private final Button adv = add(new Button("Adv", NetworkInteractorApp.this::toggleAdvanced).setLabelScale(Texts.SMALL));
        private final QuantityBox qty = add(new QuantityBox(() -> "x" + popupQty + (popupEntries.size() > 1 ? " each" : "")));
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
                popupEntries.clear();
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
        craftRecipe = CraftPlanRequestPayload.ANY; // the machine's choice, until the plan says which it took
        craftPriority = OperationPriority.DEFAULT;
        craftPlan = null;
        closePopup();
        // An item with more than one recipe grows the popup when its plan lands; until then it is the plain one.
        craftPopup.setPreferredSize(CRAFT_W, CRAFT_H);
        craftPopup.open();
        craftPopup.placeIn(lastX, lastY, contentW, contentH);
        requestCraftPlan();
    }

    /** Asks the server to plan the current craft at the current quantity, with the chosen recipe. */
    private void requestCraftPlan() {
        if (craftEntry != null) {
            PacketDistributor.sendToServer(new CraftPlanRequestPayload(monitorPos, host, craftEntry.result(), craftQty,
                    craftRecipe));
        }
    }

    private void setCraftQty(final long value) {
        craftQty = Math.max(1, Math.min(MAX_CRAFT_QTY, value));
        requestCraftPlan();
    }

    /** Picks a recipe card: the machine remembers it for the item, and the plan is redone with it. */
    private void chooseRecipe(final int index) {
        if (craftEntry == null || craftPlan == null || index < 0 || index >= craftPlan.options().size()) {
            return;
        }
        craftRecipe = index;
        PacketDistributor.sendToServer(new SetSettingPayload(host, "recipe",
                StorageKey.of(craftEntry.result()).id() + "=" + index));
        requestCraftPlan();
    }

    /** Submits the craft (full or partial up to what is currently feasible) and closes the popup. */
    private void submitCraft(final boolean partial) {
        if (craftEntry != null) {
            PacketDistributor.sendToServer(new CraftSubmitPayload(monitorPos, host, craftEntry.result(), craftQty, partial,
                    true, craftPriority, craftRecipe));
        }
        craftPopup.close();
    }

    /** Whether the open craft popup offers a choice of recipe (the plan came back with more than one). */
    private boolean craftHasChoice() {
        return craftPlan != null && craftPlan.hasChoice();
    }

    /** The option the strip compares the chosen recipe against: the first other one. */
    @Nullable
    private RecipeChoice otherChoice() {
        if (craftPlan == null || craftPlan.chosen() == null) {
            return null;
        }
        for (int i = 0; i < craftPlan.options().size(); i++) {
            if (i != craftPlan.recipe()) {
                return craftPlan.options().get(i);
            }
        }
        return null;
    }

    /** One recipe the craft popup offers, drawn as a card; clicking it picks the recipe. */
    private final class RecipeCard extends UiComponent {
        private final int index;

        private RecipeCard(final int index) {
            this.index = index;
        }

        @Nullable
        private RecipeChoice choice() {
            return craftPlan != null && index < craftPlan.options().size() ? craftPlan.options().get(index) : null;
        }

        @Override
        public void render(final GuiGraphics g, final UiContext ctx) {
            final RecipeChoice choice = choice();
            if (choice == null) {
                return;
            }
            final boolean chosen = craftPlan != null && craftPlan.recipe() == index;
            final Font font = ctx.font();
            g.fill(x(), y(), right(), bottom(), chosen ? ctx.skin().listHover() : ctx.skin().fieldBg());
            Draw.outline(g, x(), y(), width(), height(), chosen ? ctx.skin().accent() : ctx.skin().edge());
            // The radio mark, top-right: a well, filled when this is the recipe picked.
            final int rx = right() - 8;
            Draw.outline(g, rx, y() + 3, 5, 5, ctx.skin().dim());
            if (chosen) {
                g.fill(rx + 1, y() + 4, rx + 4, y() + 7, ctx.skin().accent());
            }
            final int tw = width() - 14;
            int ty = y() + 3;
            Texts.small(g, font, Texts.clip(font, choice.label(), Texts.smallFits(tw)), x() + 3, ty, ctx.skin().text());
            ty += 8;
            // The kind line as two: what it is and where it runs, then how long and in how many stages.
            Texts.small(g, font, Texts.clip(font, choice.kind() + " · " + choice.machinesLine(), Texts.smallFits(tw + 8)),
                    x() + 3, ty, ctx.skin().dim());
            ty += 8;
            final String stages = choice.stages() + (choice.stages() == 1 ? " stage" : " stages")
                    + (choice.estimateTicks() > 0 ? " · ~" + RecipeChoice.seconds(choice.estimateTicks()) + " s" : "");
            Texts.small(g, font, Texts.clip(font, stages, Texts.smallFits(tw + 8)), x() + 3, ty, ctx.skin().dim());
            ty += 8;
            final int shown = Math.min(2, choice.inputs().size());
            for (int i = 0; i < shown; i++) {
                final RecipeChoice.Input in = choice.inputs().get(i);
                g.fill(x() + 3, ty + 2, x() + 6, ty + 5, in.satisfied() ? ONLINE_GREEN : SHORT_RED);
                final String line = in.need() + " " + in.name() + " (" + formatCount(in.have()) + " in stock)";
                Texts.small(g, font, Texts.clip(font, line, Texts.smallFits(tw + 2)), x() + 9, ty, ctx.skin().text());
                ty += 8;
            }
            if (choice.inputs().size() > shown) {
                Texts.small(g, font, "+" + (choice.inputs().size() - shown) + " more", x() + 9, ty, ctx.skin().dim());
                ty += 8;
            }
            final boolean ok = choice.allInStock();
            Texts.small(g, font, Texts.clip(font, choice.stockNote(), Texts.smallFits(tw + 8)), x() + 3, ty,
                    ok ? ONLINE_GREEN : SHORT_RED);
        }

        @Override
        public boolean mouseClicked(final double mx, final double my, final int button) {
            chooseRecipe(index);
            return true;
        }
    }

    /**
     * The MC-NET-style craft popup: result + quantity steppers + a live plan (need vs have) + Craft/Partial/Close.
     * An item the network makes more than one way grows the popup by a row of recipe cards and a strip saying
     * what the recipe picked does differently; the plan below is the picked recipe's.
     */
    private final class CraftPopup extends Popup {
        private final UiComponent header = add(new UiComponent() {
            @Override
            public void render(final GuiGraphics g, final UiContext ctx) {
                if (craftEntry == null) {
                    return;
                }
                DesktopItems.item(g, craftEntry.result(), x(), y() - 1);
                g.drawString(ctx.font(), "Craft " + Texts.clip(ctx.font(), craftEntry.title(), width() - 40), x() + 20, y() + 2,
                        ctx.skin().text(), false);
            }
        });
        private final QuantityBox qty = add(new QuantityBox(() -> Long.toString(craftQty)));
        private final Button[] steps = new Button[CRAFT_STEPS.length];
        private final Label recipeLabel = add(new Label(this::recipeLabelText, Label.Tone.DIM).setScale(Texts.SMALL));
        private final RecipeCard[] cards = new RecipeCard[CARDS_SHOWN];
        private final UiComponent differences = add(new UiComponent() {
            @Override
            public void render(final GuiGraphics g, final UiContext ctx) {
                g.fill(x(), y(), right(), bottom(), ctx.skin().fieldBg());
                Draw.outline(g, x(), y(), width(), height(), AMBER);
                int ty = y() + 2;
                for (final String line : wrapWords(ctx.font(), differencesText(), width() - 6, 3)) {
                    Texts.small(g, ctx.font(), line, x() + 3, ty, ctx.skin().text());
                    ty += 8;
                }
            }
        });
        private final Label planLabel = add(new Label(this::planLabelText, Label.Tone.DIM).setScale(Texts.SMALL));
        private final Button priority = add(new Button(() -> "Prio: " + craftPriority.label(),
                NetworkInteractorApp.this::cycleCraftPriority).setLabelScale(Texts.SMALL));
        private final UiComponent plan = add(new UiComponent() {
            @Override
            public void render(final GuiGraphics g, final UiContext ctx) {
                renderPlan(g, ctx, x(), y(), height());
            }
        });
        private final Label estimate = add(new Label(this::estimateText, Label.Tone.DIM).setScale(Texts.SMALL));
        private final Label feasible = add(new Label(this::feasibleText).setColor(0xFFB8860B).setScale(Texts.SMALL));
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
            for (int i = 0; i < CARDS_SHOWN; i++) {
                cards[i] = add(new RecipeCard(i));
            }
            setDim(0xB0000000);
            setLayouter(p -> layout());
            setOnClose(() -> {
                craftEntry = null;
                craftPlan = null;
            });
        }

        private void layout() {
            final boolean choice = craftHasChoice();
            final int px = x();
            final int py = y();
            final int w = width();
            header.setBounds(px + 4, py + 4, w - 8, 16);
            qty.setBounds(px + 5, py + 20, 61, 14);
            for (int i = 0; i < steps.length; i++) {
                steps[i].setBounds(px + 70 + i * 31, py + 20, 29, 14);
            }
            final int optionCount = craftPlan == null ? 0 : craftPlan.options().size();
            final int shownCards = choice ? Math.min(CARDS_SHOWN, optionCount) : 0;
            recipeLabel.setBounds(px + 5, py + 36, w - 10, 8);
            recipeLabel.setVisible(choice);
            final int cardW = shownCards == 0 ? 0 : (w - 10 - (shownCards - 1) * 3) / shownCards;
            for (int i = 0; i < CARDS_SHOWN; i++) {
                cards[i].setVisible(i < shownCards);
                cards[i].setBounds(px + 5 + i * (cardW + 3), py + 45, cardW, CARD_H);
            }
            differences.setBounds(px + 5, py + 45 + CARD_H + 2, w - 10, 27);
            differences.setVisible(choice);
            final int planTop = choice ? py + 45 + CARD_H + 2 + 27 + 2 : py + 41;
            planLabel.setBounds(px + 5, planTop, w - 70, 8);
            // The scheduling level shares the plan header's row, on the right.
            priority.setBounds(px + w - 61, planTop - 3, 56, 11);
            final int rowsTop = planTop + 10;
            final int footerTop = py + height() - 19;
            plan.setBounds(px + 4, rowsTop, w - 8, choice ? footerTop - 4 - rowsTop : CRAFT_H - 51 - 34);
            if (choice) {
                // Estimate and buttons share the one footer row.
                estimate.setBounds(px + 5, footerTop + 4, 90, 8);
                feasible.setBounds(px + 5, footerTop + 4, 0, 8);
                feasible.setVisible(false);
                craft.setBounds(px + w - 5 - 40 - 3 - 44 - 3 - 40, footerTop, 40, 16);
                partial.setBounds(px + w - 5 - 40 - 3 - 44, footerTop, 44, 16);
                closeButton.setBounds(px + w - 5 - 40, footerTop, 40, 16);
            } else {
                estimate.setBounds(px + 5, py + CRAFT_H - 32, 60, 8);
                feasible.setBounds(px + 70, py + CRAFT_H - 32, 44, 8);
                feasible.setVisible(craftPlan != null && !craftPlan.feasible());
                craft.setBounds(px + 5, footerTop, 58, 16);
                partial.setBounds(px + 67, footerTop, 66, 16);
                closeButton.setBounds(px + 137, footerTop, 54, 16);
            }
            estimate.setVisible(craftPlan != null);
        }

        private String recipeLabelText() {
            final int count = craftPlan == null ? 0 : craftPlan.options().size();
            return "RECIPE  ·  " + count + " patterns make this" + (count > CARDS_SHOWN ? " (Left/Right for more)" : "");
        }

        private String planLabelText() {
            final RecipeChoice chosen = craftPlan == null ? null : craftPlan.chosen();
            return craftHasChoice() && chosen != null ? "PLAN - with " + chosen.label() : "PLAN - raw ingredients";
        }

        private String differencesText() {
            final RecipeChoice chosen = craftPlan == null ? null : craftPlan.chosen();
            final RecipeChoice other = otherChoice();
            return chosen == null || other == null ? "" : RecipeDifferences.describe(chosen, other, craftQty);
        }

        /** Plan rows (need vs have): green when the network has enough, red otherwise, then what covers a shortfall. */
        private void renderPlan(final GuiGraphics g, final UiContext ctx, final int px, final int top, final int h) {
            final Font font = ctx.font();
            int ry = top;
            if (craftPlan == null) {
                Texts.small(g, font, "planning...", px + 1, ry, ctx.skin().dim());
                return;
            }
            final boolean choice = craftHasChoice();
            final int pitch = choice ? 10 : 12;
            final int coverLines = Math.min(craftPlan.cover().size(), choice ? 1 : 2);
            final int room = Math.max(1, (h - coverLines * 8) / pitch);
            final int shown = Math.min(room, craftPlan.rows().size());
            final int w = craftPopup.width();
            for (int i = 0; i < shown; i++) {
                final CraftPlanPayload.Row row = craftPlan.rows().get(i);
                final int color = row.satisfied() ? ctx.skin().text() : SHORT_RED;
                DesktopItems.item(g, row.item(), px, ry - 2);
                Texts.small(g, font, Texts.clip(font, row.item().getHoverName().getString(), Texts.smallFits(w - 100)),
                        px + 18, ry, color);
                final String counts = formatCount(row.have()) + " / " + formatCount(row.need());
                Texts.small(g, font, counts, px + w - 10 - Texts.smallWidth(font, counts), ry,
                        row.satisfied() ? ONLINE_GREEN : SHORT_RED);
                ry += pitch;
            }
            if (craftPlan.rows().size() > shown) {
                Texts.small(g, font, "+" + (craftPlan.rows().size() - shown) + " more", px + 18, ry, ctx.skin().dim());
                ry += 8;
            }
            for (int i = 0; i < coverLines; i++) {
                Texts.small(g, font, Texts.clip(font, craftPlan.cover().get(i), Texts.smallFits(w - 12)), px + 1, ry,
                        SHORT_RED);
                ry += 8;
            }
        }

        private String estimateText() {
            if (craftPlan == null) {
                return "";
            }
            final String est = craftPlan.estimateTicks() > 0 ? "EST ~" + Math.max(1, craftPlan.estimateTicks() / 20) + "s" : "EST --";
            return craftHasChoice() && craftPlan.stages() > 1 ? est + " · " + craftPlan.stages() + " stages" : est;
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
                case GLFW.GLFW_KEY_LEFT -> {
                    if (craftHasChoice()) {
                        chooseRecipe((craftPlan.recipe() + craftPlan.options().size() - 1) % craftPlan.options().size());
                    }
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    if (craftHasChoice()) {
                        chooseRecipe((craftPlan.recipe() + 1) % craftPlan.options().size());
                    }
                }
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

    public boolean isRequestPopupOpen() {
        return requestPopup.isOpen();
    }

    /** The request dialog's title: the item's name, or "Request N items" for a selection. */
    public String requestPopupTitle() {
        return requestTitle();
    }

    /** The centre of the request dialog's Request button (the plain, single-tab form). */
    public int[] requestPopupSubmitCenter() {
        return local(requestPopup.request.center());
    }

    /** The names of the selected cells, in the order the active grid tab lists them. */
    public List<String> selectedNames() {
        final List<String> out = new ArrayList<>();
        final int count = gridCount();
        for (int i = 0; i < count; i++) {
            final StorageKey key = keyAt(i);
            if (key != null && selected.contains(key)) {
                out.add(key.displayName().getString());
            }
        }
        return out;
    }

    /** The centre of grid cell {@code index} on any grid tab (must be scrolled into view). */
    public int[] gridCellCenter(final int index) {
        return local(grid.cellCenter(index));
    }

    /** The Operations tab's selected row, or -1. */
    public int selectedOperation() {
        return opSelected;
    }

    public int[] operationsTabCenter() {
        return local(tabs.tabCenter(TAB_OPS));
    }

    public long craftQuantity() {
        return craftQty;
    }

    /** The Crafting tab's entries as listed (after the search and the filters), by display name. */
    public List<String> craftableNames() {
        final List<String> out = new ArrayList<>();
        for (final CraftCatalogPayload.Entry e : filteredCrafts()) {
            out.add(e.title());
        }
        return out;
    }

    /** The entries the active entry tab lists (after the search, the filters and the sort), by display name. */
    public List<String> listedNames() {
        final List<String> out = new ArrayList<>();
        if (tab == TAB_CRAFTING) {
            return craftableNames();
        }
        for (final NetworkItemEntry e : gridEntries()) {
            out.add(e.name().getString());
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

    /** Content-local centre of the Favourites tab (the starred one at the strip's right end). */
    public int[] favouritesTabCenter() {
        return local(tabs.tabCenter(TAB_FAV));
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
        return new int[] {z.invX() + col * CELL + CELL / 2, NetworkInteractorLayout.slotRowY(z, row) + CELL / 2};
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

    /** The recipes the open craft popup offers, by label; empty until the plan lands or when there is one. */
    public List<String> craftPopupOptionLabels() {
        final List<String> out = new ArrayList<>();
        if (craftPlan != null && craftPlan.hasChoice()) {
            for (final RecipeChoice choice : craftPlan.options()) {
                out.add(choice.label());
            }
        }
        return out;
    }

    /** The index of the recipe the open craft popup is planned with, or -1 before the plan lands. */
    public int craftPopupChosen() {
        return craftPlan == null ? -1 : craftPlan.recipe();
    }

    /** The centre of recipe card {@code index} in the craft popup. */
    public int[] craftPopupCardCenter(final int index) {
        return local(craftPopup.cards[index].center());
    }

    /** The differences strip's text, or "" when the popup offers no choice. */
    public String craftPopupDifferences() {
        return craftPopup.differencesText();
    }

    /** The plan's caption, which names the recipe planned with when there is a choice. */
    public String craftPopupPlanLabel() {
        return craftPopup.planLabelText();
    }

    /** The plan rows as "name have/need", with "!" after a row that is short. */
    public List<String> craftPopupPlanRows() {
        final List<String> out = new ArrayList<>();
        if (craftPlan != null) {
            for (final CraftPlanPayload.Row row : craftPlan.rows()) {
                out.add(row.item().getHoverName().getString() + " " + row.have() + "/" + row.need() + (row.satisfied() ? "" : "!"));
            }
        }
        return out;
    }

    /** What the plan says the network would craft to cover what is short. */
    public List<String> craftPopupCoverLines() {
        return craftPlan == null ? List.of() : craftPlan.cover();
    }

    /** Whether the open craft popup's plan is feasible as it stands. */
    public boolean craftPopupFeasible() {
        return craftPlan != null && craftPlan.feasible();
    }

    /** The grid cell the keyboard is on, or -1. */
    public int keyCell() {
        return keyCell;
    }

    /** The starred data ids this window shows. */
    public List<String> favouriteIds() {
        return new ArrayList<>(favourites);
    }

    /** How many columns the grid has now (nine plus the vertical grip's extra ones). */
    public int gridColumns() {
        return zones().gridCols();
    }

    /** How many inventory rows the band shows now. */
    public int inventoryRowsShown() {
        return invRows;
    }

    /** Content-local centre of the vertical grip between the grid and the details panel. */
    public int[] verticalGripCenter() {
        final NetworkInteractorLayout.Zones z = zones();
        return new int[] {z.gripX() + NetworkInteractorLayout.GAP / 2,
                z.gridY() + (z.invBandY() + z.invBandH() - z.gridY()) / 2};
    }

    /** Content-local centre of the horizontal grip strip above the inventory band. */
    public int[] horizontalGripCenter() {
        final NetworkInteractorLayout.Zones z = zones();
        return new int[] {z.invBandX() + z.invBandW() / 2, z.gripY() + NetworkInteractorLayout.GRIP_H / 2};
    }

    /** The mod namespace the grid is narrowed to, or "". */
    public String modFilter() {
        return modFilter;
    }

    /** The category the grid is narrowed to, or "". */
    public String categoryFilter() {
        return categoryFilter;
    }

    public int[] modFilterCenter() {
        return local(modButton.center());
    }

    public int[] categoryFilterCenter() {
        return local(categoryButton.center());
    }

    public int[] searchFieldCenter() {
        return local(search.center());
    }

    /** The search field's text. */
    public String searchText() {
        return search.edit();
    }

    /** Whether a filter drop-down is up. */
    public boolean filterMenuOpen() {
        return filterMenu.isOpen();
    }

    /** The labels of the open filter drop-down. */
    public List<String> filterMenuLabels() {
        final List<String> out = new ArrayList<>();
        for (final ContextMenu.Item entry : filterMenu.items()) {
            out.add(entry.label());
        }
        return out;
    }

    /** The middle of the open drop-down's entry with that label, content-local, or null. */
    public int[] filterMenuPoint(final String label) {
        final int index = filterMenuLabels().indexOf(label);
        return index < 0 ? null : local(filterMenu.itemCenter(index));
    }

    /** What the details panel lists as making the item in view, or empty before the server answered. */
    public List<String> detailsMadeBy() {
        final NetworkItemEntry e = detailEntry(lastMouseX, lastMouseY);
        final ItemRecipesPayload known = e == null ? null : recipes.get(e.key());
        return known == null ? List.of() : known.madeBy();
    }

    /** What the details panel lists the item in view as used in, or empty before the server answered. */
    public List<String> detailsUsedIn() {
        final NetworkItemEntry e = detailEntry(lastMouseX, lastMouseY);
        final ItemRecipesPayload known = e == null ? null : recipes.get(e.key());
        return known == null ? List.of() : known.usedIn();
    }

    /** The name of the item the details panel is about, or "". */
    public String detailsName() {
        final NetworkItemEntry e = detailEntry(lastMouseX, lastMouseY);
        return e == null ? "" : e.name().getString();
    }

    /** The centre of the details panel's star button. */
    public int[] detailsStarCenter() {
        return local(detailStar.center());
    }

    /** The centre of the details panel's craft button. */
    public int[] detailsCraftCenter() {
        return local(detailCraft.center());
    }

    /** The keyboard hint under the status line. */
    public String keyboardHint() {
        return hintText();
    }

    /** The caption over the grid's well: what the active tab lists. */
    public String gridCaption() {
        return gridCaptionText();
    }

    /** The network's storage capacity in item-equivalents, as the last snapshot said; zero when unknown. */
    public long storageCapacity() {
        return capacityItems;
    }

    /** The network's used storage in item-equivalents, as the last snapshot said. */
    public long storageUsed() {
        return usedItems;
    }
}
