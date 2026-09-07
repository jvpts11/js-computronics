/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.event;

import dev.jstech.core.uuid.NetworkUuid;

import java.util.Objects;
import java.util.UUID;

/**
 * The life of one Operation on a network, as an orchestrator reports it on the series' event bus: created
 * when it is accepted, started the first tick it runs, then exactly one of completed, failed or discarded.
 * {@code operationId} is the Operation's own identity and {@code typeId} the registered type it belongs to
 * (such as {@code jsc:select}), so a listener can follow one Operation or react to a whole kind.
 */
public sealed interface IOperationLifecycleEvent extends ICoreEvent
        permits IOperationLifecycleEvent.Created,
        IOperationLifecycleEvent.Started,
        IOperationLifecycleEvent.Completed,
        IOperationLifecycleEvent.Failed,
        IOperationLifecycleEvent.Discarded {

    NetworkUuid networkUuid();

    UUID operationId();

    String typeId();

    /** The orchestrator accepted the Operation; it may queue before it runs. */
    record Created(NetworkUuid networkUuid, UUID operationId, String typeId)
            implements IOperationLifecycleEvent {

        public Created {
            Objects.requireNonNull(networkUuid, "networkUuid must not be null");
            Objects.requireNonNull(operationId, "operationId must not be null");
            Objects.requireNonNull(typeId, "typeId must not be null");
        }

        @Override
        public String eventId() {
            return "operation.lifecycle.created";
        }
    }

    /** The Operation got its first tick of work. */
    record Started(NetworkUuid networkUuid, UUID operationId, String typeId)
            implements IOperationLifecycleEvent {

        public Started {
            Objects.requireNonNull(networkUuid, "networkUuid must not be null");
            Objects.requireNonNull(operationId, "operationId must not be null");
            Objects.requireNonNull(typeId, "typeId must not be null");
        }

        @Override
        public String eventId() {
            return "operation.lifecycle.started";
        }
    }

    /** The Operation delivered everything it was asked for; {@code durationTicks} spans queue and work. */
    record Completed(NetworkUuid networkUuid, UUID operationId, String typeId, long durationTicks)
            implements IOperationLifecycleEvent {

        public Completed {
            Objects.requireNonNull(networkUuid, "networkUuid must not be null");
            Objects.requireNonNull(operationId, "operationId must not be null");
            Objects.requireNonNull(typeId, "typeId must not be null");
            if (durationTicks < 0) {
                throw new IllegalArgumentException(
                        "durationTicks must be >= 0; got " + durationTicks);
            }
        }

        @Override
        public String eventId() {
            return "operation.lifecycle.completed";
        }
    }

    /** The Operation fell short on its own (a partial delivery, a timeout, a failure); {@code reason} says how. */
    record Failed(NetworkUuid networkUuid, UUID operationId, String typeId, String reason)
            implements IOperationLifecycleEvent {

        public Failed {
            Objects.requireNonNull(networkUuid, "networkUuid must not be null");
            Objects.requireNonNull(operationId, "operationId must not be null");
            Objects.requireNonNull(typeId, "typeId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }

        @Override
        public String eventId() {
            return "operation.lifecycle.failed";
        }
    }

    /** Somebody stopped the Operation, or the orchestrator dropped it (a power-off, an expired resume). */
    record Discarded(NetworkUuid networkUuid, UUID operationId, String typeId)
            implements IOperationLifecycleEvent {

        public Discarded {
            Objects.requireNonNull(networkUuid, "networkUuid must not be null");
            Objects.requireNonNull(operationId, "operationId must not be null");
            Objects.requireNonNull(typeId, "typeId must not be null");
        }

        @Override
        public String eventId() {
            return "operation.lifecycle.discarded";
        }
    }
}
