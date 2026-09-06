/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import java.util.List;

/**
 * The view of a computer and its network that a CLI command operates on. It is a deliberately small, Minecraft-free facade: the server backs it with the real Mainframe and storage, while tests back it with a fake, so command logic is unit-testable without a running game. Every effecting call returns an {@link OpResult} describing what happened, never throwing for an ordinary failure (unknown item, no network), so commands can print a clean message.
 */
public interface CliComputer {

    String name();

    /** A human label for the computer kind, e.g. {@code "Personal Computer"} or {@code "Mainframe"}. */
    String type();

    /** A short, stable identifier for this node (the head of its UUID), for display. */
    String nodeId();

    boolean running();

    /** Orchestration capacity in items per tick. */
    long cpuCapacity();

    /** RAM buffer in items. */
    long ramBuffer();

    boolean onNetwork();

    /** A short identifier for the network this computer is on, or {@code ""} when unlinked. */
    String networkId();

    /** Whether this computer is itself the network's Mainframe (gates maintenance commands). */
    boolean isMainframe();

    NetSummary network();

    /**
     * Items the network holds, optionally filtered by a case-insensitive name substring and scoped to a
     * single server by name.
     *
     * @param filter a name fragment, or {@code ""} for everything
     * @param server a server name to scope the read to, or {@code ""} for the whole network
     * @param limit  the maximum number of rows to return
     */
    List<StoredItem> query(dev.jstech.computronics.program.iql.IqlCondition where,
                           String server, int limit);

    /**
     * Rows for a {@code QUERY <object>} against a schema object: {@code items} (name → quantity),
     * {@code servers} (name → items stored), {@code operations} (description → progress). An object that is
     * not wired yet returns an empty list. The label/value pair is what the studio grid and the CLI render.
     *
     * @param object the schema object name (e.g. {@code "items"}, {@code "servers"}, {@code "operations"})
     * @param filter a name fragment, or {@code ""} for everything
     * @param server a server name to scope the read to, or {@code ""} for the whole network
     * @param limit  the maximum number of rows to return
     */
    List<StoredItem> queryObject(String object,
                                 dev.jstech.computronics.program.iql.IqlCondition where,
                                 String server, int limit);

    /** Which servers hold the named item and how much each has. */
    List<Holding> find(String item);

    /**
     * One server of the network: what it is called, how many items it is holding and how many it could.
     *
     * <p>{@code capacity} counts only what the network may fill, so a personal computer that published a
     * tenth of its disks contributes a tenth of them and no more.
     */
    record ServerUse(String name, long stored, long capacity) {
    }

    /**
     * Moves a running operation up or down the queue.
     *
     * <p>Only one that is still going: what has already settled cannot be hurried.
     *
     * @param id       the short id, or any longer prefix of the full one
     * @param priority the name of an {@code OperationPriority}
     */
    default OpResult repriorityOperation(final String id, final String priority) {
        return OpResult.fail("this machine cannot reach the network");
    }

    /** Every server on the network, with what each is holding; empty when this machine is on none. */
    default List<ServerUse> servers() {
        return List.of();
    }

    /** What the whole network is holding, and what it could hold. */
    default ServerUse networkUse() {
        return new ServerUse("", 0L, 0L);
    }

    /** Pull items from the network into this computer's local storage. */
    OpResult select(String item, long quantity);

    /** Push items from this computer's local storage into the network. */
    OpResult insert(String item, long quantity);

    /** Ask the network to craft the named item. */
    OpResult craft(String item, long quantity);

    /**
     * The same three, saying who asked.
     *
     * <p>Every one of these ends up as a row in the network's log, and the row names where it came
     * from. The prompt says so by default; anything else that submits work says what it is, so a player
     * reading the log can tell a script's pull from one they made themselves.
     *
     * @param origin what to put on the row, from {@link dev.jstech.computronics.operation.MoveLabels}
     */
    default OpResult select(final String item, final long quantity, final String origin) {
        return select(item, quantity);
    }

    /** Push, saying who asked. */
    default OpResult insert(final String item, final long quantity, final String origin) {
        return insert(item, quantity);
    }

