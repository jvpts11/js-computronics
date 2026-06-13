/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.item.PatternDiscItem;
import dev.jsc.jscomputronics.module.computing.menu.PatternReaderMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestRomSnapshotPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Screen for the Pattern Reader, a two-tab disc/ROM station. The READ tab copies a disc's patterns into the adjacent Crafting Computer's Recipe ROM; the ROM tab lists that ROM and exports selected patterns back onto a rewritable disc (a copy that spends one rewrite cycle), with a right-click deleting a pattern from the ROM.
 */
public class PatternReaderScreen extends AbstractComputerScreen<PatternReaderMenu> {

    private static final int TAB_READ = 0;
    private static final int TAB_ROM = 1;

    private static final int TAB_W = 30;
    private static final int TAB_H = 12;
    private static final int TAB_Y = 8;
    private static final int READ_TAB_X = 130;
    private static final int ROM_TAB_X = 162;

    private static final int LIST_X = 8;
    private static final int LIST_Y = 60;
    private static final int ROW_H = 12;
    private static final int VISIBLE_ROWS = 4;
    private static final int SEL_X = 8;
    private static final int SEL_W = 106;
    private static final int ALL_X = 122;
    private static final int ALL_W = 70;
    private static final int BTN_Y = 112;
    private static final int BTN_H = 14;

    private final Set<Integer> selection = new LinkedHashSet<>();
    private final Set<Integer> romSelection = new LinkedHashSet<>();

    private int activeTab = TAB_READ;
    private int scroll;
    private int romScroll;

    public PatternReaderScreen(final PatternReaderMenu menu, final Inventory inventory,
                               final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 218;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        requestRom();
    }

    private void requestRom() {
        PacketDistributor.sendToServer(new RequestRomSnapshotPayload(menu.readerPos()));
    }

