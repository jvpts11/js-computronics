/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.parse;

import dev.jstech.computronics.cannon.CannonError;
import dev.jstech.computronics.cannon.DiagnosticBag;
import dev.jstech.computronics.cannon.ast.CompilationUnit;
import dev.jstech.computronics.cannon.ast.IDecl;
import dev.jstech.computronics.cannon.ast.IExpr;
import dev.jstech.computronics.cannon.ast.Operator;
import dev.jstech.computronics.cannon.ast.IStmt;
import dev.jstech.computronics.cannon.ast.TypeRef;
import dev.jstech.computronics.cannon.lex.Token;
import dev.jstech.computronics.cannon.lex.TokenKind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads tokens into a tree.
 *
 * <p>It is a plain recursive descent parser with one token of lookahead, plus a few longer scans
 * where the grammar is genuinely ambiguous: telling a declaration from an expression, a cast from a
 * parenthesised value, and a lambda from either. Those scans only look, they never report, so a
 * guess that turns out wrong costs nothing.
 *
 * <p>It never throws. A construct it cannot read is reported once and skipped to the next safe
 * point, so one missing brace does not turn into a page of noise, and every loop is guaranteed to
 * consume at least one token per pass so a file of nonsense still terminates. The tree it returns
 * is only meaningful when nothing was reported: after an error it holds whatever could still be
 * read, which is enough to keep parsing and not enough to compile.
 */
public final class Parser {

    private static final Set<TokenKind> MODIFIERS = EnumSet.of(
            TokenKind.PUBLIC, TokenKind.PRIVATE, TokenKind.PROTECTED, TokenKind.STATIC, TokenKind.READONLY);

    private static final Set<TokenKind> BUILT_IN_TYPES = EnumSet.of(
            TokenKind.INT, TokenKind.LONG, TokenKind.FLOAT, TokenKind.DOUBLE,
            TokenKind.BOOL, TokenKind.STRING, TokenKind.CHAR, TokenKind.OBJECT);

    private static final Set<TokenKind> LITERALS = EnumSet.of(
            TokenKind.INT_LITERAL, TokenKind.LONG_LITERAL, TokenKind.FLOAT_LITERAL, TokenKind.DOUBLE_LITERAL,
            TokenKind.STRING_LITERAL, TokenKind.CHAR_LITERAL, TokenKind.TRUE, TokenKind.FALSE, TokenKind.NULL);

    private static final Set<TokenKind> TYPE_DECLARATION_STARTS = EnumSet.of(
            TokenKind.CLASS, TokenKind.INTERFACE, TokenKind.ENUM, TokenKind.DELEGATE);

    private final List<Token> tokens;
    private final DiagnosticBag diagnostics;
    private int position;

    public Parser(final List<Token> tokens, final DiagnosticBag diagnostics) {
        this.tokens = new ArrayList<>(tokens);
        this.diagnostics = diagnostics;
    }

    /** Reads the whole file. The unit holds every type the parser managed to read. */
    public CompilationUnit parse(final String file) {
        final List<IDecl.ITypeDecl> types = new ArrayList<>();
        while (!this.atEnd()) {
            final int before = this.position;
            final IDecl.ITypeDecl type = this.parseTypeDeclaration();
            if (type != null) {
                types.add(type);
            } else {
                this.skipToTypeDeclaration();
            }
            if (this.position == before) {
                this.advance();
            }
        }
        return new CompilationUnit(file, types);
    }

    // ---------------------------------------------------------------- declarations

    private IDecl.ITypeDecl parseTypeDeclaration() {
        final Set<IDecl.Modifier> modifiers = this.parseModifiers();
        final Token start = this.peek();
        if (this.match(TokenKind.CLASS)) {
            return this.parseClass(modifiers, start);
        }
        if (this.match(TokenKind.INTERFACE)) {
            return this.parseInterface(modifiers, start);
        }
        if (this.match(TokenKind.ENUM)) {
            return this.parseEnum(modifiers, start);
        }
        if (this.match(TokenKind.DELEGATE)) {
            return this.parseDelegate(modifiers, start);
        }
        this.diagnostics.error(start.line(), start.column(),
                CannonError.EXPECTED_TYPE_DECLARATION, start.describe());
        return null;
    }

    private IDecl.ITypeDecl parseClass(final Set<IDecl.Modifier> modifiers, final Token start) {
        final String name = this.expectIdentifier();
        final List<TypeRef> bases = this.parseBaseList();
        final List<IDecl.IMemberDecl> members = new ArrayList<>();
        if (this.expect(TokenKind.LEFT_BRACE)) {
            while (!this.check(TokenKind.RIGHT_BRACE) && !this.atEnd()) {
                final int before = this.position;
                final IDecl.IMemberDecl member = this.parseMember(name);
                if (member != null) {
                    members.add(member);
                } else {
                    this.skipToMember();
                }
                if (this.position == before) {
                    this.advance();
                }
            }
            this.expect(TokenKind.RIGHT_BRACE);
        }
        return new IDecl.ClassDecl(modifiers, name, bases, members, start.line(), start.column());
    }

    private IDecl.ITypeDecl parseInterface(final Set<IDecl.Modifier> modifiers, final Token start) {
        final String name = this.expectIdentifier();
        final List<TypeRef> bases = this.parseBaseList();
        final List<IDecl.MethodDecl> methods = new ArrayList<>();
        if (this.expect(TokenKind.LEFT_BRACE)) {
            while (!this.check(TokenKind.RIGHT_BRACE) && !this.atEnd()) {
                final int before = this.position;
                final IDecl.MethodDecl method = this.parseInterfaceMethod();
                if (method != null) {
                    methods.add(method);
                } else {
                    this.skipToMember();
                }
                if (this.position == before) {
                    this.advance();
                }
            }
            this.expect(TokenKind.RIGHT_BRACE);
        }
        return new IDecl.InterfaceDecl(modifiers, name, bases, methods, start.line(), start.column());
    }

