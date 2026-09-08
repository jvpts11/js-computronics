/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.multiblock;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of attempting to match a {@link MultiblockPattern} against the world at a candidate controller position.
 */
public sealed interface IMatchResult permits IMatchResult.Success, IMatchResult.Failure{
    /**
     * Successful match.
     */
    record Success(Rotation rotation, List<Long> slavePositions) implements IMatchResult {
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
    ) implements IMatchResult {
        public Failure {
            Objects.requireNonNull(actualBlockId, "actualBlockId must not be null");
        }
    }
}
