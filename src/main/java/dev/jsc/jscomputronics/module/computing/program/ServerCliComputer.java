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
import dev.jsc.jscomputronics.module.computing.os.KernelDef;
import dev.jsc.jscomputronics.module.computing.os.OsDef;
import dev.jsc.jscomputronics.module.computing.os.OsRegistry;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FileType;
import dev.jsc.jscomputronics.module.computing.os.fs.FsPaths;
import dev.jsc.jscomputronics.module.computing.program.cli.CliComputer;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlOperation;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlParseResult;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlParser;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlVerb;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    public List<StoredItem> query(final dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition where,
                                  final String server, final int limit) {
        final NetworkUuid net = host.networkUuid();
        if (net == null) {
            return List.of();
        }
        // WHERE server=X scopes the read to that server; every other field is evaluated per item, so the
        // full condition (qty < 100, name contains "ore", damaged = true, ...) really filters now.
        final String serverName = (server == null || server.isBlank())
                ? dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition.firstValue(where, "server")
                : server;
        final NetworkStorage storage;
        final String scopedServer;
        if (serverName == null || serverName.isBlank()) {
            storage = NetworkStorage.of(level, net);
            scopedServer = "";
        } else {
            final NodeUuid scoped = resolveServer(net, serverName);
            if (scoped == null) {
                return List.of(); // a WHERE server that names no server yields nothing
            }
            storage = NetworkStorage.ofServers(level, java.util.List.of(scoped));
            scopedServer = serverName;
        }
        // Filter by the condition, then sort by quantity and take the top rows: the limit applies after the
        // sort so the result is the largest holdings, not an arbitrary slice.
        return storage.query().entrySet().stream()
                .filter(entry -> where == null
                        || where.matches(rowOf(entry.getKey(), entry.getValue(), scopedServer)))
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(Math.max(limit, 0))
                .map(entry -> new StoredItem(entry.getKey().displayName().getString(), entry.getValue(),
                        location(net, entry.getKey(), scopedServer)))
                .toList();
    }

    /** Where an item lives: the scoped server, the single server holding it, or "N servers" across the net. */
    private String location(final NetworkUuid net, final StorageKey key, final String scopedServer) {
        if (!scopedServer.isEmpty()) {
            return scopedServer;
        }
        final java.util.Map<NodeUuid, Long> breakdown = NetworkStorage.of(level, net).breakdown(key);
        if (breakdown.size() == 1) {
            return ComputingPayloads.serverLabel(level, breakdown.keySet().iterator().next());
        }
        return breakdown.size() + " servers";
    }

    /** The fields a WHERE can test on an item row: item id, name, qty, server (scoped), damaged, durability. */
    private static java.util.function.Function<String, String> rowOf(final StorageKey key, final long qty,
                                                                     final String scopedServer) {
        return field -> switch (field.toLowerCase(java.util.Locale.ROOT)) {
            case "item" -> itemPath(key);
            case "name" -> key.displayName().getString();
            case "qty", "count", "amount" -> Long.toString(qty);
            case "server" -> scopedServer;
            case "damaged" -> Boolean.toString(key.stack(1).isDamaged());
            case "durability" -> durabilityPercent(key);
            default -> null; // an unknown field makes its comparison false, so the row is excluded
        };
    }

    private static String itemPath(final StorageKey key) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(key.item()).getPath();
    }

    private static String durabilityPercent(final StorageKey key) {
        final net.minecraft.world.item.ItemStack stack = key.stack(1);
        if (!stack.isDamageableItem() || stack.getMaxDamage() == 0) {
            return "100";
        }
        return Long.toString(Math.round(
                100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage()));
    }

    @Override
    public List<StoredItem> queryObject(final String object,
                                        final dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition where,
                                        final String server, final int limit) {
        return switch (object.toLowerCase(java.util.Locale.ROOT)) {
            case "items", "*" -> query(where, server, limit); // '*' means every item, like SELECT *
            case "servers" -> queryServers(limit);
            case "operations" -> queryOperations(limit);
            case "computers" -> queryComputers(limit);
            case "recipes" -> queryRecipes(limit);
            // disks: the schema object exists, the per-disk live data is not wired yet.
            default -> List.of();
        };
    }

    /** One row per network node: the Mainframe, then servers, personal computers, and crafting computers. */
    private List<StoredItem> queryComputers(final int limit) {
        final NetworkUuid net = host.networkUuid();
        if (net == null) {
            return List.of();
        }
        final NetworkSystem system = NetworkSystem.get(level);
        final List<StoredItem> out = new ArrayList<>();
        if (mainframe(net) != null) {
            out.add(new StoredItem("Mainframe", 1L));
        }
        for (final dev.jsc.jscomputronics.common.network.ServerNode server : system.serversOf(net)) {
            if (out.size() >= limit) {
                break;
            }
            out.add(new StoredItem(ComputingPayloads.serverLabel(level, server.nodeUuid()) + " (server)", 1L));
        }
        for (final var pc : system.personalComputersOf(net)) {
            if (out.size() >= limit) {
                break;
            }
            out.add(new StoredItem("PC-"
                    + dev.jsc.jscomputronics.common.util.ShortId.of(pc.nodeUuid().asString()) + " (pc)", 1L));
        }
        for (final var cc : system.craftingComputersOf(net)) {
            if (out.size() >= limit) {
                break;
            }
            out.add(new StoredItem("CC-"
                    + dev.jsc.jscomputronics.common.util.ShortId.of(cc.nodeUuid().asString()) + " (crafting)", 1L));
        }
        return out;
    }

    /** One row per craftable recipe known to the network: the result item and its output count. */
    private List<StoredItem> queryRecipes(final int limit) {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return List.of();
        }
        final List<StoredItem> out = new ArrayList<>();
        for (final var pattern : mainframe.networkPatterns()) {
            if (out.size() >= limit) {
                break;
            }
            final net.minecraft.world.item.ItemStack result = pattern.result();
            out.add(new StoredItem(result.getHoverName().getString(), result.getCount()));
        }
        return out;
    }

    /** One row per server: its label and the total item count it stores. */
    private List<StoredItem> queryServers(final int limit) {
        final NetworkUuid net = host.networkUuid();
        if (net == null) {
            return List.of();
        }
        final List<StoredItem> out = new ArrayList<>();
        for (final dev.jsc.jscomputronics.common.network.ServerNode srv
                : NetworkSystem.get(level).serversOf(net)) {
            final long used = NetworkStorage.ofServers(level, java.util.List.of(srv.nodeUuid()))
                    .query().values().stream().mapToLong(Long::longValue).sum();
            out.add(new StoredItem(ComputingPayloads.serverLabel(level, srv.nodeUuid()), used));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /** One row per in-flight operation: a "VERB item" label and how much it has moved so far. */
    private List<StoredItem> queryOperations(final int limit) {
        final List<StoredItem> out = new ArrayList<>();
        for (final ActiveOp op : activeOps()) {
            out.add(new StoredItem(op.type() + " " + op.item(), op.progress()));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
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
        final var op = mainframe.submitNetworkSelect(key, demand(quantity), host.localStorage(), "cli");
        if (op == null) {
            return OpResult.fail("could not start the SELECT");
        }
        return OpResult.ok("SELECT queued: " + qtyLabel(quantity) + " " + key.displayName().getString()
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
        final long take = Math.min(demand(quantity), held);
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
        final var op = mainframe.submitNetworkCraft(key, demand(quantity), true, "cli");
        if (op == null) {
            return OpResult.fail("no pattern crafts " + key.displayName().getString());
        }
        return OpResult.ok("CRAFT queued: " + qtyLabel(quantity) + " " + key.displayName().getString());
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
        if (program.id().equals(Programs.IQL_ENGINE)) {
            // The Engine is a service on the Mainframe, not a console-local app — install it there.
            return engineControl("install");
        }
        if (program.preinstalled()) {
            return OpResult.fail(program.commandName() + " is pre-installed on every computer");
        }
        // Install economy: an app needs its physical install medium in a linked drive — you cannot conjure
        // a program out of thin air.
        if (!hasInstallMediumFor(program.id())) {
            return OpResult.fail(program.commandName() + " needs its install disc in a linked drive");
        }
        // OS-capability gate: e.g. the NMS only runs on a full desktop OS (Panes), not MC-DOS/MC-NET.
        if (host instanceof dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity oc
                && !dev.jsc.jscomputronics.module.computing.os.OsRegistry.canHostRun(
                        oc.installedOsId(), program.id())) {
            return OpResult.fail(program.commandName() + " needs a more capable OS (a graphical desktop)");
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

    /** Whether a media reader linked to this computer holds a PROGRAM_INSTALL medium for {@code programId}. */
    private boolean hasInstallMediumFor(final ResourceLocation programId) {
        if (!(host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                .AbstractComputerBlockEntity computer)) {
            return false;
        }
        for (final long endpoint : computer.linkedEndpoints()) {
            if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                    instanceof dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity reader
                    && reader.insertedKind()
                            == dev.jsc.jscomputronics.module.computing.os.media.MediaKind.PROGRAM_INSTALL
                    && programId.equals(reader.insertedPayload())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public OpResult engineControl(final String action) {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return OpResult.fail("the network has no running Mainframe to host the IQL Engine");
        }
        return switch (action.toLowerCase(java.util.Locale.ROOT)) {
            case "install" -> mainframe.installIqlEngine()
                    ? OpResult.ok("IQL Engine installed on the Mainframe and started")
                    : OpResult.fail("the IQL Engine is already installed");
            case "start" -> mainframe.setIqlEngineRunning(true)
                    ? OpResult.ok("IQL Engine started")
                    : OpResult.fail(mainframe.isIqlEngineInstalled()
                            ? "the IQL Engine is already running" : "the IQL Engine is not installed");
            case "stop" -> mainframe.setIqlEngineRunning(false)
                    ? OpResult.ok("IQL Engine stopped")
                    : OpResult.fail(mainframe.isIqlEngineInstalled()
                            ? "the IQL Engine is already stopped" : "the IQL Engine is not installed");
            case "status", "" -> OpResult.ok("IQL Engine: " + engineState(mainframe));
            default -> OpResult.fail("usage: iqlengine install|start|stop|status");
        };
    }

    @Override
    public java.util.List<ServiceStatus> services() {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return java.util.List.of();
        }
        return java.util.List.of(new ServiceStatus("IQL Engine", engineState(mainframe)));
    }

    @Override
    public boolean iqlEngineInstalled() {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        return mainframe != null && mainframe.isIqlEngineInstalled();
    }

    private static String engineState(final MainframeBlockEntity mainframe) {
        if (!mainframe.isIqlEngineInstalled()) {
            return "not installed";
        }
        return mainframe.isIqlEngineRunning() ? "running" : "stopped";
    }

    @Override
    public OpResult execute(final IqlOperation op) {
        return switch (op.verb()) {
            case SELECT -> executeSelect(op);
            case INSERT -> executeInsert(op);
            case CRAFT -> craft(op.item(), op.quantity());
            case DELETE -> executeDestroy(op, "DELETE");
            case DROP -> executeDestroy(op, "DROP");
            case MOVE -> executeMove(op);
            case LOCK -> lock(op.item(), op.quantity());
            case UNLOCK -> unlock(op.item());
            case ANALYZE -> maintenance("analyze");
            case VACUUM -> maintenance("vacuum");
            case REINDEX -> maintenance("reindex");
            case QUERY, COUNT -> OpResult.fail("a read does not run as an operation");
        };
    }

    /** Safety cap on how many item types a single {@code *} operation expands to. */
    private static final int MAX_WILDCARD_TYPES = 256;

    /**
     * The keys an operation targets: a single resolved item, or every item type in scope (the whole network, or one
     * server) when the item is the {@code *} wildcard, capped at {@link #MAX_WILDCARD_TYPES}.
     */
    private List<StorageKey> keysFor(final String item, final NodeUuid scopeServer) {
        if (IqlOperation.ANY_ITEM.equals(item)) {
            final NetworkUuid net = host.networkUuid();
            if (net == null) {
                return List.of();
            }
            final NetworkStorage storage = scopeServer == null
                    ? NetworkStorage.of(level, net)
                    : NetworkStorage.ofServers(level, java.util.List.of(scopeServer));
            return storage.query().keySet().stream().limit(MAX_WILDCARD_TYPES).toList();
        }
        final StorageKey key = resolveKey(item);
        return key == null ? List.of() : List.of(key);
    }

    /** How an operation reads back: "N item types" for a {@code *}, else "qty item". */
    private static String describe(final IqlOperation op, final List<StorageKey> keys) {
        if (op.isAnyItem()) {
            return keys.size() + (keys.size() == 1 ? " item type" : " item types");
        }
        return qtyLabel(op.quantity()) + " " + keys.get(0).displayName().getString();
    }

    /**
     * INSERT from a named bus imports through that bus's external inventory; an INSERT with no bus source
     * pushes this computer's local storage into the network, as it always did (the source name, if any, is
     * then informational).
     */
    private OpResult executeInsert(final IqlOperation op) {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (op.from() != null && !op.from().isBlank() && mainframe != null) {
            final dev.jsc.jscomputronics.module.computing.block.part.NamedBus.Located bus =
                    dev.jsc.jscomputronics.module.computing.block.part.NamedBus.find(level, host.networkUuid(), op.from());
            if (bus != null) {
                return moveFromBus(op, mainframe, bus.port());
            }
        }
        return insert(op.item(), op.quantity());
    }

    private OpResult executeSelect(final IqlOperation op) {
        final NetworkUuid net = host.networkUuid();
        final MainframeBlockEntity mainframe = mainframe(net);
        if (mainframe == null || net == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        NodeUuid from = null;
        if (op.hasFrom()) {
            from = resolveServer(net, op.from());
            if (from == null) {
                return OpResult.fail("no server named '" + op.from() + "'");
            }
        }
        final List<StorageKey> keys = keysFor(op.item(), from);
        if (keys.isEmpty()) {
            return op.isAnyItem() ? OpResult.fail("nothing to select") : OpResult.fail("unknown item: " + op.item());
        }
        int queued = 0;
        for (final StorageKey key : keys) {
            // SELECT pulls from the whole network; SELECT ... FROM <server> is a move scoped to that server,
            // both landing in this computer's local storage.
            final var operation = from == null
                    ? mainframe.submitNetworkSelect(key, demand(op.quantity()), host.localStorage(), "cli")
                    : mainframe.submitNetworkMove(key, demand(op.quantity()), host.localStorage(), "cli",
                            java.util.Set.of(from));
            if (operation != null) {
                queued++;
            }
        }
        if (queued == 0) {
            return OpResult.fail("could not start the SELECT");
        }
        return OpResult.ok("SELECT queued: " + describe(op, keys)
                + (from == null ? "" : " from " + op.from()) + " -> local storage");
    }

    private OpResult executeDestroy(final IqlOperation op, final String verb) {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        // A DELETE that names a bus EXPORTS to that bus's external inventory (the "leaves the network" sense);
        // a DROP, or a DELETE with no target, trashes via a sink that accepts everything and keeps nothing.
        dev.jsc.jscomputronics.module.computing.storage.DataSink target = (k, amount, simulate) -> amount;
        if ("DELETE".equals(verb) && op.to() != null && !op.to().isBlank()) {
            final dev.jsc.jscomputronics.module.computing.block.part.NamedBus.Located bus =
                    dev.jsc.jscomputronics.module.computing.block.part.NamedBus.find(level, host.networkUuid(), op.to());
            if (bus == null) {
                return OpResult.fail("no bus named '" + op.to() + "'");
            }
            target = bus.port();
        }
        final List<StorageKey> keys = keysFor(op.item(), null);
        if (keys.isEmpty()) {
            return op.isAnyItem()
                    ? OpResult.ok("nothing to " + verb.toLowerCase(java.util.Locale.ROOT))
                    : OpResult.fail("unknown item: " + op.item());
        }
        // Only act on items the network actually holds, so a repeating job's DROP/DELETE becomes a quiet
        // no-op once the stock runs out, instead of a stream of failed operations polluting the log.
        final java.util.Map<StorageKey, Long> stock = NetworkStorage.of(level, host.networkUuid()).query();
        int queued = 0;
        for (final StorageKey key : keys) {
            if (stock.getOrDefault(key, 0L) <= 0L) {
                continue;
            }
            if (mainframe.submitNetworkDelete(key, demand(op.quantity()), target, "cli") != null) {
                queued++;
            }
        }
        return queued == 0 ? OpResult.ok("nothing to " + verb.toLowerCase(java.util.Locale.ROOT))
                : OpResult.ok(verb + " queued: " + describe(op, keys));
    }

    private OpResult executeMove(final IqlOperation op) {
        final NetworkUuid net = host.networkUuid();
        final MainframeBlockEntity mainframe = mainframe(net);
        if (mainframe == null || net == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        // A named bus on either side routes through its external inventory: TO a bus EXPORTS, FROM a bus
        // IMPORTS. Otherwise both sides name servers and it is an internal server-to-server move.
        final dev.jsc.jscomputronics.module.computing.block.part.NamedBus.Located toBus =
                dev.jsc.jscomputronics.module.computing.block.part.NamedBus.find(level, net, op.to());
        if (toBus != null) {
            return moveToBus(op, mainframe, toBus.port());
        }
        final dev.jsc.jscomputronics.module.computing.block.part.NamedBus.Located fromBus =
                dev.jsc.jscomputronics.module.computing.block.part.NamedBus.find(level, net, op.from());
        if (fromBus != null) {
            return moveFromBus(op, mainframe, fromBus.port());
        }
        final NodeUuid source = resolveServer(net, op.from());
        final NodeUuid dest = resolveServer(net, op.to());
        if (source == null) {
            return OpResult.fail("no server or bus named '" + op.from() + "'");
        }
        if (dest == null) {
            return OpResult.fail("no server or bus named '" + op.to() + "'");
        }
        final dev.jsc.jscomputronics.module.computing.storage.DataSink destSink = serverSink(dest);
        if (destSink == null) {
            return OpResult.fail("the destination server is unavailable");
        }
        final List<StorageKey> keys = keysFor(op.item(), source);
        if (keys.isEmpty()) {
            return op.isAnyItem() ? OpResult.fail("nothing to move") : OpResult.fail("unknown item: " + op.item());
        }
        int queued = 0;
        for (final StorageKey key : keys) {
            if (mainframe.submitNetworkMove(key, demand(op.quantity()), destSink, "cli",
                    java.util.Set.of(source)) != null) {
                queued++;
            }
        }
        return queued == 0 ? OpResult.fail("could not start the MOVE")
                : OpResult.ok("MOVE queued: " + describe(op, keys)
                        + " " + op.from() + " -> " + ComputingPayloads.serverLabel(level, dest));
    }

    /** Network -> a named bus's external inventory: a timed export, the same path the Export Bus uses. */
    private OpResult moveToBus(final IqlOperation op, final MainframeBlockEntity mainframe,
                               final dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort port) {
        if (port.isEmpty()) {
            return OpResult.fail("the bus '" + op.to() + "' touches no inventory");
        }
        final List<StorageKey> keys = keysFor(op.item(), null);
        if (keys.isEmpty()) {
            return op.isAnyItem() ? OpResult.ok("nothing to move") : OpResult.fail("unknown item: " + op.item());
        }
        final java.util.Map<StorageKey, Long> stock = NetworkStorage.of(level, host.networkUuid()).query();
        int queued = 0;
        for (final StorageKey key : keys) {
            if (stock.getOrDefault(key, 0L) <= 0L) {
                continue;
            }
            if (mainframe.submitNetworkDelete(key, demand(op.quantity()), port, "cli") != null) {
                queued++;
            }
        }
        return queued == 0 ? OpResult.ok("nothing to move to " + op.to())
                : OpResult.ok("MOVE queued: " + describe(op, keys) + " -> " + op.to());
    }

    /**
     * A named bus's external inventory -> network. Pulls from the bus and inserts into the network as a
     * timed operation; anything the network cannot hold is returned to the source, so nothing is lost.
     */
    private OpResult moveFromBus(final IqlOperation op, final MainframeBlockEntity mainframe,
                                 final dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort port) {
        if (port.isEmpty()) {
            return OpResult.fail("the bus '" + op.from() + "' touches no inventory");
        }
        final List<StorageKey> keys = op.isAnyItem() ? port.available() : keysFor(op.item(), null);
        if (keys.isEmpty()) {
            return op.isAnyItem() ? OpResult.ok("nothing to import") : OpResult.fail("unknown item: " + op.item());
        }
        final long perKey = demand(op.quantity());
        int queued = 0;
        for (final StorageKey key : keys) {
            final long avail = port.extract(key, perKey, true);
            if (avail <= 0L) {
                continue;
            }
            final long pulled = port.extract(key, avail, false);
            if (pulled <= 0L) {
                continue;
            }
            final dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation insert =
                    mainframe.submitNetworkInsert(key, pulled, "cli");
            if (insert != null) {
                insert.onSettle(() -> {
                    final long left = insert.leftover();
                    if (left > 0L) {
                        port.insert(key, left, false); // the network could not hold it all: return to the source
                    }
                });
                queued++;
            } else {
                port.insert(key, pulled, false); // dispatch failed (engine off): put it back, lose nothing
            }
        }
        return queued == 0 ? OpResult.ok("nothing to import from " + op.from())
                : OpResult.ok("MOVE queued: import from " + op.from());
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

    /** Resolves a parsed quantity to a concrete demand: ALL or unspecified means "as much as possible". */
    private static long demand(final long quantity) {
        return quantity <= 0L ? Long.MAX_VALUE : quantity;
    }

    /** How a quantity reads back to the player: a real count, or {@code "all"} for ALL/unspecified. */
    private static String qtyLabel(final long quantity) {
        return quantity <= 0L ? "all" : Long.toString(quantity);
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

    // --- filesystem -------------------------------------------------------------------------------

    /**
     * Resolved system-disk context: the disk {@link ItemStack} held by the hardware inventory and
     * the filesystem kind derived from the installed OS kernel.
     */
    private record DiskCtx(ItemStack disk, FilesystemKind kind) {}

    /**
     * Resolves the host's system disk and filesystem kind, or {@code null} when no bootable disk
     * is present, the OS has no known kernel, or the kernel's filesystem is {@link FilesystemKind#NONE}.
     */
    private DiskCtx resolveDiskCtx() {
        if (!(hostBlock instanceof AbstractComputerBlockEntity computer)) {
            return null;
        }
        final ItemStack disk = computer.systemDisk();
        if (disk.isEmpty()) {
            return null;
        }
        final OsDef os = computer.installedOs();
        if (os == null) {
            return null;
        }
        final KernelDef kernel = OsRegistry.getKernel(os.kernelId());
        final FilesystemKind kind = kernel != null ? kernel.filesystem() : FilesystemKind.NONE;
        if (kind == FilesystemKind.NONE) {
            return null;
        }
        return new DiskCtx(disk, kind);
    }

    @Override
    public FsResult listDisk(final String dir) {
        final DiskCtx ctx = resolveDiskCtx();
        if (ctx == null) {
            return FsResult.noOs();
        }
        final List<DiskFilesystem.FileEntry> raw = DiskFilesystem.list(ctx.disk(), dir, ctx.kind());
        final List<FsEntry> entries = new ArrayList<>(raw.size());
        for (final DiskFilesystem.FileEntry e : raw) {
            entries.add(new FsEntry(e.path(), e.type().extension(), e.weight(), e.readOnly()));
        }
        return FsResult.listing(entries);
    }

    @Override
    public FsResult readFile(final String path) {
        final DiskCtx ctx = resolveDiskCtx();
        if (ctx == null) {
            return FsResult.noOs();
        }
        final java.util.Optional<String> content = DiskFilesystem.read(ctx.disk(), path);
        if (content.isEmpty()) {
            // Distinguish a .dat rejection from a plain missing file for a cleaner error.
            final List<DiskFilesystem.FileEntry> all = DiskFilesystem.list(ctx.disk(), "", ctx.kind());
            final boolean isDat = all.stream().anyMatch(e -> e.path().equals(path) && e.readOnly());
            if (isDat) {
                return FsResult.fail(path + ": .dat files are read-only (use the Network Interactor to access items)");
            }
            return FsResult.fail(path + ": file not found");
        }
        return FsResult.content(content.get());
    }

    @Override
    public FsResult deleteFile(final String path) {
        final DiskCtx ctx = resolveDiskCtx();
        if (ctx == null) {
            return FsResult.noOs();
        }
        // Reject .dat entries before attempting deletion so we surface a clear message.
        final List<DiskFilesystem.FileEntry> all = DiskFilesystem.list(ctx.disk(), "", ctx.kind());
        final boolean isDat = all.stream().anyMatch(e -> e.path().equals(path) && e.readOnly());
        if (isDat) {
            return FsResult.fail(path + ": .dat files cannot be deleted (use the Network Interactor)");
        }
        final boolean deleted = DiskFilesystem.delete(ctx.disk(), path);
        if (!deleted) {
            return FsResult.fail(path + ": file not found");
        }
        // DiskFilesystem.delete mutated the component in-place on the stack that is stored inside
        // the AbstractComputerBlockEntity's ItemStackHandler. Mark the BE dirty so NBT is saved.
        if (hostBlock instanceof AbstractComputerBlockEntity computer) {
            computer.setChanged();
        }
        return FsResult.ok("deleted " + path);
    }

    @Override
    public FsResult runScript(final String path) {
        final DiskCtx ctx = resolveDiskCtx();
        if (ctx == null) {
            return FsResult.noOs();
        }
        // Check the extension first so the error names the right problem.
        final String ext = extensionOf(path);
        if (!"iql".equalsIgnoreCase(ext)) {
            return FsResult.fail(path + ": only .iql files can be run (got ." + (ext.isEmpty() ? "<none>" : ext) + ")");
        }
        final java.util.Optional<String> content = DiskFilesystem.read(ctx.disk(), path);
        if (content.isEmpty()) {
            return FsResult.fail(path + ": file not found");
        }
        // Parse and dispatch through the exact same path the 'operation' command uses.
        final IqlParseResult parsed = IqlParser.tryParse(content.get().trim());
        if (!parsed.ok()) {
            return FsResult.fail(path + ": syntax error: " + parsed.error());
        }
        final IqlOperation op = parsed.operation();
        // QUERY/COUNT are read operations that produce rows, not timed operations; they cannot be
        // dispatched via execute(). The caller should use 'operation' for those.
        if (op.verb() == IqlVerb.QUERY || op.verb() == IqlVerb.COUNT) {
            return FsResult.fail(path + ": QUERY/COUNT are not supported by 'run' — use 'operation' instead");
        }
        final OpResult result = execute(op);
        return FsResult.iqlResult(result);
    }

    @Override
    public FsResult writeFile(final String path, final String content) {
        final DiskCtx ctx = resolveDiskCtx();
        if (ctx == null) {
            return FsResult.noOs();
        }
        final FileType type = FileType.fromExtension(extensionOf(path)).orElse(null);
        if (type == null) {
            return FsResult.fail(path + ": unknown file type (use .txt/.iql/.cfg/.csv/.cmd)");
        }
        if (!type.userEditable()) {
            return FsResult.fail(path + ": ." + type.extension() + " files cannot be edited");
        }
        // Free space available, crediting back the file being overwritten so a same-size rewrite fits.
        long freeWeight = 0L;
        if (hostBlock instanceof AbstractComputerBlockEntity computer) {
            freeWeight = computer.systemDiskFreeWeight();
        }
        final long oldWeight = DiskFilesystem.read(ctx.disk(), path)
                .map(c -> FsPaths.sizeMbEq(c.getBytes(java.nio.charset.StandardCharsets.UTF_8).length))
                .orElse(0L);
        final DiskFilesystem.WriteResult result = DiskFilesystem.write(
                ctx.disk(), path, type, content, freeWeight + oldWeight, ctx.kind());
        return switch (result) {
            case OK -> {
                if (hostBlock instanceof AbstractComputerBlockEntity computer) {
                    computer.setChanged();
                }
                yield FsResult.ok("wrote " + path);
            }
            case INVALID_PATH -> FsResult.fail(path + ": invalid file name for this filesystem");
            case DISK_FULL -> FsResult.fail(path + ": not enough free space on the disk");
            case READ_ONLY -> FsResult.fail(path + ": ." + type.extension() + " is read-only");
        };
    }

    /** Returns the lowercase extension of a file path (after the last dot), or {@code ""} if none. */
    private static String extensionOf(final String path) {
        final int dot = path.lastIndexOf('.');
        return dot >= 0 && dot < path.length() - 1
                ? path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT)
                : "";
    }
}
