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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computronics.cannon.asm.AsmMethod;
import dev.jstech.computronics.cannon.asm.AsmProgram;
import dev.jstech.computronics.cannon.asm.AsmReader;
import dev.jstech.computronics.cannon.asm.AsmType;
import dev.jstech.computronics.cannon.asm.AsmWriter;
import dev.jstech.computronics.cannon.asm.Instruction;
import dev.jstech.computronics.cannon.asm.Opcode;
import dev.jstech.computronics.cannon.asm.IOperand;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsmRoundTripTest {

    private DiagnosticBag bag;

    @BeforeEach
    void setUp() {
        this.bag = new DiagnosticBag("Monitor.asm");
    }

    private AsmProgram read(final String text) {
        return new AsmReader(text, this.bag).read();
    }

    private List<String> codes() {
        return this.bag.sorted().stream().map(Diagnostic::code).toList();
    }

    /** A program using every directive and a spread of operand shapes. */
    private static AsmProgram sample() {
        final AsmProgram program = new AsmProgram();
        program.setEntryPoint("Monitor", Shape.SCRIPT);

        final AsmType script = new AsmType(AsmType.Kind.INTERFACE, "IScript");
        script.addMethod(new AsmMethod("OnInit", "void", List.of(), false, 0, null));
        script.addMethod(new AsmMethod("OnTick", "void", List.of(), false, 0, null));
        program.addType(script);

        final AsmType level = new AsmType(AsmType.Kind.ENUM, "LogLevel");
        level.addValue(new AsmType.Value("INFO", 0));
        level.addValue(new AsmType.Value("WARN", 1));
        program.addType(level);

        final AsmType finder = new AsmType(AsmType.Kind.DELEGATE, "Finder");
        finder.setInvoke(new AsmMethod("Invoke", "bool", List.of("string", "out int"), false, 0, null));
        program.addType(finder);

        final AsmType monitor = new AsmType(AsmType.Kind.CLASS, "Monitor");
        monitor.addBase("IScript");
        monitor.addField(new AsmType.Field("threshold", "int", false));
        monitor.addField(new AsmType.Field("counts", "Map<string, int>", false));
        monitor.addField(new AsmType.Field("started", "bool", true));
        monitor.addEvent(new AsmType.Event("Changed", "Finder"));
        monitor.addMethod(new AsmMethod("OnInit", "void", List.of(), false, 0, List.of(
                Instruction.of(Opcode.RET))));
        monitor.addMethod(new AsmMethod("OnTick", "void", List.of(), false, 3, List.of(
                Instruction.of(Opcode.LDTHIS),
                Instruction.of(Opcode.LDFLD, new IOperand.Field(null, "counts")),
                Instruction.of(Opcode.LDSTR, new IOperand.Text("minecraft:diamond")),
                Instruction.of(Opcode.CALL, new IOperand.Method("Map", "TryGet",
                        List.of("string", "out int"), "bool")),
                Instruction.of(Opcode.STLOC, new IOperand.Slot(1)),
                Instruction.of(Opcode.BRFALSE, new IOperand.Label("L1")),
                Instruction.of(Opcode.LDLOC, new IOperand.Slot(1)),
                Instruction.of(Opcode.LDTHIS),
                Instruction.of(Opcode.LDFLD, new IOperand.Field(null, "threshold")),
                Instruction.of(Opcode.BGE, new IOperand.Label("L1")),
                Instruction.of(Opcode.LDC_I4, new IOperand.I4(1)).saying("LogLevel.WARN"),
                Instruction.of(Opcode.LDSTR, new IOperand.Text("low; and \"quoted\"")),
                Instruction.of(Opcode.CALL, new IOperand.Method("Mainframe", "Log",
                        List.of("int", "string"), "void")),
                Instruction.of(Opcode.LDC_I8, new IOperand.I8(9000000000L)),
                Instruction.of(Opcode.LDC_R4, new IOperand.R4(1.5f)),
                Instruction.of(Opcode.LDC_R8, new IOperand.R8(2.25)),
                Instruction.of(Opcode.CONV_R8),
                Instruction.of(Opcode.NEWOBJ, new IOperand.Constructor("Monitor", List.of("int"))),
                Instruction.of(Opcode.NEWARR, new IOperand.Type("int")),
                Instruction.of(Opcode.CASTCLASS, new IOperand.Type("List<string>")),
                Instruction.of(Opcode.LDSFLD, new IOperand.Field("LogLevel", "WARN")),
                Instruction.of(Opcode.SYS, new IOperand.Text("Network.Current")),
                Instruction.of(Opcode.POP),
                Instruction.of(Opcode.RET).labelled("L1"))));
        program.addType(monitor);
        return program;
    }

    /** A small program whose listing is short enough to write out line by line. */
    private static AsmProgram golden() {
        final AsmProgram program = new AsmProgram();
        program.setEntryPoint("Monitor", Shape.SCRIPT);
        final AsmType monitor = new AsmType(AsmType.Kind.CLASS, "Monitor");
        monitor.addBase("IScript");
        monitor.addField(new AsmType.Field("threshold", "int", false));
        monitor.addMethod(new AsmMethod("OnTick", "void", List.of(), false, 1, List.of(
                Instruction.of(Opcode.LDTHIS),
                Instruction.of(Opcode.LDFLD, new IOperand.Field(null, "threshold")),
                Instruction.of(Opcode.LDC_I4, new IOperand.I4(100)),
                Instruction.of(Opcode.BLT, new IOperand.Label("L1")),
                Instruction.of(Opcode.RET),
                Instruction.of(Opcode.LDC_I4, new IOperand.I4(1)).labelled("L1").saying("LogLevel.WARN"),
                Instruction.of(Opcode.LDSTR, new IOperand.Text("stock is low")),
                Instruction.of(Opcode.CALL, new IOperand.Method("Mainframe", "Log",
                        List.of("int", "string"), "void")),
                Instruction.of(Opcode.RET))));
        program.addType(monitor);
        return program;
    }

    @Test
    void write_laysTheListingOutInColumnsAPlayerCanFollow() {
        final String[] lines = AsmWriter.write(golden()).split("\n", -1);
        assertEquals(".asm 1", lines[0]);
        assertEquals(".start Monitor script", lines[1]);
        assertEquals("", lines[2]);
        assertEquals(".class Monitor : IScript", lines[3]);
        assertEquals(".field int threshold", lines[4]);
        assertEquals("", lines[5]);
        assertEquals(".method void OnTick() slots 1", lines[6]);
        assertEquals("    ldthis", lines[7]);
        assertEquals("    ldfld   threshold", lines[8]);
        assertEquals("    ldc.i4  100", lines[9]);
        assertEquals("    blt     L1", lines[10]);
        assertEquals("    ret", lines[11]);
        assertEquals("L1: ldc.i4  1" + " ".repeat(32) + "; LogLevel.WARN", lines[12]);
        assertEquals("    ldstr   \"stock is low\"", lines[13]);
        assertEquals("    call    Mainframe.Log(int, string) -> void", lines[14]);
        assertEquals("    ret", lines[15]);
    }

    @Test
    void write_thenRead_thenWrite_givesTheSameText() {
        final String once = AsmWriter.write(sample());
        final AsmProgram read = this.read(once);
        assertFalse(this.bag.hasErrors(), () -> String.join("\n", this.bag.sorted().stream()
                .map(Diagnostic::format).toList()));
        assertEquals(once, AsmWriter.write(read));
    }

    @Test
    void read_keepsEveryPartOfTheProgram() {
        final AsmProgram read = this.read(AsmWriter.write(sample()));
        assertEquals("Monitor", read.entryPoint());
        assertEquals(4, read.types().size());

        final AsmType monitor = read.type("Monitor");
        assertEquals(List.of("IScript"), monitor.bases());
        assertEquals("Map<string, int>", monitor.fields().get(1).type());
        assertTrue(monitor.fields().get(2).isStatic());
        assertEquals("Finder", monitor.events().getFirst().type());

        final AsmMethod tick = monitor.methods().get(1);
        assertEquals(3, tick.slots());
        assertTrue(tick.hasBody());
        assertEquals("L1", tick.body().getLast().label());
        assertEquals("LogLevel.WARN", tick.body().get(10).comment());
    }

    @Test
    void read_keepsWhatEachOperandHeld() {
        final AsmType monitor = this.read(AsmWriter.write(sample())).type("Monitor");
        final List<Instruction> body = monitor.methods().get(1).body();
        assertEquals(new IOperand.Text("minecraft:diamond"), body.get(2).operand());
        assertEquals(new IOperand.Method("Map", "TryGet", List.of("string", "out int"), "bool"),
                body.get(3).operand());
        assertEquals(new IOperand.Text("low; and \"quoted\""), body.get(11).operand());
        assertEquals(new IOperand.I8(9000000000L), body.get(13).operand());
        assertEquals(new IOperand.R4(1.5f), body.get(14).operand());
        assertEquals(new IOperand.R8(2.25), body.get(15).operand());
        assertEquals(new IOperand.Constructor("Monitor", List.of("int")), body.get(17).operand());
        assertEquals(new IOperand.Type("List<string>"), body.get(19).operand());
        assertEquals(new IOperand.Field("LogLevel", "WARN"), body.get(20).operand());
    }

    @Test
    void read_keepsASignatureWithNoBodyApartFromOneWithABody() {
        final AsmProgram read = this.read(AsmWriter.write(sample()));
        assertFalse(read.type("IScript").methods().getFirst().hasBody());
        assertNull(read.type("IScript").methods().getFirst().body());
        assertTrue(read.type("Monitor").methods().getFirst().hasBody());
    }

    @Test
    void read_keepsADelegateShapeAndAnEnumsNumbers() {
        final AsmProgram read = this.read(AsmWriter.write(sample()));
        final AsmMethod invoke = read.type("Finder").invoke();
        assertNotNull(invoke);
        assertEquals("bool", invoke.returns());
        assertEquals(List.of("string", "out int"), invoke.parameters());
        assertEquals(1, read.type("LogLevel").values().get(1).number());
    }

    @Test
    void write_thenRead_copesWithAProgramThatHoldsNothing() {
        final String text = AsmWriter.write(new AsmProgram());
        assertEquals(".asm 1\n", text);
        final AsmProgram read = this.read(text);
        assertFalse(this.bag.hasErrors());
        assertTrue(read.types().isEmpty());
        assertNull(read.entryPoint());
    }

    @Test
    void read_refusesAListingWithNoVersionLine() {
        this.read(".class C\n");
        assertEquals(List.of("C4001"), this.codes());
    }

    @Test
    void read_refusesAListingFromALaterVersion() {
        this.read(".asm 99\n.class C\n");
        assertEquals(List.of("C4002"), this.codes());
    }

    @Test
    void read_reportsALineItDoesNotKnow() {
        this.read(".asm 1\n.class C\n.method void M() slots 0\n    nonsense\n");
        assertEquals(List.of("C4003"), this.codes());
    }

    @Test
    void read_reportsAnInstructionGivenTheWrongThing() {
        this.read(".asm 1\n.class C\n.method void M() slots 0\n    ldc.i4\n    ret 7\n    br\n");
        assertEquals(List.of("C4004", "C4005", "C4004"), this.codes());
    }

    @Test
    void read_reportsANumberThatIsNotOne() {
        this.read(".asm 1\n.class C\n.method void M() slots 0\n    ldc.i4  nine\n");
        assertEquals(List.of("C4006"), this.codes());
    }

    @Test
    void read_reportsAnInstructionThatIsNotInAMethod() {
        this.read(".asm 1\n.class C\n    ret\n");
        assertEquals(List.of("C4008"), this.codes());
    }

    @Test
    void read_reportsADirectiveThatIsNotInAType() {
        this.read(".asm 1\n.field int x\n");
        assertEquals(List.of("C4009"), this.codes());
    }

    @Test
    void read_reportsABranchToALabelNothingCarries() {
        this.read(".asm 1\n.class C\n.method void M() slots 0\n    br      L9\n    ret\n");
        assertEquals(List.of("C4010"), this.codes());
        assertEquals(4, this.bag.sorted().getFirst().line());
    }

    @Test
    void read_ignoresBlankLinesAndLinesThatAreOnlyANote() {
        final AsmProgram read = this.read(".asm 1\n\n; a note of its own\n.class C\n"
                + ".method void M() slots 0\n\n    ret\n");
        assertFalse(this.bag.hasErrors());
        assertEquals(1, read.type("C").methods().getFirst().body().size());
    }
}
