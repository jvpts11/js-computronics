/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.os.edit.CodeRuns;
import dev.jstech.computers.os.edit.InkPalette;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.UiComponent;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.client.gui.logic.TextDocument;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * The text area every editor in the mod writes code in: numbered rows, a colour per piece of source,
 * a mark in the margin and a red line under the word where the compiler complained.
 *
 * <p>It knows no language. Something else says how a row is coloured and what the marks are, and this
 * draws the answer, so the same component serves Cannon today and whatever a pack registers tomorrow.
 * Colouring is asked for only when the text has actually changed, because reading a whole program to
 * paint one frame of a window nobody typed into is work for nothing.
 *
 * <p>What it does know is how code is typed: a selection made with the mouse or Shift, cut, copy and
 * paste through the game's clipboard, undo, a new line that keeps its depth, brackets that close
 * themselves, Tab that pushes a whole selection in, and text that can be made bigger or smaller.
 */
public final class CodeArea extends UiComponent {

    private static final int LINE_H = 9;
    private static final int INSET = 3;
    private static final int GUTTER_PAD = 4;
    private static final int MARK_W = 5;
    /** The blue laid over selected text; translucent, so the colours underneath still read. */
    private static final int SELECTION = 0x663A72B0;
    private static final float MIN_SCALE = 0.75f;
    private static final float MAX_SCALE = 2.0f;

    /** Says how the rows of a document are coloured. */
    @FunctionalInterface
    public interface IColouring {
        /** The runs of every row, in order, as {@link CodeRuns#byLine} builds them. */
        List<List<CodeRuns.Run>> runsOf(List<String> lines);
    }

    /**
     * Something the compiler said about a row: shown as a square in the margin and, when it knows
     * which word, as a line under that word.
     *
     * @param line    the row, counted from one
     * @param column  the column the complaint points at, counted from one, or 0 for the whole row
     * @param length  how many characters the complaint covers from that column
     * @param error   whether it stops the build, rather than only being worth a look
     * @param message what was said
     */
    public record Mark(int line, int column, int length, boolean error, String message) {

        /** A mark on a whole row, which is all an older compiler could say. */
        public Mark(final int line, final boolean error, final String message) {
            this(line, 0, 0, error, message);
        }
    }

    private final TextDocument doc = new TextDocument();
    private IColouring colouring = lines -> List.of();
    private List<Mark> marks = List.of();
    private InkPalette palette = InkPalette.LIGHT;
    private Runnable onEdit = () -> { };
    /*
     * A code area is used on its own, with no panel to hand the keyboard around, so it keeps its own
     * flag rather than the one a panel would set. Which window gets the keyboard is already the
     * desktop's decision, and it routes input to that window's app.
     */
    private boolean active = true;
    private int scroll;
    private Font lastFont;
    /** How big the text is drawn, 1 being the game's own size. */
    private float scale = 1.0f;
    /** Whether the mouse is sweeping a selection since the last click. */
    private boolean sweeping;
    /** The colouring stands until the text changes; painting is not a reason to read the program again. */
    private List<List<CodeRuns.Run>> cached = List.of();
    private String colouredText;
    /** How many spaces the Tab key puts down; four unless an editor's settings say otherwise. */
    private int tabSize = 4;

    /** The document being edited, so an owner can read it or put a file in it. */
    public TextDocument document() {
        return this.doc;
    }

    /** The whole text. */
    public String text() {
        return this.doc.text();
    }

    /** Replaces the whole text and puts the view back at the top. */
    public CodeArea setText(final String value) {
        this.doc.setText(value);
        this.scroll = 0;
        this.colouredText = null;
        return this;
    }

    /** Says how to colour the rows. */
    public CodeArea setColouring(final IColouring value) {
        this.colouring = value == null ? lines -> List.of() : value;
        this.colouredText = null;
        return this;
    }

    /** What the compiler said, to show in the margin and under the words. */
    public CodeArea setMarks(final List<Mark> value) {
        this.marks = value == null ? List.of() : List.copyOf(value);
        return this;
    }

