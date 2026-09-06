/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.asm;

import dev.jstech.computronics.cannon.CannonError;
import dev.jstech.computronics.cannon.DiagnosticBag;
import dev.jstech.computronics.cannon.ast.Decl;
import dev.jstech.computronics.cannon.ast.Expr;
import dev.jstech.computronics.cannon.ast.Operator;
import dev.jstech.computronics.cannon.ast.Stmt;
import dev.jstech.computronics.cannon.sem.Binding;
import dev.jstech.computronics.cannon.sem.BodyChecker;
import dev.jstech.computronics.cannon.sem.BuiltIns;
import dev.jstech.computronics.cannon.sem.Declarations;
import dev.jstech.computronics.cannon.sem.MemberSymbol;
import dev.jstech.computronics.cannon.sem.NamedType;
import dev.jstech.computronics.cannon.sem.SemanticModel;
import dev.jstech.computronics.cannon.sem.TypeRules;
import dev.jstech.computronics.cannon.sem.TypeSymbol;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import dev.jstech.computronics.cannon.ast.Node;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a checked program into the assembly.
 *
 * <p>Everything it needs was worked out already: what each expression is, what each name stands for,
 * and which method each call resolved to. So this stage never decides anything about the language,
 * it only writes down what the earlier stages settled, which is why nothing here reports a mistake.
 *
 * <p>A few shapes of the language have no instruction of their own and are written out in terms of
 * ones that do. A property is a field. A short-circuit is a branch. An enum value is a number. And
 * the values a method fills in come back on the stack, so the caller stores them like anything else.
 */
public final class Emitter {

    /** What the runtime provides for joining and parting delegates, and for putting text together. */
    private static final String DELEGATE = "Delegate";
    private static final String STRING = "string";

    private final SemanticModel model;
    private final TypeRules rules;
    private final BuiltIns builtIns;
    private final Declarations declarations;
    private final DiagnosticBag diagnostics;
    private final List<AsmMethod> synthesized = new ArrayList<>();
    private final List<AsmType> closures = new ArrayList<>();
    private final Map<String, AsmType> closureTypes = new LinkedHashMap<>();
    private int lambdaCount;
    private int closureCount;

    public Emitter(final SemanticModel model, final TypeRules rules, final BuiltIns builtIns,
                   final Declarations declarations, final DiagnosticBag diagnostics) {
        this.model = model;
        this.rules = rules;
        this.builtIns = builtIns;
        this.declarations = declarations;
        this.diagnostics = diagnostics;
    }

    /** Writes the whole program out. */
    public AsmProgram emit() {
        final AsmProgram program = new AsmProgram();
        if (this.model.entryPoint() != null) {
            program.setEntryPoint(this.model.entryPoint().name());
        }
        for (final NamedType type : this.model.declaredTypes()) {
            final Decl.TypeDecl source = this.declarations.source(type);
            this.diagnostics.setFile(this.declarations.fileOf(type));
            switch (source) {
                case Decl.ClassDecl declaration -> program.addType(this.emitClass(type, declaration));
                case Decl.InterfaceDecl ignored -> program.addType(emitInterface(type));
                case Decl.EnumDecl declaration -> program.addType(emitEnum(type, declaration));
                case Decl.DelegateDecl declaration -> program.addType(this.emitDelegate(type, declaration));
                case null, default -> { }
            }
            for (final AsmType closure : this.closures) {
                program.addType(closure);
            }
            this.closures.clear();
            this.closureTypes.clear();
        }
        return program;
    }

    // ---------------------------------------------------------------- types

    private AsmType emitClass(final NamedType type, final Decl.ClassDecl declaration) {
        final AsmType written = new AsmType(AsmType.Kind.CLASS, type.name());
        if (type.base() != null) {
            written.addBase(type.base().name());
        }
        for (final NamedType face : type.interfaces()) {
            written.addBase(face.name());
        }
        final List<Decl.FieldDecl> instanceStart = new ArrayList<>();
        final List<Decl.FieldDecl> staticStart = new ArrayList<>();
        for (final Decl.MemberDecl member : declaration.members()) {
            switch (member) {
                case Decl.FieldDecl field -> {
                    written.addField(new AsmType.Field(field.name(),
                            this.declarations.resolve(field.type()).describe(),
                            field.modifiers().contains(Decl.Modifier.STATIC)));
                    if (field.initializer() != null) {
                        (field.modifiers().contains(Decl.Modifier.STATIC) ? staticStart : instanceStart)
                                .add(field);
                    }
                }
                case Decl.PropertyDecl property -> written.addField(new AsmType.Field(property.name(),
                        this.declarations.resolve(property.type()).describe(),
                        property.modifiers().contains(Decl.Modifier.STATIC)));
                case Decl.EventDecl event -> written.addEvent(new AsmType.Event(event.name(),
                        this.declarations.resolve(event.type()).describe()));
                default -> { }
            }
        }
        for (final Decl.MemberDecl member : declaration.members()) {
            if (member instanceof Decl.MethodDecl method && method.body() != null) {
                written.addMethod(this.emitMethod(type, method));
            } else if (member instanceof Decl.ConstructorDecl constructor) {
                written.addMethod(this.emitConstructor(type, constructor, instanceStart));
            }
        }
        this.addSetUp(type, written, declaration, instanceStart, staticStart);
        // A lambda is a method the player did not write a name for, so it becomes one here, on the
        // type it was written inside.
        for (final AsmMethod method : this.synthesized) {
            written.addMethod(method);
        }
        this.synthesized.clear();
        this.lambdaCount = 0;
        return written;
    }

    // A class with fields that start out holding something, and no constructor of its own, still has
    // to put those values there. A static one gets a method named after its type, which no source
    // method can be, because inside a class that name is a constructor.
    private void addSetUp(final NamedType type, final AsmType written, final Decl.ClassDecl declaration,
                          final List<Decl.FieldDecl> instanceStart, final List<Decl.FieldDecl> staticStart) {
        final boolean hasConstructor = declaration.members().stream()
                .anyMatch(member -> member instanceof Decl.ConstructorDecl);
        if (!hasConstructor && !instanceStart.isEmpty()) {
            final Body body = new Body(type, TypeSymbol.Primitive.VOID);
            body.fieldStarts(instanceStart);
            written.addMethod(new AsmMethod(type.name(), "void", List.of(), false,
                    body.slotCount(), body.finish()));
        }
        if (!staticStart.isEmpty()) {
            final Body body = new Body(type, TypeSymbol.Primitive.VOID);
            body.fieldStarts(staticStart);
            written.addMethod(new AsmMethod(type.name(), "void", List.of(), true,
                    body.slotCount(), body.finish()));
        }
    }

    private static AsmType emitInterface(final NamedType type) {
        final AsmType written = new AsmType(AsmType.Kind.INTERFACE, type.name());
        for (final NamedType face : type.interfaces()) {
            written.addBase(face.name());
        }
        for (final MemberSymbol member : type.members()) {
            if (member instanceof MemberSymbol.MethodSymbol method) {
                written.addMethod(new AsmMethod(method.name(), method.returnType().describe(),
                        writtenParameters(method), method.isStatic(), 0, null));
            }
        }
        return written;
    }

    private static AsmType emitEnum(final NamedType type, final Decl.EnumDecl declaration) {
        final AsmType written = new AsmType(AsmType.Kind.ENUM, type.name());
        int next = 0;
        for (final Decl.EnumConstant constant : declaration.constants()) {
            final Integer given = constant.value() == null ? null
                    : BodyChecker.numberOf(constant.value());
            final int number = given == null ? next : given;
            written.addValue(new AsmType.Value(constant.name(), number));
            next = number + 1;
        }
        return written;
    }

