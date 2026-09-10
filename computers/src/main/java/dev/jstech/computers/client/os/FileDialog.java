/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.operation.payload.DiskFilesPayload;
import dev.jstech.computers.operation.payload.MkdirPayload;
import dev.jstech.computers.operation.payload.RequestDiskFilesPayload;
import dev.jstech.computers.os.fs.SystemLayout;
import dev.jstech.core.JsCore;
import dev.jstech.core.client.gui.component.Breadcrumbs;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.ColumnHeader;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.language.IProgrammingLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The system's own window for choosing a file, a folder, or where to save: the one every program
 * opens rather than each asking in its own way.
 *
 * <p>It reads the way the desktop's file dialogs read. On the left the places, the same the explorer
 * lists: the quick ones and the disks. Along the top the way back, forward and up, and the address as
 * crumbs that can be typed over. In the middle the folder's contents with the explorer's icons and
 * columns, folders first and only the files of the kind asked for. Along the bottom the name, the kind,
 * and the button that does what the window was opened for: Open, Save or Select Folder.
 *
 * <p>A program keeps one and opens it with what it wants and a callback. It comes up as a window of
 * its own over the program's, listed with the program on the panel, and holds the program until it
 * is answered or put away, the way an Open window holds the program that opened it. It is drawn with
 * the components the desktop's skin draws, so it wears the system's clothes on every desktop.
 */
public final class FileDialog implements IDesktopApp, CodeFileReplies.IReader {

    /** What the window is for, which decides its button, its filter and what a double click does. */
    public enum Mode { OPEN_FILE, OPEN_FOLDER, SAVE }

    /**
     * One entry of the kind list: what it says and the extensions it shows, lower-case and without the
     * dot; none means every file.
     */
    public record Filter(String label, List<String> extensions) {

        public Filter {
            extensions = List.copyOf(extensions);
        }

        /** Every file, whatever it is. */
        public static final Filter ALL = new Filter("All files (*.*)", List.of());

        /** Whether a file with {@code ext} is shown under this filter. */
        public boolean admits(final String ext) {
            return this.extensions.isEmpty() || this.extensions.contains(ext.toLowerCase(Locale.ROOT));
        }

        /** A filter for the extensions given, labelled with them: {@code Text (*.txt)}. */
        public static Filter of(final String name, final String... extensions) {
            final StringBuilder label = new StringBuilder(name).append(" (");
            for (int i = 0; i < extensions.length; i++) {
                label.append(i > 0 ? ", " : "").append("*.").append(extensions[i]);
            }
            return new Filter(label.append(')').toString(), List.of(extensions));
        }

        /** One filter per language the machine knows, its sources by their extensions, then every file. */
        public static List<Filter> sources() {
            final List<Filter> out = new ArrayList<>();
            for (final IProgrammingLanguage language : JsCore.languages().all()) {
                final List<String> extensions = new ArrayList<>(language.sourceExtensions());
                java.util.Collections.sort(extensions);
                out.add(of(language.displayName(), extensions.toArray(new String[0])));
            }
            out.add(ALL);
            return out;
        }
    }

    /** One row of the list: a file or folder here, or the way up. */
    private record Entry(String name, boolean up, DiskFilesPayload.WireFile file) {
    }

    /** One row of the places: what it says, its icon, and where it goes; a heading goes nowhere. */
    private record Place(String label, FileIcons.Kind icon, String target, boolean heading) {
    }

    /**
     * The content the window opens with: wide enough for the places and three columns, and smaller
     * than the windows of the programs that open it, so the program still shows around it.
     */
    private static final int W = 280;
    private static final int H = 160;
    private static final int ROW_H = 11;
    private static final int PLACES_W = 82;
    private static final int TYPE_W = 78;
    private static final int SIZE_W = 30;
    /** The width of the labels in front of the name and the kind: "File name:" has to fit. */
    private static final int LABEL_W = 54;
    private static final int HISTORY_MAX = 32;

