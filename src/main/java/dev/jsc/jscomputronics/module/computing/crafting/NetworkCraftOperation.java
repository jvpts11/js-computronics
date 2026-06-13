/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.crafting;

import dev.jsc.jscomputronics.common.operation.index.Allocation;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.NetworkIndex;
import dev.jsc.jscomputronics.module.computing.operation.NetworkOperation;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A multi-tick CRAFT: executes a {@link CraftPlanner.Plan} on a Crafting Computer.
 */
public final class NetworkCraftOperation implements NetworkOperation {

    private static final int STALL_LIMIT = 100;

    public static final int DEFAULT_WAIT_TIMEOUT_TICKS = 1200;

    private final ServerLevel level;
    private final NetworkUuid network;
    private final StorageKey resultKey;
    private final long requested;
    private final CraftPlanner.Plan plan;
    private final NetworkIndex index;
    private final UUID operationId;
    private final List<BlockPos> candidateComputers;
    private final List<BlockPos> supercomputers;
    private final String requesterLabel;

    private final Map<StorageKey, Long> pool = new HashMap<>();
    // The servers each ingredient's reservation was placed on, so the per-tick drain targets the same
    // servers and every release frees the matching reservation instead of silently missing.
    private final Map<StorageKey, java.util.Set<NodeUuid>> lockedServers = new HashMap<>();
    private final long[] runsDone;

    private CraftingComputerBlockEntity executor;
    private dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity orchestrator;
    private boolean exclusiveClaim;
    private boolean locked;
    private boolean waiting = true;
    private int waitTicks;
    private boolean timedOut;
    private int stalledTicks;
    private int stepIndex;
    private long deliveredResult;
    private boolean done;
    private byte status = OperationRecord.STATUS_FAILED;
    private Runnable onSettle;

    public NetworkCraftOperation(final ServerLevel level, final NetworkUuid network,
                                 final StorageKey resultKey, final long requested,
                                 final CraftPlanner.Plan plan, final NetworkIndex index,
                                 final UUID operationId, final List<BlockPos> candidateComputers,
                                 final List<BlockPos> supercomputers, final String requesterLabel) {
        this.level = level;
        this.network = network;
        this.resultKey = resultKey;
        this.requested = requested;
        this.plan = plan;
        this.index = index;
        this.operationId = operationId;
        this.candidateComputers = List.copyOf(candidateComputers);
        this.supercomputers = List.copyOf(supercomputers);
        this.requesterLabel = requesterLabel;
        this.runsDone = new long[plan.steps().size()];
        if (plan.steps().isEmpty()) {
            finish();
        }
    }

    @Override
    public void tick(final long throughputBudget) {
        if (done) {
            return;
        }
        if (waiting) {
            // Acquire in two stages, holding nothing while blocked so waiters cannot deadlock:
            // first the full ingredient reservation, then an idle computer that can execute.
            if (++waitTicks > DEFAULT_WAIT_TIMEOUT_TICKS) {
                timedOut = true;
                finish();
                return;
            }
            if (!locked && !tryLockIngredients()) {
                return;
            }
            if (!tryClaimExecutor()) {
                return;
            }
            waiting = false;
        }
        if (!executorAlive()) {
            // The computer was broken or powered off mid-craft: settle with what was produced.
            finish();
            return;
        }

        // The computer crafts at its card throughput, never above the Mainframe's grant. Work is
        // measured in ingredient items handled, so denser recipes genuinely take longer.
        final long rate = Math.min(throughputBudget, executor.craftingThroughput());
        if (rate <= 0) {
            return;
        }
        long budget = rate;
        boolean progressed = false;
        final NetworkStorage storage = NetworkStorage.of(level, network);

        while (budget > 0 && stepIndex < plan.steps().size()) {
            final CraftPlanner.Step step = plan.steps().get(stepIndex);
            final long remainingRuns = step.runs() - runsDone[stepIndex];
            if (remainingRuns <= 0) {
                stepIndex++;
                continue;
            }
            final long unitsPerRun = Math.max(1, step.pattern().filledCells());
            final long runsAffordable = Math.max(budget >= unitsPerRun ? budget / unitsPerRun : 0, 0);
            if (runsAffordable <= 0) {
                break; // budget exhausted mid-step; resume next tick
            }
            final long runsNow = Math.min(remainingRuns, runsAffordable);
            final long executable = consumeIngredients(storage, step.pattern(), runsNow);
            if (executable <= 0) {
                break; // ingredients not deliverable this tick (stall counter decides)
            }
            pool.merge(StorageKey.of(step.pattern().result()),
                    executable * step.pattern().result().getCount(), Long::sum);
            runsDone[stepIndex] += executable;
            budget -= executable * unitsPerRun;
            progressed = true;
            if (runsDone[stepIndex] >= step.runs()) {
                stepIndex++;
            }
        }

        if (stepIndex >= plan.steps().size()) {
            finish();
        } else if (!progressed && ++stalledTicks >= STALL_LIMIT) {
            finish();
        } else if (progressed) {
            stalledTicks = 0;
        }
    }

