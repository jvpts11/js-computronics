/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.multiblock;

/**
 * Provider of block ids for arbitrary world positions, used by {@link PatternMatcher} to query the world without depending on Minecraft classes.
 */
@FunctionalInterface
public interface BlockProvider {

    String blockAt(long encodedPos);
}
