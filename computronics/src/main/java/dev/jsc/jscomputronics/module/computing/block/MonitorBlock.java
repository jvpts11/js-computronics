/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.core.peripheral.PeripheralCableType;
import dev.jstech.core.peripheral.PeripheralConnectable;
import dev.jstech.core.peripheral.PeripheralOwner;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.core.util.BlockEntityTickers;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.PeripheralLinks;
import dev.jsc.jscomputronics.module.computing.os.OsHost;
import dev.jsc.jscomputronics.module.computing.blockentity.MonitorBlockEntity;
import dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu;
import dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.OpenComputerUiPayload;
import dev.jsc.jscomputronics.module.computing.os.FirmwareKind;
import dev.jsc.jscomputronics.module.computing.os.boot.BootController;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Monitor: a peripheral that displays the interface of the computer it is linked to (over a Peripheral Cable, ≤ 16 blocks).
 *
 * <p>Implements {@link EraChassisBlock} so era-specific subclasses ({@link VintageMonitorBlock},
 * {@link LegacyMonitorBlock}) each wear their own era's textures and the {@code LIT} blockstate
 * texture resolves to the correct on-screen OS style.
 */
public class MonitorBlock extends HorizontalDirectionalBlock implements EntityBlock, PeripheralConnectable, EraChassisBlock {

    public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public MonitorBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends MonitorBlock> codec() {
        return CODEC;
    }

    /** The hardware era this monitor chassis belongs to. Overridden by era-specific subclasses. */
    public HardwareEra era() {
        return HardwareEra.STANDARD;
    }

    @Override
    public HardwareEra chassisEra() {
        return era();
    }

