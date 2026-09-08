/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TypeCheckTest {

    private static CannonSemantics.Result check(final String source) {
        return CannonSemantics.check(List.of(new SourceFile("Test.can", source)));
    }

    /** Checks the statements as the body of a method of a class with nothing else in it. */
    private static CannonSemantics.Result body(final String statements) {
        return check("class C {\n    void M() {\n" + statements + "\n    }\n}");
    }

    private static void assertClean(final CannonSemantics.Result result) {
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
    }

    private static void assertReports(final String code, final CannonSemantics.Result result) {
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals(code)),
                () -> code + " was not among " + String.join("\n", result.lines()));
    }

    @Test
    void check_widensANumberAlongTheChainAndNeverBack() {
        assertClean(body("int a = 1; long b = a; float c = b; double d = c; char e = 'x'; int f = e;"));
        assertReports("C3003", body("double d = 1.5; int n = d;"));
        assertReports("C3003", body("long l = 1; int n = l;"));
    }

    @Test
    void check_refusesAValueOfTheWrongKindEntirely() {
        assertReports("C3003", body("int n = \"text\";"));
        assertReports("C3003", body("bool b = 1;"));
    }

    @Test
    void check_letsNullStandForAReferenceAndNothingElse() {
        assertClean(body("string s = null; object o = null;"));
        assertReports("C3003", body("int n = null;"));
    }

    @Test
    void check_reportsANameThatStandsForNothing() {
        assertReports("C3001", body("int n = nowhere;"));
    }

    @Test
    void check_reportsANameDeclaredTwiceInSightOfItself() {
        assertReports("C3002", body("int n = 1; string n = \"x\";"));
        assertReports("C3002", body("int n = 1; { int n = 2; }"));
    }

    @Test
    void check_letsTwoBlocksThatDoNotSeeEachOtherUseOneName() {
        assertClean(body("{ int n = 1; } { int n = 2; }"));
    }

    @Test
    void check_givesAnOperatorItsResultType() {
        assertClean(body("int a = 1 + 2; double b = 1 + 2.5; string c = \"n: \" + 4; bool d = 1 < 2;"
                + " bool e = d && !d; int f = 6 % 4; int g = 1 << 2;"));
        assertReports("C3007", body("bool b = true; int n = b + 1;"));
        assertReports("C3008", body("string s = \"x\"; bool b = !s;"));
        assertReports("C3007", body("double d = 1.5; int n = d << 1;"));
    }

    @Test
    void check_wantsABoolWhereAConditionGoes() {
        assertClean(body("if (1 < 2) { } while (false) { } for (int i = 0; i < 2; i++) { }"));
        assertReports("C3009", body("if (1) { }"));
        assertReports("C3009", body("int n = 0; while (n) { }"));
    }

    @Test
    void check_infersTheTypeOfVar() {
        assertClean(body("var n = 1; int m = n; var s = \"x\"; string t = s;"));
        assertReports("C3003", body("var n = 1; string s = n;"));
        assertReports("C3003", body("var n = null;"));
    }

    @Test
    void check_walksAnArrayAndAListAndNothingElse() {
        assertClean(body("int[] slots = new int[4]; foreach (int slot in slots) { }"));
        assertClean(body("List<string> names = new List<string>(); foreach (var name in names) { }"));
        assertReports("C3023", body("int n = 1; foreach (int x in n) { }"));
    }

    @Test
    void check_indexesAnArrayAListAndAMap() {
        assertClean(body("""
                int[] slots = new int[4];
                slots[0] = 7;
                List<string> names = new List<string>();
                string first = names[0];
                Map<string, int> counts = new Map<string, int>();
                int one = counts["a"];
                """));
        assertReports("C3024", body("int n = 1; int m = n[0];"));
        assertReports("C3024", body("List<string> names = new List<string>(); string s = names[\"a\"];"));
    }

    @Test
    void check_fillsInWhatACollectionHolds() {
        assertClean(body("""
                List<string> names = new List<string>();
                names.Add("first");
                string found = names.Get(0);
                int count = names.Count;
                Map<string, int> counts = new Map<string, int>();
                counts.Put("a", 1);
                List<string> keys = counts.Keys();
                """));
        assertReports("C3003", body("List<string> names = new List<string>(); names.Add(1);"));
        assertReports("C3003", body("List<string> names = new List<string>(); int n = names.Get(0);"));
    }

    @Test
    void check_picksTheVersionOfAMethodThatFits() {
        assertClean(body("int a = Math.Abs(0 - 3); double b = Math.Abs(1.5); double c = Math.Max(1, 2.0);"));
        assertReports("C3005", body("int n = Math.Abs(\"x\");"));
    }

    @Test
    void check_reportsACallThatFitsTwoVersionsEqually() {
        assertReports("C3006", check("""
                class C {
                    void F(int a, double b) { }
                    void F(double a, int b) { }
                    void M() { F(1, 1); }
                }
                """));
    }

    @Test
    void check_keepsStaticAndInstanceApart() {
        assertReports("C3020", check("class C { static int F() { return 1; } "
                + "void M() { C c = new C(); int n = c.F(); } }"));
        assertReports("C3021", check("class C { int f = 1; void M() { int n = C.f; } }"));
        assertReports("C3014", check("class C { int f = 1; static void M() { int n = f; } }"));
    }

    @Test
    void check_refusesToWriteWhatIsReadOnly() {
        assertReports("C3016", check("class C { readonly int f = 1; void M() { f = 2; } }"));
        assertClean(check("class C { readonly int f; public C() { f = 2; } }"));
        assertReports("C3016", check("class C { public int Count { get; } void M() { Count = 1; } }"));
        assertClean(check("class C { public int Count { get; set; } void M() { Count = 1; } }"));
    }

    @Test
    void check_wantsAValueBackOnEveryWayOutOfAMethod() {
        assertClean(check("class C { int F() { return 1; } }"));
        assertClean(check("class C { int F(bool b) { if (b) { return 1; } else { return 2; } } }"));
        assertClean(check("class C { int F() { while (true) { } } }"));
        assertReports("C3012", check("class C { int F(bool b) { if (b) { return 1; } } }"));
        assertReports("C3012", check("class C { int F() { return; } }"));
        assertReports("C3013", check("class C { void F() { return 1; } }"));
    }

    @Test
    void check_allowsBreakAndContinueOnlyWhereTheyMeanSomething() {
        assertClean(body("while (true) { break; } for (int i = 0; i < 2; i++) { continue; }"));
        assertReports("C3010", body("break;"));
        assertReports("C3011", body("continue;"));
        assertClean(body("int n = 1; switch (n) { case 1: break; }"));
        assertReports("C3011", body("int n = 1; switch (n) { case 1: continue; }"));
    }

    @Test
    void check_wantsSwitchLabelsThatFitTheValueAndDoNotRepeat() {
        assertClean(body("string s = \"a\"; switch (s) { case \"a\": break; case \"b\": break; }"));
        assertReports("C3003", body("int n = 1; switch (n) { case \"a\": break; }"));
        assertReports("C3029", body("int n = 1; switch (n) { case 1: break; case 1: break; }"));
    }

    @Test
    void check_disposesAnObjectAndRefusesANumber() {
        assertClean(body("string s = \"x\"; dispose s;"));
        assertReports("C3022", body("int n = 1; dispose n;"));
    }

    @Test
    void check_readsTheTypeQuestionsOnReferencesOnly() {
        assertClean(body("object o = null; bool b = o is string; string s = o as string;"));
        assertReports("C3008", body("int n = 1; bool b = n is string;"));
        assertReports("C3008", body("object o = null; int n = o as int;"));
    }

    @Test
    void check_hooksALambdaToTheDelegateItIsHandedTo() {
        assertClean(check("""
                delegate void Handler(int value);
                class C {
                    void Use(Handler h) { }
                    void M() {
                        Use((v) => { int doubled = v + v; });
                        Use((int v) => { });
                    }
                }
                """));
        assertReports("C3027", check("""
                delegate void Handler(int value);
                class C {
                    void Use(Handler h) { }
                    void M() { Use((a, b) => { }); }
                }
                """));
        assertReports("C3003", check("""
                delegate void Handler(int value);
                class C {
                    void Use(Handler h) { }
                    void M() { Use((string v) => { }); }
                }
                """));
    }

    @Test
    void check_wantsALambdaToGiveBackWhatItsDelegateAsksFor() {
        assertClean(check("""
                delegate int Count(string text);
                class C {
                    void Use(Count c) { }
                    void M() { Use((text) => text.Length); }
                }
                """));
        assertReports("C3003", check("""
                delegate int Count(string text);
                class C {
                    void Use(Count c) { }
                    void M() { Use((text) => text); }
                }
                """));
    }

    @Test
    void check_handsAMethodOverWhereADelegateOfItsShapeIsWanted() {
        assertClean(check("""
                delegate void Handler(int value);
                class C {
                    void OnValue(int value) { }
                    void Use(Handler h) { }
                    void M() { Use(OnValue); }
                }
                """));
        assertReports("C3027", check("""
                delegate void Handler(int value);
                class C {
                    void OnValue(string value) { }
                    void Use(Handler h) { }
                    void M() { Use(OnValue); }
                }
                """));
        assertReports("C3033", check("class C { void F() { } void M() { object o = F; } }"));
    }

    @Test
    void check_subscribesToAnEventAndRaisesItOnlyAtHome() {
        assertClean(check("""
                delegate void Handler(int value);
                class Source {
                    public event Handler Changed;
                    void Raise() { Changed(1); }
                    void Listen() { Changed += OnValue; Changed -= OnValue; }
                    void OnValue(int value) { }
                }
                """));
        assertReports("C3030", check("""
                delegate void Handler(int value);
                class Source { public event Handler Changed; }
                class Other {
                    void M() { Source s = new Source(); s.Changed(1); }
                }
                """));
        assertReports("C3003", check("""
                delegate void Handler(int value);
                class Source {
                    public event Handler Changed;
                    void Listen() { Changed += 1; }
                }
                """));
    }

    @Test
    void check_callsWhatALocalDelegateHolds() {
        assertClean(check("""
                delegate int Count(string text);
                class C {
                    void M(Count counter) { int n = counter("abc"); }
                }
                """));
        assertReports("C3025", body("int n = 1; n();"));
    }

    @Test
    void check_letsAClassCallTheMethodItsInterfaceAlsoDeclares() {
        assertClean(check("""
                class Monitor : IScript {
                    public void OnInit() { }
                    public void OnTick() { OnInit(); }
                    public void OnDestroy() { }
                }
                """));
    }

    @Test
    void check_wantsAnInterfaceMethodToGiveBackWhatItSaidItWould() {
        assertReports("C3018", check("""
                class Monitor : IScript {
                    public int OnInit() { return 1; }
                    public void OnTick() { }
                    public void OnDestroy() { }
                }
                """));
    }

    @Test
    void check_reachesAMemberThroughABaseClass() {
        assertClean(check("""
                class Base { public int shared = 1; public int Twice() { return shared + shared; } }
                class Below : Base { void M() { int n = Twice() + shared; } }
                """));
        assertReports("C3001", check("class Base { }\nclass Below : Base { void M() { int n = missing; } }"));
        assertReports("C3004", check("class Base { }\n"
                + "class Below : Base { void M() { Base b = new Base(); int n = b.missing; } }"));
    }

    @Test
    void check_callsAConstructorAndTheOneItChainsTo() {
        assertClean(check("""
                class Base { public Base(int a) { } }
                class Below : Base {
                    public Below() : base(1) { }
                    public Below(int a) : this() { }
                }
                """));
        assertReports("C3003", check("class C { public C(int a) { } void M() { C c = new C(\"x\"); } }"));
        assertReports("C3005", check("class C { public C(int a) { } void M() { C c = new C(1, 2); } }"));
        assertReports("C3015", check("class C { public C() : base() { } }"));
    }

    @Test
    void check_refusesToMakeSomethingThatIsNotAClass() {
        assertReports("C3026", check("interface IThing { }\nclass C { void M() { IThing t = new IThing(); } }"));
        assertReports("C3026", check("enum Colour { RED }\nclass C { void M() { Colour c = new Colour(); } }"));
    }

    @Test
    void check_readsAnEnumValueThroughItsType() {
        assertClean(check("""
                enum LogLevel { INFO, WARN }
                class C { void M() { LogLevel level = LogLevel.WARN; } }
                """));
        assertReports("C3004", check("""
                enum LogLevel { INFO }
                class C { void M() { LogLevel level = LogLevel.NOPE; } }
                """));
    }

    @Test
    void check_fillsInAnOutParameterAndPassesItThreeWays() {
        assertClean(check("""
                class C {
                    bool Find(string key, out int value) {
                        value = 0;
                        return false;
                    }

                    void M() {
                        int a;
                        Find("a", out a);
                        if (Find("b", out int b)) { int copy = b; }
                        if (Find("c", out var c)) { int copy = c; }
                    }
                }
                """));
    }

    @Test
    void check_wantsAnOutParameterGivenAValueOnEveryWayOut() {
        assertReports("C3037", check("class C { bool Find(out int value) { return false; } }"));
        assertReports("C3037", check("""
                class C {
                    bool Find(bool ok, out int value) {
                        if (ok) { value = 1; return true; }
                        return false;
                    }
                }
                """));
        assertClean(check("""
                class C {
                    bool Find(bool ok, out int value) {
                        if (ok) { value = 1; return true; }
                        value = 0;
                        return false;
                    }
                }
                """));
    }

    @Test
    void check_countsPassingItOnAsGivingItAValue() {
        assertClean(check("""
                class C {
                    Map<string, int> counts = new Map<string, int>();
                    bool Find(string key, out int value) { return counts.TryGet(key, out value); }
                }
                """));
    }

    @Test
    void check_wantsOutWrittenAtTheCallAndNowhereElse() {
        assertReports("C3034", check("""
                class C {
                    bool Find(out int value) { value = 0; return true; }
                    void M() { int n = 0; Find(n); }
                }
                """));
        assertReports("C3035", check("""
                class C {
                    void Take(int value) { }
                    void M() { int n = 0; Take(out n); }
                }
                """));
    }

    @Test
    void check_wantsAnOutArgumentToBeExactlyTheTypeAsked() {
        assertReports("C3036", check("""
                class C {
                    bool Find(out int value) { value = 0; return true; }
                    void M() { long n = 0; Find(out n); }
                }
                """));
    }

    @Test
    void check_writesIntoAFieldButNotAReadOnlyOne() {
        assertClean(check("""
                class C {
                    int cached = 0;
                    bool Find(out int value) { value = 1; return true; }
                    void M() { Find(out cached); }
                }
                """));
        assertReports("C3038", check("""
                class C {
                    readonly int cached = 0;
                    bool Find(out int value) { value = 1; return true; }
                    void M() { Find(out cached); }
                }
                """));
    }

    @Test
    void check_readsTheTryFormsOfTheLibrary() {
        assertClean(body("""
                Map<string, int> counts = new Map<string, int>();
                if (counts.TryGet("iron", out int found)) { int copy = found; }
                if (Convert.TryInt("42", out int parsed)) { int copy = parsed; }
                """));
        assertReports("C3036", body("""
                Map<string, int> counts = new Map<string, int>();
                counts.TryGet("iron", out string wrong);
                """));
    }

    @Test
    void check_hooksALambdaToADelegateThatFillsSomethingIn() {
        assertClean(check("""
                delegate bool Finder(string key, out int value);
                class C {
                    void Use(Finder f) { }
                    void M() { Use((key, out value) => { value = 0; return true; }); }
                }
                """));
        assertReports("C3034", check("""
                delegate bool Finder(string key, out int value);
                class C {
                    void Use(Finder f) { }
                    void M() { Use((key, value) => { return true; }); }
                }
                """));
    }

    @Test
    void check_recordsTheTypeOfEveryExpressionItChecked() {
        final CannonSemantics.Result result = check("class C { void M() { int n = 1 + 2; } }");
        assertClean(result);
        assertEquals(1, result.model().declaredTypes().size());
    }
}