    private IDecl.MethodDecl parseInterfaceMethod() {
        final Set<IDecl.Modifier> modifiers = this.parseModifiers();
        final Token start = this.peek();
        final TypeRef returnType = this.parseReturnType();
        if (returnType == null) {
            return null;
        }
        final String name = this.expectIdentifier();
        final List<IDecl.Parameter> parameters = this.parseParameters();
        this.expect(TokenKind.SEMICOLON);
        return new IDecl.MethodDecl(modifiers, returnType, name, parameters, null, start.line(), start.column());
    }

    private IDecl.ITypeDecl parseEnum(final Set<IDecl.Modifier> modifiers, final Token start) {
        final String name = this.expectIdentifier();
        final List<IDecl.EnumConstant> constants = new ArrayList<>();
        if (this.expect(TokenKind.LEFT_BRACE)) {
            while (!this.check(TokenKind.RIGHT_BRACE) && !this.atEnd()) {
                final int before = this.position;
                final Token constantStart = this.peek();
                final String constantName = this.expectIdentifier();
                IExpr value = null;
                if (this.match(TokenKind.ASSIGN)) {
                    value = this.parseExpression();
                }
                constants.add(new IDecl.EnumConstant(constantName, value,
                        constantStart.line(), constantStart.column()));
                if (!this.match(TokenKind.COMMA)) {
                    break;
                }
                if (this.position == before) {
                    this.advance();
                }
            }
            this.expect(TokenKind.RIGHT_BRACE);
        }
        return new IDecl.EnumDecl(modifiers, name, constants, start.line(), start.column());
    }

    private IDecl.ITypeDecl parseDelegate(final Set<IDecl.Modifier> modifiers, final Token start) {
        final TypeRef returnType = this.parseReturnType();
        final String name = this.expectIdentifier();
        final List<IDecl.Parameter> parameters = this.parseParameters();
        this.expect(TokenKind.SEMICOLON);
        return new IDecl.DelegateDecl(modifiers, returnType, name, parameters, start.line(), start.column());
    }

    private List<TypeRef> parseBaseList() {
        final List<TypeRef> bases = new ArrayList<>();
        if (this.match(TokenKind.COLON)) {
            do {
                final TypeRef base = this.parseTypeRef();
                if (base == null) {
                    break;
                }
                bases.add(base);
            } while (this.match(TokenKind.COMMA));
        }
        return bases;
    }

    // A member starts with its modifiers, then a shape that says what it is: the class's own name
    // before a parenthesis is a constructor, "event" is an event, and otherwise a type and a name
    // are followed by parentheses for a method, a brace for a property, or neither for a field.
    private IDecl.IMemberDecl parseMember(final String className) {
        final Set<IDecl.Modifier> modifiers = this.parseModifiers();
        final Token start = this.peek();

        if (this.match(TokenKind.EVENT)) {
            final TypeRef type = this.parseTypeRef();
            if (type == null) {
                return null;
            }
            final String name = this.expectIdentifier();
            this.expect(TokenKind.SEMICOLON);
            return new IDecl.EventDecl(modifiers, type, name, start.line(), start.column());
        }

        if (this.check(TokenKind.IDENTIFIER) && start.text().equals(className)
                && this.kindAt(this.position + 1) == TokenKind.LEFT_PAREN) {
            return this.parseConstructor(modifiers, start, className);
        }

        if (!this.check(TokenKind.VOID) && !this.isTypeStart(start.kind())) {
            this.diagnostics.error(start.line(), start.column(), CannonError.EXPECTED_MEMBER, start.describe());
            return null;
        }
        final TypeRef type = this.parseReturnType();
        final String name = this.expectIdentifier();

        if (this.check(TokenKind.LEFT_PAREN)) {
            final List<IDecl.Parameter> parameters = this.parseParameters();
            IStmt.Block body = null;
            if (this.check(TokenKind.LEFT_BRACE)) {
                body = this.parseBlock();
            } else {
                this.expect(TokenKind.SEMICOLON);
            }
            return new IDecl.MethodDecl(modifiers, type, name, parameters, body, start.line(), start.column());
        }

        if (this.check(TokenKind.LEFT_BRACE)) {
            return this.parseProperty(modifiers, type, name, start);
        }

        IExpr initializer = null;
        if (this.match(TokenKind.ASSIGN)) {
            initializer = this.parseExpression();
        }
        this.expect(TokenKind.SEMICOLON);
        return new IDecl.FieldDecl(modifiers, type, name, initializer, start.line(), start.column());
    }

    private IDecl.IMemberDecl parseConstructor(final Set<IDecl.Modifier> modifiers, final Token start,
                                             final String className) {
        this.advance();
        final List<IDecl.Parameter> parameters = this.parseParameters();
        IDecl.ConstructorCall chained = null;
        if (this.match(TokenKind.COLON)) {
            final Token chainStart = this.peek();
            final boolean base = this.check(TokenKind.BASE);
            if (base || this.check(TokenKind.THIS)) {
                this.advance();
            } else {
                this.diagnostics.error(chainStart.line(), chainStart.column(),
                        CannonError.EXPECTED_TOKEN, TokenKind.BASE.describe(), chainStart.describe());
            }
            final List<IExpr> arguments = this.parseArguments();
            chained = new IDecl.ConstructorCall(base, arguments, chainStart.line(), chainStart.column());
        }
        final IStmt.Block body = this.check(TokenKind.LEFT_BRACE) ? this.parseBlock() : null;
        if (body == null) {
            this.expect(TokenKind.SEMICOLON);
        }
        return new IDecl.ConstructorDecl(modifiers, className, parameters, chained, body,
                start.line(), start.column());
    }

