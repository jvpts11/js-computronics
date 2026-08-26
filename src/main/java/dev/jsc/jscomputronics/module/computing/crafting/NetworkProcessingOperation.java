/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.crafting;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.operation.PersistentOperation;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Runs a {@link ProcessingPattern} through a real machine: it finds the machine a Crafting Switch declared for
 * the pattern's machine type, feeds it one lot of inputs at a time from the network, lets it process on its own,
 * pulls the declared outputs back into the network, and counts the REAL yield (a probabilistic output's chance
 * only guided the plan, never the runtime). If nothing progresses within the pattern's timeout, it settles
 * partial/failed. Items and fluids feed in the same way; collecting a fluid back into the network is a follow-up.
 */
public final class NetworkProcessingOperation implements PersistentOperation {

    private static final int FEED_INTERVAL = 4;
    public static final String KIND = "processing";

    private final ServerLevel level;
    private final NetworkUuid network;
    private final ProcessingPattern pattern;
    private final long requested;
    private final List<BlockPos> candidateComputers;
    private final UUID operationId;
    private final String requesterLabel;
    private final StorageKey resultKey;

    private CraftingSwitchBlockEntity.DeclaredMachine machine;
    private long produced;
    private boolean done;
    private boolean waiting = true;
    private int idleTicks;
    private int feedCooldown;
    private byte status = OperationRecord.STATUS_FAILED;
    private Runnable onSettle;
    private boolean concurrencyBlocked;

    public NetworkProcessingOperation(final ServerLevel level, final NetworkUuid network,
                                      final ProcessingPattern pattern, final long requested,
                                      final List<BlockPos> candidateComputers, final UUID operationId,
                                      final String requesterLabel) {
        this.level = level;
        this.network = network;
        this.pattern = pattern;
        this.requested = requested;
        this.candidateComputers = List.copyOf(candidateComputers);
        this.operationId = operationId;
        this.requesterLabel = requesterLabel;
        final ProcessingPattern.ProcessingOutput primary = pattern.primaryOutput();
        this.resultKey = primary == null ? null : primary.key();
        if (this.resultKey == null || pattern.inputs().isEmpty() || requested <= 0) {
            finish(); // malformed pattern: settle immediately as FAILED
        }
    }

    @Override
    public void tick(final long throughputBudget) {
        if (done) {
            return;
        }
        if (machine == null) {
            machine = findMachine();
            if (machine == null) {
                waiting = true;
                if (++idleTicks > pattern.timeoutTicks()) {
                    finishTimedOut();
                }
                return;
            }
            waiting = false;
        }
        // Sided machines route through crafting buses when present: an Input Bus aimed at the machine carries
        // the deliveries, a Receiving Bus the pickups. Without buses both ride the switch-touched face.
        final ExternalDataPort inPort = portFor(dev.jsc.jscomputronics.module.computing.block.part.CablePartType.INPUT);
        final ExternalDataPort outPort =
                portFor(dev.jsc.jscomputronics.module.computing.block.part.CablePartType.RECEIVING);
        if (inPort.isEmpty() && outPort.isEmpty()) {
            machine = null; // the machine was broken/removed; re-resolve next tick
            return;
        }
        final NetworkStorage storage = NetworkStorage.of(level, network);
        boolean progressed = false;

        // 1) Collect any finished output the machine holds, back into the network, counting the primary yield.
        for (final ProcessingPattern.ProcessingOutput out : pattern.outputs()) {
            final long inMachine = outPort.count(out.key());
            if (inMachine <= 0) {
                continue;
            }
            final long pulled = outPort.extract(out.key(), inMachine, false);
            if (pulled > 0) {
                final long stored = writeBack(storage, out.key(), pulled);
                if (out.key().equals(resultKey)) {
                    produced += stored;
                }
                progressed = true;
            }
        }
        if (produced >= requested) {
            status = OperationRecord.STATUS_COMPLETED;
            finish();
            return;
        }

        final CraftingComputerBlockEntity.MachineConfig config = resolveConfig();
        if (config.locked() || concurrencyBlocked) {
            // Paused from the Machines tab, or over the machine's concurrent-job cap: keep collecting finished
            // output, but don't feed or time out.
            waiting = true;
            return;
        }
        waiting = false;

        // 2) Feed inputs when the cooldown elapses (so we don't overfill a slow machine). feedMax keeps feeding
        // until the machine is full each cycle; otherwise a single lot goes in.
        if (--feedCooldown <= 0) {
            feedCooldown = FEED_INTERVAL;
            final int maxLots = config.feedMax() ? 64 : 1;
            for (int lot = 0; lot < maxLots; lot++) {
                boolean fedThisLot = false;
                for (final ProcessingPattern.ProcessingInput in : pattern.inputs()) {
                    if (storage.select(in.key(), in.amount(), inPort) > 0) {
                        fedThisLot = true;
                        progressed = true;
                    }
                }
                if (!fedThisLot) {
                    break; // the machine is full or the network is drained
                }
            }
        }

        if (progressed) {
            idleTicks = 0;
        } else if (++idleTicks > pattern.timeoutTicks()) {
            finishTimedOut();
        }
    }