    /** What is drawn on: the colours follow the window's own ground. */
    public CodeArea setPalette(final InkPalette value) {
        this.palette = value == null ? InkPalette.LIGHT : value;
        return this;
    }

    /** Sets how many spaces the Tab key puts down, between two and eight. */
    public CodeArea setTabSize(final int value) {
        this.tabSize = Math.max(2, Math.min(8, value));
        return this;
    }

    public int tabSize() {
        return this.tabSize;
    }

    /** Runs after every change the player makes, for an owner that recompiles as it is typed. */
    public CodeArea setOnEdit(final Runnable action) {
        this.onEdit = action == null ? () -> { } : action;
        return this;
    }

    /** Whether the keyboard is on this area: the caret shows and typing lands here. */
    public CodeArea setActive(final boolean value) {
        this.active = value;
        return this;
    }

    /** How big the text is drawn, 1 being the game's own size, held between three quarters and double. */
    public CodeArea setScale(final float value) {
        this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
        return this;
    }

    public float scale() {
        return this.scale;
    }

    /** One step bigger or smaller, the way Ctrl and the wheel or the plus and minus keys ask. */
    public void zoom(final int steps) {
        setScale(this.scale + steps * 0.25f);
    }

    @Override
    public boolean focusable() {
        return true;
    }

    /** The height of one row on the screen, at the size the text is drawn. */
    private int rowHeight() {
        return Math.max(1, Math.round(LINE_H * this.scale));
    }

    /** How many rows fit. */
    public int visibleLines() {
        return Math.max(1, (height() - 2) / rowHeight());
    }

    /** The width the numbers take, which is what the code is indented past, in unscaled units. */
    private int gutterWidth(final Font font) {
        final int widest = font.width(String.valueOf(Math.max(1, this.doc.lineCount())));
        return MARK_W + GUTTER_PAD + widest + GUTTER_PAD;
    }

    private void followCaret() {
        final int visible = visibleLines();
        if (this.doc.cursorLine() < this.scroll) {
            this.scroll = this.doc.cursorLine();
        } else if (this.doc.cursorLine() >= this.scroll + visible) {
            this.scroll = this.doc.cursorLine() - visible + 1;
        }
        this.scroll = Math.max(0, Math.min(Math.max(0, this.doc.lineCount() - visible), this.scroll));
    }

    /** The rows, coloured, reading the program again only when it is not the one already coloured. */
    private List<List<CodeRuns.Run>> runs() {
        final String text = this.doc.text();
        if (!text.equals(this.colouredText)) {
            final List<String> lines = new ArrayList<>(this.doc.lineCount());
            for (int i = 0; i < this.doc.lineCount(); i++) {
                lines.add(this.doc.line(i));
            }
            this.cached = this.colouring.runsOf(lines);
            this.colouredText = text;
        }
        return this.cached;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        this.lastFont = ctx.font();
        final boolean focused = this.active;
        followCaret();
        final Font font = ctx.font();
        final int gutter = gutterWidth(font);
        g.fill(x(), y(), right(), bottom(), this.palette.ground());
        g.fill(x(), y(), x() + Math.round(gutter * this.scale), bottom(), this.palette.gutter());
        final List<List<CodeRuns.Run>> runs = runs();
        final int visible = visibleLines();
        final TextDocument.Spot from = this.doc.selectionStart();
        final TextDocument.Spot to = this.doc.selectionEnd();
        final boolean selected = this.doc.hasSelection();
        Draw.pushScissor(g, x(), y(), right(), bottom());
        /*
         * Everything inside is drawn in unscaled units under one scaling of the pose, so the font, the
         * rows and the caret all grow together; the area's own frame stays where the window put it.
         */
        g.pose().pushPose();
        g.pose().translate(x(), y(), 0);
        g.pose().scale(this.scale, this.scale, 1);
        final int innerRight = Math.round((right() - x()) / this.scale);
        int ry = 1;
        for (int i = this.scroll; i < this.doc.lineCount() && i - this.scroll < visible; i++) {
            final String line = this.doc.line(i);
            final int textX = gutter + INSET;
            if (i == this.doc.cursorLine() && focused && !selected) {
                g.fill(gutter, ry, innerRight, ry + LINE_H, this.palette.currentLine());
            }
            if (selected && i >= from.line() && i <= to.line()) {
                final int startCol = i == from.line() ? Math.min(from.col(), line.length()) : 0;
                final int endCol = i == to.line() ? Math.min(to.col(), line.length()) : line.length();
                final int sx = textX + font.width(line.substring(0, startCol));
                // A line wholly inside the selection shows a little past its end, the way editors do.
                final int ex = i == to.line() ? textX + font.width(line.substring(0, endCol))
                        : textX + font.width(line) + 4;
                g.fill(sx, ry, Math.max(sx + 1, ex), ry + LINE_H, SELECTION);
            }
            drawMark(g, i, ry);
            final String number = String.valueOf(i + 1);
            g.drawString(font, number, gutter - GUTTER_PAD - font.width(number), ry + 1,
                    this.palette.gutterText(), false);
            drawLine(g, font, line, i < runs.size() ? runs.get(i) : List.of(), textX, ry + 1);
            drawSquiggles(g, font, i, line, textX, ry);
            if (focused && i == this.doc.cursorLine()) {
                final int col = Math.min(this.doc.cursorCol(), line.length());
                final int cx = textX + font.width(line.substring(0, col));
                g.fill(cx, ry, cx + 1, ry + LINE_H, this.palette.caret());
            }
            ry += LINE_H;
        }
        g.pose().popPose();
        Draw.popScissor(g);
    }

