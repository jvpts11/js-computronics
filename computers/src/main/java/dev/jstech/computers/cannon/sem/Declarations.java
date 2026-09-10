/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.sem;

import dev.jstech.computers.cannon.CannonError;
import dev.jstech.computers.cannon.DiagnosticBag;
import dev.jstech.computers.cannon.Shape;
import dev.jstech.computers.cannon.ast.CompilationUnit;
import dev.jstech.computers.cannon.ast.IDecl;
import dev.jstech.computers.cannon.ast.INode;
import dev.jstech.computers.cannon.ast.TypeRef;
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
 *
 * <p>Names are found the way a file says they may be: a type sees the types of its own namespace and
 * of the namespaces around it, the types it is nested with, and what its file brought in with using,
 * one type or a whole namespace at a time. The language's own types live in namespaces of their own
 * under {@code System} and are brought in the same way; only the roots every program is made of, the
 * text and the object, are there without asking.
 */
public final class Declarations {

    private final BuiltIns builtIns;
    private final TypeRules rules;
    private final DiagnosticBag diagnostics;
    private final SemanticModel model;
    /** Every type the program declares, by its name with its namespace in front. */
    private final Map<String, NamedType> declared = new LinkedHashMap<>();
    private final Map<NamedType, IDecl.ITypeDecl> sources = new LinkedHashMap<>();
    private final Map<NamedType, String> files = new LinkedHashMap<>();
    /** What each type can see without saying the namespace: its own, and the ones its file brought in. */
    private final Map<NamedType, Scope> scopes = new LinkedHashMap<>();
    /** The type each nested type was declared inside, whose other nested types it may name plainly. */
    private final Map<NamedType, NamedType> outers = new LinkedHashMap<>();
    /** The type being filled, whose scope decides what a bare name means. */
    private NamedType current;

