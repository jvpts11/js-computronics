/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.event;

import dev.jstech.core.uuid.NetworkUuid;

import java.util.Objects;

/**
 * Canonical events fired during the lifecycle of an Operation.
 */
public sealed interface OperationLifecycleEvent extends CoreEvent
        permits OperationLifecycleEvent.Created,
        OperationLifecycleEvent.Started,
        OperationLifecycleEvent.Completed,
        OperationLifecycleEvent.Failed {

    NetworkUuid networkUuid();

    long operationId();

    /**
     * An Operation has been validated and queued for execution but not yet started.
     */
    record Created(NetworkUuid networkUuid, long operationId)
            implements OperationLifecycleEvent {

        public Created {
            Objects.requireNonNull(networkUuid, "networkUuid must not be null");
        }

        @Override
        public String eventId() {
            return "operation.lifecycle.created";
        }
    }

    /**
     * Execution of the Operation has begun.
     */
    record Started(NetworkUuid networkUuid, long operationId)
            implements OperationLifecycleEvent {

        public Started {
            Objects.requireNonNull(networkUuid, "networkUuid must not be null");
        }

        @Override
        public String eventId() {
            return "operation.lifecycle.started";
        }
    }

    /**
     * The Operation finished successfully.
     */
    record Completed(NetworkUuid networkUuid, long operationId,
                     long durationTicks) implements OperationLifecycleEvent {

        public Completed {
            Objects.requireNonNull(networkUuid, "networkUuid must not be null");
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

    /**
     * The Operation failed (resource missing, lock collision, etc.).
     */
    record Failed(NetworkUuid networkUuid, long operationId,
                  String reason) implements OperationLifecycleEvent {

        public Failed {
            Objects.requireNonNull(networkUuid, "networkUuid must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }

        @Override
        public String eventId() {
            return "operation.lifecycle.failed";
        }
    }
}
