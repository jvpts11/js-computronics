/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client;

import dev.jstech.computers.blockentity.PatternEncoderBlockEntity;
import dev.jstech.computers.gui.layout.PatternEncoderLayout;
import dev.jstech.computers.menu.PatternEncoderMenu;
import dev.jstech.computers.os.VolumeLabel;
import dev.jstech.core.client.gui.theme.EraTheme;
import dev.jstech.core.client.gui.theme.EraThemes;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Pattern Encoder's bay panel: the medium in the bay, who the encoder is linked to, what it is doing and how
 * far along it is, with Eject and Cancel. It draws in the encoder's own era theme; the recipes themselves are
 * authored on the linked computer's Pattern Studio.
 *
 * <p>Every info line is clipped to its column; a clipped line shows its full text as a tooltip when hovered,
 * so a long file name never runs out of the frame.
 */
public class PatternEncoderScreen extends AbstractContainerScreen<PatternEncoderMenu> {

    private static final int LINES = 4;

    private EraTheme theme = EraThemes.STANDARD;
    private Button ejectBtn;
    private Button cancelBtn;
    /** The full text of each info line as last drawn, or null where the line was not clipped. */
    private final String[] clippedLines = new String[LINES];

    public PatternEncoderScreen(final PatternEncoderMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = PatternEncoderLayout.WIDTH;
        this.imageHeight = PatternEncoderLayout.HEIGHT;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        theme = EraThemes.of(era());
        ejectBtn = addRenderableWidget(new EraButton(leftPos + PatternEncoderLayout.EJECT_X,
                topPos + PatternEncoderLayout.BTN_Y, PatternEncoderLayout.EJECT_W, PatternEncoderLayout.BTN_H,
                Component.literal("Eject"), b -> press(PatternEncoderMenu.BUTTON_EJECT)));
        cancelBtn = addRenderableWidget(new EraButton(leftPos + PatternEncoderLayout.CANCEL_X,
                topPos + PatternEncoderLayout.BTN_Y, PatternEncoderLayout.CANCEL_W, PatternEncoderLayout.BTN_H,
                Component.literal("Cancel queue"), b -> press(PatternEncoderMenu.BUTTON_CANCEL)));
    }