    // The short form only: "{ get; private set; }". A body on an accessor is a v2 feature, so a
    // brace where the semicolon belongs is reported as the missing semicolon it is.
    private IDecl.IMemberDecl parseProperty(final Set<IDecl.Modifier> modifiers, final TypeRef type,
                                          final String name, final Token start) {
        this.advance();
        IDecl.Accessor getter = null;
        IDecl.Accessor setter = null;
        while (!this.check(TokenKind.RIGHT_BRACE) && !this.atEnd()) {
            final int before = this.position;
            final Set<IDecl.Modifier> accessorModifiers = this.parseModifiers();
            final Token word = this.peek();
            if (this.check(TokenKind.IDENTIFIER) && "get".equals(word.text())) {
                this.advance();
                getter = new IDecl.Accessor(accessorModifiers, word.line(), word.column());
            } else if (this.check(TokenKind.IDENTIFIER) && "set".equals(word.text())) {
                this.advance();
                setter = new IDecl.Accessor(accessorModifiers, word.line(), word.column());
            } else {
                this.diagnostics.error(word.line(), word.column(), CannonError.EXPECTED_MEMBER, word.describe());
                break;
            }
            this.expect(TokenKind.SEMICOLON);
            if (this.position == before) {
                this.advance();
            }
        }
        this.expect(TokenKind.RIGHT_BRACE);
        return new IDecl.PropertyDecl(modifiers, type, name, getter, setter, start.line(), start.column());
    }

    private Set<IDecl.Modifier> parseModifiers() {
        final Set<IDecl.Modifier> modifiers = EnumSet.noneOf(IDecl.Modifier.class);
        while (MODIFIERS.contains(this.peek().kind())) {
            final Token word = this.advance();
            final IDecl.Modifier modifier = switch (word.kind()) {
                case PUBLIC -> IDecl.Modifier.PUBLIC;
                case PRIVATE -> IDecl.Modifier.PRIVATE;
                case PROTECTED -> IDecl.Modifier.PROTECTED;
                case STATIC -> IDecl.Modifier.STATIC;
                default -> IDecl.Modifier.READONLY;
            };
            if (!modifiers.add(modifier)) {
                this.diagnostics.error(word.line(), word.column(),
                        CannonError.DUPLICATE_MODIFIER, modifier.text());
            }
        }
        return modifiers;
    }

    private List<IDecl.Parameter> parseParameters() {
        final List<IDecl.Parameter> parameters = new ArrayList<>();
        if (!this.expect(TokenKind.LEFT_PAREN)) {
            return parameters;
        }
        while (!this.check(TokenKind.RIGHT_PAREN) && !this.atEnd()) {
            final int before = this.position;
            final Token start = this.peek();
            final boolean outward = this.match(TokenKind.OUT);
            final TypeRef type = this.parseTypeRef();
            if (type == null) {
                break;
            }
            final String name = this.expectIdentifier();
            parameters.add(new IDecl.Parameter(outward, type, name, start.line(), start.column()));
            if (!this.match(TokenKind.COMMA)) {
                break;
            }
            if (this.position == before) {
                this.advance();
            }
        }
        this.expect(TokenKind.RIGHT_PAREN);
        return parameters;
    }

    // ---------------------------------------------------------------- types

    private TypeRef parseReturnType() {
        if (this.check(TokenKind.VOID)) {
            final Token word = this.advance();
            return TypeRef.named("void", word.line(), word.column());
        }
        return this.parseTypeRef();
    }

    private TypeRef parseTypeRef() {
        final Token start = this.peek();
        if (!this.isTypeStart(start.kind())) {
            this.diagnostics.error(start.line(), start.column(), CannonError.EXPECTED_TYPE, start.describe());
            return null;
        }
        this.advance();
        final List<TypeRef> arguments = new ArrayList<>();
        if (this.check(TokenKind.LESS)) {
            this.advance();
            do {
                final TypeRef argument = this.parseTypeRef();
                if (argument == null) {
                    break;
                }
                arguments.add(argument);
            } while (this.match(TokenKind.COMMA));
            this.closeTypeArguments();
        }
        int arrayRank = 0;
        while (this.check(TokenKind.LEFT_BRACKET) && this.kindAt(this.position + 1) == TokenKind.RIGHT_BRACKET) {
            this.advance();
            this.advance();
            arrayRank++;
        }
        return new TypeRef(start.text(), arguments, arrayRank, start.line(), start.column());
    }

    // "Map<string, List<int>>" ends on one token holding two closing angles, so the first one is
    // taken here and the token is left behind as the second.
    private void closeTypeArguments() {
        if (this.match(TokenKind.GREATER)) {
            return;
        }
        if (this.check(TokenKind.SHIFT_RIGHT)) {
            final Token shift = this.peek();
            this.tokens.set(this.position,
                    Token.of(TokenKind.GREATER, ">", shift.line(), shift.column() + 1));
            return;
        }
        this.expect(TokenKind.GREATER);
    }

    private boolean isTypeStart(final TokenKind kind) {
        return kind == TokenKind.IDENTIFIER || kind == TokenKind.VAR || BUILT_IN_TYPES.contains(kind);
    }

