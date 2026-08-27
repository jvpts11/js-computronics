/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity.DataCell;
import dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.PatternEncoderEditPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestPatternEncoderFilesPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Screen for the Pattern Encoder. Three authoring tabs share one window and one media bay: CRAFTING (the ghost
 * 3x3 bench grid with a live result preview), PROCESSING (input/output ghost grids fed into a named machine type,
 * with per-output chances and a timeout), and MULTI-STAGE (an ordered pipeline assembled from the other two). Each
 * tab's WRITE action serializes a {@code .craft} file onto the inserted medium. The processing/multi state lives on
 * the block entity (synced by its update tag) and is edited through {@link PatternEncoderEditPayload}; the bench
 * grid keeps its original menu-slot ghost behavior.
 */
public class PatternEncoderScreen extends AbstractComputerScreen<PatternEncoderMenu> {

    private static final int PROC = PatternEncoderBlockEntity.PROC_GRID;
    private static final int PROC_COLS = 3;
    private static final int PROC_VIS_ROWS = 3;
    private static final int PROC_MAX_SCROLL = (PROC + PROC_COLS - 1) / PROC_COLS - PROC_VIS_ROWS;

    // Tab bar.
    private static final int TAB_Y = 19;
    private static final int TAB_H = 11;
    private static final int TAB_C_X = 8;
    private static final int TAB_C_W = 50;
    private static final int TAB_P_X = 60;
    private static final int TAB_P_W = 58;
    private static final int TAB_M_X = 120;
    private static final int TAB_M_W = 72;

    // Shared bottom bar (media slot + write control).
    private static final int MEDIA_X = 8;
    private static final int MEDIA_Y = 108;
    private static final int WRITE_X = 96;
    private static final int WRITE_Y = 109;
    private static final int WRITE_W = 96;
    private static final int WRITE_H = 14;

    // Processing tab.
    private static final int IN_X = 8;
    private static final int IN_Y = 44;
    private static final int OUT_X = 138;
    private static final int OUT_Y = 44;

    // Real draggable scrollbar (track + thumb) right of each 3x3 grid; inputs and outputs scroll independently.
    private static final int SB_W = 4;
    private static final int SB_H = PROC_VIS_ROWS * 18;             // 54: same height as the visible grid
    private static final int SB_IN_X = IN_X + PROC_COLS * 18 + 1;   // 63: just right of the inputs grid
    private static final int SB_OUT_X = OUT_X + PROC_COLS * 18 + 2; // 194: just right of the outputs grid

    // Chance popup (a small modal over the processing tab).
    private static final int PX = 52;
    private static final int PY = 44;
    private static final int PW = 120;
    private static final int PH = 44;

    // Amount popup (the same idiom, for a fluid or chemical cell): title, steppers around the value, DONE.
    private static final int AX = 40;
    private static final int AY = 40;
    private static final int AW = 122;
    private static final int AH = 58;

    // Stage picker popup (multi-stage tab): a modal list of the craftings on the inserted medium.
    private static final int PICK_X = 24;
    private static final int PICK_Y = 32;
    private static final int PICK_W = 152;
    private static final int PICK_ROW_H = 11;
    private static final int PICK_MAX_ROWS = 6;

    // Machine picker popup (processing tab): all installed-mod machines grouped by mod, collapsible, searchable.
    private static final int MP_X = 8;
    private static final int MP_Y = 32;
    private static final int MP_W = 184;
    private static final int MP_H = 102;
    private static final int MP_LIST_Y = MP_Y + 30;
    private static final int MP_ROW_H = 10;
    private static final int MP_VIS_ROWS = (MP_H - 34) / MP_ROW_H;

    /** Sentinel machine id the player picks to say "this recipe is for a machine the mod did not detect". */
    private static final String UNKNOWN_MACHINE = "unknown";

    private int tab;
    private int inScroll;
    private int outScroll;
    private int draggingScroll; // 0 = none, 1 = inputs scrollbar, 2 = outputs scrollbar
    private boolean suppressResponder;

    private boolean chancePopupOpen;
    private int selectedOutput = -1;

    private Button machineBtn;
    private EditBox machineSearch;
    private boolean machinePickerOpen;
    private int machineScroll;
    private final java.util.Set<String> expandedMods = new java.util.HashSet<>();
    private java.util.List<net.minecraft.resources.ResourceLocation> allMachines;
    private java.util.List<String> allCategories;
    private EditBox timeoutBox;
    private EditBox chanceBox;
    private Button addBenchBtn;
    private Button clearStagesBtn;
    private boolean stagePickerOpen;
    private int stageScroll;
    private Button guaranteedBtn;

    // Amount popup state: which grid and cell it edits, its value box and its buttons.
    private boolean amountPopupOpen;
    private boolean amountForOutput;
    private int amountCell = -1;
    private EditBox amountBox;
    private final List<Button> amountButtons = new java.util.ArrayList<>();

    // --- inspection (client tests assert on what the player sees) ---

    /** The tab currently shown: one of the {@code PatternEncoderMenu.TAB_*} constants. */
    public int activeTab() {
        return tab;
    }

    public int inputScroll() {
        return inScroll;
    }

    public int outputScroll() {
        return outScroll;
    }

    public boolean isMachinePickerOpen() {
        return machinePickerOpen;
    }

    public boolean isChancePopupOpen() {
        return chancePopupOpen;
    }

    public boolean isAmountPopupOpen() {
        return amountPopupOpen;
    }

    /** GUI-local centre of the amount popup's stepper {@code index} (0 = -100, 1 = -10, 2 = +10, 3 = +100). */
    public static int amountStepperX(final int index) {
        return AX + new int[]{15, 33, 89, 107}[index];
    }

    public static int amountStepperY() {
        return AY + 32;
    }

    public static int amountDoneX() {
        return AX + 97;
    }

    public static int amountDoneY() {
        return AY + 51;
    }

    public boolean isStagePickerOpen() {
        return stagePickerOpen;
    }

    /**
     * The machine picker's rows as the player sees them, top to bottom: a machine id, {@code "#<namespace>"}
     * for a collapsible mod header, or {@code "*"} for the "unknown machine" entry.
     */
    public java.util.List<String> machinePickerRows() {
        final java.util.List<String> out = new java.util.ArrayList<>();
        for (final MachineRow row : visibleMachineRows()) {
            out.add(row.unknown() ? "*" : row.header() ? "#" + row.namespace() : row.machineId());
        }
        return out;
    }

    public int machinePickerScroll() {
        return machineScroll;
    }

    /** Where to click (relative to the window's top-left) to hit the {@code visibleIndex}-th picker row. */
    public int machinePickerRowX() {
        return MP_X + 20;
    }

    public int machinePickerRowY(final int visibleIndex) {
        return MP_LIST_Y + visibleIndex * MP_ROW_H + MP_ROW_H / 2;
    }

    /** The MULTI-STAGE stage picker's files (the .craft files on the medium), as listed. */
    public java.util.List<String> stagePickerFiles() {
        return menu.craftFiles();
    }

    public int stagePickerRowX() {
        return PICK_X + PICK_W / 2;
    }

    public int stagePickerRowY(final int visibleIndex) {
        return PICK_Y + 15 + visibleIndex * PICK_ROW_H + PICK_ROW_H / 2;
    }

    /** Window-relative centre of the MULTI-STAGE tab's "Add stage" button. */
    public static int addStageButtonX() {
        return 8 + 40;
    }

    public static int addStageButtonY() {
        return 90 + 7;
    }

