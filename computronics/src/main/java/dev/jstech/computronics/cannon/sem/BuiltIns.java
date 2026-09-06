/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.sem;

import dev.jstech.computronics.cannon.ast.Decl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The types the language brings with it, before a single line of a player's program is read.
 *
 * <p>This is the language's own universe: the root, strings, the two collections, the delegates a
 * handler is written as, the entry-point interface, and the parts of the library that are pure
 * calculation. The objects that reach out into the world get declared where they are implemented,
 * beside the network they read, so nothing here has to know that a network exists.
 */
public final class BuiltIns {

    private static final Set<Decl.Modifier> PUBLIC = Set.of(Decl.Modifier.PUBLIC);
    private static final Set<Decl.Modifier> PUBLIC_STATIC = Set.of(Decl.Modifier.PUBLIC, Decl.Modifier.STATIC);

    private final Map<String, NamedType> types = new LinkedHashMap<>();

    private final NamedType objectType;
    private final NamedType stringType;
    private final NamedType listType;
    private final NamedType mapType;
    private final NamedType actionType;
    private final NamedType actionOfType;
    private final NamedType funcType;
    private final NamedType scriptType;

    public BuiltIns() {
        this.objectType = this.declare("object", NamedType.Kind.CLASS);
        this.stringType = this.declare("string", NamedType.Kind.CLASS);
        this.listType = this.declare("List", NamedType.Kind.CLASS, "T");
        this.mapType = this.declare("Map", NamedType.Kind.CLASS, "K", "V");
        this.actionType = this.declare("Action", NamedType.Kind.DELEGATE);
        this.actionOfType = this.declare("Action", NamedType.Kind.DELEGATE, "T");
        this.funcType = this.declare("Func", NamedType.Kind.DELEGATE, "T", "R");
        this.scriptType = this.declare("IScript", NamedType.Kind.INTERFACE);

        this.fillString();
        this.fillList();
        this.fillMap();
        this.fillDelegates();
        this.fillScript();
        this.fillMath();
        this.fillConsole();
        this.fillConvert();
        this.fillTime();
        this.fillRandom();
        this.fillFile();
        this.fillComputer();
    }

    /** The root of every reference type. */
    public NamedType objectType() {
        return this.objectType;
    }

    /** Text. */
    public NamedType stringType() {
        return this.stringType;
    }

    /** The growable sequence. */
    public NamedType listType() {
        return this.listType;
    }

    /** The keyed collection. */
    public NamedType mapType() {
        return this.mapType;
    }

    /** The interface a program's entry point implements. */
    public NamedType scriptType() {
        return this.scriptType;
    }

