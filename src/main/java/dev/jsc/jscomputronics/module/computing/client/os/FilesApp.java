/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.operation.payload.DeleteFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DiskFilesPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.MkdirPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.MoveFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RenameFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestDiskFilesPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestFileContentPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.SaveFilePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A file explorer for the system disk, shown as a desktop window in the Windows-Explorer mould: an
 * address bar, an icon-per-type list of folders and files with a selection highlight, and a status
 * bar. It lists the real directories and files on the disk plus the read-only {@code .dat} storage
 * projection. Left-click selects a row; double-click opens it (a folder navigates, an editable file
 * opens in the Editor). Right-click opens a context menu (Open, Rename, Delete, New File, New
 * Folder, Refresh). New items are created in the directory currently shown.
 */
public final class FilesApp implements DesktopApp {

    private static final int ADDR_H = 13;
    private static final int STATUS_H = 11;
    private static final int ROW_H = 11;
    private static final int ICON_W = 12;
    private static final long DOUBLE_CLICK_MS = 300L;

    // Per-OS skin colours, set in applySkin() from the host OS so each Panes version looks distinct
    // (95 grey, XP white/blue, 11 dark).
    private int panel = 0xFFFFFFFF;
    private int edge = 0xFF6E7686;
    private int text = 0xFF1A2230;
    private int textRo = 0xFF707888;
    private int sizeCol = 0xFF60687A;
    private int selBg = 0xFF000080;
    private int selText = 0xFFFFFFFF;
    private int sizeSel = 0xFFCDD6FF;
    private int ctxBg = 0xFFE8E8EC;
    private int ctxHover = 0xFF000080;
    private int dropEdge = 0xFF2E8B2E;
    private int headerBg = 0xFFE6E8EF;
    private String os = "panes_95";

    private final BlockPos host;
    // The monitor the desktop is shown on, needed to authenticate the sanctioned .dat-to-medium item
    // transfer (the server validates the player is within reach of this monitor). May be null when the
    // explorer is opened outside a desktop context.
    @org.jetbrains.annotations.Nullable
    private final BlockPos monitorPos;
    private String dir = "";
    private List<Row> rows = new ArrayList<>();
    private int selected = -1;
    private int scroll;

    private int lastClickRow = -1;
    private long lastClickAt;

    private boolean contextOpen;
    private int ctxX;
    private int ctxY;
    private int ctxRow = -1;

    private int renaming = -1;
    private final StringBuilder renameBuf = new StringBuilder();
    private String renameExt = "";

    // Drag-and-drop state: the row picked up on press, whether a drag is in progress, and the
    // current cursor position (desktop-local) for the drag ghost.
    private int dragRow = -1;
    private boolean dragging;
    private double dragMx;
    private double dragMy;

    // After creating a New File/New Folder, the next listing enters rename on the matching row.
    private String pendingRename;

    // Left drive tree: the mountable volumes, plus inline-relabel state for renaming a disk/medium.
    private static final int TREE_W = 58;
    private List<DiskFilesPayload.WireVolume> volumes = new ArrayList<>();
    private int volRenaming = -1;
    private final StringBuilder volRenameBuf = new StringBuilder();
    private boolean volContextOpen;
    private int volCtxIndex = -1;
    private int volCtxX;
    private int volCtxY;

    private static final String[] CONTEXT_ITEMS =
            {"Open", "Rename", "Delete", "New File", "New Folder", "Refresh"};
    private static final int CTX_W = 88;
    private static final int CTX_ITEM_H = 11;

    private static FilesApp active;

    /** A visible row: a navigation shortcut, a real folder, or a real/projected file. */
    private enum Kind { UP, STORAGE, DIR, FILE }

    private record Row(Kind kind, String name, String detail, IconType icon,
                       @org.jetbrains.annotations.Nullable DiskFilesPayload.WireFile file) {
    }

    private enum IconType { UP, FOLDER, HOME, IQL, DOC, DAT }

    public FilesApp(final BlockPos host) {
        this(host, "panes_95", "", null);
    }

    /** Opens the explorer skinned for {@code os} (panes_95 / panes_xp / panes_11). */
    public FilesApp(final BlockPos host, final String os) {
        this(host, os, "", null);
    }

    /** Opens the explorer skinned for {@code os}, already navigated to {@code initialDir}. */
    public FilesApp(final BlockPos host, final String os, final String initialDir) {
        this(host, os, initialDir, null);
    }

    /**
     * Opens the explorer skinned for {@code os} at {@code initialDir}, aware of the {@code monitorPos} the
     * desktop is shown on so it can authenticate the sanctioned {@code .dat}-to-medium item transfer.
     */
    public FilesApp(final BlockPos host, final String os, final String initialDir,
                    @org.jetbrains.annotations.Nullable final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
        applySkin(os);
        active = this;
        request(initialDir);
    }

    @Override
    public void applySkin(final OsSkin skin) {
        applySkin(skin.osPath());
    }

