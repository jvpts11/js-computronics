/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.CommandOutputPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RunCommandPayload;
import dev.jsc.jscomputronics.module.computing.program.cli.CliStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The Command Prompt: a full CLI over the computer the Monitor is bound to. A typed line is echoed, sent to the server to run through the shell, and the styled result is appended to the scrollback. Up/Down walk the input history; the mouse wheel scrolls back through output. The same OS skin as the rest of the computing GUIs, square corners and all.
 */
public class CommandPromptScreen extends AbstractContainerScreen<CommandPromptMenu> {

    private static final int CONSOLE = 0xFF070A0E;
    private static final int MAX_SCROLLBACK = 512;
    private static final float TEXT_SCALE = 0.85f;
    private static final int LINE_H = 9;

    private final Deque<Line> scrollback = new ArrayDeque<>();
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private int scrollOffset;

    private EditBox input;

    public CommandPromptScreen(final CommandPromptMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 178;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(font, leftPos + 28, topPos + imageHeight - 18, imageWidth - 36, 11,
                Component.literal("command"));
        input.setBordered(false);
        input.setMaxLength(RunCommandPayload.MAX_LEN);
        input.setTextColor(JscOsTheme.TEXT);
        input.setFocused(true);
        setInitialFocus(input);
        addRenderableWidget(input);
        if (scrollback.isEmpty()) {
            push("J's Computronics Shell v1.0", CliStyle.ACCENT);
            push("type 'help' for commands", CliStyle.DIM);
            push("", CliStyle.PLAIN);
        }
    }

    // --- output ----------------------------------------------------------------------------------

    /** Routes a server output payload to the open Command Prompt, if one is showing. */
    public static void accept(final CommandOutputPayload payload) {
        if (Minecraft.getInstance().screen instanceof CommandPromptScreen screen) {
            screen.apply(payload);
        }
    }

    private void apply(final CommandOutputPayload payload) {
        if (payload.clear()) {
            scrollback.clear();
        }
        for (final CommandOutputPayload.WireLine line : payload.lines()) {
            push(line.text(), styleOf(line.style()));
        }
        scrollOffset = 0;
    }

    private void push(final String text, final CliStyle style) {
        scrollback.addLast(new Line(text, style));
        while (scrollback.size() > MAX_SCROLLBACK) {
            scrollback.removeFirst();
        }
    }

    private void submit() {
        final String line = input.getValue().trim();
        input.setValue("");
        historyIndex = -1;
        push("jsc> " + line, CliStyle.PROMPT);
        if (line.isEmpty()) {
            return;
        }
        if (history.isEmpty() || !history.get(history.size() - 1).equals(line)) {
            history.add(line);
        }
        scrollOffset = 0;
        PacketDistributor.sendToServer(new RunCommandPayload(menu.monitorPos(), menu.hostPos(), line));
    }

    // --- rendering -------------------------------------------------------------------------------

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, imageWidth - 12);
        // The console panel.
        final int top = y + 26;
        final int bottom = y + imageHeight - 22;
        g.fill(x + 6, top, x + imageWidth - 6, bottom, CONSOLE);
        g.fill(x + 6, top, x + imageWidth - 6, top + 1, JscOsTheme.LINE);
        // Input strip.
        g.fill(x + 6, y + imageHeight - 20, x + imageWidth - 6, y + imageHeight - 8, JscOsTheme.PANEL);
        g.fill(x + 6, y + imageHeight - 20, x + imageWidth - 6, y + imageHeight - 19, JscOsTheme.LINE);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "COMMAND PROMPT", 12, 11, JscOsTheme.TEXT);
        // PROGRAM chip-ish marker + host on the right of the header.
        JscOsTheme.textRight(g, font, "PROGRAM", imageWidth - 10, 11, JscOsTheme.ACCENT);

        // Console scrollback, newest at the bottom, honoring the scroll offset.
        final int top = 27;
        final int bottom = imageHeight - 23;
        final int visible = (bottom - top) / LINE_H;
        final List<Line> all = new ArrayList<>(scrollback);
        final int total = all.size();
        final int end = Math.max(0, total - scrollOffset);
        final int start = Math.max(0, end - visible);
        int row = 0;
        for (int i = start; i < end; i++) {
            final Line line = all.get(i);
            drawSmall(g, line.text(), 10, top + row * LINE_H, colorOf(line.style()));
            row++;
        }
        if (scrollOffset > 0) {
            JscOsTheme.textSRight(g, font, "scrolled +" + scrollOffset, imageWidth - 10, bottom - 7, JscOsTheme.DIM);
        }

        // Prompt glyph before the input box.
        JscOsTheme.text(g, font, "jsc>", 10, imageHeight - 18, JscOsTheme.ACCENT);

        JscOsTheme.textS(g, font, "ENTER run    UP/DOWN history    wheel scroll    ESC close",
                10, imageHeight - 7, JscOsTheme.DIM);
    }

    private void drawSmall(final GuiGraphics g, final String text, final int x, final int y, final int color) {
        if (text.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static CliStyle styleOf(final int ordinal) {
        final CliStyle[] values = CliStyle.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CliStyle.PLAIN;
    }

    private static int colorOf(final CliStyle style) {
        return switch (style) {
            case PROMPT, ACCENT, HEADER -> JscOsTheme.ACCENT;
            case OK -> JscOsTheme.GREEN;
            case ERROR -> JscOsTheme.RED;
            case WARN -> JscOsTheme.AMBER;
            case INFO -> JscOsTheme.ACCENT2;
            case DIM -> JscOsTheme.DIM;
            default -> JscOsTheme.TEXT;
        };
    }

    // --- input -----------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        if (key == 257 || key == 335) { // Enter / numpad Enter
            submit();
            return true;
        }
        if (key == 265) { // Up — older history
            recallHistory(-1);
            return true;
        }
        if (key == 264) { // Down — newer history
            recallHistory(1);
            return true;
        }
        if (key == 256) { // Esc closes the prompt
            onClose();
            return true;
        }
        // Everything else (typing, backspace, arrows within the line) goes to the input box, so the
        // inventory key never reaches the screen and closes it mid-command.
        if (input != null) {
            input.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(final char c, final int mods) {
        return input != null && input.charTyped(c, mods);
    }

    private void recallHistory(final int direction) {
        if (history.isEmpty()) {
            return;
        }
        if (historyIndex == -1) {
            historyIndex = history.size();
        }
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
        if (historyIndex >= history.size()) {
            historyIndex = -1;
            input.setValue("");
        } else {
            input.setValue(history.get(historyIndex));
        }
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double dx, final double dy) {
        final int top = 27;
        final int bottom = imageHeight - 23;
        final int visible = (bottom - top) / LINE_H;
        final int maxScroll = Math.max(0, scrollback.size() - visible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (int) Math.signum(dy)));
        return true;
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** One scrollback line: its text and the style that colours it. */
    private record Line(String text, CliStyle style) {
    }
}
