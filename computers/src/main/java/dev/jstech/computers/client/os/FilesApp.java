/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.gui.layout.FilesLayout;
import dev.jstech.computers.operation.payload.CopyFilePayload;
import dev.jstech.computers.operation.payload.DeleteFilePayload;
import dev.jstech.computers.operation.payload.DiskFilesPayload;
import dev.jstech.computers.operation.payload.EjectMediaPayload;
import dev.jstech.computers.operation.payload.InstallFromMediaPayload;
import dev.jstech.computers.operation.payload.MediumTransferPayload;
import dev.jstech.computers.operation.payload.MkdirPayload;
import dev.jstech.computers.operation.payload.MoveFilePayload;
import dev.jstech.computers.operation.payload.RenameFilePayload;
import dev.jstech.computers.operation.payload.RenameVolumePayload;
import dev.jstech.computers.operation.payload.RequestDiskFilesPayload;
import dev.jstech.computers.operation.payload.SaveFilePayload;
import dev.jstech.computers.os.fs.InstallerLayout;
import dev.jstech.computers.os.fs.SystemLayout;
import dev.jstech.core.client.gui.component.Breadcrumbs;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.CellGrid;
import dev.jstech.core.client.gui.component.ColumnHeader;
import dev.jstech.core.client.gui.component.ContextMenu;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.Popup;
import dev.jstech.core.client.gui.component.SearchField;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.Texts;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The file explorer, shown as a desktop window in the Windows-Explorer mould: a toolbar with back,
 * forward and up, a clickable address trail and a search box; a drive tree with quick-access shortcuts,
 * every volume with its letter and an eject control on removable media; a sortable list with name,
 * type and size columns and an icon per file type; and a status bar. A stored item's {@code .dat}
 * shows the item itself with its count. Double-click opens a folder, runs an installer's setup, or
 * opens a text file in the Editor. Right-click opens a context menu with cut, copy, paste, rename,
 * delete, new file, new folder and properties, greyed where the volume forbids them.
 *
 * <p>The toolbar, the trail, the search, the tree, the column header, the list, the icon view, the
 * inline rename fields, the status texts, the context menu and the properties dialog are components;
 * the drag ghost, the drop targets and the rubber band are the explorer's own, since they read the
 * lists' layout rather than draw in it. The geometry lives in {@link FilesLayout}, where a test proves
 * nothing overlaps.
 */
public final class FilesApp implements IDesktopApp {

    private static final long DOUBLE_CLICK_MS = 300L;
    private static final int HISTORY_MAX = 32;
    private static final int ICON_CELL_W = 52;
    private static final int ICON_CELL_H = 30;
    private static final int NAME_MAX = 64;
    private static final int VOLUME_LABEL_MAX = 32;
    private static final int SEARCH_MAX = 40;
    private static final int PROPERTY_ROWS = 5;
    private static final int DROP_TARGET_EDGE = 0xFF2E8B2E;
    private static final int BAND_FILL = 0x334C84F0;
    private static final int BAND_EDGE = 0xCC4C84F0;

    private OsSkin skin = OsSkin.fallback();
    private String os = "frames_95";

    private final BlockPos host;
    /*
     * The monitor the desktop is shown on, needed to authenticate the sanctioned .dat-to-medium item
     * transfer (the server validates the player is within reach of this monitor). May be null when the
     * explorer is opened outside a desktop context.
     */
    @Nullable
    private final BlockPos monitorPos;
    private String dir = "";
    private List<Row> allRows = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();
    private int selected = -1;
    private List<DiskFilesPayload.WireVolume> volumes = new ArrayList<>();

    private int lastClickRow = -1;
    private long lastClickAt;

    // Navigation history for back and forward.
    private final List<String> back = new ArrayList<>();
    private final List<String> forward = new ArrayList<>();

    private boolean iconView;

    /*
     * An inline rename edits the row named by its path, so a listing that arrives meanwhile cannot make
     * the field commit onto a different row.
     */
    private int renaming = -1;
    @Nullable
    private String renamePath;
    private int volRenaming = -1;

    /*
     * Drag-and-drop state: the row picked up on press, whether a drag is in progress, and the
     * current cursor position for the drag ghost.
     */
    private int dragRow = -1;
    private boolean dragging;
    private double dragMx;
    private double dragMy;
    // The folder row a drag hovers, outlined in the list; computed once per frame.
    private int dropTarget = -1;

    /*
     * Rubber-band selection over the file list. Pressing on empty space below the last row starts a
     * sweep; every row it crosses joins the selection. It never starts on a row, so the existing
     * click-and-drag of a file into a folder keeps working untouched.
     */
    private boolean bandActive;
    private double bandStartX;
    private double bandStartY;
    private double bandX;
    private double bandY;
    private final Set<Integer> bandRows = new LinkedHashSet<>();

    // After creating a New File/New Folder, the next listing enters rename on the matching row.
    @Nullable
    private String pendingRename;

    // The clipboard: paths waiting to be pasted, and whether the paste moves them.
    private final List<String> clipboard = new ArrayList<>();
    private boolean clipboardCut;

    // The row the context menu was opened on, for the actions that apply to a sweep.
    private int ctxRow = -1;
    // The lines the properties dialog shows for the row it was opened on.
    private List<String[]> propertyLines = List.of();

    // components
    private final Panel root = new Panel();
    private final Button backButton;
    private final Button forwardButton;
    private final Button upButton;
    private final Button viewButton;
    private final Breadcrumbs address;
    private final SearchField search;
    private final ListView<TreeItem> treeList;
    private final ColumnHeader columns;
    private final ListView<Row> fileList;
    private final CellGrid iconGrid;
    private final TextField renameField;
    private final TextField volumeField;
    private final Label statusLeft;
    private final Label statusRight;
    private final ContextMenu context = new ContextMenu(FilesLayout.CTX_W, FilesLayout.CTX_ITEM_H);
    private final Popup properties;
    private final Label[] propertyKeys = new Label[PROPERTY_ROWS];
    private final Label[] propertyValues = new Label[PROPERTY_ROWS];
    private final Button propertiesClose;