    /** Craft, saying who asked. */
    default OpResult craft(final String item, final long quantity, final String origin) {
        return craft(item, quantity);
    }

    /** Place a standing hold on the named item so concurrent operations WAIT on it. */
    OpResult lock(String item, long quantity);

    /** Release the standing hold on the named item. */
    OpResult unlock(String item);

    /** The item types currently held by a manual lock, and how much each holds. */
    List<StoredItem> locks();

    /** The operations currently in flight on the network. */
    List<ActiveOp> activeOps();

    /**
     * Run an index-maintenance action (analyze / reindex / vacuum) on the Mainframe.
     *
     * @return a result whose message describes the outcome, or a failure when this computer is not a Mainframe
     */
    OpResult maintenance(String action);

    /** One-line summaries of the peripherals linked to this computer (monitors, drives, ...). */
    List<String> peripherals();

    /** The programs installed on this computer (kept here, not in the CLI layer, so the engine stays Minecraft-free). */
    List<ProgramInfo> programs();

    /** Installs a program on this computer by id. */
    OpResult install(String programId);

    /** Runs a parsed effecting IQL statement (SELECT/INSERT/MOVE/CRAFT/DELETE/DROP/LOCK/...) against the network. */
    OpResult execute(dev.jstech.computronics.program.iql.IqlOperation operation);

    /** Controls the network's IQL Engine service: {@code install}/{@code start}/{@code stop}/{@code status}. */
    /** The block entity behind this shell, for commands that exist on one kind of machine only. */
    default Object hostBlock() {
        return null;
    }

    default OpResult engineControl(final String action) {
        return OpResult.fail("the IQL Engine can only be controlled from a networked computer");
    }

    /** The services the network exposes (the IQL Engine, and any future service) with their current state. */
    default List<ServiceStatus> services() {
        return List.of();
    }

    /** Whether the network's IQL Engine is installed — gates the Engine's own commands in the prompt. */
    default boolean iqlEngineInstalled() {
        return false;
    }

    // -------------------------------------------------------------------------
    // Terminal session (DOS navigation)
    // -------------------------------------------------------------------------

    /**
     * This terminal session's current drive and directory, used by the DOS command line to resolve relative paths
     * and to draw the {@code C:\DIR>} prompt. Defaults to the boot drive's root; the server persists it per terminal.
     */
    default DosPath.Location currentLocation() {
        return DosPath.Location.root('C');
    }

    /** The command-syntax family of the installed OS's kernel; DOS when nothing says otherwise. */
    default dev.jstech.computronics.os.ShellFamily shellFamily() {
        return dev.jstech.computronics.os.ShellFamily.DOS;
    }

    /** The prompt to show for the current location, in the installed shell's style ({@code C:\>} by default). */
    default String prompt() {
        return currentLocation().dosPath() + ">";
    }

    /** This computer's host name as a POSIX shell shows it (its name, or the OS id when unnamed). */
    default String hostname() {
        return "computer";
    }

    /** One mounted drive for {@code df}: its letter, device name, capacity/free space, and whether a medium is present. */
    record MountInfo(char drive, String device, long capacityMbEq, long freeMbEq, boolean ready) {
    }

    /** Every drive the shell can see (the system disk first), for {@code df}; empty when there is no OS. */
    default List<MountInfo> mounts() {
        return List.of();
    }

    /**
     * Everything {@code screenfetch} shows about this machine: the distribution and its id (picks the
     * ASCII logo), kernel line, host and shell, the desktop environment (or the TTY), the hardware, disk
     * usage, installed package count and the world's uptime in ticks.
     */
    record SystemInfo(String distroId, String os, String kernel, String hostname, String shell,
                      String desktop, String cpu, int ramMb, long diskUsedMb, long diskTotalMb,
                      int packages, long uptimeTicks) {
    }

    /** The system information for {@code screenfetch}, or null when no OS is installed. */
    default SystemInfo systemInfo() {
        return null;
    }

    /** Whether the program with the given id is present on this computer (installed, or a service flag). */
    default boolean hasProgram(final net.minecraft.resources.ResourceLocation id) {
        return false;
    }