    private final BlockPos host;
    /** The program that opened this window, whose window it sits over and holds. */
    private final IDesktopApp owner;
    private final Panel root = new Panel();
    private final Button back;
    private final Button forward;
    private final Button up;
    private final Breadcrumbs address;
    private final TextField addressEdit;
    private final ListView<Place> places;
    private final ColumnHeader header;
    private final ListView<Entry> rows;
    private final Label nameLabel;
    private final TextField name;
    private final Label kindLabel;
    private final Button kind;
    private final Button newFolder;
    private final Button primary;
    private final Button cancel;
    private final Label status;

    private OsSkin skin = OsSkin.fallback();
    private Mode mode = Mode.OPEN_FILE;
    private String title = "Open";
    private List<Filter> filters = List.of(Filter.ALL);
    private int filter;
    private Consumer<String> onPick = path -> { };
    /** Whether the window is up on the desktop. */
    private boolean open;
    /** Whether the desktop is a Linux one, read from the skin: the Linux ones name their places differently. */
    private boolean posix;

    private String dir = "";
    private final List<DiskFilesPayload.WireFile> listed = new ArrayList<>();
    private final List<DiskFilesPayload.WireVolume> volumes = new ArrayList<>();
    private final List<String> backStack = new ArrayList<>();
    private final List<String> forwardStack = new ArrayList<>();
    private String message = "";
    /** The path Save was pressed on once and would replace, so a second press is the answer. */
    private String replacing = "";
    private long lastClickAt;
    private int lastClickRow = -1;

    public FileDialog(final BlockPos host, final IDesktopApp owner) {
        this.host = host;
        this.owner = owner;
        this.back = this.root.add(new Button("<", this::goBack));
        this.forward = this.root.add(new Button(">", this::goForward));
        this.up = this.root.add(new Button("^", this::goUp));
        this.address = this.root.add(new Breadcrumbs(this::crumbs, this::go).setOnEmptyClick(this::startAddressEdit));
        this.addressEdit = this.root.add(new TextField(200).setOnCommit(this::goTyped).setRevertOnEscape(true)
                .setOnBlur(this::stopAddressEdit));
        this.addressEdit.setVisible(false);
        this.places = this.root.add(new ListView<>(this::places, ROW_H - 1, this::drawPlace).setOnClick(this::onPlace));
        this.header = this.root.add(new ColumnHeader(List.of("Name", "Type", "Size")).setSortable(false));
        this.rows = this.root.add(new ListView<>(this::entries, ROW_H, this::drawEntry).setOnClick(this::onRow));
        this.nameLabel = this.root.add(new Label(() -> this.mode == Mode.OPEN_FOLDER ? "Folder:" : "File name:", Label.Tone.DIM));
        this.name = this.root.add(new TextField(120).setOnCommit(value -> confirm()));
        this.kindLabel = this.root.add(new Label(() -> this.mode == Mode.SAVE ? "Save as:" : "Type:", Label.Tone.DIM));
        this.kind = this.root.add(new Button(() -> this.filters.get(this.filter).label(), this::cycleFilter).setLabelScale(0.85f));
        this.newFolder = this.root.add(new Button("New folder", this::makeFolder).setLabelScale(0.85f));
        this.primary = this.root.add(new Button(this::primaryLabel, this::confirm).setPrimary(true));
        this.cancel = this.root.add(new Button("Cancel", this::close));
        this.status = this.root.add(new Label(this::statusText, Label.Tone.DIM));
    }

    /* Opening it */

    /** Opens on {@code start} to pick a file of one of the {@code filters}' kinds; the path chosen goes to {@code onPick}. */
    public void openFile(final String windowTitle, final String start, final List<Filter> filters,
                         final Consumer<String> onPick) {
        show(Mode.OPEN_FILE, windowTitle, start, filters, "", onPick);
    }

