/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program;

import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.util.ShortId;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.program.cli.CliComputer;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Backs the Command Prompt's {@link CliComputer} facade with a real computer and its network. Every command the shell runs ultimately calls one of these methods on the server; effecting verbs route through the same Mainframe operation dispatch the graphical terminal uses, so the CLI is a true alternative interface, not a parallel code path.
 */
public final class ServerCliComputer implements CliComputer {

    private final ComputerTerminalHost host;
    private final BlockEntity hostBlock;
    private final ServerLevel level;

    public ServerCliComputer(final ComputerTerminalHost host, final ServerLevel level) {
        this.host = host;
        this.hostBlock = (BlockEntity) host;
        this.level = level;
    }

    @Override
    public String name() {
        // The Mainframe has no custom name; its kind is shown by type() instead.
        if (hostBlock instanceof AbstractComputerBlockEntity computer) {
            return computer.customName();
        }
        return "";
    }

    @Override
    public String type() {
        if (hostBlock instanceof MainframeBlockEntity) {
            return "Mainframe";
        }
        if (hostBlock instanceof CraftingComputerBlockEntity) {
            return "Crafting Computer";
        }
        if (hostBlock instanceof PersonalComputerBlockEntity) {
            return "Personal Computer";
        }
        return "Computer";
    }

    @Override
    public String nodeId() {
        final NodeUuid node;
        if (hostBlock instanceof AbstractComputerBlockEntity computer) {
            node = computer.nodeUuid();
        } else if (hostBlock instanceof MainframeBlockEntity mainframe) {
            node = mainframe.nodeUuid();
        } else {
            node = null;
        }
        return node == null ? "------" : ShortId.of(node.asString());
    }

    @Override
    public boolean running() {
        return host.computerRunning();
    }

    @Override
    public long cpuCapacity() {
        return host.orchestrationCapacity();
    }

    @Override
    public long ramBuffer() {
        return host.computerRamBuffer();
    }

    @Override
    public boolean onNetwork() {
        return host.networkUuid() != null;
    }

    @Override
    public String networkId() {
        final NetworkUuid net = host.networkUuid();
        return net == null ? "" : ShortId.of(net.asString());
    }

    @Override
    public boolean isMainframe() {
        return host.isMainframeHost();
    }

    @Override
    public NetSummary network() {
        final NetworkUuid net = host.networkUuid();
        if (net == null) {
            return new NetSummary(false, 0, 0, 0, 0, false);
        }
        final NetworkSystem system = NetworkSystem.get(level);
        final int servers = system.serversOf(net).size();
        final int pcs = system.personalComputersOf(net).size();
        final int subframes = system.subframesOf(net).size();
        final MainframeBlockEntity mainframe = mainframe(net);
        final int types = mainframe == null ? 0 : mainframe.networkIndex().catalogSize();
        return new NetSummary(true, servers, pcs, subframes, types, mainframe != null);
    }

    @Override
    public List<StoredItem> query(final String filter, final String server, final int limit) {
        final NetworkUuid net = host.networkUuid();
        if (net == null) {
            return List.of();
        }
        final NetworkStorage storage;
        if (server == null || server.isBlank()) {
            storage = NetworkStorage.of(level, net);
        } else {
            final NodeUuid scoped = resolveServer(net, server);
            if (scoped == null) {
                return List.of(); // a WHERE server filter that names no server yields nothing
            }
            storage = NetworkStorage.ofServers(level, java.util.List.of(scoped));
        }
        final String needle = filter.toLowerCase(java.util.Locale.ROOT);
        // Filter, then sort by quantity, then take the top rows: the limit must apply after the sort so
        // the result is the largest holdings, and the whole catalog is not walked once the limit is met.
        return storage.query().entrySet().stream()
                .filter(entry -> needle.isEmpty()
                        || entry.getKey().displayName().getString().toLowerCase(java.util.Locale.ROOT)
                        .contains(needle))
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(Math.max(limit, 0))
                .map(entry -> new StoredItem(entry.getKey().displayName().getString(), entry.getValue()))
                .toList();
    }