    /*
     * The content rectangle and cursor of the last render: the components are laid out in it, and the
     * click that follows arrives in the same coordinates.
     */
    private int lastX;
    private int lastY;
    private int contentW = FilesLayout.DEFAULT_W;
    private int contentH = FilesLayout.DEFAULT_H;
    private int lastMouseX;
    private int lastMouseY;
    // Where the click being handled landed, for a cell click that reports no position of its own.
    private double clickX;
    private double clickY;

    private enum Kind { UP, STORAGE, DIR, FILE }

    private enum SortBy { NAME, TYPE, SIZE }

    private enum IconType { UP, FOLDER, HOME, IQL, DOC, DAT, EXE, PKG, INF, BIN, CFG, LOG, CRAFT,
        SOURCE, PROGRAM, BUNDLE }

    private record Row(Kind kind, String name, String type, String size, IconType icon,
                       @Nullable DiskFilesPayload.WireFile file, @Nullable ItemStack item) {
    }

    /** One entry of the drive tree: a section title, a quick-access shortcut, or a volume. */
    private record TreeItem(String label, String target, boolean section, boolean removable, int volumeIndex) {
    }

    /** A text field for a file or volume name: a path separator cannot be typed into it. */
    private static final class NameField extends TextField {

        NameField(final int maxLength) {
            super(maxLength);
        }

        @Override
        protected boolean accepts(final char c) {
            return super.accepts(c) && c != '/' && c != '\\';
        }
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
    public FilesApp(final BlockPos host, final String os, final String initialDir, @Nullable final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
        this.os = os;
        this.skin = OsSkin.forDesktop(ResourceLocation.fromNamespaceAndPath("jsc", os));

        backButton = root.add(new Button("<", this::goBack));
        forwardButton = root.add(new Button(">", this::goForward));
        upButton = root.add(new Button("^", this::goUp));
        address = root.add(new Breadcrumbs(this::crumbs, this::go));
        search = root.add(new SearchField(SEARCH_MAX));
        search.setOnEdit(this::applyFilterAndSort);
        search.setOnEscape(() -> {
            search.reset();
            applyFilterAndSort();
        });
        viewButton = root.add(new Button(() -> iconView ? "=" : "#", this::toggleView));

        treeList = root.add(new ListView<TreeItem>(this::tree, FilesLayout.ROW_H, this::renderTreeRow)
                .setPadding(1, 2)
                .setOnClick(this::treeClicked));
        columns = root.add(new ColumnHeader(List.of("Name", "Type", "Size")).setOnSort(column -> applyFilterAndSort()));
        fileList = root.add(new ListView<Row>(() -> rows, FilesLayout.ROW_H, this::renderFileRow)
                .setPadding(1, 1)
                .setOnClick(this::rowClicked));
        iconGrid = root.add(new CellGrid(1, 1, 1, ICON_CELL_W, ICON_CELL_H)
                .setWells(false)
                .setInset(2)
                .setSelected(this::isSelected)
                .setRenderer(this::renderIconCell)
                .setOnClick((index, button, shift) -> rowClicked(index, button, clickX, clickY)));

        renameField = root.add(new NameField(NAME_MAX));
        renameField.setOnCommit(this::commitRename).setOnBlur(this::endRename);
        renameField.setVisible(false);
        volumeField = root.add(new NameField(VOLUME_LABEL_MAX));
        volumeField.setOnCommit(this::commitVolumeRename).setOnBlur(this::endVolumeRename);
        volumeField.setVisible(false);

        statusLeft = root.add(new Label(this::statusLeftText));
        statusRight = root.add(new Label(this::statusRightText, Label.Tone.DIM).setAlign(Label.Align.RIGHT));

        properties = new Popup("Properties", FilesLayout.PROPS_W, FilesLayout.PROPS_H)
                .setDim(0x40000000)
                .setLayouter(this::layoutProperties);
        for (int i = 0; i < PROPERTY_ROWS; i++) {
            final int line = i;
            propertyKeys[i] = properties.add(new Label(() -> propertyText(line, 0), Label.Tone.DIM));
            propertyValues[i] = properties.add(new Label(() -> propertyText(line, 1)));
        }
        propertiesClose = properties.add(new Button("Close", properties::close).setPrimary(true));

        FilesApps.register(this);
        request(initialDir);
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        this.os = osSkin.osPath();
    }

    /** Takes a listing of the folder this explorer is on. */
    void accept(final DiskFilesPayload payload) {
        this.volumes = payload.volumes();
        rebuild(payload.files());
    }

    @Override
    public void onClosed() {
        FilesApps.forget(this);
    }

    /** The names listed right now, top to bottom, which is what a player sees in the window. */
    public List<String> names() {
        final List<String> out = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            if (row.file() != null) {
                out.add(row.name());
            }
        }
        return out;
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
    @Nullable
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

    @Override
    public boolean modalActive() {
        return properties.isOpen();
    }

    // navigation

    @Override
    public void onRestored() {
        FilesApps.register(this);
        request(dir); // the folder may have gained or lost files while the window was away
    }

