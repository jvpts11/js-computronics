/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.gui.layout.FilesLayout;
import dev.jsc.jscomputronics.module.computing.operation.payload.CopyFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DeleteFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DiskFilesPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.EjectMediaPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.InstallFromMediaPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.MkdirPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.MoveFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RenameFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestDiskFilesPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestFileContentPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.SaveFilePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The file explorer, shown as a desktop window in the Windows-Explorer mould: a toolbar with back,
 * forward and up, a clickable address trail and a search box; a drive tree with quick-access shortcuts,
 * every volume with its letter and an eject control on removable media; a sortable list with name,
 * type and size columns and an icon per file type; and a status bar. A stored item's {@code .dat}
 * shows the item itself with its count. Double-click opens a folder, runs an installer's setup, or
 * opens a text file in the Editor. Right-click opens a context menu with cut, copy, paste, rename,
 * delete, new file, new folder and properties, greyed where the volume forbids them.
 *
 * <p>The geometry lives in {@link FilesLayout}, where a test proves nothing overlaps.
 */
public final class FilesApp implements DesktopApp {

    private static final long DOUBLE_CLICK_MS = 300L;
    private static final int HISTORY_MAX = 32;

    private OsSkin skin = OsSkin.fallback();
    private String os = "frames_95";

    private final BlockPos host;
    // The monitor the desktop is shown on, needed to authenticate the sanctioned .dat-to-medium item
    // transfer (the server validates the player is within reach of this monitor). May be null when the
    // explorer is opened outside a desktop context.
    @org.jetbrains.annotations.Nullable
    private final BlockPos monitorPos;
    private String dir = "";
    private List<Row> allRows = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();
    private int selected = -1;
    private int scroll;
    private List<DiskFilesPayload.WireVolume> volumes = new ArrayList<>();

    private int lastClickRow = -1;
    private long lastClickAt;

    // Navigation history for back and forward.
    private final List<String> back = new ArrayList<>();
    private final List<String> forward = new ArrayList<>();

    // The search box filters the listing as the player types.
    private final StringBuilder search = new StringBuilder();
    private boolean searchFocused;

    private SortBy sortBy = SortBy.NAME;
    private boolean sortAscending = true;
    private boolean iconView;

    // Right-click context menu: items built for the target when it opens.
    private boolean contextOpen;
    private int ctxX;
    private int ctxY;
    private int ctxRow = -1;
    private List<MenuItem> ctxItems = List.of();

    private int renaming = -1;
    private final StringBuilder renameBuf = new StringBuilder();
    private String renameExt = "";

    // Drag-and-drop state: the row picked up on press, whether a drag is in progress, and the
    // current cursor position (desktop-local) for the drag ghost.
    private int dragRow = -1;
    private boolean dragging;
    private double dragMx;
    private double dragMy;

    // Rubber-band selection over the file list. Pressing on empty space below the last row starts a
    // sweep; every row it crosses joins the selection. It never starts on a row, so the existing
    // click-and-drag of a file into a folder keeps working untouched.
    private boolean bandActive;
    private double bandStartX;
    private double bandStartY;
    private double bandX;
    private double bandY;
    private final java.util.Set<Integer> bandRows = new java.util.LinkedHashSet<>();
    /** Rows the list can show at its current size, published by the last render for the band to respect. */
    private int visibleRows = 1;

    // After creating a New File/New Folder, the next listing enters rename on the matching row.
    private String pendingRename;

    // The clipboard: paths waiting to be pasted, and whether the paste moves them.
    private final List<String> clipboard = new ArrayList<>();
    private boolean clipboardCut;

    // The properties panel, over the list, for one row.
    private Row propsRow;

    // Volume relabel state (right-click a volume in the tree).
    private int volRenaming = -1;
    private final StringBuilder volRenameBuf = new StringBuilder();
    private boolean volContextOpen;
    private int volCtxIndex = -1;
    private int volCtxX;
    private int volCtxY;

    private static FilesApp active;

    // The content size of the last render, so click math uses the geometry that was drawn.
    private int contentW = FilesLayout.DEFAULT_W;
    private int contentH = FilesLayout.DEFAULT_H;

    private enum Kind { UP, STORAGE, DIR, FILE }

    private enum SortBy { NAME, TYPE, SIZE }

    private enum IconType { UP, FOLDER, HOME, IQL, DOC, DAT, EXE, PKG, INF, BIN, CFG, LOG, CRAFT }

    private record Row(Kind kind, String name, String type, String size, IconType icon,
                       @org.jetbrains.annotations.Nullable DiskFilesPayload.WireFile file,
                       @org.jetbrains.annotations.Nullable ItemStack item) {
    }

    private record MenuItem(String label, boolean enabled, Runnable action) {
        static MenuItem separator() {
            return new MenuItem("-", false, () -> { });
        }
    }

    /** One entry of the drive tree: a section title, a quick-access shortcut, or a volume. */
    private record TreeItem(String label, String target, boolean section, boolean removable, int volumeIndex) {
    }

    public FilesApp(final BlockPos host) {
        this(host, "frames_95", "", null);
    }

    /** Opens the explorer skinned for {@code os} (frames_95 / frames_xp / frames_11). */
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
        this.os = os;
        this.skin = OsSkin.forDesktop(ResourceLocation.fromNamespaceAndPath("jsc", os));
        active = this;
        request(initialDir);
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        this.os = osSkin.osPath();
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

    /** The directory this explorer is currently showing (the system-disk root is {@code ""}). */
    public String currentDir() {
        return dir;
    }

    @Override
    public String title() {
        return "Files";
    }

    @Override
    public int defaultWidth() {
        return FilesLayout.DEFAULT_W;
    }

    @Override
    public int defaultHeight() {
        return FilesLayout.DEFAULT_H;
    }

    @Override
    public int minWidth() {
        return FilesLayout.MIN_W;
    }

    @Override
    public int minHeight() {
        return FilesLayout.MIN_H;
    }

    // ---- navigation ------------------------------------------------------------------------

    private void request(final String target) {
        this.dir = target;
        this.selected = -1;
        this.scroll = 0;
        this.propsRow = null;
        this.contextOpen = false;
        // Row indices are about to mean something else, so a sweep selection cannot survive.
        this.bandActive = false;
        this.bandRows.clear();
        PacketDistributor.sendToServer(new RequestDiskFilesPayload(host, target));
    }

    /** Navigates somewhere new: the current folder joins the back history and forward is cleared. */
    private void go(final String target) {
        if (target.equals(dir)) {
            request(target);
            return;
        }
        back.add(dir);
        if (back.size() > HISTORY_MAX) {
            back.remove(0);
        }
        forward.clear();
        request(target);
    }

    private void goBack() {
        if (back.isEmpty()) {
            return;
        }
        forward.add(dir);
        request(back.remove(back.size() - 1));
    }

    private void goForward() {
        if (forward.isEmpty()) {
            return;
        }
        back.add(dir);
        request(forward.remove(forward.size() - 1));
    }

    private void goUp() {
        if (dir.isEmpty()) {
            return;
        }
        // Up from a medium's root lands on This PC, i.e. the system-disk root with the drives listed.
        if (dir.startsWith("media:") && dir.indexOf('/') < 0) {
            go("");
            return;
        }
        go(parentOf(dir));
    }

    private boolean onMedia() {
        return dir.startsWith("media:");
    }

    /** The reader position of the medium being browsed, or {@code -1} on the system disk. */
    private long mediaReaderPos() {
        return mediaReaderPos(dir);
    }

