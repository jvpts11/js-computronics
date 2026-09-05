/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.testkit;

import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.crafting.ProcessingPattern;
import dev.jstech.computronics.storage.ExternalDataPort;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.JsTests;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * The traffic a busy base puts on its Mainframe, tick after tick: terminal pulls of random catalog
 * items, deposits of the same, and a crafting workload — bench recipes, recipe chains and machine
 * processing — requested in bursts. The scale benchmark ticks a {@link Session} itself; the
 * {@code /jsc benchmark load} command runs one on the server tick so a player can watch the profiler
 * while the base works.
 */
@EventBusSubscriber(modid = JsTests.MODID)
public final class BenchmarkLoad {

    /** Of every four operations, three are SELECTs and one is an INSERT of the same kind of item. */
    private static final int SELECTS_PER_INSERT = 3;

    @Nullable
    private static Session active;
    @Nullable
    private static BigBaseScenario.Built lastBuilt;

    private BenchmarkLoad() {
    }

    /** One crafting request the traffic repeats: a bench or chained recipe by its output, or a machine pattern. */
    public record CraftRequest(@Nullable StorageKey output, @Nullable ProcessingPattern pattern, long quantity) {

        public static CraftRequest recipe(final StorageKey output, final long quantity) {
            return new CraftRequest(output, null, quantity);
        }

        public static CraftRequest processing(final ProcessingPattern pattern, final long quantity) {
            return new CraftRequest(null, pattern, quantity);
        }
    }

    /**
     * The crafting side of the traffic: the requests fired together every {@code everyTicks}, and hooks
     * run every tick (keeping the machines powered, for one).
     */
    public record Workload(List<CraftRequest> requests, int everyTicks, List<Runnable> tickHooks) {

        public static final Workload NONE = new Workload(List.of(), 20, List.of());

        /** A planks craft now and then: the light workload the in-world command uses by default. */
        public static Workload planks(final long quantity) {
            return new Workload(List.of(CraftRequest.recipe(StorageKey.of(Items.OAK_PLANKS), quantity)), 20, List.of());
        }
    }

    /** One run of traffic: {@code opsPerTick} operations for {@code ticksToRun} ticks, plus the crafting workload. */
    public static final class Session {

        private final MainframeBlockEntity mainframe;
        private final List<StorageKey> catalog;
        private final int opsPerTick;
        private final int ticksToRun;
        private final Workload workload;
        private final Random random;
        // A terminal player takes what arrives: the sink is emptied every tick so pulls never stall on it.
        private final ItemStackHandler sink = new ItemStackHandler(54);
        private final ExternalDataPort port = new ExternalDataPort(sink, null);
        private int ticks;
        private int submitted;
        private int accepted;
        private int craftsSubmitted;
        private int craftsAccepted;

        public Session(final MainframeBlockEntity mainframe, final List<StorageKey> catalog, final int opsPerTick,
                       final int ticksToRun, final Workload workload, final long seed) {
            this.mainframe = mainframe;
            this.catalog = catalog;
            this.opsPerTick = opsPerTick;
            this.ticksToRun = ticksToRun;
            this.workload = workload;
            this.random = new Random(seed);
        }

        /** Submits this tick's operations; returns false once the run is over. */
        public boolean tick() {
            if (ticks >= ticksToRun) {
                return false;
            }
            for (final Runnable hook : workload.tickHooks()) {
                hook.run();
            }
            for (int i = 0; i < sink.getSlots(); i++) {
                sink.setStackInSlot(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < opsPerTick; i++) {
                final StorageKey key = catalog.get(random.nextInt(catalog.size()));
                final boolean insert = i % (SELECTS_PER_INSERT + 1) == SELECTS_PER_INSERT;
                final Object op = insert
                        ? mainframe.submitNetworkInsert(key, 16 + random.nextInt(48), "bench")
                        : mainframe.submitNetworkSelect(key, 1 + random.nextInt(16), port, "bench");
                submitted++;
                if (op != null) {
                    accepted++;
                }
            }
            if (ticks % workload.everyTicks() == 0) {
                for (final CraftRequest request : workload.requests()) {
                    craftsSubmitted++;
                    final Object op = request.pattern() != null
                            ? mainframe.submitNetworkProcessing(request.pattern(), request.quantity(), "bench")
                            : mainframe.submitNetworkCraft(request.output(), request.quantity(), false, "bench");
                    if (op != null) {
                        craftsAccepted++;
                    }
                }
            }
            ticks++;
            return ticks < ticksToRun;
        }

        public boolean done() {
            return ticks >= ticksToRun;
        }

        public int ticks() {
            return ticks;
        }

        public int submitted() {
            return submitted;
        }

        public int accepted() {
            return accepted;
        }

        public int craftsSubmitted() {
            return craftsSubmitted;
        }

        public int craftsAccepted() {
            return craftsAccepted;
        }
    }

    // ---- the in-world driver behind the command ----

    public static void remember(final BigBaseScenario.Built built) {
        lastBuilt = built;
    }

    @Nullable
    public static BigBaseScenario.Built lastBuilt() {
        return lastBuilt;
    }

    public static void start(final Session session) {
        active = session;
    }

    public static boolean stop() {
        final boolean wasRunning = active != null;
        active = null;
        return wasRunning;
    }

    @Nullable
    public static Session active() {
        return active;
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event) {
        final Session session = active;
        if (session != null && !session.tick()) {
            active = null;
        }
    }
}
