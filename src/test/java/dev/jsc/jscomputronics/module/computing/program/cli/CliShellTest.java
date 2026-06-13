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
        assertTrue(out.contains("select"));
        assertTrue(out.contains("craft"));
        assertTrue(out.contains("query"));
    }

    @Test
    void run_helpForOneCommandShowsItsUsage() {
        assertTrue(joined("help select").contains("<quantity> <item>"));
    }

    @Test
    void run_aliasResolvesToTheSameCommand() {
        // 'q' is an alias of query; on an empty network both print the same "not on a network" error.
        assertEquals(joined("query"), joined("q"));
    }

    @Test
    void run_echoPrintsTheRest() {
        assertEquals("hello world\n", joined("echo hello world"));
    }

    @Test
    void run_clearSetsTheClearFlag() {
        assertTrue(shell.run("clear", computer).clearScreen());
        assertFalse(shell.run("echo x", computer).clearScreen());
    }

    @Test
    void run_statusReflectsTheComputerState() {
        computer.running = true;
        assertTrue(anyStyle("status", CliStyle.OK));
        computer.running = false;
        assertTrue(anyStyle("status", CliStyle.ERROR));
    }

    @Test
    void run_queryOffNetworkErrors() {
        computer.onNetwork = false;
        assertTrue(anyStyle("query", CliStyle.ERROR));
    }

    @Test
    void run_queryListsHeldItemsAndHonorsFilter() {
        computer.onNetwork = true;
        computer.stock.add(new CliComputer.StoredItem("cobblestone", 2304));
        computer.stock.add(new CliComputer.StoredItem("oak_planks", 64));
        assertTrue(joined("query").contains("cobblestone"));
        assertTrue(joined("query").contains("oak_planks"));
        // The fake honors the filter itself, mimicking the real server.
        final String filtered = joined("query cobble");
        assertTrue(filtered.contains("cobblestone"));
        assertFalse(filtered.contains("oak_planks"));
    }

    @Test
    void run_selectRequiresAPositiveQuantityAndItem() {
        computer.onNetwork = true;
        assertTrue(anyStyle("select", CliStyle.ERROR));
        assertTrue(anyStyle("select abc cobblestone", CliStyle.ERROR));
        assertTrue(anyStyle("select 0 cobblestone", CliStyle.ERROR));
        assertTrue(anyStyle("select 64", CliStyle.ERROR));
    }

    @Test
    void run_selectPassesQuantityAndItemThrough() {
        computer.onNetwork = true;
        final String out = joined("select 64 iron_ingot");
        assertEquals("select(iron_ingot, 64)", computer.lastCall);
        assertTrue(out.contains("ok"));
    }

    @Test
    void run_selectAcceptsAQuotedMultiWordItem() {
        computer.onNetwork = true;
        joined("select 4 \"oak planks\"");
        assertEquals("select(oak planks, 4)", computer.lastCall);
    }

    @Test
    void run_craftOffNetworkErrors() {
        computer.onNetwork = false;
        assertTrue(anyStyle("craft 8 hopper", CliStyle.ERROR));
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
        final String out = joined("operation query");
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

        @Override public List<StoredItem> query(final String filter, final String server, final int limit) {
            final List<StoredItem> out = new ArrayList<>();
            for (final StoredItem item : stock) {
                if (filter.isEmpty() || item.name().toLowerCase().contains(filter.toLowerCase())) {
                    out.add(item);
                }
            }
            return out;
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

        @Override public dev.jsc.jscomputronics.module.computing.program.sql.SqlDialect dialect() {
            return dev.jsc.jscomputronics.module.computing.program.sql.SqlDialect.SIMPLE;
        }

        @Override public OpResult execute(
                final dev.jsc.jscomputronics.module.computing.program.sql.SqlOperation operation) {
            lastCall = "execute(" + operation.verb() + "," + operation.item() + "," + operation.quantity() + ")";
            return OpResult.ok("queued " + operation.verb());
        }
    }
}