    // ---- packages (Linux package managers over the network mirror) ----

    /** The installed OS's package manager; {@code NONE} on media-installed platforms. */
    default dev.jstech.computronics.os.PackageManagerKind packageManager() {
        return dev.jstech.computronics.os.PackageManagerKind.NONE;
    }

    /** One package the mirror offers: its name, description, and whether this computer already has it. */
    /**
     * One package on the shelf.
     *
     * <p>{@code community} marks one a player wrote and published, as against one that came with the
     * machines. Both install the same way; a player is entitled to know which is which.
     */
    record PackageInfo(String name, String description, boolean installed, boolean building,
                       boolean community) {

        public PackageInfo(final String name, final String description, final boolean installed,
                           final boolean building) {
            this(name, description, installed, building, false);
        }
    }

    /**
     * Puts a built package on the network's Mirror, for anyone on the network to install.
     *
     * @param path the package file on this computer's disk
     */
    default OpResult publishPackage(final String path) {
        return OpResult.fail("could not resolve mirror://");
    }

    /** Takes one back off the Mirror. */
    default OpResult unpublishPackage(final String name) {
        return OpResult.fail("could not resolve mirror://");
    }

    /** The packages the network mirror offers this computer (empty when no mirror is reachable). */
    default List<PackageInfo> packagesAvailable() {
        return List.of();
    }

    /**
     * Installs the named package from the network mirror: resolves it, checks the OS/hardware gates, and
     * installs it (or, for a source-based manager, starts the build). The message reads like the package
     * manager's own output; a missing mirror is the classic "could not resolve" failure.
     */
    default OpResult packageInstall(final String name) {
        return OpResult.fail("could not resolve mirror://");
    }

    /**
     * Removes the named installed program (a package manager's remove verb, or the DOS-family
     * {@code uninstall} command). Mainframe services turn their agent off; a removed desktop
     * environment drops the computer back to the TTY on its next boot.
     */
    default OpResult packageRemove(final String name) {
        return OpResult.fail("unable to locate package " + name);
    }

    /**
     * Brings every installed package up to the current build. Packages installed before a mod update
     * carry the version they were installed at, so this is what reconciles a repository that moved on
     * without the machine — it never installs anything new.
     */
    default OpResult packageUpdate() {
        return OpResult.fail("could not resolve mirror://");
    }

    /**
     * Formats the drive with the given letter: erases the installed system, every file, and the item
     * storage on it. Refuses the drive the running system lives on.
     */
    default OpResult formatDrive(final char letter) {
        return OpResult.fail("format: drive not found");
    }

    /** Whether a network mirror is reachable from this computer right now. */
    default boolean mirrorReachable() {
        return false;
    }

    /** Controls the Mirror service on the network's Mainframe: {@code install|status}. */
    default OpResult mirrorControl(final String action) {
        return OpResult.fail("the network has no Mainframe");
    }

    /** Source builds still compiling on this computer: program id to ticks remaining. */
    default java.util.Map<String, Long> buildsRemaining() {
        return java.util.Map.of();
    }

    /**
     * One notice per source build that finished since the shell last asked (returned once, then forgotten):
     * a build completes while the player is elsewhere, so the shell prints these ahead of the next command.
     */
    default java.util.List<String> drainBuildNotices() {
        return java.util.List.of();
    }

    // ---- live installation media (the manual Arch / Gentoo installs) ----

    /** The live installation in progress on this computer, or null when it booted a real OS. */
    default dev.jstech.computronics.program.install.LiveInstallState liveInstall() {
        return null;
    }

    /**
     * Runs one live-installer line against the install state. The message carries the tool's output lines
     * (newline-separated); when the sequence completes, the host installs the system and ends the session.
     */
    default OpResult liveRun(final String line) {
        return OpResult.fail("no live medium is booted");
    }

    /** Asks the host to restart into its firmware setup once this command finishes (the {@code reboot --firmware} verb). */
    default void requestFirmwareReboot() {
    }

    /** Whether a command asked for a restart into the firmware during this run. */
    default boolean firmwareRebootRequested() {
        return false;
    }