    private void request(final String target) {
        this.dir = target;
        this.selected = -1;
        fileList.setScroll(0);
        iconGrid.setScroll(0);
        properties.close();
        context.close();
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

    private void toggleView() {
        iconView = !iconView;
        fileList.setScroll(0);
        iconGrid.setScroll(0);
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

    // listing

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
        final String needle = search.query();
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
        final Comparator<Row> order = switch (sortBy()) {
            case TYPE -> Comparator.comparing((Row r) -> r.type().toLowerCase(Locale.ROOT))
                    .thenComparing(r -> r.name().toLowerCase(Locale.ROOT));
            case SIZE -> Comparator.comparingLong((Row r) -> r.file() == null ? 0L
                    : (r.file().projectsItem() ? r.file().count() : r.file().weight()))
                    .thenComparing(r -> r.name().toLowerCase(Locale.ROOT));
            default -> Comparator.comparing((Row r) -> r.name().toLowerCase(Locale.ROOT));
        };
        dirs.sort(columns.ascending() ? order : order.reversed());
        files.sort(columns.ascending() ? order : order.reversed());
        final List<Row> out = new ArrayList<>(nav.size() + dirs.size() + files.size());
        out.addAll(nav);
        out.addAll(dirs);
        out.addAll(files);
        this.rows = out;
        if (selected >= rows.size()) {
            selected = -1;
        }
        bandRows.removeIf(i -> i >= rows.size());
        // The row being renamed follows its path through the rebuild.
        renaming = renamePath == null ? -1 : indexOfPath(renamePath);
    }

    private SortBy sortBy() {
        final SortBy[] all = SortBy.values();
        final int column = columns.sortColumn();
        return column >= 0 && column < all.length ? all[column] : SortBy.NAME;
    }

    private int indexOfPath(final String path) {
        for (int i = 0; i < rows.size(); i++) {
            final Row r = rows.get(i);
            if (r.file() != null && r.file().path().equals(path)) {
                return i;
            }
        }
        return -1;
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
            case "cpk" -> "Program package";
            /*
             * A language names its own files. Whatever is registered gets this for nothing, and the
             * explorer stops needing to know which language the machines happen to speak.
             */
            default -> languageLabel(f.ext());
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

    private boolean isSelected(final int index) {
        return index == selected || bandRows.contains(index);
    }

    // the drive tree

    private List<TreeItem> tree() {
        final List<TreeItem> out = new ArrayList<>();
        out.add(new TreeItem("Quick access", "", true, false, -1));
        out.add(new TreeItem("Desktop", linux() ? SystemLayout.POSIX_DESKTOP_DIR : SystemLayout.DESKTOP_DIR,
                false, false, -1));
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

    /** The index in the tree of the volume with {@code volumeIndex}, or -1. */
    private int treeRowOfVolume(final int volumeIndex) {
        final List<TreeItem> items = tree();
        for (int i = 0; i < items.size(); i++) {
            if (isVolumeItem(items.get(i)) && items.get(i).volumeIndex() == volumeIndex) {
                return i;
            }
        }
        return -1;
    }

    /** The crumbs of the current path, from the root to here. */
    private List<Breadcrumbs.Crumb> crumbs() {
        final List<Breadcrumbs.Crumb> out = new ArrayList<>();
        if (onMedia()) {
            final String rootKey = "media:" + mediaReaderPos();
            out.add(new Breadcrumbs.Crumb(linux() ? "Devices" : "This PC", ""));
            final String letter = letterOf(rootKey);
            out.add(new Breadcrumbs.Crumb(volumeLabel(rootKey) + (letter.isEmpty() ? "" : " (" + letter + ")"), rootKey));
            final int slash = dir.indexOf('/');
            if (slash >= 0) {
                String acc = rootKey;
                for (final String seg : dir.substring(slash + 1).split("/")) {
                    if (seg.isEmpty()) {
                        continue;
                    }
                    acc = acc + "/" + seg;
                    out.add(new Breadcrumbs.Crumb(seg, acc));
                }
            }
            return out;
        }
        out.add(new Breadcrumbs.Crumb(linux() ? "/" : "Local Disk (C:)", ""));
        if (!dir.isEmpty()) {
            String acc = "";
            for (final String seg : dir.split("/")) {
                if (seg.isEmpty()) {
                    continue;
                }
                acc = acc.isEmpty() ? seg : acc + "/" + seg;
                out.add(new Breadcrumbs.Crumb(seg, acc));
            }
        }
        return out;
    }

    // rendering

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        lastX = x;
        lastY = y;
        contentW = width;
        contentH = height;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());
        layout(x, y, width, height);

        // The surfaces the components sit on: the toolbar rule, the tree rail, the list well, the status bar.
        g.fill(x, y + FilesLayout.TOOL_H, x + width, y + FilesLayout.TOOL_H + 1, skin.edge());
        g.fill(treeList.x(), treeList.y(), treeList.right(), treeList.bottom(), skin.listHover());
        g.fill(treeList.right() - 1, treeList.y(), treeList.right(), treeList.bottom(), skin.edge());
        final int lx = x + FilesLayout.listX();
        final int lw = FilesLayout.listW(width);
        final int listY = y + FilesLayout.listY();
        final int listH = FilesLayout.listH(height);
        g.fill(lx, listY, lx + lw, listY + listH, skin.panelBg());
        Draw.outline(g, lx, listY, lw, listH, skin.edge());
        skin.statusBar(g, x, y + FilesLayout.statusY(height), width, FilesLayout.STATUS_H);

        dropTarget = dragging && !iconView ? folderRowAt(dragMx, dragMy) : -1;
        root.render(g, ctx);

        // The rubber band, over the rows it is selecting.
        if (bandActive) {
            final int[] b = bandRect();
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], BAND_FILL);
            Draw.outline(g, b[0], b[1], b[2], b[3], BAND_EDGE);
        }
        if (dragging && dragRow >= 0 && dragRow < rows.size()) {
            final String label = rows.get(dragRow).name();
            final int gw = font.width(label) + 6;
            final int gx = (int) dragMx + 6;
            final int gy = (int) dragMy + 2;
            g.fill(gx, gy, gx + gw, gy + 11, 0xD0303848);
            g.drawString(font, label, gx + 3, gy + 2, 0xFFFFFFFF, false);
        }
        context.render(g, ctx);
    }

