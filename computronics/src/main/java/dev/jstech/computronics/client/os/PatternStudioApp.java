/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.crafting.MachineCategory;
import dev.jstech.computronics.crafting.PatternWorkbench;
import dev.jstech.computronics.crafting.ProcessingPattern;
import dev.jstech.computronics.gui.layout.PatternStudioLayout;
import dev.jstech.computronics.operation.payload.PatternStudioEditPayload;
import dev.jstech.computronics.operation.payload.PatternStudioStatePayload;
import dev.jstech.computronics.operation.payload.RequestPatternStudioPayload;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.client.gui.component.AmountStepper;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.CellGrid;
import dev.jstech.core.client.gui.component.FlowLayout;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.Popup;
import dev.jstech.core.client.gui.component.ProgressBar;
import dev.jstech.core.client.gui.component.SearchField;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.Texts;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Pattern Studio: where recipes are authored. Three drafts live on the machine (a bench recipe, a machine
 * recipe and a multi-stage pipeline), shown one per tab, each a ghost editor: a click on a cell records a copy
 * of what the cursor carries (the player's inventory sits in a band under the editor), a recipe transferred
 * or dragged from the recipe viewer beside the monitor lays itself out, and nothing is ever consumed. The rail
 * on the right lists the files on the computer's drives and the linked encoder; the bar at the bottom sends a
 * finished draft to the encoder, the system disk or this Crafting Computer's Recipe ROM.
 *
 * <p>The content is a tree of the core's components, laid out every frame from the window's size; the app
 * keeps the draft state the server sends and the callbacks that send edits back.
 */
public final class PatternStudioApp implements InventoryBandApp {

    private static final int TAB_H = PatternStudioLayout.TAB_H;
    private static final int RAIL_W = 112;
    private static final int RAIL_TAB_H = 11;
    private static final int ROW_H = 11;
    // The vertical arithmetic (tabs, editor, band, bar) lives in the pure layout so a test can prove the band
    // fits the window a standard monitor opens the program in.
    private static final int CELL = PatternStudioLayout.CELL;
    private static final int BAR_H = PatternStudioLayout.BAR_H;
    private static final int PAD = PatternStudioLayout.PAD;
    private static final int FIELD_H = PatternStudioLayout.FIELD_H;
    private static final int BTN_H = 12;
    private static final int BAR_BTN_W = 62;
    private static final int NAME_LABEL_W = 28;
    private static final int PROC_COLS = PatternStudioLayout.PROC_COLS;
    private static final int PROC_ROWS = PatternStudioLayout.PROC_ROWS;
    private static final int REFRESH_EVERY_FRAMES = 60;
    private static final int[] CHANCE_STEPS = {100, 75, 50, 25, 10};
    private static final int ERROR_RED = 0xFFEF6A5A;

    // The player's inventory band under the editor: three rows, a gap, the hotbar, inside a frame. The desktop
    // lays the real container slots over these cells.
    private static final int INV_COLS = PatternStudioLayout.INV_COLS;
    private static final int INV_ROWS = PatternStudioLayout.INV_ROWS;
    private static final int BAND_PAD = PatternStudioLayout.BAND_PAD;
    private static final int BAND_H = PatternStudioLayout.BAND_H;
    private static final int BAND_W = PatternStudioLayout.BAND_W;

    public static final int TAB_BENCH = 0;
    public static final int TAB_MACHINE = 1;
    public static final int TAB_PIPELINE = 2;
    public static final int RAIL_FILES = 0;
    public static final int RAIL_ENCODER = 1;

    private static PatternStudioApp active;

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();

    @Nullable
    private PatternStudioStatePayload state;
    private String status = "";
    private int statusFrames;
    private int refreshFrames;
    private int tab;
    private int rail;

    // Geometry of the last frame, so clicks land where the player sees things.
    private int lastX;
    private int lastY;
    private int lastW;
    private int lastH;
    private int lastMouseX;
    private int lastMouseY;
    @Nullable
    private Font lastFont;
    private final List<int[]> ghostCells = new ArrayList<>(); // {x, y, w, h, kind(0 bench,1 in,2 out), index}

    // The content: one tree, the components of the other tabs hidden.
    private final Panel root = new Panel();
    private final TabStrip tabs;
    // Bench.
    private final CellGrid benchGrid;
    private final CellGrid resultCell;
    private final Label arrow;
    private final Label resultLine;
    private final Label benchFileLine;
    private final Label benchRomLine;
    private final Button benchClear;
    private final NameNoteRow benchNames;
    // Machine.
    private final CellGrid inGrid;
    private final CellGrid outGrid;
    private final Button machineButton;
    private final Label timeoutLabel;
    private final TextField timeout;
    private final Label procFlag;
    private final Button procClear;
    private final NameNoteRow procNames;
    // Multi-stage.
    private final ListView<PatternStudioStatePayload.Stage> stageList;
    private final Label noStages;
    private final Button addBench;
    private final Button addMachine;
    private final Button removeStage;
    private final Label pipeRom;
    private final NameNoteRow pipeNames;
    // Rail.
    private final TabStrip railTabs;
    private final ListView<FileRow> fileList;
    private final Label noDrives;
    private final Label railHint;
    private final Label encLine1;
    private final Label encLine2;
    private final Label encLine3;
    private final ProgressBar encProgress;
    private final Label encQueued;
    private final Button encCancel;
    private final Button encEject;
    // Bar.
    private final Button burn;
    private final Button saveDisk;
    private final Button loadRom;
    private final Label statusLine;
    // Popups.
    private final Popup machinePicker;
    private final SearchField machineSearch;
    private final ListView<Choice> machineList;
    private final Button pickerClose;
    private final Popup amountPopup;
    private final AmountStepper amount;
    private final Button amountClear;
    private final Button amountDone;
    private boolean amountForOutput;
    private int amountCell = -1;

    public PatternStudioApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;

        tabs = root.add(new TabStrip(List.of("Bench", "Machine", "Multi-stage")).setOnSelect(this::selectTab));

        benchGrid = root.add(new CellGrid(3, 3, 3, CELL)
                .setRenderer(this::renderBenchCell)
                .setMarked(this::benchCellMarked)
                .setOnClick(this::benchCellClicked));
        resultCell = root.add(new CellGrid(1, 1, 1, CELL).setRenderer(this::renderResultCell));
        arrow = root.add(new Label("->", Label.Tone.DIM));
        resultLine = root.add(new Label(this::resultText).setTone(this::resultTone));
        benchFileLine = root.add(new Label(this::benchFileText, Label.Tone.DIM));
        benchRomLine = root.add(new Label("In the Recipe ROM", Label.Tone.ACCENT));
        benchClear = root.add(new Button("Clear", () -> send(of(PatternStudioEditPayload.BENCH_CLEAR))));
        benchNames = root.add(new NameNoteRow(PatternStudioEditPayload.BENCH_SET_NAME));

        inGrid = root.add(new CellGrid(PROC_COLS, PROC_ROWS, PatternWorkbench.PROC_GRID / PROC_COLS, CELL)
                .setCues(CellGrid.Cues.RIGHT)
                .setRenderer((g, ctx, index, cx, cy, size, hovered) -> renderProcCell(g, ctx, false, index, cx, cy))
                .setMarked(index -> procCellMarked(false, index))
                .setOnClick((index, button, shift) -> procCellClicked(false, index, button, shift)));
        outGrid = root.add(new CellGrid(PROC_COLS, PROC_ROWS, PatternWorkbench.PROC_GRID / PROC_COLS, CELL)
                .setCues(CellGrid.Cues.LEFT)
                .setRenderer((g, ctx, index, cx, cy, size, hovered) -> renderProcCell(g, ctx, true, index, cx, cy))
                .setMarked(index -> procCellMarked(true, index))
                .setOnClick((index, button, shift) -> procCellClicked(true, index, button, shift)));
        machineButton = root.add(new Button(this::machineButtonLabel, this::openMachinePicker));
        timeoutLabel = root.add(new Label("Timeout", Label.Tone.DIM));
        timeout = root.add(new TextField(6).setOnCommit(this::commitTimeout));
        procFlag = root.add(new Label(this::procFlagText).setTone(this::procFlagTone));
        procClear = root.add(new Button("Clear", () -> send(of(PatternStudioEditPayload.PROC_CLEAR))));
        procNames = root.add(new NameNoteRow(PatternStudioEditPayload.PROC_SET_NAME));

        stageList = root.add(new ListView<PatternStudioStatePayload.Stage>(this::stages, ROW_H, this::renderStageRow)
                .setSelectable(true)
                .setPadding(1));
        noStages = root.add(new Label("No stages yet", Label.Tone.DIM));
        addBench = root.add(new Button("+ Bench", () -> send(of(PatternStudioEditPayload.PIPE_ADD_BENCH))));
        addMachine = root.add(new Button("+ Machine", () -> send(of(PatternStudioEditPayload.PIPE_ADD_PROC))));
        removeStage = root.add(new Button("Remove", this::removeSelectedStage));
        pipeRom = root.add(new Label("In ROM", Label.Tone.ACCENT).setAlign(Label.Align.RIGHT));
        pipeNames = root.add(new NameNoteRow(PatternStudioEditPayload.PIPE_SET_NAME));

        railTabs = root.add(new TabStrip(List.of("Files", "Encoder")).setUnderline(false).setOnSelect(this::selectRail));
        fileList = root.add(new ListView<FileRow>(this::fileRows, ROW_H, this::renderFileRow).setOnClick(this::fileRowClicked));
        noDrives = root.add(new Label("No drives", Label.Tone.DIM));
        railHint = root.add(new Label(this::railHintText, Label.Tone.DIM));
        encLine1 = root.add(new Label(this::encoderLine1).setTone(this::encoderLine1Tone));
        encLine2 = root.add(new Label(this::encoderLine2).setTone(this::encoderLine2Tone));
        encLine3 = root.add(new Label(this::encoderLine3).setTone(this::encoderLine3Tone).setColor(this::encoderLine3Color));
        encProgress = root.add(new ProgressBar(this::encoderProgress));
        encQueued = root.add(new Label(this::encoderQueued, Label.Tone.DIM));
        encCancel = root.add(new Button("Cancel", () -> send(of(PatternStudioEditPayload.ENCODER_CANCEL))));
        encEject = root.add(new Button("Eject", () -> send(of(PatternStudioEditPayload.ENCODER_EJECT))));

        burn = root.add(new Button("Burn", () -> barAction(PatternStudioEditPayload.BURN)));
        saveDisk = root.add(new Button("Save to disk", () -> barAction(PatternStudioEditPayload.SAVE_TO_DISK)));
        loadRom = root.add(new Button(this::loadRomLabel, () -> barAction(PatternStudioEditPayload.LOAD_INTO_ROM)));
        statusLine = root.add(new Label(this::statusText).setTone(this::statusTone));

        machineSearch = new SearchField(48);
        machineSearch.setOnEdit(this::resetMachineScroll);
        machineList = new ListView<Choice>(this::machineChoices, ROW_H, this::renderChoiceRow).setOnClick(this::choiceClicked);
        pickerClose = new Button("Close", this::closeMachinePicker);
        machinePicker = new Popup("Pick a machine", 220, 150).setLayouter(this::layoutMachinePicker);
        machinePicker.add(machineSearch);
        machinePicker.add(machineList);
        machinePicker.add(pickerClose);

        amount = new AmountStepper();
        amountClear = new Button("Clear", () -> commitAmount(0L));
        amountDone = new Button("Done", () -> commitAmount(amount.amount()));
        amountPopup = new Popup(this::amountTitle, 150, 60).setLayouter(this::layoutAmountPopup);
        amountPopup.add(amount);
        amountPopup.add(amountClear);
        amountPopup.add(amountDone);

        active = this;
        request();
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestPatternStudioPayload(host, monitorPos));
    }

    private void send(final PatternStudioEditPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private PatternStudioEditPayload of(final int action) {
        return PatternStudioEditPayload.of(host, monitorPos, action);
    }

    @Override
    public void onRestored() {
        active = this;
        request();
    }

    @Override
    public void markActive() {
        active = this;
    }

    /** Delivers a state refresh from the server to the live window. */
    public static void accept(final PatternStudioStatePayload payload) {
        if (active == null) {
            return;
        }
        active.state = payload;
        if (!payload.status().isEmpty()) {
            active.status = payload.status();
            active.statusFrames = 200;
        }
        if (payload.tabHint() >= 0) {
            active.showTab(payload.tabHint());
        }
        active.benchNames.sync(payload.benchName(), payload.benchNote());
        active.procNames.sync(payload.procName(), payload.procNote());
        active.timeout.sync(Integer.toString(payload.timeout()));
        active.pipeNames.sync(payload.pipeName(), payload.pipeNote());
    }

    /** The live Studio window, for the recipe viewer's transfer and ghost drop. */
    @Nullable
    public static PatternStudioApp active() {
        return active;
    }

    public BlockPos host() {
        return host;
    }

    public BlockPos monitorPos() {
        return monitorPos;
    }

    public int activeTab() {
        return tab;
    }

    public void showTab(final int t) {
        tab = Math.max(0, Math.min(2, t));
        tabs.setSelected(tab);
    }

    private void selectTab(final int t) {
        tab = t;
        stageList.setSelected(-1);
    }

    private void selectRail(final int r) {
        rail = r;
        fileList.setScroll(0);
    }

    /**
     * The ghost cells the last frame drew, as desktop-local rectangles with what a dropped item does there:
     * {@code kind} 0 is a bench cell, 1 a machine input, 2 a machine output; {@code index} the cell.
     */
    public List<int[]> ghostCells() {
        return List.copyOf(ghostCells);
    }

    /** Puts {@code stack} in the cell of {@code kind} at {@code index}, as a drop from the recipe viewer does. */
    public void dropInto(final int kind, final int index, final ItemStack stack) {
        switch (kind) {
            case 0 -> send(PatternStudioEditPayload.item(host, monitorPos, PatternStudioEditPayload.BENCH_SET_CELL, index, stack));
            case 1 -> send(PatternStudioEditPayload.item(host, monitorPos, PatternStudioEditPayload.PROC_SET_INPUT, index, stack));
            default -> send(PatternStudioEditPayload.item(host, monitorPos, PatternStudioEditPayload.PROC_SET_OUTPUT, index, stack));
        }
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        active = this;
        skin = osSkin;
    }

    @Override
    public String title() {
        return "Pattern Studio";
    }

    @Override
    public int defaultWidth() {
        return 330;
    }

    @Override
    public int defaultHeight() {
        return minHeight() + 8;
    }

    @Override
    public int minWidth() {
        return 290;
    }

    /**
     * The window height (title and margins included) that keeps the tabs, the tallest editor, the band and the
     * bar laid out without overlapping.
     */
    @Override
    public int minHeight() {
        return PatternStudioLayout.minContentHeight() + DesktopWindow.TITLE_H + 8;
    }

    // ---- inventory band geometry (content-local) ----

    private static int rowYOffset(final int row) {
        return PatternStudioLayout.rowYOffset(row);
    }

    /**
     * Whether the band fits under the editor at this height. A window squeezed below the minimum (a small
     * monitor) drops the band rather than draw it over the editor.
     */
    private static boolean bandVisible(final int contentHeight) {
        return PatternStudioLayout.bandVisible(contentHeight);
    }

    /** The content-local top of the band frame for a content area {@code contentHeight} tall. */
    private static int bandTop(final int contentHeight) {
        return PatternStudioLayout.bandTop(contentHeight);
    }

    /** The content-local bottom of the editor: above the band's label, the band, or the bar. */
    private static int editorBottom(final int contentHeight) {
        return PatternStudioLayout.editorBottom(contentHeight);
    }

    @Override
    public int invCellContentX(final int col) {
        return PAD + BAND_PAD + col * CELL;
    }

    @Override
    public int invCellContentY(final int row, final int contentHeight) {
        // A hidden band parks its slots far below the window, where the desktop draws and clicks nothing.
        return bandVisible(contentHeight) ? bandTop(contentHeight) + BAND_PAD + rowYOffset(row) : contentHeight + 10_000;
    }

    @Override
    public int invBandBottom(final int contentHeight) {
        return bandTop(contentHeight) + BAND_PAD + rowYOffset(INV_ROWS - 1) + CELL;
    }

    // ======================================================================================
    //  Rendering: lay the components out for this frame, then draw the tree
    // ======================================================================================

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                              final int height, final int mouseX, final int mouseY, final float partialTick) {
        lastX = x;
        lastY = y;
        lastW = width;
        lastH = height;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastFont = font;
        ghostCells.clear();
        if (++refreshFrames >= REFRESH_EVERY_FRAMES) {
            refreshFrames = 0;
            request();
        }
        if (statusFrames > 0 && --statusFrames == 0) {
            status = "";
        }
        g.fill(x, y, x + width, y + height, skin.windowBg());
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);

        final int editorW = width - RAIL_W;
        final int barY = y + height - BAR_H;
        final int editorY = y + TAB_H;
        final int editorH = editorBottom(height) - TAB_H;
        final boolean loaded = state != null;

        tabs.setBounds(x, y, editorW, TAB_H);
        tabs.setSelected(tab);
        layoutBench(x, editorY, editorW, loaded && tab == TAB_BENCH);
        layoutMachine(x, editorY, editorW, loaded && tab == TAB_MACHINE);
        layoutPipeline(g, x, editorY, editorW, editorH, loaded && tab == TAB_PIPELINE);
        if (!loaded) {
            g.drawString(font, "Loading...", x + PAD, editorY + PAD, skin.dim(), false);
        }
        if (bandVisible(height)) {
            renderBand(g, font, x + PAD, y + bandTop(height), PatternStudioLayout.bandLabelVisible(height));
        }
        final int railX = x + editorW;
        g.fill(railX, y, railX + RAIL_W, barY, skin.panelBg());
        g.fill(railX, y, railX + 1, barY, skin.edge());
        layoutRail(railX, y, RAIL_W, barY - y, loaded);
        g.fill(x, barY, x + width, barY + BAR_H, skin.panelBg());
        g.fill(x, barY, x + width, barY + 1, skin.edge());
        layoutBar(x, barY, width, loaded);

        root.render(g, ctx);
        collectGhostCells();
    }

    private void renderBand(final GuiGraphics g, final Font font, final int bx, final int by, final boolean label) {
        if (label) {
            g.drawString(font, "Inventory", bx + 2, by - 9, skin.dim(), false);
        }
        skin.panel(g, bx, by, BAND_W, BAND_H);
        for (int r = 0; r < INV_ROWS; r++) {
            for (int c = 0; c < INV_COLS; c++) {
                final int cx = bx + BAND_PAD + c * CELL;
                final int cy = by + BAND_PAD + rowYOffset(r);
                g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, skin.fieldBg());
                OsSkin.outline(g, cx, cy, CELL - 2, CELL - 2, skin.edge());
            }
        }
    }

    private String hint() {
        if (state == null) {
            return "";
        }
        return switch (tab) {
            case TAB_MACHINE -> "Right-click: chance. Shift-click: amount";
            case TAB_PIPELINE -> "Add a draft or a file as a stage";
            default -> "Right-click a cell: the tag it accepts";
        };
    }

    // ---- bench ----

    private void layoutBench(final int x, final int y, final int w, final boolean show) {
        final int gx = x + PAD;
        final int gy = y + PAD;
        benchGrid.place(gx, gy);
        final int ax = gx + 3 * CELL + 6;
        arrow.setBounds(ax, gy + CELL + 5, 12, 8);
        final int rx = ax + 16;
        resultCell.place(rx, gy + CELL);
        final int infoX = rx + CELL + 6;
        final int infoW = x + w - PAD - 40 - infoX;
        resultLine.setBounds(infoX, gy + 1, infoW, 8);
        benchFileLine.setBounds(infoX, gy + 11, infoW, 8);
        benchRomLine.setBounds(infoX, gy + 21, infoW, 8);
        benchClear.setBounds(x + w - PAD - 36, gy, 36, BTN_H);
        benchNames.layout(x + PAD, gy + 3 * CELL + PAD, w - PAD * 2);

        benchGrid.setVisible(show);
        resultCell.setVisible(show);
        arrow.setVisible(show);
        resultLine.setVisible(show);
        benchFileLine.setVisible(show && !benchFileText().isEmpty());
        benchRomLine.setVisible(show && state != null && state.romHasBench());
        benchClear.setVisible(show);
        benchNames.setVisible(show);
    }

    private void renderBenchCell(final GuiGraphics g, final UiContext ctx, final int index, final int cx, final int cy,
                                 final int size, final boolean hovered) {
        if (state == null || index >= state.bench().size()) {
            return;
        }
        final PatternStudioStatePayload.BenchCell cell = state.bench().get(index);
        if (cell.stack().isEmpty()) {
            return;
        }
        final ItemStack shown = cell.resolved().isEmpty() ? cell.stack() : cell.resolved();
        DesktopItems.itemWithCount(g, ctx.font(), shown, cx + 1, cy + 1, shortCount(cell.stock()));
        if (!cell.tag().isEmpty()) {
            g.drawString(ctx.font(), "*", cx + 2, cy + 1, ctx.skin().accent(), false);
        }
    }

    private boolean benchCellMarked(final int index) {
        return state != null && index < state.bench().size() && !state.bench().get(index).tag().isEmpty();
    }

    private void renderResultCell(final GuiGraphics g, final UiContext ctx, final int index, final int cx, final int cy,
                                  final int size, final boolean hovered) {
        if (state == null) {
            return;
        }
        if (!state.preview().isEmpty()) {
            DesktopItems.itemWithCount(g, ctx.font(), state.preview(), cx + 1, cy + 1, null);
        } else {
            g.drawString(ctx.font(), "?", cx + 7, cy + 5, ctx.skin().dim(), false);
        }
    }

    private void benchCellClicked(final int index, final int button, final boolean shift) {
        if (state == null || index >= state.bench().size()) {
            return;
        }
        final PatternStudioStatePayload.BenchCell cell = state.bench().get(index);
        if (button == 1) {
            if (!cell.stack().isEmpty()) {
                send(PatternStudioEditPayload.text(host, monitorPos, PatternStudioEditPayload.BENCH_SET_TAG, index,
                        nextTag(cell.stack(), cell.tag()), ""));
            }
        } else {
            // The server reads the carried stack itself; an empty item here means "what I carry".
            send(PatternStudioEditPayload.at(host, monitorPos,
                    carried().isEmpty() && !cell.stack().isEmpty() ? PatternStudioEditPayload.BENCH_CLEAR_CELL
                            : PatternStudioEditPayload.BENCH_SET_CELL, index));
        }
    }

    private String resultText() {
        if (state == null) {
            return "";
        }
        return state.preview().isEmpty() ? "No recipe"
                : state.preview().getCount() + " x " + state.preview().getHoverName().getString();
    }

    private Label.Tone resultTone() {
        return state == null || state.preview().isEmpty() ? Label.Tone.DIM : Label.Tone.TEXT;
    }

    private String benchFileText() {
        return state == null || state.benchOpened().isEmpty() ? "" : "File: " + state.benchOpened();
    }

    // ---- machine ----

    private void layoutMachine(final int x, final int y, final int w, final boolean show) {
        final int gx = x + PAD;
        final int gy = y + PAD;
        final int outX = x + w - PAD - PROC_COLS * CELL;
        // Inputs on the left, the machine between, outputs on the right: the order reads as the process, so
        // no caption row is spent on it (the row is what lets the inventory band fit under the editor).
        inGrid.place(gx, gy);
        outGrid.place(outX, gy);
        final int mx = gx + PROC_COLS * CELL + 10;
        final int mw = outX - mx - 10;
        machineButton.setBounds(mx, gy, mw, BTN_H);
        timeoutLabel.setBounds(mx, gy + 16, 40, 8);
        timeout.setBounds(mx + 42, gy + 14, Math.max(30, mw - 42), FIELD_H);
        procFlag.setBounds(mx, gy + 30, mw - 40, 8);
        procClear.setBounds(mx + mw - 36, gy + 40, 36, BTN_H);
        procNames.layout(x + PAD, gy + PROC_ROWS * CELL + PAD, w - PAD * 2);

        inGrid.setVisible(show);
        outGrid.setVisible(show);
        machineButton.setVisible(show);
        timeoutLabel.setVisible(show);
        timeout.setVisible(show);
        procFlag.setVisible(show && !procFlagText().isEmpty());
        procClear.setVisible(show);
        procNames.setVisible(show);
    }

    private void renderProcCell(final GuiGraphics g, final UiContext ctx, final boolean output, final int index,
                                final int cx, final int cy) {
        if (state == null) {
            return;
        }
        final PatternStudioStatePayload.ProcCell cell = procCell(output ? state.outputs() : state.inputs(), index);
        if (cell == null) {
            return;
        }
        final String label = cell.cell().isItem() ? Long.toString(cell.cell().amount()) : shortAmount(cell.cell().amount());
        DesktopItems.data(g, ctx.font(), cell.cell().key(), cx + 1, cy + 1, label);
        if (output && cell.chance() < ProcessingPattern.FULL_CHANCE) {
            g.drawString(ctx.font(), cell.chance() + "%", cx + 1, cy + 1, ctx.skin().accent(), false);
        }
    }

    private boolean procCellMarked(final boolean output, final int index) {
        if (state == null) {
            return false;
        }
        final PatternStudioStatePayload.ProcCell cell = procCell(output ? state.outputs() : state.inputs(), index);
        return cell != null && cell.cell().estimated();
    }

    private void procCellClicked(final boolean output, final int index, final int button, final boolean shift) {
        if (state == null) {
            return;
        }
        final PatternStudioStatePayload.ProcCell cell = procCell(output ? state.outputs() : state.inputs(), index);
        if (shift && cell != null) {
            amountForOutput = output;
            amountCell = index;
            amount.setAmount(cell.cell().amount());
            amountPopup.open();
        } else if (button == 1) {
            if (output && cell != null) {
                send(PatternStudioEditPayload.number(host, monitorPos, PatternStudioEditPayload.PROC_SET_CHANCE,
                        index, nextChance(cell.chance())));
            }
        } else if (carried().isEmpty() && cell != null) {
            send(PatternStudioEditPayload.at(host, monitorPos,
                    output ? PatternStudioEditPayload.PROC_CLEAR_OUTPUT : PatternStudioEditPayload.PROC_CLEAR_INPUT, index));
        } else {
            send(PatternStudioEditPayload.at(host, monitorPos,
                    output ? PatternStudioEditPayload.PROC_SET_OUTPUT : PatternStudioEditPayload.PROC_SET_INPUT, index));
        }
    }

    @Nullable
    private static PatternStudioStatePayload.ProcCell procCell(final List<PatternStudioStatePayload.ProcCell> cells,
                                                              final int index) {
        for (final PatternStudioStatePayload.ProcCell c : cells) {
            if (c.index() == index) {
                return c;
            }
        }
        return null;
    }

    private String machineButtonLabel() {
        final String machine = state == null || state.machineType().isEmpty() ? "Machine..." : machineLabel(state.machineType());
        return lastFont == null ? machine : Texts.clip(lastFont, machine, machineButton.width() - 6);
    }

    private String procFlagText() {
        if (state == null) {
            return "";
        }
        return state.romHasProc() ? "In the ROM" : !state.procOpened().isEmpty() ? "File: " + state.procOpened() : "";
    }

    private Label.Tone procFlagTone() {
        return state != null && state.romHasProc() ? Label.Tone.ACCENT : Label.Tone.DIM;
    }

    private void commitTimeout(final String value) {
        try {
            final int ticks = Math.max(1, Integer.parseInt(value.trim()));
            send(PatternStudioEditPayload.number(host, monitorPos, PatternStudioEditPayload.PROC_SET_TIMEOUT, 0, ticks));
        } catch (final NumberFormatException ignored) {
            // The next refresh from the server puts the real timeout back in the field.
        }
    }

    // ---- multi-stage ----

    private int pipelineListH(final int h) {
        return Math.max(ROW_H * 2, h - PAD * 2 - FIELD_H - BTN_H - 6);
    }

    private void layoutPipeline(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean show) {
        final int lx = x + PAD;
        final int ly = y + PAD;
        final int listH = pipelineListH(h);
        final int listW = w - PAD * 2;
        if (show) {
            skin.panel(g, lx, ly, listW, listH);
        }
        stageList.setBounds(lx, ly, listW, listH);
        noStages.setBounds(lx + 4, ly + 3, listW - 8, 8);
        final int by = ly + listH + 3;
        addBench.setBounds(lx, by, 66, BTN_H);
        addMachine.setBounds(lx + 69, by, 66, BTN_H);
        removeStage.setBounds(lx + 138, by, 50, BTN_H);
        removeStage.setEnabled(stageList.selected() >= 0);
        pipeRom.setBounds(lx + 138 + 50 + 4, by + 2, x + w - PAD - (lx + 138 + 50 + 4), 8);
        pipeNames.layout(lx, by + BTN_H + 3, listW);

        final boolean empty = state == null || state.stages().isEmpty();
        stageList.setVisible(show);
        noStages.setVisible(show && empty);
        addBench.setVisible(show);
        addMachine.setVisible(show);
        removeStage.setVisible(show);
        pipeRom.setVisible(show && state != null && state.romHasPipe());
        pipeNames.setVisible(show);
    }

    private List<PatternStudioStatePayload.Stage> stages() {
        return state == null ? List.of() : state.stages();
    }

    private void renderStageRow(final GuiGraphics g, final UiContext ctx, final PatternStudioStatePayload.Stage s,
                                final int index, final int x, final int y, final int w, final int h,
                                final boolean hovered, final boolean selected) {
        ctx.skin().listRow(g, x, y, w, h, hovered, selected);
        final String text = (index + 1) + ". " + (s.bench() ? "[bench] " : "[machine] ") + s.label();
        g.drawString(ctx.font(), Texts.clip(ctx.font(), text, w - 6), x + 3, y + 2, ctx.skin().listRowText(selected), false);
    }

    private void removeSelectedStage() {
        final int selected = stageList.selected();
        if (selected >= 0) {
            send(PatternStudioEditPayload.at(host, monitorPos, PatternStudioEditPayload.PIPE_REMOVE, selected));
            stageList.setSelected(-1);
        }
    }

    // ---- rail ----

    /** A flat list of the rail's file rows: drive headers and files, for drawing and clicking. */
    private record FileRow(String driveKey, String label, boolean header, String file) {
    }

    private List<FileRow> fileRows() {
        final List<FileRow> rows = new ArrayList<>();
        if (state == null) {
            return rows;
        }
        for (final PatternStudioStatePayload.Drive d : state.drives()) {
            rows.add(new FileRow(d.key(), d.label(), true, ""));
            for (final String f : d.files()) {
                rows.add(new FileRow(d.key(), f, false, f));
            }
        }
        return rows;
    }

    private void layoutRail(final int rx, final int ry, final int rw, final int rh, final boolean loaded) {
        railTabs.setBounds(rx + 1, ry + 1, rw - 2, RAIL_TAB_H);
        railTabs.setSelected(rail);
        final int top = ry + RAIL_TAB_H + 3;
        final int listH = rh - (top - ry) - 2;
        final int cx = rx + 2;
        final int cw = rw - 4;
        final boolean files = loaded && rail == RAIL_FILES;
        final boolean encoder = loaded && rail == RAIL_ENCODER;
        final boolean hasRows = files && !fileRows().isEmpty();

        fileList.setBounds(cx, top, cw, Math.max(ROW_H, listH - ROW_H));
        noDrives.setBounds(cx + 2, top + 2, cw - 4, 8);
        railHint.setBounds(cx + 2, top + listH - 9, cw - 4, 8);
        fileList.setVisible(hasRows);
        noDrives.setVisible(files && !hasRows);
        railHint.setVisible(hasRows);

        final boolean linked = encoder && state.encoder().linked();
        encLine1.setBounds(cx + 2, top + 2, cw - 4, 8);
        encLine2.setBounds(cx + 2, top + 12, cw - 4, 8);
        encLine3.setBounds(cx + 2, top + 22, cw - 4, 8);
        encProgress.setBounds(cx + 2, top + 32, cw - 4, 6);
        encQueued.setBounds(cx + 2, top + 41, cw - 4, 8);
        final int halfW = (cw - 6) / 2;
        encCancel.setBounds(cx + 2, top + 53, halfW, BTN_H);
        encEject.setBounds(cx + 2 + halfW + 2, top + 53, halfW, BTN_H);
        encLine1.setVisible(encoder);
        encLine2.setVisible(encoder);
        encLine3.setVisible(encoder);
        encProgress.setVisible(linked);
        encQueued.setVisible(linked);
        encCancel.setVisible(linked);
        encEject.setVisible(linked);
        if (linked) {
            final PatternStudioStatePayload.Encoder e = state.encoder();
            encCancel.setEnabled(e.busy() || e.queued() > 0);
            encEject.setEnabled(!e.media().isEmpty() && !e.busy());
        }
    }

    private void renderFileRow(final GuiGraphics g, final UiContext ctx, final FileRow r, final int index, final int x,
                               final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        if (r.header()) {
            g.drawString(ctx.font(), Texts.clip(ctx.font(), r.label(), w - 4), x + 2, y + 2, ctx.skin().dim(), false);
            return;
        }
        ctx.skin().listRow(g, x, y, w, h, hovered, false);
        g.drawString(ctx.font(), Texts.clip(ctx.font(), "  " + r.label(), w - 4), x + 2, y + 2,
                ctx.skin().listRowText(false), false);
    }

    private void fileRowClicked(final int index, final int button) {
        final List<FileRow> rows = fileRows();
        if (button != 0 || index >= rows.size() || rows.get(index).header()) {
            return;
        }
        final FileRow r = rows.get(index);
        final int action = tab == TAB_PIPELINE ? PatternStudioEditPayload.PIPE_ADD_FILE : PatternStudioEditPayload.OPEN_FILE;
        send(PatternStudioEditPayload.text(host, monitorPos, action, 0, r.driveKey(), r.file()));
    }

    private String railHintText() {
        return tab == TAB_PIPELINE ? "Click adds a stage" : "Click opens the file";
    }

    @Nullable
    private PatternStudioStatePayload.Encoder encoder() {
        return state == null ? null : state.encoder();
    }

    private String encoderLine1() {
        final PatternStudioStatePayload.Encoder e = encoder();
        return e != null && e.linked() ? e.era() + " encoder" : "No encoder linked";
    }

    private Label.Tone encoderLine1Tone() {
        final PatternStudioStatePayload.Encoder e = encoder();
        return e != null && e.linked() ? Label.Tone.TEXT : Label.Tone.DIM;
    }

    private String encoderLine2() {
        final PatternStudioStatePayload.Encoder e = encoder();
        if (e == null || !e.linked()) {
            return "Run a peripheral cable";
        }
        return e.media().isEmpty() ? "Bay: empty" : "Bay: " + e.media();
    }

    private Label.Tone encoderLine2Tone() {
        final PatternStudioStatePayload.Encoder e = encoder();
        return e != null && e.linked() && !e.media().isEmpty() ? Label.Tone.TEXT : Label.Tone.DIM;
    }

    private String encoderLine3() {
        final PatternStudioStatePayload.Encoder e = encoder();
        return e != null && e.linked() ? e.status() : "to a Pattern Encoder.";
    }

    private Label.Tone encoderLine3Tone() {
        final PatternStudioStatePayload.Encoder e = encoder();
        return e != null && e.linked() ? Label.Tone.TEXT : Label.Tone.DIM;
    }

    private int encoderLine3Color() {
        final PatternStudioStatePayload.Encoder e = encoder();
        return e != null && e.linked() && e.error() ? ERROR_RED : 0;
    }

    private int encoderProgress() {
        final PatternStudioStatePayload.Encoder e = encoder();
        return e == null ? 0 : e.progress();
    }

    private String encoderQueued() {
        final PatternStudioStatePayload.Encoder e = encoder();
        return "Queued: " + (e == null ? 0 : e.queued());
    }

    // ---- action bar ----

    private void layoutBar(final int x, final int barY, final int width, final boolean loaded) {
        final boolean complete = draftComplete();
        final boolean inRom = draftInRom();
        burn.setEnabled(loaded && complete && state.encoder().linked());
        saveDisk.setEnabled(loaded && complete);
        loadRom.setEnabled(loaded && complete && state.craftingComputer() && state.hasCard() && !inRom);
        final FlowLayout row = FlowLayout.row(x + PAD, barY + (BAR_H - BTN_H) / 2 + 1, PAD);
        row.place(burn, BAR_BTN_W, BTN_H);
        row.place(saveDisk, BAR_BTN_W, BTN_H);
        row.place(loadRom, BAR_BTN_W, BTN_H);
        burn.setVisible(loaded);
        saveDisk.setVisible(loaded);
        loadRom.setVisible(loaded);
        final int sx = loaded ? row.x() + PAD : x + PAD;
        statusLine.setBounds(sx, barY + (BAR_H - 8) / 2 + 1, x + width - sx - PAD, 8);
    }

    private void barAction(final int action) {
        send(PatternStudioEditPayload.at(host, monitorPos, action, tab));
    }

    private boolean draftComplete() {
        if (state == null) {
            return false;
        }
        return switch (tab) {
            case TAB_MACHINE -> !state.machineType().isEmpty() && !state.inputs().isEmpty() && !state.outputs().isEmpty();
            case TAB_PIPELINE -> !state.stages().isEmpty();
            default -> !state.preview().isEmpty();
        };
    }

    private boolean draftInRom() {
        if (state == null) {
            return false;
        }
        return tab == TAB_BENCH ? state.romHasBench() : tab == TAB_MACHINE ? state.romHasProc() : state.romHasPipe();
    }

    private String loadRomLabel() {
        return draftInRom() ? "In ROM" : "Load ROM";
    }

    private String statusText() {
        return !status.isEmpty() ? status : hint();
    }

    private Label.Tone statusTone() {
        return status.isEmpty() ? Label.Tone.DIM : Label.Tone.TEXT;
    }

    // ---- popups ----

    @Override
    public boolean modalActive() {
        return machinePicker.isOpen() || amountPopup.isOpen();
    }

    @Override
    public void renderModal(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                            final int height, final int mouseX, final int mouseY) {
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, 0f);
        if (machinePicker.isOpen()) {
            machinePicker.renderIn(g, ctx, x, y, width, height);
        } else if (amountPopup.isOpen()) {
            amountPopup.renderIn(g, ctx, x, y, width, height);
        }
    }

    private record Choice(String key, String label) {
    }

    private List<Choice> machineChoices() {
        final List<Choice> out = new ArrayList<>();
        final String q = machineSearch.query();
        if (state != null) {
            for (final PatternStudioStatePayload.Machine m : state.machines()) {
                if (q.isEmpty() || m.label().toLowerCase(Locale.ROOT).contains(q) || m.typeKey().toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(new Choice(m.typeKey(), m.label().equals(m.typeKey()) ? m.typeKey() : m.label() + "  " + m.typeKey()));
                }
            }
        }
        for (final String category : MachineCategory.categoryIds()) {
            final String key = MachineCategory.genericIdOf(category);
            if (q.isEmpty() || category.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Choice(key, "Any " + category));
            }
        }
        return out;
    }

    /** The machine picker's rows, as labelled, for a test that picks one. */
    public List<String> machinePickerRows() {
        final List<String> out = new ArrayList<>();
        for (final Choice c : machineChoices()) {
            out.add(c.key());
        }
        return out;
    }

    private void openMachinePicker() {
        machineSearch.reset();
        machineList.setScroll(0);
        machinePicker.open();
        machinePicker.focus(machineSearch);
    }

    private void closeMachinePicker() {
        machinePicker.close();
    }

    private void resetMachineScroll() {
        machineList.setScroll(0);
    }

    private void layoutMachinePicker(final Popup p) {
        machineSearch.setBounds(p.x() + 5, p.y() + 14, p.width() - 10, FIELD_H);
        final int listY = p.y() + 14 + FIELD_H + 3;
        final int listH = p.height() - (listY - p.y()) - BTN_H - 6;
        machineList.setBounds(p.x() + 5, listY, p.width() - 10, Math.max(ROW_H, listH));
        pickerClose.setBounds(p.right() - 5 - 44, p.bottom() - BTN_H - 4, 44, BTN_H);
    }

    private void renderChoiceRow(final GuiGraphics g, final UiContext ctx, final Choice c, final int index, final int x,
                                 final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        final boolean current = state != null && c.key().equals(state.machineType());
        ctx.skin().listRow(g, x, y, w, h, hovered, current);
        g.drawString(ctx.font(), Texts.clip(ctx.font(), c.label(), w - 6), x + 3, y + 2, ctx.skin().listRowText(current), false);
    }

    private void choiceClicked(final int index, final int button) {
        if (button != 0) {
            return;
        }
        final List<Choice> choices = machineChoices();
        if (index < choices.size()) {
            send(PatternStudioEditPayload.text(host, monitorPos, PatternStudioEditPayload.PROC_SET_MACHINE, 0,
                    choices.get(index).key(), ""));
            machinePicker.close();
        }
    }

    private String amountTitle() {
        return amountForOutput ? "Output amount per run" : "Input amount per run";
    }

    private void layoutAmountPopup(final Popup p) {
        amount.setBounds(p.x() + 5, p.y() + 18, p.width() - 10, BTN_H);
        amountClear.setBounds(p.x() + 5, p.bottom() - BTN_H - 5, 44, BTN_H);
        amountDone.setBounds(p.right() - 49, p.bottom() - BTN_H - 5, 44, BTN_H);
    }

    private void commitAmount(final long value) {
        send(PatternStudioEditPayload.number(host, monitorPos,
                amountForOutput ? PatternStudioEditPayload.PROC_SET_OUTPUT_AMOUNT : PatternStudioEditPayload.PROC_SET_INPUT_AMOUNT,
                amountCell, value));
        amountPopup.close();
    }

    // ======================================================================================
    //  Input: everything goes to the open popup, or else to the content tree
    // ======================================================================================

    private Panel inputTarget() {
        if (machinePicker.isOpen()) {
            return machinePicker;
        }
        if (amountPopup.isOpen()) {
            return amountPopup;
        }
        return root;
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (state == null) {
            return;
        }
        inputTarget().mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        inputTarget().mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        return inputTarget().mouseScrolled(lastMouseX, lastMouseY, delta);
    }

    @Override
    public boolean charTyped(final char c) {
        return inputTarget().charTyped(c);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        return inputTarget().keyPressed(key, scanCode, modifiers);
    }

    // ======================================================================================
    //  Tooltips
    // ======================================================================================

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                              final int height, final int mouseX, final int mouseY) {
        if (state == null || modalActive()) {
            return;
        }
        for (final int[] c : ghostCells) {
            if (!in(mouseX, mouseY, c[0], c[1], c[2], c[3])) {
                continue;
            }
            final List<Component> lines = new ArrayList<>();
            if (c[4] == 0) {
                final PatternStudioStatePayload.BenchCell cell = state.bench().get(c[5]);
                if (cell.stack().isEmpty()) {
                    return;
                }
                lines.add(cell.stack().getHoverName());
                if (!cell.tag().isEmpty()) {
                    lines.add(Component.literal("Any #" + cell.tag()).withStyle(ChatFormatting.AQUA));
                    if (!cell.resolved().isEmpty()) {
                        lines.add(Component.literal("Network would use: ").withStyle(ChatFormatting.GRAY)
                                .append(cell.resolved().getHoverName()));
                    }
                }
                lines.add(Component.literal("In stock: " + cell.stock()).withStyle(ChatFormatting.GRAY));
            } else {
                final PatternStudioStatePayload.ProcCell cell = procCell(c[4] == 2 ? state.outputs() : state.inputs(), c[5]);
                if (cell == null) {
                    return;
                }
                lines.add(cell.cell().key().displayName());
                lines.add(Component.literal(amountLabel(cell.cell().key(), cell.cell().amount()) + " per run"
                        + (cell.cell().estimated() ? " (estimated)" : "")).withStyle(ChatFormatting.GRAY));
                if (c[4] == 2 && cell.chance() < ProcessingPattern.FULL_CHANCE) {
                    lines.add(Component.literal("Chance: " + cell.chance() + "%").withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.literal("In stock: " + amountLabel(cell.cell().key(), cell.stock())).withStyle(ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
    }

    /** Records where this frame's ghost cells are, for the tooltips and the recipe viewer's drop. */
    private void collectGhostCells() {
        if (state == null) {
            return;
        }
        if (tab == TAB_BENCH) {
            for (int i = 0; i < 9; i++) {
                final int[] r = benchGrid.cellRect(i);
                if (r != null) {
                    ghostCells.add(new int[] {r[0], r[1], r[2], r[3], 0, i});
                }
            }
        } else if (tab == TAB_MACHINE) {
            collectGridCells(inGrid, 1);
            collectGridCells(outGrid, 2);
        }
    }

    private void collectGridCells(final CellGrid grid, final int kind) {
        for (int row = 0; row < PROC_ROWS; row++) {
            for (int col = 0; col < PROC_COLS; col++) {
                final int index = (grid.scroll() + row) * PROC_COLS + col;
                final int[] r = grid.cellRect(index);
                if (r != null) {
                    ghostCells.add(new int[] {r[0], r[1], r[2], r[3], kind, index});
                }
            }
        }
    }

    // ======================================================================================
    //  Helpers
    // ======================================================================================

    /** The name and note of a draft on one row: the name takes two fifths, the note the rest. */
    private final class NameNoteRow extends Panel {
        private final int action;
        private final Label nameLabel = add(new Label("Name", Label.Tone.DIM));
        private final TextField name = add(new TextField(PatternStudioStatePayload.MAX_NAME).setOnCommit(v -> sendNames()));
        private final Label noteLabel = add(new Label("Note", Label.Tone.DIM));
        private final TextField note = add(new TextField(PatternStudioStatePayload.MAX_NOTE).setOnCommit(v -> sendNames()));

        private NameNoteRow(final int action) {
            this.action = action;
        }

        private void sync(final String nameValue, final String noteValue) {
            name.sync(nameValue);
            note.sync(noteValue);
        }

        private void layout(final int x, final int y, final int w) {
            setBounds(x, y, w, FIELD_H);
            final int nameW = (w - NAME_LABEL_W * 2) * 2 / 5;
            nameLabel.setBounds(x, y + 2, NAME_LABEL_W, 8);
            name.setBounds(x + NAME_LABEL_W, y, nameW, FIELD_H);
            final int noteX = x + NAME_LABEL_W + nameW + NAME_LABEL_W;
            noteLabel.setBounds(noteX - NAME_LABEL_W + 2, y + 2, NAME_LABEL_W - 2, 8);
            note.setBounds(noteX, y, x + w - noteX, FIELD_H);
        }

        private void sendNames() {
            send(PatternStudioEditPayload.text(host, monitorPos, action, 0, name.value(), note.value()));
        }
    }

    private static boolean in(final int mx, final int my, final int x, final int y, final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static ItemStack carried() {
        final var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.containerMenu.getCarried();
    }

    private static int nextChance(final int current) {
        for (int i = 0; i < CHANCE_STEPS.length; i++) {
            if (CHANCE_STEPS[i] == current) {
                return CHANCE_STEPS[(i + 1) % CHANCE_STEPS.length];
            }
        }
        return CHANCE_STEPS[0];
    }

    /** The tag after {@code current} among the item's tags, in name order; back to exact after the last. */
    static String nextTag(final ItemStack stack, final String current) {
        final List<String> tags = new ArrayList<>();
        stack.getItemHolder().tags().forEach(t -> tags.add(t.location().toString()));
        tags.sort(String::compareTo);
        if (tags.isEmpty()) {
            return "";
        }
        if (current.isEmpty()) {
            return tags.get(0);
        }
        final int i = tags.indexOf(current);
        return i < 0 || i + 1 >= tags.size() ? "" : tags.get(i + 1);
    }

    private static String machineLabel(final String type) {
        if (MachineCategory.isGenericId(type)) {
            return "Any " + MachineCategory.categoryOf(type);
        }
        final int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1).replace('_', ' ');
    }

    private static String amountLabel(final StorageKey key, final long amount) {
        return key.isItem() ? amount + " items" : amount + " mB";
    }

    @Nullable
    private static String shortCount(final long n) {
        if (n <= 0) {
            return "0";
        }
        if (n >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        }
        if (n >= 10_000L) {
            return (n / 1000) + "k";
        }
        if (n >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        }
        return Long.toString(n);
    }

    private static String shortAmount(final long mb) {
        if (mb >= 1000L && mb % 1000L == 0L) {
            return (mb / 1000L) + "B";
        }
        return shortCount(mb);
    }

    // ======================================================================================
    //  Inspection (client tests): content-local centres of the controls, from the last frame's layout
    // ======================================================================================

    public boolean isLoaded() {
        return state != null;
    }

    @Nullable
    public PatternStudioStatePayload state() {
        return state;
    }

    public String status() {
        return status;
    }

    public boolean isMachinePickerOpen() {
        return machinePicker.isOpen();
    }

    public boolean isAmountPopupOpen() {
        return amountPopup.isOpen();
    }

    public int railTab() {
        return rail;
    }

    private int[] local(final int[] c) {
        return new int[] {c[0] - lastX, c[1] - lastY};
    }

    /** The centre of editor tab {@code t} (0 bench, 1 machine, 2 multi-stage). */
    public int[] tabCenter(final int t) {
        return local(tabs.tabCenter(t));
    }

    /** The centre of rail tab {@code t} (0 files, 1 encoder). */
    public int[] railTabCenter(final int t) {
        return local(railTabs.tabCenter(t));
    }

    /** The centre of rail list row {@code row} among the visible rows of the Files rail. */
    public int[] railRowCenter(final int row) {
        return local(fileList.rowCenter(fileList.scroll() + row));
    }

    /** The centre of bar button {@code i} (0 burn, 1 save to disk, 2 load into the ROM). */
    public int[] barButtonCenter(final int i) {
        return local((i == 0 ? burn : i == 1 ? saveDisk : loadRom).center());
    }

    /** The centre of bench cell {@code index} (0..8). */
    public int[] benchCellCenter(final int index) {
        return local(benchGrid.cellCenter(index));
    }

    /** The centre of machine input ({@code output == false}) or output cell {@code index} among the visible rows. */
    public int[] procCellCenter(final boolean output, final int index) {
        return local((output ? outGrid : inGrid).cellCenter(index));
    }

    /** The centre of the machine picker button. */
    public int[] machineButtonCenter() {
        return local(machineButton.center());
    }

    /** The centre of the timeout field. */
    public int[] timeoutFieldCenter() {
        return local(timeout.center());
    }

    /** The centre of machine picker row {@code row} among the visible rows. */
    public int[] machinePickerRowCenter(final int row) {
        return local(machineList.rowCenter(row));
    }

    /** The centre of pipeline button {@code i} (0 add bench, 1 add machine, 2 remove). */
    public int[] pipelineButtonCenter(final int i) {
        return local((i == 0 ? addBench : i == 1 ? addMachine : removeStage).center());
    }

    /** The centre of inventory band slot {@code index} (rows 0-2 main inventory, row 3 hotbar). */
    public int[] inventoryBandSlotCenter(final int index) {
        return new int[] {invCellContentX(index % INV_COLS) + CELL / 2,
                invCellContentY(index / INV_COLS, lastH) + CELL / 2};
    }

    /** Whether the inventory band was drawn on the last frame (a window tall enough to hold it). */
    public boolean bandShown() {
        return bandVisible(lastH);
    }
}