    /** Picks the colour palette for the host Panes version. */
    private void applySkin(final String os) {
        this.os = os;
        switch (os) {
            case "panes_xp" -> {
                panel = 0xFFFFFFFF; edge = 0xFF7BA0D0; text = 0xFF1A2230; textRo = 0xFF7A8290;
                sizeCol = 0xFF5E6B7E; selBg = 0xFF2F6AC6; selText = 0xFFFFFFFF; sizeSel = 0xFFD6E4FF;
                ctxBg = 0xFFEFF4FB; ctxHover = 0xFF2F6AC6; headerBg = 0xFFD6E5F7;
            }
            case "panes_11" -> {
                // Light, to match the Panes 11 window chrome (the OS skin is light, not dark).
                panel = 0xFFFAFAFE; edge = 0xFFE3E5EE; text = 0xFF202434; textRo = 0xFF6B7488;
                sizeCol = 0xFF6B7488; selBg = 0xFF3A6AE0; selText = 0xFFFFFFFF; sizeSel = 0xFFD6E0F8;
                ctxBg = 0xFFFFFFFF; ctxHover = 0xFFE7EEFC; headerBg = 0xFFF0F1F7;
            }
            default -> {
                panel = 0xFFC0C0C0; edge = 0xFF808080; text = 0xFF000000; textRo = 0xFF505050;
                sizeCol = 0xFF303030; selBg = 0xFF000080; selText = 0xFFFFFFFF; sizeSel = 0xFFC9D2FF;
                ctxBg = 0xFFC0C0C0; ctxHover = 0xFF000080; headerBg = 0xFFC0C0C0;
            }
        }
    }

    /** Routes a server listing reply to the open Files window. */
    public static void accept(final DiskFilesPayload payload) {
        if (active != null) {
            active.dir = payload.dir();
            active.volumes = payload.volumes();
            active.rebuild(payload.files());
        }
    }

    /** Re-requests this explorer's current listing, so a file moved in from outside shows up at once. */
    public void refresh() {
        request(dir);
    }

    /** Whether a file or folder is currently being dragged out of this explorer. */
    public boolean isDragging() {
        return dragging && dragRow >= 0 && dragRow < rows.size() && rows.get(dragRow).file() != null;
    }

    /** The file or folder currently being dragged out of this explorer, or {@code null} when none. */
    @org.jetbrains.annotations.Nullable
    public DiskFilesPayload.WireFile draggedFile() {
        return isDragging() ? rows.get(dragRow).file() : null;
    }

    /** Ends an in-progress drag without acting on it (the host handled the cross-window drop instead). */
    public void cancelDrag() {
        dragging = false;
        dragRow = -1;
    }

    private void request(final String target) {
        this.dir = target;
        this.selected = -1;
        this.scroll = 0;
        PacketDistributor.sendToServer(new RequestDiskFilesPayload(host, target));
    }

    private void rebuild(final List<DiskFilesPayload.WireFile> files) {
        final List<Row> built = new ArrayList<>();
        if (dir.isEmpty()) {
            built.add(new Row(Kind.STORAGE, "Storage", "<DIR>", IconType.FOLDER, null));
        } else {
            built.add(new Row(Kind.UP, "[ .. ]", "Up", IconType.UP, null));
        }
        for (final DiskFilesPayload.WireFile f : files) {
            // The path already carries the name; show the bare last segment (no doubled extension).
            if (f.directory()) {
                built.add(new Row(Kind.DIR, driveOrBaseName(f.path()),
                        f.path().startsWith("media:") && f.path().indexOf('/') < 0 ? "<DRIVE>" : "<DIR>",
                        IconType.FOLDER, f));
            } else {
                built.add(new Row(Kind.FILE, baseName(f.path()),
                        f.weight() + " mB" + (f.readOnly() ? "  RO" : ""), iconFor(f.ext()), f));
            }
        }
        this.rows = built;
        if (selected >= built.size()) {
            selected = -1;
        }
        // Enter rename on a freshly created item, once it shows up in the listing.
        if (pendingRename != null) {
            for (int i = 0; i < rows.size(); i++) {
                final Row r = rows.get(i);
                if ((r.kind() == Kind.FILE || r.kind() == Kind.DIR) && r.name().equals(pendingRename)) {
                    selected = i;
                    startRenameAt(i);
                    break;
                }
            }
            pendingRename = null;
        }
    }

    @Override
    public String title() {
        return "Files";
    }

    /** The directory this explorer is currently showing (the system-disk root is {@code ""}). */
    public String currentDir() {
        return dir;
    }

