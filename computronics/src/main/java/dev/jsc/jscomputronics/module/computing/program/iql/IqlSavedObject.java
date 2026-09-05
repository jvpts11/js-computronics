/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.iql;

/**
 * A saved IQL object as it lives in the IQL Engine's catalog: a view, a procedure, or a job, with the raw
 * body the player wrote (re-parsed when it runs) and, for a job, its trigger. Pure logic, no Minecraft.
 * This is what an {@link IqlDefinition} of verb {@code CREATE} becomes once the Engine stores it.
 */
public record IqlSavedObject(IqlDefinition.ObjectType type, String name, String body,
                             IqlDefinition.TriggerKind triggerKind, String triggerSpec) {

    public static IqlSavedObject from(final IqlDefinition definition) {
        return new IqlSavedObject(definition.objectType(), definition.name(), definition.body(),
                definition.triggerKind(), definition.triggerSpec());
    }

    public boolean isJob() {
        return type == IqlDefinition.ObjectType.JOB;
    }

    public boolean hasTrigger() {
        return triggerKind != IqlDefinition.TriggerKind.NONE;
    }
}
