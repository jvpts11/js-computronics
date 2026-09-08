/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.item;

import dev.jstech.computers.hardware.IExpansionCardSpec;

/**
 * Marks an item that goes in a computer's PCIe slot.
 */
public interface IExpansionCardItem {

    IExpansionCardSpec cardSpec();
}