    /** Asks the host to restart once this command finishes: the monitor replays the POST, then boots. */
    default void requestReboot() {
    }

    /** Whether a command asked for a plain restart during this run. */
    default boolean rebootRequested() {
        return false;
    }

    /** Sets the session's current drive and directory (used by {@code cd} and drive changes). */
    default void setCurrentLocation(final DosPath.Location location) {
    }

    // -------------------------------------------------------------------------
    // Filesystem (system disk)
    // -------------------------------------------------------------------------

    /**
     * Lists the files visible in the current directory of the host computer's system disk.
     *
     * <p>Returns a {@link FsResult} whose {@link FsResult#entries()} carries the listing, or a failure
     * message when no system disk or OS is present. The directory parameter follows the filesystem
     * kind of the installed OS kernel: {@code ""} for the root in FLAT and HIERARCHICAL.
     *
     * @param dir the directory to list ({@code ""} for the root)
     */
    default FsResult listDisk(final String dir) {
        return FsResult.noOs();
    }

    /**
     * Reads the content of a file on the host computer's system disk.
     *
     * <p>Returns a {@link FsResult} carrying the file text, or a failure message when the file is
     * absent, is a read-only {@code .dat} projection, or no system disk is present.
     *
     * @param path the file path relative to the root of the system disk
     */
    default FsResult readFile(final String path) {
        return FsResult.noOs();
    }

    /**
     * Deletes a file from the host computer's system disk.
     *
     * <p>Returns a {@link FsResult} with a confirmation message on success, or a failure message when the
     * file does not exist, is a read-only {@code .dat} projection, or no system disk is present.
     *
     * @param path the file path to delete
     */
    default FsResult deleteFile(final String path) {
        return FsResult.noOs();
    }

    /**
     * Writes (creates or overwrites) a user file on the host computer's system disk.
     *
     * <p>Returns a {@link FsResult} with a confirmation message on success, or a failure message when the
     * path is invalid for the filesystem, the file type is not user-editable, the disk is full, or no
     * system disk is present.
     *
     * @param path    the file path to write
     * @param content the UTF-8 text content
     */
    default FsResult writeFile(final String path, final String content) {
        return FsResult.noOs();
    }

    /**
     * Runs the content of an {@code .iql} file from the host computer's system disk as an IQL
     * statement, routing it through the same dispatch path as the {@code operation} command.
     *
     * <p>Returns a failure when the file is absent, is not an {@code .iql} file, or no system disk
     * is present. On success returns the same {@link OpResult} the engine would have returned.
     *
     * @param path the file path of the IQL script to execute
     */
    default FsResult runScript(final String path) {
        return FsResult.noOs();
    }

    /**
     * Changes the shell's current directory. {@code input} is a DOS path relative to the current
     * location (or absolute); implementations resolve it, verify the target directory exists, and
     * persist the new location. A blank input or {@code \} means the drive root.
     *
     * @param input the DOS path to change to
     * @return a confirmation, or a failure when the path does not exist
     */
    default FsResult changeDir(final String input) {
        return FsResult.noOs();
    }

    /**
     * Switches the shell's current drive to {@code drive}, restoring that drive's remembered current
     * directory. Fails when the drive letter is not mapped to any installed disk or linked medium.
     *
     * @param drive the drive letter (case-insensitive)
     * @return a confirmation, or a failure when the drive does not exist or is not ready
     */
    default FsResult changeDrive(final char drive) {
        return FsResult.noOs();
    }

    /**
     * The current per-computer settings as {@code key   value} lines, for the {@code config} command
     * to print. An empty list means this computer has no settings store.
     *
     * @return the settings summary lines, oldest-first
     */
    default java.util.List<String> configSummary() {
        return java.util.List.of();
    }

    /**
     * Changes one setting (the computer name, the network share, or a value owned by the settings
     * store), clamping as needed. Returns a confirmation or a failure describing the problem.
     *
     * @param key   the setting key (case-insensitive; e.g. {@code name}, {@code netshare}, {@code clock})
     * @param value the raw value
     * @return the outcome of the change
     */
    default OpResult setConfig(final String key, final String value) {
        return OpResult.fail("this computer has no settings store");
    }

