/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.format.Unit;
import dev.jsc.jscomputronics.common.format.UnitFormatter;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.network.ServerNode;
import dev.jsc.jscomputronics.common.network.SubframeNode;
import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.os.OsDef;
import dev.jsc.jscomputronics.module.computing.os.OsGating;
import dev.jsc.jscomputronics.module.computing.os.OsRegistry;
import dev.jsc.jscomputronics.module.computing.os.media.MediaKind;
import dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity;
import dev.jsc.jscomputronics.common.util.ShortId;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRouterBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.DatacenterStationBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.block.part.AbstractBusPart;
import dev.jsc.jscomputronics.module.computing.menu.AbstractBusMenu;
import dev.jsc.jscomputronics.module.computing.datacenter.LoadBalancer;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu;
import dev.jsc.jscomputronics.module.computing.menu.MainframeMenu;
import dev.jsc.jscomputronics.module.computing.menu.PersonalComputerMenu;
import dev.jsc.jscomputronics.module.computing.menu.ServerRouterMenu;
import dev.jsc.jscomputronics.module.computing.menu.DatacenterStationMenu;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.program.Programs;
import dev.jsc.jscomputronics.module.computing.menu.CraftingComputerMenu;
import dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.os.OsDef;
import dev.jsc.jscomputronics.module.computing.os.fs.CraftFile;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FileType;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import net.minecraft.network.chat.Component;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers and handles the network storage payloads that let a Personal Computer's Network tab drive the storage operations: the client asks to SELECT, the server runs it through the network and replies with a fresh snapshot of what the network holds.
 */
@EventBusSubscriber(modid = JsComputronics.MODID)
public final class ComputingPayloads {

