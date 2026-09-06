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
import dev.jstech.computronics.cannon.ast.Decl;
import dev.jstech.computronics.cannon.ast.Expr;
import dev.jstech.computronics.cannon.ast.Node;
import dev.jstech.computronics.cannon.ast.Operator;
import dev.jstech.computronics.cannon.ast.Stmt;
import dev.jstech.computronics.cannon.ast.TypeRef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks what every method actually does against what the types say.
 *
 * <p>It runs once the symbols exist, so a body can mention anything the program declares whatever
 * order it was written in. Each expression is given a type and each name is tied to the thing it
 * stands for; both are kept in the model for the stage that emits the assembly.
 *
 * <p>A mistake never stops the walk. An expression it cannot make sense of becomes the error type,
 * which fits everywhere, so one unknown name produces one message instead of one for every line that
 * used it.
 */
public final class BodyChecker {

    private static final String INFERRED = "var";

    /** How a member was reached, which is what decides whether static and instance line up. */
    private enum Access {
        /** Through the name of a type, as in a static call. */
        TYPE,
        /** Through a value. */
        INSTANCE,
        /** By a bare name inside the type that declares it. */
        IMPLICIT
    }

    private final BuiltIns builtIns;
    private final TypeRules rules;
    private final Declarations declarations;
    private final DiagnosticBag diagnostics;
    private final SemanticModel model;

    private NamedType currentType;
    private TypeSymbol returnType = TypeSymbol.Primitive.VOID;
    private Scope scope = new Scope(null);
    private boolean staticContext;
    private boolean inConstructor;
    private int loopDepth;
    private int switchDepth;
    private int quiet;

    public BodyChecker(final BuiltIns builtIns, final TypeRules rules, final Declarations declarations,
                       final DiagnosticBag diagnostics, final SemanticModel model) {
        this.builtIns = builtIns;
        this.rules = rules;
        this.declarations = declarations;
        this.diagnostics = diagnostics;
        this.model = model;
    }

    /** Checks every body in every type the program declares. */
    public void check(final List<NamedType> types) {
        for (final NamedType type : types) {
            final Decl.TypeDecl source = this.declarations.source(type);
            this.diagnostics.setFile(this.declarations.fileOf(type));
            if (source instanceof Decl.ClassDecl declaration) {
                this.checkClass(type, declaration);
            } else if (source instanceof Decl.EnumDecl declaration) {
                this.checkEnum(type, declaration);
            }
        }
    }

    private void checkClass(final NamedType type, final Decl.ClassDecl declaration) {
        this.currentType = type;
        for (final Decl.MemberDecl member : declaration.members()) {
            switch (member) {
                case Decl.FieldDecl field -> this.checkFieldInitializer(field);
                case Decl.MethodDecl method -> this.checkMethod(method);
                case Decl.ConstructorDecl constructor -> this.checkConstructor(type, constructor);
                case Decl.PropertyDecl ignored -> { }
                case Decl.EventDecl ignored -> { }
            }
        }
    }

    // An enum's numbers are the one place outside a body where an expression can appear.
    private void checkEnum(final NamedType type, final Decl.EnumDecl declaration) {
        this.currentType = type;
        this.begin(true, TypeSymbol.Primitive.VOID, false);
        for (final Decl.EnumConstant constant : declaration.constants()) {
            if (constant.value() != null) {
                this.expect(this.check(constant.value(), TypeSymbol.Primitive.INT),
                        TypeSymbol.Primitive.INT, constant.value());
            }
        }
    }

    private void checkFieldInitializer(final Decl.FieldDecl field) {
        if (field.initializer() == null) {
            return;
        }
        final TypeSymbol declared = this.declarations.resolve(field.type());
        this.begin(field.modifiers().contains(Decl.Modifier.STATIC), TypeSymbol.Primitive.VOID, false);
        this.expect(this.check(field.initializer(), declared), declared, field.initializer());
    }

    private void checkMethod(final Decl.MethodDecl method) {
        if (method.body() == null) {
            return;
        }
        final TypeSymbol declared = this.declarations.resolve(method.returnType());
        this.begin(method.modifiers().contains(Decl.Modifier.STATIC), declared, false);
        this.declareParameters(method.parameters());
        this.checkBlock(method.body(), false);
        if (declared != TypeSymbol.Primitive.VOID && !alwaysReturns(method.body())) {
            this.report(method.line(), method.column(),
                    CannonError.MISSING_RETURN_VALUE, declared.describe());
        }
    }

    private void checkConstructor(final NamedType type, final Decl.ConstructorDecl constructor) {
        this.begin(false, TypeSymbol.Primitive.VOID, true);
        this.declareParameters(constructor.parameters());
        if (constructor.chained() != null) {
            this.checkChainedCall(type, constructor.chained());
        }
        if (constructor.body() != null) {
            this.checkBlock(constructor.body(), false);
        }
    }

    private void checkChainedCall(final NamedType type, final Decl.ConstructorCall chained) {
        final NamedType target = chained.base() ? type.base() : type;
        if (target == null) {
            this.report(chained.line(), chained.column(), CannonError.NO_BASE_CLASS, type.name());
            this.checkArguments(chained.arguments());
            return;
        }
        this.callConstructor(target, target, chained.arguments(), chained);
    }

    private void declareParameters(final List<Decl.Parameter> parameters) {
        for (final Decl.Parameter parameter : parameters) {
            final TypeSymbol type = this.declarations.resolve(parameter.type());
            if (!this.scope.declare(new Binding.Variable(parameter.name(), type, true))) {
                this.report(parameter.line(), parameter.column(),
                        CannonError.DUPLICATE_DECLARATION, parameter.name());
            }
        }
    }

