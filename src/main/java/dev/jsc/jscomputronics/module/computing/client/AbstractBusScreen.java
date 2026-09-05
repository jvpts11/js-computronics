/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.block.part.AbstractBusPart;
import dev.jsc.jscomputronics.module.computing.gui.layout.BusLayout;
import dev.jsc.jscomputronics.module.computing.menu.AbstractBusMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.SetBusNamePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shared configuration screen for the two bus parts, in the flat-dark "computer OS" theme: an editable name
 * field (so a query can address the bus), a ghost filter slot, the min/max stock steppers (shift for ×16),
 * and the continuous/redstone mode toggle, over the player inventory. Every element position comes from
 * {@link BusLayout}, the same source the layout test validates for overlaps and overflow; the subclasses only
 * supply the window title and the filter-slot hint.
 */
public abstract class AbstractBusScreen<T extends AbstractBusMenu> extends AbstractComputerScreen<T> {

    private EditBox nameBox;
    private String nameValue;

    protected AbstractBusScreen(final T menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = BusLayout.WIDTH;
        this.imageHeight = BusLayout.HEIGHT;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    /** The window caption, e.g. "EXPORT BUS". */
    protected abstract String windowTitle();

    /** The tooltip shown when hovering the empty filter slot. */
    protected abstract String filterHint();

    @Override
    protected void init() {
        super.init();
        final int bx = leftPos + BusLayout.NAME_X + 3;
        final int by = topPos + BusLayout.NAME_Y + 2;
        nameBox = new EditBox(font, bx, by, BusLayout.NAME_W - 6, BusLayout.NAME_H - 3,
                Component.literal("name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(AbstractBusPart.MAX_NAME_LENGTH);
        nameBox.setTextColor(JscOsTheme.text());
        // Set the value before the responder so restoring it (open, or a window resize) sends no packet.
        nameBox.setValue(nameValue != null ? nameValue : menu.busName());
        nameBox.setResponder(this::onNameChanged);
        addRenderableWidget(nameBox);
    }

    private void onNameChanged(final String value) {
        nameValue = value;
        PacketDistributor.sendToServer(new SetBusNamePayload(menu.cablePos(), menu.face().get3DDataValue(), value));
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + BusLayout.HEADER_X, y + BusLayout.HEADER_Y, BusLayout.HEADER_W);

        // Name field background (the EditBox itself is borderless and draws only its text over this).
        final int nx = x + BusLayout.NAME_X;
        final int ny = y + BusLayout.NAME_Y;
        g.fill(nx, ny, nx + BusLayout.NAME_W, ny + BusLayout.NAME_H, JscOsTheme.track());
        JscOsTheme.hLine(g, nx, ny, BusLayout.NAME_W);
        JscOsTheme.hLine(g, nx, ny + BusLayout.NAME_H - 1, BusLayout.NAME_W);
        JscOsTheme.vLine(g, nx, ny, BusLayout.NAME_H);
        JscOsTheme.vLine(g, nx + BusLayout.NAME_W - 1, ny, BusLayout.NAME_H);

        // Every bus shows its filter slot (on a crafting bus it routes the mounted face). Only the stock
        // controls (min/max window, mode) vanish on the passive crafting buses rather than lie.
        if (menu.filterApplies()) {
            JscOsTheme.slot(g, x + BusLayout.FILTER_X, y + BusLayout.FILTER_Y); // ghost filter slot
        }
        if (menu.stockControlsApply()) {
            stepperBg(g, x, y, BusLayout.MIN_Y, mouseX, mouseY);
            stepperBg(g, x, y, BusLayout.MAX_Y, mouseX, mouseY);
            JscOsTheme.button(g, x + BusLayout.MODE_X, y + BusLayout.MODE_Y, BusLayout.MODE_W, BusLayout.MODE_H,
                    hover(mouseX, mouseY, BusLayout.MODE_X, BusLayout.MODE_Y, BusLayout.MODE_W, BusLayout.MODE_H));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + BusLayout.INV_X + col * BusLayout.SLOT,
                        y + BusLayout.INV_Y + row * BusLayout.SLOT);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + BusLayout.INV_X + col * BusLayout.SLOT, y + BusLayout.HOTBAR_Y);
        }
    }

    private void stepperBg(final GuiGraphics g, final int x, final int y, final int row,
                           final int mouseX, final int mouseY) {
        JscOsTheme.button(g, x + BusLayout.MINUS_X, y + row, BusLayout.STEP, BusLayout.STEP,
                hover(mouseX, mouseY, BusLayout.MINUS_X, row, BusLayout.STEP, BusLayout.STEP));
        g.fill(x + BusLayout.TRACK_X, y + row, x + BusLayout.TRACK_X + BusLayout.TRACK_W,
                y + row + BusLayout.STEP, JscOsTheme.track());
        JscOsTheme.hLine(g, x + BusLayout.TRACK_X, y + row, BusLayout.TRACK_W);
        JscOsTheme.button(g, x + BusLayout.PLUS_X, y + row, BusLayout.STEP, BusLayout.STEP,
                hover(mouseX, mouseY, BusLayout.PLUS_X, row, BusLayout.STEP, BusLayout.STEP));
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, windowTitle(), 12, 11, JscOsTheme.text());
        final boolean linked = menu.linked();
        final String pill = linked ? "LINKED" : "OFFLINE";
        final int pillColor = linked ? JscOsTheme.green() : JscOsTheme.red();
        final int pillX = BusLayout.HEADER_W - font.width(pill);
        JscOsTheme.text(g, font, pill, pillX, 11, pillColor);
        g.fill(pillX - 6, 11, pillX - 2, 15, pillColor);