    // Looks past a type without reporting anything, and answers where it ends, or -1 if what is
    // there is not a type at all. Used only to tell a declaration from an expression.
    private int scanType(final int from) {
        int at = from;
        if (!this.isTypeStart(this.kindAt(at))) {
            return -1;
        }
        at++;
        if (this.kindAt(at) == TokenKind.LESS) {
            int depth = 1;
            at++;
            while (depth > 0) {
                final TokenKind kind = this.kindAt(at);
                if (kind == TokenKind.END_OF_FILE) {
                    return -1;
                }
                if (kind == TokenKind.LESS) {
                    depth++;
                } else if (kind == TokenKind.GREATER) {
                    depth--;
                } else if (kind == TokenKind.SHIFT_RIGHT) {
                    depth -= 2;
                } else if (kind != TokenKind.COMMA && kind != TokenKind.LEFT_BRACKET
                        && kind != TokenKind.RIGHT_BRACKET && !this.isTypeStart(kind)) {
                    return -1;
                }
                at++;
                if (depth < 0) {
                    return -1;
                }
            }
        }
        while (this.kindAt(at) == TokenKind.LEFT_BRACKET && this.kindAt(at + 1) == TokenKind.RIGHT_BRACKET) {
            at += 2;
        }
        return at;
    }

    // ---------------------------------------------------------------- statements

    private IStmt.Block parseBlock() {
        final Token start = this.peek();
        final List<IStmt> statements = new ArrayList<>();
        if (!this.expect(TokenKind.LEFT_BRACE)) {
            return new IStmt.Block(statements, start.line(), start.column());
        }
        while (!this.check(TokenKind.RIGHT_BRACE) && !this.atEnd()) {
            final int before = this.position;
            final IStmt statement = this.parseStatement();
            if (statement != null) {
                statements.add(statement);
            }
            if (this.position == before) {
                this.advance();
            }
        }
        this.expect(TokenKind.RIGHT_BRACE);
        return new IStmt.Block(statements, start.line(), start.column());
    }

    private IStmt parseStatement() {
        final Token start = this.peek();
        switch (start.kind()) {
            case LEFT_BRACE:
                return this.parseBlock();
            case SEMICOLON:
                this.advance();
                return new IStmt.Empty(start.line(), start.column());
            case IF:
                return this.parseIf();
            case WHILE:
                return this.parseWhile();
            case DO:
                return this.parseDoWhile();
            case FOR:
                return this.parseFor();
            case FOREACH:
                return this.parseForEach();
            case SWITCH:
                return this.parseSwitch();
            case BREAK:
                this.advance();
                this.expect(TokenKind.SEMICOLON);
                return new IStmt.Break(start.line(), start.column());
            case CONTINUE:
                this.advance();
                this.expect(TokenKind.SEMICOLON);
                return new IStmt.Continue(start.line(), start.column());
            case RETURN:
                return this.parseReturn();
            case DISPOSE:
                return this.parseDispose();
            default:
                return this.parseDeclarationOrExpressionStatement();
        }
    }

    private IStmt parseIf() {
        final Token start = this.advance();
        this.expect(TokenKind.LEFT_PAREN);
        final IExpr condition = this.parseExpression();
        this.expect(TokenKind.RIGHT_PAREN);
        final IStmt then = this.parseStatement();
        IStmt otherwise = null;
        if (this.match(TokenKind.ELSE)) {
            otherwise = this.parseStatement();
        }
        return new IStmt.If(condition, then, otherwise, start.line(), start.column());
    }

    private IStmt parseWhile() {
        final Token start = this.advance();
        this.expect(TokenKind.LEFT_PAREN);
        final IExpr condition = this.parseExpression();
        this.expect(TokenKind.RIGHT_PAREN);
        final IStmt body = this.parseStatement();
        return new IStmt.While(condition, body, start.line(), start.column());
    }

    private IStmt parseDoWhile() {
        final Token start = this.advance();
        final IStmt body = this.parseStatement();
        this.expect(TokenKind.WHILE);
        this.expect(TokenKind.LEFT_PAREN);
        final IExpr condition = this.parseExpression();
        this.expect(TokenKind.RIGHT_PAREN);
        this.expect(TokenKind.SEMICOLON);
        return new IStmt.DoWhile(body, condition, start.line(), start.column());
    }

    private IStmt parseFor() {
        final Token start = this.advance();
        this.expect(TokenKind.LEFT_PAREN);
        final List<IStmt> initializers = new ArrayList<>();
        if (!this.check(TokenKind.SEMICOLON)) {
            if (this.looksLikeDeclaration()) {
                initializers.add(this.parseLocalDeclaration(false));
            } else {
                do {
                    final Token at = this.peek();
                    final IExpr expression = this.parseExpression();
                    if (expression != null) {
                        initializers.add(new IStmt.ExprStmt(expression, at.line(), at.column()));
                    }
                } while (this.match(TokenKind.COMMA));
            }
        }
        this.expect(TokenKind.SEMICOLON);
        final IExpr condition = this.check(TokenKind.SEMICOLON) ? null : this.parseExpression();
        this.expect(TokenKind.SEMICOLON);
        final List<IExpr> updates = new ArrayList<>();
        if (!this.check(TokenKind.RIGHT_PAREN)) {
            do {
                final IExpr update = this.parseExpression();
                if (update != null) {
                    updates.add(update);
                }
            } while (this.match(TokenKind.COMMA));
        }
        this.expect(TokenKind.RIGHT_PAREN);
        final IStmt body = this.parseStatement();
        return new IStmt.For(initializers, condition, updates, body, start.line(), start.column());
    }