    /**
     * The destination directory for a cross-window drop landing on this explorer window, or {@code null}
     * when the drop is not a valid target (the left drive tree, a removable-drive volume, or a folder shown
     * as a media drive). A drop onto a real subfolder targets that subfolder; a drop anywhere else in the
     * file list targets the folder currently open. A media volume is excluded here: a desktop file cannot be
     * dropped onto a removable drive through this path (that is the sanctioned medium-transfer flow only).
     */
    @org.jetbrains.annotations.Nullable
    public String crossWindowDropDir(final DesktopWindow window, final double mouseX, final double mouseY) {
        // The left drive tree is not a drop target for an icon dragged in from outside.
        final double lx = mouseX - (window.x() + 4);
        if (lx < TREE_W) {
            return null;
        }
        // The explorer must be browsing a real disk folder, never a removable medium.
        if (dir.startsWith("media:")) {
            return null;
        }
        final int row = rowIndexAt(window, mouseX, mouseY);
        if (row >= 0 && row < rows.size()) {
            final Row t = rows.get(row);
            if (t.kind() == Kind.DIR && t.file() != null && !t.file().path().startsWith("media:")) {
                return t.file().path();
            }
            if (t.kind() == Kind.UP) {
                return parentOf(dir);
            }
        }
        // Anywhere else in the file list drops into the folder currently open.
        return dir;
    }

    @Override
    public int defaultWidth() {
        return 250;
    }

    @Override
    public int defaultHeight() {
        return 176;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        // The front (last-rendered) explorer window owns the DiskFilesPayload routing, so two open
        // Files windows don't leave the back one as a stale target and a closed one stops receiving.
        active = this;
        // Header toolbar — a distinct design per Panes version (menu bar / Back button / breadcrumb).
        g.fill(x, y, x + width, y + ADDR_H, headerBg);
        final String winPath = displayPath(dir);
        switch (os) {
            case "panes_xp" -> {
                g.fill(x + 2, y + 2, x + 24, y + ADDR_H - 1, 0xFF7ED36A);
                g.drawString(font, "Back", x + 5, y + 3, 0xFFFFFFFF, false);
                g.drawString(font, winPath, x + 28, y + 3, text, false);
            }
            case "panes_11" -> g.drawString(font, crumbLabel(dir), x + 4, y + 3, text, false);
            case "panes_95" -> g.drawString(font, "File   Edit   View   Help", x + 3, y + 3, text, false);
            default -> g.drawString(font, "Address  " + winPath, x + 3, y + 3, text, false);
        }
        outlineColor(g, x, y, width, ADDR_H, edge);

        // Left drive tree (volumes) and the file list shifted right by the tree's width.
        final int listY = y + ADDR_H + 2;
        final int listH = height - ADDR_H - STATUS_H - 4;
        final int lx = x + TREE_W;
        final int lw = width - TREE_W;
        renderTree(g, font, x, listY, TREE_W, listH, mouseX, mouseY);

        // File list panel.
        g.fill(lx, listY, lx + lw, listY + listH, panel);
        outlineColor(g, lx, listY, lw, listH, edge);

        // While dragging, the folder (or "..") under the cursor is the drop target — highlight it.
        int dropTarget = -1;
        if (dragging && dragMx >= TREE_W) {
            final int idx = scroll + (int) Math.floor((dragMy - (listY + 1)) / (double) ROW_H);
            if (idx >= 0 && idx < rows.size() && idx != dragRow) {
                final Kind k = rows.get(idx).kind();
                if (k == Kind.DIR || k == Kind.UP) {
                    dropTarget = idx;
                }
            }
        }

        final int visible = Math.max(1, (listH - 2) / ROW_H);
        clampScroll(visible);
        int row = listY + 1;
        for (int i = scroll; i < rows.size() && (i - scroll) < visible; i++) {
            final Row r = rows.get(i);
            final boolean sel = i == selected;
            if (sel) {
                g.fill(lx + 1, row, lx + lw - 1, row + ROW_H, selBg);
            } else if (i != renaming && mouseX >= lx && mouseX < lx + lw
                    && mouseY >= row && mouseY < row + ROW_H) {
                // Hover feedback so the player sees which file the cursor is over.
                g.fill(lx + 1, row, lx + lw - 1, row + ROW_H, 0x22000000);
            }
            if (i == dropTarget) {
                outlineColor(g, lx + 1, row, lw - 2, ROW_H, dropEdge);
            }
            drawIcon(g, lx + 3, row + 1, r.icon());
            final boolean ro = r.file() != null && r.file().readOnly();
            final int nameColor = sel ? selText : (ro ? textRo : text);
            final int sizeColor = sel ? sizeSel : sizeCol;
            final String size = r.detail();
            final int sizeX = lx + lw - 4 - font.width(size);
            String name = i == renaming ? renameBuf + "_" + renameExt : r.name();
            final int maxNameW = sizeX - (lx + 4 + ICON_W + 3) - 2;
            while (name.length() > 2 && font.width(name) > maxNameW) {
                name = name.substring(0, name.length() - 1);
            }
            g.drawString(font, name, lx + 4 + ICON_W + 3, row + 2, nameColor, false);
            g.drawString(font, size, sizeX, row + 2, sizeColor, false);
            row += ROW_H;
        }
        long usedTotal = 0L;
        for (final Row r : rows) {
            if (r.kind() == Kind.FILE && r.file() != null) {
                usedTotal += r.file().weight();
            }
        }

        // Status bar (full width below the tree and list).
        final int sy = y + height - STATUS_H;
        final int objects = Math.max(0, rows.size() - 1);
        g.drawString(font, objects + " object(s)", x + 2, sy + 2, text, false);
        final String used = usedTotal + " mB used";
        g.drawString(font, used, x + width - font.width(used) - 2, sy + 2, sizeCol, false);

        // Right-click context menu, with a hover highlight on the item under the cursor.
        if (contextOpen) {
            final int mx = x + ctxX;
            final int my = y + ctxY;
            final int mh = CONTEXT_ITEMS.length * CTX_ITEM_H + 2;
            g.fill(mx - 1, my - 1, mx + CTX_W + 1, my + mh + 1, 0xFF000000);
            g.fill(mx, my, mx + CTX_W, my + mh, ctxBg);
            g.fill(mx, my, mx + CTX_W, my + 1, 0xFFFFFFFF);
            final int hover = mouseX >= mx && mouseX <= mx + CTX_W
                    ? (int) Math.floor((mouseY - (my + 1)) / (double) CTX_ITEM_H) : -1;
            int iy = my + 1;
            for (int k = 0; k < CONTEXT_ITEMS.length; k++) {
                if (k == hover) {
                    g.fill(mx + 1, iy, mx + CTX_W - 1, iy + CTX_ITEM_H, ctxHover);
                }
                g.drawString(font, CONTEXT_ITEMS[k], mx + 4, iy + 2, k == hover ? 0xFFFFFFFF : text, false);
                iy += CTX_ITEM_H;
            }
        }

        // Drag ghost: a small label trailing the cursor while a file or folder is being dragged.
        if (dragging && dragRow >= 0 && dragRow < rows.size()) {
            final String label = rows.get(dragRow).name();
            final int gw = font.width(label) + 6;
            final int gx = (int) dragMx + 6;
            final int gy = (int) dragMy + 2;
            g.fill(gx, gy, gx + gw, gy + 11, 0xD0303848);
            g.drawString(font, label, gx + 3, gy + 2, 0xFFFFFFFF, false);
        }

        // Volume context menu (Rename), drawn above everything.
        if (volContextOpen) {
            final int mx = x + volCtxX;
            final int my = y + volCtxY;
            final int mw = 60;
            g.fill(mx - 1, my - 1, mx + mw + 1, my + CTX_ITEM_H + 3, 0xFF000000);
            g.fill(mx, my, mx + mw, my + CTX_ITEM_H + 2, ctxBg);
            final boolean hov = mouseX >= mx && mouseX <= mx + mw
                    && mouseY >= my + 1 && mouseY <= my + 1 + CTX_ITEM_H;
            if (hov) {
                g.fill(mx + 1, my + 1, mx + mw - 1, my + 1 + CTX_ITEM_H, ctxHover);
            }
            g.drawString(font, "Rename", mx + 4, my + 3, hov ? 0xFFFFFFFF : text, false);
        }
    }