        JscOsTheme.text(g, font, "NAME", BusLayout.NAME_LABEL_X, BusLayout.NAME_LABEL_Y, JscOsTheme.dim());

        if (menu.stockControlsApply()) {
            // Min / max steppers: label + centered value + "-"/"+".
            JscOsTheme.text(g, font, "MIN", BusLayout.LABEL_X, BusLayout.MIN_Y + 3, JscOsTheme.dim());
            JscOsTheme.text(g, font, "MAX", BusLayout.LABEL_X, BusLayout.MAX_Y + 3, JscOsTheme.dim());
            JscOsTheme.textCenter(g, font, "-", BusLayout.MINUS_X + BusLayout.STEP / 2,
                    BusLayout.MIN_Y + 3, JscOsTheme.accent());
            JscOsTheme.textCenter(g, font, "-", BusLayout.MINUS_X + BusLayout.STEP / 2,
                    BusLayout.MAX_Y + 3, JscOsTheme.accent());
            JscOsTheme.textCenter(g, font, "+", BusLayout.PLUS_X + BusLayout.STEP / 2,
                    BusLayout.MIN_Y + 3, JscOsTheme.accent());
            JscOsTheme.textCenter(g, font, "+", BusLayout.PLUS_X + BusLayout.STEP / 2,
                    BusLayout.MAX_Y + 3, JscOsTheme.accent());
            final int mid = (BusLayout.MINUS_X + BusLayout.STEP + BusLayout.PLUS_X) / 2;
            JscOsTheme.textCenter(g, font, String.valueOf(menu.min()), mid, BusLayout.MIN_Y + 3,
                    JscOsTheme.text());
            JscOsTheme.textCenter(g, font, menu.max() <= 0 ? "any" : String.valueOf(menu.max()), mid,
                    BusLayout.MAX_Y + 3, JscOsTheme.text());

            // Mode toggle.
            JscOsTheme.text(g, font, "MODE", BusLayout.LABEL_X, BusLayout.MODE_Y + 4, JscOsTheme.dim());
            final String modeText = menu.mode() == AbstractBusPart.MODE_CONTINUOUS ? "CONTINUOUS" : "ON DEMAND";
            JscOsTheme.textCenter(g, font, modeText, BusLayout.MODE_X + BusLayout.MODE_W / 2,
                    BusLayout.MODE_Y + 4, JscOsTheme.accent());
        } else {
            // A passive crafting bus keeps its filter (it routes the mounted face) but has no stock window:
            // explain the filter in place of the inapplicable min/max/mode controls.
            JscOsTheme.textS(g, font, "Filter pins what this face", BusLayout.LABEL_X,
                    BusLayout.MIN_Y + 1, JscOsTheme.dim());
            JscOsTheme.textS(g, font, "carries (empty = any). The", BusLayout.LABEL_X,
                    BusLayout.MIN_Y + 10, JscOsTheme.dim());
            JscOsTheme.textS(g, font, "crafting engine drives it.", BusLayout.LABEL_X,
                    BusLayout.MIN_Y + 19, JscOsTheme.dim());
        }

        JscOsTheme.text(g, font, "INVENTORY", 8, BusLayout.INV_LABEL_Y, JscOsTheme.dim());
    }

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        // While the name field has focus, route keys to it and swallow the inventory key so 'e' types
        // a character instead of closing the GUI; ESC just unfocuses the field.
        if (nameBox != null && nameBox.isFocused()) {
            if (key == 256) {
                nameBox.setFocused(false);
                setFocused(null);
                return true;
            }
            nameBox.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0 && menu.stockControlsApply()) {
            final boolean shift = hasShiftDown();
            if (hover((int) mouseX, (int) mouseY, BusLayout.MINUS_X, BusLayout.MIN_Y, BusLayout.STEP, BusLayout.STEP)) {
                sendButton(shift ? AbstractBusMenu.BTN_MIN_DOWN16 : AbstractBusMenu.BTN_MIN_DOWN1);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, BusLayout.PLUS_X, BusLayout.MIN_Y, BusLayout.STEP, BusLayout.STEP)) {
                sendButton(shift ? AbstractBusMenu.BTN_MIN_UP16 : AbstractBusMenu.BTN_MIN_UP1);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, BusLayout.MINUS_X, BusLayout.MAX_Y, BusLayout.STEP, BusLayout.STEP)) {
                sendButton(shift ? AbstractBusMenu.BTN_MAX_DOWN16 : AbstractBusMenu.BTN_MAX_DOWN1);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, BusLayout.PLUS_X, BusLayout.MAX_Y, BusLayout.STEP, BusLayout.STEP)) {
                sendButton(shift ? AbstractBusMenu.BTN_MAX_UP16 : AbstractBusMenu.BTN_MAX_UP1);
                return true;
            }
            if (hover((int) mouseX, (int) mouseY, BusLayout.MODE_X, BusLayout.MODE_Y, BusLayout.MODE_W, BusLayout.MODE_H)) {
                sendButton(AbstractBusMenu.BTN_MODE);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Hint on the empty ghost filter slot (a held item sets the filter, not consumed).
        if (menu.filterApplies() && menu.filterStack().isEmpty()
                && hover(mouseX, mouseY, BusLayout.FILTER_X, BusLayout.FILTER_Y, 16, 16)) {
            g.renderTooltip(font, Component.literal(filterHint()), mouseX, mouseY);
        }
    }
}
