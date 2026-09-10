/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.edit;

import dev.jstech.computers.cannon.ast.CompilationUnit;
import dev.jstech.computers.cannon.ast.IDecl;
import dev.jstech.computers.cannon.sem.BuiltIns;
import dev.jstech.computers.cannon.sem.IBinding;
import dev.jstech.computers.cannon.sem.IMemberSymbol;
import dev.jstech.computers.cannon.sem.ITypeSymbol;
import dev.jstech.computers.cannon.sem.NamedType;
import dev.jstech.computers.cannon.sem.SemanticModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What an editor may offer after a name and a dot, or for a name being typed on its own.
 *
 * <p>The answers come from the same model the checker built: the types the language brings, the types
 * the program declares, the members of each, and the variables the checker met on its way through the
 * bodies. What is being reached into is worked out from where the caret is: a local or a parameter
 * declared above it in the same method, a field of the type around it, {@code this}, {@code base}, a
 * type named for its static side, or a chain of those read one member at a time.
 */
public final class CannonCompletions {

    /** What a candidate is, which is what an editor draws its little mark from. */
    public enum Sort { METHOD, PROPERTY, FIELD, EVENT, TYPE, VARIABLE }

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

    /**
     * Where the caret is, as the answers need it: the type around it and the variables in reach.
     *
     * @param enclosing the type the caret is inside, or null when it is outside every type
     * @param variables the parameters and locals declared above the caret in the method around it
     */
    public record Scope(NamedType enclosing, List<IBinding.Variable> variables) {

        public Scope {
            variables = List.copyOf(variables);
        }

        /** Nowhere in particular: no type around, nothing in reach. */
        public static final Scope NONE = new Scope(null, List.of());

        /** The variable called {@code name} in reach, the one declared last when there are two, or null. */
        public IBinding.Variable variable(final String name) {
            for (int i = this.variables.size() - 1; i >= 0; i--) {
                if (this.variables.get(i).name().equals(name)) {
                    return this.variables.get(i);
                }
            }
            return null;
        }
    }

    /**
     * What a receiver turned out to be.
     *
     * @param type       the type reached
     * @param staticSide whether it was reached as a type, so only what belongs to the type is offered
     */
    public record Target(ITypeSymbol type, boolean staticSide) {
    }

    private CannonCompletions() {
    }

    /**
     * Where the caret is in {@code file}: the type whose declaration spans line {@code line}, the
     * innermost when one is nested in another, and the variables the checker declared in the member
     * around that line, above it.
     *
     * <p>A declaration knows only where it starts, so a type or a member runs until the next one starts,
     * which is where the closing brace has to be anyway.
     */
    public static Scope scopeAt(final SemanticModel model, final CompilationUnit unit, final String file,
                                final int line) {
        if (model == null || unit == null) {
            return Scope.NONE;
        }
        IDecl.ITypeDecl around = spanning(unit.types(), line);
        if (around == null) {
            return Scope.NONE;
        }
        int memberStart = around.line();
        while (true) {
            final IDecl.IMemberDecl member = around instanceof IDecl.ClassDecl made
                    ? spanning(made.members(), line) : null;
            if (member == null) {
                break;
            }
            memberStart = member.line();
            if (!(member instanceof IDecl.TypeMember nested)) {
                break;
            }
            around = nested.type();
        }
        final NamedType enclosing = model.declaredType(around.name());
        final List<IBinding.Variable> variables = new ArrayList<>();
        for (final SemanticModel.DeclaredVariable declared : model.variables()) {
            if (declared.file().equals(file) && declared.line() >= memberStart && declared.line() <= line) {
                variables.add(declared.variable());
            }
        }
        return new Scope(enclosing, variables);
    }

    /** The last declaration that starts on or before {@code line}, or null when none has yet. */
    private static <T extends IDecl> T spanning(final List<T> declarations, final int line) {
        T found = null;
        for (final T declaration : declarations) {
            if (declaration.line() <= line) {
                found = declaration;
            }
        }
        return found;
    }

    /**
     * What {@code chain} reaches, read from {@code scope}: the first name is a variable in reach, a
     * member of the type around the caret, {@code this}, {@code base} or a type; every name after it
     * is a member of what came before. Null when a link means nothing.
     */
    public static Target resolve(final BuiltIns builtIns, final SemanticModel model, final Scope scope,
                                 final List<String> chain) {
        if (chain.isEmpty()) {
            return null;
        }
        Target at = first(builtIns, model, scope, chain.getFirst());
        for (int i = 1; at != null && i < chain.size(); i++) {
            at = through(builtIns, model, at, chain.get(i));
        }
        return at;
    }

