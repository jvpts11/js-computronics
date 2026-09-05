/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A modal confirm/cancel dialog ("Confirm launch?", "Really delete?").
 */
public final class ModalDialog extends Screen {

    private final Screen parent;
    private final Component message;
    private final Consumer<Boolean> onResult;

    public ModalDialog(
            final Screen parent,
            final Component title,
            final Component message,
            final Consumer<Boolean> onResult) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.onResult = onResult;
    }

    @Override
    protected void init() {
        final int centerX = this.width / 2;
        final int buttonsY = this.height / 2 + 20;

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.jsc.confirm"),
                        b -> resolve(true))
                .bounds(centerX - 105, buttonsY, 100, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.jsc.cancel"),
                        b -> resolve(false))
                .bounds(centerX + 5, buttonsY, 100, 20)
                .build());
    }

    private void resolve(final boolean confirmed) {
        onResult.accept(confirmed);
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(
            final GuiGraphics graphics,
            final int mouseX,
            final int mouseY,
            final float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        // Dim the parent further with a translucent overlay.
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
        graphics.drawCenteredString(
                this.font, this.title, this.width / 2, this.height / 2 - 30, 0xFFFFFFFF);
        graphics.drawCenteredString(
                this.font, this.message, this.width / 2, this.height / 2 - 10, 0xFFBBBBBB);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        // Treat closing (Esc) as cancel.
        resolve(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}