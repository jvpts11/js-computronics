/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.cli;

import java.util.List;

/**
 * The view of a computer and its network that a CLI command operates on. It is a deliberately small, Minecraft-free facade: the server backs it with the real Mainframe and storage, while tests back it with a fake, so command logic is unit-testable without a running game. Every effecting call returns an {@link OpResult} describing what happened, never throwing for an ordinary failure (unknown item, no network), so commands can print a clean message.
 */
public interface CliComputer {

    String name();

    /** A human label for the computer kind, e.g. {@code "Personal Computer"} or {@code "Mainframe"}. */
    String type();

    /** A short, stable identifier for this node (the head of its UUID), for display. */
    String nodeId();

    boolean running();

    /** Orchestration capacity in items per tick. */
    long cpuCapacity();

    /** RAM buffer in items. */
    long ramBuffer();

    boolean onNetwork();

    /** A short identifier for the network this computer is on, or {@code ""} when unlinked. */
    String networkId();

    /** Whether this computer is itself the network's Mainframe (gates maintenance commands). */
    boolean isMainframe();

    NetSummary network();

    /**
     * Items the network holds, optionally filtered by a case-insensitive name substring.
     *
     * @param filter a name fragment, or {@code ""} for everything
     * @param limit  the maximum number of rows to return
     */
    List<StoredItem> query(String filter, int limit);

    /** Which servers hold the named item and how much each has. */
    List<Holding> find(String item);

    /** Pull items from the network into this computer's local storage. */
    OpResult select(String item, long quantity);

    /** Push items from this computer's local storage into the network. */
    OpResult insert(String item, long quantity);

    /** Ask the network to craft the named item. */
    OpResult craft(String item, long quantity);

    /** Place a standing hold on the named item so concurrent operations WAIT on it. */
    OpResult lock(String item, long quantity);

    /** Release the standing hold on the named item. */
    OpResult unlock(String item);

    /** The item types currently held by a manual lock, and how much each holds. */
    List<StoredItem> locks();

    /** The operations currently in flight on the network. */
    List<ActiveOp> activeOps();

    /**
     * Run an index-maintenance action (analyze / reindex / vacuum) on the Mainframe.
     *
     * @return a result whose message describes the outcome, or a failure when this computer is not a Mainframe
     */
    OpResult maintenance(String action);

    /** One-line summaries of the peripherals linked to this computer (monitors, drives, ...). */
    List<String> peripherals();

    /** The programs installed on this computer (kept here, not in the CLI layer, so the engine stays Minecraft-free). */
    List<ProgramInfo> programs();

    /** Installs a program on this computer by id. */
    OpResult install(String programId);

    /** The SQL dialect the {@code operation} verb should parse, from the server config. */
    dev.jsc.jscomputronics.module.computing.program.sql.SqlDialect dialect();

    /** Runs a parsed effecting operation (SELECT/INSERT/MOVE/CRAFT/DELETE) against the network. */
    OpResult execute(dev.jsc.jscomputronics.module.computing.program.sql.SqlOperation operation);

    /** Counts that describe the network at a glance. */
    record NetSummary(boolean linked, int servers, int personalComputers, int subframes,
                      int indexedTypes, boolean mainframePresent) {
    }

    /** A line in a storage listing. */
    record StoredItem(String name, long quantity) {
    }

    /** How much of an item one server holds. */
    record Holding(String server, long quantity) {
    }

    /** A snapshot of one in-flight operation. */
    record ActiveOp(String type, String item, long progress, long total, String status) {
    }

    /** A program installed on the computer: its short command name and full id, for listing. */
    record ProgramInfo(String name, String id) {
    }

    /** The outcome of an effecting command: whether it was accepted, and a message to print. */
    record OpResult(boolean ok, String message) {

        public static OpResult ok(final String message) {
            return new OpResult(true, message);
        }

        public static OpResult fail(final String message) {
            return new OpResult(false, message);
        }
    }
}