    private void begin(final boolean isStatic, final TypeSymbol returns, final boolean constructor) {
        this.scope = new Scope(null);
        this.staticContext = isStatic;
        this.returnType = returns;
        this.inConstructor = constructor;
        this.loopDepth = 0;
        this.switchDepth = 0;
    }

    // ---------------------------------------------------------------- statements

    private void checkBlock(final Stmt.Block block, final boolean newScope) {
        final Scope saved = this.scope;
        if (newScope) {
            this.scope = new Scope(saved);
        }
        for (final Stmt statement : block.statements()) {
            this.checkStatement(statement);
        }
        this.scope = saved;
    }

    private void checkStatement(final Stmt statement) {
        switch (statement) {
            case Stmt.Block block -> this.checkBlock(block, true);
            case Stmt.If branch -> {
                this.condition(branch.condition());
                this.checkStatement(branch.then());
                if (branch.otherwise() != null) {
                    this.checkStatement(branch.otherwise());
                }
            }
            case Stmt.While loop -> {
                this.condition(loop.condition());
                this.loopDepth++;
                this.checkStatement(loop.body());
                this.loopDepth--;
            }
            case Stmt.DoWhile loop -> {
                this.loopDepth++;
                this.checkStatement(loop.body());
                this.loopDepth--;
                this.condition(loop.condition());
            }
            case Stmt.For loop -> this.checkFor(loop);
            case Stmt.ForEach loop -> this.checkForEach(loop);
            case Stmt.Switch choice -> this.checkSwitch(choice);
            case Stmt.Break stop -> {
                if (this.loopDepth == 0 && this.switchDepth == 0) {
                    this.report(stop.line(), stop.column(), CannonError.BREAK_OUTSIDE_LOOP);
                }
            }
            case Stmt.Continue next -> {
                if (this.loopDepth == 0) {
                    this.report(next.line(), next.column(), CannonError.CONTINUE_OUTSIDE_LOOP);
                }
            }
            case Stmt.Return give -> this.checkReturn(give);
            case Stmt.LocalDecl local -> this.checkLocal(local);
            case Stmt.ExprStmt expression -> this.check(expression.expression(), null);
            case Stmt.Dispose dispose -> this.checkDispose(dispose);
            case Stmt.Empty ignored -> { }
        }
    }

    private void checkFor(final Stmt.For loop) {
        final Scope saved = this.scope;
        this.scope = new Scope(saved);
        for (final Stmt initializer : loop.initializers()) {
            this.checkStatement(initializer);
        }
        if (loop.condition() != null) {
            this.condition(loop.condition());
        }
        for (final Expr update : loop.updates()) {
            this.check(update, null);
        }
        this.loopDepth++;
        this.checkStatement(loop.body());
        this.loopDepth--;
        this.scope = saved;
    }

    private void checkForEach(final Stmt.ForEach loop) {
        final TypeSymbol source = this.check(loop.source(), null);
        final TypeSymbol element = this.rules.elementOf(source);
        if (element == null) {
            this.report(loop.line(), loop.column(), CannonError.NOT_A_COLLECTION, source.describe());
        }
        final TypeSymbol found = element == null ? TypeSymbol.Special.ERROR : element;
        final TypeSymbol declared = isInferred(loop.type()) ? found : this.declarations.resolve(loop.type());
        if (!this.rules.isAssignable(found, declared)) {
            this.report(loop.line(), loop.column(),
                    CannonError.CANNOT_CONVERT, found.describe(), declared.describe());
        }
        final Scope saved = this.scope;
        this.scope = new Scope(saved);
        if (!this.scope.declare(new Binding.Variable(loop.name(), declared, false))) {
            this.report(loop.line(), loop.column(), CannonError.DUPLICATE_DECLARATION, loop.name());
        }
        this.loopDepth++;
        this.checkStatement(loop.body());
        this.loopDepth--;
        this.scope = saved;
    }

    private void checkSwitch(final Stmt.Switch choice) {
        final TypeSymbol value = this.check(choice.value(), null);
        final Set<String> seen = new HashSet<>();
        this.switchDepth++;
        for (final Stmt.SwitchSection section : choice.sections()) {
            for (final Expr label : section.labels()) {
                final TypeSymbol labelType = this.check(label, value);
                if (!this.rules.isAssignable(labelType, value)) {
                    this.report(label.line(), label.column(),
                            CannonError.CANNOT_CONVERT, labelType.describe(), value.describe());
                } else if (label instanceof Expr.Literal literal && !seen.add(String.valueOf(literal.value()))) {
                    this.report(label.line(), label.column(), CannonError.DUPLICATE_SWITCH_LABEL);
                }
            }
            final Scope saved = this.scope;
            this.scope = new Scope(saved);
            for (final Stmt statement : section.statements()) {
                this.checkStatement(statement);
            }
            this.scope = saved;
        }
        this.switchDepth--;
    }

    private void checkReturn(final Stmt.Return give) {
        if (give.value() == null) {
            if (this.returnType != TypeSymbol.Primitive.VOID) {
                this.report(give.line(), give.column(),
                        CannonError.MISSING_RETURN_VALUE, this.returnType.describe());
            }
            return;
        }
        final TypeSymbol value = this.check(give.value(), this.returnType);
        if (this.returnType == TypeSymbol.Primitive.VOID) {
            this.report(give.line(), give.column(), CannonError.UNEXPECTED_RETURN_VALUE);
            return;
        }
        this.expect(value, this.returnType, give.value());
    }