    private IStmt parseForEach() {
        final Token start = this.advance();
        this.expect(TokenKind.LEFT_PAREN);
        final TypeRef type = this.parseTypeRef();
        final String name = this.expectIdentifier();
        this.expect(TokenKind.IN);
        final IExpr source = this.parseExpression();
        this.expect(TokenKind.RIGHT_PAREN);
        final IStmt body = this.parseStatement();
        return new IStmt.ForEach(type, name, source, body, start.line(), start.column());
    }

    private IStmt parseSwitch() {
        final Token start = this.advance();
        this.expect(TokenKind.LEFT_PAREN);
        final IExpr value = this.parseExpression();
        this.expect(TokenKind.RIGHT_PAREN);
        final List<IStmt.SwitchSection> sections = new ArrayList<>();
        if (this.expect(TokenKind.LEFT_BRACE)) {
            while (!this.check(TokenKind.RIGHT_BRACE) && !this.atEnd()) {
                final int before = this.position;
                final IStmt.SwitchSection section = this.parseSwitchSection();
                if (section != null) {
                    sections.add(section);
                }
                if (this.position == before) {
                    this.advance();
                }
            }
            this.expect(TokenKind.RIGHT_BRACE);
        }
        return new IStmt.Switch(value, sections, start.line(), start.column());
    }

    private IStmt.SwitchSection parseSwitchSection() {
        final Token start = this.peek();
        final List<IExpr> labels = new ArrayList<>();
        boolean fallback = false;
        while (this.check(TokenKind.CASE) || this.check(TokenKind.DEFAULT)) {
            if (this.match(TokenKind.CASE)) {
                final IExpr label = this.parseExpression();
                if (label != null) {
                    labels.add(label);
                }
            } else {
                this.advance();
                fallback = true;
            }
            this.expect(TokenKind.COLON);
        }
        if (labels.isEmpty() && !fallback) {
            this.diagnostics.error(start.line(), start.column(),
                    CannonError.EXPECTED_TOKEN, TokenKind.CASE.describe(), start.describe());
            return null;
        }
        final List<IStmt> statements = new ArrayList<>();
        while (!this.check(TokenKind.CASE) && !this.check(TokenKind.DEFAULT)
                && !this.check(TokenKind.RIGHT_BRACE) && !this.atEnd()) {
            final int before = this.position;
            final IStmt statement = this.parseStatement();
            if (statement != null) {
                statements.add(statement);
            }
            if (this.position == before) {
                this.advance();
            }
        }
        return new IStmt.SwitchSection(labels, fallback, statements, start.line(), start.column());
    }

    private IStmt parseReturn() {
        final Token start = this.advance();
        final IExpr value = this.check(TokenKind.SEMICOLON) ? null : this.parseExpression();
        this.expect(TokenKind.SEMICOLON);
        return new IStmt.Return(value, start.line(), start.column());
    }

    private IStmt parseDispose() {
        final Token start = this.advance();
        final IExpr target = this.parseExpression();
        this.expect(TokenKind.SEMICOLON);
        return new IStmt.Dispose(target, start.line(), start.column());
    }

    private IStmt parseDeclarationOrExpressionStatement() {
        if (this.looksLikeDeclaration()) {
            return this.parseLocalDeclaration(true);
        }
        final Token start = this.peek();
        final IExpr expression = this.parseExpression();
        if (expression == null) {
            return null;
        }
        if (!this.isStatementExpression(expression)) {
            this.diagnostics.error(start.line(), start.column(), CannonError.NOT_A_STATEMENT);
        }
        this.expect(TokenKind.SEMICOLON);
        return new IStmt.ExprStmt(expression, start.line(), start.column());
    }

    private IStmt parseLocalDeclaration(final boolean terminated) {
        final Token start = this.peek();
        final TypeRef type = this.parseTypeRef();
        final String name = this.expectIdentifier();
        IExpr initializer = null;
        if (this.match(TokenKind.ASSIGN)) {
            initializer = this.parseExpression();
        }
        if (terminated) {
            this.expect(TokenKind.SEMICOLON);
        }
        return new IStmt.LocalDecl(type, name, initializer, start.line(), start.column());
    }

    // A type followed by a name is a declaration; anything else at the head of a statement is an
    // expression. This is the one place the grammar genuinely needs more than one token of lookahead.
    private boolean looksLikeDeclaration() {
        final int after = this.scanType(this.position);
        return after > this.position && this.kindAt(after) == TokenKind.IDENTIFIER;
    }

    // Evaluating a value and throwing it away is always a mistake, so only the forms that do
    // something are allowed to stand alone.
    private boolean isStatementExpression(final IExpr expression) {
        return switch (expression) {
            case IExpr.Call ignored -> true;
            case IExpr.Assign ignored -> true;
            case IExpr.New ignored -> true;
            case IExpr.Unary unary -> unary.operator() == Operator.INCREMENT
                    || unary.operator() == Operator.DECREMENT;
            default -> false;
        };
    }

    // ---------------------------------------------------------------- expressions

    private IExpr parseExpression() {
        return this.parseAssignment();
    }

    private IExpr parseAssignment() {
        final IExpr left = this.parseConditional();
        final Operator operator = this.assignmentOperator(this.peek().kind());
        if (operator == null || left == null) {
            return left;
        }
        final Token at = this.advance();
        final IExpr value = this.parseAssignment();
        if (!this.isAssignable(left)) {
            this.diagnostics.error(at.line(), at.column(), CannonError.INVALID_ASSIGNMENT_TARGET);
        }
        return new IExpr.Assign(left, operator, value, left.line(), left.column());
    }