    /** Places every component from the content rectangle; the same layout the next click is read against. */
    private void layout(final int x, final int y, final int width, final int height) {
        final int ny = y + FilesLayout.navY();
        backButton.setBounds(x + FilesLayout.navX(0), ny, FilesLayout.NAV_W, FilesLayout.NAV_H);
        backButton.setEnabled(!back.isEmpty());
        forwardButton.setBounds(x + FilesLayout.navX(1), ny, FilesLayout.NAV_W, FilesLayout.NAV_H);
        forwardButton.setEnabled(!forward.isEmpty());
        upButton.setBounds(x + FilesLayout.navX(2), ny, FilesLayout.NAV_W, FilesLayout.NAV_H);
        upButton.setEnabled(!dir.isEmpty());
        address.setBounds(x + FilesLayout.addressX(), ny, FilesLayout.addressW(width), FilesLayout.NAV_H);
        search.setBounds(x + FilesLayout.searchX(width), ny, FilesLayout.SEARCH_W, FilesLayout.NAV_H);
        viewButton.setBounds(x + FilesLayout.viewX(width), ny, FilesLayout.NAV_W, FilesLayout.NAV_H);

        treeList.setBounds(x, y + FilesLayout.treeY(), FilesLayout.TREE_W, FilesLayout.treeH(height));

        final int lx = x + FilesLayout.listX();
        final int lw = FilesLayout.listW(width);
        columns.setBounds(lx, y + FilesLayout.colsY(), lw, FilesLayout.COLS_H);
        columns.setColumnX(lx + 4 + FilesLayout.ICON_W + 3, x + FilesLayout.typeColX(width), x + FilesLayout.sizeColX(width));

        final int listY = y + FilesLayout.listY();
        final int listH = FilesLayout.listH(height);
        fileList.setBounds(lx, listY, lw, listH);
        fileList.setVisible(!iconView);
        final int cols = Math.max(1, (lw - 4) / ICON_CELL_W);
        final int gridRows = Math.max(1, (listH - 2) / ICON_CELL_H);
        iconGrid.setColumns(cols).setVisibleRows(gridRows).setTotalRows((rows.size() + cols - 1) / cols)
                .setCellCount(rows.size()).place(lx + 2, listY + 2);
        iconGrid.setVisible(iconView);

        layoutRenameFields(width);

        final int sy = y + FilesLayout.statusY(height) + 2;
        statusLeft.setBounds(x + 3, sy, width / 2 - 3, 8);
        statusRight.setBounds(x + width / 2, sy, width / 2 - 3, 8);
    }

    /** Puts the inline rename fields over the rows they edit, hidden while their row is out of view. */
    private void layoutRenameFields(final int width) {
        renameField.setVisible(false);
        if (renaming >= 0 && renaming < rows.size()) {
            if (iconView) {
                final int[] c = iconGrid.cellRect(renaming);
                if (c != null) {
                    renameField.setBounds(c[0], c[1] + c[3] - 12, c[2], FilesLayout.ROW_H);
                    renameField.setVisible(true);
                }
            } else if (renaming >= fileList.scroll() && renaming < fileList.scroll() + fileList.visibleRows()) {
                final int[] r = fileList.rowRect(renaming);
                final int nameX = r[0] + 3 + FilesLayout.ICON_W + 3;
                renameField.setBounds(nameX - 3, r[1], FilesLayout.nameMaxW(width) + 6, FilesLayout.ROW_H);
                renameField.setVisible(true);
            }
        }
        volumeField.setVisible(false);
        if (volRenaming >= 0 && volRenaming < volumes.size()) {
            final int row = treeRowOfVolume(volRenaming);
            if (row >= treeList.scroll() && row < treeList.scroll() + treeList.visibleRows()) {
                final int[] r = treeList.rowRect(row);
                final boolean removable = volumes.get(volRenaming).removable();
                final int fx = r[0] + FilesLayout.ICON_W;
                volumeField.setBounds(fx, r[1], r[0] + r[2] - fx - (removable ? 10 : 1), FilesLayout.ROW_H);
                volumeField.setVisible(true);
            }
        }
    }

    private void renderTreeRow(final GuiGraphics g, final UiContext ctx, final TreeItem item, final int index,
                               final int x, final int y, final int w, final int h, final boolean hovered,
                               final boolean selectedRow) {
        if (item.section()) {
            g.drawString(ctx.font(), item.label().toUpperCase(Locale.ROOT), x + 3, y + 3, ctx.skin().dim(), false);
            return;
        }
        final boolean cur = item.target().isEmpty() ? (dir.isEmpty() && isVolumeItem(item))
                : isVolumeItem(item) ? isCurrentVolume(item.target()) : dir.equals(item.target());
        ctx.skin().listRow(g, x, y, w, h, hovered, cur);
        drawIcon(g, x + 2, y + 1, item.target().equals("Storage") ? IconType.DAT
                : (isVolumeItem(item) ? (item.removable() ? IconType.BIN : IconType.HOME) : IconType.FOLDER));
        if (!(isVolumeItem(item) && item.volumeIndex() == volRenaming)) {
            final int maxW = w + 2 - (FilesLayout.ICON_W + 8) - (item.removable() ? 8 : 0);
            g.drawString(ctx.font(), Texts.clip(ctx.font(), item.label(), maxW), x + 3 + FilesLayout.ICON_W, y + 2,
                    ctx.skin().listRowText(cur), false);
        }
        if (item.removable()) {
            // The eject control at the row's right edge: a tray glyph.
            final int ex = x + w - 8;
            final int c = cur ? ctx.skin().listRowText(true) : ctx.skin().dim();
            g.fill(ex + 2, y + 3, ex + 4, y + 4, c);
            g.fill(ex + 1, y + 4, ex + 5, y + 5, c);
            g.fill(ex, y + 5, ex + 6, y + 6, c);
            g.fill(ex, y + 7, ex + 6, y + 8, c);
        }
    }

    /** Whether {@code key} is the volume currently being browsed (system disk = any non-media path). */
    private boolean isCurrentVolume(final String key) {
        if (key.isEmpty()) {
            return !dir.startsWith("media:");
        }
        return dir.equals(key) || dir.startsWith(key + "/");
    }

