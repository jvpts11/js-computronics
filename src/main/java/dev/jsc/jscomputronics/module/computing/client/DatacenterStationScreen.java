/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.datacenter.LoadBalanceMode;
import dev.jsc.jscomputronics.module.computing.menu.DatacenterStationMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.DatacenterSelectPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DatacenterSnapshotPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DatacenterStationActionPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkItemEntry;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Datacenter Station terminal: the monitor of one datacenter section seen as a single giant computer.
 */
public final class DatacenterStationScreen extends AbstractContainerScreen<DatacenterStationMenu> {

    private static final int W = 230;
    private static final int H = 252;

    // Row 1: identity. Row 2: the combined "giant computer".
    private static final int TILE_Y = 24;
    private static final int TILE_H = 20;
    private static final int SECTION_X = 8;
    private static final int SECTION_W = 106;
    private static final int SERVERS_X = 118;
    private static final int SERVERS_W = 50;
    private static final int OPS_X = 172;
    private static final int OPS_W = 50;
    private static final int ROW2_Y = 46;
    private static final int CPU_X = 8;
    private static final int CPU_W = 66;
    private static final int RAM_X = 78;
    private static final int RAM_W = 66;
    private static final int STORAGE_X = 148;
    private static final int STORAGE_W = 74;

    private static final int BAL_X = 8;
    private static final int BAL_Y = 68;
    private static final int BAL_W = 122;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 4;
    private static final int GRID_X = 34;
    private static final int GRID_Y = 84;
    private static final int CELL = 18;
    private static final int HINT_Y = 159;

    private static final int POP_W = 158;
    private static final int POP_H = 96;

    private int refreshTicks;

    // MOVE-out popup state (null key = closed).
    @org.jetbrains.annotations.Nullable
    private StorageKey popupKey;
    private long popupTotal;
    private long popupQty;
    private int destIndex;