    /** One row, run by run, each stretch in the colour its piece of source asked for. */
    private void drawLine(final GuiGraphics g, final Font font, final String line,
                          final List<CodeRuns.Run> runs, final int startX, final int textY) {
        if (runs.isEmpty()) {
            g.drawString(font, line, startX, textY, this.palette.plain(), false);
            return;
        }
        int rx = startX;
        for (final CodeRuns.Run run : runs) {
            final int from = Math.min(run.start(), line.length());
            final int to = Math.min(run.start() + run.length(), line.length());
            if (to <= from) {
                continue;
            }
            final String piece = line.substring(from, to);
            g.drawString(font, piece, rx, textY, this.palette.of(run.ink()), false);
            rx += font.width(piece);
        }
    }

    /** The margin beside a row: a square where the compiler complained, red for an error. */
    private void drawMark(final GuiGraphics g, final int line, final int rowY) {
        for (final Mark mark : this.marks) {
            if (mark.line() - 1 != line) {
                continue;
            }
            g.fill(1, rowY + 2, 1 + MARK_W - 1, rowY + 2 + MARK_W - 1, colourOf(mark));
            return;
        }
    }

    /**
     * A dotted line under the word a complaint points at, the way an editor underlines what the
     * compiler stopped on; a complaint with no column has the square in the margin and nothing more.
     */
    private void drawSquiggles(final GuiGraphics g, final Font font, final int line, final String text,
                               final int textX, final int rowY) {
        for (final Mark mark : this.marks) {
            if (mark.line() - 1 != line || mark.column() <= 0) {
                continue;
            }
            final int from = Math.min(mark.column() - 1, text.length());
            final int to = Math.min(from + Math.max(1, mark.length()), text.length());
            final int sx = textX + font.width(text.substring(0, from));
            final int ex = to > from ? textX + font.width(text.substring(0, to)) : sx + 4;
            final int colour = colourOf(mark);
            for (int px = sx; px < ex; px += 2) {
                g.fill(px, rowY + LINE_H - 1, px + 1, rowY + LINE_H, colour);
            }
        }
    }

    private static int colourOf(final Mark mark) {
        return mark.error() ? 0xFFC0392B : 0xFFD08A1E;
    }

