/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.common.hardware.ExpansionCardSpec;

/**
 * Marks an item that goes in a computer's PCIe slot.
 */
public interface ExpansionCardItem {

    ExpansionCardSpec cardSpec();
}
