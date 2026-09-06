/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.clienttest;

/** A failed assertion or timeout inside a client test; the runner records it and moves on. */
public final class ClientTestFailure extends RuntimeException {

    public ClientTestFailure(final String message) {
        super(message);
    }

    public ClientTestFailure(final String message, final Throwable cause) {
        super(message, cause);
    }
}
