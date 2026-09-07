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
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.language.IProgrammingLanguage;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Aural Studio Code: the light editor, with the machine's programs down the side and the compiler
 * reading over the player's shoulder.
 *
 * <p>All it owns is the arrangement. What the files are, which are open and what is wrong with them is
 * the workspace's; the explorer, the tabs and the text are the toolkit's own components. That is what
 * lets the other two editors be a different arrangement of the same parts rather than a second copy.
 */
public final class AuralStudioCodeApp implements IDesktopApp {

    private static final int RAIL_W = 14;
    private static final int SIDE_W = 76;
    private static final int CAPTION_H = 9;
    private static final int TAB_H = 10;
    private static final int STATUS_H = 9;
    private static final int ROW_H = 9;
    /** How tall the panel under the code is, and the least the code itself is ever left with. */
    private static final int PANEL_H = 62;
    private static final int MIN_CODE_H = 36;

    /** The two things the panel under the code can show. */
    private static final List<String> PANEL_TABS = List.of("PROBLEMS", "TERMINAL");
    private static final int PANEL_PROBLEMS = 0;
    private static final int PANEL_TERMINAL = 1;

    private final CodeWorkspace workspace;
    private OsSkin skin = OsSkin.fallback();
    /** Whether the keyboard is on the terminal rather than on the code. */
    private boolean typingInTerminal;

    private final Panel root = new Panel();
    private final ListView<DiskFilesPayload.WireFile> explorer;
    private final TabStrip tabs;
    private final TabStrip panelTabs;
    private final ListView<IProgrammingLanguage.Complaint> problems;
    private final ShellView terminal;
    private final CodeCompletions completions = new CodeCompletions();

    public AuralStudioCodeApp(final BlockPos host) {
        this.workspace = new CodeWorkspace(host);
        this.explorer = this.root.add(new ListView<>(this.workspace::files, ROW_H, this::drawFileRow))
                .setOnClick(this::onFilePicked);
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
        this.terminal = this.root.add(new ShellView(host, false, false));
        this.workspace.refresh();
    }