    private static long mediaReaderPos(final String path) {
        if (!path.startsWith("media:")) {
            return -1L;
        }
        final String rest = path.substring("media:".length());
        final int slash = rest.indexOf('/');
        try {
            return Long.parseLong(slash < 0 ? rest : rest.substring(0, slash));
        } catch (final NumberFormatException e) {
            return -1L;
        }
    }

    /** Whether the volume being browsed refuses writes: an installer's projection, or a pressed disc. */
    private boolean readOnlyVolume() {
        if (!onMedia()) {
            return false;
        }
        boolean anyFile = false;
        for (final Row r : allRows) {
            if (r.kind() == Kind.FILE && r.file() != null) {
                anyFile = true;
                if (!r.file().readOnly()) {
                    return false;
                }
            }
        }
        return anyFile;
    }

    // ---- listing ---------------------------------------------------------------------------

    private void rebuild(final List<DiskFilesPayload.WireFile> files) {
        final List<Row> built = new ArrayList<>();
        if (dir.isEmpty()) {
            built.add(new Row(Kind.STORAGE, "Storage", "Stored items", "", IconType.FOLDER, null, null));
        } else {
            built.add(new Row(Kind.UP, "..", "Up one level", "", IconType.UP, null, null));
        }
        for (final DiskFilesPayload.WireFile f : files) {
            if (f.directory()) {
                final boolean drive = f.path().startsWith("media:") && f.path().indexOf('/') < 0;
                built.add(new Row(Kind.DIR, drive ? volumeLabel(f.path()) : baseName(f.path()),
                        drive ? "Removable drive" : "Folder", "", IconType.FOLDER, f, null));
            } else if (f.projectsItem()) {
                final ItemStack stack = stackOf(f.itemId());
                built.add(new Row(Kind.FILE, stack.isEmpty() ? baseName(f.path()) : stack.getHoverName().getString(),
                        "Stored item", f.count() + " it", IconType.DAT, f, stack.isEmpty() ? null : stack));
            } else {
                built.add(new Row(Kind.FILE, baseName(f.path()), typeLabel(f), sizeLabel(f), iconFor(f.ext()), f, null));
            }
        }
        this.allRows = built;
        applyFilterAndSort();
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

    /** Rebuilds the visible rows from the full listing: the search filter, then the sort, navigation rows first. */
    private void applyFilterAndSort() {
        final String needle = search.toString().trim().toLowerCase(Locale.ROOT);
        final List<Row> nav = new ArrayList<>();
        final List<Row> dirs = new ArrayList<>();
        final List<Row> files = new ArrayList<>();
        for (final Row r : allRows) {
            if (r.kind() == Kind.UP || r.kind() == Kind.STORAGE) {
                if (needle.isEmpty()) {
                    nav.add(r);
                }
            } else if (needle.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(needle)) {
                (r.kind() == Kind.DIR ? dirs : files).add(r);
            }
        }
        final java.util.Comparator<Row> order = switch (sortBy) {
            case TYPE -> java.util.Comparator.comparing((Row r) -> r.type().toLowerCase(Locale.ROOT))
                    .thenComparing(r -> r.name().toLowerCase(Locale.ROOT));
            case SIZE -> java.util.Comparator.comparingLong((Row r) -> r.file() == null ? 0L
                    : (r.file().projectsItem() ? r.file().count() : r.file().weight()))
                    .thenComparing(r -> r.name().toLowerCase(Locale.ROOT));
            default -> java.util.Comparator.comparing((Row r) -> r.name().toLowerCase(Locale.ROOT));
        };
        dirs.sort(sortAscending ? order : order.reversed());
        files.sort(sortAscending ? order : order.reversed());
        final List<Row> out = new ArrayList<>(nav.size() + dirs.size() + files.size());
        out.addAll(nav);
        out.addAll(dirs);
        out.addAll(files);
        this.rows = out;
        if (selected >= rows.size()) {
            selected = -1;
        }
        bandRows.removeIf(i -> i >= rows.size());
    }

    private static ItemStack stackOf(final String itemId) {
        final ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static String typeLabel(final DiskFilesPayload.WireFile f) {
        return switch (f.ext().toLowerCase(Locale.ROOT)) {
            case "iql" -> "IQL script";
            case "txt" -> "Text";
            case "log" -> "Log";
            case "cfg" -> "Configuration";
            case "csv" -> "Table";
            case "cmd" -> "Shell script";
            case "craft" -> "Craft pattern";
            case "dat" -> "Stored item";
            case "exe" -> "Installer";
            case "sh" -> "Install script";
            case "pkg" -> "Package manifest";
            case "inf" -> "Setup information";
            case "bin" -> "Installer data";
            default -> f.ext().isEmpty() ? "File" : f.ext().toUpperCase(Locale.ROOT) + " file";
        };
    }

    private static String sizeLabel(final DiskFilesPayload.WireFile f) {
        if (f.readOnly() && f.weight() == 0L) {
            return "-";
        }
        return f.weight() + " mB";
    }

    private String volumeLabel(final String key) {
        for (final DiskFilesPayload.WireVolume v : volumes) {
            if (v.key().equals(key)) {
                return v.label();
            }
        }
        return "Removable Drive";
    }

    /** The drive letter of a volume: the system disk is C:, then the media in the order the tree lists them. */
    private String letterOf(final String key) {
        if (linux()) {
            return "";
        }
        if (key.isEmpty()) {
            return "C:";
        }
        int n = 0;
        for (final DiskFilesPayload.WireVolume v : volumes) {
            if (v.removable()) {
                n++;
                if (v.key().equals(key)) {
                    return (char) ('C' + n) + ":";
                }
            }
        }
        return "";
    }

    private boolean linux() {
        return !os.startsWith("frames_");
    }

    // ---- the drive tree --------------------------------------------------------------------

    private List<TreeItem> tree() {
        final List<TreeItem> out = new ArrayList<>();
        out.add(new TreeItem("Quick access", "", true, false, -1));
        out.add(new TreeItem("Desktop", linux()
                ? dev.jsc.jscomputronics.module.computing.os.fs.SystemLayout.POSIX_DESKTOP_DIR
                : dev.jsc.jscomputronics.module.computing.os.fs.SystemLayout.DESKTOP_DIR, false, false, -1));
        out.add(new TreeItem("Storage", "Storage", false, false, -1));
        out.add(new TreeItem(linux() ? "Devices" : "This PC", "", true, false, -1));
        for (int i = 0; i < volumes.size(); i++) {
            final DiskFilesPayload.WireVolume v = volumes.get(i);
            final String letter = letterOf(v.key());
            out.add(new TreeItem(letter.isEmpty() ? v.label() : v.label() + " (" + letter + ")", v.key(),
                    false, v.removable(), i));
        }
        return out;
    }

    private static boolean isVolumeItem(final TreeItem item) {
        return !item.section() && item.volumeIndex() >= 0;
    }

    // ---- rendering -------------------------------------------------------------------------

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        // The front (last-rendered) explorer window owns the DiskFilesPayload routing, so two open
        // Files windows don't leave the back one as a stale target and a closed one stops receiving.
        active = this;
        this.contentW = width;
        this.contentH = height;
        final int text = skin.text();
        final int dim = skin.dim();
        g.fill(x, y, x + width, y + height, skin.windowBg());

        // ---- toolbar
        final int ny = y + FilesLayout.navY();
        navButton(g, font, x + FilesLayout.navX(0), ny, "<", !back.isEmpty(), mouseX, mouseY);
        navButton(g, font, x + FilesLayout.navX(1), ny, ">", !forward.isEmpty(), mouseX, mouseY);
        navButton(g, font, x + FilesLayout.navX(2), ny, "^", !dir.isEmpty(), mouseX, mouseY);
        final int ax = x + FilesLayout.addressX();
        final int aw = FilesLayout.addressW(width);
        skin.field(g, ax, ny, aw, FilesLayout.NAV_H, false);
        drawCrumbs(g, font, ax + 3, ny + 2, aw - 6, mouseX, mouseY);
        final int sx = x + FilesLayout.searchX(width);
        skin.field(g, sx, ny, FilesLayout.SEARCH_W, FilesLayout.NAV_H, searchFocused);
        final String shown = search.length() == 0 && !searchFocused ? "Search" : search + (searchFocused ? "_" : "");
        g.drawString(font, trim(font, shown, FilesLayout.SEARCH_W - 6), sx + 3, ny + 2,
                search.length() == 0 && !searchFocused ? dim : text, false);
        navButton(g, font, x + FilesLayout.viewX(width), ny, iconView ? "=" : "#", true, mouseX, mouseY);
        g.fill(x, y + FilesLayout.TOOL_H, x + width, y + FilesLayout.TOOL_H + 1, skin.edge());

        // ---- the drive tree
        final int ty = y + FilesLayout.treeY();
        final int th = FilesLayout.treeH(height);
        renderTree(g, font, x, ty, FilesLayout.TREE_W, th, mouseX, mouseY);

        // ---- the column header and the list
        final int lx = x + FilesLayout.listX();
        final int lw = FilesLayout.listW(width);
        final int cy = y + FilesLayout.colsY();
        g.fill(lx, cy, lx + lw, cy + FilesLayout.COLS_H, skin.listHover());
        g.fill(lx, cy + FilesLayout.COLS_H - 1, lx + lw, cy + FilesLayout.COLS_H, skin.edge());
        final String arrow = sortAscending ? " ^" : " v";
        g.drawString(font, "Name" + (sortBy == SortBy.NAME ? arrow : ""), lx + 4 + FilesLayout.ICON_W + 3, cy + 1, dim, false);
        g.drawString(font, "Type" + (sortBy == SortBy.TYPE ? arrow : ""), x + FilesLayout.typeColX(width), cy + 1, dim, false);
        g.drawString(font, "Size" + (sortBy == SortBy.SIZE ? arrow : ""), x + FilesLayout.sizeColX(width), cy + 1, dim, false);

        final int listY = y + FilesLayout.listY();
        final int listH = FilesLayout.listH(height);
        g.fill(lx, listY, lx + lw, listY + listH, skin.panelBg());
        OsSkin.outline(g, lx, listY, lw, listH, skin.edge());
        if (iconView) {
            renderIconGrid(g, font, x, width, lx, listY, lw, listH, mouseX, mouseY);
        } else {
            renderRows(g, font, x, width, lx, listY, lw, listH, mouseX, mouseY);
        }

        // The rubber band, over the rows it is selecting.
        if (bandActive) {
            final int[] b = bandRect();
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x334C84F0);
            OsSkin.outline(g, b[0], b[1], b[2], b[3], 0xCC4C84F0);
        }

        // ---- status bar
        final int sy = y + FilesLayout.statusY(height);
        skin.statusBar(g, x, sy, width, FilesLayout.STATUS_H);
        int items = 0;
        long used = 0L;
        for (final Row r : rows) {
            if (r.kind() == Kind.FILE || r.kind() == Kind.DIR) {
                items++;
            }
            if (r.kind() == Kind.FILE && r.file() != null) {
                used += r.file().weight();
            }
        }
        final int selectedCount = bandRows.size() > 1 ? bandRows.size() : (selected >= 0 ? 1 : 0);
        String left = items + (items == 1 ? " item" : " items");
        if (selectedCount > 0) {
            left += " · " + selectedCount + " selected";
            if (selectedCount == 1 && selected >= 0 && selected < rows.size()) {
                left += " · " + rows.get(selected).name();
            }
        }
        g.drawString(font, trim(font, left, width / 2), x + 3, sy + 2, text, false);
        final String right = readOnlyVolume() ? "read-only medium" : used + " mB used";
        g.drawString(font, right, x + width - font.width(right) - 3, sy + 2, dim, false);

        // ---- overlays: properties, the context menus, the drag ghost
        if (propsRow != null) {
            renderProperties(g, font, lx, listY, lw, listH, mouseX, mouseY);
        }
        if (contextOpen) {
            renderContextMenu(g, font, x + ctxX, y + ctxY, mouseX, mouseY);
        }
        if (dragging && dragRow >= 0 && dragRow < rows.size()) {
            final String label = rows.get(dragRow).name();
            final int gw = font.width(label) + 6;
            final int gx = (int) dragMx + 6;
            final int gy = (int) dragMy + 2;
            g.fill(gx, gy, gx + gw, gy + 11, 0xD0303848);
            g.drawString(font, label, gx + 3, gy + 2, 0xFFFFFFFF, false);
        }
        if (volContextOpen) {
            final int mx = x + volCtxX;
            final int my = y + volCtxY;
            final int mw = 60;
            g.fill(mx - 1, my - 1, mx + mw + 1, my + FilesLayout.CTX_ITEM_H + 3, 0xFF000000);
            g.fill(mx, my, mx + mw, my + FilesLayout.CTX_ITEM_H + 2, skin.panelBg());
            final boolean hov = mouseX >= mx && mouseX <= mx + mw
                    && mouseY >= my + 1 && mouseY <= my + 1 + FilesLayout.CTX_ITEM_H;
            if (hov) {
                g.fill(mx + 1, my + 1, mx + mw - 1, my + 1 + FilesLayout.CTX_ITEM_H, skin.accent());
            }
            g.drawString(font, "Rename", mx + 4, my + 3, hov ? 0xFFFFFFFF : text, false);
        }
    }

