/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.edit;

import dev.jstech.computronics.cannon.sem.BuiltIns;
import dev.jstech.computronics.cannon.sem.IMemberSymbol;
import dev.jstech.computronics.cannon.sem.NamedType;
import dev.jstech.computronics.cannon.sem.SemanticModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What an editor can offer the player after they type a name and a dot.
 *
 * <p>The checker already knows every type the language brings and every type the program declares, with
 * the members of each and the types they take. An editor that asked the player to remember all of it
 * while the compiler had it written down would be making them do the machine's work. So this reads the
 * same tables the checker reads, and hands back a list ready to show.
 *
 * <p>Nothing here touches the world, which is the point: the same list is built the same way whether it
 * is for a window on a monitor or for a test.
 */
public final class CannonCompletions {

    /** What a candidate is, which is what an editor draws its little mark from. */
    public enum Sort { METHOD, PROPERTY, FIELD, EVENT, TYPE }

    /**
     * One candidate.
     *
     * @param label     what to insert, and what to list it under
     * @param signature the whole shape, with what it takes and what it gives back
     * @param sort      what kind of member it is
     * @param owner     the type that declares it, for the line under the list
     */
    public record Item(String label, String signature, Sort sort, String owner) {
    }

    private CannonCompletions() {
    }

    /**
     * The members of the type called {@code receiver} whose names begin with {@code prefix}.
     *
     * <p>{@code staticSide} tells the two halves of a type apart: a name written straight into the
     * source, as in {@code Network.}, can only reach what belongs to the type, while a variable of that
     * type reaches the rest. Matching ignores case, because a player who typed {@code net} is looking
     * for {@code Network} and telling them otherwise helps nobody.
     */
    public static List<Item> members(final BuiltIns builtIns, final SemanticModel model,
                                     final String receiver, final String prefix, final boolean staticSide) {
        final NamedType type = type(builtIns, model, receiver);
        if (type == null) {
            return List.of();
        }
        final String wanted = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        final Set<String> seen = new LinkedHashSet<>();
        final List<Item> items = new ArrayList<>();
        for (final IMemberSymbol member : type.allMembers()) {
            if (member instanceof IMemberSymbol.ConstructorSymbol || member.isStatic() != staticSide) {
                continue;
            }
            final String name = member.name();
            if (!name.toLowerCase(Locale.ROOT).startsWith(wanted) || !seen.add(name + signatureOf(member))) {
                continue;
            }
            items.add(new Item(name, signatureOf(member), sortOf(member), member.owner().name()));
        }
        items.sort(Comparator.comparing(Item::label).thenComparing(Item::signature));
        return items;
    }

    /**
     * The types whose names begin with {@code prefix}: the ones the language brings and the ones the
     * program declares, with the program's own first when a name is in both.
     */
    public static List<Item> types(final BuiltIns builtIns, final SemanticModel model, final String prefix) {
        final String wanted = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        final Set<String> seen = new LinkedHashSet<>();
        final List<Item> items = new ArrayList<>();
        for (final NamedType type : declared(model)) {
            offer(items, seen, type, wanted, "this program");
        }
        if (builtIns != null) {
            for (final NamedType type : builtIns.all()) {
                offer(items, seen, type, wanted, "Cannon");
            }
        }
        items.sort(Comparator.comparing(Item::label));
        return items;
    }

    /** The type of that name: the program's own before the language's, since a program may shadow one. */
    private static NamedType type(final BuiltIns builtIns, final SemanticModel model, final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        final NamedType own = model == null ? null : model.declaredType(name);
        if (own != null) {
            return own;
        }
        return builtIns == null ? null : builtIns.type(name, 0);
    }

    private static List<NamedType> declared(final SemanticModel model) {
        return model == null ? List.of() : model.declaredTypes();
    }

    private static void offer(final List<Item> items, final Set<String> seen, final NamedType type,
                              final String wanted, final String owner) {
        if (type == null || !type.name().toLowerCase(Locale.ROOT).startsWith(wanted) || !seen.add(type.name())) {
            return;
        }
        items.add(new Item(type.name(), type.describe(), Sort.TYPE, owner));
    }

    private static Sort sortOf(final IMemberSymbol member) {
        return switch (member) {
            case IMemberSymbol.MethodSymbol ignored -> Sort.METHOD;
            case IMemberSymbol.PropertySymbol ignored -> Sort.PROPERTY;
            case IMemberSymbol.FieldSymbol ignored -> Sort.FIELD;
            case IMemberSymbol.EventSymbol ignored -> Sort.EVENT;
            case IMemberSymbol.ConstructorSymbol ignored -> Sort.METHOD;
        };
    }

    /** The member written the way the popup shows it, with what it takes and what it gives back. */
    private static String signatureOf(final IMemberSymbol member) {
        return switch (member) {
            case IMemberSymbol.MethodSymbol method -> {
                final StringBuilder text = new StringBuilder(method.name()).append('(');
                for (int i = 0; i < method.parameters().size(); i++) {
                    final IMemberSymbol.ParameterSymbol parameter = method.parameters().get(i);
                    text.append(i > 0 ? ", " : "")
                            .append(parameter.outward() ? "out " : "")
                            .append(parameter.type().describe());
                }
                yield text.append(')').append(" : ").append(method.returnType().describe()).toString();
            }
            case IMemberSymbol.PropertySymbol property ->
                    property.name() + " : " + property.type().describe();
            case IMemberSymbol.FieldSymbol field ->
                    field.name() + " : " + field.type().describe();
            case IMemberSymbol.EventSymbol event ->
                    event.name() + " : " + event.delegateType().name();
            case IMemberSymbol.ConstructorSymbol constructor -> constructor.name() + "()";
        };
    }
}