    /** What the compiler said about the open file, which is what the Problems tab lists. */
    private List<IProgrammingLanguage.Complaint> complaints() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        return doc == null ? List.of() : doc.complaints();
    }

    /** One row of the explorer: the file's name, in the colour a selected row asks for. */
    private void drawFileRow(final GuiGraphics g, final UiContext ctx, final DiskFilesPayload.WireFile file,
                             final int index, final int x, final int y, final int width, final int height,
                             final boolean hovered, final boolean selected) {
        g.drawString(ctx.font(), shortName(file.path()), x + 2, y + 1, ctx.skin().listRowText(selected), false);
    }

    private void onFilePicked(final int index, final int button, final double mx, final double my) {
        if (index >= 0 && index < this.workspace.files().size()) {
            this.workspace.open(this.workspace.files().get(index).path());
        }
    }

    /** One complaint: where it is and what it says, cut to the width there is for it. */
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

    /** Clicking a complaint puts the caret on the line it is about. */
    private void onProblemPicked(final int index, final int button, final double mx, final double my) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        final List<IProgrammingLanguage.Complaint> found = complaints();
        if (doc == null || index < 0 || index >= found.size()) {
            return;
        }
        final IProgrammingLanguage.Complaint complaint = found.get(index);
        doc.area().document().setCursor(complaint.line() - 1, Math.max(0, complaint.column() - 1));
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
        this.terminal.setSkin(osSkin);
    }

    @Override
    public String title() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        return doc == null ? "Aural Studio Code"
                : doc.name() + (doc.dirty() ? " *" : "") + " - Aural Studio Code";
    }

    @Override
    public int defaultWidth() {
        return 300;
    }

    @Override
    public int defaultHeight() {
        return 176;
    }

    @Override
    public int minWidth() {
        return 210;
    }

    @Override
    public int minHeight() {
        return 110;
    }

    @Override
    public void onRestored() {
        this.workspace.refresh();
    }

    @Override
    public void onClosed() {
        this.workspace.release();
        this.terminal.release();
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        final UiContext ctx = new UiContext(this.skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, this.skin.windowBg());

        drawRail(g, x, y, height);
        this.skin.panel(g, x + RAIL_W, y, SIDE_W, height);
        g.drawString(font, "EXPLORER", x + RAIL_W + 4, y + 1, this.skin.dim(), false);
        this.explorer.setBounds(x + RAIL_W, y + CAPTION_H, SIDE_W, height - CAPTION_H);

        final int codeX = x + RAIL_W + SIDE_W;
        final int codeW = width - RAIL_W - SIDE_W;
        this.skin.panel(g, codeX, y, codeW, TAB_H);
        this.tabs.setBounds(codeX, y, codeW, TAB_H);
        this.tabs.setSelected(this.workspace.currentIndex());

        /*
         * The panel takes the bottom of the code column, and gives it back when the window is too short
         * to leave the code a readable few lines.
         */
        final int bodyY = y + TAB_H;
        final int bodyH = height - TAB_H - STATUS_H;
        final boolean panelShown = bodyH - PANEL_H >= MIN_CODE_H;
        final int codeH = panelShown ? bodyH - PANEL_H : bodyH;

        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null) {
            doc.area().setBounds(codeX, bodyY, codeW, codeH);
        }
        if (panelShown) {
            layoutPanel(codeX, bodyY + codeH, codeW, PANEL_H);
        } else {
            this.panelTabs.setBounds(0, 0, 0, 0);
            this.problems.setBounds(0, 0, 0, 0);
            this.terminal.setBounds(0, 0, 0, 0);
        }

        this.root.setBounds(x, y, width, height);
        this.root.render(g, ctx);
        if (doc != null) {
            doc.area().render(g, ctx);
        } else {
            drawEmpty(g, font, codeX, bodyY, codeW, codeH);
        }
        drawStatus(g, font, x, y + height - STATUS_H, width, doc);
        // The list of what could follow belongs over everything else the window drew.
        this.completions.render(g, ctx);
    }

    /**
     * Lays out the panel under the code: a row of tabs, then whichever of the two it is showing.
     *
     * <p>The one not showing is given no room at all rather than hidden, so a click can never land on
     * something that is not on the screen.
     */
    private void layoutPanel(final int x, final int y, final int width, final int height) {
        this.panelTabs.setBounds(x, y, width, TAB_H);
        final int inner = height - TAB_H;
        final boolean terminalShown = this.panelTabs.selected() == PANEL_TERMINAL;
        this.terminal.setBounds(x, terminalShown ? y + TAB_H : 0, terminalShown ? width : 0,
                terminalShown ? inner : 0);
        this.problems.setBounds(x, terminalShown ? 0 : y + TAB_H, terminalShown ? 0 : width,
                terminalShown ? 0 : inner);
    }

    /** The rail: today it holds the one thing there is to show, the machine's programs. */
    private void drawRail(final GuiGraphics g, final int x, final int y, final int height) {
        this.skin.panel(g, x, y, RAIL_W, height);
        for (int i = 0; i < 3; i++) {
            g.fill(x + 3, y + 4 + i * 3, x + 11, y + 5 + i * 3, this.skin.accent());
        }
    }

    private void drawEmpty(final GuiGraphics g, final Font font, final int x, final int y,
                           final int width, final int height) {
        final InkPalette palette = InkPalette.forGround(this.skin.isDark());
        g.fill(x, y, x + width, y + height, palette.ground());
        Draw.pushScissor(g, x, y, x + width, y + height);
        g.drawString(font, "Pick a program on the left", x + 6, y + 6, palette.gutterText(), false);
        Draw.popScissor(g);
    }

    private void drawStatus(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final CodeWorkspace.Doc doc) {
        this.skin.statusBar(g, x, y, width, STATUS_H);
        final IProgrammingLanguage language = this.workspace.language();
        final String left = language == null ? "no file" : language.displayName();
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
        /*
         * The keyboard follows the last click: into the terminal to run something, back into the code to
         * write it. Both are always drawn, so which one is typed into has to be said somewhere.
         */
        if (this.completions.mouseClicked(mouseX, mouseY, button)) {
            this.workspace.edited();
            return;
        }
        this.completions.close();
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
        if (this.typingInTerminal) {
            return this.terminal.charTyped(c);
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null || !doc.area().charTyped(c)) {
            return false;
        }
        this.workspace.edited();
        /*
         * A dot is a question, so it is answered without being asked; while a list is up the letters
         * that follow narrow it, and a character that could not be part of a name puts it away.
         */
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
        if (this.typingInTerminal) {
            return this.terminal.keyPressed(key, scanCode, modifiers);
        }
        // While the list is up it has the keys it uses: the arrows, Enter, Tab and Escape.
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

    /** Offers what could follow what is written at the caret, inside the code column. */
    private void offerCompletions(final CodeWorkspace.Doc doc) {
        final CodeArea area = doc.area();
        this.completions.offer(area, doc.path(),
                new int[] {area.x(), area.y(), area.width(), area.height()});
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        if (this.typingInTerminal) {
            return this.terminal.mouseScrolled(this.terminal.x(), this.terminal.y(), delta);
        }
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
