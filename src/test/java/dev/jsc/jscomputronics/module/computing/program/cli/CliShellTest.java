/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliShellTest {

    private CliShell shell;
    private FakeComputer computer;

    @BeforeEach
    void setUp() {
        shell = new CliShell(BuiltinCommands.all(), 52);
        computer = new FakeComputer();
    }

    private String joined(final String line) {
        final StringBuilder sb = new StringBuilder();
        for (final CliLine cliLine : shell.run(line, computer).lines()) {
            sb.append(cliLine.text()).append('\n');
        }
        return sb.toString();
    }

    private boolean anyStyle(final String line, final CliStyle style) {
        return shell.run(line, computer).lines().stream().anyMatch(l -> l.style() == style);
    }

    @Test
    void run_emptyLineProducesNoOutput() {
        assertTrue(shell.run("   ", computer).lines().isEmpty());
    }

    @Test
    void run_unknownCommandReportsError() {
        final CliShell.Response response = shell.run("frobnicate now", computer);
        assertTrue(response.lines().stream().anyMatch(l -> l.style() == CliStyle.ERROR));
        assertTrue(response.lines().get(0).text().contains("frobnicate"));
    }

    @Test
    void run_helpListsEveryRegisteredCommand() {
        final String out = joined("help");
        assertTrue(out.contains("operation"));
        assertTrue(out.contains("find"));
        assertTrue(out.contains("lock"));
    }

    @Test
    void run_helpForOneCommandShowsItsUsage() {
        assertTrue(joined("help operation").contains("<statement>"));
    }

    @Test
    void run_aliasResolvesToTheSameCommand() {
        // 'op' is an alias of operation; with no statement both print the same usage error.
        assertEquals(joined("operation"), joined("op"));
    }

    @Test
    void run_echoPrintsTheRest() {
        assertEquals("hello world\n", joined("echo hello world"));
    }

    @Test
    void run_clsSetsTheClearFlag() {
        assertTrue(shell.run("cls", computer).clearScreen());
        assertFalse(shell.run("echo x", computer).clearScreen());
    }

    @Test
    void run_bareDriveQualifierIsHandledAsADriveSwitch() {
        // "D:" must be routed to the drive switch, not reported as an unknown command.
        final CliShell.Response response = shell.run("D:", computer);
        assertFalse(response.lines().stream().anyMatch(l -> l.text().contains("command not found")),
                "a bare drive qualifier must not be treated as an unknown command");
    }

    @Test
    void run_unixAliasesAreNotRegistered() {
        // The shell uses DOS syntax; the old Unix aliases must not resolve to any command.
        for (final String unix : List.of("ls", "cat", "rm", "clear", "man", "ps", "locate")) {
            assertTrue(shell.find(unix) == null, unix + " must not be a registered command or alias");
        }
        // The DOS verbs must be present.
        for (final String dos : List.of("dir", "cd", "cls", "type", "del", "mkdir", "md",
                "rmdir", "rd", "copy", "move", "ren")) {
            assertFalse(shell.find(dos) == null, dos + " must be a registered command");
        }
    }

    @Test
    void run_statusReflectsTheComputerState() {
        computer.running = true;
        assertTrue(anyStyle("status", CliStyle.OK));
        computer.running = false;
        assertTrue(anyStyle("status", CliStyle.ERROR));
    }

    @Test
    void run_operationQueryOffNetworkErrors() {
        computer.onNetwork = false;
        assertTrue(anyStyle("operation query items", CliStyle.ERROR));
    }

    @Test
    void run_operationParsesAndExecutesEffectingVerb() {
        computer.onNetwork = true;
        joined("operation select 64 cobblestone");
        assertEquals("execute(SELECT,cobblestone,64)", computer.lastCall);
    }

    @Test
    void run_operationQueryReadsStorageInsteadOfExecuting() {
        computer.onNetwork = true;
        computer.stock.add(new CliComputer.StoredItem("cobblestone", 100));
        final String out = joined("operation query items");
        assertTrue(out.contains("cobblestone"));
    }

    @Test
    void run_operationReportsSyntaxErrors() {
        assertTrue(anyStyle("operation frobnicate 1 thing", CliStyle.ERROR));
    }

    @Test
    void run_installDelegatesToTheComputer() {
        joined("install nms");
        assertEquals("install(nms)", computer.lastCall);
    }

    @Test
    void run_maintenanceDelegatesToTheComputer() {
        joined("vacuum");
        assertEquals("maintenance(vacuum)", computer.lastCall);
    }

    @Test
    void run_commandNeverThrowsEvenIfTheComputerDoes() {
        computer.explode = true;
        final CliShell.Response response = shell.run("status", computer);
        assertTrue(response.lines().stream().anyMatch(l -> l.style() == CliStyle.ERROR));
    }

    /** An in-memory CliComputer that records the last effecting call so tests can assert on it. */
    private static final class FakeComputer implements CliComputer {
        boolean running;
        boolean onNetwork = true;
        boolean explode;
        String lastCall = "";
        final List<StoredItem> stock = new ArrayList<>();

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
            if (explode) {
                throw new IllegalStateException("boom");
            }
            return running;
        }

        @Override public long cpuCapacity() {
            return 1000;
        }

        @Override public long ramBuffer() {
            return 8192;
        }

        @Override public boolean onNetwork() {
            return onNetwork;
        }

        @Override public String networkId() {
            return "net7f3a";
        }

        @Override public boolean isMainframe() {
            return false;
        }

        @Override public NetSummary network() {
            return new NetSummary(onNetwork, 2, 1, 0, stock.size(), true);
        }

        @Override public List<StoredItem> query(
                final dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition where,
                final String server, final int limit) {
            final String filter = dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition
                    .itemNameFilter(where).toLowerCase();
            final List<StoredItem> out = new ArrayList<>();
            for (final StoredItem item : stock) {
                if (filter.isEmpty() || item.name().toLowerCase().contains(filter)) {
                    out.add(item);
                }
            }
            return out;
        }

        @Override public List<StoredItem> queryObject(final String object,
                final dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition where,
                final String server, final int limit) {
            return object.equalsIgnoreCase("items") ? query(where, server, limit) : List.of();
        }

        @Override public List<Holding> find(final String item) {
            return List.of();
        }

        @Override public OpResult select(final String item, final long quantity) {
            lastCall = "select(" + item + ", " + quantity + ")";
            return OpResult.ok("ok - pulling " + quantity + " " + item);
        }

        @Override public OpResult insert(final String item, final long quantity) {
            lastCall = "insert(" + item + ", " + quantity + ")";
            return OpResult.ok("ok");
        }

        @Override public OpResult craft(final String item, final long quantity) {
            lastCall = "craft(" + item + ", " + quantity + ")";
            return OpResult.ok("ok");
        }

        @Override public OpResult lock(final String item, final long quantity) {
            lastCall = "lock(" + item + ", " + quantity + ")";
            return OpResult.ok("ok");
        }

        @Override public OpResult unlock(final String item) {
            lastCall = "unlock(" + item + ")";
            return OpResult.ok("ok");
        }

        @Override public List<StoredItem> locks() {
            return List.of();
        }

        @Override public List<ActiveOp> activeOps() {
            return List.of();
        }

        @Override public OpResult maintenance(final String action) {
            lastCall = "maintenance(" + action + ")";
            return OpResult.ok("done");
        }

        @Override public List<String> peripherals() {
            return List.of();
        }

        @Override public List<ProgramInfo> programs() {
            return List.of(new ProgramInfo("cmd", "jsc:command_prompt"));
        }

        @Override public OpResult install(final String programId) {
            lastCall = "install(" + programId + ")";
            return OpResult.ok("installed " + programId);
        }

        @Override public OpResult execute(
                final dev.jsc.jscomputronics.module.computing.program.iql.IqlOperation operation) {
            lastCall = "execute(" + operation.verb() + "," + operation.item() + "," + operation.quantity() + ")";
            return OpResult.ok("queued " + operation.verb());
        }
    }
}