    private void checkLocal(final Stmt.LocalDecl local) {
        final TypeSymbol declared = isInferred(local.type()) ? this.inferred(local) : this.written(local);
        if (!this.scope.declare(new Binding.Variable(local.name(), declared, false))) {
            this.report(local.line(), local.column(),
                    CannonError.DUPLICATE_DECLARATION, local.name());
        }
    }

    // "var" takes the type of what it is given, which means it has to be given something, and
    // something with a type of its own: null and a call that gives nothing back have neither.
    private TypeSymbol inferred(final Stmt.LocalDecl local) {
        if (local.initializer() == null) {
            this.report(local.line(), local.column(),
                    CannonError.CANNOT_CONVERT, "nothing", INFERRED);
            return TypeSymbol.Special.ERROR;
        }
        final TypeSymbol found = this.check(local.initializer(), null);
        if (found == TypeSymbol.Special.NULL || found == TypeSymbol.Primitive.VOID) {
            this.report(local.line(), local.column(),
                    CannonError.CANNOT_CONVERT, found.describe(), INFERRED);
            return TypeSymbol.Special.ERROR;
        }
        return found;
    }

    private TypeSymbol written(final Stmt.LocalDecl local) {
        final TypeSymbol declared = this.declarations.resolve(local.type());
        if (local.initializer() != null) {
            this.expect(this.check(local.initializer(), declared), declared, local.initializer());
        }
        return declared;
    }

    private void checkDispose(final Stmt.Dispose dispose) {
        final TypeSymbol target = this.check(dispose.target(), null);
        if (!this.rules.isError(target) && !this.rules.isReference(target)) {
            this.report(dispose.line(), dispose.column(),
                    CannonError.CANNOT_DISPOSE, target.describe());
        }
    }

    private void condition(final Expr expression) {
        final TypeSymbol type = this.check(expression, TypeSymbol.Primitive.BOOL);
        if (!this.rules.isError(type) && type != TypeSymbol.Primitive.BOOL) {
            this.report(expression.line(), expression.column(),
                    CannonError.CONDITION_MUST_BE_BOOL, type.describe());
        }
    }

    private static boolean isInferred(final TypeRef reference) {
        return reference != null && INFERRED.equals(reference.name())
                && reference.arrayRank() == 0 && reference.arguments().isEmpty();
    }

    // A method that gives something back has to do it on every way out. This knows the shapes that
    // certainly leave; anything else counts as a path that falls off the end.
    private static boolean alwaysReturns(final Stmt statement) {
        return switch (statement) {
            case Stmt.Return ignored -> true;
            case Stmt.Block block -> block.statements().stream().anyMatch(BodyChecker::alwaysReturns);
            case Stmt.If branch -> branch.otherwise() != null
                    && alwaysReturns(branch.then()) && alwaysReturns(branch.otherwise());
            case Stmt.While loop -> isAlwaysTrue(loop.condition());
            case Stmt.DoWhile loop -> alwaysReturns(loop.body()) || isAlwaysTrue(loop.condition());
            case Stmt.For loop -> loop.condition() == null || isAlwaysTrue(loop.condition());
            case Stmt.Switch choice -> choice.sections().stream().anyMatch(Stmt.SwitchSection::fallback)
                    && choice.sections().stream().allMatch(section ->
                            section.statements().stream().anyMatch(BodyChecker::alwaysReturns));
            default -> false;
        };
    }

    private static boolean isAlwaysTrue(final Expr condition) {
        return condition instanceof Expr.Literal literal && Boolean.TRUE.equals(literal.value());
    }

    // ---------------------------------------------------------------- expressions

    private TypeSymbol check(final Expr expression, final TypeSymbol expected) {
        if (expression == null) {
            return TypeSymbol.Special.ERROR;
        }
        final TypeSymbol type = switch (expression) {
            case Expr.Literal literal -> this.literalType(literal);
            case Expr.Name name -> this.nameType(name, expected);
            case Expr.This self -> this.thisType(self);
            case Expr.Base base -> this.baseType(base);
            case Expr.Unary unary -> this.unaryType(unary);
            case Expr.Binary binary -> this.binaryType(binary);
            case Expr.Assign assign -> this.assignType(assign);
            case Expr.Conditional conditional -> this.conditionalType(conditional, expected);
            case Expr.Call call -> this.callType(call);
            case Expr.Member member -> this.memberType(member, expected);
            case Expr.Index index -> this.indexType(index);
            case Expr.New created -> this.newType(created);
            case Expr.NewArray created -> this.newArrayType(created);
            case Expr.Cast cast -> this.castType(cast);
            case Expr.TypeTest test -> this.typeTestType(test);
            case Expr.Lambda lambda -> this.lambdaType(lambda, expected);
        };
        this.model.setType(expression, type);
        return type;
    }

    private TypeSymbol literalType(final Expr.Literal literal) {
        return switch (literal.kind()) {
            case INT_LITERAL -> TypeSymbol.Primitive.INT;
            case LONG_LITERAL -> TypeSymbol.Primitive.LONG;
            case FLOAT_LITERAL -> TypeSymbol.Primitive.FLOAT;
            case DOUBLE_LITERAL -> TypeSymbol.Primitive.DOUBLE;
            case CHAR_LITERAL -> TypeSymbol.Primitive.CHAR;
            case STRING_LITERAL -> this.builtIns.stringType();
            case TRUE, FALSE -> TypeSymbol.Primitive.BOOL;
            default -> TypeSymbol.Special.NULL;
        };
    }

