/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import java.util.Locale;

/**
 * Builds the endpoint labels an Operation's provenance rows carry: who sent or received the data, and through
 * what. A computer reads as {@code "host (program)"}, the machine that asked and the program it asked with, so
 * a row says {@code lab-pc (Interactor) 64 > SRV-3f9a} rather than a bare program tag. A bus is its own origin
 * and its own medium, so it reads as its kind and, when it has one, its name. Pure string logic: the hosts
 * resolve their names and call in here.
 */
public final class MoveLabels {

    /** The Network Interactor desktop window. */
    public static final String INTERACTOR = "Interactor";
    /** The Command Prompt and the Linux shells. */
    public static final String SHELL = "Shell";
    /** An IQL statement run from a prompt or a job. */
    public static final String IQL = "IQL";
    /** The MC-NET terminal screen of a computer without a desktop. */
    public static final String TERMINAL = "Terminal";
    /** The Cluster Manager on a Cluster Management Computer. */
    public static final String CLUSTER_MANAGER = "Cluster Manager";

    /** The host name of a computer that has no name of its own anywhere. */
    public static final String DEFAULT_HOSTNAME = "computer";

    private MoveLabels() {
    }

    /** {@code "host (program)"}; just the host when the program is blank. */
    public static String via(final String hostname, final String program) {
        if (program == null || program.isBlank()) {
            return hostname;
        }
        return hostname + " (" + program + ")";
    }

    /** A bus's label: its kind alone, or {@code kind "name"} when the player named it. */
    public static String bus(final String kind, final String name) {
        if (name == null || name.isBlank()) {
            return kind;
        }
        return kind + " \"" + name.strip() + "\"";
    }

    /**
     * A computer's host name as the shell's {@code hostname} prints it: the name set in its console, else the
     * name given on the assembly screen, else its system's id, else {@link #DEFAULT_HOSTNAME}. A chosen name
     * is lowered and its spaces become dashes, so it reads as a host name wherever it is printed.
     */
    public static String hostname(final String consoleName, final String customName, final String osId) {
        if (consoleName != null && !consoleName.isBlank()) {
            return normalize(consoleName);
        }
        if (customName != null && !customName.isBlank()) {
            return normalize(customName);
        }
        if (osId != null && !osId.isBlank()) {
            return osId;
        }
        return DEFAULT_HOSTNAME;
    }

    private static String normalize(final String name) {
        return name.strip().toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
