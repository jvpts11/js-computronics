/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.cannon.CannonSemantics;
import dev.jstech.computers.cannon.SourceFile;
import dev.jstech.computers.cannon.sem.IMemberSymbol;
import dev.jstech.computers.cannon.sem.NamedType;
import dev.jstech.computers.operation.payload.DiskFilesPayload;
import dev.jstech.computers.os.edit.InkPalette;
import dev.jstech.computers.os.edit.ProblemReport;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.ContextMenu;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.MenuBar;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.Popup;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.UiContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Exposure: the editor that reads the whole folder rather than the file in front of you.
 *
 * <p>It offers nothing as you type, on purpose. What it has instead is every complaint from every
 * program in the folder, in one table, which is the only way to find out that changing something
 * shared broke three programs nobody has open. Beside the code it keeps an outline of what the open
 * file declares, so a long program can be walked without scrolling it.
 *
 * <p>Around that it is an editor like the others: files are made, opened, saved and closed from the
 * File menu, a folder is picked to work in, the right button on the code offers the clipboard and the
 * refactorings, and a program is built and run at the machine's terminal.
 */
public final class ExposureApp implements IDesktopApp {

    private static final int TOOLBAR_H = 13;
    private static final int SIDE_W = 74;
    private static final int OUTLINE_W = 68;
    private static final int CAPTION_H = 9;
    private static final int TAB_H = 10;
    private static final int STATUS_H = 9;
    private static final int ROW_H = 9;
    private static final int DOCK_H = 54;
    private static final int MIN_CODE_H = 36;
    /** The problems table's columns: the file's name, then the line, then what was said. */
    private static final int NAME_W = 68;
    private static final int LINE_W = 26;
    private static final String KEY = "Exposure";

    /** One line of the outline: how deep it sits, what it says, and the line it stands on. */
    private record Outline(int depth, String label, int line) {
    }

    private final BlockPos host;
    private final CodeWorkspace workspace;
    private OsSkin skin = OsSkin.fallback();

    private final Panel root = new Panel();
    private final MenuBar menuBar = new MenuBar(92, 10);
    private final ListView<DiskFilesPayload.WireFile> explorer;
    private final TabStrip tabs;
    private final ListView<Outline> outline;
    private final ListView<ProblemReport.Row> problems;
    private final Button survey;
    /** The system's file window, for everything the editor opens or saves by choosing on the disk. */
    private final FileDialog dialog;
    private final ContextMenu editorContext = new ContextMenu(110, 11);

    /* The small window that asks for one thing: a file's name, a line number. */
    private final Popup ask = new Popup(() -> this.askTitle, 150, 44).setLayouter(this::layoutAsk);
    private final TextField askField = new TextField(64);
    private final Button askOk;
    private String askTitle = "";
    private java.util.function.Consumer<String> askAction = value -> { };

    /* The question asked before a file with changes is closed. */
    private final Popup askClose = new Popup(() -> "Save changes to " + closingName() + "?", 176, 40)
            .setLayouter(this::layoutAskClose);
    private int closing = -1;

    /** The outline of the open file, read again only when its text changes. */
    private List<Outline> outlineRows = List.of();
    private String outlineOf = "";
    /** The size the code is drawn at, shared by every open file. */
    private float editorScale = 1.0f;