    /**
     * The type of that name, or null. Two delegates share the name Action and are told apart by how
     * many arguments they were given; a name written with the wrong number still resolves, so the
     * mistake is reported as the wrong count rather than as an unknown name.
     */
    public NamedType type(final String name, final int arity) {
        final NamedType exact = this.types.get(key(name, arity));
        if (exact != null) {
            return exact;
        }
        for (final NamedType type : this.types.values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }

    /** Every built-in type, in the order they were declared. */
    public List<NamedType> all() {
        return List.copyOf(this.types.values());
    }

    /** Whether a name belongs to the language rather than to the player. */
    public boolean isReserved(final String name) {
        return this.type(name, 0) != null;
    }

    private static String key(final String name, final int arity) {
        return name + "/" + arity;
    }

    private NamedType declare(final String name, final NamedType.Kind kind, final String... parameters) {
        final NamedType type = new NamedType(name, kind, List.of(parameters), true);
        this.types.put(key(name, parameters.length), type);
        return type;
    }

    private void method(final NamedType owner, final String name, final TypeSymbol returns,
                        final Set<Decl.Modifier> modifiers, final TypeSymbol... takes) {
        final List<MemberSymbol.ParameterSymbol> parameters = new ArrayList<>();
        for (int i = 0; i < takes.length; i++) {
            parameters.add(MemberSymbol.ParameterSymbol.of("a" + i, takes[i]));
        }
        owner.addMember(new MemberSymbol.MethodSymbol(owner, name, returns, parameters, modifiers));
    }

    private void property(final NamedType owner, final String name, final TypeSymbol type,
                          final Set<Decl.Modifier> modifiers) {
        owner.addMember(new MemberSymbol.PropertySymbol(owner, name, type, true, false, modifiers, Set.of()));
    }

    private void fillString() {
        final TypeSymbol text = this.stringType;
        final TypeSymbol integer = TypeSymbol.Primitive.INT;
        final TypeSymbol flag = TypeSymbol.Primitive.BOOL;
        this.property(this.stringType, "Length", integer, PUBLIC);
        this.method(this.stringType, "Substring", text, PUBLIC, integer);
        this.method(this.stringType, "Substring", text, PUBLIC, integer, integer);
        this.method(this.stringType, "IndexOf", integer, PUBLIC, text);
        this.method(this.stringType, "Contains", flag, PUBLIC, text);
        this.method(this.stringType, "StartsWith", flag, PUBLIC, text);
        this.method(this.stringType, "EndsWith", flag, PUBLIC, text);
        this.method(this.stringType, "ToUpper", text, PUBLIC);
        this.method(this.stringType, "ToLower", text, PUBLIC);
        this.method(this.stringType, "Trim", text, PUBLIC);
        this.method(this.stringType, "Replace", text, PUBLIC, text, text);
        this.method(this.stringType, "Split", new TypeSymbol.GenericType(this.listType, List.of(text)),
                PUBLIC, TypeSymbol.Primitive.CHAR);
        this.method(this.stringType, "Format", text, PUBLIC_STATIC, text, this.objectType);
        this.method(this.stringType, "Format", text, PUBLIC_STATIC, text, this.objectType, this.objectType);
    }

    private void fillList() {
        final TypeSymbol item = new TypeSymbol.TypeParameter("T", 0);
        final TypeSymbol integer = TypeSymbol.Primitive.INT;
        final TypeSymbol flag = TypeSymbol.Primitive.BOOL;
        final TypeSymbol nothing = TypeSymbol.Primitive.VOID;
        this.property(this.listType, "Count", integer, PUBLIC);
        this.method(this.listType, "Add", nothing, PUBLIC, item);
        this.method(this.listType, "Insert", nothing, PUBLIC, integer, item);
        this.method(this.listType, "RemoveAt", nothing, PUBLIC, integer);
        this.method(this.listType, "Remove", flag, PUBLIC, item);
        this.method(this.listType, "Clear", nothing, PUBLIC);
        this.method(this.listType, "Contains", flag, PUBLIC, item);
        this.method(this.listType, "IndexOf", integer, PUBLIC, item);
        this.method(this.listType, "Get", item, PUBLIC, integer);
        this.method(this.listType, "Set", nothing, PUBLIC, integer, item);
        this.method(this.listType, "Sort", nothing, PUBLIC);
    }

    private void fillMap() {
        final TypeSymbol key = new TypeSymbol.TypeParameter("K", 0);
        final TypeSymbol value = new TypeSymbol.TypeParameter("V", 1);
        final TypeSymbol nothing = TypeSymbol.Primitive.VOID;
        this.property(this.mapType, "Count", TypeSymbol.Primitive.INT, PUBLIC);
        this.method(this.mapType, "Put", nothing, PUBLIC, key, value);
        this.method(this.mapType, "Get", value, PUBLIC, key);
        this.method(this.mapType, "ContainsKey", TypeSymbol.Primitive.BOOL, PUBLIC, key);
        // The one lookup that answers both questions at once: whether the key was there, and what it
        // held. It is why the language has an outward parameter at all.
        this.mapType.addMember(new MemberSymbol.MethodSymbol(this.mapType, "TryGet",
                TypeSymbol.Primitive.BOOL,
                List.of(MemberSymbol.ParameterSymbol.of("key", key),
                        new MemberSymbol.ParameterSymbol("value", value, true)), PUBLIC));
        this.method(this.mapType, "Remove", TypeSymbol.Primitive.BOOL, PUBLIC, key);
        this.method(this.mapType, "Keys", new TypeSymbol.GenericType(this.listType, List.of(key)), PUBLIC);
        this.method(this.mapType, "Values", new TypeSymbol.GenericType(this.listType, List.of(value)), PUBLIC);
    }

    private void fillDelegates() {
        this.actionType.setInvoke(new MemberSymbol.MethodSymbol(this.actionType, "Invoke",
                TypeSymbol.Primitive.VOID, List.of(), PUBLIC));
        this.actionOfType.setInvoke(new MemberSymbol.MethodSymbol(this.actionOfType, "Invoke",
                TypeSymbol.Primitive.VOID,
                List.of(MemberSymbol.ParameterSymbol.of("value", new TypeSymbol.TypeParameter("T", 0))), PUBLIC));
        this.funcType.setInvoke(new MemberSymbol.MethodSymbol(this.funcType, "Invoke",
                new TypeSymbol.TypeParameter("R", 1),
                List.of(MemberSymbol.ParameterSymbol.of("value", new TypeSymbol.TypeParameter("T", 0))), PUBLIC));
    }

    private void fillScript() {
        this.method(this.scriptType, "OnInit", TypeSymbol.Primitive.VOID, PUBLIC);
        this.method(this.scriptType, "OnTick", TypeSymbol.Primitive.VOID, PUBLIC);
        this.method(this.scriptType, "OnDestroy", TypeSymbol.Primitive.VOID, PUBLIC);
    }

    private void fillMath() {
        final NamedType math = this.declare("Math", NamedType.Kind.CLASS);
        final TypeSymbol integer = TypeSymbol.Primitive.INT;
        final TypeSymbol real = TypeSymbol.Primitive.DOUBLE;
        this.method(math, "Abs", integer, PUBLIC_STATIC, integer);
        this.method(math, "Abs", real, PUBLIC_STATIC, real);
        this.method(math, "Min", integer, PUBLIC_STATIC, integer, integer);
        this.method(math, "Min", real, PUBLIC_STATIC, real, real);
        this.method(math, "Max", integer, PUBLIC_STATIC, integer, integer);
        this.method(math, "Max", real, PUBLIC_STATIC, real, real);
        this.method(math, "Clamp", integer, PUBLIC_STATIC, integer, integer, integer);
        this.method(math, "Clamp", real, PUBLIC_STATIC, real, real, real);
        this.method(math, "Floor", real, PUBLIC_STATIC, real);
        this.method(math, "Ceil", real, PUBLIC_STATIC, real);
        this.method(math, "Round", real, PUBLIC_STATIC, real);
        this.method(math, "Sqrt", real, PUBLIC_STATIC, real);
        this.method(math, "Pow", real, PUBLIC_STATIC, real, real);
    }

    private void fillConsole() {
        final NamedType console = this.declare("Console", NamedType.Kind.CLASS);
        this.method(console, "Print", TypeSymbol.Primitive.VOID, PUBLIC_STATIC, this.stringType);
        this.method(console, "PrintLine", TypeSymbol.Primitive.VOID, PUBLIC_STATIC, this.stringType);
        this.method(console, "Clear", TypeSymbol.Primitive.VOID, PUBLIC_STATIC);
    }

    private void fillConvert() {
        final NamedType convert = this.declare("Convert", NamedType.Kind.CLASS);
        this.method(convert, "ToInt", TypeSymbol.Primitive.INT, PUBLIC_STATIC, this.stringType);
        this.method(convert, "ToLong", TypeSymbol.Primitive.LONG, PUBLIC_STATIC, this.stringType);
        this.method(convert, "ToDouble", TypeSymbol.Primitive.DOUBLE, PUBLIC_STATIC, this.stringType);
        this.method(convert, "ToString", this.stringType, PUBLIC_STATIC, this.objectType);
        convert.addMember(new MemberSymbol.MethodSymbol(convert, "TryInt", TypeSymbol.Primitive.BOOL,
                List.of(MemberSymbol.ParameterSymbol.of("text", this.stringType),
                        new MemberSymbol.ParameterSymbol("value", TypeSymbol.Primitive.INT, true)),
                PUBLIC_STATIC));
    }

    private void fillTime() {
        final NamedType time = this.declare("Time", NamedType.Kind.CLASS);
        final TypeSymbol ticks = TypeSymbol.Primitive.LONG;
        this.property(time, "Tick", ticks, PUBLIC_STATIC);
        this.property(time, "DayTime", ticks, PUBLIC_STATIC);
        this.property(time, "Day", ticks, PUBLIC_STATIC);
        this.method(time, "Ticks", ticks, PUBLIC_STATIC, TypeSymbol.Primitive.INT);
    }

    /**
     * The machine's own drives, reached with the paths the shell uses.
     *
     * <p>Writing can fail without the program being wrong: a disk fills up. So the writes answer whether
     * they happened rather than stopping the program, and reading something that is not there is asked
     * for with the try form.
     */
    private void fillFile() {
        final NamedType file = this.declare("File", NamedType.Kind.CLASS);
        this.method(file, "Exists", TypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType);
        this.method(file, "Read", this.stringType, PUBLIC_STATIC, this.stringType);
        file.addMember(new MemberSymbol.MethodSymbol(file, "TryRead", TypeSymbol.Primitive.BOOL,
                List.of(MemberSymbol.ParameterSymbol.of("path", this.stringType),
                        new MemberSymbol.ParameterSymbol("text", this.stringType, true)),
                PUBLIC_STATIC));
        this.method(file, "Write", TypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType, this.stringType);
        this.method(file, "Append", TypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType, this.stringType);
        this.method(file, "Delete", TypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType);
        this.method(file, "MkDir", TypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType);
        this.method(file, "List", new TypeSymbol.GenericType(this.listType, List.of(this.stringType)),
                PUBLIC_STATIC, this.stringType);
    }

    /**
     * The machine the program is running on, and the little records it answers with.
     *
     * <p>These are read-only pictures taken when they are asked for, not live views: a program holds
     * what it was told, and asks again when it wants to know again.
     */
    private void fillComputer() {
        final TypeSymbol integer = TypeSymbol.Primitive.INT;
        final TypeSymbol whole = TypeSymbol.Primitive.LONG;

        final NamedType cpu = this.declare("CpuInfo", NamedType.Kind.CLASS);
        this.property(cpu, "Mhz", integer, PUBLIC);
        this.property(cpu, "Cores", integer, PUBLIC);
        this.property(cpu, "Era", this.stringType, PUBLIC);

        final NamedType disk = this.declare("DiskInfo", NamedType.Kind.CLASS);
        this.property(disk, "Mount", this.stringType, PUBLIC);
        this.property(disk, "UsedMb", whole, PUBLIC);
        this.property(disk, "CapacityMb", whole, PUBLIC);

        final NamedType os = this.declare("OsInfo", NamedType.Kind.CLASS);
        this.property(os, "Id", this.stringType, PUBLIC);
        this.property(os, "Name", this.stringType, PUBLIC);

        final NamedType process = this.declare("ProcessInfo", NamedType.Kind.CLASS);
        this.property(process, "Id", integer, PUBLIC);
        this.property(process, "Name", this.stringType, PUBLIC);
        this.property(process, "State", this.stringType, PUBLIC);
        this.property(process, "HeldBytes", whole, PUBLIC);

        final NamedType computer = this.declare("Computer", NamedType.Kind.CLASS);
        this.property(computer, "Name", this.stringType, PUBLIC_STATIC);
        this.property(computer, "Cpu", cpu, PUBLIC_STATIC);
        this.property(computer, "Os", os, PUBLIC_STATIC);
        this.property(computer, "RamMb", integer, PUBLIC_STATIC);
        this.property(computer, "FreeRamMb", integer, PUBLIC_STATIC);
        this.property(computer, "Online", TypeSymbol.Primitive.BOOL, PUBLIC_STATIC);
        this.method(computer, "Disks", new TypeSymbol.GenericType(this.listType, List.of(disk)), PUBLIC_STATIC);
        this.method(computer, "Programs", new TypeSymbol.GenericType(this.listType, List.of(this.stringType)),
                PUBLIC_STATIC);
        this.method(computer, "Processes", new TypeSymbol.GenericType(this.listType, List.of(process)),
                PUBLIC_STATIC);
    }

    private void fillRandom() {
        final NamedType random = this.declare("Random", NamedType.Kind.CLASS);
        this.method(random, "Next", TypeSymbol.Primitive.INT, PUBLIC_STATIC, TypeSymbol.Primitive.INT);
        this.method(random, "NextDouble", TypeSymbol.Primitive.DOUBLE, PUBLIC_STATIC);
        this.method(random, "Seed", TypeSymbol.Primitive.VOID, PUBLIC_STATIC, TypeSymbol.Primitive.LONG);
    }
}
