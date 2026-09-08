/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computers.cannon.ast.IDecl;
import dev.jstech.computers.cannon.ast.IExpr;
import dev.jstech.computers.cannon.ast.Operator;
import dev.jstech.computers.cannon.ast.IStmt;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ParserTest {

    private static CannonFrontEnd.Result parse(final String source) {
        return CannonFrontEnd.parse(new SourceFile("Test.can", source));
    }

    /** Parses a method body and hands back its statements, asserting nothing went wrong on the way. */
    private static List<IStmt> body(final String statements) {
        final CannonFrontEnd.Result result = parse("class C { void M() { " + statements + " } }");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final IDecl.ClassDecl type = (IDecl.ClassDecl) result.unit().type("C");
        final IDecl.MethodDecl method = (IDecl.MethodDecl) type.members().getFirst();
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
        final IDecl.ClassDecl type = (IDecl.ClassDecl) result.unit().type("Monitor");
        assertEquals(List.of("IScript"), type.bases().stream().map(base -> base.name()).toList());
        assertEquals(6, type.members().size());
        assertInstanceOf(IDecl.FieldDecl.class, type.members().get(0));
        assertInstanceOf(IDecl.FieldDecl.class, type.members().get(1));
        assertInstanceOf(IDecl.PropertyDecl.class, type.members().get(2));
        assertInstanceOf(IDecl.EventDecl.class, type.members().get(3));
        assertInstanceOf(IDecl.ConstructorDecl.class, type.members().get(4));
        assertInstanceOf(IDecl.MethodDecl.class, type.members().get(5));
    }

    @Test
    void parse_keepsTheAccessWrittenOnEachHalfOfAProperty() {
        final CannonFrontEnd.Result result = parse("class C { public int Count { get; private set; } }");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final IDecl.ClassDecl type = (IDecl.ClassDecl) result.unit().type("C");
        final IDecl.PropertyDecl property = (IDecl.PropertyDecl) type.members().getFirst();
        assertEquals(Set.of(IDecl.Modifier.PUBLIC), property.modifiers());
        assertNotNull(property.getter());
        assertTrue(property.getter().modifiers().isEmpty());
        assertEquals(Set.of(IDecl.Modifier.PRIVATE), property.setter().modifiers());
    }

    @Test
    void parse_readsADelegateBesideTheEventThatUsesIt() {
        final CannonFrontEnd.Result result = parse("""
                delegate void StockHandler(StockEvent e);
                class C { public event StockHandler Changed; }
                """);
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final IDecl.DelegateDecl handler = (IDecl.DelegateDecl) result.unit().type("StockHandler");
        assertEquals("void", handler.returnType().name());
        assertEquals(1, handler.parameters().size());
        assertEquals("StockEvent", handler.parameters().getFirst().type().name());
        final IDecl.ClassDecl type = (IDecl.ClassDecl) result.unit().type("C");
        assertEquals("StockHandler", ((IDecl.EventDecl) type.members().getFirst()).type().name());
    }

    @Test
    void parse_readsAnInterfaceAsSignaturesWithoutBodies() {
        final CannonFrontEnd.Result result = parse("interface IScript { void OnInit(); void OnTick(); }");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final IDecl.InterfaceDecl type = (IDecl.InterfaceDecl) result.unit().type("IScript");
        assertEquals(2, type.methods().size());
        assertNull(type.methods().getFirst().body());
    }

    @Test
    void parse_readsAnEnumWithAndWithoutExplicitValues() {
        final CannonFrontEnd.Result result = parse("enum LogLevel { INFO, WARN = 2, ERROR }");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final IDecl.EnumDecl type = (IDecl.EnumDecl) result.unit().type("LogLevel");
        assertEquals(3, type.constants().size());
        assertNull(type.constants().getFirst().value());
        assertNotNull(type.constants().get(1).value());
    }

    @Test
    void parse_bindsOperatorsByPrecedence() {
        final List<IStmt> statements = body("int x = 1 + 2 * 3;");
        final IExpr.Binary sum = (IExpr.Binary) ((IStmt.LocalDecl) statements.getFirst()).initializer();
        assertEquals(Operator.ADD, sum.operator());
        assertEquals(Operator.MULTIPLY, ((IExpr.Binary) sum.right()).operator());
    }

    @Test
    void parse_bindsAssignmentFromTheRight() {
        final List<IStmt> statements = body("int a = 0; int b = 0; int c = 0; a = b = c;");
        final IExpr.Assign assign = (IExpr.Assign) ((IStmt.ExprStmt) statements.get(3)).expression();
        assertEquals("a", ((IExpr.Name) assign.target()).identifier());
        assertEquals("b", ((IExpr.Name) ((IExpr.Assign) assign.value()).target()).identifier());
    }

    @Test
    void parse_tellsALocalDeclarationFromAnExpression() {
        final List<IStmt> statements = body("Network n = Network.Current; n.Query(\"minecraft:diamond\");");
        assertInstanceOf(IStmt.LocalDecl.class, statements.get(0));
        assertEquals("Network", ((IStmt.LocalDecl) statements.get(0)).type().name());
        assertInstanceOf(IExpr.Call.class, ((IStmt.ExprStmt) statements.get(1)).expression());
    }

    @Test
    void parse_tellsACastFromAValueInBrackets() {
        final List<IStmt> statements = body("int a = 1; double b = (double) a; int c = (a) + a;");
        assertInstanceOf(IExpr.Cast.class, ((IStmt.LocalDecl) statements.get(1)).initializer());
        assertInstanceOf(IExpr.Binary.class, ((IStmt.LocalDecl) statements.get(2)).initializer());
    }

    @Test
    void parse_readsBothLambdaForms() {
        final List<IStmt> statements = body("""
                Network.Watch("minecraft:diamond", (e) => e.Total);
                Network.Watch("minecraft:iron", (StockEvent e) => { Console.PrintLine("hit"); });
                Time.Every(20, () => Console.PrintLine("tick"));
                """);
        final IExpr.Lambda inline = lambdaArgument(statements.get(0), 1);
        assertEquals(1, inline.parameters().size());
        assertNull(inline.parameters().getFirst().type());
        assertNotNull(inline.body());
        assertNull(inline.block());

        final IExpr.Lambda braced = lambdaArgument(statements.get(1), 1);
        assertEquals("StockEvent", braced.parameters().getFirst().type().name());
        assertNotNull(braced.block());
        assertNull(braced.body());

        assertTrue(lambdaArgument(statements.get(2), 1).parameters().isEmpty());
    }

    @Test
    void parse_readsAnOutParameterAndTheThreeWaysToPassOne() {
        final CannonFrontEnd.Result result = parse("""
                class C {
                    bool F(string key, out int value) { value = 0; return true; }
                    void M() {
                        int a;
                        F("a", out a);
                        F("b", out int b);
                        F("c", out var c);
                    }
                }
                """);
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final IDecl.ClassDecl type = (IDecl.ClassDecl) result.unit().type("C");
        final IDecl.MethodDecl find = (IDecl.MethodDecl) type.members().getFirst();
        assertFalse(find.parameters().getFirst().outward());
        assertTrue(find.parameters().get(1).outward());

        final List<IStmt> statements = ((IDecl.MethodDecl) type.members().get(1)).body().statements();
        assertNull(outArgument(statements.get(1)).type());
        assertEquals("int", outArgument(statements.get(2)).type().name());
        assertEquals("var", outArgument(statements.get(3)).type().name());
        assertEquals("c", outArgument(statements.get(3)).name());
    }

    private static IExpr.OutArgument outArgument(final IStmt statement) {
        final IExpr.Call call = (IExpr.Call) ((IStmt.ExprStmt) statement).expression();
        return (IExpr.OutArgument) call.arguments().get(1);
    }

    @Test
    void parse_splitsTheClosingAnglesOfANestedGeneric() {
        final List<IStmt> statements = body("Map<string, List<int>> m = new Map<string, List<int>>();");
        final IStmt.LocalDecl local = (IStmt.LocalDecl) statements.getFirst();
        assertEquals("Map", local.type().name());
        assertEquals(2, local.type().arguments().size());
        assertEquals("List<int>", local.type().arguments().get(1).describe());
        assertInstanceOf(IExpr.New.class, local.initializer());
    }

    @Test
    void parse_readsAnArrayTypeItsAllocationAndItsElements() {
        final List<IStmt> statements = body("int[] slots = new int[8]; slots[0] = 1;");
        final IStmt.LocalDecl local = (IStmt.LocalDecl) statements.getFirst();
        assertEquals(1, local.type().arrayRank());
        assertInstanceOf(IExpr.NewArray.class, local.initializer());
        assertInstanceOf(IExpr.Index.class, ((IExpr.Assign) ((IStmt.ExprStmt) statements.get(1)).expression()).target());
    }

    @Test
    void parse_readsEveryLoopForm() {
        final List<IStmt> statements = body("""
                for (int i = 0; i < 8; i++) { }
                while (true) { break; }
                do { continue; } while (false);
                foreach (string id in Network.Types()) { }
                """);
        assertInstanceOf(IStmt.For.class, statements.get(0));
        assertInstanceOf(IStmt.While.class, statements.get(1));
        assertInstanceOf(IStmt.DoWhile.class, statements.get(2));
        final IStmt.ForEach each = (IStmt.ForEach) statements.get(3);
        assertEquals("string", each.type().name());
        assertEquals("id", each.name());
    }

    @Test
    void parse_groupsSwitchLabelsThatShareTheirStatements() {
        final List<IStmt> statements = body("""
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
        final IStmt.Switch choice = (IStmt.Switch) statements.get(1);
        assertEquals(2, choice.sections().size());
        assertEquals(2, choice.sections().getFirst().labels().size());
        assertEquals(2, choice.sections().getFirst().statements().size());
        assertTrue(choice.sections().get(1).fallback());
    }

    @Test
    void parse_readsDisposeAsAStatementOfItsOwn() {
        final List<IStmt> statements = body("Network n = Network.Current; dispose n;");
        final IStmt.Dispose dispose = (IStmt.Dispose) statements.get(1);
        assertEquals("n", ((IExpr.Name) dispose.target()).identifier());
    }

    @Test
    void parse_readsAConstructorThatChainsToItsBase() {
        final CannonFrontEnd.Result result = parse("class C { public C(int a) : base(a) { } }");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        final IDecl.ClassDecl type = (IDecl.ClassDecl) result.unit().type("C");
        final IDecl.ConstructorDecl constructor = (IDecl.ConstructorDecl) type.members().getFirst();
        assertEquals("C", constructor.name());
        assertTrue(constructor.chained().base());
        assertEquals(1, constructor.chained().arguments().size());
    }

    @Test
    void parse_readsTheTypeQuestionsAndTheConditional() {
        final List<IStmt> statements = body("object o = null; bool b = o is string; string s = o as string;"
                + " int n = b ? 1 : 2;");
        assertFalse(((IExpr.TypeTest) ((IStmt.LocalDecl) statements.get(1)).initializer()).conversion());
        assertTrue(((IExpr.TypeTest) ((IStmt.LocalDecl) statements.get(2)).initializer()).conversion());
        assertInstanceOf(IExpr.Conditional.class, ((IStmt.LocalDecl) statements.get(3)).initializer());
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
        final IDecl.ClassDecl type = (IDecl.ClassDecl) result.unit().type("C");
        assertTrue(type.members().stream()
                .anyMatch(member -> member instanceof IDecl.MethodDecl && "Good".equals(member.name())));
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

    private static IExpr.Lambda lambdaArgument(final IStmt statement, final int index) {
        final IExpr.Call call = (IExpr.Call) ((IStmt.ExprStmt) statement).expression();
        return (IExpr.Lambda) call.arguments().get(index);
    }
}