    /**
     * Where the caret is on the screen, as {@code x, y} of its top-left, or null before the first frame
     * has measured the font. What an owner needs to put something beside it.
     */
    public int[] caretPixel() {
        if (this.lastFont == null) {
            return null;
        }
        final String line = this.doc.line(this.doc.cursorLine());
        final int col = Math.min(this.doc.cursorCol(), line.length());
        final int cx = gutterWidth(this.lastFont) + INSET + this.lastFont.width(line.substring(0, col));
        final int cy = 1 + (this.doc.cursorLine() - this.scroll) * LINE_H;
        return new int[] {x() + Math.round(cx * this.scale), y() + Math.round(cy * this.scale)};
    }

    /** The height of one row, which is what an owner leaves clear when it draws beside the caret. */
    public static int lineHeight() {
        return LINE_H;
    }

    /** What the compiler said about the row under the cursor, for the owner to show as a tooltip. */
    public String messageAt(final double mx, final double my) {
        if (this.lastFont == null || !contains(mx, my)) {
            return "";
        }
        final int line = lineAt(my);
        for (final Mark mark : this.marks) {
            if (mark.line() - 1 == line) {
                return mark.message();
            }
        }
        return "";
    }

    /** The row under a screen y, which may be past the last. */
    private int lineAt(final double my) {
        return this.scroll + (int) Math.floor((my - y() - 1) / (double) rowHeight());
    }

    /** The column a screen x falls on within {@code text}, snapping to the nearer edge of a character. */
    private int columnAt(final String text, final double mx) {
        final double target = (mx - x()) / this.scale - (gutterWidth(this.lastFont) + INSET);
        int col = 0;
        while (col < text.length()
                && this.lastFont.width(text.substring(0, col + 1))
                - this.lastFont.width(text.substring(col, col + 1)) / 2.0 <= target) {
            col++;
        }
        return col;
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (this.lastFont == null) {
            return true;
        }
        final int line = Math.max(0, Math.min(this.doc.lineCount() - 1, lineAt(my)));
        final int col = columnAt(this.doc.line(line), mx);
        // Shift and a click stretch the selection to the click; a plain click starts one for a sweep.
        this.doc.setCursor(line, col, Screen.hasShiftDown());
        this.sweeping = button == 0;
        this.doc.breakUndo();
        return true;
    }

    @Override
    public boolean mouseDragged(final double mx, final double my, final int button) {
        if (!this.sweeping || this.lastFont == null) {
            return false;
        }
        final int line = Math.max(0, Math.min(this.doc.lineCount() - 1, lineAt(my)));
        final int col = columnAt(this.doc.line(line), mx);
        this.doc.setCursor(line, col, true);
        return true;
    }

    @Override
    public boolean mouseReleased(final double mx, final double my, final int button) {
        this.sweeping = false;
        return false;
    }

    @Override
    public boolean charTyped(final char c) {
        if (!this.active) {
            return false;
        }
        if (c < 32 || c == 127) {
            return true;
        }
        /*
         * A closing bracket typed where one is already waiting steps over it, since the opening one
         * put it there; an opening bracket brings its partner along, with the caret between them, but
         * only where the caret is at the end of a word or line, so typing one in front of text does
         * not wrap the text in brackets it did not ask for.
         */
        final char after = this.doc.charAfter();
        if ((c == ')' || c == ']' || c == '}' || c == '"') && after == c && !this.doc.hasSelection()) {
            this.doc.right();
            return true;
        }
        final char partner = partnerOf(c);
        if (partner != 0 && !this.doc.hasSelection() && (after == 0 || after == ' ' || after == ')' || after == ']'
                || after == '}' || after == ';' || after == ',')) {
            this.doc.insert(c);
            this.doc.insert(partner);
            this.doc.left();
            this.onEdit.run();
            return true;
        }
        this.doc.insert(c);
        this.onEdit.run();
        return true;
    }