    private boolean tryLockIngredients() {
        lockedServers.clear();
        boolean covered = true;
        for (final Map.Entry<StorageKey, Long> entry : plan.rawConsumption().entrySet()) {
            final Allocation allocation = index.lock(operationId, entry.getKey(), entry.getValue());
            lockedServers.put(entry.getKey(), new java.util.HashSet<>(allocation.perServer().keySet()));
            if (!allocation.covers(entry.getValue())) {
                covered = false;
                break;
            }
        }
        if (!covered) {
            index.unlock(operationId);
            return false;
        }
        locked = true;
        return true;
    }

    private boolean tryClaimExecutor() {
        final CraftingPattern root = plan.steps().isEmpty()
                ? null : plan.steps().get(plan.steps().size() - 1).pattern();
        final var sc = findRunningSupercomputer();
        if (sc != null) {
            final CraftingComputerBlockEntity cc = findCapableComputer(root, false);
            if (cc != null && sc.tryAcquireCraftSlot(operationId)) {
                orchestrator = sc;
                executor = cc;
                return true;
            }
            return false; // no capable computer yet, or the parallel budget is spent — wait in line
        }
        final CraftingComputerBlockEntity cc = findCapableComputer(root, true);
        if (cc != null) {
            exclusiveClaim = true;
            executor = cc;
            return true;
        }
        return false;
    }

