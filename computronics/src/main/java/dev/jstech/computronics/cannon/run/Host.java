/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.run;

/**
 * The little a running program asks of the world outside it.
 *
 * <p>Time is the whole of it for now. Keeping it this narrow is what lets a program be run and read
 * back with no world at all, which is how the language is tested; the computer it really runs on
 * answers the same three questions.
 */
public interface Host {

    /** The tick the server is on. */
    long tick();

    /** How far through the day it is, in ticks. */
    long dayTime();

    /** Which day it is. */
    long day();

    /** A host for a program that has no world around it, whose clock never moves. */
    static Host still() {
        return new Host() {
            @Override
            public long tick() {
                return 0;
            }

            @Override
            public long dayTime() {
                return 0;
            }

            @Override
            public long day() {
                return 0;
            }
        };
    }
}
