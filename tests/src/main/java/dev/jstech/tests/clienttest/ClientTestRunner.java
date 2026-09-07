/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.clienttest;

import com.mojang.logging.LogUtils;
import dev.jstech.tests.JsTests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Drives the client tests when the game is launched with {@code -Djsc.clienttests=true}: waits for the
 * title screen, creates a fresh flat creative world, runs this shard's slice of {@link ClientTestSuite} one
 * test at a time on the client thread, writes the report and stops the game.
 *
 * <p>Each test owns a strip of the world 64 blocks from its neighbours (by its index in the whole suite, so
 * shards never overlap). The player is teleported there before the test's steps run.
 */
@EventBusSubscriber(modid = JsTests.MODID, value = Dist.CLIENT)
public final class ClientTestRunner {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("jsc.clienttests");
    private static final int SHARD = Integer.getInteger("jsc.clienttests.shard", 0);
    private static final int SHARDS = Math.max(1, Integer.getInteger("jsc.clienttests.shards", 1));
    /** Optional name substring: when set, only client tests whose name contains it (any case) run. */
    private static final String ONLY = System.getProperty("jsc.clienttests.only", "").trim();
    static final String WORLD_NAME = "jsc-clienttests";
    private static final int AREA_SPACING = 64;
    private static final int SETTLE_TICKS = 40;
    private static final int GUI_SCALE = 2;

    private static final ClientTestRunner INSTANCE = new ClientTestRunner();

    private enum State { BOOT, WAIT_LEVEL, PREPARE, NEXT_TEST, MOVE_TO_AREA, RUNNING, FINISH, DONE }

    private State state = State.BOOT;
    private int stateTicks;
    private final ClientTestReport report = new ClientTestReport();
    private List<ClientTestSuite.Entry> tests;
    private int nextTest;
    private int groundY;
    private BlockPos spawn;

    // The test in flight
    private ClientTestSuite.Entry current;
    private ClientTestContext context;
    private int stepIndex;
    private int stepDelayLeft;
    private int testTicks;
    private CompletableFuture<Void> pendingMove;

    private ClientTestRunner() {
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        if (ENABLED) {
            INSTANCE.tick(Minecraft.getInstance());
        }
    }

    private void tick(final Minecraft mc) {
        stateTicks++;
        try {
            switch (state) {
                case BOOT -> boot(mc);
                case WAIT_LEVEL -> waitLevel(mc);
                case PREPARE -> prepare(mc);
                case NEXT_TEST -> nextTest(mc);
                case MOVE_TO_AREA -> moveToArea(mc);
                case RUNNING -> running(mc);
                case FINISH -> finish(mc);
                case DONE -> { }
            }
        } catch (final RuntimeException e) {
            if (state == State.RUNNING && current != null) {
                failCurrent(mc, e);
            } else {
                LOGGER.error("[JSC-CT] harness failure in state {}", state, e);
                report.record(new ClientTestReport.Result("harness." + state, false,
                        String.valueOf(e), List.of(), stateTicks));
                enter(State.FINISH);
            }
        }
    }

    private void enter(final State next) {
        state = next;
        stateTicks = 0;
    }