    /** Opens on {@code start} to pick a folder; the folder chosen goes to {@code onPick}. */
    public void openFolder(final String windowTitle, final String start, final Consumer<String> onPick) {
        show(Mode.OPEN_FOLDER, windowTitle, start, List.of(Filter.ALL), "", onPick);
    }

    /** Opens on {@code start} to pick where to save, with {@code initialName} in the field; the path goes to {@code onPick}. */
    public void saveAs(final String windowTitle, final String start, final String initialName,
                       final List<Filter> filters, final Consumer<String> onPick) {
        show(Mode.SAVE, windowTitle, start, filters, initialName, onPick);
    }

    private void show(final Mode what, final String windowTitle, final String start, final List<Filter> kinds,
                      final String initialName, final Consumer<String> pick) {
        this.mode = what;
        this.title = windowTitle;
        this.filters = kinds == null || kinds.isEmpty() ? List.of(Filter.ALL) : List.copyOf(kinds);
        this.filter = 0;
        this.onPick = pick;
        this.message = "";
        this.replacing = "";
        this.backStack.clear();
        this.forwardStack.clear();
        this.name.set(initialName == null ? "" : initialName);
        final int dot = this.name.edit().lastIndexOf('.');
        this.name.setCaret(dot > 0 ? dot : this.name.edit().length());
        this.newFolder.setVisible(what != Mode.OPEN_FILE);
        if (!this.open) {
            this.open = true;
            DesktopScreen.openDialogFor(this.owner, this);
        }
        request(start == null ? "" : start);
        this.root.focus(what == Mode.SAVE ? this.name : this.rows);
    }

    public boolean isOpen() {
        return this.open;
    }

    /** Puts the window away without an answer. */
    public void close() {
        if (!this.open) {
            return;
        }
        this.open = false;
        DesktopScreen.closeDialog(this);
    }

    /** The folder the window is on, as the machine names it. */
    public String folder() {
        return this.dir;
    }

    /* Where it is */

    private void request(final String target) {
        this.dir = target;
        this.listed.clear();
        this.rows.setSelected(-1);
        this.replacing = "";
        CodeFileReplies.expectListing(this, target);
        PacketDistributor.sendToServer(new RequestDiskFilesPayload(this.host, target));
    }

    /** Goes to {@code target}, remembering where it came from for the way back. */
    private void go(final String target) {
        if (target.equals(this.dir)) {
            request(target);
            return;
        }
        this.backStack.add(this.dir);
        if (this.backStack.size() > HISTORY_MAX) {
            this.backStack.remove(0);
        }
        this.forwardStack.clear();
        request(target);
    }

    private void goBack() {
        if (this.backStack.isEmpty()) {
            return;
        }
        this.forwardStack.add(this.dir);
        request(this.backStack.remove(this.backStack.size() - 1));
    }

    private void goForward() {
        if (this.forwardStack.isEmpty()) {
            return;
        }
        this.backStack.add(this.dir);
        request(this.forwardStack.remove(this.forwardStack.size() - 1));
    }

    private void goUp() {
        if (onMedia()) {
            final int slash = this.dir.indexOf('/');
            go(slash >= 0 ? this.dir.substring(0, slash) : "");
            return;
        }
        final int slash = this.dir.lastIndexOf('/');
        go(slash >= 0 ? this.dir.substring(0, slash) : "");
    }

    private boolean onMedia() {
        return this.dir.startsWith("media:");
    }

    private String mediaRoot() {
        final int slash = this.dir.indexOf('/');
        return slash >= 0 ? this.dir.substring(0, slash) : this.dir;
    }

    private String mediaRest() {
        final int slash = this.dir.indexOf('/');
        return slash >= 0 ? this.dir.substring(slash + 1) : "";
    }

    @Override
    public void onListing(final DiskFilesPayload listing) {
        this.listed.clear();
        this.listed.addAll(listing.files());
        this.volumes.clear();
        this.volumes.addAll(listing.volumes());
        this.rows.setScroll(0);
    }

