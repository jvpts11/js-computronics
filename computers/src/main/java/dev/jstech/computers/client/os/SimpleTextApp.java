/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A minimal placeholder desktop app: a titled window showing a single line of body text. Used for
 * desktop programs whose full implementation lands later.
 */
public final class SimpleTextApp implements IDesktopApp {

    private final String title;
    private final Label body;
    private OsSkin skin = OsSkin.fallback();

    public SimpleTextApp(final String title, final String body) {
        this.title = title;
        this.body = new Label(body);
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int defaultWidth() {
        return 220;
    }

    @Override
    public int defaultHeight() {
        return 110;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        body.setBounds(x, y, width, 8);
        body.render(g, new UiContext(skin, font, mouseX, mouseY, partialTick));
    }
}