    private void navButton(final GuiGraphics g, final Font font, final int bx, final int by, final String glyph,
                           final boolean enabled, final int mouseX, final int mouseY) {
        final boolean hover = enabled && inRect(mouseX, mouseY, bx, by, FilesLayout.NAV_W, FilesLayout.NAV_H);
        skin.button(g, font, bx, by, FilesLayout.NAV_W, FilesLayout.NAV_H, "", hover, false, false);
        g.drawString(font, glyph, bx + (FilesLayout.NAV_W - font.width(glyph)) / 2, by + 2,
                enabled ? skin.text() : skin.dim(), false);
    }

    /** The address as a trail of crumbs, each a click target, the last one the folder being shown. */
    private void drawCrumbs(final GuiGraphics g, final Font font, final int cx, final int cy, final int maxW,
                            final int mouseX, final int mouseY) {
        final List<String[]> crumbs = crumbs();
        int px = cx;
        for (int i = 0; i < crumbs.size(); i++) {
            final String label = crumbs.get(i)[0];
            final int w = font.width(label);
            if (px + w > cx + maxW) {
                g.drawString(font, "..", px, cy, skin.dim(), false);
                break;
            }
            final boolean last = i == crumbs.size() - 1;
            final boolean hover = !last && inRect(mouseX, mouseY, px - 1, cy - 2, w + 2, FilesLayout.NAV_H - 2);
            g.drawString(font, label, px, cy, hover ? skin.accent() : (last ? skin.text() : skin.dim()), false);
            px += w;
            if (!last) {
                g.drawString(font, " > ", px, cy, skin.dim(), false);
                px += font.width(" > ");
            }
        }
    }