    private AsmType emitDelegate(final NamedType type, final Decl.DelegateDecl declaration) {
        final AsmType written = new AsmType(AsmType.Kind.DELEGATE, type.name());
        written.setInvoke(new AsmMethod("Invoke", this.declarations.resolve(declaration.returnType())
                .describe(), writtenParameters(type.invoke()), false, 0, null));
        return written;
    }

    private static List<String> writtenParameters(final MemberSymbol.MethodSymbol method) {
        final List<String> written = new ArrayList<>();
        if (method == null) {
            return written;
        }
        for (final MemberSymbol.ParameterSymbol parameter : method.parameters()) {
            written.add((parameter.outward() ? "out " : "") + parameter.type().describe());
        }
        return written;
    }

    // ---------------------------------------------------------------- methods

    private AsmMethod emitMethod(final NamedType type, final Decl.MethodDecl method) {
        final TypeSymbol returns = this.declarations.resolve(method.returnType());
        final Body body = new Body(type, returns);
        body.parameters(method.parameters());
        body.openClosure(this.closureFor(type, method.parameters(), method.body(), method));
        body.block(method.body());
        return new AsmMethod(method.name(), returns.describe(), this.written(method.parameters()),
                method.modifiers().contains(Decl.Modifier.STATIC), body.slotCount(), body.finish());
    }

    private AsmMethod emitConstructor(final NamedType type, final Decl.ConstructorDecl constructor,
                                      final List<Decl.FieldDecl> instanceStart) {
        final Body body = new Body(type, TypeSymbol.Primitive.VOID);
        body.parameters(constructor.parameters());
        // Chaining to another constructor of the same class means that one already put the starting
        // values in place, so doing it again here would undo whatever it decided.
        if (constructor.chained() == null || constructor.chained().base()) {
            body.fieldStarts(instanceStart);
        }
        if (constructor.chained() != null) {
            body.chained(type, constructor.chained());
        }
        if (constructor.body() != null) {
            body.openClosure(this.closureFor(type, constructor.parameters(), constructor.body(),
                    constructor));
            body.block(constructor.body());
        }
        return new AsmMethod(type.name(), "void", this.written(constructor.parameters()), false,
                body.slotCount(), body.finish());
    }

    private List<String> written(final List<Decl.Parameter> parameters) {
        final List<String> names = new ArrayList<>();
        for (final Decl.Parameter parameter : parameters) {
            names.add((parameter.outward() ? "out " : "")
                    + this.declarations.resolve(parameter.type()).describe());
        }
        return names;
    }

    /** The lines of one method, and the places it keeps its values in. */
    private final class Body {

        private final List<Instruction> code = new ArrayList<>();
        private final Map<Binding.Variable, Integer> places = new IdentityHashMap<>();
        private final Map<String, String> aliases = new HashMap<>();
        private final List<String> pending = new ArrayList<>();
        private final Deque<String> breaks = new ArrayDeque<>();
        private final Deque<String> continues = new ArrayDeque<>();
        private final NamedType owner;
        private final TypeSymbol returns;
        private Closure closure;
        private int closureSlot = -1;
        private boolean closureIsThis;
        private int nextLabel;
        private int nextSlot;

        Body(final NamedType owner, final TypeSymbol returns) {
            this.owner = owner;
            this.returns = returns;
        }

        int slotCount() {
            return this.nextSlot;
        }

        /**
         * Makes the object the lambdas of this method share, and moves into it every parameter they
         * keep. Locals they keep are written straight into it when they are declared.
         */
        void openClosure(final Closure made) {
            if (made == null) {
                return;
            }
            this.closure = made;
            this.closureSlot = this.hidden();
            this.emit(Opcode.NEWOBJ, new Operand.Constructor(made.type(), List.of()));
            this.emit(Opcode.STLOC, new Operand.Slot(this.closureSlot));
            if (made.holdsThis()) {
                this.pushClosure();
                this.emit(Opcode.LDTHIS);
                this.emit(Opcode.STFLD, new Operand.Field(made.type(), Closure.OUTER));
            }
            for (final Map.Entry<Binding.Variable, String> field : made.fields().entrySet()) {
                final Integer at = this.places.get(field.getKey());
                if (at != null) {
                    this.pushClosure();
                    this.emit(Opcode.LDLOC, new Operand.Slot(at));
                    this.emit(Opcode.STFLD, new Operand.Field(made.type(), field.getValue()));
                }
            }
        }

        /** Inside a lambda, the object the method shared with it is the object the lambda belongs to. */
        void closureIsThis(final Closure made) {
            this.closure = made;
            this.closureIsThis = true;
        }

        private boolean kept(final Binding.Variable variable) {
            return this.closure != null && this.closure.fields().containsKey(variable);
        }

        private void pushClosure() {
            if (this.closureIsThis) {
                this.emit(Opcode.LDTHIS);
            } else {
                this.emit(Opcode.LDLOC, new Operand.Slot(this.closureSlot));
            }
        }

        private void loadKept(final Binding.Variable variable) {
            this.pushClosure();
            this.emit(Opcode.LDFLD,
                    new Operand.Field(this.closure.type(), this.closure.fields().get(variable)));
        }

        /** Puts a value already on the stack into the shared object. The object goes on first. */
        private void storeKept(final Binding.Variable variable) {
            this.emit(Opcode.STFLD,
                    new Operand.Field(this.closure.type(), this.closure.fields().get(variable)));
        }

        // Inside a lambda the object the method belonged to is a field of the shared object, because
        // the lambda itself belongs to that shared object and not to the type the method was in.
        private void pushThis() {
            this.emit(Opcode.LDTHIS);
            if (this.closureIsThis) {
                this.emit(Opcode.LDFLD, new Operand.Field(this.closure.type(), Closure.OUTER));
            }
        }

        void parameters(final List<Decl.Parameter> parameters) {
            for (final Decl.Parameter parameter : parameters) {
                this.slot(Emitter.this.model.declaredAt(parameter));
            }
        }

        void fieldStarts(final List<Decl.FieldDecl> fields) {
            for (final Decl.FieldDecl field : fields) {
                final TypeSymbol type = Emitter.this.declarations.resolve(field.type());
                final boolean fieldIsStatic = field.modifiers().contains(Decl.Modifier.STATIC);
                if (!fieldIsStatic) {
                    this.emit(Opcode.LDTHIS);
                }
                this.value(field.initializer(), type);
                this.emit(fieldIsStatic ? Opcode.STSFLD : Opcode.STFLD,
                        new Operand.Field(fieldIsStatic ? this.owner.name() : null, field.name()));
            }
        }

        void chained(final NamedType type, final Decl.ConstructorCall call) {
            final NamedType target = call.base() ? type.base() : type;
            if (target == null) {
                return;
            }
            this.emit(Opcode.LDTHIS);
            final MemberSymbol.MethodSymbol chosen = this.constructorOf(target, call.arguments().size());
            this.arguments(call.arguments(), chosen);
            this.emit(Opcode.CALL, new Operand.Method(target.name(), target.name(),
                    chosen == null ? List.of() : writtenParameters(chosen), "void"));
        }

        private MemberSymbol.MethodSymbol constructorOf(final NamedType target, final int count) {
            for (final MemberSymbol member : target.members()) {
                if (member instanceof MemberSymbol.ConstructorSymbol constructor
                        && constructor.parameters().size() == count) {
                    return new MemberSymbol.MethodSymbol(target, target.name(),
                            TypeSymbol.Primitive.VOID, constructor.parameters(), constructor.modifiers());
                }
            }
            return null;
        }