    private TypeSymbol nameType(final Expr.Name name, final TypeSymbol expected) {
        final Binding.Variable variable = this.scope.lookup(name.identifier());
        if (variable != null) {
            this.model.setBinding(name, variable);
            return variable.type();
        }
        if (this.currentType != null) {
            final List<MemberSymbol> members = lookup(this.currentType, name.identifier());
            if (!members.isEmpty()) {
                return this.bindMember(name, this.currentType, members, expected, Access.IMPLICIT);
            }
        }
        final TypeSymbol type = this.namedType(name.identifier());
        if (type != null) {
            this.model.setBinding(name, new Binding.TypeName(type));
            return type;
        }
        this.report(name.line(), name.column(), CannonError.UNKNOWN_NAME, name.identifier());
        return TypeSymbol.Special.ERROR;
    }

    private TypeSymbol namedType(final String name) {
        final NamedType declared = this.model.declaredType(name);
        return declared != null ? declared : this.builtIns.type(name, 0);
    }

    private TypeSymbol thisType(final Expr.This self) {
        if (this.staticContext) {
            this.report(self.line(), self.column(), CannonError.THIS_IN_STATIC, "this");
            return TypeSymbol.Special.ERROR;
        }
        return this.currentType == null ? TypeSymbol.Special.ERROR : this.currentType;
    }

    private TypeSymbol baseType(final Expr.Base base) {
        if (this.staticContext) {
            this.report(base.line(), base.column(), CannonError.THIS_IN_STATIC, "base");
            return TypeSymbol.Special.ERROR;
        }
        if (this.currentType == null || this.currentType.base() == null) {
            this.report(base.line(), base.column(), CannonError.NO_BASE_CLASS,
                    this.currentType == null ? "?" : this.currentType.name());
            return TypeSymbol.Special.ERROR;
        }
        return this.currentType.base();
    }

    private TypeSymbol unaryType(final Expr.Unary unary) {
        final TypeSymbol operand = this.check(unary.operand(), null);
        final TypeSymbol result = this.rules.unaryResult(unary.operator(), operand);
        if (result == null) {
            this.report(unary.line(), unary.column(), CannonError.OPERATOR_ON_TYPE,
                    unary.operator().text(), operand.describe());
            return TypeSymbol.Special.ERROR;
        }
        return result;
    }

    private TypeSymbol binaryType(final Expr.Binary binary) {
        final TypeSymbol left = this.check(binary.left(), null);
        final TypeSymbol right = this.check(binary.right(), null);
        final TypeSymbol result = this.rules.binaryResult(binary.operator(), left, right);
        if (result == null) {
            this.report(binary.line(), binary.column(), CannonError.OPERATOR_ON_TYPES,
                    binary.operator().text(), left.describe(), right.describe());
            return TypeSymbol.Special.ERROR;
        }
        return result;
    }

    private TypeSymbol conditionalType(final Expr.Conditional conditional, final TypeSymbol expected) {
        this.condition(conditional.condition());
        final TypeSymbol whenTrue = this.check(conditional.whenTrue(), expected);
        final TypeSymbol whenFalse = this.check(conditional.whenFalse(), expected);
        if (this.rules.isAssignable(whenFalse, whenTrue)) {
            return whenTrue;
        }
        if (this.rules.isAssignable(whenTrue, whenFalse)) {
            return whenFalse;
        }
        this.report(conditional.line(), conditional.column(),
                CannonError.CANNOT_CONVERT, whenFalse.describe(), whenTrue.describe());
        return TypeSymbol.Special.ERROR;
    }

    private TypeSymbol indexType(final Expr.Index index) {
        final TypeSymbol target = this.check(index.target(), null);
        final TypeSymbol key = this.check(index.index(), null);
        final TypeSymbol result = this.rules.indexResult(target, key);
        if (result == null) {
            this.report(index.line(), index.column(), CannonError.CANNOT_INDEX,
                    target.describe(), key.describe());
            return TypeSymbol.Special.ERROR;
        }
        return result;
    }

    private TypeSymbol newType(final Expr.New created) {
        final TypeSymbol type = this.declarations.resolve(created.type());
        final NamedType named = this.rules.named(type);
        if (named == null || named.kind() != NamedType.Kind.CLASS) {
            if (!this.rules.isError(type)) {
                this.report(created.line(), created.column(),
                        CannonError.CANNOT_CREATE, type.describe());
            }
            this.checkArguments(created.arguments());
            return this.rules.isError(type) ? TypeSymbol.Special.ERROR : type;
        }
        this.callConstructor(named, type, created.arguments(), created);
        return type;
    }

    // A class with no constructor of its own can be made with no arguments and nothing else.
    private void callConstructor(final NamedType named, final TypeSymbol type, final List<Expr> arguments,
                                 final Node at) {
        final List<MemberSymbol.MethodSymbol> candidates = new ArrayList<>();
        for (final MemberSymbol member : named.members()) {
            if (member instanceof MemberSymbol.ConstructorSymbol constructor) {
                candidates.add(new MemberSymbol.MethodSymbol(named, named.name(), type,
                        constructor.parameters(), constructor.modifiers()));
            }
        }
        if (candidates.isEmpty()) {
            this.checkArguments(arguments);
            if (!arguments.isEmpty()) {
                this.report(at.line(), at.column(),
                        CannonError.NO_MATCHING_OVERLOAD, named.name());
            }
            return;
        }
        this.callWith(candidates, arguments, named.name(), at);
    }

    private TypeSymbol newArrayType(final Expr.NewArray created) {
        final TypeSymbol element = this.declarations.resolve(created.elementType());
        this.expect(this.check(created.length(), TypeSymbol.Primitive.INT),
                TypeSymbol.Primitive.INT, created.length());
        return new TypeSymbol.ArrayType(element);
    }

    private TypeSymbol castType(final Expr.Cast cast) {
        final TypeSymbol target = this.declarations.resolve(cast.type());
        final TypeSymbol value = this.check(cast.value(), null);
        if (!this.rules.isAssignable(value, target) && !this.rules.isAssignable(target, value)) {
            this.report(cast.line(), cast.column(),
                    CannonError.CANNOT_CONVERT, value.describe(), target.describe());
        }
        return target;
    }