    /** The namespace a file's types live in, and what the file brought in with using. */
    private record Scope(String namespace, List<CompilationUnit.Using> usings) {
        static final Scope TOP = new Scope("", List.of());
    }

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
            for (final CompilationUnit.Declared one : unit.declared()) {
                this.declareType(one.type(), unit.file(), new Scope(one.namespace(), unit.usings()), null);
            }
        }
        /*
         * Only once every name exists can a using be read: one without a star has to name a type, and
         * naming a namespace that way is the one mistake worth pointing out, since the star is all it
         * is missing. A using that names nothing at all is left alone; the names it was meant to bring
         * in are reported where they are used.
         */
        for (final CompilationUnit unit : units) {
            this.diagnostics.setFile(unit.file());
            for (final CompilationUnit.Using using : unit.usings()) {
                if (!using.all() && this.find(using.name(), -1) == null && this.isNamespace(using.name())) {
                    this.diagnostics.error(using.line(), using.column(), CannonError.USING_NEEDS_STAR,
                            using.name(), using.name());
                }
            }
        }
    }

    private void declareType(final IDecl.ITypeDecl declaration, final String file, final Scope scope,
                             final NamedType outer) {
        final String name = declaration.name();
        final String prefix = outer != null ? outer.qualifiedName() : scope.namespace();
        final String qualified = prefix.isEmpty() ? name : prefix + "." + name;
        if (this.declared.containsKey(qualified) || this.builtIns.qualified(qualified, -1) != null) {
            this.diagnostics.error(declaration.line(), declaration.column(),
                    CannonError.DUPLICATE_DECLARATION, qualified);
            return;
        }
        final NamedType.Kind kind = switch (declaration) {
            case IDecl.ClassDecl made -> switch (made.flavour()) {
                case CLASS -> NamedType.Kind.CLASS;
                case STRUCT -> NamedType.Kind.STRUCT;
                case RECORD -> NamedType.Kind.RECORD;
            };
            case IDecl.InterfaceDecl ignored -> NamedType.Kind.INTERFACE;
            case IDecl.EnumDecl ignored -> NamedType.Kind.ENUM;
            case IDecl.DelegateDecl ignored -> NamedType.Kind.DELEGATE;
        };
        final NamedType type = NamedType.of(name, kind, false);
        type.setNamespace(prefix);
        this.declared.put(qualified, type);
        this.sources.put(type, declaration);
        this.files.put(type, file);
        this.scopes.put(type, scope);
        if (outer != null) {
            this.outers.put(type, outer);
        }
        this.model.addDeclared(type);
        if (declaration instanceof IDecl.ClassDecl made) {
            for (final IDecl.IMemberDecl member : made.members()) {
                if (member instanceof IDecl.TypeMember nested) {
                    this.declareType(nested.type(), file, scope, type);
                }
            }
        }
    }

    /** The type a name means where {@code from} was written, or null when it means none. */
    public NamedType lookup(final String name, final NamedType from) {
        return this.lookup(name, from, -1);
    }

    /**
     * The type a name means where {@code from} was written, or null when it means none.
     *
     * <p>A name with its namespace in front means exactly that type. A bare name is looked for among
     * the types nested with the type it was written in, then in the namespace that type sits in and in
     * each namespace enclosing that one, then in what its file brought in with using, and last among
     * the roots that belong to no namespace. A built-in type with {@code arity} arguments is preferred
     * where the name has more than one; {@code -1} asks for whichever there is.
     */
    public NamedType lookup(final String name, final NamedType from, final int arity) {
        NamedType found = this.find(name, arity);
        if (found != null) {
            return found;
        }
        for (NamedType around = from; around != null; around = this.outers.get(around)) {
            found = this.declared.get(around.qualifiedName() + "." + name);
            if (found != null) {
                return found;
            }
        }
        final Scope scope = from == null ? Scope.TOP : this.scopes.getOrDefault(from, Scope.TOP);
        String enclosing = scope.namespace();
        while (!enclosing.isEmpty()) {
            found = this.find(enclosing + "." + name, arity);
            if (found != null) {
                return found;
            }
            final int dot = enclosing.lastIndexOf('.');
            enclosing = dot >= 0 ? enclosing.substring(0, dot) : "";
        }
        for (final CompilationUnit.Using using : scope.usings()) {
            if (using.all()) {
                found = this.find(using.name() + "." + name, arity);
            } else if (using.name().equals(name) || using.name().endsWith("." + name)) {
                found = this.find(using.name(), arity);
            }
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The type known by exactly that name with its namespace in front, the program's or the language's. */
    private NamedType find(final String qualified, final int arity) {
        final NamedType own = this.declared.get(qualified);
        return own != null ? own : this.builtIns.qualified(qualified, arity);
    }

    /**
     * Whether {@code prefix} is the start of some namespace: {@code Tools} for {@code Tools.Counter},
     * {@code System.IO} for the console. A type with types nested inside it reads as one as well, so
     * that {@code Outer.Inner} written from outside finds its way.
     */
    public boolean isNamespace(final String prefix) {
        for (final NamedType type : this.declared.values()) {
            final String namespace = type.namespace();
            if (namespace.equals(prefix) || namespace.startsWith(prefix + ".")) {
                return true;
            }
        }
        return this.builtIns.isNamespace(prefix);
    }

    /**
     * The namespace a bare name would be found in if the file brought it in, or null when it is in
     * none: what a message about an unknown name points the player to.
     */
    public String homeOf(final String name) {
        for (final NamedType type : this.declared.values()) {
            if (type.name().equals(name) && !type.namespace().isEmpty() && !this.outers.containsKey(type)) {
                return type.namespace();
            }
        }
        return this.builtIns.homeOf(name);
    }

    /** Reports a name that means nothing here, saying where it would be found when it is somewhere. */
    public void reportUnknown(final int line, final int column, final String name) {
        final String home = this.homeOf(name);
        if (home != null) {
            this.diagnostics.error(line, column, CannonError.NEEDS_USING, name, home, home, home, name);
        } else {
            this.diagnostics.error(line, column, CannonError.UNKNOWN_NAME, name);
        }
    }

    /** Second pass: every type gets what it is made of. */
    public void fill() {
        for (final Map.Entry<NamedType, IDecl.ITypeDecl> entry : this.sources.entrySet()) {
            this.diagnostics.setFile(this.files.get(entry.getKey()));
            this.current = entry.getKey();
            switch (entry.getValue()) {
                case IDecl.ClassDecl declaration -> this.fillClass(entry.getKey(), declaration);
                case IDecl.InterfaceDecl declaration -> this.fillInterface(entry.getKey(), declaration);
                case IDecl.EnumDecl declaration -> this.fillEnum(entry.getKey(), declaration);
                case IDecl.DelegateDecl declaration -> this.fillDelegate(entry.getKey(), declaration);
            }
        }
    }

    /** The declaration a type came from, so the body checker can walk it. */
    public IDecl.ITypeDecl source(final NamedType type) {
        return this.sources.get(type);
    }

    /** The file a type was written in, so a message about it names the right one. */
    public String fileOf(final NamedType type) {
        return this.files.get(type);
    }

    /** The type {@code type} was declared inside, or null for one at the top of its namespace. */
    public NamedType outerOf(final NamedType type) {
        return this.outers.get(type);
    }

    private void fillClass(final NamedType type, final IDecl.ClassDecl declaration) {
        for (final TypeRef base : declaration.bases()) {
            final ITypeSymbol resolved = this.resolve(base);
            if (!(resolved instanceof NamedType named)) {
                continue;
            }
            if (named.kind() == NamedType.Kind.INTERFACE) {
                type.addInterface(named);
            } else if (named.kind().classLike() && type.kind() == NamedType.Kind.STRUCT) {
                this.diagnostics.error(base.line(), base.column(), CannonError.STRUCT_NO_BASE, base.describe());
            } else if (named.kind().classLike() && type.base() == null) {
                type.setBase(named);
            } else {
                this.diagnostics.error(base.line(), base.column(), CannonError.INVALID_BASE, base.describe());
            }
        }
        for (final IDecl.IMemberDecl member : declaration.members()) {
            this.fillMember(type, member);
        }
    }

    private void fillMember(final NamedType type, final IDecl.IMemberDecl member) {
        switch (member) {
            case IDecl.FieldDecl field -> this.addUnique(type, new IMemberSymbol.FieldSymbol(
                    type, field.name(), this.resolve(field.type()), field.modifiers()), field);
            case IDecl.MethodDecl method -> this.addMethod(type, this.methodOf(type, method), method);
            case IDecl.ConstructorDecl constructor -> type.addMember(new IMemberSymbol.ConstructorSymbol(
                    type, this.parametersOf(constructor.parameters()), constructor.modifiers()));
            case IDecl.PropertyDecl property -> this.addUnique(type, new IMemberSymbol.PropertySymbol(
                    type, property.name(), this.resolve(property.type()),
                    property.getter() != null, property.setter() != null, property.modifiers(),
                    property.setter() == null ? Set.of() : property.setter().modifiers()), property);
            case IDecl.EventDecl event -> this.addEvent(type, event);
            case IDecl.TypeMember ignored -> { } // a nested type was named in the first pass and is filled as itself
        }
    }

    private void addEvent(final NamedType type, final IDecl.EventDecl event) {
        final ITypeSymbol resolved = this.resolve(event.type());
        if (resolved instanceof NamedType handler && handler.kind() == NamedType.Kind.DELEGATE) {
            this.addUnique(type, new IMemberSymbol.EventSymbol(type, event.name(), handler,
                    event.modifiers()), event);
            return;
        }
        if (!this.rules.isError(resolved)) {
            this.diagnostics.error(event.type().line(), event.type().column(),
                    CannonError.EVENT_NEEDS_DELEGATE, event.type().describe());
        }
    }

    /*
     * A field, a property and an event share one set of names, because they are all read the same
     * way at a use site. Methods are left out of this: telling them apart by parameters is the point.
     */
    private void addUnique(final NamedType type, final IMemberSymbol member, final INode declaration) {
        for (final IMemberSymbol existing : type.members()) {
            if (!(existing instanceof IMemberSymbol.MethodSymbol) && existing.name().equals(member.name())) {
                this.diagnostics.error(declaration.line(), declaration.column(),
                        CannonError.DUPLICATE_DECLARATION, member.name());
                return;
            }
        }
        type.addMember(member);
    }

    /*
     * Two methods may share a name as long as their parameters tell them apart; two that take the same
     * types are one method written twice, and a call could never say which it meant.
     */
    private void addMethod(final NamedType type, final IMemberSymbol.MethodSymbol method, final INode declaration) {
        for (final IMemberSymbol existing : type.members()) {
            if (existing instanceof IMemberSymbol.MethodSymbol other && other.name().equals(method.name())
                    && this.sameParameters(other, method)) {
                this.diagnostics.error(declaration.line(), declaration.column(),
                        CannonError.DUPLICATE_DECLARATION, method.describe());
                return;
            }
        }
        type.addMember(method);
    }

    private IMemberSymbol.MethodSymbol methodOf(final NamedType type, final IDecl.MethodDecl method) {
        return new IMemberSymbol.MethodSymbol(type, method.name(), this.resolve(method.returnType()),
                this.parametersOf(method.parameters()), method.modifiers());
    }

    private List<IMemberSymbol.ParameterSymbol> parametersOf(final List<IDecl.Parameter> parameters) {
        final List<IMemberSymbol.ParameterSymbol> symbols = new ArrayList<>();
        for (final IDecl.Parameter parameter : parameters) {
            symbols.add(new IMemberSymbol.ParameterSymbol(parameter.name(),
                    this.resolve(parameter.type()), parameter.outward()));
        }
        return symbols;
    }

    private void fillInterface(final NamedType type, final IDecl.InterfaceDecl declaration) {
        for (final TypeRef base : declaration.bases()) {
            final ITypeSymbol resolved = this.resolve(base);
            if (resolved instanceof NamedType named && named.kind() == NamedType.Kind.INTERFACE) {
                type.addInterface(named);
            } else if (!this.rules.isError(resolved)) {
                this.diagnostics.error(base.line(), base.column(), CannonError.INVALID_BASE, base.describe());
            }
        }
        for (final IDecl.MethodDecl method : declaration.methods()) {
            type.addMember(this.methodOf(type, method));
        }
    }

    // Every name in an enum is a value of the enum, held by the type rather than by an instance.
    private void fillEnum(final NamedType type, final IDecl.EnumDecl declaration) {
        for (final IDecl.EnumConstant constant : declaration.constants()) {
            this.addUnique(type, new IMemberSymbol.FieldSymbol(type, constant.name(), type,
                    Set.of(IDecl.Modifier.PUBLIC, IDecl.Modifier.STATIC, IDecl.Modifier.READONLY)), constant);
        }
    }

    private void fillDelegate(final NamedType type, final IDecl.DelegateDecl declaration) {
        type.setInvoke(new IMemberSymbol.MethodSymbol(type, "Invoke", this.resolve(declaration.returnType()),
                this.parametersOf(declaration.parameters()), Set.of(IDecl.Modifier.PUBLIC)));
    }

    /** Resolves a type as it was written. Reports what it cannot resolve and gives back the error type. */
    public ITypeSymbol resolve(final TypeRef reference) {
        return this.resolve(reference, this.current);
    }

    /** Resolves a type as it was written inside {@code from}, whose scope decides what a bare name means. */
    public ITypeSymbol resolve(final TypeRef reference, final NamedType from) {
        if (reference == null) {
            return ITypeSymbol.Special.ERROR;
        }
        final NamedType was = this.current;
        this.current = from;
        try {
            return this.resolveIn(reference);
        } finally {
            this.current = was;
        }
    }

    private ITypeSymbol resolveIn(final TypeRef reference) {
        ITypeSymbol resolved = this.resolveName(reference);
        for (int rank = 0; rank < reference.arrayRank(); rank++) {
            resolved = new ITypeSymbol.ArrayType(resolved);
        }
        return resolved;
    }

    private ITypeSymbol resolveName(final TypeRef reference) {
        final String name = reference.name();
        final ITypeSymbol.Primitive primitive = ITypeSymbol.Primitive.written(name);
        if (primitive != null) {
            return this.withoutArguments(reference, primitive);
        }
        final NamedType type = this.lookup(name, this.current, reference.arguments().size());
        if (type == null) {
            this.reportUnknown(reference.line(), reference.column(), name);
            return ITypeSymbol.Special.ERROR;
        }
        if (type.typeParameters().size() != reference.arguments().size()) {
            this.diagnostics.error(reference.line(), reference.column(),
                    CannonError.WRONG_TYPE_ARGUMENT_COUNT, name, type.typeParameters().size());
            return ITypeSymbol.Special.ERROR;
        }
        if (type.typeParameters().isEmpty()) {
            return type;
        }
        final List<ITypeSymbol> arguments = new ArrayList<>();
        for (final TypeRef argument : reference.arguments()) {
            arguments.add(this.resolve(argument));
        }
        return new ITypeSymbol.GenericType(type, arguments);
    }

    private ITypeSymbol withoutArguments(final TypeRef reference, final ITypeSymbol resolved) {
        if (!reference.arguments().isEmpty()) {
            this.diagnostics.error(reference.line(), reference.column(),
                    CannonError.WRONG_TYPE_ARGUMENT_COUNT, reference.name(), 0);
            return ITypeSymbol.Special.ERROR;
        }
        return resolved;
    }

    /** Reports every interface method a class said it would have and does not. */
    public void checkInterfaces() {
        for (final Map.Entry<NamedType, IDecl.ITypeDecl> entry : this.sources.entrySet()) {
            final NamedType type = entry.getKey();
            if (!type.kind().classLike()) {
                continue;
            }
            this.diagnostics.setFile(this.files.get(type));
            for (final NamedType face : type.interfaces()) {
                for (final IMemberSymbol required : face.allMembers()) {
                    if (required instanceof IMemberSymbol.MethodSymbol method && !this.hasMethod(type, method)) {
                        this.diagnostics.error(entry.getValue().line(), entry.getValue().column(),
                                CannonError.MISSING_INTERFACE_MEMBER, type.name(), face.name(),
                                method.describe());
                    }
                }
            }
        }
    }

    private boolean hasMethod(final NamedType type, final IMemberSymbol.MethodSymbol required) {
        for (final IMemberSymbol member : type.allMembers()) {
            if (member instanceof IMemberSymbol.MethodSymbol candidate
                    && candidate.owner().kind().classLike()
                    && candidate.name().equals(required.name())
                    && candidate.returnType().equals(required.returnType())
                    && this.sameParameters(candidate, required)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameParameters(final IMemberSymbol.MethodSymbol left, final IMemberSymbol.MethodSymbol right) {
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
     * Finds where the runtime starts, and which of the two kinds of program this is.
     *
     * <p>A class that implements the script interface is one that stays up; a class with a static
     * {@code Main} that takes nothing and returns nothing is one that runs at a terminal. A program is
     * exactly one of those: none and there is nothing to run, more than one and there is no saying
     * which. A class that does both is a script, because implementing the interface is the deliberate
     * act and a method called Main is only a name.
     */
    public void checkEntryPoint(final int line, final int column) {
        final List<NamedType> scripts = new ArrayList<>();
        final List<NamedType> consoles = new ArrayList<>();
        for (final NamedType type : this.declared.values()) {
            if (type.kind() != NamedType.Kind.CLASS) {
                continue;
            }
            if (type.isOrDescendsFrom(this.builtIns.scriptType())) {
                scripts.add(type);
            } else if (mainOf(type) != null) {
                consoles.add(type);
            }
        }
        if (scripts.size() + consoles.size() != 1) {
            this.diagnostics.error(line, column, CannonError.ENTRY_POINT,
                    scripts.size() + consoles.size());
            return;
        }
        this.model.setEntryPoint(scripts.isEmpty() ? consoles.getFirst() : scripts.getFirst(),
                scripts.isEmpty() ? Shape.CONSOLE : Shape.SCRIPT);
    }

    /** That class's {@code static void Main()}, or null when it has none of that exact shape. */
    public static IMemberSymbol.MethodSymbol mainOf(final NamedType type) {
        for (final IMemberSymbol member : type.members()) {
            if (member instanceof IMemberSymbol.MethodSymbol method
                    && MAIN.equals(method.name())
                    && method.isStatic()
                    && method.parameters().isEmpty()
                    && method.returnType() == ITypeSymbol.Primitive.VOID) {
                return method;
            }
        }
        return null;
    }

    /** The name a program that runs at a terminal starts at. */
    public static final String MAIN = "Main";
}