    private void renderFileRow(final GuiGraphics g, final UiContext ctx, final Row r, final int index, final int x,
                               final int y, final int w, final int h, final boolean hovered, final boolean selectedRow) {
        final boolean sel = isSelected(index);
        ctx.skin().listRow(g, x, y, w, h, hovered && index != renaming, sel);
        if (index == dropTarget) {
            Draw.outline(g, x, y, w, h, DROP_TARGET_EDGE);
        }
        if (r.item() != null) {
            DesktopItems.item(g, r.item(), x + 1, y - 3);
        } else {
            drawIcon(g, x + 2, y + 1, r.icon());
        }
        final boolean ro = r.file() != null && r.file().readOnly();
        final int nameColor = sel ? ctx.skin().listRowText(true) : (ro ? ctx.skin().dim() : ctx.skin().text());
        final int subColor = sel ? ctx.skin().listRowText(true) : ctx.skin().dim();
        if (index != renaming) {
            g.drawString(ctx.font(), Texts.clip(ctx.font(), r.name(), FilesLayout.nameMaxW(contentW)),
                    x + 3 + FilesLayout.ICON_W + 3, y + 2, nameColor, false);
        }
        g.drawString(ctx.font(), Texts.clip(ctx.font(), r.type(), FilesLayout.TYPE_COL_W - 4),
                lastX + FilesLayout.typeColX(contentW), y + 2, subColor, false);
        g.drawString(ctx.font(), r.size(), lastX + contentW - 4 - ctx.font().width(r.size()), y + 2, subColor, false);
    }

    private void renderIconCell(final GuiGraphics g, final UiContext ctx, final int index, final int cx, final int cy,
                                final int w, final int h, final boolean hovered) {
        if (index >= rows.size()) {
            return;
        }
        final Row r = rows.get(index);
        if (r.item() != null) {
            DesktopItems.item(g, r.item(), cx + w / 2 - 8, cy + 2);
        } else {
            drawIcon(g, cx + w / 2 - FilesLayout.ICON_W / 2, cy + 4, r.icon());
        }
        if (index != renaming) {
            final String label = Texts.clip(ctx.font(), r.name(), w - 4);
            g.drawString(ctx.font(), label, cx + (w - ctx.font().width(label)) / 2, cy + h - 9,
                    ctx.skin().listRowText(isSelected(index)), false);
        }
    }