    private TypeSymbol typeTestType(final Expr.TypeTest test) {
        final TypeSymbol value = this.check(test.value(), null);
        final TypeSymbol target = this.declarations.resolve(test.type());
        final String written = test.conversion() ? "as" : "is";
        if (!this.rules.isError(value) && !this.rules.isReference(value)) {
            this.report(test.line(), test.column(),
                    CannonError.OPERATOR_ON_TYPE, written, value.describe());
        }
        if (test.conversion() && !this.rules.isError(target) && !this.rules.isReference(target)) {
            this.report(test.line(), test.column(),
                    CannonError.OPERATOR_ON_TYPE, written, target.describe());
            return TypeSymbol.Special.ERROR;
        }
        return test.conversion() ? target : TypeSymbol.Primitive.BOOL;
    }

    // ---------------------------------------------------------------- members

    private TypeSymbol memberType(final Expr.Member member, final TypeSymbol expected) {
        final TypeSymbol target = this.check(member.target(), null);
        if (this.rules.isError(target)) {
            return TypeSymbol.Special.ERROR;
        }
        final Access access = this.model.bindingOf(member.target()) instanceof Binding.TypeName
                ? Access.TYPE : Access.INSTANCE;
        final List<MemberSymbol> found = this.membersOf(target, member.name(), member);
        return found.isEmpty()
                ? TypeSymbol.Special.ERROR
                : this.bindMember(member, target, found, expected, access);
    }

    private List<MemberSymbol> membersOf(final TypeSymbol target, final String name, final Node at) {
        final NamedType named = this.rules.named(target);
        final List<MemberSymbol> found = named == null ? List.of() : lookup(named, name);
        if (found.isEmpty()) {
            this.report(at.line(), at.column(),
                    CannonError.NO_SUCH_MEMBER, target.describe(), name);
        }
        return found;
    }

    // A name that turned out to be a member: a value if it holds one, and a method only where a
    // delegate of the same shape is wanted, which is how a handler is handed over without brackets.
    private TypeSymbol bindMember(final Expr expression, final TypeSymbol receiver,
                                  final List<MemberSymbol> members, final TypeSymbol expected,
                                  final Access access) {
        final MemberSymbol first = members.getFirst();
        if (first instanceof MemberSymbol.MethodSymbol) {
            return this.methodGroupType(expression, receiver, members, expected, access);
        }
        if (!this.checkAccess(expression, first, access)) {
            return TypeSymbol.Special.ERROR;
        }
        final List<TypeSymbol> arguments = this.rules.arguments(receiver);
        final TypeSymbol type = switch (first) {
            case MemberSymbol.FieldSymbol field -> this.rules.substitute(field.type(), arguments);
            case MemberSymbol.PropertySymbol property -> this.rules.substitute(property.type(), arguments);
            case MemberSymbol.EventSymbol event -> event.delegateType();
            default -> TypeSymbol.Special.ERROR;
        };
        this.model.setBinding(expression, new Binding.Member(first, type));
        return type;
    }

    private TypeSymbol methodGroupType(final Expr expression, final TypeSymbol receiver,
                                       final List<MemberSymbol> members, final TypeSymbol expected,
                                       final Access access) {
        final NamedType wanted = this.rules.named(expected);
        if (wanted == null || wanted.kind() != NamedType.Kind.DELEGATE || wanted.invoke() == null) {
            this.report(expression.line(), expression.column(),
                    CannonError.METHOD_AS_VALUE, members.getFirst().name());
            return TypeSymbol.Special.ERROR;
        }
        final List<TypeSymbol> wantedArguments = this.rules.arguments(expected);
        final List<TypeSymbol> ownArguments = this.rules.arguments(receiver);
        for (final MemberSymbol member : members) {
            final MemberSymbol.MethodSymbol candidate = (MemberSymbol.MethodSymbol) member;
            if (this.matchesShape(candidate, wanted.invoke(), wantedArguments, ownArguments)
                    && this.checkAccess(expression, candidate, access)) {
                this.model.setBinding(expression, new Binding.Member(candidate, expected));
                this.model.setCall(expression, candidate);
                return expected;
            }
        }
        this.report(expression.line(), expression.column(),
                CannonError.LAMBDA_SHAPE, expected.describe());
        return TypeSymbol.Special.ERROR;
    }

    private boolean matchesShape(final MemberSymbol.MethodSymbol candidate, final MemberSymbol.MethodSymbol shape,
                                 final List<TypeSymbol> wantedArguments, final List<TypeSymbol> ownArguments) {
        if (candidate.parameters().size() != shape.parameters().size()) {
            return false;
        }
        for (int i = 0; i < shape.parameters().size(); i++) {
            final TypeSymbol wanted = this.rules.substitute(shape.parameters().get(i).type(), wantedArguments);
            final TypeSymbol given = this.rules.substitute(candidate.parameters().get(i).type(), ownArguments);
            if (!wanted.equals(given)) {
                return false;
            }
        }
        final TypeSymbol wantedReturn = this.rules.substitute(shape.returnType(), wantedArguments);
        return this.rules.isAssignable(this.rules.substitute(candidate.returnType(), ownArguments), wantedReturn);
    }

