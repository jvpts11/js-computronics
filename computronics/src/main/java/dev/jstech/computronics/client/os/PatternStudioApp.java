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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

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
    private static final int PROC_COLS = PatternStudioLayout.PROC_COLS;
    private static final int PROC_ROWS = PatternStudioLayout.PROC_ROWS;
    private static final int REFRESH_EVERY_FRAMES = 60;
    private static final int[] CHANCE_STEPS = {100, 75, 50, 25, 10};

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
    private static final int RAIL_TABS = 2;

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
    private int railScroll;
    private int inScroll;
    private int outScroll;
    private int selectedStage = -1;

    // Text fields per tab: name and note.
    private final Field benchName = new Field(PatternStudioStatePayload.MAX_NAME);
    private final Field benchNote = new Field(PatternStudioStatePayload.MAX_NOTE);
    private final Field procName = new Field(PatternStudioStatePayload.MAX_NAME);
    private final Field procNote = new Field(PatternStudioStatePayload.MAX_NOTE);
    private final Field timeout = new Field(6);
    private final Field pipeName = new Field(PatternStudioStatePayload.MAX_NAME);
    private final Field pipeNote = new Field(PatternStudioStatePayload.MAX_NOTE);
    @Nullable
    private Field focused;

    // Popups.
    private boolean machinePickerOpen;
    private final Field machineSearch = new Field(48);
    private int machineScroll;
    private boolean amountPopupOpen;
    private boolean amountForOutput;
    private int amountCell = -1;
    private long amountValue;

    // Geometry of the last frame, so clicks land where the player sees things.
    private int lastX;
    private int lastY;
    private int lastW;
    private int lastH;
    private int lastMouseX;
    private int lastMouseY;
    private final List<int[]> ghostCells = new ArrayList<>(); // {x, y, w, h, kind(0 bench,1 in,2 out), index}

    public PatternStudioApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
        active = this;
        request();
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestPatternStudioPayload(host, monitorPos));
    }

    private void send(final PatternStudioEditPayload payload) {
        PacketDistributor.sendToServer(payload);
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
            active.tab = payload.tabHint();
        }
        active.benchName.sync(payload.benchName());
        active.benchNote.sync(payload.benchNote());
        active.procName.sync(payload.procName());
        active.procNote.sync(payload.procNote());
        active.timeout.sync(Integer.toString(payload.timeout()));
        active.pipeName.sync(payload.pipeName());
        active.pipeNote.sync(payload.pipeNote());
        if (active.selectedStage >= payload.stages().size()) {
            active.selectedStage = -1;
        }
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
    //  Rendering
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
        ghostCells.clear();
        if (++refreshFrames >= REFRESH_EVERY_FRAMES) {
            refreshFrames = 0;
            request();
        }
        if (statusFrames > 0 && --statusFrames == 0) {
            status = "";
        }
        g.fill(x, y, x + width, y + height, skin.windowBg());

        final int editorW = width - RAIL_W;
        final int barY = y + height - BAR_H;
        final int editorY = y + TAB_H;
        final int editorH = editorBottom(height) - TAB_H;

        // Tab strip.
        final String[] tabs = {"Bench", "Machine", "Multi-stage"};
        final int tw = editorW / 3;
        for (int i = 0; i < 3; i++) {
            skin.tab(g, font, x + i * tw, y, tw, TAB_H, tabs[i], tab == i);
        }
        g.fill(x, y + TAB_H - 1, x + editorW, y + TAB_H, skin.edge());

        if (state == null) {
            g.drawString(font, "Loading...", x + PAD, editorY + PAD, skin.dim(), false);
        } else {
            switch (tab) {
                case TAB_MACHINE -> renderMachine(g, font, x, editorY, editorW, editorH, mouseX, mouseY);
                case TAB_PIPELINE -> renderPipeline(g, font, x, editorY, editorW, editorH, mouseX, mouseY);
                default -> renderBench(g, font, x, editorY, editorW, editorH, mouseX, mouseY);
            }
        }
        if (bandVisible(height)) {
            renderBand(g, font, x + PAD, y + bandTop(height), PatternStudioLayout.bandLabelVisible(height));
        }
        renderRail(g, font, x + editorW, y, RAIL_W, barY - y, mouseX, mouseY);
        renderBar(g, font, x, barY, width, mouseX, mouseY);
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

    private void renderBench(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                             final int h, final int mouseX, final int mouseY) {
        final int gx = x + PAD;
        final int gy = y + PAD;
        for (int i = 0; i < 9; i++) {
            final int cx = gx + (i % 3) * CELL;
            final int cy = gy + (i / 3) * CELL;
            final PatternStudioStatePayload.BenchCell cell = state.bench().get(i);
            drawCell(g, cx, cy, mouseX, mouseY, !cell.tag().isEmpty());
            if (!cell.stack().isEmpty()) {
                final ItemStack shown = cell.resolved().isEmpty() ? cell.stack() : cell.resolved();
                DesktopItems.itemWithCount(g, font, shown, cx + 1, cy + 1, shortCount(cell.stock()));
                if (!cell.tag().isEmpty()) {
                    g.drawString(font, "*", cx + 2, cy + 1, skin.accent(), false);
                }
            }
            ghostCells.add(new int[] {cx, cy, CELL, CELL, 0, i});
        }
        // Arrow and result.
        final int ax = gx + 3 * CELL + 6;
        final int ay = gy + CELL + 5;
        g.drawString(font, "->", ax, ay, skin.dim(), false);
        final int rx = ax + 16;
        final int ry = gy + CELL;
        drawCell(g, rx, ry, mouseX, mouseY, false);
        if (!state.preview().isEmpty()) {
            DesktopItems.itemWithCount(g, font, state.preview(), rx + 1, ry + 1, null);
        } else {
            g.drawString(font, "?", rx + 7, ry + 5, skin.dim(), false);
        }
        // Result line, the opened-file provenance and the ROM flag, then Clear.
        final int infoX = rx + CELL + 6;
        final int infoW = x + w - PAD - 40 - infoX;
        final String result = state.preview().isEmpty() ? "No recipe"
                : state.preview().getCount() + " x " + state.preview().getHoverName().getString();
        g.drawString(font, clip(font, result, infoW), infoX, gy + 1, state.preview().isEmpty() ? skin.dim() : skin.text(), false);
        if (!state.benchOpened().isEmpty()) {
            g.drawString(font, clip(font, "File: " + state.benchOpened(), infoW), infoX, gy + 11, skin.dim(), false);
        }
        if (state.romHasBench()) {
            g.drawString(font, "In the Recipe ROM", infoX, gy + 21, skin.accent(), false);
        }
        button(g, font, x + w - PAD - 36, gy, 36, "Clear", true, mouseX, mouseY);
        // Name and note on one row under the grid.
        final int fy = gy + 3 * CELL + PAD;
        nameNoteRow(g, font, benchName, benchNote, x + PAD, fy, w - PAD * 2);
    }

    // ---- machine ----

    private void renderMachine(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                               final int h, final int mouseX, final int mouseY) {
        final int gx = x + PAD;
        final int gy = y + PAD;
        final int outX = x + w - PAD - PROC_COLS * CELL;
        inScroll = clampScroll(inScroll, PatternWorkbench.PROC_GRID / PROC_COLS, PROC_ROWS);
        outScroll = clampScroll(outScroll, PatternWorkbench.PROC_GRID / PROC_COLS, PROC_ROWS);
        // Inputs on the left, the machine between, outputs on the right: the order reads as the process, so
        // no caption row is spent on it (the row is what lets the inventory band fit under the editor). The
        // scroll cues sit in the gaps beside the grids.
        drawProcGrid(g, font, gx, gy, false, inScroll, gx + PROC_COLS * CELL + 2, mouseX, mouseY);
        drawProcGrid(g, font, outX, gy, true, outScroll, outX - 7, mouseX, mouseY);

        // Machine, timeout and the flags between the grids.
        final int mx = gx + PROC_COLS * CELL + 10;
        final int mw = outX - mx - 10;
        final String machine = state.machineType().isEmpty() ? "Machine..." : machineLabel(state.machineType());
        button(g, font, mx, gy, mw, clip(font, machine, mw - 6), true, mouseX, mouseY);
        g.drawString(font, "Timeout", mx, gy + 16, skin.dim(), false);
        field(g, font, timeout, mx + 42, gy + 14, Math.max(30, mw - 42));
        final String flag = state.romHasProc() ? "In the ROM"
                : !state.procOpened().isEmpty() ? "File: " + state.procOpened() : "";
        if (!flag.isEmpty()) {
            g.drawString(font, clip(font, flag, mw - 40), mx, gy + 30, state.romHasProc() ? skin.accent() : skin.dim(), false);
        }
        button(g, font, mx + mw - 36, gy + 40, 36, "Clear", true, mouseX, mouseY);
        final int fy = gy + PROC_ROWS * CELL + PAD;
        nameNoteRow(g, font, procName, procNote, x + PAD, fy, w - PAD * 2);
    }

    private void drawProcGrid(final GuiGraphics g, final Font font, final int gx, final int gy, final boolean output,
                              final int scroll, final int cueX, final int mouseX, final int mouseY) {
        final List<PatternStudioStatePayload.ProcCell> cells = output ? state.outputs() : state.inputs();
        for (int row = 0; row < PROC_ROWS; row++) {
            for (int col = 0; col < PROC_COLS; col++) {
                final int index = (scroll + row) * PROC_COLS + col;
                final int cx = gx + col * CELL;
                final int cy = gy + row * CELL;
                final PatternStudioStatePayload.ProcCell cell = procCell(cells, index);
                drawCell(g, cx, cy, mouseX, mouseY, cell != null && cell.cell().estimated());
                if (cell != null) {
                    final String label = cell.cell().isItem() ? Long.toString(cell.cell().amount())
                            : shortAmount(cell.cell().amount());
                    DesktopItems.data(g, font, cell.cell().key(), cx + 1, cy + 1, label);
                    if (output && cell.chance() < ProcessingPattern.FULL_CHANCE) {
                        g.drawString(font, cell.chance() + "%", cx + 1, cy + 1, skin.accent(), false);
                    }
                }
                ghostCells.add(new int[] {cx, cy, CELL, CELL, output ? 2 : 1, index});
            }
        }
        // A scroll cue beside the grid when there is more than fits.
        if (scroll > 0) {
            g.drawString(font, "^", cueX, gy, skin.dim(), false);
        }
        if (scroll + PROC_ROWS < PatternWorkbench.PROC_GRID / PROC_COLS) {
            g.drawString(font, "v", cueX, gy + PROC_ROWS * CELL - 9, skin.dim(), false);
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

    // ---- pipeline ----

    private int pipelineListH(final int h) {
        return Math.max(ROW_H * 2, h - PAD * 2 - FIELD_H - BTN_H - 6);
    }

    private void renderPipeline(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                                final int h, final int mouseX, final int mouseY) {
        final int lx = x + PAD;
        final int ly = y + PAD;
        final int listH = pipelineListH(h);
        final int listW = w - PAD * 2;
        skin.panel(g, lx, ly, listW, listH);
        final List<PatternStudioStatePayload.Stage> stages = state.stages();
        if (stages.isEmpty()) {
            g.drawString(font, "No stages yet", lx + 4, ly + 3, skin.dim(), false);
        }
        final int visible = Math.max(1, (listH - 2) / ROW_H);
        for (int i = 0; i < Math.min(visible, stages.size()); i++) {
            final PatternStudioStatePayload.Stage s = stages.get(i);
            final int ry = ly + 1 + i * ROW_H;
            final boolean hover = in(mouseX, mouseY, lx, ry, listW, ROW_H);
            skin.listRow(g, lx + 1, ry, listW - 2, ROW_H, hover, i == selectedStage);
            final String text = (i + 1) + ". " + (s.bench() ? "[bench] " : "[machine] ") + s.label();
            g.drawString(font, clip(font, text, listW - 8), lx + 4, ry + 2, skin.listRowText(i == selectedStage), false);
        }
        final int by = ly + listH + 3;
        int bx = lx;
        bx = button(g, font, bx, by, 66, "+ Bench", true, mouseX, mouseY) + 3;
        bx = button(g, font, bx, by, 66, "+ Machine", true, mouseX, mouseY) + 3;
        button(g, font, bx, by, 50, "Remove", selectedStage >= 0, mouseX, mouseY);
        if (state.romHasPipe()) {
            g.drawString(font, "In ROM", x + w - PAD - font.width("In ROM"), by + 2, skin.accent(), false);
        }
        final int fy = by + BTN_H + 3;
        nameNoteRow(g, font, pipeName, pipeNote, lx, fy, listW);
    }

    // ---- rail ----

    private void renderRail(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                            final int h, final int mouseX, final int mouseY) {
        g.fill(x, y, x + w, y + h, skin.panelBg());
        g.fill(x, y, x + 1, y + h, skin.edge());
        final String[] tabs = {"Files", "Encoder"};
        final int tw = w / RAIL_TABS;
        for (int i = 0; i < RAIL_TABS; i++) {
            skin.tab(g, font, x + 1 + i * tw, y + 1, tw - 1, RAIL_TAB_H, tabs[i], rail == i);
        }
        final int top = y + RAIL_TAB_H + 3;
        final int listH = h - (top - y) - 2;
        if (state == null) {
            return;
        }
        if (rail == RAIL_ENCODER) {
            renderEncoder(g, font, x + 2, top, w - 4, listH, mouseX, mouseY);
        } else {
            renderFiles(g, font, x + 2, top, w - 4, listH, mouseX, mouseY);
        }
    }

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

    private void renderFiles(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                             final int h, final int mouseX, final int mouseY) {
        final List<FileRow> rows = fileRows();
        if (rows.isEmpty()) {
            g.drawString(font, "No drives", x + 2, y + 2, skin.dim(), false);
            return;
        }
        final int visible = Math.max(1, (h - ROW_H) / ROW_H);
        railScroll = clampScroll(railScroll, rows.size(), visible);
        for (int i = 0; i < visible; i++) {
            final int idx = railScroll + i;
            if (idx >= rows.size()) {
                break;
            }
            final FileRow r = rows.get(idx);
            final int ry = y + i * ROW_H;
            if (r.header()) {
                g.drawString(font, clip(font, r.label(), w - 4), x + 2, ry + 2, skin.dim(), false);
                continue;
            }
            final boolean hover = in(mouseX, mouseY, x, ry, w, ROW_H);
            skin.listRow(g, x, ry, w, ROW_H, hover, false);
            g.drawString(font, clip(font, "  " + r.label(), w - 4), x + 2, ry + 2, skin.listRowText(false), false);
        }
        g.drawString(font, tab == TAB_PIPELINE ? "Click adds a stage" : "Click opens the file", x + 2, y + h - 9,
                skin.dim(), false);
    }

    private void renderEncoder(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                               final int h, final int mouseX, final int mouseY) {
        final PatternStudioStatePayload.Encoder e = state.encoder();
        int ly = y + 2;
        if (!e.linked()) {
            g.drawString(font, "No encoder linked", x + 2, ly, skin.dim(), false);
            g.drawString(font, clip(font, "Run a peripheral cable", w - 4), x + 2, ly + 10, skin.dim(), false);
            g.drawString(font, clip(font, "to a Pattern Encoder.", w - 4), x + 2, ly + 20, skin.dim(), false);
            return;
        }
        g.drawString(font, clip(font, e.era() + " encoder", w - 4), x + 2, ly, skin.text(), false);
        ly += 10;
        g.drawString(font, clip(font, e.media().isEmpty() ? "Bay: empty" : "Bay: " + e.media(), w - 4), x + 2, ly,
                e.media().isEmpty() ? skin.dim() : skin.text(), false);
        ly += 10;
        g.drawString(font, clip(font, e.status(), w - 4), x + 2, ly, e.error() ? 0xFFEF6A5A : skin.text(), false);
        ly += 10;
        skin.panel(g, x + 2, ly, w - 4, 6);
        if (e.progress() > 0) {
            g.fill(x + 3, ly + 1, x + 3 + (w - 6) * e.progress() / 100, ly + 5, skin.accent());
        }
        ly += 9;
        g.drawString(font, "Queued: " + e.queued(), x + 2, ly, skin.dim(), false);
        ly += 12;
        button(g, font, x + 2, ly, (w - 6) / 2, "Cancel", e.busy() || e.queued() > 0, mouseX, mouseY);
        button(g, font, x + 2 + (w - 6) / 2 + 2, ly, (w - 6) / 2, "Eject", !e.media().isEmpty() && !e.busy(), mouseX, mouseY);
    }

    /** The content-local y of the encoder rail's two buttons. */
    private int encoderButtonsY() {
        return RAIL_TAB_H + 3 + 2 + 10 + 10 + 10 + 9 + 12;
    }

    // ---- action bar ----

    private void renderBar(final GuiGraphics g, final Font font, final int x, final int barY, final int width,
                           final int mouseX, final int mouseY) {
        g.fill(x, barY, x + width, barY + BAR_H, skin.panelBg());
        g.fill(x, barY, x + width, barY + 1, skin.edge());
        final int used = layoutBar(g, font, x, barY, width, mouseX, mouseY, false);
        final String line = !status.isEmpty() ? status : hint();
        final int sx = x + used + PAD;
        g.drawString(font, clip(font, line, x + width - sx - PAD), sx, barY + (BAR_H - 8) / 2 + 1,
                status.isEmpty() ? skin.dim() : skin.text(), false);
    }

    private static final int BAR_BTN_W = 62;

    /** Lays out the three bar buttons (returns the width used), or hit-tests them (returns the button, or -1). */
    private int layoutBar(final GuiGraphics g, final Font font, final int x, final int barY, final int width,
                          final int mouseX, final int mouseY, final boolean hitTest) {
        if (state == null) {
            return hitTest ? -1 : 0;
        }
        final boolean complete = draftComplete();
        final boolean inRom = tab == TAB_BENCH ? state.romHasBench() : tab == TAB_MACHINE ? state.romHasProc() : state.romHasPipe();
        final String[] labels = {"Burn", "Save to disk", inRom ? "In ROM" : "Load ROM"};
        final boolean[] enabled = {
                complete && state.encoder().linked(),
                complete,
                complete && state.craftingComputer() && state.hasCard() && !inRom,
        };
        final int by = barY + (BAR_H - BTN_H) / 2 + 1;
        for (int i = 0; i < labels.length; i++) {
            final int bx = x + PAD + i * (BAR_BTN_W + PAD);
            if (hitTest) {
                if (enabled[i] && in(mouseX, mouseY, bx, by, BAR_BTN_W, BTN_H)) {
                    return i;
                }
            } else {
                button(g, font, bx, by, BAR_BTN_W, labels[i], enabled[i], mouseX, mouseY);
            }
        }
        return hitTest ? -1 : labels.length * (BAR_BTN_W + PAD);
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

    // ---- popups ----

    @Override
    public boolean modalActive() {
        return machinePickerOpen || amountPopupOpen;
    }

    @Override
    public void renderModal(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                            final int height, final int mouseX, final int mouseY) {
        g.fill(x, y, x + width, y + height, 0x88000000);
        if (machinePickerOpen) {
            renderMachinePicker(g, font, x, y, width, height, mouseX, mouseY);
        } else if (amountPopupOpen) {
            renderAmountPopup(g, font, x, y, width, height, mouseX, mouseY);
        }
    }

    private record Choice(String key, String label) {
    }

    private List<Choice> machineChoices() {
        final List<Choice> out = new ArrayList<>();
        final String q = machineSearch.value().toLowerCase(Locale.ROOT);
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

    private int[] pickerRect(final int x, final int y, final int width, final int height) {
        final int pw = Math.min(width - 16, 220);
        final int ph = Math.min(height - 16, 150);
        return new int[] {x + (width - pw) / 2, y + (height - ph) / 2, pw, ph};
    }

    private void renderMachinePicker(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                                     final int height, final int mouseX, final int mouseY) {
        final int[] r = pickerRect(x, y, width, height);
        skin.windowFrame(g, r[0], r[1], r[2], r[3]);
        g.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, skin.windowBg());
        g.drawString(font, "Pick a machine", r[0] + 5, r[1] + 4, skin.text(), false);
        field(g, font, machineSearch, r[0] + 5, r[1] + 14, r[2] - 10);
        final int listY = r[1] + 14 + FIELD_H + 3;
        final int listH = r[3] - (listY - r[1]) - BTN_H - 6;
        final List<Choice> choices = machineChoices();
        final int visible = Math.max(1, listH / ROW_H);
        machineScroll = clampScroll(machineScroll, choices.size(), visible);
        for (int i = 0; i < visible; i++) {
            final int idx = machineScroll + i;
            if (idx >= choices.size()) {
                break;
            }
            final int ry = listY + i * ROW_H;
            final boolean hover = in(mouseX, mouseY, r[0] + 5, ry, r[2] - 10, ROW_H);
            final boolean sel = state != null && choices.get(idx).key().equals(state.machineType());
            skin.listRow(g, r[0] + 5, ry, r[2] - 10, ROW_H, hover, sel);
            g.drawString(font, clip(font, choices.get(idx).label(), r[2] - 16), r[0] + 8, ry + 2, skin.listRowText(sel), false);
        }
        button(g, font, r[0] + r[2] - 5 - 44, r[1] + r[3] - BTN_H - 4, 44, "Close", true, mouseX, mouseY);
    }

    private int[] amountRect(final int x, final int y, final int width, final int height) {
        final int pw = 150;
        final int ph = 60;
        return new int[] {x + (width - pw) / 2, y + (height - ph) / 2, pw, ph};
    }

    private void renderAmountPopup(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                                   final int height, final int mouseX, final int mouseY) {
        final int[] r = amountRect(x, y, width, height);
        skin.windowFrame(g, r[0], r[1], r[2], r[3]);
        g.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, skin.windowBg());
        g.drawString(font, amountForOutput ? "Output amount per run" : "Input amount per run", r[0] + 5, r[1] + 4, skin.text(), false);
        final int rowY = r[1] + 18;
        button(g, font, r[0] + 5, rowY, 18, "-", amountValue > 1, mouseX, mouseY);
        button(g, font, r[0] + 25, rowY, 18, "/2", amountValue > 1, mouseX, mouseY);
        final String value = Long.toString(amountValue);
        g.drawString(font, value, r[0] + r[2] / 2 - font.width(value) / 2, rowY + 2, skin.text(), false);
        button(g, font, r[0] + r[2] - 43, rowY, 18, "x2", true, mouseX, mouseY);
        button(g, font, r[0] + r[2] - 23, rowY, 18, "+", true, mouseX, mouseY);
        button(g, font, r[0] + 5, r[1] + r[3] - BTN_H - 5, 44, "Clear", true, mouseX, mouseY);
        button(g, font, r[0] + r[2] - 49, r[1] + r[3] - BTN_H - 5, 44, "Done", true, mouseX, mouseY);
    }

    // ======================================================================================
    //  Input
    // ======================================================================================

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (state == null) {
            return;
        }
        final int mx = (int) mouseX;
        final int my = (int) mouseY;
        if (machinePickerOpen) {
            machinePickerClicked(mx, my, button);
            return;
        }
        if (amountPopupOpen) {
            amountPopupClicked(mx, my, button);
            return;
        }
        blur();
        final int x = lastX;
        final int y = lastY;
        final int width = lastW;
        final int height = lastH;
        final int editorW = width - RAIL_W;
        final int barY = y + height - BAR_H;
        final int editorEnd = y + editorBottom(height);
        // Tab strip.
        if (my >= y && my < y + TAB_H && mx < x + editorW) {
            tab = Math.min(2, Math.max(0, (mx - x) / Math.max(1, editorW / 3)));
            selectedStage = -1;
            return;
        }
        // Action bar.
        if (my >= barY) {
            final int action = layoutBar(null, null, x, barY, width, mx, my, true);
            if (action >= 0 && button == 0) {
                final int a = action == 0 ? PatternStudioEditPayload.BURN
                        : action == 1 ? PatternStudioEditPayload.SAVE_TO_DISK : PatternStudioEditPayload.LOAD_INTO_ROM;
                send(PatternStudioEditPayload.at(host, monitorPos, a, tab));
            }
            return;
        }
        // Rail.
        if (mx >= x + editorW) {
            railClicked(x + editorW, y, RAIL_W, barY - y, mx, my, button);
            return;
        }
        // The inventory band is the container's; a click there that reached the app fell between the slots.
        if (my >= editorEnd) {
            return;
        }
        final int ey = y + TAB_H;
        final int eh = editorEnd - ey;
        switch (tab) {
            case TAB_MACHINE -> machineClicked(x, ey, editorW, eh, mx, my, button);
            case TAB_PIPELINE -> pipelineClicked(x, ey, editorW, eh, mx, my, button);
            default -> benchClicked(x, ey, editorW, eh, mx, my, button);
        }
    }

    private void benchClicked(final int x, final int y, final int w, final int h, final int mx, final int my,
                              final int button) {
        final int gx = x + PAD;
        final int gy = y + PAD;
        for (int i = 0; i < 9; i++) {
            final int cx = gx + (i % 3) * CELL;
            final int cy = gy + (i / 3) * CELL;
            if (in(mx, my, cx, cy, CELL, CELL)) {
                final PatternStudioStatePayload.BenchCell cell = state.bench().get(i);
                if (button == 1) {
                    if (!cell.stack().isEmpty()) {
                        send(PatternStudioEditPayload.text(host, monitorPos, PatternStudioEditPayload.BENCH_SET_TAG, i,
                                nextTag(cell.stack(), cell.tag()), ""));
                    }
                } else {
                    // The server reads the carried stack itself; an empty item here means "what I carry".
                    send(PatternStudioEditPayload.at(host, monitorPos,
                            carried().isEmpty() && !cell.stack().isEmpty() ? PatternStudioEditPayload.BENCH_CLEAR_CELL
                                    : PatternStudioEditPayload.BENCH_SET_CELL, i));
                }
                return;
            }
        }
        if (in(mx, my, x + w - PAD - 36, gy, 36, BTN_H)) {
            send(PatternStudioEditPayload.of(host, monitorPos, PatternStudioEditPayload.BENCH_CLEAR));
            return;
        }
        final int fy = gy + 3 * CELL + PAD;
        nameNoteClicked(benchName, benchNote, x + PAD, fy, w - PAD * 2, mx, my);
    }

    private void machineClicked(final int x, final int y, final int w, final int h, final int mx, final int my,
                                final int button) {
        final int gx = x + PAD;
        final int gy = y + PAD;
        final int outX = x + w - PAD - PROC_COLS * CELL;
        if (procGridClicked(gx, gy, false, inScroll, mx, my, button)
                || procGridClicked(outX, gy, true, outScroll, mx, my, button)) {
            return;
        }
        final int mxx = gx + PROC_COLS * CELL + 10;
        final int mw = outX - mxx - 10;
        if (in(mx, my, mxx, gy, mw, BTN_H)) {
            machinePickerOpen = true;
            machineSearch.sync("");
            focus(machineSearch);
            return;
        }
        if (in(mx, my, mxx + 42, gy + 14, Math.max(30, mw - 42), FIELD_H)) {
            focus(timeout);
            return;
        }
        if (in(mx, my, mxx + mw - 36, gy + 40, 36, BTN_H)) {
            send(PatternStudioEditPayload.of(host, monitorPos, PatternStudioEditPayload.PROC_CLEAR));
            return;
        }
        final int fy = gy + PROC_ROWS * CELL + PAD;
        nameNoteClicked(procName, procNote, x + PAD, fy, w - PAD * 2, mx, my);
    }

    private boolean procGridClicked(final int gx, final int gy, final boolean output, final int scroll, final int mx,
                                    final int my, final int button) {
        for (int row = 0; row < PROC_ROWS; row++) {
            for (int col = 0; col < PROC_COLS; col++) {
                final int index = (scroll + row) * PROC_COLS + col;
                final int cx = gx + col * CELL;
                final int cy = gy + row * CELL;
                if (!in(mx, my, cx, cy, CELL, CELL)) {
                    continue;
                }
                final PatternStudioStatePayload.ProcCell cell = procCell(output ? state.outputs() : state.inputs(), index);
                if (isShiftDown() && cell != null) {
                    amountPopupOpen = true;
                    amountForOutput = output;
                    amountCell = index;
                    amountValue = cell.cell().amount();
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
                return true;
            }
        }
        return false;
    }

    private void pipelineClicked(final int x, final int y, final int w, final int h, final int mx, final int my,
                                 final int button) {
        final int lx = x + PAD;
        final int ly = y + PAD;
        final int listH = pipelineListH(h);
        final int listW = w - PAD * 2;
        if (in(mx, my, lx, ly, listW, listH)) {
            final int idx = (my - ly - 1) / ROW_H;
            selectedStage = idx >= 0 && idx < state.stages().size() ? idx : -1;
            return;
        }
        final int by = ly + listH + 3;
        if (in(mx, my, lx, by, 66, BTN_H)) {
            send(PatternStudioEditPayload.of(host, monitorPos, PatternStudioEditPayload.PIPE_ADD_BENCH));
            return;
        }
        if (in(mx, my, lx + 69, by, 66, BTN_H)) {
            send(PatternStudioEditPayload.of(host, monitorPos, PatternStudioEditPayload.PIPE_ADD_PROC));
            return;
        }
        if (in(mx, my, lx + 138, by, 50, BTN_H) && selectedStage >= 0) {
            send(PatternStudioEditPayload.at(host, monitorPos, PatternStudioEditPayload.PIPE_REMOVE, selectedStage));
            selectedStage = -1;
            return;
        }
        final int fy = by + BTN_H + 3;
        nameNoteClicked(pipeName, pipeNote, lx, fy, listW, mx, my);
    }

    private void railClicked(final int x, final int y, final int w, final int h, final int mx, final int my,
                             final int button) {
        final int tw = w / RAIL_TABS;
        if (my >= y + 1 && my < y + 1 + RAIL_TAB_H) {
            rail = Math.min(RAIL_TABS - 1, Math.max(0, (mx - x - 1) / Math.max(1, tw)));
            railScroll = 0;
            return;
        }
        final int top = y + RAIL_TAB_H + 3;
        final int rx = x + 2;
        final int rw = w - 4;
        if (rail == RAIL_FILES) {
            final List<FileRow> rows = fileRows();
            final int idx = railScroll + (my - top) / ROW_H;
            if (my >= top && idx >= 0 && idx < rows.size() && !rows.get(idx).header() && button == 0) {
                final FileRow r = rows.get(idx);
                final int action = tab == TAB_PIPELINE ? PatternStudioEditPayload.PIPE_ADD_FILE : PatternStudioEditPayload.OPEN_FILE;
                send(PatternStudioEditPayload.text(host, monitorPos, action, 0, r.driveKey(), r.file()));
            }
            return;
        }
        final PatternStudioStatePayload.Encoder e = state.encoder();
        if (!e.linked()) {
            return;
        }
        final int by = lastY + encoderButtonsY();
        if (in(mx, my, rx + 2, by, (rw - 6) / 2, BTN_H)) {
            send(PatternStudioEditPayload.of(host, monitorPos, PatternStudioEditPayload.ENCODER_CANCEL));
        } else if (in(mx, my, rx + 2 + (rw - 6) / 2 + 2, by, (rw - 6) / 2, BTN_H)) {
            send(PatternStudioEditPayload.of(host, monitorPos, PatternStudioEditPayload.ENCODER_EJECT));
        }
    }

    private void machinePickerClicked(final int mx, final int my, final int button) {
        final int[] r = pickerRect(lastX, lastY, lastW, lastH);
        if (in(mx, my, r[0] + 5, r[1] + 14, r[2] - 10, FIELD_H)) {
            focus(machineSearch);
            return;
        }
        if (in(mx, my, r[0] + r[2] - 5 - 44, r[1] + r[3] - BTN_H - 4, 44, BTN_H) || !in(mx, my, r[0], r[1], r[2], r[3])) {
            machinePickerOpen = false;
            blur();
            return;
        }
        final int listY = r[1] + 14 + FIELD_H + 3;
        final int listH = r[3] - (listY - r[1]) - BTN_H - 6;
        if (my >= listY && my < listY + listH && button == 0) {
            final List<Choice> choices = machineChoices();
            final int idx = machineScroll + (my - listY) / ROW_H;
            if (idx >= 0 && idx < choices.size()) {
                send(PatternStudioEditPayload.text(host, monitorPos, PatternStudioEditPayload.PROC_SET_MACHINE, 0,
                        choices.get(idx).key(), ""));
                machinePickerOpen = false;
                blur();
            }
        }
    }

    private void amountPopupClicked(final int mx, final int my, final int button) {
        final int[] r = amountRect(lastX, lastY, lastW, lastH);
        final int rowY = r[1] + 18;
        if (in(mx, my, r[0] + 5, rowY, 18, BTN_H)) {
            amountValue = Math.max(1, amountValue - 1);
        } else if (in(mx, my, r[0] + 25, rowY, 18, BTN_H)) {
            amountValue = Math.max(1, amountValue / 2);
        } else if (in(mx, my, r[0] + r[2] - 43, rowY, 18, BTN_H)) {
            amountValue = Math.min(Long.MAX_VALUE / 4, amountValue * 2);
        } else if (in(mx, my, r[0] + r[2] - 23, rowY, 18, BTN_H)) {
            amountValue = Math.min(Long.MAX_VALUE / 4, amountValue + 1);
        } else if (in(mx, my, r[0] + 5, r[1] + r[3] - BTN_H - 5, 44, BTN_H)) {
            send(PatternStudioEditPayload.number(host, monitorPos,
                    amountForOutput ? PatternStudioEditPayload.PROC_SET_OUTPUT_AMOUNT : PatternStudioEditPayload.PROC_SET_INPUT_AMOUNT,
                    amountCell, 0L));
            amountPopupOpen = false;
        } else if (in(mx, my, r[0] + r[2] - 49, r[1] + r[3] - BTN_H - 5, 44, BTN_H)) {
            send(PatternStudioEditPayload.number(host, monitorPos,
                    amountForOutput ? PatternStudioEditPayload.PROC_SET_OUTPUT_AMOUNT : PatternStudioEditPayload.PROC_SET_INPUT_AMOUNT,
                    amountCell, amountValue));
            amountPopupOpen = false;
        } else if (!in(mx, my, r[0], r[1], r[2], r[3])) {
            amountPopupOpen = false;
        }
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        final int step = delta > 0 ? -1 : 1;
        if (machinePickerOpen) {
            machineScroll = Math.max(0, machineScroll + step);
            return true;
        }
        final int x = lastX;
        final int editorW = lastW - RAIL_W;
        if (lastMouseX >= x + editorW) {
            railScroll = Math.max(0, railScroll + step);
            return true;
        }
        if (tab == TAB_MACHINE && state != null && lastMouseY < lastY + editorBottom(lastH)) {
            final int outX = x + editorW - PAD - PROC_COLS * CELL;
            if (lastMouseX >= outX) {
                outScroll = Math.max(0, outScroll + step);
            } else {
                inScroll = Math.max(0, inScroll + step);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(final char c) {
        if (focused == null) {
            return false;
        }
        if (c >= 32 && c != 127) {
            focused.type(c);
            if (focused == machineSearch) {
                machineScroll = 0;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (machinePickerOpen || amountPopupOpen) {
                machinePickerOpen = false;
                amountPopupOpen = false;
                blur();
                return true;
            }
            if (focused != null) {
                focused.revert();
                blur();
                return true;
            }
            return false;
        }
        if (focused == null) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            focused.backspace();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) {
            blur();
            return true;
        }
        return true; // a focused field eats every other key so the desktop never sees it
    }

    /** Gives {@code f} the keyboard, committing whatever field had it. */
    private void focus(final Field f) {
        if (focused != f) {
            blur();
        }
        focused = f;
        f.focused = true;
    }

    /** Drops the keyboard focus, sending the field's value if it changed. */
    private void blur() {
        if (focused == null) {
            return;
        }
        final Field f = focused;
        focused = null;
        f.focused = false;
        if (!f.dirty()) {
            return;
        }
        f.commit();
        if (f == benchName || f == benchNote) {
            send(PatternStudioEditPayload.text(host, monitorPos, PatternStudioEditPayload.BENCH_SET_NAME, 0,
                    benchName.value(), benchNote.value()));
        } else if (f == procName || f == procNote) {
            send(PatternStudioEditPayload.text(host, monitorPos, PatternStudioEditPayload.PROC_SET_NAME, 0,
                    procName.value(), procNote.value()));
        } else if (f == pipeName || f == pipeNote) {
            send(PatternStudioEditPayload.text(host, monitorPos, PatternStudioEditPayload.PIPE_SET_NAME, 0,
                    pipeName.value(), pipeNote.value()));
        } else if (f == timeout) {
            try {
                final int ticks = Math.max(1, Integer.parseInt(timeout.value().trim()));
                send(PatternStudioEditPayload.number(host, monitorPos, PatternStudioEditPayload.PROC_SET_TIMEOUT, 0, ticks));
            } catch (final NumberFormatException ignored) {
                timeout.revert();
            }
        }
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

    // ======================================================================================
    //  Helpers
    // ======================================================================================

    private void drawCell(final GuiGraphics g, final int x, final int y, final int mouseX, final int mouseY,
                          final boolean marked) {
        g.fill(x, y, x + CELL, y + CELL, skin.fieldBg());
        OsSkin.outline(g, x, y, CELL, CELL, marked ? skin.accent() : skin.edge());
        if (in(mouseX, mouseY, x, y, CELL, CELL)) {
            g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, skin.listHover());
        }
    }

    /** Draws a button; returns its right edge. */
    private int button(final GuiGraphics g, final Font font, final int x, final int y, final int w, final String label,
                       final boolean enabled, final int mouseX, final int mouseY) {
        final boolean hover = enabled && in(mouseX, mouseY, x, y, w, BTN_H);
        skin.button(g, font, x, y, w, BTN_H, label, hover, false, false);
        if (!enabled) {
            g.fill(x, y, x + w, y + BTN_H, 0x66FFFFFF);
        }
        return x + w;
    }

    private static final int NAME_LABEL_W = 28;

    /** Name and note fields on one row: the name takes two fifths, the note the rest. */
    private void nameNoteRow(final GuiGraphics g, final Font font, final Field name, final Field note, final int x,
                             final int y, final int w) {
        final int nameW = (w - NAME_LABEL_W * 2) * 2 / 5;
        g.drawString(font, "Name", x, y + 2, skin.dim(), false);
        field(g, font, name, x + NAME_LABEL_W, y, nameW);
        final int noteX = x + NAME_LABEL_W + nameW + NAME_LABEL_W;
        g.drawString(font, "Note", noteX - NAME_LABEL_W + 2, y + 2, skin.dim(), false);
        field(g, font, note, noteX, y, x + w - noteX);
    }

    private void nameNoteClicked(final Field name, final Field note, final int x, final int y, final int w,
                                 final int mx, final int my) {
        final int nameW = (w - NAME_LABEL_W * 2) * 2 / 5;
        if (in(mx, my, x + NAME_LABEL_W, y, nameW, FIELD_H)) {
            focus(name);
            return;
        }
        final int noteX = x + NAME_LABEL_W + nameW + NAME_LABEL_W;
        if (in(mx, my, noteX, y, x + w - noteX, FIELD_H)) {
            focus(note);
        }
    }

    private void field(final GuiGraphics g, final Font font, final Field f, final int x, final int y, final int w) {
        skin.field(g, x, y, w, FIELD_H, f.focused);
        final String shown = f.focused ? f.edit : f.value();
        final String text = f.focused ? tail(font, shown, w - 8) + "_" : clip(font, shown, w - 6);
        g.drawString(font, text, x + 3, y + 2, skin.text(), false);
    }

    private static String tail(final Font font, final String s, final int width) {
        String out = s;
        while (!out.isEmpty() && font.width(out) > width) {
            out = out.substring(1);
        }
        return out;
    }

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

    private static boolean in(final int mx, final int my, final int x, final int y, final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int clampScroll(final int scroll, final int count, final int visible) {
        return Math.max(0, Math.min(scroll, Math.max(0, count - visible)));
    }

    private static ItemStack carried() {
        final var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.containerMenu.getCarried();
    }

    private static boolean isShiftDown() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
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

    /** A single-line text field: the committed value, the text being edited, and whether it has the keyboard. */
    private static final class Field {
        private final int max;
        private String committed = "";
        private String edit = "";
        private boolean focused;

        private Field(final int max) {
            this.max = max;
        }

        /** Adopts the server's value unless the player is typing in it. */
        private void sync(final String value) {
            if (!focused) {
                committed = value == null ? "" : value;
                edit = committed;
            }
        }

        private String value() {
            return committed;
        }

        private void type(final char c) {
            if (edit.length() < max) {
                edit += c;
            }
        }

        private void backspace() {
            if (!edit.isEmpty()) {
                edit = edit.substring(0, edit.length() - 1);
            }
        }

        private boolean dirty() {
            return !edit.equals(committed);
        }

        private void commit() {
            committed = edit;
        }

        private void revert() {
            edit = committed;
        }
    }

    // ======================================================================================
    //  Inspection (client tests): content-local centres of the controls, from the last frame's geometry
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
        return machinePickerOpen;
    }

    public boolean isAmountPopupOpen() {
        return amountPopupOpen;
    }

    public int railTab() {
        return rail;
    }

    private int[] local(final int x, final int y) {
        return new int[] {x - lastX, y - lastY};
    }

    /** The centre of editor tab {@code t} (0 bench, 1 machine, 2 multi-stage). */
    public int[] tabCenter(final int t) {
        final int tw = (lastW - RAIL_W) / 3;
        return local(lastX + t * tw + tw / 2, lastY + TAB_H / 2);
    }

    /** The centre of rail tab {@code t} (0 files, 1 encoder). */
    public int[] railTabCenter(final int t) {
        final int tw = RAIL_W / RAIL_TABS;
        return local(lastX + lastW - RAIL_W + 1 + t * tw + tw / 2, lastY + 1 + RAIL_TAB_H / 2);
    }

    /** The centre of rail list row {@code row} among the visible rows of the Files rail. */
    public int[] railRowCenter(final int row) {
        final int top = lastY + RAIL_TAB_H + 3;
        return local(lastX + lastW - RAIL_W + RAIL_W / 2, top + row * ROW_H + ROW_H / 2);
    }

    /** The centre of bar button {@code i} (0 burn, 1 save to disk, 2 load into the ROM). */
    public int[] barButtonCenter(final int i) {
        final int barY = lastY + lastH - BAR_H;
        return local(lastX + PAD + i * (BAR_BTN_W + PAD) + BAR_BTN_W / 2, barY + (BAR_H - BTN_H) / 2 + 1 + BTN_H / 2);
    }

    /** The centre of bench cell {@code index} (0..8). */
    public int[] benchCellCenter(final int index) {
        return local(lastX + PAD + (index % 3) * CELL + CELL / 2, lastY + TAB_H + PAD + (index / 3) * CELL + CELL / 2);
    }

    /** The centre of machine input ({@code output == false}) or output cell {@code index} among the visible rows. */
    public int[] procCellCenter(final boolean output, final int index) {
        final int gx = output ? lastX + lastW - RAIL_W - PAD - PROC_COLS * CELL : lastX + PAD;
        final int scroll = output ? outScroll : inScroll;
        final int row = index / PROC_COLS - scroll;
        return local(gx + (index % PROC_COLS) * CELL + CELL / 2,
                lastY + TAB_H + PAD + row * CELL + CELL / 2);
    }

    /** The centre of the machine picker button. */
    public int[] machineButtonCenter() {
        final int gx = lastX + PAD;
        final int outX = lastX + lastW - RAIL_W - PAD - PROC_COLS * CELL;
        final int mx = gx + PROC_COLS * CELL + 10;
        return local(mx + (outX - mx - 10) / 2, lastY + TAB_H + PAD + BTN_H / 2);
    }

    /** The centre of the timeout field. */
    public int[] timeoutFieldCenter() {
        final int gx = lastX + PAD;
        final int outX = lastX + lastW - RAIL_W - PAD - PROC_COLS * CELL;
        final int mx = gx + PROC_COLS * CELL + 10;
        final int mw = outX - mx - 10;
        return local(mx + 42 + Math.max(30, mw - 42) / 2, lastY + TAB_H + PAD + 14 + FIELD_H / 2);
    }

    /** The centre of machine picker row {@code row} among the visible rows. */
    public int[] machinePickerRowCenter(final int row) {
        final int[] r = pickerRect(lastX, lastY, lastW, lastH);
        final int listY = r[1] + 14 + FIELD_H + 3;
        return local(r[0] + r[2] / 2, listY + (row - machineScroll) * ROW_H + ROW_H / 2);
    }

    /** The centre of pipeline button {@code i} (0 add bench, 1 add machine, 2 remove). */
    public int[] pipelineButtonCenter(final int i) {
        final int editorH = editorBottom(lastH) - TAB_H;
        final int by = lastY + TAB_H + PAD + pipelineListH(editorH) + 3;
        final int bx = lastX + PAD + (i == 0 ? 33 : i == 1 ? 69 + 33 : 138 + 25);
        return local(bx, by + BTN_H / 2);
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
