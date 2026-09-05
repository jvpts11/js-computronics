/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.uuid;

import java.util.Optional;

/**
 * Pure functions modelling network UUID propagation rules across cable connections.
 */
public final class UuidPropagation {

    private UuidPropagation(){
        //Utility Class - no instances.
    }

    public static PropagationResult propagate(
            Optional<NetworkUuid> sideA,
            Optional<NetworkUuid> sideB
    ) {
        if (sideA.isEmpty() && sideB.isEmpty()) {
            return PropagationResult.empty();
        }
        if (sideA.isEmpty()) {
            return PropagationResult.inherit(sideB.orElseThrow());
        }
        if (sideB.isEmpty()) {
            return PropagationResult.inherit(sideA.orElseThrow());
        }
        // Both present.
        var a = sideA.orElseThrow();
        var b = sideB.orElseThrow();
        if (a.equals(b)) {
            return PropagationResult.same(a);
        }
        return PropagationResult.conflict(a, b);
    }

    /**
     * Outcome of a propagation step.
     */
    public sealed interface PropagationResult
            permits PropagationResult.Empty,
            PropagationResult.Inherit,
            PropagationResult.Same,
            PropagationResult.Conflict {

        /**
         * Both endpoints are empty.
         */
        record Empty() implements PropagationResult {}

        /**
         * One endpoint had a UUID; the other now inherits it.
         */
        record Inherit(NetworkUuid uuid) implements PropagationResult {}

        /**
         * Both endpoints already share the same UUID.
         */
        record Same(NetworkUuid uuid) implements PropagationResult {}

        /**
         * Both endpoints had different UUIDs.
         */
        record Conflict(NetworkUuid first, NetworkUuid second) implements PropagationResult {}

        static PropagationResult empty() {
            return new Empty();
        }

        static PropagationResult inherit(NetworkUuid uuid) {
            return new Inherit(uuid);
        }

        static PropagationResult same(NetworkUuid uuid) {
            return new Same(uuid);
        }

        static PropagationResult conflict(NetworkUuid first, NetworkUuid second) {
            return new Conflict(first, second);
        }
    }
}