    private boolean checkAccess(final Expr expression, final MemberSymbol member, final Access access) {
        switch (access) {
            case TYPE:
                if (!member.isStatic()) {
                    this.report(expression.line(), expression.column(),
                            CannonError.INSTANCE_THROUGH_TYPE, member.name());
                    return false;
                }
                return true;
            case INSTANCE:
                if (member.isStatic()) {
                    this.report(expression.line(), expression.column(),
                            CannonError.STATIC_THROUGH_INSTANCE, member.name());
                    return false;
                }
                return true;
            default:
                if (!member.isStatic() && this.staticContext) {
                    this.report(expression.line(), expression.column(),
                            CannonError.THIS_IN_STATIC, member.name());
                    return false;
                }
                return true;
        }
    }

    // ---------------------------------------------------------------- calls

    private TypeSymbol callType(final Expr.Call call) {
        if (call.callee() instanceof Expr.Name name && this.scope.lookup(name.identifier()) == null
                && this.currentType != null) {
            final List<MemberSymbol> members = lookup(this.currentType, name.identifier());
            if (!members.isEmpty() && members.getFirst() instanceof MemberSymbol.MethodSymbol) {
                return this.callMembers(call, name, this.currentType, members,
                        name.identifier(), Access.IMPLICIT);
            }
        }
        if (call.callee() instanceof Expr.Member member) {
            return this.callThroughMember(call, member);
        }
        return this.invoke(call, this.check(call.callee(), null));
    }

    private TypeSymbol callThroughMember(final Expr.Call call, final Expr.Member member) {
        final TypeSymbol target = this.check(member.target(), null);
        if (this.rules.isError(target)) {
            this.checkArguments(call.arguments());
            return TypeSymbol.Special.ERROR;
        }
        final Access access = this.model.bindingOf(member.target()) instanceof Binding.TypeName
                ? Access.TYPE : Access.INSTANCE;
        final List<MemberSymbol> members = this.membersOf(target, member.name(), member);
        if (members.isEmpty()) {
            this.checkArguments(call.arguments());
            return TypeSymbol.Special.ERROR;
        }
        if (members.getFirst() instanceof MemberSymbol.MethodSymbol) {
            return this.callMembers(call, member, target, members, member.name(), access);
        }
        final TypeSymbol held = this.bindMember(member, target, members, null, access);
        this.model.setType(member, held);
        return this.invoke(call, held);
    }

    // Calling something that holds a delegate: a local, a parameter, a field, a property or an event.
    private TypeSymbol invoke(final Expr.Call call, final TypeSymbol callee) {
        if (this.rules.isError(callee)) {
            this.checkArguments(call.arguments());
            return TypeSymbol.Special.ERROR;
        }
        final NamedType named = this.rules.named(callee);
        if (named == null || named.kind() != NamedType.Kind.DELEGATE || named.invoke() == null) {
            this.report(call.line(), call.column(), CannonError.CANNOT_CALL, callee.describe());
            this.checkArguments(call.arguments());
            return TypeSymbol.Special.ERROR;
        }
        this.checkEventRaise(call);
        final List<TypeSymbol> arguments = this.rules.arguments(callee);
        final MemberSymbol.MethodSymbol invoke = this.fill(named.invoke(), arguments);
        this.callWith(List.of(invoke), call.arguments(), named.name(), call);
        return invoke.returnType();
    }

    // Raising an event is only allowed where it was declared, as in the language this one borrows
    // from: everywhere else an event is something to subscribe to, not something to fire.
    private void checkEventRaise(final Expr.Call call) {
        if (this.model.bindingOf(call.callee()) instanceof Binding.Member member
                && member.member() instanceof MemberSymbol.EventSymbol event
                && event.owner() != this.currentType) {
            this.report(call.line(), call.column(), CannonError.EVENT_OUTSIDE_ITS_TYPE);
        }
    }

    private TypeSymbol callMembers(final Expr.Call call, final Expr callee, final TypeSymbol receiver,
                                   final List<MemberSymbol> members, final String name, final Access access) {
        if (!this.checkAccess(callee, members.getFirst(), access)) {
            this.checkArguments(call.arguments());
            return TypeSymbol.Special.ERROR;
        }
        final List<TypeSymbol> arguments = this.rules.arguments(receiver);
        final List<MemberSymbol.MethodSymbol> candidates = new ArrayList<>();
        for (final MemberSymbol member : members) {
            if (member instanceof MemberSymbol.MethodSymbol method) {
                candidates.add(this.fill(method, arguments));
            }
        }
        final MemberSymbol.MethodSymbol chosen = this.callWith(candidates, call.arguments(), name, call);
        if (chosen != null) {
            this.model.setBinding(callee, new Binding.Member(chosen, chosen.returnType()));
        }
        return chosen == null ? TypeSymbol.Special.ERROR : chosen.returnType();
    }

    // A method read off a filled-in collection has its stand-in types replaced by what that
    // collection holds, so List<string>.Get gives back a string and not a T.
    private MemberSymbol.MethodSymbol fill(final MemberSymbol.MethodSymbol method,
                                           final List<TypeSymbol> arguments) {
        if (arguments.isEmpty()) {
            return method;
        }
        final List<MemberSymbol.ParameterSymbol> parameters = new ArrayList<>();
        for (final MemberSymbol.ParameterSymbol parameter : method.parameters()) {
            parameters.add(new MemberSymbol.ParameterSymbol(parameter.name(),
                    this.rules.substitute(parameter.type(), arguments)));
        }
        return new MemberSymbol.MethodSymbol(method.owner(), method.name(),
                this.rules.substitute(method.returnType(), arguments), parameters, method.modifiers());
    }