    /** The crumbs of the current path: {label, target} pairs from the root to here. */
    private List<String[]> crumbs() {
        final List<String[]> out = new ArrayList<>();
        if (onMedia()) {
            final String rootKey = "media:" + mediaReaderPos();
            out.add(new String[]{linux() ? "Devices" : "This PC", ""});
            final String letter = letterOf(rootKey);
            out.add(new String[]{volumeLabel(rootKey) + (letter.isEmpty() ? "" : " (" + letter + ")"), rootKey});
            final int slash = dir.indexOf('/');
            if (slash >= 0) {
                String acc = rootKey;
                for (final String seg : dir.substring(slash + 1).split("/")) {
                    if (seg.isEmpty()) {
                        continue;
                    }
                    acc = acc + "/" + seg;
                    out.add(new String[]{seg, acc});
                }
            }
            return out;
        }
        out.add(new String[]{linux() ? "/" : "Local Disk (C:)", ""});
        if (!dir.isEmpty()) {
            String acc = "";
            for (final String seg : dir.split("/")) {
                if (seg.isEmpty()) {
                    continue;
                }
                acc = acc.isEmpty() ? seg : acc + "/" + seg;
                out.add(new String[]{seg, acc});
            }
        }
        return out;
    }

    /** The crumb under {@code mouseX} on the address bar, as a navigation target, or {@code null}. */
    @org.jetbrains.annotations.Nullable
    private String crumbAt(final Font font, final int ax, final double mouseX) {
        final List<String[]> crumbs = crumbs();
        int px = ax + 3;
        for (int i = 0; i < crumbs.size() - 1; i++) {
            final int w = font.width(crumbs.get(i)[0]);
            if (mouseX >= px && mouseX < px + w) {
                return crumbs.get(i)[1];
            }
            px += w + font.width(" > ");
        }
        return null;
    }

    private void renderTree(final GuiGraphics g, final Font font, final int tx, final int ty,
                            final int tw, final int th, final int mouseX, final int mouseY) {
        g.fill(tx, ty, tx + tw, ty + th, skin.listHover());
        g.fill(tx + tw - 1, ty, tx + tw, ty + th, skin.edge());
        int vy = ty + 2;
        final List<TreeItem> items = tree();
        for (int i = 0; i < items.size() && vy + FilesLayout.ROW_H <= ty + th; i++) {
            final TreeItem item = items.get(i);
            if (item.section()) {
                g.drawString(font, item.label().toUpperCase(Locale.ROOT), tx + 4, vy + 3, skin.dim(), false);
                vy += FilesLayout.ROW_H;
                continue;
            }
            final boolean cur = item.target().isEmpty() ? (dir.isEmpty() && isVolumeItem(item))
                    : isVolumeItem(item) ? isCurrentVolume(item.target()) : dir.equals(item.target());
            final boolean hover = inRect(mouseX, mouseY, tx + 1, vy, tw - 2, FilesLayout.ROW_H);
            skin.listRow(g, tx + 1, vy, tw - 2, FilesLayout.ROW_H, hover, cur);
            drawIcon(g, tx + 3, vy + 1, item.target().equals("Storage") ? IconType.DAT
                    : (isVolumeItem(item) ? (item.removable() ? IconType.BIN : IconType.HOME) : IconType.FOLDER));
            String label = isVolumeItem(item) && item.volumeIndex() == volRenaming ? volRenameBuf + "_" : item.label();
            final int maxW = tw - (FilesLayout.ICON_W + 8) - (item.removable() ? 8 : 0);
            label = trim(font, label, maxW);
            g.drawString(font, label, tx + 4 + FilesLayout.ICON_W, vy + 2, skin.listRowText(cur), false);
            if (item.removable()) {
                // The eject control at the row's right edge: a tray glyph.
                final int ex = tx + tw - 9;
                final int c = cur ? skin.listRowText(true) : skin.dim();
                g.fill(ex + 2, vy + 3, ex + 4, vy + 4, c);
                g.fill(ex + 1, vy + 4, ex + 5, vy + 5, c);
                g.fill(ex, vy + 5, ex + 6, vy + 6, c);
                g.fill(ex, vy + 7, ex + 6, vy + 8, c);
            }
            vy += FilesLayout.ROW_H;
        }
    }

    /** Whether {@code key} is the volume currently being browsed (system disk = any non-media path). */
    private boolean isCurrentVolume(final String key) {
        if (key.isEmpty()) {
            return !dir.startsWith("media:");
        }
        return dir.equals(key) || dir.startsWith(key + "/");
    }

    private void renderRows(final GuiGraphics g, final Font font, final int x, final int width, final int lx,
                            final int listY, final int lw, final int listH, final int mouseX, final int mouseY) {
        // Remembered for the rubber band, which must never select a row the player cannot see: a
        // sweep that reached off-screen rows would delete files that were never shown as selected.
        this.visibleRows = Math.max(1, (listH - 2) / FilesLayout.ROW_H);
        clampScroll(visibleRows);
        int dropTarget = -1;
        if (dragging && dragMx >= lx) {
            final int idx = scroll + (int) Math.floor((dragMy - (listY + 1)) / (double) FilesLayout.ROW_H);
            if (idx >= 0 && idx < rows.size() && idx != dragRow) {
                final Kind k = rows.get(idx).kind();
                if (k == Kind.DIR || k == Kind.UP) {
                    dropTarget = idx;
                }
            }
        }
        int row = listY + 1;
        for (int i = scroll; i < rows.size() && (i - scroll) < visibleRows; i++) {
            final Row r = rows.get(i);
            final boolean sel = i == selected || bandRows.contains(i);
            final boolean hover = i != renaming && inRect(mouseX, mouseY, lx, row, lw, FilesLayout.ROW_H);
            skin.listRow(g, lx + 1, row, lw - 2, FilesLayout.ROW_H, hover, sel);
            if (i == dropTarget) {
                OsSkin.outline(g, lx + 1, row, lw - 2, FilesLayout.ROW_H, 0xFF2E8B2E);
            }
            if (r.item() != null) {
                DesktopItems.item(g, r.item(), lx + 2, row - 3);
            } else {
                drawIcon(g, lx + 3, row + 1, r.icon());
            }
            final boolean ro = r.file() != null && r.file().readOnly();
            final int nameColor = sel ? skin.listRowText(true) : (ro ? skin.dim() : skin.text());
            final int subColor = sel ? skin.listRowText(true) : skin.dim();
            final String name = i == renaming ? renameBuf + "_" + renameExt : r.name();
            g.drawString(font, trim(font, name, FilesLayout.nameMaxW(width)), lx + 4 + FilesLayout.ICON_W + 3, row + 2,
                    nameColor, false);
            g.drawString(font, trim(font, r.type(), FilesLayout.TYPE_COL_W - 4), x + FilesLayout.typeColX(width), row + 2,
                    subColor, false);
            final int sizeX = x + width - 4 - font.width(r.size());
            g.drawString(font, r.size(), sizeX, row + 2, subColor, false);
            row += FilesLayout.ROW_H;
        }
    }