    /**
     * Whether a declared machine serves the pattern's machine id: a generic id ({@code generic:<recipeType>})
     * matches any face the player tagged with that category; a concrete id matches by block id or face name.
     */
    public static boolean machineMatches(final CraftingSwitchBlockEntity.DeclaredMachine m, final String want) {
        if (MachineCategory.isGenericId(want)) {
            return !m.category().isEmpty() && m.category().equals(MachineCategory.categoryOf(want));
        }
        return m.machineType().equals(want) || m.name().equalsIgnoreCase(want);
    }

    /** The concurrency config the Machines tab set for this machine, resolved through the owning computer. */
    private CraftingComputerBlockEntity.MachineConfig resolveConfig() {
        for (final BlockPos pos : candidateComputers) {
            if (level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity cc) {
                for (final CraftingSwitchBlockEntity.DeclaredMachine m : cc.availableMachines()) {
                    if (machineMatches(m, pattern.machineType())) {
                        // Config is keyed by what the pattern targets, falling back to the machine's own name.
                        final CraftingComputerBlockEntity.MachineConfig byType =
                                cc.machineConfig(pattern.machineType());
                        return byType != CraftingComputerBlockEntity.MachineConfig.DEFAULT
                                ? byType : cc.machineConfig(m.name());
                    }
                }
            }
        }
        return CraftingComputerBlockEntity.MachineConfig.DEFAULT;
    }

    @Nullable
    private CraftingSwitchBlockEntity.DeclaredMachine findMachine() {
        for (final BlockPos pos : candidateComputers) {
            if (level.getBlockEntity(pos) instanceof CraftingComputerBlockEntity cc) {
                for (final CraftingSwitchBlockEntity.DeclaredMachine m : cc.availableMachines()) {
                    if (machineMatches(m, pattern.machineType())) {
                        return m;
                    }
                }
            }
        }
        return null;
    }

    private ExternalDataPort machinePort() {
        final Direction side = machine.face().getOpposite();
        return new ExternalDataPort(
                level.getCapability(Capabilities.ItemHandler.BLOCK, machine.machinePos(), side),
                level.getCapability(Capabilities.FluidHandler.BLOCK, machine.machinePos(), side));
    }

