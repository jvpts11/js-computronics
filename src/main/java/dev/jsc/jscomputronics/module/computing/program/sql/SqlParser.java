/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.sql;

import dev.jsc.jscomputronics.module.computing.program.cli.CliTokenizer;

import java.util.List;
import java.util.Locale;

/**
 * Compiles an operation statement into a {@link SqlOperation}. The simple dialect is a friendly verb-first shorthand; the standard dialect is a subset of real SQL over a {@code network(item, quantity, server)} table:
 * <ul>
 *   <li>{@code SELECT * FROM network [WHERE item = 'x'] [LIMIT n]} - read what is held;</li>
 *   <li>{@code SELECT n FROM network WHERE item = 'x' [AND server = 's']} - pull n of an item;</li>
 *   <li>{@code INSERT INTO network VALUES ('x', n)} - push n of an item;</li>
 *   <li>{@code DELETE n FROM network WHERE item = 'x'} - destroy n of an item.</li>
 * </ul>
 * Pure logic with no Minecraft types, so the grammar is unit-tested in plain Java.
 */
public final class SqlParser {

    private static final int DEFAULT_QUERY_LIMIT = 256;

    private SqlParser() {
    }

    public static SqlParseResult parse(final String sql, final SqlDialect dialect) {
        if (sql == null || sql.isBlank()) {
            return SqlParseResult.fail("empty statement");
        }
        return dialect == SqlDialect.STANDARD ? parseStandard(sql) : parseSimple(sql);
    }

    // --- simple dialect ---------------------------------------------------------------------------

    private static SqlParseResult parseSimple(final String sql) {
        final List<String> tokens = CliTokenizer.tokenize(sql);
        if (tokens.isEmpty()) {
            return SqlParseResult.fail("empty statement");
        }
        final SqlOperation.Verb verb = verbOf(tokens.get(0));
        if (verb == null) {
            return SqlParseResult.fail("unknown verb: " + tokens.get(0));
        }
        if (verb == SqlOperation.Verb.QUERY) {
            return SqlParseResult.ok(SqlOperation.query(join(tokens, 1, tokens.size()), DEFAULT_QUERY_LIMIT));
        }
        if (tokens.size() < 3) {
            return SqlParseResult.fail("usage: " + verb + " <quantity> <item>");
        }
        final long quantity = parseQuantity(tokens.get(1));
        if (quantity <= 0L) {
            return SqlParseResult.fail("expected a positive quantity, got '" + tokens.get(1) + "'");
        }
        final int fromIdx = indexOf(tokens, "FROM", 2);
        final int toIdx = indexOf(tokens, "TO", 2);
        final int itemEnd = firstPositive(fromIdx, toIdx, tokens.size());
        final String item = join(tokens, 2, itemEnd);
        if (item.isBlank()) {
            return SqlParseResult.fail("expected an item name");
        }
        final String source = fromIdx < 0 ? "" : join(tokens, fromIdx + 1, toIdx > fromIdx ? toIdx : tokens.size());
        final String dest = toIdx < 0 ? "" : join(tokens, toIdx + 1, tokens.size());
        if (verb == SqlOperation.Verb.MOVE && (source.isBlank() || dest.isBlank())) {
            return SqlParseResult.fail("MOVE needs FROM <server> TO <server>");
        }
        return SqlParseResult.ok(new SqlOperation(verb, item, quantity, source, dest, 0));
    }

