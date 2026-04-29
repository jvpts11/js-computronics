/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.multiblock;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of attempting to match a {@link MultiblockPattern} against the world at a candidate controller position.
 */
public sealed interface MatchResult permits MatchResult.Success, MatchResult.Failure{
    /**
     * Successful match.
     */
    record Success(Rotation rotation, List<Long> slavePositions) implements MatchResult {
        public Success {
            Objects.requireNonNull(rotation, "rotation must not be null");
            Objects.requireNonNull(slavePositions, "slavePositions must not be null");
            slavePositions = List.copyOf(slavePositions);
        }
    }

    /**
     * Failed match.
     */
    record Failure(
            int relX, int relY, int relZ,
            char expectedChar,
            String actualBlockId
    ) implements MatchResult {
        public Failure {
            Objects.requireNonNull(actualBlockId, "actualBlockId must not be null");
        }
    }
}
