/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.cannon.CannonSemantics;
import dev.jstech.computronics.cannon.SourceFile;
import dev.jstech.computronics.cannon.sem.IMemberSymbol;
import dev.jstech.computronics.cannon.sem.NamedType;
import dev.jstech.computronics.operation.payload.DiskFilesPayload;
import dev.jstech.computronics.os.edit.InkPalette;
import dev.jstech.computronics.os.edit.ProblemReport;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.UiContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Exposure: the editor that reads the whole disk rather than the file in front of you.
 *
 * <p>It offers nothing as you type, on purpose. What it has instead is every complaint from every
 * program on the machine, in one table, which is the only way to find out that changing something
 * shared broke three programs nobody has open. Beside the code it keeps an outline of what the open
 * file declares, so a long program can be walked without scrolling it.
 */
public final class ExposureApp implements IDesktopApp {

    private static final int MENU_H = 10;
    private static final int TOOLBAR_H = 13;
    private static final int SIDE_W = 74;
    private static final int OUTLINE_W = 68;
    private static final int CAPTION_H = 9;
    private static final int TAB_H = 10;
    private static final int STATUS_H = 9;
    private static final int ROW_H = 9;
    private static final int DOCK_H = 54;
    private static final int MIN_CODE_H = 36;

    /** One line of the outline: how deep it sits, what it says, and the line it stands on. */
    private record Outline(int depth, String label, int line) {
    }

    private final CodeWorkspace workspace;
    private OsSkin skin = OsSkin.fallback();

    private final Panel root = new Panel();
    private final ListView<DiskFilesPayload.WireFile> explorer;
    private final TabStrip tabs;
    private final ListView<Outline> outline;
    private final ListView<ProblemReport.Row> problems;
    private final Button survey;

    /** The outline of the open file, read again only when its text changes. */
    private List<Outline> outlineRows = List.of();
    private String outlineOf = "";

    public ExposureApp(final BlockPos host) {
        this.workspace = new CodeWorkspace(host);
        this.explorer = this.root.add(new ListView<>(this.workspace::files, ROW_H, this::drawFileRow))
                .setOnClick(this::onFilePicked);
        this.tabs = this.root.add(new TabStrip(this.workspace::tabLabels).fitToLabels(10).setUnderline(false));
        this.tabs.setOnSelect(this.workspace::setCurrent);
        this.outline = this.root.add(new ListView<>(this::outlineRows, ROW_H, this::drawOutlineRow))
                .setOnClick(this::onOutlinePicked);
        this.problems = this.root.add(new ListView<>(this.workspace::folderProblems, ROW_H, this::drawProblemRow))
                .setOnClick(this::onProblemPicked);
        this.survey = this.root.add(new Button("Rebuild all", this.workspace::surveyFolder));
        this.workspace.refresh();
        this.workspace.surveyFolder();
    }

    /* The lists */

    private void drawFileRow(final GuiGraphics g, final UiContext ctx, final DiskFilesPayload.WireFile file,
                             final int index, final int x, final int y, final int width, final int height,
                             final boolean hovered, final boolean selected) {
        g.drawString(ctx.font(), ProblemReport.nameOf(file.path()), x + 2, y + 1,
                ctx.skin().listRowText(selected), false);
    }

    private void onFilePicked(final int index, final int button, final double mx, final double my) {
        if (index >= 0 && index < this.workspace.files().size()) {
            this.workspace.open(this.workspace.files().get(index).path());
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
        final Font font = ctx.font();
        final int nameW = 58;
        g.drawString(font, font.plainSubstrByWidth(row.name(), nameW - 2), x + 2, y + 1,
                ctx.skin().text(), false);
        final String where = String.valueOf(row.complaint().line());
        g.drawString(font, where, x + nameW, y + 1, ctx.skin().dim(), false);
        final int textX = x + nameW + font.width("0000") + 4;
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
        this.workspace.open(row.path());
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
        return doc == null ? "Exposure" : doc.name() + (doc.dirty() ? " *" : "") + " - Exposure";
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

    @Override
    public void onRestored() {
        this.workspace.refresh();
        this.workspace.surveyFolder();
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

        this.skin.panel(g, x, y, width, MENU_H);
        int mx = x + 4;
        for (final String label : List.of("File", "Source", "Project")) {
            g.drawString(font, label, mx, y + 1, this.skin.text(), false);
            mx += font.width(label) + 8;
        }
        this.skin.panel(g, x, y + MENU_H, width, TOOLBAR_H);
        this.survey.setBounds(x + 3, y + MENU_H + 2, 52, TOOLBAR_H - 4);

        final int bodyY = y + MENU_H + TOOLBAR_H;
        final int bodyH = height - MENU_H - TOOLBAR_H - STATUS_H;
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
        g.drawString(font, "PROBLEMS (" + count + ")", x + 3, y + 1, this.skin.dim(), false);
        g.drawString(font, "FILE", x + 60, y + 1, this.skin.dim(), false);
        g.drawString(font, "LINE", x + 60 + 58, y + 1, this.skin.dim(), false);
        this.problems.setBounds(x, y + CAPTION_H, width, height - CAPTION_H);
    }

    private void drawEmpty(final GuiGraphics g, final Font font, final int x, final int y,
                           final int width, final int height) {
        final InkPalette palette = InkPalette.forGround(this.skin.isDark());
        g.fill(x, y, x + width, y + height, palette.ground());
        Draw.pushScissor(g, x, y, x + width, y + height);
        g.drawString(font, "Pick a problem, or a file", x + 6, y + 6, palette.gutterText(), false);
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

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.area().contains(mouseX, mouseY)) {
            doc.area().mouseClicked(mouseX, mouseY, button);
            return;
        }
        this.root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(final char c) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.area().charTyped(c)) {
            this.workspace.edited();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_S) {
            /*
             * Saving is what makes the disk disagree with the table, so the survey is run again: the
             * point of this editor is that the report is about what is really there.
             */
            this.workspace.save();
            this.workspace.surveyFolder();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            this.workspace.surveyFolder();
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
