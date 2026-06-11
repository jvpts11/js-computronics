/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

/**
 * The category of a PCIe expansion card, so a build can tell what an installed card <em>does</em> even though every card shares the same slot.
 */
public enum ExpansionCardKind {

    GPU,

    CRAFTING,

    PHI
}