    private Operator assignmentOperator(final TokenKind kind) {
        return switch (kind) {
            case ASSIGN -> Operator.ASSIGN;
            case PLUS_ASSIGN -> Operator.ADD;
            case MINUS_ASSIGN -> Operator.SUBTRACT;
            case STAR_ASSIGN -> Operator.MULTIPLY;
            case SLASH_ASSIGN -> Operator.DIVIDE;
            case PERCENT_ASSIGN -> Operator.REMAINDER;
            case AMPERSAND_ASSIGN -> Operator.BIT_AND;
            case PIPE_ASSIGN -> Operator.BIT_OR;
            case CARET_ASSIGN -> Operator.BIT_XOR;
            case SHIFT_LEFT_ASSIGN -> Operator.SHIFT_LEFT;
            case SHIFT_RIGHT_ASSIGN -> Operator.SHIFT_RIGHT;
            default -> null;
        };
    }

    private boolean isAssignable(final IExpr expression) {
        return expression instanceof IExpr.Name
                || expression instanceof IExpr.Member
                || expression instanceof IExpr.Index;
    }

    private IExpr parseConditional() {
        final IExpr condition = this.parseBinary(0);
        if (!this.check(TokenKind.QUESTION)) {
            return condition;
        }
        this.advance();
        final IExpr whenTrue = this.parseAssignment();
        this.expect(TokenKind.COLON);
        final IExpr whenFalse = this.parseAssignment();
        return new IExpr.Conditional(condition, whenTrue, whenFalse,
                condition == null ? this.peek().line() : condition.line(),
                condition == null ? this.peek().column() : condition.column());
    }

    // One table instead of nine near-identical methods; the index is the precedence level, lowest
    // binding first, exactly as C# orders them.
    private static final TokenKind[][] BINARY_LEVELS = {
        {TokenKind.OR_OR},
        {TokenKind.AND_AND},
        {TokenKind.PIPE},
        {TokenKind.CARET},
        {TokenKind.AMPERSAND},
        {TokenKind.EQUAL, TokenKind.NOT_EQUAL},
        {TokenKind.LESS, TokenKind.LESS_EQUAL, TokenKind.GREATER, TokenKind.GREATER_EQUAL},
        {TokenKind.SHIFT_LEFT, TokenKind.SHIFT_RIGHT},
        {TokenKind.PLUS, TokenKind.MINUS},
        {TokenKind.STAR, TokenKind.SLASH, TokenKind.PERCENT},
    };

    /** The level that also carries {@code is} and {@code as}, which bind like a comparison. */
    private static final int RELATIONAL_LEVEL = 6;

    private IExpr parseBinary(final int level) {
        if (level >= BINARY_LEVELS.length) {
            return this.parseUnary();
        }
        IExpr left = this.parseBinary(level + 1);
        while (true) {
            if (level == RELATIONAL_LEVEL && (this.check(TokenKind.IS) || this.check(TokenKind.AS))) {
                final Token at = this.advance();
                final TypeRef type = this.parseTypeRef();
                left = new IExpr.TypeTest(left, type, at.is(TokenKind.AS),
                        left == null ? at.line() : left.line(), left == null ? at.column() : left.column());
                continue;
            }
            final TokenKind kind = this.matchAny(BINARY_LEVELS[level]);
            if (kind == null) {
                return left;
            }
            final IExpr right = this.parseBinary(level + 1);
            left = new IExpr.Binary(this.binaryOperator(kind), left, right,
                    left == null ? this.peek().line() : left.line(),
                    left == null ? this.peek().column() : left.column());
        }
    }

    private Operator binaryOperator(final TokenKind kind) {
        return switch (kind) {
            case OR_OR -> Operator.OR;
            case AND_AND -> Operator.AND;
            case PIPE -> Operator.BIT_OR;
            case CARET -> Operator.BIT_XOR;
            case AMPERSAND -> Operator.BIT_AND;
            case EQUAL -> Operator.EQUAL;
            case NOT_EQUAL -> Operator.NOT_EQUAL;
            case LESS -> Operator.LESS;
            case LESS_EQUAL -> Operator.LESS_EQUAL;
            case GREATER -> Operator.GREATER;
            case GREATER_EQUAL -> Operator.GREATER_EQUAL;
            case SHIFT_LEFT -> Operator.SHIFT_LEFT;
            case SHIFT_RIGHT -> Operator.SHIFT_RIGHT;
            case PLUS -> Operator.ADD;
            case MINUS -> Operator.SUBTRACT;
            case STAR -> Operator.MULTIPLY;
            case SLASH -> Operator.DIVIDE;
            default -> Operator.REMAINDER;
        };
    }

    private IExpr parseUnary() {
        final Token start = this.peek();
        switch (start.kind()) {
            case NOT:
                this.advance();
                return new IExpr.Unary(Operator.NOT, this.parseUnary(), false, start.line(), start.column());
            case MINUS:
                this.advance();
                return new IExpr.Unary(Operator.NEGATE, this.parseUnary(), false, start.line(), start.column());
            case PLUS:
                this.advance();
                return new IExpr.Unary(Operator.PLUS, this.parseUnary(), false, start.line(), start.column());
            case TILDE:
                this.advance();
                return new IExpr.Unary(Operator.COMPLEMENT, this.parseUnary(), false, start.line(), start.column());
            case PLUS_PLUS:
                this.advance();
                return new IExpr.Unary(Operator.INCREMENT, this.parseUnary(), false, start.line(), start.column());
            case MINUS_MINUS:
                this.advance();
                return new IExpr.Unary(Operator.DECREMENT, this.parseUnary(), false, start.line(), start.column());
            default:
                break;
        }
        if (this.isCastAhead()) {
            this.advance();
            final TypeRef type = this.parseTypeRef();
            this.expect(TokenKind.RIGHT_PAREN);
            return new IExpr.Cast(type, this.parseUnary(), start.line(), start.column());
        }
        return this.parsePostfix();
    }

