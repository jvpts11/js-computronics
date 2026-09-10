/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.sem;

import dev.jstech.computers.cannon.ast.IDecl;
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

    private static final Set<IDecl.Modifier> PUBLIC = Set.of(IDecl.Modifier.PUBLIC);
    private static final Set<IDecl.Modifier> PUBLIC_STATIC = Set.of(IDecl.Modifier.PUBLIC, IDecl.Modifier.STATIC);

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
        this.fillProgram();
        this.fillTime();
        this.fillRandom();
        this.fillFile();
        this.fillComputer();
        this.fillNetwork();
        this.fillMainframe();
        this.fillOperations();
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

    /*
     * Where the language keeps its types. Everything a program reaches for lives under System, sorted
     * by what it is about, and has to be brought in with a using before its bare name means anything;
     * only the two roots every program is made of, the text and the object, belong to no namespace.
     */
    /** The root of the language's own namespaces. */
    public static final String SYSTEM = "System";
    private static final String COLLECTIONS = "System.Collections";
    private static final String IO = "System.IO";
    private static final String UTILS = "System.Utils";
    private static final String MACHINE = "System.Machine";
    private static final String NETWORK = "System.Network";
    private static final String OPERATIONS = "System.Operations";
    private static final String EXECUTION = "System.Execution";

    private static final Map<String, String> HOMES = Map.ofEntries(
            Map.entry("IScript", SYSTEM), Map.entry("Action", SYSTEM), Map.entry("Func", SYSTEM),
            Map.entry("Program", EXECUTION),
            Map.entry("List", COLLECTIONS), Map.entry("Map", COLLECTIONS),
            Map.entry("Console", IO), Map.entry("File", IO),
            Map.entry("Math", UTILS), Map.entry("Convert", UTILS), Map.entry("Random", UTILS), Map.entry("Time", UTILS),
            Map.entry("Computer", MACHINE), Map.entry("CpuInfo", MACHINE), Map.entry("DiskInfo", MACHINE),
            Map.entry("OsInfo", MACHINE), Map.entry("ProcessInfo", MACHINE),
            Map.entry("Network", NETWORK), Map.entry("ServerInfo", NETWORK), Map.entry("HoldingInfo", NETWORK),
            Map.entry("StockEvent", NETWORK), Map.entry("Subscription", NETWORK), Map.entry("WorkStat", NETWORK),
            Map.entry("Mainframe", NETWORK),
            Map.entry("Operations", OPERATIONS), Map.entry("OperationInfo", OPERATIONS), Map.entry("AskResult", OPERATIONS));

    /**
     * The type known by exactly {@code fullName}, its namespace in front ({@code System.IO.Console}),
     * with {@code arity} arguments or, when there is none of that count, whichever there is; {@code -1}
     * asks for whichever. A bare name of a type that lives in a namespace is not found here.
     */
    public NamedType qualified(final String fullName, final int arity) {
        NamedType any = null;
        for (final NamedType type : this.types.values()) {
            if (!type.fullName().equals(fullName)) {
                continue;
            }
            if (type.typeParameters().size() == arity) {
                return type;
            }
            if (any == null) {
                any = type;
            }
        }
        return any;
    }

    /** Whether {@code prefix} is one of the language's namespaces, or the start of one: System, System.IO. */
    public boolean isNamespace(final String prefix) {
        for (final String home : HOMES.values()) {
            if (home.equals(prefix) || home.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    /** The namespace a bare name would be found in, or null when the language has no type of that name there. */
    public String homeOf(final String name) {
        return HOMES.get(name);
    }

    /** Every namespace the language has, System first, for a list that offers them. */
    public List<String> namespaces() {
        return List.of(SYSTEM, COLLECTIONS, IO, UTILS, MACHINE, NETWORK, OPERATIONS, EXECUTION);
    }

    private static String key(final String name, final int arity) {
        return name + "/" + arity;
    }

    private NamedType declare(final String name, final NamedType.Kind kind, final String... parameters) {
        final NamedType type = new NamedType(name, kind, List.of(parameters), true);
        type.setNamespace(HOMES.getOrDefault(name, ""));
        this.types.put(key(name, parameters.length), type);
        return type;
    }

    private void method(final NamedType owner, final String name, final ITypeSymbol returns,
                        final Set<IDecl.Modifier> modifiers, final ITypeSymbol... takes) {
        final List<IMemberSymbol.ParameterSymbol> parameters = new ArrayList<>();
        for (int i = 0; i < takes.length; i++) {
            parameters.add(IMemberSymbol.ParameterSymbol.of("a" + i, takes[i]));
        }
        owner.addMember(new IMemberSymbol.MethodSymbol(owner, name, returns, parameters, modifiers));
    }

    private void property(final NamedType owner, final String name, final ITypeSymbol type,
                          final Set<IDecl.Modifier> modifiers) {
        owner.addMember(new IMemberSymbol.PropertySymbol(owner, name, type, true, false, modifiers, Set.of()));
    }

    private void fillString() {
        final ITypeSymbol text = this.stringType;
        final ITypeSymbol integer = ITypeSymbol.Primitive.INT;
        final ITypeSymbol flag = ITypeSymbol.Primitive.BOOL;
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
        this.method(this.stringType, "Split", new ITypeSymbol.GenericType(this.listType, List.of(text)),
                PUBLIC, ITypeSymbol.Primitive.CHAR);
        this.method(this.stringType, "Format", text, PUBLIC_STATIC, text, this.objectType);
        this.method(this.stringType, "Format", text, PUBLIC_STATIC, text, this.objectType, this.objectType);
    }

    private void fillList() {
        final ITypeSymbol item = new ITypeSymbol.TypeParameter("T", 0);
        final ITypeSymbol integer = ITypeSymbol.Primitive.INT;
        final ITypeSymbol flag = ITypeSymbol.Primitive.BOOL;
        final ITypeSymbol nothing = ITypeSymbol.Primitive.VOID;
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
        final ITypeSymbol key = new ITypeSymbol.TypeParameter("K", 0);
        final ITypeSymbol value = new ITypeSymbol.TypeParameter("V", 1);
        final ITypeSymbol nothing = ITypeSymbol.Primitive.VOID;
        this.property(this.mapType, "Count", ITypeSymbol.Primitive.INT, PUBLIC);
        this.method(this.mapType, "Put", nothing, PUBLIC, key, value);
        this.method(this.mapType, "Get", value, PUBLIC, key);
        this.method(this.mapType, "ContainsKey", ITypeSymbol.Primitive.BOOL, PUBLIC, key);
        /*
         * The one lookup that answers both questions at once: whether the key was there, and what it
         * held. It is why the language has an outward parameter at all.
         */
        this.mapType.addMember(new IMemberSymbol.MethodSymbol(this.mapType, "TryGet",
                ITypeSymbol.Primitive.BOOL,
                List.of(IMemberSymbol.ParameterSymbol.of("key", key),
                        new IMemberSymbol.ParameterSymbol("value", value, true)), PUBLIC));
        this.method(this.mapType, "Remove", ITypeSymbol.Primitive.BOOL, PUBLIC, key);
        this.method(this.mapType, "Keys", new ITypeSymbol.GenericType(this.listType, List.of(key)), PUBLIC);
        this.method(this.mapType, "Values", new ITypeSymbol.GenericType(this.listType, List.of(value)), PUBLIC);
    }

    private void fillDelegates() {
        this.actionType.setInvoke(new IMemberSymbol.MethodSymbol(this.actionType, "Invoke",
                ITypeSymbol.Primitive.VOID, List.of(), PUBLIC));
        this.actionOfType.setInvoke(new IMemberSymbol.MethodSymbol(this.actionOfType, "Invoke",
                ITypeSymbol.Primitive.VOID,
                List.of(IMemberSymbol.ParameterSymbol.of("value", new ITypeSymbol.TypeParameter("T", 0))), PUBLIC));
        this.funcType.setInvoke(new IMemberSymbol.MethodSymbol(this.funcType, "Invoke",
                new ITypeSymbol.TypeParameter("R", 1),
                List.of(IMemberSymbol.ParameterSymbol.of("value", new ITypeSymbol.TypeParameter("T", 0))), PUBLIC));
    }

    private void fillScript() {
        this.method(this.scriptType, "OnInit", ITypeSymbol.Primitive.VOID, PUBLIC);
        this.method(this.scriptType, "OnTick", ITypeSymbol.Primitive.VOID, PUBLIC);
        this.method(this.scriptType, "OnDestroy", ITypeSymbol.Primitive.VOID, PUBLIC);
    }

    private void fillMath() {
        final NamedType math = this.declare("Math", NamedType.Kind.CLASS);
        final ITypeSymbol integer = ITypeSymbol.Primitive.INT;
        final ITypeSymbol real = ITypeSymbol.Primitive.DOUBLE;
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
        this.method(console, "Print", ITypeSymbol.Primitive.VOID, PUBLIC_STATIC, this.stringType);
        this.method(console, "PrintLine", ITypeSymbol.Primitive.VOID, PUBLIC_STATIC, this.stringType);
        this.method(console, "Clear", ITypeSymbol.Primitive.VOID, PUBLIC_STATIC);
        /*
         * Reading waits: a program that asks for a line stops until one is typed at the terminal it is
         * in front of. HasLine asks without waiting, for a program that has other things to do meanwhile.
         */
        this.method(console, "ReadLine", this.stringType, PUBLIC_STATIC);
        this.method(console, "HasLine", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC);
        /*
         * The same wait, with the line read as a value: a program asking for a number gets one, and a
         * line that is not one stops the program with the text it could not read, as Convert would.
         */
        this.method(console, "ReadInt", ITypeSymbol.Primitive.INT, PUBLIC_STATIC);
        this.method(console, "ReadLong", ITypeSymbol.Primitive.LONG, PUBLIC_STATIC);
        this.method(console, "ReadDouble", ITypeSymbol.Primitive.DOUBLE, PUBLIC_STATIC);
        this.method(console, "ReadBool", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC);
    }

    private void fillConvert() {
        final NamedType convert = this.declare("Convert", NamedType.Kind.CLASS);
        this.method(convert, "ToInt", ITypeSymbol.Primitive.INT, PUBLIC_STATIC, this.stringType);
        this.method(convert, "ToLong", ITypeSymbol.Primitive.LONG, PUBLIC_STATIC, this.stringType);
        this.method(convert, "ToFloat", ITypeSymbol.Primitive.FLOAT, PUBLIC_STATIC, this.stringType);
        this.method(convert, "ToDouble", ITypeSymbol.Primitive.DOUBLE, PUBLIC_STATIC, this.stringType);
        this.method(convert, "ToBool", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType);
        this.method(convert, "ToString", this.stringType, PUBLIC_STATIC, this.objectType);
        /*
         * The Try forms answer whether the text was a value and hand the value out sideways, for a
         * program that would rather ask again than stop on a line somebody mistyped.
         */
        this.tries(convert, "TryInt", ITypeSymbol.Primitive.INT);
        this.tries(convert, "TryLong", ITypeSymbol.Primitive.LONG);
        this.tries(convert, "TryDouble", ITypeSymbol.Primitive.DOUBLE);
        this.tries(convert, "TryBool", ITypeSymbol.Primitive.BOOL);
    }

    /** Declares {@code bool Name(string text, out T value)} on the converter. */
    private void tries(final NamedType convert, final String name, final ITypeSymbol value) {
        convert.addMember(new IMemberSymbol.MethodSymbol(convert, name, ITypeSymbol.Primitive.BOOL,
                List.of(IMemberSymbol.ParameterSymbol.of("text", this.stringType),
                        new IMemberSymbol.ParameterSymbol("value", value, true)),
                PUBLIC_STATIC));
    }

    /**
     * The program itself, as a thing it can speak about. A program that says what it is called is
     * listed by that name on the machine's process list; one that does not is listed by the runtime.
     */
    private void fillProgram() {
        final NamedType program = this.declare("Program", NamedType.Kind.CLASS);
        this.method(program, "SetName", ITypeSymbol.Primitive.VOID, PUBLIC_STATIC, this.stringType);
        this.property(program, "Name", this.stringType, PUBLIC_STATIC);
    }

    private void fillTime() {
        final NamedType time = this.declare("Time", NamedType.Kind.CLASS);
        final ITypeSymbol ticks = ITypeSymbol.Primitive.LONG;
        this.property(time, "Tick", ticks, PUBLIC_STATIC);
        this.property(time, "DayTime", ticks, PUBLIC_STATIC);
        this.property(time, "Day", ticks, PUBLIC_STATIC);
        this.method(time, "Ticks", ticks, PUBLIC_STATIC, ITypeSymbol.Primitive.INT);
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
        this.method(file, "Exists", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType);
        this.method(file, "Read", this.stringType, PUBLIC_STATIC, this.stringType);
        file.addMember(new IMemberSymbol.MethodSymbol(file, "TryRead", ITypeSymbol.Primitive.BOOL,
                List.of(IMemberSymbol.ParameterSymbol.of("path", this.stringType),
                        new IMemberSymbol.ParameterSymbol("text", this.stringType, true)),
                PUBLIC_STATIC));
        this.method(file, "Write", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType, this.stringType);
        this.method(file, "Append", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType, this.stringType);
        this.method(file, "Delete", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType);
        this.method(file, "MkDir", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC, this.stringType);
        this.method(file, "List", new ITypeSymbol.GenericType(this.listType, List.of(this.stringType)),
                PUBLIC_STATIC, this.stringType);
    }

    /**
     * The machine the program is running on, and the little records it answers with.
     *
     * <p>These are read-only pictures taken when they are asked for, not live views: a program holds
     * what it was told, and asks again when it wants to know again.
     */
    private void fillComputer() {
        final ITypeSymbol integer = ITypeSymbol.Primitive.INT;
        final ITypeSymbol whole = ITypeSymbol.Primitive.LONG;

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
        this.property(computer, "Online", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC);
        this.method(computer, "Disks", new ITypeSymbol.GenericType(this.listType, List.of(disk)), PUBLIC_STATIC);
        this.method(computer, "Programs", new ITypeSymbol.GenericType(this.listType, List.of(this.stringType)),
                PUBLIC_STATIC);
        this.method(computer, "Processes", new ITypeSymbol.GenericType(this.listType, List.of(process)),
                PUBLIC_STATIC);
    }

    /**
     * The data network the machine is on.
     *
     * <p>{@code Online} and {@code Current} answer on any machine, because whether there is a network is
     * a fair question anywhere. Everything else needs one, and says so if there is none.
     */
    private void fillNetwork() {
        final ITypeSymbol whole = ITypeSymbol.Primitive.LONG;

        final NamedType holding = this.declare("HoldingInfo", NamedType.Kind.CLASS);
        this.property(holding, "Server", this.stringType, PUBLIC);
        this.property(holding, "Quantity", whole, PUBLIC);

        final NamedType server = this.declare("ServerInfo", NamedType.Kind.CLASS);
        this.property(server, "Name", this.stringType, PUBLIC);
        this.property(server, "Stored", whole, PUBLIC);
        this.property(server, "Capacity", whole, PUBLIC);

        final NamedType network = this.declare("Network", NamedType.Kind.CLASS);
        this.property(network, "Online", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC);
        this.property(network, "Current", this.stringType, PUBLIC_STATIC);
        this.property(network, "Capacity", whole, PUBLIC_STATIC);
        this.property(network, "Used", whole, PUBLIC_STATIC);
        this.method(network, "Total", whole, PUBLIC_STATIC, this.stringType);
        this.method(network, "Types", new ITypeSymbol.GenericType(this.listType, List.of(this.stringType)),
                PUBLIC_STATIC);
        this.method(network, "Find", new ITypeSymbol.GenericType(this.listType, List.of(holding)),
                PUBLIC_STATIC, this.stringType);
        this.method(network, "Servers", new ITypeSymbol.GenericType(this.listType, List.of(server)),
                PUBLIC_STATIC);

        /*
         * Being told beats asking. A program that wants to know when the iron runs low says so once and
         * is called when it happens, instead of asking every tick for the rest of the world's life.
         */
        final NamedType event = this.declare("StockEvent", NamedType.Kind.CLASS);
        this.property(event, "Item", this.stringType, PUBLIC);
        this.property(event, "Total", whole, PUBLIC);
        this.property(event, "Previous", whole, PUBLIC);

        final NamedType subscription = this.declare("Subscription", NamedType.Kind.CLASS);
        this.property(subscription, "Id", ITypeSymbol.Primitive.INT, PUBLIC);
        this.property(subscription, "Item", this.stringType, PUBLIC);

        final ITypeSymbol told = new ITypeSymbol.GenericType(this.actionOfType, List.of(event));
        this.method(network, "Watch", subscription, PUBLIC_STATIC, this.stringType, told);
        this.method(network, "WatchBelow", subscription, PUBLIC_STATIC, this.stringType, whole, told);
        this.method(network, "WatchAbove", subscription, PUBLIC_STATIC, this.stringType, whole, told);
    }

    /**
     * The machine that orchestrates the network, and what it remembers of the work it has done.
     *
     * <p>A kind of work the network has not done reads as zeroes, so a script can add up and compare
     * without first asking whether there is anything to add up.
     */
    private void fillMainframe() {
        final ITypeSymbol integer = ITypeSymbol.Primitive.INT;

        final NamedType stat = this.declare("WorkStat", NamedType.Kind.CLASS);
        this.property(stat, "Type", this.stringType, PUBLIC);
        this.property(stat, "Count", integer, PUBLIC);
        this.property(stat, "AverageWait", integer, PUBLIC);
        this.property(stat, "AverageRun", integer, PUBLIC);
        this.property(stat, "ShortfallPercent", integer, PUBLIC);
        this.property(stat, "Moved", ITypeSymbol.Primitive.LONG, PUBLIC);

        final NamedType mainframe = this.declare("Mainframe", NamedType.Kind.CLASS);
        this.property(mainframe, "Online", ITypeSymbol.Primitive.BOOL, PUBLIC_STATIC);
        this.property(mainframe, "PeakToday", integer, PUBLIC_STATIC);
        this.method(mainframe, "Stats", stat, PUBLIC_STATIC, this.stringType);
        this.method(mainframe, "Work", new ITypeSymbol.GenericType(this.listType, List.of(stat)),
                PUBLIC_STATIC);
    }

    /**
     * Asking the network to move things.
     *
     * <p>Asking can fail without the program being wrong: there may be no Mainframe running, or nothing
     * that crafts the thing. So an ask answers whether it was taken and why not, and a script carries on
     * and tries something else rather than stopping.
     */
    private void fillOperations() {
        final ITypeSymbol whole = ITypeSymbol.Primitive.LONG;

        final NamedType asked = this.declare("AskResult", NamedType.Kind.CLASS);
        this.property(asked, "Ok", ITypeSymbol.Primitive.BOOL, PUBLIC);
        this.property(asked, "Message", this.stringType, PUBLIC);

        final NamedType operation = this.declare("OperationInfo", NamedType.Kind.CLASS);
        this.property(operation, "Id", this.stringType, PUBLIC);
        this.property(operation, "Type", this.stringType, PUBLIC);
        this.property(operation, "Item", this.stringType, PUBLIC);
        this.property(operation, "Moved", whole, PUBLIC);
        this.property(operation, "Requested", whole, PUBLIC);
        this.property(operation, "Status", this.stringType, PUBLIC);
        this.property(operation, "Priority", this.stringType, PUBLIC);

        final NamedType operations = this.declare("Operations", NamedType.Kind.CLASS);
        this.method(operations, "Pull", asked, PUBLIC_STATIC, this.stringType, whole);
        this.method(operations, "Push", asked, PUBLIC_STATIC, this.stringType, whole);
        this.method(operations, "Craft", asked, PUBLIC_STATIC, this.stringType, whole);
        this.method(operations, "Cancel", asked, PUBLIC_STATIC, this.stringType);
        /*
         * Moving one up the queue is asked for, not written into the record a program was handed: what
         * it holds is a picture of how things were, and painting over a picture changes nothing.
         */
        this.method(operations, "Reprioritise", asked, PUBLIC_STATIC, this.stringType, this.stringType);
        this.method(operations, "Get", operation, PUBLIC_STATIC, this.stringType);
        this.method(operations, "List", new ITypeSymbol.GenericType(this.listType, List.of(operation)),
                PUBLIC_STATIC);
    }

    private void fillRandom() {
        final NamedType random = this.declare("Random", NamedType.Kind.CLASS);
        this.method(random, "Next", ITypeSymbol.Primitive.INT, PUBLIC_STATIC, ITypeSymbol.Primitive.INT);
        this.method(random, "NextDouble", ITypeSymbol.Primitive.DOUBLE, PUBLIC_STATIC);
        this.method(random, "Seed", ITypeSymbol.Primitive.VOID, PUBLIC_STATIC, ITypeSymbol.Primitive.LONG);
    }
}