        // ------------------------------------------------------------ writing lines

        private void add(final Instruction instruction) {
            Instruction written = instruction;
            if (!this.pending.isEmpty()) {
                written = written.labelled(this.pending.getFirst());
                for (int i = 1; i < this.pending.size(); i++) {
                    this.aliases.put(this.pending.get(i), this.pending.getFirst());
                }
                this.pending.clear();
            }
            this.code.add(written);
        }

        private void emit(final Opcode opcode) {
            this.add(Instruction.of(opcode));
        }

        private void emit(final Opcode opcode, final Operand operand) {
            this.add(Instruction.of(opcode, operand));
        }

        private String label() {
            this.nextLabel++;
            return "L" + this.nextLabel;
        }

        private void mark(final String label) {
            this.pending.add(label);
        }

        private int slot(final Binding.Variable variable) {
            if (variable == null) {
                return this.nextSlot++;
            }
            final Integer known = this.places.get(variable);
            if (known != null) {
                return known;
            }
            final int given = this.nextSlot++;
            this.places.put(variable, given);
            return given;
        }

        /** A place with no name, for what a lowered loop needs to hold on to. */
        private int hidden() {
            return this.nextSlot++;
        }

        List<Instruction> finish() {
            if (this.code.isEmpty() || !this.pending.isEmpty()
                    || this.code.getLast().opcode() != Opcode.RET) {
                this.emit(Opcode.RET);
            }
            final List<Instruction> resolved = new ArrayList<>();
            for (final Instruction instruction : this.code) {
                if (instruction.operand() instanceof Operand.Label target) {
                    resolved.add(new Instruction(instruction.label(), instruction.opcode(),
                            new Operand.Label(this.resolve(target.name())), instruction.comment()));
                } else {
                    resolved.add(instruction);
                }
            }
            return resolved;
        }

        // Two labels can land on the same line, and a line carries one, so the rest point at it.
        private String resolve(final String label) {
            String at = label;
            while (this.aliases.containsKey(at)) {
                at = this.aliases.get(at);
            }
            return at;
        }

        // ------------------------------------------------------------ statements

        void block(final Stmt.Block block) {
            for (final Stmt statement : block.statements()) {
                this.statement(statement);
            }
        }

        private void statement(final Stmt statement) {
            switch (statement) {
                case Stmt.Block inner -> this.block(inner);
                case Stmt.LocalDecl local -> this.local(local);
                case Stmt.ExprStmt expression -> this.discard(expression.expression());
                case Stmt.If branch -> this.branch(branch);
                case Stmt.While loop -> this.whileLoop(loop);
                case Stmt.DoWhile loop -> this.doLoop(loop);
                case Stmt.For loop -> this.forLoop(loop);
                case Stmt.ForEach loop -> this.forEach(loop);
                case Stmt.Switch choice -> this.choice(choice);
                case Stmt.Break ignored -> this.emit(Opcode.BR, new Operand.Label(this.breaks.peek()));
                case Stmt.Continue ignored -> this.emit(Opcode.BR, new Operand.Label(this.continues.peek()));
                case Stmt.Return give -> this.give(give);
                case Stmt.Dispose dispose -> this.dispose(dispose);
                case Stmt.Empty ignored -> { }
            }
        }

        private void local(final Stmt.LocalDecl local) {
            final Binding.Variable variable = Emitter.this.model.declaredAt(local);
            if (this.kept(variable)) {
                if (local.initializer() != null) {
                    this.pushClosure();
                    this.value(local.initializer(), variable.type());
                    this.storeKept(variable);
                }
                return;
            }
            final int place = this.slot(variable);
            if (local.initializer() == null) {
                return;
            }
            this.value(local.initializer(), variable == null ? null : variable.type());
            this.emit(Opcode.STLOC, new Operand.Slot(place));
        }

        // An expression written as a statement is there for what it does, so whatever it leaves
        // behind is thrown away. An assignment is told beforehand, so it never puts it there at all.
        private void discard(final Expr expression) {
            if (expression instanceof Expr.Assign assign) {
                this.assign(assign, false);
                return;
            }
            this.value(expression, null);
            if (Emitter.this.leavesAValue(expression)) {
                this.emit(Opcode.POP);
            }
        }

        private void branch(final Stmt.If statement) {
            final String otherwise = this.label();
            this.value(statement.condition(), TypeSymbol.Primitive.BOOL);
            this.emit(Opcode.BRFALSE, new Operand.Label(otherwise));
            this.statement(statement.then());
            if (statement.otherwise() == null) {
                this.mark(otherwise);
                return;
            }
            final String end = this.label();
            this.emit(Opcode.BR, new Operand.Label(end));
            this.mark(otherwise);
            this.statement(statement.otherwise());
            this.mark(end);
        }

        private void whileLoop(final Stmt.While loop) {
            final String top = this.label();
            final String end = this.label();
            this.mark(top);
            this.value(loop.condition(), TypeSymbol.Primitive.BOOL);
            this.emit(Opcode.BRFALSE, new Operand.Label(end));
            this.inLoop(top, end, loop.body());
            this.emit(Opcode.BR, new Operand.Label(top));
            this.mark(end);
        }

        private void doLoop(final Stmt.DoWhile loop) {
            final String top = this.label();
            final String again = this.label();
            final String end = this.label();
            this.mark(top);
            this.inLoop(again, end, loop.body());
            this.mark(again);
            this.value(loop.condition(), TypeSymbol.Primitive.BOOL);
            this.emit(Opcode.BRTRUE, new Operand.Label(top));
            this.mark(end);
        }

        private void forLoop(final Stmt.For loop) {
            for (final Stmt initializer : loop.initializers()) {
                this.statement(initializer);
            }
            final String top = this.label();
            final String again = this.label();
            final String end = this.label();
            this.mark(top);
            if (loop.condition() != null) {
                this.value(loop.condition(), TypeSymbol.Primitive.BOOL);
                this.emit(Opcode.BRFALSE, new Operand.Label(end));
            }
            this.inLoop(again, end, loop.body());
            this.mark(again);
            for (final Expr update : loop.updates()) {
                this.discard(update);
            }
            this.emit(Opcode.BR, new Operand.Label(top));
            this.mark(end);
        }

        // A foreach is a counted loop over the thing it walks, which is why the assembly has no
        // instruction of its own for it.
        private void forEach(final Stmt.ForEach loop) {
            final TypeSymbol source = Emitter.this.model.typeOf(loop.source());
            final int held = this.hidden();
            final int index = this.hidden();
            this.value(loop.source(), null);
            this.emit(Opcode.STLOC, new Operand.Slot(held));
            this.emit(Opcode.LDC_I4, new Operand.I4(0));
            this.emit(Opcode.STLOC, new Operand.Slot(index));

            final String top = this.label();
            final String again = this.label();
            final String end = this.label();
            this.mark(top);
            this.emit(Opcode.LDLOC, new Operand.Slot(index));
            this.emit(Opcode.LDLOC, new Operand.Slot(held));
            this.length(source);
            this.emit(Opcode.BGE, new Operand.Label(end));

            this.emit(Opcode.LDLOC, new Operand.Slot(held));
            this.emit(Opcode.LDLOC, new Operand.Slot(index));
            this.element(source);
            this.emit(Opcode.STLOC, new Operand.Slot(this.slot(Emitter.this.model.declaredAt(loop))));

            this.inLoop(again, end, loop.body());
            this.mark(again);
            this.emit(Opcode.LDLOC, new Operand.Slot(index));
            this.emit(Opcode.LDC_I4, new Operand.I4(1));
            this.emit(Opcode.ADD);
            this.emit(Opcode.STLOC, new Operand.Slot(index));
            this.emit(Opcode.BR, new Operand.Label(top));
            this.mark(end);
        }