    private void boot(final Minecraft mc) {
        /*
         * A shard's game directory is fresh, so the game would show the accessibility onboarding screen and
         * would pause whenever its window loses focus, and several shard windows run side by side, so at
         * most one of them is ever focused. Both are settled before anything else happens.
         */
        if (mc.options.pauseOnLostFocus || mc.options.onboardAccessibility) {
            mc.options.pauseOnLostFocus = false;
            mc.options.onboardAccessibility = false;
            mc.options.save();
        }
        if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose();
            return;
        }
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) {
            return;
        }
        tests = ClientTestSuite.shard(SHARD, SHARDS);
        if (!ONLY.isEmpty()) {
            final String needle = ONLY.toLowerCase(java.util.Locale.ROOT);
            tests = tests.stream()
                    .filter(t -> t.name().toLowerCase(java.util.Locale.ROOT).contains(needle))
                    .toList();
        }
        LOGGER.info("[JSC-CT] shard {}/{} runs {} tests", SHARD, SHARDS, tests.size());
        deleteOldWorld(mc);
        final GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, null);
        final LevelSettings settings = new LevelSettings(WORLD_NAME, GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        final WorldOptions options = new WorldOptions(0L, false, false);
        mc.createWorldOpenFlows().createFreshLevel(WORLD_NAME, settings, options,
                registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
        enter(State.WAIT_LEVEL);
    }

    private void deleteOldWorld(final Minecraft mc) {
        final Path folder = mc.getLevelSource().getBaseDir().resolve(WORLD_NAME);
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (final IOException e) {
                    throw new IllegalStateException("could not delete " + p, e);
                }
            });
        } catch (final IOException e) {
            throw new IllegalStateException("could not clear the old test world", e);
        }
    }

    private void waitLevel(final Minecraft mc) {
        final MinecraftServer server = mc.getSingleplayerServer();
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null); // the window lost focus before the option above took effect
        }
        if (mc.level == null || mc.player == null || mc.screen != null || server == null || !server.isReady()) {
            return;
        }
        if (stateTicks < SETTLE_TICKS) {
            return;
        }
        enter(State.PREPARE);
    }

    private void prepare(final Minecraft mc) {
        /*
         * Screens must not pause when a shard window loses focus (several shards run side by side), and a
         * fixed GUI scale keeps screen coordinates and screenshots identical from run to run.
         */
        mc.options.pauseOnLostFocus = false;
        mc.options.guiScale().set(GUI_SCALE);
        mc.resizeDisplay();
        final MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("integrated server vanished");
        }
        pendingMove = server.submit(() -> {
            final ServerLevel level = server.overworld();
            level.setDayTime(6000L);
            spawn = level.getSharedSpawnPos();
            groundY = level.getHeight(Heightmap.Types.WORLD_SURFACE, spawn.getX(), spawn.getZ());
        });
        nextTest = 0;
        enter(State.NEXT_TEST);
    }

    private void nextTest(final Minecraft mc) {
        if (pendingMove != null && !pendingMove.isDone()) {
            return;
        }
        pendingMove = null;
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        if (nextTest >= tests.size()) {
            enter(State.FINISH);
            return;
        }
        current = tests.get(nextTest++);
        /*
         * Areas are laid out along x by the test's index in the whole suite; y is chosen so the GameTest
         * convention (relative y = 2 stands on the ground) holds here too.
         */
        final BlockPos origin = new BlockPos(spawn.getX() + current.index() * AREA_SPACING, groundY - 2, spawn.getZ());
        context = new ClientTestContext(mc, current.name(), origin);
        final MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("integrated server vanished");
        }
        pendingMove = server.submit(() -> context.teleport(new BlockPos(3, 2, 8), net.minecraft.core.Direction.NORTH));
        enter(State.MOVE_TO_AREA);
    }

    private void moveToArea(final Minecraft mc) {
        if (!pendingMove.isDone()) {
            return;
        }
        pendingMove.join();
        final MinecraftServer server = mc.getSingleplayerServer();
        final boolean loaded = server != null && server.overworld().isLoaded(context.abs(new BlockPos(3, 2, 3)))
                && mc.level != null && mc.level.isLoaded(context.abs(new BlockPos(3, 2, 3)));
        if (!loaded || stateTicks < 10) {
            if (stateTicks > 600) {
                throw new ClientTestFailure("the test area never loaded");
            }
            return;
        }
        pendingMove = null;
        LOGGER.info("[JSC-CT] running {} at {}", current.name(), context.origin());
        try {
            current.method().invoke(null, context);
        } catch (final IllegalAccessException | InvocationTargetException e) {
            throw new ClientTestFailure("could not build the test sequence", e.getCause() != null ? e.getCause() : e);
        }
        stepIndex = 0;
        stepDelayLeft = context.steps().isEmpty() ? 0 : context.steps().get(0).delay();
        testTicks = 0;
        enter(State.RUNNING);
    }

    private void running(final Minecraft mc) {
        testTicks++;
        if (testTicks > current.timeoutTicks()) {
            throw new ClientTestFailure("test timed out after " + current.timeoutTicks() + " ticks at step "
                    + stepIndex + " (" + stepLabel() + ")");
        }
        final List<ClientTestContext.Queued> steps = context.steps();
        if (stepIndex >= steps.size()) {
            report.record(new ClientTestReport.Result(current.name(), true, "", context.screenshotsTaken(), testTicks));
            current = null;
            enter(State.NEXT_TEST);
            return;
        }
        if (stepDelayLeft > 0) {
            stepDelayLeft--;
            return;
        }
        if (steps.get(stepIndex).step().tick()) {
            stepIndex++;
            stepDelayLeft = stepIndex < steps.size() ? steps.get(stepIndex).delay() : 0;
        }
    }

    private String stepLabel() {
        final List<ClientTestContext.Queued> steps = context.steps();
        return stepIndex < steps.size() ? steps.get(stepIndex).label() : "end";
    }

    private void failCurrent(final Minecraft mc, final RuntimeException e) {
        final String where = " [step " + stepIndex + " " + stepLabel() + "]";
        LOGGER.error("[JSC-CT] {} failed{}", current.name(), where, e);
        try {
            context.screenshot("FAILED");
        } catch (final RuntimeException ignored) {
            // A screenshot is a courtesy; the failure itself is what matters.
        }
        final Throwable cause = e instanceof ClientTestFailure && e.getCause() != null ? e.getCause() : e;
        report.record(new ClientTestReport.Result(current.name(), false,
                (e.getMessage() == null ? cause.toString() : e.getMessage()) + where,
                context.screenshotsTaken(), testTicks));
        current = null;
        enter(State.NEXT_TEST);
    }

    private void finish(final Minecraft mc) {
        final Path file = mc.gameDirectory.toPath().resolve("clienttests").resolve("report.txt");
        report.write(file, SHARD, SHARDS);
        enter(State.DONE);
        mc.stop();
    }
}
