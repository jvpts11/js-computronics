/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.PersonalComputerMenu;
import dev.jsc.jscomputronics.client.gui.logic.ScrollState;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkInsertPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkItemEntry;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkSelectPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Screen for the Personal Computer.
 */
public class PersonalComputerScreen extends AbstractContainerScreen<PersonalComputerMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int DIVIDER = 0xFF9A9A9A;
    private static final int LABEL = 0xFF404040;
    private static final int VIEW_FILL = 0xFFB2B2B2;
    private static final int INFO_X = 126;
    private static final int DIVIDER_X = 120;

    // Network tab item grid.
    private static final int NET_X = 8;
    private static final int NET_Y = 24;
    private static final int NET_COLS = 9;
    private static final int NET_ROWS = 5;

    private Button localTab;
    private Button storageTab;
    private Button networkTab;
    private Button powerButton;
    private Button autoButton;

    private ScrollState networkScroll = ScrollState.of(0, NET_ROWS);

    public PersonalComputerScreen(final PersonalComputerMenu menu, final Inventory inventory,
                                  final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 256;
        this.inventoryLabelY = 162;
    }

    @Override
    protected void init() {
        super.init();
        localTab = addRenderableWidget(Button.builder(Component.literal("Local"),
                        b -> selectTab(PersonalComputerMenu.TAB_LOCAL))
                .bounds(leftPos + 6, topPos + 2, 40, 14).build());
        storageTab = addRenderableWidget(Button.builder(Component.literal("Storage"),
                        b -> selectTab(PersonalComputerMenu.TAB_STORAGE))
                .bounds(leftPos + 48, topPos + 2, 50, 14).build());
        networkTab = addRenderableWidget(Button.builder(Component.literal("Network"),
                        b -> selectTab(PersonalComputerMenu.TAB_NETWORK))
                .bounds(leftPos + 100, topPos + 2, 54, 14).build());
        powerButton = addRenderableWidget(Button.builder(Component.literal("Turn On"),
                        b -> sendButton(PersonalComputerMenu.BUTTON_POWER))
                .bounds(leftPos + INFO_X, topPos + 78, 66, 16).build());
        autoButton = addRenderableWidget(Button.builder(Component.literal("Auto: OFF"),
                        b -> sendButton(PersonalComputerMenu.BUTTON_AUTOSTART))
                .bounds(leftPos + INFO_X, topPos + 96, 66, 16).build());
    }

    private void selectTab(final int tab) {
        menu.setActiveTab(tab);                                   // client-side, immediate
        if (tab == PersonalComputerMenu.TAB_NETWORK) {
            networkScroll = networkScroll.scrolledToTop();        // always open at the top
        }
        sendButton(PersonalComputerMenu.BUTTON_TAB_BASE + tab);   // sync the server
    }

    private void sendButton(final int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;

        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth, y + 1, BEVEL_LIGHT);
        g.fill(x, y, x + 1, y + imageHeight, BEVEL_LIGHT);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, BEVEL_DARK);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, BEVEL_DARK);
        g.fill(x, y + 18, x + imageWidth, y + 19, DIVIDER); // under the tab strip

        final int tab = menu.activeTab();
        if (tab == PersonalComputerMenu.TAB_LOCAL) {
            g.fill(x + DIVIDER_X, y + 20, x + DIVIDER_X + 1, y + 112, DIVIDER); // hardware | status

            slot(g, x + 8, y + 32);  // motherboard (always)
            slot(g, x + 8, y + 64);  // psu (always)
            if (menu.boardCpuSlots() > 0) {
                slot(g, x + 44, y + 32); // cpu
            }
            final int ram = Math.min(menu.boardRamSlots(), 4);
            final int gpu = Math.min(menu.boardPcieSlots(), 4);
            for (int i = 0; i < ram; i++) {
                slot(g, x + 44 + i * 18, y + 64);
            }
            for (int i = 0; i < gpu; i++) {
                slot(g, x + 44 + i * 18, y + 96);
            }
            final int disk = Math.min(menu.boardDiskSlots(), 2);
            for (int i = 0; i < disk; i++) {
                slot(g, x + 8 + i * 18, y + 96);
            }
        } else if (tab == PersonalComputerMenu.TAB_STORAGE) {
            // Only the disk-backed slots are drawn; no disk => an empty bay.
            final int usable = menu.usableStorageSlots();
            for (int i = 0; i < usable; i++) {
                slot(g, x + 8 + (i % 9) * 18, y + 30 + (i / 9) * 18);
            }
        } else {
            // Network tab: a grid of slots that the network item view renders into.
            g.fill(x + 6, y + 22, x + 170, y + 116, VIEW_FILL);
            for (int i = 0; i < NET_COLS * NET_ROWS; i++) {
                slot(g, x + NET_X + (i % NET_COLS) * 18, y + NET_Y + (i / NET_COLS) * 18);
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 8 + col * 18, y + 172 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + 8 + col * 18, y + 232);
        }
    }

    private static void slot(final GuiGraphics g, final int x, final int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        g.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, LABEL, false);

        final int tab = menu.activeTab();
        if (tab == PersonalComputerMenu.TAB_LOCAL) {
            g.drawString(font, "Board", 8, 22, LABEL, false);
            g.drawString(font, "PSU", 8, 54, LABEL, false);
            g.drawString(font, "CPU", 44, 22, LABEL, false);
            g.drawString(font, "RAM", 44, 54, LABEL, false);
            g.drawString(font, "GPU", 44, 86, LABEL, false);
            g.drawString(font, "Disk", 8, 86, LABEL, false);

            final String status;
            final int color;
            if (!menu.buildValid()) {
                status = "OFFLINE";
                color = 0xFFA53D3D;
            } else if (menu.isRunning()) {
                status = "POWERED";
                color = 0xFF3DA53D;
            } else {
                status = "READY";
                color = 0xFFB0902A;
            }
            g.drawString(font, status, INFO_X, 28, color, false);
            g.drawString(font, menu.capacity() + " it/t", INFO_X, 40, LABEL, false);
            g.drawString(font, menu.ramBuffer() + " buf", INFO_X, 52, LABEL, false);
            final boolean net = menu.isOnNetwork();
            g.drawString(font, net ? "Net: linked" : "Net: --", INFO_X, 64, net ? 0xFF3DA53D : LABEL, false);
        } else if (tab == PersonalComputerMenu.TAB_STORAGE) {
            g.drawString(font, "Local Storage", 8, 20, LABEL, false);
            if (menu.usableStorageSlots() == 0) {
                g.drawString(font, "No disk installed.", 8, 40, 0xFFA53D3D, false);
                g.drawString(font, "Add a disk on the Local tab.", 8, 52, LABEL, false);
            }
        } else {
            // Status and hint live BELOW the item grid, never over it.
            final String status;
            final int color;
            if (!menu.isOnNetwork()) {
                status = "Not connected";
                color = 0xFFA53D3D;
            } else if (menu.networkServerCount() == 0) {
                status = "No servers on network";
                color = 0xFFB0902A;
            } else {
                final int n = menu.networkServerCount();
                status = n + (n == 1 ? " server connected" : " servers connected");
                color = 0xFF3DA53D;
            }
            g.drawString(font, status, 8, 119, color, false);
            g.drawString(font, "Click: take   ·   Hold item + click: store", 8, 130, LABEL, false);
        }
    }

    private ItemStack renderNetworkItems(final GuiGraphics g, final int mouseX, final int mouseY) {
        final List<NetworkItemEntry> items = menu.networkItems();
        final int totalRows = (items.size() + NET_COLS - 1) / NET_COLS;
        networkScroll = networkScroll.withTotalItems(totalRows);
        final int firstItem = networkScroll.firstVisibleIndex() * NET_COLS;
        final int shown = Math.min(items.size() - firstItem, NET_COLS * NET_ROWS);

        ItemStack hovered = null;
        for (int i = 0; i < shown; i++) {
            final NetworkItemEntry entry = items.get(firstItem + i);
            final int px = leftPos + NET_X + (i % NET_COLS) * 18;
            final int py = topPos + NET_Y + (i / NET_COLS) * 18;
            g.renderItem(entry.icon(), px, py);
            // renderItemDecorations draws the count text ON TOP of the icon (item z-layer).
            g.renderItemDecorations(font, entry.icon(), px, py, compactCount(entry.total()));
            if (mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16) {
                hovered = entry.icon();
            }
        }
        renderScrollbar(g);
        return hovered;
    }

    private void renderScrollbar(final GuiGraphics g) {
        if (!networkScroll.isScrollable()) {
            return;
        }
        final int trackX = leftPos + 162;
        final int trackY = topPos + NET_Y;
        final int trackH = NET_ROWS * 18 - 2;
        g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0xFF555555);
        final int thumbH = Math.max(8, (int) (networkScroll.thumbSize() * trackH));
        final int thumbY = trackY + (int) (networkScroll.thumbPosition() * (trackH - thumbH));
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFB0B0B0);
    }

    private static String compactCount(final long total) {
        if (total < 1_000L) {
            return Long.toString(total);
        }
        if (total < 1_000_000L) {
            return String.format("%.1fk", total / 1_000.0);
        }
        return String.format("%.1fM", total / 1_000_000.0);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        // Storage-terminal interaction on the Network tab: holding an item and
        if (menu.activeTab() == PersonalComputerMenu.TAB_NETWORK && (button == 0 || button == 1)) {
            final boolean inPanel = mouseX >= leftPos + 6 && mouseX < leftPos + 170
                    && mouseY >= topPos + 22 && mouseY < topPos + 116;
            if (!menu.getCarried().isEmpty()) {
                if (inPanel) {
                    PacketDistributor.sendToServer(new NetworkInsertPayload(menu.pcPos(), button == 0));
                    return true;
                }
            } else {
                final List<NetworkItemEntry> items = menu.networkItems();
                final int firstItem = networkScroll.firstVisibleIndex() * NET_COLS;
                final int shown = Math.min(items.size() - firstItem, NET_COLS * NET_ROWS);
                for (int i = 0; i < shown; i++) {
                    final int px = leftPos + NET_X + (i % NET_COLS) * 18;
                    final int py = topPos + NET_Y + (i / NET_COLS) * 18;
                    if (mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16) {
                        final long quantity = button == 0 ? 64L : 1L;
                        PacketDistributor.sendToServer(new NetworkSelectPayload(
                                menu.pcPos(), items.get(firstItem + i).icon().getItem(), quantity));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY,
                                 final double scrollX, final double scrollY) {
        if (menu.activeTab() == PersonalComputerMenu.TAB_NETWORK && networkScroll.isScrollable()) {
            final boolean inPanel = mouseX >= leftPos + 6 && mouseX < leftPos + 170
                    && mouseY >= topPos + 22 && mouseY < topPos + 116;
            if (inPanel && scrollY != 0) {
                networkScroll = networkScroll.scrolledBy(scrollY > 0 ? -1 : 1);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final int tab = menu.activeTab();
        final boolean local = tab == PersonalComputerMenu.TAB_LOCAL;
        final boolean network = tab == PersonalComputerMenu.TAB_NETWORK;
        localTab.active = !local;
        storageTab.active = tab != PersonalComputerMenu.TAB_STORAGE;
        networkTab.active = !network;

        final boolean auto = menu.isAutoStart();
        powerButton.visible = local;
        autoButton.visible = local;
        powerButton.active = local && !auto;
        powerButton.setMessage(Component.literal(
                auto ? "Auto" : (menu.isRunning() ? "Turn Off" : "Turn On")));
        autoButton.setMessage(Component.literal("Auto: " + (auto ? "ON" : "OFF")));

        super.render(g, mouseX, mouseY, partialTick);
        if (network) {
            final ItemStack hovered = renderNetworkItems(g, mouseX, mouseY);
            renderTooltip(g, mouseX, mouseY); // player-inventory slot tooltips
            if (hovered != null) {
                g.renderTooltip(font, hovered, mouseX, mouseY); // network item tooltip, on top
            }
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }
}
