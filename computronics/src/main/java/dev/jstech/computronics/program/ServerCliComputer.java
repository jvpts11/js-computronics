/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program;

import dev.jstech.computronics.blockentity.AbstractComputerBlockEntity;
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computronics.cannon.machine.CannonProcesses;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.blockentity.PersonalComputerBlockEntity;
import dev.jstech.computronics.operation.MoveLabels;
import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.computronics.operation.payload.ComputingPayloads;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.os.FilesystemKind;
import dev.jstech.computronics.os.KernelDef;
import dev.jstech.computronics.os.OsDef;
import dev.jstech.computronics.os.OsHost;
import dev.jstech.computronics.os.OsRegistry;
import dev.jstech.computronics.os.fs.DiskFilesystem;
import dev.jstech.computronics.os.fs.FileType;
import dev.jstech.computronics.os.fs.FsPaths;
import dev.jstech.computronics.program.cli.CliComputer;
import dev.jstech.computronics.program.cli.DosPath;
import dev.jstech.computronics.program.iql.IqlOperation;
import dev.jstech.computronics.program.iql.IqlParseResult;
import dev.jstech.computronics.program.iql.IqlParser;
import dev.jstech.computronics.program.iql.IqlVerb;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.computronics.terminal.ComputerTerminalHost;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.util.ShortId;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
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
        if (hostBlock instanceof OsHost computer) {
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
        if (hostBlock instanceof dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity) {
            return "Cluster Management Computer";
        }
        return "Computer";
    }

    @Override
    public Object hostBlock() {
        return hostBlock;
    }

    @Override
    public String nodeId() {
        final NodeUuid node;
        if (hostBlock instanceof OsHost computer) {
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

    // ---- Remote shells ---------------------------------------------------------------------------

    /**
     * Every machine on this network a remote shell can reach, keyed by host name: the Mainframe, the
     * personal computers, and the servers in their racks. The local machine is left out — you cannot
     * ssh into the terminal you are already sitting at.
     */
    /** The reachable machines by host name, for callers outside the CLI (Remote Control's list). */
    public Map<String, BlockEntity> remoteMachines() {
        return reachableMachines();
    }

    private Map<String, BlockEntity> reachableMachines() {
        final Map<String, BlockEntity> out = new java.util.LinkedHashMap<>();
        final NetworkUuid network = host.networkUuid();
        if (network == null) {
            return out;
        }
        final NetworkSystem system = NetworkSystem.get(level);
        final java.util.List<BlockEntity> candidates = new ArrayList<>();
        final MainframeBlockEntity mainframe = mainframe(network);
        if (mainframe != null) {
            candidates.add(mainframe);
        }
        for (final NetworkSystem.PersonalComputerNode pc : system.personalComputersOf(network)) {
            if (level.getBlockEntity(net.minecraft.core.BlockPos.of(pc.pos()))
                    instanceof PersonalComputerBlockEntity be) {
                candidates.add(be);
            }
        }
        for (final dev.jstech.core.network.ServerNode server : system.serversOf(network)) {
            system.locationOf(server.nodeUuid()).ifPresent(loc -> {
                if (level.getBlockEntity(net.minecraft.core.BlockPos.of(loc.rackPos()))
                        instanceof dev.jstech.computronics.blockentity
                                .ServerRackBlockEntity rack) {
                    candidates.add(rack);
                }
            });
        }
        for (final BlockEntity candidate : candidates) {
            if (candidate == hostBlock || !(candidate instanceof ComputerTerminalHost terminalHost)) {
                continue;
            }
            final String hostname = new ServerCliComputer(terminalHost, level).hostname();
            // A duplicate host name keeps the first machine found, the way a name collision would.
            out.putIfAbsent(hostname, candidate);
        }
        return out;
    }

    @Override
    public List<RemoteHost> reachableHosts() {
        final List<RemoteHost> hosts = new ArrayList<>();
        reachableMachines().forEach((hostname, machine) -> {
            final ServerCliComputer remote = new ServerCliComputer((ComputerTerminalHost) machine, level);
            final dev.jstech.computronics.os.OsDef os = remote.installedOsDef();
            hosts.add(new RemoteHost(hostname, remote.name(), remote.nodeId(),
                    os == null ? "" : os.displayName(), remote.type(), remote.running()));
        });
        return hosts;
    }

    /**
     * Resolves what the player typed to one machine. A host name, the machine's own name and the
     * head of its node id all address it; an OS name works too, but only while it picks out exactly
     * one machine — two Debian servers make "debian" ambiguous, and saying so is more useful than
     * guessing.
     */
    private Map<String, BlockEntity> matchMachines(final String wanted) {
        final String needle = wanted == null ? "" : wanted.trim().toLowerCase(java.util.Locale.ROOT);
        final Map<String, BlockEntity> matches = new java.util.LinkedHashMap<>();
        if (needle.isEmpty()) {
            return matches;
        }
        reachableMachines().forEach((hostname, machine) -> {
            final ServerCliComputer remote = new ServerCliComputer((ComputerTerminalHost) machine, level);
            final dev.jstech.computronics.os.OsDef os = remote.installedOsDef();
            final boolean hit = hostname.equalsIgnoreCase(needle)
                    || remote.name().equalsIgnoreCase(needle)
                    || remote.nodeId().equalsIgnoreCase(needle)
                    || (os != null && (os.displayName().equalsIgnoreCase(needle)
                            || os.id().getPath().equalsIgnoreCase(needle)));
            if (hit) {
                matches.put(hostname, machine);
            }
        });
        return matches;
    }

    @Override
    public OpResult sshConnect(final String hostname) {
        final ComputerConsoleState console = host.console();
        if (console == null) {
            return OpResult.fail("ssh: this terminal keeps no session");
        }
        final Map<String, BlockEntity> matches = matchMachines(hostname);
        if (matches.isEmpty()) {
            return OpResult.fail("ssh: " + hostname + ": host not found on this network");
        }
        if (matches.size() > 1) {
            return OpResult.fail("ssh: " + hostname + " matches " + matches.size() + " machines ("
                    + String.join(", ", matches.keySet()) + ") - use the host name or node id");
        }
        final BlockEntity target = matches.values().iterator().next();
        final ServerCliComputer remote = new ServerCliComputer((ComputerTerminalHost) target, level);
        if (!remote.running()) {
            return OpResult.fail("ssh: connect to host " + hostname + ": machine is powered off");
        }
        console.setSshTarget(target.getBlockPos().asLong());
        return OpResult.ok("Connected to " + hostname + ". Type exit to return.");
    }

    @Override
    public OpResult sshDisconnect() {
        final ComputerConsoleState console = host.console();
        if (console == null || console.sshTarget() == null) {
            return OpResult.fail("exit: not connected - close the window to leave this terminal");
        }
        console.setSshTarget(null);
        return OpResult.ok("Connection closed.");
    }

    @Override
    public String sshSession() {
        final ComputerConsoleState console = host.console();
        return console == null || console.sshTarget() == null ? "" : hostname();
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
    public List<StoredItem> query(final dev.jstech.computronics.program.iql.IqlCondition where,
                                  final String server, final int limit) {
        final NetworkUuid net = host.networkUuid();
        if (net == null) {
            return List.of();
        }
        // WHERE server=X scopes the read to that server; every other field is evaluated per item, so the
        // full condition (qty < 100, name contains "ore", damaged = true, ...) really filters now.
        final String serverName = (server == null || server.isBlank())
                ? dev.jstech.computronics.program.iql.IqlCondition.firstValue(where, "server")
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
                                        final dev.jstech.computronics.program.iql.IqlCondition where,
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
        for (final dev.jstech.core.network.ServerNode server : system.serversOf(net)) {
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
                    + dev.jstech.core.util.ShortId.of(pc.nodeUuid().asString()) + " (pc)", 1L));
        }
        for (final var cc : system.craftingComputersOf(net)) {
            if (out.size() >= limit) {
                break;
            }
            out.add(new StoredItem("CC-"
                    + dev.jstech.core.util.ShortId.of(cc.nodeUuid().asString()) + " (crafting)", 1L));
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
        for (final dev.jstech.core.network.ServerNode srv
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

    /**
     * One row per operation: the in-flight ones first ("VERB item [STATUS]" and how much moved so far), then
     * the settled ones from the Mainframe's log, newest first, so a craft that finished a moment ago is still
     * there to be read.
     */
    private List<StoredItem> queryOperations(final int limit) {
        final List<StoredItem> out = new ArrayList<>();
        for (final ActiveOp op : activeOps()) {
            out.add(new StoredItem(op.type() + " " + op.item() + " [" + op.status() + "]", op.progress()));
            if (out.size() >= limit) {
                return out;
            }
        }
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe != null) {
            for (final OperationRecord record : mainframe.recentOperations()) {
                out.add(new StoredItem(opType(record.type()) + " " + record.name().getString()
                        + " [" + opStatus(record.status()) + "]", record.moved()));
                if (out.size() >= limit) {
                    break;
                }
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
        final var op = mainframe.submitNetworkSelect(key, demand(quantity), host.localStorage(),
                host.originLabel(MoveLabels.SHELL));
        if (op == null) {
            return OpResult.fail("could not start the SELECT");
        }
        op.abortWhen(hostGone());
        return OpResult.ok("SELECT queued: " + qtyLabel(quantity) + " " + key.displayName().getString()
                + " -> local storage");
    }

    /** True once this computer has left the world: a pull into its storage stops there instead of feeding a ghost. */
    private java.util.function.BooleanSupplier hostGone() {
        return host instanceof BlockEntity be ? be::isRemoved : () -> false;
    }

    @Override
    public List<OperationStat> operationStats() {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return List.of();
        }
        final List<OperationStat> rows = new ArrayList<>();
        for (final var summary : mainframe.statistics().summaries(level.getGameTime())) {
            rows.add(new OperationStat(opType((byte) summary.type()), summary.count(), summary.averageWait(),
                    summary.averageRun(), summary.shortfallPercent(), summary.moved()));
        }
        return rows;
    }

    @Override
    public int peakOperationsToday() {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        return mainframe == null ? 0 : mainframe.statistics().peakConcurrentLastDay(level.getGameTime());
    }

    @Override
    public OpResult cancelOperation(final String id) {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        final String wanted = id.trim().toLowerCase(java.util.Locale.ROOT);
        if (wanted.isEmpty()) {
            return OpResult.fail("usage: cancel <id>   (see 'ops')");
        }
        for (final dev.jstech.computronics.operation.NetworkOperation operation : mainframe.liveOperations()) {
            final String full = operation.operationId().toString();
            // The prompt shows the short id; accept it, or any longer prefix of the full id.
            if (full.startsWith(wanted) && wanted.length() >= ShortId.of(full).length()) {
                final OperationRecord record = operation.liveRecord();
                if (!mainframe.cancelOperation(operation.operationId())) {
                    return OpResult.fail("operation " + ShortId.of(full) + " has already settled");
                }
                return OpResult.ok("cancelled " + opType(record.type()) + " " + record.name().getString());
            }
        }
        return OpResult.fail("no operation " + wanted + " in flight (see 'ops')");
    }

    @Override
    public OpResult insert(final String item, final long quantity) {
        return insert(item, quantity, dev.jstech.core.operation.OperationPriority.DEFAULT);
    }

    private OpResult insert(final String item, final long quantity,
                            final dev.jstech.core.operation.OperationPriority priority) {
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
        final var op = mainframe == null ? null
                : mainframe.submitNetworkInsert(key, taken, host.originLabel(MoveLabels.SHELL));
        if (op == null) {
            host.localStore().insert(key, taken); // no dispatcher: put it straight back, never lose it
            return OpResult.fail("the network has no running Mainframe");
        }
        op.setPriority(priority);
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
        return craft(item, quantity, dev.jstech.core.operation.OperationPriority.DEFAULT);
    }

    private OpResult craft(final String item, final long quantity,
                           final dev.jstech.core.operation.OperationPriority priority) {
        final StorageKey key = resolveKey(item);
        if (key == null) {
            return OpResult.fail("unknown item: " + item);
        }
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return OpResult.fail("the network has no running Mainframe");
        }
        // Route through the shared entry point so the CLI and IQL craft a machine or multi-stage recipe
        // directly (not only a bench-planned tree), exactly as the terminal and Network Interactor do.
        final var op = mainframe.submitCraftRequest(key, demand(quantity), true,
                host.originLabel(MoveLabels.SHELL), null);
        if (op == null) {
            return OpResult.fail("no pattern crafts " + key.displayName().getString());
        }
        op.setPriority(priority);
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
            rows.add(new ActiveOp(ShortId.of(record.id().toString()), opType(record.type()),
                    record.name().getString(), record.moved(), record.requested(), opStatus(record.status()),
                    record.priority().label()));
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
                // The disks are read now; the catalog is built off the tick and swapped in a tick or two later.
                mainframe.reindexAsync(null);
                yield OpResult.ok("REINDEX started - rebuilding the catalog from disks");
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
        if (hostBlock instanceof dev.jstech.core.peripheral.PeripheralOwnerSupport owner) {
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
        final List<ProgramInfo> out = new ArrayList<>();
        // Pre-installed programs the host's platform supports, so an MC-DOS listing does not show the Frames
        // desktop apps (which are pre-installed only on the Frames platform).
        final dev.jstech.computronics.os.Platform platform = hostPlatform();
        for (final dev.jstech.computronics.os.ProgramSpec spec : Programs.installed()) {
            if (platform == null || spec.platforms().contains(platform)) {
                out.add(new ProgramInfo(spec.commandName(), spec.id().toString()));
            }
        }
        final ComputerConsoleState console = host.console();
        if (console != null) {
            for (final String id : console.installed()) {
                final var program = Programs.get(ResourceLocation.tryParse(id));
                if (program != null && !program.preinstalled()) {
                    out.add(new ProgramInfo(program.commandName(), program.id().toString()));
                }
            }
        }
        return out;
    }

    /** The platform of the OS installed on the host computer, or {@code null} when it cannot be resolved. */
    private dev.jstech.computronics.os.Platform hostPlatform() {
        if (host instanceof dev.jstech.computronics.os.OsHost oc) {
            final dev.jstech.computronics.os.OsDef os =
                    dev.jstech.computronics.os.OsRegistry.getOs(oc.installedOsId());
            return os == null ? null : os.platform();
        }
        return null;
    }

    @Override
    public OpResult install(final String programId) {
        final ResourceLocation location = ResourceLocation.tryParse(
                programId.contains(":") ? programId.toLowerCase(java.util.Locale.ROOT)
                        : "jsc:" + programId.toLowerCase(java.util.Locale.ROOT));
        final dev.jstech.computronics.os.ProgramSpec program =
                location == null ? null : Programs.get(location);
        if (program == null) {
            return OpResult.fail("no such program: " + programId);
        }
        if (program.id().equals(Programs.IQL_ENGINE)) {
            // The Engine is a service on the Mainframe, not a console-local app — install it there.
            return engineControl("install");
        }
        // The other Mainframe services flip their agent flag, exactly like the mirror-based package path:
        // marking only the console entry would leave the service itself off (the bug that made a Mirror
        // installed from its disc unable to serve packages).
        if (hostBlock instanceof MainframeBlockEntity mainframe
                && program.kind() == dev.jstech.computronics.os.ProgramKind.SERVICE) {
            if (!hasInstallMediumFor(program.id())) {
                return OpResult.fail(program.commandName() + " needs its install disc in a linked drive");
            }
            final boolean done = switch (program.id().getPath()) {
                case "automation_engine" -> mainframe.installAutomationEngine();
                case "mirror" -> mainframe.installMirror();
                default -> host.console() != null && host.console().install(program.id().toString());
            };
            if (host.console() != null) {
                host.console().install(program.id().toString());
            }
            hostBlock.setChanged();
            return done ? OpResult.ok("Setting up " + program.commandName() + " ... done")
                    : OpResult.fail(program.commandName() + " is already installed");
        }
        if (program.preinstalled()) {
            return OpResult.fail(program.commandName() + " is pre-installed on every computer");
        }
        final OpResult tooOld = eraGate(program);
        if (tooOld != null) {
            return tooOld;
        }
        // Install economy: an app needs its physical install medium in a linked drive — you cannot conjure
        // a program out of thin air.
        if (!hasInstallMediumFor(program.id())) {
            return OpResult.fail(program.commandName() + " needs its install disc in a linked drive");
        }
        // Program install gate: the OS platform must be supported and the hardware must meet the program's
        // CPU/VRAM/disk minimums (e.g. the NMS installs only on the Frames platform).
        if (host instanceof dev.jstech.computronics.os.OsHost oc
                && !dev.jstech.computronics.os.OsRegistry.canInstallProgram(
                        oc.installedOsId(), program.id(),
                        oc.maxCpuMhz(), oc.totalVramMb(), oc.systemDiskFreeMb())) {
            return OpResult.fail(program.commandName() + " cannot install on this computer's OS or hardware");
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

    /**
     * Refuses a program the machine is too old to run, or {@code null} when the era is fine. Software
     * cannot predate its hardware generation: a desktop of the 2010s does not install on a machine of
     * the 1990s, however much disk it has free. Both install paths (the package manager and the install
     * medium) go through this, so neither is a way around the rule.
     */
    @org.jetbrains.annotations.Nullable
    private OpResult eraGate(final dev.jstech.computronics.os.ProgramSpec spec) {
        if (spec.minEra() == dev.jstech.core.tier.HardwareEra.VINTAGE) {
            return null; // no requirement
        }
        // displayEra, not installedEra: a Vintage or Legacy chassis IS that generation whatever board
        // sits in it, and that chassis is the only way a machine of an older era exists right now.
        final dev.jstech.core.tier.HardwareEra era =
                hostBlock instanceof OsHost computer ? computer.displayEra() : null;
        if (era != null && dev.jstech.computronics.os.OsGating.canInstall(spec.minEra(), era)) {
            return null;
        }
        final String needed = spec.minEra().name();
        return OpResult.fail(spec.commandName() + " needs "
                + (needed.charAt(0) + needed.substring(1).toLowerCase(java.util.Locale.ROOT))
                + " hardware or later");
    }

    /** Whether a media reader linked to this computer holds a PROGRAM_INSTALL medium for {@code programId}. */
    private boolean hasInstallMediumFor(final ResourceLocation programId) {
        if (!(host instanceof dev.jstech.computronics.os
                .OsHost computer)) {
            return false;
        }
        for (final long endpoint : computer.linkedEndpoints()) {
            if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                    instanceof dev.jstech.computronics.os.media.MediaReaderBlockEntity reader
                    && reader.insertedKind()
                            == dev.jstech.computronics.os.media.MediaKind.PROGRAM_INSTALL
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
        return java.util.List.of(new ServiceStatus("IQL Engine", engineState(mainframe)),
                new ServiceStatus("Mirror", mirrorState(mainframe)));
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
            case CRAFT -> craft(op.item(), op.quantity(), op.priority());
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
            final dev.jstech.computronics.block.part.NamedBus.Located bus =
                    dev.jstech.computronics.block.part.NamedBus.find(level, host.networkUuid(), op.from());
            if (bus != null) {
                return moveFromBus(op, mainframe, bus.port());
            }
        }
        return insert(op.item(), op.quantity(), op.priority());
    }

    /** Applies the statement's {@code PRIORITY} to a freshly submitted Operation; a null submission passes through. */
    @org.jetbrains.annotations.Nullable
    private static <T extends dev.jstech.computronics.operation.NetworkOperation> T prioritize(
            @org.jetbrains.annotations.Nullable final T operation, final IqlOperation statement) {
        if (operation != null) {
            operation.setPriority(statement.priority());
        }
        return operation;
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
            final var operation = prioritize(from == null
                    ? mainframe.submitNetworkSelect(key, demand(op.quantity()), host.localStorage(),
                            host.originLabel(MoveLabels.IQL))
                    : mainframe.submitNetworkMove(key, demand(op.quantity()), host.localStorage(),
                            host.originLabel(MoveLabels.IQL), java.util.Set.of(from)), op);
            if (operation != null) {
                operation.abortWhen(hostGone()); // the pull lands in this computer: stop once it is gone
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
        dev.jstech.computronics.storage.DataSink target = (k, amount, simulate) -> amount;
        if ("DELETE".equals(verb) && op.to() != null && !op.to().isBlank()) {
            final dev.jstech.computronics.block.part.NamedBus.Located bus =
                    dev.jstech.computronics.block.part.NamedBus.find(level, host.networkUuid(), op.to());
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
            if (prioritize(mainframe.submitNetworkDelete(key, demand(op.quantity()), target,
                    host.originLabel(MoveLabels.IQL)), op) != null) {
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
        final dev.jstech.computronics.block.part.NamedBus.Located toBus =
                dev.jstech.computronics.block.part.NamedBus.find(level, net, op.to());
        if (toBus != null) {
            return moveToBus(op, mainframe, toBus.port());
        }
        final dev.jstech.computronics.block.part.NamedBus.Located fromBus =
                dev.jstech.computronics.block.part.NamedBus.find(level, net, op.from());
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
        final dev.jstech.computronics.storage.DataSink destSink = serverSink(dest);
        if (destSink == null) {
            return OpResult.fail("the destination server is unavailable");
        }
        final List<StorageKey> keys = keysFor(op.item(), source);
        if (keys.isEmpty()) {
            return op.isAnyItem() ? OpResult.fail("nothing to move") : OpResult.fail("unknown item: " + op.item());
        }
        int queued = 0;
        for (final StorageKey key : keys) {
            if (prioritize(mainframe.submitNetworkMove(key, demand(op.quantity()), destSink,
                    host.originLabel(MoveLabels.IQL), java.util.Set.of(source)), op) != null) {
                queued++;
            }
        }
        return queued == 0 ? OpResult.fail("could not start the MOVE")
                : OpResult.ok("MOVE queued: " + describe(op, keys)
                        + " " + op.from() + " -> " + ComputingPayloads.serverLabel(level, dest));
    }

    /** Network -> a named bus's external inventory: a timed export, the same path the Export Bus uses. */
    private OpResult moveToBus(final IqlOperation op, final MainframeBlockEntity mainframe,
                               final dev.jstech.computronics.storage.ExternalDataPort port) {
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
            if (prioritize(mainframe.submitNetworkDelete(key, demand(op.quantity()), port,
                    host.originLabel(MoveLabels.IQL)), op) != null) {
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
                                 final dev.jstech.computronics.storage.ExternalDataPort port) {
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
            final dev.jstech.computronics.operation.NetworkInsertOperation insert =
                    prioritize(mainframe.submitNetworkInsert(key, pulled, host.originLabel(MoveLabels.IQL)), op);
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
        for (final dev.jstech.core.network.ServerNode server
                : NetworkSystem.get(level).serversOf(net)) {
            if (ComputingPayloads.serverLabel(level, server.nodeUuid()).equalsIgnoreCase(name)) {
                return server.nodeUuid();
            }
        }
        return null;
    }

    private dev.jstech.computronics.storage.DataSink serverSink(final NodeUuid node) {
        return NetworkSystem.get(level).locationOf(node)
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                        instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity rack
                        ? (dev.jstech.computronics.storage.DataSink)
                                new dev.jstech.computronics.storage.StoreSink(
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
    /** A resolved drive: its letter, the backing disk/medium stack, its filesystem kind, and how to persist a mutation. */
    private record DiskCtx(char drive, ItemStack disk, FilesystemKind kind, Runnable commit) {}

    /** A path argument resolved against the current location: the target drive letter, its context, and the storage path. */
    private record Resolved(char drive, DiskCtx ctx, String path) {}

    /**
     * Builds the ordered drive table for the host computer: {@code C:} is the bootable system disk;
     * then {@code D:}, {@code E:} ... are the remaining data-disk slots (in slot order) followed by
     * the linked media readers (in ascending position order, so the assignment is stable). An empty
     * media reader still gets a letter but a disk that is {@link ItemStack#EMPTY} (a not-ready drive).
     * Returns an empty list when the host is not a computer.
     */
    private java.util.List<DiskCtx> driveTable() {
        if (!(hostBlock instanceof OsHost computer)) {
            return java.util.List.of();
        }
        final java.util.List<DiskCtx> table = new ArrayList<>();
        final ItemStack system = computer.systemDisk();
        char letter = 'C';
        if (!system.isEmpty()) {
            final OsDef os = computer.installedOs();
            final KernelDef kernel = os != null ? OsRegistry.getKernel(os.kernelId()) : null;
            final FilesystemKind kind = kernel != null ? kernel.filesystem() : FilesystemKind.NONE;
            table.add(new DiskCtx('C', system, kind, computer::setChanged));
            letter = 'D';
        }
        // Data disks: every disk slot holding a real disk other than the boot disk.
        for (int i = 0; i < computer.diskSlots() && letter <= 'Z'; i++) {
            final ItemStack disk = computer.diskInSlot(i);
            if (disk.isEmpty() || disk == system
                    || !(disk.getItem() instanceof dev.jstech.computronics.item.DiskItem)) {
                continue;
            }
            table.add(new DiskCtx(letter, disk, FilesystemKind.HIERARCHICAL, computer::setChanged));
            letter++;
        }
        // Linked media readers, in ascending packed-position order for a stable letter assignment.
        final java.util.List<Long> readers = new ArrayList<>(computer.linkedEndpoints());
        java.util.Collections.sort(readers);
        for (final long pos : readers) {
            if (letter > 'Z') {
                break;
            }
            if (!(level.getBlockEntity(BlockPos.of(pos))
                    instanceof dev.jstech.computronics.os.media.MediaReaderBlockEntity reader)) {
                continue;
            }
            final ItemStack media = reader.mediaSlot().getStackInSlot(0);
            table.add(new DiskCtx(letter, media, FilesystemKind.HIERARCHICAL, () -> syncReader(reader)));
            letter++;
        }
        return table;
    }

    /** Resolves a drive letter to its context, or {@code null} when the letter is not mapped. */
    private DiskCtx diskFor(final char drive) {
        final char upper = Character.toUpperCase(drive);
        for (final DiskCtx ctx : driveTable()) {
            if (ctx.drive() == upper) {
                return ctx;
            }
        }
        return null;
    }

    /** Resolves a DOS path argument against the current location, mapping it to the target drive's context. */
    private Resolved resolve(final String input) {
        final DosPath.Location loc = DosPath.resolve(currentLocation(), input);
        return new Resolved(loc.drive(), diskFor(loc.drive()), loc.storagePath());
    }

    /** The error for an unmapped drive: a friendly no-OS message for {@code C:}, generic otherwise. */
    private FsResult driveError(final char drive) {
        if (Character.toUpperCase(drive) == 'C') {
            return FsResult.noOs();
        }
        return FsResult.fail(Character.toUpperCase(drive) + ":\\ The system cannot find the drive specified.");
    }

    /** The error for a mapped but empty drive (a media reader with no medium inserted). */
    private static FsResult notReady(final char drive) {
        return FsResult.fail(Character.toUpperCase(drive) + ":\\ The device is not ready.");
    }

    /** Pushes a block update so clients see a medium whose filesystem the shell just mutated. */
    private void syncReader(final dev.jstech.computronics.os.media.MediaReaderBlockEntity reader) {
        reader.setChanged();
        if (reader.getLevel() != null) {
            reader.getLevel().sendBlockUpdated(reader.getBlockPos(), reader.getBlockState(),
                    reader.getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Free space in mB-equivalents on a disk or medium stack: capacity minus stored items, files, and OS. */
    private static long freeWeightOf(final ItemStack stack) {
        final long capacityItems;
        if (stack.getItem() instanceof dev.jstech.computronics.item.DiskItem diskItem) {
            capacityItems = diskItem.spec().capacityItems();
        } else if (stack.getItem()
                instanceof dev.jstech.computronics.os.media.FormattedMediaItem mediaItem) {
            capacityItems = mediaItem.format().capacityItems();
        } else {
            return 0L;
        }
        final long capacity = capacityItems * StorageKey.MB_EQ_PER_ITEM;
        final long storageUsed = dev.jstech.computronics.storage.DriveVolumes.usedWeight(stack);
        final long fsUsed = DiskFilesystem.filesWeight(stack);
        final ResourceLocation osId = stack.get(dev.jstech.computronics.ComputingModule.SYSTEM_OS.get());
        final OsDef os = osId != null ? OsRegistry.getOs(osId) : null;
        final long osReserved = os != null
                ? os.footprintItemsOn(DiskFilesystem.eraOf(stack)) * StorageKey.MB_EQ_PER_ITEM : 0L;
        return Math.max(0L, capacity - storageUsed - fsUsed - osReserved);
    }

    /** The shell family of the OS installed on {@code host} (DOS when it has no OS or is not a computer). */
    public static dev.jstech.computronics.os.ShellFamily shellFamilyOf(final Object host) {
        if (host instanceof OsHost computer) {
            final OsDef os = computer.installedOs();
            final KernelDef kernel = os == null ? null : OsRegistry.getKernel(os.kernelId());
            if (kernel != null) {
                return kernel.shellFamily();
            }
        }
        return dev.jstech.computronics.os.ShellFamily.DOS;
    }

    @Override
    public dev.jstech.computronics.os.ShellFamily shellFamily() {
        return shellFamilyOf(hostBlock);
    }

    // Set by the reboot verb during a command run; the payload handler reads it once the shell returns.
    private boolean firmwareReboot;
    private boolean reboot;

    @Override
    public void requestFirmwareReboot() {
        this.firmwareReboot = true;
    }

    @Override
    public boolean firmwareRebootRequested() {
        return firmwareReboot;
    }

    @Override
    public void requestReboot() {
        this.reboot = true;
    }

    @Override
    public boolean rebootRequested() {
        return reboot;
    }

    // ---- packages: the Linux package managers over the network's Mirror service ----

    private OsDef installedOsDef() {
        return hostBlock instanceof OsHost c ? c.installedOs() : null;
    }

    @Override
    public dev.jstech.computronics.os.PackageManagerKind packageManager() {
        final OsDef os = installedOsDef();
        return os == null ? dev.jstech.computronics.os.PackageManagerKind.NONE : os.packageManager();
    }

    /** The network's Mainframe when its Mirror service is serving, else null. */
    private MainframeBlockEntity mirrorMainframe() {
        final MainframeBlockEntity mf = mainframe(host.networkUuid());
        return mf != null && mf.isMirrorActive() ? mf : null;
    }

    @Override
    public boolean mirrorReachable() {
        return mirrorMainframe() != null;
    }

    /** Moves finished source builds into the installed set (lazy: runs whenever packages are touched). */
    private void settleBuilds() {
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        if (console != null && !console.settleBuilds(level.getGameTime()).isEmpty()) {
            hostBlock.setChanged();
        }
    }

    @Override
    public java.util.List<String> drainBuildNotices() {
        settleBuilds();
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        if (console == null) {
            return java.util.List.of();
        }
        final java.util.List<String> finished = console.drainFinishedBuilds();
        if (finished.isEmpty()) {
            return java.util.List.of();
        }
        hostBlock.setChanged();
        final java.util.List<String> out = new ArrayList<>(finished.size());
        for (final String id : finished) {
            final dev.jstech.computronics.os.ProgramSpec spec =
                    OsRegistry.getProgram(net.minecraft.resources.ResourceLocation.tryParse(id));
            out.add(">>> " + (spec != null ? spec.commandName() : id) + ": build finished, package installed");
        }
        return out;
    }

    /** Whether the named program is present on this computer (console install, or a Mainframe service flag). */
    private boolean hasPackage(final dev.jstech.computronics.os.ProgramSpec spec) {
        if (hostBlock instanceof MainframeBlockEntity mf) {
            switch (spec.id().getPath()) {
                case "iqlengine" -> {
                    return mf.isIqlEngineInstalled();
                }
                case "automation_engine" -> {
                    return mf.isAutomationEngineInstalled();
                }
                case "mirror" -> {
                    return mf.isMirrorInstalled();
                }
                default -> {
                }
            }
        }
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        return console != null && console.isInstalled(spec.id().toString());
    }

    /** Every program the mirror can serve a Linux computer: the specs that list the Linux platform. */
    /**
     * The packages a manager on THIS computer can offer: everything installable that runs on the
     * platform it is running. Reading the platform (rather than assuming Linux) is what lets the
     * Frames manager see the Frames-only software the mirror serves.
     */
    private java.util.List<dev.jstech.computronics.os.ProgramSpec> mirrorPackages() {
        final dev.jstech.computronics.os.OsDef os = installedOsDef();
        final dev.jstech.computronics.os.Platform platform =
                os == null ? dev.jstech.computronics.os.Platform.LINUX : os.platform();
        final java.util.List<dev.jstech.computronics.os.ProgramSpec> out = new ArrayList<>();
        for (final dev.jstech.computronics.os.ProgramSpec spec : OsRegistry.programs()) {
            if (spec.installable() && spec.platforms().contains(platform)) {
                out.add(spec);
            }
        }
        return out;
    }

    @Override
    public java.util.List<PackageInfo> packagesAvailable() {
        settleBuilds();
        if (mirrorMainframe() == null) {
            return java.util.List.of();
        }
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        final java.util.List<PackageInfo> out = new ArrayList<>();
        for (final dev.jstech.computronics.os.ProgramSpec spec : mirrorPackages()) {
            final boolean building = console != null && console.pendingBuilds().containsKey(spec.id().toString());
            out.add(new PackageInfo(spec.commandName(), spec.displayName()
                    + (spec.kind() == dev.jstech.computronics.os.ProgramKind.SERVICE ? " (service)" : ""),
                    hasPackage(spec), building));
        }
        return out;
    }

    @Override
    public OpResult packageInstall(final String name) {
        settleBuilds();
        final dev.jstech.computronics.os.PackageManagerKind manager = packageManager();
        if (manager == dev.jstech.computronics.os.PackageManagerKind.NONE) {
            return OpResult.fail("this system installs programs from install media, not a package manager");
        }
        if (mirrorMainframe() == null) {
            return OpResult.fail("could not resolve mirror:// - connect this computer to a network whose Mainframe"
                    + " runs the Mirror service");
        }
        dev.jstech.computronics.os.ProgramSpec spec = null;
        final String wanted = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
        for (final dev.jstech.computronics.os.ProgramSpec candidate : mirrorPackages()) {
            if (candidate.commandName().equalsIgnoreCase(wanted) || candidate.id().getPath().equalsIgnoreCase(wanted)) {
                spec = candidate;
                break;
            }
        }
        if (spec == null) {
            return OpResult.fail("unable to locate package " + wanted);
        }
        final OpResult tooOld = eraGate(spec);
        if (tooOld != null) {
            return tooOld;
        }
        if (spec.hostScope() == dev.jstech.computronics.os.HostScope.MAINFRAME
                && !(hostBlock instanceof MainframeBlockEntity)) {
            return OpResult.fail(spec.commandName() + " only installs on the Mainframe");
        }
        if (spec.hostScope() == dev.jstech.computronics.os.HostScope.SERVER
                && !(hostBlock instanceof dev.jstech.computronics.blockentity
                        .ServerRackBlockEntity)) {
            return OpResult.fail(spec.commandName() + " only installs on a server in a rack");
        }
        if (spec.hostScope() == dev.jstech.computronics.os.HostScope.CLUSTER_MANAGEMENT_COMPUTER
                && !(hostBlock instanceof dev.jstech.computronics.blockentity
                        .ClusterManagementComputerBlockEntity)) {
            return OpResult.fail(spec.commandName() + " only installs on a Cluster Management Computer");
        }
        if (hasPackage(spec)) {
            return OpResult.ok(spec.commandName() + " is already the newest version");
        }
        // Re-running emerge on a package still compiling reports the build instead of restarting it from zero.
        final Long readyAt = host.console() == null ? null : host.console().pendingBuilds().get(spec.id().toString());
        if (readyAt != null) {
            final long left = Math.max(0L, readyAt - level.getGameTime());
            return OpResult.ok(">>> " + spec.commandName() + " is already compiling (about " + (left / 20) + "s left)");
        }
        // A Mainframe service switches its flag on directly (a prebuilt daemon, so no source build either).
        if (hostBlock instanceof MainframeBlockEntity mf
                && spec.kind() == dev.jstech.computronics.os.ProgramKind.SERVICE) {
            final boolean done = switch (spec.id().getPath()) {
                case "iqlengine" -> mf.installIqlEngine();
                case "automation_engine" -> mf.installAutomationEngine();
                case "mirror" -> mf.installMirror();
                default -> host.console() != null && host.console().install(spec.id().toString());
            };
            hostBlock.setChanged();
            return done ? OpResult.ok("Setting up " + spec.commandName() + " ... done")
                    : OpResult.fail(spec.commandName() + " could not be set up");
        }
        if (hostBlock instanceof OsHost oc
                && !OsRegistry.canInstallProgram(oc.installedOsId(), spec.id(), oc.maxCpuMhz(), oc.totalVramMb(),
                        oc.systemDiskFreeMb())) {
            return OpResult.fail(spec.commandName() + ": unmet requirements (hardware or free disk space)");
        }
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        if (console == null) {
            return OpResult.fail("this computer cannot store installed programs");
        }
        if (manager.compilesFromSource()) {
            final long ticks = buildTicks(spec);
            console.startBuild(spec.id().toString(), level.getGameTime() + ticks, ticks);
            hostBlock.setChanged();
            return OpResult.ok(">>> Emerging " + spec.commandName() + " ... compiling (about " + (ticks / 20) + "s)");
        }
        console.install(spec.id().toString());
        console.setInstalledVersion(spec.id().toString(), modVersion());
        hostBlock.setChanged();
        return OpResult.ok("Setting up " + spec.commandName() + " ... done");
    }

    /** The build every package the Mirror serves is currently at: the mod's own version. */
    public static String modVersion() {
        return net.neoforged.fml.ModList.get()
                .getModContainerById(dev.jstech.computronics.JsComputronics.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("0");
    }

    @Override
    public OpResult packageUpdate() {
        settleBuilds();
        final dev.jstech.computronics.os.PackageManagerKind manager = packageManager();
        if (manager == dev.jstech.computronics.os.PackageManagerKind.NONE) {
            return OpResult.fail("this system installs programs from install media, not a package manager");
        }
        final ComputerConsoleState console = host.console();
        if (console == null) {
            return OpResult.fail("this computer cannot store installed programs");
        }
        if (mirrorMainframe() == null) {
            return OpResult.fail("could not resolve mirror:// - connect this computer to a network whose Mainframe"
                    + " runs the Mirror service");
        }
        final String current = modVersion();
        final java.util.List<String> outdated = console.outdatedPackages(current);
        if (outdated.isEmpty()) {
            return OpResult.ok("All packages are up to date (" + current + ").");
        }
        // Bringing a package to the current build is a re-stamp: the program itself always runs the
        // code this mod version ships, so an update reconciles the record rather than moving files.
        for (final String id : outdated) {
            console.setInstalledVersion(id, current);
        }
        hostBlock.setChanged();
        return OpResult.ok("Updated " + outdated.size() + " package"
                + (outdated.size() == 1 ? "" : "s") + " to " + current + ".");
    }

    /**
     * How long a source build takes: proportional to the package's footprint and inversely to the CPU clock, so
     * faster hardware compiles faster (balancing estimate, clamped to a few seconds ... half an hour).
     */
    private long buildTicks(final dev.jstech.computronics.os.ProgramSpec spec) {
        final int cpu = Math.max(100, hostBlock instanceof OsHost c ? c.maxCpuMhz() : 100);
        final long seconds = Math.max(5L, Math.min(1800L, Math.max(16L, spec.minDiskMb()) * 1000L / cpu));
        return seconds * 20L;
    }

    @Override
    public OpResult packageRemove(final String name) {
        settleBuilds();
        final String wanted = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
        dev.jstech.computronics.os.ProgramSpec spec = null;
        for (final dev.jstech.computronics.os.ProgramSpec candidate : OsRegistry.programs()) {
            if (candidate.installable()
                    && (candidate.commandName().equalsIgnoreCase(wanted)
                            || candidate.id().getPath().equalsIgnoreCase(wanted))) {
                spec = candidate;
                break;
            }
        }
        if (spec == null) {
            return OpResult.fail("unable to locate package " + wanted);
        }
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        // A build still compiling is simply cancelled.
        if (console != null && console.cancelBuild(spec.id().toString())) {
            hostBlock.setChanged();
            return OpResult.ok(">>> " + spec.commandName() + ": build cancelled");
        }
        boolean removed = console != null && console.uninstall(spec.id().toString());
        // A Mainframe service also turns its agent off (removing only the console entry would leave the
        // service running headless).
        if (hostBlock instanceof MainframeBlockEntity mainframe
                && spec.kind() == dev.jstech.computronics.os.ProgramKind.SERVICE) {
            removed = switch (spec.id().getPath()) {
                case "iqlengine" -> mainframe.uninstallIqlEngine() || removed;
                case "automation_engine" -> mainframe.uninstallAutomationEngine() || removed;
                case "mirror" -> mainframe.uninstallMirror() || removed;
                default -> removed;
            };
        }
        if (!removed) {
            return OpResult.fail(spec.commandName() + " is not installed, so not removed");
        }
        hostBlock.setChanged();
        return OpResult.ok("Removing " + spec.commandName() + " ... done");
    }

    @Override
    public OpResult formatDrive(final char letterRaw) {
        final char letter = Character.toUpperCase(letterRaw);
        for (final DiskCtx ctx : driveTable()) {
            if (ctx.drive() != letter) {
                continue;
            }
            final ItemStack target = ctx.disk();
            if (target.isEmpty()) {
                return OpResult.fail("format: drive " + letter + ": drive not ready");
            }
            if (letter == 'C' && hostBlock instanceof OsHost computer && computer.hasOs()) {
                return OpResult.fail("format: cannot format drive C: - the running system lives on it");
            }
            // Formatting erases everything the volume carries: the system, the filesystem, the item
            // storage, and (on removable media) the stamped installer identity — a blank volume remains.
            target.remove(dev.jstech.computronics.ComputingModule.SYSTEM_OS.get());
            target.remove(dev.jstech.computronics.ComputingModule.FILESYSTEM.get());
            dev.jstech.computronics.storage.DriveVolumes.erase(target);
            target.remove(dev.jstech.computronics.ComputingModule.DISK_PUBLIC_PERMILLE.get());
            target.remove(dev.jstech.computronics.ComputingModule.MEDIA_KIND.get());
            target.remove(dev.jstech.computronics.ComputingModule.MEDIA_PAYLOAD.get());
            target.remove(dev.jstech.computronics.ComputingModule.MEDIA_DATA.get());
            ctx.commit().run();
            return OpResult.ok("Formatting drive " + letter + ": ... done\nAll data on the volume was erased.");
        }
        return OpResult.fail("format: drive " + letter + ": not found");
    }

    @Override
    public boolean hasProgram(final net.minecraft.resources.ResourceLocation id) {
        final dev.jstech.computronics.os.ProgramSpec spec =
                id == null ? null : OsRegistry.getProgram(id);
        return spec != null && hasPackage(spec);
    }

    @Override
    public SystemInfo systemInfo() {
        if (!(hostBlock instanceof OsHost computer) || computer.installedOs() == null) {
            return null;
        }
        final dev.jstech.computronics.os.OsDef os = computer.installedOs();
        final net.minecraft.resources.ResourceLocation desktopId = computer.installedDesktopId();
        final dev.jstech.computronics.os.DesktopEnvironmentDef chrome =
                desktopId == null ? null : OsRegistry.getDesktop(desktopId);
        final ItemStack systemDisk = computer.systemDisk();
        final long totalMb = systemDisk.getItem()
                instanceof dev.jstech.computronics.item.DiskItem disk
                ? disk.spec().capacityItems() * StorageKey.MB_EQ_PER_ITEM : 0L;
        final long freeMb = computer.systemDiskFreeMb();
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        return new SystemInfo(
                os.id().getPath(),
                os.displayName(),
                shellFamily() == dev.jstech.computronics.os.ShellFamily.POSIX
                        ? "Linux 6.8-jsc x86_64" : "JSC " + os.id().getPath(),
                hostname(),
                os.shellId(),
                chrome != null ? chrome.displayName() : "none (tty1)",
                computer.maxCpuMhz() + " MHz",
                (int) Math.min(Integer.MAX_VALUE, computer.ramBuffer()),
                Math.max(0L, totalMb - freeMb),
                totalMb,
                console == null ? 0 : console.installed().size(),
                level.getGameTime());
    }

    @Override
    public java.util.Map<String, Long> buildsRemaining() {
        settleBuilds();
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        if (console == null) {
            return java.util.Map.of();
        }
        final java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        final long now = level.getGameTime();
        console.pendingBuilds().forEach((id, readyAt) -> out.put(id, Math.max(0L, readyAt - now)));
        return out;
    }

    @Override
    public OpResult mirrorControl(final String action) {
        final MainframeBlockEntity mainframe = mainframe(host.networkUuid());
        if (mainframe == null) {
            return OpResult.fail("the network has no running Mainframe to host the Mirror");
        }
        return switch (action == null ? "" : action.toLowerCase(java.util.Locale.ROOT)) {
            case "install" -> mainframe.installMirror()
                    ? OpResult.ok("Mirror installed on the Mainframe and serving packages")
                    : OpResult.fail("the Mirror is already installed");
            case "status", "" -> OpResult.ok("Mirror: " + mirrorState(mainframe));
            default -> OpResult.fail("usage: mirror install|status");
        };
    }

    private static String mirrorState(final MainframeBlockEntity mainframe) {
        if (!mainframe.isMirrorInstalled()) {
            return "not installed";
        }
        return mainframe.isMirrorActive() ? "serving" : "installed (Mainframe off)";
    }

    @Override
    public String hostname() {
        // The host resolves its own name so the shell, the provenance rows and the remote host list agree.
        return host.hostname();
    }

    @Override
    public dev.jstech.computronics.program.install.LiveInstallState liveInstall() {
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        return console == null ? null : console.liveInstall();
    }

    @Override
    public OpResult liveRun(final String line) {
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        final dev.jstech.computronics.program.install.LiveInstallState state =
                console == null ? null : console.liveInstall();
        if (state == null || !(hostBlock instanceof OsHost computer)) {
            return OpResult.fail("no live medium is booted");
        }
        // The devices the live system sees: every installed disk, in slot order (sda, sdb, ...).
        final java.util.List<String> devices = new ArrayList<>();
        for (int i = 0; i < computer.diskSlots(); i++) {
            if (computer.diskInSlot(i).getItem() instanceof dev.jstech.computronics.item.DiskItem) {
                devices.add("sd" + (char) ('a' + i));
            }
        }
        final long kernelTicks = Math.max(5L, Math.min(1800L, 64_000L / Math.max(100, computer.maxCpuMhz()))) * 20L;
        final dev.jstech.computronics.program.install.LiveInstallState.Result result =
                state.run(line, new dev.jstech.computronics.program.install.LiveInstallState.Env(
                        devices, mirrorReachable(), level.getGameTime(), kernelTicks));
        hostBlock.setChanged();
        final String text = String.join("\n", result.lines());
        if (!result.complete()) {
            return result.ok() ? OpResult.ok(text) : OpResult.fail(text);
        }
        // The sequence completed: the hand-installed system lands on the chosen disk and boots first.
        final ResourceLocation osId = ResourceLocation.fromNamespaceAndPath("jsc",
                state.distro() == dev.jstech.computronics.program.install.LiveInstallState.Distro.ARCH
                        ? "arch" : "gentoo");
        final int target = state.targetIndex();
        if (!computer.installOs(osId, target)) {
            return OpResult.fail(text + "\nThe installation could not be written to the disk (no space or no disk).");
        }
        computer.setBootDiskSlot(target);
        // Ask the host for the console again rather than reusing the reference taken at the top of this
        // method: writing the system may have replaced the disk stack, and the console is bound to the
        // drive it was read from. Clearing the stale binding would leave the finished live session on
        // the newly written disk, so the machine would boot straight back into the installer.
        host.console().clearLiveInstall();
        hostBlock.setChanged();
        // The live medium's reboot is a real one: the shell closes, the POST replays, the new system boots.
        requestReboot();
        return OpResult.ok(text + "\nInstallation complete. Rebooting into the new system ...");
    }

    @Override
    public String prompt() {
        final dev.jstech.computronics.program.install.LiveInstallState live = liveInstall();
        if (live != null) {
            return live.prompt();
        }
        if (shellFamily() != dev.jstech.computronics.os.ShellFamily.POSIX) {
            return currentLocation().dosPath() + ">";
        }
        final String cwd = dev.jstech.computronics.program.cli.PosixPath.renderForPrompt(currentLocation());
        final OsDef os = hostBlock instanceof OsHost c ? c.installedOs() : null;
        final boolean zsh = os != null && os.shellId().equals("zsh");
        return zsh ? "player@" + hostname() + " " + cwd + " %" : "player@" + hostname() + ":" + cwd + "$";
    }

    @Override
    public java.util.List<MountInfo> mounts() {
        final java.util.List<MountInfo> out = new ArrayList<>();
        int index = 0;
        for (final DiskCtx ctx : driveTable()) {
            final ItemStack stack = ctx.disk();
            final boolean ready = !stack.isEmpty();
            final long capacity;
            if (stack.getItem() instanceof dev.jstech.computronics.item.DiskItem diskItem) {
                capacity = diskItem.spec().capacityItems() * StorageKey.MB_EQ_PER_ITEM;
            } else if (stack.getItem()
                    instanceof dev.jstech.computronics.os.media.FormattedMediaItem mediaItem) {
                capacity = mediaItem.format().capacityItems() * StorageKey.MB_EQ_PER_ITEM;
            } else {
                capacity = 0L;
            }
            final String device = ctx.drive() == 'C' ? "sda1" : "sd" + (char) ('a' + index);
            out.add(new MountInfo(ctx.drive(), device, capacity, ready ? freeWeightOf(stack) : 0L, ready));
            index++;
        }
        return out;
    }

    @Override
    public dev.jstech.computronics.program.cli.DosPath.Location currentLocation() {
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        if (console == null) {
            return dev.jstech.computronics.program.cli.DosPath.Location.root('C');
        }
        // A fresh POSIX session starts in the home directory (a DOS one at the drive root); once the player
        // has changed directory the stored location wins, so "cd /" really lands on the root.
        if (!console.hasTerminalLocation() && console.terminalDrive() == 'C'
                && shellFamily() == dev.jstech.computronics.os.ShellFamily.POSIX) {
            return dev.jstech.computronics.program.cli.PosixPath.home();
        }
        final String dir = console.terminalDir();
        final java.util.List<String> segments = dir.isEmpty()
                ? java.util.List.of() : java.util.List.of(dir.split("/"));
        return new dev.jstech.computronics.program.cli.DosPath.Location(console.terminalDrive(), segments);
    }

    @Override
    public void setCurrentLocation(final dev.jstech.computronics.program.cli.DosPath.Location location) {
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        if (console != null) {
            console.setTerminalLocation(location.drive(), location.storagePath());
        }
    }

    @Override
    public FsResult listDisk(final String dir) {
        final Resolved r = resolve(dir == null ? "" : dir);
        if (r.ctx() == null) {
            return driveError(r.drive());
        }
        if (r.ctx().disk().isEmpty()) {
            return notReady(r.drive());
        }
        final DiskCtx ctx = r.ctx();
        final String target = r.path();
        final List<FsEntry> entries = new ArrayList<>();
        // Subdirectories first, then files — matching DOS DIR ordering.
        for (final String sub : DiskFilesystem.listDirs(ctx.disk(), target, ctx.kind())) {
            entries.add(new FsEntry(FsPaths.fileName(sub), "", 0L, false, true, 0L));
        }
        for (final DiskFilesystem.FileEntry e : DiskFilesystem.list(ctx.disk(), target, ctx.kind())) {
            entries.add(new FsEntry(FsPaths.fileName(e.path()), e.type().extension(),
                    e.weight(), e.readOnly(), false, e.modified()));
        }
        return FsResult.listing(entries);
    }

    @Override
    public FsResult readFile(final String path) {
        final Resolved r = resolve(path);
        if (r.ctx() == null) {
            return driveError(r.drive());
        }
        if (r.ctx().disk().isEmpty()) {
            return notReady(r.drive());
        }
        final DiskCtx ctx = r.ctx();
        final String real = r.path();
        final java.util.Optional<String> content = DiskFilesystem.read(ctx.disk(), real);
        if (content.isEmpty()) {
            // Distinguish a .dat rejection from a plain missing file for a cleaner error.
            final List<DiskFilesystem.FileEntry> all = DiskFilesystem.list(ctx.disk(), FsPaths.parentDir(real), ctx.kind());
            final boolean isDat = all.stream().anyMatch(e -> e.path().equals(real) && e.readOnly());
            if (isDat) {
                return FsResult.fail(path + ": .dat files are read-only (use the Network Interactor to access items)");
            }
            return FsResult.fail(path + ": file not found");
        }
        return FsResult.content(content.get());
    }

    @Override
    public FsResult deleteFile(final String path) {
        final Resolved r = resolve(path);
        if (r.ctx() == null) {
            return driveError(r.drive());
        }
        if (r.ctx().disk().isEmpty()) {
            return notReady(r.drive());
        }
        final DiskCtx ctx = r.ctx();
        final String real = r.path();
        // Reject .dat entries before attempting deletion so we surface a clear message.
        final List<DiskFilesystem.FileEntry> all = DiskFilesystem.list(ctx.disk(), FsPaths.parentDir(real), ctx.kind());
        final boolean isDat = all.stream().anyMatch(e -> e.path().equals(real) && e.readOnly());
        if (isDat) {
            return FsResult.fail(path + ": .dat files cannot be deleted (use the Network Interactor)");
        }
        final boolean deleted = DiskFilesystem.delete(ctx.disk(), real);
        if (!deleted) {
            return FsResult.fail(path + ": file not found");
        }
        // DiskFilesystem.delete mutated the component in-place on the drive's stack; persist the owner.
        ctx.commit().run();
        return FsResult.ok("deleted " + path);
    }

    @Override
    public FsResult runScript(final String path) {
        // Check the extension first so the error names the right problem.
        final String ext = extensionOf(path);
        if (!"iql".equalsIgnoreCase(ext)) {
            return FsResult.fail(path + ": only .iql files can be run (got ." + (ext.isEmpty() ? "<none>" : ext) + ")");
        }
        final Resolved r = resolve(path);
        if (r.ctx() == null) {
            return driveError(r.drive());
        }
        if (r.ctx().disk().isEmpty()) {
            return notReady(r.drive());
        }
        final DiskCtx ctx = r.ctx();
        final String real = r.path();
        final java.util.Optional<String> content = DiskFilesystem.read(ctx.disk(), real);
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
        final Resolved r = resolve(path);
        if (r.ctx() == null) {
            return driveError(r.drive());
        }
        if (r.ctx().disk().isEmpty()) {
            return notReady(r.drive());
        }
        final DiskCtx ctx = r.ctx();
        final String real = r.path();
        final FileType type = FileType.fromExtension(extensionOf(path)).orElse(null);
        if (type == null) {
            return FsResult.fail(path + ": unknown file type (use .txt/.iql/.cfg/.csv/.cmd)");
        }
        if (!type.userEditable()) {
            return FsResult.fail(path + ": ." + type.extension() + " files cannot be edited");
        }
        // Free space available, crediting back the file being overwritten so a same-size rewrite fits.
        final long oldWeight = DiskFilesystem.read(ctx.disk(), real)
                .map(c -> FsPaths.sizeMbEq(c.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                        DiskFilesystem.eraOf(ctx.disk())))
                .orElse(0L);
        final DiskFilesystem.WriteResult result = DiskFilesystem.write(
                ctx.disk(), real, type, content, freeWeightOf(ctx.disk()) + oldWeight, ctx.kind(), level.getGameTime());
        return switch (result) {
            case OK -> {
                ctx.commit().run();
                yield FsResult.ok("wrote " + path);
            }
            case INVALID_PATH -> FsResult.fail(path + ": invalid file name for this filesystem");
            case DISK_FULL -> FsResult.fail(path + ": not enough free space on the disk");
            case READ_ONLY -> FsResult.fail(path + ": ." + type.extension() + " is read-only");
        };
    }

    @Override
    public FsResult changeDir(final String input) {
        final DosPath.Location target = DosPath.resolve(currentLocation(), input);
        final DiskCtx ctx = diskFor(target.drive());
        if (ctx == null) {
            return driveError(target.drive());
        }
        if (ctx.disk().isEmpty()) {
            return notReady(target.drive());
        }
        if (!dirExists(ctx, target.storagePath())) {
            return FsResult.fail("The system cannot find the path specified.");
        }
        setCurrentLocation(target);
        return FsResult.ok("");
    }

    @Override
    public FsResult changeDrive(final char drive) {
        final DiskCtx ctx = diskFor(drive);
        if (ctx == null) {
            return driveError(drive);
        }
        if (ctx.disk().isEmpty()) {
            return notReady(drive);
        }
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        if (console != null) {
            console.setTerminalDrive(Character.toUpperCase(drive));
        }
        return FsResult.ok("");
    }

    @Override
    public FsResult makeDir(final String path) {
        if (path == null || path.isBlank()) {
            return FsResult.fail("The syntax of the command is incorrect.");
        }
        final Resolved r = resolve(path);
        if (r.ctx() == null) {
            return driveError(r.drive());
        }
        if (r.ctx().disk().isEmpty()) {
            return notReady(r.drive());
        }
        final DiskCtx ctx = r.ctx();
        final String real = r.path();
        if (ctx.kind() != FilesystemKind.HIERARCHICAL) {
            return FsResult.fail("Directories are not supported on this drive.");
        }
        if (real.isEmpty()) {
            return FsResult.fail("The syntax of the command is incorrect.");
        }
        if (dirExists(ctx, real) || DiskFilesystem.exists(ctx.disk(), real)) {
            return FsResult.fail("A subdirectory or file " + path + " already exists.");
        }
        if (DiskFilesystem.mkdir(ctx.disk(), real, ctx.kind())) {
            ctx.commit().run();
            return FsResult.ok("");
        }
        return FsResult.fail(path + ": unable to create directory");
    }

    @Override
    public FsResult removeDir(final String path) {
        final Resolved r = resolve(path);
        if (r.ctx() == null) {
            return driveError(r.drive());
        }
        if (r.ctx().disk().isEmpty()) {
            return notReady(r.drive());
        }
        final DiskCtx ctx = r.ctx();
        final String real = r.path();
        if (ctx.kind() != FilesystemKind.HIERARCHICAL) {
            return FsResult.fail("Directories are not supported on this drive.");
        }
        if (real.isEmpty()) {
            return FsResult.fail("The syntax of the command is incorrect.");
        }
        final DosPath.Location cwd = currentLocation();
        if (r.drive() == cwd.drive() && real.equals(cwd.storagePath())) {
            return FsResult.fail("The process cannot access the directory because it is in use.");
        }
        if (!dirExists(ctx, real)) {
            return FsResult.fail("The system cannot find the path specified.");
        }
        // DOS 'rd' refuses a non-empty directory; there is no implicit recursive delete.
        final boolean hasChildren = !DiskFilesystem.listDirs(ctx.disk(), real, ctx.kind()).isEmpty()
                || !DiskFilesystem.list(ctx.disk(), real, ctx.kind()).isEmpty();
        if (hasChildren) {
            return FsResult.fail("The directory is not empty.");
        }
        if (DiskFilesystem.rmdir(ctx.disk(), real, ctx.kind())) {
            ctx.commit().run();
            return FsResult.ok("");
        }
        return FsResult.fail("The system cannot find the path specified.");
    }

    @Override
    public FsResult copyPath(final String src, final String dest) {
        final Resolved s = resolve(src);
        if (s.ctx() == null) {
            return driveError(s.drive());
        }
        if (s.ctx().disk().isEmpty()) {
            return notReady(s.drive());
        }
        final Resolved d = resolve(dest);
        if (d.ctx() == null) {
            return driveError(d.drive());
        }
        if (d.ctx().disk().isEmpty()) {
            return notReady(d.drive());
        }
        // A destination that is an existing directory means "copy into it", keeping the source name.
        String realDest = d.path();
        if (dirExists(d.ctx(), realDest)) {
            realDest = FsPaths.join(realDest, FsPaths.fileName(s.path()));
        }
        if (s.drive() == d.drive()) {
            // Same drive: DiskFilesystem.copy handles both a single file and a whole directory subtree.
            if (DiskFilesystem.copy(s.ctx().disk(), s.path(), realDest, freeWeightOf(d.ctx().disk()), s.ctx().kind())) {
                s.ctx().commit().run();
                return FsResult.ok("        1 file(s) copied.");
            }
            return FsResult.fail("The system cannot find the file specified.");
        }
        // Cross-drive: copy a single file by reading the source and writing it to the destination drive.
        final java.util.Optional<String> content = DiskFilesystem.read(s.ctx().disk(), s.path());
        if (content.isEmpty()) {
            return FsResult.fail(src + ": file not found (cross-drive copy supports files only)");
        }
        final FileType type = FileType.fromExtension(extensionOf(realDest)).orElse(FileType.TXT);
        final DiskFilesystem.WriteResult wr = DiskFilesystem.write(d.ctx().disk(), realDest, type,
                content.get(), freeWeightOf(d.ctx().disk()), d.ctx().kind(), level.getGameTime());
        return switch (wr) {
            case OK -> {
                d.ctx().commit().run();
                yield FsResult.ok("        1 file(s) copied.");
            }
            case DISK_FULL -> FsResult.fail(dest + ": not enough free space on the disk");
            case INVALID_PATH -> FsResult.fail(dest + ": invalid file name for this filesystem");
            case READ_ONLY -> FsResult.fail(dest + ": the destination is read-only");
        };
    }

    @Override
    public FsResult movePath(final String src, final String destDir) {
        final Resolved s = resolve(src);
        if (s.ctx() == null) {
            return driveError(s.drive());
        }
        if (s.ctx().disk().isEmpty()) {
            return notReady(s.drive());
        }
        final Resolved d = resolve(destDir);
        if (d.ctx() == null) {
            return driveError(d.drive());
        }
        if (d.ctx().disk().isEmpty()) {
            return notReady(d.drive());
        }
        if (!d.path().isEmpty() && !dirExists(d.ctx(), d.path())) {
            return FsResult.fail("The system cannot find the path specified.");
        }
        if (s.drive() == d.drive()) {
            if (DiskFilesystem.move(s.ctx().disk(), s.path(), d.path(), s.ctx().kind())) {
                s.ctx().commit().run();
                return FsResult.ok("        1 file(s) moved.");
            }
            return FsResult.fail("The system cannot find the file specified.");
        }
        // Cross-drive move = copy the file onto the destination drive, then delete the source.
        final java.util.Optional<String> content = DiskFilesystem.read(s.ctx().disk(), s.path());
        if (content.isEmpty()) {
            return FsResult.fail(src + ": file not found (cross-drive move supports files only)");
        }
        final String destPath = FsPaths.join(d.path(), FsPaths.fileName(s.path()));
        final FileType type = FileType.fromExtension(extensionOf(destPath)).orElse(FileType.TXT);
        final DiskFilesystem.WriteResult wr = DiskFilesystem.write(d.ctx().disk(), destPath, type,
                content.get(), freeWeightOf(d.ctx().disk()), d.ctx().kind(), level.getGameTime());
        if (wr != DiskFilesystem.WriteResult.OK) {
            return switch (wr) {
                case DISK_FULL -> FsResult.fail(destDir + ": not enough free space on the disk");
                case INVALID_PATH -> FsResult.fail(destDir + ": invalid file name for this filesystem");
                case READ_ONLY -> FsResult.fail(destDir + ": the destination is read-only");
                case OK -> FsResult.ok("");
            };
        }
        DiskFilesystem.delete(s.ctx().disk(), s.path());
        s.ctx().commit().run();
        d.ctx().commit().run();
        return FsResult.ok("        1 file(s) moved.");
    }

    @Override
    public FsResult renamePath(final String src, final String newName) {
        if (newName == null || newName.isBlank() || newName.contains("/") || newName.contains("\\")) {
            return FsResult.fail("The syntax of the command is incorrect.");
        }
        final Resolved s = resolve(src);
        if (s.ctx() == null) {
            return driveError(s.drive());
        }
        if (s.ctx().disk().isEmpty()) {
            return notReady(s.drive());
        }
        final DiskCtx ctx = s.ctx();
        final String dest = FsPaths.join(FsPaths.parentDir(s.path()), newName);
        if (DiskFilesystem.rename(ctx.disk(), s.path(), dest, ctx.kind())) {
            ctx.commit().run();
            return FsResult.ok("");
        }
        return FsResult.fail("The system cannot find the file specified.");
    }

    @Override
    public java.util.List<String> configSummary() {
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        if (console == null) {
            return java.util.List.of();
        }
        final java.util.List<String> lines = new java.util.ArrayList<>();
        final String name = console.computerName();
        lines.add(String.format(java.util.Locale.ROOT, "  %-12s%s", "name", name.isEmpty() ? "(unnamed)" : name));
        lines.add(String.format(java.util.Locale.ROOT, "  %-12s%d permille", "netshare", systemDiskPermille()));
        lines.addAll(console.settings().summaryLines());
        return lines;
    }

    @Override
    public OpResult setConfig(final String key, final String value) {
        final dev.jstech.computronics.program.ComputerConsoleState console = host.console();
        if (console == null) {
            return OpResult.fail("this computer has no settings store");
        }
        final String k = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT).trim();
        switch (k) {
            case "name" -> {
                console.setComputerName(value == null ? "" : value.trim());
                hostBlock.setChanged();
                return OpResult.ok("name set");
            }
            case "wallpaper" -> {
                console.setWallpaper(value == null ? "" : value.trim());
                hostBlock.setChanged();
                return OpResult.ok("wallpaper set");
            }
            case "theme" -> {
                // A theme preset bundles an accent and a wallpaper, so picking one restyles the desktop.
                final String preset = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
                console.settings().setThemePreset(preset.equals("system") ? "" : preset);
                switch (preset) {
                    case "ocean" -> {
                        console.settings().setAccent(0xFF12A26F);
                        console.setWallpaper("winxp");
                    }
                    case "slate" -> {
                        console.settings().setAccent(0xFF7B52C9);
                        console.setWallpaper("win11");
                    }
                    default -> {
                        console.settings().setAccent(0);
                        console.setWallpaper("");
                    }
                }
                hostBlock.setChanged();
                return OpResult.ok("theme set");
            }
            case "netshare" -> {
                final Integer permille = tryInt(value);
                if (permille == null) {
                    return OpResult.fail("netshare needs a number from 0 to 1000");
                }
                if (!setSystemDiskPermille(permille)) {
                    return OpResult.fail("no system disk to share");
                }
                hostBlock.setChanged();
                return OpResult.ok("netshare set");
            }
            default -> {
                if (console.settings().applySetting(k, value)) {
                    hostBlock.setChanged();
                    return OpResult.ok(k + " set");
                }
                return OpResult.fail("unknown setting: " + k);
            }
        }
    }

    /** The system disk's public-share permille (0 when there is no system disk). */
    private int systemDiskPermille() {
        final DiskCtx ctx = diskFor('C');
        return ctx == null || ctx.disk().isEmpty() ? 0
                : dev.jstech.computronics.item.DiskItem.publicPermille(ctx.disk());
    }

    /** Writes a clamped public-share permille onto the system disk; false when there is none. */
    private boolean setSystemDiskPermille(final int permille) {
        final DiskCtx ctx = diskFor('C');
        if (ctx == null || ctx.disk().isEmpty()) {
            return false;
        }
        dev.jstech.computronics.item.DiskItem.setPublicPermille(ctx.disk(), permille);
        return true;
    }

    private static Integer tryInt(final String v) {
        try {
            return Integer.parseInt(v == null ? "" : v.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    /** True if {@code storagePath} is the drive root or an existing (explicit or implicit) directory. */
    private boolean dirExists(final DiskCtx ctx, final String storagePath) {
        if (storagePath.isEmpty()) {
            return true;
        }
        if (ctx.kind() != FilesystemKind.HIERARCHICAL) {
            return false;
        }
        final String parent = FsPaths.parentDir(storagePath);
        return DiskFilesystem.listDirs(ctx.disk(), parent, ctx.kind()).contains(storagePath);
    }

    /** Returns the lowercase extension of a file path (after the last dot), or {@code ""} if none. */
    private static String extensionOf(final String path) {
        final int dot = path.lastIndexOf('.');
        return dot >= 0 && dot < path.length() - 1
                ? path.substring(dot + 1).toLowerCase(java.util.Locale.ROOT)
                : "";
    }

    // Script processes — the Cannon programs this machine is running.

    @Override
    public OpResult startCannon(final String path, final int heapMb) {
        if (!(hostBlock instanceof AbstractComputerBlockEntity computer)) {
            return OpResult.fail("cannon: this machine cannot run programs");
        }
        if (!"asm".equals(extensionOf(path))) {
            return OpResult.fail(path + ": only a compiled listing can be run (compile it with cannonc)");
        }
        final FsResult read = readFile(path);
        if (!read.ok()) {
            return OpResult.fail(read.message());
        }
        final int room = heapMb <= 0 ? CannonProcesses.DEFAULT_HEAP_MB
                : Math.min(heapMb, CannonProcesses.MAX_HEAP_MB);
        if (!computer.ramLedger().fits(room)) {
            return OpResult.fail("cannon: " + room + " MB will not fit in "
                    + computer.ramLedger().freeMb() + " MB of free memory");
        }
        final CannonProcesses.Started started = computer.cannon()
                .start(FsPaths.fileName(path), read.message(), room, computer.cannonHost());
        if (!started.ok()) {
            return OpResult.fail(started.message());
        }
        computer.setChanged();
        return OpResult.ok(started.message());
    }

    @Override
    public OpResult stopCannon(final int id) {
        if (!(hostBlock instanceof AbstractComputerBlockEntity computer)) {
            return OpResult.fail("cannon: this machine cannot run programs");
        }
        if (!computer.cannon().stop(id)) {
            return OpResult.fail("cannon: nothing is running as " + id);
        }
        computer.setChanged();
        return OpResult.ok("stopped " + id);
    }

    @Override
    public List<CannonProcess> cannonProcesses() {
        if (!(hostBlock instanceof AbstractComputerBlockEntity computer)) {
            return List.of();
        }
        final List<CannonProcess> running = new java.util.ArrayList<>();
        for (final CannonProcesses.Live one : computer.cannon().all()) {
            running.add(new CannonProcess(one.id(), one.name(), one.process().state().name().toLowerCase(
                    java.util.Locale.ROOT), one.process().heap().used(), one.process().heap().budget()));
        }
        return running;
    }
}
