/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program;

import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.program.cli.ICliComputer;
import dev.jstech.computronics.program.iql.IIqlCondition;
import dev.jstech.computronics.program.iql.IqlDefinition;
import dev.jstech.computronics.program.iql.IqlDefinitionParser;
import dev.jstech.computronics.program.iql.IqlOperation;
import dev.jstech.computronics.program.iql.IqlParseResult;
import dev.jstech.computronics.program.iql.IqlParser;
import dev.jstech.computronics.program.iql.IqlSavedObject;
import dev.jstech.computronics.program.iql.IqlVerb;

import java.util.List;
import java.util.Locale;

/**
 * The IQL Engine's runtime: it takes a statement and either runs it as an immediate action/query (via the
 * {@link ICliComputer}) or, for a Layer-2 statement, stores/runs a saved object against the Mainframe's
 * catalog. CREATE/DROP touch the catalog; EXEC runs a procedure's statements in order, stopping at the
 * first error (the chosen default); a QUERY whose object is a view name runs the saved query. Touching the
 * catalog requires the Engine to be installed and running on the Mainframe; ad-hoc actions do not.
 */
public final class IqlEngine {

    private static final int RECURSION_GUARD = 32;

    private final MainframeBlockEntity mainframe;
    private final ICliComputer computer;
    private final int queryRowLimit;

    public IqlEngine(final MainframeBlockEntity mainframe, final ICliComputer computer, final int queryRowLimit) {
        this.mainframe = mainframe;
        this.computer = computer;
        this.queryRowLimit = queryRowLimit;
    }

    /** The result of running a statement: a status, a message, and (for a read) the result rows. */
    public record Outcome(boolean ok, String message, List<ICliComputer.StoredItem> rows) {

        static Outcome ok(final String message) {
            return new Outcome(true, message, List.of());
        }

        static Outcome fail(final String message) {
            return new Outcome(false, message, List.of());
        }

        static Outcome rows(final List<ICliComputer.StoredItem> rows) {
            return new Outcome(true, rows.size() + (rows.size() == 1 ? " row" : " rows"), rows);
        }
    }

    public Outcome run(final String statement) {
        return run(statement, 0);
    }

    private Outcome run(final String statement, final int depth) {
        if (depth > RECURSION_GUARD) {
            return Outcome.fail("IQL recursion too deep (a procedure or view referencing itself?)");
        }
        final IqlParseResult parsed = IqlParser.tryParse(statement);
        if (!parsed.ok()) {
            return Outcome.fail("syntax: " + parsed.error());
        }
        if (parsed.isDefinition()) {
            return runDefinition(parsed.definition(), depth);
        }
        return runOperation(parsed.operation(), depth);
    }

    private Outcome runDefinition(final IqlDefinition definition, final int depth) {
        if (!mainframe.isIqlEngineActive()) {
            return Outcome.fail("the IQL Engine is not running on the Mainframe, install and start it first");
        }
        return switch (definition.verb()) {
            case CREATE -> create(definition);
            case DROP -> drop(definition);
            case EXEC -> runProcedure(definition.name(), depth);
        };
    }

    private Outcome create(final IqlDefinition definition) {
        mainframe.iqlCatalog().put(IqlSavedObject.from(definition));
        mainframe.markIqlCatalogChanged();
        return Outcome.ok(typeName(definition.objectType()) + " " + definition.name() + " created");
    }

    private Outcome drop(final IqlDefinition definition) {
        if (!mainframe.iqlCatalog().remove(definition.objectType(), definition.name())) {
            return Outcome.fail("no " + typeName(definition.objectType()) + " named " + definition.name());
        }
        mainframe.markIqlCatalogChanged();
        return Outcome.ok(typeName(definition.objectType()) + " " + definition.name() + " dropped");
    }

    private Outcome runProcedure(final String name, final int depth) {
        final IqlSavedObject procedure = mainframe.iqlCatalog().get(IqlDefinition.ObjectType.PROCEDURE, name);
        if (procedure == null) {
            return Outcome.fail("no procedure named " + name);
        }
        final List<String> statements = IqlDefinitionParser.splitBody(procedure.body());
        int ran = 0;
        for (final String statement : statements) {
            final Outcome result = run(statement, depth + 1);
            if (!result.ok()) {
                return Outcome.fail("procedure " + name + " stopped at statement " + (ran + 1) + ": "
                        + result.message());
            }
            ran++;
        }
        return Outcome.ok("procedure " + name + " ran " + ran + (ran == 1 ? " statement" : " statements"));
    }

    private Outcome runOperation(final IqlOperation operation, final int depth) {
        if (operation.verb() == IqlVerb.QUERY || operation.verb() == IqlVerb.COUNT) {
            final IqlSavedObject view =
                    mainframe.iqlCatalog().get(IqlDefinition.ObjectType.VIEW, operation.item());
            if (view != null) {
                return run(view.body(), depth + 1); // QUERY <view> runs the saved query
            }
            final int limit = operation.limit() > 0 ? operation.limit() : queryRowLimit;
            // Pass the whole WHERE so the read filters on every field (qty/name/damaged/...), not just name.
            return Outcome.rows(computer.queryObject(operation.item(), operation.where(), "", limit));
        }
        final ICliComputer.OpResult result = computer.execute(operation);
        return new Outcome(result.ok(), result.message(), List.of());
    }

    private static String typeName(final IqlDefinition.ObjectType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