        private void length(final TypeSymbol source) {
            if (source instanceof TypeSymbol.ArrayType) {
                this.emit(Opcode.LDLEN);
            } else {
                this.emit(Opcode.LDFLD, new Operand.Field(Emitter.this.builtIns.listType().name(), "Count"));
            }
        }

        private void element(final TypeSymbol source) {
            if (source instanceof TypeSymbol.ArrayType) {
                this.emit(Opcode.LDELEM);
                return;
            }
            final TypeSymbol held = Emitter.this.rules.elementOf(source);
            this.emit(Opcode.CALL, new Operand.Method(Emitter.this.builtIns.listType().name(), "Get",
                    List.of("int"), held == null ? "object" : held.describe()));
        }

        private void inLoop(final String again, final String end, final Stmt body) {
            this.continues.push(again);
            this.breaks.push(end);
            this.statement(body);
            this.breaks.pop();
            this.continues.pop();
        }

        // Every label is tested first and the sections follow, so a section is entered only by being
        // chosen and a run of labels can share the lines under them.
        private void choice(final Stmt.Switch choice) {
            final TypeSymbol type = Emitter.this.model.typeOf(choice.value());
            final int held = this.hidden();
            this.value(choice.value(), null);
            this.emit(Opcode.STLOC, new Operand.Slot(held));

            final String end = this.label();
            final List<String> starts = new ArrayList<>();
            String fallback = end;
            for (final Stmt.SwitchSection section : choice.sections()) {
                final String start = this.label();
                starts.add(start);
                if (section.fallback()) {
                    fallback = start;
                }
                for (final Expr label : section.labels()) {
                    this.emit(Opcode.LDLOC, new Operand.Slot(held));
                    this.value(label, type);
                    this.emit(Opcode.BEQ, new Operand.Label(start));
                }
            }
            this.emit(Opcode.BR, new Operand.Label(fallback));

            this.breaks.push(end);
            for (int i = 0; i < choice.sections().size(); i++) {
                this.mark(starts.get(i));
                for (final Stmt statement : choice.sections().get(i).statements()) {
                    this.statement(statement);
                }
            }
            this.breaks.pop();
            this.mark(end);
        }

        private void give(final Stmt.Return give) {
            if (give.value() != null) {
                this.value(give.value(), this.returns);
            }
            this.emit(Opcode.RET);
        }

        // Freeing an object leaves the place that held it empty, so the reference is cleared as well.
        private void dispose(final Stmt.Dispose statement) {
            this.value(statement.target(), null);
            this.emit(Opcode.DISPOSE);
            final Binding binding = Emitter.this.model.bindingOf(statement.target());
            if (binding instanceof Binding.Variable variable && this.kept(variable)) {
                this.pushClosure();
                this.emit(Opcode.LDNULL);
                this.storeKept(variable);
            } else if (binding instanceof Binding.Variable variable) {
                this.emit(Opcode.LDNULL);
                this.emit(Opcode.STLOC, new Operand.Slot(this.slot(variable)));
            } else if (binding instanceof Binding.Member member
                    && member.member() instanceof MemberSymbol.FieldSymbol field) {
                this.storeField(statement.target(), field, Opcode.LDNULL);
            }
        }

        private void storeField(final Expr target, final MemberSymbol.FieldSymbol field,
                                final Opcode pushValue) {
            if (field.isStatic()) {
                this.emit(pushValue);
                this.emit(Opcode.STSFLD, new Operand.Field(field.owner().name(), field.name()));
                return;
            }
            this.receiver(target);
            this.emit(pushValue);
            this.emit(Opcode.STFLD, new Operand.Field(this.ownerOf(field), field.name()));
        }

        private String ownerOf(final MemberSymbol member) {
            return member.owner() == this.owner ? null : member.owner().name();
        }

        // ------------------------------------------------------------ expressions

        private void value(final Expr expression, final TypeSymbol wanted) {
            if (expression == null) {
                return;
            }
            switch (expression) {
                case Expr.Literal literal -> this.constant(literal);
                case Expr.Name name -> this.name(name);
                case Expr.This ignored -> this.pushThis();
                case Expr.Base ignored -> this.pushThis();
                case Expr.Member member -> this.member(member);
                case Expr.Index index -> this.index(index);
                case Expr.Call call -> this.call(call);
                case Expr.New created -> this.created(created);
                case Expr.NewArray created -> this.createdArray(created);
                case Expr.Cast cast -> this.cast(cast);
                case Expr.TypeTest test -> this.typeTest(test);
                case Expr.Unary unary -> this.unary(unary);
                case Expr.Binary binary -> this.binary(binary);
                case Expr.Conditional conditional -> this.conditional(conditional, wanted);
                case Expr.Assign assign -> this.assign(assign, true);
                case Expr.Lambda lambda -> this.lambda(lambda);
                case Expr.OutArgument ignored -> { }
            }
            this.coerce(Emitter.this.model.typeOf(expression), wanted);
        }

        private void constant(final Expr.Literal literal) {
            final Object held = literal.value();
            switch (literal.kind()) {
                case INT_LITERAL -> this.emit(Opcode.LDC_I4, new Operand.I4((Integer) held));
                case LONG_LITERAL -> this.emit(Opcode.LDC_I8, new Operand.I8((Long) held));
                case FLOAT_LITERAL -> this.emit(Opcode.LDC_R4, new Operand.R4((Float) held));
                case DOUBLE_LITERAL -> this.emit(Opcode.LDC_R8, new Operand.R8((Double) held));
                case CHAR_LITERAL -> this.emit(Opcode.LDC_I4, new Operand.I4((Character) held));
                case STRING_LITERAL -> this.emit(Opcode.LDSTR, new Operand.Text((String) held));
                case TRUE -> this.emit(Opcode.LDC_I4, new Operand.I4(1));
                case FALSE -> this.emit(Opcode.LDC_I4, new Operand.I4(0));
                default -> this.emit(Opcode.LDNULL);
            }
        }

        private void name(final Expr.Name name) {
            final Binding binding = Emitter.this.model.bindingOf(name);
            if (binding instanceof Binding.Variable variable) {
                if (this.kept(variable)) {
                    this.loadKept(variable);
                } else {
                    this.emit(Opcode.LDLOC, new Operand.Slot(this.slot(variable)));
                }
                return;
            }
            if (binding instanceof Binding.Member member) {
                this.loadMember(null, member.member());
            }
        }

        private void member(final Expr.Member expression) {
            final Binding binding = Emitter.this.model.bindingOf(expression);
            if (binding instanceof Binding.Member member) {
                this.loadMember(expression.target(), member.member());
            }
        }

        // A field, a property and an event are all read the same way, because in the assembly they
        // are the same thing: a named place on an object.
        private void loadMember(final Expr target, final MemberSymbol member) {
            if (member instanceof MemberSymbol.MethodSymbol method) {
                this.handler(target, method);
                return;
            }
            if (member.isStatic()) {
                this.emit(Opcode.LDSFLD, new Operand.Field(member.owner().name(), member.name()));
                return;
            }
            if (target == null) {
                this.pushThis();
            } else {
                this.receiver(target);
            }
            this.emit(Opcode.LDFLD, new Operand.Field(this.ownerOf(member), member.name()));
        }

