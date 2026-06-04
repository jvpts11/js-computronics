/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.config;

/**
 * Minimal logging contract for {@link ConfigValidator}, allowing tests to capture log output without an SLF4J runtime.
 */
@FunctionalInterface
public interface ConfigLogger {

    void warn(String message);

    ConfigLogger NOOP = msg -> {};
}
