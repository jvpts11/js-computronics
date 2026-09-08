/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os;

import dev.jstech.core.tier.HardwareEra;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;

import static dev.jstech.core.tier.HardwareEra.LEGACY;
import static dev.jstech.core.tier.HardwareEra.STANDARD;
import static dev.jstech.core.tier.HardwareEra.VINTAGE;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.Optional;

/**
 * Registers the built-in kernels and operating systems during the common-setup phase.
 *
 * <p>All registrations go through {@link JSComputersAPI} so the built-in entries exercise the
 * same public addon path that third-party developers use.
 */
@EventBusSubscriber(modid = "jsc", bus = EventBusSubscriber.Bus.MOD)
public final class OsBootstrap {

    private OsBootstrap() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(OsBootstrap::registerBuiltins);
    }

    // Built-in registrations

    static void registerBuiltins() {
        registerKernels();
        registerOses();
        registerDesktops();
        registerPrograms();
    }

    /**
     * The built-in kernels. A static list (built eagerly) so both the runtime registration and datagen read
     * from one source. dos = MS-DOS 2.0+ (real directories); win9x = cooperative; nt = preemptive (XP + 11);
     * net_min = the minimal network kernel with no scheduler or filesystem.
     */
    private static final java.util.List<KernelDef> BUILTIN_KERNELS = java.util.List.of(
            new KernelDef(rl("dos"), SchedulerKind.NONE, FilesystemKind.HIERARCHICAL, ShellFamily.DOS),
            new KernelDef(rl("win9x"), SchedulerKind.COOPERATIVE, FilesystemKind.HIERARCHICAL, ShellFamily.DOS),
            new KernelDef(rl("nt"), SchedulerKind.PREEMPTIVE, FilesystemKind.HIERARCHICAL, ShellFamily.DOS),
            new KernelDef(rl("net_min"), SchedulerKind.NONE, FilesystemKind.NONE, ShellFamily.DOS),
            /*
             * The Linux kernel: preemptive, a single rooted hierarchical filesystem, and POSIX shell syntax.
             * Public through the addon API, so any add-on distribution built on it speaks bash for free.
             */
            new KernelDef(rl("linux"), SchedulerKind.PREEMPTIVE, FilesystemKind.HIERARCHICAL, ShellFamily.POSIX));

    /**
     * The built-in operating systems, in install order. A static list (built eagerly) so datagen (lang and the
     * OS install media) reads the same source as the runtime registration.
     */
    /*
     * A system's size is in megabytes, the way its box would print it; what it costs in items depends on the
     * disk it lands on (1 MB an item at 16 bits, 16 MB at 32, 256 MB at 64). MC-DOS fills a fifth of a
     * 20 MB vintage drive; Frames 11 is 80 items of a standard disk and will not fit a vintage one at all.
     */
    private static final java.util.List<OsDef> BUILTIN_OSES = java.util.List.of(
            /*
             * MC-DOS: terminal-only CLI shell from the Vintage era.
             * Each system also says the RAM it holds for itself while running (withRam): a fifth of a
             * vintage machine's few megabytes for the DOS family, most of a small Legacy machine for XP,
             * a good part of a gigabyte for Frames 11. Its bundled programs weigh a quarter of that each.
             */
            OsDef.mediaInstalled(rl("mc_dos"), OsCapability.TERMINAL_ONLY, HardwareEra.VINTAGE, rl("dos"), 4,
                    Platform.MC_DOS, "MC-DOS", Optional.empty(), SoftwareHouse.MIDSOFT).withRam(1),
            // MC-NET: full-screen network GUI (the rewrapped network interactor), from the Vintage era.
            OsDef.mediaInstalled(rl("mc_net"), OsCapability.NETWORK_GUI, HardwareEra.VINTAGE, rl("net_min"), 8,
                    Platform.MC_NET, "MC-NET", Optional.empty(), SoftwareHouse.NOUVELL).withRam(2),
            // The Frames editions bundle their own desktop environment (the id doubles as the DE id).
            OsDef.mediaInstalled(rl("frames_95"), OsCapability.FULL_DESKTOP, HardwareEra.LEGACY, rl("win9x"), 48,
                    Platform.FRAMES, "Frames 95", Optional.of(rl("frames_95")), SoftwareHouse.MIDSOFT).withRam(16),
            OsDef.mediaInstalled(rl("frames_xp"), OsCapability.FULL_DESKTOP, HardwareEra.LEGACY, rl("nt"), 1_536,
                    Platform.FRAMES, "Frames XP", Optional.of(rl("frames_xp")), SoftwareHouse.MIDSOFT).withRam(64),
            OsDef.mediaInstalled(rl("frames_11"), OsCapability.FULL_DESKTOP, HardwareEra.STANDARD, rl("nt"), 20_480,
                    Platform.FRAMES, "Frames 11", Optional.of(rl("frames_11")), SoftwareHouse.MIDSOFT).withRam(768),

            /*
             * Linux distributions: all on the Linux kernel, all boot to a bash TTY until a desktop environment
             * is installed from the network mirror. Footprints are balancing estimates. The shells and package
             * managers are each distribution's real ones; Arch and Gentoo keep their manual installs. Each is
             * credited to its own house, the way the machines are credited to their makers.
             * A distribution's own share is its base system at the TTY; the desktop package it installs
             * weighs on top of it (the desktop environments below say how much).
             */
            OsDef.linuxDistro(rl("ubuntu"), 8_192, "Ubuntu", "bash", PackageManagerKind.APT, InstallMode.GUIDED,
                    SoftwareHouse.AXIOMATIC).withRam(48),
            OsDef.linuxDistro(rl("debian"), 4_096, "Debian", "bash", PackageManagerKind.APT, InstallMode.GUIDED,
                    SoftwareHouse.DEBIAN_CIRCLE).withRam(24),
            OsDef.linuxDistro(rl("fedora"), 8_192, "Fedora", "bash", PackageManagerKind.DNF, InstallMode.GUIDED,
                    SoftwareHouse.RED_CAP).withRam(48),
            OsDef.linuxDistro(rl("arch"), 2_048, "Arch Linux", "zsh", PackageManagerKind.PACMAN, InstallMode.LIVE_MANUAL,
                    SoftwareHouse.ARCH_COLLECTIVE).withRam(12),
            OsDef.linuxDistro(rl("gentoo"), 4_096, "Gentoo", "bash", PackageManagerKind.EMERGE, InstallMode.SOURCE,
                    SoftwareHouse.GENTOO_FOUNDRY).withRam(12)
            /*
             * OS case (c): PDA/Tablet/Smartphone portables ship with a factory mobile OS. Those item/block
             * types do not exist yet; register the mobile OS here once they do.
             */
    );

    /** The built-in kernels, so datagen and tooling read them from one source. */
    public static java.util.List<KernelDef> builtinKernels() {
        return BUILTIN_KERNELS;
    }

    /** The built-in operating systems, so datagen (lang, install media) reads them from one source. */
    public static java.util.List<OsDef> builtinOses() {
        return BUILTIN_OSES;
    }

    private static void registerKernels() {
        for (final KernelDef kernel : BUILTIN_KERNELS) {
            JSComputersAPI.registerKernel(kernel);
        }
    }

    private static void registerOses() {
        for (final OsDef os : BUILTIN_OSES) {
            JSComputersAPI.registerOS(os);
        }
    }

    /**
     * Desktop apps that run under any desktop environment: the Frames editions and a Linux desktop alike. On
     * Frames they arrive on install media; on Linux the same programs are packages the Mirror serves.
     */
    private static final java.util.Set<Platform> DESKTOPS = java.util.Set.of(Platform.FRAMES, Platform.LINUX);
    private static final java.util.Set<Platform> LINUX_ONLY = java.util.Set.of(Platform.LINUX);
    private static final java.util.Set<Platform> FRAMES_ONLY = java.util.Set.of(Platform.FRAMES);

    /** The nine built-in desktop apps every desktop environment can bundle, in rail order. */
    private static final java.util.List<ResourceLocation> BUILTIN_APPS = java.util.List.of(
            rl("network"), rl("this_pc"), rl("settings"), rl("files"), rl("editor"), rl("command_prompt"),
            rl("system_monitor"), rl("calculator"), rl("network_manager"));

    /**
     * The built-in desktop environments. The Frames editions bundle their own (the id equals the OS id, so the
     * existing skins and icon sets keep their keys); KDE Plasma, GNOME and Cinnamon are Linux packages, each
     * with its chrome and the native names its bundled apps show.
     */
    private static final java.util.List<DesktopEnvironmentDef> BUILTIN_DESKTOPS = java.util.List.of(
            new DesktopEnvironmentDef(rl("frames_95"), "Frames 95", PanelStyle.FRAMES_95, BUILTIN_APPS, java.util.Map.of(),
                    SoftwareHouse.MIDSOFT),
            new DesktopEnvironmentDef(rl("frames_xp"), "Frames XP", PanelStyle.FRAMES_XP, BUILTIN_APPS, java.util.Map.of(),
                    SoftwareHouse.MIDSOFT),
            new DesktopEnvironmentDef(rl("frames_11"), "Frames 11", PanelStyle.FRAMES_11, BUILTIN_APPS, java.util.Map.of(),
                    SoftwareHouse.MIDSOFT),
            new DesktopEnvironmentDef(rl("kde_plasma"), "KDE Plasma", PanelStyle.KDE, BUILTIN_APPS, java.util.Map.of(
                    rl("files"), "Dolphin", rl("editor"), "Kate", rl("command_prompt"), "Konsole",
                    rl("calculator"), "KCalc", rl("system_monitor"), "System Monitor",
                    rl("settings"), "System Settings", rl("this_pc"), "Info Center"), SoftwareHouse.KDE_GUILD),
            new DesktopEnvironmentDef(rl("gnome"), "GNOME", PanelStyle.GNOME, BUILTIN_APPS, java.util.Map.of(
                    rl("files"), "Files", rl("editor"), "Text Editor", rl("command_prompt"), "Terminal",
                    rl("calculator"), "Calculator", rl("system_monitor"), "System Monitor",
                    rl("settings"), "Settings", rl("this_pc"), "About"), SoftwareHouse.GNOME_TRUST),
            new DesktopEnvironmentDef(rl("cinnamon"), "Cinnamon", PanelStyle.CINNAMON, BUILTIN_APPS, java.util.Map.of(
                    rl("files"), "Nemo", rl("editor"), "xed", rl("command_prompt"), "Terminal",
                    rl("calculator"), "Calculator", rl("system_monitor"), "System Monitor",
                    rl("settings"), "System Settings", rl("this_pc"), "System Info"), SoftwareHouse.SPEARMINT)
    );

    /** The built-in desktop environments, so tooling reads them from one source. */
    public static java.util.List<DesktopEnvironmentDef> builtinDesktops() {
        return BUILTIN_DESKTOPS;
    }

    private static void registerDesktops() {
        for (final DesktopEnvironmentDef desktop : BUILTIN_DESKTOPS) {
            JSComputersAPI.registerDesktopEnvironment(desktop);
        }
    }
    private static final java.util.Set<Platform> ALL_PLATFORMS =
            java.util.Set.of(Platform.MC_DOS, Platform.MC_NET, Platform.FRAMES, Platform.LINUX);

    /**
     * The built-in program descriptors, in desktop launcher order (the built-in apps first, then the
     * installables). Built eagerly as a static list so both the runtime registration and datagen (which does
     * not run common setup) read from the same single source. Hardware minimums are conservative balancing
     * estimates; {@code hostScope} replaces the old per-program install special cases.
     */
    private static final java.util.List<ProgramSpec> BUILTIN_PROGRAMS = java.util.List.of(
            /*
             * Built-in Frames apps: pre-installed, no install disc, always present on a Frames desktop (subject
             * to host scope and OS rank). These used to be a hardcoded launcher list.
             * The built-in apps carry no house of their own: Files is Midsoft's on Frames and the KDE Guild's on
             * Plasma. The network tools are the exception: they are the hardware house's wherever they run.
             */
            ProgramSpec.of(rl("network"), "network", "Network", true, DESKTOPS, 0, ProgramKind.APP, 0, HostScope.ANY)
                    .withHouse(SoftwareHouse.JSC),
            /*
             * "This PC" is a Frames idea and stays one. Linux has no single such place: its volumes
             * live in the file manager's device list and the detail in a disks utility, which is what
             * the Disks program below is.
             */
            ProgramSpec.of(rl("this_pc"), "thispc", "This PC", true, FRAMES_ONLY, 0, ProgramKind.APP, 0, HostScope.ANY),
            ProgramSpec.of(rl("disks"), "disks", "Disks", true, LINUX_ONLY, 0, ProgramKind.APP, 0, HostScope.ANY),
            ProgramSpec.of(rl("settings"), "settings", "Settings", true, DESKTOPS, 0, ProgramKind.APP, 0, HostScope.ANY),
            ProgramSpec.of(rl("files"), "files", "Files", true, DESKTOPS, 0, ProgramKind.APP, 0, HostScope.ANY),
            ProgramSpec.of(rl("editor"), "editor", "Editor", true, DESKTOPS, 0, ProgramKind.APP, 0, HostScope.ANY),
            /*
             * The Command Prompt ships with every computer (terminal on MC-DOS, shell app on Frames), so it is
             * allowed on every platform, matching its prior behaviour of no gating at all.
             */
            ProgramSpec.of(rl("command_prompt"), "cmd", "Command Prompt", true, ALL_PLATFORMS, 0, ProgramKind.APP, 0, HostScope.ANY),
            ProgramSpec.of(rl("system_monitor"), "sysmon", "System Monitor", true, DESKTOPS, 0, ProgramKind.APP, 0, HostScope.ANY),
            ProgramSpec.of(rl("calculator"), "calc", "Calculator", true, DESKTOPS, 0, ProgramKind.APP, 0, HostScope.ANY),
            // The Network Manager is pre-installed but exclusive to the Mainframe, and needs Frames XP or newer.
            ProgramSpec.of(rl("network_manager"), "netmgr", "Network Manager", true, DESKTOPS, 0, ProgramKind.APP, 2, HostScope.MAINFRAME)
                    .withHouse(SoftwareHouse.JSC),
            /*
             * The Task Manager ships with every desktop but keeps off the desktop and the Start menu: it is
             * reached by right-clicking the panel, the way it always was, so it is not in the bundled list.
             */
            ProgramSpec.of(rl("task_manager"), "taskmgr", "Task Manager", true, DESKTOPS, 0, ProgramKind.APP, 0, HostScope.ANY),

            /*
             * Installables. The OS rank only gates the Frames editions (a Linux distribution ranks 0, so any
             * desktop program the Mirror serves installs on it once a desktop environment is present).
             * Network Management Studio: a professional network tool -> Frames XP or newer (rank 2).
             * Every installable also says the generation it was WRITTEN in (withEra): that decides the
             * medium it ships on and the year on its banner, and never where it may install. The OS rank
             * stays the gate. A modern tool that still runs on XP is Standard-era software on a DVD.
             * Each installable also names its house (withHouse): the maker on its disc and its banner.
             * And each says the RAM it holds while it runs (withRam): the balancing estimates follow the
             * generation the tool was written in, so a modern tool is the heavier one.
             */
            ProgramSpec.of(rl("nms"), "nms", "Network Management Studio", false, DESKTOPS, 128, ProgramKind.APP, 2, HostScope.ANY)
                    .withEra(STANDARD).withHouse(SoftwareHouse.MIDSOFT).withRam(128),
            /*
             * The IQL Engine is a headless Mainframe service (the NMS is its client): every platform, lives on
             * the Mainframe, follows the NMS OS version (Frames XP or newer).
             */
            ProgramSpec.of(rl("iqlengine"), "iqlengine", "IQL Engine", false, ALL_PLATFORMS, 32, ProgramKind.SERVICE, 2, HostScope.MAINFRAME)
                    .withEra(LEGACY).withHouse(SoftwareHouse.MIDSOFT).withRam(24),
            // The Crafting Manager installs only on a Crafting Computer -> Frames XP or newer.
            ProgramSpec.of(rl("crafting_manager"), "craftmgr", "Crafting Manager", false, DESKTOPS, 128, ProgramKind.APP, 2, HostScope.CRAFTING_COMPUTER)
                    .withEra(LEGACY).withHouse(SoftwareHouse.AUTODECK).withRam(32),
            // The Pattern Studio authors recipe files on any desktop and hands them to a linked encoder.
            ProgramSpec.of(rl("pattern_studio"), "studio", "Pattern Studio", false, DESKTOPS, 96, ProgramKind.APP, 2, HostScope.ANY)
                    .withEra(LEGACY).withHouse(SoftwareHouse.AUTODECK).withRam(24),
            // The Cluster Manager installs only on a Cluster Management Computer -> Frames XP or newer.
            ProgramSpec.of(rl("cluster_manager"), "clustermgr", "Cluster Manager", false, DESKTOPS, 96, ProgramKind.APP, 2, HostScope.CLUSTER_MANAGEMENT_COMPUTER)
                    .withEra(STANDARD).withHouse(SoftwareHouse.JSC).withRam(96),
            // Minesweeper: a small game available on any desktop (rank 0 = Frames 95 and newer).
            ProgramSpec.of(rl("minesweeper"), "mines", "Minesweeper", false, DESKTOPS, 16, ProgramKind.APP, 0, HostScope.ANY)
                    .withEra(VINTAGE).withHouse(SoftwareHouse.MIDSOFT).withRam(1),
            // Storage Insights: a network dashboard -> Frames XP or newer.
            ProgramSpec.of(rl("storage_insights"), "insights", "Storage Insights", false, DESKTOPS, 64, ProgramKind.APP, 2, HostScope.ANY)
                    .withEra(STANDARD).withHouse(SoftwareHouse.VAULTIS).withRam(64),
            // Craft Planner: a network planning tool -> Frames XP or newer.
            ProgramSpec.of(rl("craft_planner"), "planner", "Craft Planner", false, DESKTOPS, 64, ProgramKind.APP, 2, HostScope.ANY)
                    .withEra(STANDARD).withHouse(SoftwareHouse.AUTODECK).withRam(64),
            // The Automation Engine is a headless Mainframe service; it needs the modern OS (Frames 11, rank 3).
            ProgramSpec.of(rl("automation_engine"), "autoeng", "Automation Engine", false, ALL_PLATFORMS, 32, ProgramKind.SERVICE, 3, HostScope.MAINFRAME)
                    .withEra(STANDARD).withHouse(SoftwareHouse.RED_CAP).withRam(64),
            // The Automation Manager is a modern automation front-end -> Frames 11 (rank 3).
            ProgramSpec.of(rl("automation_manager"), "automgr", "Automation Manager", false, DESKTOPS, 64, ProgramKind.APP, 3, HostScope.ANY)
                    .withEra(STANDARD).withHouse(SoftwareHouse.RED_CAP).withRam(96),
            /*
             * Server services: headless daemons that only make sense on a machine mounted in a rack,
             * which is what gives a server its ROLE: hardware decides capacity, software decides job.
             * Predictive Cache keeps the hot items staged, so queries this bay serves come back sooner.
             */
            ProgramSpec.of(rl("predictive_cache"), "predcache", "Predictive Cache", false, ALL_PLATFORMS, 32, ProgramKind.SERVICE, 0, HostScope.SERVER)
                    .withEra(STANDARD).withHouse(SoftwareHouse.VAULTIS).withRam(128),
            // Load Balancer spreads writes across the bay's drives instead of filling them in order.
            ProgramSpec.of(rl("load_balancer"), "loadbal", "Load Balancer", false, ALL_PLATFORMS, 32, ProgramKind.SERVICE, 0, HostScope.SERVER)
                    .withEra(LEGACY).withHouse(SoftwareHouse.JSC).withRam(16),
            /*
             * Integrity Monitor re-reads what a hot event left in doubt, so light index maintenance
             * stops being a chore (a fragmented index still wants a vacuum by hand).
             */
            ProgramSpec.of(rl("integrity_monitor"), "integrity", "Integrity Monitor", false, ALL_PLATFORMS, 32, ProgramKind.SERVICE, 0, HostScope.SERVER)
                    .withEra(STANDARD).withHouse(SoftwareHouse.VAULTIS).withRam(32),
            /*
             * Remote Control: the graphical way into the network's other machines (a headless rack server
             * above all). Any desktop, Frames XP or newer, the point-and-click twin of ssh.
             */
            ProgramSpec.of(rl("remote_control"), "remotectl", "Remote Control", false, DESKTOPS, 48, ProgramKind.APP, 2, HostScope.ANY)
                    .withEra(STANDARD).withHouse(SoftwareHouse.MIDSOFT).withRam(48),
            /*
             * The Mirror: the package repository service every Linux computer on the network installs from
             * (apt/dnf/pacman/emerge resolve against it). A headless Mainframe service on any platform.
             */
            ProgramSpec.of(rl("mirror"), "mirror", "Mirror", false, ALL_PLATFORMS, 64, ProgramKind.SERVICE, 0, HostScope.MAINFRAME)
                    .withEra(STANDARD).withHouse(SoftwareHouse.JSC).withRam(32),
            /*
             * screenfetch: the little system-identity tool, a package the Mirror serves to any Linux (its
             * absence teaching the package manager: 'command not found' until you apt/dnf/pacman/emerge it).
             */
            ProgramSpec.of(rl("screenfetch"), "screenfetch", "screenfetch", false, LINUX_ONLY, 4, ProgramKind.APP, 0, HostScope.ANY)
                    .withEra(LEGACY).withHouse(SoftwareHouse.ARCH_COLLECTIVE).withRam(1),
            /*
             * The Cannon toolchain: the compiler and the runtime, two packages the Mirror serves to any
             * machine of the Legacy generation or later running Frames XP or a Linux. Neither has a window
             * of its own; both are verbs at the prompt, which is where a program is written and run from.
             */
            ProgramSpec.of(rl("cannonc"), "cannonc", "Cannon Compiler", false, ALL_PLATFORMS, 8, ProgramKind.APP, 2, HostScope.ANY)
                    .withMinEra(LEGACY).withEra(LEGACY).withHouse(SoftwareHouse.CANNON_FOUNDATION).withRam(16),
            ProgramSpec.of(rl("cannonrt"), "cannon", "Cannon Runtime", false, ALL_PLATFORMS, 12, ProgramKind.SERVICE, 2, HostScope.ANY)
                    .withMinEra(LEGACY).withEra(LEGACY).withHouse(SoftwareHouse.CANNON_FOUNDATION).withRam(24),
            /*
             * Virtual Studio: the whole workshop in one window, and the only editor that says what a call
             * will cost the program before the line is written. Frames only, and it asks the machine to
             * prove it: a quarter of a gigabyte held while it is open, which is what a program of the
             * generation after this one weighs.
             */
            ProgramSpec.of(rl("virtual_studio"), "virtualstudio", "Virtual Studio", false, FRAMES_ONLY, 512, ProgramKind.APP, 2, HostScope.ANY)
                    .withMinEra(LEGACY).withEra(STANDARD).withHouse(SoftwareHouse.MIDSOFT).withRam(256),
            /*
             * Virtual Studio Code: the light editor for Cannon, with the machine's programs down the side
             * and its console welded into the bottom of the window, so a program is written, compiled and
             * run without leaving it. Frames XP or newer, and any Linux desktop.
             */
            ProgramSpec.of(rl("virtual_studio_code"), "virtualcode", "Virtual Studio Code", false, DESKTOPS, 128, ProgramKind.APP, 2, HostScope.ANY)
                    .withMinEra(LEGACY).withEra(STANDARD).withHouse(SoftwareHouse.MIDSOFT).withRam(48),
            /*
             * Exposure: the editor that compiles every program on the disk instead of the one in front
             * of you, so changing something shared shows which of the others stopped building. It offers
             * nothing as you type, which is the trade.
             */
            ProgramSpec.of(rl("exposure"), "exposure", "Exposure", false, DESKTOPS, 192, ProgramKind.APP, 2, HostScope.ANY)
                    .withMinEra(LEGACY).withEra(LEGACY).withHouse(SoftwareHouse.DAYLIGHT_FOUNDATION).withRam(64),
            /*
             * Vim takes over the terminal it was started from instead of opening a window of its own,
             * which is the only reason a rack server with no graphics can be programmed at all, and why
             * it runs on every platform there is a prompt on. Four megabytes, and it shows.
             */
            ProgramSpec.of(rl("vim"), "vim", "Vim", false, ALL_PLATFORMS, 8, ProgramKind.APP, 0, HostScope.ANY)
                    .withMinEra(VINTAGE).withEra(VINTAGE).withHouse(SoftwareHouse.BUNDLED).withRam(4),
            /*
             * Emacs is also a terminal editor and also the answer to a different question: it splits the
             * glass, so the source and what the compiler said about it are readable at once. Three times
             * the memory of Vim, which is the trade.
             */
            ProgramSpec.of(rl("emacs"), "emacs", "Emacs", false, ALL_PLATFORMS, 24, ProgramKind.APP, 0, HostScope.ANY)
                    .withMinEra(VINTAGE).withEra(VINTAGE).withHouse(SoftwareHouse.BUNDLED).withRam(12),
            /*
             * The Linux desktop environments: packages that turn a TTY distribution into a graphical desktop.
             * Footprints are balancing estimates (Plasma is the heaviest, Cinnamon the lightest), and so is
             * the RAM each holds once it is up, on top of the distribution's own share.
             * Each one requires the hardware generation it belongs to. KDE and GNOME are old enough to
             * run on Legacy machines; Cinnamon is a much later desktop and needs Standard hardware. A
             * Vintage computer therefore has no graphical desktop at all and lives at the TTY.
             */
            ProgramSpec.of(rl("kde_plasma"), "kde-plasma", "KDE Plasma", false, LINUX_ONLY, 256, ProgramKind.DESKTOP_ENVIRONMENT, 0, HostScope.ANY)
                    .withMinEra(LEGACY).withEra(LEGACY).withHouse(SoftwareHouse.KDE_GUILD).withRam(224),
            ProgramSpec.of(rl("gnome"), "gnome", "GNOME", false, LINUX_ONLY, 192, ProgramKind.DESKTOP_ENVIRONMENT, 0, HostScope.ANY)
                    .withMinEra(LEGACY).withEra(LEGACY).withHouse(SoftwareHouse.GNOME_TRUST).withRam(256),
            ProgramSpec.of(rl("cinnamon"), "cinnamon", "Cinnamon", false, LINUX_ONLY, 160, ProgramKind.DESKTOP_ENVIRONMENT, 0, HostScope.ANY)
                    .withMinEra(STANDARD).withEra(STANDARD).withHouse(SoftwareHouse.SPEARMINT).withRam(160)
    );

    /** The built-in program descriptors, so datagen (lang, install media) reads them from one source. */
    public static java.util.List<ProgramSpec> builtinPrograms() {
        return BUILTIN_PROGRAMS;
    }

    private static void registerPrograms() {
        for (final ProgramSpec program : BUILTIN_PROGRAMS) {
            JSComputersAPI.registerProgram(program);
        }
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("jsc", path);
    }
}
