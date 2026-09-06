/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Base for the computer-assembly screens, holding the header rename field they share. A computer is renamed in its own assembly GUI (never via an anvil), so each of these screens carried the same EditBox plus the same keyboard plumbing to keep typing in the field from leaking to the screen (and the inventory key from closing the GUI). That plumbing lives here once; the screen only positions the field and supplies its rename action. The Mainframe, which has no rename field, does not extend this.
 */
public abstract class AbstractAssemblyScreen<T extends AbstractContainerMenu> extends AbstractComputerScreen<T> {

    @Nullable
    protected EditBox nameBox;

    protected AbstractAssemblyScreen(final T menu, final Inventory playerInventory, final Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * Installs the header rename field at {@code (x, y)} relative to this screen's top-left. Call from {@code init()} after {@code super.init()}. The responder runs on every edit (typically to send a rename packet).
     */
    protected void setupNameBox(final int x, final int y, final int width, final int maxLength,
                                final Component hint, final String initialValue,
                                final Consumer<String> responder) {
        nameBox = new EditBox(font, leftPos + x, topPos + y, width, 11, Component.literal("Name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(maxLength);
        // The rename field sits on a dark header strip in every era, so its text is a fixed white — the
        // era's own text colour goes dark on the Legacy strip and the name becomes unreadable.
        nameBox.setTextColor(0xFFFFFFFF);
        nameBox.setHint(hint);
        nameBox.setValue(initialValue);
        nameBox.setResponder(responder);
        addRenderableWidget(nameBox);
    }

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        // While the name field has focus, route typing to it and never let a key (e.g. the inventory
        // key 'E') reach the screen and close the GUI. ESC just unfocuses the field.
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
    public boolean charTyped(final char c, final int mods) {
        if (nameBox != null && nameBox.isFocused()) {
            return nameBox.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }
}