    /**
     * Creates a directory. {@code path} is a DOS path relative to the current location (or absolute).
     *
     * @param path the directory to create
     * @return a confirmation, or a failure message
     */
    default FsResult makeDir(final String path) {
        return FsResult.noOs();
    }

    /**
     * Removes a directory and everything under it. {@code path} is a DOS path relative to the current
     * location (or absolute).
     *
     * @param path the directory to remove
     * @return a confirmation, or a failure message
     */
    default FsResult removeDir(final String path) {
        return FsResult.noOs();
    }

    /**
     * Copies a file (or directory subtree) from {@code src} to {@code dest}. Both are DOS paths
     * relative to the current location (or absolute).
     *
     * @param src  the source path
     * @param dest the destination path
     * @return a confirmation, or a failure message
     */
    default FsResult copyPath(final String src, final String dest) {
        return FsResult.noOs();
    }

    /**
     * Moves a file (or directory subtree) into the directory {@code destDir}. Both are DOS paths
     * relative to the current location (or absolute).
     *
     * @param src     the source path
     * @param destDir the destination directory
     * @return a confirmation, or a failure message
     */
    default FsResult movePath(final String src, final String destDir) {
        return FsResult.noOs();
    }

    /**
     * Renames a file or directory {@code src} to the new leaf name {@code newName} (kept in the same
     * parent directory). {@code src} is a DOS path relative to the current location (or absolute).
     *
     * @param src     the source path
     * @param newName the new leaf name
     * @return a confirmation, or a failure message
     */
    default FsResult renamePath(final String src, final String newName) {
        return FsResult.noOs();
    }

    /**
     * One entry returned by {@link #listDisk}: a file or a subdirectory of the listed directory.
     *
     * @param name     the entry's leaf name (a file name with its extension, or a directory name)
     * @param ext      the file extension without the dot, or {@code ""} for a directory
     * @param weightMbEq the file's disk weight in mB-equivalents ({@code 0} for a directory)
     * @param readOnly true if the entry cannot be written or deleted via the shell
     * @param isDir    true if this entry is a subdirectory rather than a file
     * @param modified the world game time the file was last written; {@code 0} means unknown
     */
    record FsEntry(String name, String ext, long weightMbEq, boolean readOnly, boolean isDir, long modified) {}

    /**
     * The result of a filesystem operation: either a success carrying optional entries + an optional
     * text payload + an optional {@link OpResult}, or a failure carrying an error message.
     */
    final class FsResult {

        private final boolean ok;
        private final String message;
        private final java.util.List<FsEntry> entries;
        private final OpResult opResult;

        private FsResult(final boolean ok, final String message, final java.util.List<FsEntry> entries,
                         final OpResult opResult) {
            this.ok = ok;
            this.message = message;
            this.entries = entries != null ? entries : java.util.List.of();
            this.opResult = opResult;
        }

        /** A listing result carrying one or more directory entries. */
        public static FsResult listing(final java.util.List<FsEntry> entries) {
            return new FsResult(true, "", entries, null);
        }

        /** A text-content result (for {@code type} / {@code cat}). */
        public static FsResult content(final String text) {
            return new FsResult(true, text, null, null);
        }

        /** A simple confirmation message (for {@code del}). */
        public static FsResult ok(final String message) {
            return new FsResult(true, message, null, null);
        }

        /** An IQL execution result forwarded from the dispatcher. */
        public static FsResult iqlResult(final OpResult result) {
            return new FsResult(result.ok(), result.message(), null, result);
        }

        /** A standard failure with a human-readable message. */
        public static FsResult fail(final String message) {
            return new FsResult(false, message, null, null);
        }

        /** Returned when no OS or system disk is present. */
        public static FsResult noOs() {
            return fail("no system disk or OS installed");
        }

        public boolean ok() { return ok; }
        public String message() { return message; }
        public java.util.List<FsEntry> entries() { return entries; }
        /** The forwarded IQL result when this is an {@link #iqlResult}, or {@code null} otherwise. */
        public OpResult opResult() { return opResult; }
    }