    private String statusLeftText() {
        int items = 0;
        for (final Row r : rows) {
            if (r.kind() == Kind.FILE || r.kind() == Kind.DIR) {
                items++;
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
        return left;
    }

    private String statusRightText() {
        if (readOnlyVolume()) {
            return "read-only medium";
        }
        long used = 0L;
        for (final Row r : rows) {
            if (r.kind() == Kind.FILE && r.file() != null) {
                used += r.file().weight();
            }
        }
        return used + " mB used";
    }

    @Override
    public void renderModal(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                            final int height, final int mouseX, final int mouseY) {
        if (properties.isOpen()) {
            properties.renderIn(g, new UiContext(skin, font, mouseX, mouseY, 0f), x, y, width, height);
        }
    }

    private void layoutProperties(final Popup p) {
        int ly = p.contentTop();
        for (int i = 0; i < PROPERTY_ROWS; i++) {
            propertyKeys[i].setBounds(p.x() + 4, ly, 38, 8);
            propertyValues[i].setBounds(p.x() + 44, ly, p.width() - 48, 8);
            ly += 10;
        }
        propertiesClose.setBounds(p.right() - 40, p.bottom() - 15, 36, 11);
    }

    private String propertyText(final int line, final int column) {
        return line < propertyLines.size() ? propertyLines.get(line)[column] : "";
    }

    private void openProperties(final Row r) {
        final List<String[]> out = new ArrayList<>();
        out.add(new String[] {"Name", r.name()});
        out.add(new String[] {"Type", r.type()});
        if (r.file() != null) {
            out.add(new String[] {"Size", r.file().projectsItem() ? r.file().count() + " items" : r.file().weight() + " mB"});
            out.add(new String[] {"Where", displayPath(parentOf(r.file().path()))});
            out.add(new String[] {"Access", r.file().readOnly() ? "read-only" : "read/write"});
        }
        propertyLines = out;
        properties.open();
        properties.placeIn(lastX, lastY, contentW, contentH);
    }

    // input

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        // An open context menu takes the click first, wherever it lands; then the properties dialog.
        if (context.isOpen()) {
            context.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (properties.isOpen()) {
            properties.mouseClicked(mouseX, mouseY, button);
            return;
        }
        clickX = mouseX;
        clickY = mouseY;
        if (!root.mouseClicked(mouseX, mouseY, button) && iconView && inListWell(mouseX, mouseY)) {
            // The icon view reports no click past its last tile; the rest of the well is the list's empty space.
            rowClicked(-1, button, mouseX, mouseY);
        }
    }

    /** Whether the point is in the list's well: the area the rows or the tiles are shown in. */
    private boolean inListWell(final double mx, final double my) {
        return mx >= lastX + FilesLayout.listX() && my >= lastY + FilesLayout.listY()
                && my < lastY + FilesLayout.statusY(contentH);
    }

    private void treeClicked(final int index, final int button, final double mx, final double my) {
        final List<TreeItem> items = tree();
        if (index < 0 || index >= items.size() || items.get(index).section()) {
            return;
        }
        final TreeItem item = items.get(index);
        if (button == 1) {
            if (isVolumeItem(item)) {
                final int volume = item.volumeIndex();
                openContext(List.of(new ContextMenu.Item("Rename", true, () -> startVolumeRename(volume))), mx, my);
            }
            return;
        }
        if (item.removable() && mx >= treeList.right() - 11) {
            eject(item.target());
        } else {
            go(item.target());
        }
    }

    /** A click on row {@code index} of the list or the icon view, or on their empty space (-1). */
    private void rowClicked(final int index, final int button, final double mx, final double my) {
        final boolean onRow = index >= 0 && index < rows.size();
        if (button == 1) {
            /*
             * Right-click: select the row under the cursor and open the context menu there. A sweep
             * survives only when the menu is opened on one of the rows it selected.
             */
            if (!onRow || !bandRows.contains(index)) {
                bandRows.clear();
            }
            selected = onRow ? index : -1;
            ctxRow = onRow ? index : -1;
            openContext(buildContext(onRow ? rows.get(index) : null), mx, my);
            return;
        }
        if (!onRow) {
            selected = -1;
            bandRows.clear();
            /*
             * Pressing empty space in the list starts a sweep, in the coordinates the row hit test uses,
             * so the band lines up with what it selects.
             */
            if (!iconView) {
                bandActive = true;
                bandStartX = mx;
                bandStartY = my;
                bandX = mx;
                bandY = my;
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
            open(r);
        }
    }

    private void openContext(final List<ContextMenu.Item> items, final double mx, final double my) {
        context.open(items, (int) mx, (int) my, lastX, lastY, contentW, contentH);
    }

    /** The context menu for {@code target} (a row, or {@code null} for empty space), greyed where the volume forbids. */
    private List<ContextMenu.Item> buildContext(@Nullable final Row target) {
        final boolean ro = readOnlyVolume();
        final List<ContextMenu.Item> items = new ArrayList<>();
        if (target != null && target.file() != null) {
            final boolean dat = target.file().projectsItem();
            final boolean setup = isSetup(target);
            final boolean program = target.kind() == Kind.FILE && isProgram(target.file());
            items.add(new ContextMenu.Item(setup || program ? "Run" : "Open", true, () -> open(target)));
            if (target.kind() == Kind.FILE && !dat && !setup) {
                /*
                 * One entry per program on this machine that can open the kind, so a player picks the
                 * one they want rather than getting whichever the desktop would have chosen.
                 */
                final String path = target.file().path();
                for (final String programId : dev.jstech.computers.os.fs.FileOpeners.available(
                        path, DesktopScreen.installedProgramIds())) {
                    items.add(new ContextMenu.Item("Open with " + DesktopScreen.openerName(programId), true,
                            () -> DesktopScreen.requestOpenFileWith(programId, path)));
                }
            }
            items.add(ContextMenu.Item.separator());
            items.add(new ContextMenu.Item("Cut", !ro && !target.file().readOnly(), () -> cut(target)));
            items.add(new ContextMenu.Item("Copy", !target.file().readOnly(), () -> copy(target)));
            items.add(new ContextMenu.Item("Paste", !clipboard.isEmpty() && !ro, this::paste));
            items.add(ContextMenu.Item.separator());
            items.add(new ContextMenu.Item("Rename", !ro && !target.file().readOnly(), () -> startRenameAt(rows.indexOf(target))));
            items.add(new ContextMenu.Item("Delete", !ro && !target.file().readOnly(), this::deleteContextRow));
            items.add(ContextMenu.Item.separator());
            items.add(new ContextMenu.Item("Properties", true, () -> openProperties(target)));
        } else if (target != null) {
            items.add(new ContextMenu.Item("Open", true, () -> open(target)));
            items.add(ContextMenu.Item.separator());
        } else {
            items.add(new ContextMenu.Item("Paste", !clipboard.isEmpty() && !ro, this::paste));
            for (final dev.jstech.computers.os.fs.FileType type
                    : dev.jstech.computers.os.fs.FileOpeners.creatable()) {
                items.add(new ContextMenu.Item("New ." + type.extension(), !ro, () -> newFile(type)));
            }
            items.add(new ContextMenu.Item("New Folder", !ro, this::newFolder));
            items.add(ContextMenu.Item.separator());
            if (onMedia()) {
                items.add(new ContextMenu.Item("Eject", true, () -> eject("media:" + mediaReaderPos())));
            }
        }
        items.add(new ContextMenu.Item("Refresh", true, () -> request(dir)));
        return items;
    }

    /**
     * Whether this is something the machine can run.
     *
     * <p>Asked of the languages the machines know rather than of a list of extensions here, so opening a
     * file written in a language an addon brought does the same thing as opening one of ours.
     */
    private static boolean isProgram(final DiskFilesPayload.WireFile f) {
        return dev.jstech.core.JsCore.languages().runnerOf(f.ext().toLowerCase(Locale.ROOT)) != null;
    }

    private boolean isSetup(final Row r) {
        return r.kind() == Kind.FILE && r.file() != null && r.file().path().startsWith("media:")
                && InstallerLayout.isSetup(r.file().path());
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (context.isOpen()) {
            return;
        }
        if (properties.isOpen()) {
            properties.mouseDragged(mouseX, mouseY, button);
            return;
        }
        if (root.mouseDragged(mouseX, mouseY, button)) {
            return;
        }
        if (bandActive) {
            bandX = mouseX;
            bandY = mouseY;
            updateBandSelection();
            return;
        }
        if (renaming >= 0 || dragRow < 0) {
            return;
        }
        dragging = true;
        dragMx = mouseX;
        dragMy = mouseY;
    }

    /** The band's rectangle: {x, y, w, h}. */
    private int[] bandRect() {
        final int bx = (int) Math.min(bandStartX, bandX);
        final int by = (int) Math.min(bandStartY, bandY);
        return new int[] {bx, by, (int) Math.abs(bandX - bandStartX), (int) Math.abs(bandY - bandStartY)};
    }

    /** Selects every visible row the band's vertical span crosses. */
    private void updateBandSelection() {
        bandRows.clear();
        if (iconView) {
            return;
        }
        final int[] r = bandRect();
        /*
         * Only rows actually on screen are candidates. Sweeping past the bottom of the list must not
         * reach rows scrolled out of view: they would be deleted without ever having looked selected.
         */
        final int first = fileList.scroll();
        final int last = Math.min(rows.size(), first + fileList.visibleRows());
        for (int i = first; i < last; i++) {
            final int rowTop = fileList.rowRect(i)[1];
            if (rowTop >= r[1] + r[3]) {
                break; // past the band; the rows below cannot intersect it either
            }
            if (rowTop + FilesLayout.ROW_H > r[1]) {
                bandRows.add(i);
            }
        }
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (properties.isOpen()) {
            properties.mouseReleased(mouseX, mouseY, button);
            return;
        }
        root.mouseReleased(mouseX, mouseY, button);
        if (bandActive) {
            // Letting go ends the sweep; what it crossed stays selected.
            bandActive = false;
            return;
        }
        if (dragging && dragRow >= 0 && dragRow < rows.size()) {
            final Row src = rows.get(dragRow);
            // Where did the drag land: a removable-drive destination (left tree or a media row), or a folder?
            final String mediaDest = mediaDropTarget(mouseX, mouseY);
            final String destDir = folderDropTarget(mouseX, mouseY);

            final boolean srcIsDat = src.file() != null && src.file().projectsItem();
            if (srcIsDat) {
                if (mediaDest != null) {
                    /*
                     * The one sanctioned .dat action: drop onto a removable medium fires a conservative item
                     * transfer (the stored item leaves the computer and appears on the medium), not a file move.
                     */
                    if (monitorPos != null) {
                        PacketDistributor.sendToServer(new MediumTransferPayload(host, monitorPos, src.file().path(), mediaDest));
                        FilesApps.diskChanged();
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
                FilesApps.diskChanged();
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
    @Nullable
    public String crossWindowDropDir(final DesktopWindow window, final double mouseX, final double mouseY) {
        if (mouseX < lastX + FilesLayout.listX()) {
            return null;
        }
        if (dir.startsWith("media:")) {
            return null;
        }
        final int row = rowIndexAt(mouseX, mouseY);
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

    /** The row of the list or the tile of the icon view under the point, whichever is shown, or -1. */
    private int rowIndexAt(final double mx, final double my) {
        return iconView ? iconGrid.cellAt(mx, my) : fileList.rowAt(mx, my);
    }

    /** The folder row (or the up row) under the point that a dragged row could drop into, or -1. */
    private int folderRowAt(final double mx, final double my) {
        final int index = rowIndexAt(mx, my);
        if (index < 0 || index == dragRow) {
            return -1;
        }
        final Kind k = rows.get(index).kind();
        return k == Kind.DIR || k == Kind.UP ? index : -1;
    }

    /**
     * The removable-medium volume key under the drop point, or {@code null}. A medium is a target either as a
     * drive row in the file list ({@code media:} path) or as a row in the left drive tree.
     */
    @Nullable
    private String mediaDropTarget(final double mouseX, final double mouseY) {
        if (treeList.contains(mouseX, mouseY)) {
            final int index = treeList.rowAt(mouseX, mouseY);
            final List<TreeItem> items = tree();
            if (index >= 0 && index < items.size() && items.get(index).removable()) {
                return items.get(index).target();
            }
            return null;
        }
        final int row = rowIndexAt(mouseX, mouseY);
        if (row >= 0 && row != dragRow) {
            final Row t = rows.get(row);
            if (t.kind() == Kind.DIR && t.file() != null && t.file().path().startsWith("media:")) {
                return t.file().path();
            }
        }
        return null;
    }

    /** The real folder directory under the drop point, or {@code null} (used for ordinary file/folder moves). */
    @Nullable
    private String folderDropTarget(final double mouseX, final double mouseY) {
        final int target = folderRowAt(mouseX, mouseY);
        if (target < 0) {
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
        if (properties.isOpen()) {
            return true;
        }
        if (root.mouseScrolled(lastMouseX, lastMouseY, delta)) {
            return true;
        }
        // The wheel anywhere else in the window moves the listing.
        final int step = delta > 0 ? -1 : 1;
        if (iconView) {
            iconGrid.scrollBy(step);
        } else {
            fileList.setScroll(fileList.scroll() + step);
        }
        return true;
    }

    // actions

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
                } else if (!r.file().projectsItem()) {
                    /*
                     * Which program opens a kind of file is the desktop's one answer, so the explorer
                     * asks it rather than keeping a second opinion that would disagree with a
                     * double-click on the desktop.
                     */
                    DesktopScreen.requestOpenFile(r.file().path());
                }
            }
        }
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
        FilesApps.diskChanged();
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
        renamePath = r.file().path();
        /*
         * The whole name is edited, extension included. The extension is what decides which program
         * opens a file, so keeping it out of reach made a text file that should have been a program
         * into one that could only be deleted and made again.
         */
        renameField.set(r.name());
        root.focus(renameField);
    }

    /** The rename field committing: the new name, whole. */
    private void commitRename(final String name) {
        final String oldPath = renamePath;
        if (oldPath == null || name.trim().isEmpty()) {
            return;
        }
        final int slash = oldPath.lastIndexOf('/');
        final String prefix = slash >= 0 ? oldPath.substring(0, slash + 1) : "";
        final String newPath = prefix + name.trim();
        if (!newPath.equals(oldPath)) {
            PacketDistributor.sendToServer(new RenameFilePayload(host, oldPath, newPath));
            FilesApps.diskChanged();
        }
    }

    private void endRename() {
        renaming = -1;
        renamePath = null;
        renameField.setVisible(false);
    }

    private void deleteContextRow() {
        /*
         * A sweep that selected several rows deletes all of them: selecting many and then acting on
         * one would make the selection a lie.
         */
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
            FilesApps.diskChanged();
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
        FilesApps.diskChanged();
    }

    /** The right refusal for a projected entry: a stored item points at the Network Interactor, an installer's file at setup. */
    private static void lockedError(final Row r) {
        if (r.file() != null && r.file().projectsItem()) {
            DesktopScreen.showDatLockedError();
        } else {
            DesktopScreen.showInstallerLockedError();
        }
    }

    /**
     * Makes an empty file of that kind, and puts the cursor in its name.
     *
     * <p>The kind is chosen before the file exists, because the extension decides which program opens
     * it, and a file made as text and renamed afterwards is a rename the player should not have had to do.
     */
    public void newFile(final dev.jstech.computers.os.fs.FileType type) {
        final String name = uniqueName("New File", "." + type.extension());
        pendingRename = name;
        PacketDistributor.sendToServer(new SaveFilePayload(host, join(dir, name), ""));
        FilesApps.diskChanged();
    }

    private void newFolder() {
        final String name = uniqueName("New Folder", "");
        pendingRename = name;
        PacketDistributor.sendToServer(new MkdirPayload(host, join(dir, name)));
        FilesApps.diskChanged();
    }

    private void startVolumeRename(final int index) {
        if (index < 0 || index >= volumes.size()) {
            return;
        }
        volRenaming = index;
        volumeField.set(volumes.get(index).label());
        root.focus(volumeField);
    }

    private void commitVolumeRename(final String label) {
        if (volRenaming >= 0 && volRenaming < volumes.size()) {
            PacketDistributor.sendToServer(new RenameVolumePayload(host, volumes.get(volRenaming).key(), label.trim()));
            FilesApps.diskChanged();
        }
    }

    private void endVolumeRename() {
        volRenaming = -1;
        volumeField.setVisible(false);
    }

    // keyboard

    @Override
    public boolean charTyped(final char c) {
        if (properties.isOpen()) {
            return properties.charTyped(c);
        }
        return root.charTyped(c);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (context.isOpen()) {
            return context.keyPressed(key, scanCode, modifiers);
        }
        if (properties.isOpen()) {
            return properties.keyPressed(key, scanCode, modifiers);
        }
        if (root.keyPressed(key, scanCode, modifiers)) {
            return true; // a field being typed in
        }
        final boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> goUp();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (selected >= 0 && selected < rows.size()) {
                    open(rows.get(selected));
                }
            }
            case GLFW.GLFW_KEY_UP -> moveSelection(-1);
            case GLFW.GLFW_KEY_DOWN -> moveSelection(1);
            case GLFW.GLFW_KEY_DELETE -> {
                if (selected >= 0) {
                    ctxRow = selected;
                    deleteContextRow();
                }
            }
            case GLFW.GLFW_KEY_F2 -> startRenameAt(selected);
            case GLFW.GLFW_KEY_C -> {
                if (ctrl && selected >= 0 && selected < rows.size()) {
                    copy(rows.get(selected));
                } else {
                    return false;
                }
            }
            case GLFW.GLFW_KEY_X -> {
                if (ctrl && selected >= 0 && selected < rows.size() && !readOnlyVolume()) {
                    cut(rows.get(selected));
                } else {
                    return false;
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    paste();
                } else {
                    return false;
                }
            }
            case GLFW.GLFW_KEY_A -> {
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
        if (iconView) {
            final int row = selected / iconGrid.columns();
            if (row < iconGrid.scroll()) {
                iconGrid.setScroll(row);
            } else if (row >= iconGrid.scroll() + iconGrid.visibleRows()) {
                iconGrid.setScroll(row - iconGrid.visibleRows() + 1);
            }
            return;
        }
        if (selected < fileList.scroll()) {
            fileList.setScroll(selected);
        } else if (selected >= fileList.scroll() + fileList.visibleRows()) {
            fileList.setScroll(selected - fileList.visibleRows() + 1);
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

    // icons and helpers

    /** What to call a file of a language the machines know, or a plain description when they know none. */
    private static String languageLabel(final String ext) {
        final String lower = ext.toLowerCase(Locale.ROOT);
        final var language = dev.jstech.core.JsCore.languages().byExtension(lower);
        if (language != null) {
            return language.displayName()
                    + (language.sourceExtensions().contains(lower) ? " source" : " program");
        }
        return lower.isEmpty() ? "File" : lower.toUpperCase(Locale.ROOT) + " file";
    }

    private static IconType iconFor(final String ext) {
        final String lower = ext.toLowerCase(Locale.ROOT);
        final var language = dev.jstech.core.JsCore.languages().byExtension(lower);
        if (language != null) {
            return language.sourceExtensions().contains(lower) ? IconType.SOURCE : IconType.PROGRAM;
        }
        return switch (lower) {
            case "iql" -> IconType.IQL;
            case "dat" -> IconType.DAT;
            case "exe", "sh" -> IconType.EXE;
            case "pkg" -> IconType.PKG;
            case "inf" -> IconType.INF;
            case "bin" -> IconType.BIN;
            case "cfg" -> IconType.CFG;
            case "log" -> IconType.LOG;
            case "craft" -> IconType.CRAFT;
            case "cpk" -> IconType.BUNDLE;
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
                Draw.outline(g, x, y + 1, w, 8, 0xFF707070);
                g.fill(x + 5, y + 3, x + 7, y + 8, 0xFF303030);
                g.fill(x + 3, y + 4, x + 9, y + 5, 0xFF303030);
            }
            case FOLDER -> {
                g.fill(x, y + 2, x + 5, y, 0xFFFFE9A8);
                g.fill(x, y + 2, x + w, y + 9, 0xFFF4C842);
                g.fill(x, y + 2, x + w, y + 3, 0xFFFFF3C4);
                Draw.outline(g, x, y, w, 9, 0xFF9A7B16);
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
                Draw.outline(g, x + 1, y, w - 2, 9, 0xFF1C1F24);
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
            // Something a person writes: a page with two lines of writing on it.
            case SOURCE -> {
                doc(g, x, y, 0xFFCFE6D8, 0xFF2E7D5B);
                g.fill(x + 3, y + 4, x + 8, y + 5, 0xFF2E7D5B);
                g.fill(x + 3, y + 6, x + 7, y + 7, 0xFF2E7D5B);
            }
            // Something a machine runs: the same page with the play mark an installer wears.
            case PROGRAM -> {
                doc(g, x, y, 0xFFD8E6CF, 0xFF4C7D2E);
                g.fill(x + 4, y + 3, x + 6, y + 8, 0xFF4C7D2E);
                g.fill(x + 6, y + 4, x + 8, y + 7, 0xFF4C7D2E);
            }
            // Something with other things inside it: a page with a band across it, like a parcel.
            case BUNDLE -> {
                doc(g, x, y, 0xFFE6DCCF, 0xFF7D5B2E);
                g.fill(x + 1, y + 5, x + 10, y + 6, 0xFF7D5B2E);
                g.fill(x + 5, y + 2, x + 6, y + 9, 0xFF7D5B2E);
            }
            case DOC -> doc(g, x, y, 0xFFDFE3EA, 0xFF8A93A6);
        }
    }

    private static void doc(final GuiGraphics g, final int x, final int y, final int fill, final int edge) {
        final int w = FilesLayout.ICON_W;
        g.fill(x + 1, y, x + w, y + 9, fill);
        g.fill(x + w - 3, y, x + w, y + 3, 0xFFFFFFFF);
        Draw.outline(g, x + 1, y, w - 1, 9, edge);
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