    /** Draws the left drive tree: the Home shortcut at the top then one row per mountable volume. */
    private void renderTree(final GuiGraphics g, final Font font, final int tx, final int ty,
                            final int tw, final int th, final int mouseX, final int mouseY) {
        g.fill(tx, ty, tx + tw, ty + th, headerBg);
        outlineColor(g, tx, ty, tw, th, edge);
        int vy = ty + 2;

        // Home — a permanent shortcut that always navigates to the system-disk root.
        if (vy + ROW_H <= ty + th) {
            final boolean homeActive = dir.isEmpty();
            if (homeActive) {
                g.fill(tx + 1, vy, tx + tw - 1, vy + ROW_H, selBg);
            }
            drawIcon(g, tx + 2, vy + 1, IconType.HOME);
            g.drawString(font, "Home", tx + 3 + ICON_W, vy + 2, homeActive ? selText : text, false);
            vy += ROW_H;
        }

        for (int i = 0; i < volumes.size() && vy + ROW_H <= ty + th; i++) {
            final DiskFilesPayload.WireVolume v = volumes.get(i);
            // The system-disk root volume (key="") is only highlighted when we are inside a subdirectory,
            // not at the root itself — the Home row already claims the root highlight.
            final boolean cur = v.key().isEmpty()
                    ? (!dir.isEmpty() && !dir.startsWith("media:"))
                    : isCurrentVolume(v.key());
            if (cur) {
                g.fill(tx + 1, vy, tx + tw - 1, vy + ROW_H, selBg);
            }
            drawIcon(g, tx + 2, vy + 1, IconType.FOLDER);
            String label = i == volRenaming ? volRenameBuf + "_" : v.label();
            final int maxW = tw - (ICON_W + 7);
            while (label.length() > 1 && font.width(label) > maxW) {
                label = label.substring(0, label.length() - 1);
            }
            g.drawString(font, label, tx + 3 + ICON_W, vy + 2, cur ? selText : text, false);
            vy += ROW_H;
        }
    }

