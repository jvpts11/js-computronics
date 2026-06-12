/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program;

import net.minecraft.resources.ResourceLocation;

/**
 * An installed program: a small piece of metadata describing an application a computer can run. The id is namespaced so add-ons can ship their own programs; {@code commandName} is the short word the Command Prompt's {@code run} verb and {@code programs} listing use; {@code preinstalled} marks the ones every computer ships with (the Command Prompt itself). How a program is opened on the client is registered separately (in the client layer), keeping this record free of rendering types.
 */
public record Program(ResourceLocation id, String commandName, String titleKey, boolean preinstalled) {

    public Program {
        if (id == null) {
            throw new IllegalArgumentException("program id must not be null");
        }
        if (commandName == null || commandName.isBlank()) {
            commandName = id.getPath();
        }
        if (titleKey == null || titleKey.isBlank()) {
            titleKey = "program." + id.getNamespace() + "." + id.getPath();
        }
    }
}
