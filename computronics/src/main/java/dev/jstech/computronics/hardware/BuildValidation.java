/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.hardware;

import java.util.List;

/**
 * Result of validating a {@link ComputerBuild}: whether it can power on, and, if not, the human-readable reasons why.
 */
public record BuildValidation(boolean valid, List<String> problems) {

    public BuildValidation {
        problems = List.copyOf(problems);
    }
}