    /** Whether {@code key} is the volume currently being browsed (system disk = any non-media path). */
    private boolean isCurrentVolume(final String key) {
        if (key.isEmpty()) {
            return !dir.startsWith("media:");
        }
        return dir.equals(key) || dir.startsWith(key + "/");
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        if (renaming >= 0) {
            commitRename();
        }
        if (volRenaming >= 0) {
            commitVolumeRename();
        }
        final int contentX = window.x() + 4;
        final int contentY = window.y() + 18;
        final double lx = mouseX - contentX;
        final double ly = mouseY - contentY;

        // An open context menu intercepts this click first.
        if (contextOpen) {
            final int item = contextItemAt(lx, ly);
            contextOpen = false;
            if (item >= 0) {
                runContext(item);
                return;
            }
        }

        // The volume context menu (Rename) intercepts next.
        if (volContextOpen) {
            final boolean onItem = lx >= volCtxX && lx <= volCtxX + 60
                    && ly >= volCtxY + 1 && ly <= volCtxY + 1 + CTX_ITEM_H;
            volContextOpen = false;
            if (onItem && volCtxIndex >= 0 && volCtxIndex < volumes.size()) {
                startVolumeRename(volCtxIndex);
                return;
            }
        }

        // A click in the left drive tree navigates to a volume or the Home root.
        final int treeItem = treeItemAt(lx, ly);
        if (treeItem >= 0) {
            if (button == 1 && treeItem > 0) {
                // Right-click on a real volume opens the rename context menu.
                volCtxIndex = treeItem - 1;
                volCtxX = (int) lx;
                volCtxY = (int) ly;
                volContextOpen = true;
            } else if (button != 1) {
                if (treeItem == 0) {
                    request(""); // Home — navigate to system-disk root
                } else {
                    request(volumes.get(treeItem - 1).key());
                }
            }
            return;
        }

        final int index = rowIndexAt(window, mouseX, mouseY);
        final boolean onRow = index >= 0;

        if (button == 1) {
            // Right-click: select the row under the cursor and open the context menu there.
            selected = onRow ? index : -1;
            ctxRow = onRow ? index : -1;
            ctxX = (int) lx;
            ctxY = (int) ly;
            contextOpen = true;
            return;
        }

        if (!onRow) {
            selected = -1;
            return;
        }
        final long now = System.currentTimeMillis();
        final boolean doubleClick = index == lastClickRow && (now - lastClickAt) < DOUBLE_CLICK_MS;
        lastClickRow = index;
        lastClickAt = now;
        selected = index;
        // Arm a potential drag of a real file or folder; the drag starts once the mouse moves.
        final Row r = rows.get(index);
        dragRow = (r.kind() == Kind.FILE || r.kind() == Kind.DIR) ? index : -1;
        dragging = false;
        if (doubleClick) {
            open(rows.get(index));
        }
    }

    /** Maps a desktop position to a row index in the file list, or {@code -1} (tree, above/below rows). */
    private int rowIndexAt(final DesktopWindow window, final double mouseX, final double mouseY) {
        if (mouseX < window.x() + 4 + TREE_W) {
            return -1; // in the drive tree, not the file list
        }
        final int listY = window.y() + 18 + ADDR_H + 2;
        if (mouseY < listY) {
            return -1;
        }
        final int idx = scroll + (int) Math.floor((mouseY - (listY + 1)) / (double) ROW_H);
        return idx >= 0 && idx < rows.size() ? idx : -1;
    }

    /**
     * Maps a content-local position to a tree item index, or {@code -1} when outside the tree.
     * Returns {@code 0} for the Home row and {@code i + 1} for {@code volumes[i]}.
     */
    private int treeItemAt(final double localX, final double localY) {
        if (localX < 0 || localX >= TREE_W) {
            return -1;
        }
        final int treeTop = ADDR_H + 4;
        if (localY < treeTop) {
            return -1;
        }
        final int idx = (int) Math.floor((localY - treeTop) / (double) ROW_H);
        if (idx < 0) {
            return -1;
        }
        if (idx == 0) {
            return 0; // Home row
        }
        final int volIdx = idx - 1;
        return volIdx < volumes.size() ? volIdx + 1 : -1;
    }

    /** Maps a content-local position to a volume index in the drive tree, or {@code -1}. */
    private int volumeIndexAt(final double localX, final double localY) {
        final int item = treeItemAt(localX, localY);
        return item > 0 ? item - 1 : -1;
    }

    private void startVolumeRename(final int idx) {
        volRenaming = idx;
        volRenameBuf.setLength(0);
        volRenameBuf.append(volumes.get(idx).label());
    }

