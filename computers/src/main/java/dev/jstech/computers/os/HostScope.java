/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os;

/**
 * Where a program is allowed to exist. A program gated to a specific host only installs on, and only
 * shows a launcher on, that kind of computer; {@link #ANY} places no restriction. This single field
 * replaces the scattered per-program install/launcher special cases (e.g. the Crafting Manager only on
 * a Crafting Computer, the Network Manager and the Mainframe services only on a Mainframe).
 */
public enum HostScope {

    /** No host restriction: the program may exist on any computer that meets its platform and specs. */
    ANY,

    /** Only on a Crafting Computer (the recipe-store host). */
    CRAFTING_COMPUTER,

    /** Only on the Mainframe (the network's orchestrator, holder of the network index). */
    MAINFRAME,

    /** Only on a server mounted in a rack, where the headless server services live. */
    SERVER,

    /** Only on a Cluster Management Computer (the master of the racks on its network). */
    CLUSTER_MANAGEMENT_COMPUTER
}
