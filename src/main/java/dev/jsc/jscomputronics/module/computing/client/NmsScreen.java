/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.NmsMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.RunSqlPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.SqlResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Network Management Studio: an SSMS-style operations console. A left object-explorer shows the network schema; the query editor at the top runs an SQL statement (the server's dialect) against the network; the results grid below lists a read's rows, and the status bar reports the outcome. The player installs it from the Command Prompt and opens it with {@code run nms}.
 */
public class NmsScreen extends AbstractComputerScreen<NmsMenu> {

    private static final int EXPLORER_W = 66;
    private static final int ROW_H = 9;
    private static final float SMALL = 0.85f;

    private EditBox query;
    private final List<SqlResultPayload.Row> rows = new ArrayList<>();
    private String status = "ready";
    private boolean statusOk = true;
    private int scroll;

    public NmsScreen(final NmsMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 270;
        this.imageHeight = 198;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        query = new EditBox(font, leftPos + EXPLORER_W + 10, topPos + 30, imageWidth - EXPLORER_W - 70, 12,
                Component.literal("query"));
        query.setBordered(false);
        query.setMaxLength(RunSqlPayload.MAX_LEN);
        query.setTextColor(JscOsTheme.TEXT);
        query.setHint(Component.literal("SELECT * FROM network").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        query.setFocused(true);
        setInitialFocus(query);
        addRenderableWidget(query);
    }

    public static void accept(final SqlResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof NmsScreen screen) {
            screen.apply(payload);
        }
    }

    private void apply(final SqlResultPayload payload) {
        rows.clear();
        rows.addAll(payload.rows());
        status = payload.message();
        statusOk = payload.ok();
        scroll = 0;
    }

    private void execute() {
        final String sql = query.getValue().trim();
        if (sql.isEmpty()) {
            return;
        }
        status = "running...";
        statusOk = true;
        PacketDistributor.sendToServer(new RunSqlPayload(menu.monitorPos(), menu.hostPos(), sql));
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, imageWidth - 12);
        // Object explorer (left), query editor strip (top), results grid (centre), status bar (bottom).
        g.fill(x + 6, y + 24, x + EXPLORER_W, y + imageHeight - 6, JscOsTheme.RAIL);
        JscOsTheme.vLine(g, x + EXPLORER_W, y + 24, imageHeight - 30);
        g.fill(x + EXPLORER_W + 6, y + 27, x + imageWidth - 6, y + 41, JscOsTheme.PANEL);
        g.fill(x + EXPLORER_W + 6, y + 44, x + imageWidth - 6, y + imageHeight - 18, 0xFF070A0E);
        g.fill(x + EXPLORER_W + 6, y + imageHeight - 16, x + imageWidth - 6, y + imageHeight - 6, JscOsTheme.PANEL);
        // Execute button.
        final boolean hover = hover(mouseX, mouseY, imageWidth - 60, 28, 52, 12);
        JscOsTheme.button(g, x + imageWidth - 60, y + 28, 52, 12, hover);
        // Column header under the editor.
        g.fill(x + EXPLORER_W + 6, y + 44, x + imageWidth - 6, y + 53, JscOsTheme.PANEL);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "NETWORK MANAGEMENT STUDIO", 12, 11, JscOsTheme.TEXT);

        // Object explorer: the schema the statements address.
        JscOsTheme.textS(g, font, "OBJECT EXPLORER", 10, 28, JscOsTheme.DIM);
        JscOsTheme.textS(g, font, "v NETWORK", 10, 40, JscOsTheme.ACCENT);
        JscOsTheme.textS(g, font, "  v tables", 10, 49, JscOsTheme.DIM);
        JscOsTheme.textS(g, font, "    network", 10, 58, JscOsTheme.TEXT);
        JscOsTheme.textS(g, font, "  columns:", 10, 70, JscOsTheme.DIM);
        JscOsTheme.textS(g, font, "    item", 10, 79, JscOsTheme.TEXT);
        JscOsTheme.textS(g, font, "    quantity", 10, 88, JscOsTheme.TEXT);
        JscOsTheme.textS(g, font, "    server", 10, 97, JscOsTheme.TEXT);

        JscOsTheme.text(g, font, ">", EXPLORER_W + 4, 30, JscOsTheme.ACCENT);
        JscOsTheme.textCenter(g, font, "EXECUTE", imageWidth - 34, 30, JscOsTheme.ACCENT);

        // Results column header.
        JscOsTheme.textS(g, font, "item", EXPLORER_W + 10, 46, JscOsTheme.DIM);
        JscOsTheme.textSRight(g, font, "quantity", imageWidth - 12, 46, JscOsTheme.DIM);

        // Results grid.
        final int top = 55;
        final int bottom = imageHeight - 19;
        final int visible = (bottom - top) / ROW_H;
        final int clamped = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible)));
        for (int i = 0; i < visible && clamped + i < rows.size(); i++) {
            final SqlResultPayload.Row row = rows.get(clamped + i);
            final int ry = top + i * ROW_H;
            small(g, row.label(), EXPLORER_W + 10, ry, JscOsTheme.TEXT);
            JscOsTheme.textSRight(g, font, JscOsTheme.fmt(row.quantity()), imageWidth - 12, ry, JscOsTheme.GREEN);
        }
        if (rows.isEmpty()) {
            JscOsTheme.textS(g, font, "no result set", EXPLORER_W + 10, top + 2, JscOsTheme.DIM);
        }

        // Status bar.
        JscOsTheme.textS(g, font, status, EXPLORER_W + 10, imageHeight - 14,
                statusOk ? JscOsTheme.GREEN : JscOsTheme.RED);
        JscOsTheme.textSRight(g, font, "F5 / ENTER to run", imageWidth - 10, imageHeight - 14, JscOsTheme.DIM);
    }

    private void small(final GuiGraphics g, final String text, final int x, final int y, final int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(SMALL, SMALL, 1.0f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0 && hover((int) mouseX, (int) mouseY, imageWidth - 60, 28, 52, 12)) {
            execute();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double dx, final double dy) {
        if (!rows.isEmpty()) {
            scroll = Math.max(0, scroll - (int) Math.signum(dy));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        if (key == 257 || key == 335 || key == 294) { // Enter / numpad Enter / F5
            execute();
            return true;
        }
        if (key == 256) { // Esc closes
            onClose();
            return true;
        }
        if (query != null && query.isFocused()) {
            query.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(final char c, final int mods) {
        return query != null && query.charTyped(c, mods);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
    }
}
