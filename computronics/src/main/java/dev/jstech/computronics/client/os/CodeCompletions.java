/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.cannon.CannonCosts;
import dev.jstech.computronics.cannon.CannonSemantics;
import dev.jstech.computronics.cannon.SourceFile;
import dev.jstech.computronics.cannon.edit.CannonCompletions;
import dev.jstech.computronics.cannon.edit.CompletionContext;
import dev.jstech.computronics.cannon.sem.BuiltIns;
import dev.jstech.computronics.cannon.sem.SemanticModel;
import dev.jstech.core.client.gui.component.ContextMenu;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.client.gui.logic.TextDocument;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The list an editor offers after a name and a dot, and what happens when one is taken.
 *
 * <p>Cannon is the language that can answer this: the checker knows every type it brings and every type
 * the program declares, with the members of each. A language a pack registers gets its source coloured
 * and its complaints listed all the same, and simply offers nothing here, which is honest about what the
 * registry promises and what it does not.
 *
 * <p>The list itself is the toolkit's menu, so it looks like every other menu on the system and is
 * driven the same way: the arrows walk it, Enter or Tab takes what is on, Escape leaves.
 */
public final class CodeCompletions {

    /** Wide enough for a whole signature, which is the point of showing one. */
    private static final int WIDTH = 168;
    private static final int ROW_H = 10;
    private static final int MAX_ITEMS = 8;

    private final ContextMenu menu = new ContextMenu(WIDTH, ROW_H);
    private final BuiltIns builtIns = new BuiltIns();
    /** Whether the strip under the list says what the call on it will cost the program. */
    private boolean showCosts;
    /** What each offered call is, kept beside the list so the strip can price the one being looked at. */
    private final List<String> offered = new ArrayList<>();

    /**
     * Says what each call costs, under the list.
     *
     * <p>Only one editor does this, and it is what that editor is for: a player deciding whether a line
     * belongs in something that runs every tick wants the price before they write it, not after the
     * program has eaten its budget.
     */
    public CodeCompletions withCosts(final boolean value) {
        this.showCosts = value;
        return this;
    }

    /** Whether a list is being offered. */
    public boolean isOpen() {
        return this.menu.isOpen();
    }

    /** Takes the list away. */
    public void close() {
        this.menu.close();
    }

    /**
     * Offers what could follow what the player has written, or nothing when that is not a question with
     * an answer. The list opens beside the caret and inside {@code bounds}, given as x, y, width, height.
     */
    public void offer(final CodeArea area, final String path, final int[] bounds) {
        close();
        if (!isCannon(path)) {
            return;
        }
        final TextDocument doc = area.document();
        final CompletionContext.Where where =
                CompletionContext.at(doc.line(doc.cursorLine()), doc.cursorCol());
        final int[] caret = area.caretPixel();
        if (where == null || caret == null) {
            return;
        }
        final SemanticModel model = CannonSemantics.check(
                List.of(new SourceFile("editor.can", doc.text()))).model();
        final List<CannonCompletions.Item> found = where.intoMember()
                ? membersOf(model, where)
                : CannonCompletions.types(this.builtIns, model, where.prefix());
        if (found.isEmpty()) {
            return;
        }
        final List<ContextMenu.Item> entries = new ArrayList<>(Math.min(found.size(), MAX_ITEMS));
        this.offered.clear();
        for (final CannonCompletions.Item item : found.subList(0, Math.min(found.size(), MAX_ITEMS))) {
            entries.add(new ContextMenu.Item(item.signature(), true,
                    () -> take(doc, where, item.label())));
            this.offered.add(item.owner() + "." + item.label());
        }
        this.menu.open(entries, caret[0], caret[1] + CodeArea.lineHeight(),
                bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    /**
     * The members of what is being reached into.
     *
     * <p>A name written into the source reaches what belongs to the type; the same name as a variable
     * reaches the rest. Which of the two it is, is read from whether the language or the program has a
     * type of that name, so {@code Network.} offers what the network can do and a variable does not.
     */
    private List<CannonCompletions.Item> membersOf(final SemanticModel model,
                                                   final CompletionContext.Where where) {
        final boolean isType = model != null && model.declaredType(where.receiver()) != null
                || this.builtIns.type(where.receiver(), 0) != null;
        return CannonCompletions.members(this.builtIns, model, where.receiver(), where.prefix(), isType);
    }

    /** Puts the chosen name in, in place of however much of it had been typed. */
    private static void take(final TextDocument doc, final CompletionContext.Where where, final String label) {
        for (int i = 0; i < where.prefix().length(); i++) {
            doc.backspace();
        }
        for (int i = 0; i < label.length(); i++) {
            doc.insert(label.charAt(i));
        }
    }

    /** Whether the file is one this can answer for. */
    private static boolean isCannon(final String path) {
        return path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".can");
    }

    /** Draws the list, which belongs over everything else the editor drew. */
    public void render(final GuiGraphics g, final UiContext ctx) {
        this.menu.render(g, ctx);
        if (this.showCosts && this.menu.isOpen()) {
            drawCost(g, ctx);
        }
    }

    /**
     * The strip under the list: what the call the keyboard is on will cost the program.
     *
     * <p>A call that never reaches the machine says so, because "free" is the useful thing to know
     * about the calls that are free on purpose.
     */
    private void drawCost(final GuiGraphics g, final UiContext ctx) {
        final int at = this.menu.selected();
        if (at < 0 || at >= this.offered.size()) {
            return;
        }
        final String[] call = this.offered.get(at).split("\\.", 2);
        if (call.length < 2 || !CannonCosts.known(call[0], call[1])) {
            return;
        }
        final String text = "costs " + CannonCosts.of(call[0], call[1]).describe();
        final int y = this.menu.bottom();
        g.fill(this.menu.x() - 1, y, this.menu.right() + 1, y + ROW_H + 1, 0xFF000000);
        g.fill(this.menu.x(), y, this.menu.right(), y + ROW_H, ctx.skin().fieldBg());
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(text, this.menu.width() - 6),
                this.menu.x() + 3, y + 1, ctx.skin().dim(), false);
    }

    /** Gives the keys to the list while it is up; false when it wants none of them. */
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        return this.menu.isOpen() && this.menu.keyPressed(key, scanCode, modifiers);
    }

    /** Gives a click to the list while it is up. */
    public boolean mouseClicked(final double mx, final double my, final int button) {
        return this.menu.isOpen() && this.menu.mouseClicked(mx, my, button);
    }
}