    private void renderIconGrid(final GuiGraphics g, final Font font, final int x, final int width, final int lx,
                                final int listY, final int lw, final int listH, final int mouseX, final int mouseY) {
        final int cellW = 52;
        final int cellH = 30;
        final int cols = Math.max(1, (lw - 4) / cellW);
        final int rowsVisible = Math.max(1, (listH - 2) / cellH);
        this.visibleRows = rowsVisible * cols;
        clampScroll(visibleRows);
        for (int i = scroll; i < rows.size() && (i - scroll) < visibleRows; i++) {
            final Row r = rows.get(i);
            final int n = i - scroll;
            final int cx = lx + 2 + (n % cols) * cellW;
            final int cy = listY + 2 + (n / cols) * cellH;
            final boolean sel = i == selected || bandRows.contains(i);
            final boolean hover = inRect(mouseX, mouseY, cx, cy, cellW - 2, cellH - 2);
            skin.listRow(g, cx, cy, cellW - 2, cellH - 2, hover, sel);
            if (r.item() != null) {
                DesktopItems.item(g, r.item(), cx + (cellW - 2) / 2 - 8, cy + 2);
            } else {
                drawIcon(g, cx + (cellW - 2) / 2 - FilesLayout.ICON_W / 2, cy + 4, r.icon());
            }
            final String label = trim(font, r.name(), cellW - 6);
            g.drawString(font, label, cx + ((cellW - 2) - font.width(label)) / 2, cy + cellH - 11,
                    skin.listRowText(sel), false);
        }
    }

    private void renderProperties(final GuiGraphics g, final Font font, final int lx, final int listY,
                                  final int lw, final int listH, final int mouseX, final int mouseY) {
        final int pw = Math.min(FilesLayout.PROPS_W, lw - 8);
        final int ph = FilesLayout.PROPS_H;
        final int px = lx + (lw - pw) / 2;
        final int py = listY + Math.max(2, (listH - ph) / 2);
        skin.panel(g, px, py, pw, ph);
        g.fill(px, py, px + pw, py + 11, skin.listHover());
        g.drawString(font, "Properties", px + 4, py + 2, skin.text(), false);
        int ly = py + 14;
        for (final String[] kv : propertiesOf(propsRow)) {
            g.drawString(font, kv[0], px + 4, ly, skin.dim(), false);
            g.drawString(font, trim(font, kv[1], pw - 48), px + 44, ly, skin.text(), false);
            ly += 10;
        }
        final int bw = 36;
        final int bx = px + pw - bw - 4;
        final int by = py + ph - 14;
        skin.button(g, font, bx, by, bw, 11, "Close", inRect(mouseX, mouseY, bx, by, bw, 11), false, true);
    }

    private List<String[]> propertiesOf(final Row r) {
        final List<String[]> out = new ArrayList<>();
        out.add(new String[]{"Name", r.name()});
        out.add(new String[]{"Type", r.type()});
        if (r.file() != null) {
            out.add(new String[]{"Size", r.file().projectsItem() ? r.file().count() + " items" : r.file().weight() + " mB"});
            out.add(new String[]{"Where", displayPath(parentOf(r.file().path()))});
            out.add(new String[]{"Access", r.file().readOnly() ? "read-only" : "read/write"});
        }
        return out;
    }

    private void renderContextMenu(final GuiGraphics g, final Font font, final int mx, final int my,
                                   final int mouseX, final int mouseY) {
        final int mh = ctxItems.size() * FilesLayout.CTX_ITEM_H + 2;
        g.fill(mx - 1, my - 1, mx + FilesLayout.CTX_W + 1, my + mh + 1, 0xFF000000);
        g.fill(mx, my, mx + FilesLayout.CTX_W, my + mh, skin.panelBg());
        final int hover = mouseX >= mx && mouseX <= mx + FilesLayout.CTX_W
                ? (int) Math.floor((mouseY - (my + 1)) / (double) FilesLayout.CTX_ITEM_H) : -1;
        int iy = my + 1;
        for (int k = 0; k < ctxItems.size(); k++) {
            final MenuItem item = ctxItems.get(k);
            if (item.label().equals("-")) {
                g.fill(mx + 3, iy + FilesLayout.CTX_ITEM_H / 2, mx + FilesLayout.CTX_W - 3,
                        iy + FilesLayout.CTX_ITEM_H / 2 + 1, skin.edge());
            } else {
                final boolean hov = k == hover && item.enabled();
                if (hov) {
                    g.fill(mx + 1, iy, mx + FilesLayout.CTX_W - 1, iy + FilesLayout.CTX_ITEM_H, skin.accent());
                }
                g.drawString(font, item.label(), mx + 4, iy + 2,
                        hov ? 0xFFFFFFFF : (item.enabled() ? skin.text() : skin.dim()), false);
            }
            iy += FilesLayout.CTX_ITEM_H;
        }
    }

    // ---- input -----------------------------------------------------------------------------

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
        final int width = contentW;
        final int height = contentH;
        final double lx = mouseX - contentX;
        final double ly = mouseY - contentY;
        searchFocused = false;

        // An open context menu intercepts this click first.
        if (contextOpen) {
            final int item = contextItemAt(lx, ly);
            contextOpen = false;
            if (item >= 0 && ctxItems.get(item).enabled() && !ctxItems.get(item).label().equals("-")) {
                ctxItems.get(item).action().run();
            }
            return;
        }
        if (volContextOpen) {
            final boolean onItem = lx >= volCtxX && lx <= volCtxX + 60
                    && ly >= volCtxY + 1 && ly <= volCtxY + 1 + FilesLayout.CTX_ITEM_H;
            volContextOpen = false;
            if (onItem && volCtxIndex >= 0 && volCtxIndex < volumes.size()) {
                startVolumeRename(volCtxIndex);
            }
            return;
        }
        // The properties panel swallows clicks; its Close button dismisses it.
        if (propsRow != null) {
            if (ly >= FilesLayout.listY()) {
                propsRow = null;
            }
            return;
        }

        // ---- toolbar
        if (ly < FilesLayout.TOOL_H) {
            final Font font = net.minecraft.client.Minecraft.getInstance().font;
            if (inRect(lx, ly, FilesLayout.navX(0), FilesLayout.navY(), FilesLayout.NAV_W, FilesLayout.NAV_H)) {
                goBack();
            } else if (inRect(lx, ly, FilesLayout.navX(1), FilesLayout.navY(), FilesLayout.NAV_W, FilesLayout.NAV_H)) {
                goForward();
            } else if (inRect(lx, ly, FilesLayout.navX(2), FilesLayout.navY(), FilesLayout.NAV_W, FilesLayout.NAV_H)) {
                goUp();
            } else if (inRect(lx, ly, FilesLayout.viewX(width), FilesLayout.navY(), FilesLayout.NAV_W, FilesLayout.NAV_H)) {
                iconView = !iconView;
                scroll = 0;
            } else if (inRect(lx, ly, FilesLayout.searchX(width), FilesLayout.navY(), FilesLayout.SEARCH_W, FilesLayout.NAV_H)) {
                searchFocused = true;
            } else if (inRect(lx, ly, FilesLayout.addressX(), FilesLayout.navY(), FilesLayout.addressW(width), FilesLayout.NAV_H)) {
                final String target = crumbAt(font, contentX + FilesLayout.addressX(), mouseX);
                if (target != null) {
                    go(target);
                }
            }
            return;
        }

        // ---- the drive tree
        if (lx < FilesLayout.TREE_W && ly >= FilesLayout.treeY()) {
            final int idx = (int) Math.floor((ly - FilesLayout.treeY() - 2) / (double) FilesLayout.ROW_H);
            final List<TreeItem> items = tree();
            if (idx >= 0 && idx < items.size() && !items.get(idx).section()) {
                final TreeItem item = items.get(idx);
                if (button == 1 && isVolumeItem(item)) {
                    volCtxIndex = item.volumeIndex();
                    volCtxX = (int) lx;
                    volCtxY = (int) ly;
                    volContextOpen = true;
                } else if (button != 1) {
                    if (item.removable() && lx >= FilesLayout.TREE_W - 11) {
                        eject(item.target());
                    } else {
                        go(item.target());
                    }
                }
            }
            return;
        }

