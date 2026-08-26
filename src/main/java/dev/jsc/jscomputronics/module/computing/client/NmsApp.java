/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.client.os.DesktopApp;
import dev.jsc.jscomputronics.module.computing.client.os.DesktopWindow;
import dev.jsc.jscomputronics.module.computing.client.theme.EraTheme;
import dev.jsc.jscomputronics.module.computing.client.theme.NmsThemes;
import dev.jsc.jscomputronics.module.computing.gui.layout.NmsLayout;
import dev.jsc.jscomputronics.module.computing.operation.payload.IqlFileContentPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.IqlFileListPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.IqlResultPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NmsSchemaPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.OpenIqlFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestIqlFileListPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestNmsSchemaPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RunIqlPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.SaveIqlFilePayload;
import dev.jsc.jscomputronics.module.computing.program.ProgramKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Network Management Studio as a desktop window (a {@link DesktopApp}): an in-world clone of SQL
 * Server Management Studio with an Object Explorer, a query editor, a results/messages grid and a status
 * bar. It opens from the Panes desktop NMS icon and closes back to the desktop like any other window.
 *
 * <p>It carries its own light SSMS skin ({@link NmsThemes}) rather than the host's era OS skin — a program
 * has its own identity. The {@link NmsLayout} constants give the fixed internal geometry; the explorer
 * width and editor height are live, draggable via the two splitters. Unlike the other desktop apps it has
 * no real inventory slots, so it needs no container menu: the payload handlers authenticate by the player's
 * proximity to the host or one of its linked monitors.
 */
public final class NmsApp implements DesktopApp {

    private static final int DB = 0;
    private static final int FOLDER = 1;
    private static final int TABLE = 2;
    private static final int COLUMN = 3;
    private static final int VIEW = 4;
    private static final int PROC = 5;
    private static final int JOB = 6;
    private static final int SERVER = 7;
    private static final int ICON_VIEW = 0xFFA371F7;
    private static final int ICON_PROC = 0xFFE08A8A;
    private static final int ICON_JOB = 0xFFF0A64E;

    private static final int TREE_PITCH = 8;
    private static final int GUTTER_W = 16;
    private static final int EXEC_X = 4;
    private static final int EXEC_W = 62;

    private static final int DRAG_NONE = 0;
    private static final int DRAG_V = 1;
    private static final int DRAG_H = 2;
    private static final int MIN_EXPLORER_W = 60;
    private static final int MIN_EDITOR_H = 30;
    private static final int MIN_GRID_H = 22;

    private static final String[] MENU = {"File", "Edit", "View", "Query", "Tools", "Window", "Help"};
    private static final int TAB_W = 82;
    private static final int EDITOR_LINE_H = 10;

    private static final String[] FILE_ITEMS = {"New", "Save", "Save As...", "Open..."};

    private static final int DIALOG_NONE = 0;
    private static final int DIALOG_SAVE_AS = 1;
    private static final int DIALOG_OPEN = 2;

    /** A node in the Object Explorer. {@code inject} is the text put into the query when clicked, or null. */
    private static final class Node {
        private final int icon;
        private final String label;
        private final String inject;
        private final List<Node> children;
        private boolean expanded;
        private boolean liveCount;

        private Node(final int icon, final String label, final String inject, final List<Node> children) {
            this.icon = icon;
            this.label = label;
            this.inject = inject;
            this.children = children;
        }

        private boolean expandable() {
            return !children.isEmpty();
        }
    }

    private record VisibleNode(Node node, int depth) {
    }

    private record MsgLine(String text, boolean ok) {
    }

    private final BlockPos host;
    private final BlockPos monitorPos;

    // Set fresh every frame from renderContent, so input handlers and tooltips share the live geometry.
    private Font font = Minecraft.getInstance().font;
    private int originX;
    private int originY;
    private double lastMouseX;
    private double lastMouseY;
    private EraTheme theme = NmsThemes.of(null);
    private dev.jsc.jscomputronics.module.computing.client.os.OsSkin osSkin =
            dev.jsc.jscomputronics.module.computing.client.os.OsSkin.fallback();

    private IqlEditor editor;
    private final List<IqlResultPayload.Row> rows = new ArrayList<>();
    private String status = "ready";
    private boolean statusOk = true;
    private int gridScroll;

    private final List<MsgLine> messages = new ArrayList<>();
    private boolean showMessages;

    private Node treeRoot;
    private final List<VisibleNode> visible = new ArrayList<>();
    private int treeScroll;

    private String networkLabel = "jsc-net";
    private List<String> liveServers = List.of();
    private int liveItemTypes = -1;
    private int liveOperations = -1;
    private NmsSchemaPayload.EngineSnapshot liveEngine = NmsSchemaPayload.EngineSnapshot.offline();
    private boolean schemaRequested;
    private boolean refreshOnResult;

    private int explorerW = NmsLayout.EXPLORER_W;
    private int editorH = NmsLayout.EDITOR_H;
    // The live content size, set each frame from the window so the Studio fills (and resizes with) its window
    // instead of drawing at a fixed size in the corner. Defaults to the layout's design size.
    private int viewW = NmsLayout.WIDTH;
    private int viewH = NmsLayout.HEIGHT;
    private int dragging = DRAG_NONE;

    private boolean fileMenuOpen;
    private int fileMenuLabelX = 6;
    @Nullable
    private String currentFile;
    private int dialogMode = DIALOG_NONE;
    private final StringBuilder saveAsName = new StringBuilder();
    private List<String> iqlFiles = List.of();
    private int pickerScroll;

    private static NmsApp active;

