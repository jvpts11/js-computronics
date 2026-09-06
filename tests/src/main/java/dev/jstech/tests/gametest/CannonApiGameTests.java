/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computronics.cannon.CannonCompiler;
import dev.jstech.computronics.cannon.SourceFile;
import dev.jstech.computronics.cannon.machine.CannonProcesses;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.os.fs.DiskFilesystem;
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

    /** Compiles a script and gives back the listing the machine is asked to run. */
    private static String listing(final String source) {
        final CannonCompiler.Result built =
                CannonCompiler.compile(List.of(new SourceFile("Script.can", source)));
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
        // Frames XP, because that is the oldest system the language is allowed on and the oldest one
        // with drives a program can write to at all.
        computer.installOs(ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_xp"));
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
                    final var direct = new dev.jstech.computronics.program.ServerCliComputer(
                            computer, helper.getLevel()).writeFile("direct.txt", "by the shell");
                    helper.assertTrue(direct.ok(), "the shell itself can write here: " + direct.message());
                    final CannonProcesses.Started started = computer.cannon().start("writer.asm", listing("""
                            class Writer {
                                static void Main() {
                                    if (File.Write("stock.txt", "iron 64")) {
                                        Console.PrintLine("wrote");
                                    } else {
                                        Console.PrintLine("refused");
                                    }
                                }
                            }
                            """), 1, computer.cannonHost());
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.equals(List.of("wrote")),
                            "the drive takes the write; it said " + said);
                    final Optional<String> read =
                            DiskFilesystem.read(computer.systemDisk(), "stock.txt");
                    helper.assertTrue(read.isPresent(), "the file is on the disk; it holds "
                            + DiskFilesystem.list(computer.systemDisk(), "",
                                    dev.jstech.computronics.os.FilesystemKind.FLAT).stream()
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
                            dev.jstech.computronics.os.fs.FileType.TXT, "written by hand", Long.MAX_VALUE,
                            dev.jstech.computronics.os.FilesystemKind.FLAT);
                    final CannonProcesses.Started started = computer.cannon().start("reader.asm", listing("""
                            class Reader {
                                static void Main() {
                                    if (File.TryRead("note.txt", out string held)) {
                                        Console.PrintLine("read " + held);
                                    } else {
                                        Console.PrintLine("nothing there");
                                    }
                                }
                            }
                            """), 1, computer.cannonHost());
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.equals(List.of("read written by hand")),
                            "the program reads what is on the disk; got " + said);
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
                    final int quiet = spend(computer, "class A { static void Main() { int n = 1 + 1; } }");
                    final int loud = spend(computer,
                            "class B { static void Main() { File.Write(\"a.txt\", \"x\"); } }");
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
                    final CannonProcesses.Started started = computer.cannon().start("look.asm", listing("""
                            class Look {
                                static void Main() {
                                    CpuInfo cpu = Computer.Cpu;
                                    Console.PrintLine(cpu.Cores + " at " + cpu.Mhz + " " + cpu.Era);
                                    Console.PrintLine("os " + Computer.Os.Name);
                                    Console.PrintLine("ram " + Computer.RamMb);
                                }
                            }
                            """), 1, computer.cannonHost());
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    // The socket holds a four-core Ascent X4 965 at 3400 on a Standard board, with 8 GB
                    // in the slot and Frames XP on the disk: what the machine reports has to be that.
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
                    final CannonProcesses.Started started = computer.cannon().start("ps.asm", listing("""
                            class Ps {
                                static void Main() {
                                    foreach (ProcessInfo one in Computer.Processes()) {
                                        Console.PrintLine(one.Id + " " + one.Name + " " + one.State);
                                    }
                                }
                            }
                            """), 1, computer.cannonHost());
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.equals(List.of(started.id() + " ps.asm running")),
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
                    final CannonProcesses.Started started = computer.cannon().start("stock.asm", listing("""
                            class Stock {
                                static void Main() {
                                    if (!Network.Online) { Console.PrintLine("standalone"); return; }
                                    Console.PrintLine("logs " + Network.Total("minecraft:oak_log"));
                                    foreach (HoldingInfo where in Network.Find("minecraft:oak_log")) {
                                        Console.PrintLine(where.Server + " " + where.Quantity);
                                    }
                                }
                            }
                            """), 1, computer.cannonHost());
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
                    final long capacity = dev.jstech.computronics.operation.NetworkStorage.of(
                            helper.getLevel(), wired.mainframe().networkUuid()).capacity();
                    helper.assertTrue(capacity > 0, "the network has drives to fill; got " + capacity);
                    final CannonProcesses.Started started = computer.cannon().start("room.asm", listing("""
                            class Room {
                                static void Main() {
                                    Console.PrintLine(Network.Used + " of " + Network.Capacity);
                                    foreach (ServerInfo server in Network.Servers()) {
                                        Console.PrintLine(server.Stored + "/" + server.Capacity);
                                    }
                                }
                            }
                            """), 1, computer.cannonHost());
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
                    final CannonProcesses.Started started = computer.cannon().start("watch.asm", listing("""
                            class Watch {
                                static void Main() {
                                    Console.PrintLine(Mainframe.Online ? "orchestrated" : "headless");
                                    WorkStat select = Mainframe.Stats("select");
                                    Console.PrintLine("selects " + select.Count);
                                }
                            }
                            """), 1, computer.cannonHost());
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
                    final CannonProcesses.Started started = computer.cannon().start("restock.asm", listing("""
                            class Restock : IScript {
                                public void OnInit() {
                                    AskResult asked = Operations.Pull("minecraft:oak_log", 64);
                                    Console.PrintLine(asked.Ok ? "asked" : asked.Message);
                                }
                                public void OnTick() { }
                                public void OnDestroy() { }
                            }
                            """), 1, computer.cannonHost());
                    helper.assertTrue(started.ok(), "the program starts: " + started.message());
                    computer.cannon().tick(100000);
                    final List<String> said = computer.cannon().byId(started.id()).process().console();
                    helper.assertTrue(said.equals(List.of("asked")),
                            "the network takes the ask; got " + said);
                })
                .thenExecuteAfter(20, () -> {
                    // The row the network wrote down has to name the script, not just say a program did
                    // it: a base runs many at once and the player has to know which one to go and fix.
                    final List<dev.jstech.computronics.operation.payload.OperationRecord> log =
                            wired.mainframe().recentOperations();
                    helper.assertFalse(log.isEmpty(), "the network wrote the work down");
                    boolean named = false;
                    for (final var record : log) {
                        for (final var move : record.moves()) {
                            named = named || move.to().contains("Cannon: Restock")
                                    || move.from().contains("Cannon: Restock");
                        }
                    }
                    helper.assertTrue(named, "a row names the script that asked; got " + log);
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
                    final CannonProcesses.Started started = computer.cannon().start("alone.asm", listing("""
                            class Alone {
                                static void Main() {
                                    Console.PrintLine(Network.Online ? "networked" : "standalone");
                                }
                            }
                            """), 1, computer.cannonHost());
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
        final CannonProcesses.Started started =
                computer.cannon().start("one.asm", listing(source), 1, computer.cannonHost());
        computer.cannon().tick(100000);
        final CannonProcesses.Live one = computer.cannon().byId(started.id());
        final int spent = one == null ? 0 : one.process().spent();
        computer.cannon().stop(started.id());
        return spent;
    }
}