    /**
     * Picks the version that fits and checks the arguments against it. A lambda has no type until it
     * knows what it is being handed to, so it is left out of the choosing and checked afterwards,
     * against the version that won.
     */
    private MemberSymbol.MethodSymbol callWith(final List<MemberSymbol.MethodSymbol> candidates,
                                               final List<Expr> arguments, final String name, final Node at) {
        final List<TypeSymbol> given = new ArrayList<>();
        for (final Expr argument : arguments) {
            final boolean waits = argument instanceof Expr.Lambda || this.isMethodGroup(argument);
            given.add(waits ? null : this.check(argument, null));
        }
        final List<MemberSymbol.MethodSymbol> fitting = new ArrayList<>();
        int best = -1;
        for (final MemberSymbol.MethodSymbol candidate : candidates) {
            final int score = this.score(candidate, given);
            if (score < 0) {
                continue;
            }
            if (score > best) {
                best = score;
                fitting.clear();
            }
            if (score == best) {
                fitting.add(candidate);
            }
        }
        if (fitting.isEmpty()) {
            return this.reportNoFit(candidates, arguments, given, name, at);
        }
        if (fitting.size() > 1) {
            this.report(at.line(), at.column(), CannonError.AMBIGUOUS_CALL, name);
        }
        final MemberSymbol.MethodSymbol chosen = fitting.getFirst();
        this.checkAgainst(chosen, arguments, given);
        if (at instanceof Expr expression) {
            this.model.setCall(expression, chosen);
        }
        return chosen;
    }

    // When only one version could have been meant, saying which argument is wrong beats saying that
    // none of them fit: with a single version there is nothing to choose between.
    private MemberSymbol.MethodSymbol reportNoFit(final List<MemberSymbol.MethodSymbol> candidates,
                                                  final List<Expr> arguments, final List<TypeSymbol> given,
                                                  final String name, final Node at) {
        final List<MemberSymbol.MethodSymbol> sameCount = new ArrayList<>();
        for (final MemberSymbol.MethodSymbol candidate : candidates) {
            if (candidate.parameters().size() == given.size()) {
                sameCount.add(candidate);
            }
        }
        if (sameCount.size() == 1) {
            final MemberSymbol.MethodSymbol only = sameCount.getFirst();
            this.checkAgainst(only, arguments, given);
            if (at instanceof Expr expression) {
                this.model.setCall(expression, only);
            }
            return only;
        }
        this.report(at.line(), at.column(), CannonError.NO_MATCHING_OVERLOAD, name);
        for (int i = 0; i < arguments.size(); i++) {
            if (given.get(i) == null) {
                this.check(arguments.get(i), TypeSymbol.Special.ERROR);
            }
        }
        return null;
    }

    private void checkAgainst(final MemberSymbol.MethodSymbol chosen, final List<Expr> arguments,
                              final List<TypeSymbol> given) {
        for (int i = 0; i < arguments.size(); i++) {
            final TypeSymbol wanted = chosen.parameters().get(i).type();
            if (given.get(i) == null) {
                this.check(arguments.get(i), wanted);
            } else {
                this.expect(given.get(i), wanted, arguments.get(i));
            }
        }
    }

    // How well a version fits: an exact type counts double, a conversion counts once, and anything
    // that does not fit at all rules the version out.
    private int score(final MemberSymbol.MethodSymbol candidate, final List<TypeSymbol> given) {
        if (candidate.parameters().size() != given.size()) {
            return -1;
        }
        int total = 0;
        for (int i = 0; i < given.size(); i++) {
            final TypeSymbol wanted = candidate.parameters().get(i).type();
            final TypeSymbol argument = given.get(i);
            if (argument == null) {
                final NamedType named = this.rules.named(wanted);
                if (named == null || named.kind() != NamedType.Kind.DELEGATE) {
                    return -1;
                }
                total += 1;
            } else if (argument.equals(wanted)) {
                total += 2;
            } else if (this.rules.isAssignable(argument, wanted)) {
                total += 1;
            } else {
                return -1;
            }
        }
        return total;
    }

    private void checkArguments(final List<Expr> arguments) {
        for (final Expr argument : arguments) {
            this.check(argument, null);
        }
    }

    // ---------------------------------------------------------------- assignment and lambdas

    private TypeSymbol assignType(final Expr.Assign assign) {
        final TypeSymbol target = this.check(assign.target(), null);
        final Binding binding = this.model.bindingOf(assign.target());
        if (binding instanceof Binding.Member member
                && member.member() instanceof MemberSymbol.EventSymbol event) {
            return this.subscribe(assign, event);
        }
        final TypeSymbol value = this.check(assign.value(), target);
        if (this.rules.isError(target) || !this.writable(assign, binding)) {
            return this.rules.isError(target) ? TypeSymbol.Special.ERROR : target;
        }
        if (assign.operator() == Operator.ASSIGN) {
            this.expect(value, target, assign.value());
            return target;
        }
        final TypeSymbol combined = this.rules.binaryResult(assign.operator(), target, value);
        if (combined == null || !this.rules.isAssignable(combined, target)) {
            this.report(assign.line(), assign.column(), CannonError.OPERATOR_ON_TYPES,
                    assign.operator().text() + "=", target.describe(), value.describe());
        }
        return target;
    }

    private TypeSymbol subscribe(final Expr.Assign assign, final MemberSymbol.EventSymbol event) {
        if (assign.operator() != Operator.ADD && assign.operator() != Operator.SUBTRACT) {
            this.report(assign.line(), assign.column(), CannonError.OPERATOR_ON_TYPE,
                    assign.operator().text() + "=", event.delegateType().name());
            this.check(assign.value(), event.delegateType());
            return TypeSymbol.Special.ERROR;
        }
        this.expect(this.check(assign.value(), event.delegateType()), event.delegateType(), assign.value());
        return event.delegateType();
    }