    private static char partnerOf(final char c) {
        return switch (c) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            case '"' -> '"';
            default -> 0;
        };
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (!this.active) {
            return false;
        }
        final boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        final boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> this.doc.selectAll();
                case GLFW.GLFW_KEY_C -> copy();
                case GLFW.GLFW_KEY_X -> {
                    copy();
                    if (this.doc.deleteSelection()) {
                        this.onEdit.run();
                    }
                }
                case GLFW.GLFW_KEY_V -> paste();
                case GLFW.GLFW_KEY_Z -> {
                    if (shift ? this.doc.redo() : this.doc.undo()) {
                        this.onEdit.run();
                    }
                }
                case GLFW.GLFW_KEY_Y -> {
                    if (this.doc.redo()) {
                        this.onEdit.run();
                    }
                }
                case GLFW.GLFW_KEY_HOME -> this.doc.setCursor(0, 0, shift);
                case GLFW.GLFW_KEY_END -> this.doc.setCursor(this.doc.lineCount() - 1,
                        this.doc.line(this.doc.lineCount() - 1).length(), shift);
                case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> zoom(1);
                case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> zoom(-1);
                case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0 -> setScale(1.0f);
                case GLFW.GLFW_KEY_L -> this.doc.selectLine();
                default -> {
                    return false;
                }
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                this.doc.newlineIndented(this.tabSize);
                this.onEdit.run();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                // Backspace between a pair that came together takes both away.
                final char before = this.doc.charBefore();
                if (!this.doc.hasSelection() && before != 0 && partnerOf(before) == this.doc.charAfter()) {
                    this.doc.delete();
                }
                this.doc.backspace();
                this.onEdit.run();
            }
            case GLFW.GLFW_KEY_DELETE -> {
                this.doc.delete();
                this.onEdit.run();
            }
            case GLFW.GLFW_KEY_TAB -> {
                /*
                 * Spaces, not a tab character: the file is read back by a compiler that counts
                 * columns, and a column has to mean the same thing to it as it does on the screen.
                 * With lines selected, the whole stretch moves in or out.
                 */
                if (shift) {
                    this.doc.outdent(this.tabSize);
                } else if (this.doc.hasSelection()
                        && this.doc.selectionStart().line() != this.doc.selectionEnd().line()) {
                    this.doc.indent(this.tabSize);
                } else {
                    final int pad = this.tabSize - this.doc.cursorCol() % this.tabSize;
                    for (int i = 0; i < pad; i++) {
                        this.doc.insert(' ');
                    }
                }
                this.onEdit.run();
            }
            case GLFW.GLFW_KEY_LEFT -> this.doc.left(shift);
            case GLFW.GLFW_KEY_RIGHT -> this.doc.right(shift);
            case GLFW.GLFW_KEY_UP -> this.doc.up(shift);
            case GLFW.GLFW_KEY_DOWN -> this.doc.down(shift);
            case GLFW.GLFW_KEY_HOME -> this.doc.home(shift);
            case GLFW.GLFW_KEY_END -> this.doc.end(shift);
            case GLFW.GLFW_KEY_PAGE_UP -> this.doc.setCursor(this.doc.cursorLine() - visibleLines(),
                    this.doc.cursorCol(), shift);
            case GLFW.GLFW_KEY_PAGE_DOWN -> this.doc.setCursor(this.doc.cursorLine() + visibleLines(),
                    this.doc.cursorCol(), shift);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Puts the selection, or the caret's line when nothing is selected, on the clipboard. */
    private void copy() {
        final String text = this.doc.hasSelection() ? this.doc.selectedText() : this.doc.line(this.doc.cursorLine());
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    /** Types what is on the clipboard at the caret, in place of the selection. */
    private void paste() {
        final String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null || text.isEmpty()) {
            return;
        }
        this.doc.insertText(text.replace("\r\n", "\n").replace('\t', ' '));
        this.onEdit.run();
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        // Ctrl and the wheel change the size of the text, the way every editor lets them.
        if (Screen.hasControlDown()) {
            zoom(delta > 0 ? 1 : -1);
            return true;
        }
        final int visible = visibleLines();
        this.scroll = Math.max(0, Math.min(Math.max(0, this.doc.lineCount() - visible),
                this.scroll - (int) Math.signum(delta) * 3));
        return true;
    }
}