    private void commitVolumeRename() {
        if (volRenaming >= 0 && volRenaming < volumes.size()) {
            PacketDistributor.sendToServer(new dev.jsc.jscomputronics.module.computing.operation.payload
                    .RenameVolumePayload(host, volumes.get(volRenaming).key(), volRenameBuf.toString().trim()));
            volRenaming = -1;
            request(dir); // refresh so the relabeled volume shows its new name
            return;
        }
        volRenaming = -1;
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        if (renaming >= 0 || dragRow < 0) {
            return;
        }
        dragging = true;
        dragMx = mouseX;
        dragMy = mouseY;
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY,
                              final int button) {
        if (dragging && dragRow >= 0 && dragRow < rows.size()) {
            final Row src = rows.get(dragRow);
            // Where did the drag land: a removable-drive destination (left tree or a media row), or a folder?
            final String mediaDest = mediaDropTarget(window, mouseX, mouseY);
            final String destDir = folderDropTarget(window, mouseX, mouseY);

            final boolean srcIsDat = src.file() != null && src.file().readOnly();
            if (srcIsDat) {
                if (mediaDest != null) {
                    // The one sanctioned .dat action: drop onto a removable medium fires a conservative item
                    // transfer (the stored item leaves the computer and appears on the medium), not a file move.
                    if (monitorPos != null) {
                        PacketDistributor.sendToServer(new dev.jsc.jscomputronics.module.computing.operation
                                .payload.MediumTransferPayload(host, monitorPos, src.file().path(), mediaDest));
                        request(dir);
                    }
                } else if (destDir != null) {
                    // Reorganising a .dat into a normal folder by hand is forbidden; surface the error dialog.
                    DesktopScreen.showDatLockedError();
                }
            } else if (destDir != null && src.file() != null) {
                // A real, non-projection entry moves into a real folder as before.
                PacketDistributor.sendToServer(new MoveFilePayload(host, src.file().path(), destDir));
                request(dir);
            }
        }
        dragging = false;
        dragRow = -1;
    }

    /**
     * The removable-medium volume key under the drop point, or {@code null}. A medium is a target either as a
     * drive row in the file list ({@code media:} path) or as a row in the left drive tree.
     */
    @org.jetbrains.annotations.Nullable
    private String mediaDropTarget(final DesktopWindow window, final double mouseX, final double mouseY) {
        // Left drive tree: map the drop to a volume and accept it only when it is a removable medium.
        final double lx = mouseX - (window.x() + 4);
        final double ly = mouseY - (window.y() + 18);
        final int treeIdx = volumeIndexAt(lx, ly);
        if (treeIdx >= 0) {
            final String key = volumes.get(treeIdx).key();
            return key.startsWith("media:") ? key : null;
        }
        // A drive row in the file list (the medium shown as a folder at the system-disk root).
        final int row = rowIndexAt(window, mouseX, mouseY);
        if (row >= 0 && row != dragRow) {
            final Row t = rows.get(row);
            if (t.kind() == Kind.DIR && t.file() != null && t.file().path().startsWith("media:")) {
                return t.file().path();
            }
        }
        return null;
    }

    /** The real folder directory under the drop point, or {@code null} (used for ordinary file/folder moves). */
    @org.jetbrains.annotations.Nullable
    private String folderDropTarget(final DesktopWindow window, final double mouseX, final double mouseY) {
        final int target = rowIndexAt(window, mouseX, mouseY);
        if (target < 0 || target == dragRow) {
            return null;
        }
        final Row t = rows.get(target);
        if (t.kind() == Kind.DIR && t.file() != null && !t.file().path().startsWith("media:")) {
            return t.file().path();
        }
        if (t.kind() == Kind.UP) {
            return parentOf(dir);
        }
        return null;
    }

    private int contextItemAt(final double lx, final double ly) {
        if (lx < ctxX || lx > ctxX + CTX_W) {
            return -1;
        }
        final int rel = (int) Math.floor((ly - (ctxY + 1)) / (double) CTX_ITEM_H);
        return rel >= 0 && rel < CONTEXT_ITEMS.length ? rel : -1;
    }

    private void runContext(final int item) {
        switch (item) {
            case 0 -> openContextRow();   // Open
            case 1 -> startRename();      // Rename
            case 2 -> deleteContextRow(); // Delete
            case 3 -> newFile();          // New File
            case 4 -> newFolder();        // New Folder
            case 5 -> request(dir);       // Refresh
            default -> { }
        }
    }

    private void openContextRow() {
        if (ctxRow >= 0 && ctxRow < rows.size()) {
            open(rows.get(ctxRow));
        }
    }

    private void startRename() {
        if (ctxRow >= 0 && ctxRow < rows.size()) {
            startRenameAt(ctxRow);
        }
    }