    public NmsApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
        active = this;
        treeRoot = buildTree();
        rebuildVisible();
    }

    @Override
    public String title() {
        return "Network Management Studio";
    }

    @Override
    public int defaultWidth() {
        return NmsLayout.WIDTH;
    }

    @Override
    public int defaultHeight() {
        return NmsLayout.HEIGHT;
    }

    @Override
    public int minWidth() {
        return NmsLayout.WIDTH;
    }

    @Override
    public int minHeight() {
        return 150;
    }

    // --- payload routing (to the open NMS window) -------------------------------------------------

    public static void accept(final IqlResultPayload payload) {
        if (active != null) {
            active.apply(payload);
        }
    }

    public static void acceptSchema(final NmsSchemaPayload payload) {
        if (active != null) {
            active.applySchema(payload);
        }
    }

    public static void acceptFileList(final IqlFileListPayload payload) {
        if (active != null) {
            active.applyFileList(payload);
        }
    }

    public static void acceptFileContent(final IqlFileContentPayload payload) {
        if (active != null) {
            active.applyFileContent(payload);
        }
    }

    private void apply(final IqlResultPayload payload) {
        rows.clear();
        rows.addAll(payload.rows());
        status = payload.message();
        statusOk = payload.ok();
        gridScroll = 0;
        messages.add(new MsgLine(payload.message(), payload.ok()));
        while (messages.size() > 64) {
            messages.remove(0);
        }
        if (refreshOnResult) {
            refreshOnResult = false;
            PacketDistributor.sendToServer(new RequestNmsSchemaPayload(host));
        }
    }

    private void applyFileList(final IqlFileListPayload payload) {
        iqlFiles = new ArrayList<>(payload.files());
        if (!payload.status().isEmpty()) {
            status = payload.status();
            statusOk = payload.ok();
        }
        if (dialogMode == DIALOG_SAVE_AS && payload.ok() && !payload.status().isEmpty()) {
            dialogMode = DIALOG_NONE;
        }
    }

    private void applyFileContent(final IqlFileContentPayload payload) {
        if (!payload.ok()) {
            status = "could not open file";
            statusOk = false;
            return;
        }
        editor().setValue(payload.content());
        currentFile = payload.fileName();
        status = "opened: " + payload.fileName();
        statusOk = true;
        dialogMode = DIALOG_NONE;
    }

    private void applySchema(final NmsSchemaPayload payload) {
        networkLabel = payload.networkLabel();
        liveServers = payload.servers();
        liveItemTypes = payload.itemTypes();
        liveOperations = payload.operations();
        liveEngine = payload.engine();
        final java.util.Set<String> expanded = new java.util.HashSet<>();
        if (treeRoot != null) {
            captureExpanded(treeRoot, expanded);
        }
        treeRoot = buildTree();
        applyExpanded(treeRoot, expanded);
        rebuildVisible();
    }

    private static String nodeKey(final Node node) {
        return node.inject != null ? node.inject : node.label;
    }

    private void captureExpanded(final Node node, final java.util.Set<String> out) {
        if (node.expanded) {
            out.add(nodeKey(node));
        }
        for (final Node child : node.children) {
            captureExpanded(child, out);
        }
    }

    private void applyExpanded(final Node node, final java.util.Set<String> expanded) {
        if (node.expandable()) {
            node.expanded = expanded.contains(nodeKey(node));
        }
        for (final Node child : node.children) {
            applyExpanded(child, expanded);
        }
    }

    /** Lazily builds the editor once a font is available, and requests the schema on first frame. */
    private IqlEditor editor() {
        if (editor == null) {
            editor = new IqlEditor(font, RunIqlPayload.MAX_LEN);
        }
        return editor;
    }

    private void ensureSchema() {
        if (!schemaRequested) {
            schemaRequested = true;
            PacketDistributor.sendToServer(new RequestNmsSchemaPayload(host));
        }
    }

    private void execute() {
        final String statement = editor().value().trim();
        if (statement.isEmpty()) {
            return;
        }
        status = "executing...";
        statusOk = true;
        refreshOnResult = isDefinition(statement);
        PacketDistributor.sendToServer(new RunIqlPayload(monitorPos, host, statement));
    }

    private static boolean isDefinition(final String statement) {
        final String head = statement.strip().toUpperCase(Locale.ROOT);
        if (head.startsWith("CREATE ") || head.startsWith("EXEC ") || head.startsWith("CALL ")) {
            return true;
        }
        return head.startsWith("DROP VIEW ") || head.startsWith("DROP PROCEDURE ")
                || head.startsWith("DROP PROC ") || head.startsWith("DROP JOB ");
    }

    // --- live geometry ----------------------------------------------------------------------------

    private int vsplitX() {
        return explorerW;
    }

    private int rightX() {
        return explorerW + NmsLayout.VSPLIT_W;
    }

    private int rightW() {
        return viewW - rightX();
    }

    private int hsplitY() {
        return NmsLayout.EDITOR_Y + editorH;
    }

    private int resTabsY() {
        return hsplitY() + NmsLayout.HSPLIT_H;
    }

    private int gridY() {
        return resTabsY() + NmsLayout.RES_TABS_H;
    }

    private int maxEditorH() {
        return statusY() - NmsLayout.EDITOR_Y - NmsLayout.HSPLIT_H - NmsLayout.RES_TABS_H - MIN_GRID_H;
    }

    /** The status bar's top, anchored to the window bottom so the body grows with a taller window. */
    private int statusY() {
        return viewH - NmsLayout.STATUS_H;
    }

    /** The body height (between the toolbar and the status bar) for the current window height. */
    private int bodyH() {
        return statusY() - NmsLayout.BODY_Y;
    }

    /** The widest the Object Explorer split may grow for the current window width. */
    private int maxExplorerW() {
        return viewW - 150;
    }

    // --- Object Explorer tree ---------------------------------------------------------------------

    private static Node leaf(final int icon, final String label) {
        return new Node(icon, label, label, List.of());
    }

    private static Node branch(final int icon, final String label, final String inject, final Node... kids) {
        return new Node(icon, label, inject, List.of(kids));
    }

    private Node buildTree() {
        final Node items = branch(TABLE, withCount("items", liveItemTypes), "items",
                leaf(COLUMN, "item"), leaf(COLUMN, "qty"), leaf(COLUMN, "server"), leaf(COLUMN, "enchant"),
                leaf(COLUMN, "durability"), leaf(COLUMN, "name"), leaf(COLUMN, "tag"), leaf(COLUMN, "damaged"));
        items.expanded = true;
        final Node tables = branch(FOLDER, "tables", null,
                items,
                branch(TABLE, "servers", "servers",
                        leaf(COLUMN, "name"), leaf(COLUMN, "free"), leaf(COLUMN, "used"),
                        leaf(COLUMN, "capacity"), leaf(COLUMN, "era"), leaf(COLUMN, "cpu"), leaf(COLUMN, "ram")),
                branch(TABLE, "disks", "disks",
                        leaf(COLUMN, "server"), leaf(COLUMN, "type"), leaf(COLUMN, "capacity"),
                        leaf(COLUMN, "used"), leaf(COLUMN, "public")),
                branch(TABLE, withCount("operations", liveOperations), "operations",
                        leaf(COLUMN, "id"), leaf(COLUMN, "verb"), leaf(COLUMN, "status"),
                        leaf(COLUMN, "progress"), leaf(COLUMN, "age")),
                branch(TABLE, "recipes", "recipes",
                        leaf(COLUMN, "result"), leaf(COLUMN, "ingredients"), leaf(COLUMN, "craftable")),
                branch(TABLE, "computers", "computers",
                        leaf(COLUMN, "name"), leaf(COLUMN, "type"), leaf(COLUMN, "era"), leaf(COLUMN, "online")));
        tables.expanded = true;
        final Node root = branch(DB, networkLabel + " (Mainframe)", null,
                engineInfo(),
                serversBranch(),
                tables,
                objectBranch(VIEW, "views", liveEngine.views()),
                objectBranch(PROC, "procedures", liveEngine.procedures()),
                objectBranch(JOB, "jobs", liveEngine.jobs()));
        root.expanded = true;
        return root;
    }

    private Node engineInfo() {
        return new Node(DB, "engine: " + liveEngine.state(), null, List.of());
    }

    private Node objectBranch(final int leafIcon, final String label, final List<String> names) {
        final List<Node> leaves = new ArrayList<>();
        for (final String name : names) {
            leaves.add(leaf(leafIcon, name));
        }
        final Node node = new Node(FOLDER, label, null, List.copyOf(leaves));
        node.liveCount = true;
        return node;
    }

    private Node serversBranch() {
        final List<Node> leaves = new ArrayList<>();
        for (final String name : liveServers) {
            leaves.add(leaf(SERVER, name));
        }
        final Node servers = new Node(FOLDER, "servers", null, List.copyOf(leaves));
        servers.liveCount = true;
        servers.expanded = true;
        return servers;
    }

    private static String withCount(final String base, final int count) {
        return count < 0 ? base : base + " (" + count + ")";
    }

    private void rebuildVisible() {
        visible.clear();
        appendVisible(treeRoot, 0);
    }

    private void appendVisible(final Node node, final int depth) {
        visible.add(new VisibleNode(node, depth));
        if (node.expanded) {
            for (final Node child : node.children) {
                appendVisible(child, depth + 1);
            }
        }
    }

    private int treeMaxRows() {
        return NmsLayout.EX_TREE_H / TREE_PITCH;
    }

    private int treeStart() {
        return Math.max(0, Math.min(treeScroll, Math.max(0, visible.size() - treeMaxRows())));
    }

    // --- File menu actions ------------------------------------------------------------------------

    private void fileNew() {
        editor().setValue("");
        currentFile = null;
        status = "new query";
        statusOk = true;
        fileMenuOpen = false;
    }

    private void fileSave() {
        fileMenuOpen = false;
        if (currentFile == null) {
            fileSaveAs();
            return;
        }
        final String baseName = currentFile.endsWith(".iql")
                ? currentFile.substring(0, currentFile.length() - 4) : currentFile;
        PacketDistributor.sendToServer(new SaveIqlFilePayload(host, baseName, editor().value()));
    }

    private void fileSaveAs() {
        fileMenuOpen = false;
        dialogMode = DIALOG_SAVE_AS;
        saveAsName.setLength(0);
        if (currentFile != null) {
            final String base = currentFile.endsWith(".iql")
                    ? currentFile.substring(0, currentFile.length() - 4) : currentFile;
            saveAsName.append(base);
        }
    }

    private void commitSaveAs() {
        final String name = saveAsName.toString().trim();
        if (name.isEmpty()) {
            status = "enter a file name";
            statusOk = false;
            return;
        }
        PacketDistributor.sendToServer(new SaveIqlFilePayload(host, name, editor().value()));
        currentFile = name + ".iql";
        dialogMode = DIALOG_NONE;
    }

    private void fileOpen() {
        fileMenuOpen = false;
        dialogMode = DIALOG_OPEN;
        pickerScroll = 0;
        PacketDistributor.sendToServer(new RequestIqlFileListPayload(host));
    }

    private void openPickerSelect(final String fileName) {
        PacketDistributor.sendToServer(new OpenIqlFilePayload(host, fileName));
    }

    private int fileLabelW() {
        return JscOsTheme.widthS(font, "File") + 8;
    }

    private int hoveredFileItem(final int mlx, final int mly) {
        if (!fileMenuOpen) {
            return -1;
        }
        if (mlx < NmsLayout.FILE_DROP_X || mlx >= NmsLayout.FILE_DROP_X + NmsLayout.FILE_DROP_W) {
            return -1;
        }
        final int row = (mly - NmsLayout.FILE_DROP_Y - 1) / NmsLayout.FILE_DROP_ITEM_H;
        return (row >= 0 && row < FILE_ITEMS.length) ? row : -1;
    }

    // --- rendering --------------------------------------------------------------------------------

    @Override
    public void applySkin(final dev.jsc.jscomputronics.module.computing.client.os.OsSkin skin) {
        this.osSkin = skin;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        this.font = font;
        this.originX = x;
        this.originY = y;
        this.viewW = width;
        this.viewH = height;
        // Keep the explorer/editor splits within the live window, so a shrunk window never inverts a pane.
        this.explorerW = Math.max(MIN_EXPLORER_W, Math.min(maxExplorerW(), explorerW));
        this.editorH = Math.max(MIN_EDITOR_H, Math.min(maxEditorH(), editorH));
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.theme = NmsThemes.forOs(osSkin);
        editor().tick();
        ensureSchema();

        final int mlx = mouseX - x;
        final int mly = mouseY - y;
        JscOsTheme.bind(theme);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        try {
            drawBackground(g, mlx, mly);
            drawForeground(g, mlx, mly);
        } finally {
            g.pose().popPose();
            JscOsTheme.unbind();
        }
    }

    private void drawBackground(final GuiGraphics g, final int mlx, final int mly) {
        final int w = viewW;
        JscOsTheme.window(g, 0, 0, w, viewH);

        // No internal title bar: the DesktopWindow already draws the program's title bar (TITLE_H = 0). The
        // menu bar is the top strip.
        strip(g, NmsLayout.MENU_Y, NmsLayout.MENU_H);
        strip(g, NmsLayout.TOOLBAR_Y, NmsLayout.TOOLBAR_H);
        final boolean execHover = inRect(mlx, mly, EXEC_X, NmsLayout.TOOLBAR_Y + 2, EXEC_W, 10);
        JscOsTheme.button(g, EXEC_X, NmsLayout.TOOLBAR_Y + 2, EXEC_W, 10, execHover);
        playTri(g, EXEC_X + 5, NmsLayout.TOOLBAR_Y + 4);

        g.fill(0, NmsLayout.BODY_Y, explorerW, statusY(), JscOsTheme.panel());
        g.fill(0, NmsLayout.BODY_Y, explorerW, NmsLayout.BODY_Y + NmsLayout.EX_HEAD_H, JscOsTheme.rail());

        g.fill(rightX(), NmsLayout.BODY_Y, w, NmsLayout.BODY_Y + NmsLayout.TABS_H, JscOsTheme.rail());
        final int tabW = Math.min(TAB_W, rightW());
        g.fill(rightX(), NmsLayout.BODY_Y, rightX() + tabW, NmsLayout.BODY_Y + NmsLayout.TABS_H, JscOsTheme.tabOn());
        g.fill(rightX(), NmsLayout.BODY_Y, rightX() + tabW, NmsLayout.BODY_Y + 1, JscOsTheme.accent());
        g.fill(rightX(), NmsLayout.EDITOR_Y, w, hsplitY(), JscOsTheme.panel());
        g.fill(rightX(), NmsLayout.EDITOR_Y, rightX() + GUTTER_W, hsplitY(), JscOsTheme.rail());
        g.fill(rightX(), resTabsY(), w, resTabsY() + NmsLayout.RES_TABS_H, JscOsTheme.rail());
        g.fill(rightX(), gridY(), w, statusY(), JscOsTheme.panel());
        g.fill(rightX(), gridY(), w, gridY() + 9, JscOsTheme.rail());

        splitV(g, vsplitX(), NmsLayout.BODY_Y, bodyH(), dragging == DRAG_V || overVSplitLocal(mlx, mly));
        splitH(g, rightX(), hsplitY(), rightW(), dragging == DRAG_H || overHSplitLocal(mlx, mly));

        g.fill(0, statusY(), w, viewH, JscOsTheme.accent());

        if (fileMenuOpen) {
            final int dx = NmsLayout.FILE_DROP_X;
            final int dy = NmsLayout.FILE_DROP_Y;
            g.fill(dx, dy, dx + NmsLayout.FILE_DROP_W, dy + NmsLayout.FILE_DROP_H, JscOsTheme.panel());
            frameBox(g, dx, dy, NmsLayout.FILE_DROP_W, NmsLayout.FILE_DROP_H);
            final int hovered = hoveredFileItem(mlx, mly);
            for (int i = 0; i < FILE_ITEMS.length; i++) {
                if (i == hovered) {
                    g.fill(dx + 1, dy + 1 + i * NmsLayout.FILE_DROP_ITEM_H,
                            dx + NmsLayout.FILE_DROP_W - 1,
                            dy + 1 + (i + 1) * NmsLayout.FILE_DROP_ITEM_H, JscOsTheme.hover());
                }
            }
        }

        if (dialogMode == DIALOG_SAVE_AS) {
            final int dw = NmsLayout.DIALOG_W;
            final int dh = NmsLayout.DIALOG_H_SAVE;
            final int dx = NmsLayout.DIALOG_X;
            final int dy = NmsLayout.DIALOG_SAVE_Y;
            g.fill(dx, dy, dx + dw, dy + dh, JscOsTheme.panel());
            frameBox(g, dx, dy, dw, dh);
            g.fill(dx, dy, dx + dw, dy + NmsLayout.DIALOG_HEADER_H, JscOsTheme.rail());
            // Inline name field.
            final int bx = NmsLayout.SAVE_EDIT_X;
            final int by = NmsLayout.SAVE_EDIT_Y;
            g.fill(bx, by, bx + NmsLayout.SAVE_EDIT_W, by + NmsLayout.SAVE_EDIT_H, JscOsTheme.panel());
            frameBox(g, bx, by, NmsLayout.SAVE_EDIT_W, NmsLayout.SAVE_EDIT_H);
        } else if (dialogMode == DIALOG_OPEN) {
            final int dw = NmsLayout.DIALOG_W;
            final int dh = NmsLayout.DIALOG_H_OPEN;
            final int dx = NmsLayout.DIALOG_X;
            final int dy = NmsLayout.DIALOG_OPEN_Y;
            g.fill(dx, dy, dx + dw, dy + dh, JscOsTheme.panel());
            frameBox(g, dx, dy, dw, dh);
            g.fill(dx, dy, dx + dw, dy + NmsLayout.DIALOG_HEADER_H, JscOsTheme.rail());
            final int listTop = dy + NmsLayout.PICKER_LIST_OFFSET_Y;
            final int clamped = Math.max(0, Math.min(pickerScroll,
                    Math.max(0, iqlFiles.size() - NmsLayout.PICKER_VISIBLE)));
            for (int i = 0; i < NmsLayout.PICKER_VISIBLE && clamped + i < iqlFiles.size(); i++) {
                final int ry = listTop + i * NmsLayout.PICKER_ROW_H;
                final boolean rowHover = mlx >= dx + 1 && mlx < dx + dw - 1
                        && mly >= ry && mly < ry + NmsLayout.PICKER_ROW_H;
                if (rowHover) {
                    g.fill(dx + 1, ry, dx + dw - 1, ry + NmsLayout.PICKER_ROW_H, JscOsTheme.hover());
                }
            }
            final int cancelY = dy + NmsLayout.CANCEL_BTN_REL_Y;
            g.fill(dx + NmsLayout.CANCEL_BTN_REL_X, cancelY,
                    dx + NmsLayout.CANCEL_BTN_REL_X + NmsLayout.CANCEL_BTN_W,
                    cancelY + NmsLayout.CANCEL_BTN_H, JscOsTheme.rail());
        }
    }

    private void strip(final GuiGraphics g, final int yTop, final int h) {
        g.fill(0, yTop, viewW, yTop + h, JscOsTheme.rail());
        g.fill(0, yTop + h - 1, viewW, yTop + h, JscOsTheme.line());
    }

    private static void frameBox(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + 1, JscOsTheme.line());
        g.fill(x, y, x + 1, y + h, JscOsTheme.line());
        g.fill(x + w - 1, y, x + w, y + h, JscOsTheme.line());
        g.fill(x, y + h - 1, x + w, y + h, JscOsTheme.line());
    }

    private void playTri(final GuiGraphics g, final int x, final int y) {
        for (int i = 0; i < 4; i++) {
            g.fill(x + i, y + i, x + i + 1, y + 7 - i, JscOsTheme.green());
        }
    }

    private void splitV(final GuiGraphics g, final int x, final int yTop, final int h, final boolean activeSplit) {
        g.fill(x, yTop, x + NmsLayout.VSPLIT_W, yTop + h, activeSplit ? JscOsTheme.hover() : JscOsTheme.rail());
        g.fill(x, yTop, x + 1, yTop + h, JscOsTheme.line());
        g.fill(x + NmsLayout.VSPLIT_W - 1, yTop, x + NmsLayout.VSPLIT_W, yTop + h, JscOsTheme.line());
        final int gripColor = activeSplit ? JscOsTheme.accent() : JscOsTheme.dim();
        final int gy = yTop + h / 2 - 6;
        for (int i = 0; i < 4; i++) {
            g.fill(x + 1, gy + i * 4, x + 2, gy + i * 4 + 1, gripColor);
        }
    }

    private void splitH(final GuiGraphics g, final int x, final int yTop, final int w, final boolean activeSplit) {
        g.fill(x, yTop, x + w, yTop + NmsLayout.HSPLIT_H, activeSplit ? JscOsTheme.hover() : JscOsTheme.rail());
        g.fill(x, yTop, x + w, yTop + 1, JscOsTheme.line());
        g.fill(x, yTop + NmsLayout.HSPLIT_H - 1, x + w, yTop + NmsLayout.HSPLIT_H, JscOsTheme.line());
        final int gripColor = activeSplit ? JscOsTheme.accent() : JscOsTheme.dim();
        final int gx = x + w / 2 - 6;
        for (int i = 0; i < 4; i++) {
            g.fill(gx + i * 4, yTop + 2, gx + i * 4 + 1, yTop + 3, gripColor);
        }
    }

    private void drawForeground(final GuiGraphics g, final int mlx, final int mly) {
        final int w = viewW;
        int mx = 6;
        fileMenuLabelX = mx;
        for (final String item : MENU) {
            final int color = (item.equals("File") && fileMenuOpen) ? JscOsTheme.accent2() : JscOsTheme.text();
            JscOsTheme.textS(g, font, item, mx, NmsLayout.MENU_Y + 2, color);
            mx += JscOsTheme.widthS(font, item) + 8;
        }
        JscOsTheme.textS(g, font, "Execute", EXEC_X + 12, NmsLayout.TOOLBAR_Y + 5, JscOsTheme.text());
        JscOsTheme.textS(g, font, "network: " + networkLabel, EXEC_X + EXEC_W + 8, NmsLayout.TOOLBAR_Y + 5,
                JscOsTheme.accent2());
        final String engineState = liveEngine.state();
        final int engineColor = "running".equals(engineState) ? JscOsTheme.green()
                : "stopped".equals(engineState) ? JscOsTheme.amber() : JscOsTheme.red();
        JscOsTheme.textSRight(g, font, "engine: " + engineState, w - 6, NmsLayout.TOOLBAR_Y + 5, engineColor);

        JscOsTheme.textS(g, font, fit("OBJECT EXPLORER", explorerW - 8), 6, NmsLayout.BODY_Y + 2, JscOsTheme.dim());
        drawTree(g, mlx, mly);

        final int tabW = Math.min(TAB_W, rightW());
        final String tabLabel = currentFile != null ? currentFile : networkLabel + ".query 1";
        JscOsTheme.textS(g, font, fit(tabLabel, tabW - 8), rightX() + 6, NmsLayout.BODY_Y + 2, JscOsTheme.tabLabelOn());
        drawEditor(g);

        JscOsTheme.textS(g, font, "Results", rightX() + 6, resTabsY() + 2,
                showMessages ? JscOsTheme.dim() : JscOsTheme.text());
        JscOsTheme.textS(g, font, "Messages", rightX() + 44, resTabsY() + 2,
                showMessages ? JscOsTheme.text() : JscOsTheme.dim());
        if (showMessages) {
            drawMessages(g);
        } else {
            drawGrid(g);
        }

        JscOsTheme.textS(g, font, status, 6, statusY() + 5, statusOk ? 0xFFFFFFFF : 0xFFFFD2D2);
        JscOsTheme.textSRight(g, font, "F5 to run", w - 6, statusY() + 5, 0xFFE0ECF8);

        if (fileMenuOpen) {
            for (int i = 0; i < FILE_ITEMS.length; i++) {
                JscOsTheme.textS(g, font, FILE_ITEMS[i], NmsLayout.FILE_DROP_X + 4,
                        NmsLayout.FILE_DROP_Y + 1 + i * NmsLayout.FILE_DROP_ITEM_H + 1, JscOsTheme.text());
            }
        }

        if (dialogMode == DIALOG_SAVE_AS) {
            final int dxL = NmsLayout.DIALOG_X;
            final int dyL = NmsLayout.DIALOG_SAVE_Y;
            JscOsTheme.textS(g, font, "Save As", dxL + 4, dyL + 2, JscOsTheme.dim());
            JscOsTheme.textS(g, font, "File name:", dxL + 4, dyL + 14, JscOsTheme.text());
            JscOsTheme.textS(g, font, fit(saveAsName + "_", NmsLayout.SAVE_EDIT_W - 6),
                    NmsLayout.SAVE_EDIT_X + 3, NmsLayout.SAVE_EDIT_Y + 3, JscOsTheme.text());
            JscOsTheme.textS(g, font, "Enter = save   Esc = cancel", dxL + 4,
                    dyL + NmsLayout.DIALOG_H_SAVE - 10, JscOsTheme.dim());
        } else if (dialogMode == DIALOG_OPEN) {
            final int dxL = NmsLayout.DIALOG_X;
            final int dyL = NmsLayout.DIALOG_OPEN_Y;
            JscOsTheme.textS(g, font, "Open IQL File", dxL + 4, dyL + 2, JscOsTheme.dim());
            final int listTop = dyL + NmsLayout.PICKER_LIST_OFFSET_Y;
            if (iqlFiles.isEmpty()) {
                JscOsTheme.textS(g, font, "no .iql files on disk", dxL + 4, listTop + 2, JscOsTheme.dim());
            } else {
                final int clamped = Math.max(0, Math.min(pickerScroll,
                        Math.max(0, iqlFiles.size() - NmsLayout.PICKER_VISIBLE)));
                for (int i = 0; i < NmsLayout.PICKER_VISIBLE && clamped + i < iqlFiles.size(); i++) {
                    JscOsTheme.textS(g, font, fit(iqlFiles.get(clamped + i), NmsLayout.DIALOG_W - 8),
                            dxL + 4, listTop + i * NmsLayout.PICKER_ROW_H + 2, JscOsTheme.text());
                }
            }
            JscOsTheme.textS(g, font, "Cancel", NmsLayout.DIALOG_X + NmsLayout.CANCEL_BTN_REL_X,
                    NmsLayout.DIALOG_OPEN_Y + NmsLayout.CANCEL_BTN_REL_Y, JscOsTheme.text());
        }
    }

    private void drawEditor(final GuiGraphics g) {
        final int rows = Math.max(1, (editorH - 6) / EDITOR_LINE_H);
        final int yTop = NmsLayout.EDITOR_Y + 4;
        for (int i = 0; i < rows && editor().scrollLine() + i < editor().lineCount(); i++) {
            JscOsTheme.textSRight(g, font, String.valueOf(editor().scrollLine() + i + 1),
                    rightX() + GUTTER_W - 2, yTop + i * EDITOR_LINE_H + 2, JscOsTheme.dim());
        }
        editor().render(g, rightX() + GUTTER_W + 4, yTop, EDITOR_LINE_H, rows);
    }

    private void drawTree(final GuiGraphics g, final int mlx, final int mly) {
        final int start = treeStart();
        final int hoverRow = treeRowAtLocal(mlx, mly);
        for (int i = 0; i < treeMaxRows() && start + i < visible.size(); i++) {
            final VisibleNode vn = visible.get(start + i);
            final Node node = vn.node();
            final int ry = NmsLayout.EX_TREE_Y + i * TREE_PITCH;
            if (start + i == hoverRow) {
                g.fill(1, ry - 1, explorerW - 1, ry + TREE_PITCH - 1, JscOsTheme.hover());
            }
            final int indent = 4 + vn.depth() * 7;
            if (node.expandable()) {
                JscOsTheme.textS(g, font, node.expanded ? "v" : ">", indent - 4, ry, JscOsTheme.dim());
            }
            drawIcon(g, node.icon, indent + 4, ry);
            String label = node.label;
            if (node.expandable() && node.inject == null && (!node.expanded || node.liveCount)) {
                label = label + " (" + node.children.size() + ")";
            }
            final int labelX = indent + 13;
            JscOsTheme.textS(g, font, fit(label, explorerW - labelX - 2), labelX, ry, JscOsTheme.text());
        }
    }

    private String fit(final String text, final int maxWidth) {
        String t = text;
        while (!t.isEmpty() && JscOsTheme.widthS(font, t) > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private void drawIcon(final GuiGraphics g, final int type, final int x, final int y) {
        final int s = 7;
        switch (type) {
            case DB -> g.fill(x, y, x + s, y + s, JscOsTheme.accent2());
            case FOLDER -> g.fill(x, y, x + s, y + s, JscOsTheme.amber());
            case TABLE -> {
                g.fill(x, y, x + s, y + s, JscOsTheme.line());
                g.fill(x + 1, y + 1, x + s - 1, y + s - 1, JscOsTheme.panel());
                g.fill(x + 1, y + 1, x + s - 1, y + 3, JscOsTheme.amber());
            }
            case COLUMN -> g.fill(x + 1, y + 1, x + s - 1, y + s - 1, JscOsTheme.dim());
            case VIEW -> g.fill(x, y, x + s, y + s, ICON_VIEW);
            case PROC -> g.fill(x, y, x + s, y + s, ICON_PROC);
            case JOB -> g.fill(x, y, x + s, y + s, ICON_JOB);
            case SERVER -> {
                g.fill(x, y, x + s, y + s, JscOsTheme.dim());
                g.fill(x + 1, y + 1, x + s - 1, y + 2, JscOsTheme.green());
                g.fill(x + 1, y + 3, x + s - 1, y + 4, JscOsTheme.green());
            }
            default -> { /* no icon */ }
        }
    }

    private void drawGrid(final GuiGraphics g) {
        final int left = rightX() + 6;
        final int qtyRight = viewW - 6;
        final int nameMax = qtyRight - left - 30;
        JscOsTheme.textS(g, font, "item", left, gridY() + 1, JscOsTheme.dim());
        JscOsTheme.textSRight(g, font, "qty", qtyRight, gridY() + 1, JscOsTheme.dim());
        final int top = gridY() + 10;
        final int bottom = statusY() - 1;
        final int visibleRows = (bottom - top) / NmsLayout.ROW_H;
        final int clamped = Math.max(0, Math.min(gridScroll, Math.max(0, rows.size() - visibleRows)));
        for (int i = 0; i < visibleRows && clamped + i < rows.size(); i++) {
            final IqlResultPayload.Row row = rows.get(clamped + i);
            final int ry = top + i * NmsLayout.ROW_H;
            JscOsTheme.textS(g, font, fit(row.label(), nameMax), left, ry, JscOsTheme.text());
            JscOsTheme.textSRight(g, font, JscOsTheme.fmt(row.quantity()), qtyRight, ry, JscOsTheme.accent2());
        }
        if (rows.isEmpty()) {
            JscOsTheme.textS(g, font, "no result set", left, top, JscOsTheme.dim());
        }
    }

    private void drawMessages(final GuiGraphics g) {
        final int left = rightX() + 6;
        JscOsTheme.textS(g, font, "message", left, gridY() + 1, JscOsTheme.dim());
        final int top = gridY() + 10;
        final int bottom = statusY() - 1;
        final int visibleRows = Math.max(1, (bottom - top) / NmsLayout.ROW_H);
        final int start = Math.max(0, messages.size() - visibleRows);
        for (int i = 0; start + i < messages.size() && i < visibleRows; i++) {
            final MsgLine line = messages.get(start + i);
            JscOsTheme.textS(g, font, fit(line.text(), viewW - left - 6), left, top + i * NmsLayout.ROW_H,
                    line.ok() ? JscOsTheme.text() : 0xFFCC2222);
        }
        if (messages.isEmpty()) {
            JscOsTheme.textS(g, font, "no messages yet", left, top, JscOsTheme.dim());
        }
    }

    // --- input ------------------------------------------------------------------------------------

    private boolean inRect(final int mlx, final int mly, final int rx, final int ry, final int w, final int h) {
        return mlx >= rx && mlx < rx + w && mly >= ry && mly < ry + h;
    }

    private boolean overVSplitLocal(final double mlx, final double mly) {
        return Math.abs(mlx - vsplitX() - NmsLayout.VSPLIT_W / 2.0) <= 3
                && mly >= NmsLayout.BODY_Y && mly < statusY();
    }

    private boolean overHSplitLocal(final double mlx, final double mly) {
        return Math.abs(mly - hsplitY() - NmsLayout.HSPLIT_H / 2.0) <= 3
                && mlx >= rightX() && mlx < viewW;
    }

    private int treeRowAtLocal(final double mlx, final double mly) {
        if (mlx < 0 || mlx >= explorerW || mly < NmsLayout.EX_TREE_Y || mly >= statusY()) {
            return -1;
        }
        final int idx = treeStart() + ((int) mly - NmsLayout.EX_TREE_Y) / TREE_PITCH;
        return idx < visible.size() ? idx : -1;
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (button != 0) {
            return;
        }
        final int mlx = (int) mouseX - originX;
        final int mly = (int) mouseY - originY;

        if (dialogMode == DIALOG_SAVE_AS) {
            dialogMode = DIALOG_NONE;
            return;
        }
        if (dialogMode == DIALOG_OPEN) {
            final int dxL = NmsLayout.DIALOG_X;
            final int dyL = NmsLayout.DIALOG_OPEN_Y;
            final int dw = NmsLayout.DIALOG_W;
            final int dh = NmsLayout.DIALOG_H_OPEN;
            if (mlx >= dxL + NmsLayout.CANCEL_BTN_REL_X
                    && mlx < dxL + NmsLayout.CANCEL_BTN_REL_X + NmsLayout.CANCEL_BTN_W
                    && mly >= dyL + NmsLayout.CANCEL_BTN_REL_Y
                    && mly < dyL + NmsLayout.CANCEL_BTN_REL_Y + NmsLayout.CANCEL_BTN_H) {
                dialogMode = DIALOG_NONE;
                return;
            }
            if (mlx >= dxL + 1 && mlx < dxL + dw - 1
                    && mly >= dyL + NmsLayout.PICKER_LIST_OFFSET_Y
                    && mly < dyL + dh - NmsLayout.CANCEL_BTN_H - 2
                    && !iqlFiles.isEmpty()) {
                final int clamped = Math.max(0, Math.min(pickerScroll,
                        Math.max(0, iqlFiles.size() - NmsLayout.PICKER_VISIBLE)));
                final int rowIdx = (mly - dyL - NmsLayout.PICKER_LIST_OFFSET_Y) / NmsLayout.PICKER_ROW_H;
                final int fileIdx = clamped + rowIdx;
                if (fileIdx >= 0 && fileIdx < iqlFiles.size()) {
                    openPickerSelect(iqlFiles.get(fileIdx));
                    return;
                }
            }
            if (mlx < dxL || mlx >= dxL + dw || mly < dyL || mly >= dyL + dh) {
                dialogMode = DIALOG_NONE;
            }
            return;
        }

        if (fileMenuOpen) {
            final int item = hoveredFileItem(mlx, mly);
            if (item >= 0) {
                switch (item) {
                    case 0 -> fileNew();
                    case 1 -> fileSave();
                    case 2 -> fileSaveAs();
                    case 3 -> fileOpen();
                    default -> fileMenuOpen = false;
                }
            } else {
                fileMenuOpen = false;
            }
            return;
        }

        if (mly >= NmsLayout.MENU_Y && mly < NmsLayout.MENU_Y + NmsLayout.MENU_H
                && mlx >= fileMenuLabelX && mlx < fileMenuLabelX + fileLabelW()) {
            fileMenuOpen = !fileMenuOpen;
            return;
        }

        if (overVSplitLocal(mlx, mly)) {
            dragging = DRAG_V;
            return;
        }
        if (overHSplitLocal(mlx, mly)) {
            dragging = DRAG_H;
            return;
        }
        if (inRect(mlx, mly, EXEC_X, NmsLayout.TOOLBAR_Y + 2, EXEC_W, 10)) {
            execute();
            return;
        }
        if (mly >= resTabsY() && mly < resTabsY() + NmsLayout.RES_TABS_H) {
            if (mlx >= rightX() + 4 && mlx < rightX() + 42) {
                showMessages = false;
                return;
            }
            if (mlx >= rightX() + 42 && mlx < rightX() + 92) {
                showMessages = true;
                return;
            }
        }
        final int row = treeRowAtLocal(mlx, mly);
        if (row >= 0) {
            final VisibleNode vn = visible.get(row);
            final Node node = vn.node();
            final int indent = 4 + vn.depth() * 7;
            final boolean onTwisty = mlx < indent + 12;
            if (node.expandable() && (onTwisty || node.inject == null)) {
                node.expanded = !node.expanded;
                rebuildVisible();
            } else if (node.inject != null) {
                editor().insert(node.inject + " ");
            }
        }
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        final int mlx = (int) mouseX - originX;
        final int mly = (int) mouseY - originY;
        if (dragging == DRAG_V) {
            explorerW = Math.max(MIN_EXPLORER_W, Math.min(maxExplorerW(), mlx));
        } else if (dragging == DRAG_H) {
            editorH = Math.max(MIN_EDITOR_H, Math.min(maxEditorH(), mly - NmsLayout.EDITOR_Y));
        }
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        dragging = DRAG_NONE;
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        final double mlx = lastMouseX - originX;
        final double mly = lastMouseY - originY;
        final int dir = delta > 0 ? 1 : -1;
        if (dialogMode == DIALOG_OPEN && !iqlFiles.isEmpty()) {
            final int max = Math.max(0, iqlFiles.size() - NmsLayout.PICKER_VISIBLE);
            pickerScroll = Math.max(0, Math.min(max, pickerScroll - dir));
            return true;
        }
        if (mlx < explorerW) {
            final int max = Math.max(0, visible.size() - treeMaxRows());
            treeScroll = Math.max(0, Math.min(max, treeScroll - dir));
            return true;
        }
        if (mly >= gridY() && !rows.isEmpty()) {
            gridScroll = Math.max(0, gridScroll - dir);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        if (dialogMode == DIALOG_SAVE_AS) {
            if (key == 256) {
                dialogMode = DIALOG_NONE;
            } else if (key == 257 || key == 335) {
                commitSaveAs();
            } else if (key == 259 && saveAsName.length() > 0) {
                saveAsName.deleteCharAt(saveAsName.length() - 1);
            }
            return true;
        }
        if (dialogMode == DIALOG_OPEN) {
            if (key == 256) {
                dialogMode = DIALOG_NONE;
            }
            return true;
        }
        if (fileMenuOpen && key == 256) {
            fileMenuOpen = false;
            return true;
        }
        final boolean ctrl = (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
        final boolean shift = (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        if (ctrl) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
                if (shift) {
                    fileSaveAs();
                } else {
                    fileSave();
                }
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_N) {
                fileNew();
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_O) {
                fileOpen();
                return true;
            }
        }
        boolean handled = false;
        switch (ProgramKeybinds.route(key)) {
            case RUN -> {
                execute();
                handled = true;
            }
            case EDIT -> handled = editor().keyPressed(key);
            default -> {
            }
        }
        return handled;
    }

    @Override
    public boolean charTyped(final char c) {
        if (dialogMode == DIALOG_SAVE_AS) {
            if (c >= ' ' && c != 127 && c != '/' && c != '\\' && saveAsName.length() < SaveIqlFilePayload.MAX_NAME_LEN) {
                saveAsName.append(c);
            }
            return true;
        }
        if (dialogMode == DIALOG_OPEN || fileMenuOpen) {
            return true;
        }
        editor().insert(c);
        return true;
    }

}