        // ---- the column header sorts
        if (ly >= FilesLayout.colsY() && ly < FilesLayout.listY() && lx >= FilesLayout.listX()) {
            final SortBy by = lx >= FilesLayout.sizeColX(width) ? SortBy.SIZE
                    : (lx >= FilesLayout.typeColX(width) ? SortBy.TYPE : SortBy.NAME);
            if (sortBy == by) {
                sortAscending = !sortAscending;
            } else {
                sortBy = by;
                sortAscending = true;
            }
            applyFilterAndSort();
            return;
        }

        final int index = rowIndexAt(window, mouseX, mouseY);
        final boolean onRow = index >= 0;

        if (button == 1) {
            // Right-click: select the row under the cursor and open the context menu there. A sweep
            // survives only when the menu is opened on one of the rows it selected.
            if (!onRow || !bandRows.contains(index)) {
                bandRows.clear();
            }
            selected = onRow ? index : -1;
            ctxRow = onRow ? index : -1;
            ctxItems = buildContext(onRow ? rows.get(index) : null);
            ctxX = (int) Math.min(lx, width - FilesLayout.CTX_W - 1);
            ctxY = (int) Math.min(ly, height - ctxItems.size() * FilesLayout.CTX_ITEM_H - 3);
            contextOpen = true;
            return;
        }

        if (!onRow) {
            selected = -1;
            bandRows.clear();
            // Pressing empty space in the list starts a sweep. The coordinates are the window's own,
            // the same ones the row hit test uses, so the band lines up with what it selects.
            if (lx >= FilesLayout.listX() && ly >= FilesLayout.listY() && ly < FilesLayout.statusY(height)) {
                bandActive = true;
                bandStartX = mouseX;
                bandStartY = mouseY;
                bandX = mouseX;
                bandY = mouseY;
            }
            return;
        }
        bandRows.clear(); // a plain click on a row replaces whatever a sweep had selected
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

    /** Maps a desktop position to a row index in the file list, or {@code -1} (tree, header, below the rows). */
    private int rowIndexAt(final DesktopWindow window, final double mouseX, final double mouseY) {
        final int lx = window.x() + 4 + FilesLayout.listX();
        if (mouseX < lx) {
            return -1;
        }
        final int listY = window.y() + 18 + FilesLayout.listY();
        if (mouseY < listY) {
            return -1;
        }
        if (iconView) {
            final int lw = contentW - FilesLayout.listX();
            final int cols = Math.max(1, (lw - 4) / 52);
            final int col = (int) ((mouseX - (lx + 2)) / 52);
            final int rowN = (int) ((mouseY - (listY + 2)) / 30);
            if (col < 0 || col >= cols || rowN < 0) {
                return -1;
            }
            final int idx = scroll + rowN * cols + col;
            return idx >= 0 && idx < rows.size() ? idx : -1;
        }
        final int idx = scroll + (int) Math.floor((mouseY - (listY + 1)) / (double) FilesLayout.ROW_H);
        return idx >= 0 && idx < rows.size() ? idx : -1;
    }

    private int contextItemAt(final double lx, final double ly) {
        if (lx < ctxX || lx > ctxX + FilesLayout.CTX_W) {
            return -1;
        }
        final int rel = (int) Math.floor((ly - (ctxY + 1)) / (double) FilesLayout.CTX_ITEM_H);
        return rel >= 0 && rel < ctxItems.size() ? rel : -1;
    }

    /** The context menu for {@code target} (a row, or {@code null} for empty space), greyed where the volume forbids. */
    private List<MenuItem> buildContext(@org.jetbrains.annotations.Nullable final Row target) {
        final boolean ro = readOnlyVolume();
        final List<MenuItem> items = new ArrayList<>();
        if (target != null && target.file() != null) {
            final boolean dat = target.file().projectsItem();
            final boolean setup = isSetup(target);
            final boolean editable = target.kind() == Kind.FILE && !dat && !setup && isText(target.file());
            items.add(new MenuItem(setup ? "Run" : "Open", true, () -> open(target)));
            if (target.kind() == Kind.FILE) {
                items.add(new MenuItem("Open with Editor", editable, () -> openInEditor(target)));
            }
            items.add(MenuItem.separator());
            items.add(new MenuItem("Cut", !ro && !target.file().readOnly(), () -> cut(target)));
            items.add(new MenuItem("Copy", !target.file().readOnly(), () -> copy(target)));
            items.add(new MenuItem("Paste", !clipboard.isEmpty() && !ro, this::paste));
            items.add(MenuItem.separator());
            items.add(new MenuItem("Rename", !ro && !target.file().readOnly(), () -> startRenameAt(rows.indexOf(target))));
            items.add(new MenuItem("Delete", !ro && !target.file().readOnly(), this::deleteContextRow));
            items.add(MenuItem.separator());
            items.add(new MenuItem("Properties", true, () -> propsRow = target));
        } else if (target != null) {
            items.add(new MenuItem("Open", true, () -> open(target)));
            items.add(MenuItem.separator());
        } else {
            items.add(new MenuItem("Paste", !clipboard.isEmpty() && !ro, this::paste));
            items.add(new MenuItem("New File", !ro, this::newFile));
            items.add(new MenuItem("New Folder", !ro, this::newFolder));
            items.add(MenuItem.separator());
            if (onMedia()) {
                items.add(new MenuItem("Eject", true, () -> eject("media:" + mediaReaderPos())));
            }
        }
        items.add(new MenuItem("Refresh", true, () -> request(dir)));
        return items;
    }

    private static boolean isText(final DiskFilesPayload.WireFile f) {
        return switch (f.ext().toLowerCase(Locale.ROOT)) {
            case "bin", "exe", "sh", "dat" -> false;
            default -> true;
        };
    }

    private boolean isSetup(final Row r) {
        return r.kind() == Kind.FILE && r.file() != null && r.file().path().startsWith("media:")
                && dev.jsc.jscomputronics.module.computing.os.fs.InstallerLayout.isSetup(r.file().path());
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        if (bandActive) {
            bandX = mouseX;
            bandY = mouseY;
            updateBandSelection(window);
            return;
        }
        if (renaming >= 0 || dragRow < 0) {
            return;
        }
        dragging = true;
        dragMx = mouseX;
        dragMy = mouseY;
    }

    /** The band's rectangle in desktop coordinates: {x, y, w, h}. */
    private int[] bandRect() {
        final int bx = (int) Math.min(bandStartX, bandX);
        final int by = (int) Math.min(bandStartY, bandY);
        return new int[]{bx, by, (int) Math.abs(bandX - bandStartX), (int) Math.abs(bandY - bandStartY)};
    }