    /** Begins an inline rename of the row at {@code index} (a file keeps its extension; a folder has none). */
    private void startRenameAt(final int index) {
        if (index < 0 || index >= rows.size()) {
            return;
        }
        final Row r = rows.get(index);
        if (r.file() == null) {
            return; // navigation row
        }
        if (r.kind() == Kind.FILE && r.file().readOnly()) {
            DesktopScreen.showDatLockedError(); // .dat projection cannot be renamed by hand
            return;
        }
        renaming = index;
        renameBuf.setLength(0);
        if (r.kind() == Kind.FILE) {
            // Edit only the name, keeping the extension fixed (Windows-style rename).
            final String full = r.name();
            final int dot = full.lastIndexOf('.');
            if (dot > 0) {
                renameBuf.append(full, 0, dot);
                renameExt = full.substring(dot);
            } else {
                renameBuf.append(full);
                renameExt = "";
            }
        } else {
            // A folder has no extension.
            renameBuf.append(r.name());
            renameExt = "";
        }
    }

    private void commitRename() {
        if (renaming >= 0 && renaming < rows.size()) {
            final Row r = rows.get(renaming);
            if (r.file() != null) {
                final String oldPath = r.file().path();
                final String newName = renameBuf.toString().trim() + renameExt;
                final int slash = oldPath.lastIndexOf('/');
                final String prefix = slash >= 0 ? oldPath.substring(0, slash + 1) : "";
                final String newPath = prefix + newName;
                if (!renameBuf.toString().trim().isEmpty() && !newPath.equals(oldPath)) {
                    PacketDistributor.sendToServer(new RenameFilePayload(host, oldPath, newPath));
                    request(dir);
                }
            }
        }
        renaming = -1;
    }

    private void deleteContextRow() {
        if (ctxRow < 0 || ctxRow >= rows.size()) {
            return;
        }
        final Row r = rows.get(ctxRow);
        if (r.file() == null) {
            return; // navigation row
        }
        if (r.kind() == Kind.FILE && r.file().readOnly()) {
            DesktopScreen.showDatLockedError(); // .dat projection cannot be deleted here
            return;
        }
        // The server deletes a file directly, or removes a directory recursively (rmdir fallback).
        PacketDistributor.sendToServer(new DeleteFilePayload(host, r.file().path()));
        request(dir);
    }

    private void newFile() {
        final String name = uniqueName("New File", ".txt");
        pendingRename = name;
        PacketDistributor.sendToServer(new SaveFilePayload(host, join(dir, name), ""));
        request(dir);
    }

    private void newFolder() {
        final String name = uniqueName("New Folder", "");
        pendingRename = name;
        PacketDistributor.sendToServer(new MkdirPayload(host, join(dir, name)));
        request(dir);
    }

    /** Opens a row: a folder navigates; an editable file opens in the Editor with its content loaded. */
    private void open(final Row r) {
        switch (r.kind()) {
            case STORAGE -> request("Storage");
            case UP -> request(parentOf(dir));
            case DIR -> {
                if (r.file() != null) {
                    request(r.file().path());
                }
            }
            case FILE -> {
                if (r.file() != null && !r.file().readOnly()) {
                    DesktopScreen.requestOpen("Editor");
                    PacketDistributor.sendToServer(new RequestFileContentPayload(host, r.file().path()));
                }
            }
        }
    }

