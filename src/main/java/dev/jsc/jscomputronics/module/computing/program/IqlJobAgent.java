/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program;

import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlConditionParser;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlDefinition;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlDuration;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlSavedObject;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The IQL Engine's job agent — the "SQL Server Agent". Each Mainframe owns one; it runs on the Mainframe
 * tick (only while the Engine is installed and running) and fires the catalog's jobs by their trigger:
 * {@code EVERY <duration>} on a schedule, {@code WHEN <condition>} on the rising edge of the condition
 * (so it fires once when the condition becomes true, not every tick it stays true). Firing runs the job's
 * body through the {@link IqlEngine}, producing ordinary network Operations — there is no special per-job
 * cost beyond those Operations. The agent evaluates every {@value #EVAL_INTERVAL} ticks to keep the
 * condition checks cheap; its scheduling state is transient (a reload reschedules from the next tick).
 */
public final class IqlJobAgent {

    private static final int EVAL_INTERVAL = 10;
    private static final int QUERY_ROW_LIMIT = 64;

    private long clock;
    private final Map<String, Long> lastFired = new HashMap<>();
    private final Map<String, Boolean> lastCondition = new HashMap<>();

    public void tick(final MainframeBlockEntity mainframe, final ServerLevel level) {
        clock++;
        if (!mainframe.isIqlEngineActive() || clock % EVAL_INTERVAL != 0) {
            return;
        }
        final List<IqlSavedObject> jobs = mainframe.iqlCatalog().ofType(IqlDefinition.ObjectType.JOB);
        if (jobs.isEmpty()) {
            return;
        }
        IqlEngine engine = null;
        for (final IqlSavedObject job : jobs) {
            if (mainframe.isJobPaused(job.name())) {
                continue; // a paused job never fires until it is restarted from the Processes tab
            }
            if (shouldFire(job, mainframe, level)) {
                if (engine == null) {
                    engine = new IqlEngine(mainframe, new ServerCliComputer(mainframe, level), QUERY_ROW_LIMIT);
                }
                engine.run(job.body());
            }
        }
    }

    /** Re-arms a job's trigger so it reschedules from now (EVERY) or resets its edge detector (WHEN). */
    public void rearm(final String jobName) {
        lastFired.remove(jobName);
        lastCondition.remove(jobName);
    }

    private boolean shouldFire(final IqlSavedObject job, final MainframeBlockEntity mainframe,
                               final ServerLevel level) {
        return switch (job.triggerKind()) {
            case EVERY -> shouldFireEvery(job);
            case WHEN -> shouldFireWhen(job, mainframe, level);
            case NONE -> false;
        };
    }

    private boolean shouldFireEvery(final IqlSavedObject job) {
        final long interval;
        try {
            interval = IqlDuration.toTicks(job.triggerSpec());
        } catch (final IllegalArgumentException e) {
            return false; // a malformed interval never fires (the NMS surfaces the error on create)
        }
        if (interval <= 0) {
            return false;
        }
        final Long last = lastFired.get(job.name());
        if (last == null) {
            lastFired.put(job.name(), clock); // schedule from now rather than firing immediately
            return false;
        }
        if (clock - last >= interval) {
            lastFired.put(job.name(), clock);
            return true;
        }
        return false;
    }

    private boolean shouldFireWhen(final IqlSavedObject job, final MainframeBlockEntity mainframe,
                                   final ServerLevel level) {
        final IqlCondition condition;
        try {
            condition = IqlConditionParser.parse(job.triggerSpec());
        } catch (final IllegalArgumentException e) {
            return false;
        }
        final boolean now = condition.matches(field -> resolveField(field, mainframe, level));
        final boolean was = lastCondition.getOrDefault(job.name(), false);
        lastCondition.put(job.name(), now);
        return now && !was; // rising edge: fire once when the condition turns true
    }

    /** Resolves the fields a WHEN condition can test today: {@code qty(<item>)} against the live network. */
    private static String resolveField(final String field, final MainframeBlockEntity mainframe,
                                       final ServerLevel level) {
        final String lower = field.toLowerCase(Locale.ROOT);
        if (lower.startsWith("qty(") && lower.endsWith(")")) {
            final String itemName = field.substring(4, field.length() - 1).strip();
            return Long.toString(networkQuantity(mainframe, level, itemName));
        }
        return null; // an unknown field makes the comparison false, so the job stays idle
    }

    private static long networkQuantity(final MainframeBlockEntity mainframe, final ServerLevel level,
                                        final String itemName) {
        final NetworkUuid net = mainframe.networkUuid();
        final Item item = resolveItem(itemName);
        if (net == null || item == null) {
            return 0L;
        }
        return NetworkStorage.of(level, net).query().getOrDefault(StorageKey.of(item), 0L);
    }

    private static Item resolveItem(final String name) {
        final String id = name.contains(":") ? name : "minecraft:" + name;
        final ResourceLocation location = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        return location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }
}
