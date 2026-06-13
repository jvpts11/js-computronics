/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.CommandOutputPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.ConsoleInitPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestConsoleInitPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RunCommandPayload;
import dev.jsc.jscomputronics.module.computing.program.cli.CliStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Command Prompt: a full CLI over the computer the Monitor is bound to. A typed line is echoed, sent to the server to run through the shell, and the styled result is appended to the scrollback. Up/Down walk the input history; the mouse wheel scrolls back through output. The same OS skin as the rest of the computing GUIs, square corners and all.
 */
public class CommandPromptScreen extends AbstractComputerScreen<CommandPromptMenu> {

    private static final int CONSOLE = 0xFF070A0E;
    private static final int MAX_SCROLLBACK = 512;
    private static final float TEXT_SCALE = 0.85f;
    private static final int LINE_H = 9;

    private final Deque<Line> scrollback = new ArrayDeque<>();
    private final List<String> history = new ArrayList<>();
    private final List<String> commandNames = new ArrayList<>();
    private final Map<String, String> commandUsage = new LinkedHashMap<>();
    private int historyIndex = -1;
    private int scrollOffset;
    private int completionCycle;
    private boolean programmaticEdit;

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
        // Start the input box just past the "jsc> " prompt so the caret never sits on top of it.
        final int promptW = font.width("jsc> ");
        input = new EditBox(font, leftPos + 10 + promptW, topPos + imageHeight - 18,
                imageWidth - 18 - promptW, 11, Component.literal("command"));
        input.setBordered(false);
        input.setMaxLength(RunCommandPayload.MAX_LEN);
        input.setTextColor(JscOsTheme.text());
        input.setFocused(true);
        // A real edit (typing/backspace) restarts Tab cycling; our own programmatic setValue does not.
        input.setResponder(s -> {
            if (!programmaticEdit) {
                completionCycle = 0;
            }
        });
        setInitialFocus(input);
        addRenderableWidget(input);
        if (scrollback.isEmpty()) {
            push("J's Computronics Shell v1.0", CliStyle.ACCENT);
            push("type 'help' for commands, TAB to complete", CliStyle.DIM);
            push("", CliStyle.PLAIN);
        }
        // Ask the server for this computer's saved history and the command list (for completion).
        PacketDistributor.sendToServer(new RequestConsoleInitPayload(menu.hostPos()));
    }

    // --- output ----------------------------------------------------------------------------------

    /** Routes a server output payload to the open Command Prompt, if one is showing. */
    public static void accept(final CommandOutputPayload payload) {
        if (Minecraft.getInstance().screen instanceof CommandPromptScreen screen) {
            screen.apply(payload);
        }
    }

    /** Seeds the open Command Prompt with the computer's saved history and the command list. */
    public static void acceptInit(final ConsoleInitPayload payload) {
        if (Minecraft.getInstance().screen instanceof CommandPromptScreen screen) {
            screen.applyInit(payload);
        }
    }

    private void applyInit(final ConsoleInitPayload payload) {
        history.clear();
        history.addAll(payload.history());
        historyIndex = -1;
        commandNames.clear();
        commandUsage.clear();
        for (final ConsoleInitPayload.WireCommand command : payload.commands()) {
            commandNames.add(command.name());
            commandUsage.put(command.name(), command.usage());
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
        g.fill(x + 6, top, x + imageWidth - 6, top + 1, JscOsTheme.line());
        // Input strip.
        g.fill(x + 6, y + imageHeight - 20, x + imageWidth - 6, y + imageHeight - 8, JscOsTheme.panel());
        g.fill(x + 6, y + imageHeight - 20, x + imageWidth - 6, y + imageHeight - 19, JscOsTheme.line());
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "COMMAND PROMPT", 12, 11, JscOsTheme.text());
        // PROGRAM chip-ish marker + host on the right of the header.
        JscOsTheme.textRight(g, font, "PROGRAM", imageWidth - 10, 11, JscOsTheme.accent());

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
            JscOsTheme.textSRight(g, font, "scrolled +" + scrollOffset, imageWidth - 10, bottom - 7, JscOsTheme.dim());
        }

        // Prompt glyph before the input box.
        JscOsTheme.text(g, font, "jsc>", 10, imageHeight - 18, JscOsTheme.accent());

        // Usage hint: once the verb is recognised, show how it is used, dimmed on the right.
        final String typed = input == null ? "" : input.getValue().trim();
        final int space = typed.indexOf(' ');
        final String verb = (space < 0 ? typed : typed.substring(0, space)).toLowerCase(Locale.ROOT);
        final String usage = commandUsage.get(verb);
        if (usage != null && !usage.isEmpty()) {
            JscOsTheme.textSRight(g, font, verb + " " + usage, imageWidth - 10, imageHeight - 17, JscOsTheme.dim());
        }

        JscOsTheme.textS(g, font, "ENTER run    UP/DOWN history    wheel scroll    ESC close",
                10, imageHeight - 7, JscOsTheme.dim());
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
            case PROMPT, ACCENT, HEADER -> JscOsTheme.accent();
            case OK -> JscOsTheme.green();
            case ERROR -> JscOsTheme.red();
            case WARN -> JscOsTheme.amber();
            case INFO -> JscOsTheme.accent2();
            case DIM -> JscOsTheme.dim();
            default -> JscOsTheme.text();
        };
    }

    // --- input -----------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        if (key == 257 || key == 335) { // Enter / numpad Enter
            submit();
            return true;
        }
        if (key == 258) { // Tab — complete the command word
            complete();
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

    /**
     * Completes the command word the player is typing against the known command names, cycling
     * through the matches on repeated Tab. Only the first word (the verb) is completed for now.
     */
    private void complete() {
        final String text = input.getValue();
        if (text.contains(" ") || text.isEmpty()) {
            return; // arguments are not completed yet; only the leading command word
        }
        final String prefix = text.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String name : commandNames) {
            if (name.startsWith(prefix)) {
                matches.add(name);
            }
        }
        if (matches.isEmpty()) {
            return;
        }
        final String pick = matches.get(completionCycle % matches.size());
        completionCycle++;
        programmaticEdit = true;
        input.setValue(matches.size() == 1 ? pick + " " : pick);
        input.moveCursorToEnd(false);
        programmaticEdit = false;
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

    @Override
    protected HardwareEra screenEra() {
        return menu.hardwareEra();
    }

    /** One scrollback line: its text and the style that colours it. */
    private record Line(String text, CliStyle style) {
    }
}