    public ExposureApp(final BlockPos host) {
        this.host = host;
        this.workspace = new CodeWorkspace(host);
        this.dialog = new FileDialog(host, this);
        this.explorer = this.root.add(new ListView<>(this.workspace::files, ROW_H, this::drawFileRow))
                .setOnClick(this::onFilePicked);
        this.tabs = this.root.add(new TabStrip(this.workspace::tabLabels).fitToLabels(10).setUnderline(false)
                .setCloseable(this::closeTab));
        this.tabs.setOnSelect(this.workspace::setCurrent);
        this.outline = this.root.add(new ListView<>(this::outlineRows, ROW_H, this::drawOutlineRow))
                .setOnClick(this::onOutlinePicked);
        this.problems = this.root.add(new ListView<>(this.workspace::folderProblems, ROW_H, this::drawProblemRow))
                .setOnClick(this::onProblemPicked);
        this.survey = this.root.add(new Button("Rebuild all", this.workspace::surveyFolder));
        this.root.add(this.menuBar);
        this.menuBar.add("File", this::fileMenu).add("Source", this::sourceMenu).add("Project", this::projectMenu);
        this.ask.add(this.askField);
        this.askOk = this.ask.add(new Button("OK", () -> {
            this.ask.close();
            this.askAction.accept(this.askField.edit().trim());
        }).setPrimary(true));
        this.askField.setOnCommit(value -> this.askOk.mouseClicked(this.askOk.center()[0], this.askOk.center()[1], 0));
        this.askClose.add(new Button("Save", () -> {
            this.askClose.close();
            final int index = this.closing;
            this.workspace.setCurrent(index);
            this.workspace.save();
            this.workspace.close(index);
        }).setPrimary(true));
        this.askClose.add(new Button("Don't Save", () -> {
            this.askClose.close();
            this.workspace.close(this.closing);
        }));
        this.askClose.add(new Button("Cancel", this.askClose::close));
        this.workspace.refresh();
        this.workspace.surveyFolder();
    }

    /* The menus */

    private ContextMenu.Item item(final String label, final boolean enabled, final Runnable action) {
        return new ContextMenu.Item(label, enabled, action);
    }

    private boolean hasDoc() {
        return this.workspace.current() != null;
    }

    private List<ContextMenu.Item> fileMenu() {
        return List.of(
                item("New File...", true, this::newFile),
                item("Open File...", true, this::openFileByName),
                item("Open Folder...", true, this::pickFolder),
                ContextMenu.Item.separator(),
                item("Save", hasDoc(), this::save),
                item("Save As...", hasDoc(), this::saveAs),
                item("Save All", this.workspace.anyDirty(), () -> {
                    this.workspace.saveAll();
                    this.workspace.surveyFolder();
                }),
                ContextMenu.Item.separator(),
                item("Close File", hasDoc(), () -> closeTab(this.workspace.currentIndex())),
                item("Exit", true, () -> DesktopScreen.requestClose(KEY)));
    }

    private List<ContextMenu.Item> sourceMenu() {
        return List.of(
                item("Toggle Line Comment", hasDoc(), this::toggleComment),
                item("Go To Line...", hasDoc(), this::goToLine),
                ContextMenu.Item.submenu("Refactor", List.of(
                        item("Implement Interface", hasDoc(), this::implementInterface))),
                ContextMenu.Item.separator(),
                item("Zoom In", hasDoc(), () -> zoomEditor(1)),
                item("Zoom Out", hasDoc(), () -> zoomEditor(-1)),
                item("Reset Zoom", hasDoc(), () -> setEditorScale(1.0f)));
    }

    private List<ContextMenu.Item> projectMenu() {
        return List.of(
                item("Rebuild All", true, this.workspace::surveyFolder),
                item("Run at Terminal", hasDoc(), this::runAtTerminal));
    }