    // Backgrounds

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, imageWidth - 12);
        tab(g, x, y, READ_TAB_X, activeTab == TAB_READ, mouseX, mouseY);
        tab(g, x, y, ROM_TAB_X, activeTab == TAB_ROM, mouseX, mouseY);
        JscOsTheme.slot(g, x + 12, y + 30);

        final List<CraftingPattern> rows = activeTab == TAB_READ ? menu.discPatterns() : menu.romPatterns();
        final int scrollPos = activeTab == TAB_READ ? clampScroll(scroll, rows.size()) : clampScroll(romScroll, rows.size());
        final Set<Integer> sel = activeTab == TAB_READ ? selection : romSelection;
        for (int row = 0; row < Math.min(VISIBLE_ROWS, rows.size()); row++) {
            final int index = row + scrollPos;
            final int rowY = y + LIST_Y + row * ROW_H;
            g.fill(x + LIST_X, rowY, x + imageWidth - 8, rowY + ROW_H - 2, JscOsTheme.panel());
            if (sel.contains(index)) {
                g.fill(x + LIST_X, rowY, x + LIST_X + 2, rowY + ROW_H - 2, JscOsTheme.amber());
            }
        }

        if (activeTab == TAB_READ) {
            final boolean usable = menu.hasComputer() && !menu.discPatterns().isEmpty();
            if (usable) {
                JscOsTheme.button(g, x + SEL_X, y + BTN_Y, SEL_W, BTN_H,
                        !selection.isEmpty() && hover(mouseX, mouseY, SEL_X, BTN_Y, SEL_W, BTN_H));
                JscOsTheme.button(g, x + ALL_X, y + BTN_Y, ALL_W, BTN_H,
                        hover(mouseX, mouseY, ALL_X, BTN_Y, ALL_W, BTN_H));
            }
        } else if (canExport()) {
            JscOsTheme.button(g, x + SEL_X, y + BTN_Y, SEL_W, BTN_H,
                    !romSelection.isEmpty() && hover(mouseX, mouseY, SEL_X, BTN_Y, SEL_W, BTN_H));
            JscOsTheme.button(g, x + ALL_X, y + BTN_Y, ALL_W, BTN_H,
                    hover(mouseX, mouseY, ALL_X, BTN_Y, ALL_W, BTN_H));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + 8 + col * 18, y + 138 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + 8 + col * 18, y + 196);
        }
    }

    private void tab(final GuiGraphics g, final int x, final int y, final int tx, final boolean on,
                     final int mouseX, final int mouseY) {
        final int bg = on ? JscOsTheme.tabOn()
                : (hover(mouseX, mouseY, tx, TAB_Y, TAB_W, TAB_H) ? JscOsTheme.hover() : JscOsTheme.panel());
        g.fill(x + tx, y + TAB_Y, x + tx + TAB_W, y + TAB_Y + TAB_H, bg);
        if (on) {
            g.fill(x + tx, y + TAB_Y + TAB_H - 1, x + tx + TAB_W, y + TAB_Y + TAB_H, JscOsTheme.accent());
        }
    }

    // Labels

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "PATTERN READER", 12, 11, JscOsTheme.text());
        JscOsTheme.textSCenter(g, font, "READ", READ_TAB_X + TAB_W / 2, TAB_Y + 3,
                activeTab == TAB_READ ? JscOsTheme.accent() : JscOsTheme.dim());
        JscOsTheme.textSCenter(g, font, "ROM", ROM_TAB_X + TAB_W / 2, TAB_Y + 3,
                activeTab == TAB_ROM ? JscOsTheme.accent() : JscOsTheme.dim());

        discReadout(g);

        if (activeTab == TAB_READ) {
            readLabels(g);
        } else {
            romLabels(g);
        }
    }

    private void discReadout(final GuiGraphics g) {
        final ItemStack disc = menu.slots.get(PatternReaderMenu.MEDIA_SLOT).getItem();
        if (disc.isEmpty()) {
            JscOsTheme.textS(g, font, activeTab == TAB_ROM ? "insert a rewritable disc" : "insert pattern media",
                    34, 36, JscOsTheme.dim());
            return;
        }
        final List<CraftingPattern> onDisc = menu.discPatterns();
        JscOsTheme.textS(g, font, onDisc.size() + (onDisc.size() == 1 ? " pattern" : " patterns"),
                34, 32, JscOsTheme.text());
        if (disc.getItem() instanceof PatternDiscItem item && item.isRewritable()) {
            final int cycles = PatternDiscItem.cyclesLeft(disc);
            JscOsTheme.textS(g, font, cycles > 0 ? cycles + " cycles left" : "read-only",
                    34, 41, cycles > 0 ? JscOsTheme.amber() : JscOsTheme.dim());
        } else {
            JscOsTheme.textS(g, font, "write-once", 34, 41, JscOsTheme.dim());
        }
    }

    private void readLabels(final GuiGraphics g) {
        final List<CraftingPattern> patterns = menu.discPatterns();
        JscOsTheme.textS(g, font, "PATTERNS ON MEDIA", LIST_X, 53, JscOsTheme.dim());
        listRows(g, patterns, clampScroll(scroll, patterns.size()), selection);
        if (patterns.size() > VISIBLE_ROWS) {
            JscOsTheme.textSRight(g, font, rangeLabel(scroll, patterns.size()), imageWidth - 8, 53, JscOsTheme.dim());
        }

        final boolean usable = menu.hasComputer() && !patterns.isEmpty();
        if (usable) {
            JscOsTheme.textCenter(g, font, "LOAD SELECTED (" + selection.size() + ")",
                    SEL_X + SEL_W / 2, BTN_Y + 4, selection.isEmpty() ? JscOsTheme.dim() : JscOsTheme.green());
            JscOsTheme.textCenter(g, font, "LOAD ALL", ALL_X + ALL_W / 2, BTN_Y + 4, JscOsTheme.accent());
        }

        if (menu.hasComputer()) {
            JscOsTheme.textS(g, font, "> Crafting Computer", LIST_X, 130, JscOsTheme.accent());
            JscOsTheme.textSRight(g, font, "ROM " + menu.romUsed() + " / " + menu.romLimit(),
                    imageWidth - 8, 130, menu.romUsed() >= menu.romLimit() ? JscOsTheme.red() : JscOsTheme.text());
        } else {
            JscOsTheme.textS(g, font, "no Crafting Computer adjacent", LIST_X, 130, JscOsTheme.red());
        }
    }

    private void romLabels(final GuiGraphics g) {
        final List<CraftingPattern> patterns = menu.romPatterns();
        JscOsTheme.textS(g, font, "PATTERNS IN ROM", LIST_X, 53, JscOsTheme.dim());
        if (!menu.hasComputer()) {
            JscOsTheme.textS(g, font, "no Crafting Computer adjacent", LIST_X, 62, JscOsTheme.red());
            return;
        }
        if (patterns.isEmpty()) {
            JscOsTheme.textS(g, font, "ROM is empty", LIST_X, 62, JscOsTheme.dim());
        }
        listRows(g, patterns, clampScroll(romScroll, patterns.size()), romSelection);
        if (patterns.size() > VISIBLE_ROWS) {
            JscOsTheme.textSRight(g, font, rangeLabel(romScroll, patterns.size()), imageWidth - 8, 53, JscOsTheme.dim());
        }

        if (canExport()) {
            JscOsTheme.textCenter(g, font, "EXPORT (" + romSelection.size() + ")",
                    SEL_X + SEL_W / 2, BTN_Y + 4, romSelection.isEmpty() ? JscOsTheme.dim() : JscOsTheme.green());
            JscOsTheme.textCenter(g, font, "EXPORT ALL", ALL_X + ALL_W / 2, BTN_Y + 4, JscOsTheme.accent());
        } else {
            JscOsTheme.textSCenter(g, font, exportBlockReason(), imageWidth / 2, BTN_Y + 4, JscOsTheme.dim());
        }

        JscOsTheme.textS(g, font, "right-click a row to delete it", LIST_X, 130, JscOsTheme.dim());
        JscOsTheme.textSRight(g, font, menu.romUsed() + " / " + menu.romLimit(),
                imageWidth - 8, 130, menu.romUsed() >= menu.romLimit() ? JscOsTheme.red() : JscOsTheme.text());
    }

    private void listRows(final GuiGraphics g, final List<CraftingPattern> patterns, final int scrollPos,
                          final Set<Integer> sel) {
        for (int row = 0; row < Math.min(VISIBLE_ROWS, patterns.size()); row++) {
            final int index = row + scrollPos;
            final CraftingPattern p = patterns.get(index);
            final int rowY = LIST_Y + row * ROW_H + 2;
            JscOsTheme.textS(g, font, "CRAFT", LIST_X + 5, rowY, JscOsTheme.accent());
            JscOsTheme.textS(g, font, p.result().getHoverName().getString() + " x" + p.result().getCount(),
                    LIST_X + 36, rowY, JscOsTheme.text());
            JscOsTheme.textSRight(g, font, sel.contains(index) ? "selected" : "",
                    imageWidth - 12, rowY, JscOsTheme.amber());
        }
    }

    private String rangeLabel(final int scrollPos, final int count) {
        final int clamped = clampScroll(scrollPos, count);
        return (clamped + 1) + "-" + Math.min(clamped + VISIBLE_ROWS, count) + " / " + count;
    }

    /** True when the media slot holds a rewritable disc with cycles left and there is something to export. */
    private boolean canExport() {
        final ItemStack disc = menu.slots.get(PatternReaderMenu.MEDIA_SLOT).getItem();
        return menu.hasComputer() && !menu.romPatterns().isEmpty()
                && disc.getItem() instanceof PatternDiscItem item && item.isRewritable()
                && PatternDiscItem.cyclesLeft(disc) > 0;
    }

    private String exportBlockReason() {
        if (!menu.hasComputer() || menu.romPatterns().isEmpty()) {
            return "";
        }
        final ItemStack disc = menu.slots.get(PatternReaderMenu.MEDIA_SLOT).getItem();
        if (disc.isEmpty()) {
            return "insert a rewritable disc to export";
        }
        if (!(disc.getItem() instanceof PatternDiscItem item) || !item.isRewritable()) {
            return "disc is write-once - cannot export";
        }
        return "disc has no rewrite cycles left";
    }

    private int clampScroll(final int value, final int count) {
        return Math.max(0, Math.min(value, Math.max(0, count - VISIBLE_ROWS)));
    }

    // Input

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double dx, final double dy) {
        final List<CraftingPattern> rows = activeTab == TAB_READ ? menu.discPatterns() : menu.romPatterns();
        if (rows.size() > VISIBLE_ROWS) {
            if (activeTab == TAB_READ) {
                scroll = clampScroll(scroll - (int) Math.signum(dy), rows.size());
            } else {
                romScroll = clampScroll(romScroll - (int) Math.signum(dy), rows.size());
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final int mx = (int) mouseX;
        final int my = (int) mouseY;
        // Tabs (either mouse button).
        if (hover(mx, my, READ_TAB_X, TAB_Y, TAB_W, TAB_H)) {
            activeTab = TAB_READ;
            return true;
        }
        if (hover(mx, my, ROM_TAB_X, TAB_Y, TAB_W, TAB_H)) {
            activeTab = TAB_ROM;
            requestRom();
            return true;
        }
        if (activeTab == TAB_READ) {
            return readClick(mx, my, button) || super.mouseClicked(mouseX, mouseY, button);
        }
        return romClick(mx, my, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean readClick(final int mx, final int my, final int button) {
        if (button != 0) {
            return false;
        }
        final List<CraftingPattern> patterns = menu.discPatterns();
        final int scrollPos = clampScroll(scroll, patterns.size());
        for (int row = 0; row < Math.min(VISIBLE_ROWS, patterns.size()); row++) {
            final int index = row + scrollPos;
            if (hover(mx, my, LIST_X, LIST_Y + row * ROW_H, imageWidth - 16, ROW_H - 2)) {
                if (!selection.remove(index)) {
                    selection.add(index);
                }
                sendButton(PatternReaderMenu.BUTTON_TOGGLE_BASE + index);
                return true;
            }
        }
        final boolean usable = menu.hasComputer() && !patterns.isEmpty();
        if (usable && !selection.isEmpty() && hover(mx, my, SEL_X, BTN_Y, SEL_W, BTN_H)) {
            sendButton(PatternReaderMenu.BUTTON_LOAD_SELECTED);
            selection.clear();
            return true;
        }
        if (usable && hover(mx, my, ALL_X, BTN_Y, ALL_W, BTN_H)) {
            sendButton(PatternReaderMenu.BUTTON_LOAD_ALL);
            selection.clear();
            return true;
        }
        return false;
    }

    private boolean romClick(final int mx, final int my, final int button) {
        final List<CraftingPattern> patterns = menu.romPatterns();
        final int scrollPos = clampScroll(romScroll, patterns.size());
        for (int row = 0; row < Math.min(VISIBLE_ROWS, patterns.size()); row++) {
            final int index = row + scrollPos;
            if (hover(mx, my, LIST_X, LIST_Y + row * ROW_H, imageWidth - 16, ROW_H - 2)) {
                if (button == 1) {
                    // Right-click deletes the pattern from the ROM; deletion reindexes the list, so
                    // drop any selection and let the server push a fresh snapshot.
                    romSelection.clear();
                    sendButton(PatternReaderMenu.BUTTON_ROM_REMOVE_BASE + index);
                } else if (button == 0) {
                    if (!romSelection.remove(index)) {
                        romSelection.add(index);
                    }
                    sendButton(PatternReaderMenu.BUTTON_ROM_TOGGLE_BASE + index);
                }
                return true;
            }
        }
        if (button == 0 && canExport()) {
            if (!romSelection.isEmpty() && hover(mx, my, SEL_X, BTN_Y, SEL_W, BTN_H)) {
                sendButton(PatternReaderMenu.BUTTON_EXPORT_SELECTED);
                romSelection.clear();
                return true;
            }
            if (hover(mx, my, ALL_X, BTN_Y, ALL_W, BTN_H)) {
                sendButton(PatternReaderMenu.BUTTON_EXPORT_ALL);
                romSelection.clear();
                return true;
            }
        }
        return false;
    }

}
