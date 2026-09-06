/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computronics.cannon.ast.Decl;
import dev.jstech.computronics.cannon.ast.Expr;
import dev.jstech.computronics.cannon.ast.Operator;
import dev.jstech.computronics.cannon.ast.Stmt;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ParserTest {

    private static CannonFrontEnd.Result parse(final String source) {
        return CannonFrontEnd.parse(new SourceFile("Test.can", source));
    }

    /** Parses a method body and hands back its statements, asserting nothing went wrong on the way. */
    private static List<Stmt> body(final String statements) {
        final CannonFrontEnd.Result result = parse("class C { void M() { " + statements + " } }");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final Decl.ClassDecl type = (Decl.ClassDecl) result.unit().type("C");
        final Decl.MethodDecl method = (Decl.MethodDecl) type.members().getFirst();
        return method.body().statements();
    }

    private static List<String> codes(final CannonFrontEnd.Result result) {
        return result.diagnostics().stream().map(Diagnostic::code).toList();
    }

    @Test
    void parse_readsAClassWithEveryMemberKind() {
        final CannonFrontEnd.Result result = parse("""
                class Monitor : IScript {
                    private Network network;
                    private int threshold = 100;
                    public int Count { get; private set; }
                    public event StockHandler Changed;

                    public Monitor(int start) {
                        threshold = start;
                    }

                    public void OnInit() {
                        network = Network.Current;
                    }
                }
                """);
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final Decl.ClassDecl type = (Decl.ClassDecl) result.unit().type("Monitor");
        assertEquals(List.of("IScript"), type.bases().stream().map(base -> base.name()).toList());
        assertEquals(6, type.members().size());
        assertInstanceOf(Decl.FieldDecl.class, type.members().get(0));
        assertInstanceOf(Decl.FieldDecl.class, type.members().get(1));
        assertInstanceOf(Decl.PropertyDecl.class, type.members().get(2));
        assertInstanceOf(Decl.EventDecl.class, type.members().get(3));
        assertInstanceOf(Decl.ConstructorDecl.class, type.members().get(4));
        assertInstanceOf(Decl.MethodDecl.class, type.members().get(5));
    }

    @Test
    void parse_keepsTheAccessWrittenOnEachHalfOfAProperty() {
        final CannonFrontEnd.Result result = parse("class C { public int Count { get; private set; } }");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final Decl.ClassDecl type = (Decl.ClassDecl) result.unit().type("C");
        final Decl.PropertyDecl property = (Decl.PropertyDecl) type.members().getFirst();
        assertEquals(Set.of(Decl.Modifier.PUBLIC), property.modifiers());
        assertNotNull(property.getter());
        assertTrue(property.getter().modifiers().isEmpty());
        assertEquals(Set.of(Decl.Modifier.PRIVATE), property.setter().modifiers());
    }

    @Test
    void parse_readsADelegateBesideTheEventThatUsesIt() {
        final CannonFrontEnd.Result result = parse("""
                delegate void StockHandler(StockEvent e);
                class C { public event StockHandler Changed; }
                """);
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final Decl.DelegateDecl handler = (Decl.DelegateDecl) result.unit().type("StockHandler");
        assertEquals("void", handler.returnType().name());
        assertEquals(1, handler.parameters().size());
        assertEquals("StockEvent", handler.parameters().getFirst().type().name());
        final Decl.ClassDecl type = (Decl.ClassDecl) result.unit().type("C");
        assertEquals("StockHandler", ((Decl.EventDecl) type.members().getFirst()).type().name());
    }

    @Test
    void parse_readsAnInterfaceAsSignaturesWithoutBodies() {
        final CannonFrontEnd.Result result = parse("interface IScript { void OnInit(); void OnTick(); }");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final Decl.InterfaceDecl type = (Decl.InterfaceDecl) result.unit().type("IScript");
        assertEquals(2, type.methods().size());
        assertNull(type.methods().getFirst().body());
    }

    @Test
    void parse_readsAnEnumWithAndWithoutExplicitValues() {
        final CannonFrontEnd.Result result = parse("enum LogLevel { INFO, WARN = 2, ERROR }");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final Decl.EnumDecl type = (Decl.EnumDecl) result.unit().type("LogLevel");
        assertEquals(3, type.constants().size());
        assertNull(type.constants().getFirst().value());
        assertNotNull(type.constants().get(1).value());
    }

    @Test
    void parse_bindsOperatorsByPrecedence() {
        final List<Stmt> statements = body("int x = 1 + 2 * 3;");
        final Expr.Binary sum = (Expr.Binary) ((Stmt.LocalDecl) statements.getFirst()).initializer();
        assertEquals(Operator.ADD, sum.operator());
        assertEquals(Operator.MULTIPLY, ((Expr.Binary) sum.right()).operator());
    }

    @Test
    void parse_bindsAssignmentFromTheRight() {
        final List<Stmt> statements = body("int a = 0; int b = 0; int c = 0; a = b = c;");
        final Expr.Assign assign = (Expr.Assign) ((Stmt.ExprStmt) statements.get(3)).expression();
        assertEquals("a", ((Expr.Name) assign.target()).identifier());
        assertEquals("b", ((Expr.Name) ((Expr.Assign) assign.value()).target()).identifier());
    }

    @Test
    void parse_tellsALocalDeclarationFromAnExpression() {
        final List<Stmt> statements = body("Network n = Network.Current; n.Query(\"minecraft:diamond\");");
        assertInstanceOf(Stmt.LocalDecl.class, statements.get(0));
        assertEquals("Network", ((Stmt.LocalDecl) statements.get(0)).type().name());
        assertInstanceOf(Expr.Call.class, ((Stmt.ExprStmt) statements.get(1)).expression());
    }

    @Test
    void parse_tellsACastFromAValueInBrackets() {
        final List<Stmt> statements = body("int a = 1; double b = (double) a; int c = (a) + a;");
        assertInstanceOf(Expr.Cast.class, ((Stmt.LocalDecl) statements.get(1)).initializer());
        assertInstanceOf(Expr.Binary.class, ((Stmt.LocalDecl) statements.get(2)).initializer());
    }

    @Test
    void parse_readsBothLambdaForms() {
        final List<Stmt> statements = body("""
                Network.Watch("minecraft:diamond", (e) => e.Total);
                Network.Watch("minecraft:iron", (StockEvent e) => { Console.PrintLine("hit"); });
                Time.Every(20, () => Console.PrintLine("tick"));
                """);
        final Expr.Lambda inline = lambdaArgument(statements.get(0), 1);
        assertEquals(1, inline.parameters().size());
        assertNull(inline.parameters().getFirst().type());
        assertNotNull(inline.body());
        assertNull(inline.block());

        final Expr.Lambda braced = lambdaArgument(statements.get(1), 1);
        assertEquals("StockEvent", braced.parameters().getFirst().type().name());
        assertNotNull(braced.block());
        assertNull(braced.body());

        assertTrue(lambdaArgument(statements.get(2), 1).parameters().isEmpty());
    }

    @Test
    void parse_splitsTheClosingAnglesOfANestedGeneric() {
        final List<Stmt> statements = body("Map<string, List<int>> m = new Map<string, List<int>>();");
        final Stmt.LocalDecl local = (Stmt.LocalDecl) statements.getFirst();
        assertEquals("Map", local.type().name());
        assertEquals(2, local.type().arguments().size());
        assertEquals("List<int>", local.type().arguments().get(1).describe());
        assertInstanceOf(Expr.New.class, local.initializer());
    }

    @Test
    void parse_readsAnArrayTypeItsAllocationAndItsElements() {
        final List<Stmt> statements = body("int[] slots = new int[8]; slots[0] = 1;");
        final Stmt.LocalDecl local = (Stmt.LocalDecl) statements.getFirst();
        assertEquals(1, local.type().arrayRank());
        assertInstanceOf(Expr.NewArray.class, local.initializer());
        assertInstanceOf(Expr.Index.class, ((Expr.Assign) ((Stmt.ExprStmt) statements.get(1)).expression()).target());
    }

    @Test
    void parse_readsEveryLoopForm() {
        final List<Stmt> statements = body("""
                for (int i = 0; i < 8; i++) { }
                while (true) { break; }
                do { continue; } while (false);
                foreach (string id in Network.Types()) { }
                """);
        assertInstanceOf(Stmt.For.class, statements.get(0));
        assertInstanceOf(Stmt.While.class, statements.get(1));
        assertInstanceOf(Stmt.DoWhile.class, statements.get(2));
        final Stmt.ForEach each = (Stmt.ForEach) statements.get(3);
        assertEquals("string", each.type().name());
        assertEquals("id", each.name());
    }

    @Test
    void parse_groupsSwitchLabelsThatShareTheirStatements() {
        final List<Stmt> statements = body("""
                int n = 1;
                switch (n) {
                    case 1:
                    case 2:
                        n = 0;
                        break;
                    default:
                        break;
                }
                """);
        final Stmt.Switch choice = (Stmt.Switch) statements.get(1);
        assertEquals(2, choice.sections().size());
        assertEquals(2, choice.sections().getFirst().labels().size());
        assertEquals(2, choice.sections().getFirst().statements().size());
        assertTrue(choice.sections().get(1).fallback());
    }

    @Test
    void parse_readsDisposeAsAStatementOfItsOwn() {
        final List<Stmt> statements = body("Network n = Network.Current; dispose n;");
        final Stmt.Dispose dispose = (Stmt.Dispose) statements.get(1);
        assertEquals("n", ((Expr.Name) dispose.target()).identifier());
    }

    @Test
    void parse_readsAConstructorThatChainsToItsBase() {
        final CannonFrontEnd.Result result = parse("class C { public C(int a) : base(a) { } }");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final Decl.ClassDecl type = (Decl.ClassDecl) result.unit().type("C");
        final Decl.ConstructorDecl constructor = (Decl.ConstructorDecl) type.members().getFirst();
        assertEquals("C", constructor.name());
        assertTrue(constructor.chained().base());
        assertEquals(1, constructor.chained().arguments().size());
    }

    @Test
    void parse_readsTheTypeQuestionsAndTheConditional() {
        final List<Stmt> statements = body("object o = null; bool b = o is string; string s = o as string;"
                + " int n = b ? 1 : 2;");
        assertFalse(((Expr.TypeTest) ((Stmt.LocalDecl) statements.get(1)).initializer()).conversion());
        assertTrue(((Expr.TypeTest) ((Stmt.LocalDecl) statements.get(2)).initializer()).conversion());
        assertInstanceOf(Expr.Conditional.class, ((Stmt.LocalDecl) statements.get(3)).initializer());
    }

    @Test
    void parse_rejectsAValueUsedAsAStatement() {
        final CannonFrontEnd.Result result = parse("class C { void M() { 1 + 2; } }");
        assertFalse(result.ok());
        assertTrue(codes(result).contains("C2006"), () -> String.join("\n", result.lines()));
    }

    @Test
    void parse_rejectsAnAssignmentToSomethingThatIsNotAPlace() {
        final CannonFrontEnd.Result result = parse("class C { void M() { M() = 1; } }");
        assertFalse(result.ok());
        assertTrue(codes(result).contains("C2008"), () -> String.join("\n", result.lines()));
    }

    @Test
    void parse_namesTheLineAndColumnOfAMissingSemicolon() {
        final CannonFrontEnd.Result result = parse("class C {\n    void M() {\n        int x = 1\n    }\n}");
        assertFalse(result.ok());
        final Diagnostic first = result.diagnostics().getFirst();
        assertEquals("C2001", first.code());
        assertEquals(4, first.line());
        assertEquals(5, first.column());
        assertEquals("Test.can(4,5): error C2001: expected ';' but found '}'", first.format());
    }

    @Test
    void parse_keepsReadingTheMembersAfterABadOne() {
        final CannonFrontEnd.Result result = parse("class C { int ; int Good() { return 1; } }");
        assertFalse(result.ok());
        final Decl.ClassDecl type = (Decl.ClassDecl) result.unit().type("C");
        assertTrue(type.members().stream()
                .anyMatch(member -> member instanceof Decl.MethodDecl && "Good".equals(member.name())));
    }

    @Test
    void parse_keepsReadingTheTypesAfterABadOne() {
        final CannonFrontEnd.Result result = parse("nonsense here; class C { }");
        assertFalse(result.ok());
        assertNotNull(result.unit().type("C"));
    }

    @Test
    void parse_stopsOnAFileOfNonsenseInsteadOfSpinning() {
        final CannonFrontEnd.Result result = parse("}}} ]] ;;; class ((");
        assertFalse(result.ok());
        assertNotNull(result.unit());
    }

    private static Expr.Lambda lambdaArgument(final Stmt statement, final int index) {
        final Expr.Call call = (Expr.Call) ((Stmt.ExprStmt) statement).expression();
        return (Expr.Lambda) call.arguments().get(index);
    }
}
