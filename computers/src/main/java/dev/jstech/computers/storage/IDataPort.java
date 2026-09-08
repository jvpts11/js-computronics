/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.storage;

/**
 * A machine-side port: something the network can push data into, pull data out of, and inspect. One machine
 * face is a port; so is a set of faces reached through several buses.
 */
public interface IDataPort extends IDataSink, IDataSource {

    /** How much of {@code key} the machine currently holds behind this port. */
    long count(StorageKey key);

    /** True when no capability at all backs this port (the machine is gone or unreachable). */
    boolean isEmpty();
}
