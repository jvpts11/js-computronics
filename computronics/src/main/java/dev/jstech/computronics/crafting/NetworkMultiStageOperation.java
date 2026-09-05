/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.crafting;

import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.operation.NetworkOperation;
import dev.jstech.computronics.operation.PersistentOperation;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Runs a {@link MultiStagePattern} as an ordered pipeline: it submits one stage at a time to the Mainframe (a
 * bench craft via {@code submitNetworkCraft}, a processing step via {@code submitNetworkProcessing}) and only
 * starts the next stage once the current one is done. Each stage's output goes to the network and the next
 * stage pulls it back, so the chain flows through shared network storage. If a stage fails, the whole craft
 * fails. The stages themselves are real network operations the Mainframe ticks; this just sequences them.
 */
public final class NetworkMultiStageOperation implements PersistentOperation {

    public static final String KIND = "multi";

    private final MainframeBlockEntity mainframe;
    private final MultiStagePattern pattern;
    private final long requested;
    private final String requesterLabel;
    private final StorageKey resultKey;
    private final UUID operationId;

    private int stageIndex;
    private NetworkOperation currentStage;
    // After a reload: the id of the stage this pipeline was waiting on, until the Mainframe hands it back.
    @Nullable
    private UUID pendingStageId;
    private boolean done;
    private byte status = OperationRecord.STATUS_PROCESSING;
    private Runnable onSettle;

    public NetworkMultiStageOperation(final MainframeBlockEntity mainframe, final MultiStagePattern pattern,
                                      final long requested, final String requesterLabel) {
        this(mainframe, pattern, requested, requesterLabel, UUID.randomUUID());
    }

    private NetworkMultiStageOperation(final MainframeBlockEntity mainframe, final MultiStagePattern pattern,
                                       final long requested, final String requesterLabel, final UUID operationId) {
        this.mainframe = mainframe;
        this.pattern = pattern;
        this.requested = requested;
        this.requesterLabel = requesterLabel;
        this.resultKey = finalResultKey(pattern);
        this.operationId = operationId;
        if (pattern.stages().isEmpty()) {
            status = OperationRecord.STATUS_FAILED;
            finish();
        }
    }

    @Override
    public UUID operationId() {
        return operationId;
    }

    @Override
    public CompoundTag saveState(final HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putString(KIND_KEY, KIND);
        tag.putUUID(ID_KEY, operationId);
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        MultiStagePattern.CODEC.encodeStart(ops, pattern).result().ifPresent(t -> tag.put("Pattern", t));
        tag.putLong("Requested", requested);
        tag.putString("Label", requesterLabel);
        tag.putInt("StageIndex", stageIndex);
        if (currentStage instanceof PersistentOperation stage && !currentStage.isDone()) {
            tag.putUUID("StageId", stage.operationId());
        } else if (pendingStageId != null) {
            tag.putUUID("StageId", pendingStageId);
        }
        return tag;
    }

    /** Rebuilds a pipeline saved by {@link #saveState}; the stage it waited on is handed back via {@link #adoptStage}. */
    @Nullable
    public static NetworkMultiStageOperation restore(final CompoundTag tag, final MainframeBlockEntity mainframe,
                                                     final HolderLookup.Provider registries) {
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final MultiStagePattern pattern = tag.contains("Pattern")
                ? MultiStagePattern.CODEC.parse(ops, tag.get("Pattern")).result().orElse(null) : null;
        if (pattern == null) {
            return null;
        }
        final NetworkMultiStageOperation op = new NetworkMultiStageOperation(mainframe, pattern,
                tag.getLong("Requested"), tag.getString("Label"),
                tag.hasUUID(ID_KEY) ? tag.getUUID(ID_KEY) : UUID.randomUUID());
        op.stageIndex = tag.getInt("StageIndex");
        op.pendingStageId = tag.hasUUID("StageId") ? tag.getUUID("StageId") : null;
        return op;
    }

    /** The id of the stage this restored pipeline was waiting on, or null when it starts its next stage fresh. */
    @Nullable
    public UUID pendingStageId() {
        return pendingStageId;
    }

