/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.iql;

/**
 * A parsed IQL Layer-2 statement: a CREATE/DROP of a saved object (a view, a procedure, or a job) or an
 * EXEC of a procedure. Where an {@link IqlOperation} is one immediate action, an {@code IqlDefinition}
 * names an object the IQL Engine stores and later runs. Pure logic, no Minecraft.
 *
 * <p>{@code body} is the raw text after {@code AS} (a query for a view, a {@code { ... }} block for a
 * procedure, a statement or procedure name for a job) — kept verbatim and re-parsed when the object runs,
 * so the catalog persists exactly what the player wrote. {@code triggerKind}/{@code triggerSpec} carry a
 * job's {@code EVERY <duration>} or {@code WHEN <condition>}; both are empty/NONE for views and procedures.
 */
public record IqlDefinition(Verb verb, ObjectType objectType, String name, String body,
                            TriggerKind triggerKind, String triggerSpec) {

    /** What the statement does to the catalog. */
    public enum Verb { CREATE, DROP, EXEC }

    /** The kind of saved object. {@code NONE} only for EXEC, which names a procedure. */
    public enum ObjectType { VIEW, PROCEDURE, JOB, NONE }

    /** A job's trigger family: a fixed interval, a condition, or none (views/procedures). */
    public enum TriggerKind { NONE, EVERY, WHEN }

    public IqlDefinition {
        if (name == null) {
            name = "";
        }
        if (body == null) {
            body = "";
        }
        if (triggerSpec == null) {
            triggerSpec = "";
        }
    }

    public static IqlDefinition create(final ObjectType type, final String name, final String body,
                                       final TriggerKind triggerKind, final String triggerSpec) {
        return new IqlDefinition(Verb.CREATE, type, name, body, triggerKind, triggerSpec);
    }

    public static IqlDefinition drop(final ObjectType type, final String name) {
        return new IqlDefinition(Verb.DROP, type, name, "", TriggerKind.NONE, "");
    }

    public static IqlDefinition exec(final String name) {
        return new IqlDefinition(Verb.EXEC, ObjectType.PROCEDURE, name, "", TriggerKind.NONE, "");
    }

    public boolean hasTrigger() {
        return triggerKind != TriggerKind.NONE;
    }
}