        // A method handed over without brackets becomes a delegate bound to whatever it belongs to.
        private void handler(final Expr target, final MemberSymbol.MethodSymbol method) {
            if (method.isStatic()) {
                this.emit(Opcode.LDNULL);
            } else if (target == null) {
                this.pushThis();
            } else {
                this.receiver(target);
            }
            this.emit(Opcode.LDFN, this.methodRef(method));
        }

        private Operand.Method methodRef(final MemberSymbol.MethodSymbol method) {
            return new Operand.Method(method.owner().name(), method.name(),
                    writtenParameters(method), method.returnType().describe());
        }

        /** Puts the object a member is read from on the stack, unless the member belongs to a type. */
        private void receiver(final Expr target) {
            if (target instanceof Expr.Member member) {
                if (Emitter.this.model.bindingOf(member) instanceof Binding.TypeName) {
                    return;
                }
                this.value(member, null);
                return;
            }
            if (Emitter.this.model.bindingOf(target) instanceof Binding.TypeName) {
                return;
            }
            this.value(target, null);
        }

        private void index(final Expr.Index expression) {
            final TypeSymbol target = Emitter.this.model.typeOf(expression.target());
            this.value(expression.target(), null);
            this.value(expression.index(), null);
            if (target instanceof TypeSymbol.ArrayType) {
                this.emit(Opcode.LDELEM);
                return;
            }
            final NamedType named = Emitter.this.rules.named(target);
            final List<TypeSymbol> held = Emitter.this.rules.arguments(target);
            final boolean isMap = named == Emitter.this.builtIns.mapType();
            this.emit(Opcode.CALL, new Operand.Method(named == null ? "object" : named.name(), "Get",
                    List.of(isMap ? held.getFirst().describe() : "int"),
                    held.isEmpty() ? "object" : held.getLast().describe()));
        }

        private void created(final Expr.New expression) {
            final MemberSymbol chosen = Emitter.this.model.callOf(expression);
            final MemberSymbol.MethodSymbol constructor = chosen instanceof MemberSymbol.MethodSymbol method
                    ? method : null;
            this.arguments(expression.arguments(), constructor);
            final TypeSymbol type = Emitter.this.model.typeOf(expression);
            this.emit(Opcode.NEWOBJ, new Operand.Constructor(type == null ? "object" : type.describe(),
                    constructor == null ? List.of() : writtenParameters(constructor)));
        }

        private void createdArray(final Expr.NewArray expression) {
            this.value(expression.length(), TypeSymbol.Primitive.INT);
            this.emit(Opcode.NEWARR, new Operand.Type(expression.elementType().describe()));
        }

        private void cast(final Expr.Cast expression) {
            final TypeSymbol from = Emitter.this.model.typeOf(expression.value());
            final TypeSymbol to = Emitter.this.model.typeOf(expression);
            this.value(expression.value(), null);
            if (Emitter.this.rules.isNumeric(from) && Emitter.this.rules.isNumeric(to)) {
                this.convert(to);
                return;
            }
            this.emit(Opcode.CASTCLASS, new Operand.Type(expression.type().describe()));
        }

        // "is" asks and gives back an answer; "as" converts when it can and gives back nothing when
        // it cannot, which is the same question asked first and acted on.
        private void typeTest(final Expr.TypeTest expression) {
            final String written = expression.type().describe();
            if (!expression.conversion()) {
                this.value(expression.value(), null);
                this.emit(Opcode.ISINST, new Operand.Type(written));
                return;
            }
            final String otherwise = this.label();
            final String end = this.label();
            this.value(expression.value(), null);
            this.emit(Opcode.DUP);
            this.emit(Opcode.ISINST, new Operand.Type(written));
            this.emit(Opcode.BRFALSE, new Operand.Label(otherwise));
            this.emit(Opcode.CASTCLASS, new Operand.Type(written));
            this.emit(Opcode.BR, new Operand.Label(end));
            this.mark(otherwise);
            this.emit(Opcode.POP);
            this.emit(Opcode.LDNULL);
            this.mark(end);
        }

        private void unary(final Expr.Unary expression) {
            if (expression.operator() == Operator.INCREMENT || expression.operator() == Operator.DECREMENT) {
                this.step(expression);
                return;
            }
            this.value(expression.operand(), null);
            switch (expression.operator()) {
                case NOT -> {
                    this.emit(Opcode.LDC_I4, new Operand.I4(0));
                    this.emit(Opcode.CEQ);
                }
                case NEGATE -> this.emit(Opcode.NEG);
                case COMPLEMENT -> this.emit(Opcode.NOT);
                default -> { }
            }
        }

        // Reading, changing and writing back, with the value that is left over being the one the
        // language says: the old one after, the new one before.
        private void step(final Expr.Unary expression) {
            final Expr place = expression.operand();
            final TypeSymbol type = Emitter.this.model.typeOf(place);
            final Opcode change = expression.operator() == Operator.INCREMENT ? Opcode.ADD : Opcode.SUB;
            final Binding binding = Emitter.this.model.bindingOf(place);
            if (binding instanceof Binding.Variable variable && this.kept(variable)) {
                if (expression.postfix()) {
                    this.loadKept(variable);
                }
                this.pushClosure();
                this.loadKept(variable);
                this.one(type);
                this.emit(change);
                this.storeKept(variable);
                if (!expression.postfix()) {
                    this.loadKept(variable);
                }
                return;
            }
            if (binding instanceof Binding.Variable variable) {
                final int at = this.slot(variable);
                this.emit(Opcode.LDLOC, new Operand.Slot(at));
                if (expression.postfix()) {
                    this.emit(Opcode.DUP);
                }
                this.one(type);
                this.emit(change);
                if (!expression.postfix()) {
                    this.emit(Opcode.DUP);
                }
                this.emit(Opcode.STLOC, new Operand.Slot(at));
                return;
            }
            this.value(place, null);
            if (expression.postfix()) {
                this.emit(Opcode.DUP);
            }
            this.one(type);
            this.emit(change);
            if (!expression.postfix()) {
                this.emit(Opcode.DUP);
            }
            if (binding instanceof Binding.Member member
                    && member.member() instanceof MemberSymbol.FieldSymbol field) {
                this.putBack(place, field);
            }
        }

        private void putBack(final Expr place, final MemberSymbol.FieldSymbol field) {
            if (field.isStatic()) {
                this.emit(Opcode.STSFLD, new Operand.Field(field.owner().name(), field.name()));
                return;
            }
            this.emit(Opcode.STFLD, new Operand.Field(this.ownerOf(field), field.name()));
        }

        private void one(final TypeSymbol type) {
            if (type == TypeSymbol.Primitive.LONG) {
                this.emit(Opcode.LDC_I8, new Operand.I8(1));
            } else if (type == TypeSymbol.Primitive.FLOAT) {
                this.emit(Opcode.LDC_R4, new Operand.R4(1));
            } else if (type == TypeSymbol.Primitive.DOUBLE) {
                this.emit(Opcode.LDC_R8, new Operand.R8(1));
            } else {
                this.emit(Opcode.LDC_I4, new Operand.I4(1));
            }
        }

        private void binary(final Expr.Binary expression) {
            switch (expression.operator()) {
                case AND, OR -> this.shortCircuit(expression);
                case ADD -> this.plus(expression);
                case EQUAL, NOT_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL ->
                        this.compare(expression);
                default -> {
                    final TypeSymbol result = Emitter.this.model.typeOf(expression);
                    this.value(expression.left(), result);
                    this.value(expression.right(), shiftKeepsItsOwn(expression) ? null : result);
                    this.emit(arithmetic(expression.operator()));
                }
            }
        }

