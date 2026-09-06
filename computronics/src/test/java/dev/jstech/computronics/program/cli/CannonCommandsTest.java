/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CannonCommandsTest {

    private static final String SCRIPT = """
            class Monitor : IScript {
                public void OnInit() { }
                public void OnTick() { Console.PrintLine("hello"); }
                public void OnDestroy() { }
            }
            """;

    private CliShell shell;
    private Fake computer;

    @BeforeEach
    void setUp() {
        this.shell = new CliShell(BuiltinCommands.all(), 60);
        this.computer = new Fake();
    }

    private String run(final String line) {
        final StringBuilder text = new StringBuilder();
        for (final CliLine written : this.shell.run(line, this.computer).lines()) {
            text.append(written.text()).append('\n');
        }
        return text.toString();
    }

    private boolean errored(final String line) {
        return this.shell.run(line, this.computer).lines().stream()
                .anyMatch(written -> written.style() == CliStyle.ERROR);
    }

    @Test
    void run_hasNoCannonVerbsUntilThePackagesAreInstalled() {
        this.computer.files.put("Monitor.can", SCRIPT);
        assertTrue(this.run("cannonc Monitor.can").contains("command not found"));
        assertTrue(this.run("cannon ps").contains("command not found"));
    }

    @Test
    void compile_writesTheAssemblyBesideTheSource() {
        this.computer.add(CannonCommands.COMPILER);
        this.computer.files.put("Monitor.can", SCRIPT);
        assertTrue(this.run("cannonc Monitor.can").contains("wrote Monitor.asm"));
        assertTrue(this.computer.files.get("Monitor.asm").startsWith(".asm 1"));
        assertTrue(this.computer.files.get("Monitor.asm").contains(".start Monitor"));
    }

    @Test
    void compile_writesWhereItWasToldTo() {
        this.computer.add(CannonCommands.COMPILER);
        this.computer.files.put("Monitor.can", SCRIPT);
        assertTrue(this.run("cannonc Monitor.can -o build.asm").contains("wrote build.asm"));
        assertTrue(this.computer.files.containsKey("build.asm"));
        assertFalse(this.computer.files.containsKey("Monitor.asm"));
    }

    @Test
    void compile_saysWhatIsWrongAndWritesNothing() {
        this.computer.add(CannonCommands.COMPILER);
        this.computer.files.put("Bad.can", "class C { void M() { int n = \"text\"; } }\n");
        final String out = this.run("cannonc Bad.can");
        assertTrue(out.contains("Bad.can("), out);
        assertTrue(out.contains("nothing was written"), out);
        assertFalse(this.computer.files.containsKey("Bad.asm"));
    }

    @Test
    void compile_namesTheFileTheMistakeIsIn() {
        this.computer.add(CannonCommands.COMPILER);
        this.computer.files.put("disk/Bad.can", "class C { void M() { nope(); } }\n");
        assertTrue(this.run("cannonc disk/Bad.can").contains("Bad.can("));
    }

    @Test
    void compile_saysSoWhenThereIsNoSuchFile() {
        this.computer.add(CannonCommands.COMPILER);
        assertTrue(this.run("cannonc Missing.can").contains("no such file"));
    }

    @Test
    void compile_asksForAFileWhenGivenNone() {
        this.computer.add(CannonCommands.COMPILER);
        assertTrue(this.run("cannonc").contains("usage: cannonc"));
    }

    @Test
    void cannon_listsWhatIsRunningAndSaysWhenNothingIs() {
        this.computer.add(CannonCommands.RUNTIME);
        assertTrue(this.run("cannon ps").contains("no Cannon programs are running"));
        this.computer.running.add(new CliComputer.CannonProcess(1, "Monitor", "running", 2048, 65536));
        final String out = this.run("cannon ps");
        assertTrue(out.contains("Monitor"), out);
        assertTrue(out.contains("2 KB of 64 KB"), out);
    }

    @Test
    void cannon_startsAProgramAndHandsOnTheRoomItWasAskedFor() {
        this.computer.add(CannonCommands.RUNTIME);
        assertTrue(this.run("cannon run Monitor.asm --heap 16M").contains("started"));
        assertEquals("Monitor.asm", this.computer.started);
        assertEquals(16, this.computer.startedHeap);
    }

    @Test
    void cannon_letsTheComputerDecideTheRoomWhenNoneIsAsked() {
        this.computer.add(CannonCommands.RUNTIME);
        this.run("cannon run Monitor.asm");
        assertEquals(0, this.computer.startedHeap);
    }

    @Test
    void cannon_stopsOneByTheNumberItWasListedUnder() {
        this.computer.add(CannonCommands.RUNTIME);
        assertTrue(this.run("cannon stop 3").contains("stopped 3"));
        assertEquals(3, this.computer.stopped);
        assertTrue(errored("cannon stop"));
    }

    @Test
    void cannon_saysHowItIsUsedWhenGivenSomethingElse() {
        this.computer.add(CannonCommands.RUNTIME);
        assertTrue(this.run("cannon frobnicate").contains("usage: cannon"));
    }

    /** A computer with a disk, a list of installed packages, and a note of what it was asked to run. */
    private static final class Fake implements CliComputer {

        private final Map<String, String> files = new LinkedHashMap<>();
        private final List<ProgramInfo> installed = new ArrayList<>();
        private final List<CannonProcess> running = new ArrayList<>();
        private String started;
        private int startedHeap = -1;
        private int stopped = -1;

        void add(final String id) {
            this.installed.add(new ProgramInfo(id.substring(id.indexOf(':') + 1), id));
        }

        @Override public String name() {
            return "TEST-PC";
        }

        @Override public String type() {
            return "Personal Computer";
        }

        @Override public String nodeId() {
            return "abc123";
        }

        @Override public boolean running() {
            return true;
        }

        @Override public long cpuCapacity() {
            return 500;
        }

        @Override public long ramBuffer() {
            return 256;
        }

        @Override public boolean onNetwork() {
            return false;
        }

        @Override public String networkId() {
            return "";
        }

        @Override public boolean isMainframe() {
            return false;
        }

        @Override public NetSummary network() {
            return new NetSummary(false, 0, 0, 0, 0, false);
        }

        @Override public List<Holding> find(final String item) {
            return List.of();
        }

        @Override public OpResult select(final String item, final long quantity) {
            return OpResult.fail("no network");
        }

        @Override public OpResult insert(final String item, final long quantity) {
            return OpResult.fail("no network");
        }

        @Override public OpResult craft(final String item, final long quantity) {
            return OpResult.fail("no network");
        }

        @Override public OpResult lock(final String item, final long quantity) {
            return OpResult.fail("no network");
        }

        @Override public OpResult unlock(final String item) {
            return OpResult.fail("no network");
        }

        @Override public List<StoredItem> locks() {
            return List.of();
        }

        @Override public List<ActiveOp> activeOps() {
            return List.of();
        }

        @Override public OpResult maintenance(final String action) {
            return OpResult.fail("no network");
        }

        @Override public List<String> peripherals() {
            return List.of();
        }

        @Override public List<ProgramInfo> programs() {
            return List.copyOf(this.installed);
        }

        @Override public OpResult install(final String programId) {
            return OpResult.fail("nothing to install from");
        }

        @Override public OpResult execute(final dev.jstech.computronics.program.iql.IqlOperation operation) {
            return OpResult.fail("no network");
        }

        @Override public List<StoredItem> query(final dev.jstech.computronics.program.iql.IqlCondition where,
                final String server, final int limit) {
            return List.of();
        }

        @Override public List<StoredItem> queryObject(final String object,
                final dev.jstech.computronics.program.iql.IqlCondition where, final String server,
                final int limit) {
            return List.of();
        }

        @Override public FsResult readFile(final String path) {
            final String held = this.files.get(path);
            return held == null ? FsResult.fail("no such file: " + path) : FsResult.content(held);
        }

        @Override public FsResult writeFile(final String path, final String content) {
            this.files.put(path, content);
            return FsResult.ok("wrote " + path);
        }

        @Override public List<CannonProcess> cannonProcesses() {
            return List.copyOf(this.running);
        }

        @Override public OpResult startCannon(final String path, final int heapMb) {
            this.started = path;
            this.startedHeap = heapMb;
            return OpResult.ok("started " + path);
        }

        @Override public OpResult stopCannon(final int id) {
            this.stopped = id;
            return OpResult.ok("stopped " + id);
        }
    }
}