    /* The places on the left */

    private List<Place> places() {
        final List<Place> out = new ArrayList<>();
        if (this.posix) {
            out.add(new Place("PLACES", FileIcons.Kind.HOME, "", true));
            out.add(new Place("Home", FileIcons.Kind.HOME, parentOf(SystemLayout.POSIX_DESKTOP_DIR), false));
            out.add(new Place("Desktop", FileIcons.Kind.FOLDER, SystemLayout.POSIX_DESKTOP_DIR, false));
            out.add(new Place("progs", FileIcons.Kind.FOLDER, CodeWorkspace.HOME, false));
            out.add(new Place("DEVICES", FileIcons.Kind.HOME, "", true));
            out.add(new Place("Root", FileIcons.Kind.BIN, "", false));
        } else {
            out.add(new Place("QUICK ACCESS", FileIcons.Kind.HOME, "", true));
            out.add(new Place("Desktop", FileIcons.Kind.FOLDER, SystemLayout.DESKTOP_DIR, false));
            out.add(new Place("progs", FileIcons.Kind.FOLDER, CodeWorkspace.HOME, false));
            out.add(new Place("THIS PC", FileIcons.Kind.HOME, "", true));
            out.add(new Place("Local Disk (C:)", FileIcons.Kind.BIN, "", false));
        }
        for (final DiskFilesPayload.WireVolume volume : this.volumes) {
            if (volume.removable()) {
                final String letter = letterOf(volume.key());
                out.add(new Place(volume.label() + (letter.isEmpty() ? "" : " (" + letter + ")"),
                        FileIcons.Kind.BIN, volume.key(), false));
            }
        }
        return out;
    }

    private static String parentOf(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(0, slash) : "";
    }

    /** The drive letter of a volume: the system disk is C:, then the media in the order the tree lists them. */
    private String letterOf(final String key) {
        if (this.posix) {
            return "";
        }
        if (key.isEmpty()) {
            return "C:";
        }
        int n = 0;
        for (final DiskFilesPayload.WireVolume volume : this.volumes) {
            if (volume.removable()) {
                n++;
                if (volume.key().equals(key)) {
                    return (char) ('C' + n) + ":";
                }
            }
        }
        return "";
    }