    /** Window-relative centre of the PROCESSING tab's timeout box. */
    public static int timeoutBoxX() {
        return 70 + 20;
    }

    public static int timeoutBoxY() {
        return 82 + 6;
    }

    public PatternEncoderScreen(final PatternEncoderMenu menu, final Inventory inventory,
                                final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 218;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        this.tab = menu.activeTab();

        // The machine is chosen from a popup of all installed-mod machines; this button shows the current pick.
        machineBtn = new ThemeButton(leftPos + 70, topPos + 44, 62, 12,
                Component.literal("choose..."), b -> openMachinePicker());
        addRenderableWidget(machineBtn);

        machineSearch = new EditBox(this.font, leftPos + MP_X + 4, topPos + MP_Y + 14, MP_W - 8, 12,
                Component.empty());
        machineSearch.setMaxLength(50);
        machineSearch.setHint(Component.literal("search machines..."));
        machineSearch.setResponder(s -> machineScroll = 0);
        addWidget(machineSearch); // picker input only; rendered on top in render()

        timeoutBox = new EditBox(this.font, leftPos + 70, topPos + 82, 40, 12, Component.empty());
        timeoutBox.setMaxLength(6);
        timeoutBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,6}"));
        timeoutBox.setResponder(this::onTimeoutChanged);
        addRenderableWidget(timeoutBox);

        chanceBox = new EditBox(this.font, leftPos + PX + 72, topPos + PY + 22, 34, 14, Component.empty());
        chanceBox.setMaxLength(3);
        chanceBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
        chanceBox.setResponder(this::onChanceChanged);
        // Popup widgets are added for input only (addWidget, not addRenderableWidget) and drawn on top in render(),
        // so the modal sits above the slots/items instead of behind them.
        addWidget(chanceBox);

        // One "Add stage" button opens a picker of the craftings on the medium; the server routes each by its
        // own kind (a bench .craft becomes a bench stage, a machine .craft a processing stage).
        addBenchBtn = new ThemeButton(leftPos + 8, topPos + 90, 80, 14,
                Component.literal("Add stage"), b -> openStagePicker());
        addRenderableWidget(addBenchBtn);

        clearStagesBtn = new ThemeButton(leftPos + 92, topPos + 90, 40, 14, Component.literal("Clear"),
                b -> send(PatternEncoderEditPayload.action(
                        menu.blockEntityPos(), PatternEncoderEditPayload.ACTION_CLEAR_STAGES)));
        addRenderableWidget(clearStagesBtn);

        guaranteedBtn = new ThemeButton(leftPos + PX + 6, topPos + PY + 22, 60, 14,
                Component.literal("Guaranteed"), b -> {
                    setChance(ProcessingPattern.FULL_CHANCE);
                    closeChancePopup();
                });
        addWidget(guaranteedBtn);