    // "(int) x" is a conversion and "(a) + b" is a sum in brackets. The rule is the one C# uses: a
    // built-in type name always converts, and any other name only converts when what follows could
    // start a value on its own.
    private boolean isCastAhead() {
        if (!this.check(TokenKind.LEFT_PAREN) || this.isLambdaAhead()) {
            return false;
        }
        final int after = this.scanType(this.position + 1);
        if (after < 0 || this.kindAt(after) != TokenKind.RIGHT_PAREN) {
            return false;
        }
        final TokenKind next = this.kindAt(after + 1);
        if (BUILT_IN_TYPES.contains(this.kindAt(this.position + 1))) {
            return true;
        }
        return next == TokenKind.IDENTIFIER || next == TokenKind.THIS || next == TokenKind.BASE
                || next == TokenKind.NEW || next == TokenKind.NOT || next == TokenKind.TILDE
                || next == TokenKind.LEFT_PAREN || LITERALS.contains(next);
    }

    private boolean isLambdaAhead() {
        if (!this.check(TokenKind.LEFT_PAREN)) {
            return false;
        }
        int depth = 0;
        int at = this.position;
        while (true) {
            final TokenKind kind = this.kindAt(at);
            if (kind == TokenKind.END_OF_FILE) {
                return false;
            }
            if (kind == TokenKind.LEFT_PAREN) {
                depth++;
            } else if (kind == TokenKind.RIGHT_PAREN) {
                depth--;
                if (depth == 0) {
                    return this.kindAt(at + 1) == TokenKind.ARROW;
                }
            }
            at++;
        }
    }

    private IExpr parsePostfix() {
        IExpr expression = this.parsePrimary();
        while (true) {
            if (this.check(TokenKind.DOT)) {
                final Token dot = this.advance();
                final String name = this.expectIdentifier();
                expression = new IExpr.Member(expression, name,
                        expression == null ? dot.line() : expression.line(),
                        expression == null ? dot.column() : expression.column());
            } else if (this.check(TokenKind.LEFT_PAREN)) {
                final Token at = this.peek();
                final List<IExpr> arguments = this.parseArguments();
                expression = new IExpr.Call(expression, arguments,
                        expression == null ? at.line() : expression.line(),
                        expression == null ? at.column() : expression.column());
            } else if (this.check(TokenKind.LEFT_BRACKET)) {
                final Token at = this.advance();
                final IExpr index = this.parseExpression();
                this.expect(TokenKind.RIGHT_BRACKET);
                expression = new IExpr.Index(expression, index,
                        expression == null ? at.line() : expression.line(),
                        expression == null ? at.column() : expression.column());
            } else if (this.check(TokenKind.PLUS_PLUS) || this.check(TokenKind.MINUS_MINUS)) {
                final Token at = this.advance();
                expression = new IExpr.Unary(at.is(TokenKind.PLUS_PLUS) ? Operator.INCREMENT : Operator.DECREMENT,
                        expression, true,
                        expression == null ? at.line() : expression.line(),
                        expression == null ? at.column() : expression.column());
            } else {
                return expression;
            }
        }
    }

    private List<IExpr> parseArguments() {
        final List<IExpr> arguments = new ArrayList<>();
        if (!this.expect(TokenKind.LEFT_PAREN)) {
            return arguments;
        }
        while (!this.check(TokenKind.RIGHT_PAREN) && !this.atEnd()) {
            final int before = this.position;
            final IExpr argument = this.check(TokenKind.OUT) ? this.parseOutArgument() : this.parseExpression();
            if (argument != null) {
                arguments.add(argument);
            }
            if (!this.match(TokenKind.COMMA)) {
                break;
            }
            if (this.position == before) {
                this.advance();
            }
        }
        this.expect(TokenKind.RIGHT_PAREN);
        return arguments;
    }

    // "out value" hands over a place that already exists; "out int value" and "out var value" declare
    // it right there, which is where a player wants it when the call is the only reason it exists.
    private IExpr parseOutArgument() {
        final Token start = this.advance();
        final int afterType = this.scanType(this.position);
        TypeRef type = null;
        if (afterType > this.position && this.kindAt(afterType) == TokenKind.IDENTIFIER) {
            type = this.parseTypeRef();
        }
        final String name = this.expectIdentifier();
        return new IExpr.OutArgument(type, name, start.line(), start.column());
    }

    private IExpr parsePrimary() {
        final Token start = this.peek();
        if (LITERALS.contains(start.kind())) {
            this.advance();
            return new IExpr.Literal(start.kind(), start.value(), start.line(), start.column());
        }
        switch (start.kind()) {
            case THIS:
                this.advance();
                return new IExpr.This(start.line(), start.column());
            case BASE:
                this.advance();
                return new IExpr.Base(start.line(), start.column());
            case NEW:
                return this.parseNew();
            case IDENTIFIER:
                if (this.kindAt(this.position + 1) == TokenKind.ARROW) {
                    return this.parseShorthandLambda();
                }
                this.advance();
                return new IExpr.Name(start.text(), start.line(), start.column());
            case LEFT_PAREN:
                if (this.isLambdaAhead()) {
                    return this.parseLambda();
                }
                this.advance();
                final IExpr grouped = this.parseExpression();
                this.expect(TokenKind.RIGHT_PAREN);
                return grouped;
            default:
                break;
        }
        // A built-in type name can stand where a value does, as the receiver of one of its own
        // methods: "string.Format(...)" and "int.Parse(...)" read the way a player expects.
        if (BUILT_IN_TYPES.contains(start.kind()) && this.kindAt(this.position + 1) == TokenKind.DOT) {
            this.advance();
            return new IExpr.Name(start.text(), start.line(), start.column());
        }
        this.diagnostics.error(start.line(), start.column(), CannonError.EXPECTED_EXPRESSION, start.describe());
        return null;
    }