    @Override
    public List<Holding> find(final String item) {
        final NetworkUuid net = host.networkUuid();
        final StorageKey key = resolveKey(item);
        if (net == null || key == null) {
            return List.of();
        }
        final Map<NodeUuid, Long> perServer = NetworkStorage.of(level, net).breakdown(key);
        final List<Holding> rows = new ArrayList<>();
        for (final Map.Entry<NodeUuid, Long> entry : perServer.entrySet()) {
            if (entry.getValue() > 0L) {
                rows.add(new Holding(ComputingPayloads.serverLabel(level, entry.getKey()), entry.getValue()));
            }
        }
        return rows;
    }

    @Override
    public OpResult select(final String item, final long quantity) {
        final StorageKey key = resolveKey(item);
        if (key == null) {
            return OpResult.fail("unknown item: " + item);
        }
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        final var op = mainframe.submitNetworkSelect(key, quantity, host.localStorage(), "cli");
        if (op == null) {
            return OpResult.fail("could not start the SELECT");
        }
        return OpResult.ok("SELECT queued: " + quantity + " " + key.displayName().getString()
                + " -> local storage");
    }

    @Override
    public OpResult insert(final String item, final long quantity) {
        final StorageKey key = resolveKey(item);
        if (key == null) {
            return OpResult.fail("unknown item: " + item);
        }
        final long held = host.localStore().count(key);
        if (held <= 0L) {
            return OpResult.fail("this computer holds no " + key.displayName().getString());
        }
        final long take = Math.min(quantity, held);
        final long taken = host.localStore().extract(key, take);
        if (taken <= 0L) {
            return OpResult.fail("nothing to push");
        }
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        final var op = mainframe == null ? null : mainframe.submitNetworkInsert(key, taken, "cli");
        if (op == null) {
            host.localStore().insert(key, taken); // no dispatcher: put it straight back, never lose it
            return OpResult.fail("the network has no running Mainframe");
        }
        op.onSettle(() -> {
            final long leftover = op.leftover();
            if (leftover > 0L) {
                host.localStore().insert(key, leftover);
            }
        });
        return OpResult.ok("INSERT queued: " + taken + " " + key.displayName().getString() + " -> network");
    }

    @Override
    public OpResult craft(final String item, final long quantity) {
        final StorageKey key = resolveKey(item);
        if (key == null) {
            return OpResult.fail("unknown item: " + item);
        }
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        final var op = mainframe.submitNetworkCraft(key, quantity, true, "cli");
        if (op == null) {
            return OpResult.fail("no pattern crafts " + key.displayName().getString());
        }
        return OpResult.ok("CRAFT queued: " + quantity + " " + key.displayName().getString());
    }

    @Override
    public OpResult lock(final String item, final long quantity) {
        final StorageKey key = resolveKey(item);
        if (key == null) {
            return OpResult.fail("unknown item: " + item);
        }
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        final long demand = quantity > 0L ? quantity : Long.MAX_VALUE; // 0 locks everything available
        final long held = mainframe.lockType(key, demand, null);
        if (held <= 0L) {
            return OpResult.fail(mainframe.networkIndex().isManuallyLocked(key)
                    ? key.displayName().getString() + " is already locked"
                    : "nothing to lock: the network holds no free " + key.displayName().getString());
        }
        return OpResult.ok("LOCK held " + held + " " + key.displayName().getString()
                + " (concurrent operations will wait)");
    }

    @Override
    public OpResult unlock(final String item) {
        final StorageKey key = resolveKey(item);
        if (key == null) {
            return OpResult.fail("unknown item: " + item);
        }
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        final long released = mainframe.unlockType(key);
        if (released <= 0L) {
            return OpResult.fail(key.displayName().getString() + " is not locked");
        }
        return OpResult.ok("UNLOCK released " + released + " " + key.displayName().getString());
    }