    @Override
    public boolean charTyped(final char c) {
        if (volRenaming >= 0 && c >= 32 && c != 127 && c != '/' && c != '\\' && volRenameBuf.length() < 32) {
            volRenameBuf.append(c);
            return true;
        }
        if (renaming >= 0 && c >= 32 && c != 127 && c != '/' && c != '\\' && renameBuf.length() < 64) {
            renameBuf.append(c);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (volRenaming >= 0) {
            switch (key) {
                case 257, 335 -> commitVolumeRename(); // Enter
                case 256 -> volRenaming = -1;          // Escape cancels
                case 259 -> {                          // Backspace
                    if (volRenameBuf.length() > 0) {
                        volRenameBuf.deleteCharAt(volRenameBuf.length() - 1);
                    }
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (renaming < 0) {
            return false;
        }
        switch (key) {
            case 257, 335 -> commitRename(); // Enter
            case 256 -> renaming = -1;       // Escape cancels the rename
            case 259 -> {                    // Backspace
                if (renameBuf.length() > 0) {
                    renameBuf.deleteCharAt(renameBuf.length() - 1);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void clampScroll(final int visible) {
        final int max = Math.max(0, rows.size() - visible);
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    /** Returns a name not already present in the current listing, suffixing " (n)" before the extension. */
    private String uniqueName(final String base, final String ext) {
        if (!nameExists(base + ext)) {
            return base + ext;
        }
        int n = 2;
        while (nameExists(base + " (" + n + ")" + ext)) {
            n++;
        }
        return base + " (" + n + ")" + ext;
    }

    private boolean nameExists(final String name) {
        for (final Row r : rows) {
            if ((r.kind() == Kind.FILE || r.kind() == Kind.DIR) && r.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static IconType iconFor(final String ext) {
        return switch (ext.toLowerCase(java.util.Locale.ROOT)) {
            case "iql" -> IconType.IQL;
            case "dat" -> IconType.DAT;
            default -> IconType.DOC;
        };
    }

    /** Draws a small per-type icon (a folder with a tab, or a document with a folded corner). */
    private static void drawIcon(final GuiGraphics g, final int x, final int y, final IconType type) {
        switch (type) {
            case UP -> {
                g.fill(x, y + 1, x + ICON_W, y + 9, 0xFFA8A8A8);
                g.fill(x, y + 1, x + ICON_W, y + 2, 0xFFD8D8D8);
                outlineColor(g, x, y + 1, ICON_W, 8, 0xFF707070);
                // A small up-arrow: a vertical stem with a wider head.
                g.fill(x + 5, y + 3, x + 7, y + 8, 0xFF303030);
                g.fill(x + 3, y + 4, x + 9, y + 5, 0xFF303030);
            }
            case FOLDER -> {
                g.fill(x, y + 2, x + 5, y, 0xFFFFE9A8);            // tab
                g.fill(x, y + 2, x + ICON_W, y + 9, 0xFFF4C842);   // body
                g.fill(x, y + 2, x + ICON_W, y + 3, 0xFFFFF3C4);   // highlight
                outlineColor(g, x, y, ICON_W, 9, 0xFF9A7B16);
            }
            case HOME -> {
                // A small house: peaked roof (3 rows, widening down) then a walled body with a door.
                final int mid = x + ICON_W / 2;
                g.fill(mid,     y,     mid + 1, y + 1, 0xFFD4822E); // peak
                g.fill(mid - 1, y + 1, mid + 2, y + 2, 0xFFD4822E); // 2nd roof row
                g.fill(mid - 2, y + 2, mid + 3, y + 3, 0xFFD4822E); // 3rd roof row
                g.fill(mid - 3, y + 3, mid + 4, y + 4, 0xFFD4822E); // eaves
                g.fill(mid - 3, y + 4, mid + 4, y + 9, 0xFFE8D4A0); // walls
                outlineColor(g, mid - 3, y + 4, 7, 5, 0xFF9A7A30);
                g.fill(mid - 1, y + 6, mid + 1, y + 9, 0xFF8A5810); // door
            }
            case IQL -> doc(g, x, y, 0xFFA9D4FF, 0xFF3A72B0);
            case DAT -> doc(g, x, y, 0xFFBDEEC0, 0xFF4F9B53);
            case DOC -> doc(g, x, y, 0xFFDFE3EA, 0xFF8A93A6);
        }
    }

    private static void doc(final GuiGraphics g, final int x, final int y, final int fill, final int edge) {
        g.fill(x + 1, y, x + ICON_W, y + 9, fill);
        g.fill(x + ICON_W - 3, y, x + ICON_W, y + 3, 0xFFFFFFFF); // folded corner
        outlineColor(g, x + 1, y, ICON_W - 1, 9, edge);
    }

    private static void outlineColor(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                     final int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** A Windows-style address for the path: {@code C:\dir\} on the disk, {@code Removable Drive\sub\} on media. */
    private static String displayPath(final String dir) {
        if (dir.startsWith("media:")) {
            final int slash = dir.indexOf('/');
            final String sub = slash < 0 ? "" : dir.substring(slash + 1).replace('/', '\\') + "\\";
            return "Removable Drive\\" + sub;
        }
        return "C:\\" + (dir.isEmpty() ? "" : dir.replace('/', '\\') + "\\");
    }

    /** A breadcrumb label for the path (Win11 header). */
    private static String crumbLabel(final String dir) {
        if (dir.isEmpty()) {
            return "Home";
        }
        if (dir.startsWith("media:")) {
            final int slash = dir.indexOf('/');
            return slash < 0 ? "Removable Drive"
                    : "Removable Drive  >  " + dir.substring(slash + 1).replace("/", "  >  ");
        }
        return "Home  >  " + dir.replace("/", "  >  ");
    }

    /** A friendly name for a removable-drive root, otherwise the path's last segment. */
    private static String driveOrBaseName(final String path) {
        if (path.startsWith("media:") && path.indexOf('/') < 0) {
            return "Removable Drive";
        }
        return baseName(path);
    }

    /** The last path segment (after the final {@code /}), or the whole path when it has no slash. */
    private static String baseName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    /** The parent directory of {@code dir} (everything before the final {@code /}), or {@code ""} at the root. */
    private static String parentOf(final String dir) {
        final int slash = dir.lastIndexOf('/');
        return slash < 0 ? "" : dir.substring(0, slash);
    }

    /** Joins a directory and a child name; the root ({@code ""}) yields the bare name. */
    private static String join(final String dir, final String name) {
        return dir.isEmpty() ? name : dir + "/" + name;
    }
}