    private IExpr parseNew() {
        final Token start = this.advance();
        final TypeRef type = this.parseTypeRef();
        if (this.check(TokenKind.LEFT_BRACKET)) {
            this.advance();
            final IExpr length = this.parseExpression();
            this.expect(TokenKind.RIGHT_BRACKET);
            return new IExpr.NewArray(type, length, start.line(), start.column());
        }
        final List<IExpr> arguments = this.parseArguments();
        return new IExpr.New(type, arguments, start.line(), start.column());
    }

    private IExpr parseShorthandLambda() {
        final Token name = this.advance();
        this.advance();
        final List<IDecl.Parameter> parameters = List.of(
                new IDecl.Parameter(false, null, name.text(), name.line(), name.column()));
        return this.finishLambda(parameters, name);
    }

    private IExpr parseLambda() {
        final Token start = this.peek();
        final List<IDecl.Parameter> parameters = new ArrayList<>();
        this.advance();
        while (!this.check(TokenKind.RIGHT_PAREN) && !this.atEnd()) {
            final int before = this.position;
            final Token at = this.peek();
            final boolean outward = this.match(TokenKind.OUT);
            final int afterType = this.scanType(this.position);
            TypeRef type = null;
            if (afterType > this.position && this.kindAt(afterType) == TokenKind.IDENTIFIER) {
                type = this.parseTypeRef();
            }
            final String name = this.expectIdentifier();
            parameters.add(new IDecl.Parameter(outward, type, name, at.line(), at.column()));
            if (!this.match(TokenKind.COMMA)) {
                break;
            }
            if (this.position == before) {
                this.advance();
            }
        }
        this.expect(TokenKind.RIGHT_PAREN);
        this.expect(TokenKind.ARROW);
        return this.finishLambda(parameters, start);
    }

    private IExpr finishLambda(final List<IDecl.Parameter> parameters, final Token start) {
        if (this.check(TokenKind.LEFT_BRACE)) {
            final IStmt.Block block = this.parseBlock();
            return new IExpr.Lambda(parameters, null, block, start.line(), start.column());
        }
        final IExpr body = this.parseExpression();
        return new IExpr.Lambda(parameters, body, null, start.line(), start.column());
    }

    // ---------------------------------------------------------------- recovery and cursor

    // After a mistake, run to the next place a declaration can plausibly start, so the rest of the
    // file is still read and the player sees every other mistake in one go.
    private void skipToTypeDeclaration() {
        while (!this.atEnd()) {
            if (TYPE_DECLARATION_STARTS.contains(this.peek().kind()) || MODIFIERS.contains(this.peek().kind())) {
                return;
            }
            this.advance();
        }
    }

    private void skipToMember() {
        int depth = 0;
        while (!this.atEnd()) {
            final TokenKind kind = this.peek().kind();
            if (kind == TokenKind.LEFT_BRACE) {
                depth++;
            } else if (kind == TokenKind.RIGHT_BRACE) {
                if (depth == 0) {
                    return;
                }
                depth--;
            } else if (kind == TokenKind.SEMICOLON && depth == 0) {
                this.advance();
                return;
            }
            this.advance();
        }
    }

    private boolean atEnd() {
        return this.peek().is(TokenKind.END_OF_FILE);
    }

    private Token peek() {
        return this.tokens.get(Math.min(this.position, this.tokens.size() - 1));
    }

    private TokenKind kindAt(final int index) {
        return this.tokens.get(Math.min(Math.max(index, 0), this.tokens.size() - 1)).kind();
    }

    private boolean check(final TokenKind kind) {
        return this.peek().is(kind);
    }

    private boolean match(final TokenKind kind) {
        if (this.check(kind)) {
            this.advance();
            return true;
        }
        return false;
    }

    private TokenKind matchAny(final TokenKind[] kinds) {
        for (final TokenKind kind : kinds) {
            if (this.check(kind)) {
                this.advance();
                return kind;
            }
        }
        return null;
    }

    private Token advance() {
        final Token token = this.peek();
        if (this.position < this.tokens.size() - 1) {
            this.position++;
        }
        return token;
    }

    // Reports and does not consume, so the caller decides how to recover rather than losing a token
    // that might be the start of the next good construct.
    private boolean expect(final TokenKind kind) {
        if (this.check(kind)) {
            this.advance();
            return true;
        }
        final Token found = this.peek();
        this.diagnostics.error(found.line(), found.column(),
                CannonError.EXPECTED_TOKEN, kind.describe(), found.describe());
        return false;
    }

    private String expectIdentifier() {
        if (this.check(TokenKind.IDENTIFIER)) {
            return this.advance().text();
        }
        final Token found = this.peek();
        this.diagnostics.error(found.line(), found.column(),
                CannonError.EXPECTED_TOKEN, TokenKind.IDENTIFIER.describe(), found.describe());
        return found.text();
    }
}