    /** The menu the right button opens on the code. */
    private List<ContextMenu.Item> editorMenu() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        final boolean selected = doc != null && doc.area().document().hasSelection();
        return List.of(
                item("Cut", selected, () -> pressInEditor(GLFW.GLFW_KEY_X)),
                item("Copy", hasDoc(), () -> pressInEditor(GLFW.GLFW_KEY_C)),
                item("Paste", hasDoc(), () -> pressInEditor(GLFW.GLFW_KEY_V)),
                ContextMenu.Item.separator(),
                item("Toggle Line Comment", hasDoc(), this::toggleComment),
                ContextMenu.Item.submenu("Refactor", List.of(
                        item("Implement Interface", hasDoc(), this::implementInterface))));
    }

    private void pressInEditor(final int key) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            doc.area().keyPressed(key, 0, GLFW.GLFW_MOD_CONTROL);
            this.workspace.edited();
        }
    }

    /* The file actions */

    private void newFile() {
        ask("New File", "untitled.can", name -> {
            if (!name.isEmpty()) {
                createFile(name);
            }
        });
    }

    /** Makes an empty file called {@code name} in the folder and opens it, as New File does. */
    public void createFile(final String name) {
        final String folder = this.workspace.folder();
        this.workspace.newFile(folder.isEmpty() ? name : folder + "/" + name);
        applyScale();
    }

    private void openFileByName() {
        this.dialog.openFile("Open File", this.workspace.folder(), FileDialog.Filter.sources(), this::openFile);
    }

    private void pickFolder() {
        this.dialog.openFolder("Open Folder", this.workspace.folder(), folder -> {
            this.workspace.setFolder(folder);
            this.workspace.surveyFolder();
        });
    }

    /** Shows the Open File window, as the File menu does; a test drives it from here. */
    public void showOpenFileDialog() {
        openFileByName();
    }

    /** The system's file window this editor opens, for a test to drive. */
    public FileDialog dialog() {
        return this.dialog;
    }

    private void save() {
        /*
         * Saving is what makes the disk disagree with the table, so the survey is run again: the
         * point of this editor is that the report is about what is really there.
         */
        this.workspace.save();
        this.workspace.surveyFolder();
    }

    private void saveAs() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return;
        }
        this.dialog.saveAs("Save As", this.workspace.folder(), doc.name(), FileDialog.Filter.sources(), path -> {
            this.workspace.saveAs(path);
            this.workspace.surveyFolder();
        });
    }

    private void closeTab(final int index) {
        final List<CodeWorkspace.Doc> docs = this.workspace.docs();
        if (index < 0 || index >= docs.size()) {
            return;
        }
        if (docs.get(index).dirty()) {
            this.closing = index;
            this.askClose.open();
            return;
        }
        this.workspace.close(index);
    }

    private String closingName() {
        final List<CodeWorkspace.Doc> docs = this.workspace.docs();
        return this.closing >= 0 && this.closing < docs.size() ? docs.get(this.closing).name() : "";
    }

    private void toggleComment() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            doc.area().document().toggleLinePrefix("// ");
            this.workspace.edited();
        }
    }

    private void goToLine() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return;
        }
        ask("Go To Line", String.valueOf(doc.area().document().cursorLine() + 1), typed -> {
            try {
                final int line = Integer.parseInt(typed.trim());
                doc.area().document().setCursor(Math.max(0, line - 1), 0);
            } catch (final NumberFormatException ignored) {
                this.workspace.say("Not a line number: " + typed);
            }
        });
    }

    private void implementInterface() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            this.workspace.say(this.workspace.implementInterface(doc) ? "Interface implemented"
                    : "Nothing to implement here");
        }
    }

    /** Compiles the open file and runs what came out, at the machine's terminal, the way Exposure has no terminal of its own. */
    private void runAtTerminal() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return;
        }
        this.workspace.save();
        final String source = doc.path();
        final int dot = source.lastIndexOf('.');
        final String built = (dot > source.lastIndexOf('/') ? source.substring(0, dot) : source) + ".asm";
        DesktopScreen.requestTypeAtTerminal(List.of("cannonc " + quote(source), "cannon run " + quote(built)));
    }

    private static String quote(final String path) {
        return path.contains(" ") ? "\"" + path + "\"" : path;
    }

    private void zoomEditor(final int steps) {
        setEditorScale(this.editorScale + steps * 0.25f);
    }

    private void setEditorScale(final float value) {
        this.editorScale = Math.max(0.75f, Math.min(2.0f, value));
        applyScale();
    }

    private void applyScale() {
        for (final CodeWorkspace.Doc doc : this.workspace.docs()) {
            doc.area().setScale(this.editorScale);
        }
    }

    /** Asks for one thing in a small window and does something with the answer. */
    private void ask(final String title, final String initial, final java.util.function.Consumer<String> action) {
        this.askTitle = title;
        this.askAction = action;
        this.askField.set(initial);
        final int dot = initial.lastIndexOf('.');
        this.askField.setCaret(dot > 0 ? dot : initial.length());
        this.ask.open();
        this.ask.focus(this.askField);
    }

    private void layoutAsk(final Popup p) {
        this.askField.setBounds(p.x() + 4, p.contentTop() + 3, p.width() - 8, 11);
        this.askOk.setBounds(p.right() - 38, p.bottom() - 15, 34, 11);
    }

    private void layoutAskClose(final Popup p) {
        final List<dev.jstech.core.client.gui.component.UiComponent> c = p.children();
        final int by = p.bottom() - 15;
        c.get(0).setBounds(p.right() - 134, by, 38, 11);
        c.get(1).setBounds(p.right() - 92, by, 50, 11);
        c.get(2).setBounds(p.right() - 38, by, 34, 11);
    }

    /* What a test reads */

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

    /** The folder the editor is on. */
    public String folder() {
        return this.workspace.folder();
    }

    /** The names of the programs the folder holds, as the package list shows them. */
    public List<String> fileNames() {
        final List<String> out = new ArrayList<>();
        for (final DiskFilesPayload.WireFile file : this.workspace.files()) {
            out.add(ProblemReport.nameOf(file.path()));
        }
        return out;
    }

    /** How many complaints the table holds, over every program in the folder. */
    public int problemCount() {
        return this.workspace.folderProblems().size();
    }

    /** Whether the small asking window is up. */
    public boolean asking() {
        return this.ask.isOpen();
    }

    /** The desktop-local centre of the menu bar's {@code title}, where a test clicks to open it; null for none. */
    public int[] menuTitlePoint(final String title) {
        return this.menuBar.titleCenter(this.menuBar.titles().indexOf(title));
    }

    /** Whether a menu is dropped down from the bar. */
    public boolean menuOpen() {
        return this.menuBar.isOpen();
    }

    /** The desktop-local centre of the open menu's {@code index}-th entry, where a test clicks it. */
    public int[] menuItemPoint(final int index) {
        return this.menuBar.menu().itemCenter(index);
    }

    /** Whether the folder picker is up. */
    public boolean pickingFolder() {
        return this.dialog.isOpen();
    }

    /* The lists */

    private void drawFileRow(final GuiGraphics g, final UiContext ctx, final DiskFilesPayload.WireFile file,
                             final int index, final int x, final int y, final int width, final int height,
                             final boolean hovered, final boolean selected) {
        ctx.skin().listRow(g, x, y, width, height, hovered, selected);
        FileIcons.draw(g, x + 2, y, FileIcons.kindOfPath(file.path(), false));
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(ProblemReport.nameOf(file.path()), width - 16),
                x + 14, y + 1, ctx.skin().listRowText(selected), false);
    }

    private void onFilePicked(final int index, final int button, final double mx, final double my) {
        if (index >= 0 && index < this.workspace.files().size()) {
            openFile(this.workspace.files().get(index).path());
        }
    }

    /**
     * The open file's own shape: the types it declares and what each of them has.
     *
     * <p>Read from the checker rather than from the text, so it says what the program actually declares
     * and not what looks like a declaration. It is read again only when the text changes, because
     * walking a program to draw a panel every frame is work for nothing.
     */
    private List<Outline> outlineRows() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return List.of();
        }
        final String text = doc.area().text();
        final String key = doc.path() + ":" + text.length() + ":" + text.hashCode();
        if (key.equals(this.outlineOf)) {
            return this.outlineRows;
        }
        final List<Outline> rows = new ArrayList<>();
        if (doc.path().toLowerCase(java.util.Locale.ROOT).endsWith(".can")) {
            for (final NamedType type : CannonSemantics.check(
                    List.of(new SourceFile(doc.name(), text))).model().declaredTypes()) {
                rows.add(new Outline(0, type.name(), 0));
                for (final IMemberSymbol member : type.members()) {
                    rows.add(new Outline(1, describe(member), 0));
                }
            }
        }
        this.outlineRows = rows;
        this.outlineOf = key;
        return rows;
    }

    /** A member as the outline shows it: its name and what it gives back. */
    private static String describe(final IMemberSymbol member) {
        return switch (member) {
            case IMemberSymbol.MethodSymbol method ->
                    method.name() + "() : " + method.returnType().describe();
            case IMemberSymbol.PropertySymbol property ->
                    property.name() + " : " + property.type().describe();
            case IMemberSymbol.FieldSymbol field -> field.name() + " : " + field.type().describe();
            case IMemberSymbol.EventSymbol event -> event.name() + " : " + event.delegateType().name();
            case IMemberSymbol.ConstructorSymbol constructor -> constructor.name() + "()";
        };
    }

    private void drawOutlineRow(final GuiGraphics g, final UiContext ctx, final Outline row, final int index,
                                final int x, final int y, final int width, final int height,
                                final boolean hovered, final boolean selected) {
        ctx.skin().listRow(g, x, y, width, height, hovered, selected);
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(row.label(), width - 4 - row.depth() * 5),
                x + 2 + row.depth() * 5, y + 1, ctx.skin().listRowText(selected), false);
    }

    /**
     * Clicking an outline row goes to what it names.
     *
     * <p>The checker records where a name was written for its own complaints, not for a panel to jump
     * by, so the line is found by looking for the declaration in the text. It is the file's own text and
     * the name is the one the checker read out of it, so what is found is the thing that was clicked.
     */
    private void onOutlinePicked(final int index, final int button, final double mx, final double my) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        final List<Outline> rows = outlineRows();
        if (doc == null || index < 0 || index >= rows.size()) {
            return;
        }
        final String wanted = rows.get(index).label().split("[ (:]", 2)[0];
        for (int line = 0; line < doc.area().document().lineCount(); line++) {
            final int at = doc.area().document().line(line).indexOf(wanted);
            if (at >= 0) {
                doc.area().document().setCursor(line, at);
                return;
            }
        }
    }

    /**
     * One row of the table: which file, where in it, and what the compiler said.
     *
     * <p>The file's name is on every row rather than heading a group, because the point of the table is
     * that the problems are spread across files and a row has to say which one on its own.
     */
    private void drawProblemRow(final GuiGraphics g, final UiContext ctx, final ProblemReport.Row row,
                                final int index, final int x, final int y, final int width, final int height,
                                final boolean hovered, final boolean selected) {
        ctx.skin().listRow(g, x, y, width, height, hovered, selected);
        final Font font = ctx.font();
        g.drawString(font, font.plainSubstrByWidth(row.name(), NAME_W - 4), x + 2, y + 1,
                ctx.skin().text(), false);
        final String where = String.valueOf(row.complaint().line());
        g.drawString(font, where, x + NAME_W, y + 1, ctx.skin().dim(), false);
        final int textX = x + NAME_W + LINE_W;
        g.drawString(font, font.plainSubstrByWidth(row.complaint().message(), width - (textX - x) - 2),
                textX, y + 1, 0xFFC0392B, false);
    }

    /** Clicking a row opens the file it is about and puts the caret where the compiler stopped. */
    private void onProblemPicked(final int index, final int button, final double mx, final double my) {
        final List<ProblemReport.Row> rows = this.workspace.folderProblems();
        if (index < 0 || index >= rows.size()) {
            return;
        }
        final ProblemReport.Row row = rows.get(index);
        openFile(row.path());
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.path().equals(row.path())) {
            doc.area().document().setCursor(row.complaint().line() - 1,
                    Math.max(0, row.complaint().column() - 1));
        }
    }

    /* The window */

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        this.workspace.setPalette(InkPalette.forGround(osSkin.isDark()));
    }

    @Override
    public String title() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        return doc == null ? KEY : doc.name() + (doc.dirty() ? " *" : "") + " - " + KEY;
    }

    @Override
    public int defaultWidth() {
        return 316;
    }

    @Override
    public int defaultHeight() {
        return 190;
    }

    @Override
    public int minWidth() {
        return 236;
    }

    @Override
    public int minHeight() {
        return 118;
    }

    /** Opens a file, as picking this program with "Open with" does. */
    @Override
    public void openFile(final String path) {
        this.workspace.open(path);
        applyScale();
    }

    @Override
    public void onRestored() {
        this.workspace.refresh();
        this.workspace.surveyFolder();
    }

    @Override
    public String saveState() {
        return this.workspace.describeOpen();
    }

    @Override
    public void restoreState(final String state) {
        final String folder = CodeWorkspace.folderOf(state);
        if (!folder.isEmpty()) {
            this.workspace.setFolder(folder);
            this.workspace.surveyFolder();
        }
        this.workspace.reopen(state);
        applyScale();
    }

    @Override
    public void onClosed() {
        this.workspace.release();
        this.dialog.release();
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        final UiContext ctx = new UiContext(this.skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, this.skin.windowBg());
        applyScale();

        this.menuBar.setBounds(x, y, width, MenuBar.HEIGHT);
        // A drop-down stays inside the window; without this it is clamped into nothing and never shows.
        this.menuBar.setWindow(x, y, width, height);
        this.skin.panel(g, x, y + MenuBar.HEIGHT, width, TOOLBAR_H);
        this.survey.setBounds(x + 3, y + MenuBar.HEIGHT + 2, 52, TOOLBAR_H - 4);
        final String where = "Folder: C:\\" + this.workspace.folder().replace('/', '\\');
        g.drawString(font, font.plainSubstrByWidth(where, width - 64), x + 60, y + MenuBar.HEIGHT + 4,
                this.skin.dim(), false);

        final int bodyY = y + MenuBar.HEIGHT + TOOLBAR_H;
        final int bodyH = height - MenuBar.HEIGHT - TOOLBAR_H - STATUS_H;
        final boolean dockShown = bodyH - DOCK_H >= MIN_CODE_H + TAB_H;
        final int upperH = dockShown ? bodyH - DOCK_H : bodyH;

        this.skin.panel(g, x, bodyY, SIDE_W, upperH);
        g.drawString(font, "PACKAGE", x + 3, bodyY + 1, this.skin.dim(), false);
        this.explorer.setBounds(x, bodyY + CAPTION_H, SIDE_W, upperH - CAPTION_H);

        final int outlineX = x + width - OUTLINE_W;
        this.skin.panel(g, outlineX, bodyY, OUTLINE_W, upperH);
        g.drawString(font, "OUTLINE", outlineX + 3, bodyY + 1, this.skin.dim(), false);
        this.outline.setBounds(outlineX, bodyY + CAPTION_H, OUTLINE_W, upperH - CAPTION_H);

        final int codeX = x + SIDE_W;
        final int codeW = width - SIDE_W - OUTLINE_W;
        this.skin.panel(g, codeX, bodyY, codeW, TAB_H);
        this.tabs.setBounds(codeX, bodyY, codeW, TAB_H);
        this.tabs.setSelected(this.workspace.currentIndex());

        final CodeWorkspace.Doc doc = this.workspace.current();
        final int codeY = bodyY + TAB_H;
        final int codeH = upperH - TAB_H;
        if (doc != null) {
            doc.area().setBounds(codeX, codeY, codeW, codeH);
        }
        layoutDock(g, font, x, bodyY + upperH, width, dockShown ? DOCK_H : 0);

        this.root.setBounds(x, y, width, height);
        this.root.render(g, ctx);
        if (doc != null) {
            doc.area().render(g, ctx);
        } else {
            drawEmpty(g, font, codeX, codeY, codeW, codeH);
        }
        drawStatus(g, font, x, y + height - STATUS_H, width, doc);
        if (this.ask.isOpen()) {
            this.ask.renderIn(g, ctx, x, y, width, height);
        }
        if (this.askClose.isOpen()) {
            this.askClose.renderIn(g, ctx, x, y, width, height);
        }
        this.editorContext.render(g, ctx);
        this.menuBar.render(g, ctx);
    }

    /** The table under everything: a header row, then a row per complaint. */
    private void layoutDock(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final int height) {
        if (height <= 0) {
            this.problems.setBounds(0, 0, 0, 0);
            return;
        }
        final int count = this.workspace.folderProblems().size();
        this.skin.panel(g, x, y, width, CAPTION_H);
        // The heading names the columns the rows have: the file, the line, and what was said.
        final String heading = "PROBLEMS (" + count + ")";
        g.drawString(font, heading, x + 3, y + 1, this.skin.dim(), false);
        if (x + 3 + font.width(heading) + 6 < x + NAME_W) {
            g.drawString(font, "LINE", x + NAME_W, y + 1, this.skin.dim(), false);
        }
        g.drawString(font, "WHAT THE COMPILER SAID", x + NAME_W + LINE_W, y + 1, this.skin.dim(), false);
        this.problems.setBounds(x, y + CAPTION_H, width, height - CAPTION_H);
    }

    private void drawEmpty(final GuiGraphics g, final Font font, final int x, final int y,
                           final int width, final int height) {
        final InkPalette palette = InkPalette.forGround(this.skin.isDark());
        g.fill(x, y, x + width, y + height, palette.ground());
        Draw.pushScissor(g, x, y, x + width, y + height);
        g.drawString(font, "Pick a file, or File > New File", x + 6, y + 6, palette.gutterText(), false);
        Draw.popScissor(g);
    }

    private void drawStatus(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final CodeWorkspace.Doc doc) {
        this.skin.statusBar(g, x, y, width, STATUS_H);
        g.drawString(font, this.workspace.status().isEmpty() ? "Writable" : this.workspace.status(),
                x + 3, y + 1, this.skin.dim(), false);
        if (doc != null) {
            final String where = (doc.area().document().cursorLine() + 1)
                    + " : " + (doc.area().document().cursorCol() + 1);
            g.drawString(font, where, x + width - font.width(where) - 3, y + 1, this.skin.dim(), false);
        }
    }

    /* Input */

    /*
     * The file window is not counted here: it is a window of its own, in front of this one, and the
     * desktop keeps this window from taking anything while it is up.
     */
    private boolean modal() {
        return this.ask.isOpen() || this.askClose.isOpen();
    }

    /** Escape closes whatever menu or window is up before it means anything to the desktop. */
    @Override
    public boolean wantsEscape() {
        return modal() || this.editorContext.isOpen() || this.menuBar.isOpen();
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (this.ask.isOpen()) {
            this.ask.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.askClose.isOpen()) {
            this.askClose.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (this.editorContext.isOpen()) {
            this.editorContext.mouseClicked(mouseX, mouseY, button);
            return;
        }
        // An open menu gets the click first: on one of its items, on another title, or outside to close.
        if (this.menuBar.mouseClicked(mouseX, mouseY, button)) {
            return;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.area().contains(mouseX, mouseY)) {
            if (button == 1) {
                this.editorContext.open(editorMenu(), (int) mouseX, (int) mouseY, window.x(), window.y(),
                        window.width(), window.height());
                return;
            }
            doc.area().mouseClicked(mouseX, mouseY, button);
            return;
        }
        this.root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseDragged(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && !modal()) {
            doc.area().mouseDragged(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            doc.area().mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean charTyped(final char c) {
        if (this.ask.isOpen()) {
            return this.ask.charTyped(c);
        }
        if (this.askClose.isOpen()) {
            return true;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.area().charTyped(c)) {
            this.workspace.edited();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        final boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (this.ask.isOpen()) {
            return this.ask.keyPressed(key, scanCode, modifiers);
        }
        if (this.askClose.isOpen()) {
            return this.askClose.keyPressed(key, scanCode, modifiers);
        }
        if (this.editorContext.isOpen()) {
            return this.editorContext.keyPressed(key, scanCode, modifiers);
        }
        if (this.menuBar.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_S -> {
                    save();
                    return true;
                }
                case GLFW.GLFW_KEY_N -> {
                    newFile();
                    return true;
                }
                case GLFW.GLFW_KEY_O -> {
                    openFileByName();
                    return true;
                }
                case GLFW.GLFW_KEY_W -> {
                    closeTab(this.workspace.currentIndex());
                    return true;
                }
                case GLFW.GLFW_KEY_PERIOD -> {
                    implementInterface();
                    return true;
                }
                default -> { }
            }
        }
        if (key == GLFW.GLFW_KEY_F5) {
            this.workspace.surveyFolder();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F6) {
            runAtTerminal();
            return true;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.area().keyPressed(key, scanCode, modifiers)) {
            this.workspace.edited();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return false;
        }
        return doc.area().mouseScrolled(doc.area().x(), doc.area().y(), delta);
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