    public DatacenterStationScreen(final DatacenterStationMenu menu, final Inventory inventory,
                                   final Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelY = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Refresh the section snapshot about once a second so the view reflects live storage.
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(
                    new DatacenterStationActionPayload(menu.stationPos(), DatacenterStationActionPayload.ACTION_REFRESH));
        }
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, W, H);
        JscOsTheme.headerBar(g, x + 6, y + 6, W - 12);

        // Row 1: section picker + counts. Row 2: the combined machine.
        JscOsTheme.button(g, x + SECTION_X, y + TILE_Y, SECTION_W, TILE_H, inSection(mouseX, mouseY));
        JscOsTheme.panel(g, x + SERVERS_X, y + TILE_Y, SERVERS_W, TILE_H);
        JscOsTheme.panel(g, x + OPS_X, y + TILE_Y, OPS_W, TILE_H);
        JscOsTheme.panel(g, x + CPU_X, y + ROW2_Y, CPU_W, TILE_H);
        JscOsTheme.panel(g, x + RAM_X, y + ROW2_Y, RAM_W, TILE_H);
        JscOsTheme.panel(g, x + STORAGE_X, y + ROW2_Y, STORAGE_W, TILE_H);

        JscOsTheme.button(g, x + BAL_X, y + BAL_Y, BAL_W, 12, inBalance(mouseX, mouseY));

        // Unified item grid.
        final List<NetworkItemEntry> items = menu.items();
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                final int cx = x + GRID_X + col * CELL;
                final int cy = y + GRID_Y + row * CELL;
                JscOsTheme.slot(g, cx, cy);
                final int idx = row * GRID_COLS + col;
                if (idx < items.size()) {
                    final NetworkItemEntry entry = items.get(idx);
                    drawDataIcon(g, entry.key(), entry.total(), cx, cy);
                }
            }
        }

        // Player inventory + hotbar slot cells (no background texture, so the panel draws them).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + DatacenterStationMenu.INV_X + col * 18,
                        y + DatacenterStationMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + DatacenterStationMenu.INV_X + col * 18, y + DatacenterStationMenu.HOTBAR_Y);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "DATACENTER STATION", 12, 10, JscOsTheme.TEXT);

        // SECTION tile: picker counter on the label line, the bound section's name below.
        JscOsTheme.textS(g, font, "SECTION", SECTION_X + 4, TILE_Y + 3, JscOsTheme.DIM);
        final int avail = menu.availableSectionCount();
        if (avail > 1) {
            JscOsTheme.textSRight(g, font, avail + " ▸", SECTION_X + SECTION_W - 4, TILE_Y + 3, JscOsTheme.DIM);
        }
        JscOsTheme.textS(g, font, trim(menu.sectionLabel(), 20), SECTION_X + 4, TILE_Y + 11, JscOsTheme.ACCENT);

        JscOsTheme.tileTextS(g, font, SERVERS_X, TILE_Y, "SERVERS", String.valueOf(menu.serverCount()),
                JscOsTheme.TEXT);
        JscOsTheme.tileTextS(g, font, OPS_X, TILE_Y, "OPS", String.valueOf(menu.activeOps()),
                menu.activeOps() > 0 ? JscOsTheme.GREEN : JscOsTheme.DIM);

        // Row 2 — the section as ONE machine: summed CPU, RAM and storage.
        JscOsTheme.tileTextS(g, font, CPU_X, ROW2_Y, "CPU", JscOsTheme.fmt(menu.cpuCapacity()) + " it/t",
                JscOsTheme.TEXT);
        JscOsTheme.tileTextS(g, font, RAM_X, ROW2_Y, "RAM", JscOsTheme.fmt(menu.ramBuffer()) + " it",
                JscOsTheme.TEXT);
        JscOsTheme.tileTextS(g, font, STORAGE_X, ROW2_Y, "STORAGE",
                itemsTight(menu.storageUsed()) + " / " + itemsTight(menu.storageTotal()), JscOsTheme.ACCENT);

        final LoadBalanceMode mode = menu.loadBalanceMode();
        JscOsTheme.textS(g, font, "BALANCE", BAL_X + 4, BAL_Y + 2, JscOsTheme.DIM);
        JscOsTheme.textSRight(g, font, modeLabel(mode) + " ⟳", BAL_X + BAL_W - 4, BAL_Y + 2, modeColor(mode));

        JscOsTheme.textSCenter(g, font, "click item: move out · with items: deposit", W / 2, HINT_Y,
                JscOsTheme.DIM);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (popupKey != null) {
            renderPopup(g, mouseX, mouseY);
            return;
        }
        // Per-Server breakdown as a tooltip on the SERVERS tile.
        if (inServers(mouseX, mouseY) && !menu.servers().isEmpty()) {
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("Section Servers"));
            for (final DatacenterSnapshotPayload.ServerLine line : menu.servers()) {
                lines.add(Component.literal(
                        line.name() + "  " + itemsTight(line.used()) + " / " + itemsTight(line.total())));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        // Name + exact quantity of the hovered grid item (only with an empty cursor, like the terminal).
        if (menu.getCarried().isEmpty()) {
            final int idx = gridIndexAt(mouseX, mouseY);
            if (idx >= 0 && idx < menu.items().size()) {
                final NetworkItemEntry entry = menu.items().get(idx);
                final String qty = entry.isFluid()
                        ? String.format("%,d mB", entry.total())
                        : String.format("%,d", entry.total());
                g.renderComponentTooltip(font,
                        List.of(entry.name(), Component.literal(qty)), mouseX, mouseY);
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (popupKey != null) {
            return popupClick(mouseX, mouseY);
        }
        if (button == 0 && inSection(mouseX, mouseY)) {
            send(DatacenterStationActionPayload.ACTION_NEXT_SECTION);
            return true;
        }
        if (button == 0 && inBalance(mouseX, mouseY)) {
            send(DatacenterStationActionPayload.ACTION_CYCLE_BALANCE);
            return true;
        }
        if (inGrid(mouseX, mouseY)) {
            if (!menu.getCarried().isEmpty()) {
                // Deposit the cursor stack into the section (left = all, right = one).
                send(button == 1
                        ? DatacenterStationActionPayload.ACTION_INSERT_CURSOR_ONE
                        : DatacenterStationActionPayload.ACTION_INSERT_CURSOR);
                return true;
            }
            // Empty cursor: open the MOVE-out popup for the clicked item.
            final int idx = gridIndexAt(mouseX, mouseY);
            if (idx >= 0 && idx < menu.items().size()) {
                openPopup(menu.items().get(idx));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        // Escape closes an open popup rather than the whole screen.
        if (popupKey != null && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            popupKey = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(final int action) {
        PacketDistributor.sendToServer(new DatacenterStationActionPayload(menu.stationPos(), action));
    }

    private static final long[] PRESETS = {1L, 16L, 64L, 256L, -1L}; // -1 = MAX (the whole total)

    private void openPopup(final NetworkItemEntry entry) {
        popupKey = entry.key();
        popupTotal = entry.total();
        popupQty = Math.min(entry.total(), 64L);
        destIndex = 0;
    }

    private int gridIndexAt(final double mx, final double my) {
        final int col = (int) Math.floor((mx - (leftPos + GRID_X)) / CELL);
        final int row = (int) Math.floor((my - (topPos + GRID_Y)) / CELL);
        if (col < 0 || col >= GRID_COLS || row < 0 || row >= GRID_ROWS) {
            return -1;
        }
        return row * GRID_COLS + col;
    }

    private int popX() {
        return leftPos + (W - POP_W) / 2;
    }

    private int popY() {
        return topPos + 40;
    }

    private boolean popupClick(final double mx, final double my) {
        final int px = popX();
        final int py = popY();
        if (!inRect(mx, my, px, py, POP_W, POP_H)) {
            popupKey = null; // a click outside the box cancels
            return true;
        }
        for (int i = 0; i < PRESETS.length; i++) {
            if (inRect(mx, my, px + 6 + i * 29, py + 40, 27, 12)) {
                popupQty = PRESETS[i] < 0 ? popupTotal : Math.min(PRESETS[i], popupTotal);
                return true;
            }
        }
        final List<DatacenterSnapshotPayload.DestEntry> dests = menu.destinations();
        if (!dests.isEmpty()) {
            if (inRect(mx, my, px + 18, py + 55, 12, 12)) {
                destIndex = (destIndex - 1 + dests.size()) % dests.size();
                return true;
            }
            if (inRect(mx, my, px + POP_W - 18, py + 55, 12, 12)) {
                destIndex = (destIndex + 1) % dests.size();
                return true;
            }
            if (inRect(mx, my, px + 6, py + 74, POP_W - 12, 14)) {
                sendSelect(dests);
                popupKey = null;
                return true;
            }
        }
        return true; // swallow any other click inside the box
    }

    private void sendSelect(final List<DatacenterSnapshotPayload.DestEntry> dests) {
        if (popupKey == null || dests.isEmpty() || popupQty <= 0L) {
            return;
        }
        final long destPos = dests.get(Math.min(destIndex, dests.size() - 1)).pos();
        PacketDistributor.sendToServer(new DatacenterSelectPayload(menu.stationPos(), popupKey, popupQty, destPos));
    }

    private void renderPopup(final GuiGraphics g, final int mx, final int my) {
        // The grid items draw at an elevated z (~200 for decorations); lift the whole modal above
        // them so nothing from the screen behind bleeds through the popup.
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, this.width, this.height, 0xA0000000); // dim the screen behind the modal
        final int px = popX();
        final int py = popY();
        JscOsTheme.window(g, px, py, POP_W, POP_H);
        JscOsTheme.headerBar(g, px + 4, py + 4, POP_W - 8);
        if (popupKey != null) {
            if (popupKey.isFluid()) {
                FluidSprite.draw(g, popupKey.fluidPrototype(), px + 6, py + 4);
            } else {
                g.renderItem(popupKey.stack(1), px + 6, py + 4);
            }
        }
        g.drawString(font, "MOVE OUT", px + 26, py + 7, JscOsTheme.TEXT, false);
        JscOsTheme.textS(g, font, "of " + JscOsTheme.fmt(popupTotal) + " in section", px + 6, py + 23,
                JscOsTheme.DIM);
        JscOsTheme.textS(g, font, "QUANTITY  " + JscOsTheme.fmt(popupQty), px + 6, py + 31, JscOsTheme.TEXT);

        final String[] labels = {"1", "16", "64", "256", "MAX"};
        for (int i = 0; i < labels.length; i++) {
            final int bx = px + 6 + i * 29;
            JscOsTheme.button(g, bx, py + 40, 27, 12, inRect(mx, my, bx, py + 40, 27, 12));
            JscOsTheme.textSCenter(g, font, labels[i], bx + 13, py + 42, JscOsTheme.ACCENT2);
        }

        final List<DatacenterSnapshotPayload.DestEntry> dests = menu.destinations();
        JscOsTheme.textS(g, font, "TO", px + 6, py + 57, JscOsTheme.DIM);
        if (dests.isEmpty()) {
            JscOsTheme.textSCenter(g, font, "no destination computer", px + POP_W / 2, py + 57, JscOsTheme.RED);
        } else {
            JscOsTheme.button(g, px + 18, py + 55, 12, 12, inRect(mx, my, px + 18, py + 55, 12, 12));
            JscOsTheme.button(g, px + POP_W - 18, py + 55, 12, 12, inRect(mx, my, px + POP_W - 18, py + 55, 12, 12));
            JscOsTheme.textSCenter(g, font, "<", px + 24, py + 57, JscOsTheme.TEXT);
            JscOsTheme.textSCenter(g, font, ">", px + POP_W - 12, py + 57, JscOsTheme.TEXT);
            final DatacenterSnapshotPayload.DestEntry d = dests.get(Math.min(destIndex, dests.size() - 1));
            JscOsTheme.textSCenter(g, font, trim(d.name(), 16), px + (POP_W + 18) / 2, py + 57, JscOsTheme.ACCENT);
        }

        final boolean canMove = !dests.isEmpty();
        final boolean hovMove = inRect(mx, my, px + 6, py + 74, POP_W - 12, 14);
        g.fill(px + 6, py + 74, px + POP_W - 6, py + 88,
                canMove ? (hovMove ? 0xFF2BB3A4 : 0xFF1F9488) : 0xFF2A2F38);
        JscOsTheme.textSCenter(g, font, "MOVE", px + POP_W / 2, py + 77, canMove ? 0xFFFFFFFF : JscOsTheme.DIM);
        g.pose().popPose();
    }

    private boolean inSection(final double mx, final double my) {
        return inRect(mx, my, leftPos + SECTION_X, topPos + TILE_Y, SECTION_W, TILE_H);
    }

    private boolean inServers(final double mx, final double my) {
        return inRect(mx, my, leftPos + SERVERS_X, topPos + TILE_Y, SERVERS_W, TILE_H);
    }

    private boolean inBalance(final double mx, final double my) {
        return inRect(mx, my, leftPos + BAL_X, topPos + BAL_Y, BAL_W, 12);
    }

    private boolean inGrid(final double mx, final double my) {
        return inRect(mx, my, leftPos + GRID_X, topPos + GRID_Y, GRID_COLS * CELL, GRID_ROWS * CELL);
    }

    private static boolean inRect(final double mx, final double my, final int x, final int y, final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawDataIcon(final GuiGraphics g, final StorageKey key, final long count, final int x, final int y) {
        if (key.isFluid()) {
            FluidSprite.draw(g, key.fluidPrototype(), x, y);
            final String c = JscOsTheme.fmt(count);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, c, x + 17 - font.width(c), y + 9, 0xFFFFFFFF, true);
            g.pose().popPose();
        } else {
            g.renderItem(key.stack(1), x, y);
            g.renderItemDecorations(font, key.stack(1), x, y, JscOsTheme.fmt(count));
        }
    }

    private static String itemsTight(final long weight) {
        final long items = weight / StorageKey.MB_EQ_PER_ITEM;
        if (items < 10_000L) {
            return String.format("%,d", items);
        }
        if (items < 1_000_000L) {
            return items % 1_000L == 0L ? (items / 1_000L) + "k" : String.format("%.1fk", items / 1_000.0);
        }
        return items % 1_000_000L == 0L ? (items / 1_000_000L) + "M" : String.format("%.1fM", items / 1_000_000.0);
    }

    private static String modeLabel(final LoadBalanceMode mode) {
        return switch (mode) {
            case ROUND_ROBIN -> "ROUND-ROBIN";
            case LEAST_LOADED -> "LEAST-LOADED";
            case MANUAL -> "MANUAL";
        };
    }

    private static int modeColor(final LoadBalanceMode mode) {
        return switch (mode) {
            case ROUND_ROBIN -> JscOsTheme.ACCENT2;
            case LEAST_LOADED -> JscOsTheme.AMBER;
            case MANUAL -> JscOsTheme.DIM;
        };
    }

    private static String trim(final String s, final int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
