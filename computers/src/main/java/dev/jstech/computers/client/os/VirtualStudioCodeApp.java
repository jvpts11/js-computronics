/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.operation.payload.DiskFilesPayload;
import dev.jstech.computers.os.edit.InkPalette;
import dev.jstech.core.JsCore;
import dev.jstech.core.client.gui.component.AmountStepper;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.ContextMenu;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.MenuBar;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.Popup;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.language.IProgrammingLanguage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Virtual Studio Code: the light editor, which works in folders.
 *
 * <p>It opens on a Welcome page. Open a folder and everything means that folder: the Explorer lists it,
 * the palette's commands act on it, Run compiles what is open in it and runs it in the terminal below.
 * There is no project; a folder is the whole idea, which is what makes it light.
 *
 * <p>All it owns is the arrangement and the commands. What the files are, which are open and what is
 * wrong with them is the workspace's; the explorer, the tabs, the menus, the palette and the text are
 * components, so the heavier studio is a different arrangement of the same parts.
 */
public final class VirtualStudioCodeApp implements IDesktopApp {

    private static final int RAIL_W = 14;
    private static final int SIDE_W = 82;
    private static final int CAPTION_H = 9;
    private static final int TAB_H = 10;
    private static final int STATUS_H = 9;
    private static final int ROW_H = 9;
    private static final int PANEL_H = 62;
    private static final int MIN_CODE_H = 36;

    private static final List<String> PANEL_TABS = List.of("PROBLEMS", "TERMINAL");
    private static final int PANEL_PROBLEMS = 0;
    private static final int PANEL_TERMINAL = 1;

    /** What the rail can show down the side. */
    private enum Side { EXPLORER, SEARCH, RUN, EXTENSIONS }

    /** Folders and files opened on each machine lately, kept for the Welcome page while the game runs. */
    private static final Map<BlockPos, Deque<String>> RECENT = new LinkedHashMap<>();
    private static final int RECENT_MAX = 5;

    private final BlockPos host;
    private final CodeWorkspace workspace;
    private OsSkin skin = OsSkin.fallback();
    private boolean folderOpen;
    private Side side = Side.EXPLORER;
    private boolean typingInTerminal;
    private int tabSize = 4;

    private final Panel root = new Panel();
    private final MenuBar menuBar = new MenuBar(92, 10);
    private final ListView<SideRow> explorer;
    private final ListView<IProgrammingLanguage> extensions;
    private final TabStrip tabs;
    private final TabStrip panelTabs;
    private final ListView<IProgrammingLanguage.Complaint> problems;
    private final ShellView terminal;
    private final CodeCompletions completions = new CodeCompletions();
    private final CommandPalette palette = new CommandPalette();
    private final FolderPicker picker;

    /* The small windows a command opens for one thing: a name, a line, a word. */
    private final Popup ask = new Popup(() -> this.askTitle, 150, 44).setLayouter(this::layoutAsk);
    private final TextField askField = new TextField(64);
    private final Button askOk;
    private String askTitle = "";
    private java.util.function.Consumer<String> askAction = value -> { };
    private final Popup settings = new Popup("Settings", 170, 50).setLayouter(this::layoutSettings);
    private final AmountStepper tabStepper = new AmountStepper();

    /** Lines to run at the terminal, one after the other as each finishes. */
    private final Deque<String> queue = new ArrayDeque<>();

    /** The clickable lines of the Welcome page, laid out as it is drawn. */
    private final List<Link> links = new ArrayList<>();

    private record Link(int x, int y, int width, int height, Runnable action) {
    }

    /** One row of the side panel: a section title, an open editor, or an entry of the folder's tree. */
    private record SideRow(String label, int depth, DiskFilesPayload.WireFile file, int docIndex, boolean header) {
    }

    public VirtualStudioCodeApp(final BlockPos host) {
        this.host = host;
        this.workspace = new CodeWorkspace(host);
        this.picker = new FolderPicker(host, "Open Folder");
        this.explorer = this.root.add(new ListView<>(this::sideRows, ROW_H, this::drawSideRow))
                .setOnClick(this::onSideRow);
        this.extensions = this.root.add(new ListView<>(() -> JsCore.languages().all(), ROW_H + 2, this::drawLanguage));
        this.tabs = this.root.add(new TabStrip(this.workspace::tabLabels).fitToLabels(10).setUnderline(false));
        this.tabs.setOnSelect(this.workspace::setCurrent);
        this.panelTabs = this.root.add(new TabStrip(PANEL_TABS).fitToLabels(12).setUnderline(true));
        this.panelTabs.setSelected(PANEL_TERMINAL);
        this.problems = this.root.add(new ListView<>(this::complaints, ROW_H, this::drawProblemRow))
                .setOnClick(this::onProblemPicked);
        /*
         * The panel is a view of the machine's own console, not a terminal of its own: what is compiled
         * here shows in the Command Prompt window too, because a computer has one console.
         */
        this.terminal = this.root.add(new ShellView(host, false, false)).setOnIdle(this::runNext);
        this.root.add(this.menuBar);
        this.menuBar.add("File", this::fileMenu).add("Edit", this::editMenu).add("View", this::viewMenu)
                .add("Go", this::goMenu).add("Run", this::runMenu).add("Terminal", this::terminalMenu)
                .add("Help", this::helpMenu);
        this.ask.add(this.askField);
        this.askOk = this.ask.add(new Button("OK", () -> {
            this.ask.close();
            this.askAction.accept(this.askField.edit().trim());
        }).setPrimary(true));
        this.askField.setOnCommit(value -> this.askOk.mouseClicked(this.askOk.center()[0], this.askOk.center()[1], 0));
        this.settings.add(new Label("Tab size", Label.Tone.DIM));
        this.settings.add(this.tabStepper.setRange(2, 8).setAmount(4).setOnChange(v -> setTabSize((int) v)));
        this.settings.add(new Button("Close", this.settings::close).setPrimary(true));
    }