        // A shift moves its left side by however much its right side says, and the two do not have
        // to be the same kind of number.
        private static boolean shiftKeepsItsOwn(final Expr.Binary expression) {
            return expression.operator() == Operator.SHIFT_LEFT
                    || expression.operator() == Operator.SHIFT_RIGHT;
        }

        private void plus(final Expr.Binary expression) {
            final TypeSymbol result = Emitter.this.model.typeOf(expression);
            if (result == Emitter.this.builtIns.stringType()) {
                final TypeSymbol left = Emitter.this.model.typeOf(expression.left());
                final TypeSymbol right = Emitter.this.model.typeOf(expression.right());
                this.value(expression.left(), null);
                this.value(expression.right(), null);
                this.emit(Opcode.CALL, new Operand.Method(STRING, "Concat",
                        List.of(describe(left), describe(right)), STRING));
                return;
            }
            this.value(expression.left(), result);
            this.value(expression.right(), result);
            this.emit(Opcode.ADD);
        }

        private void compare(final Expr.Binary expression) {
            final TypeSymbol left = Emitter.this.model.typeOf(expression.left());
            final TypeSymbol right = Emitter.this.model.typeOf(expression.right());
            final TypeSymbol common = Emitter.this.rules.promote(left, right);
            this.value(expression.left(), common);
            this.value(expression.right(), common);
            switch (expression.operator()) {
                case EQUAL -> this.emit(Opcode.CEQ);
                case NOT_EQUAL -> {
                    this.emit(Opcode.CEQ);
                    this.invert();
                }
                case LESS -> this.emit(Opcode.CLT);
                case GREATER -> this.emit(Opcode.CGT);
                case LESS_EQUAL -> {
                    this.emit(Opcode.CGT);
                    this.invert();
                }
                default -> {
                    this.emit(Opcode.CLT);
                    this.invert();
                }
            }
        }

        private void invert() {
            this.emit(Opcode.LDC_I4, new Operand.I4(0));
            this.emit(Opcode.CEQ);
        }

        // The right side of "and" and "or" is not run when the left side already settles the answer,
        // which is a branch and cannot be an instruction that takes both sides at once.
        private void shortCircuit(final Expr.Binary expression) {
            final String settled = this.label();
            final String end = this.label();
            final boolean isAnd = expression.operator() == Operator.AND;
            this.value(expression.left(), TypeSymbol.Primitive.BOOL);
            this.emit(isAnd ? Opcode.BRFALSE : Opcode.BRTRUE, new Operand.Label(settled));
            this.value(expression.right(), TypeSymbol.Primitive.BOOL);
            this.emit(Opcode.BR, new Operand.Label(end));
            this.mark(settled);
            this.emit(Opcode.LDC_I4, new Operand.I4(isAnd ? 0 : 1));
            this.mark(end);
        }

        private void conditional(final Expr.Conditional expression, final TypeSymbol wanted) {
            final TypeSymbol result = wanted != null ? wanted : Emitter.this.model.typeOf(expression);
            final String otherwise = this.label();
            final String end = this.label();
            this.value(expression.condition(), TypeSymbol.Primitive.BOOL);
            this.emit(Opcode.BRFALSE, new Operand.Label(otherwise));
            this.value(expression.whenTrue(), result);
            this.emit(Opcode.BR, new Operand.Label(end));
            this.mark(otherwise);
            this.value(expression.whenFalse(), result);
            this.mark(end);
        }

        // ------------------------------------------------------------ calls

        private void call(final Expr.Call expression) {
            final MemberSymbol resolved = Emitter.this.model.callOf(expression);
            if (!(resolved instanceof MemberSymbol.MethodSymbol method)) {
                return;
            }
            final Expr callee = expression.callee();
            final boolean throughDelegate = "Invoke".equals(method.name())
                    && method.owner().kind() == NamedType.Kind.DELEGATE;
            if (throughDelegate) {
                this.value(callee, null);
            } else if (!method.isStatic()) {
                if (callee instanceof Expr.Member member) {
                    this.receiver(member.target());
                } else {
                    this.pushThis();
                }
            }
            this.arguments(expression.arguments(), method);
            this.emit(throughDelegate ? Opcode.CALLVIRT : Opcode.CALL, this.methodRef(method));
            this.storeOutward(expression.arguments(), method);
        }

        private void arguments(final List<Expr> arguments, final MemberSymbol.MethodSymbol method) {
            for (int i = 0; i < arguments.size(); i++) {
                final MemberSymbol.ParameterSymbol parameter = method == null
                        || i >= method.parameters().size() ? null : method.parameters().get(i);
                if (parameter != null && parameter.outward()) {
                    continue;
                }
                this.value(arguments.get(i), parameter == null ? null : parameter.type());
            }
        }

        // What a method fills in comes back on the stack after its answer, the last one on top, so
        // they are put away from the last to the first.
        private void storeOutward(final List<Expr> arguments, final MemberSymbol.MethodSymbol method) {
            for (int i = arguments.size() - 1; i >= 0; i--) {
                if (i >= method.parameters().size() || !method.parameters().get(i).outward()) {
                    continue;
                }
                final Binding binding = Emitter.this.model.bindingOf(arguments.get(i));
                if (binding instanceof Binding.Variable variable && this.kept(variable)) {
                    final int held = this.hidden();
                    this.emit(Opcode.STLOC, new Operand.Slot(held));
                    this.pushClosure();
                    this.emit(Opcode.LDLOC, new Operand.Slot(held));
                    this.storeKept(variable);
                } else if (binding instanceof Binding.Variable variable) {
                    this.emit(Opcode.STLOC, new Operand.Slot(this.slot(variable)));
                } else if (binding instanceof Binding.Member member
                        && member.member() instanceof MemberSymbol.FieldSymbol field) {
                    this.emit(field.isStatic() ? Opcode.STSFLD : Opcode.STFLD,
                            new Operand.Field(field.isStatic() ? field.owner().name()
                                    : this.ownerOf(field), field.name()));
                } else {
                    this.emit(Opcode.POP);
                }
            }
        }

        // ------------------------------------------------------------ assignment

        private void assign(final Expr.Assign expression, final boolean leavesValue) {
            final Binding binding = Emitter.this.model.bindingOf(expression.target());
            if (binding instanceof Binding.Member member
                    && member.member() instanceof MemberSymbol.EventSymbol event) {
                this.subscribe(expression, event);
                return;
            }
            final TypeSymbol target = Emitter.this.model.typeOf(expression.target());
            if (binding instanceof Binding.Variable variable) {
                if (this.kept(variable)) {
                    this.pushClosure();
                    if (expression.operator() != Operator.ASSIGN) {
                        this.loadKept(variable);
                    }
                    this.combine(expression, target);
                    this.storeKept(variable);
                    if (leavesValue) {
                        this.loadKept(variable);
                    }
                    return;
                }
                final int at = this.slot(variable);
                if (expression.operator() != Operator.ASSIGN) {
                    this.emit(Opcode.LDLOC, new Operand.Slot(at));
                }
                this.combine(expression, target);
                if (leavesValue) {
                    this.emit(Opcode.DUP);
                }
                this.emit(Opcode.STLOC, new Operand.Slot(at));
                return;
            }
            if (expression.target() instanceof Expr.Index index) {
                this.assignElement(expression, index, target);
                return;
            }
            if (!(binding instanceof Binding.Member member)
                    || !(member.member() instanceof MemberSymbol.FieldSymbol field)) {
                return;
            }
            if (!field.isStatic()) {
                this.receiverOf(expression.target());
            }
            if (expression.operator() != Operator.ASSIGN) {
                if (!field.isStatic()) {
                    this.emit(Opcode.DUP);
                }
                this.emit(field.isStatic() ? Opcode.LDSFLD : Opcode.LDFLD,
                        new Operand.Field(field.isStatic() ? field.owner().name()
                                : this.ownerOf(field), field.name()));
            }
            this.combine(expression, target);
            this.putBack(expression.target(), field);
            if (leavesValue) {
                this.loadMember(expression.target() instanceof Expr.Member member2 ? member2.target() : null,
                        field);
            }
        }

