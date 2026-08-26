/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.gui.layout;

import dev.jsc.jscomputronics.common.gui.layout.GuiLayout;

/**
 * Pure layout model for the Datacenter Station screen: the two tile rows (section/servers/ops, then the
 * summed cpu/ram/storage), the balance bar, the unified item grid and the player inventory. It is the
 * single source of the inventory slot positions, which {@code DatacenterStationMenu} consumes. The MOVE
 * popup is a modal overlay drawn by the screen and is not modeled here.
 */
public final class DatacenterStationLayout {

    public static final int WIDTH = 230;
    public static final int HEIGHT = 252;

    public static final int TILE_Y = 24;
    public static final int TILE_H = 20;
    public static final int SECTION_X = 8;
    public static final int SECTION_W = 106;
    public static final int SERVERS_X = 118;
    public static final int SERVERS_W = 50;
    public static final int OPS_X = 172;
    public static final int OPS_W = 50;

    public static final int ROW2_Y = 46;
    public static final int CPU_X = 8;
    public static final int CPU_W = 66;
    public static final int RAM_X = 78;
    public static final int RAM_W = 66;
    public static final int STORAGE_X = 148;
    public static final int STORAGE_W = 74;

    public static final int BAL_X = 8;
    public static final int BAL_Y = 68;
    public static final int BAL_W = 122;
    public static final int BAL_H = 12;

    public static final int GRID_COLS = 9;
    public static final int GRID_ROWS = 4;
    public static final int GRID_X = 34;
    public static final int GRID_Y = 84;
    public static final int CELL = 18;

    public static final int INV_X = 34;
    public static final int INV_Y = 170;
    public static final int HOTBAR_Y = INV_Y + 58;

    private DatacenterStationLayout() {
    }

    public static GuiLayout layout() {
        final GuiLayout l = new GuiLayout(WIDTH, HEIGHT)
                .box("sectionTile", SECTION_X, TILE_Y, SECTION_W, TILE_H)
                .box("serversTile", SERVERS_X, TILE_Y, SERVERS_W, TILE_H)
                .box("opsTile", OPS_X, TILE_Y, OPS_W, TILE_H)
                .box("cpuTile", CPU_X, ROW2_Y, CPU_W, TILE_H)
                .box("ramTile", RAM_X, ROW2_Y, RAM_W, TILE_H)
                .box("storageTile", STORAGE_X, ROW2_Y, STORAGE_W, TILE_H)
                .box("balance", BAL_X, BAL_Y, BAL_W, BAL_H);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                l.box("grid_" + row + "_" + col, GRID_X + col * CELL, GRID_Y + row * CELL, CELL, CELL);
            }
        }
        l.playerInventory(INV_X, INV_Y);
        l.text("title", 12, 10, 18, 1.0f); // "DATACENTER STATION"
        return l;
    }
}