    /* What is open and what is wrong with it */

    private List<IProgrammingLanguage.Complaint> complaints() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        return doc == null ? List.of() : doc.complaints();
    }

    /** The file being edited, or empty when none is. */
    public String openFile() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        return doc == null ? "" : doc.path();
    }

    /** The text of the file being edited. */
    public String text() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        return doc == null ? "" : doc.area().text();
    }

    /** How many things the compiler has to say about it. */
    public int complaintCount() {
        return complaints().size();
    }

    /** Everything the terminal panel has printed, as one piece of text. */
    public String terminalText() {
        return this.terminal.scrollbackText();
    }

    /** The folder the window works in, or empty on the Welcome page. */
    public String folder() {
        return this.folderOpen ? this.workspace.folder() : "";
    }

    /** Whether the palette is up. */
    public boolean paletteOpen() {
        return this.palette.isOpen();
    }

    /** Opens a file, as clicking it in the explorer or picking this program with "Open with" does. */
    @Override
    public void openFile(final String path) {
        if (!this.folderOpen) {
            // A file opened from outside brings its folder with it, so the explorer has something to show.
            final int slash = path.lastIndexOf('/');
            openFolder(slash > 0 ? path.substring(0, slash) : "");
        }
        this.workspace.open(path);
        remember(path);
    }

    /** Points the window at a folder: from then on everything means that folder. */
    public void openFolder(final String dir) {
        this.folderOpen = true;
        this.side = Side.EXPLORER;
        this.workspace.setFolder(dir);
        remember(dir);
    }

    /** Runs a line at the terminal panel, as typing it and pressing return does. */
    public void runInTerminal(final String line) {
        this.terminal.run(line);
    }

    private void remember(final String path) {
        final Deque<String> recent = RECENT.computeIfAbsent(this.host, h -> new ArrayDeque<>());
        recent.remove(path);
        recent.addFirst(path);
        while (recent.size() > RECENT_MAX) {
            recent.removeLast();
        }
    }

    private List<String> recent() {
        return new ArrayList<>(RECENT.getOrDefault(this.host, new ArrayDeque<>()));
    }

    private void setTabSize(final int value) {
        this.tabSize = value;
        for (final CodeWorkspace.Doc doc : this.workspace.docs()) {
            doc.area().setTabSize(value);
        }
    }

    /* The side panel */

    private static String shortName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    private List<SideRow> sideRows() {
        final List<SideRow> out = new ArrayList<>();
        out.add(new SideRow("OPEN EDITORS", 0, null, -1, true));
        final List<CodeWorkspace.Doc> docs = this.workspace.docs();
        for (int i = 0; i < docs.size(); i++) {
            out.add(new SideRow(docs.get(i).name() + (docs.get(i).dirty() ? " *" : ""), 1, null, i, false));
        }
        final String folder = this.workspace.folder();
        out.add(new SideRow(folder.isEmpty() ? "C:\\" : shortName(folder).toUpperCase(java.util.Locale.ROOT),
                0, null, -1, true));
        for (final CodeWorkspace.TreeRow row : this.workspace.tree()) {
            final String mark = row.file().directory()
                    ? (this.workspace.isExpanded(row.file().path()) ? "v " : "> ") : "";
            out.add(new SideRow(mark + shortName(row.file().path()), row.depth() + 1, row.file(), -1, false));
        }
        return out;
    }

    private void drawSideRow(final GuiGraphics g, final UiContext ctx, final SideRow row, final int index,
                             final int x, final int y, final int width, final int height,
                             final boolean hovered, final boolean selected) {
        final int color = row.header() ? ctx.skin().dim() : ctx.skin().listRowText(selected);
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(row.label(), width - 2 - row.depth() * 5),
                x + 2 + row.depth() * 5, y + 1, color, false);
    }

    private void onSideRow(final int index, final int button, final double mx, final double my) {
        final List<SideRow> rows = sideRows();
        if (index < 0 || index >= rows.size()) {
            return;
        }
        final SideRow row = rows.get(index);
        if (row.header()) {
            return;
        }
        if (row.docIndex() >= 0) {
            this.workspace.setCurrent(row.docIndex());
            return;
        }
        if (row.file() == null) {
            return;
        }
        if (row.file().directory()) {
            this.workspace.toggleFolder(row.file().path());
        } else {
            this.workspace.open(row.file().path());
        }
    }

    private void drawLanguage(final GuiGraphics g, final UiContext ctx, final IProgrammingLanguage language,
                              final int index, final int x, final int y, final int width, final int height,
                              final boolean hovered, final boolean selected) {
        g.drawString(ctx.font(), language.displayName(), x + 2, y + 1, ctx.skin().text(), false);
        g.drawString(ctx.font(), "installed", x + width - ctx.font().width("installed") - 2, y + 1,
                ctx.skin().dim(), false);
    }

    private void drawProblemRow(final GuiGraphics g, final UiContext ctx,
                                final IProgrammingLanguage.Complaint complaint, final int index,
                                final int x, final int y, final int width, final int height,
                                final boolean hovered, final boolean selected) {
        final String where = complaint.line() + ":" + complaint.column();
        g.drawString(ctx.font(), where, x + 2, y + 1, ctx.skin().dim(), false);
        final int textX = x + 2 + ctx.font().width("00:00") + 4;
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(complaint.message(), width - (textX - x) - 2),
                textX, y + 1, 0xFFC0392B, false);
    }

    private void onProblemPicked(final int index, final int button, final double mx, final double my) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        final List<IProgrammingLanguage.Complaint> found = complaints();
        if (doc == null || index < 0 || index >= found.size()) {
            return;
        }
        final IProgrammingLanguage.Complaint complaint = found.get(index);
        doc.area().document().setCursor(complaint.line() - 1, Math.max(0, complaint.column() - 1));
    }

    /* The menus, and the palette that holds every one of their entries */

    private ContextMenu.Item item(final String label, final boolean enabled, final Runnable action) {
        return new ContextMenu.Item(label, enabled, action);
    }

    private boolean hasDoc() {
        return this.workspace.current() != null;
    }

    private List<ContextMenu.Item> fileMenu() {
        final List<ContextMenu.Item> items = new ArrayList<>(List.of(
                item("New File", this.folderOpen, this::newFile),
                item("Open File...", this.folderOpen, this::openFileByName),
                item("Open Folder...", true, this::pickFolder)));
        for (final String path : recent()) {
            items.add(item("Recent: " + shortName(path), true, () -> openRecent(path)));
        }
        items.add(ContextMenu.Item.separator());
        items.add(item("Save", hasDoc(), this.workspace::save));
        items.add(item("Save As...", hasDoc(), this::saveAs));
        items.add(item("Save All", this.workspace.anyDirty(), this.workspace::saveAll));
        items.add(ContextMenu.Item.separator());
        items.add(item("Close Editor", hasDoc(), () -> this.workspace.close(this.workspace.currentIndex())));
        items.add(item("Close Folder", this.folderOpen, this::closeFolder));
        items.add(item("Exit", true, () -> DesktopScreen.requestClose("Virtual Studio Code")));
        return items;
    }

    private List<ContextMenu.Item> editMenu() {
        return List.of(
                item("Find...", hasDoc(), this::find),
                item("Toggle Line Comment", hasDoc(), this::toggleComment));
    }

    private List<ContextMenu.Item> viewMenu() {
        return List.of(
                item("Command Palette...", true, this::openPalette),
                ContextMenu.Item.separator(),
                item("Explorer", this.folderOpen, () -> this.side = Side.EXPLORER),
                item("Extensions", true, () -> this.side = Side.EXTENSIONS),
                ContextMenu.Item.separator(),
                item("Problems", true, () -> this.panelTabs.setSelected(PANEL_PROBLEMS)),
                item("Terminal", true, () -> this.panelTabs.setSelected(PANEL_TERMINAL)));
    }

    private List<ContextMenu.Item> goMenu() {
        return List.of(
                item("Go to File...", this.folderOpen, this::goToFile),
                item("Go to Line...", hasDoc(), this::goToLine));
    }

    private List<ContextMenu.Item> runMenu() {
        return List.of(
                item("Run File", hasDoc(), this::runFile),
                item("Build File", hasDoc(), this::buildFile),
                item("Build Folder", this.folderOpen && !this.workspace.files().isEmpty(), this::buildFolder),
                item("Stop", true, () -> this.terminal.run("cannon stop")));
    }

    private List<ContextMenu.Item> terminalMenu() {
        return List.of(
                item("New Terminal", true, this::focusTerminal),
                item("Clear", true, () -> this.terminal.run("cls")));
    }

    private List<ContextMenu.Item> helpMenu() {
        return List.of(
                item("Welcome", true, this::closeFolder),
                item("Keyboard Shortcuts", true, this::showShortcuts),
                item("About", true, () -> this.workspace.say("Virtual Studio Code, by Midsoft. Cannon 1.0.")));
    }

    /** Every command there is, under the name its menu gives it, for the palette. */
    private List<CommandPalette.Entry> commands() {
        return List.of(
                new CommandPalette.Entry("Cannon: Run File", "F5", this::runFile),
                new CommandPalette.Entry("Cannon: Build File", "Ctrl+Shift+B", this::buildFile),
                new CommandPalette.Entry("Cannon: Build Folder", "", this::buildFolder),
                new CommandPalette.Entry("Cannon: Stop", "", () -> this.terminal.run("cannon stop")),
                new CommandPalette.Entry("Terminal: New Terminal", "Ctrl+`", this::focusTerminal),
                new CommandPalette.Entry("Terminal: Clear", "", () -> this.terminal.run("cls")),
                new CommandPalette.Entry("File: New File", "", this::newFile),
                new CommandPalette.Entry("File: Open File...", "Ctrl+O", this::openFileByName),
                new CommandPalette.Entry("File: Open Folder...", "", this::pickFolder),
                new CommandPalette.Entry("File: Save", "Ctrl+S", this.workspace::save),
                new CommandPalette.Entry("File: Save As...", "", this::saveAs),
                new CommandPalette.Entry("File: Save All", "", this.workspace::saveAll),
                new CommandPalette.Entry("File: Close Folder", "", this::closeFolder),
                new CommandPalette.Entry("Edit: Find...", "Ctrl+F", this::find),
                new CommandPalette.Entry("Edit: Toggle Line Comment", "", this::toggleComment),
                new CommandPalette.Entry("View: Toggle Problems", "Ctrl+Shift+M", () -> this.panelTabs.setSelected(
                        this.panelTabs.selected() == PANEL_PROBLEMS ? PANEL_TERMINAL : PANEL_PROBLEMS)),
                new CommandPalette.Entry("View: Explorer", "", () -> this.side = Side.EXPLORER),
                new CommandPalette.Entry("View: Extensions", "", () -> this.side = Side.EXTENSIONS),
                new CommandPalette.Entry("Go to File...", "Ctrl+P", this::goToFile),
                new CommandPalette.Entry("Go to Line...", "Ctrl+G", this::goToLine),
                new CommandPalette.Entry("Preferences: Open Settings", "Ctrl+,", this::openSettings),
                new CommandPalette.Entry("Help: Welcome", "", this::closeFolder),
                new CommandPalette.Entry("Help: Keyboard Shortcuts", "", this::showShortcuts));
    }

    private void openPalette() {
        this.palette.open(commands(), ">");
    }

    /* What the commands do */

    private void openRecent(final String path) {
        final int dot = shortName(path).lastIndexOf('.');
        if (dot > 0) {
            openFile(path);
        } else {
            openFolder(path);
        }
    }

    private void pickFolder() {
        this.picker.open(this.folderOpen ? this.workspace.folder() : CodeWorkspace.HOME, this::openFolder);
    }

    private void closeFolder() {
        this.folderOpen = false;
        this.workspace.closeAll();
        this.side = Side.EXPLORER;
    }

    /** Files by name, the palette in its other role. */
    private void goToFile() {
        final List<CommandPalette.Entry> entries = new ArrayList<>();
        for (final CodeWorkspace.TreeRow row : this.workspace.tree()) {
            if (!row.file().directory()) {
                final String path = row.file().path();
                entries.add(new CommandPalette.Entry(shortName(path), "", () -> this.workspace.open(path)));
            }
        }
        this.palette.open(entries, "");
    }

    private void openFileByName() {
        goToFile();
    }

    private void goToLine() {
        ask("Go to Line", "", value -> {
            final CodeWorkspace.Doc doc = this.workspace.current();
            try {
                if (doc != null) {
                    doc.area().document().setCursor(Integer.parseInt(value) - 1, 0);
                }
            } catch (final NumberFormatException ignored) {
                this.workspace.say("Not a line number: " + value);
            }
        });
    }

    private void find() {
        ask("Find", "", needle -> {
            final CodeWorkspace.Doc doc = this.workspace.current();
            if (doc != null && !doc.area().document().find(needle)) {
                this.workspace.say("No results for '" + needle + "'");
            }
        });
    }

    private void toggleComment() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            doc.area().document().toggleLinePrefix("// ");
            this.workspace.edited();
        }
    }

    private void newFile() {
        ask("New File", "untitled.can", name -> {
            if (!name.isEmpty()) {
                final String folder = this.workspace.folder();
                this.workspace.newFile(folder.isEmpty() ? name : folder + "/" + name);
                setTabSize(this.tabSize);
            }
        });
    }

    private void saveAs() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return;
        }
        ask("Save As", doc.name(), name -> {
            if (!name.isEmpty()) {
                final String folder = this.workspace.folder();
                this.workspace.saveAs(folder.isEmpty() ? name : folder + "/" + name);
            }
        });
    }

    private void openSettings() {
        this.tabStepper.setAmount(this.tabSize);
        this.settings.open();
    }

    private void showShortcuts() {
        this.workspace.say("F5 run, Ctrl+Shift+B build, Ctrl+Shift+P palette, Ctrl+P file, Ctrl+G line, Ctrl+F find");
    }

    private void focusTerminal() {
        this.panelTabs.setSelected(PANEL_TERMINAL);
        this.typingInTerminal = true;
    }

    /** Where the compiler puts what it makes: a build folder beside the sources. */
    private String outputFor(final String path) {
        final String name = shortName(path);
        final int dot = name.lastIndexOf('.');
        final String stem = dot > 0 ? name.substring(0, dot) : name;
        final String folder = this.workspace.folder();
        return (folder.isEmpty() ? "" : folder + "/") + "build/" + stem + ".asm";
    }

    private void buildFile() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return;
        }
        if (doc.dirty()) {
            this.workspace.save();
        }
        focusTerminal();
        enqueue("cannonc " + doc.path() + " -o " + outputFor(doc.path()));
    }

    private void runFile() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return;
        }
        buildFile();
        enqueue("cannon run " + outputFor(doc.path()));
    }

    private void buildFolder() {
        final List<DiskFilesPayload.WireFile> files = this.workspace.files();
        if (files.isEmpty()) {
            return;
        }
        final StringBuilder line = new StringBuilder("cannonc");
        for (final DiskFilesPayload.WireFile file : files) {
            line.append(' ').append(file.path());
        }
        final String folder = this.workspace.folder();
        final String stem = folder.isEmpty() ? "programs" : shortName(folder);
        line.append(" -o ").append(folder.isEmpty() ? "" : folder + "/").append("build/").append(stem).append(".asm");
        focusTerminal();
        enqueue(line.toString());
    }

    /**
     * Runs lines at the terminal one after the other, the next only once the machine has answered the
     * one before, since the console runs one command at a time and the run needs the build to be done.
     */
    private void enqueue(final String line) {
        this.queue.add(line);
        if (!this.terminal.busy() && this.queue.size() == 1) {
            runNext();
        }
    }

    private void runNext() {
        final String next = this.queue.poll();
        if (next != null) {
            this.terminal.run(next);
        }
    }

    /** Asks for one thing in a small window and does something with the answer. */
    private void ask(final String title, final String initial, final java.util.function.Consumer<String> action) {
        this.askTitle = title;
        this.askAction = action;
        this.askField.set(initial);
        this.ask.open();
        this.ask.focus(this.askField);
    }

    private void layoutAsk(final Popup p) {
        this.askField.setBounds(p.x() + 4, p.contentTop() + 3, p.width() - 8, 11);
        this.askOk.setBounds(p.right() - 38, p.bottom() - 15, 34, 11);
    }

    private void layoutSettings(final Popup p) {
        final List<dev.jstech.core.client.gui.component.UiComponent> children = p.children();
        children.get(0).setBounds(p.x() + 4, p.contentTop() + 4, 50, 9);
        this.tabStepper.setBounds(p.x() + 56, p.contentTop() + 2, 96, 12);
        children.get(2).setBounds(p.right() - 38, p.bottom() - 15, 34, 11);
    }

    /* The window */

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        this.workspace.setPalette(InkPalette.forGround(osSkin.isDark()));
        this.terminal.setSkin(osSkin);
    }

    @Override
    public String title() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return this.folderOpen ? shortName(this.workspace.folder()) + " - Virtual Studio Code"
                    : "Welcome - Virtual Studio Code";
        }
        return doc.name() + (doc.dirty() ? " *" : "") + " - " + shortName(this.workspace.folder())
                + " - Virtual Studio Code";
    }

    @Override
    public int defaultWidth() {
        return 300;
    }

    @Override
    public int defaultHeight() {
        return 186;
    }

    @Override
    public int minWidth() {
        return 210;
    }

    @Override
    public int minHeight() {
        return 120;
    }

    @Override
    public void onRestored() {
        if (this.folderOpen) {
            this.workspace.refresh();
        }
    }

    @Override
    public void onClosed() {
        this.workspace.release();
        this.terminal.release();
        this.picker.release();
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        final UiContext ctx = new UiContext(this.skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, this.skin.windowBg());
        this.root.setBounds(x, y, width, height);
        this.menuBar.setBounds(x, y, width, MenuBar.HEIGHT);
        this.menuBar.setWindow(x, y, width, height);
        final int top = y + MenuBar.HEIGHT;
        final int bodyTotal = height - MenuBar.HEIGHT - STATUS_H;

        drawRail(g, x, top, bodyTotal);
        final boolean sideShown = this.folderOpen || this.side == Side.EXTENSIONS;
        final int sideW = sideShown ? SIDE_W : 0;
        if (sideShown) {
            this.skin.panel(g, x + RAIL_W, top, SIDE_W, bodyTotal);
            g.drawString(font, this.side == Side.EXTENSIONS ? "EXTENSIONS" : "EXPLORER", x + RAIL_W + 4, top + 1,
                    this.skin.dim(), false);
        }
        // A list that is not on show is hidden outright: one with no room still has rows to draw.
        this.explorer.setVisible(sideShown && this.side != Side.EXTENSIONS);
        this.extensions.setVisible(sideShown && this.side == Side.EXTENSIONS);
        this.explorer.setBounds(x + RAIL_W, top + CAPTION_H, SIDE_W, bodyTotal - CAPTION_H);
        this.extensions.setBounds(x + RAIL_W, top + CAPTION_H, SIDE_W, bodyTotal - CAPTION_H);

        final int codeX = x + RAIL_W + sideW;
        final int codeW = width - RAIL_W - sideW;
        final CodeWorkspace.Doc doc = this.workspace.current();
        final boolean welcome = !this.folderOpen && doc == null;
        this.tabs.setVisible(!welcome);
        this.panelTabs.setVisible(!welcome);
        this.problems.setVisible(!welcome);
        this.terminal.setVisible(!welcome);
        if (welcome) {
            drawWelcome(g, font, codeX, top, codeW, bodyTotal);
        } else {
            this.skin.panel(g, codeX, top, codeW, TAB_H);
            this.tabs.setBounds(codeX, top, codeW, TAB_H);
            this.tabs.setSelected(this.workspace.currentIndex());
            final int bodyY = top + TAB_H;
            final int bodyH = bodyTotal - TAB_H;
            final boolean panelShown = bodyH - PANEL_H >= MIN_CODE_H;
            final int codeH = panelShown ? bodyH - PANEL_H : bodyH;
            if (doc != null) {
                doc.area().setBounds(codeX, bodyY, codeW, codeH);
            }
            if (panelShown) {
                layoutPanel(codeX, bodyY + codeH, codeW, PANEL_H);
            } else {
                layoutPanel(codeX, bodyY + codeH, codeW, 0);
            }
            if (doc == null) {
                drawEmpty(g, font, codeX, bodyY, codeW, codeH);
            }
        }
        this.root.render(g, ctx);
        if (doc != null) {
            doc.area().render(g, ctx);
        }
        drawStatus(g, font, x, y + height - STATUS_H, width, doc);
        // Whatever floats belongs over everything the window drew, the menus last of all.
        this.completions.render(g, ctx);
        this.palette.render(g, ctx, x, top, width);
        this.picker.render(g, ctx, x, y, width, height);
        if (this.ask.isOpen()) {
            this.ask.renderIn(g, ctx, x, y, width, height);
        }
        if (this.settings.isOpen()) {
            this.settings.renderIn(g, ctx, x, y, width, height);
        }
        this.menuBar.render(g, ctx);
    }

    private void layoutPanel(final int x, final int y, final int width, final int height) {
        final boolean shown = height > 0;
        final boolean terminalShown = this.panelTabs.selected() == PANEL_TERMINAL;
        this.panelTabs.setVisible(shown);
        this.terminal.setVisible(shown && terminalShown);
        this.problems.setVisible(shown && !terminalShown);
        this.panelTabs.setBounds(x, y, width, TAB_H);
        final int inner = Math.max(0, height - TAB_H);
        this.terminal.setBounds(x, y + TAB_H, width, inner);
        this.problems.setBounds(x, y + TAB_H, width, inner);
    }

    /** The rail: Explorer, Search, Run and Extensions, the one showing lit. */
    private void drawRail(final GuiGraphics g, final int x, final int y, final int height) {
        this.skin.panel(g, x, y, RAIL_W, height);
        final Side[] all = {Side.EXPLORER, Side.SEARCH, Side.RUN, Side.EXTENSIONS};
        for (int i = 0; i < all.length; i++) {
            final int iy = y + 4 + i * 13;
            final boolean lit = all[i] == this.side;
            final int color = lit ? this.skin.accent() : this.skin.dim();
            switch (all[i]) {
                case EXPLORER -> {
                    for (int k = 0; k < 3; k++) {
                        g.fill(x + 3, iy + k * 3, x + 11, iy + 1 + k * 3, color);
                    }
                }
                case SEARCH -> {
                    g.fill(x + 3, iy, x + 9, iy + 1, color);
                    g.fill(x + 3, iy, x + 4, iy + 6, color);
                    g.fill(x + 8, iy, x + 9, iy + 6, color);
                    g.fill(x + 3, iy + 5, x + 9, iy + 6, color);
                    g.fill(x + 8, iy + 6, x + 11, iy + 9, color);
                }
                case RUN -> {
                    for (int k = 0; k < 4; k++) {
                        g.fill(x + 4 + k, iy + k, x + 5 + k, iy + 8 - k, color);
                    }
                }
                case EXTENSIONS -> {
                    g.fill(x + 3, iy, x + 7, iy + 4, color);
                    g.fill(x + 7, iy, x + 11, iy + 4, color);
                    g.fill(x + 3, iy + 4, x + 7, iy + 8, color);
                }
            }
            if (lit) {
                g.fill(x, iy - 1, x + 1, iy + 9, this.skin.accent());
            }
        }
    }

    /** The Welcome page: the three ways to begin, what was opened lately, and where to read. */
    private void drawWelcome(final GuiGraphics g, final Font font, final int x, final int y,
                             final int width, final int height) {
        final InkPalette palette = InkPalette.forGround(this.skin.isDark());
        g.fill(x, y, x + width, y + height, palette.ground());
        Draw.pushScissor(g, x, y, x + width, y + height);
        this.links.clear();
        final int left = x + 10;
        int ly = y + 8;
        g.drawString(font, "Virtual Studio Code", left, ly, palette.plain(), false);
        ly += 10;
        g.drawString(font, "Editing evolved", left, ly, palette.gutterText(), false);
        ly += 14;
        g.drawString(font, "Start", left, ly, palette.plain(), false);
        ly += 10;
        ly = link(g, font, left, ly, "New File...", this::newFile, palette);
        ly = link(g, font, left, ly, "Open File...", this::pickFolder, palette);
        ly = link(g, font, left, ly, "Open Folder...", this::pickFolder, palette);
        ly += 6;
        g.drawString(font, "Recent", left, ly, palette.plain(), false);
        ly += 10;
        final List<String> recent = recent();
        if (recent.isEmpty()) {
            g.drawString(font, "Nothing yet", left, ly, palette.gutterText(), false);
            ly += 9;
        }
        for (final String path : recent) {
            ly = link(g, font, left, ly, shortName(path), () -> openRecent(path), palette);
        }
        final int rightX = x + width / 2 + 6;
        int ry = y + 32;
        g.drawString(font, "Walkthroughs", rightX, ry, palette.plain(), false);
        ry += 10;
        g.drawString(font, "Get started with Cannon:", rightX, ry, palette.gutterText(), false);
        ry += 9;
        g.drawString(font, "open a folder, write, press F5", rightX, ry, palette.gutterText(), false);
        ry += 14;
        g.drawString(font, "Help", rightX, ry, palette.plain(), false);
        ry += 10;
        ry = link(g, font, rightX, ry, "Keyboard shortcuts", this::showShortcuts, palette);
        link(g, font, rightX, ry, "Command palette", this::openPalette, palette);
        Draw.popScissor(g);
    }

    private int link(final GuiGraphics g, final Font font, final int x, final int y, final String label,
                     final Runnable action, final InkPalette palette) {
        g.drawString(font, label, x, y, palette.of(dev.jstech.computers.os.edit.CodeRuns.Ink.KEYWORD), false);
        this.links.add(new Link(x, y - 1, font.width(label), 9, action));
        return y + 9;
    }

    private void drawEmpty(final GuiGraphics g, final Font font, final int x, final int y,
                           final int width, final int height) {
        final InkPalette palette = InkPalette.forGround(this.skin.isDark());
        g.fill(x, y, x + width, y + height, palette.ground());
        Draw.pushScissor(g, x, y, x + width, y + height);
        g.drawString(font, "Pick a file in the Explorer, or Ctrl+P", x + 6, y + 6, palette.gutterText(), false);
        Draw.popScissor(g);
    }

    private void drawStatus(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final CodeWorkspace.Doc doc) {
        this.skin.statusBar(g, x, y, width, STATUS_H);
        final String where = this.folderOpen ? "[+] " + shortName(this.workspace.folder()) : "No folder open";
        g.drawString(font, where, x + 3, y + 1, this.skin.dim(), false);
        int right = x + width - 3;
        final IProgrammingLanguage language = this.workspace.language();
        if (language != null) {
            right -= font.width(language.displayName());
            g.drawString(font, language.displayName(), right, y + 1, this.skin.dim(), false);
            right -= 8;
        }
        if (doc != null) {
            final String spaces = "Spaces: " + this.tabSize;
            right -= font.width(spaces);
            g.drawString(font, spaces, right, y + 1, this.skin.dim(), false);
            right -= 8;
            final String pos = "Ln " + (doc.area().document().cursorLine() + 1)
                    + ", Col " + (doc.area().document().cursorCol() + 1);
            right -= font.width(pos);
            g.drawString(font, pos, right, y + 1, this.skin.dim(), false);
        }
        if (!this.workspace.status().isEmpty()) {
            final int from = x + 3 + font.width(where) + 8;
            g.drawString(font, font.plainSubstrByWidth(this.workspace.status(), Math.max(0, right - from - 8)),
                    from, y + 1, this.skin.dim(), false);
        }
    }

    /* Input */

    private boolean popupOpen() {
        return this.picker.isOpen() || this.ask.isOpen() || this.settings.isOpen();
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (this.picker.isOpen()) {
            this.picker.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.ask.isOpen()) {
            this.ask.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.settings.isOpen()) {
            this.settings.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.palette.isOpen()) {
            this.palette.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.menuBar.isOpen() || this.menuBar.mouseClicked(mouseX, mouseY, button)) {
            if (!this.menuBar.isOpen()) {
                // A menu that just closed took the click; a title that just opened one took it too.
                return;
            }
            return;
        }
        if (this.completions.mouseClicked(mouseX, mouseY, button)) {
            this.workspace.edited();
            return;
        }
        this.completions.close();
        // The rail.
        if (mouseX >= this.root.x() && mouseX < this.root.x() + RAIL_W && mouseY >= this.root.y() + MenuBar.HEIGHT) {
            final int slot = (int) ((mouseY - (this.root.y() + MenuBar.HEIGHT + 3)) / 13);
            switch (slot) {
                case 0 -> this.side = Side.EXPLORER;
                case 1 -> this.workspace.say("Search across files is not here yet");
                case 2 -> runFile();
                case 3 -> this.side = Side.EXTENSIONS;
                default -> { }
            }
            return;
        }
        for (final Link link : this.links) {
            if (mouseX >= link.x() && mouseX < link.x() + link.width()
                    && mouseY >= link.y() && mouseY < link.y() + link.height()) {
                link.action().run();
                return;
            }
        }
        if (this.terminal.contains(mouseX, mouseY)) {
            this.typingInTerminal = true;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.area().contains(mouseX, mouseY)) {
            this.typingInTerminal = false;
            doc.area().mouseClicked(mouseX, mouseY, button);
            return;
        }
        this.root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(final char c) {
        if (this.picker.isOpen()) {
            return this.picker.charTyped(c);
        }
        if (this.ask.isOpen()) {
            return this.ask.charTyped(c);
        }
        if (this.settings.isOpen()) {
            return this.settings.charTyped(c);
        }
        if (this.palette.isOpen()) {
            return this.palette.charTyped(c);
        }
        if (this.typingInTerminal) {
            return this.terminal.charTyped(c);
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null || !doc.area().charTyped(c)) {
            return false;
        }
        this.workspace.edited();
        if (c == '.' || this.completions.isOpen()) {
            offerCompletions(doc);
        }
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        final boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        final boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (this.picker.isOpen()) {
            return this.picker.keyPressed(key, scanCode, modifiers);
        }
        if (this.ask.isOpen()) {
            return this.ask.keyPressed(key, scanCode, modifiers);
        }
        if (this.settings.isOpen()) {
            return this.settings.keyPressed(key, scanCode, modifiers);
        }
        if (this.palette.isOpen()) {
            return this.palette.keyPressed(key, scanCode, modifiers);
        }
        if (this.menuBar.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (ctrl && shift && key == GLFW.GLFW_KEY_P) {
            openPalette();
            return true;
        }
        if (ctrl && shift && key == GLFW.GLFW_KEY_B) {
            buildFile();
            return true;
        }
        if (ctrl && shift && key == GLFW.GLFW_KEY_M) {
            this.panelTabs.setSelected(this.panelTabs.selected() == PANEL_PROBLEMS ? PANEL_TERMINAL : PANEL_PROBLEMS);
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_P) {
            goToFile();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_G) {
            goToLine();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_F) {
            find();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_O) {
            pickFolder();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            this.workspace.save();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_COMMA) {
            openSettings();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_GRAVE_ACCENT) {
            focusTerminal();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            runFile();
            return true;
        }
        if (this.typingInTerminal) {
            return this.terminal.keyPressed(key, scanCode, modifiers);
        }
        if (this.completions.keyPressed(key, scanCode, modifiers)) {
            this.workspace.edited();
            return true;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return false;
        }
        if (ctrl && key == GLFW.GLFW_KEY_SPACE) {
            offerCompletions(doc);
            return true;
        }
        if (doc.area().keyPressed(key, scanCode, modifiers)) {
            this.workspace.edited();
            if (this.completions.isOpen()) {
                offerCompletions(doc);
            }
            return true;
        }
        return false;
    }

    private void offerCompletions(final CodeWorkspace.Doc doc) {
        final CodeArea area = doc.area();
        this.completions.offer(area, doc.path(), new int[] {area.x(), area.y(), area.width(), area.height()});
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        if (this.palette.isOpen()) {
            return this.palette.mouseScrolled(0, 0, delta);
        }
        if (this.typingInTerminal) {
            return this.terminal.mouseScrolled(this.terminal.x(), this.terminal.y(), delta);
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return this.explorer.mouseScrolled(this.explorer.x(), this.explorer.y(), delta);
        }
        return doc.area().mouseScrolled(doc.area().x(), doc.area().y(), delta);
    }

    @Override
    public boolean modalActive() {
        return popupOpen();
    }

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return;
        }
        final String message = doc.area().messageAt(mouseX, mouseY);
        if (!message.isEmpty()) {
            g.renderTooltip(font, Component.literal(message), mouseX, mouseY);
        }
    }
}