    private static Target first(final BuiltIns builtIns, final SemanticModel model, final Scope scope,
                                final String name) {
        final IBinding.Variable variable = scope.variable(name);
        if (variable != null) {
            return new Target(variable.type(), false);
        }
        if (scope.enclosing() != null) {
            if (name.equals("this")) {
                return new Target(scope.enclosing(), false);
            }
            if (name.equals("base")) {
                return scope.enclosing().base() == null ? null : new Target(scope.enclosing().base(), false);
            }
            final Target member = memberOf(scope.enclosing(), name);
            if (member != null) {
                return member;
            }
        }
        final NamedType type = type(builtIns, model, name);
        return type == null ? null : new Target(type, true);
    }

    /** What {@code name} is on {@code from}: a member's type, or a type nested inside it. */
    private static Target through(final BuiltIns builtIns, final SemanticModel model, final Target from,
                                  final String name) {
        final NamedType named = named(from.type());
        if (named == null) {
            return null;
        }
        final Target member = memberOf(named, name);
        if (member != null) {
            return member;
        }
        final NamedType nested = model == null ? null : model.declaredType(named.qualifiedName() + "." + name);
        return nested != null ? new Target(nested, true) : null;
    }

    /** The type of the field, property or event called {@code name} on {@code type}, or null. */
    private static Target memberOf(final NamedType type, final String name) {
        for (final IMemberSymbol member : type.allMembers()) {
            if (!member.name().equals(name)) {
                continue;
            }
            final ITypeSymbol held = switch (member) {
                case IMemberSymbol.FieldSymbol field -> field.type();
                case IMemberSymbol.PropertySymbol property -> property.type();
                case IMemberSymbol.EventSymbol event -> event.delegateType();
                default -> null;
            };
            if (held != null) {
                return new Target(held, false);
            }
        }
        return null;
    }

    /** The members of {@code target} whose names begin with {@code prefix}. */
    public static List<Item> members(final Target target, final String prefix) {
        final NamedType type = named(target.type());
        return type == null ? List.of() : membersOf(type, prefix, target.staticSide());
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
        return type == null ? List.of() : membersOf(type, prefix, staticSide);
    }

    private static List<Item> membersOf(final NamedType type, final String prefix, final boolean staticSide) {
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
     * What a name standing on its own could be: the variables in reach, the members of the type around
     * the caret, and the types the program and the language have, in that order, each sorted by name.
     */
    public static List<Item> names(final BuiltIns builtIns, final SemanticModel model, final Scope scope,
                                   final String prefix) {
        final String wanted = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        final Set<String> seen = new LinkedHashSet<>();
        final List<Item> items = new ArrayList<>();
        final List<Item> variables = new ArrayList<>();
        for (int i = scope.variables().size() - 1; i >= 0; i--) {
            final IBinding.Variable variable = scope.variables().get(i);
            if (variable.name().toLowerCase(Locale.ROOT).startsWith(wanted) && seen.add(variable.name())) {
                variables.add(new Item(variable.name(), variable.name() + " : " + variable.type().describe(),
                        Sort.VARIABLE, variable.parameter() ? "parameter" : "local"));
            }
        }
        variables.sort(Comparator.comparing(Item::label));
        items.addAll(variables);
        if (scope.enclosing() != null) {
            final List<Item> own = new ArrayList<>();
            for (final IMemberSymbol member : scope.enclosing().allMembers()) {
                if (member instanceof IMemberSymbol.ConstructorSymbol
                        || !member.name().toLowerCase(Locale.ROOT).startsWith(wanted)
                        || !seen.add(member.name() + signatureOf(member))) {
                    continue;
                }
                own.add(new Item(member.name(), signatureOf(member), sortOf(member), member.owner().name()));
            }
            own.sort(Comparator.comparing(Item::label).thenComparing(Item::signature));
            items.addAll(own);
        }
        items.addAll(types(builtIns, model, prefix));
        return items;
    }

    /**
     * The types whose names begin with {@code prefix}: the language's, and the ones the program
     * declares, with the program's own first when a name is in both.
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
                offer(items, seen, type, wanted, type.namespace().isEmpty() ? "Cannon" : type.namespace());
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

    /** The named type behind a type as the checker knows it: a collection's own, an array's none. */
    private static NamedType named(final ITypeSymbol type) {
        return switch (type) {
            case NamedType named -> named;
            case ITypeSymbol.GenericType generic -> generic.definition();
            case null, default -> null;
        };
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