    @Override
    public List<StoredItem> locks() {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return List.of();
        }
        final List<StoredItem> rows = new ArrayList<>();
        mainframe.lockedTypes().forEach((key, amount) ->
                rows.add(new StoredItem(key.displayName().getString(), amount)));
        return rows;
    }

    @Override
    public List<ActiveOp> activeOps() {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return List.of();
        }
        final List<ActiveOp> rows = new ArrayList<>();
        for (final OperationRecord record : mainframe.activeOperationRecords()) {
            rows.add(new ActiveOp(opType(record.type()), record.name().getString(),
                    record.moved(), record.requested(), opStatus(record.status())));
        }
        return rows;
    }

    @Override
    public OpResult maintenance(final String action) {
        if (!host.isMainframeHost()) {
            return OpResult.fail("maintenance runs on the Mainframe only");
        }
        final NetworkUuid net = host.networkUuid();
        final MainframeBlockEntity mainframe = mainframe(net);
        if (mainframe == null || net == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        final var index = mainframe.networkIndex();
        return switch (action) {
            case "analyze" -> {
                index.analyzeIncremental(level, net);
                yield OpResult.ok("ANALYZE complete - " + index.catalogSize() + " types reconciled");
            }
            case "reindex" -> {
                index.rebuild(level, net);
                yield OpResult.ok("REINDEX complete - catalog rebuilt from disks");
            }
            case "vacuum" -> {
                final int freed = index.vacuum(level, net);
                yield OpResult.ok("VACUUM freed " + freed + (freed == 1 ? " ghost entry" : " ghost entries"));
            }
            default -> OpResult.fail("unknown maintenance action: " + action);
        };
    }

    @Override
    public List<String> peripherals() {
        if (hostBlock instanceof dev.jsc.jscomputronics.common.peripheral.PeripheralOwnerSupport owner) {
            final List<String> rows = new ArrayList<>();
            for (final long endpoint : owner.peripheralEndpoints()) {
                final BlockPos pos = BlockPos.of(endpoint);
                rows.add(level.getBlockState(pos).getBlock().getName().getString()
                        + " @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
            }
            return rows;
        }
        return List.of();
    }

    @Override
    public List<ProgramInfo> programs() {
        final List<ProgramInfo> out = new ArrayList<>(Programs.installedInfo()); // the pre-installed set
        final ComputerConsoleState console = host.console();
        if (console != null) {
            for (final String id : console.installed()) {
                final Program program = Programs.get(ResourceLocation.tryParse(id));
                if (program != null && !program.preinstalled()) {
                    out.add(new ProgramInfo(program.commandName(), program.id().toString()));
                }
            }
        }
        return out;
    }

    @Override
    public OpResult install(final String programId) {
        final ResourceLocation location = ResourceLocation.tryParse(
                programId.contains(":") ? programId.toLowerCase(java.util.Locale.ROOT)
                        : "jsc:" + programId.toLowerCase(java.util.Locale.ROOT));
        final Program program = location == null ? null : Programs.get(location);
        if (program == null) {
            return OpResult.fail("no such program: " + programId);
        }
        if (program.preinstalled()) {
            return OpResult.fail(program.commandName() + " is pre-installed on every computer");
        }
        final ComputerConsoleState console = host.console();
        if (console == null) {
            return OpResult.fail("this computer cannot store installed programs");
        }
        if (!console.install(program.id().toString())) {
            return OpResult.fail(program.commandName() + " is already installed");
        }
        hostBlock.setChanged();
        return OpResult.ok("installed " + program.commandName());
    }

    @Override
    public dev.jsc.jscomputronics.module.computing.program.sql.SqlDialect dialect() {
        return ProgramSettings.sqlDialect();
    }

    @Override
    public OpResult execute(final dev.jsc.jscomputronics.module.computing.program.sql.SqlOperation op) {
        return switch (op.verb()) {
            case SELECT -> select(op.item(), op.quantity());
            case INSERT -> insert(op.item(), op.quantity());
            case CRAFT -> craft(op.item(), op.quantity());
            case DELETE -> executeDelete(op);
            case MOVE -> executeMove(op);
            case QUERY -> OpResult.fail("a query reads the network; it does not run as an operation");
        };
    }

    private OpResult executeDelete(final dev.jsc.jscomputronics.module.computing.program.sql.SqlOperation op) {
        final StorageKey key = resolveKey(op.item());
        if (key == null) {
            return OpResult.fail("unknown item: " + op.item());
        }
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        // DELETE extracts and discards: a sink that accepts everything and keeps nothing.
        final dev.jsc.jscomputronics.module.computing.storage.DataSink voidSink =
                (k, amount, simulate) -> amount;
        final var operation = mainframe.submitNetworkDelete(key, op.quantity(), voidSink, "cli");
        return operation == null ? OpResult.fail("could not start the DELETE")
                : OpResult.ok("DELETE queued: " + op.quantity() + " " + key.displayName().getString());
    }

    private OpResult executeMove(final dev.jsc.jscomputronics.module.computing.program.sql.SqlOperation op) {
        final StorageKey key = resolveKey(op.item());
        if (key == null) {
            return OpResult.fail("unknown item: " + op.item());
        }
        final NetworkUuid net = host.networkUuid();
        final MainframeBlockEntity mainframe = mainframe(net);
        if (mainframe == null || net == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        final NodeUuid source = resolveServer(net, op.source());
        final NodeUuid dest = resolveServer(net, op.dest());
        if (source == null) {
            return OpResult.fail("no server named '" + op.source() + "'");
        }
        if (dest == null) {
            return OpResult.fail("no server named '" + op.dest() + "'");
        }
        final dev.jsc.jscomputronics.module.computing.storage.DataSink destSink = serverSink(dest);
        if (destSink == null) {
            return OpResult.fail("the destination server is unavailable");
        }
        final var operation = mainframe.submitNetworkMove(key, op.quantity(), destSink, "cli",
                java.util.Set.of(source));
        return operation == null ? OpResult.fail("could not start the MOVE")
                : OpResult.ok("MOVE queued: " + op.quantity() + " " + key.displayName().getString()
                        + " -> " + ComputingPayloads.serverLabel(level, dest));
    }

    private NodeUuid resolveServer(final NetworkUuid net, final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (final dev.jsc.jscomputronics.common.network.ServerNode server
                : NetworkSystem.get(level).serversOf(net)) {
            if (ComputingPayloads.serverLabel(level, server.nodeUuid()).equalsIgnoreCase(name)) {
                return server.nodeUuid();
            }
        }
        return null;
    }

    private dev.jsc.jscomputronics.module.computing.storage.DataSink serverSink(final NodeUuid node) {
        return NetworkSystem.get(level).locationOf(node)
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                        instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack
                        ? (dev.jsc.jscomputronics.module.computing.storage.DataSink)
                                new dev.jsc.jscomputronics.module.computing.storage.StoreSink(
                                        rack.getServerStorage(loc.slot()))
                        : null)
                .orElse(null);
    }

    // --- helpers ----------------------------------------------------------------------------------

    private MainframeBlockEntity mainframe(final NetworkUuid net) {
        if (net == null) {
            return null;
        }
        return NetworkSystem.get(level).mainframePositionOf(net)
                .map(pos -> level.getBlockEntity(BlockPos.of(pos)) instanceof MainframeBlockEntity mf ? mf : null)
                .orElse(null);
    }

    private StorageKey resolveKey(final String name) {
        final Item item = resolveItem(name);
        return item == null ? null : StorageKey.of(item);
    }

    private Item resolveItem(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        final String id = name.contains(":") ? name : "minecraft:" + name;
        final ResourceLocation location = ResourceLocation.tryParse(id.toLowerCase(java.util.Locale.ROOT));
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }

    private static String opType(final byte type) {
        return switch (type) {
            case OperationRecord.TYPE_SELECT -> "SELECT";
            case OperationRecord.TYPE_INSERT -> "INSERT";
            case OperationRecord.TYPE_DELETE -> "DELETE";
            case OperationRecord.TYPE_MOVE -> "MOVE";
            case OperationRecord.TYPE_CRAFT -> "CRAFT";
            case OperationRecord.TYPE_ANALYZE -> "ANALYZE";
            case OperationRecord.TYPE_REINDEX -> "REINDEX";
            case OperationRecord.TYPE_VACUUM -> "VACUUM";
            case OperationRecord.TYPE_DROP -> "DROP";
            default -> "OP";
        };
    }

    private static String opStatus(final byte status) {
        return switch (status) {
            case OperationRecord.STATUS_COMPLETED -> "done";
            case OperationRecord.STATUS_PARTIAL -> "partial";
            case OperationRecord.STATUS_FAILED -> "failed";
            case OperationRecord.STATUS_PROCESSING -> "running";
            case OperationRecord.STATUS_WAITING -> "waiting";
            case OperationRecord.STATUS_RESOURCE_LOCKED -> "locked";
            case OperationRecord.STATUS_PENDING -> "pending";
            case OperationRecord.STATUS_DISCARDED -> "discarded";
            default -> "?";
        };
    }
}
