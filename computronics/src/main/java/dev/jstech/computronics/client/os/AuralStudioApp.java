/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.DiskFilesPayload;
import dev.jstech.computronics.os.edit.InkPalette;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.ContextMenu;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.language.IProgrammingLanguage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Aural Studio: the whole workshop in one window.
 *
 * <p>It is the same workspace the lighter editor uses, arranged for somebody who wants everything on
 * the glass at once: the project down the left with what the compiler produced from it, the source and
 * the Assembly it became side by side, and every complaint listed underneath. What it has that nothing
 * else does is the price: the list of what can follow a name says what each call will cost the program,
 * before the line is written.
 */
public final class AuralStudioApp implements IDesktopApp {

    private static final int MENU_H = 10;
    private static final int TOOLBAR_H = 13;
    private static final int SIDE_W = 84;
    private static final int CAPTION_H = 9;
    private static final int TAB_H = 10;
    private static final int STATUS_H = 9;
    private static final int ROW_H = 9;
    private static final int DOCK_H = 52;
    private static final int MIN_CODE_H = 36;

    private static final List<String> DOCK_TABS = List.of("ERROR LIST", "BUILD OUTPUT");
    private static final int DOCK_ERRORS = 0;

    /** One row of the project tree: how deep it sits, what it says, and the file it stands for. */
    private record Row(int depth, String label, String path) {
    }

    private final CodeWorkspace workspace;
    private OsSkin skin = OsSkin.fallback();

    private final Panel root = new Panel();
    private final ListView<Row> solution;
    private final TabStrip tabs;
    private final TabStrip dockTabs;
    private final ListView<IProgrammingLanguage.Complaint> errors;
    private final ListView<String> output;
    private final Button start;
    private final ContextMenu menu = new ContextMenu(78, 10);
    private final CodeCompletions completions = new CodeCompletions().withCosts(true);

    /** What the last build said, which is what the Build Output pane shows. */
    private final List<String> built = new ArrayList<>();

    public AuralStudioApp(final BlockPos host) {
        this.workspace = new CodeWorkspace(host);
        this.solution = this.root.add(new ListView<>(this::rows, ROW_H, this::drawTreeRow))
                .setOnClick(this::onTreePicked);
        this.tabs = this.root.add(new TabStrip(this.workspace::tabLabels).fitToLabels(10).setUnderline(false));
        this.tabs.setOnSelect(this.workspace::setCurrent);
        this.dockTabs = this.root.add(new TabStrip(DOCK_TABS).fitToLabels(12).setUnderline(true));
        this.errors = this.root.add(new ListView<>(this::complaints, ROW_H, this::drawErrorRow))
                .setOnClick(this::onErrorPicked);
        this.output = this.root.add(new ListView<>(() -> this.built, ROW_H, this::drawOutputRow));
        this.start = this.root.add(new Button("Start", this::build).setPrimary(true));
        this.root.add(this.menu);
        this.workspace.refresh();
    }

    /* What is in the project */

    /**
     * The project as a tree: what the machine holds, and under each program what compiling it produced.
     *
     * <p>Built fresh from the listing rather than kept, so a file written by something else shows up as
     * soon as the machine is asked again.
     */
    private List<Row> rows() {
        final List<Row> out = new ArrayList<>();
        out.add(new Row(0, "Programs", ""));
        for (final DiskFilesPayload.WireFile file : this.workspace.files()) {
            final String name = shortName(file.path());
            if (name.endsWith(".can")) {
                out.add(new Row(1, name, file.path()));
                final String assembly = file.path().substring(0, file.path().length() - 4) + ".asm";
                if (has(assembly)) {
                    out.add(new Row(2, shortName(assembly), assembly));
                }
            }
        }
        return out;
    }

    private boolean has(final String path) {
        for (final DiskFilesPayload.WireFile file : this.workspace.files()) {
            if (file.path().equals(path)) {
                return true;
            }
        }
        return false;
    }