    @Override
    public PeripheralCableType peripheralType() {
        return PeripheralCableType.COMPUTING;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        // A Monitor is meant to be looked AT, so the screen faces the player who places it.
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(LIT, false);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        final BlockPos owner = monitor.ownerPos();
        if (owner == null) {
            // Explain WHY the screen is dark instead of a generic "not linked", so a missing GPU
            // (the most common cause) or a full host is obvious rather than silent.
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(diagnoseUnlinked(level, pos), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // The monitor mirrors the linked computer's OS: the screen depends on the installed OS, not on
        // the monitor. The desktop, terminal and network GUI are all server-opened container menus; the
        // firmware setup is a client-only screen the server requests via OpenComputerUiPayload; with no
        // OS the screen stays dark with a hint.
        if (level.isClientSide()) {
            // The server (which alone knows the installed OS) decides and opens the right screen.
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            // Using the monitor directly always means "show me MY machine": any remote session this
            // screen was holding ends here.
            monitor.setRemoteSession(null);
            bootOrPost(serverPlayer, level, pos, owner);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * The monitor-use entry point: a powered-off computer shows nothing (no boot, no firmware — the screen
     * has no signal); a freshly powered-on or rebooting one plays the power-on self-test first (DEL during
     * it enters the firmware setup); otherwise the boot target opens directly.
     */
    public static void bootOrPost(final ServerPlayer player, final Level level, final BlockPos monitorPos,
                                  final BlockPos owner) {
        // A rack holding several machines has to be told which one the monitor means: that is what
        // the KVM Switch is for. Without one the screen cannot address a bay at all.
        if (level.getBlockEntity(owner)
                instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack) {
            final java.util.List<Integer> computers = rack.computerSlots();
            if (computers.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("block.jsc.monitor.rack_empty"), true);
                return;
            }
            if (computers.size() > 1) {
                if (!rack.hasKvmSwitch()) {
                    player.displayClientMessage(
                            Component.translatable("block.jsc.monitor.needs_kvm"), true);
                    return;
                }
                openKvmChannels(player, rack, monitorPos);
                return;
            }
        }
        openSession(player, level, monitorPos, owner);
    }

    /**
     * Starts the session of the machine the KVM switch is currently showing. Called after the player
     * picks a channel, so it skips the channel bar it just came from.
     */
    public static void openSelectedChannel(final ServerPlayer player, final Level level,
                                           final BlockPos monitorPos, final BlockPos rackPos) {
        openSession(player, level, monitorPos, rackPos);
    }

    /** What the monitor shows when a session on this machine is opened. */
    public enum Entry {
        /** The machine is off: the screen has no signal. */
        NO_POWER,
        /** A fresh power-up or a restart: the power-on self-test first. */
        POST,
        /** A guided installer has written the system and still waits for the reboot that boots it. */
        INSTALLER,
        /** Hand over to whatever the boot target is (firmware, shell, desktop). */
        BOOT
    }

    /**
     * Decides the entry for a session on {@code computer}. A finished installer keeps the machine
     * until it restarts, exactly like a real one: closing the monitor is not a reboot. If the system
     * it installed is no longer on that disk (formatted, pulled), there is nothing to reboot into and
     * the pending install is dropped.
     */
    public static Entry entryFor(final OsHost computer) {
        if (!computer.isRunning()) {
            return Entry.NO_POWER;
        }
        if (computer.needsPost()) {
            return Entry.POST;
        }
        final int slot = computer.pendingInstallSlot();
        if (slot != OsHost.NO_PENDING_INSTALL) {
            final boolean systemStillThere = slot < 0 ? computer.hasOs()
                    : dev.jsc.jscomputronics.module.computing.os.OsDisks.hasSystem(computer.diskInSlot(slot));
            if (systemStillThere) {
                return Entry.INSTALLER;
            }
            computer.setPendingInstallSlot(OsHost.NO_PENDING_INSTALL);
        }
        return Entry.BOOT;
    }

    private static void openSession(final ServerPlayer player, final Level level, final BlockPos monitorPos,
                                    final BlockPos owner) {
        if (level.getBlockEntity(owner) instanceof OsHost computer) {
            switch (entryFor(computer)) {
                case NO_POWER -> {
                    player.displayClientMessage(Component.translatable("block.jsc.monitor.no_power"), true);
                    return;
                }
                case POST -> {
                    openPost(player, level, monitorPos, owner);
                    return;
                }
                case INSTALLER -> {
                    openInstallerPrompt(player, level, monitorPos, owner, computer);
                    return;
                }
                default -> {
                }
            }
        }
        openBootTarget(player, level, monitorPos, owner);
    }

    /** Sends the client the finished installer's reboot prompt for the system it just put on the disk. */
    private static void openInstallerPrompt(final ServerPlayer player, final Level level, final BlockPos monitorPos,
                                            final BlockPos owner, final OsHost computer) {
        final int slot = computer.pendingInstallSlot();
        final HardwareEra era = computer.displayEra();
        final FirmwareKind kind = FirmwareKind.forEra(era != null ? era : HardwareEra.STANDARD);
        final net.minecraft.resources.ResourceLocation osId = slot < 0 ? computer.installedOsId()
                : computer.diskInSlot(slot).get(dev.jsc.jscomputronics.module.computing.ComputingModule.SYSTEM_OS.get());
        final dev.jsc.jscomputronics.module.computing.os.OsDef os = osId == null ? null
                : dev.jsc.jscomputronics.module.computing.os.OsRegistry.getOs(osId);
        final String osName = os != null ? os.displayName() : "";
        final String targetLabel = slot < 0 ? "the default disk" : "Disk " + slot;
        PacketDistributor.sendToPlayer(player, new dev.jsc.jscomputronics.module.computing.operation.payload
                .OpenInstallDonePayload(owner, monitorPos, kind.ordinal(), osName, targetLabel, slot, ""));
    }

    /** Sends the client the switch's channel bar: every machine this rack can put on the monitor. */
    private static void openKvmChannels(
            final ServerPlayer player,
            final dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack,
            final BlockPos monitorPos) {
        final java.util.List<dev.jsc.jscomputronics.module.computing.operation.payload
                .OpenKvmPayload.Channel> channels = new java.util.ArrayList<>();
        for (final int slot : rack.computerSlots()) {
            final net.minecraft.world.item.ItemStack stack = rack.getServers().getStackInSlot(slot);
            final String custom = dev.jsc.jscomputronics.module.computing.item.ServerItem.customName(stack);
            channels.add(new dev.jsc.jscomputronics.module.computing.operation.payload
                    .OpenKvmPayload.Channel(slot,
                    custom.isEmpty() ? "bay " + (slot + 1) + "U" : custom,
                    rack.bayPowerOn(slot)
                            && dev.jsc.jscomputronics.module.computing.item.ServerItem.build(stack) != null));
        }
        PacketDistributor.sendToPlayer(player,
                new dev.jsc.jscomputronics.module.computing.operation.payload.OpenKvmPayload(
                        rack.getBlockPos(), monitorPos, rack.activeChannel(), channels));
    }

    /** Sends the client the era-styled power-on self-test for the host computer on this monitor. */
    public static void openPost(final ServerPlayer player, final Level level, final BlockPos monitorPos,
                                final BlockPos owner) {
        final BlockEntity ownerBe = level.getBlockEntity(owner);
        final String name = level.getBlockState(owner).getBlock().getName().getString();
        final HardwareEra era = ownerBe instanceof OsHost c ? c.displayEra() : null;
        final FirmwareKind kind = FirmwareKind.forEra(era != null ? era : HardwareEra.STANDARD);
        PacketDistributor.sendToPlayer(player, new dev.jsc.jscomputronics.module.computing.operation.payload
                .OpenPostPayload(owner, monitorPos, kind.ordinal(), name));
    }

    /**
     * Boots the computer at {@code owner} on the monitor at {@code monitorPos}: opens the UI its boot target
     * calls for (firmware when there is no system, else the installed OS's desktop / terminal / network GUI).
     * Also used by the firmware boot manager after the player picks a boot entry.
     */
    public static void openBootTarget(final ServerPlayer player, final Level level, final BlockPos monitorPos,
                                      final BlockPos owner) {
        final BlockEntity ownerBe = level.getBlockEntity(owner);
        // A powered-off computer opens nothing, whichever path asked for the boot (monitor use, a firmware
        // action, a reboot): the screen simply has no signal until the machine is switched on.
        if (ownerBe instanceof OsHost gate) {
            if (!gate.isRunning()) {
                player.displayClientMessage(Component.translatable("block.jsc.monitor.no_power"), true);
                return;
            }
            // Drop a live-install session whose medium was pulled out, so the boot below falls back to
            // the firmware (or the disk system) instead of resurrecting the installer shell.
            gate.validateOsSession();
        }
        final BootController.BootTarget target = BootController.targetForComputer(ownerBe);
        switch (target) {
            case FIRMWARE -> openFirmwareUi(player, level, monitorPos, owner, ownerBe);
            case FULL_DESKTOP -> openDesktopUi(player, level, monitorPos, owner, ownerBe);
            case TERMINAL_ONLY -> openCommandPrompt(player, level, monitorPos, owner);
            case NETWORK_GUI -> openTerminal(player, level, monitorPos, owner);
        }
        // Booting into an installed OS is an advancement criterion (the live installers do not count yet).
        if (target != BootController.BootTarget.FIRMWARE
                && ownerBe instanceof OsHost c && c.installedOsId() != null
                && (c.console() == null || c.console().liveInstall() == null)) {
            dev.jsc.jscomputronics.module.computing.ComputingModule.OS_FIRST_BOOT.get()
                    .trigger(player, c.installedOsId());
        }
    }

    /** Opens the firmware setup on the monitor regardless of an installed OS (a restart into setup). */
    public static void openFirmware(final ServerPlayer player, final Level level, final BlockPos monitorPos,
                                    final BlockPos owner) {
        openFirmwareUi(player, level, monitorPos, owner, level.getBlockEntity(owner));
    }


    /** Sends the client the era-correct firmware setup screen for the host computer. */
    private static void openFirmwareUi(final ServerPlayer player, final Level level, final BlockPos monitorPos,
                                       final BlockPos owner, final BlockEntity ownerBe) {
        final String name = level.getBlockState(owner).getBlock().getName().getString();
        final HardwareEra era = ownerBe instanceof OsHost c ? c.displayEra() : null;
        final FirmwareKind kind = FirmwareKind.forEra(era != null ? era : HardwareEra.STANDARD);
        PacketDistributor.sendToPlayer(player, new OpenComputerUiPayload(owner, monitorPos, kind.ordinal(), name));
    }

    /** Opens the desktop shell (a real container menu) for the host's installed FULL_DESKTOP OS. */
    private static void openDesktopUi(final ServerPlayer player, final Level level, final BlockPos monitorPos,
                                      final BlockPos owner, final BlockEntity ownerBe) {
        if (ownerBe instanceof OsHost c && c.installedOsId() != null) {
            final String name = level.getBlockState(owner).getBlock().getName().getString();
            final net.minecraft.resources.ResourceLocation osId = c.installedOsId();
            final Component title = level.getBlockState(owner).getBlock().getName();
            final int ram = (int) Math.min(Integer.MAX_VALUE, c.ramBuffer());
            // The desktop environment: the OS's bundled one (Frames) or the Linux package installed.
            // The desktop this session booted, not whatever is on disk right now: a package installed
            // since the machine came up belongs to the next boot.
            final net.minecraft.resources.ResourceLocation desktopId =
                    c.bootedDesktopId() != null ? c.bootedDesktopId() : osId;
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new dev.jsc.jscomputronics.module.computing.menu.DesktopMenu(
                            id, inv, monitorPos, owner, osId, desktopId, name, ram), title),
                    buf -> dev.jsc.jscomputronics.module.computing.menu.DesktopMenu.writeOpenBuffer(
                            buf, monitorPos, owner, osId, desktopId, name, ram));
        }
    }

    /** Opens the Command Prompt (the sole shell of a terminal-only OS) on this monitor for its host. */
    private static void openCommandPrompt(final ServerPlayer player, final Level level,
                                          final BlockPos monitorPos, final BlockPos owner) {
        if (level.getBlockEntity(owner) instanceof OsHost host) {
            final HardwareEra era = host.displayEra();
            final Component title = level.getBlockState(owner).getBlock().getName();
            // A POSIX (Linux) OS gets a login banner and a bash-style prompt: tell the client which shell,
            // host name and OS it is booting so it can draw them before the first command round-trip.
            final dev.jsc.jscomputronics.module.computing.os.OsDef os = host.installedOs();
            final dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState live =
                    host.console() == null ? null : host.console().liveInstall();
            final boolean posix = dev.jsc.jscomputronics.module.computing.program.ServerCliComputer.shellFamilyOf(host)
                    == dev.jsc.jscomputronics.module.computing.os.ShellFamily.POSIX;
            final String shellId;
            final String hostname;
            final String osLabel;
            if (live != null) {
                // A booted live medium: a root shell on the installer, named after the medium.
                shellId = "live";
                hostname = live.hostname();
                osLabel = (live.distro() == dev.jsc.jscomputronics.module.computing.program.install.LiveInstallState.Distro.ARCH
                        ? "Arch Linux" : "Gentoo") + " live";
            } else {
                shellId = posix && os != null ? os.shellId() : "";
                hostname = posix
                        && host instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost terminalHost
                        && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                        ? new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(terminalHost, serverLevel)
                                .hostname()
                        : "";
                osLabel = os == null ? "" : os.displayName();
            }
            // Each platform gets its own console screen: the Linux TTY (installed distributions and live
            // media), the MC-DOS terminal, and the MC-NET Command Prompt window — never each other's.
            final boolean tty = posix || live != null;
            final boolean dos = !tty && os != null
                    && os.platform() == dev.jsc.jscomputronics.module.computing.os.Platform.MC_DOS;
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> tty
                            ? new dev.jsc.jscomputronics.module.computing.menu.LinuxTtyMenu(
                                    id, inv, monitorPos, owner, era, shellId, hostname, osLabel)
                            : dos
                                    ? new dev.jsc.jscomputronics.module.computing.menu.DosTerminalMenu(
                                            id, inv, monitorPos, owner, era, shellId, hostname, osLabel)
                                    : new CommandPromptMenu(id, inv, monitorPos, owner, era, shellId, hostname, osLabel),
                    title),
                    buf -> CommandPromptMenu.writeOpenBuffer(buf, monitorPos, owner, era, shellId, hostname, osLabel));
        }
    }

    private static void openTerminal(final ServerPlayer player, final Level level,
                                     final BlockPos monitorPos, final BlockPos owner) {
        if (level.getBlockEntity(owner) instanceof ComputerTerminalHost host) {
            final Component title = level.getBlockState(owner).getBlock().getName();
            // Reopen on the tab the player last used here (persisted on the Monitor).
            final int initialTab =
                    level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitor
                            ? monitor.lastTab() : ComputerTerminalMenu.TAB_NETWORK;
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new ComputerTerminalMenu(id, inv, host, owner, monitorPos, initialTab), title),
                    buf -> {
                        buf.writeBlockPos(monitorPos);
                        buf.writeBlockPos(owner);
                        buf.writeVarInt(initialTab);
                    });
        }
    }

    private static Component diagnoseUnlinked(final Level level, final BlockPos monitorPos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Component.translatable("block.jsc.monitor.unlinked");
        }
        final java.util.OptionalLong host =
                PeripheralLinks.discoverOwner(serverLevel, monitorPos.asLong());
        if (host.isEmpty()) {
            return Component.translatable("block.jsc.monitor.no_computer");
        }
        if (serverLevel.getBlockEntity(BlockPos.of(host.getAsLong())) instanceof PeripheralOwner owner) {
            if (owner.maxEndpoints() <= 0) {
                return Component.translatable("block.jsc.monitor.no_gpu");
            }
            if (owner.linkedEndpoints().size() >= owner.maxEndpoints()) {
                return Component.translatable("block.jsc.monitor.at_capacity");
            }
        }
        return Component.translatable("block.jsc.monitor.unlinked");
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
            monitor.unlink(serverLevel); // free the computer's endpoint slot
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return BlockEntityTickers.create(type, ComputingModule.MONITOR_BE.get(), MonitorBlockEntity::serverTick);
    }

}
