/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.modonomicon;

import net.neoforged.fml.ModList;

/**
 * Entry point for the optional Modonomicon guidebook integration.
 */
public final class ModonomiconIntegration {

    private ModonomiconIntegration() {
    }

    public static final String MODONOMICON_MOD_ID = "modonomicon";

    public static final String BOOK_ID = "jsc:guide";

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODONOMICON_MOD_ID);
    }
}