    private boolean writable(final Expr.Assign assign, final Binding binding) {
        if (!(binding instanceof Binding.Member member)) {
            return true;
        }
        if (member.member() instanceof MemberSymbol.FieldSymbol field && field.isReadOnly()
                && !(this.inConstructor && field.owner() == this.currentType)) {
            this.report(assign.line(), assign.column(),
                    CannonError.CANNOT_ASSIGN_READONLY, field.name());
            return false;
        }
        if (member.member() instanceof MemberSymbol.PropertySymbol property && !property.writable()) {
            this.report(assign.line(), assign.column(),
                    CannonError.CANNOT_ASSIGN_READONLY, property.name());
            return false;
        }
        return true;
    }

    private TypeSymbol lambdaType(final Expr.Lambda lambda, final TypeSymbol expected) {
        final NamedType wanted = this.rules.named(expected);
        if (wanted == null || wanted.kind() != NamedType.Kind.DELEGATE || wanted.invoke() == null) {
            if (!this.rules.isError(expected)) {
                this.report(lambda.line(), lambda.column(), CannonError.LAMBDA_SHAPE,
                        expected == null ? "nothing" : expected.describe());
            }
            return TypeSymbol.Special.ERROR;
        }
        final MemberSymbol.MethodSymbol shape = this.fill(wanted.invoke(), this.rules.arguments(expected));
        if (shape.parameters().size() != lambda.parameters().size()) {
            this.report(lambda.line(), lambda.column(),
                    CannonError.LAMBDA_SHAPE, expected.describe());
            return TypeSymbol.Special.ERROR;
        }
        final Scope saved = this.scope;
        final TypeSymbol savedReturn = this.returnType;
        this.scope = new Scope(saved);
        this.declareLambdaParameters(lambda, shape);
        this.returnType = shape.returnType();
        if (lambda.block() != null) {
            this.checkBlock(lambda.block(), false);
        } else {
            final TypeSymbol body = this.check(lambda.body(), shape.returnType());
            if (shape.returnType() != TypeSymbol.Primitive.VOID) {
                this.expect(body, shape.returnType(), lambda.body());
            }
        }
        this.scope = saved;
        this.returnType = savedReturn;
        return expected;
    }

    private void declareLambdaParameters(final Expr.Lambda lambda, final MemberSymbol.MethodSymbol shape) {
        for (int i = 0; i < lambda.parameters().size(); i++) {
            final Decl.Parameter parameter = lambda.parameters().get(i);
            final TypeSymbol fromShape = shape.parameters().get(i).type();
            TypeSymbol type = fromShape;
            if (parameter.type() != null) {
                type = this.declarations.resolve(parameter.type());
                if (!type.equals(fromShape)) {
                    this.report(parameter.line(), parameter.column(),
                            CannonError.CANNOT_CONVERT, fromShape.describe(), type.describe());
                }
            }
            if (!this.scope.declare(new Binding.Variable(parameter.name(), type, true))) {
                this.report(parameter.line(), parameter.column(),
                        CannonError.DUPLICATE_DECLARATION, parameter.name());
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    // Every message goes through here so a look-ahead can be taken back. Working out whether an
    // argument is a method being handed over means resolving it, and resolving it must not complain
    // about what the real pass is about to do properly.
    private void report(final int line, final int column, final CannonError error, final Object... arguments) {
        if (this.quiet == 0) {
            this.diagnostics.error(line, column, error, arguments);
        }
    }

    // A name or a member that turns out to be a method, written without brackets. Like a lambda, it
    // has no type of its own until it is known what it is being handed to.
    private boolean isMethodGroup(final Expr expression) {
        if (expression instanceof Expr.Name name) {
            if (this.scope.lookup(name.identifier()) != null || this.currentType == null) {
                return false;
            }
            final List<MemberSymbol> found = lookup(this.currentType, name.identifier());
            return !found.isEmpty() && found.getFirst() instanceof MemberSymbol.MethodSymbol;
        }
        if (expression instanceof Expr.Member member) {
            this.quiet++;
            final TypeSymbol target = this.check(member.target(), null);
            this.quiet--;
            final NamedType named = this.rules.named(target);
            if (named == null) {
                return false;
            }
            final List<MemberSymbol> found = lookup(named, member.name());
            return !found.isEmpty() && found.getFirst() instanceof MemberSymbol.MethodSymbol;
        }
        return false;
    }

    private void expect(final TypeSymbol given, final TypeSymbol wanted, final Node at) {
        if (!this.rules.isAssignable(given, wanted)) {
            this.report(at.line(), at.column(),
                    CannonError.CANNOT_CONVERT, given.describe(), wanted.describe());
        }
    }

    // The nearest declaration wins. A class that writes a method its interface also declares would
    // otherwise offer the same method twice and every call of it would look ambiguous.
    private static List<MemberSymbol> lookup(final NamedType type, final String name) {
        final List<MemberSymbol> found = new ArrayList<>();
        for (final MemberSymbol member : type.allMembers()) {
            if (!member.name().equals(name) || member instanceof MemberSymbol.ConstructorSymbol) {
                continue;
            }
            if (found.isEmpty()) {
                found.add(member);
                continue;
            }
            if (!(found.getFirst() instanceof MemberSymbol.MethodSymbol)
                    || !(member instanceof MemberSymbol.MethodSymbol method)) {
                continue;
            }
            boolean alreadyThere = false;
            for (final MemberSymbol seen : found) {
                alreadyThere = alreadyThere
                        || sameSignature((MemberSymbol.MethodSymbol) seen, method);
            }
            if (!alreadyThere) {
                found.add(member);
            }
        }
        return found;
    }

    private static boolean sameSignature(final MemberSymbol.MethodSymbol left,
                                         final MemberSymbol.MethodSymbol right) {
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
}