    @org.jetbrains.annotations.Nullable
    private dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity
            findRunningSupercomputer() {
        for (final BlockPos pos : supercomputers) {
            if (level.getBlockEntity(pos)
                    instanceof dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity sc
                    && sc.clusterOnline()) {
                return sc;
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private CraftingComputerBlockEntity findCapableComputer(final CraftingPattern root,
                                                            final boolean claimExclusive) {
        for (final BlockPos pos : candidateComputers) {
            if (level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity cc
                    && cc.canCraft()
                    && (root == null || cc.romContains(root))
                    && (!claimExclusive || cc.tryClaimCraft(operationId))) {
                return cc;
            }
        }
        return null;
    }

    private boolean executorAlive() {
        return executor != null && !executor.isRemoved() && executor.canCraft();
    }

    private long consumeIngredients(final NetworkStorage storage, final CraftingPattern pattern,
                                    final long runs) {
        long executable = runs;
        // First pass: how many runs can the pools + network actually deliver?
        for (final Map.Entry<StorageKey, Long> entry : pattern.ingredientTotals().entrySet()) {
            final long perRun = entry.getValue();
            final long pooled = pool.getOrDefault(entry.getKey(), 0L);
            final long networkHas = storage.count(entry.getKey());
            executable = Math.min(executable, (pooled + networkHas) / perRun);
        }
        if (executable <= 0) {
            return 0;
        }
        for (final Map.Entry<StorageKey, Long> entry : pattern.ingredientTotals().entrySet()) {
            final StorageKey key = entry.getKey();
            long need = entry.getValue() * executable;
            final long fromPool = Math.min(need, pool.getOrDefault(key, 0L));
            if (fromPool > 0) {
                pool.merge(key, -fromPool, Long::sum);
                need -= fromPool;
            }
            if (need > 0) {
                // Crafting consumes the items: extract from the SAME servers the lock holds (not just any
                // server in discovery order) so each release frees its matching reservation as the items
                // leave, instead of missing and leaving them reserved until the craft finishes.
                final Map<NodeUuid, Long> moved = storage.selectBreakdown(
                        key, need, (k, amount, simulate) -> amount, lockedServers.get(key));
                moved.forEach((server, amount) -> index.release(operationId, key, server, amount));
            }
        }
        return executable;
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        waiting = false;
        index.unlock(operationId);
        if (orchestrator != null) {
            orchestrator.releaseCraftSlot(operationId);
        }
        if (exclusiveClaim && executor != null) {
            executor.releaseCraft(operationId);
        }

        // Deliver the result, then return every leftover intermediate — nothing is ever wasted.
        deliveredResult = Math.min(pool.getOrDefault(resultKey, 0L), requested);
        if (deliveredResult > 0) {
            pool.merge(resultKey, -deliveredResult, Long::sum);
            writeBack(resultKey, deliveredResult);
        }
        for (final Map.Entry<StorageKey, Long> leftover : new LinkedHashMap<>(pool).entrySet()) {
            if (leftover.getValue() > 0) {
                writeBack(leftover.getKey(), leftover.getValue());
            }
        }
        pool.clear();

        status = timedOut ? OperationRecord.STATUS_RESOURCE_LOCKED
                : deliveredResult >= requested ? OperationRecord.STATUS_COMPLETED
                : deliveredResult > 0 ? OperationRecord.STATUS_PARTIAL : OperationRecord.STATUS_FAILED;
        if (onSettle != null) {
            onSettle.run();
        }
    }

    private void writeBack(final StorageKey key, final long amount) {
        final ItemStack prototype = key.stack(1);
        if (prototype.isEmpty()) {
            return; // 3x3 patterns never produce fluids; nothing to write for a fluid key
        }
        final NetworkStorage storage = NetworkStorage.of(level, network);
        long remaining = amount;
        while (remaining > 0) {
            final int chunk = (int) Math.min(remaining, prototype.getMaxStackSize());
            final int accepted = storage.insert(prototype.copyWithCount(chunk));
            remaining -= chunk;
            final int overflow = chunk - accepted;
            if (overflow > 0 && executor != null) {
                // Network storage filled mid-craft: surface the items in the world, never void them.
                net.minecraft.world.Containers.dropItemStack(level,
                        executor.getBlockPos().getX() + 0.5, executor.getBlockPos().getY() + 1.0,
                        executor.getBlockPos().getZ() + 0.5, prototype.copyWithCount(overflow));
            }
        }
    }

    @Override
    public boolean isWaiting() {
        return waiting && !done;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void abandon() {
        finish();
    }

    public UUID operationId() {
        return operationId;
    }

    public byte craftStatus() {
        return status;
    }

    public long delivered() {
        return deliveredResult;
    }

    public NetworkCraftOperation onSettle(final Runnable callback) {
        this.onSettle = callback;
        if (done && callback != null) {
            callback.run();
        }
        return this;
    }

    @Override
    public OperationRecord toRecord() {
        return buildRecord(status, false);
    }

    @Override
    public OperationRecord liveRecord() {
        final byte liveStatus = done ? status
                : waiting ? OperationRecord.STATUS_WAITING : OperationRecord.STATUS_PROCESSING;
        return buildRecord(liveStatus, true);
    }

    private OperationRecord buildRecord(final byte recordStatus, final boolean includeSubs) {
        final long produced = done ? deliveredResult : producedSoFar();
        final List<OperationRecord.MoveRow> moves = new ArrayList<>();
        if (produced > 0) {
            moves.add(new OperationRecord.MoveRow(executorLabel(), produced, requesterLabel));
        }
        final List<OperationRecord.SubRow> subs = includeSubs ? subRows() : List.of();
        return new OperationRecord(OperationRecord.TYPE_CRAFT, resultKey, requested, produced,
                recordStatus, List.copyOf(moves), subs);
    }

    private long producedSoFar() {
        if (plan.steps().isEmpty()) {
            return 0;
        }
        final int last = plan.steps().size() - 1;
        return runsDone[last] * plan.steps().get(last).pattern().result().getCount();
    }

    private List<OperationRecord.SubRow> subRows() {
        final List<OperationRecord.SubRow> subs = new ArrayList<>(Math.min(plan.steps().size(),
                OperationRecord.MAX_SUBS));
        for (int i = 0; i < plan.steps().size() && subs.size() < OperationRecord.MAX_SUBS; i++) {
            final CraftPlanner.Step step = plan.steps().get(i);
            final byte state = runsDone[i] >= step.runs() ? OperationRecord.SubRow.SUB_COMPLETED
                    : i == stepIndex && !waiting ? OperationRecord.SubRow.SUB_STREAMING
                    : OperationRecord.SubRow.SUB_READING;
            subs.add(new OperationRecord.SubRow(
                    step.pattern().result().getHoverName().getString() + " x" + step.produced(),
                    step.runs(), runsDone[i], state));
        }
        return subs;
    }

    private String executorLabel() {
        if (executor == null) {
            return "CC";
        }
        final String name = executor.customName();
        return name.isEmpty() ? "CC" : name;
    }
}
