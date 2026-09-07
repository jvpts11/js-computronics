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
import dev.jstech.computronics.cannon.ast.IDecl;
import dev.jstech.computronics.cannon.ast.IExpr;
import dev.jstech.computronics.cannon.ast.INode;
import dev.jstech.computronics.cannon.ast.Operator;
import dev.jstech.computronics.cannon.ast.IStmt;
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
    private ITypeSymbol returnType = ITypeSymbol.Primitive.VOID;
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
            final IDecl.ITypeDecl source = this.declarations.source(type);
            this.diagnostics.setFile(this.declarations.fileOf(type));
            if (source instanceof IDecl.ClassDecl declaration) {
                this.checkClass(type, declaration);
            } else if (source instanceof IDecl.EnumDecl declaration) {
                this.checkEnum(type, declaration);
            }
        }
    }

    private void checkClass(final NamedType type, final IDecl.ClassDecl declaration) {
        this.currentType = type;
        for (final IDecl.IMemberDecl member : declaration.members()) {
            switch (member) {
                case IDecl.FieldDecl field -> this.checkFieldInitializer(field);
                case IDecl.MethodDecl method -> this.checkMethod(method);
                case IDecl.ConstructorDecl constructor -> this.checkConstructor(type, constructor);
                case IDecl.PropertyDecl ignored -> { }
                case IDecl.EventDecl ignored -> { }
            }
        }
    }

    // An enum's numbers are the one place outside a body where an expression can appear.
    private void checkEnum(final NamedType type, final IDecl.EnumDecl declaration) {
        this.currentType = type;
        this.begin(true, ITypeSymbol.Primitive.VOID, false);
        for (final IDecl.EnumConstant constant : declaration.constants()) {
            if (constant.value() == null) {
                continue;
            }
            this.expect(this.check(constant.value(), ITypeSymbol.Primitive.INT),
                    ITypeSymbol.Primitive.INT, constant.value());
            // The number has to be there in the source, not worked out from it: an enum's numbers are
            // what the assembly and every saved file are written with, so they are read, never computed.
            if (numberOf(constant.value()) == null) {
                this.report(constant.value().line(), constant.value().column(),
                        CannonError.ENUM_VALUE_MUST_BE_WRITTEN);
            }
        }
    }

    /** The number an enum's constant was written as, or null if it was written some other way. */
    public static Integer numberOf(final IExpr expression) {
        if (expression instanceof IExpr.Literal literal && literal.value() instanceof Integer value) {
            return value;
        }
        if (expression instanceof IExpr.Unary unary && unary.operator() == Operator.NEGATE
                && !unary.postfix() && unary.operand() instanceof IExpr.Literal literal
                && literal.value() instanceof Integer value) {
            return -value;
        }
        return null;
    }

    private void checkFieldInitializer(final IDecl.FieldDecl field) {
        if (field.initializer() == null) {
            return;
        }
        final ITypeSymbol declared = this.declarations.resolve(field.type());
        this.begin(field.modifiers().contains(IDecl.Modifier.STATIC), ITypeSymbol.Primitive.VOID, false);
        this.expect(this.check(field.initializer(), declared), declared, field.initializer());
    }

    private void checkMethod(final IDecl.MethodDecl method) {
        if (method.body() == null) {
            return;
        }
        final ITypeSymbol declared = this.declarations.resolve(method.returnType());
        this.begin(method.modifiers().contains(IDecl.Modifier.STATIC), declared, false);
        this.declareParameters(method.parameters());
        this.checkBlock(method.body(), false);
        this.checkOutParameters(method.parameters(), method.body(), method);
        if (declared != ITypeSymbol.Primitive.VOID && !alwaysReturns(method.body())) {
            this.report(method.line(), method.column(),
                    CannonError.MISSING_RETURN_VALUE, declared.describe());
        }
    }

    private void checkConstructor(final NamedType type, final IDecl.ConstructorDecl constructor) {
        this.begin(false, ITypeSymbol.Primitive.VOID, true);
        this.declareParameters(constructor.parameters());
        if (constructor.chained() != null) {
            this.checkChainedCall(type, constructor.chained());
        }
        if (constructor.body() != null) {
            this.checkBlock(constructor.body(), false);
        }
        this.checkOutParameters(constructor.parameters(), constructor.body(), constructor);
    }

    private void checkChainedCall(final NamedType type, final IDecl.ConstructorCall chained) {
        final NamedType target = chained.base() ? type.base() : type;
        if (target == null) {
            this.report(chained.line(), chained.column(), CannonError.NO_BASE_CLASS, type.name());
            this.checkArguments(chained.arguments());
            return;
        }
        this.callConstructor(target, target, chained.arguments(), chained);
    }

    private void declareParameters(final List<IDecl.Parameter> parameters) {
        for (final IDecl.Parameter parameter : parameters) {
            final ITypeSymbol type = this.declarations.resolve(parameter.type());
            final IBinding.Variable variable = new IBinding.Variable(parameter.name(), type, true);
            if (!this.scope.declare(variable)) {
                this.report(parameter.line(), parameter.column(),
                        CannonError.DUPLICATE_DECLARATION, parameter.name());
            }
            this.model.setDeclared(parameter, variable);
        }
    }

    private void begin(final boolean isStatic, final ITypeSymbol returns, final boolean constructor) {
        this.scope = new Scope(null);
        this.staticContext = isStatic;
        this.returnType = returns;
        this.inConstructor = constructor;
        this.loopDepth = 0;
        this.switchDepth = 0;
    }

    // ---------------------------------------------------------------- statements

    private void checkBlock(final IStmt.Block block, final boolean newScope) {
        final Scope saved = this.scope;
        if (newScope) {
            this.scope = new Scope(saved);
        }
        for (final IStmt statement : block.statements()) {
            this.checkStatement(statement);
        }
        this.scope = saved;
    }

    private void checkStatement(final IStmt statement) {
        switch (statement) {
            case IStmt.Block block -> this.checkBlock(block, true);
            case IStmt.If branch -> {
                this.condition(branch.condition());
                this.checkStatement(branch.then());
                if (branch.otherwise() != null) {
                    this.checkStatement(branch.otherwise());
                }
            }
            case IStmt.While loop -> {
                this.condition(loop.condition());
                this.loopDepth++;
                this.checkStatement(loop.body());
                this.loopDepth--;
            }
            case IStmt.DoWhile loop -> {
                this.loopDepth++;
                this.checkStatement(loop.body());
                this.loopDepth--;
                this.condition(loop.condition());
            }
            case IStmt.For loop -> this.checkFor(loop);
            case IStmt.ForEach loop -> this.checkForEach(loop);
            case IStmt.Switch choice -> this.checkSwitch(choice);
            case IStmt.Break stop -> {
                if (this.loopDepth == 0 && this.switchDepth == 0) {
                    this.report(stop.line(), stop.column(), CannonError.BREAK_OUTSIDE_LOOP);
                }
            }
            case IStmt.Continue next -> {
                if (this.loopDepth == 0) {
                    this.report(next.line(), next.column(), CannonError.CONTINUE_OUTSIDE_LOOP);
                }
            }
            case IStmt.Return give -> this.checkReturn(give);
            case IStmt.LocalDecl local -> this.checkLocal(local);
            case IStmt.ExprStmt expression -> this.check(expression.expression(), null);
            case IStmt.Dispose dispose -> this.checkDispose(dispose);
            case IStmt.Empty ignored -> { }
        }
    }

    private void checkFor(final IStmt.For loop) {
        final Scope saved = this.scope;
        this.scope = new Scope(saved);
        for (final IStmt initializer : loop.initializers()) {
            this.checkStatement(initializer);
        }
        if (loop.condition() != null) {
            this.condition(loop.condition());
        }
        for (final IExpr update : loop.updates()) {
            this.check(update, null);
        }
        this.loopDepth++;
        this.checkStatement(loop.body());
        this.loopDepth--;
        this.scope = saved;
    }

    private void checkForEach(final IStmt.ForEach loop) {
        final ITypeSymbol source = this.check(loop.source(), null);
        final ITypeSymbol element = this.rules.elementOf(source);
        if (element == null) {
            this.report(loop.line(), loop.column(), CannonError.NOT_A_COLLECTION, source.describe());
        }
        final ITypeSymbol found = element == null ? ITypeSymbol.Special.ERROR : element;
        final ITypeSymbol declared = isInferred(loop.type()) ? found : this.declarations.resolve(loop.type());
        if (!this.rules.isAssignable(found, declared)) {
            this.report(loop.line(), loop.column(),
                    CannonError.CANNOT_CONVERT, found.describe(), declared.describe());
        }
        final Scope saved = this.scope;
        this.scope = new Scope(saved);
        final IBinding.Variable variable = new IBinding.Variable(loop.name(), declared, false);
        if (!this.scope.declare(variable)) {
            this.report(loop.line(), loop.column(), CannonError.DUPLICATE_DECLARATION, loop.name());
        }
        this.model.setDeclared(loop, variable);
        this.loopDepth++;
        this.checkStatement(loop.body());
        this.loopDepth--;
        this.scope = saved;
    }

    private void checkSwitch(final IStmt.Switch choice) {
        final ITypeSymbol value = this.check(choice.value(), null);
        final Set<String> seen = new HashSet<>();
        this.switchDepth++;
        for (final IStmt.SwitchSection section : choice.sections()) {
            for (final IExpr label : section.labels()) {
                final ITypeSymbol labelType = this.check(label, value);
                if (!this.rules.isAssignable(labelType, value)) {
                    this.report(label.line(), label.column(),
                            CannonError.CANNOT_CONVERT, labelType.describe(), value.describe());
                } else if (label instanceof IExpr.Literal literal && !seen.add(String.valueOf(literal.value()))) {
                    this.report(label.line(), label.column(), CannonError.DUPLICATE_SWITCH_LABEL);
                }
            }
            final Scope saved = this.scope;
            this.scope = new Scope(saved);
            for (final IStmt statement : section.statements()) {
                this.checkStatement(statement);
            }
            this.scope = saved;
        }
        this.switchDepth--;
    }

    private void checkReturn(final IStmt.Return give) {
        if (give.value() == null) {
            if (this.returnType != ITypeSymbol.Primitive.VOID) {
                this.report(give.line(), give.column(),
                        CannonError.MISSING_RETURN_VALUE, this.returnType.describe());
            }
            return;
        }
        final ITypeSymbol value = this.check(give.value(), this.returnType);
        if (this.returnType == ITypeSymbol.Primitive.VOID) {
            this.report(give.line(), give.column(), CannonError.UNEXPECTED_RETURN_VALUE);
            return;
        }
        this.expect(value, this.returnType, give.value());
    }

    private void checkLocal(final IStmt.LocalDecl local) {
        final ITypeSymbol declared = isInferred(local.type()) ? this.inferred(local) : this.written(local);
        final IBinding.Variable variable = new IBinding.Variable(local.name(), declared, false);
        if (!this.scope.declare(variable)) {
            this.report(local.line(), local.column(),
                    CannonError.DUPLICATE_DECLARATION, local.name());
        }
        this.model.setDeclared(local, variable);
    }

    // "var" takes the type of what it is given, which means it has to be given something, and
    // something with a type of its own: null and a call that gives nothing back have neither.
    private ITypeSymbol inferred(final IStmt.LocalDecl local) {
        if (local.initializer() == null) {
            this.report(local.line(), local.column(),
                    CannonError.CANNOT_CONVERT, "nothing", INFERRED);
            return ITypeSymbol.Special.ERROR;
        }
        final ITypeSymbol found = this.check(local.initializer(), null);
        if (found == ITypeSymbol.Special.NULL || found == ITypeSymbol.Primitive.VOID) {
            this.report(local.line(), local.column(),
                    CannonError.CANNOT_CONVERT, found.describe(), INFERRED);
            return ITypeSymbol.Special.ERROR;
        }
        return found;
    }

    private ITypeSymbol written(final IStmt.LocalDecl local) {
        final ITypeSymbol declared = this.declarations.resolve(local.type());
        if (local.initializer() != null) {
            this.expect(this.check(local.initializer(), declared), declared, local.initializer());
        }
        return declared;
    }

    private void checkDispose(final IStmt.Dispose dispose) {
        final ITypeSymbol target = this.check(dispose.target(), null);
        if (!this.rules.isError(target) && !this.rules.isReference(target)) {
            this.report(dispose.line(), dispose.column(),
                    CannonError.CANNOT_DISPOSE, target.describe());
        }
    }

    private void condition(final IExpr expression) {
        final ITypeSymbol type = this.check(expression, ITypeSymbol.Primitive.BOOL);
        if (!this.rules.isError(type) && type != ITypeSymbol.Primitive.BOOL) {
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
    private static boolean alwaysReturns(final IStmt statement) {
        return switch (statement) {
            case IStmt.Return ignored -> true;
            case IStmt.Block block -> block.statements().stream().anyMatch(BodyChecker::alwaysReturns);
            case IStmt.If branch -> branch.otherwise() != null
                    && alwaysReturns(branch.then()) && alwaysReturns(branch.otherwise());
            case IStmt.While loop -> isAlwaysTrue(loop.condition());
            case IStmt.DoWhile loop -> alwaysReturns(loop.body()) || isAlwaysTrue(loop.condition());
            case IStmt.For loop -> loop.condition() == null || isAlwaysTrue(loop.condition());
            case IStmt.Switch choice -> choice.sections().stream().anyMatch(IStmt.SwitchSection::fallback)
                    && choice.sections().stream().allMatch(section ->
                            section.statements().stream().anyMatch(BodyChecker::alwaysReturns));
            default -> false;
        };
    }

    private static boolean isAlwaysTrue(final IExpr condition) {
        return condition instanceof IExpr.Literal literal && Boolean.TRUE.equals(literal.value());
    }

    // ---------------------------------------------------------------- expressions

    private ITypeSymbol check(final IExpr expression, final ITypeSymbol expected) {
        if (expression == null) {
            return ITypeSymbol.Special.ERROR;
        }
        final ITypeSymbol type = switch (expression) {
            case IExpr.Literal literal -> this.literalType(literal);
            case IExpr.Name name -> this.nameType(name, expected);
            case IExpr.This self -> this.thisType(self);
            case IExpr.Base base -> this.baseType(base);
            case IExpr.Unary unary -> this.unaryType(unary);
            case IExpr.Binary binary -> this.binaryType(binary);
            case IExpr.Assign assign -> this.assignType(assign);
            case IExpr.Conditional conditional -> this.conditionalType(conditional, expected);
            case IExpr.Call call -> this.callType(call);
            case IExpr.Member member -> this.memberType(member, expected);
            case IExpr.Index index -> this.indexType(index);
            case IExpr.New created -> this.newType(created);
            case IExpr.NewArray created -> this.newArrayType(created);
            case IExpr.Cast cast -> this.castType(cast);
            case IExpr.TypeTest test -> this.typeTestType(test);
            case IExpr.Lambda lambda -> this.lambdaType(lambda, expected);
            case IExpr.OutArgument outward -> this.outArgumentType(outward, expected);
        };
        this.model.setType(expression, type);
        return type;
    }

    private ITypeSymbol literalType(final IExpr.Literal literal) {
        return switch (literal.kind()) {
            case INT_LITERAL -> ITypeSymbol.Primitive.INT;
            case LONG_LITERAL -> ITypeSymbol.Primitive.LONG;
            case FLOAT_LITERAL -> ITypeSymbol.Primitive.FLOAT;
            case DOUBLE_LITERAL -> ITypeSymbol.Primitive.DOUBLE;
            case CHAR_LITERAL -> ITypeSymbol.Primitive.CHAR;
            case STRING_LITERAL -> this.builtIns.stringType();
            case TRUE, FALSE -> ITypeSymbol.Primitive.BOOL;
            default -> ITypeSymbol.Special.NULL;
        };
    }

    private ITypeSymbol nameType(final IExpr.Name name, final ITypeSymbol expected) {
        final IBinding.Variable variable = this.scope.lookup(name.identifier());
        if (variable != null) {
            this.model.setBinding(name, variable);
            return variable.type();
        }
        if (this.currentType != null) {
            final List<IMemberSymbol> members = lookup(this.currentType, name.identifier());
            if (!members.isEmpty()) {
                return this.bindMember(name, this.currentType, members, expected, Access.IMPLICIT);
            }
        }
        final ITypeSymbol type = this.namedType(name.identifier());
        if (type != null) {
            this.model.setBinding(name, new IBinding.TypeName(type));
            return type;
        }
        this.report(name.line(), name.column(), CannonError.UNKNOWN_NAME, name.identifier());
        return ITypeSymbol.Special.ERROR;
    }

    private ITypeSymbol namedType(final String name) {
        final NamedType declared = this.model.declaredType(name);
        return declared != null ? declared : this.builtIns.type(name, 0);
    }

    private ITypeSymbol thisType(final IExpr.This self) {
        if (this.staticContext) {
            this.report(self.line(), self.column(), CannonError.THIS_IN_STATIC, "this");
            return ITypeSymbol.Special.ERROR;
        }
        return this.currentType == null ? ITypeSymbol.Special.ERROR : this.currentType;
    }

    private ITypeSymbol baseType(final IExpr.Base base) {
        if (this.staticContext) {
            this.report(base.line(), base.column(), CannonError.THIS_IN_STATIC, "base");
            return ITypeSymbol.Special.ERROR;
        }
        if (this.currentType == null || this.currentType.base() == null) {
            this.report(base.line(), base.column(), CannonError.NO_BASE_CLASS,
                    this.currentType == null ? "?" : this.currentType.name());
            return ITypeSymbol.Special.ERROR;
        }
        return this.currentType.base();
    }

    private ITypeSymbol unaryType(final IExpr.Unary unary) {
        final ITypeSymbol operand = this.check(unary.operand(), null);
        final ITypeSymbol result = this.rules.unaryResult(unary.operator(), operand);
        if (result == null) {
            this.report(unary.line(), unary.column(), CannonError.OPERATOR_ON_TYPE,
                    unary.operator().text(), operand.describe());
            return ITypeSymbol.Special.ERROR;
        }
        return result;
    }

    private ITypeSymbol binaryType(final IExpr.Binary binary) {
        final ITypeSymbol left = this.check(binary.left(), null);
        final ITypeSymbol right = this.check(binary.right(), null);
        final ITypeSymbol result = this.rules.binaryResult(binary.operator(), left, right);
        if (result == null) {
            this.report(binary.line(), binary.column(), CannonError.OPERATOR_ON_TYPES,
                    binary.operator().text(), left.describe(), right.describe());
            return ITypeSymbol.Special.ERROR;
        }
        return result;
    }

    private ITypeSymbol conditionalType(final IExpr.Conditional conditional, final ITypeSymbol expected) {
        this.condition(conditional.condition());
        final ITypeSymbol whenTrue = this.check(conditional.whenTrue(), expected);
        final ITypeSymbol whenFalse = this.check(conditional.whenFalse(), expected);
        if (this.rules.isAssignable(whenFalse, whenTrue)) {
            return whenTrue;
        }
        if (this.rules.isAssignable(whenTrue, whenFalse)) {
            return whenFalse;
        }
        this.report(conditional.line(), conditional.column(),
                CannonError.CANNOT_CONVERT, whenFalse.describe(), whenTrue.describe());
        return ITypeSymbol.Special.ERROR;
    }

    private ITypeSymbol indexType(final IExpr.Index index) {
        final ITypeSymbol target = this.check(index.target(), null);
        final ITypeSymbol key = this.check(index.index(), null);
        final ITypeSymbol result = this.rules.indexResult(target, key);
        if (result == null) {
            this.report(index.line(), index.column(), CannonError.CANNOT_INDEX,
                    target.describe(), key.describe());
            return ITypeSymbol.Special.ERROR;
        }
        return result;
    }

    private ITypeSymbol newType(final IExpr.New created) {
        final ITypeSymbol type = this.declarations.resolve(created.type());
        final NamedType named = this.rules.named(type);
        if (named == null || named.kind() != NamedType.Kind.CLASS) {
            if (!this.rules.isError(type)) {
                this.report(created.line(), created.column(),
                        CannonError.CANNOT_CREATE, type.describe());
            }
            this.checkArguments(created.arguments());
            return this.rules.isError(type) ? ITypeSymbol.Special.ERROR : type;
        }
        this.callConstructor(named, type, created.arguments(), created);
        return type;
    }

    // A class with no constructor of its own can be made with no arguments and nothing else.
    private void callConstructor(final NamedType named, final ITypeSymbol type, final List<IExpr> arguments,
                                 final INode at) {
        final List<IMemberSymbol.MethodSymbol> candidates = new ArrayList<>();
        for (final IMemberSymbol member : named.members()) {
            if (member instanceof IMemberSymbol.ConstructorSymbol constructor) {
                candidates.add(new IMemberSymbol.MethodSymbol(named, named.name(), type,
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

    private ITypeSymbol newArrayType(final IExpr.NewArray created) {
        final ITypeSymbol element = this.declarations.resolve(created.elementType());
        this.expect(this.check(created.length(), ITypeSymbol.Primitive.INT),
                ITypeSymbol.Primitive.INT, created.length());
        return new ITypeSymbol.ArrayType(element);
    }

    private ITypeSymbol castType(final IExpr.Cast cast) {
        final ITypeSymbol target = this.declarations.resolve(cast.type());
        final ITypeSymbol value = this.check(cast.value(), null);
        if (!this.rules.isAssignable(value, target) && !this.rules.isAssignable(target, value)) {
            this.report(cast.line(), cast.column(),
                    CannonError.CANNOT_CONVERT, value.describe(), target.describe());
        }
        return target;
    }

    private ITypeSymbol typeTestType(final IExpr.TypeTest test) {
        final ITypeSymbol value = this.check(test.value(), null);
        final ITypeSymbol target = this.declarations.resolve(test.type());
        final String written = test.conversion() ? "as" : "is";
        if (!this.rules.isError(value) && !this.rules.isReference(value)) {
            this.report(test.line(), test.column(),
                    CannonError.OPERATOR_ON_TYPE, written, value.describe());
        }
        if (test.conversion() && !this.rules.isError(target) && !this.rules.isReference(target)) {
            this.report(test.line(), test.column(),
                    CannonError.OPERATOR_ON_TYPE, written, target.describe());
            return ITypeSymbol.Special.ERROR;
        }
        return test.conversion() ? target : ITypeSymbol.Primitive.BOOL;
    }

    // ---------------------------------------------------------------- members

    private ITypeSymbol memberType(final IExpr.Member member, final ITypeSymbol expected) {
        final ITypeSymbol target = this.check(member.target(), null);
        if (this.rules.isError(target)) {
            return ITypeSymbol.Special.ERROR;
        }
        final Access access = this.model.bindingOf(member.target()) instanceof IBinding.TypeName
                ? Access.TYPE : Access.INSTANCE;
        final List<IMemberSymbol> found = this.membersOf(target, member.name(), member);
        return found.isEmpty()
                ? ITypeSymbol.Special.ERROR
                : this.bindMember(member, target, found, expected, access);
    }

    private List<IMemberSymbol> membersOf(final ITypeSymbol target, final String name, final INode at) {
        final NamedType named = this.rules.named(target);
        final List<IMemberSymbol> found = named == null ? List.of() : lookup(named, name);
        if (found.isEmpty()) {
            this.report(at.line(), at.column(),
                    CannonError.NO_SUCH_MEMBER, target.describe(), name);
        }
        return found;
    }

    // A name that turned out to be a member: a value if it holds one, and a method only where a
    // delegate of the same shape is wanted, which is how a handler is handed over without brackets.
    private ITypeSymbol bindMember(final IExpr expression, final ITypeSymbol receiver,
                                  final List<IMemberSymbol> members, final ITypeSymbol expected,
                                  final Access access) {
        final IMemberSymbol first = members.getFirst();
        if (first instanceof IMemberSymbol.MethodSymbol) {
            return this.methodGroupType(expression, receiver, members, expected, access);
        }
        if (!this.checkAccess(expression, first, access)) {
            return ITypeSymbol.Special.ERROR;
        }
        final List<ITypeSymbol> arguments = this.rules.arguments(receiver);
        final ITypeSymbol type = switch (first) {
            case IMemberSymbol.FieldSymbol field -> this.rules.substitute(field.type(), arguments);
            case IMemberSymbol.PropertySymbol property -> this.rules.substitute(property.type(), arguments);
            case IMemberSymbol.EventSymbol event -> event.delegateType();
            default -> ITypeSymbol.Special.ERROR;
        };
        this.model.setBinding(expression, new IBinding.Member(first, type));
        return type;
    }

    private ITypeSymbol methodGroupType(final IExpr expression, final ITypeSymbol receiver,
                                       final List<IMemberSymbol> members, final ITypeSymbol expected,
                                       final Access access) {
        final NamedType wanted = this.rules.named(expected);
        if (wanted == null || wanted.kind() != NamedType.Kind.DELEGATE || wanted.invoke() == null) {
            this.report(expression.line(), expression.column(),
                    CannonError.METHOD_AS_VALUE, members.getFirst().name());
            return ITypeSymbol.Special.ERROR;
        }
        final List<ITypeSymbol> wantedArguments = this.rules.arguments(expected);
        final List<ITypeSymbol> ownArguments = this.rules.arguments(receiver);
        for (final IMemberSymbol member : members) {
            final IMemberSymbol.MethodSymbol candidate = (IMemberSymbol.MethodSymbol) member;
            if (this.matchesShape(candidate, wanted.invoke(), wantedArguments, ownArguments)
                    && this.checkAccess(expression, candidate, access)) {
                this.model.setBinding(expression, new IBinding.Member(candidate, expected));
                this.model.setCall(expression, candidate);
                return expected;
            }
        }
        this.report(expression.line(), expression.column(),
                CannonError.LAMBDA_SHAPE, expected.describe());
        return ITypeSymbol.Special.ERROR;
    }

    private boolean matchesShape(final IMemberSymbol.MethodSymbol candidate, final IMemberSymbol.MethodSymbol shape,
                                 final List<ITypeSymbol> wantedArguments, final List<ITypeSymbol> ownArguments) {
        if (candidate.parameters().size() != shape.parameters().size()) {
            return false;
        }
        for (int i = 0; i < shape.parameters().size(); i++) {
            if (shape.parameters().get(i).outward() != candidate.parameters().get(i).outward()) {
                return false;
            }
            final ITypeSymbol wanted = this.rules.substitute(shape.parameters().get(i).type(), wantedArguments);
            final ITypeSymbol given = this.rules.substitute(candidate.parameters().get(i).type(), ownArguments);
            if (!wanted.equals(given)) {
                return false;
            }
        }
        final ITypeSymbol wantedReturn = this.rules.substitute(shape.returnType(), wantedArguments);
        return this.rules.isAssignable(this.rules.substitute(candidate.returnType(), ownArguments), wantedReturn);
    }

    private boolean checkAccess(final IExpr expression, final IMemberSymbol member, final Access access) {
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

    private ITypeSymbol callType(final IExpr.Call call) {
        if (call.callee() instanceof IExpr.Name name && this.scope.lookup(name.identifier()) == null
                && this.currentType != null) {
            final List<IMemberSymbol> members = lookup(this.currentType, name.identifier());
            if (!members.isEmpty() && members.getFirst() instanceof IMemberSymbol.MethodSymbol) {
                return this.callMembers(call, name, this.currentType, members,
                        name.identifier(), Access.IMPLICIT);
            }
        }
        if (call.callee() instanceof IExpr.Member member) {
            return this.callThroughMember(call, member);
        }
        return this.invoke(call, this.check(call.callee(), null));
    }

    private ITypeSymbol callThroughMember(final IExpr.Call call, final IExpr.Member member) {
        final ITypeSymbol target = this.check(member.target(), null);
        if (this.rules.isError(target)) {
            this.checkArguments(call.arguments());
            return ITypeSymbol.Special.ERROR;
        }
        final Access access = this.model.bindingOf(member.target()) instanceof IBinding.TypeName
                ? Access.TYPE : Access.INSTANCE;
        final List<IMemberSymbol> members = this.membersOf(target, member.name(), member);
        if (members.isEmpty()) {
            this.checkArguments(call.arguments());
            return ITypeSymbol.Special.ERROR;
        }
        if (members.getFirst() instanceof IMemberSymbol.MethodSymbol) {
            return this.callMembers(call, member, target, members, member.name(), access);
        }
        final ITypeSymbol held = this.bindMember(member, target, members, null, access);
        this.model.setType(member, held);
        return this.invoke(call, held);
    }

    // Calling something that holds a delegate: a local, a parameter, a field, a property or an event.
    private ITypeSymbol invoke(final IExpr.Call call, final ITypeSymbol callee) {
        if (this.rules.isError(callee)) {
            this.checkArguments(call.arguments());
            return ITypeSymbol.Special.ERROR;
        }
        final NamedType named = this.rules.named(callee);
        if (named == null || named.kind() != NamedType.Kind.DELEGATE || named.invoke() == null) {
            this.report(call.line(), call.column(), CannonError.CANNOT_CALL, callee.describe());
            this.checkArguments(call.arguments());
            return ITypeSymbol.Special.ERROR;
        }
        this.checkEventRaise(call);
        final List<ITypeSymbol> arguments = this.rules.arguments(callee);
        final IMemberSymbol.MethodSymbol invoke = this.fill(named.invoke(), arguments);
        this.callWith(List.of(invoke), call.arguments(), named.name(), call);
        return invoke.returnType();
    }

    // Raising an event is only allowed where it was declared, as in the language this one borrows
    // from: everywhere else an event is something to subscribe to, not something to fire.
    private void checkEventRaise(final IExpr.Call call) {
        if (this.model.bindingOf(call.callee()) instanceof IBinding.Member member
                && member.member() instanceof IMemberSymbol.EventSymbol event
                && event.owner() != this.currentType) {
            this.report(call.line(), call.column(), CannonError.EVENT_OUTSIDE_ITS_TYPE);
        }
    }

    private ITypeSymbol callMembers(final IExpr.Call call, final IExpr callee, final ITypeSymbol receiver,
                                   final List<IMemberSymbol> members, final String name, final Access access) {
        if (!this.checkAccess(callee, members.getFirst(), access)) {
            this.checkArguments(call.arguments());
            return ITypeSymbol.Special.ERROR;
        }
        final List<ITypeSymbol> arguments = this.rules.arguments(receiver);
        final List<IMemberSymbol.MethodSymbol> candidates = new ArrayList<>();
        for (final IMemberSymbol member : members) {
            if (member instanceof IMemberSymbol.MethodSymbol method) {
                candidates.add(this.fill(method, arguments));
            }
        }
        final IMemberSymbol.MethodSymbol chosen = this.callWith(candidates, call.arguments(), name, call);
        if (chosen != null) {
            this.model.setBinding(callee, new IBinding.Member(chosen, chosen.returnType()));
        }
        return chosen == null ? ITypeSymbol.Special.ERROR : chosen.returnType();
    }

    // A method read off a filled-in collection has its stand-in types replaced by what that
    // collection holds, so List<string>.Get gives back a string and not a T.
    private IMemberSymbol.MethodSymbol fill(final IMemberSymbol.MethodSymbol method,
                                           final List<ITypeSymbol> arguments) {
        if (arguments.isEmpty()) {
            return method;
        }
        final List<IMemberSymbol.ParameterSymbol> parameters = new ArrayList<>();
        for (final IMemberSymbol.ParameterSymbol parameter : method.parameters()) {
            parameters.add(new IMemberSymbol.ParameterSymbol(parameter.name(),
                    this.rules.substitute(parameter.type(), arguments), parameter.outward()));
        }
        return new IMemberSymbol.MethodSymbol(method.owner(), method.name(),
                this.rules.substitute(method.returnType(), arguments), parameters, method.modifiers());
    }

    /**
     * Picks the version that fits and checks the arguments against it. A lambda has no type until it
     * knows what it is being handed to, so it is left out of the choosing and checked afterwards,
     * against the version that won.
     */
    private IMemberSymbol.MethodSymbol callWith(final List<IMemberSymbol.MethodSymbol> candidates,
                                               final List<IExpr> arguments, final String name, final INode at) {
        final List<ITypeSymbol> given = new ArrayList<>();
        for (final IExpr argument : arguments) {
            final boolean waits = argument instanceof IExpr.Lambda
                    || waitsForItsParameter(argument) || this.isMethodGroup(argument);
            given.add(waits ? null : this.check(argument, null));
        }
        final List<IMemberSymbol.MethodSymbol> fitting = new ArrayList<>();
        int best = -1;
        for (final IMemberSymbol.MethodSymbol candidate : candidates) {
            final int score = this.score(candidate, given, arguments);
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
        final IMemberSymbol.MethodSymbol chosen = fitting.getFirst();
        this.checkAgainst(chosen, arguments, given);
        if (at instanceof IExpr expression) {
            this.model.setCall(expression, chosen);
        }
        return chosen;
    }

    // When only one version could have been meant, saying which argument is wrong beats saying that
    // none of them fit: with a single version there is nothing to choose between.
    private IMemberSymbol.MethodSymbol reportNoFit(final List<IMemberSymbol.MethodSymbol> candidates,
                                                  final List<IExpr> arguments, final List<ITypeSymbol> given,
                                                  final String name, final INode at) {
        final List<IMemberSymbol.MethodSymbol> sameCount = new ArrayList<>();
        for (final IMemberSymbol.MethodSymbol candidate : candidates) {
            if (candidate.parameters().size() == given.size()) {
                sameCount.add(candidate);
            }
        }
        if (sameCount.size() == 1) {
            final IMemberSymbol.MethodSymbol only = sameCount.getFirst();
            this.checkAgainst(only, arguments, given);
            if (at instanceof IExpr expression) {
                this.model.setCall(expression, only);
            }
            return only;
        }
        this.report(at.line(), at.column(), CannonError.NO_MATCHING_OVERLOAD, name);
        for (int i = 0; i < arguments.size(); i++) {
            if (given.get(i) == null) {
                this.check(arguments.get(i), ITypeSymbol.Special.ERROR);
            }
        }
        return null;
    }

    private void checkAgainst(final IMemberSymbol.MethodSymbol chosen, final List<IExpr> arguments,
                              final List<ITypeSymbol> given) {
        for (int i = 0; i < arguments.size(); i++) {
            final IMemberSymbol.ParameterSymbol parameter = chosen.parameters().get(i);
            final IExpr argument = arguments.get(i);
            final boolean outward = argument instanceof IExpr.OutArgument;
            if (parameter.outward() != outward) {
                this.report(argument.line(), argument.column(), parameter.outward()
                        ? CannonError.OUT_ARGUMENT_EXPECTED : CannonError.OUT_ARGUMENT_UNEXPECTED,
                        parameter.name());
                continue;
            }
            if (given.get(i) == null) {
                this.check(argument, parameter.type());
            } else if (outward) {
                if (!given.get(i).equals(parameter.type())) {
                    this.report(argument.line(), argument.column(),
                            CannonError.OUT_TYPE_MUST_MATCH, parameter.type().describe());
                }
            } else {
                this.expect(given.get(i), parameter.type(), argument);
            }
        }
    }

    // How well a version fits: an exact type counts double, a conversion counts once, and anything
    // that does not fit at all rules the version out. An outward argument fits only an outward
    // parameter, and only exactly, because the method writes straight into the place it is given.
    private int score(final IMemberSymbol.MethodSymbol candidate, final List<ITypeSymbol> given,
                      final List<IExpr> arguments) {
        if (candidate.parameters().size() != given.size()) {
            return -1;
        }
        int total = 0;
        for (int i = 0; i < given.size(); i++) {
            final IMemberSymbol.ParameterSymbol parameter = candidate.parameters().get(i);
            final boolean outward = arguments.get(i) instanceof IExpr.OutArgument;
            if (outward != parameter.outward()) {
                return -1;
            }
            final ITypeSymbol wanted = parameter.type();
            final ITypeSymbol argument = given.get(i);
            if (argument == null) {
                if (outward) {
                    total += 2;
                    continue;
                }
                final NamedType named = this.rules.named(wanted);
                if (named == null || named.kind() != NamedType.Kind.DELEGATE) {
                    return -1;
                }
                total += 1;
            } else if (argument.equals(wanted)) {
                total += 2;
            } else if (!outward && this.rules.isAssignable(argument, wanted)) {
                total += 1;
            } else {
                return -1;
            }
        }
        return total;
    }

    private void checkArguments(final List<IExpr> arguments) {
        for (final IExpr argument : arguments) {
            this.check(argument, null);
        }
    }

    // ---------------------------------------------------------------- assignment and lambdas

    private ITypeSymbol assignType(final IExpr.Assign assign) {
        final ITypeSymbol target = this.check(assign.target(), null);
        final IBinding binding = this.model.bindingOf(assign.target());
        if (binding instanceof IBinding.Member member
                && member.member() instanceof IMemberSymbol.EventSymbol event) {
            return this.subscribe(assign, event);
        }
        final ITypeSymbol value = this.check(assign.value(), target);
        if (this.rules.isError(target) || !this.writable(assign, binding)) {
            return this.rules.isError(target) ? ITypeSymbol.Special.ERROR : target;
        }
        if (assign.operator() == Operator.ASSIGN) {
            this.expect(value, target, assign.value());
            return target;
        }
        final ITypeSymbol combined = this.rules.binaryResult(assign.operator(), target, value);
        if (combined == null || !this.rules.isAssignable(combined, target)) {
            this.report(assign.line(), assign.column(), CannonError.OPERATOR_ON_TYPES,
                    assign.operator().text() + "=", target.describe(), value.describe());
        }
        return target;
    }

    private ITypeSymbol subscribe(final IExpr.Assign assign, final IMemberSymbol.EventSymbol event) {
        if (assign.operator() != Operator.ADD && assign.operator() != Operator.SUBTRACT) {
            this.report(assign.line(), assign.column(), CannonError.OPERATOR_ON_TYPE,
                    assign.operator().text() + "=", event.delegateType().name());
            this.check(assign.value(), event.delegateType());
            return ITypeSymbol.Special.ERROR;
        }
        this.expect(this.check(assign.value(), event.delegateType()), event.delegateType(), assign.value());
        return event.delegateType();
    }

    private boolean writable(final IExpr.Assign assign, final IBinding binding) {
        if (!(binding instanceof IBinding.Member member)) {
            return true;
        }
        if (member.member() instanceof IMemberSymbol.FieldSymbol field && field.isReadOnly()
                && !(this.inConstructor && field.owner() == this.currentType)) {
            this.report(assign.line(), assign.column(),
                    CannonError.CANNOT_ASSIGN_READONLY, field.name());
            return false;
        }
        if (member.member() instanceof IMemberSymbol.PropertySymbol property && !property.writable()) {
            this.report(assign.line(), assign.column(),
                    CannonError.CANNOT_ASSIGN_READONLY, property.name());
            return false;
        }
        return true;
    }

    private ITypeSymbol lambdaType(final IExpr.Lambda lambda, final ITypeSymbol expected) {
        final NamedType wanted = this.rules.named(expected);
        if (wanted == null || wanted.kind() != NamedType.Kind.DELEGATE || wanted.invoke() == null) {
            if (!this.rules.isError(expected)) {
                this.report(lambda.line(), lambda.column(), CannonError.LAMBDA_SHAPE,
                        expected == null ? "nothing" : expected.describe());
            }
            return ITypeSymbol.Special.ERROR;
        }
        final IMemberSymbol.MethodSymbol shape = this.fill(wanted.invoke(), this.rules.arguments(expected));
        if (shape.parameters().size() != lambda.parameters().size()) {
            this.report(lambda.line(), lambda.column(),
                    CannonError.LAMBDA_SHAPE, expected.describe());
            return ITypeSymbol.Special.ERROR;
        }
        final Scope saved = this.scope;
        final ITypeSymbol savedReturn = this.returnType;
        this.scope = new Scope(saved);
        this.declareLambdaParameters(lambda, shape);
        this.returnType = shape.returnType();
        if (lambda.block() != null) {
            this.checkBlock(lambda.block(), false);
        } else {
            final ITypeSymbol body = this.check(lambda.body(), shape.returnType());
            if (shape.returnType() != ITypeSymbol.Primitive.VOID) {
                this.expect(body, shape.returnType(), lambda.body());
            }
        }
        this.checkOutParameters(lambda.parameters(), lambda.block(), lambda);
        this.scope = saved;
        this.returnType = savedReturn;
        return expected;
    }

    private void declareLambdaParameters(final IExpr.Lambda lambda, final IMemberSymbol.MethodSymbol shape) {
        for (int i = 0; i < lambda.parameters().size(); i++) {
            final IDecl.Parameter parameter = lambda.parameters().get(i);
            if (parameter.outward() != shape.parameters().get(i).outward()) {
                this.report(parameter.line(), parameter.column(), shape.parameters().get(i).outward()
                        ? CannonError.OUT_ARGUMENT_EXPECTED : CannonError.OUT_ARGUMENT_UNEXPECTED,
                        shape.parameters().get(i).name());
            }
            final ITypeSymbol fromShape = shape.parameters().get(i).type();
            ITypeSymbol type = fromShape;
            if (parameter.type() != null) {
                type = this.declarations.resolve(parameter.type());
                if (!type.equals(fromShape)) {
                    this.report(parameter.line(), parameter.column(),
                            CannonError.CANNOT_CONVERT, fromShape.describe(), type.describe());
                }
            }
            final IBinding.Variable variable = new IBinding.Variable(parameter.name(), type, true);
            if (!this.scope.declare(variable)) {
                this.report(parameter.line(), parameter.column(),
                        CannonError.DUPLICATE_DECLARATION, parameter.name());
            }
            this.model.setDeclared(parameter, variable);
        }
    }

    // ---------------------------------------------------------------- outward arguments

    // The place a method is being asked to write into: a local it declares here, a local that already
    // exists, or a field. Written as var, the local takes whatever the method fills in, which is only
    // known once the version of the method has been chosen.
    private ITypeSymbol outArgumentType(final IExpr.OutArgument argument, final ITypeSymbol expected) {
        if (argument.type() != null) {
            final ITypeSymbol type = isInferred(argument.type())
                    ? expected : this.declarations.resolve(argument.type());
            if (type == null || this.rules.isError(type)) {
                return ITypeSymbol.Special.ERROR;
            }
            final IBinding.Variable variable = new IBinding.Variable(argument.name(), type, false);
            if (!this.scope.declare(variable)) {
                this.report(argument.line(), argument.column(),
                        CannonError.DUPLICATE_DECLARATION, argument.name());
            }
            this.model.setBinding(argument, variable);
            this.model.setDeclared(argument, variable);
            return type;
        }
        final IBinding.Variable variable = this.scope.lookup(argument.name());
        if (variable != null) {
            this.model.setBinding(argument, variable);
            return variable.type();
        }
        final List<IMemberSymbol> members = this.currentType == null
                ? List.of() : lookup(this.currentType, argument.name());
        if (!members.isEmpty()) {
            if (members.getFirst() instanceof IMemberSymbol.FieldSymbol field && !field.isReadOnly()) {
                this.model.setBinding(argument, new IBinding.Member(field, field.type()));
                return field.type();
            }
            this.report(argument.line(), argument.column(), CannonError.OUT_NOT_A_PLACE, argument.name());
            return ITypeSymbol.Special.ERROR;
        }
        this.report(argument.line(), argument.column(), CannonError.UNKNOWN_NAME, argument.name());
        return ITypeSymbol.Special.ERROR;
    }

    private static boolean waitsForItsParameter(final IExpr argument) {
        return argument instanceof IExpr.OutArgument outward && isInferred(outward.type());
    }

    // An outward parameter has to be given a value on every way out of the method, because the caller
    // is promised one. This walks the body carrying whether it has been given yet, and says so at the
    // first way out that has not.
    private void checkOutParameters(final List<IDecl.Parameter> parameters, final IStmt.Block body,
                                    final INode at) {
        for (final IDecl.Parameter parameter : parameters) {
            if (!parameter.outward()) {
                continue;
            }
            if (body == null || !this.flow(body, parameter.name(), false)) {
                this.report(at.line(), at.column(), CannonError.OUT_NOT_ASSIGNED, parameter.name());
            }
        }
    }

    private boolean flow(final IStmt statement, final String name, final boolean assigned) {
        return switch (statement) {
            case IStmt.Block block -> {
                boolean now = assigned;
                for (final IStmt inner : block.statements()) {
                    now = this.flow(inner, name, now);
                }
                yield now;
            }
            case IStmt.ExprStmt expression -> assigned || writesTo(expression.expression(), name);
            case IStmt.LocalDecl local -> assigned || writesTo(local.initializer(), name);
            case IStmt.Return give -> {
                if (!assigned && !writesTo(give.value(), name)) {
                    this.report(give.line(), give.column(), CannonError.OUT_NOT_ASSIGNED, name);
                }
                yield true;
            }
            case IStmt.If branch -> {
                final boolean then = this.flow(branch.then(), name, assigned);
                final boolean otherwise = branch.otherwise() == null
                        ? assigned : this.flow(branch.otherwise(), name, assigned);
                yield then && otherwise;
            }
            case IStmt.DoWhile loop -> this.flow(loop.body(), name, assigned);
            case IStmt.While loop -> this.aside(loop.body(), name, assigned);
            case IStmt.For loop -> this.aside(loop.body(), name, assigned);
            case IStmt.ForEach loop -> this.aside(loop.body(), name, assigned);
            case IStmt.Switch choice -> {
                for (final IStmt.SwitchSection section : choice.sections()) {
                    boolean now = assigned;
                    for (final IStmt inner : section.statements()) {
                        now = this.flow(inner, name, now);
                    }
                }
                yield assigned;
            }
            default -> assigned;
        };
    }

    // A body that may not run at all cannot be counted on to have given the value, but a way out
    // inside it still has to be checked.
    private boolean aside(final IStmt body, final String name, final boolean assigned) {
        this.flow(body, name, assigned);
        return assigned;
    }

    // Whether evaluating this expression gives the name a value: an assignment to it, or handing it
    // to a method as the place to fill in. A lambda's body does not count, because it runs later.
    private static boolean writesTo(final IExpr expression, final String name) {
        return switch (expression) {
            case null -> false;
            case IExpr.Assign assign -> (assign.operator() == Operator.ASSIGN
                    && assign.target() instanceof IExpr.Name target && target.identifier().equals(name))
                    || writesTo(assign.target(), name) || writesTo(assign.value(), name);
            case IExpr.OutArgument outward -> outward.name().equals(name);
            case IExpr.Binary binary -> writesTo(binary.left(), name) || writesTo(binary.right(), name);
            case IExpr.Unary unary -> writesTo(unary.operand(), name);
            case IExpr.Conditional conditional -> writesTo(conditional.condition(), name);
            case IExpr.Call call -> writesTo(call.callee(), name)
                    || call.arguments().stream().anyMatch(argument -> writesTo(argument, name));
            case IExpr.Member member -> writesTo(member.target(), name);
            case IExpr.Index index -> writesTo(index.target(), name) || writesTo(index.index(), name);
            case IExpr.New created -> created.arguments().stream()
                    .anyMatch(argument -> writesTo(argument, name));
            case IExpr.NewArray created -> writesTo(created.length(), name);
            case IExpr.Cast cast -> writesTo(cast.value(), name);
            case IExpr.TypeTest test -> writesTo(test.value(), name);
            default -> false;
        };
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
    private boolean isMethodGroup(final IExpr expression) {
        if (expression instanceof IExpr.Name name) {
            if (this.scope.lookup(name.identifier()) != null || this.currentType == null) {
                return false;
            }
            final List<IMemberSymbol> found = lookup(this.currentType, name.identifier());
            return !found.isEmpty() && found.getFirst() instanceof IMemberSymbol.MethodSymbol;
        }
        if (expression instanceof IExpr.Member member) {
            this.quiet++;
            final ITypeSymbol target = this.check(member.target(), null);
            this.quiet--;
            final NamedType named = this.rules.named(target);
            if (named == null) {
                return false;
            }
            final List<IMemberSymbol> found = lookup(named, member.name());
            return !found.isEmpty() && found.getFirst() instanceof IMemberSymbol.MethodSymbol;
        }
        return false;
    }

    private void expect(final ITypeSymbol given, final ITypeSymbol wanted, final INode at) {
        if (!this.rules.isAssignable(given, wanted)) {
            this.report(at.line(), at.column(),
                    CannonError.CANNOT_CONVERT, given.describe(), wanted.describe());
        }
    }

    // The nearest declaration wins. A class that writes a method its interface also declares would
    // otherwise offer the same method twice and every call of it would look ambiguous.
    private static List<IMemberSymbol> lookup(final NamedType type, final String name) {
        final List<IMemberSymbol> found = new ArrayList<>();
        for (final IMemberSymbol member : type.allMembers()) {
            if (!member.name().equals(name) || member instanceof IMemberSymbol.ConstructorSymbol) {
                continue;
            }
            if (found.isEmpty()) {
                found.add(member);
                continue;
            }
            if (!(found.getFirst() instanceof IMemberSymbol.MethodSymbol)
                    || !(member instanceof IMemberSymbol.MethodSymbol method)) {
                continue;
            }
            boolean alreadyThere = false;
            for (final IMemberSymbol seen : found) {
                alreadyThere = alreadyThere
                        || sameSignature((IMemberSymbol.MethodSymbol) seen, method);
            }
            if (!alreadyThere) {
                found.add(member);
            }
        }
        return found;
    }

    private static boolean sameSignature(final IMemberSymbol.MethodSymbol left,
                                         final IMemberSymbol.MethodSymbol right) {
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
