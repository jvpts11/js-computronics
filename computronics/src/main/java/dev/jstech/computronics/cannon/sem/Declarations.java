/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.sem;

import dev.jstech.computronics.cannon.CannonError;
import dev.jstech.computronics.cannon.DiagnosticBag;
import dev.jstech.computronics.cannon.ast.CompilationUnit;
import dev.jstech.computronics.cannon.ast.Decl;
import dev.jstech.computronics.cannon.ast.Node;
import dev.jstech.computronics.cannon.ast.TypeRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the declarations in the tree into symbols.
 *
 * <p>It runs in two passes because types refer to each other in circles: the first gives every type
 * its name, the second gives each one its base, its interfaces and its members, by which point every
 * name it could mention already exists. Only then can a member's type be resolved without caring
 * what order the player wrote their classes in.
 */
public final class Declarations {

    private final BuiltIns builtIns;
    private final TypeRules rules;
    private final DiagnosticBag diagnostics;
    private final SemanticModel model;
    private final Map<String, NamedType> declared = new LinkedHashMap<>();
    private final Map<NamedType, Decl.TypeDecl> sources = new LinkedHashMap<>();
    private final Map<NamedType, String> files = new LinkedHashMap<>();

    public Declarations(final BuiltIns builtIns, final TypeRules rules, final DiagnosticBag diagnostics,
                        final SemanticModel model) {
        this.builtIns = builtIns;
        this.rules = rules;
        this.diagnostics = diagnostics;
        this.model = model;
    }

    /** First pass: every type in the program gets its name and nothing else. */
    public void declare(final List<CompilationUnit> units) {
        for (final CompilationUnit unit : units) {
            this.diagnostics.setFile(unit.file());
            for (final Decl.TypeDecl declaration : unit.types()) {
                this.declareType(declaration, unit.file());
            }
        }
    }

    private void declareType(final Decl.TypeDecl declaration, final String file) {
        final String name = declaration.name();
        if (this.declared.containsKey(name) || this.builtIns.isReserved(name)) {
            this.diagnostics.error(declaration.line(), declaration.column(),
                    CannonError.DUPLICATE_DECLARATION, name);
            return;
        }
        final NamedType.Kind kind = switch (declaration) {
            case Decl.ClassDecl ignored -> NamedType.Kind.CLASS;
            case Decl.InterfaceDecl ignored -> NamedType.Kind.INTERFACE;
            case Decl.EnumDecl ignored -> NamedType.Kind.ENUM;
            case Decl.DelegateDecl ignored -> NamedType.Kind.DELEGATE;
        };
        final NamedType type = NamedType.of(name, kind, false);
        this.declared.put(name, type);
        this.sources.put(type, declaration);
        this.files.put(type, file);
        this.model.addDeclared(type);
    }

    /** Second pass: every type gets what it is made of. */
    public void fill() {
        for (final Map.Entry<NamedType, Decl.TypeDecl> entry : this.sources.entrySet()) {
            this.diagnostics.setFile(this.files.get(entry.getKey()));
            switch (entry.getValue()) {
                case Decl.ClassDecl declaration -> this.fillClass(entry.getKey(), declaration);
                case Decl.InterfaceDecl declaration -> this.fillInterface(entry.getKey(), declaration);
                case Decl.EnumDecl declaration -> this.fillEnum(entry.getKey(), declaration);
                case Decl.DelegateDecl declaration -> this.fillDelegate(entry.getKey(), declaration);
            }
        }
    }

    /** The declaration a type came from, so the body checker can walk it. */
    public Decl.TypeDecl source(final NamedType type) {
        return this.sources.get(type);
    }

    /** The file a type was written in, so a message about it names the right one. */
    public String fileOf(final NamedType type) {
        return this.files.get(type);
    }

    private void fillClass(final NamedType type, final Decl.ClassDecl declaration) {
        for (final TypeRef base : declaration.bases()) {
            final TypeSymbol resolved = this.resolve(base);
            if (!(resolved instanceof NamedType named)) {
                continue;
            }
            if (named.kind() == NamedType.Kind.INTERFACE) {
                type.addInterface(named);
            } else if (named.kind() == NamedType.Kind.CLASS && type.base() == null) {
                type.setBase(named);
            } else {
                this.diagnostics.error(base.line(), base.column(), CannonError.INVALID_BASE, base.describe());
            }
        }
        for (final Decl.MemberDecl member : declaration.members()) {
            this.fillMember(type, member);
        }
    }