    private void drawPlace(final GuiGraphics g, final UiContext ctx, final Place place, final int index,
                           final int x, final int y, final int width, final int height,
                           final boolean hovered, final boolean selected) {
        if (place.heading()) {
            g.drawString(ctx.font(), place.label(), x + 2, y + 1, ctx.skin().dim(), false);
            return;
        }
        final boolean here = place.target().equals(onMedia() ? mediaRoot() : this.dir);
        ctx.skin().listRow(g, x, y, width, height, hovered, here);
        FileIcons.draw(g, x + 3, y, place.icon());
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(place.label(), width - 18), x + 16, y + 1,
                ctx.skin().listRowText(here), false);
    }

    private void onPlace(final int index, final int button, final double mx, final double my) {
        final List<Place> list = places();
        if (index >= 0 && index < list.size() && !list.get(index).heading()) {
            go(list.get(index).target());
        }
    }

    /* The address */

    private List<Breadcrumbs.Crumb> crumbs() {
        final List<Breadcrumbs.Crumb> out = new ArrayList<>();
        if (onMedia()) {
            final String root = mediaRoot();
            out.add(new Breadcrumbs.Crumb(this.posix ? "Devices" : "This PC", ""));
            final String letter = letterOf(root);
            String label = "Removable Drive";
            for (final DiskFilesPayload.WireVolume volume : this.volumes) {
                if (volume.key().equals(root)) {
                    label = volume.label();
                }
            }
            out.add(new Breadcrumbs.Crumb(label + (letter.isEmpty() ? "" : " (" + letter + ")"), root));
            String acc = root;
            for (final String seg : mediaRest().split("/")) {
                if (!seg.isEmpty()) {
                    acc = acc + "/" + seg;
                    out.add(new Breadcrumbs.Crumb(seg, acc));
                }
            }
            return out;
        }
        out.add(new Breadcrumbs.Crumb(this.posix ? "/" : "Local Disk (C:)", ""));
        String acc = "";
        for (final String seg : this.dir.split("/")) {
            if (!seg.isEmpty()) {
                acc = acc.isEmpty() ? seg : acc + "/" + seg;
                out.add(new Breadcrumbs.Crumb(seg, acc));
            }
        }
        return out;
    }

    private void startAddressEdit() {
        this.addressEdit.set(typedPath());
        this.addressEdit.setVisible(true);
        this.address.setVisible(false);
        this.root.focus(this.addressEdit);
    }

    private void stopAddressEdit() {
        this.addressEdit.setVisible(false);
        this.address.setVisible(true);
    }

    /** The folder the way a person types it: {@code C:\progs\}, {@code D:\support\}, or {@code /progs/} on Linux. */
    private String typedPath() {
        if (this.posix) {
            final String rest = onMedia() ? mediaRest() : this.dir;
            return "/" + rest + (rest.isEmpty() ? "" : "/");
        }
        if (onMedia()) {
            final String letter = letterOf(mediaRoot());
            final String rest = mediaRest();
            return (letter.isEmpty() ? "D:" : letter) + "\\" + rest.replace('/', '\\') + (rest.isEmpty() ? "" : "\\");
        }
        return "C:\\" + this.dir.replace('/', '\\') + (this.dir.isEmpty() ? "" : "\\");
    }

    /** Where a typed path leads, or null when it names a drive the machine does not have. */
    private String keyOf(final String typed) {
        String path = typed.trim().replace('\\', '/');
        String rootKey = "";
        if (path.length() >= 2 && path.charAt(1) == ':') {
            final String letter = path.substring(0, 2).toUpperCase(Locale.ROOT);
            path = path.substring(2);
            if (!letter.equals("C:")) {
                boolean found = false;
                for (final DiskFilesPayload.WireVolume volume : this.volumes) {
                    if (letterOf(volume.key()).equalsIgnoreCase(letter)) {
                        rootKey = volume.key();
                        found = true;
                    }
                }
                if (!found) {
                    return null;
                }
            }
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return rootKey.isEmpty() ? path : (path.isEmpty() ? rootKey : rootKey + "/" + path);
    }

    private void goTyped(final String typed) {
        stopAddressEdit();
        final String key = keyOf(typed);
        if (key == null) {
            this.message = "No such drive: " + typed.trim();
            return;
        }
        go(key);
    }

    /* The list */

    /** The rows: the way up, then the folders here, then the files the kind admits. */
    private List<Entry> entries() {
        final List<Entry> out = new ArrayList<>();
        if (!this.dir.isEmpty()) {
            out.add(new Entry("..", true, null));
        }
        for (final DiskFilesPayload.WireFile file : this.listed) {
            if (file.directory() && !file.path().startsWith("media:")) {
                out.add(new Entry(leaf(file.path()), false, file));
            }
        }
        if (this.mode != Mode.OPEN_FOLDER) {
            for (final DiskFilesPayload.WireFile file : this.listed) {
                if (!file.directory() && !file.path().startsWith("media:") && !file.projectsItem()
                        && this.filters.get(this.filter).admits(file.ext())) {
                    out.add(new Entry(leaf(file.path()), false, file));
                }
            }
        }
        return out;
    }

    private static String leaf(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private void drawEntry(final GuiGraphics g, final UiContext ctx, final Entry entry, final int index,
                           final int x, final int y, final int width, final int height,
                           final boolean hovered, final boolean selected) {
        ctx.skin().listRow(g, x, y, width, height, hovered, selected);
        final int nameX = this.header.columnX(0);
        final int typeX = this.header.columnX(1);
        final int sizeX = this.header.columnX(2);
        FileIcons.draw(g, nameX - 13, y + 1, entry.up() ? FileIcons.Kind.UP
                : FileIcons.kindOfPath(entry.file().path(), entry.file().directory()));
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(entry.name(), typeX - nameX - 4), nameX, y + 2,
                ctx.skin().listRowText(selected), false);
        final String type = entry.up() ? "Up one level" : entry.file().directory() ? "Folder" : FilesApp.typeLabel(entry.file());
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(type, sizeX - typeX - 4), typeX, y + 2,
                selected ? ctx.skin().listRowText(true) : ctx.skin().dim(), false);
        if (!entry.up() && !entry.file().directory()) {
            g.drawString(ctx.font(), entry.file().weight() + " mB", sizeX, y + 2,
                    selected ? ctx.skin().listRowText(true) : ctx.skin().dim(), false);
        }
    }

    /** A click picks the row; a second click on it within a moment goes into a folder or takes a file. */
    private void onRow(final int index, final int button, final double mx, final double my) {
        final List<Entry> list = entries();
        if (index < 0 || index >= list.size()) {
            return;
        }
        final Entry entry = list.get(index);
        final long now = System.currentTimeMillis();
        final boolean again = index == this.lastClickRow && now - this.lastClickAt < 350;
        this.lastClickAt = now;
        this.lastClickRow = index;
        if (!entry.up() && (this.mode != Mode.OPEN_FOLDER || entry.file().directory())) {
            this.name.set(entry.name());
            this.replacing = "";
        }
        if (!again) {
            return;
        }
        if (entry.up()) {
            goUp();
        } else if (entry.file().directory()) {
            go(entry.file().path());
        } else {
            confirm();
        }
    }

    /* The bottom row */

    private String primaryLabel() {
        return switch (this.mode) {
            case OPEN_FILE -> "Open";
            case OPEN_FOLDER -> "Select Folder";
            case SAVE -> this.replacing.isEmpty() ? "Save" : "Replace";
        };
    }

    private String statusText() {
        if (!this.message.isEmpty()) {
            return this.message;
        }
        final int count = entries().size() - (this.dir.isEmpty() ? 0 : 1);
        if (this.mode == Mode.OPEN_FOLDER) {
            final Entry picked = selectedEntry();
            return picked != null && picked.file() != null ? "Select Folder picks " + picked.name()
                    : "Select Folder picks this folder";
        }
        if (this.mode == Mode.SAVE) {
            return this.name.edit().isBlank() ? "Type a name" : "Will be written to " + shown(join(this.dir, this.name.edit().trim()));
        }
        return count + (count == 1 ? " item" : " items");
    }

    private Entry selectedEntry() {
        final List<Entry> list = entries();
        final int index = this.rows.selected();
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    private void cycleFilter() {
        this.filter = (this.filter + 1) % this.filters.size();
        this.rows.setSelected(-1);
    }

    /** Makes a folder here with the next free name and goes into it, so Select Folder picks the new one. */
    private void makeFolder() {
        String made = "New Folder";
        int n = 2;
        while (has(made)) {
            made = "New Folder (" + n++ + ")";
        }
        final String path = join(this.dir, made);
        PacketDistributor.sendToServer(new MkdirPayload(this.host, path));
        FilesApps.diskChanged();
        go(path);
    }

    private boolean has(final String fileName) {
        for (final DiskFilesPayload.WireFile file : this.listed) {
            if (leaf(file.path()).equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }

    private static String join(final String dir, final String fileName) {
        return dir.isEmpty() ? fileName : dir + "/" + fileName;
    }

    /** The path the way the desktop shows it, for the status line. */
    private String shown(final String path) {
        if (this.posix) {
            return "/" + path;
        }
        return "C:\\" + path.replace('/', '\\');
    }

    /**
     * Does what the window was opened for with what is typed or picked: a file that is here, a folder
     * here or the one the window is on, or a name to save under. A typed name with a folder in front
     * goes there first, the way every file dialog takes a path in its name field.
     */
    private void confirm() {
        final String typed = this.name.edit().trim();
        if (this.mode == Mode.OPEN_FOLDER) {
            final Entry picked = selectedEntry();
            final String chosen = picked != null && picked.file() != null && picked.file().directory()
                    ? picked.file().path() : (typed.isEmpty() ? this.dir : join(this.dir, typed));
            finish(chosen);
            return;
        }
        if (typed.isEmpty()) {
            this.message = "Type a name, or pick one from the list";
            return;
        }
        if (typed.contains("/") || typed.contains("\\") || typed.contains(":")) {
            final String key = keyOf(typed);
            if (key == null) {
                this.message = "No such drive: " + typed;
                return;
            }
            final int slash = key.lastIndexOf('/');
            final String folder = slash >= 0 ? key.substring(0, slash) : "";
            final String fileName = slash >= 0 ? key.substring(slash + 1) : key;
            if (!folder.equals(this.dir)) {
                this.name.set(fileName);
                go(folder);
                return;
            }
        }
        final String fileName = leaf(typed.replace('\\', '/'));
        for (final DiskFilesPayload.WireFile file : this.listed) {
            if (file.directory() && leaf(file.path()).equalsIgnoreCase(fileName)) {
                this.name.set("");
                go(file.path());
                return;
            }
        }
        final String path = join(this.dir, fileName);
        if (this.mode == Mode.OPEN_FILE) {
            if (!has(fileName)) {
                this.message = "Not found: " + fileName;
                return;
            }
            finish(path);
            return;
        }
        if (has(fileName) && !path.equals(this.replacing)) {
            this.replacing = path;
            this.message = fileName + " exists. Replace it?";
            return;
        }
        finish(path);
    }

    private void finish(final String path) {
        close();
        this.onPick.accept(path);
    }

    /* The window */

    private void layout(final int cx, final int cy, final int cw, final int ch) {
        final int x = cx + 1;
        final int w = cw - 2;
        int y = cy + 1;
        this.back.setBounds(x, y, 11, 11);
        this.forward.setBounds(x + 13, y, 11, 11);
        this.up.setBounds(x + 26, y, 11, 11);
        this.address.setBounds(x + 40, y, w - 40, 11);
        this.addressEdit.setBounds(x + 40, y, w - 40, 11);
        y += 14;
        final int bottom = cy + ch - 1;
        final int listH = bottom - y - 42;
        this.places.setBounds(x, y, PLACES_W, listH);
        final int lx = x + PLACES_W + 3;
        final int lw = w - PLACES_W - 3;
        this.header.setBounds(lx, y, lw, 10);
        this.header.setColumnX(lx + 16, lx + lw - SIZE_W - TYPE_W, lx + lw - SIZE_W);
        this.rows.setBounds(lx, y + 10, lw, listH - 10);
        y += listH + 3;
        final int primaryW = this.mode == Mode.OPEN_FOLDER ? 64 : 38;
        final int buttonsW = primaryW + 4 + 38;
        final int right = cx + cw - 1;
        this.nameLabel.setBounds(x, y + 2, LABEL_W, 9);
        this.name.setBounds(x + LABEL_W, y, w - LABEL_W - buttonsW - 4, 11);
        this.primary.setBounds(right - 38 - 4 - primaryW, y, primaryW, 11);
        this.cancel.setBounds(right - 38, y, 38, 11);
        y += 13;
        /*
         * The kind takes the whole second row: its label is the longest thing here. A window that can
         * make a folder keeps that button at the row's right end instead.
         */
        final boolean makes = this.mode != Mode.OPEN_FILE;
        this.kindLabel.setBounds(x, y + 2, LABEL_W, 9);
        this.kindLabel.setVisible(this.mode != Mode.OPEN_FOLDER);
        this.kind.setBounds(x + LABEL_W, y, w - LABEL_W - (makes ? 56 : 0), 11);
        this.kind.setVisible(this.mode != Mode.OPEN_FOLDER);
        this.newFolder.setBounds(this.mode == Mode.OPEN_FOLDER ? x + LABEL_W : right - 52, y, 52, 11);
        this.newFolder.setVisible(makes);
        y += 13;
        this.status.setBounds(x, y, w, 9);
    }

    @Override
    public String title() {
        return this.title;
    }

    @Override
    public int defaultWidth() {
        return W + 8;
    }

    @Override
    public int defaultHeight() {
        return H + DesktopWindow.TITLE_H + 8;
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
    public void applySkin(final OsSkin value) {
        this.skin = value;
        this.posix = value.form() == OsSkin.Form.KDE2 || value.form() == OsSkin.Form.GNOME1;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        layout(x, y, width, height);
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        g.fill(x, y, x + width, y + height, this.skin.windowBg());
        this.root.render(g, new UiContext(this.skin, font, mouseX, mouseY, partialTick));
    }

    /** Where the cursor was last drawn, so the wheel scrolls the list under it. */
    private int lastMouseX;
    private int lastMouseY;

    /** Escape puts the window away, the way it does in every file dialog. */
    @Override
    public boolean wantsEscape() {
        return true;
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mx, final double my, final int button) {
        /*
         * A click on the address past its last crumb turns it into text; answered here rather than by
         * the trail, because the panel would then move the keyboard to what was clicked and take it
         * straight back off the field.
         */
        if (!this.addressEdit.visible() && this.address.contains(mx, my) && this.address.crumbAt(mx) == null
                && button == 0) {
            startAddressEdit();
            return;
        }
        this.message = "";
        this.root.mouseClicked(mx, my, button);
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mx, final double my, final int button) {
        this.root.mouseDragged(mx, my, button);
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mx, final double my, final int button) {
        this.root.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        return this.root.mouseScrolled(this.lastMouseX, this.lastMouseY, delta);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && !this.addressEdit.isFocused()) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER && !this.name.isFocused() && !this.addressEdit.isFocused()) {
            final Entry picked = selectedEntry();
            if (picked != null && (picked.up() || picked.file().directory())) {
                if (picked.up()) {
                    goUp();
                } else {
                    go(picked.file().path());
                }
                return true;
            }
            confirm();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !this.name.isFocused() && !this.addressEdit.isFocused()) {
            goUp();
            return true;
        }
        this.root.keyPressed(key, scanCode, modifiers);
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        this.root.charTyped(c);
        return true;
    }

    /** The window went away, answered or not: nothing it was waiting for is for it any more. */
    @Override
    public void onClosed() {
        this.open = false;
        CodeFileReplies.forget(this);
    }

    /** Puts the window away and stops listening, as the program that owns it does when it closes. */
    public void release() {
        close();
        CodeFileReplies.forget(this);
    }

    /* What a test reads and drives */

    /** The names the list shows, in order. */
    public List<String> rowNames() {
        final List<String> out = new ArrayList<>();
        for (final Entry entry : entries()) {
            out.add(entry.name());
        }
        return out;
    }

    /** The desktop-local centre of the row called {@code rowName}, where a test clicks it; null when absent. */
    public int[] rowPoint(final String rowName) {
        final List<Entry> list = entries();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equals(rowName)) {
                return this.rows.rowCenter(i);
            }
        }
        return null;
    }

    /** The desktop-local centre of the window's main button. */
    public int[] primaryPoint() {
        return this.primary.center();
    }

    /** What the name field holds. */
    public String typedName() {
        return this.name.edit();
    }

    /** Puts a name in the field, as typing it would. */
    public void setName(final String value) {
        this.name.set(value);
    }

    /** The window's kind filter as shown. */
    public String filterLabel() {
        return this.filters.get(this.filter).label();
    }

    /** The bottom line's text, for a test to read what the window says. */
    public String statusLine() {
        return statusText();
    }
}