    private static SqlOperation.Verb verbOf(final String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "select", "get", "pull" -> SqlOperation.Verb.SELECT;
            case "insert", "push", "put" -> SqlOperation.Verb.INSERT;
            case "move" -> SqlOperation.Verb.MOVE;
            case "craft", "make" -> SqlOperation.Verb.CRAFT;
            case "delete", "drop" -> SqlOperation.Verb.DELETE;
            case "query", "show", "list", "ls" -> SqlOperation.Verb.QUERY;
            default -> null;
        };
    }

    // --- standard dialect -------------------------------------------------------------------------

    private static SqlParseResult parseStandard(final String sql) {
        // Make punctuation its own token so a WHERE clause and VALUES list tokenize cleanly.
        final String spaced = sql.replace("=", " = ").replace("(", " ( ").replace(")", " ) ")
                .replace(",", " , ");
        final List<String> t = CliTokenizer.tokenize(spaced);
        if (t.isEmpty()) {
            return SqlParseResult.fail("empty statement");
        }
        final String head = t.get(0).toLowerCase(Locale.ROOT);
        return switch (head) {
            case "select" -> parseStandardSelect(t);
            case "insert" -> parseStandardInsert(t);
            case "delete" -> parseStandardDelete(t);
            default -> SqlParseResult.fail("unsupported statement: " + t.get(0)
                    + " (use SELECT, INSERT or DELETE)");
        };
    }

    private static SqlParseResult parseStandardSelect(final List<String> t) {
        final String item = whereValue(t, "item");
        final String server = whereValue(t, "server");
        final int limit = limitValue(t, DEFAULT_QUERY_LIMIT);
        // SELECT * -> a read; SELECT <n> -> a pull of n.
        if (t.size() >= 2 && t.get(1).equals("*")) {
            // Carry the WHERE server filter into the read so a scoped query honors it.
            return SqlParseResult.ok(new SqlOperation(SqlOperation.Verb.QUERY, item, 0L, server, "", limit));
        }
        final long quantity = parseQuantity(t.size() >= 2 ? t.get(1) : "");
        if (quantity <= 0L) {
            return SqlParseResult.fail("SELECT needs '*' or a positive quantity");
        }
        if (item.isBlank()) {
            return SqlParseResult.fail("SELECT a quantity needs WHERE item = '...'");
        }
        return SqlParseResult.ok(new SqlOperation(SqlOperation.Verb.SELECT, item, quantity, server, "", 0));
    }

    private static SqlParseResult parseStandardInsert(final List<String> t) {
        // INSERT INTO network VALUES ( 'item', n )  -- the first string literal is the item, the number the count.
        final String item = firstStringLiteral(t);
        final long quantity = firstNumber(t);
        if (item.isBlank() || quantity <= 0L) {
            return SqlParseResult.fail("usage: INSERT INTO network VALUES ('item', quantity)");
        }
        return SqlParseResult.ok(SqlOperation.of(SqlOperation.Verb.INSERT, item, quantity));
    }

    private static SqlParseResult parseStandardDelete(final List<String> t) {
        final String item = whereValue(t, "item");
        long quantity = parseQuantity(t.size() >= 2 ? t.get(1) : "");
        if (quantity <= 0L) {
            quantity = limitValue(t, 0);
        }
        if (item.isBlank() || quantity <= 0L) {
            return SqlParseResult.fail("usage: DELETE n FROM network WHERE item = 'x'");
        }
        return SqlParseResult.ok(SqlOperation.of(SqlOperation.Verb.DELETE, item, quantity));
    }

    /** The literal after {@code <column> =} in a WHERE clause, or {@code ""} when absent. */
    private static String whereValue(final List<String> t, final String column) {
        for (int i = 0; i + 2 < t.size(); i++) {
            if (t.get(i).equalsIgnoreCase(column) && t.get(i + 1).equals("=")) {
                return t.get(i + 2);
            }
        }
        return "";
    }

    private static int limitValue(final List<String> t, final int fallback) {
        for (int i = 0; i + 1 < t.size(); i++) {
            if (t.get(i).equalsIgnoreCase("limit")) {
                final long n = parseQuantity(t.get(i + 1));
                return n > 0L ? (int) Math.min(Integer.MAX_VALUE, n) : fallback;
            }
        }
        return fallback;
    }

    private static String firstStringLiteral(final List<String> t) {
        // After punctuation spacing, a quoted literal keeps its content as a single token; the easiest
        // reliable signal is the token right after VALUES (and an opening paren), so scan from there.
        for (int i = 0; i < t.size(); i++) {
            if (t.get(i).equalsIgnoreCase("values")) {
                for (int j = i + 1; j < t.size(); j++) {
                    final String tok = t.get(j);
                    if (!tok.equals("(") && !tok.equals(")") && !tok.equals(",") && !isNumber(tok)) {
                        return tok;
                    }
                }
            }
        }
        return "";
    }

    private static long firstNumber(final List<String> t) {
        for (int i = 0; i < t.size(); i++) {
            if (t.get(i).equalsIgnoreCase("values")) {
                for (int j = i + 1; j < t.size(); j++) {
                    if (isNumber(t.get(j))) {
                        return parseQuantity(t.get(j));
                    }
                }
            }
        }
        return -1L;
    }

    // --- shared helpers ---------------------------------------------------------------------------

    private static int indexOf(final List<String> tokens, final String keyword, final int from) {
        for (int i = from; i < tokens.size(); i++) {
            if (tokens.get(i).equalsIgnoreCase(keyword)) {
                return i;
            }
        }
        return -1;
    }

    private static int firstPositive(final int a, final int b, final int fallback) {
        int best = fallback;
        if (a >= 0) {
            best = Math.min(best, a);
        }
        if (b >= 0) {
            best = Math.min(best, b);
        }
        return best;
    }

    private static String join(final List<String> tokens, final int from, final int to) {
        if (from >= to || from >= tokens.size()) {
            return "";
        }
        return String.join(" ", tokens.subList(from, Math.min(to, tokens.size()))).trim();
    }

    private static boolean isNumber(final String s) {
        return parseQuantity(s) > 0L;
    }

    private static long parseQuantity(final String raw) {
        final String cleaned = raw.replace(",", "").replace("_", "").trim();
        if (cleaned.isEmpty()) {
            return -1L;
        }
        try {
            return Long.parseLong(cleaned);
        } catch (final NumberFormatException notANumber) {
            return -1L;
        }
    }
}