        private void receiverOf(final Expr target) {
            if (target instanceof Expr.Member member) {
                this.receiver(member.target());
            } else {
                this.pushThis();
            }
        }

        private void combine(final Expr.Assign expression, final TypeSymbol target) {
            if (expression.operator() == Operator.ASSIGN) {
                this.value(expression.value(), target);
                return;
            }
            if (target == Emitter.this.builtIns.stringType()) {
                this.value(expression.value(), null);
                this.emit(Opcode.CALL, new Operand.Method(STRING, "Concat",
                        List.of(STRING, describe(Emitter.this.model.typeOf(expression.value()))), STRING));
                return;
            }
            this.value(expression.value(), target);
            this.emit(arithmetic(expression.operator()));
        }

        private void assignElement(final Expr.Assign expression, final Expr.Index index,
                                   final TypeSymbol element) {
            final TypeSymbol target = Emitter.this.model.typeOf(index.target());
            this.value(index.target(), null);
            this.value(index.index(), null);
            if (expression.operator() != Operator.ASSIGN) {
                this.emit(Opcode.DUP);
                this.value(index.target(), null);
                this.value(index.index(), null);
                this.emit(Opcode.LDELEM);
            }
            this.combine(expression, element);
            if (target instanceof TypeSymbol.ArrayType) {
                this.emit(Opcode.STELEM);
                return;
            }
            final NamedType named = Emitter.this.rules.named(target);
            final List<TypeSymbol> held = Emitter.this.rules.arguments(target);
            final boolean isMap = named == Emitter.this.builtIns.mapType();
            this.emit(Opcode.CALL, new Operand.Method(named == null ? "object" : named.name(),
                    isMap ? "Put" : "Set",
                    List.of(isMap ? held.getFirst().describe() : "int",
                            held.isEmpty() ? "object" : held.getLast().describe()), "void"));
        }

        // Joining and parting handlers is what the runtime does with a delegate, so the assembly asks
        // it rather than pretending an event is a kind of arithmetic.
        private void subscribe(final Expr.Assign expression, final MemberSymbol.EventSymbol event) {
            final String delegate = event.delegateType().name();
            if (!event.isStatic()) {
                this.receiverOf(expression.target());
                this.emit(Opcode.DUP);
            }
            this.emit(event.isStatic() ? Opcode.LDSFLD : Opcode.LDFLD,
                    new Operand.Field(event.isStatic() ? event.owner().name() : this.ownerOf(event),
                            event.name()));
            this.value(expression.value(), event.delegateType());
            this.emit(Opcode.CALL, new Operand.Method(DELEGATE,
                    expression.operator() == Operator.ADD ? "Combine" : "Remove",
                    List.of(delegate, delegate), delegate));
            this.emit(event.isStatic() ? Opcode.STSFLD : Opcode.STFLD,
                    new Operand.Field(event.isStatic() ? event.owner().name() : this.ownerOf(event),
                            event.name()));
        }

        // A lambda becomes a method of the type it was written in, and a delegate bound to the same
        // object. Its name begins with a digit so no source name can ever be the same.
        private void lambda(final Expr.Lambda lambda) {
            final TypeSymbol type = Emitter.this.model.typeOf(lambda);
            final NamedType delegate = Emitter.this.rules.named(type);
            if (delegate == null || delegate.invoke() == null) {
                this.emit(Opcode.LDNULL);
                return;
            }
            final List<TypeSymbol> filled = Emitter.this.rules.arguments(type);
            final MemberSymbol.MethodSymbol shape = delegate.invoke();
            final TypeSymbol gives = Emitter.this.rules.substitute(shape.returnType(), filled);
            final List<String> written = new ArrayList<>();
            for (final MemberSymbol.ParameterSymbol parameter : shape.parameters()) {
                written.add((parameter.outward() ? "out " : "")
                        + Emitter.this.rules.substitute(parameter.type(), filled).describe());
            }

            Emitter.this.lambdaCount++;
            final String name = "0lambda" + Emitter.this.lambdaCount;
            final Body body = new Body(this.owner, gives);
            if (this.closure != null) {
                body.closureIsThis(this.closure);
            }
            body.parameters(lambda.parameters());
            if (lambda.block() != null) {
                body.block(lambda.block());
            } else {
                body.value(lambda.body(), gives);
                body.emit(Opcode.RET);
            }
            final AsmMethod made = new AsmMethod(name, gives.describe(), written, false,
                    body.slotCount(), body.finish());

            // With nothing of the method's own to keep, the lambda belongs to the type it was written
            // in. With something to keep, it belongs to the object holding it, so it can reach it.
            if (this.closure == null) {
                Emitter.this.synthesized.add(made);
                this.pushThis();
                this.emit(Opcode.LDFN,
                        new Operand.Method(this.owner.name(), name, written, gives.describe()));
                return;
            }
            Emitter.this.closureTypes.get(this.closure.type()).addMethod(made);
            this.pushClosure();
            this.emit(Opcode.LDFN,
                    new Operand.Method(this.closure.type(), name, written, gives.describe()));
        }

        // ------------------------------------------------------------ conversions

        private void coerce(final TypeSymbol from, final TypeSymbol to) {
            if (from == null || to == null || from.equals(to)) {
                return;
            }
            if (Emitter.this.rules.isNumeric(from) && Emitter.this.rules.isNumeric(to)) {
                this.convert(to);
            }
        }

        private void convert(final TypeSymbol to) {
            if (to == TypeSymbol.Primitive.LONG) {
                this.emit(Opcode.CONV_I8);
            } else if (to == TypeSymbol.Primitive.FLOAT) {
                this.emit(Opcode.CONV_R4);
            } else if (to == TypeSymbol.Primitive.DOUBLE) {
                this.emit(Opcode.CONV_R8);
            } else {
                this.emit(Opcode.CONV_I4);
            }
        }
    }

    // ---------------------------------------------------------------- what a lambda keeps

    /**
     * The object a lambda keeps the method's variables in.
     *
     * <p>A lambda that uses a variable of the method around it does not take a copy: the variable
     * moves into an object both of them read and write, so a change either makes is a change the
     * other sees. That is the one thing the language it borrows its shape from also promises.
     */
    private record Closure(String type, Map<Binding.Variable, String> fields, boolean holdsThis) {

        /** The name of the field a closure keeps the object the method belonged to in. */
        static final String OUTER = "0this";
    }

    /** What one walk of a method found. */
    private static final class Found {
        private final List<Expr.Lambda> lambdas = new ArrayList<>();
        private final Map<Binding.Variable, Integer> declared = new IdentityHashMap<>();
        private final Set<Binding.Variable> used = Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean thisToo;
    }

