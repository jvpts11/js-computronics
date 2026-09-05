/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import java.util.function.BooleanSupplier;

/**
 * A player-inventory slot the Frames desktop shows inside a Network Interactor window. The slot follows the
 * focused window: the screen rewrites its {@code x}/{@code y} every tick to the window's inventory zone, and
 * gates it through {@link #isActive()} so the slot only renders and accepts clicks while that window is the
 * front, non-minimized one. The vanilla container handles the actual item movement (cursor, drag, shift-click)
 * once the slot is active and positioned. The focus state is cached per tick by the desktop manager, so every
 * {@code isActive()} call within a frame reads the same value and hit-testing stays deterministic.
 *
 * <p>A per-slot {@code visible} flag also lets a row that is scrolled out of the window's viewport go inactive,
 * so when the window is shrunk small enough to scroll the inventory, the rows above or below the viewport do
 * not render or accept clicks past the window's edges.
 */
public final class NetworkInteractorSlot extends Slot {

    private final BooleanSupplier active;
    private final boolean visible;

    public NetworkInteractorSlot(final Container container, final int index, final int x, final int y,
                                 final BooleanSupplier active, final boolean visible) {
        super(container, index, x, y);
        this.active = active;
        this.visible = visible;
    }

    @Override
    public boolean isActive() {
        return visible && active.getAsBoolean();
    }
}