    private void fillMember(final NamedType type, final Decl.MemberDecl member) {
        switch (member) {
            case Decl.FieldDecl field -> this.addUnique(type, new MemberSymbol.FieldSymbol(
                    type, field.name(), this.resolve(field.type()), field.modifiers()), field);
            case Decl.MethodDecl method -> type.addMember(this.methodOf(type, method));
            case Decl.ConstructorDecl constructor -> type.addMember(new MemberSymbol.ConstructorSymbol(
                    type, this.parametersOf(constructor.parameters()), constructor.modifiers()));
            case Decl.PropertyDecl property -> this.addUnique(type, new MemberSymbol.PropertySymbol(
                    type, property.name(), this.resolve(property.type()),
                    property.getter() != null, property.setter() != null, property.modifiers(),
                    property.setter() == null ? Set.of() : property.setter().modifiers()), property);
            case Decl.EventDecl event -> this.addEvent(type, event);
        }
    }

    private void addEvent(final NamedType type, final Decl.EventDecl event) {
        final TypeSymbol resolved = this.resolve(event.type());
        if (resolved instanceof NamedType handler && handler.kind() == NamedType.Kind.DELEGATE) {
            this.addUnique(type, new MemberSymbol.EventSymbol(type, event.name(), handler,
                    event.modifiers()), event);
            return;
        }
        if (!this.rules.isError(resolved)) {
            this.diagnostics.error(event.type().line(), event.type().column(),
                    CannonError.EVENT_NEEDS_DELEGATE, event.type().describe());
        }
    }

    // A field, a property and an event share one set of names, because they are all read the same
    // way at a use site. Methods are left out of this: telling them apart by parameters is the point.
    private void addUnique(final NamedType type, final MemberSymbol member, final Node declaration) {
        for (final MemberSymbol existing : type.members()) {
            if (!(existing instanceof MemberSymbol.MethodSymbol) && existing.name().equals(member.name())) {
                this.diagnostics.error(declaration.line(), declaration.column(),
                        CannonError.DUPLICATE_DECLARATION, member.name());
                return;
            }
        }
        type.addMember(member);
    }

    private MemberSymbol.MethodSymbol methodOf(final NamedType type, final Decl.MethodDecl method) {
        return new MemberSymbol.MethodSymbol(type, method.name(), this.resolve(method.returnType()),
                this.parametersOf(method.parameters()), method.modifiers());
    }

    private List<MemberSymbol.ParameterSymbol> parametersOf(final List<Decl.Parameter> parameters) {
        final List<MemberSymbol.ParameterSymbol> symbols = new ArrayList<>();
        for (final Decl.Parameter parameter : parameters) {
            symbols.add(new MemberSymbol.ParameterSymbol(parameter.name(),
                    this.resolve(parameter.type()), parameter.outward()));
        }
        return symbols;
    }

    private void fillInterface(final NamedType type, final Decl.InterfaceDecl declaration) {
        for (final TypeRef base : declaration.bases()) {
            final TypeSymbol resolved = this.resolve(base);
            if (resolved instanceof NamedType named && named.kind() == NamedType.Kind.INTERFACE) {
                type.addInterface(named);
            } else if (!this.rules.isError(resolved)) {
                this.diagnostics.error(base.line(), base.column(), CannonError.INVALID_BASE, base.describe());
            }
        }
        for (final Decl.MethodDecl method : declaration.methods()) {
            type.addMember(this.methodOf(type, method));
        }
    }

    // Every name in an enum is a value of the enum, held by the type rather than by an instance.
    private void fillEnum(final NamedType type, final Decl.EnumDecl declaration) {
        for (final Decl.EnumConstant constant : declaration.constants()) {
            this.addUnique(type, new MemberSymbol.FieldSymbol(type, constant.name(), type,
                    Set.of(Decl.Modifier.PUBLIC, Decl.Modifier.STATIC, Decl.Modifier.READONLY)), constant);
        }
    }

    private void fillDelegate(final NamedType type, final Decl.DelegateDecl declaration) {
        type.setInvoke(new MemberSymbol.MethodSymbol(type, "Invoke", this.resolve(declaration.returnType()),
                this.parametersOf(declaration.parameters()), Set.of(Decl.Modifier.PUBLIC)));
    }

