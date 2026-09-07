/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.crafting.MultiStagePattern;
import dev.jstech.computronics.crafting.ProcessingPattern;
import dev.jstech.computronics.storage.IDataSink;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.JsCore;
import dev.jstech.core.network.NetworkCategory;
import dev.jstech.core.operation.IOperationArgs;
import dev.jstech.core.operation.OperationCategory;
import dev.jstech.core.operation.IOperationHandler;
import dev.jstech.core.operation.OperationPriority;
import dev.jstech.core.operation.OperationStatus;
import dev.jstech.core.operation.OperationType;
import dev.jstech.core.tier.IndustrialTier;
import dev.jstech.core.uuid.NodeUuid;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Operation types the computing mod declares in the core registry, one per verb the network runs,
 * with a typed argument record and a handler that hands the request to the Mainframe. Every live
 * {@link INetworkOperation} names one of these ids, the lifecycle events on {@link JsCore#events()} carry
 * it, and another mod can look a type up (or submit through its handler) without a class dependency on
 * the computing mod's internals.
 *
 * <p>The handlers return the status the request settled or was accepted at: a timed Operation is accepted
 * as PENDING and finishes on its own ticks; an instant one runs to COMPLETED on the spot.
 */
public final class ComputingOperations {

    private static final String NAMESPACE = "jsc";

    public static final String SELECT = NAMESPACE + ":select";
    public static final String INSERT = NAMESPACE + ":insert";
    public static final String DELETE = NAMESPACE + ":delete";
    public static final String MOVE = NAMESPACE + ":move";
    public static final String CRAFT = NAMESPACE + ":craft";
    public static final String PROCESSING = NAMESPACE + ":processing";
    public static final String MULTI_STAGE = NAMESPACE + ":multi_stage";
    public static final String ANALYZE = NAMESPACE + ":analyze";
    public static final String REINDEX = NAMESPACE + ":reindex";
    public static final String VACUUM = NAMESPACE + ":vacuum";
    public static final String DROP = NAMESPACE + ":drop";
    public static final String LOCK = NAMESPACE + ":lock";
    public static final String UNLOCK = NAMESPACE + ":unlock";

    private static final EnumSet<NetworkCategory> ORCHESTRATED = EnumSet.of(NetworkCategory.C);

    private ComputingOperations() {
    }

    /** A pull out of the network into {@code destination}; {@code sources} narrows the Servers it draws from. */
    public record PullArgs(MainframeBlockEntity mainframe, StorageKey key, long demand, IDataSink destination,
                           String label, @Nullable Set<NodeUuid> sources, OperationPriority priority)
            implements IOperationArgs {
    }

    /** A write into the network of items the caller already holds. */
    public record InsertArgs(MainframeBlockEntity mainframe, StorageKey key, long amount, String label,
                             OperationPriority priority) implements IOperationArgs {
    }

    /** A craft of {@code demand} of {@code key} through whatever recipes the network holds. */
    public record CraftArgs(MainframeBlockEntity mainframe, StorageKey key, long demand, boolean partial,
                            String label, OperationPriority priority) implements IOperationArgs {
    }

    /** One machine recipe run for {@code demand} of its primary output. */
    public record ProcessingArgs(MainframeBlockEntity mainframe, ProcessingPattern pattern, long demand,
                                 String label, OperationPriority priority) implements IOperationArgs {
    }

    /** One multi-stage pipeline run for {@code demand} of its final result. */
    public record MultiStageArgs(MainframeBlockEntity mainframe, MultiStagePattern pattern, long demand,
                                 String label, OperationPriority priority) implements IOperationArgs {
    }

    /** An instant index maintenance run (ANALYZE, REINDEX, VACUUM) on a network. */
    public record IndexArgs(MainframeBlockEntity mainframe, ServerLevel level) implements IOperationArgs {
    }

    /** An instant DROP: destroys {@code key} on the network, or everything when {@code key} is null. */
    public record DropArgs(MainframeBlockEntity mainframe, ServerLevel level, @Nullable StorageKey key)
            implements IOperationArgs {
    }

    /** A manual reservation of {@code demand} of {@code key}, or its release. */
    public record LockArgs(MainframeBlockEntity mainframe, StorageKey key, long demand,
                           @Nullable Set<NodeUuid> sources) implements IOperationArgs {
    }

    /** Declares every type once; safe to call again (a second registration is refused by the registry). */
    public static void register() {
        if (JsCore.operations().contains(SELECT)) {
            return;
        }
        final var registry = JsCore.operations();
        registry.register(timed(SELECT, PullArgs.class, OperationCategory.STORAGE, a -> accepted(
                a.mainframe().submitNetworkSelect(a.key(), a.demand(), a.destination(), a.label(), a.sources()),
                a.priority())));
        registry.register(timed(MOVE, PullArgs.class, OperationCategory.STORAGE, a -> accepted(
                a.mainframe().submitNetworkMove(a.key(), a.demand(), a.destination(), a.label(), a.sources()),
                a.priority())));
        registry.register(timed(DELETE, PullArgs.class, OperationCategory.STORAGE, a -> accepted(
                a.mainframe().submitNetworkDelete(a.key(), a.demand(), a.destination(), a.label()), a.priority())));
        registry.register(timed(INSERT, InsertArgs.class, OperationCategory.STORAGE, a -> accepted(
                a.mainframe().submitNetworkInsert(a.key(), a.amount(), a.label()), a.priority())));
        registry.register(timed(CRAFT, CraftArgs.class, OperationCategory.CRAFTING, a -> accepted(
                a.mainframe().submitCraftRequest(a.key(), a.demand(), a.partial(), a.label(), null), a.priority())));
        registry.register(timed(PROCESSING, ProcessingArgs.class, OperationCategory.CRAFTING, a -> accepted(
                a.mainframe().submitNetworkProcessing(a.pattern(), a.demand(), a.label()), a.priority())));
        registry.register(timed(MULTI_STAGE, MultiStageArgs.class, OperationCategory.CRAFTING, a -> accepted(
                a.mainframe().submitNetworkMultiStage(a.pattern(), a.demand(), a.label()), a.priority())));
        registry.register(instant(ANALYZE, IndexArgs.class, OperationCategory.NETWORK, a -> {
            a.mainframe().networkIndex().analyzeIncremental(a.level(), a.mainframe().networkUuid());
            return OperationStatus.COMPLETED;
        }));
        registry.register(instant(REINDEX, IndexArgs.class, OperationCategory.NETWORK, a -> {
            a.mainframe().networkIndex().rebuild(a.level(), a.mainframe().networkUuid());
            return OperationStatus.COMPLETED;
        }));
        registry.register(instant(VACUUM, IndexArgs.class, OperationCategory.NETWORK, a -> {
            a.mainframe().networkIndex().vacuum(a.level(), a.mainframe().networkUuid());
            return OperationStatus.COMPLETED;
        }));
        registry.register(instant(DROP, DropArgs.class, OperationCategory.STORAGE, a -> {
            if (a.key() == null) {
                a.mainframe().networkIndex().dropAll(a.level(), a.mainframe().networkUuid());
            } else {
                a.mainframe().networkIndex().dropType(a.level(), a.mainframe().networkUuid(), a.key(), null);
            }
            return OperationStatus.COMPLETED;
        }));
        registry.register(instant(LOCK, LockArgs.class, OperationCategory.CONCURRENCY, a ->
                a.mainframe().lockType(a.key(), a.demand(), a.sources()) > 0L
                        ? OperationStatus.COMPLETED : OperationStatus.RESOURCE_LOCKED));
        registry.register(instant(UNLOCK, LockArgs.class, OperationCategory.CONCURRENCY, a -> {
            a.mainframe().unlockType(a.key());
            return OperationStatus.COMPLETED;
        }));
    }

    private static <T extends IOperationArgs> OperationType<T> timed(final String id, final Class<T> args,
                                                                   final OperationCategory category,
                                                                   final IOperationHandler<T> handler) {
        return new OperationType<>(id, args, category, IndustrialTier.T1, ORCHESTRATED, handler);
    }

    private static <T extends IOperationArgs> OperationType<T> instant(final String id, final Class<T> args,
                                                                     final OperationCategory category,
                                                                     final IOperationHandler<T> handler) {
        return new OperationType<>(id, args, category, IndustrialTier.T1, ORCHESTRATED, handler);
    }

    /** A submitted timed Operation is PENDING until the Mainframe ticks it; a refused one is FAILED. */
    private static OperationStatus accepted(@Nullable final INetworkOperation operation,
                                            final OperationPriority priority) {
        if (operation == null) {
            return OperationStatus.FAILED;
        }
        operation.setPriority(priority);
        return OperationStatus.PENDING;
    }
}