    /**
     * Hands a restored pipeline the stage operation it was waiting on (or null when that stage could not be
     * restored — the pipeline then simply starts the same stage again, which is safe because a stage only
     * moves items once it runs).
     */
    public void adoptStage(@Nullable final NetworkOperation stage) {
        this.currentStage = stage;
        this.pendingStageId = null;
    }

    /** The stage this restored pipeline waited on was fully delivered during the restore: move on to the next. */
    public void skipCompletedStage() {
        this.currentStage = null;
        this.pendingStageId = null;
        this.stageIndex++;
    }

    @Override
    public void tick(final long throughputBudget) {
        if (done) {
            return;
        }
        if (currentStage == null) {
            if (stageIndex >= pattern.stages().size()) {
                status = OperationRecord.STATUS_COMPLETED;
                finish();
                return;
            }
            currentStage = startStage(pattern.stages().get(stageIndex));
            if (currentStage == null) {
                status = OperationRecord.STATUS_FAILED;
                finish();
                return;
            }
            return;
        }
        if (currentStage.isDone()) {
            // Take whatever status the finished stage carried; a failed stage fails the whole pipeline.
            final byte stageStatus = currentStage.toRecord().status();
            if (stageStatus == OperationRecord.STATUS_FAILED) {
                status = OperationRecord.STATUS_FAILED;
                finish();
                return;
            }
            stageIndex++;
            currentStage = null;
        }
        // Otherwise the Mainframe is ticking the current stage; just wait for it.
    }

    @Nullable
    private NetworkOperation startStage(final MultiStagePattern.Stage stage) {
        // Each stage is sized by what the stage after it consumes, never by the final quantity: nine nuggets
        // (one ingot makes nine) smelt one ingot; one iron block (nine ingots) smelts nine.
        final long demand = pattern.stageDemands(requested)[stageIndex];
        if (stage.proc().isPresent()) {
            return mainframe.submitNetworkProcessing(stage.proc().get(), demand, requesterLabel);
        }
        if (stage.bench().isPresent()) {
            // The stage carries its own pattern: plan with it so the pipeline runs even when the bench
            // recipe was never loaded into a Recipe ROM on its own.
            final CraftingPattern bench = stage.bench().get();
            return mainframe.submitNetworkCraft(
                    StorageKey.of(bench.result()), demand, true, requesterLabel, bench);
        }
        return null;
    }

    @Nullable
    private static StorageKey finalResultKey(final MultiStagePattern pattern) {
        final MultiStagePattern.Stage last = pattern.finalStage();
        if (last == null) {
            return null;
        }
        if (last.bench().isPresent()) {
            return StorageKey.of(last.bench().get().result());
        }
        if (last.proc().isPresent()) {
            final ProcessingPattern.ProcessingOutput out = last.proc().get().primaryOutput();
            return out == null ? null : out.key();
        }
        return null;
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        if (currentStage != null && !currentStage.isDone()) {
            currentStage.abandon(); // settle the in-flight stage so it returns its ingredients
        }
        if (onSettle != null) {
            onSettle.run();
        }
    }

    public NetworkMultiStageOperation onSettle(final Runnable callback) {
        this.onSettle = callback;
        if (done && callback != null) {
            callback.run();
        }
        return this;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean isWaiting() {
        // While a stage is in flight the pipeline is only waiting on it: the stage is the live operation.
        // Claiming a queue slot here would starve the stage on a single-queue Mainframe — the pipeline
        // holds the slot to idle while the stage behind it in the list never gets ticked.
        return !done && currentStage != null;
    }

    @Override
    public void abandon() {
        status = OperationRecord.STATUS_DISCARDED;
        finish();
    }

    @Override
    public OperationRecord toRecord() {
        return buildRecord(status);
    }

    @Override
    public OperationRecord liveRecord() {
        return buildRecord(done ? status : OperationRecord.STATUS_PROCESSING);
    }

    private OperationRecord buildRecord(final byte recordStatus) {
        return new OperationRecord(OperationRecord.TYPE_CRAFT, resultKey, requested, stageIndex, recordStatus,
                List.of());
    }
}