    /** Resolves a type as it was written. Reports what it cannot resolve and gives back the error type. */
    public TypeSymbol resolve(final TypeRef reference) {
        if (reference == null) {
            return TypeSymbol.Special.ERROR;
        }
        TypeSymbol resolved = this.resolveName(reference);
        for (int rank = 0; rank < reference.arrayRank(); rank++) {
            resolved = new TypeSymbol.ArrayType(resolved);
        }
        return resolved;
    }

    private TypeSymbol resolveName(final TypeRef reference) {
        final String name = reference.name();
        final TypeSymbol.Primitive primitive = TypeSymbol.Primitive.written(name);
        if (primitive != null) {
            return this.withoutArguments(reference, primitive);
        }
        final NamedType user = this.declared.get(name);
        final NamedType type = user != null ? user : this.builtIns.type(name, reference.arguments().size());
        if (type == null) {
            this.diagnostics.error(reference.line(), reference.column(), CannonError.UNKNOWN_NAME, name);
            return TypeSymbol.Special.ERROR;
        }
        if (type.typeParameters().size() != reference.arguments().size()) {
            this.diagnostics.error(reference.line(), reference.column(),
                    CannonError.WRONG_TYPE_ARGUMENT_COUNT, name, type.typeParameters().size());
            return TypeSymbol.Special.ERROR;
        }
        if (type.typeParameters().isEmpty()) {
            return type;
        }
        final List<TypeSymbol> arguments = new ArrayList<>();
        for (final TypeRef argument : reference.arguments()) {
            arguments.add(this.resolve(argument));
        }
        return new TypeSymbol.GenericType(type, arguments);
    }

    private TypeSymbol withoutArguments(final TypeRef reference, final TypeSymbol resolved) {
        if (!reference.arguments().isEmpty()) {
            this.diagnostics.error(reference.line(), reference.column(),
                    CannonError.WRONG_TYPE_ARGUMENT_COUNT, reference.name(), 0);
            return TypeSymbol.Special.ERROR;
        }
        return resolved;
    }

    /** Reports every interface method a class said it would have and does not. */
    public void checkInterfaces() {
        for (final Map.Entry<NamedType, Decl.TypeDecl> entry : this.sources.entrySet()) {
            final NamedType type = entry.getKey();
            if (type.kind() != NamedType.Kind.CLASS) {
                continue;
            }
            this.diagnostics.setFile(this.files.get(type));
            for (final NamedType face : type.interfaces()) {
                for (final MemberSymbol required : face.allMembers()) {
                    if (required instanceof MemberSymbol.MethodSymbol method && !this.hasMethod(type, method)) {
                        this.diagnostics.error(entry.getValue().line(), entry.getValue().column(),
                                CannonError.MISSING_INTERFACE_MEMBER, type.name(), face.name(),
                                method.describe());
                    }
                }
            }
        }
    }

    private boolean hasMethod(final NamedType type, final MemberSymbol.MethodSymbol required) {
        for (final MemberSymbol member : type.allMembers()) {
            if (member instanceof MemberSymbol.MethodSymbol candidate
                    && candidate.owner().kind() == NamedType.Kind.CLASS
                    && candidate.name().equals(required.name())
                    && candidate.returnType().equals(required.returnType())
                    && this.sameParameters(candidate, required)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameParameters(final MemberSymbol.MethodSymbol left, final MemberSymbol.MethodSymbol right) {
        if (left.parameters().size() != right.parameters().size()) {
            return false;
        }
        for (int i = 0; i < left.parameters().size(); i++) {
            if (!left.parameters().get(i).type().equals(right.parameters().get(i).type())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the class the runtime starts from. A program is exactly one of them: none and there is
     * nothing to run, more than one and there is no saying which.
     */
    public void checkEntryPoint(final int line, final int column) {
        final List<NamedType> candidates = new ArrayList<>();
        for (final NamedType type : this.declared.values()) {
            if (type.kind() == NamedType.Kind.CLASS && type.isOrDescendsFrom(this.builtIns.scriptType())) {
                candidates.add(type);
            }
        }
        if (candidates.size() == 1) {
            this.model.setEntryPoint(candidates.getFirst());
            return;
        }
        this.diagnostics.error(line, column, CannonError.ENTRY_POINT, candidates.size());
    }
}
