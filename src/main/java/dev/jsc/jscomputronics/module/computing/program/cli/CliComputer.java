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
     * Items the network holds, optionally filtered by a case-insensitive name substring and scoped to a
     * single server by name.
     *
     * @param filter a name fragment, or {@code ""} for everything
     * @param server a server name to scope the read to, or {@code ""} for the whole network
     * @param limit  the maximum number of rows to return
     */
    List<StoredItem> query(dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition where,
                           String server, int limit);

    /**
     * Rows for a {@code QUERY <object>} against a schema object: {@code items} (name → quantity),
     * {@code servers} (name → items stored), {@code operations} (description → progress). An object that is
     * not wired yet returns an empty list. The label/value pair is what the studio grid and the CLI render.
     *
     * @param object the schema object name (e.g. {@code "items"}, {@code "servers"}, {@code "operations"})
     * @param filter a name fragment, or {@code ""} for everything
     * @param server a server name to scope the read to, or {@code ""} for the whole network
     * @param limit  the maximum number of rows to return
     */
    List<StoredItem> queryObject(String object,
                                 dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition where,
                                 String server, int limit);

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

    /** Runs a parsed effecting IQL statement (SELECT/INSERT/MOVE/CRAFT/DELETE/DROP/LOCK/...) against the network. */
    OpResult execute(dev.jsc.jscomputronics.module.computing.program.iql.IqlOperation operation);

    /** Controls the network's IQL Engine service: {@code install}/{@code start}/{@code stop}/{@code status}. */
    default OpResult engineControl(final String action) {
        return OpResult.fail("the IQL Engine can only be controlled from a networked computer");
    }

    /** The services the network exposes (the IQL Engine, and any future service) with their current state. */
    default List<ServiceStatus> services() {
        return List.of();
    }

    /** Whether the network's IQL Engine is installed — gates the Engine's own commands in the prompt. */
    default boolean iqlEngineInstalled() {
        return false;
    }

    // -------------------------------------------------------------------------
    // Filesystem (system disk)
    // -------------------------------------------------------------------------

    /**
     * Lists the files visible in the current directory of the host computer's system disk.
     *
     * <p>Returns a {@link FsResult} whose {@link FsResult#entries()} carries the listing, or a failure
     * message when no system disk or OS is present. The directory parameter follows the filesystem
     * kind of the installed OS kernel: {@code ""} for the root in FLAT and HIERARCHICAL.
     *
     * @param dir the directory to list ({@code ""} for the root)
     */
    default FsResult listDisk(final String dir) {
        return FsResult.noOs();
    }

    /**
     * Reads the content of a file on the host computer's system disk.
     *
     * <p>Returns a {@link FsResult} carrying the file text, or a failure message when the file is
     * absent, is a read-only {@code .dat} projection, or no system disk is present.
     *
     * @param path the file path relative to the root of the system disk
     */
    default FsResult readFile(final String path) {
        return FsResult.noOs();
    }

    /**
     * Deletes a file from the host computer's system disk.
     *
     * <p>Returns a {@link FsResult} with a confirmation message on success, or a failure message when the
     * file does not exist, is a read-only {@code .dat} projection, or no system disk is present.
     *
     * @param path the file path to delete
     */
    default FsResult deleteFile(final String path) {
        return FsResult.noOs();
    }

    /**
     * Writes (creates or overwrites) a user file on the host computer's system disk.
     *
     * <p>Returns a {@link FsResult} with a confirmation message on success, or a failure message when the
     * path is invalid for the filesystem, the file type is not user-editable, the disk is full, or no
     * system disk is present.
     *
     * @param path    the file path to write
     * @param content the UTF-8 text content
     */
    default FsResult writeFile(final String path, final String content) {
        return FsResult.noOs();
    }

    /**
     * Runs the content of an {@code .iql} file from the host computer's system disk as an IQL
     * statement, routing it through the same dispatch path as the {@code operation} command.
     *
     * <p>Returns a failure when the file is absent, is not an {@code .iql} file, or no system disk
     * is present. On success returns the same {@link OpResult} the engine would have returned.
     *
     * @param path the file path of the IQL script to execute
     */
    default FsResult runScript(final String path) {
        return FsResult.noOs();
    }

    /** One directory entry returned by {@link #listDisk}. */
    record FsEntry(String path, String ext, long weightMbEq, boolean readOnly) {}

    /**
     * The result of a filesystem operation: either a success carrying optional entries + an optional
     * text payload + an optional {@link OpResult}, or a failure carrying an error message.
     */
    final class FsResult {

        private final boolean ok;
        private final String message;
        private final java.util.List<FsEntry> entries;
        private final OpResult opResult;

        private FsResult(final boolean ok, final String message, final java.util.List<FsEntry> entries,
                         final OpResult opResult) {
            this.ok = ok;
            this.message = message;
            this.entries = entries != null ? entries : java.util.List.of();
            this.opResult = opResult;
        }

        /** A listing result carrying one or more directory entries. */
        public static FsResult listing(final java.util.List<FsEntry> entries) {
            return new FsResult(true, "", entries, null);
        }

        /** A text-content result (for {@code type} / {@code cat}). */
        public static FsResult content(final String text) {
            return new FsResult(true, text, null, null);
        }

        /** A simple confirmation message (for {@code del}). */
        public static FsResult ok(final String message) {
            return new FsResult(true, message, null, null);
        }

        /** An IQL execution result forwarded from the dispatcher. */
        public static FsResult iqlResult(final OpResult result) {
            return new FsResult(result.ok(), result.message(), null, result);
        }

        /** A standard failure with a human-readable message. */
        public static FsResult fail(final String message) {
            return new FsResult(false, message, null, null);
        }

        /** Returned when no OS or system disk is present. */
        public static FsResult noOs() {
            return fail("no system disk or OS installed");
        }

        public boolean ok() { return ok; }
        public String message() { return message; }
        public java.util.List<FsEntry> entries() { return entries; }
        /** The forwarded IQL result when this is an {@link #iqlResult}, or {@code null} otherwise. */
        public OpResult opResult() { return opResult; }
    }

    /** Counts that describe the network at a glance. */
    record NetSummary(boolean linked, int servers, int personalComputers, int subframes,
                      int indexedTypes, boolean mainframePresent) {
    }

    /** A line in a storage listing: a name, a quantity, and an optional detail (e.g. where the item lives). */
    record StoredItem(String name, long quantity, String detail) {

        public StoredItem(final String name, final long quantity) {
            this(name, quantity, "");
        }
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

    /** A service the computer hosts and its state, for the process/service manager (e.g. "running"). */
    record ServiceStatus(String name, String state) {
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