    /** Selects every visible row the band's vertical span crosses. */
    private void updateBandSelection(final DesktopWindow window) {
        bandRows.clear();
        if (iconView) {
            return;
        }
        final int listY = window.y() + 18 + FilesLayout.listY() + 1;
        final int[] r = bandRect();
        // Only rows actually on screen are candidates. Sweeping past the bottom of the list must not
        // reach rows scrolled out of view: they would be deleted without ever having looked selected.
        final int last = Math.min(rows.size(), scroll + visibleRows);
        for (int i = scroll; i < last; i++) {
            final int rowTop = listY + (i - scroll) * FilesLayout.ROW_H;
            if (rowTop >= r[1] + r[3]) {
                break; // past the band; the rows below cannot intersect it either
            }
            if (rowTop + FilesLayout.ROW_H > r[1]) {
                bandRows.add(i);
            }
        }
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY,
                              final int button) {
        if (bandActive) {
            // Letting go ends the sweep; what it crossed stays selected.
            bandActive = false;
            return;
        }
        if (dragging && dragRow >= 0 && dragRow < rows.size()) {
            final Row src = rows.get(dragRow);
            // Where did the drag land: a removable-drive destination (left tree or a media row), or a folder?
            final String mediaDest = mediaDropTarget(window, mouseX, mouseY);
            final String destDir = folderDropTarget(window, mouseX, mouseY);

            final boolean srcIsDat = src.file() != null && src.file().projectsItem();
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
            } else if (src.file() != null && src.file().readOnly()) {
                // An installer's projected file cannot leave its medium.
                DesktopScreen.showInstallerLockedError();
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
     * The destination directory for a cross-window drop landing on this explorer window, or {@code null}
     * when the drop is not a valid target (the left drive tree, a removable-drive volume, or a folder shown
     * as a media drive). A drop onto a real subfolder targets that subfolder; a drop anywhere else in the
     * file list targets the folder currently open. A media volume is excluded here: a desktop file cannot be
     * dropped onto a removable drive through this path (that is the sanctioned medium-transfer flow only).
     */
    @org.jetbrains.annotations.Nullable
    public String crossWindowDropDir(final DesktopWindow window, final double mouseX, final double mouseY) {
        final double lx = mouseX - (window.x() + 4);
        if (lx < FilesLayout.listX()) {
            return null;
        }
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
        return dir;
    }

    /**
     * The removable-medium volume key under the drop point, or {@code null}. A medium is a target either as a
     * drive row in the file list ({@code media:} path) or as a row in the left drive tree.
     */
    @org.jetbrains.annotations.Nullable
    private String mediaDropTarget(final DesktopWindow window, final double mouseX, final double mouseY) {
        final double lx = mouseX - (window.x() + 4);
        final double ly = mouseY - (window.y() + 18);
        if (lx < FilesLayout.TREE_W && ly >= FilesLayout.treeY()) {
            final int idx = (int) Math.floor((ly - FilesLayout.treeY() - 2) / (double) FilesLayout.ROW_H);
            final List<TreeItem> items = tree();
            if (idx >= 0 && idx < items.size() && items.get(idx).removable()) {
                return items.get(idx).target();
            }
            return null;
        }
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

    @Override
    public boolean mouseScrolled(final double delta) {
        scroll -= (int) Math.signum(delta) * (iconView ? Math.max(1, visibleRows / 3) : 1);
        return true;
    }

    // ---- actions ---------------------------------------------------------------------------

    /** Opens a row: a folder navigates; an installer's setup runs; a text file opens in the Editor. */
    private void open(final Row r) {
        switch (r.kind()) {
            case STORAGE -> go("Storage");
            case UP -> goUp();
            case DIR -> {
                if (r.file() != null) {
                    go(r.file().path());
                }
            }
            case FILE -> {
                if (r.file() == null) {
                    return;
                }
                if (isSetup(r)) {
                    runSetup(r.file().path());
                } else if (!r.file().projectsItem() && isText(r.file())) {
                    openInEditor(r);
                }
            }
        }
    }

    private void openInEditor(final Row r) {
        if (r.file() == null) {
            return;
        }
        DesktopScreen.requestOpen("Editor");
        PacketDistributor.sendToServer(new RequestFileContentPayload(host, r.file().path()));
    }

    /** Runs the setup program on an installer medium: the same install This PC's button does. */
    private void runSetup(final String path) {
        final long reader = mediaReaderPos(path);
        if (reader >= 0) {
            PacketDistributor.sendToServer(new InstallFromMediaPayload(host, reader));
            DesktopScreen.refreshActive();
        }
    }

    private void eject(final String volumeKey) {
        final long reader = mediaReaderPos(volumeKey);
        if (reader >= 0) {
            PacketDistributor.sendToServer(new EjectMediaPayload(host, reader));
            if (isCurrentVolume(volumeKey)) {
                go("");
            } else {
                request(dir);
            }
        }
    }

    private void cut(final Row r) {
        clipboard.clear();
        for (final Row s : selection(r)) {
            if (s.file() != null && !s.file().readOnly()) {
                clipboard.add(s.file().path());
            }
        }
        clipboardCut = true;
    }

    private void copy(final Row r) {
        clipboard.clear();
        for (final Row s : selection(r)) {
            if (s.file() != null && !s.file().readOnly()) {
                clipboard.add(s.file().path());
            }
        }
        clipboardCut = false;
    }

    /** The rows an action applies to: the sweep when the target is in it, else the target alone. */
    private List<Row> selection(final Row target) {
        final int idx = rows.indexOf(target);
        if (bandRows.size() > 1 && bandRows.contains(idx)) {
            final List<Row> out = new ArrayList<>();
            for (final int i : bandRows) {
                if (i >= 0 && i < rows.size()) {
                    out.add(rows.get(i));
                }
            }
            return out;
        }
        return List.of(target);
    }

    private void paste() {
        if (clipboard.isEmpty() || readOnlyVolume()) {
            return;
        }
        for (final String src : clipboard) {
            if (clipboardCut) {
                PacketDistributor.sendToServer(new MoveFilePayload(host, src, dir));
            } else {
                PacketDistributor.sendToServer(new CopyFilePayload(host, src, dir));
            }
        }
        if (clipboardCut) {
            clipboard.clear();
        }
        request(dir);
    }

    private void startRenameAt(final int index) {
        if (index < 0 || index >= rows.size()) {
            return;
        }
        final Row r = rows.get(index);
        if (r.file() == null) {
            return; // navigation row
        }
        if (r.file().readOnly()) {
            lockedError(r); // a projection cannot be renamed by hand
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
        // A sweep that selected several rows deletes all of them: selecting many and then acting on
        // one would make the selection a lie.
        if (bandRows.size() > 1 && bandRows.contains(ctxRow)) {
            boolean locked = false;
            for (final int index : bandRows) {
                if (index < 0 || index >= rows.size()) {
                    continue;
                }
                final Row r = rows.get(index);
                if (r.file() == null) {
                    continue;
                }
                if (r.file().readOnly()) {
                    locked = true; // a projection in the sweep is skipped, not silently lost
                    continue;
                }
                PacketDistributor.sendToServer(new DeleteFilePayload(host, r.file().path()));
            }
            if (locked) {
                DesktopScreen.showDatLockedError();
            }
            bandRows.clear();
            request(dir);
            return;
        }
        if (ctxRow < 0 || ctxRow >= rows.size()) {
            return;
        }
        final Row r = rows.get(ctxRow);
        if (r.file() == null) {
            return;
        }
        if (r.file().readOnly()) {
            lockedError(r);
            return;
        }
        PacketDistributor.sendToServer(new DeleteFilePayload(host, r.file().path()));
        request(dir);
    }

    /** The right refusal for a projected entry: a stored item points at the Network Interactor, an installer's file at setup. */
    private static void lockedError(final Row r) {
        if (r.file() != null && r.file().projectsItem()) {
            DesktopScreen.showDatLockedError();
        } else {
            DesktopScreen.showInstallerLockedError();
        }
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
            request(dir);
            return;
        }
        volRenaming = -1;
    }

    // ---- keyboard --------------------------------------------------------------------------

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
        if (searchFocused && c >= 32 && c != 127 && search.length() < 40) {
            search.append(c);
            applyFilterAndSort();
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
        if (renaming >= 0) {
            switch (key) {
                case 257, 335 -> commitRename();
                case 256 -> renaming = -1;
                case 259 -> {
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
        if (searchFocused) {
            switch (key) {
                case 256 -> {                          // Escape clears the search
                    search.setLength(0);
                    searchFocused = false;
                    applyFilterAndSort();
                }
                case 259 -> {
                    if (search.length() > 0) {
                        search.deleteCharAt(search.length() - 1);
                        applyFilterAndSort();
                    }
                }
                case 257, 335 -> searchFocused = false;
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (contextOpen || propsRow != null) {
            if (key == 256) {
                contextOpen = false;
                propsRow = null;
                return true;
            }
            return false;
        }
        final boolean ctrl = (modifiers & 2) != 0;
        switch (key) {
            case 259 -> goUp();                                   // Backspace
            case 257, 335 -> {                                    // Enter opens the selection
                if (selected >= 0 && selected < rows.size()) {
                    open(rows.get(selected));
                }
            }
            case 265 -> moveSelection(-1);                        // Up
            case 264 -> moveSelection(1);                         // Down
            case 261 -> {                                         // Delete
                if (selected >= 0) {
                    ctxRow = selected;
                    deleteContextRow();
                }
            }
            case 291 -> startRenameAt(selected);                  // F2
            case 67 -> {                                          // Ctrl+C
                if (ctrl && selected >= 0 && selected < rows.size()) {
                    copy(rows.get(selected));
                } else {
                    return false;
                }
            }
            case 88 -> {                                          // Ctrl+X
                if (ctrl && selected >= 0 && selected < rows.size() && !readOnlyVolume()) {
                    cut(rows.get(selected));
                } else {
                    return false;
                }
            }
            case 86 -> {                                          // Ctrl+V
                if (ctrl) {
                    paste();
                } else {
                    return false;
                }
            }
            case 65 -> {                                          // Ctrl+A
                if (ctrl) {
                    bandRows.clear();
                    for (int i = 0; i < rows.size(); i++) {
                        if (rows.get(i).kind() == Kind.FILE || rows.get(i).kind() == Kind.DIR) {
                            bandRows.add(i);
                        }
                    }
                } else {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void moveSelection(final int delta) {
        if (rows.isEmpty()) {
            return;
        }
        selected = Math.max(0, Math.min(rows.size() - 1, selected + delta));
        bandRows.clear();
        if (selected < scroll) {
            scroll = selected;
        } else if (selected >= scroll + visibleRows) {
            scroll = selected - visibleRows + 1;
        }
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
        for (final Row r : allRows) {
            if ((r.kind() == Kind.FILE || r.kind() == Kind.DIR) && r.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    // ---- icons and helpers -----------------------------------------------------------------

    private static IconType iconFor(final String ext) {
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "iql" -> IconType.IQL;
            case "dat" -> IconType.DAT;
            case "exe", "sh" -> IconType.EXE;
            case "pkg" -> IconType.PKG;
            case "inf" -> IconType.INF;
            case "bin" -> IconType.BIN;
            case "cfg" -> IconType.CFG;
            case "log" -> IconType.LOG;
            case "craft" -> IconType.CRAFT;
            default -> IconType.DOC;
        };
    }

    /** Draws a small per-type icon (a folder with a tab, or a document with a folded corner). */
    private static void drawIcon(final GuiGraphics g, final int x, final int y, final IconType type) {
        final int w = FilesLayout.ICON_W;
        switch (type) {
            case UP -> {
                g.fill(x, y + 1, x + w, y + 9, 0xFFA8A8A8);
                g.fill(x, y + 1, x + w, y + 2, 0xFFD8D8D8);
                OsSkin.outline(g, x, y + 1, w, 8, 0xFF707070);
                g.fill(x + 5, y + 3, x + 7, y + 8, 0xFF303030);
                g.fill(x + 3, y + 4, x + 9, y + 5, 0xFF303030);
            }
            case FOLDER -> {
                g.fill(x, y + 2, x + 5, y, 0xFFFFE9A8);
                g.fill(x, y + 2, x + w, y + 9, 0xFFF4C842);
                g.fill(x, y + 2, x + w, y + 3, 0xFFFFF3C4);
                OsSkin.outline(g, x, y, w, 9, 0xFF9A7B16);
            }
            case HOME -> {
                // A disk drive: a slab with an activity lamp.
                g.fill(x, y + 1, x + w, y + 9, 0xFF8B93A4);
                g.fill(x + 1, y + 2, x + w - 1, y + 8, 0xFFC7CDDA);
                g.fill(x + 2, y + 3, x + w - 2, y + 4, 0xFFEDF0F6);
                g.fill(x + w - 4, y + 6, x + w - 2, y + 8, 0xFF49E07A);
            }
            case BIN -> {
                // A removable medium or an opaque installer file: a dark cartridge.
                g.fill(x + 1, y, x + w - 1, y + 9, 0xFF2E3238);
                g.fill(x + 3, y + 2, x + w - 3, y + 4, 0xFFB8BEC8);
                OsSkin.outline(g, x + 1, y, w - 2, 9, 0xFF1C1F24);
            }
            case IQL -> doc(g, x, y, 0xFFA9D4FF, 0xFF3A72B0);
            case DAT -> doc(g, x, y, 0xFFBDEEC0, 0xFF4F9B53);
            case EXE -> {
                doc(g, x, y, 0xFFDDE2EC, 0xFF3A4256);
                g.fill(x + 4, y + 3, x + 6, y + 7, 0xFF3A4256);   // a play glyph
                g.fill(x + 6, y + 4, x + 8, y + 6, 0xFF3A4256);
            }
            case PKG -> doc(g, x, y, 0xFFE8DDB5, 0xFF9C7A2B);
            case INF -> doc(g, x, y, 0xFFEDEDED, 0xFF8A93A6);
            case CFG -> doc(g, x, y, 0xFFE3E0F5, 0xFF6C5FB0);
            case LOG -> doc(g, x, y, 0xFFF0E6D6, 0xFFA0865A);
            case CRAFT -> doc(g, x, y, 0xFFFFD9B0, 0xFFC26A1A);
            case DOC -> doc(g, x, y, 0xFFDFE3EA, 0xFF8A93A6);
        }
    }

    private static void doc(final GuiGraphics g, final int x, final int y, final int fill, final int edge) {
        final int w = FilesLayout.ICON_W;
        g.fill(x + 1, y, x + w, y + 9, fill);
        g.fill(x + w - 3, y, x + w, y + 3, 0xFFFFFFFF);
        OsSkin.outline(g, x + 1, y, w - 1, 9, edge);
    }

    private static boolean inRect(final double mx, final double my, final int x, final int y, final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String trim(final Font font, final String s, final int maxW) {
        String out = s;
        while (out.length() > 2 && font.width(out) > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    /** A Windows-style address for the path: {@code C:\dir\} on the disk, the drive's label on media. */
    private String displayPath(final String dir) {
        if (dir.startsWith("media:")) {
            final int slash = dir.indexOf('/');
            final String sub = slash < 0 ? "" : dir.substring(slash + 1).replace('/', '\\') + "\\";
            return volumeLabel(slash < 0 ? dir : dir.substring(0, slash)) + "\\" + sub;
        }
        return "C:\\" + (dir.isEmpty() ? "" : dir.replace('/', '\\') + "\\");
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