    private void press(final int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Nullable
    private PatternEncoderBlockEntity encoder() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.blockEntityPos()) instanceof PatternEncoderBlockEntity be
                ? be : null;
    }

    private HardwareEra era() {
        final PatternEncoderBlockEntity be = encoder();
        return be == null ? HardwareEra.STANDARD : be.era();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        final PatternEncoderBlockEntity be = encoder();
        final boolean hasMedia = !menu.mediaStack().isEmpty();
        ejectBtn.active = hasMedia && (be == null || !be.locked());
        cancelBtn.active = be != null && (be.busy() || be.displayQueued() > 0);
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        theme.window(g, x, y, imageWidth, imageHeight);
        // Slot frames sit around the slots' own positions (the menu places each slot one pixel inside its frame).
        theme.slot(g, x + PatternEncoderLayout.MEDIA_X + 1, y + PatternEncoderLayout.MEDIA_Y + 1);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                theme.slot(g, x + PatternEncoderLayout.INV_X + col * 18, y + PatternEncoderLayout.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            theme.slot(g, x + PatternEncoderLayout.INV_X + col * 18, y + PatternEncoderLayout.INV_Y + 58);
        }

        final PatternEncoderBlockEntity be = encoder();
        final int ix = x + PatternEncoderLayout.INFO_X;
        final int iy = y + PatternEncoderLayout.INFO_Y;
        final int lh = PatternEncoderLayout.LINE_H;

        final boolean linked = be != null && be.ownerPos() != null;
        line(g, 0, linked ? "Linked to " + pos(be.ownerPos()) : "Not linked", ix, iy,
                linked ? theme.green() : theme.amber());
        final HardwareEra era = era();
        line(g, 1, eraLine(era), ix, iy + lh, era.screenColor() | 0xFF000000);
        final ItemStack media = menu.mediaStack();
        line(g, 2, media.isEmpty() ? "Bay: empty" : "Bay: " + VolumeLabel.of(media, "Removable medium"),
                ix, iy + lh * 2, media.isEmpty() ? theme.dim() : theme.text());
        final String status;
        final int statusColor;
        if (be == null) {
            status = "";
            statusColor = theme.text();
        } else if (!linked && be.phase() == PatternEncoderBlockEntity.Phase.IDLE) {
            status = "Connect a peripheral cable";
            statusColor = theme.dim();
        } else {
            status = be.statusLine() + (be.displayQueued() > 0 ? " (" + be.displayQueued() + " queued)" : "");
            statusColor = be.phase() == PatternEncoderBlockEntity.Phase.ERROR ? theme.red()
                    : be.phase() == PatternEncoderBlockEntity.Phase.DONE ? theme.green() : theme.text();
        }
        line(g, 3, status, ix, iy + lh * 3, statusColor);

        // Progress bar: filled by the job's percent, in the accent while busy, green once done.
        final int pct = be == null ? 0 : be.progressPercent();
        final boolean done = be != null && be.phase() == PatternEncoderBlockEntity.Phase.DONE;
        theme.track(g, x + PatternEncoderLayout.BAR_X, y + PatternEncoderLayout.BAR_Y, PatternEncoderLayout.BAR_W,
                pct / 100.0, done ? theme.green() : theme.accent());
    }

    private static String pos(final BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    /** What this era's encoder writes, for the era line. */
    private static String eraLine(final HardwareEra era) {
        return switch (era) {
            case VINTAGE -> "Vintage encoder: floppy disks";
            case LEGACY -> "Legacy encoder: CD-RW";
            default -> "Standard encoder: DVD, CD, USB";
        };
    }

    /**
     * Draws one info line at the small font, clipped to the column with an ellipsis; remembers the full text
     * when it had to clip so the tooltip can show it.
     */
    private void line(final GuiGraphics g, final int index, final String text, final int x, final int y,
                      final int color) {
        final float scale = PatternEncoderLayout.INFO_SCALE;
        final int limit = (int) (PatternEncoderLayout.INFO_W / scale);
        String shown = text;
        if (font.width(text) > limit) {
            shown = font.plainSubstrByWidth(text, limit - font.width("...")) + "...";
            clippedLines[index] = text;
        } else {
            clippedLines[index] = null;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, shown, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        g.drawString(font, "PATTERN ENCODER", PatternEncoderLayout.TITLE_X, PatternEncoderLayout.TITLE_Y,
                theme.text(), false);
        g.drawString(font, playerInventoryTitle, PatternEncoderLayout.INV_X, PatternEncoderLayout.INV_LABEL_Y,
                theme.dim(), false);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        final int ix = leftPos + PatternEncoderLayout.INFO_X;
        final int iy = topPos + PatternEncoderLayout.INFO_Y;
        if (mouseX >= ix && mouseX < ix + PatternEncoderLayout.INFO_W) {
            for (int i = 0; i < LINES; i++) {
                final int ly = iy + i * PatternEncoderLayout.LINE_H;
                if (clippedLines[i] != null && mouseY >= ly && mouseY < ly + PatternEncoderLayout.LINE_H) {
                    g.renderTooltip(font, Component.literal(clippedLines[i]), mouseX, mouseY);
                }
            }
        }
    }

    /** A button painted through the encoder's era theme, so the panel's controls match its frame. */
    private final class EraButton extends Button {

        EraButton(final int x, final int y, final int width, final int height, final Component message,
                  final OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY,
                                    final float partialTick) {
            final boolean lit = isHovered() && active;
            theme.button(g, getX(), getY(), getWidth(), getHeight(), lit);
            final int color = !active ? theme.dim() : lit ? theme.text() : theme.accent();
            g.drawCenteredString(font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
        }
    }
}