    private Closure closureFor(final NamedType type, final List<Decl.Parameter> parameters,
                               final Stmt.Block body, final Node at) {
        final Found method = new Found();
        for (final Decl.Parameter parameter : parameters) {
            keep(method.declared, this.model.declaredAt(parameter), 0);
        }
        this.walk(body, 0, method);
        if (method.lambdas.isEmpty()) {
            return null;
        }
        final Map<Binding.Variable, String> fields = new LinkedHashMap<>();
        boolean holdsThis = false;
        for (final Expr.Lambda lambda : method.lambdas) {
            final Found inside = this.inside(lambda);
            holdsThis = holdsThis || inside.thisToo;
            for (final Binding.Variable variable : inside.used) {
                final Integer depth = method.declared.get(variable);
                if (depth == null) {
                    this.cannotYet(at, "a lambda inside another one that keeps its variable");
                    return null;
                }
                if (depth > 0) {
                    this.cannotYet(at, "a lambda that keeps a variable a loop declares");
                    return null;
                }
                fields.putIfAbsent(variable, variable.name());
            }
        }
        if (fields.isEmpty()) {
            return null;
        }
        this.closureCount++;
        final Closure closure = new Closure("0closure" + this.closureCount, fields, holdsThis);
        final AsmType written = new AsmType(AsmType.Kind.CLASS, closure.type());
        if (holdsThis) {
            written.addField(new AsmType.Field(Closure.OUTER, type.name(), false));
        }
        for (final Map.Entry<Binding.Variable, String> field : fields.entrySet()) {
            written.addField(new AsmType.Field(field.getValue(), field.getKey().type().describe(), false));
        }
        this.closures.add(written);
        this.closureTypes.put(closure.type(), written);
        return closure;
    }

    private void cannotYet(final Node at, final String what) {
        this.diagnostics.error(at.line(), at.column(), CannonError.NOT_YET_BUILT, what);
    }

    /** What a lambda uses from outside itself, and whether it reaches the object it was written in. */
    private Found inside(final Expr.Lambda lambda) {
        final Found found = new Found();
        for (final Decl.Parameter parameter : lambda.parameters()) {
            keep(found.declared, this.model.declaredAt(parameter), 0);
        }
        this.walk(lambda.block(), 0, found);
        this.walk(lambda.body(), 0, found);
        found.used.removeAll(found.declared.keySet());
        return found;
    }

    private static void keep(final Map<Binding.Variable, Integer> into, final Binding.Variable variable,
                             final int depth) {
        if (variable != null) {
            into.putIfAbsent(variable, depth);
        }
    }

    // One walk serves both questions: how deep inside loops each variable was declared, and what each
    // lambda reaches for. The depth is what says whether a variable outlives the lambda that keeps it.
    private void walk(final Stmt statement, final int depth, final Found found) {
        switch (statement) {
            case null -> { }
            case Stmt.Block block -> block.statements().forEach(inner -> this.walk(inner, depth, found));
            case Stmt.LocalDecl local -> {
                keep(found.declared, this.model.declaredAt(local), depth);
                this.walk(local.initializer(), depth, found);
            }
            case Stmt.ExprStmt expression -> this.walk(expression.expression(), depth, found);
            case Stmt.If branch -> {
                this.walk(branch.condition(), depth, found);
                this.walk(branch.then(), depth, found);
                this.walk(branch.otherwise(), depth, found);
            }
            case Stmt.While loop -> {
                this.walk(loop.condition(), depth, found);
                this.walk(loop.body(), depth + 1, found);
            }
            case Stmt.DoWhile loop -> {
                this.walk(loop.body(), depth + 1, found);
                this.walk(loop.condition(), depth, found);
            }
            case Stmt.For loop -> {
                loop.initializers().forEach(inner -> this.walk(inner, depth + 1, found));
                this.walk(loop.condition(), depth + 1, found);
                loop.updates().forEach(update -> this.walk(update, depth + 1, found));
                this.walk(loop.body(), depth + 1, found);
            }
            case Stmt.ForEach loop -> {
                keep(found.declared, this.model.declaredAt(loop), depth + 1);
                this.walk(loop.source(), depth, found);
                this.walk(loop.body(), depth + 1, found);
            }
            case Stmt.Switch choice -> {
                this.walk(choice.value(), depth, found);
                for (final Stmt.SwitchSection section : choice.sections()) {
                    section.labels().forEach(label -> this.walk(label, depth, found));
                    section.statements().forEach(inner -> this.walk(inner, depth, found));
                }
            }
            case Stmt.Return give -> this.walk(give.value(), depth, found);
            case Stmt.Dispose dispose -> this.walk(dispose.target(), depth, found);
            default -> { }
        }
    }

    private void walk(final Expr expression, final int depth, final Found found) {
        switch (expression) {
            case null -> { }
            case Expr.Name name -> {
                final Binding binding = this.model.bindingOf(name);
                if (binding instanceof Binding.Variable variable) {
                    found.used.add(variable);
                } else if (binding instanceof Binding.Member member && !member.member().isStatic()) {
                    found.thisToo = true;
                }
            }
            case Expr.This ignored -> found.thisToo = true;
            case Expr.Base ignored -> found.thisToo = true;
            case Expr.OutArgument outward -> {
                if (outward.type() != null) {
                    keep(found.declared, this.model.declaredAt(outward), depth);
                }
                if (this.model.bindingOf(outward) instanceof Binding.Variable variable) {
                    found.used.add(variable);
                }
            }
            case Expr.Binary binary -> {
                this.walk(binary.left(), depth, found);
                this.walk(binary.right(), depth, found);
            }
            case Expr.Unary unary -> this.walk(unary.operand(), depth, found);
            case Expr.Assign assign -> {
                this.walk(assign.target(), depth, found);
                this.walk(assign.value(), depth, found);
            }
            case Expr.Conditional conditional -> {
                this.walk(conditional.condition(), depth, found);
                this.walk(conditional.whenTrue(), depth, found);
                this.walk(conditional.whenFalse(), depth, found);
            }
            case Expr.Call call -> {
                this.walk(call.callee(), depth, found);
                call.arguments().forEach(argument -> this.walk(argument, depth, found));
            }
            case Expr.Member member -> this.walk(member.target(), depth, found);
            case Expr.Index index -> {
                this.walk(index.target(), depth, found);
                this.walk(index.index(), depth, found);
            }
            case Expr.New created -> created.arguments()
                    .forEach(argument -> this.walk(argument, depth, found));
            case Expr.NewArray created -> this.walk(created.length(), depth, found);
            case Expr.Cast cast -> this.walk(cast.value(), depth, found);
            case Expr.TypeTest test -> this.walk(test.value(), depth, found);
            case Expr.Lambda inner -> {
                found.lambdas.add(inner);
                inner.parameters().forEach(parameter ->
                        keep(found.declared, this.model.declaredAt(parameter), depth));
                this.walk(inner.block(), depth, found);
                this.walk(inner.body(), depth, found);
            }
            default -> { }
        }
    }

    private boolean leavesAValue(final Expr expression) {
        final TypeSymbol type = this.model.typeOf(expression);
        return type != null && type != TypeSymbol.Primitive.VOID
                && type != TypeSymbol.Special.ERROR;
    }

    private static String describe(final TypeSymbol type) {
        return type == null ? "object" : type.describe();
    }

    private static Opcode arithmetic(final Operator operator) {
        return switch (operator) {
            case ADD -> Opcode.ADD;
            case SUBTRACT -> Opcode.SUB;
            case MULTIPLY -> Opcode.MUL;
            case DIVIDE -> Opcode.DIV;
            case REMAINDER -> Opcode.REM;
            case BIT_AND -> Opcode.AND;
            case BIT_OR -> Opcode.OR;
            case BIT_XOR -> Opcode.XOR;
            case SHIFT_LEFT -> Opcode.SHL;
            default -> Opcode.SHR;
        };
    }
}