    /**
     * The port to move items through for the given bus kind. When a crafting cable adjacent to the machine has
     * an Input Bus (deliveries) or Receiving Bus (pickups) mounted against it, that bus's machine face is used —
     * this is how sided machines whose I/O faces differ from the switch-touched face are driven. Without a bus,
     * the switch-touched face serves both directions.
     */
    private ExternalDataPort portFor(final dev.jsc.jscomputronics.module.computing.block.part.CablePartType kind) {
        for (final Direction d : Direction.values()) {
            final net.minecraft.core.BlockPos cablePos = machine.machinePos().relative(d);
            if (level.getBlockEntity(cablePos)
                    instanceof dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity cable
                    && cable.getPart(d.getOpposite())
                    instanceof dev.jsc.jscomputronics.module.computing.block.part.AbstractBusPart bus
                    && bus.type() == kind) {
                final ExternalDataPort port = new ExternalDataPort(
                        level.getCapability(Capabilities.ItemHandler.BLOCK, machine.machinePos(), d),
                        level.getCapability(Capabilities.FluidHandler.BLOCK, machine.machinePos(), d));
                if (!port.isEmpty()) {
                    return port;
                }
            }
        }
        return machinePort();
    }

    /** Puts {@code amount} of a key (item OR fluid) into the network; returns how much was stored. */
    private long writeBack(final NetworkStorage storage, final StorageKey key, final long amount) {
        return storage.insert(key, amount);
    }

    private void finishTimedOut() {
        status = produced > 0 ? OperationRecord.STATUS_PARTIAL : OperationRecord.STATUS_FAILED;
        finish();
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        waiting = false;
        if (onSettle != null) {
            onSettle.run();
        }
    }

    public NetworkProcessingOperation onSettle(final Runnable callback) {
        this.onSettle = callback;
        if (done && callback != null) {
            callback.run();
        }
        return this;
    }

    /** The machine key this op targets, used by the Mainframe to cap concurrent jobs per machine (maxJobs). */
    public String machineKey() {
        return pattern.machineType();
    }

    /** Set by the Mainframe each tick: true when this op is over its machine's concurrent-job cap. */
    public void setConcurrencyBlocked(final boolean blocked) {
        this.concurrencyBlocked = blocked;
    }

    public long produced() {
        return produced;
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
        ProcessingPattern.CODEC.encodeStart(ops, pattern).result().ifPresent(t -> tag.put("Pattern", t));
        tag.putLong("Requested", requested);
        tag.putLong("Produced", produced);
        tag.putInt("IdleTicks", idleTicks);
        tag.putString("Label", requesterLabel);
        return tag;
    }

    /**
     * Rebuilds a processing operation saved by {@link #saveState}. The machine is resolved again on the first
     * tick, so inputs already delivered to it are collected as they finish; the yield counted so far carries
     * over. Returns null when the saved pattern cannot be read.
     */
    @Nullable
    public static NetworkProcessingOperation restore(final CompoundTag tag, final ServerLevel level,
                                                     final NetworkUuid network, final List<BlockPos> candidateComputers,
                                                     final HolderLookup.Provider registries) {
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final ProcessingPattern pattern = tag.contains("Pattern")
                ? ProcessingPattern.CODEC.parse(ops, tag.get("Pattern")).result().orElse(null) : null;
        if (pattern == null) {
            return null;
        }
        final NetworkProcessingOperation op = new NetworkProcessingOperation(level, network, pattern,
                tag.getLong("Requested"), candidateComputers,
                tag.hasUUID(ID_KEY) ? tag.getUUID(ID_KEY) : UUID.randomUUID(), tag.getString("Label"));
        op.produced = tag.getLong("Produced");
        op.idleTicks = tag.getInt("IdleTicks");
        return op;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean isWaiting() {
        return waiting && !done;
    }

    @Override
    public void abandon() {
        finish();
    }

    @Override
    public OperationRecord toRecord() {
        return buildRecord(status);
    }

    @Override
    public OperationRecord liveRecord() {
        final byte live = done ? status
                : waiting ? OperationRecord.STATUS_WAITING : OperationRecord.STATUS_PROCESSING;
        return buildRecord(live);
    }

    private OperationRecord buildRecord(final byte recordStatus) {
        final StorageKey key = resultKey != null ? resultKey
                : (pattern.inputs().isEmpty() ? null : pattern.inputs().get(0).key());
        return new OperationRecord(OperationRecord.TYPE_CRAFT, key, requested, produced, recordStatus,
                List.of());
    }
}
