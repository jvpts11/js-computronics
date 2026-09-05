/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.clienttest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a client test: a {@code public static void name(ClientTestContext ctx)} method that builds a
 * sequence of steps driven on the real client (world, integrated server, screens, input). Discovered by
 * {@link ClientTestRunner} from the classes listed in {@link ClientTestSuite}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ClientTest {

    /** Ticks the whole test may take before it is failed as timed out. */
    int timeoutTicks() default 1200;
}
