/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.clienttest;

import com.mojang.blaze3d.platform.NativeImage;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The driver a client test builds its sequence with, and the toolbox its steps call while running.
 *
 * <p>A test method receives the context, queues steps ({@link #then}, {@link #thenServer},
 * {@link #thenWaitUntil}, ...) and returns; {@link ClientTestRunner} then executes the steps on the client
 * thread, one tick at a time. Everything the synthetic player does goes through the real client paths:
 * a right-click is {@code gameMode.useItemOn} (packet to the integrated server, which opens the menu, which
 * the client turns into a screen); screen input is the screen's own {@code mouseClicked} / {@code keyPressed}.
 *
 * <p>Positions are relative to the test's own area, following the GameTest convention (y = 2 stands on the
 * ground); {@link #abs(BlockPos)} maps them to the world.
 */
public final class ClientTestContext {

    /** One unit of work; returns true once it has finished (a one-shot step finishes on its first tick). */
    @FunctionalInterface
    public interface IStep {
        boolean tick();
    }

    record Queued(int delay, String label, IStep step) {
    }

    private final Minecraft mc;
    private final String testName;
    private final BlockPos origin;
    private final List<Queued> queue = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();

    ClientTestContext(final Minecraft mc, final String testName, final BlockPos origin) {
        this.mc = mc;
        this.testName = testName;
        this.origin = origin;
    }

    List<Queued> steps() {
        return queue;
    }

    List<String> screenshotsTaken() {
        return screenshots;
    }

    // Sequence building

    /** Runs {@code action} once, {@code delayTicks} after the previous step finished. */
    public ClientTestContext then(final int delayTicks, final Runnable action) {
        queue.add(new Queued(delayTicks, "then", () -> {
            action.run();
            return true;
        }));
        return this;
    }

    /** Runs {@code action} on the integrated server's thread and waits for it to complete. */
    public ClientTestContext thenServer(final int delayTicks, final Consumer<ServerLevel> action) {
        queue.add(new Queued(delayTicks, "server", new IStep() {
            private CompletableFuture<Void> pending;

            @Override
            public boolean tick() {
                if (pending == null) {
                    final MinecraftServer server = server();
                    pending = server.submit(() -> action.accept(serverLevel()));
                }
                if (pending.isCompletedExceptionally()) {
                    try {
                        pending.join();
                    } catch (final CompletionException e) {
                        // Surface the server-side failure itself, not the wrapper.
                        if (e.getCause() instanceof RuntimeException cause) {
                            throw cause;
                        }
                        throw new ClientTestFailure("server step failed", e.getCause());
                    }
                }
                return pending.isDone();
            }
        }));
        return this;
    }

    /** Builds a scenario on the server with the shared fixtures, at this test's origin. */
    public ClientTestContext thenBuild(final int delayTicks, final Consumer<TestWorldBuilder> build) {
        return thenServer(delayTicks, level -> build.accept(TestWorldBuilder.at(level, origin)));
    }

    /** Polls {@code condition} every tick; fails the test if it is still false after {@code maxTicks}. */
    public ClientTestContext thenWaitUntil(final BooleanSupplier condition, final int maxTicks, final String what) {
        return thenWaitUntil(condition, maxTicks, what, () -> "");
    }

    /**
     * As {@link #thenWaitUntil(BooleanSupplier, int, String)}, with {@code diagnostics} evaluated only on
     * timeout and appended to the failure, for the state that explains why the wait never ended.
     */
    public ClientTestContext thenWaitUntil(final BooleanSupplier condition, final int maxTicks, final String what,
                                           final java.util.function.Supplier<String> diagnostics) {
        queue.add(new Queued(0, "wait:" + what, new IStep() {
            private int waited;

            @Override
            public boolean tick() {
                if (condition.getAsBoolean()) {
                    return true;
                }
                if (++waited > maxTicks) {
                    String extra;
                    try {
                        extra = diagnostics.get();
                    } catch (final RuntimeException e) {
                        extra = "(diagnostics failed: " + e + ")";
                    }
                    throw new ClientTestFailure("timed out after " + maxTicks + " ticks waiting for " + what
                            + (extra.isEmpty() ? "" : " - " + extra));
                }
                return false;
            }
        }));
        return this;
    }

    /**
     * Polls {@code condition} ON THE SERVER THREAD (one probe in flight at a time) until it is true; fails after
     * {@code maxTicks} with {@code diagnostics} (also evaluated on the server). Use this for anything read from
     * the server level: {@code Level.getBlockEntity} and friends return null when called from another thread.
     */
    public ClientTestContext thenWaitUntilServer(final java.util.function.Predicate<ServerLevel> condition,
                                                 final int maxTicks, final String what,
                                                 final java.util.function.Function<ServerLevel, String> diagnostics) {
        queue.add(new Queued(0, "waitServer:" + what, new IStep() {
            private int waited;
            private CompletableFuture<Boolean> probe;

            @Override
            public boolean tick() {
                if (probe == null) {
                    final MinecraftServer server = server();
                    probe = server.submit(() -> condition.test(server.overworld()));
                }
                if (probe.isDone()) {
                    final boolean met;
                    try {
                        met = probe.join();
                    } catch (final CompletionException e) {
                        throw e.getCause() instanceof RuntimeException cause ? cause
                                : new ClientTestFailure("server probe failed", e.getCause());
                    }
                    probe = null;
                    if (met) {
                        return true;
                    }
                }
                if (++waited > maxTicks) {
                    String extra;
                    try {
                        final MinecraftServer server = server();
                        extra = server.submit(() -> diagnostics.apply(server.overworld())).join();
                    } catch (final RuntimeException e) {
                        extra = "(diagnostics failed: " + e + ")";
                    }
                    throw new ClientTestFailure("timed out after " + maxTicks + " ticks waiting for " + what
                            + (extra.isEmpty() ? "" : " - " + extra));
                }
                return false;
            }
        }));
        return this;
    }

    /** Waits until the open screen is a {@code type}. */
    public <T extends Screen> ClientTestContext thenAwaitScreen(final Class<T> type, final int maxTicks) {
        return thenWaitUntil(() -> type.isInstance(mc.screen), maxTicks, "screen " + type.getSimpleName());
    }

    /** Waits until no screen is open. */
    public ClientTestContext thenAwaitNoScreen(final int maxTicks) {
        return thenWaitUntil(() -> mc.screen == null, maxTicks, "no screen");
    }

    /**
     * Saves and leaves the world, reopens it from disk and waits until the player is back in, a real reload,
     * so whatever the test checks afterwards went through NBT and the level save. Any open screen is closed.
     */
    public ClientTestContext thenSaveAndReload(final int delayTicks) {
        queue.add(new Queued(delayTicks, "saveAndReload", new IStep() {
            private int phase;
            private int waited;

            @Override
            public boolean tick() {
                waited++;
                switch (phase) {
                    case 0 -> {
                        if (mc.level == null) {
                            throw new ClientTestFailure("no world to reload");
                        }
                        mc.level.disconnect();
                        mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen());
                        phase = 1;
                        waited = 0;
                    }
                    case 1 -> {
                        if (mc.level == null && mc.getSingleplayerServer() == null) {
                            mc.createWorldOpenFlows().openWorld(ClientTestRunner.WORLD_NAME,
                                    () -> { throw new ClientTestFailure("the test world could not be reopened"); });
                            phase = 2;
                            waited = 0;
                        } else if (waited > 600) {
                            throw new ClientTestFailure("leaving the world took more than 600 ticks");
                        }
                    }
                    case 2 -> {
                        final MinecraftServer server = mc.getSingleplayerServer();
                        if (mc.level != null && mc.player != null && mc.screen == null && server != null
                                && server.isReady() && mc.player.tickCount > 20) {
                            return true;
                        }
                        if (waited > 1200) {
                            throw new ClientTestFailure("reopening the world took more than 1200 ticks");
                        }
                    }
                    default -> throw new IllegalStateException("bad reload phase " + phase);
                }
                return false;
            }
        }));
        return this;
    }

    /** Teleports the synthetic player to {@code relative} (its feet), looking toward {@code facing}. */
    public ClientTestContext thenTeleport(final int delayTicks, final BlockPos relative, final Direction facing) {
        return thenServer(delayTicks, level -> teleport(relative, facing));
    }

    /** Right-clicks the block at {@code relative} with the empty hand, the way the player opens a GUI. */
    public ClientTestContext thenRightClick(final int delayTicks, final BlockPos relative) {
        return then(delayTicks, () -> rightClick(relative));
    }

    /** Takes a screenshot named after the test and {@code label}. */
    public ClientTestContext thenScreenshot(final int delayTicks, final String label) {
        return then(delayTicks, () -> screenshot(label));
    }

    /** Asserts {@code condition} after {@code delayTicks}. */
    public ClientTestContext thenAssert(final int delayTicks, final BooleanSupplier condition, final String message) {
        return then(delayTicks, () -> assertTrue(condition.getAsBoolean(), message));
    }

    // Toolbox for steps

    public Minecraft mc() {
        return mc;
    }

    public BlockPos origin() {
        return origin;
    }

    public BlockPos abs(final BlockPos relative) {
        return origin.offset(relative);
    }

    public MinecraftServer server() {
        final MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new ClientTestFailure("no integrated server");
        }
        return server;
    }

    public ServerLevel serverLevel() {
        return server().overworld();
    }

    public ServerPlayer serverPlayer() {
        final List<ServerPlayer> players = server().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new ClientTestFailure("no player on the integrated server");
        }
        return players.get(0);
    }

    public LocalPlayer player() {
        if (mc.player == null) {
            throw new ClientTestFailure("no client player");
        }
        return mc.player;
    }

    /** The open screen as {@code type}, or a failure naming what is open instead. */
    public <T extends Screen> T screen(final Class<T> type) {
        if (type.isInstance(mc.screen)) {
            return type.cast(mc.screen);
        }
        throw new ClientTestFailure("expected " + type.getSimpleName() + " to be open but found "
                + (mc.screen == null ? "no screen" : mc.screen.getClass().getSimpleName()));
    }

    /** Server-side: moves the player to {@code relative} looking toward {@code facing}. Call from a server step. */
    public void teleport(final BlockPos relative, final Direction facing) {
        final BlockPos at = abs(relative);
        final ServerPlayer player = serverPlayer();
        player.teleportTo(serverLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5,
                facing.toYRot(), 0.0F);
    }

    /** Server-side: puts {@code stack} in the player's main hand. Call from a server step. */
    public void hold(final ItemStack stack) {
        serverPlayer().setItemInHand(InteractionHand.MAIN_HAND, stack);
    }

    /** Server-side: puts {@code stack} in inventory slot {@code slot} (0-8 = hotbar). Call from a server step. */
    public void give(final int slot, final ItemStack stack) {
        serverPlayer().getInventory().setItem(slot, stack);
    }

    /** Client-side: selects hotbar slot {@code slot}; the next use sends the selection to the server first. */
    public void selectHotbar(final int slot) {
        player().getInventory().selected = slot;
    }

    /**
     * Client-side: uses the held item on the block at {@code relative}, hitting the face that looks at the
     * player, the same path a real click takes, so the server-side reach and hit checks apply.
     */
    public void rightClick(final BlockPos relative) {
        final BlockPos target = abs(relative);
        final Vec3 center = Vec3.atCenterOf(target);
        rightClick(relative, Direction.getNearest(player().getEyePosition().subtract(center)));
    }

    /** Client-side: uses the held item on the given face of the block at {@code relative}. */
    public void rightClick(final BlockPos relative, final Direction face) {
        final BlockPos target = abs(relative);
        final Vec3 center = Vec3.atCenterOf(target);
        final Vec3 location = center.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
        final BlockHitResult hit = new BlockHitResult(location, face, target, false);
        Objects.requireNonNull(mc.gameMode, "no game mode").useItemOn(player(), InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Client-side: places the held block item at {@code relative} by clicking the top face of the block
     * below it, exactly as the player builds on the ground. Orientation follows the player's facing, so
     * teleport with the right {@code facing} first. Hold sneak (see {@link #sneak}) when the block below
     * has a GUI, as a player would.
     */
    public void place(final BlockPos relative) {
        placeAgainst(relative, Direction.UP);
    }

    /**
     * Client-side: places the held block item at {@code relative} by clicking the neighbour on the side
     * {@code face} points away from (e.g. {@code UP} clicks the block below, {@code NORTH} the block south).
     */
    public void placeAgainst(final BlockPos relative, final Direction face) {
        rightClick(relative.relative(face.getOpposite()), face);
    }

    /**
     * Client-side: presses or releases the sneak key. The key state reaches the server on the next tick,
     * so wait a tick before the click that depends on it. Sneaking is how a player places a block on, or
     * ejects media from, a block that would otherwise open its GUI.
     */
    public void sneak(final boolean down) {
        mc.options.keyShift.setDown(down);
    }

    /** Queues sneak → place → release for {@code relative} (three ticks, the way a player does it). */
    public ClientTestContext thenPlace(final int delayTicks, final BlockPos relative) {
        return thenPlaceAgainst(delayTicks, relative, Direction.UP);
    }

    public ClientTestContext thenPlaceAgainst(final int delayTicks, final BlockPos relative, final Direction face) {
        then(delayTicks, () -> sneak(true));
        then(2, () -> placeAgainst(relative, face));
        return then(1, () -> sneak(false));
    }

    /** Queues sneak → right-click {@code face} of the block at {@code relative} → release. */
    public ClientTestContext thenSneakClick(final int delayTicks, final BlockPos relative, final Direction face) {
        then(delayTicks, () -> sneak(true));
        then(2, () -> rightClick(relative, face));
        return then(1, () -> sneak(false));
    }

    /** Queues a server step that fills hotbar slots 0..n-1 with {@code stacks} and empties the rest. */
    public ClientTestContext thenGive(final int delayTicks, final ItemStack... stacks) {
        return thenServer(delayTicks, level -> {
            for (int i = 0; i < 9; i++) {
                give(i, i < stacks.length ? stacks[i].copy() : ItemStack.EMPTY);
            }
        });
    }

    /** Presses and releases the left mouse button at screen coordinates ({@code x}, {@code y}). */
    public void click(final double x, final double y) {
        final Screen screen = screen(Screen.class);
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    /** Presses and releases the right mouse button at screen coordinates ({@code x}, {@code y}). */
    public void rightClick(final double x, final double y) {
        final Screen screen = screen(Screen.class);
        screen.mouseClicked(x, y, 1);
        screen.mouseReleased(x, y, 1);
    }

    /** Clicks a desktop-relative point (window and app geometry, as the desktop apps report it). */
    public void clickDesktop(final int[] point) {
        final dev.jstech.computronics.client.os.DesktopScreen desktop =
                screen(dev.jstech.computronics.client.os.DesktopScreen.class);
        click(desktop.desktopX() + point[0] + 0.5, desktop.desktopY() + point[1] + 0.5);
    }

    /** Clicks at ({@code x}, {@code y}) relative to the open container screen's top-left corner. */
    public void clickGui(final int x, final int y) {
        final AbstractContainerScreen<?> screen = screen(AbstractContainerScreen.class);
        click(screen.getGuiLeft() + x + 0.5, screen.getGuiTop() + y + 0.5);
    }

    /** Scrolls the wheel by {@code delta} notches at ({@code x}, {@code y}) relative to the container screen. */
    public void scrollGui(final int x, final int y, final double delta) {
        final AbstractContainerScreen<?> screen = screen(AbstractContainerScreen.class);
        screen.mouseScrolled(screen.getGuiLeft() + x + 0.5, screen.getGuiTop() + y + 0.5, 0.0, delta);
    }

    /** Drags the left button from ({@code x1}, {@code y1}) to ({@code x2}, {@code y2}), container-relative. */
    public void dragGui(final int x1, final int y1, final int x2, final int y2) {
        final AbstractContainerScreen<?> screen = screen(AbstractContainerScreen.class);
        final double sx = screen.getGuiLeft() + x1 + 0.5;
        final double sy = screen.getGuiTop() + y1 + 0.5;
        final double ex = screen.getGuiLeft() + x2 + 0.5;
        final double ey = screen.getGuiTop() + y2 + 0.5;
        screen.mouseClicked(sx, sy, 0);
        screen.mouseDragged(ex, ey, 0, ex - sx, ey - sy);
        screen.mouseReleased(ex, ey, 0);
    }

    /** Presses and releases a GLFW key on the open screen. */
    public void key(final int keyCode) {
        key(keyCode, 0);
    }

    /**
     * The same, with modifiers held: {@code GLFW_MOD_CONTROL} and friends, combined with {@code |}.
     *
     * <p>The shortcuts a program has are keys held with something, so a test that never holds anything
     * cannot reach them at all.
     */
    public void key(final int keyCode, final int modifiers) {
        final Screen screen = screen(Screen.class);
        screen.keyPressed(keyCode, 0, modifiers);
        screen.keyReleased(keyCode, 0, modifiers);
    }

    /** Types {@code text} into the open screen, character by character. */
    public void type(final String text) {
        final Screen screen = screen(Screen.class);
        for (final char c : text.toCharArray()) {
            screen.charTyped(c, 0);
        }
    }

    /** Saves the current frame as {@code ct-<test>-<label>.png} under the game's screenshots folder. */
    public String screenshot(final String label) {
        final String name = "ct-" + testName.replace('.', '_') + "-" + label + ".png";
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> { });
        screenshots.add(name);
        return name;
    }

    /**
     * The colour currently on screen at logical GUI coordinates ({@code x}, {@code y}), as ARGB. Reads the
     * last rendered frame, so it proves what the player actually sees (a popup drawn above the slots, ...).
     */
    public int pixel(final int x, final int y) {
        final double scale = mc.getWindow().getGuiScale();
        final int px = (int) Math.min(mc.getWindow().getWidth() - 1, x * scale);
        final int py = (int) Math.min(mc.getWindow().getHeight() - 1, y * scale);
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            final int abgr = image.getPixelRGBA(px, py);
            final int r = abgr & 0xFF;
            final int g = (abgr >> 8) & 0xFF;
            final int b = (abgr >> 16) & 0xFF;
            final int a = (abgr >>> 24) & 0xFF;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    // Assertions

    public void assertTrue(final boolean condition, final String message) {
        if (!condition) {
            throw new ClientTestFailure(message);
        }
    }

    public void assertEquals(final Object expected, final Object actual, final String message) {
        if (!Objects.equals(expected, actual)) {
            throw new ClientTestFailure(message + " (expected " + expected + ", got " + actual + ")");
        }
    }

    public void fail(final String message) {
        throw new ClientTestFailure(message);
    }
}