    private ComputingPayloads() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(NetworkSnapshotPayload.TYPE, NetworkSnapshotPayload.STREAM_CODEC,
                ComputingPayloads::handleSnapshot);
        registrar.playToServer(RequestNetworkNodesPayload.TYPE, RequestNetworkNodesPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestNodes);
        registrar.playToClient(NetworkNodesPayload.TYPE, NetworkNodesPayload.STREAM_CODEC,
                ComputingPayloads::handleNodes);
        registrar.playToServer(TerminalSelectPayload.TYPE, TerminalSelectPayload.STREAM_CODEC,
                ComputingPayloads::handleTerminalSelect);
        registrar.playToServer(TerminalInsertPayload.TYPE, TerminalInsertPayload.STREAM_CODEC,
                ComputingPayloads::handleTerminalInsert);
        registrar.playToServer(RequestServerBreakdownPayload.TYPE, RequestServerBreakdownPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestBreakdown);
        registrar.playToClient(ServerBreakdownPayload.TYPE, ServerBreakdownPayload.STREAM_CODEC,
                ComputingPayloads::handleServerBreakdown);
        registrar.playToClient(OperationsLogPayload.TYPE, OperationsLogPayload.STREAM_CODEC,
                ComputingPayloads::handleOpsLog);
        registrar.playToClient(LocalStorageSnapshotPayload.TYPE, LocalStorageSnapshotPayload.STREAM_CODEC,
                ComputingPayloads::handleLocalSnapshot);
        registrar.playToServer(TerminalLocalWithdrawPayload.TYPE, TerminalLocalWithdrawPayload.STREAM_CODEC,
                ComputingPayloads::handleLocalWithdraw);
        registrar.playToServer(TerminalDiskPrivacyPayload.TYPE, TerminalDiskPrivacyPayload.STREAM_CODEC,
                ComputingPayloads::handleDiskPrivacy);
        registrar.playToServer(TerminalLocalDepositPayload.TYPE, TerminalLocalDepositPayload.STREAM_CODEC,
                ComputingPayloads::handleLocalDeposit);
        registrar.playToClient(ActiveOperationsPayload.TYPE, ActiveOperationsPayload.STREAM_CODEC,
                ComputingPayloads::handleActiveOps);
        registrar.playToClient(NetworkServersPayload.TYPE, NetworkServersPayload.STREAM_CODEC,
                ComputingPayloads::handleNetworkServers);
        registrar.playToServer(RenameServerPayload.TYPE, RenameServerPayload.STREAM_CODEC,
                ComputingPayloads::handleRenameServer);
        registrar.playToServer(TerminalLocalUploadPayload.TYPE, TerminalLocalUploadPayload.STREAM_CODEC,
                ComputingPayloads::handleLocalUpload);
        registrar.playToServer(RenamePcPayload.TYPE, RenamePcPayload.STREAM_CODEC,
                ComputingPayloads::handleRenamePc);
        registrar.playToServer(RenameServerRouterPayload.TYPE, RenameServerRouterPayload.STREAM_CODEC,
                ComputingPayloads::handleRenameServerRouter);
        registrar.playToServer(SetBusNamePayload.TYPE, SetBusNamePayload.STREAM_CODEC,
                ComputingPayloads::handleSetBusName);
        registrar.playToClient(DatacenterSnapshotPayload.TYPE, DatacenterSnapshotPayload.STREAM_CODEC,
                ComputingPayloads::handleDatacenterSnapshot);
        registrar.playToServer(DatacenterStationActionPayload.TYPE, DatacenterStationActionPayload.STREAM_CODEC,
                ComputingPayloads::handleDatacenterAction);
        registrar.playToServer(DatacenterSelectPayload.TYPE, DatacenterSelectPayload.STREAM_CODEC,
                ComputingPayloads::handleDatacenterSelect);
        registrar.playToServer(TerminalMaintenancePayload.TYPE, TerminalMaintenancePayload.STREAM_CODEC,
                ComputingPayloads::handleTerminalMaintenance);
        registrar.playToServer(TerminalDropPayload.TYPE, TerminalDropPayload.STREAM_CODEC,
                ComputingPayloads::handleTerminalDrop);
        registrar.playToClient(CraftCatalogPayload.TYPE, CraftCatalogPayload.STREAM_CODEC,
                ComputingPayloads::handleCraftCatalog);
        registrar.playToServer(CraftPlanRequestPayload.TYPE, CraftPlanRequestPayload.STREAM_CODEC,
                ComputingPayloads::handleCraftPlanRequest);
        registrar.playToClient(CraftPlanPayload.TYPE, CraftPlanPayload.STREAM_CODEC,
                ComputingPayloads::handleCraftPlan);
        registrar.playToServer(CraftSubmitPayload.TYPE, CraftSubmitPayload.STREAM_CODEC,
                ComputingPayloads::handleCraftSubmit);
        registrar.playToServer(RunCommandPayload.TYPE, RunCommandPayload.STREAM_CODEC,
                ComputingPayloads::handleRunCommand);
        registrar.playToClient(CommandOutputPayload.TYPE, CommandOutputPayload.STREAM_CODEC,
                ComputingPayloads::handleCommandOutput);
        registrar.playToServer(RequestConsoleInitPayload.TYPE, RequestConsoleInitPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestConsoleInit);
        registrar.playToClient(ConsoleInitPayload.TYPE, ConsoleInitPayload.STREAM_CODEC,
                ComputingPayloads::handleConsoleInit);
        registrar.playToServer(RequestDiskFilesPayload.TYPE, RequestDiskFilesPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestDiskFiles);
        registrar.playToClient(DiskFilesPayload.TYPE, DiskFilesPayload.STREAM_CODEC,
                ComputingPayloads::handleDiskFiles);
        registrar.playToServer(RequestDesktopFilesPayload.TYPE, RequestDesktopFilesPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestDesktopFiles);
        registrar.playToClient(DesktopFilesPayload.TYPE, DesktopFilesPayload.STREAM_CODEC,
                ComputingPayloads::handleDesktopFiles);
        registrar.playToServer(RequestThisPcPayload.TYPE, RequestThisPcPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestThisPc);
        registrar.playToClient(ThisPcPayload.TYPE, ThisPcPayload.STREAM_CODEC,
                ComputingPayloads::handleThisPc);
        registrar.playToServer(InstallFromMediaPayload.TYPE, InstallFromMediaPayload.STREAM_CODEC,
                ComputingPayloads::handleInstallFromMedia);
        registrar.playToServer(SetDesktopPrefsPayload.TYPE, SetDesktopPrefsPayload.STREAM_CODEC,
                ComputingPayloads::handleSetDesktopPrefs);
        registrar.playToServer(SetIconPositionPayload.TYPE, SetIconPositionPayload.STREAM_CODEC,
                ComputingPayloads::handleSetIconPosition);
        registrar.playToServer(DesktopShellRunPayload.TYPE, DesktopShellRunPayload.STREAM_CODEC,
                ComputingPayloads::handleDesktopShellRun);
        registrar.playToClient(DesktopShellOutputPayload.TYPE, DesktopShellOutputPayload.STREAM_CODEC,
                ComputingPayloads::handleDesktopShellOutput);
        registrar.playToServer(SaveFilePayload.TYPE, SaveFilePayload.STREAM_CODEC,
                ComputingPayloads::handleSaveFile);
        registrar.playToClient(FileSavedPayload.TYPE, FileSavedPayload.STREAM_CODEC,
                ComputingPayloads::handleFileSaved);
        registrar.playToServer(DeleteFilePayload.TYPE, DeleteFilePayload.STREAM_CODEC,
                ComputingPayloads::handleDeleteFile);
        registrar.playToServer(RequestFileContentPayload.TYPE, RequestFileContentPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestFileContent);
        registrar.playToClient(FileContentPayload.TYPE, FileContentPayload.STREAM_CODEC,
                ComputingPayloads::handleFileContent);
        registrar.playToServer(RenameFilePayload.TYPE, RenameFilePayload.STREAM_CODEC,
                ComputingPayloads::handleRenameFile);
        registrar.playToServer(MkdirPayload.TYPE, MkdirPayload.STREAM_CODEC,
                ComputingPayloads::handleMkdir);
        registrar.playToServer(MoveFilePayload.TYPE, MoveFilePayload.STREAM_CODEC,
                ComputingPayloads::handleMoveFile);
        registrar.playToServer(MediumTransferPayload.TYPE, MediumTransferPayload.STREAM_CODEC,
                ComputingPayloads::handleMediumTransfer);
        registrar.playToServer(RenameVolumePayload.TYPE, RenameVolumePayload.STREAM_CODEC,
                ComputingPayloads::handleRenameVolume);
        registrar.playToServer(RequestNetworkInteractorPayload.TYPE,
                RequestNetworkInteractorPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestNetworkInteractor);
        registrar.playToClient(NetworkInteractorPayload.TYPE, NetworkInteractorPayload.STREAM_CODEC,
                ComputingPayloads::handleNetworkInteractor);
        registrar.playToServer(NiGridClickPayload.TYPE, NiGridClickPayload.STREAM_CODEC,
                ComputingPayloads::handleNiGridClick);
        registrar.playToServer(NiDepositPayload.TYPE, NiDepositPayload.STREAM_CODEC,
                ComputingPayloads::handleNiDeposit);
        registrar.playToServer(NiShiftInsertPayload.TYPE, NiShiftInsertPayload.STREAM_CODEC,
                ComputingPayloads::handleNiShiftInsert);
        registrar.playToServer(SetCraftingSwitchFacePayload.TYPE, SetCraftingSwitchFacePayload.STREAM_CODEC,
                ComputingPayloads::handleSetCraftingSwitchFace);
        registrar.playToServer(RequestNiServersPayload.TYPE, RequestNiServersPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestNiServers);
        registrar.playToServer(NiSelectPayload.TYPE, NiSelectPayload.STREAM_CODEC,
                ComputingPayloads::handleNiSelect);
        registrar.playToServer(RequestNiOperationsPayload.TYPE, RequestNiOperationsPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestNiOperations);
        registrar.playToServer(NiHotbarClickPayload.TYPE, NiHotbarClickPayload.STREAM_CODEC,
                ComputingPayloads::handleNiHotbarClick);
        registrar.playToServer(NiCraftPayload.TYPE, NiCraftPayload.STREAM_CODEC,
                ComputingPayloads::handleNiCraft);
        registrar.playToServer(OpenProgramPayload.TYPE, OpenProgramPayload.STREAM_CODEC,
                ComputingPayloads::handleOpenProgram);
        registrar.playToServer(RunIqlPayload.TYPE, RunIqlPayload.STREAM_CODEC,
                ComputingPayloads::handleRunIql);
        registrar.playToClient(IqlResultPayload.TYPE, IqlResultPayload.STREAM_CODEC,
                ComputingPayloads::handleIqlResult);
        registrar.playToServer(RequestNmsSchemaPayload.TYPE, RequestNmsSchemaPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestNmsSchema);
        registrar.playToClient(NmsSchemaPayload.TYPE, NmsSchemaPayload.STREAM_CODEC,
                ComputingPayloads::handleNmsSchema);
        registrar.playToServer(SaveScriptPayload.TYPE, SaveScriptPayload.STREAM_CODEC,
                ComputingPayloads::handleSaveScript);
        registrar.playToClient(ProcessListPayload.TYPE, ProcessListPayload.STREAM_CODEC,
                ComputingPayloads::handleProcessList);
        registrar.playToServer(ProcessActionPayload.TYPE, ProcessActionPayload.STREAM_CODEC,
                ComputingPayloads::handleProcessAction);
        registrar.playToServer(InstallOsPayload.TYPE, InstallOsPayload.STREAM_CODEC,
                ComputingPayloads::handleInstallOs);
        registrar.playToClient(CraftFileListPayload.TYPE, CraftFileListPayload.STREAM_CODEC,
                ComputingPayloads::handleCraftFileList);
        registrar.playToServer(SaveIqlFilePayload.TYPE, SaveIqlFilePayload.STREAM_CODEC,
                ComputingPayloads::handleSaveIqlFile);
        registrar.playToServer(RequestIqlFileListPayload.TYPE, RequestIqlFileListPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestIqlFileList);
        registrar.playToClient(IqlFileListPayload.TYPE, IqlFileListPayload.STREAM_CODEC,
                ComputingPayloads::handleIqlFileList);
        registrar.playToServer(OpenIqlFilePayload.TYPE, OpenIqlFilePayload.STREAM_CODEC,
                ComputingPayloads::handleOpenIqlFile);
        registrar.playToClient(IqlFileContentPayload.TYPE, IqlFileContentPayload.STREAM_CODEC,
                ComputingPayloads::handleIqlFileContent);
        registrar.playToClient(OpenComputerUiPayload.TYPE, OpenComputerUiPayload.STREAM_CODEC,
                ComputingPayloads::handleOpenComputerUi);
        registrar.playToServer(RequestPatternEncoderFilesPayload.TYPE,
                RequestPatternEncoderFilesPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestPatternEncoderFiles);
        registrar.playToServer(PatternEncoderEditPayload.TYPE, PatternEncoderEditPayload.STREAM_CODEC,
                ComputingPayloads::handlePatternEncoderEdit);
        registrar.playToServer(RequestCraftManagerPayload.TYPE, RequestCraftManagerPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestCraftManager);
        registrar.playToServer(SetMachineConfigPayload.TYPE, SetMachineConfigPayload.STREAM_CODEC,
                ComputingPayloads::handleSetMachineConfig);
        registrar.playToClient(CraftManagerStatePayload.TYPE, CraftManagerStatePayload.STREAM_CODEC,
                ComputingPayloads::handleCraftManagerState);
        registrar.playToServer(LoadFromMediaPayload.TYPE, LoadFromMediaPayload.STREAM_CODEC,
                ComputingPayloads::handleLoadFromMedia);
        registrar.playToServer(DownloadToMediaPayload.TYPE, DownloadToMediaPayload.STREAM_CODEC,
                ComputingPayloads::handleDownloadToMedia);
        registrar.playToServer(RemoveRomCraftPayload.TYPE, RemoveRomCraftPayload.STREAM_CODEC,
                ComputingPayloads::handleRemoveRomCraft);
    }

    /** The processes running on the host at {@code hostPos}: the IQL Engine and its jobs if it is a Mainframe with the Engine installed, else an empty list (a computer with no service). */
    public static void dispatchProcesses(final ServerPlayer player, final BlockPos hostPos,
                                         final ServerLevel level) {
        final List<ProcessListPayload.ProcessLine> lines = new ArrayList<>();
        if (level.getBlockEntity(hostPos) instanceof MainframeBlockEntity mainframe
                && mainframe.isIqlEngineInstalled()) {
            final boolean running = mainframe.isIqlEngineRunning();
            lines.add(new ProcessListPayload.ProcessLine(ProcessListPayload.KIND_SERVICE, "IQL Engine",
                    running ? "running" : "stopped",
                    running ? "the network's query and job engine" : "stopped — start it to run jobs"));
            for (final dev.jsc.jscomputronics.module.computing.program.iql.IqlSavedObject job
                    : mainframe.iqlCatalog().ofType(
                            dev.jsc.jscomputronics.module.computing.program.iql.IqlDefinition.ObjectType.JOB)) {
                final boolean paused = mainframe.isJobPaused(job.name());
                final String state = paused ? "paused" : running ? "active" : "idle";
                lines.add(new ProcessListPayload.ProcessLine(ProcessListPayload.KIND_JOB, job.name(),
                        state, jobDetail(job)));
            }
        }
        PacketDistributor.sendToPlayer(player, new ProcessListPayload(lines));
    }

    private static String jobDetail(final dev.jsc.jscomputronics.module.computing.program.iql.IqlSavedObject job) {
        return switch (job.triggerKind()) {
            case EVERY -> "every " + job.triggerSpec();
            case WHEN -> "when " + job.triggerSpec();
            case NONE -> job.body();
        };
    }

    private static void handleProcessList(final ProcessListPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setProcesses(payload.processes());
            }
        });
    }

    private static void handleProcessAction(final ProcessActionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ComputerTerminalMenu menu)
                    || !menu.hostPos().equals(payload.hostPos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos()) instanceof MainframeBlockEntity mainframe)) {
                return;
            }
            if (payload.kind() == ProcessListPayload.KIND_SERVICE) {
                switch (payload.action()) {
                    case ProcessActionPayload.ACTION_STOP -> mainframe.setIqlEngineRunning(false);
                    case ProcessActionPayload.ACTION_START -> mainframe.setIqlEngineRunning(true);
                    case ProcessActionPayload.ACTION_RESTART -> {
                        mainframe.setIqlEngineRunning(false);
                        mainframe.setIqlEngineRunning(true);
                    }
                    default -> { /* END has no meaning for a service */ }
                }
            } else {
                switch (payload.action()) {
                    case ProcessActionPayload.ACTION_END -> mainframe.pauseJob(payload.name());
                    case ProcessActionPayload.ACTION_RESTART -> mainframe.restartJob(payload.name());
                    default -> { /* a job has only End/Restart */ }
                }
            }
            dispatchProcesses(player, payload.hostPos(), level);
        });
    }

    // Command Prompt — a typed line runs through the shell against the open host and the styled
    // output is streamed back. The CLI is an alternative interface over the same network operations.

    private static final int CLI_WIDTH = 50;

    private static void handleRunCommand(final RunCommandPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu menu)
                    || !menu.hostPos().equals(payload.hostPos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host)) {
                return;
            }
            // "run/open <program>" launches another installed program from the prompt.
            final String[] parts = payload.line().trim().split("\\s+", 2);
            if (parts.length == 2 && (parts[0].equalsIgnoreCase("run") || parts[0].equalsIgnoreCase("open"))) {
                if (host.console() != null && !payload.line().isBlank()) {
                    host.console().pushHistory(payload.line().trim());
                }
                launchProgram(player, host, menu.monitorPos(), payload.hostPos(), parts[1].trim());
                return;
            }
            final var computer = new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(host, level);
            final var shell = dev.jsc.jscomputronics.module.computing.program.cli.CliCommands.newShell(CLI_WIDTH);
            final var response = shell.run(payload.line(), computer);
            final List<CommandOutputPayload.WireLine> wire = new ArrayList<>(response.lines().size());
            for (final var cliLine : response.lines()) {
                wire.add(new CommandOutputPayload.WireLine(cliLine.text(), cliLine.style().ordinal()));
            }
            PacketDistributor.sendToPlayer(player, new CommandOutputPayload(response.clearScreen(), wire));
            // Persist the typed line on the computer so the history survives closing the prompt or Monitor.
            if (host.console() != null && !payload.line().isBlank()) {
                host.console().pushHistory(payload.line().trim());
                ((net.minecraft.world.level.block.entity.BlockEntity) host).setChanged();
            }
        });
    }

    private static void launchProgram(final ServerPlayer player,
            final dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host,
            final BlockPos monitorPos, final BlockPos hostPos, final String name) {
        dev.jsc.jscomputronics.module.computing.program.Program program = null;
        for (final var candidate : dev.jsc.jscomputronics.module.computing.program.Programs.all()) {
            if (candidate.commandName().equalsIgnoreCase(name)
                    || candidate.id().getPath().equalsIgnoreCase(name)
                    || candidate.id().toString().equalsIgnoreCase(name)) {
                program = candidate;
                break;
            }
        }
        if (program == null) {
            sendConsoleLine(player, "no such program: " + name, OperationRecord.STATUS_FAILED);
            return;
        }
        final boolean installed = program.preinstalled()
                || (host.console() != null && host.console().isInstalled(program.id().toString()));
        if (!installed) {
            sendConsoleLine(player, program.commandName() + " is not installed - try: install "
                    + program.commandName(), OperationRecord.STATUS_FAILED);
            return;
        }
        // OS-capability gate: a program may require a richer OS than the host runs (e.g. the NMS needs a
        // full graphical desktop). Null-safe: programs with no declared requirement always pass.
        if (player.level() instanceof ServerLevel osLevel
                && osLevel.getBlockEntity(hostPos) instanceof dev.jsc.jscomputronics.module.computing.blockentity
                        .AbstractComputerBlockEntity osComputer
                && !dev.jsc.jscomputronics.module.computing.os.OsRegistry.canHostRun(
                        osComputer.installedOsId(), program.id())) {
            sendConsoleLine(player, program.commandName()
                    + " requires a graphical desktop OS (Panes) on this computer", OperationRecord.STATUS_FAILED);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "The " + program.commandName() + " needs a graphical desktop OS (Panes) to run."), false);
            return;
        }
        if (program.id().equals(dev.jsc.jscomputronics.module.computing.program.Programs.NMS)) {
            // The NMS is now a desktop window opened from its Panes desktop icon, not a server-side menu.
            sendConsoleLine(player, "open the NMS from its desktop icon on a Panes computer", -1);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Open the NMS from its desktop icon."), false);
        } else {
            sendConsoleLine(player, "the " + program.commandName() + " is already open", -1);
        }
    }

    /** One styled line back to the open Command Prompt (status -1 = dim, FAILED = red, else green). */
    private static void sendConsoleLine(final ServerPlayer player, final String text, final int status) {
        final dev.jsc.jscomputronics.module.computing.program.cli.CliStyle style = status == OperationRecord.STATUS_FAILED
                ? dev.jsc.jscomputronics.module.computing.program.cli.CliStyle.ERROR
                : status < 0 ? dev.jsc.jscomputronics.module.computing.program.cli.CliStyle.DIM
                : dev.jsc.jscomputronics.module.computing.program.cli.CliStyle.OK;
        final List<CommandOutputPayload.WireLine> wire = new ArrayList<>();
        for (final String line : wrapToConsole(text)) {
            wire.add(new CommandOutputPayload.WireLine(line, style.ordinal()));
        }
        PacketDistributor.sendToPlayer(player, new CommandOutputPayload(false, wire));
    }

    /** Word-wraps a direct console message to the console width so a long line never overflows the prompt. */
    private static List<String> wrapToConsole(final String text) {
        final List<String> lines = new ArrayList<>();
        for (final String paragraph : text.split("\n", -1)) {
            String remaining = paragraph;
            while (remaining.length() > CLI_WIDTH) {
                int cut = remaining.lastIndexOf(' ', CLI_WIDTH);
                if (cut <= 0) {
                    cut = CLI_WIDTH;
                }
                lines.add(remaining.substring(0, cut));
                remaining = remaining.substring(cut).stripLeading();
            }
            lines.add(remaining);
        }
        return lines;
    }

    /**
     * Anti-spoof for the windowed NMS (it has no container menu to authenticate against): the player must be
     * within 8 blocks of the host computer or one of its linked monitors, so a forged packet aimed at a
     * foreign computer is rejected.
     */
    private static boolean nmsNear(final ServerPlayer player, final BlockPos hostPos,
                                   final dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host) {
        final net.minecraft.world.phys.Vec3 p = player.position();
        if (hostPos.distToCenterSqr(p) <= 64.0) {
            return true;
        }
        if (host instanceof dev.jsc.jscomputronics.common.peripheral.PeripheralOwner owner) {
            for (final long endpoint : owner.linkedEndpoints()) {
                if (net.minecraft.core.BlockPos.of(endpoint).distToCenterSqr(p) <= 64.0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void handleRunIql(final RunIqlPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host)
                    || !nmsNear(player, payload.hostPos(), host)) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                PacketDistributor.sendToPlayer(player,
                        new IqlResultPayload(false, "the network has no running Mainframe", List.of()));
                return;
            }
            final var computer = new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(host, level);
            final var engine = new dev.jsc.jscomputronics.module.computing.program.IqlEngine(
                    mainframe, computer, IqlResultPayload.MAX_ROWS);
            final var outcome = engine.run(payload.statement());
            final List<IqlResultPayload.Row> rows = new ArrayList<>(outcome.rows().size());
            for (final var item : outcome.rows()) {
                rows.add(new IqlResultPayload.Row(
                        item.detail().isEmpty() ? item.name() : item.name() + "  ·  " + item.detail(),
                        item.quantity()));
            }
            PacketDistributor.sendToPlayer(player,
                    new IqlResultPayload(outcome.ok(), outcome.message(), rows));
        });
    }

    private static void handleIqlResult(final IqlResultPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.NmsApp.accept(payload));
    }

    private static void handleRequestNmsSchema(final RequestNmsSchemaPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host)
                    || !nmsNear(player, payload.hostPos(), host)) {
                return;
            }
            PacketDistributor.sendToPlayer(player, nmsSchema(level, host));
        });
    }

    private static void handleNmsSchema(final NmsSchemaPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.NmsApp.acceptSchema(payload));
    }

    private static void handleSaveScript(final SaveScriptPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos()) instanceof ComputerTerminalHost host)
                    || host.networkUuid() == null) {
                return;
            }
            // Save to the network's Mainframe — the same place the schema snapshot reads it back from —
            // whether the studio's host is the Mainframe itself or a PC on its network. Saving to the raw
            // host position instead would silently drop the script when the host is not the Mainframe.
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe != null) {
                mainframe.setSavedScript(payload.script());
            }
        });
    }

    /**
     * The Object Explorer snapshot for an open Studio: the network label, the real server labels, and live item-type and active-operation counts. The IQL schema (table and column names) is fixed on the client; this fills in only the parts that reflect the running network.
     */
    public static NmsSchemaPayload nmsSchema(final ServerLevel level,
            final dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host) {
        final NetworkUuid net = host.networkUuid();
        if (net == null) {
            return new NmsSchemaPayload("jsc-net (offline)", List.of(), 0, 0,
                    NmsSchemaPayload.EngineSnapshot.offline());
        }
        final NetworkSystem system = NetworkSystem.get(level);
        final List<String> servers = new ArrayList<>();
        for (final ServerNode server : system.serversOf(net)) {
            if (servers.size() >= NmsSchemaPayload.MAX_SERVERS) {
                break;
            }
            servers.add(serverLabel(level, server.nodeUuid()));
        }
        final int itemTypes = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                .of(level, net).query().size();
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        final int operations = mainframe != null ? mainframe.activeOperationRecords().size() : 0;
        return new NmsSchemaPayload(networkLabel(net), List.copyOf(servers), itemTypes, operations,
                engineSnapshot(mainframe));
    }

    private static NmsSchemaPayload.EngineSnapshot engineSnapshot(final MainframeBlockEntity mainframe) {
        if (mainframe == null || !mainframe.isIqlEngineInstalled()) {
            return NmsSchemaPayload.EngineSnapshot.offline();
        }
        final var catalog = mainframe.iqlCatalog();
        return new NmsSchemaPayload.EngineSnapshot(mainframe.isIqlEngineRunning() ? "running" : "stopped",
                objectNames(catalog.ofType(
                        dev.jsc.jscomputronics.module.computing.program.iql.IqlDefinition.ObjectType.VIEW)),
                objectNames(catalog.ofType(
                        dev.jsc.jscomputronics.module.computing.program.iql.IqlDefinition.ObjectType.PROCEDURE)),
                objectNames(catalog.ofType(
                        dev.jsc.jscomputronics.module.computing.program.iql.IqlDefinition.ObjectType.JOB)),
                mainframe.savedScript());
    }

    private static List<String> objectNames(
            final List<dev.jsc.jscomputronics.module.computing.program.iql.IqlSavedObject> objects) {
        final List<String> names = new ArrayList<>();
        for (final var object : objects) {
            if (names.size() >= NmsSchemaPayload.MAX_OBJECTS) {
                break;
            }
            names.add(object.name());
        }
        return names;
    }

    private static String networkLabel(final NetworkUuid net) {
        return "jsc-net-" + dev.jsc.jscomputronics.common.util.ShortId.of(net.asString());
    }

    private static void handleRequestConsoleInit(final RequestConsoleInitPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu menu
                    && menu.hostPos().equals(payload.hostPos())
                    && player.level().getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host) {
                sendConsoleInit(player, host);
            }
        });
    }

    private static void sendConsoleInit(final ServerPlayer player,
            final dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host) {
        final var console = host.console();
        final List<String> history = console == null ? List.of() : console.history();
        final List<ConsoleInitPayload.WireCommand> commands = new ArrayList<>();
        for (final var command : dev.jsc.jscomputronics.module.computing.program.cli.CliCommands.all()) {
            if (commands.size() >= ConsoleInitPayload.MAX_COMMANDS) {
                break;
            }
            commands.add(new ConsoleInitPayload.WireCommand(command.name(), command.usage()));
        }
        PacketDistributor.sendToPlayer(player, new ConsoleInitPayload(List.copyOf(history), commands));
    }

    private static void handleOpenComputerUi(final OpenComputerUiPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                // Only the firmware setup is a client-only screen; the desktop opens as a server-side menu.
                dev.jsc.jscomputronics.module.computing.block.FirmwareScreenOpener.Holder.open(
                        payload.host(),
                        dev.jsc.jscomputronics.module.computing.os.FirmwareKind.values()[payload.firmwareKind()],
                        payload.name()));
    }

    private static void handleConsoleInit(final ConsoleInitPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.CommandPromptScreen.acceptInit(payload));
    }

    private static void handleOpenProgram(final OpenProgramPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level().getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost terminalHost)) {
                return;
            }
            // Anti-spoof: either this host's terminal menu is open, or the player is within reach of the
            // monitor they used (the desktop shell is a client-only screen with no server-side menu, so a
            // program opened from it cannot be validated against an open container).
            final boolean viaTerminal = player.containerMenu instanceof ComputerTerminalMenu terminal
                    && terminal.hostPos().equals(payload.hostPos());
            // The desktop path is only valid when the monitor is actually a linked peripheral of this host,
            // so a player near any monitor cannot open a program bound to a foreign computer.
            final boolean nearMonitor = player.distanceToSqr(
                    net.minecraft.world.phys.Vec3.atCenterOf(payload.monitorPos())) <= 64.0
                    && terminalHost instanceof dev.jsc.jscomputronics.common.peripheral.PeripheralOwner owner
                    && owner.linkedEndpoints().contains(payload.monitorPos().asLong());
            if (!viaTerminal && !nearMonitor) {
                return;
            }
            final String id = payload.programId();
            if (id.equals(dev.jsc.jscomputronics.module.computing.program.Programs.COMMAND_PROMPT.toString())
                    || id.equals("command_prompt")) {
                final net.minecraft.network.chat.Component title =
                        player.level().getBlockState(payload.hostPos()).getBlock().getName();
                // The host's board-derived era drives the prompt's GUI skin; capture it at open time. It is
                // not re-synced afterwards because the board is only swapped in the computer's own assembly
                // GUI, never from the running prompt.
                final dev.jsc.jscomputronics.common.tier.HardwareEra hostEra =
                        player.level().getBlockEntity(payload.hostPos())
                                instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                        .AbstractComputerBlockEntity host ? host.displayEra() : null;
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (windowId, inv, p) -> new dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu(
                                windowId, inv, payload.monitorPos(), payload.hostPos(), hostEra), title),
                        buf -> dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu.writeOpenBuffer(
                                buf, payload.monitorPos(), payload.hostPos(), hostEra));
            } else {
                // The NMS and other windowed programs open through the shared launcher, which checks they
                // are installed and (for the NMS) that the IQL Engine is running on the network's Mainframe.
                launchProgram(player, terminalHost, payload.monitorPos(), payload.hostPos(), id);
            }
        });
    }

    private static void handleCommandOutput(final CommandOutputPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.CommandPromptScreen.accept(payload));
    }

    private static void handleRequestDiskFiles(final RequestDiskFilesPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            final java.util.List<DiskFilesPayload.WireFile> wire = new java.util.ArrayList<>();
            final java.util.List<DiskFilesPayload.WireVolume> volumes = new java.util.ArrayList<>();
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer) {
                // The mountable volumes (drive tree): the system disk, then each linked drive with a medium.
                if (!computer.systemDisk().isEmpty()
                        && filesystemKindOf(computer)
                                != dev.jsc.jscomputronics.module.computing.os.FilesystemKind.NONE) {
                    volumes.add(new DiskFilesPayload.WireVolume("",
                            dev.jsc.jscomputronics.module.computing.os.VolumeLabel.of(
                                    computer.systemDisk(), "Local Disk")));
                }
                for (final long endpoint : computer.linkedEndpoints()) {
                    if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                            instanceof MediaReaderBlockEntity reader
                            && !reader.mediaSlot().getStackInSlot(0).isEmpty()) {
                        volumes.add(new DiskFilesPayload.WireVolume("media:" + endpoint,
                                dev.jsc.jscomputronics.module.computing.os.VolumeLabel.of(
                                        reader.mediaSlot().getStackInSlot(0), "Removable Drive")));
                    }
                }
                final String reqDir = payload.dir();
                if (reqDir.startsWith("media:")) {
                    // Browsing a removable medium in a linked drive.
                    listMediaInto(wire, level, computer, reqDir);
                } else {
                    final net.minecraft.world.item.ItemStack disk = computer.systemDisk();
                    final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind =
                            filesystemKindOf(computer);
                    if (!disk.isEmpty()
                            && kind != dev.jsc.jscomputronics.module.computing.os.FilesystemKind.NONE) {
                        // Subdirectories first (folders before files, Windows-style).
                        for (final String d
                                : dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.listDirs(
                                        disk, reqDir, kind)) {
                            wire.add(new DiskFilesPayload.WireFile(d, "", 0L, false, true));
                        }
                        for (final dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.FileEntry e
                                : dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.list(
                                        disk, reqDir, kind)) {
                            wire.add(new DiskFilesPayload.WireFile(
                                    e.path(), e.type().extension(), e.weight(), e.readOnly(), false));
                        }
                    }
                    // At the root, removable media in linked drives appear as drives to open.
                    if (reqDir.isEmpty()) {
                        for (final long endpoint : computer.linkedEndpoints()) {
                            if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                                    instanceof dev.jsc.jscomputronics.module.computing.os.media
                                            .MediaReaderBlockEntity reader
                                    && !reader.mediaSlot().getStackInSlot(0).isEmpty()) {
                                wire.add(new DiskFilesPayload.WireFile(
                                        "media:" + endpoint, "", 0L, false, true));
                            }
                        }
                    }
                }
            }
            context.reply(new DiskFilesPayload(payload.dir(), wire, volumes));
        });
    }

    /** Lists a removable medium's files into {@code wire}, paths prefixed {@code media:<readerPos>/}. */
    private static void listMediaInto(final java.util.List<DiskFilesPayload.WireFile> wire,
            final ServerLevel level,
            final dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity computer,
            final String reqDir) {
        final net.minecraft.world.item.ItemStack media = mediaStackFor(level, computer, reqDir);
        if (media.isEmpty()) {
            return;
        }
        final String rest = reqDir.substring("media:".length());
        final int slash = rest.indexOf('/');
        final long readerPos = Long.parseLong(slash < 0 ? rest : rest.substring(0, slash));
        final String subDir = slash < 0 ? "" : rest.substring(slash + 1);
        final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind =
                dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL;
        final String prefix = "media:" + readerPos + "/";
        for (final String d : dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.listDirs(
                media, subDir, kind)) {
            wire.add(new DiskFilesPayload.WireFile(prefix + d, "", 0L, false, true));
        }
        for (final dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.FileEntry e
                : dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.list(media, subDir, kind)) {
            wire.add(new DiskFilesPayload.WireFile(
                    prefix + e.path(), e.type().extension(), e.weight(), e.readOnly(), false));
        }
    }

    /**
     * Resolves a {@code media:<readerPos>[/sub]} path to the medium's {@link net.minecraft.world.item.ItemStack}
     * in a linked drive, or {@link net.minecraft.world.item.ItemStack#EMPTY} if not reachable.
     */
    private static net.minecraft.world.item.ItemStack mediaStackFor(final ServerLevel level,
            final dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity computer,
            final String mediaPath) {
        final String rest = mediaPath.substring("media:".length());
        final int slash = rest.indexOf('/');
        final long readerPos;
        try {
            readerPos = Long.parseLong(slash < 0 ? rest : rest.substring(0, slash));
        } catch (final NumberFormatException e) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        if (!computer.linkedEndpoints().contains(readerPos)
                || !(level.getBlockEntity(net.minecraft.core.BlockPos.of(readerPos))
                        instanceof dev.jsc.jscomputronics.module.computing.os.media
                                .MediaReaderBlockEntity reader)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return reader.mediaSlot().getStackInSlot(0);
    }

    /** Strips the {@code media:<readerPos>/} prefix from a media path, leaving the path within the medium. */
    private static String mediaSubPath(final String mediaPath) {
        final String rest = mediaPath.substring("media:".length());
        final int slash = rest.indexOf('/');
        return slash < 0 ? "" : rest.substring(slash + 1);
    }

    /** Re-syncs the reader holding {@code media:<readerPos>} after its medium's filesystem changed. */
    private static void commitMedia(final ServerLevel level,
            final dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity computer,
            final String mediaPath) {
        final String rest = mediaPath.substring("media:".length());
        final int slash = rest.indexOf('/');
        final long readerPos;
        try {
            readerPos = Long.parseLong(slash < 0 ? rest : rest.substring(0, slash));
        } catch (final NumberFormatException e) {
            return;
        }
        if (level.getBlockEntity(net.minecraft.core.BlockPos.of(readerPos))
                instanceof dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity reader) {
            reader.setChanged();
            level.sendBlockUpdated(net.minecraft.core.BlockPos.of(readerPos),
                    reader.getBlockState(), reader.getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Free space on a medium in mB-equivalents (capacity minus its stored files). */
    private static long mediaFreeWeight(final net.minecraft.world.item.ItemStack media) {
        final long cap = media.getItem()
                instanceof dev.jsc.jscomputronics.module.computing.os.media.FormattedMediaItem fm
                ? fm.format().capacityItems() : 64L;
        final long capWeight = cap
                * dev.jsc.jscomputronics.module.computing.storage.StorageKey.MB_EQ_PER_ITEM;
        final long fsUsed = media.getOrDefault(
                        dev.jsc.jscomputronics.module.computing.ComputingModule.FILESYSTEM.get(),
                        dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents.EMPTY)
                .usedWeight();
        // A DATA medium can also hold a stored item/fluid snapshot (MEDIA_DATA); both consume the medium's
        // capacity, so deduct both, mirroring AbstractComputerBlockEntity.systemDiskFreeWeight (DISK_STORAGE +
        // FILESYSTEM). Ignoring MEDIA_DATA let the player write files past the medium's real capacity.
        final long dataUsed = media.getOrDefault(
                        dev.jsc.jscomputronics.module.computing.ComputingModule.MEDIA_DATA.get(),
                        dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.EMPTY)
                .usedWeight();
        return Math.max(0L, capWeight - fsUsed - dataUsed);
    }

    private static void handleDiskFiles(final DiskFilesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.os.FilesApp.accept(payload));
    }

    private static void handleRequestDesktopFiles(final RequestDesktopFilesPayload payload,
                                                  final IPayloadContext context) {
        context.enqueueWork(() -> {
            final java.util.List<DiskFilesPayload.WireFile> wire = new java.util.ArrayList<>();
            final String[] prefs = {"", ""};
            final java.util.List<String> programs = new java.util.ArrayList<>();
            final java.util.List<DesktopFilesPayload.WireIconCell> iconCells = new java.util.ArrayList<>();
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer) {
                final net.minecraft.world.item.ItemStack disk = computer.systemDisk();
                final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind =
                        filesystemKindOf(computer);
                prefs[0] = computer.console().wallpaper();
                prefs[1] = computer.console().computerName();
                // Installed programs that open as their own desktop window/menu (vs. the built-in apps).
                if (computer.console() != null && computer.console().isInstalled(
                        dev.jsc.jscomputronics.module.computing.program.Programs.NMS.toString())) {
                    programs.add("nms");
                }
                if (computer instanceof CraftingComputerBlockEntity && computer.console() != null
                        && computer.console().isInstalled(Programs.CRAFTING_MANAGER.toString())) {
                    programs.add("crafting_manager");
                }
                // The desktop folder only exists on a hierarchical (desktop OS) disk.
                if (!disk.isEmpty()
                        && kind == dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL) {
                    final String desktopDir =
                            dev.jsc.jscomputronics.module.computing.os.fs.SystemLayout.DESKTOP_DIR;
                    for (final String d
                            : dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.listDirs(
                                    disk, desktopDir, kind)) {
                        wire.add(new DiskFilesPayload.WireFile(d, "", 0L, false, true));
                    }
                    for (final dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.FileEntry e
                            : dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.list(
                                    disk, desktopDir, kind)) {
                        wire.add(new DiskFilesPayload.WireFile(
                                e.path(), e.type().extension(), e.weight(), e.readOnly(), false));
                    }
                }
                // Pinned icon cells. A "file:" pin whose desktop file no longer exists is dropped here and
                // forgotten from the console state too, so a stale position never haunts a later file that
                // happens to take the same name (self-healing). "app:" launcher pins are always kept.
                final java.util.Set<String> desktopNames = new java.util.HashSet<>();
                for (final DiskFilesPayload.WireFile f : wire) {
                    desktopNames.add(baseNameOf(f.path()));
                }
                boolean prunedAnyPin = false;
                for (final java.util.Map.Entry<String, Integer> e
                        : new java.util.ArrayList<>(computer.console().iconCells().entrySet())) {
                    final String key = e.getKey();
                    if (key.startsWith("file:") && !desktopNames.contains(key.substring("file:".length()))) {
                        computer.console().clearIconCell(key);
                        prunedAnyPin = true;
                        continue;
                    }
                    iconCells.add(new DesktopFilesPayload.WireIconCell(key, e.getValue()));
                }
                if (prunedAnyPin) {
                    computer.setChanged();
                }
            }
            context.reply(new DesktopFilesPayload(wire, prefs[0], prefs[1], programs, iconCells));
        });
    }

    private static void handleSetDesktopPrefs(final SetDesktopPrefsPayload payload,
                                              final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer) {
                computer.console().setWallpaper(payload.wallpaper());
                computer.console().setComputerName(payload.computerName());
                computer.setChanged();
            }
        });
    }

    /** The last path segment (after the final {@code /}), or the whole path when it has no slash. */
    private static String baseNameOf(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    private static void handleSetIconPosition(final SetIconPositionPayload payload,
                                              final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer) {
                computer.console().setIconCell(payload.iconKey(), payload.cell());
                computer.setChanged();
            }
        });
    }

    private static void handleDesktopFiles(final DesktopFilesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.os.DesktopScreen.acceptDesktop(payload));
    }

    private static void handleRequestThisPc(final RequestThisPcPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final java.util.List<ThisPcPayload.WireDisk> disks = new java.util.ArrayList<>();
            final java.util.List<ThisPcPayload.WireMedia> media = new java.util.ArrayList<>();
            final java.util.List<String> installed = new java.util.ArrayList<>();
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer) {
                final net.minecraft.world.item.ItemStack sys = computer.systemDisk();
                int slot = 0;
                for (final net.minecraft.world.item.ItemStack stack : computer.diskStacks()) {
                    if (stack.getItem() instanceof dev.jsc.jscomputronics.module.computing.item.DiskItem diskItem) {
                        final long cap = diskItem.spec().capacityItems();
                        final long storageW = stack.getOrDefault(
                                dev.jsc.jscomputronics.module.computing.ComputingModule.DISK_STORAGE.get(),
                                dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.EMPTY)
                                .usedWeight();
                        final long fsW = stack.getOrDefault(
                                dev.jsc.jscomputronics.module.computing.ComputingModule.FILESYSTEM.get(),
                                dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents.EMPTY)
                                .usedWeight();
                        final net.minecraft.resources.ResourceLocation osId =
                                stack.get(dev.jsc.jscomputronics.module.computing.ComputingModule.SYSTEM_OS.get());
                        final dev.jsc.jscomputronics.module.computing.os.OsDef os =
                                osId != null ? dev.jsc.jscomputronics.module.computing.os.OsRegistry.getOs(osId) : null;
                        final long osItems = os != null ? os.footprintItems() : 0L;
                        final long mbEq = dev.jsc.jscomputronics.module.computing.storage.StorageKey.MB_EQ_PER_ITEM;
                        final long usedItems = (storageW + fsW) / mbEq + osItems;
                        disks.add(new ThisPcPayload.WireDisk(slot, stack.getHoverName().getString(),
                                cap, usedItems, stack == sys, osId != null ? osId.getPath() : ""));
                    }
                    slot++;
                }
                for (final long endpoint : computer.linkedEndpoints()) {
                    if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                            instanceof dev.jsc.jscomputronics.module.computing.os.media
                                    .MediaReaderBlockEntity reader) {
                        final net.minecraft.world.item.ItemStack m = reader.mediaSlot().getStackInSlot(0);
                        final dev.jsc.jscomputronics.module.computing.os.media.MediaKind kind = reader.insertedKind();
                        final net.minecraft.resources.ResourceLocation pl = reader.insertedPayload();
                        final boolean installable = kind
                                == dev.jsc.jscomputronics.module.computing.os.media.MediaKind.PROGRAM_INSTALL
                                && pl != null && !computer.console().isInstalled(pl.toString());
                        media.add(new ThisPcPayload.WireMedia(endpoint, reader.driveType().name(),
                                m.isEmpty() ? "" : m.getHoverName().getString(),
                                kind != null ? kind.name() : "", pl != null ? pl.getPath() : "", installable));
                    }
                }
                installed.addAll(computer.console().installed());
            }
            context.reply(new ThisPcPayload(disks, media, installed));
        });
    }

    private static void handleThisPc(final ThisPcPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.os.ThisPcApp.accept(payload));
    }

    private static void handleInstallFromMedia(final InstallFromMediaPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer)) {
                return;
            }
            // The drive must be a media reader currently linked to this computer.
            if (!computer.linkedEndpoints().contains(payload.readerPos())
                    || !(level.getBlockEntity(net.minecraft.core.BlockPos.of(payload.readerPos()))
                            instanceof dev.jsc.jscomputronics.module.computing.os.media
                                    .MediaReaderBlockEntity reader)) {
                return;
            }
            if (reader.insertedKind()
                    != dev.jsc.jscomputronics.module.computing.os.media.MediaKind.PROGRAM_INSTALL) {
                return;
            }
            final net.minecraft.resources.ResourceLocation pl = reader.insertedPayload();
            // A program needs an installed OS to host it.
            if (pl == null || computer.installedOs() == null) {
                return;
            }
            // OS-capability gate: e.g. the NMS only installs on a full desktop OS (Panes), not MC-DOS/MC-NET.
            if (!dev.jsc.jscomputronics.module.computing.os.OsRegistry.canHostRun(computer.installedOsId(), pl)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "This program needs a more capable OS (a graphical desktop) than this computer runs."),
                        false);
                return;
            }
            // Hardware gate: the Crafting Manager installs only on a Crafting Computer. Other programs
            // (NMS, IQL Engine) carry no machine restriction.
            if (pl.equals(Programs.CRAFTING_MANAGER)
                    && !(computer instanceof CraftingComputerBlockEntity)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "The Crafting Manager only installs on a Crafting Computer."), false);
                return;
            }
            if (computer.console().install(pl.toString())) {
                computer.setChanged();
            }
        });
    }

    private static void handleDesktopShellRun(final DesktopShellRunPayload payload,
                                              final IPayloadContext context) {
        context.enqueueWork(() -> {
            final java.util.List<DesktopShellOutputPayload.WireLine> wire = new java.util.ArrayList<>();
            boolean clear = false;
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host) {
                final var computer =
                        new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(host, level);
                final var shell =
                        dev.jsc.jscomputronics.module.computing.program.cli.CliCommands.newShell(CLI_WIDTH);
                final var response = shell.run(payload.line(), computer);
                clear = response.clearScreen();
                for (final var cliLine : response.lines()) {
                    wire.add(new DesktopShellOutputPayload.WireLine(cliLine.text(), cliLine.style().ordinal()));
                }
            }
            context.reply(new DesktopShellOutputPayload(clear, wire));
        });
    }

    private static void handleDesktopShellOutput(final DesktopShellOutputPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            // The console reply routes to whichever desktop window owns a console (the Shell or the
            // Network Interactor's embedded command line); both ignore it when not open.
            dev.jsc.jscomputronics.module.computing.client.os.ShellApp.accept(payload);
            dev.jsc.jscomputronics.module.computing.client.os.NetworkInteractorApp.acceptConsole(payload);
        });
    }

    private static void handleSaveFile(final SaveFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            boolean ok = false;
            String msg = "No computer";
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer) {
                final String path = payload.path();
                final boolean media = path.startsWith("media:");
                final net.minecraft.world.item.ItemStack vol =
                        media ? mediaStackFor(level, computer, path) : computer.systemDisk();
                final String real = media ? mediaSubPath(path) : path;
                final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind = media
                        ? dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL
                        : filesystemKindOf(computer);
                final int dot = real.lastIndexOf('.');
                final String ext = dot >= 0 && dot < real.length() - 1
                        ? real.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
                final dev.jsc.jscomputronics.module.computing.os.fs.FileType type =
                        dev.jsc.jscomputronics.module.computing.os.fs.FileType.fromExtension(ext).orElse(null);
                if (vol.isEmpty()
                        || kind == dev.jsc.jscomputronics.module.computing.os.FilesystemKind.NONE) {
                    msg = media ? "No medium" : "No system disk";
                } else if (type == null) {
                    msg = "Unknown type (.txt/.iql/.cfg/.csv/.cmd)";
                } else if (!type.userEditable()) {
                    msg = "." + type.extension() + " is read-only";
                } else {
                    final long oldWeight = dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem
                            .read(vol, real)
                            .map(c -> dev.jsc.jscomputronics.module.computing.os.fs.FsPaths.sizeMbEq(
                                    c.getBytes(java.nio.charset.StandardCharsets.UTF_8).length))
                            .orElse(0L);
                    final long free = media ? mediaFreeWeight(vol) + oldWeight
                            : computer.systemDiskFreeWeight() + oldWeight;
                    final var result = dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.write(
                            vol, real, type, payload.content(), free, kind);
                    switch (result) {
                        case OK -> {
                            if (media) {
                                commitMedia(level, computer, path);
                            } else {
                                computer.setChanged();
                            }
                            ok = true;
                            msg = "Saved " + path;
                        }
                        case INVALID_PATH -> msg = "Invalid file name";
                        case DISK_FULL -> msg = "Not enough free space";
                        case READ_ONLY -> msg = "Read-only";
                    }
                }
            }
            context.reply(new FileSavedPayload(ok, msg));
        });
    }

    private static void handleFileSaved(final FileSavedPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.os.EditorApp.accept(payload));
    }

    private static void handleDeleteFile(final DeleteFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer)) {
                return;
            }
            final String path = payload.path();
            final boolean media = path.startsWith("media:");
            final net.minecraft.world.item.ItemStack vol =
                    media ? mediaStackFor(level, computer, path) : computer.systemDisk();
            if (vol.isEmpty()) {
                return;
            }
            final String real = media ? mediaSubPath(path) : path;
            final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind = media
                    ? dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            // Try removing a real file first; if the path is a folder, remove it recursively.
            if (dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.delete(vol, real)
                    || dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.rmdir(vol, real, kind)) {
                if (media) {
                    commitMedia(level, computer, path);
                } else {
                    computer.setChanged();
                }
            }
        });
    }

    /** Resolves the filesystem kind of the computer's installed OS, or NONE when absent. */
    private static dev.jsc.jscomputronics.module.computing.os.FilesystemKind filesystemKindOf(
            final dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity computer) {
        final dev.jsc.jscomputronics.module.computing.os.OsDef os = computer.installedOs();
        if (os == null) {
            return dev.jsc.jscomputronics.module.computing.os.FilesystemKind.NONE;
        }
        final dev.jsc.jscomputronics.module.computing.os.KernelDef kernel =
                dev.jsc.jscomputronics.module.computing.os.OsRegistry.getKernel(os.kernelId());
        return kernel != null ? kernel.filesystem()
                : dev.jsc.jscomputronics.module.computing.os.FilesystemKind.NONE;
    }

    /**
     * Computes the available free weight on the given disk, mirroring the formula used in
     * {@link dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity#installOs}:
     * capacity minus storage used minus filesystem used minus the OS footprint.
     */
    private static long computeDiskFreeWeight(
            final dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity computer,
            final net.minecraft.world.item.ItemStack disk) {
        if (!(disk.getItem() instanceof dev.jsc.jscomputronics.module.computing.item.DiskItem diskItem)) {
            return 0L;
        }
        final long capacityWeight = diskItem.spec().capacityItems()
                * dev.jsc.jscomputronics.module.computing.storage.StorageKey.MB_EQ_PER_ITEM;
        final long storageUsed = disk.getOrDefault(
                dev.jsc.jscomputronics.module.computing.ComputingModule.DISK_STORAGE.get(),
                dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.EMPTY).usedWeight();
        final long fsUsed = disk.getOrDefault(
                dev.jsc.jscomputronics.module.computing.ComputingModule.FILESYSTEM.get(),
                dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents.EMPTY).usedWeight();
        final long osReserved = computer.reservedByOs()
                * dev.jsc.jscomputronics.module.computing.storage.StorageKey.MB_EQ_PER_ITEM;
        return Math.max(0L, capacityWeight - storageUsed - fsUsed - osReserved);
    }

    private static void handleMkdir(final MkdirPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer)) {
                return;
            }
            final String path = payload.path();
            final boolean media = path.startsWith("media:");
            final net.minecraft.world.item.ItemStack vol =
                    media ? mediaStackFor(level, computer, path) : computer.systemDisk();
            if (vol.isEmpty()) {
                return;
            }
            final String real = media ? mediaSubPath(path) : path;
            final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind = media
                    ? dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            if (dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.mkdir(vol, real, kind)) {
                if (media) {
                    commitMedia(level, computer, path);
                } else {
                    computer.setChanged();
                }
            }
        });
    }

    private static void handleMoveFile(final MoveFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer)) {
                return;
            }
            final String src = payload.srcPath();
            final String destDir = payload.destDir();
            final boolean srcMedia = src.startsWith("media:");
            final boolean dstMedia = destDir.startsWith("media:");
            final net.minecraft.world.item.ItemStack srcVol =
                    srcMedia ? mediaStackFor(level, computer, src) : computer.systemDisk();
            final net.minecraft.world.item.ItemStack dstVol =
                    dstMedia ? mediaStackFor(level, computer, destDir) : computer.systemDisk();
            if (srcVol.isEmpty() || dstVol.isEmpty()) {
                return;
            }
            final String realSrc = srcMedia ? mediaSubPath(src) : src;
            final String realDstDir = dstMedia ? mediaSubPath(destDir) : destDir;
            final dev.jsc.jscomputronics.module.computing.os.FilesystemKind srcKind = srcMedia
                    ? dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            if (volumeKey(src).equals(volumeKey(destDir))) {
                // Same volume — an in-place move.
                if (dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.move(
                        srcVol, realSrc, realDstDir, srcKind)) {
                    if (srcMedia) {
                        commitMedia(level, computer, src);
                    } else {
                        computer.setChanged();
                    }
                }
                return;
            }
            // Cross-volume (disk <-> media): copy the file then delete the source. Directories are
            // not copied across volumes here.
            final var read = dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.read(srcVol, realSrc);
            if (read.isEmpty()) {
                return;
            }
            final String name = realSrc.contains("/")
                    ? realSrc.substring(realSrc.lastIndexOf('/') + 1) : realSrc;
            final int dot = name.lastIndexOf('.');
            final String ext = dot >= 0 && dot < name.length() - 1
                    ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
            final dev.jsc.jscomputronics.module.computing.os.fs.FileType type =
                    dev.jsc.jscomputronics.module.computing.os.fs.FileType.fromExtension(ext)
                            .orElse(dev.jsc.jscomputronics.module.computing.os.fs.FileType.TXT);
            final dev.jsc.jscomputronics.module.computing.os.FilesystemKind dstKind = dstMedia
                    ? dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            final String destPath = realDstDir.isEmpty() ? name : realDstDir + "/" + name;
            final long free = dstMedia ? mediaFreeWeight(dstVol) : computer.systemDiskFreeWeight();
            if (dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.write(
                    dstVol, destPath, type, read.get(), free, dstKind)
                    == dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.WriteResult.OK) {
                dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.delete(srcVol, realSrc);
                if (srcMedia) {
                    commitMedia(level, computer, src);
                } else {
                    computer.setChanged();
                }
                if (dstMedia) {
                    commitMedia(level, computer, destDir);
                } else {
                    computer.setChanged();
                }
            }
        });
    }

    /**
     * Sanctioned {@code .dat}-onto-medium item transfer (the one manual {@code .dat} operation that is allowed).
     *
     * <p>A {@code .dat} is a read-only projection of an item kept in the computer's disks. Dragging it onto a
     * removable medium does not copy a file — it moves the stored item. The flow is conservative end to end so
     * an item is never lost or duplicated:
     * <ol>
     *   <li>Resolve {@code datPath} back to its {@link StorageKey} by re-projecting the system disk (the
     *       projection is deterministic, so the same path maps back to the same key).</li>
     *   <li>Extract the full stored quantity of that key from the computer's local storage.</li>
     *   <li>Insert as much as the medium's free capacity allows into its {@code MEDIA_DATA} snapshot; whatever
     *       does not fit is returned to the computer's storage.</li>
     * </ol>
     * The source {@code .dat} vanishes on its own once the key leaves {@code DISK_STORAGE}, and the item then
     * shows up under the medium's projection — no second item-movement path, no byte copy.
     */
    private static void handleMediumTransfer(final MediumTransferPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.hostPos(), payload.monitorPos());
            if (host == null
                    || !(host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                            .AbstractComputerBlockEntity computer)) {
                return;
            }
            // The destination must be a DATA medium in a linked drive; anything else cannot hold a snapshot.
            final String mediaKey = payload.mediaVolumeKey();
            if (!mediaKey.startsWith("media:")) {
                return;
            }
            final net.minecraft.world.item.ItemStack media = mediaStackFor(level, computer, mediaKey);
            if (media.isEmpty()
                    || dev.jsc.jscomputronics.module.computing.os.media.MediaItem.kind(media)
                            != dev.jsc.jscomputronics.module.computing.os.media.MediaKind.DATA) {
                return;
            }
            // Resolve the .dat path back to the StorageKey it projects from the system disk.
            final StorageKey key = resolveDatKey(computer.systemDisk(), payload.datPath());
            if (key == null) {
                return;
            }
            if (transferDatToMedium(host, key, media) > 0L) {
                commitMedia(level, computer, mediaKey);
                computer.setChanged();
                sendNetworkInteractor(player, level, computer);
            }
        });
    }

    /**
     * Moves the stored quantity of {@code key} from a computer's local storage onto a DATA {@code media} stack,
     * bounded by the medium's free capacity. Conservative: it extracts first and inserts only what was
     * extracted, capped by what fits, so the sum across the two stores is invariant — nothing is created or
     * destroyed. Returns the number of native units actually moved.
     *
     * <p>Exposed so it can be exercised directly by a GameTest with real component stacks, without a
     * player/menu round-trip.
     */
    public static long transferDatToMedium(final ComputerTerminalHost host, final StorageKey key,
                                           final ItemStack media) {
        final long stored = host.localStore().count(key);
        if (stored <= 0L) {
            return 0L;
        }
        // How many native units fit in the medium's remaining capacity (weight budget / per-unit weight).
        final long unitWeight = Math.max(1L, key.weight(1L));
        final long roomUnits = mediaFreeWeight(media) / unitWeight;
        if (roomUnits <= 0L) {
            return 0L;
        }
        final long toMove = Math.min(stored, roomUnits);
        // Extract first; only what was actually extracted is ever inserted, so the two halves stay balanced.
        final long extracted = host.localStore().extract(key, toMove);
        if (extracted <= 0L) {
            return 0L;
        }
        try {
            final java.util.Map<StorageKey, Long> next = new java.util.LinkedHashMap<>(
                    dev.jsc.jscomputronics.module.computing.os.media.MediaItem.data(media).items());
            next.merge(key, extracted, Long::sum);
            dev.jsc.jscomputronics.module.computing.os.media.MediaItem.setData(media,
                    new dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents(next));
        } catch (final RuntimeException e) {
            // The medium write failed after the items already left local storage; put them back so the
            // exceptional path still conserves items (nothing lost), then rethrow.
            host.localStore().insert(key, extracted);
            throw e;
        }
        return extracted;
    }

    /**
     * Resolves a {@code .dat} path back to the {@link StorageKey} it projects, by re-running the deterministic
     * {@link dev.jsc.jscomputronics.module.computing.os.fs.StorageProjection} over the disk's {@code DISK_STORAGE}
     * and matching the requested path. Returns {@code null} when no projected entry matches (e.g. a stale path).
     */
    @org.jetbrains.annotations.Nullable
    public static StorageKey resolveDatKey(final net.minecraft.world.item.ItemStack disk, final String datPath) {
        if (disk.isEmpty()) {
            return null;
        }
        final dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents storage = disk.getOrDefault(
                dev.jsc.jscomputronics.module.computing.ComputingModule.DISK_STORAGE.get(),
                dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.EMPTY);
        // The projection emits one entry per key in iteration order, with the same path each time; pair each
        // emitted path with the storage key at the same position to invert the path back to its key.
        final java.util.List<dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.FileEntry> entries =
                dev.jsc.jscomputronics.module.computing.os.fs.StorageProjection.project(storage);
        final java.util.Iterator<StorageKey> keys = storage.items().keySet().iterator();
        for (final var entry : entries) {
            final StorageKey key = keys.hasNext() ? keys.next() : null;
            if (key != null && entry.path().equals(datPath)) {
                return key;
            }
        }
        return null;
    }

    /** The volume identity of a path: {@code ""} for the system disk, or the reader pos for a {@code media:} path. */
    private static String volumeKey(final String path) {
        if (!path.startsWith("media:")) {
            return "";
        }
        final String rest = path.substring("media:".length());
        final int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private static void handleRenameVolume(final RenameVolumePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.host())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer)) {
                return;
            }
            final String key = payload.volumeKey();
            final boolean media = key.startsWith("media:");
            final net.minecraft.world.item.ItemStack vol;
            if (media) {
                vol = mediaStackFor(level, computer, key);
            } else if (key.startsWith("disk:")) {
                // Rename a specific installed disk by its slot (not just the system disk).
                int slot = -1;
                try {
                    slot = Integer.parseInt(key.substring("disk:".length()));
                } catch (final NumberFormatException ignored) {
                    // leaves slot = -1, which diskInSlot rejects
                }
                vol = computer.diskInSlot(slot);
            } else {
                vol = computer.systemDisk();
            }
            if (vol.isEmpty()) {
                return;
            }
            dev.jsc.jscomputronics.module.computing.os.VolumeLabel.set(vol, payload.label());
            if (media) {
                commitMedia(level, computer, key);
            } else {
                computer.setChanged();
            }
        });
    }

    private static void handleRequestFileContent(final RequestFileContentPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            String content = "";
            boolean exists = false;
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer) {
                final String path = payload.path();
                final boolean media = path.startsWith("media:");
                final net.minecraft.world.item.ItemStack vol =
                        media ? mediaStackFor(level, computer, path) : computer.systemDisk();
                if (!vol.isEmpty()) {
                    final var read = dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem
                            .read(vol, media ? mediaSubPath(path) : path);
                    if (read.isPresent()) {
                        content = read.get();
                        exists = true;
                    }
                }
            }
            context.reply(new FileContentPayload(payload.path(), content, exists));
        });
    }

    private static void handleFileContent(final FileContentPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.os.EditorApp.acceptContent(
                        payload.path(), payload.content(), payload.exists()));
    }

    private static void handleRenameFile(final RenameFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer)) {
                return;
            }
            final String oldPath = payload.oldPath();
            final String newPath = payload.newPath();
            final boolean media = oldPath.startsWith("media:");
            final net.minecraft.world.item.ItemStack vol =
                    media ? mediaStackFor(level, computer, oldPath) : computer.systemDisk();
            if (vol.isEmpty()) {
                return;
            }
            final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind = media
                    ? dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            // rename() re-keys a real file or directory in place (rejecting .dat projections).
            if (dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.rename(vol,
                    media ? mediaSubPath(oldPath) : oldPath,
                    media ? mediaSubPath(newPath) : newPath, kind)) {
                if (media) {
                    commitMedia(level, computer, oldPath);
                } else {
                    computer.setChanged();
                }
            }
        });
    }

    private static void handleCraftCatalog(final CraftCatalogPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setCraftCatalog(payload.entries());
            }
        });
    }

    private static void handleCraftPlan(final CraftPlanPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setCraftPlan(payload);
            } else {
                // The desktop Network Interactor has no container menu; route the plan to its craft popup.
                dev.jsc.jscomputronics.module.computing.client.os.NetworkInteractorApp.acceptCraftPlan(payload);
            }
        });
    }

    public static void dispatchCraftCatalog(final ServerPlayer player, final NetworkUuid net,
                                            final ServerLevel level) {
        PacketDistributor.sendToPlayer(player, new CraftCatalogPayload(buildCraftCatalog(level, net)));
    }

    /** The network's craft catalog (distinct ROM results + availability dots), shared by the terminal and the desktop. */
    private static List<CraftCatalogPayload.Entry> buildCraftCatalog(final ServerLevel level, final NetworkUuid net) {
        final MainframeBlockEntity mainframe = net == null ? null : resolveMainframe(level, net);
        if (mainframe == null) {
            return java.util.List.of();
        }
        final var patterns = mainframe.networkPatterns();
        final var stock = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                .of(level, net).query();
        final java.util.Map<StorageKey, CraftCatalogPayload.Entry> entries = new java.util.LinkedHashMap<>();
        for (final var pattern : patterns) {
            final StorageKey key = StorageKey.of(pattern.result());
            if (entries.containsKey(key)) {
                continue;
            }
            final byte dot;
            if (dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner
                    .plan(key, 1, patterns, stock).feasible()) {
                dot = CraftCatalogPayload.DOT_GREEN;
            } else {
                boolean any = false;
                for (final StorageKey ingredient : pattern.ingredientTotals().keySet()) {
                    if (stock.getOrDefault(ingredient, 0L) > 0L) {
                        any = true;
                        break;
                    }
                }
                dot = any ? CraftCatalogPayload.DOT_AMBER : CraftCatalogPayload.DOT_RED;
            }
            entries.put(key, new CraftCatalogPayload.Entry(pattern.result().copy(), dot));
            if (entries.size() >= CraftCatalogPayload.MAX_ENTRIES) {
                break;
            }
        }
        // Machine recipes (processing / multi-stage) the network can run, shown by their primary item result.
        for (final var recipe : mainframe.networkMachineRecipes()) {
            if (entries.size() >= CraftCatalogPayload.MAX_ENTRIES) {
                break;
            }
            final StorageKey key = recipe.resultKey();
            if (key == null || entries.containsKey(key)) {
                continue;
            }
            final net.minecraft.world.item.ItemStack result = key.stack(1);
            if (result.isEmpty()) {
                continue; // a fluid result: the item catalog cannot render it yet (v1)
            }
            byte dot = CraftCatalogPayload.DOT_AMBER;
            if (recipe.proc().isPresent()) {
                boolean all = true;
                boolean any = false;
                for (final var in : recipe.proc().get().inputs()) {
                    if (stock.getOrDefault(in.key(), 0L) >= in.amount()) {
                        any = true;
                    } else {
                        all = false;
                    }
                }
                dot = all ? CraftCatalogPayload.DOT_GREEN
                        : (any ? CraftCatalogPayload.DOT_AMBER : CraftCatalogPayload.DOT_RED);
            }
            entries.put(key, new CraftCatalogPayload.Entry(result, dot));
        }
        return java.util.List.copyOf(entries.values());
    }

    private static void handleCraftPlanRequest(final CraftPlanRequestPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = craftHost(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || payload.quantity() <= 0L) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                return;
            }
            final var patterns = mainframe.networkPatterns();
            final var stock = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                    .of(level, host.networkUuid()).query();
            final StorageKey key = StorageKey.of(payload.result());
            // A machine recipe (processing or multi-stage) plans by its own inputs: the bench planner knows
            // nothing about it and would list the result itself as a missing raw ingredient.
            final var machinePlan = planMachineRecipe(mainframe, key, payload.quantity(), stock);
            if (machinePlan != null) {
                PacketDistributor.sendToPlayer(player, new CraftPlanPayload(
                        payload.result(), payload.quantity(), machinePlan.rows(),
                        machinePlan.feasible(), machinePlan.maxFeasible(), machinePlan.estimateTicks()));
                return;
            }
            final var plan = dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner
                    .plan(key, payload.quantity(), patterns, stock);

            // Raw-ingredient rows: total needed (consumed + still missing) vs what the network has.
            final java.util.Map<StorageKey, Long> need = new java.util.LinkedHashMap<>(plan.rawConsumption());
            plan.missing().forEach((k, v) -> need.merge(k, v, Long::sum));
            final java.util.List<CraftPlanPayload.Row> rows = new java.util.ArrayList<>();
            for (final var entry : need.entrySet()) {
                if (rows.size() >= CraftPlanPayload.MAX_ROWS) {
                    break;
                }
                final ItemStack icon = entry.getKey().stack(1);
                if (!icon.isEmpty()) {
                    rows.add(new CraftPlanPayload.Row(icon, entry.getValue(),
                            Math.min(stock.getOrDefault(entry.getKey(), 0L), entry.getValue())));
                }
            }
            final boolean feasible = plan.feasible();
            final long maxFeasible = feasible ? payload.quantity()
                    : dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner
                            .maxFeasible(key, payload.quantity(), patterns, stock);
            PacketDistributor.sendToPlayer(player, new CraftPlanPayload(
                    payload.result(), payload.quantity(), java.util.List.copyOf(rows),
                    feasible, maxFeasible, estimateTicks(level, mainframe, plan)));
        });
    }

    private static int estimateTicks(final ServerLevel level, final MainframeBlockEntity mainframe,
                                     final dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner.Plan plan) {
        long units = 0;
        for (final var step : plan.steps()) {
            units += step.runs() * Math.max(1, step.pattern().filledCells());
        }
        long rate = 0;
        for (final net.minecraft.core.BlockPos pos : mainframe.craftingComputerPositions()) {
            if (level.getBlockEntity(pos)
                    instanceof dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity cc
                    && cc.canCraft()) {
                rate = Math.max(rate, cc.craftingThroughput());
            }
        }
        if (rate <= 0) {
            return 0;
        }
        return (int) Math.max(1, (units + rate - 1) / rate);
    }

    /** A machine recipe's plan for the request popup: raw rows (need vs have), feasibility, max and estimate. */
    private record MachinePlan(java.util.List<CraftPlanPayload.Row> rows, boolean feasible, long maxFeasible,
                               int estimateTicks) {
    }

    /**
     * Plans {@code quantity} of a machine-made result from the raw inputs of its recipe: a processing pattern's
     * inputs over the runs its primary output needs, or a multi-stage pipeline's FIRST stage inputs over that
     * stage's demand (later stages consume what earlier ones make). Returns null when no machine recipe on the
     * network produces the key, so the bench planner handles it.
     */
    @org.jetbrains.annotations.Nullable
    private static MachinePlan planMachineRecipe(final MainframeBlockEntity mainframe, final StorageKey key,
                                                 final long quantity, final java.util.Map<StorageKey, Long> stock) {
        for (final var recipe : mainframe.networkMachineRecipes()) {
            if (!key.equals(recipe.resultKey())) {
                continue;
            }
            final dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern first;
            final long firstDemand;
            int estimate;
            if (recipe.proc().isPresent()) {
                first = recipe.proc().get();
                firstDemand = quantity;
                estimate = first.timeoutTicks();
            } else if (recipe.multi().isPresent() && !recipe.multi().get().stages().isEmpty()) {
                final var multi = recipe.multi().get();
                final long[] demands = multi.stageDemands(quantity);
                final var stage = multi.stages().get(0);
                firstDemand = demands[0];
                estimate = 0;
                for (final var s : multi.stages()) {
                    estimate += s.proc().map(dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern
                            ::timeoutTicks).orElse(20);
                }
                if (stage.proc().isPresent()) {
                    first = stage.proc().get();
                } else {
                    // A bench-first pipeline: its raw inputs are the bench pattern's ingredients per run.
                    final var bench = stage.bench().get();
                    final long runs = ceilDiv(firstDemand, Math.max(1, bench.result().getCount()));
                    final java.util.List<CraftPlanPayload.Row> rows = new java.util.ArrayList<>();
                    long maxRuns = Long.MAX_VALUE;
                    for (final var in : bench.ingredientTotals().entrySet()) {
                        final long need = in.getValue() * runs;
                        final long have = stock.getOrDefault(in.getKey(), 0L);
                        maxRuns = Math.min(maxRuns, have / Math.max(1, in.getValue()));
                        rows.add(new CraftPlanPayload.Row(in.getKey().stack(1), need, Math.min(have, need)));
                    }
                    final long maxFirst = maxRuns == Long.MAX_VALUE ? 0 : maxRuns * bench.result().getCount();
                    return new MachinePlan(java.util.List.copyOf(rows), maxFirst >= firstDemand,
                            Math.min(quantity, forwardYield(multi, maxFirst)), estimate);
                }
            } else {
                return null;
            }
            final var primary = first.primaryOutput();
            final long perRun = primary == null ? 1 : Math.max(1, primary.amount());
            final long runs = ceilDiv(firstDemand, perRun);
            final java.util.List<CraftPlanPayload.Row> rows = new java.util.ArrayList<>();
            long maxRuns = Long.MAX_VALUE;
            for (final var in : first.inputs()) {
                final long need = in.amount() * runs;
                final long have = stock.getOrDefault(in.key(), 0L);
                maxRuns = Math.min(maxRuns, have / Math.max(1, in.amount()));
                final ItemStack icon = in.key().stack(1);
                rows.add(new CraftPlanPayload.Row(icon, need, Math.min(have, need)));
            }
            final long maxFirst = maxRuns == Long.MAX_VALUE ? 0 : maxRuns * perRun;
            final long maxFinal = recipe.multi().isPresent()
                    ? forwardYield(recipe.multi().get(), maxFirst) : maxFirst;
            return new MachinePlan(java.util.List.copyOf(rows), maxFirst >= firstDemand,
                    Math.min(quantity, maxFinal), estimate);
        }
        return null;
    }

    private static long ceilDiv(final long amount, final long perRun) {
        return (amount + perRun - 1) / perRun;
    }

    /** How much of the final result a pipeline yields when its first stage produces {@code firstOutput}. */
    private static long forwardYield(final dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern multi,
                                     final long firstOutput) {
        // Walk the stage demands for one unit of final result to get each stage's output per final unit.
        final long[] perUnit = multi.stageDemands(1);
        return perUnit.length == 0 || perUnit[0] <= 0 ? firstOutput : firstOutput / perUnit[0];
    }

    /**
     * If {@code resultKey} is produced by a machine recipe on the network, dispatches it to the processing /
     * multi-stage engine and returns the operation; otherwise returns null so the caller runs a normal
     * bench craft. {@code onSettle} runs when the machine craft settles (the caller refreshes its screen).
     */
    @org.jetbrains.annotations.Nullable
    private static dev.jsc.jscomputronics.module.computing.operation.NetworkOperation submitMachineCraft(
            final MainframeBlockEntity mainframe, final StorageKey resultKey, final long quantity,
            final String label, final Runnable onSettle) {
        for (final var recipe : mainframe.networkMachineRecipes()) {
            if (resultKey.equals(recipe.resultKey())) {
                if (recipe.proc().isPresent()) {
                    final var op = mainframe.submitNetworkProcessing(recipe.proc().get(), quantity, label);
                    if (op != null) {
                        op.onSettle(onSettle);
                    }
                    return op;
                }
                if (recipe.multi().isPresent()) {
                    final var op = mainframe.submitNetworkMultiStage(recipe.multi().get(), quantity, label);
                    if (op != null) {
                        op.onSettle(onSettle);
                    }
                    return op;
                }
            }
        }
        return null;
    }

    private static void handleCraftSubmit(final CraftSubmitPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = craftHost(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || payload.quantity() <= 0L) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final StorageKey resultKey = StorageKey.of(payload.result());
            final Runnable refresh = () -> {
                dispatchTerminalOpsLog(player, net, level);
                dispatchActiveOperations(player, net, level);
                dispatchCraftCatalog(player, net, level);
            };
            if (submitMachineCraft(mainframe, resultKey, payload.quantity(), "terminal", refresh) != null) {
                refresh.run();
                return;
            }
            final var operation = mainframe.submitNetworkCraft(
                    resultKey, payload.quantity(), payload.partial(), "terminal");
            if (operation != null) {
                operation.onSettle(() -> {
                    dispatchTerminalOpsLog(player, net, level);
                    dispatchActiveOperations(player, net, level);
                    dispatchCraftCatalog(player, net, level);
                });
            }
            dispatchActiveOperations(player, net, level);
            dispatchCraftCatalog(player, net, level);
        });
    }

    private static void handleRenameServerRouter(final RenameServerRouterPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof ServerRouterMenu menu
                    && menu.routerPos().equals(payload.routerPos())
                    && player.level().getBlockEntity(payload.routerPos())
                            instanceof ServerRouterBlockEntity router) {
                router.setCustomName(payload.name());
            }
        });
    }

    private static void handleSetBusName(final SetBusNamePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof AbstractBusMenu menu
                    && menu.cablePos().equals(payload.cablePos())
                    && menu.face().get3DDataValue() == payload.face()
                    && player.level().getBlockEntity(payload.cablePos()) instanceof DataCableBlockEntity cable
                    && cable.getPart(Direction.from3DDataValue(payload.face())) instanceof AbstractBusPart bus) {
                bus.setName(payload.name());
                menu.setBusNameLocal(bus.name());
            }
        });
    }

    private static void handleDatacenterSnapshot(final DatacenterSnapshotPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof DatacenterStationMenu menu) {
                menu.setSnapshot(payload);
            }
        });
    }

    private static void handleDatacenterAction(final DatacenterStationActionPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DatacenterStationMenu menu)
                    || !menu.stationPos().equals(payload.stationPos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.stationPos()) instanceof DatacenterStationBlockEntity station)) {
                return;
            }
            switch (payload.action()) {
                case DatacenterStationActionPayload.ACTION_NEXT_SECTION -> station.bindNext();
                case DatacenterStationActionPayload.ACTION_CYCLE_BALANCE -> station.cycleLoadBalanceMode();
                case DatacenterStationActionPayload.ACTION_INSERT_CURSOR -> depositCursor(menu, station, level, false);
                case DatacenterStationActionPayload.ACTION_INSERT_CURSOR_ONE -> depositCursor(menu, station, level, true);
                default -> {
                    // ACTION_REFRESH: just re-send the snapshot below.
                }
            }
            sendDatacenterSnapshot(player, level, station);
        });
    }

    private static void depositCursor(final DatacenterStationMenu menu, final DatacenterStationBlockEntity station,
                                      final ServerLevel level, final boolean single) {
        final ItemStack cursor = menu.getCarried();
        if (cursor.isEmpty()) {
            return;
        }
        final List<NodeUuid> servers = station.sectionServers();
        final List<ServerStore> stores = sectionStores(level, servers);
        if (stores.isEmpty()) {
            return;
        }
        final StorageKey key = StorageKey.of(cursor);
        final long want = single ? 1L : cursor.getCount();
        final long stored = LoadBalancer.insert(stores, key, want, station.loadBalanceMode());
        if (stored > 0L) {
            cursor.shrink((int) stored);
            menu.setCarried(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
            // The cursor changed outside a normal slot click; without a broadcast the client keeps
            // showing the old stack (a ghost cursor).
            menu.broadcastChanges();
        }
    }

    private static List<ServerStore> sectionStores(final ServerLevel level, final List<NodeUuid> servers) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<ServerStore> stores = new ArrayList<>();
        for (final NodeUuid node : servers) {
            system.locationOf(node).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    stores.add(rack.getServerStorage(loc.slot()));
                }
            });
        }
        return stores;
    }

    public static void sendDatacenterSnapshot(final ServerPlayer player, final ServerLevel level,
                                              final DatacenterStationBlockEntity station) {
        if (player == null || player.isRemoved()) {
            return;
        }
        final List<NodeUuid> servers = station.sectionServers();
        final List<ServerStore> stores = sectionStores(level, servers);

        final Map<StorageKey, Long> totals = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                .ofServers(level, servers).query();
        final List<NetworkItemEntry> items = new ArrayList<>(Math.min(totals.size(), DatacenterSnapshotPayload.MAX_ITEMS));
        totals.entrySet().stream().limit(DatacenterSnapshotPayload.MAX_ITEMS)
                .forEach(e -> items.add(new NetworkItemEntry(e.getKey(), e.getValue())));

        // The "giant computer" view: the section's Servers summed into one machine — storage,
        // orchestration capacity (CPU) and RAM buffer — plus a per-Server line with its real name.
        long used = 0L;
        long total = 0L;
        long cpu = 0L;
        long ram = 0L;
        final NetworkSystem sys = NetworkSystem.get(level);
        final List<DatacenterSnapshotPayload.ServerLine> lines = new ArrayList<>();
        for (int i = 0; i < servers.size() && i < DatacenterSnapshotPayload.MAX_SERVERS; i++) {
            final NodeUuid node = servers.get(i);
            final ServerStore store = i < stores.size() ? stores.get(i) : null;
            final long u = store == null ? 0L : store.usedWeight();
            final long t = store == null ? 0L : store.capacityWeight();
            used += u;
            total += t;
            String name = "srv-" + node.asString().substring(0, 4);
            final var loc = sys.locationOf(node);
            if (loc.isPresent()
                    && level.getBlockEntity(BlockPos.of(loc.get().rackPos())) instanceof ServerRackBlockEntity rack) {
                final ItemStack stack = rack.getServers().getStackInSlot(loc.get().slot());
                final var build = dev.jsc.jscomputronics.module.computing.item.ServerItem.build(stack);
                if (build != null) {
                    cpu += build.totalCapacity();
                    ram += build.ramBuffer();
                }
                final String custom = dev.jsc.jscomputronics.module.computing.item.ServerItem.customName(stack);
                if (!custom.isEmpty()) {
                    name = custom;
                }
            }
            lines.add(new DatacenterSnapshotPayload.ServerLine(name, u, t));
        }

        // Operations in flight on the network's Mainframe (the orchestrator the section runs under).
        int activeOps = 0;
        final BlockPos mfPosForOps = station.mainframePos();
        if (mfPosForOps != null && level.getBlockEntity(mfPosForOps) instanceof MainframeBlockEntity mf) {
            activeOps = mf.activeOperationRecords().size();
        }

        // MOVE destinations: the network's computers with local storage (PCs + the Mainframe).
        final List<DatacenterSnapshotPayload.DestEntry> dests = new ArrayList<>();
        final NetworkUuid net = station.network();
        if (net != null) {
            final NetworkSystem system = NetworkSystem.get(level);
            for (final var pc : system.personalComputersOf(net)) {
                if (level.getBlockEntity(BlockPos.of(pc.pos())) instanceof PersonalComputerBlockEntity pcBe
                        && pcBe.localStorageCapacity() > 0L) {
                    final String name = pcBe.customName().isEmpty()
                            ? "PC-" + pc.nodeUuid().asString().substring(0, 4) : pcBe.customName();
                    dests.add(new DatacenterSnapshotPayload.DestEntry(pc.pos(), name));
                }
            }
            system.mainframePositionOf(net).ifPresent(mfPos -> {
                if (level.getBlockEntity(BlockPos.of(mfPos))
                        instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host
                        && host.localStorageCapacity() > 0L) {
                    dests.add(new DatacenterSnapshotPayload.DestEntry(mfPos, "Mainframe"));
                }
            });
        }

        PacketDistributor.sendToPlayer(player, new DatacenterSnapshotPayload(
                station.sectionLabel(), servers.size(), used, total,
                station.loadBalanceMode().ordinal(), station.availableSections().size(),
                cpu, ram, activeOps, items, lines, dests));
    }

    private static void handleDatacenterSelect(final DatacenterSelectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.quantity() <= 0L) {
                return; // never dispatch a zero/negative pull (a broken or hostile client)
            }
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DatacenterStationMenu menu)
                    || !menu.stationPos().equals(payload.stationPos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.stationPos()) instanceof DatacenterStationBlockEntity station)) {
                return;
            }
            final BlockPos mainframePos = station.mainframePos();
            if (mainframePos == null
                    || !(level.getBlockEntity(mainframePos) instanceof MainframeBlockEntity mainframe)
                    || !(level.getBlockEntity(BlockPos.of(payload.destPos()))
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost dest)) {
                return;
            }
            // The destination must live on the STATION'S network — the client only ever picks from the
            // snapshot's list, so any other position is a spoofed packet reaching into a foreign network.
            final NetworkUuid stationNet = station.network();
            if (stationNet == null || !stationNet.equals(dest.networkUuid())) {
                return;
            }
            final java.util.Set<NodeUuid> sources = new java.util.HashSet<>(station.sectionServers());
            if (sources.isEmpty()) {
                return;
            }
            final var op = mainframe.submitNetworkMove(payload.key(), payload.quantity(),
                    dest.localStorage(), "datacenter", sources);
            if (op != null) {
                op.onSettle(() -> sendDatacenterSnapshot(player, level, station));
            }
            sendDatacenterSnapshot(player, level, station);
        });
    }

    private static void handleRenamePc(final RenamePcPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && hasOpenAssemblyFor(player, payload.pcPos())
                    && player.level().getBlockEntity(payload.pcPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity computer) {
                computer.setCustomName(payload.name());
            }
        });
    }

    private static boolean hasOpenAssemblyFor(final ServerPlayer player, final net.minecraft.core.BlockPos pos) {
        if (player.containerMenu instanceof PersonalComputerMenu menu) {
            return menu.pcPos().equals(pos);
        }
        if (player.containerMenu instanceof dev.jsc.jscomputronics.module.computing.menu.CraftingComputerMenu menu) {
            return menu.computerPos().equals(pos);
        }
        return false;
    }

    private static void handleLocalUpload(final TerminalLocalUploadPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || payload.quantity() <= 0L) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final StorageKey key = payload.key();
            // Take the items out of local storage and carry them in the Operation; whatever the network
            // cannot hold is returned to local storage when it settles, so nothing is ever lost.
            final long taken = host.localStore().extract(key,
                    Math.min(payload.quantity(), host.localStore().count(key)));
            if (taken <= 0L) {
                return;
            }
            final var op = mainframe.submitNetworkInsert(key, taken, "local");
            if (op == null) {
                host.localStore().insert(key, taken); // no live dispatcher: put it straight back
                return;
            }
            op.onSettle(() -> {
                final long leftover = op.leftover();
                if (leftover > 0L) {
                    host.localStore().insert(key, leftover);
                }
                dispatchLocalSnapshot(player, host);
                sendSnapshot(player, level, net);
            });
        });
    }

    private static void handleRenameServer(final RenameServerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu
                    instanceof dev.jsc.jscomputronics.module.computing.menu.ServerAssemblyMenu menu) {
                menu.setServerName(payload.name());
            }
        });
    }

    // Network-operation dispatch — the ONLY way storage is touched. Every request

    private static void returnToPlayer(final ServerPlayer player, final ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (player.isRemoved()) {
            net.minecraft.world.Containers.dropItemStack(player.level(),
                    player.getX(), player.getY(), player.getZ(), stack);
        } else {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    public static void dispatchQuery(final ServerPlayer player, final PersonalComputerBlockEntity pc) {
        dispatch(player, pc, (level, net, mf) -> mf.submitOperation(
                new dev.jsc.jscomputronics.module.computing.operation.NetworkQueryOperationTask(level, net, player),
                dev.jsc.jscomputronics.common.operation.OperationPriority.MEDIUM));
    }

    @FunctionalInterface
    private interface OperationSubmit {
        boolean submit(ServerLevel level, NetworkUuid net, MainframeBlockEntity mainframe);
    }

    private static boolean dispatch(final ServerPlayer player, final PersonalComputerBlockEntity pc,
                                    final OperationSubmit submit) {
        final NetworkUuid net = pc.networkUuid();
        if (net == null || !(pc.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        return mainframe != null && submit.submit(level, net, mainframe);
    }

    public static boolean networkHasActiveOps(final ServerLevel level, final NetworkUuid network) {
        final MainframeBlockEntity mainframe = resolveMainframe(level, network);
        return mainframe != null && mainframe.hasActiveOperations();
    }

    private static void handleTerminalMaintenance(final TerminalMaintenancePayload payload,
                                                  final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !host.isMainframeHost() || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final dev.jsc.jscomputronics.module.computing.operation.NetworkIndex index = mainframe.networkIndex();
            byte opType;
            long count;
            ItemStack icon;
            String message;
            switch (payload.action()) {
                case TerminalMaintenancePayload.ACTION_ANALYZE -> {
                    index.analyzeIncremental(level, net);
                    opType = OperationRecord.TYPE_ANALYZE;
                    count = index.catalogSize();
                    icon = labelledIcon(Items.SPYGLASS, "index");
                    message = "ANALYZE complete - " + count + " types reconciled";
                }
                case TerminalMaintenancePayload.ACTION_REINDEX -> {
                    index.rebuild(level, net);
                    opType = OperationRecord.TYPE_REINDEX;
                    count = index.catalogSize();
                    icon = labelledIcon(Items.COMPASS, "index");
                    message = "REINDEX complete - catalog rebuilt from disks";
                }
                case TerminalMaintenancePayload.ACTION_VACUUM -> {
                    final int freed = index.vacuum(level, net);
                    opType = OperationRecord.TYPE_VACUUM;
                    count = freed;
                    icon = labelledIcon(Items.HOPPER, "ghost rows");
                    message = "VACUUM freed " + freed + (freed == 1 ? " ghost entry" : " ghost entries");
                }
                default -> {
                    return;
                }
            }
            // Index maintenance is instantaneous; log it COMPLETED so the Operations tab records that it ran.
            mainframe.recordOperation(opType, icon, count, count,
                    OperationRecord.STATUS_COMPLETED, java.util.List.of());
            player.displayClientMessage(Component.literal(message), true);
            dispatchTerminalQuery(player, net, level); // the catalog may have changed — refresh the grid
        });
    }

    private static void handleTerminalDrop(final TerminalDropPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !host.isMainframeHost() || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final dev.jsc.jscomputronics.module.computing.operation.NetworkIndex index = mainframe.networkIndex();
            long destroyed = 0L;
            String label;
            StorageKey recordKey;
            switch (payload.scope()) {
                case TerminalDropPayload.SCOPE_NETWORK -> {
                    destroyed = index.dropAll(level, net);
                    label = "the network";
                    recordKey = StorageKey.of(labelledIcon(Items.TNT, "network"));
                }
                case TerminalDropPayload.SCOPE_SERVER -> {
                    if (payload.serverKey().isEmpty()) {
                        return;
                    }
                    final NodeUuid node;
                    try {
                        node = NodeUuid.fromString(payload.serverKey());
                    } catch (final IllegalArgumentException malformed) {
                        return;
                    }
                    destroyed = index.dropServer(level, node);
                    label = "a server";
                    recordKey = StorageKey.of(labelledIcon(Items.TNT, serverLabel(level, node)));
                }
                case TerminalDropPayload.SCOPE_TYPES -> {
                    for (final StorageKey key : payload.types()) {
                        destroyed += index.dropType(level, net, key, null);
                    }
                    final int n = payload.types().size();
                    label = n + (n == 1 ? " type" : " types");
                    // A single-type DROP shows that data's real icon; many types collapse to a tagged marker.
                    recordKey = n == 1 ? payload.types().get(0) : StorageKey.of(labelledIcon(Items.TNT, n + " types"));
                }
                default -> {
                    return;
                }
            }
            mainframe.recordOperation(new OperationRecord(OperationRecord.TYPE_DROP, recordKey,
                    destroyed, destroyed, OperationRecord.STATUS_COMPLETED, java.util.List.of()));
            player.displayClientMessage(Component.literal(
                    "DROP destroyed " + destroyed + " from " + label), true);
            dispatchTerminalQuery(player, net, level);
        });
    }

    private static ItemStack labelledIcon(final net.minecraft.world.item.Item item, final String label) {
        final ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(label));
        return stack;
    }

    private static MainframeBlockEntity resolveMainframe(final ServerLevel level, final NetworkUuid network) {
        final java.util.Optional<Long> pos = NetworkSystem.get(level).mainframePositionOf(network);
        if (pos.isEmpty()) {
            return null;
        }
        return level.getBlockEntity(net.minecraft.core.BlockPos.of(pos.get())) instanceof MainframeBlockEntity mf
                ? mf : null;
    }

    private static void handleSnapshot(final NetworkSnapshotPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu
                    instanceof dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu terminal) {
                terminal.setNetworkItems(payload.items());
            }
        });
    }

    public static void dispatchTerminalQuery(final ServerPlayer player, final NetworkUuid net,
                                             final ServerLevel level) {
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        if (mainframe != null) {
            mainframe.submitOperation(
                    new dev.jsc.jscomputronics.module.computing.operation.NetworkQueryOperationTask(level, net, player),
                    dev.jsc.jscomputronics.common.operation.OperationPriority.MEDIUM);
        }
    }

    private static void handleTerminalSelect(final TerminalSelectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            // Resolve where the pulled items land: the computer's own local storage (simple/auto) or
            final Dest dest = resolveDest(host, level, net, payload.destKind(), payload.destServer());
            if (dest == null) {
                return;
            }
            Set<NodeUuid> sources = payload.serverKeys().isEmpty() ? null : toNodes(payload.serverKeys());
            // A MOVE must never pull from its own destination Server: extracting and re-inserting into
            // the same store would churn items in place. Drop the target from the sources.
            if (dest.move() && dest.target() != null) {
                sources = sourcesWithout(level, net, sources, dest.target());
                if (sources.isEmpty()) {
                    return; // the only chosen source was the destination — nothing to move
                }
            }
            final StorageKey key = payload.key();
            final var op = dest.move()
                    ? mainframe.submitNetworkMove(key, payload.quantity(), dest.handler(), dest.label(), sources)
                    : mainframe.submitNetworkSelect(key, payload.quantity(), dest.handler(), dest.label(), sources);
            if (op != null) {
                op.onSettle(() -> sendSnapshot(player, level, net));
            }
        });
    }

    /**
     * A resolved SELECT destination: where the pulled items land, the provenance label, whether it is a MOVE (into another Server), and that target Server's node (so it can be excluded as a source).
     */
    private record Dest(dev.jsc.jscomputronics.module.computing.storage.DataSink handler, String label,
                        boolean move, @Nullable NodeUuid target) {
    }

    @Nullable
    private static Dest resolveDest(final ComputerTerminalHost host, final ServerLevel level,
                                    final NetworkUuid net, final int kind, final String serverKey) {
        return kind == TerminalSelectPayload.DEST_SERVER
                ? resolveComputerDest(level, net, serverKey)
                : resolveTerminalDest(host);
    }

    @Nullable
    private static Dest resolveComputerDest(final ServerLevel level, final NetworkUuid net, final String key) {
        final NodeUuid target;
        try {
            target = NodeUuid.fromString(key);
        } catch (final IllegalArgumentException malformed) {
            return null;
        }
        final MainframeBlockEntity mf = resolveMainframe(level, net);
        if (mf != null && mf.nodeUuid() != null && mf.nodeUuid().equals(target)) {
            return new Dest(new dev.jsc.jscomputronics.module.computing.storage.StoreSink(mf.localStore()),
                    "Mainframe", false, null);
        }
        // A Personal Computer on the network: a SELECT into its own local storage (leaves the network).
        for (final NetworkSystem.PersonalComputerNode pc : NetworkSystem.get(level).personalComputersOf(net)) {
            if (pc.nodeUuid().equals(target)
                    && level.getBlockEntity(BlockPos.of(pc.pos())) instanceof PersonalComputerBlockEntity pcBe) {
                return new Dest(new dev.jsc.jscomputronics.module.computing.storage.StoreSink(pcBe.localStore()),
                        pcLabel(pcBe, target), false, null);
            }
        }
        return resolveServerDest(level, net, key);
    }

    @Nullable
    private static Dest resolveTerminalDest(final ComputerTerminalHost host) {
        return host.usableStorageSlots() > 0 ? new Dest(host.localStorage(), "storage", false, null) : null;
    }

    @Nullable
    private static Dest resolveServerDest(final ServerLevel level, final NetworkUuid net, final String serverKey) {
        final NodeUuid target;
        try {
            target = NodeUuid.fromString(serverKey);
        } catch (final IllegalArgumentException malformed) {
            return null;
        }
        final NetworkSystem system = NetworkSystem.get(level);
        boolean onNetwork = false;
        for (final ServerNode server : system.serversOf(net)) {
            if (server.nodeUuid().equals(target)) {
                onNetwork = true;
                break;
            }
        }
        if (!onNetwork) {
            return null;
        }
        return system.locationOf(target)
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                        instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack
                        ? new Dest(new dev.jsc.jscomputronics.module.computing.storage.StoreSink(
                                rack.getServerStorage(loc.slot())),
                                serverLabel(level, target), true, target)
                        : null)
                .orElse(null);
    }

    private static Set<NodeUuid> sourcesWithout(final ServerLevel level, final NetworkUuid net,
                                                @Nullable final Set<NodeUuid> sources, final NodeUuid target) {
        final Set<NodeUuid> result;
        if (sources != null) {
            result = new HashSet<>(sources);
        } else {
            result = new HashSet<>();
            for (final ServerNode server : NetworkSystem.get(level).serversOf(net)) {
                result.add(server.nodeUuid());
            }
        }
        result.remove(target);
        return result;
    }

    private static void handleTerminalInsert(final TerminalInsertPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(player.containerMenu instanceof ComputerTerminalMenu menu)) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final int idx = payload.slotIndex();
            final boolean fromCursor = idx == TerminalInsertPayload.CURSOR || idx == TerminalInsertPayload.CURSOR_ONE;
            // A slot source must be a player-inventory slot, never a storage slot.
            if (!fromCursor && (idx < menu.storageSlotCount() || idx >= menu.slots.size())) {
                return;
            }
            final net.minecraft.world.inventory.Slot slot = fromCursor ? null : menu.getSlot(idx);
            final ItemStack source = fromCursor ? menu.getCarried() : slot.getItem();
            if (source.isEmpty()) {
                return;
            }
            // A fluid container (a filled bucket, etc.) deposits its FLUID into the network and leaves
            // the emptied container — fluid is data too. One container per click.
            if (depositFluidContainer(mainframe, menu, slot, fromCursor, source, player, level, net)) {
                return;
            }
            // Take the items off the source now ("in flight"); the Operation returns overflow.
            final ItemStack inFlight;
            if (idx == TerminalInsertPayload.CURSOR_ONE) {
                inFlight = source.copyWithCount(1);
                source.shrink(1);
                menu.setCarried(source.isEmpty() ? ItemStack.EMPTY : source);
            } else if (fromCursor) {
                inFlight = source.copy();
                menu.setCarried(ItemStack.EMPTY);
            } else {
                inFlight = source.copy();
                slot.set(ItemStack.EMPTY);
            }
            menu.broadcastChanges();
            // Push the held items into the network over ticks; return whatever does not fit (with its
            // original components) to the player when the Operation settles.
            final var op = mainframe.submitNetworkInsert(StorageKey.of(inFlight), inFlight.getCount(), "terminal");
            if (op == null) {
                // Error path (no live dispatcher): hand the items straight back.
                player.getInventory().placeItemBackInInventory(inFlight);
                return;
            }
            op.onSettle(() -> {
                final long leftover = op.leftover();
                if (leftover > 0L) {
                    returnToPlayer(player, inFlight.copyWithCount((int) leftover));
                }
                sendSnapshot(player, level, net);
            });
        });
    }

    private static boolean depositFluidContainer(final MainframeBlockEntity mainframe,
            final ComputerTerminalMenu menu, final net.minecraft.world.inventory.Slot slot,
            final boolean fromCursor, final ItemStack source, final ServerPlayer player,
            final ServerLevel level, final NetworkUuid network) {
        final var handler = FluidUtil.getFluidHandler(source.copyWithCount(1));
        if (handler.isEmpty()) {
            return false;
        }
        final FluidStack drained = handler.get().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return false; // an empty container is not a fluid to deposit — try the item path
        }
        // A fluid container is atomic: only deposit when the network has room for the whole amount, so
        long freeWeight = 0L;
        for (final var room : mainframe.networkIndex().freeSpace(level, network)) {
            freeWeight += room.quantity();
            if (freeWeight >= drained.getAmount()) {
                break;
            }
        }
        if (freeWeight < drained.getAmount()) {
            return true;
        }
        final var op = mainframe.submitNetworkInsert(StorageKey.of(drained), drained.getAmount(), "terminal");
        if (op == null) {
            return true; // no dispatcher: do nothing, keep the full container in hand
        }
        // Remove one container from the source and hand back the emptied one.
        source.shrink(1);
        if (fromCursor) {
            menu.setCarried(source.isEmpty() ? ItemStack.EMPTY : source);
        } else {
            slot.set(source.isEmpty() ? ItemStack.EMPTY : source);
        }
        menu.broadcastChanges();
        returnToPlayer(player, handler.get().getContainer());
        op.onSettle(() -> {
            final long leftover = op.leftover();
            if (leftover > 0L) {
                // The network could not hold it all; hand back a filled container of the remainder.
                final ItemStack refilled = FluidUtil.getFilledBucket(
                        drained.copyWithAmount((int) Math.min(leftover, Integer.MAX_VALUE)));
                if (!refilled.isEmpty()) {
                    returnToPlayer(player, refilled);
                }
            }
            sendSnapshot(player, level, network);
        });
        return true;
    }

    /**
     * The Network Interactor's hotbar-source equivalent of {@link #depositFluidContainer}: drains a fluid
     * container from a player hotbar slot into the network and returns the emptied container. No container
     * menu (the desktop is a plain Screen), so it edits the player inventory slot directly. Returns
     * {@code true} when the source IS a fluid container (handled here, do not fall through to the item path).
     */
    private static boolean niDepositFluidContainer(final MainframeBlockEntity mainframe,
            final ServerPlayer player, final int slot, final ItemStack source,
            final ServerLevel level, final NetworkUuid network) {
        final var handler = FluidUtil.getFluidHandler(source.copyWithCount(1));
        if (handler.isEmpty()) {
            return false;
        }
        final FluidStack drained = handler.get().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return false; // an empty container is not a fluid to deposit — try the item path
        }
        // A fluid container is atomic: only deposit when the network has room for the whole amount.
        long freeWeight = 0L;
        for (final var room : mainframe.networkIndex().freeSpace(level, network)) {
            freeWeight += room.quantity();
            if (freeWeight >= drained.getAmount()) {
                break;
            }
        }
        if (freeWeight < drained.getAmount()) {
            return true; // no room for the whole fluid amount; keep the full container in the slot
        }
        final var op = mainframe.submitNetworkInsert(StorageKey.of(drained), drained.getAmount(), "ni");
        if (op == null) {
            return true; // no dispatcher: keep the full container, never lose it
        }
        // Remove one container from the hotbar slot and hand back the emptied one.
        source.shrink(1);
        player.getInventory().setItem(slot, source.isEmpty() ? ItemStack.EMPTY : source);
        returnToPlayer(player, handler.get().getContainer());
        op.onSettle(() -> {
            final long leftover = op.leftover();
            if (leftover > 0L) {
                final ItemStack refilled = FluidUtil.getFilledBucket(
                        drained.copyWithAmount((int) Math.min(leftover, Integer.MAX_VALUE)));
                if (!refilled.isEmpty()) {
                    returnToPlayer(player, refilled);
                }
            }
        });
        return true;
    }

    private static void handleRequestBreakdown(final RequestServerBreakdownPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host != null && host.networkUuid() != null
                    && context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level) {
                PacketDistributor.sendToPlayer(player, collectBreakdown(level, host.networkUuid(), payload.key()));
                // The advanced-mode destination picker needs every computer that can hold items (the
                // Mainframe's local storage and every Server), not just those holding the clicked item.
                PacketDistributor.sendToPlayer(player, collectComputers(level, host.networkUuid()));
            }
        });
    }

    private static void handleServerBreakdown(final ServerBreakdownPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setServerBreakdown(payload.servers());
            }
        });
    }

    private static void handleNetworkServers(final NetworkServersPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setNetworkServers(payload.servers());
            } else {
                // The Network Interactor desktop app (no container menu of its own) consumes the same list.
                dev.jsc.jscomputronics.module.computing.client.os.NetworkInteractorApp.acceptServers(
                        payload.servers());
            }
        });
    }

    /** Sends the network's computers (Mainframe/Servers/PCs) to the open Network Interactor's advanced popup. */
    private static void handleRequestNiServers(final RequestNiServersPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            if (host == null || host.networkUuid() == null) {
                return;
            }
            PacketDistributor.sendToPlayer(player, collectComputers(level, host.networkUuid()));
        });
    }

    /**
     * The Network Interactor's advanced request: pull from chosen source Servers into a chosen destination.
     * Reuses the same dispatch as the MC-NET terminal SELECT — only the host resolution (niHost) differs.
     */
    private static void handleNiSelect(final NiSelectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || payload.quantity() <= 0L) {
                return;
            }
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            if (host == null || host.networkUuid() == null) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            // Empty destKey lands in this computer's own storage (a plain SELECT); a key targets a Server/PC (a MOVE).
            final boolean toComputer = !payload.destKey().isEmpty();
            final Dest dest = resolveDest(host, level, net,
                    toComputer ? TerminalSelectPayload.DEST_SERVER : TerminalSelectPayload.DEST_LOCAL,
                    payload.destKey());
            if (dest == null) {
                return;
            }
            Set<NodeUuid> sources = payload.serverKeys().isEmpty() ? null : toNodes(payload.serverKeys());
            if (dest.move() && dest.target() != null) {
                sources = sourcesWithout(level, net, sources, dest.target());
                if (sources.isEmpty()) {
                    return; // the only chosen source was the destination — nothing to move
                }
            }
            final long qty = Math.min(payload.quantity(), Integer.MAX_VALUE);
            final var op = dest.move()
                    ? mainframe.submitNetworkMove(payload.key(), qty, dest.handler(), dest.label(), sources)
                    : mainframe.submitNetworkSelect(payload.key(), qty, dest.handler(), dest.label(), sources);
            if (op != null && host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                    .AbstractComputerBlockEntity computer) {
                op.onSettle(() -> sendNetworkInteractor(player, level, computer));
            }
        });
    }

    private static NetworkServersPayload collectComputers(final ServerLevel level, final NetworkUuid net) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<NetworkServersPayload.ServerEntry> rows = new ArrayList<>();
        final MainframeBlockEntity mf = resolveMainframe(level, net);
        if (mf != null && mf.nodeUuid() != null && mf.localStorageCapacity() > 0L) {
            rows.add(new NetworkServersPayload.ServerEntry(
                    mf.nodeUuid().asString(), "Mainframe", mf.localStore().free()));
        }
        for (final ServerNode server : system.serversOf(net)) {
            if (rows.size() >= NetworkServersPayload.MAX) {
                break;
            }
            final NodeUuid node = server.nodeUuid();
            final long free = system.locationOf(node)
                    .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack
                            ? rack.getServerStorage(loc.slot()).free() : 0L)
                    .orElse(0L);
            rows.add(new NetworkServersPayload.ServerEntry(node.asString(), serverLabel(level, node), free));
        }
        for (final NetworkSystem.PersonalComputerNode pc : system.personalComputersOf(net)) {
            if (rows.size() >= NetworkServersPayload.MAX) {
                break;
            }
            if (level.getBlockEntity(BlockPos.of(pc.pos()))
                    instanceof PersonalComputerBlockEntity pcBe && pcBe.localStorageCapacity() > 0L) {
                rows.add(new NetworkServersPayload.ServerEntry(
                        pc.nodeUuid().asString(), pcLabel(pcBe, pc.nodeUuid()), pcBe.localStore().free()));
            }
        }
        return new NetworkServersPayload(rows);
    }

    private static NetworkServersPayload collectServers(final ServerLevel level, final NetworkUuid net) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<NetworkServersPayload.ServerEntry> rows = new ArrayList<>();
        for (final ServerNode server : system.serversOf(net)) {
            if (rows.size() >= NetworkServersPayload.MAX) {
                break;
            }
            final NodeUuid node = server.nodeUuid();
            final long free = system.locationOf(node)
                    .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack
                            ? rack.getServerStorage(loc.slot()).free() : 0L)
                    .orElse(0L);
            rows.add(new NetworkServersPayload.ServerEntry(node.asString(), serverLabel(level, node), free));
        }
        return new NetworkServersPayload(rows);
    }

    public static void dispatchNetworkServers(final ServerPlayer player, final NetworkUuid net,
                                              final ServerLevel level) {
        PacketDistributor.sendToPlayer(player, collectServers(level, net));
    }

    private static String pcLabel(final PersonalComputerBlockEntity pc, final NodeUuid node) {
        return pc.customName().isEmpty() ? "PC-" + ShortId.of(node.asString()) : pc.customName();
    }

    public static void dispatchTerminalOpsLog(final ServerPlayer player, final NetworkUuid net,
                                              final ServerLevel level) {
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        PacketDistributor.sendToPlayer(player, new OperationsLogPayload(
                mainframe != null ? mainframe.recentOperations() : List.of()));
    }

    private static void handleOpsLog(final OperationsLogPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setOperationsLog(payload.operations());
            } else {
                dev.jsc.jscomputronics.module.computing.client.os.NetworkInteractorApp
                        .acceptOps(payload.operations());
            }
        });
    }

    public static void dispatchActiveOperations(final ServerPlayer player, final NetworkUuid net,
                                                final ServerLevel level) {
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        final int[] slots = mainframe != null ? mainframe.supercomputerCraftSlots() : new int[] {0, 0};
        PacketDistributor.sendToPlayer(player, new ActiveOperationsPayload(
                mainframe != null ? mainframe.activeOperationRecords() : List.of(), slots[0], slots[1]));
    }

    private static void handleActiveOps(final ActiveOperationsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setActiveOps(payload.operations());
            } else {
                dev.jsc.jscomputronics.module.computing.client.os.NetworkInteractorApp
                        .acceptActiveOps(payload.operations(), payload.scSlotsUsed(), payload.scSlotsTotal());
            }
        });
    }

    /** The NI's Operations tab asks for the network's recent + active Operations; replies with both logs. */
    private static void handleRequestNiOperations(final RequestNiOperationsPayload payload,
                                                  final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            if (host == null || host.networkUuid() == null) {
                return;
            }
            dispatchTerminalOpsLog(player, host.networkUuid(), level);
            dispatchActiveOperations(player, host.networkUuid(), level);
        });
    }

    private static ComputerTerminalHost openTerminal(final IPayloadContext context,
                                                     final BlockPos monitorPos, final BlockPos hostPos) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof ComputerTerminalMenu menu
                && menu.monitorPos().equals(monitorPos)
                && menu.hostPos().equals(hostPos)
                && player.level().getBlockEntity(hostPos) instanceof ComputerTerminalHost host) {
            return host;
        }
        return null;
    }

    /**
     * Resolves the computer for a craft request that may come from the MC-NET terminal (its container menu) OR
     * the desktop Network Interactor (no menu — authenticated by proximity to a linked monitor). Tries the
     * terminal first, then the NI host, so the shared craft flow works from both.
     */
    private static ComputerTerminalHost craftHost(final IPayloadContext context, final BlockPos monitorPos,
                                                   final BlockPos hostPos) {
        final ComputerTerminalHost terminal = openTerminal(context, monitorPos, hostPos);
        if (terminal != null) {
            return terminal;
        }
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            return niHost(player, level, hostPos, monitorPos);
        }
        return null;
    }

    private static ServerBreakdownPayload collectBreakdown(final ServerLevel level, final NetworkUuid net,
                                                           final StorageKey key) {
        final Map<NodeUuid, Long> perServer = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                .of(level, net).breakdown(key);
        final List<ServerBreakdownPayload.ServerHolding> rows = new ArrayList<>();
        for (final Map.Entry<NodeUuid, Long> e : perServer.entrySet()) {
            if (rows.size() >= ServerBreakdownPayload.MAX) {
                break;
            }
            rows.add(new ServerBreakdownPayload.ServerHolding(
                    e.getKey().asString(), serverLabel(level, e.getKey()), e.getValue()));
        }
        return new ServerBreakdownPayload(rows);
    }

    public static String serverLabel(final ServerLevel level, final NodeUuid node) {
        final String fallback = "SRV-" + ShortId.of(node.asString());
        return dev.jsc.jscomputronics.common.network.NetworkSystem.get(level).locationOf(node)
                .map(loc -> level.getBlockEntity(net.minecraft.core.BlockPos.of(loc.rackPos()))
                        instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack
                        ? rack.getServers().getStackInSlot(loc.slot()) : ItemStack.EMPTY)
                .map(dev.jsc.jscomputronics.module.computing.item.ServerItem::customName)
                .filter(name -> !name.isEmpty())
                .orElse(fallback);
    }

    private static Set<NodeUuid> toNodes(final List<String> keys) {
        final Set<NodeUuid> nodes = new HashSet<>();
        for (final String key : keys) {
            try {
                nodes.add(NodeUuid.fromString(key));
            } catch (final IllegalArgumentException ignored) {
                // skip a malformed key rather than fail the whole request
            }
        }
        return nodes;
    }

    private static void handleRequestNodes(final RequestNetworkNodesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof MainframeMenu menu
                    && menu.blockPos().equals(payload.mainframePos())
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.mainframePos()) instanceof MainframeBlockEntity mf) {
                PacketDistributor.sendToPlayer(player, collectNodes(level, mf));
            }
        });
    }

    private static void handleNodes(final NetworkNodesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.NetworkOverviewScreen.open(payload));
    }

    private static NetworkNodesPayload collectNodes(final ServerLevel level, final MainframeBlockEntity mf) {
        final UnitFormatter fmt = UnitFormatter.forCurrentLocale();
        final List<NetworkNodeInfo> nodes = new ArrayList<>();
        final NetworkUuid net = mf.networkUuid();

        nodes.add(new NetworkNodeInfo(NetworkNodeInfo.KIND_MAINFRAME,
                ShortId.of(mf.nodeUuid().asString()),
                fmt.compact(mf.capacity(), Unit.IT_PER_TICK),
                net != null));

        if (net != null) {
            final NetworkSystem system = NetworkSystem.get(level);
            for (final ServerNode server : system.serversOf(net)) {
                if (nodes.size() >= NetworkNodesPayload.MAX_NODES) {
                    break;
                }
                nodes.add(new NetworkNodeInfo(NetworkNodeInfo.KIND_SERVER,
                        ShortId.of(server.nodeUuid().asString()),
                        fmt.compact(server.storageMB(), Unit.MB), true));
            }
            for (final SubframeNode subframe : system.subframesOf(net)) {
                if (nodes.size() >= NetworkNodesPayload.MAX_NODES) {
                    break;
                }
                nodes.add(new NetworkNodeInfo(NetworkNodeInfo.KIND_SUBFRAME,
                        ShortId.of(subframe.nodeUuid().asString()),
                        fmt.compact(subframe.contributedCapacity(), Unit.IT_PER_TICK), true));
            }
            for (final NetworkSystem.PersonalComputerNode pc : system.personalComputersOf(net)) {
                if (nodes.size() >= NetworkNodesPayload.MAX_NODES) {
                    break;
                }
                nodes.add(new NetworkNodeInfo(NetworkNodeInfo.KIND_PC,
                        ShortId.of(pc.nodeUuid().asString()),
                        fmt.compact(pc.capacity(), Unit.IT_PER_TICK), true));
            }
        }
        return new NetworkNodesPayload(net != null ? ShortId.of(net.asString()) : "", nodes);
    }

    public static void sendSnapshot(final ServerPlayer player, final ServerLevel level, final NetworkUuid network) {
        if (player == null || player.isRemoved()) {
            return; // no one to send to (e.g. the requester logged out before the Operation settled)
        }
        final Map<StorageKey, Long> totals = network == null
                ? Map.of()
                : dev.jsc.jscomputronics.module.computing.operation.NetworkStorage.of(level, network).query();
        final List<NetworkItemEntry> entries = new ArrayList<>(Math.min(totals.size(),
                NetworkSnapshotPayload.MAX_ENTRIES));
        // Bounded by the wire cap so encoding never overflows the StreamCodec. The entry carries the
        // full stack (components and all), so the terminal shows the enchanted item, not a bare one.
        totals.entrySet().stream().limit(NetworkSnapshotPayload.MAX_ENTRIES)
                .forEach(e -> entries.add(new NetworkItemEntry(e.getKey(), e.getValue())));
        PacketDistributor.sendToPlayer(player, new NetworkSnapshotPayload(entries));
    }

    private static void handleRequestNetworkInteractor(final RequestNetworkInteractorPayload payload,
                                                       final IPayloadContext context) {
        context.enqueueWork(() -> {
            // Proximity + monitor-link gated, like the mutating handlers — the snapshot leaks the whole
            // network's contents, so a player must be at a monitor actually linked to this host.
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && niHost(player, level, payload.host(), payload.monitorPos()) != null
                    && level.getBlockEntity(payload.host())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .AbstractComputerBlockEntity computer) {
                sendNetworkInteractor(player, level, computer);
            }
        });
    }

    /** Builds and sends a fresh Network Interactor snapshot (network grid, local grid, status, craft catalog). */
    private static void sendNetworkInteractor(final ServerPlayer player, final ServerLevel level,
            final dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity computer) {
        final dev.jsc.jscomputronics.common.uuid.NetworkUuid network = computer.networkUuid();
        // The whole network's items (Network Storage tab).
        final List<NetworkItemEntry> networkItems = new ArrayList<>();
        long usedItems = 0L;
        if (network != null) {
            final dev.jsc.jscomputronics.common.network.NetworkSystem system =
                    dev.jsc.jscomputronics.common.network.NetworkSystem.get(level);
            final dev.jsc.jscomputronics.module.computing.operation.NetworkStorage storage =
                    dev.jsc.jscomputronics.module.computing.operation.NetworkStorage.of(level, network);
            final Map<dev.jsc.jscomputronics.module.computing.storage.StorageKey, Long> totals = storage.query();
            for (final var e : totals.entrySet()) {
                if (networkItems.size() >= NetworkInteractorPayload.MAX_ENTRIES) {
                    break;
                }
                // Where this type lives, for the details panel: one share per server/storage that holds it.
                final List<NetworkItemEntry.StorageShare> shares = new ArrayList<>();
                for (final var s : storage.breakdown(e.getKey()).entrySet()) {
                    if (shares.size() >= NetworkItemEntry.MAX_SHARES) {
                        break;
                    }
                    shares.add(new NetworkItemEntry.StorageShare(serverLabel(system, s.getKey()), s.getValue()));
                }
                networkItems.add(new NetworkItemEntry(e.getKey(), e.getValue(), shares));
                usedItems += e.getValue();
            }
        }
        // This computer's own disks (Local Storage tab).
        final List<NetworkItemEntry> localItems = new ArrayList<>();
        int serverCount = 0;
        if (computer instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host) {
            for (final var e : host.localStore().view().entrySet()) {
                if (localItems.size() >= NetworkInteractorPayload.MAX_ENTRIES) {
                    break;
                }
                localItems.add(new NetworkItemEntry(e.getKey(), e.getValue()));
            }
            serverCount = host.networkServerCount();
        }
        final boolean online = network != null && resolveMainframe(level, network) != null;
        final List<CraftCatalogPayload.Entry> crafts = buildCraftCatalog(level, network);
        PacketDistributor.sendToPlayer(player, new NetworkInteractorPayload(
                networkItems, localItems, online, usedItems, serverCount, crafts));
    }

    /** A human label for a storage node in the details panel's per-server breakdown — a server's rack position
     *  and slot, or a generic label for a published Personal Computer (which has no rack location). */
    private static String serverLabel(final dev.jsc.jscomputronics.common.network.NetworkSystem system,
                                      final dev.jsc.jscomputronics.common.uuid.NodeUuid node) {
        return system.locationOf(node)
                .map(loc -> {
                    final net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.of(loc.rackPos());
                    return "Server " + p.getX() + ", " + p.getY() + ", " + p.getZ() + " #" + (loc.slot() + 1);
                })
                .orElse("Published PC");
    }

    /**
     * Resolves the host computer for a desktop Network Interactor action, validating the player is within
     * reach of the monitor (the desktop is a client-only Screen with no server menu to authenticate against).
     */
    private static dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost niHost(
            final ServerPlayer player, final ServerLevel level, final BlockPos hostPos, final BlockPos monitorPos) {
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(monitorPos)) > 64.0) {
            return null;
        }
        if (!(level.getBlockEntity(hostPos)
                instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host)) {
            return null;
        }
        // Anti-spoof: the monitor must actually be a linked peripheral of this host, so a player near any
        // monitor cannot drive a foreign computer by sending that computer's position as the host.
        if (!(host instanceof dev.jsc.jscomputronics.common.peripheral.PeripheralOwner owner)
                || !owner.linkedEndpoints().contains(monitorPos.asLong())) {
            return null;
        }
        return host;
    }

    private static void handleSetCraftingSwitchFace(final SetCraftingSwitchFacePayload payload,
                                                    final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final net.minecraft.core.BlockPos pos = payload.switchPos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                return; // out of reach
            }
            if (level.getBlockEntity(pos) instanceof dev.jsc.jscomputronics.module.computing.blockentity
                    .CraftingSwitchBlockEntity sw) {
                final net.minecraft.core.Direction face =
                        net.minecraft.core.Direction.from3DDataValue(payload.face());
                sw.setFaceName(face, payload.name());
                sw.setFaceActive(face, payload.active());
                sw.setFaceCategory(face, payload.category());
                final net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
                level.sendBlockUpdated(pos, st, st, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        });
    }

    private static void handleNiShiftInsert(final NiShiftInsertPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            if (host == null) {
                return;
            }
            final int slot = payload.slot();
            final ItemStack src = player.getInventory().getItem(slot);
            if (src.isEmpty()) {
                return;
            }
            final StorageKey key = StorageKey.of(src);
            final int amount = src.getCount();
            if (payload.target() == NiShiftInsertPayload.TARGET_STORAGE) {
                final long stored = host.localStore().insert(key, amount);
                if (stored <= 0L) {
                    return;
                }
                src.shrink((int) stored);
                player.getInventory().setItem(slot, src.isEmpty() ? ItemStack.EMPTY : src);
                player.containerMenu.broadcastChanges();
                if (host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                        .AbstractComputerBlockEntity computer) {
                    sendNetworkInteractor(player, level, computer);
                }
                return;
            }
            // Network: push the stack into the network over ticks, returning any overflow to the player.
            if (host.networkUuid() == null) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                return;
            }
            final ItemStack inFlight = src.copyWithCount(amount);
            src.shrink(amount);
            player.getInventory().setItem(slot, src.isEmpty() ? ItemStack.EMPTY : src);
            player.containerMenu.broadcastChanges();
            final var op = mainframe.submitNetworkInsert(key, amount, "ni");
            if (op == null) {
                player.getInventory().placeItemBackInInventory(inFlight);
                return;
            }
            op.onSettle(() -> {
                final long leftover = op.leftover();
                if (leftover > 0L) {
                    returnToPlayer(player, inFlight.copyWithCount((int) leftover));
                }
                if (host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                        .AbstractComputerBlockEntity computer) {
                    sendNetworkInteractor(player, level, computer);
                }
            });
        });
    }

    private static void handleNiGridClick(final NiGridClickPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            if (host == null || host.networkUuid() == null || payload.amount() <= 0L) {
                return;
            }
            // Clamp the client-supplied amount so a spoofed packet cannot ask the dispatcher for Long.MAX.
            final long safeAmount = Math.min(payload.amount(), Integer.MAX_VALUE);
            final StorageKey key = payload.key();
            if (payload.mode() == NiGridClickPayload.MODE_NET_TO_LOCAL) {
                // Pull from the network into this computer's local storage, exactly like the terminal SELECT.
                final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
                if (mainframe == null) {
                    return;
                }
                final var op = mainframe.submitNetworkSelect(key, safeAmount, host.localStorage(), "ni");
                if (op != null && host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                        .AbstractComputerBlockEntity computer) {
                    op.onSettle(() -> sendNetworkInteractor(player, level, computer));
                }
            } else if (payload.mode() == NiGridClickPayload.MODE_LOCAL_TO_NET) {
                // Upload from this computer's local storage into the network (Storage popup "TO NETWORK").
                final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
                if (mainframe == null) {
                    return;
                }
                final long taken = host.localStore().extract(key,
                        Math.min(safeAmount, host.localStore().count(key)));
                if (taken <= 0L) {
                    return;
                }
                final var op = mainframe.submitNetworkInsert(key, taken, "ni");
                if (op == null) {
                    host.localStore().insert(key, taken); // no live dispatcher: put it straight back
                    return;
                }
                op.onSettle(() -> {
                    final long leftover = op.leftover();
                    if (leftover > 0L) {
                        host.localStore().insert(key, leftover);
                    }
                    if (host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                            .AbstractComputerBlockEntity computer) {
                        sendNetworkInteractor(player, level, computer);
                    }
                });
            } else if (key.isFluid()) {
                return; // a fluid cannot be held in the inventory
            } else {
                // Withdraw from local storage into the player's inventory (terminal Storage-tab withdraw).
                final int maxStack = Math.max(1, key.stack(1).getMaxStackSize());
                final long want = Math.min(safeAmount, host.localStore().count(key));
                final long toWithdraw = Math.min(want, inventoryRoomFor(player, key, maxStack));
                if (toWithdraw > 0L) {
                    long remaining = host.localStore().extract(key, toWithdraw);
                    while (remaining > 0L) {
                        final int batch = (int) Math.min(remaining, maxStack);
                        final ItemStack out = key.stack(batch);
                        player.getInventory().add(out);
                        final int placed = batch - out.getCount();
                        remaining -= placed;
                        if (placed <= 0) {
                            break;
                        }
                    }
                    if (remaining > 0L) {
                        host.localStore().insert(key, remaining);
                    }
                }
                if (host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                        .AbstractComputerBlockEntity computer) {
                    sendNetworkInteractor(player, level, computer);
                }
            }
        });
    }

    /**
     * Deposits the player's held cursor stack into the network (Network tab) or the host's local
     * storage (Storage tab), the desktop equivalent of the MC-NET terminal's deposit. A left-click
     * pushes the whole stack; a right-click pushes one. Whatever does not fit is returned.
     */
    private static void handleNiDeposit(final NiDepositPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            if (host == null) {
                return;
            }
            final ItemStack cursor = player.containerMenu.getCarried();
            if (cursor.isEmpty()) {
                return;
            }
            final int amount = payload.whole() ? cursor.getCount() : 1;
            final StorageKey key = StorageKey.of(cursor);
            if (payload.target() == NiDepositPayload.TARGET_STORAGE) {
                final long stored = host.localStore().insert(key, amount);
                if (stored <= 0L) {
                    return;
                }
                cursor.shrink((int) stored);
                player.containerMenu.setCarried(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
                player.containerMenu.broadcastChanges();
                if (host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                        .AbstractComputerBlockEntity computer) {
                    sendNetworkInteractor(player, level, computer);
                }
                return;
            }
            // Network deposit: push the held items into the network over ticks, returning overflow.
            if (host.networkUuid() == null) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                return;
            }
            final ItemStack inFlight = cursor.copyWithCount(amount);
            cursor.shrink(amount);
            player.containerMenu.setCarried(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
            player.containerMenu.broadcastChanges();
            final var op = mainframe.submitNetworkInsert(key, amount, "ni");
            if (op == null) {
                player.getInventory().placeItemBackInInventory(inFlight);
                return;
            }
            op.onSettle(() -> {
                final long leftover = op.leftover();
                if (leftover > 0L) {
                    returnToPlayer(player, inFlight.copyWithCount((int) leftover));
                }
                if (host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                        .AbstractComputerBlockEntity computer) {
                    sendNetworkInteractor(player, level, computer);
                }
            });
        });
    }

    private static void handleNiHotbarClick(final NiHotbarClickPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            // 0-35 = the player's main inventory + hotbar (not armor/offhand).
            if (host == null || payload.slot() < 0 || payload.slot() >= 36) {
                return;
            }
            final ItemStack src = player.getInventory().getItem(payload.slot());
            if (src.isEmpty()) {
                return;
            }
            if (payload.mode() == NiHotbarClickPayload.MODE_INV_TO_NET) {
                // Push the whole hotbar stack into the network; leftover the network cannot hold comes back.
                if (host.networkUuid() == null) {
                    return;
                }
                final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
                if (mainframe == null) {
                    return;
                }
                final dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity computer =
                        host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                .AbstractComputerBlockEntity c ? c : null;
                // A fluid container deposits its fluid into the network (fluid is data too), like the terminal.
                if (niDepositFluidContainer(mainframe, player, payload.slot(), src, level, host.networkUuid())) {
                    if (computer != null) {
                        sendNetworkInteractor(player, level, computer);
                    }
                    return;
                }
                final ItemStack taken = src.copy();
                final StorageKey key = StorageKey.of(taken);
                player.getInventory().setItem(payload.slot(), ItemStack.EMPTY);
                final var op = mainframe.submitNetworkInsert(key, taken.getCount(), "ni");
                if (op == null) {
                    player.getInventory().setItem(payload.slot(), taken); // no dispatcher: never lose it
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "The network Mainframe needs an OS installed to accept items."), true);
                    return;
                }
                op.onSettle(() -> {
                    final long leftover = op.leftover();
                    if (leftover > 0L) {
                        // Return the exact leftover from the captured stack, preserving its components.
                        returnToPlayer(player, taken.copyWithCount((int) Math.min(leftover, taken.getCount())));
                    }
                    if (computer != null) {
                        sendNetworkInteractor(player, level, computer);
                    }
                });
            } else {
                // Deposit the hotbar stack into this computer's local storage.
                final long inserted = host.localStore().insert(StorageKey.of(src), src.getCount());
                if (inserted > 0L) {
                    src.shrink((int) inserted);
                    player.getInventory().setItem(payload.slot(), src.isEmpty() ? ItemStack.EMPTY : src);
                }
                if (host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                        .AbstractComputerBlockEntity computer) {
                    sendNetworkInteractor(player, level, computer);
                }
            }
        });
    }

    private static void handleNiCraft(final NiCraftPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            if (host == null || host.networkUuid() == null || payload.amount() <= 0L
                    || payload.result().isEmpty()) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                return;
            }
            // Clamp the client-supplied quantity so a spoofed packet cannot ask the dispatcher for Long.MAX.
            final long safeAmount = Math.max(1L, Math.min(payload.amount(), Integer.MAX_VALUE));
            final dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity computer =
                    host instanceof dev.jsc.jscomputronics.module.computing.blockentity
                            .AbstractComputerBlockEntity c ? c : null;
            final Runnable refreshNi = () -> {
                if (computer != null) {
                    sendNetworkInteractor(player, level, computer);
                }
            };
            if (submitMachineCraft(mainframe, StorageKey.of(payload.result()), safeAmount, "ni", refreshNi) != null) {
                refreshNi.run();
                return;
            }
            final var op = mainframe.submitNetworkCraft(StorageKey.of(payload.result()), safeAmount, true, "ni");
            if (op != null && computer != null) {
                op.onSettle(() -> sendNetworkInteractor(player, level, computer));
            }
            if (computer != null) {
                sendNetworkInteractor(player, level, computer);
            }
        });
    }

    private static void handleNetworkInteractor(final NetworkInteractorPayload payload,
                                                final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.os.NetworkInteractorApp.accept(payload));
    }

    // Local storage (the Storage tab) — disk-backed, component-preserving quantity view.

    private static void handleLocalSnapshot(final LocalStorageSnapshotPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setLocalItems(payload.items());
                menu.setDiskPrivacy(payload.disks());
            }
        });
    }

    public static void dispatchLocalSnapshot(final ServerPlayer player, final ComputerTerminalHost host) {
        final Map<StorageKey, Long> view = host.localStore().view();
        final List<NetworkItemEntry> entries = new ArrayList<>(
                Math.min(view.size(), LocalStorageSnapshotPayload.MAX_ENTRIES));
        view.entrySet().stream().limit(LocalStorageSnapshotPayload.MAX_ENTRIES)
                .forEach(e -> entries.add(new NetworkItemEntry(e.getKey(), e.getValue())));
        // Per-disk privacy state for the Storage tab's slider; empty for a host with no slider, which
        // makes the Storage tab show the static "always public" badge instead of a control.
        final List<LocalStorageSnapshotPayload.DiskInfo> disks = new ArrayList<>();
        if (host.storageHasSlider()) {
            final int count = Math.min(host.diskPrivacyDiskCount(), LocalStorageSnapshotPayload.MAX_DISKS);
            for (int i = 0; i < count; i++) {
                disks.add(new LocalStorageSnapshotPayload.DiskInfo(
                        host.diskPrivacyPermille(i), host.diskUsedWeight(i), host.diskCapacityWeight(i)));
            }
        }
        PacketDistributor.sendToPlayer(player, new LocalStorageSnapshotPayload(entries, disks));
    }

    private static void handleLocalWithdraw(final TerminalLocalWithdrawPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !(context.player() instanceof ServerPlayer player)
                    || payload.quantity() <= 0L) {
                return;
            }
            final StorageKey key = payload.key();
            if (key.isFluid()) {
                return; // a fluid cannot be held in the inventory — withdraw it via an Export Bus
            }
            final int maxStack = Math.max(1, key.stack(1).getMaxStackSize());
            // Take only as much as the player's inventory can actually hold, so a "withdraw all" on a
            // huge stack never extracts more than fits — items must never be destroyed by overflow.
            final long want = Math.min(payload.quantity(), host.localStore().count(key));
            final long toWithdraw = Math.min(want, inventoryRoomFor(player, key, maxStack));
            if (toWithdraw <= 0L) {
                return;
            }
            long remaining = host.localStore().extract(key, toWithdraw);
            while (remaining > 0L) {
                final int batch = (int) Math.min(remaining, maxStack);
                final ItemStack out = key.stack(batch);
                player.getInventory().add(out); // mutates out to whatever did not fit
                final int placed = batch - out.getCount();
                remaining -= placed;
                if (placed <= 0) {
                    break; // inventory unexpectedly full — return the remainder below
                }
            }
            if (remaining > 0L) {
                host.localStore().insert(key, remaining); // belt-and-braces: never lose the remainder
            }
            dispatchLocalSnapshot(player, host);
        });
    }

    private static void handleDiskPrivacy(final TerminalDiskPrivacyPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // A Server or the Mainframe is always fully public — it carries no slider, so a privacy
            // write to one is a stale or spoofed packet. Warn lightly and ignore it.
            if (!host.storageHasSlider()
                    || !(host instanceof PersonalComputerBlockEntity pc)) {
                JsComputronics.LOGGER.warn("Ignoring disk-privacy write to a host without a storage slider at {}",
                        payload.hostPos());
                return;
            }
            if (payload.diskIndex() < 0 || payload.diskIndex() >= pc.diskPrivacyDiskCount()) {
                return;
            }
            // Whitelist + clamp: only a value inside the valid per-mille range is ever applied.
            final int permille = dev.jsc.jscomputronics.module.computing.storage.DiskPrivacy
                    .clampPermille(payload.permille());
            pc.setDiskPrivacy(payload.diskIndex(), permille); // a no-op + no counter bump if the slot has no disk
            // Refresh the owner's Storage tab so the readout reflects the authoritative value.
            dispatchLocalSnapshot(player, host);
        });
    }

    private static long inventoryRoomFor(final ServerPlayer player, final StorageKey key, final int maxStack) {
        final ItemStack probe = key.stack(1);
        final net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        long room = 0L;
        for (int i = 0; i < inv.items.size(); i++) {
            final ItemStack slot = inv.items.get(i);
            if (slot.isEmpty()) {
                room += maxStack;
            } else if (ItemStack.isSameItemSameComponents(slot, probe)) {
                room += Math.max(0, maxStack - slot.getCount());
            }
        }
        return room;
    }

    // OS install flow — client asks the server to scan linked media readers and install the OS.

    private static void handleInstallOs(final InstallOsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            installOsFromLinkedReader(level, payload.computerPos());
        });
    }

    /**
     * Scans the computer's linked peripheral endpoints for a {@link MediaReaderBlockEntity}
     * holding an OS installer medium. Takes the first match whose OS passes the era gate and
     * whose footprint fits the computer's free storage, then calls
     * {@link AbstractComputerBlockEntity#installOs(net.minecraft.resources.ResourceLocation)}.
     *
     * <p>The reader must be linked to the computer over the COMPUTING peripheral cable system
     * (same way a monitor links). Only readers that are already auto-linked endpoints are
     * considered; a reader placed in the world but not yet linked on the peripheral system
     * will not be found here.
     *
     * <p>All gating conditions must be satisfied in order:
     * <ol>
     *   <li>The computer block entity must be an {@link AbstractComputerBlockEntity} with no OS yet.</li>
     *   <li>A linked endpoint must resolve to a {@link MediaReaderBlockEntity} holding a medium
     *       of kind {@link MediaKind#OS_INSTALL} whose payload names a registered {@link OsDef}.</li>
     *   <li>{@link OsGating#canInstall} must accept the OS on the computer's hardware era.</li>
     * </ol>
     * A silent no-op is the correct outcome when any condition is unmet; the firmware screen will
     * remain open and the player can fix the configuration before trying again.
     *
     * @param level       the server level the computer lives in
     * @param computerPos the position of the computer to install the OS onto
     */
    public static void installOsFromLinkedReader(final ServerLevel level, final BlockPos computerPos) {
        if (!(level.getBlockEntity(computerPos) instanceof AbstractComputerBlockEntity computer)) {
            return;
        }
        if (computer.hasOs()) {
            return; // already installed; nothing to do
        }
        final HardwareEra hostEra = computer.installedEra() != null
                ? computer.installedEra()
                : HardwareEra.STANDARD;

        // Walk every linked peripheral endpoint and look for a media reader with an OS installer.
        for (final long endpointLong : computer.linkedEndpoints()) {
            final BlockPos endpointPos = BlockPos.of(endpointLong);
            if (!(level.getBlockEntity(endpointPos) instanceof MediaReaderBlockEntity reader)) {
                continue;
            }
            if (reader.insertedKind() != MediaKind.OS_INSTALL) {
                continue;
            }
            final net.minecraft.resources.ResourceLocation osId = reader.insertedPayload();
            if (osId == null) {
                continue;
            }
            final OsDef def = OsRegistry.getOs(osId);
            if (def == null) {
                continue;
            }
            if (!OsGating.canInstall(def.minEra(), hostEra)) {
                continue;
            }
            // installOs checks the footprint against free storage; false means it did not fit.
            computer.installOs(osId);
            return; // first valid linked reader wins
        }
    }

    /** Delivers the {@link CraftFileListPayload} to the open Pattern Encoder's screen. */
    private static void handleCraftFileList(final CraftFileListPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof PatternEncoderMenu menu) {
                menu.setCraftFiles(payload.files());
            }
        });
    }

    // ---- Pattern Encoder media file listing ----

    /**
     * Lists the {@code .craft} files on the removable medium in the Pattern Encoder's media slot.
     * Responds with a {@link CraftFileListPayload} so the open screen can show what is already
     * written on the medium.
     */
    private static void handleRequestPatternEncoderFiles(final RequestPatternEncoderFilesPayload payload,
                                                          final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof PatternEncoderMenu menu)
                    || !menu.blockEntityPos().equals(payload.encoderPos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.encoderPos()) instanceof PatternEncoderBlockEntity be)) {
                return;
            }
            final ItemStack mediaStack = be.media().getStackInSlot(0);
            if (mediaStack.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new CraftFileListPayload(List.of()));
                return;
            }
            PacketDistributor.sendToPlayer(player,
                    craftFileListFromMedia(mediaStack));
        });
    }

    /**
     * Applies one PROCESSING / MULTI-STAGE authoring edit to the Pattern Encoder. The server is authoritative: it
     * validates the open menu and position, then mutates the block entity (which syncs itself with a fresh update
     * tag). A successful write also refreshes the medium's {@code .craft} file list back to the client.
     */
    private static void handlePatternEncoderEdit(final PatternEncoderEditPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof PatternEncoderMenu menu)
                    || !menu.blockEntityPos().equals(payload.pos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.pos()) instanceof PatternEncoderBlockEntity be)) {
                return;
            }
            final ItemStack carried = menu.getCarried();
            boolean wrote = false;
            switch (payload.action()) {
                case PatternEncoderEditPayload.ACTION_SET_TAB -> menu.setActiveTab(payload.value());
                case PatternEncoderEditPayload.ACTION_SET_INPUT -> be.setProcInput(payload.index(), carried);
                case PatternEncoderEditPayload.ACTION_SET_OUTPUT -> be.setProcOutput(payload.index(), carried);
                case PatternEncoderEditPayload.ACTION_SET_CHANCE ->
                        be.setOutputChance(payload.index(), payload.value());
                case PatternEncoderEditPayload.ACTION_SET_MACHINE -> be.setMachineType(payload.text());
                case PatternEncoderEditPayload.ACTION_SET_TIMEOUT -> be.setProcTimeout(payload.value());
                case PatternEncoderEditPayload.ACTION_WRITE_PROC -> wrote = be.writeProcessingPattern();
                case PatternEncoderEditPayload.ACTION_WRITE_MULTI -> wrote = be.writeMultiStagePattern();
                case PatternEncoderEditPayload.ACTION_ADD_STAGE_BENCH -> be.addBenchStage();
                case PatternEncoderEditPayload.ACTION_ADD_STAGE_PROC -> be.addProcessingStage();
                case PatternEncoderEditPayload.ACTION_REMOVE_STAGE -> be.removeStage(payload.index());
                case PatternEncoderEditPayload.ACTION_CLEAR_STAGES -> be.clearStages();
                case PatternEncoderEditPayload.ACTION_CLEAR_PROC -> be.clearProcessing();
                case PatternEncoderEditPayload.ACTION_ADD_STAGE_FROM_MEDIA ->
                        be.addStageFromMedia(payload.text());
                default -> {
                    return;
                }
            }
            if (wrote) {
                final ItemStack mediaStack = be.media().getStackInSlot(0);
                PacketDistributor.sendToPlayer(player, mediaStack.isEmpty()
                        ? new CraftFileListPayload(List.of()) : craftFileListFromMedia(mediaStack));
            }
        });
    }

    /** Builds a {@link CraftFileListPayload} listing all {@code .craft} files on the given medium. */
    private static CraftFileListPayload craftFileListFromMedia(final ItemStack media) {
        final List<DiskFilesystem.FileEntry> entries =
                DiskFilesystem.list(media, "", FilesystemKind.HIERARCHICAL);
        final List<String> names = new ArrayList<>();
        for (final DiskFilesystem.FileEntry e : entries) {
            if (e.type() == FileType.CRAFT && names.size() < CraftFileListPayload.MAX_FILES) {
                names.add(e.path());
            }
        }
        return new CraftFileListPayload(names);
    }

    // ---- Crafting Manager (B2) — media ↔ ROM transfer ----

    /** The Machines tab sets a machine's concurrency config on a Crafting Computer, then gets a fresh state. */
    private static void handleSetMachineConfig(final SetMachineConfigPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos()) instanceof CraftingComputerBlockEntity cc)) {
                return;
            }
            cc.setMachineConfig(payload.machineKey(), new CraftingComputerBlockEntity.MachineConfig(
                    payload.maxJobs(), payload.locked(), payload.feedMax()));
            PacketDistributor.sendToPlayer(player, buildCraftManagerState(cc, level));
        });
    }

    /**
     * Returns the Crafting Manager state for a Crafting Computer: the first linked drive's medium
     * and its {@code .craft} files plus the computer's Recipe ROM with cross-reference flags.
     */
    private static void handleRequestCraftManager(final RequestCraftManagerPayload payload,
                                                   final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos()) instanceof CraftingComputerBlockEntity cc)) {
                return;
            }
            // Self-heal the crafts/ mirror against the ROM before presenting the state.
            reconcileCraftsFolder(cc, level);
            PacketDistributor.sendToPlayer(player, buildCraftManagerState(cc, level));
        });
    }

    /** Routes the Crafting Manager state payload to the open {@link dev.jsc.jscomputronics.module.computing.client.os.CraftingManagerApp}. */
    private static void handleCraftManagerState(final CraftManagerStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.os.CraftingManagerApp.accept(payload));
    }

    /**
     * Loads {@code .craft} files from a removable medium into the Crafting Computer's Recipe ROM.
     * When {@code allMissing} is set, every file not already covered by a ROM pattern is loaded.
     */
    private static void handleLoadFromMedia(final LoadFromMediaPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos()) instanceof CraftingComputerBlockEntity cc)) {
                return;
            }
            final String key = payload.mediaVolumeKey();
            if (!key.startsWith("media:")) {
                return;
            }
            final ItemStack media = mediaStackFor(level, cc, key);
            if (media.isEmpty()) {
                return;
            }
            final List<String> toLoad;
            if (payload.allMissing()) {
                // Collect every .craft file on the medium that is not already in the ROM.
                final Set<String> romNames = new HashSet<>();
                for (final CraftingPattern p : cc.romPatterns()) {
                    romNames.add(craftFileNameFor(p.result()) + ".craft");
                }
                final List<DiskFilesystem.FileEntry> entries =
                        DiskFilesystem.list(media, "", FilesystemKind.HIERARCHICAL);
                toLoad = new ArrayList<>();
                for (final DiskFilesystem.FileEntry e : entries) {
                    if (e.type() == FileType.CRAFT && !romNames.contains(e.path())) {
                        toLoad.add(e.path());
                    }
                }
            } else {
                toLoad = payload.fileNames();
            }
            int loaded = 0;
            int parsed = 0;
            boolean romFull = false;
            for (final String fileName : toLoad) {
                final java.util.Optional<String> content = DiskFilesystem.read(media, fileName);
                if (content.isEmpty()) {
                    continue;
                }
                final String kind = CraftFile.typeOf(content.get());
                if (cc.romUsed() >= CraftingComputerBlockEntity.RECIPE_ROM_LIMIT) {
                    romFull = true; // bench, processing and multi-stage all share the one ROM budget
                    continue;
                }
                boolean added = false;
                String diskName = fileName;
                if ("proc".equals(kind)) {
                    final var p = CraftFile.parseProcessing(content.get(), level.registryAccess());
                    if (p.isEmpty()) {
                        continue;
                    }
                    parsed++;
                    added = cc.loadMachineRecipe(
                            dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe.ofProcessing(p.get()));
                } else if ("multi".equals(kind)) {
                    final var p = CraftFile.parseMultiStage(content.get(), level.registryAccess());
                    if (p.isEmpty()) {
                        continue;
                    }
                    parsed++;
                    added = cc.loadMachineRecipe(
                            dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe.ofMultiStage(p.get()));
                } else {
                    final var p = CraftFile.parse(content.get(), level.registryAccess());
                    if (p.isEmpty()) {
                        continue;
                    }
                    parsed++;
                    added = cc.loadPattern(p.get());
                    diskName = craftFileNameFor(p.get().result()) + ".craft";
                }
                // The recipe registers in the ROM (what the network can craft) and the .craft is mirrored under
                // crafts/ on the system disk so it shows up in the Files app.
                if (added) {
                    loaded++;
                }
                writeCraftToDisk(cc, diskName, content.get());
            }
            cc.setChanged();
            final String status;
            if (romFull) {
                status = "Loaded " + loaded + " of " + parsed + " - ROM full ("
                        + cc.romUsed() + "/" + CraftingComputerBlockEntity.RECIPE_ROM_LIMIT + ")";
            } else if (loaded > 0) {
                status = "Loaded " + loaded + " craft" + (loaded == 1 ? "" : "s");
            } else {
                status = parsed > 0 ? "Already loaded" : "Nothing to load";
            }
            PacketDistributor.sendToPlayer(player, buildCraftManagerState(cc, level, status));
        });
    }

    /**
     * Removes the selected Recipe ROM patterns from the Crafting Computer, deleting each mirrored
     * {@code .craft} file under {@code crafts/} on the system disk as well. Indices are applied
     * highest-first so an earlier removal does not shift a later index.
     */
    private static void handleRemoveRomCraft(final RemoveRomCraftPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos()) instanceof CraftingComputerBlockEntity cc)) {
                return;
            }
            final List<Integer> indices = new ArrayList<>(payload.romIndices());
            indices.sort(java.util.Comparator.reverseOrder());
            for (final int idx : indices) {
                if (idx >= CraftManagerStatePayload.MACHINE_ROM_BASE) {
                    // A machine recipe (processing/multi-stage): the offset index addresses that list.
                    cc.removeMachineRecipe(idx - CraftManagerStatePayload.MACHINE_ROM_BASE);
                    continue;
                }
                final List<CraftingPattern> rom = cc.romPatterns();
                if (idx < 0 || idx >= rom.size()) {
                    continue;
                }
                final String diskName = craftFileNameFor(rom.get(idx).result()) + ".craft";
                cc.removePattern(idx);
                deleteCraftFromDisk(cc, diskName);
            }
            cc.setChanged();
            PacketDistributor.sendToPlayer(player, buildCraftManagerState(cc, level));
        });
    }

    /** The folder on a Crafting Computer's system disk that mirrors its loaded {@code .craft} files. */
    private static final String CRAFTS_DIR = "crafts";

    /**
     * Mirrors a {@code .craft} onto the Crafting Computer's system disk under {@code crafts/} so the
     * loaded recipes are visible (and copyable) in the Files app. A flat filesystem keeps them at the
     * root; a hierarchical one nests them in {@code crafts/}. A no-op when there is no system disk.
     */
    private static void writeCraftToDisk(final CraftingComputerBlockEntity cc, final String fileName,
                                         final String content) {
        final net.minecraft.world.item.ItemStack disk = cc.systemDisk();
        if (disk.isEmpty()) {
            return;
        }
        final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind = filesystemKindOf(cc);
        if (kind == dev.jsc.jscomputronics.module.computing.os.FilesystemKind.NONE) {
            return;
        }
        final String path;
        if (kind == dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL) {
            DiskFilesystem.mkdir(disk, CRAFTS_DIR, kind);
            path = CRAFTS_DIR + "/" + fileName;
        } else {
            path = fileName;
        }
        DiskFilesystem.write(disk, path, FileType.CRAFT, content, cc.systemDiskFreeWeight(), kind);
    }

    /** Deletes a mirrored {@code .craft} from the Crafting Computer's system disk, if present. */
    private static void deleteCraftFromDisk(final CraftingComputerBlockEntity cc, final String fileName) {
        final net.minecraft.world.item.ItemStack disk = cc.systemDisk();
        if (disk.isEmpty()) {
            return;
        }
        final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind = filesystemKindOf(cc);
        if (kind == dev.jsc.jscomputronics.module.computing.os.FilesystemKind.NONE) {
            return;
        }
        final String path = kind == dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL
                ? CRAFTS_DIR + "/" + fileName : fileName;
        DiskFilesystem.delete(disk, path);
    }

    /**
     * Reconciles the {@code crafts/} folder on the system disk against the Recipe ROM: writes a
     * {@code .craft} for any ROM pattern missing its mirror file. This self-heals a disk whose ROM
     * predates the mirror, so opening the Crafting Manager always presents a consistent view.
     */
    private static void reconcileCraftsFolder(final CraftingComputerBlockEntity cc, final ServerLevel level) {
        if (cc.systemDisk().isEmpty()) {
            return;
        }
        boolean wrote = false;
        for (final CraftingPattern pattern : cc.romPatterns()) {
            final String diskName = craftFileNameFor(pattern.result()) + ".craft";
            if (craftFileExistsOnDisk(cc, diskName)) {
                continue;
            }
            final java.util.Optional<String> content = CraftFile.serialize(pattern, level.registryAccess());
            if (content.isPresent()) {
                writeCraftToDisk(cc, diskName, content.get());
                wrote = true;
            }
        }
        if (wrote) {
            cc.setChanged();
        }
    }

    /** Reports whether a mirrored {@code .craft} of the given name already exists on the system disk. */
    private static boolean craftFileExistsOnDisk(final CraftingComputerBlockEntity cc, final String fileName) {
        final net.minecraft.world.item.ItemStack disk = cc.systemDisk();
        if (disk.isEmpty()) {
            return false;
        }
        final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind = filesystemKindOf(cc);
        if (kind == dev.jsc.jscomputronics.module.computing.os.FilesystemKind.NONE) {
            return false;
        }
        final String path = kind == dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL
                ? CRAFTS_DIR + "/" + fileName : fileName;
        return DiskFilesystem.read(disk, path).isPresent();
    }

    /**
     * Serializes selected Recipe ROM patterns as {@code .craft} files onto a removable medium.
     */
    private static void handleDownloadToMedia(final DownloadToMediaPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos()) instanceof CraftingComputerBlockEntity cc)) {
                return;
            }
            final String key = payload.mediaVolumeKey();
            if (!key.startsWith("media:")) {
                return;
            }
            final ItemStack media = mediaStackFor(level, cc, key);
            if (media.isEmpty()) {
                return;
            }
            final List<CraftingPattern> rom = cc.romPatterns();
            for (final int idx : payload.romIndices()) {
                final java.util.Optional<String> content;
                final String fileName;
                if (idx >= CraftManagerStatePayload.MACHINE_ROM_BASE) {
                    // A machine recipe: serialize it back to its typed .craft form.
                    final int mi = idx - CraftManagerStatePayload.MACHINE_ROM_BASE;
                    final var recipes = cc.machineRecipes();
                    if (mi < 0 || mi >= recipes.size()) {
                        continue;
                    }
                    final var r = recipes.get(mi);
                    if (r.proc().isPresent()) {
                        content = CraftFile.serializeProcessing(r.proc().get(), level.registryAccess());
                    } else if (r.multi().isPresent()) {
                        content = CraftFile.serializeMultiStage(r.multi().get(), level.registryAccess());
                    } else {
                        continue;
                    }
                    final var resultKey = r.resultKey();
                    fileName = sanitizeFileBase(resultKey == null ? "recipe"
                            : resultKey.displayName().getString()) + ".craft";
                } else {
                    if (idx < 0 || idx >= rom.size()) {
                        continue;
                    }
                    final CraftingPattern pattern = rom.get(idx);
                    content = CraftFile.serialize(pattern, level.registryAccess());
                    fileName = craftFileNameFor(pattern.result()) + ".craft";
                }
                if (content.isEmpty()) {
                    continue;
                }
                final long freeWeight = mediaFreeWeightFor(media);
                DiskFilesystem.write(media, fileName, FileType.CRAFT, content.get(),
                        freeWeight, FilesystemKind.HIERARCHICAL);
            }
            // Propagate the updated filesystem component to the reader slot.
            final String rest = key.substring("media:".length());
            final int slash = rest.indexOf('/');
            final String rawPos = slash < 0 ? rest : rest.substring(0, slash);
            try {
                final long encoded = Long.parseLong(rawPos);
                if (level.getBlockEntity(net.minecraft.core.BlockPos.of(encoded))
                        instanceof dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity reader) {
                    reader.setChanged();
                }
            } catch (final NumberFormatException ignored) {
            }
            PacketDistributor.sendToPlayer(player, buildCraftManagerState(cc, level));
        });
    }

    /**
     * Builds a full {@link CraftManagerStatePayload} for {@code cc}: finds the first linked drive
     * with writable removable media, lists its {@code .craft} files, and annotates each ROM
     * pattern with whether a matching file already exists on the medium.
     */
    private static CraftManagerStatePayload buildCraftManagerState(final CraftingComputerBlockEntity cc,
                                                                    final ServerLevel level) {
        return buildCraftManagerState(cc, level, "");
    }

    private static CraftManagerStatePayload buildCraftManagerState(final CraftingComputerBlockEntity cc,
                                                                    final ServerLevel level, final String status) {
        String mediaVolumeKey = "";
        String mediaLabel = "";
        List<String> mediaFiles = List.of();

        // Find the first linked drive that holds writable removable media.
        for (final long endpoint : cc.linkedEndpoints()) {
            if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                    instanceof dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity reader) {
                final ItemStack m = reader.mediaSlot().getStackInSlot(0);
                if (!m.isEmpty()
                        && m.getItem() instanceof dev.jsc.jscomputronics.module.computing.os.media.FormattedMediaItem fmt
                        && fmt.writable()) {
                    mediaVolumeKey = "media:" + endpoint;
                    mediaLabel = dev.jsc.jscomputronics.module.computing.os.VolumeLabel.of(m, "Removable Drive");
                    mediaFiles = craftFileListFromMedia(m).files();
                    break;
                }
            }
        }

        final Set<String> mediaFileSet = new HashSet<>(mediaFiles);
        final List<CraftManagerStatePayload.WireRomEntry> romEntries = new ArrayList<>();
        final List<CraftingPattern> rom = cc.romPatterns();
        for (int i = 0; i < rom.size() && i < CraftManagerStatePayload.MAX_ROM_ENTRIES; i++) {
            final CraftingPattern p = rom.get(i);
            final String name = p.result().getHoverName().getString();
            final String fileName = craftFileNameFor(p.result()) + ".craft";
            romEntries.add(new CraftManagerStatePayload.WireRomEntry(i, name, mediaFileSet.contains(fileName)));
        }
        // Machine recipes (processing / multi-stage) share the ROM and must be listed too — an invisible entry
        // reads as "not loaded" and then the duplicate check looks wrong. Their indices are offset so the
        // remove action can tell them apart from the bench patterns above.
        final var machineRecipes = cc.machineRecipes();
        for (int i = 0; i < machineRecipes.size()
                && romEntries.size() < CraftManagerStatePayload.MAX_ROM_ENTRIES; i++) {
            final var r = machineRecipes.get(i);
            final var key = r.resultKey();
            final String base = key == null ? "recipe" : key.displayName().getString();
            final String name = base + (r.multi().isPresent() ? " [multi]" : " [machine]");
            romEntries.add(new CraftManagerStatePayload.WireRomEntry(
                    CraftManagerStatePayload.MACHINE_ROM_BASE + i, name, false));
        }
        // The routed machines (the Machines tab): each distinct machine type the wired switches declare, with
        // its concurrency config. Keyed by the machine's block registry id, which is what a pattern targets.
        final List<CraftManagerStatePayload.WireMachine> machines = new ArrayList<>();
        final Set<String> machineKeys = new HashSet<>();
        for (final var dm : cc.availableMachines()) {
            final String key = dm.machineType();
            if (key == null || key.isBlank() || !machineKeys.add(key)
                    || machines.size() >= CraftManagerStatePayload.MAX_MACHINES) {
                continue;
            }
            final CraftingComputerBlockEntity.MachineConfig cfg = cc.machineConfig(key);
            final int colon = key.indexOf(':');
            final String label = !dm.name().isBlank() ? dm.name()
                    : (colon >= 0 ? key.substring(colon + 1) : key);
            machines.add(new CraftManagerStatePayload.WireMachine(
                    key, label, true, cfg.maxJobs(), cfg.locked(), cfg.feedMax()));
        }
        return new CraftManagerStatePayload(mediaVolumeKey, mediaLabel, mediaFiles, romEntries,
                cc.craftingCardFactor() > 0.0, status, machines);
    }

    /** Derives a safe file base-name from the result {@link ItemStack}'s registry path. */
    private static String craftFileNameFor(final ItemStack result) {
        final net.minecraft.resources.ResourceLocation key =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem());
        return sanitizeFileBase(key == null ? "pattern" : key.getPath());
    }

    /** Clamps a display or registry name to a safe file base (letters/digits/underscore, max 32 chars). */
    private static String sanitizeFileBase(final String base) {
        final StringBuilder sb = new StringBuilder();
        for (final char c : base.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
            if (sb.length() >= 32) {
                break;
            }
        }
        return sb.isEmpty() ? "pattern" : sb.toString();
    }

    /** Computes the remaining free weight on a removable medium (filesystem component only). */
    private static long mediaFreeWeightFor(final ItemStack media) {
        if (!(media.getItem() instanceof dev.jsc.jscomputronics.module.computing.os.media.FormattedMediaItem fmt)) {
            return 0L;
        }
        final long capWeight = (long) fmt.format().capacityItems()
                * dev.jsc.jscomputronics.module.computing.storage.StorageKey.MB_EQ_PER_ITEM;
        final long fsUsed = media.getOrDefault(
                dev.jsc.jscomputronics.module.computing.ComputingModule.FILESYSTEM.get(),
                dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents.EMPTY).usedWeight();
        return Math.max(0L, capWeight - fsUsed);
    }

    // IQL filesystem — save/open/list .iql files on the Mainframe's system disk

    /**
     * Resolves the Mainframe reachable from {@code hostPos}, then writes the editor content to an
     * {@code .iql} file on its system disk. Replies with a refreshed {@link IqlFileListPayload}
     * carrying a short outcome message in the status field.
     */
    private static void handleSaveIqlFile(final SaveIqlFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host)
                    || !nmsNear(player, payload.hostPos(), host)
                    || host.networkUuid() == null) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                PacketDistributor.sendToPlayer(player,
                        new IqlFileListPayload(List.of(), "no Mainframe on network", false));
                return;
            }
            final ItemStack sysDisk = mainframe.systemDisk();
            if (sysDisk.isEmpty()) {
                PacketDistributor.sendToPlayer(player,
                        new IqlFileListPayload(List.of(), "Mainframe has no system disk", false));
                return;
            }
            final FilesystemKind kind = filesystemKindOf(mainframe);
            if (kind == FilesystemKind.NONE) {
                PacketDistributor.sendToPlayer(player,
                        new IqlFileListPayload(List.of(), "no OS installed on Mainframe disk", false));
                return;
            }
            final String fileName = sanitizeIqlName(payload.fileName()) + ".iql";
            final long freeWeight = computeDiskFreeWeight(mainframe, sysDisk);
            final DiskFilesystem.WriteResult result =
                    DiskFilesystem.write(sysDisk, fileName, FileType.IQL, payload.content(),
                            freeWeight, kind);
            final boolean ok = result == DiskFilesystem.WriteResult.OK;
            if (ok) {
                mainframe.setChanged();
            }
            final String status = switch (result) {
                case OK -> "saved: " + fileName;
                case DISK_FULL -> "disk full — free space on the Mainframe's system disk";
                case INVALID_PATH -> "invalid file name";
                case READ_ONLY -> "file type is read-only";
            };
            PacketDistributor.sendToPlayer(player, iqlFileList(sysDisk, kind, status, ok));
        });
    }

    /** Sends the list of {@code .iql} files on the Mainframe's system disk to the NMS client. */
    private static void handleRequestIqlFileList(final RequestIqlFileListPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host)
                    || !nmsNear(player, payload.hostPos(), host)
                    || host.networkUuid() == null) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                PacketDistributor.sendToPlayer(player,
                        new IqlFileListPayload(List.of(), "", false));
                return;
            }
            final ItemStack sysDisk = mainframe.systemDisk();
            if (sysDisk.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new IqlFileListPayload(List.of(), "", true));
                return;
            }
            final FilesystemKind kind = filesystemKindOf(mainframe);
            PacketDistributor.sendToPlayer(player, iqlFileList(sysDisk, kind, "", true));
        });
    }

    /** Forwards the {@link IqlFileListPayload} to the open NMS screen. */
    private static void handleIqlFileList(final IqlFileListPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.NmsApp.acceptFileList(payload));
    }

    /** Reads an {@code .iql} file from the Mainframe's disk and sends its content back. */
    private static void handleOpenIqlFile(final OpenIqlFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host)
                    || !nmsNear(player, payload.hostPos(), host)
                    || host.networkUuid() == null) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                PacketDistributor.sendToPlayer(player,
                        new IqlFileContentPayload("", "", false));
                return;
            }
            final ItemStack sysDisk = mainframe.systemDisk();
            if (sysDisk.isEmpty()) {
                PacketDistributor.sendToPlayer(player,
                        new IqlFileContentPayload("", "", false));
                return;
            }
            final var content = DiskFilesystem.read(sysDisk, payload.fileName());
            if (content.isEmpty()) {
                PacketDistributor.sendToPlayer(player,
                        new IqlFileContentPayload("", "", false));
                return;
            }
            PacketDistributor.sendToPlayer(player,
                    new IqlFileContentPayload(payload.fileName(), content.get(), true));
        });
    }

    /** Forwards the {@link IqlFileContentPayload} to the open NMS screen. */
    private static void handleIqlFileContent(final IqlFileContentPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.NmsApp.acceptFileContent(payload));
    }

    /** Builds the payload listing every {@code .iql} file on the given disk. */
    private static IqlFileListPayload iqlFileList(final ItemStack disk, final FilesystemKind kind,
                                                   final String status, final boolean ok) {
        final List<DiskFilesystem.FileEntry> entries = DiskFilesystem.list(disk, "", kind);
        final List<String> names = new ArrayList<>();
        for (final DiskFilesystem.FileEntry entry : entries) {
            if (entry.type() == FileType.IQL && names.size() < IqlFileListPayload.MAX_FILES) {
                names.add(entry.path());
            }
        }
        return new IqlFileListPayload(names, status, ok);
    }

    /** Sanitizes a user-provided base name for an {@code .iql} file (strips extension and invalid chars). */
    private static String sanitizeIqlName(final String raw) {
        String name = raw == null ? "" : raw.trim();
        final int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replaceAll("[/\\\\\\x00-\\x1F]", "_");
        if (name.isEmpty()) {
            name = "query";
        }
        if (name.length() > SaveIqlFilePayload.MAX_NAME_LEN) {
            name = name.substring(0, SaveIqlFilePayload.MAX_NAME_LEN);
        }
        return name;
    }

    private static void handleLocalDeposit(final TerminalLocalDepositPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ComputerTerminalMenu menu)) {
                return;
            }
            final int idx = payload.slotIndex();
            final boolean fromCursor = idx == TerminalLocalDepositPayload.CURSOR
                    || idx == TerminalLocalDepositPayload.CURSOR_ONE;
            if (!fromCursor && (idx < menu.storageSlotCount() || idx >= menu.slots.size())) {
                return; // a slot source must be a player-inventory menu slot
            }
            final net.minecraft.world.inventory.Slot slot = fromCursor ? null : menu.getSlot(idx);
            final ItemStack source = fromCursor ? menu.getCarried() : slot.getItem();
            if (source.isEmpty()) {
                return;
            }
            final int amount = idx == TerminalLocalDepositPayload.CURSOR_ONE ? 1 : source.getCount();
            final long stored = host.localStore().insert(StorageKey.of(source), amount);
            if (stored <= 0L) {
                return;
            }
            source.shrink((int) stored);
            if (fromCursor) {
                menu.setCarried(source.isEmpty() ? ItemStack.EMPTY : source);
            } else {
                slot.set(source.isEmpty() ? ItemStack.EMPTY : source);
            }
            menu.broadcastChanges();
            dispatchLocalSnapshot(player, host);
        });
    }
}