    /** Counts that describe the network at a glance. */
    record NetSummary(boolean linked, int servers, int personalComputers, int subframes,
                      int indexedTypes, boolean mainframePresent) {
    }

    // ---- Remote shells ---------------------------------------------------------------------------

    /** Every machine on this network a remote shell could reach, by host name. */
    default List<RemoteHost> reachableHosts() {
        return List.of();
    }

    /**
     * Opens a remote shell on {@code hostname}: from here on the session's commands run on that
     * machine until it is closed. Same network means access — authentication arrives with the
     * security module.
     */
    default OpResult sshConnect(final String hostname) {
        return OpResult.fail("ssh: not supported on this computer");
    }

    /** Closes the remote shell and returns to the local one; fails when there is no session. */
    default OpResult sshDisconnect() {
        return OpResult.fail("exit: not connected");
    }

    /** The host name of the machine this session is connected to, or {@code ""} when local. */
    default String sshSession() {
        return "";
    }

    /**
     * One machine a remote shell can reach. A machine can be addressed by any of these: the host
     * name it answers to, the name its owner gave it, or the head of its node id — whichever the
     * player has in front of them.
     *
     * @param hostname the shell host name (what the remote prompt shows)
     * @param name     the player-given machine name, or {@code ""} when unnamed
     * @param nodeId   the short node identifier
     * @param os       the operating system it runs, or {@code ""} when it has none
     * @param type     the kind of machine (Mainframe, Server, Personal Computer, ...)
     * @param running  whether it is powered on right now
     */
    record RemoteHost(String hostname, String name, String nodeId, String os, String type,
                      boolean running) {
    }

    /** A line in a storage listing: a name, a quantity, and an optional detail (e.g. where the item lives). */
    record StoredItem(String name, long quantity, String detail) {

        public StoredItem(final String name, final long quantity) {
            this(name, quantity, "");
        }
    }

    /** How much of an item one server holds. */
    record Holding(String server, long quantity) {
    }

    /** A snapshot of one in-flight operation; {@code id} is the short id {@code cancel} takes. */
    record ActiveOp(String id, String type, String item, long progress, long total, String status,
                    String priority) {
    }

    /** Stops the in-flight operation with this short id (as listed by {@code ops}). */
    default OpResult cancelOperation(final String id) {
        return OpResult.fail("this computer cannot cancel network operations");
    }

    /**
     * One operation type's last hour on the network: how many settled, the mean ticks waited and run, the
     * share that fell short in whole percent, and what the type moved.
     */
    record OperationStat(String type, int count, int averageWait, int averageRun, int shortfallPercent, long moved) {
    }

    /** The network's operation statistics for the last hour, empty off a network. */
    default List<OperationStat> operationStats() {
        return List.of();
    }

    /** The most operations the network had in flight at once today. */
    default int peakOperationsToday() {
        return 0;
    }

    /** A program installed on the computer: its short command name and full id, for listing. */
    record ProgramInfo(String name, String id) {
    }

    /**
     * One Cannon program this computer is running: what it is called, how it is getting on, and how much
     * of the room it was given it is holding.
     */
    record CannonProcess(int id, String name, String state, long heldBytes, long heapBytes) {
    }

    /** The Cannon programs running here, oldest first. */
    default List<CannonProcess> cannonProcesses() {
        return List.of();
    }

    /**
     * Starts a compiled Cannon program from a file on this computer's disk.
     *
     * @param path   the assembly file to run
     * @param heapMb how much room to give it, or 0 for what the computer decides
     */
    default OpResult startCannon(final String path, final int heapMb) {
        return OpResult.fail("cannon: not installed");
    }

    /** Stops one of the Cannon programs running here. */
    default OpResult stopCannon(final int id) {
        return OpResult.fail("cannon: not installed");
    }

    /** A service the computer hosts and its state, for the process/service manager (e.g. "running"). */
    record ServiceStatus(String name, String state) {
    }

    /** The outcome of an effecting command: whether it was accepted, and a message to print. */
    record OpResult(boolean ok, String message) {

        public static OpResult ok(final String message) {
            return new OpResult(true, message);
        }

        public static OpResult fail(final String message) {
            return new OpResult(false, message);
        }
    }
}
