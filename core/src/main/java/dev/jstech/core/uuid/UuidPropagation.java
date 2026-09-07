/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.uuid;

import java.util.Optional;

/**
 * Pure functions modelling network UUID propagation rules across cable connections.
 */
public final class UuidPropagation {

    private UuidPropagation(){
        //Utility Class - no instances.
    }

    public static IPropagationResult propagate(
            Optional<NetworkUuid> sideA,
            Optional<NetworkUuid> sideB
    ) {
        if (sideA.isEmpty() && sideB.isEmpty()) {
            return IPropagationResult.empty();
        }
        if (sideA.isEmpty()) {
            return IPropagationResult.inherit(sideB.orElseThrow());
        }
        if (sideB.isEmpty()) {
            return IPropagationResult.inherit(sideA.orElseThrow());
        }
        // Both present.
        var a = sideA.orElseThrow();
        var b = sideB.orElseThrow();
        if (a.equals(b)) {
            return IPropagationResult.same(a);
        }
        return IPropagationResult.conflict(a, b);
    }

    /**
     * Outcome of a propagation step.
     */
    public sealed interface IPropagationResult
            permits IPropagationResult.Empty,
            IPropagationResult.Inherit,
            IPropagationResult.Same,
            IPropagationResult.Conflict {

        /**
         * Both endpoints are empty.
         */
        record Empty() implements IPropagationResult {}

        /**
         * One endpoint had a UUID; the other now inherits it.
         */
        record Inherit(NetworkUuid uuid) implements IPropagationResult {}

        /**
         * Both endpoints already share the same UUID.
         */
        record Same(NetworkUuid uuid) implements IPropagationResult {}

        /**
         * Both endpoints had different UUIDs.
         */
        record Conflict(NetworkUuid first, NetworkUuid second) implements IPropagationResult {}

        static IPropagationResult empty() {
            return new Empty();
        }

        static IPropagationResult inherit(NetworkUuid uuid) {
            return new Inherit(uuid);
        }

        static IPropagationResult same(NetworkUuid uuid) {
            return new Same(uuid);
        }

        static IPropagationResult conflict(NetworkUuid first, NetworkUuid second) {
            return new Conflict(first, second);
        }
    }
}