    private List<IProgrammingLanguage.Complaint> complaints() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        return doc == null ? List.of() : doc.complaints();
    }

    private void drawTreeRow(final GuiGraphics g, final UiContext ctx, final Row row, final int index,
                             final int x, final int y, final int width, final int height,
                             final boolean hovered, final boolean selected) {
        g.drawString(ctx.font(), row.label(), x + 2 + row.depth() * 6, y + 1,
                ctx.skin().listRowText(selected), false);
    }

    private void onTreePicked(final int index, final int button, final double mx, final double my) {
        final List<Row> rows = rows();
        if (index >= 0 && index < rows.size() && !rows.get(index).path().isEmpty()) {
            this.workspace.open(rows.get(index).path());
        }
    }

    private void drawErrorRow(final GuiGraphics g, final UiContext ctx,
                              final IProgrammingLanguage.Complaint complaint, final int index,
                              final int x, final int y, final int width, final int height,
                              final boolean hovered, final boolean selected) {
        final String where = complaint.line() + ":" + complaint.column();
        g.drawString(ctx.font(), where, x + 2, y + 1, ctx.skin().dim(), false);
        final int textX = x + 2 + ctx.font().width("00:00") + 4;
        g.drawString(ctx.font(),
                ctx.font().plainSubstrByWidth(complaint.code() + " " + complaint.message(), width - (textX - x) - 2),
                textX, y + 1, 0xFFC0392B, false);
    }

    private void onErrorPicked(final int index, final int button, final double mx, final double my) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        final List<IProgrammingLanguage.Complaint> found = complaints();
        if (doc != null && index >= 0 && index < found.size()) {
            doc.area().document().setCursor(found.get(index).line() - 1,
                    Math.max(0, found.get(index).column() - 1));
        }
    }

    private void drawOutputRow(final GuiGraphics g, final UiContext ctx, final String line, final int index,
                               final int x, final int y, final int width, final int height,
                               final boolean hovered, final boolean selected) {
        g.drawString(ctx.font(), line, x + 2, y + 1, ctx.skin().text(), false);
    }

    /**
     * Compiles what is open and says what came of it.
     *
     * <p>The same reading the margin already does, said out loud: how big the program turned out, or
     * what stopped it. Putting it on the machine is the runtime's job and happens at the prompt.
     */
    private void build() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        this.built.clear();
        if (doc == null) {
            this.built.add("nothing to build");
            return;
        }
        this.workspace.recompile(doc);
        final IProgrammingLanguage language = CodeWorkspace.languageOf(doc.path());
        if (language == null) {
            this.built.add(doc.name() + ": no language claims this file");
            return;
        }
        final IProgrammingLanguage.CompileResult result = language.compile(
                List.of(new IProgrammingLanguage.SourceText(doc.name(), doc.area().text())));
        if (result.ok()) {
            final int lines = result.binary().split("\n", -1).length;
            this.built.add(doc.name() + " -> " + doc.name().replace(".can", ".asm"));
            this.built.add(lines + " lines of assembly, no complaints");
            this.workspace.say("Build succeeded");
        } else {
            for (final IProgrammingLanguage.Complaint complaint : result.complaints()) {
                this.built.add(complaint.format());
            }
            this.workspace.say(result.complaints().size() + " error(s)");
        }
        this.dockTabs.setSelected(result.ok() ? 1 : DOCK_ERRORS);
    }

    private static String shortName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
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
        return doc == null ? "Aural Studio" : doc.name() + (doc.dirty() ? " *" : "") + " - Aural Studio";
    }

    @Override
    public int defaultWidth() {
        return 320;
    }

    @Override
    public int defaultHeight() {
        return 196;
    }

    @Override
    public int minWidth() {
        return 230;
    }

    @Override
    public int minHeight() {
        return 120;
    }

    @Override
    public void onRestored() {
        this.workspace.refresh();
    }

    @Override
    public void onClosed() {
        this.workspace.release();
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        final UiContext ctx = new UiContext(this.skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, this.skin.windowBg());

        drawMenuBar(g, font, x, y, width);
        this.skin.panel(g, x, y + MENU_H, width, TOOLBAR_H);
        this.start.setBounds(x + 3, y + MENU_H + 2, 34, TOOLBAR_H - 4);

        final int bodyY = y + MENU_H + TOOLBAR_H;
        final int bodyH = height - MENU_H - TOOLBAR_H - STATUS_H;

        this.skin.panel(g, x, bodyY, SIDE_W, bodyH);
        g.drawString(font, "SOLUTION", x + 3, bodyY + 1, this.skin.dim(), false);
        this.solution.setBounds(x, bodyY + CAPTION_H, SIDE_W, bodyH - CAPTION_H);

        final int codeX = x + SIDE_W;
        final int codeW = width - SIDE_W;
        this.skin.panel(g, codeX, bodyY, codeW, TAB_H);
        this.tabs.setBounds(codeX, bodyY, codeW, TAB_H);
        this.tabs.setSelected(this.workspace.currentIndex());

        final int paneY = bodyY + TAB_H;
        final int paneH = bodyH - TAB_H;
        final boolean dockShown = paneH - DOCK_H >= MIN_CODE_H;
        final int codeH = dockShown ? paneH - DOCK_H : paneH;

        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            doc.area().setBounds(codeX, paneY, codeW, codeH);
        }
        layoutDock(codeX, paneY + codeH, codeW, dockShown ? DOCK_H : 0);

        this.root.setBounds(x, y, width, height);
        this.root.render(g, ctx);
        if (doc != null) {
            doc.area().render(g, ctx);
        } else {
            drawEmpty(g, font, codeX, paneY, codeW, codeH);
        }
        drawStatus(g, font, x, y + height - STATUS_H, width, doc);
        this.completions.render(g, ctx);
        this.menu.render(g, ctx);
    }

    /** The menu bar carries what it can actually do, and nothing it cannot. */
    private void drawMenuBar(final GuiGraphics g, final Font font, final int x, final int y, final int width) {
        this.skin.panel(g, x, y, width, MENU_H);
        int mx = x + 4;
        for (final String label : List.of("File", "Build", "Help")) {
            g.drawString(font, label, mx, y + 1, this.skin.text(), false);
            mx += font.width(label) + 8;
        }
    }

    /** Where each menu title sits, so a click finds the one it hit. */
    private int menuAt(final Font font, final double mouseX, final int x) {
        int mx = x + 4;
        final List<String> labels = List.of("File", "Build", "Help");
        for (int i = 0; i < labels.size(); i++) {
            final int w = font.width(labels.get(i));
            if (mouseX >= mx - 2 && mouseX < mx + w + 2) {
                return i;
            }
            mx += w + 8;
        }
        return -1;
    }

    private void layoutDock(final int x, final int y, final int width, final int height) {
        if (height <= 0) {
            this.dockTabs.setBounds(0, 0, 0, 0);
            this.errors.setBounds(0, 0, 0, 0);
            this.output.setBounds(0, 0, 0, 0);
            return;
        }
        this.dockTabs.setBounds(x, y, width, TAB_H);
        final boolean showErrors = this.dockTabs.selected() == DOCK_ERRORS;
        this.errors.setBounds(x, showErrors ? y + TAB_H : 0, showErrors ? width : 0,
                showErrors ? height - TAB_H : 0);
        this.output.setBounds(x, showErrors ? 0 : y + TAB_H, showErrors ? 0 : width,
                showErrors ? 0 : height - TAB_H);
    }

    private void drawEmpty(final GuiGraphics g, final Font font, final int x, final int y,
                           final int width, final int height) {
        final InkPalette palette = InkPalette.forGround(this.skin.isDark());
        g.fill(x, y, x + width, y + height, palette.ground());
        Draw.pushScissor(g, x, y, x + width, y + height);
        g.drawString(font, "Open a program from the solution", x + 6, y + 6, palette.gutterText(), false);
        Draw.popScissor(g);
    }

    private void drawStatus(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final CodeWorkspace.Doc doc) {
        this.skin.statusBar(g, x, y, width, STATUS_H);
        final int errorCount = complaints().size();
        final String left = errorCount == 0 ? "Ready" : errorCount + " error(s)";
        g.drawString(font, left, x + 3, y + 1, this.skin.dim(), false);
        if (!this.workspace.status().isEmpty()) {
            g.drawString(font, this.workspace.status(), x + 5 + font.width(left) + 6, y + 1,
                    this.skin.dim(), false);
        }
        if (doc != null) {
            final String where = "Ln " + (doc.area().document().cursorLine() + 1)
                    + ", Col " + (doc.area().document().cursorCol() + 1);
            g.drawString(font, where, x + width - font.width(where) - 3, y + 1, this.skin.dim(), false);
        }
    }

    /* Input */

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (this.completions.mouseClicked(mouseX, mouseY, button)) {
            this.workspace.edited();
            return;
        }
        this.completions.close();
        if (this.menu.isOpen()) {
            this.menu.mouseClicked(mouseX, mouseY, button);
            return;
        }
        /*
         * The bar itself is drawn rather than made of components, because a menu title is a word and a
         * place and nothing else. The panel's own rectangle is where the window put it this frame.
         */
        if (mouseY >= this.root.y() && mouseY < this.root.y() + MENU_H) {
            final Font font = net.minecraft.client.Minecraft.getInstance().font;
            final int which = menuAt(font, mouseX, this.root.x());
            if (which >= 0) {
                openMenu(which, (int) mouseX - 4, this.root.y() + MENU_H,
                        this.root.x(), this.root.y(), this.root.width(), this.root.height());
            }
            return;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.area().contains(mouseX, mouseY)) {
            doc.area().mouseClicked(mouseX, mouseY, button);
            return;
        }
        this.root.mouseClicked(mouseX, mouseY, button);
    }

    /** Opens one of the menus, given where along the bar the click landed. */
    void openMenu(final int index, final int x, final int y, final int boundX, final int boundY,
                  final int boundW, final int boundH) {
        final List<ContextMenu.Item> entries = switch (index) {
            case 0 -> List.of(
                    new ContextMenu.Item("Save", this.workspace.current() != null, this.workspace::save),
                    new ContextMenu.Item("Refresh", true, this.workspace::refresh));
            case 1 -> List.of(
                    new ContextMenu.Item("Build", this.workspace.current() != null, this::build));
            default -> List.of(
                    new ContextMenu.Item("Cannon 1.0", false, () -> { }),
                    new ContextMenu.Item("Aural Studio", false, () -> { }));
        };
        this.menu.open(entries, x, y, boundX, boundY, boundW, boundH);
    }

    @Override
    public boolean charTyped(final char c) {
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
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_S) {
            this.workspace.save();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            build();
            return true;
        }
        if (this.completions.keyPressed(key, scanCode, modifiers)) {
            this.workspace.edited();
            return true;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return false;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_SPACE) {
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
        this.completions.offer(area, doc.path(),
                new int[] {area.x(), area.y(), area.width(), area.height()});
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
