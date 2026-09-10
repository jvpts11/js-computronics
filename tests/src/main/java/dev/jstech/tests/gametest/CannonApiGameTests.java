/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.cannon.CannonCompiler;
import dev.jstech.computers.cannon.SourceFile;
import dev.jstech.computers.cannon.machine.MachinePrograms;
import dev.jstech.computers.hardware.DiskSize;
import dev.jstech.computers.hardware.StorageTier;
import dev.jstech.computers.os.fs.DiskFilesystem;
import dev.jstech.tests.JsTests;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A Cannon program reaching out of itself on a real machine.
 *
 * <p>The language is tested on its own against a made-up machine; this is the other half, where the
 * drive is a real one with a real disk in it, the processor is whatever was put in the socket, and a
 * file a program writes is the file the shell opens.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class CannonApiGameTests {

    private CannonApiGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 2;

    /**
     * What a script starts with when it does not say so itself: the whole library brought in and a
     * namespace, on one line so the source keeps its line numbers.
     */
    private static final String PRELUDE = "using System.*; using System.IO.*; using System.Collections.*; "
            + "using System.Utils.*; using System.Machine.*; using System.Network.*; using System.Operations.*; "
            + "using System.Execution.*; namespace Programs; ";

    /** Compiles a script and gives back the listing the machine is asked to run. */
    private static String listing(final String source) {
        final String whole = source.contains("namespace ") ? source : PRELUDE + source;
        final CannonCompiler.Result built =
                CannonCompiler.compile(List.of(new SourceFile("Script.can", whole)));
        if (!built.ok()) {
            throw new IllegalStateException(String.join("\n", built.lines()));
        }
        return built.assembly();
    }

    /** A computer with enough hardware to run, an OS on it, and a drive to write to. */
    private static CraftingComputerBlockEntity computer(final GameTestHelper helper, final BlockPos at) {
        helper.setBlock(at, ComputingModule.CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(at) instanceof CraftingComputerBlockEntity computer)) {
            helper.fail("no computer at " + at);
            return null;
        }
        final ItemStackHandler hw = computer.getHardware();
        hw.setStackInSlot(CraftingComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.CPU_SLOT,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        /*
         * Frames XP, because that is the oldest system the language is allowed on and the oldest one
         * with drives a program can write to at all.
         */
        computer.installOs(ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "frames_xp"));
        return computer;
    }

    @GameTest(template = ARENA)
    public static void file_aProgramWritesToTheDriveAndTheDiskHasIt(final GameTestHelper helper) {
        final BlockPos at = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity computer = computer(helper, at);
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // The same write the shell would do, so a refusal here is the drive's, not the bridge's.
                    final var direct = new dev.jstech.computers.program.ServerCliComputer(
                            computer, helper.getLevel()).writeFile("direct.txt", "by the shell");
                    helper.assertTrue(direct.ok(), "the shell itself can write here: " + direct.message());
                    final MachinePrograms.Started started = computer.cannon().start("writer.asm", listing("""
                            class Writer {
                                static void Main() {
                                    if (File.Write("stock.txt", "iron 64")) {
                                        Console.PrintLine("wrote");
                                    } else {
                                        Console.PrintLine("refused");
                                    }
                                }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.equals(List.of("wrote")),
                            "the drive takes the write; it said " + said);
                    final Optional<String> read =
                            DiskFilesystem.read(computer.systemDisk(), "stock.txt");
                    helper.assertTrue(read.isPresent(), "the file is on the disk; it holds "
                            + DiskFilesystem.list(computer.systemDisk(), "",
                                    dev.jstech.computers.os.FilesystemKind.FLAT).stream()
                                    .map(DiskFilesystem.FileEntry::path).toList());
                    helper.assertTrue("iron 64".equals(read.orElse("")),
                            "with what it wrote in it; got " + read.orElse(""));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void file_aProgramReadsBackWhatTheShellWouldSee(final GameTestHelper helper) {
        final BlockPos at = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity computer = computer(helper, at);
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Put the file there the way anything else on the machine would.
                    DiskFilesystem.write(computer.systemDisk(), "note.txt",
                            dev.jstech.computers.os.fs.FileType.TXT, "written by hand", Long.MAX_VALUE,
                            dev.jstech.computers.os.FilesystemKind.FLAT);
                    final MachinePrograms.Started started = computer.cannon().start("reader.asm", listing("""
                            class Reader {
                                static void Main() {
                                    if (File.TryRead("note.txt", out string held)) {
                                        Console.PrintLine("read " + held);
                                    } else {
                                        Console.PrintLine("nothing there");
                                    }
                                }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.equals(List.of("read written by hand")),
                            "the program reads what is on the disk; got " + said);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void file_listsNamesAProgramCouldTurnRoundAndOpen(final GameTestHelper helper) {
        final BlockPos at = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity computer = computer(helper, at);
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final var shell = new dev.jstech.computers.program.ServerCliComputer(
                            computer, helper.getLevel());
                    shell.writeFile("notes.txt", "one");
                    /*
                     * A name that comes back from a listing has to be the name that opens the file. It
                     * is the only thing a program can do with it.
                     */
                    final MachinePrograms.Started started = computer.cannon().start("ls.asm", listing("""
                            class Ls {
                                static void Main() {
                                    foreach (string name in File.List("C:\\\\")) {
                                        if (name.EndsWith("/")) {
                                            Console.PrintLine("folder " + name);
                                        } else if (File.Exists(name)) {
                                            Console.PrintLine("open " + name);
                                        } else {
                                            Console.PrintLine("cannot open " + name);
                                        }
                                    }
                                }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertFalse(said.isEmpty(), "it lists something");
                    boolean sawFile = false;
                    for (final String line : said) {
                        helper.assertFalse(line.startsWith("cannot open "),
                                "every name a listing gives back can be acted on; got " + line);
                        sawFile = sawFile || line.startsWith("open ");
                    }
                    helper.assertTrue(sawFile, "including the file that was just written; got " + said);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void file_theDriveCostsTheProgramMoreThanItsOwnArithmetic(final GameTestHelper helper) {
        final BlockPos at = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity computer = computer(helper, at);
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final int quiet = spend(computer,
                            "namespace Costs; class A { static void Main() { int n = 1 + 1; } }");
                    final int loud = spend(computer, "using System.IO.*; namespace Costs; "
                            + "class B { static void Main() { File.Write(\"a.txt\", \"x\"); } }");
                    helper.assertTrue(loud > quiet + 50,
                            "writing to a disk is charged for; " + loud + " against " + quiet);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void computer_readsTheHardwareThatIsActuallyInTheSockets(final GameTestHelper helper) {
        final BlockPos at = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity computer = computer(helper, at);
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms.Started started = computer.cannon().start("look.asm", listing("""
                            class Look {
                                static void Main() {
                                    CpuInfo cpu = Computer.Cpu;
                                    Console.PrintLine(cpu.Cores + " at " + cpu.Mhz + " " + cpu.Era);
                                    Console.PrintLine("os " + Computer.Os.Name);
                                    Console.PrintLine("ram " + Computer.RamMb);
                                }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    /*
                     * The socket holds a four-core Ascent X4 965 at 3400 on a Standard board, with 8 GB
                     * in the slot and Frames XP on the disk: what the machine reports has to be that.
                     */
                    helper.assertTrue(said.size() == 3, "it says its three lines; got " + said);
                    helper.assertTrue(said.get(0).equals("4 at 3400 standard"),
                            "the processor is the one in the socket; got " + said.get(0));
                    helper.assertTrue(said.get(1).equals("os Frames XP"),
                            "the system is the one on the disk; got " + said.get(1));
                    helper.assertTrue(said.get(2).equals("ram 8192"),
                            "the memory is what is in the slot; got " + said.get(2));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void computer_seesItselfAmongTheProgramsItIsRunning(final GameTestHelper helper) {
        final BlockPos at = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity computer = computer(helper, at);
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // It is listed by the name it gives itself, not by the file it was started from.
                    final MachinePrograms.Started started = computer.cannon().start("ps.asm", listing("""
                            class Ps {
                                static void Main() {
                                    Program.SetName("Ps");
                                    foreach (ProcessInfo one in Computer.Processes()) {
                                        Console.PrintLine(one.Id + " " + one.Name + " " + one.State);
                                    }
                                }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.equals(List.of(started.id() + " Ps running")),
                            "a program listing the machine's programs finds itself, running; got " + said);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void network_readsWhatTheRealNetworkIsHolding(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder.CraftingNetwork wired =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper).buildCraftingNetwork();
        final CraftingComputerBlockEntity computer = wired.cc();
        wired.rack().getServerStorage(0).insert(net.minecraft.world.item.Items.OAK_LOG, 640);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms.Started started = computer.cannon().start("stock.asm", listing("""
                            class Stock {
                                static void Main() {
                                    if (!Network.Online) { Console.PrintLine("standalone"); return; }
                                    Console.PrintLine("logs " + Network.Total("minecraft:oak_log"));
                                    foreach (HoldingInfo where in Network.Find("minecraft:oak_log")) {
                                        Console.PrintLine(where.Server + " " + where.Quantity);
                                    }
                                }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.size() == 2, "it reads the network and who holds it; got " + said);
                    helper.assertTrue("logs 640".equals(said.getFirst()),
                            "the total is what was put in; got " + said.getFirst());
                    helper.assertTrue(said.get(1).endsWith(" 640"),
                            "and the server holding it says how much; got " + said.get(1));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void network_readsWhatTheRealNetworkCanHold(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder.CraftingNetwork wired =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper).buildCraftingNetwork();
        final CraftingComputerBlockEntity computer = wired.cc();
        wired.rack().getServerStorage(0).insert(net.minecraft.world.item.Items.OAK_LOG, 640);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final long capacity = dev.jstech.computers.operation.NetworkStorage.of(
                            helper.getLevel(), wired.mainframe().networkUuid()).capacity();
                    helper.assertTrue(capacity > 0, "the network has drives to fill; got " + capacity);
                    final MachinePrograms.Started started = computer.cannon().start("room.asm", listing("""
                            class Room {
                                static void Main() {
                                    Console.PrintLine(Network.Used + " of " + Network.Capacity);
                                    foreach (ServerInfo server in Network.Servers()) {
                                        Console.PrintLine(server.Stored + "/" + server.Capacity);
                                    }
                                }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.getFirst().equals("640 of " + capacity),
                            "the program reads what the network holds and could hold; got " + said.getFirst());
                    helper.assertTrue(said.size() > 1 && said.get(1).startsWith("640/"),
                            "and the same for the server holding it; got " + said);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void mainframe_readsWhatTheRealOrchestratorHasBeenDoing(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder.CraftingNetwork wired =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper).buildCraftingNetwork();
        final CraftingComputerBlockEntity computer = wired.cc();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms.Started started = computer.cannon().start("watch.asm", listing("""
                            class Watch {
                                static void Main() {
                                    Console.PrintLine(Mainframe.Online ? "orchestrated" : "headless");
                                    WorkStat select = Mainframe.Stats("select");
                                    Console.PrintLine("selects " + select.Count);
                                }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.size() == 2 && "orchestrated".equals(said.getFirst()),
                            "it finds the Mainframe on its network; got " + said);
                    helper.assertTrue(said.get(1).startsWith("selects "),
                            "and reads a kind of work it has not done as zero; got " + said.get(1));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void operations_pullsFromTheRealNetworkAndSaysWhichScriptAsked(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder.CraftingNetwork wired =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper).buildCraftingNetwork();
        final CraftingComputerBlockEntity computer = wired.cc();
        wired.rack().getServerStorage(0).insert(net.minecraft.world.item.Items.OAK_LOG, 640);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms.Started started = computer.cannon().start("restock.asm", listing("""
                            class Restock : IScript {
                                public void OnInit() {
                                    AskResult asked = Operations.Pull("minecraft:oak_log", 64);
                                    Console.PrintLine(asked.Ok ? "asked" : asked.Message);
                                }
                                public void OnTick() { }
                                public void OnDestroy() { }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.equals(List.of("asked")),
                            "the network takes the ask; got " + said);
                })
                .thenExecuteAfter(20, () -> {
                    /*
                     * The row the network wrote down has to name the script, not just say a program did
                     * it: a base runs many at once and the player has to know which one to go and fix.
                     * The name is the class's whole name, namespace and all, as the Task Manager lists it.
                     */
                    final List<dev.jstech.computers.operation.payload.OperationRecord> log =
                            wired.mainframe().recentOperations();
                    helper.assertFalse(log.isEmpty(), "the network wrote the work down");
                    boolean named = false;
                    for (final var record : log) {
                        for (final var move : record.moves()) {
                            named = named || move.to().contains("Cannon: Programs.Restock")
                                    || move.from().contains("Cannon: Programs.Restock");
                        }
                    }
                    helper.assertTrue(named, "a row names the script that asked; got " + log);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void watch_wakesAScriptWhenTheRealNetworkRunsLow(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder.CraftingNetwork wired =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper).buildCraftingNetwork();
        final CraftingComputerBlockEntity computer = wired.cc();
        wired.rack().getServerStorage(0).insert(net.minecraft.world.item.Items.OAK_LOG, 640);
        final int[] id = new int[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms.Started started = computer.cannon().start("low.asm", listing("""
                            class Low : IScript {
                                public void OnInit() {
                                    Network.WatchBelow("minecraft:oak_log", 100, Told);
                                }
                                public void OnTick() { }
                                public void Told(StockEvent e) {
                                    Console.PrintLine("low: " + e.Previous + " -> " + e.Total);
                                }
                                public void OnDestroy() { }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    id[0] = started.id();
                    // The watch is set up, and the machine now knows to look this one up each tick.
                    computer.cannon().tick(100000, item -> 640L);
                    helper.assertTrue(computer.cannon().byId(id[0]).process().console().isEmpty(),
                            "nothing has happened yet");
                })
                .thenExecuteAfter(2, () -> {
                    // The logs are taken away by something else on the network, as they would be.
                    wired.rack().getServerStorage(0).extract(
                            dev.jstech.computers.storage.StorageKey.of(
                                    new net.minecraft.world.item.ItemStack(
                                            net.minecraft.world.item.Items.OAK_LOG)), 600);
                    for (int i = 0; i < 4; i++) {
                        computer.cannon().tick(100000, computer::networkStock);
                    }
                    final List<String> said = computer.cannon().byId(id[0]).process().console();
                    helper.assertTrue(said.size() == 1 && said.getFirst().startsWith("low: "),
                            "the script is woken once, when it crosses; got " + said);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void canpack_publishesToTheMirrorAndTheNetworkOffersIt(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder.CraftingNetwork wired =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper).buildCraftingNetwork();
        final CraftingComputerBlockEntity computer = wired.cc();
        wired.mainframe().installMirror();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final var shell = new dev.jstech.computers.program.ServerCliComputer(
                            computer, helper.getLevel());
                    // A package as it would come off 'canpack build'.
                    final var manifest = new dev.jstech.computers.cannon.pack.Manifest(
                            "stockwatch", "1.0.0", "jvpts11", "stockwatch.asm", "bell", 1,
                            List.of("stockwatch.asm"), "Tells you when the iron runs low");
                    final var packed = new dev.jstech.computers.cannon.pack.Packed(manifest,
                            java.util.Map.of("stockwatch.asm", listing("""
                                    class Watcher : IScript {
                                        public void OnInit() { }
                                        public void OnTick() { }
                                        public void OnDestroy() { }
                                    }
                                    """)));
                    helper.assertTrue(shell.writeFile(packed.fileName(), packed.write()).ok(),
                            "the package is on the disk");

                    final var published = shell.publishPackage(packed.fileName());
                    helper.assertTrue(published.ok(), "it publishes: " + published.message());
                    helper.assertTrue(wired.mainframe().shelvedPackage("stockwatch") != null,
                            "the Mirror is holding it");

                    // Anyone on the network sees it on the shelf, marked as a player's own.
                    boolean offered = false;
                    for (final var info : shell.packagesAvailable()) {
                        if ("stockwatch".equals(info.name())) {
                            offered = info.community();
                        }
                    }
                    helper.assertTrue(offered, "the network offers it, marked as the community's");

                    // What comes back off the shelf is what went on it, line for line.
                    final var back = dev.jstech.computers.cannon.pack.Packed.read(
                            wired.mainframe().shelvedPackage("stockwatch"));
                    helper.assertTrue(back != null && back.files().equals(packed.files()),
                            "and it comes back unchanged");

                    /*
                     * And a computer on the network installs it: the files land in a folder of its own,
                     * and the machine knows it has a program a player wrote.
                     */
                    computer.console().install(
                            dev.jstech.computers.program.cli.CannonCommands.RUNTIME);
                    final var installed = shell.packageInstall("stockwatch");
                    helper.assertTrue(installed.ok(), "it installs: " + installed.message());
                    final var known = computer.console().communityProgram("stockwatch");
                    helper.assertTrue(known != null, "the machine knows it has it");
                    helper.assertTrue("PROGRAMS/stockwatch/stockwatch.asm".equals(known.entry()),
                            "and knows what to run; got " + (known == null ? "" : known.entry()));
                    helper.assertTrue(shell.readFile(known.entry()).ok(),
                            "the listing is on the disk where it says it is");

                    final var removed = shell.packageRemove("stockwatch");
                    helper.assertTrue(removed.ok(), "it uninstalls: " + removed.message());
                    helper.assertTrue(computer.console().communityProgram("stockwatch") == null,
                            "and the machine forgets it");
                    helper.assertFalse(shell.readFile("PROGRAMS/stockwatch/stockwatch.asm").ok(),
                            "taking its files with it");

                    helper.assertTrue(shell.unpublishPackage("stockwatch").ok(), "it comes back off");
                    helper.assertTrue(wired.mainframe().shelvedPackage("stockwatch") == null,
                            "and the shelf is clear");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void network_saysSoOnAMachineWithNoCableInIt(final GameTestHelper helper) {
        final BlockPos at = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity computer = computer(helper, at);
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms.Started started = computer.cannon().start("alone.asm", listing("""
                            class Alone {
                                static void Main() {
                                    Console.PrintLine(Network.Online ? "networked" : "standalone");
                                }
                            }
                            """), 1, computer);
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.equals(List.of("standalone")),
                            "a machine with no cable knows it; got " + said);
                })
                .thenSucceed();
    }

    /** Runs a program to the end on that machine and says what it spent. */
    private static int spend(final CraftingComputerBlockEntity computer, final String source) {
        final MachinePrograms.Started started =
                computer.cannon().start("one.asm", listing(source), 1, computer);
        computer.cannon().tick(100000);
        final MachinePrograms.Live one = computer.cannon().byId(started.id());
        final int spent = one == null ? 0 : one.process().spent();
        computer.cannon().stop(started.id());
        return spent;
    }
}