        // Amount popup widgets: -100 / -10 / [value] / +10 / +100 on one row, DONE below.
        amountBox = new EditBox(this.font, leftPos + AX + 42, topPos + AY + 27, 38, 11, Component.empty());
        amountBox.setMaxLength(9);
        amountBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,9}"));
        amountBox.setResponder(this::onAmountChanged);
        addWidget(amountBox);
        amountButtons.clear();
        final int[][] steppers = {{6, 18, -100}, {26, 14, -10}, {82, 14, 10}, {98, 18, 100}};
        for (final int[] stepper : steppers) {
            final int delta = stepper[2];
            final Button b = new ThemeButton(leftPos + AX + stepper[0], topPos + AY + 27, stepper[1], 11,
                    Component.literal((delta > 0 ? "+" : "") + delta), btn -> stepAmount(delta));
            amountButtons.add(b);
            addWidget(b);
        }
        final Button done = new ThemeButton(leftPos + AX + 78, topPos + AY + 46, 38, 10, Component.literal("DONE"),
                btn -> confirmAmount());
        amountButtons.add(done);
        addWidget(done);

        applyTabVisibility();
        if (tab == PatternEncoderMenu.TAB_CRAFTING) {
            requestFiles();
        }
    }

    private PatternEncoderBlockEntity be() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.blockEntityPos()) instanceof PatternEncoderBlockEntity be
                ? be : null;
    }

    // --- tab management ---

    private void switchTab(final int target) {
        if (target == tab) {
            return;
        }
        this.tab = target;
        menu.setActiveTab(target);
        send(PatternEncoderEditPayload.valued(menu.blockEntityPos(),
                PatternEncoderEditPayload.ACTION_SET_TAB, target));
        closeChancePopup();
        setFocused(null);
        applyTabVisibility();
        if (target == PatternEncoderMenu.TAB_CRAFTING) {
            requestFiles();
        }
    }

    /** Switches to the PROCESSING tab — the JEI "+" transfer calls this so the transferred recipe is visible. */
    public void showProcessingTab() {
        switchTab(PatternEncoderMenu.TAB_PROCESSING);
    }

    private void applyTabVisibility() {
        final boolean proc = tab == PatternEncoderMenu.TAB_PROCESSING;
        final boolean multi = tab == PatternEncoderMenu.TAB_MULTI;
        final boolean popup = proc && chancePopupOpen;
        final boolean amountPopup = proc && amountPopupOpen;
        // While a popup is open the underlying processing controls hide, so they neither bleed through nor steal clicks.
        final boolean procIdle = proc && !popup && !amountPopup && !machinePickerOpen;
        amountBox.visible = amountPopup;
        for (final Button b : amountButtons) {
            b.visible = amountPopup;
        }
        machineBtn.visible = procIdle;
        timeoutBox.visible = procIdle;
        machineSearch.visible = proc && machinePickerOpen;
        addBenchBtn.visible = multi;
        clearStagesBtn.visible = multi;
        if (!multi) {
            stagePickerOpen = false;
        }
        if (!proc) {
            machinePickerOpen = false;
        }
        chanceBox.visible = popup;
        guaranteedBtn.visible = popup;
    }

    private void updateWidgets() {
        applyTabVisibility();
        final PatternEncoderBlockEntity be = be();
        if (be == null) {
            return;
        }
        if (tab == PatternEncoderMenu.TAB_PROCESSING) {
            machineBtn.setMessage(Component.literal(
                    be.machineType().isBlank() ? "choose..." : shortMachine(be.machineType())));
            if (!timeoutBox.isFocused()) {
                setBox(timeoutBox, String.valueOf(be.procTimeout()));
            }
            if (chancePopupOpen && selectedOutput >= 0 && !chanceBox.isFocused()) {
                setBox(chanceBox, String.valueOf(be.outputChance(selectedOutput)));
            }
        }
    }

    private void setBox(final EditBox box, final String value) {
        if (box.getValue().equals(value)) {
            return;
        }
        suppressResponder = true;
        box.setValue(value);
        suppressResponder = false;
    }

    // --- responders ---

    private void setMachine(final String id) {
        send(PatternEncoderEditPayload.texted(menu.blockEntityPos(),
                PatternEncoderEditPayload.ACTION_SET_MACHINE, id));
    }

    private void onTimeoutChanged(final String value) {
        if (suppressResponder || value.isEmpty()) {
            return;
        }
        try {
            send(PatternEncoderEditPayload.valued(menu.blockEntityPos(),
                    PatternEncoderEditPayload.ACTION_SET_TIMEOUT, Integer.parseInt(value)));
        } catch (final NumberFormatException ignored) {
            // filtered to digits already; an overflow string is simply ignored until corrected
        }
    }

    private void onChanceChanged(final String value) {
        if (suppressResponder || value.isEmpty() || selectedOutput < 0) {
            return;
        }
        try {
            setChance(Integer.parseInt(value));
        } catch (final NumberFormatException ignored) {
            // ignore until the field holds a valid number
        }
    }

    private void setChance(final int percent) {
        if (selectedOutput < 0) {
            return;
        }
        send(PatternEncoderEditPayload.indexedValued(menu.blockEntityPos(),
                PatternEncoderEditPayload.ACTION_SET_CHANCE, selectedOutput,
                Math.max(1, Math.min(ProcessingPattern.FULL_CHANCE, percent))));
    }

    // --- machine picker (all installed-mod machines, grouped by mod, searchable) ---

    /**
     * One picker row. A header row groups a namespace (machineId null); a machine row carries the machine id the
     * pattern will store — a block id for concrete machines, {@code generic:<recipeType>} for the dynamic generic
     * categories — plus the label the list shows.
     */
    private record MachineRow(String namespace, String machineId, String label, boolean unknown) {
        boolean header() {
            return machineId == null && !unknown;
        }

        static MachineRow headerOf(final String namespace) {
            return new MachineRow(namespace, null, null, false);
        }

        static MachineRow of(final net.minecraft.resources.ResourceLocation id) {
            return new MachineRow(id.getNamespace(), id.toString(), id.getPath(), false);
        }

        static MachineRow generic(final String categoryId) {
            return new MachineRow(dev.jsc.jscomputronics.module.computing.crafting.MachineCategory.GENERIC_NAMESPACE,
                    dev.jsc.jscomputronics.module.computing.crafting.MachineCategory.genericIdOf(categoryId),
                    categoryId, false);
        }
    }

    private void openMachinePicker() {
        machinePickerOpen = true;
        machineScroll = 0;
        machineSearch.setValue("");
        applyTabVisibility();
        setFocused(machineSearch);
        machineSearch.setFocused(true);
    }

    private void closeMachinePicker() {
        machinePickerOpen = false;
        setFocused(null);
        applyTabVisibility();
    }

    /** Lazily enumerate every installed machine-like block via the shared, GameTest-covered catalog. */
    private java.util.List<net.minecraft.resources.ResourceLocation> machineIds() {
        if (allMachines == null) {
            allMachines = dev.jsc.jscomputronics.module.computing.crafting.MachineCatalog.machineIds();
        }
        return allMachines;
    }

    /**
     * The rows currently visible in the picker. A search query gives a flat list of matching machines; otherwise
     * one header row per mod namespace, followed by that mod's machines when the namespace is expanded.
     */
    private java.util.List<MachineRow> visibleMachineRows() {
        final java.util.List<MachineRow> rows = new java.util.ArrayList<>();
        // Always offer "unknown machine" first: it tags the recipe as belonging to a machine the mod didn't detect.
        rows.add(new MachineRow(null, null, null, true));
        final String generic = dev.jsc.jscomputronics.module.computing.crafting.MachineCategory.GENERIC_NAMESPACE;
        final String q = machineSearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        if (!q.isEmpty()) {
            for (final String categoryId : categoryIds()) {
                if ((generic + ":" + categoryId).toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    rows.add(MachineRow.generic(categoryId));
                }
            }
            for (final net.minecraft.resources.ResourceLocation id : machineIds()) {
                if (id.toString().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    rows.add(MachineRow.of(id));
                }
            }
            return rows;
        }
        // The dynamic generic group first: one entry per installed recipe type ("generic furnace" = smelting, ...).
        rows.add(MachineRow.headerOf(generic));
        if (expandedMods.contains(generic)) {
            for (final String categoryId : categoryIds()) {
                rows.add(MachineRow.generic(categoryId));
            }
        }
        String lastNs = null;
        for (final net.minecraft.resources.ResourceLocation id : machineIds()) {
            if (!id.getNamespace().equals(lastNs)) {
                lastNs = id.getNamespace();
                rows.add(MachineRow.headerOf(lastNs));
            }
            if (expandedMods.contains(id.getNamespace())) {
                rows.add(MachineRow.of(id));
            }
        }
        return rows;
    }

    private java.util.List<String> categoryIds() {
        if (allCategories == null) {
            allCategories = dev.jsc.jscomputronics.module.computing.crafting.MachineCategory.categoryIds();
        }
        return allCategories;
    }

    private void renderMachinePicker(final GuiGraphics g, final int mouseX, final int mouseY,
                                     final float partialTick) {
        final int x = leftPos;
        final int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xC0000000);
        JscOsTheme.panel(g, x + MP_X, y + MP_Y, MP_W, MP_H);
        JscOsTheme.vLine(g, x + MP_X, y + MP_Y, MP_H);
        JscOsTheme.vLine(g, x + MP_X + MP_W - 1, y + MP_Y, MP_H);
        JscOsTheme.text(g, font, "CHOOSE MACHINE", x + MP_X + 6, y + MP_Y + 3, JscOsTheme.accent());
        machineSearch.render(g, mouseX, mouseY, partialTick);
        final java.util.List<MachineRow> rows = visibleMachineRows();
        machineScroll = Math.max(0, Math.min(Math.max(0, rows.size() - MP_VIS_ROWS), machineScroll));
        for (int r = 0; r < MP_VIS_ROWS && machineScroll + r < rows.size(); r++) {
            final MachineRow row = rows.get(machineScroll + r);
            final int ry = y + MP_LIST_Y + r * MP_ROW_H;
            final boolean hovered = mouseX >= x + MP_X + 3 && mouseX < x + MP_X + MP_W - 3
                    && mouseY >= ry && mouseY < ry + MP_ROW_H;
            if (hovered) {
                g.fill(x + MP_X + 3, ry, x + MP_X + MP_W - 3, ry + MP_ROW_H, JscOsTheme.hover());
            }
            if (row.unknown()) {
                JscOsTheme.textS(g, font, "* unknown machine (not detected)", x + MP_X + 6, ry + 2,
                        JscOsTheme.amber());
            } else if (row.header()) {
                final String mark = expandedMods.contains(row.namespace()) ? "- " : "+ ";
                JscOsTheme.textS(g, font, mark + row.namespace(), x + MP_X + 6, ry + 2, JscOsTheme.accent2());
            } else {
                JscOsTheme.textS(g, font, row.label(), x + MP_X + 16, ry + 2,
                        hovered ? JscOsTheme.text() : JscOsTheme.dim());
            }
        }
        if (rows.size() > MP_VIS_ROWS) {
            JscOsTheme.textSRight(g, font, (machineScroll + 1) + "-"
                            + Math.min(rows.size(), machineScroll + MP_VIS_ROWS) + "/" + rows.size(),
                    x + MP_X + MP_W - 6, y + MP_Y + 4, JscOsTheme.dim());
        }
    }

    private boolean clickMachinePicker(final double mouseX, final double mouseY) {
        final int x = leftPos;
        final int y = topPos;
        if (machineSearch.isMouseOver(mouseX, mouseY)) {
            setFocused(machineSearch);
            machineSearch.setFocused(true);
            machineSearch.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        final java.util.List<MachineRow> rows = visibleMachineRows();
        for (int r = 0; r < MP_VIS_ROWS && machineScroll + r < rows.size(); r++) {
            final int ry = y + MP_LIST_Y + r * MP_ROW_H;
            if (mouseX >= x + MP_X + 3 && mouseX < x + MP_X + MP_W - 3
                    && mouseY >= ry && mouseY < ry + MP_ROW_H) {
                final MachineRow row = rows.get(machineScroll + r);
                if (row.unknown()) {
                    setMachine(UNKNOWN_MACHINE);
                    closeMachinePicker();
                } else if (row.header()) {
                    if (!expandedMods.add(row.namespace())) {
                        expandedMods.remove(row.namespace());
                    }
                    machineScroll = 0;
                } else {
                    setMachine(row.machineId());
                    closeMachinePicker();
                }
                return true;
            }
        }
        final boolean inPanel = mouseX >= x + MP_X && mouseX < x + MP_X + MP_W
                && mouseY >= y + MP_Y && mouseY < y + MP_Y + MP_H;
        if (!inPanel) {
            closeMachinePicker();
        }
        return true;
    }

    // --- chance popup ---

    private void openChancePopup(final int cell) {
        selectedOutput = cell;
        chancePopupOpen = true;
        final PatternEncoderBlockEntity be = be();
        setBox(chanceBox, be == null ? "100" : String.valueOf(be.outputChance(cell)));
        applyTabVisibility();
        setFocused(chanceBox);
        chanceBox.setFocused(true);
    }

    private void closeChancePopup() {
        chancePopupOpen = false;
        selectedOutput = -1;
        chanceBox.setFocused(false);
        applyTabVisibility();
    }

    // --- amount popup (fluid and chemical cells) ---

    /** The cell the amount popup is editing, or null when it is closed or the cell went away. */
    @org.jetbrains.annotations.Nullable
    private DataCell amountPopupCell() {
        final PatternEncoderBlockEntity be = be();
        if (be == null || !amountPopupOpen || amountCell < 0) {
            return null;
        }
        return amountForOutput ? be.procOutput(amountCell) : be.procInput(amountCell);
    }

    /** Opens the amount editor on a fluid or chemical cell (item cells carry their amount as the stack count). */
    private void openAmountPopup(final boolean output, final int cell, final DataCell current) {
        amountForOutput = output;
        amountCell = cell;
        amountPopupOpen = true;
        setBox(amountBox, String.valueOf(current.amount()));
        applyTabVisibility();
        setFocused(amountBox);
        amountBox.setFocused(true);
    }

    private void closeAmountPopup() {
        amountPopupOpen = false;
        amountCell = -1;
        amountBox.setFocused(false);
        applyTabVisibility();
    }

    private void onAmountChanged(final String value) {
        if (suppressResponder || value.isEmpty() || amountPopupCell() == null) {
            return;
        }
        try {
            sendAmount(Long.parseLong(value));
        } catch (final NumberFormatException ignored) {
            // A partial edit; the filter keeps it to digits.
        }
    }

    private void stepAmount(final int delta) {
        final DataCell cell = amountPopupCell();
        if (cell == null) {
            return;
        }
        // Read the box, not the server-synced cell, so several fast clicks in one tick compound instead of all
        // reading the same value before the first round trip lands.
        long current = cell.amount();
        try {
            current = Long.parseLong(amountBox.getValue());
        } catch (final NumberFormatException ignored) {
            // A blank or partial box: fall back to the last synced amount.
        }
        final long next = Math.max(1L, current + delta);
        setBox(amountBox, String.valueOf(next));
        sendAmount(next);
    }

    /** DONE confirms the amount as the author's own (an estimate stops being one) and closes the popup. */
    private void confirmAmount() {
        final DataCell cell = amountPopupCell();
        if (cell != null) {
            sendAmount(cell.amount());
        }
        closeAmountPopup();
    }

    private void sendAmount(final long amount) {
        if (amountCell < 0) {
            return;
        }
        send(new PatternEncoderEditPayload(menu.blockEntityPos(), PatternEncoderEditPayload.ACTION_SET_AMOUNT,
                amountCell, (int) Math.max(1L, Math.min(amount, Integer.MAX_VALUE)), amountForOutput ? "out" : "in"));
    }

    /** The amount popup: the cell's swatch and name, the steppers around the value, where the number came from. */
    private void renderAmountPopup(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final DataCell cell = amountPopupCell();
        if (cell == null) {
            return;
        }
        final int x = leftPos + AX;
        final int y = topPos + AY;
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0000000);
        JscOsTheme.panel(g, x, y, AW, AH);
        JscOsTheme.vLine(g, x, y, AH);
        JscOsTheme.vLine(g, x + AW - 1, y, AH);
        if (cell.key().isFluid()) {
            FluidSprite.draw(g, cell.key().fluidStack(1), x + 6, y + 4);
        } else {
            ChemicalSprite.draw(g, cell.key(), x + 6, y + 4);
        }
        JscOsTheme.text(g, font, cell.key().displayName().getString().toUpperCase(java.util.Locale.ROOT),
                x + 24, y + 5, JscOsTheme.text());
        JscOsTheme.textS(g, font, "amount per operation (mB)", x + 24, y + 15, JscOsTheme.dim());
        JscOsTheme.textS(g, font, cell.estimated() ? "estimated from the recipe: confirm or edit"
                : "the machine gets this much every run", x + 6, y + 42, cell.estimated() ? JscOsTheme.amber() : JscOsTheme.dim());
        for (final Button b : amountButtons) {
            b.render(g, mouseX, mouseY, partialTick);
        }
        amountBox.render(g, mouseX, mouseY, partialTick);
    }

    // --- render ---

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        updateWidgets();
        super.render(g, mouseX, mouseY, partialTick);
        // Popups draw at a raised Z so they sit above the slot ITEMS (Minecraft renders those at Z~150-250), not
        // just above the flat background — otherwise the disc/write button bleed through the modal.
        final boolean anyPopup = (stagePickerOpen && tab == PatternEncoderMenu.TAB_MULTI)
                || (tab == PatternEncoderMenu.TAB_PROCESSING && (chancePopupOpen || amountPopupOpen || machinePickerOpen));
        if (!anyPopup) {
            renderTooltip(g, mouseX, mouseY); // real slots: crafting grid, result, media, player inventory
            final DataCell ghost = hoveredProcCell(mouseX, mouseY);
            if (ghost != null && menu.getCarried().isEmpty()) {
                if (ghost.isItem()) {
                    g.renderTooltip(this.font, ghost.stack(), mouseX, mouseY);
                } else {
                    g.renderTooltip(this.font, cellTooltip(ghost), java.util.Optional.empty(), mouseX, mouseY);
                }
            }
        }
        if (anyPopup) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            if (stagePickerOpen && tab == PatternEncoderMenu.TAB_MULTI) {
                renderStagePicker(g, mouseX, mouseY);
            }
            if (chancePopupOpen && tab == PatternEncoderMenu.TAB_PROCESSING) {
                renderChancePopup(g, mouseX, mouseY, partialTick);
            }
            if (amountPopupOpen && tab == PatternEncoderMenu.TAB_PROCESSING) {
                renderAmountPopup(g, mouseX, mouseY, partialTick);
            }
            if (machinePickerOpen && tab == PatternEncoderMenu.TAB_PROCESSING) {
                renderMachinePicker(g, mouseX, mouseY, partialTick);
            }
            g.pose().popPose();
        }
    }

    /** The output-chance modal, drawn last so it sits above the grids, items and machine controls (no Z bleed). */
    private void renderChancePopup(final GuiGraphics g, final int mouseX, final int mouseY,
                                   final float partialTick) {
        final int x = leftPos;
        final int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xC0000000);
        JscOsTheme.panel(g, x + PX, y + PY, PW, PH);
        JscOsTheme.vLine(g, x + PX, y + PY, PH);
        JscOsTheme.vLine(g, x + PX + PW - 1, y + PY, PH);
        JscOsTheme.text(g, font, "OUTPUT CHANCE %", x + PX + 6, y + PY + 6, JscOsTheme.text());
        JscOsTheme.textS(g, font, "100 = guaranteed", x + PX + 6, y + PY + PH - 9, JscOsTheme.dim());
        guaranteedBtn.render(g, mouseX, mouseY, partialTick);
        chanceBox.render(g, mouseX, mouseY, partialTick);
    }

    private void openStagePicker() {
        stagePickerOpen = true;
        stageScroll = 0;
    }

    private static String stripExt(final String name) {
        final int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Modal picker listing the craftings on the medium; clicking one adds it as a stage (server routes by kind). */
    private void renderStagePicker(final GuiGraphics g, final int mouseX, final int mouseY) {
        final List<String> files = menu.craftFiles();
        final int px = leftPos + PICK_X;
        final int py = topPos + PICK_Y;
        final int shown = Math.min(PICK_MAX_ROWS, files.size());
        final int extra = files.size() > PICK_MAX_ROWS ? PICK_ROW_H : 0;
        final int ph = 15 + Math.max(1, shown) * PICK_ROW_H + extra + 3;
        // Dim the whole window, then a bordered floating panel that sits just below the tab bar.
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0000000);
        JscOsTheme.panel(g, px, py, PICK_W, ph);
        JscOsTheme.vLine(g, px, py, ph);
        JscOsTheme.vLine(g, px + PICK_W - 1, py, ph);
        JscOsTheme.text(g, font, "PICK A CRAFTING", px + 6, py + 3, JscOsTheme.accent());
        JscOsTheme.hLine(g, px + 4, py + 13, PICK_W - 8);
        if (files.isEmpty()) {
            JscOsTheme.textS(g, font, "no craftings on this medium", px + 8, py + 18, JscOsTheme.dim());
            return;
        }
        stageScroll = Math.max(0, Math.min(Math.max(0, files.size() - PICK_MAX_ROWS), stageScroll));
        for (int i = 0; i < shown; i++) {
            final String file = files.get(stageScroll + i);
            final int ry = py + 15 + i * PICK_ROW_H;
            final boolean hovered = mouseX >= px + 3 && mouseX < px + PICK_W - 3
                    && mouseY >= ry && mouseY < ry + PICK_ROW_H;
            if (hovered) {
                g.fill(px + 3, ry, px + PICK_W - 3, ry + PICK_ROW_H, JscOsTheme.hover());
            }
            JscOsTheme.textS(g, font, clamp(stripExt(file), 30), px + 8, ry + 2,
                    hovered ? JscOsTheme.text() : JscOsTheme.dim());
        }
        if (extra > 0) {
            JscOsTheme.textSRight(g, font, (stageScroll + 1) + "-"
                            + Math.min(files.size(), stageScroll + PICK_MAX_ROWS) + "/" + files.size(),
                    px + PICK_W - 6, py + 4, JscOsTheme.dim());
            JscOsTheme.textS(g, font, "scroll for more", px + 8, py + 15 + PICK_MAX_ROWS * PICK_ROW_H,
                    JscOsTheme.dim());
        }
    }

    private boolean clickStagePicker(final double mouseX, final double mouseY) {
        final List<String> files = menu.craftFiles();
        final int px = leftPos + PICK_X;
        final int py = topPos + PICK_Y;
        for (int i = 0; i < Math.min(PICK_MAX_ROWS, files.size() - stageScroll); i++) {
            final int ry = py + 15 + i * PICK_ROW_H;
            if (mouseX >= px + 3 && mouseX < px + PICK_W - 3 && mouseY >= ry && mouseY < ry + PICK_ROW_H) {
                send(PatternEncoderEditPayload.texted(menu.blockEntityPos(),
                        PatternEncoderEditPayload.ACTION_ADD_STAGE_FROM_MEDIA, files.get(stageScroll + i)));
                stagePickerOpen = false;
                return true;
            }
        }
        stagePickerOpen = false; // a click anywhere else dismisses the picker
        return true;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        final PatternEncoderBlockEntity be = be();

        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, imageWidth - 12);

        // Tab bar.
        JscOsTheme.button(g, x + TAB_C_X, y + TAB_Y, TAB_C_W, TAB_H, tab == PatternEncoderMenu.TAB_CRAFTING);
        JscOsTheme.button(g, x + TAB_P_X, y + TAB_Y, TAB_P_W, TAB_H, tab == PatternEncoderMenu.TAB_PROCESSING);
        JscOsTheme.button(g, x + TAB_M_X, y + TAB_Y, TAB_M_W, TAB_H, tab == PatternEncoderMenu.TAB_MULTI);

        // Shared bottom bar: the media bay and the write control.
        JscOsTheme.slot(g, x + MEDIA_X, y + MEDIA_Y);
        final boolean writable = writeEnabled(be);
        JscOsTheme.button(g, x + WRITE_X, y + WRITE_Y, WRITE_W, WRITE_H,
                writable && hover(mouseX, mouseY, WRITE_X, WRITE_Y, WRITE_W, WRITE_H));

        // Player inventory frames (every tab).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + 8 + col * 18, y + 138 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + 8 + col * 18, y + 196);
        }

        switch (tab) {
            case PatternEncoderMenu.TAB_PROCESSING -> renderProcessingBg(g, x, y, be, mouseX, mouseY);
            case PatternEncoderMenu.TAB_MULTI -> renderMultiBg(g, x, y, be);
            default -> renderCraftingBg(g, x, y);
        }
        // The chance popup is drawn last, on top of everything, in render() — not here (that was the Z bug).
    }

    private void renderCraftingBg(final GuiGraphics g, final int x, final int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                JscOsTheme.slot(g, x + 26 + col * 18, y + 44 + row * 18);
            }
        }
        JscOsTheme.slot(g, x + 100, y + 62);
    }

    private void renderProcessingBg(final GuiGraphics g, final int x, final int y,
                                    final PatternEncoderBlockEntity be, final int mouseX, final int mouseY) {
        for (int visRow = 0; visRow < PROC_VIS_ROWS; visRow++) {
            for (int col = 0; col < PROC_COLS; col++) {
                final int iIn = (inScroll + visRow) * PROC_COLS + col;
                final int iOut = (outScroll + visRow) * PROC_COLS + col;
                final int ix = x + IN_X + col * 18;
                final int iy = y + IN_Y + visRow * 18;
                final int ox = x + OUT_X + col * 18;
                final int oy = y + OUT_Y + visRow * 18;
                JscOsTheme.slot(g, ix, iy);
                JscOsTheme.slot(g, ox, oy);
                if (be != null && iIn < PROC) {
                    final DataCell in = be.procInput(iIn);
                    if (in != null) {
                        renderCell(g, in, ix, iy);
                    }
                }
                if (be != null && iOut < PROC) {
                    final DataCell out = be.procOutput(iOut);
                    if (out != null) {
                        renderCell(g, out, ox, oy);
                        final int chance = be.outputChance(iOut);
                        if (chance < ProcessingPattern.FULL_CHANCE) {
                            JscOsTheme.textSRight(g, font, chance + "%", ox + 16, oy + 11, JscOsTheme.amber());
                        }
                    }
                }
            }
        }
        // The two grids scroll independently: inputs by inScroll, outputs by outScroll.
        drawScrollbar(g, x + SB_IN_X, y + IN_Y, inScroll);
        drawScrollbar(g, x + SB_OUT_X, y + IN_Y, outScroll);

        // Hover highlight over the ghost cells, raised above the drawn items (renderItem uses Z~150).
        if (!chancePopupOpen && !amountPopupOpen && !machinePickerOpen) {
            for (int visRow = 0; visRow < PROC_VIS_ROWS; visRow++) {
                for (int col = 0; col < PROC_COLS; col++) {
                    final int ix = x + IN_X + col * 18;
                    final int iy = y + IN_Y + visRow * 18;
                    final int ox = x + OUT_X + col * 18;
                    final int oy = y + OUT_Y + visRow * 18;
                    final boolean overIn = mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16;
                    final boolean overOut = mouseX >= ox && mouseX < ox + 16 && mouseY >= oy && mouseY < oy + 16;
                    if (overIn || overOut) {
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 200);
                        g.fill(overIn ? ix : ox, overIn ? iy : oy,
                                (overIn ? ix : ox) + 16, (overIn ? iy : oy) + 16, 0x80FFFFFF);
                        g.pose().popPose();
                    }
                }
            }
        }
    }

    /**
     * One processing cell: an item draws as its stack (count = amount); a fluid or chemical draws as its sprite
     * with the amount in millibuckets, and an estimated amount wears the "≈" mark until the author confirms it.
     */
    private void renderCell(final GuiGraphics g, final DataCell cell, final int x, final int y) {
        if (cell.isItem()) {
            final ItemStack stack = cell.stack();
            g.renderItem(stack, x, y);
            g.renderItemDecorations(font, stack, x, y);
        } else {
            if (cell.key().isFluid()) {
                FluidSprite.draw(g, cell.key().fluidStack(1), x, y);
            } else {
                ChemicalSprite.draw(g, cell.key(), x, y);
            }
            JscOsTheme.textSRight(g, font, amountLabel(cell.amount()), x + 16, y + 11, JscOsTheme.text());
        }
        if (cell.estimated()) {
            JscOsTheme.textS(g, font, "≈", x + 1, y, JscOsTheme.amber());
        }
    }

    /** Millibuckets in a cell corner: whole numbers up to 9 999, then thousands ("12k"). */
    private static String amountLabel(final long amount) {
        return amount >= 10_000L ? (amount / 1000L) + "k" : String.valueOf(amount);
    }

    /** What the tooltip of a fluid or chemical cell says: the name, the amount, and whether it is an estimate. */
    private List<Component> cellTooltip(final DataCell cell) {
        final List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.literal(cell.key().displayName().getString() + " · " + cell.amount() + " mB"));
        lines.add(Component.literal("per operation").withStyle(net.minecraft.ChatFormatting.GRAY));
        if (cell.estimated()) {
            lines.add(Component.literal("estimated from the recipe; speed upgrades may need less")
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        lines.add(Component.literal("click to edit · right-click to clear").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** The processing cell under the cursor (inputs or outputs, honoring each grid's scroll), if any. */
    @org.jetbrains.annotations.Nullable
    private DataCell hoveredProcCell(final int mouseX, final int mouseY) {
        final PatternEncoderBlockEntity be = be();
        if (be == null || tab != PatternEncoderMenu.TAB_PROCESSING) {
            return null;
        }
        for (int visRow = 0; visRow < PROC_VIS_ROWS; visRow++) {
            for (int col = 0; col < PROC_COLS; col++) {
                final int ix = leftPos + IN_X + col * 18;
                final int iy = topPos + IN_Y + visRow * 18;
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                    return be.procInput((inScroll + visRow) * PROC_COLS + col);
                }
                final int ox = leftPos + OUT_X + col * 18;
                final int oy = topPos + OUT_Y + visRow * 18;
                if (mouseX >= ox && mouseX < ox + 16 && mouseY >= oy && mouseY < oy + 16) {
                    return be.procOutput((outScroll + visRow) * PROC_COLS + col);
                }
            }
        }
        return null;
    }

    /** A real scrollbar: a recessed visible track with an accent thumb sized to the visible fraction. */
    private void drawScrollbar(final GuiGraphics g, final int sx, final int sy, final int scroll) {
        if (PROC_MAX_SCROLL <= 0) {
            return;
        }
        g.fill(sx, sy, sx + SB_W, sy + SB_H, JscOsTheme.slotBg());  // track interior (visible)
        JscOsTheme.vLine(g, sx, sy, SB_H);                          // left edge
        JscOsTheme.vLine(g, sx + SB_W - 1, sy, SB_H);               // right edge
        final int thumbH = thumbHeight();
        final int ty = sy + (SB_H - thumbH) * scroll / PROC_MAX_SCROLL;
        g.fill(sx + 1, ty, sx + SB_W - 1, ty + thumbH, JscOsTheme.accent());
    }

    private static int thumbHeight() {
        final int totalRows = (PROC + PROC_COLS - 1) / PROC_COLS;
        return Math.max(8, SB_H * PROC_VIS_ROWS / totalRows);
    }

    /** Set scrollbar {@code which} (1 = inputs, 2 = outputs) so its thumb centre follows the cursor. */
    private void scrollToMouse(final double mouseY, final int which) {
        final int sy = topPos + IN_Y;
        final int thumbH = thumbHeight();
        final int span = SB_H - thumbH;
        if (span <= 0) {
            return;
        }
        final int rel = (int) Math.round((mouseY - sy - thumbH / 2.0) * PROC_MAX_SCROLL / span);
        final int clamped = Math.max(0, Math.min(PROC_MAX_SCROLL, rel));
        if (which == 1) {
            inScroll = clamped;
        } else {
            outScroll = clamped;
        }
    }

    private void renderMultiBg(final GuiGraphics g, final int x, final int y,
                               final PatternEncoderBlockEntity be) {
        final List<MultiStagePattern.Stage> stages = be == null ? List.of() : be.stages();
        final int rows = Math.min(3, stages.size());
        for (int i = 0; i < rows; i++) {
            JscOsTheme.panel(g, x + 8, y + 44 + i * 12, 118, 11);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        final PatternEncoderBlockEntity be = be();
        JscOsTheme.text(g, font, "PATTERN ENCODER", 12, 11, JscOsTheme.text());

        // Tab labels.
        JscOsTheme.textCenter(g, font, "CRAFTING", TAB_C_X + TAB_C_W / 2, TAB_Y + 2,
                tab == PatternEncoderMenu.TAB_CRAFTING ? JscOsTheme.tabLabelOn() : JscOsTheme.dim());
        JscOsTheme.textCenter(g, font, "PROCESSING", TAB_P_X + TAB_P_W / 2, TAB_Y + 2,
                tab == PatternEncoderMenu.TAB_PROCESSING ? JscOsTheme.tabLabelOn() : JscOsTheme.dim());
        JscOsTheme.textCenter(g, font, "MULTI-STAGE", TAB_M_X + TAB_M_W / 2, TAB_Y + 2,
                tab == PatternEncoderMenu.TAB_MULTI ? JscOsTheme.tabLabelOn() : JscOsTheme.dim());

        // Media readout next to the bottom-bar bay.
        final ItemStack mediaItem = menu.mediaStack();
        if (mediaItem.isEmpty()) {
            JscOsTheme.textS(g, font, "no media", MEDIA_X + 22, MEDIA_Y + 2, JscOsTheme.dim());
        } else {
            JscOsTheme.textS(g, font, mediaItem.getHoverName().getString(), MEDIA_X + 22, MEDIA_Y + 1,
                    JscOsTheme.text());
            final int count = menu.craftFiles().size();
            JscOsTheme.textS(g, font, count + (count == 1 ? " craft" : " crafts"),
                    MEDIA_X + 22, MEDIA_Y + 10, JscOsTheme.dim());
        }

        // Write label.
        final boolean writable = writeEnabled(be);
        JscOsTheme.textCenter(g, font, writeLabel(), WRITE_X + WRITE_W / 2, WRITE_Y + 4,
                writable ? JscOsTheme.green() : JscOsTheme.dim());

        switch (tab) {
            case PatternEncoderMenu.TAB_PROCESSING -> renderProcessingLabels(g, be);
            case PatternEncoderMenu.TAB_MULTI -> renderMultiLabels(g, be);
            default -> renderCraftingLabels(g);
        }
        // The chance popup title is drawn in render() (on top), not here.
    }

    private void renderCraftingLabels(final GuiGraphics g) {
        JscOsTheme.text(g, font, "RECIPE", 26, 34, JscOsTheme.dim());
        JscOsTheme.text(g, font, ">", 88, 66, JscOsTheme.dim());
        if (menu.preview().isEmpty()) {
            JscOsTheme.textS(g, font, "lay out a known recipe", 26, 100, JscOsTheme.dim());
        }
        final List<String> files = menu.craftFiles();
        JscOsTheme.textS(g, font, "ON THIS MEDIA", 122, 34, JscOsTheme.dim());
        if (files.isEmpty()) {
            JscOsTheme.textS(g, font, "-", 122, 44, JscOsTheme.dim());
        } else {
            final int first = Math.max(0, files.size() - 6);
            int rowY = 44;
            for (int i = first; i < files.size(); i++) {
                // Extension dropped and the name clamped so a long file name cannot run past the window edge.
                JscOsTheme.textS(g, font, clamp(stripExt(files.get(i)), 15), 122, rowY, JscOsTheme.text());
                rowY += 9;
            }
        }
    }

    private static String clamp(final String s, final int maxChars) {
        return s.length() <= maxChars ? s : s.substring(0, maxChars - 1) + "…";
    }

    private void renderProcessingLabels(final GuiGraphics g, final PatternEncoderBlockEntity be) {
        JscOsTheme.text(g, font, "INPUTS", IN_X, 34, JscOsTheme.dim());
        JscOsTheme.textRight(g, font, "OUTPUTS", OUT_X + 54, 34, JscOsTheme.dim());
        JscOsTheme.text(g, font, "MACHINE", 70, 34, JscOsTheme.dim());
        JscOsTheme.text(g, font, "TIMEOUT", 70, 73, JscOsTheme.dim());
        JscOsTheme.textS(g, font, "ticks", 113, 85, JscOsTheme.dim());
        if (be != null) {
            final String machine = be.machineType().isBlank() ? "no machine" : shortMachine(be.machineType());
            final ProcessingPattern pattern = be.buildProcessingPattern();
            final String summary = machine + "  ·  " + pattern.inputs().size() + " in / "
                    + pattern.outputs().size() + " out  ·  " + be.procTimeout() + "t";
            JscOsTheme.textS(g, font, summary, IN_X, 99, JscOsTheme.dim());
        }
    }

    private void renderMultiLabels(final GuiGraphics g, final PatternEncoderBlockEntity be) {
        JscOsTheme.text(g, font, "STAGES", 8, 34, JscOsTheme.dim());
        final List<MultiStagePattern.Stage> stages = be == null ? List.of() : be.stages();
        if (stages.isEmpty()) {
            JscOsTheme.textS(g, font, "Add stage -> pick a crafting on the medium", 8, 46, JscOsTheme.dim());
        }
        final int rows = Math.min(3, stages.size());
        for (int i = 0; i < rows; i++) {
            JscOsTheme.textS(g, font, stageLabel(i + 1, stages.get(i)), 11, 46 + i * 12, JscOsTheme.text());
            JscOsTheme.textSRight(g, font, "[x]", 124, 46 + i * 12, JscOsTheme.red());
        }
        if (stages.size() > rows) {
            JscOsTheme.textS(g, font, "+" + (stages.size() - rows) + " more", 11, 46 + rows * 12, JscOsTheme.dim());
        }
    }

    // --- input ---

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        // The machine picker is modal: rows/headers/search handled here, a miss closes it.
        if (machinePickerOpen && tab == PatternEncoderMenu.TAB_PROCESSING) {
            return clickMachinePicker(mouseX, mouseY);
        }
        // The stage picker is modal: it swallows every click (a row adds that stage, a miss closes it).
        if (stagePickerOpen && tab == PatternEncoderMenu.TAB_MULTI) {
            return clickStagePicker(mouseX, mouseY);
        }
        // The chance popup is modal: a click inside the panel routes to its widgets; a click anywhere else just
        // dismisses it (without falling through to the slots/inventory behind the dim).
        if (chancePopupOpen && tab == PatternEncoderMenu.TAB_PROCESSING) {
            final boolean inPanel = mouseX >= leftPos + PX && mouseX < leftPos + PX + PW
                    && mouseY >= topPos + PY && mouseY < topPos + PY + PH;
            if (inPanel && super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            closeChancePopup();
            return true;
        }
        // The amount popup is modal in the same way; a click outside it keeps the value typed so far.
        if (amountPopupOpen && tab == PatternEncoderMenu.TAB_PROCESSING) {
            final boolean inPanel = mouseX >= leftPos + AX && mouseX < leftPos + AX + AW
                    && mouseY >= topPos + AY && mouseY < topPos + AY + AH;
            if (inPanel && super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            closeAmountPopup();
            return true;
        }
        // Manual hit areas (tabs, write, ghost grids, stage list) are tested BEFORE the container, so an empty
        // slot or the creative inventory can't swallow a tab/button click (the dead-button bug).
        if (button == 0) {
            if (hover((int) mouseX, (int) mouseY, TAB_C_X, TAB_Y, TAB_C_W, TAB_H)) {
                switchTab(PatternEncoderMenu.TAB_CRAFTING);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, TAB_P_X, TAB_Y, TAB_P_W, TAB_H)) {
                switchTab(PatternEncoderMenu.TAB_PROCESSING);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, TAB_M_X, TAB_Y, TAB_M_W, TAB_H)) {
                switchTab(PatternEncoderMenu.TAB_MULTI);
                return true;
            }
            if (writeEnabled(be()) && hover((int) mouseX, (int) mouseY, WRITE_X, WRITE_Y, WRITE_W, WRITE_H)) {
                onWrite();
                return true;
            }
        }
        if (tab == PatternEncoderMenu.TAB_PROCESSING && clickProcGrids(mouseX, mouseY, button)) {
            return true;
        }
        if (tab == PatternEncoderMenu.TAB_MULTI && clickStageList(mouseX, mouseY, button)) {
            return true;
        }
        // Then the container handles the EditBoxes, the crafting ghost slots, and the player inventory.
        final boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // The container focuses whichever widget took the click — the machine button, when it just opened the
        // picker — which would steal the focus the picker gave its search box. Keyboard-first users expect to
        // type straight away, so the search box wins while the picker is open.
        if (machinePickerOpen && getFocused() != machineSearch) {
            setFocused(machineSearch);
            machineSearch.setFocused(true);
        }
        return handled;
    }

    private boolean clickProcGrids(final double mouseX, final double mouseY, final int button) {
        // Scrollbar: a click on either track grabs that thumb and starts a drag (1 = inputs, 2 = outputs).
        final int sb = onScrollbar(mouseX, mouseY);
        if (button == 0 && PROC_MAX_SCROLL > 0 && sb != 0) {
            draggingScroll = sb;
            scrollToMouse(mouseY, sb);
            return true;
        }
        final ItemStack carried = menu.getCarried();
        for (int visRow = 0; visRow < PROC_VIS_ROWS; visRow++) {
            for (int col = 0; col < PROC_COLS; col++) {
                final int iIn = (inScroll + visRow) * PROC_COLS + col;
                final int iOut = (outScroll + visRow) * PROC_COLS + col;
                final int ix = leftPos + IN_X + col * 18;
                final int iy = topPos + IN_Y + visRow * 18;
                if (iIn < PROC && inRect(mouseX, mouseY, ix, iy, 16, 16)) {
                    final DataCell current = be() == null ? null : be().procInput(iIn);
                    if (button == 0 && !carried.isEmpty()) {
                        // Place from the carried stack: an item keeps its count as the amount, a fluid or
                        // chemical container names its substance with a bucket's worth to start from.
                        send(PatternEncoderEditPayload.indexed(menu.blockEntityPos(),
                                PatternEncoderEditPayload.ACTION_SET_INPUT, iIn));
                    } else if (button == 0 && current != null && !current.isItem()) {
                        openAmountPopup(false, iIn, current);
                    } else if (button == 0 || button == 1) {
                        // An empty hand (left) or a right-click clears the cell.
                        send(new PatternEncoderEditPayload(menu.blockEntityPos(),
                                PatternEncoderEditPayload.ACTION_CLEAR_CELL, iIn, 0, "in"));
                    }
                    return true;
                }
                final int ox = leftPos + OUT_X + col * 18;
                final int oy = topPos + OUT_Y + visRow * 18;
                if (iOut < PROC && inRect(mouseX, mouseY, ox, oy, 16, 16)) {
                    final DataCell current = be() == null ? null : be().procOutput(iOut);
                    if (button == 0 && !carried.isEmpty()) {
                        send(PatternEncoderEditPayload.indexed(menu.blockEntityPos(),
                                PatternEncoderEditPayload.ACTION_SET_OUTPUT, iOut));
                    } else if (button == 0 && current != null && !current.isItem()) {
                        openAmountPopup(true, iOut, current);
                    } else if (button == 0 && current != null) {
                        openChancePopup(iOut);
                    } else if (button == 1) {
                        send(new PatternEncoderEditPayload(menu.blockEntityPos(),
                                PatternEncoderEditPayload.ACTION_CLEAR_CELL, iOut, 0, "out"));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX,
                                 final double scrollY) {
        if (machinePickerOpen && tab == PatternEncoderMenu.TAB_PROCESSING && scrollY != 0) {
            final int max = Math.max(0, visibleMachineRows().size() - MP_VIS_ROWS);
            machineScroll = Math.max(0, Math.min(max, machineScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (stagePickerOpen && tab == PatternEncoderMenu.TAB_MULTI && scrollY != 0) {
            final int max = Math.max(0, menu.craftFiles().size() - PICK_MAX_ROWS);
            stageScroll = Math.max(0, Math.min(max, stageScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (tab == PatternEncoderMenu.TAB_PROCESSING && PROC_MAX_SCROLL > 0 && scrollY != 0) {
            // Scroll the grid under the cursor: left half = inputs, right half = outputs.
            final int delta = -(int) Math.signum(scrollY);
            if (mouseX < leftPos + OUT_X) {
                inScroll = Math.max(0, Math.min(PROC_MAX_SCROLL, inScroll + delta));
            } else {
                outScroll = Math.max(0, Math.min(PROC_MAX_SCROLL, outScroll + delta));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button,
                                final double dragX, final double dragY) {
        if (draggingScroll != 0 && button == 0) {
            scrollToMouse(mouseY, draggingScroll);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        if (draggingScroll != 0 && button == 0) {
            draggingScroll = 0;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Which scrollbar track the cursor is over: 1 = inputs, 2 = outputs, 0 = none. */
    private int onScrollbar(final double mouseX, final double mouseY) {
        final int sy = topPos + IN_Y;
        if (mouseY < sy || mouseY >= sy + SB_H) {
            return 0;
        }
        final int inX = leftPos + SB_IN_X;
        final int outX = leftPos + SB_OUT_X;
        if (mouseX >= inX && mouseX < inX + SB_W) {
            return 1;
        }
        if (mouseX >= outX && mouseX < outX + SB_W) {
            return 2;
        }
        return 0;
    }

    private boolean clickStageList(final double mouseX, final double mouseY, final int button) {
        if (button != 0) {
            return false;
        }
        final PatternEncoderBlockEntity be = be();
        final int rows = be == null ? 0 : Math.min(3, be.stages().size());
        for (int i = 0; i < rows; i++) {
            // Only the [x] zone removes the stage — a click elsewhere on the row must not destroy work.
            if (inRect(mouseX, mouseY, leftPos + 110, topPos + 44 + i * 12, 16, 11)) {
                send(PatternEncoderEditPayload.indexed(menu.blockEntityPos(),
                        PatternEncoderEditPayload.ACTION_REMOVE_STAGE, i));
                return true;
            }
        }
        return false;
    }

    private void onWrite() {
        switch (tab) {
            case PatternEncoderMenu.TAB_PROCESSING -> send(PatternEncoderEditPayload.action(
                    menu.blockEntityPos(), PatternEncoderEditPayload.ACTION_WRITE_PROC));
            case PatternEncoderMenu.TAB_MULTI -> send(PatternEncoderEditPayload.action(
                    menu.blockEntityPos(), PatternEncoderEditPayload.ACTION_WRITE_MULTI));
            default -> sendButton(PatternEncoderMenu.BUTTON_WRITE);
        }
        requestFiles();
    }

    // --- helpers ---

    private boolean writeEnabled(final PatternEncoderBlockEntity be) {
        if (be == null) {
            return false;
        }
        return switch (tab) {
            case PatternEncoderMenu.TAB_PROCESSING -> be.canWriteProcessing();
            case PatternEncoderMenu.TAB_MULTI -> be.canWriteMultiStage();
            default -> menu.canWrite();
        };
    }

    private String writeLabel() {
        return switch (tab) {
            case PatternEncoderMenu.TAB_PROCESSING -> "WRITE PROCESSING";
            case PatternEncoderMenu.TAB_MULTI -> "WRITE MULTI-STAGE";
            default -> "WRITE PATTERN";
        };
    }

    private static String stageLabel(final int n, final MultiStagePattern.Stage stage) {
        if (stage.bench().isPresent()) {
            return n + ". BENCH  " + stage.bench().get().result().getHoverName().getString();
        }
        if (stage.proc().isPresent()) {
            final ProcessingPattern p = stage.proc().get();
            final ProcessingPattern.ProcessingOutput primary = p.primaryOutput();
            return n + ". " + shortMachine(p.machineType())
                    + (primary == null ? "" : "  " + primary.key().displayName().getString());
        }
        return n + ". (empty)";
    }

    private static String shortMachine(final String machineType) {
        if (machineType.isBlank()) {
            return "machine";
        }
        if (machineType.equals(UNKNOWN_MACHINE)) {
            return "unknown machine";
        }
        final int colon = machineType.indexOf(':');
        return colon >= 0 ? machineType.substring(colon + 1) : machineType;
    }

    private static boolean inRect(final double mx, final double my, final int x, final int y,
                                  final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void send(final PatternEncoderEditPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private void requestFiles() {
        PacketDistributor.sendToServer(new RequestPatternEncoderFilesPayload(menu.blockEntityPos()));
    }
}
