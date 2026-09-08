/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.block.part.AbstractBusPart;
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.blockentity.PersonalComputerBlockEntity;
import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import dev.jstech.computronics.blockentity.ServerRouterBlockEntity;
import dev.jstech.computronics.crafting.CraftingPattern;
import dev.jstech.computronics.datacenter.LoadBalancer;
import dev.jstech.computronics.menu.AbstractBusMenu;
import dev.jstech.computronics.menu.ComputerTerminalMenu;
import dev.jstech.computronics.menu.CraftingComputerMenu;
import dev.jstech.computronics.menu.PersonalComputerMenu;
import dev.jstech.computronics.menu.ServerRouterMenu;
import dev.jstech.computronics.operation.DataHandoff;
import dev.jstech.computronics.operation.MoveLabels;
import dev.jstech.computronics.os.FilesystemKind;
import dev.jstech.computronics.os.OsDef;
import dev.jstech.computronics.os.OsDef;
import dev.jstech.computronics.os.OsGating;
import dev.jstech.computronics.os.IOsHost;
import dev.jstech.computronics.os.OsRegistry;
import dev.jstech.computronics.os.fs.CraftFile;
import dev.jstech.computronics.os.fs.DiskFilesystem;
import dev.jstech.computronics.os.fs.FileType;
import dev.jstech.computronics.os.media.MediaKind;
import dev.jstech.computronics.os.media.MediaReaderBlockEntity;
import dev.jstech.computronics.program.Programs;
import dev.jstech.computronics.storage.DataContainers;
import dev.jstech.computronics.storage.ServerStore;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.computronics.terminal.IComputerTerminalHost;
import dev.jstech.core.format.Unit;
import dev.jstech.core.format.UnitFormatter;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.network.ServerNode;
import dev.jstech.core.network.SubframeNode;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.core.util.ShortId;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
        registrar.playToServer(RackBayPowerPayload.TYPE, RackBayPowerPayload.STREAM_CODEC,
                ComputingPayloads::handleRackBayPower);
        registrar.playToServer(MachinePowerPayload.TYPE, MachinePowerPayload.STREAM_CODEC,
                ComputingPayloads::handleMachinePower);
        registrar.playToClient(OpenKvmPayload.TYPE, OpenKvmPayload.STREAM_CODEC,
                ComputingPayloads::handleOpenKvm);
        registrar.playToServer(RemoteControlPayload.TYPE, RemoteControlPayload.STREAM_CODEC,
                ComputingPayloads::handleRemoteControl);
        registrar.playToClient(RemoteHostsPayload.TYPE, RemoteHostsPayload.STREAM_CODEC,
                ComputingPayloads::handleRemoteHosts);
        registrar.playBidirectional(DesktopWindowsPayload.TYPE, DesktopWindowsPayload.STREAM_CODEC,
                new net.neoforged.neoforge.network.handling.DirectionalPayloadHandler<>(
                        ComputingPayloads::handleDesktopWindowsOnClient,
                        ComputingPayloads::handleDesktopWindowsOnServer));
        registrar.playToServer(KvmSelectPayload.TYPE, KvmSelectPayload.STREAM_CODEC,
                ComputingPayloads::handleKvmSelect);
        registrar.playToServer(RequestNetworkManagerPayload.TYPE, RequestNetworkManagerPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestNetworkManager);
        registrar.playToClient(NetworkManagerPayload.TYPE, NetworkManagerPayload.STREAM_CODEC,
                ComputingPayloads::handleNetworkManager);
        registrar.playToServer(RequestStorageInsightsPayload.TYPE, RequestStorageInsightsPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestStorageInsights);
        registrar.playToClient(StorageInsightsPayload.TYPE, StorageInsightsPayload.STREAM_CODEC,
                ComputingPayloads::handleStorageInsights);
        registrar.playToServer(RequestItemDetailPayload.TYPE, RequestItemDetailPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestItemDetail);
        registrar.playToClient(ItemDetailPayload.TYPE, ItemDetailPayload.STREAM_CODEC,
                ComputingPayloads::handleItemDetail);
        registrar.playToServer(RequestCraftPlannerPayload.TYPE, RequestCraftPlannerPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestCraftPlanner);
        registrar.playToClient(CraftPlannerPayload.TYPE, CraftPlannerPayload.STREAM_CODEC,
                ComputingPayloads::handleCraftPlanner);
        registrar.playToServer(RequestAutomationPayload.TYPE, RequestAutomationPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestAutomation);
        registrar.playToClient(AutomationPayload.TYPE, AutomationPayload.STREAM_CODEC,
                ComputingPayloads::handleAutomation);
        registrar.playToServer(CreateAutomationJobPayload.TYPE, CreateAutomationJobPayload.STREAM_CODEC,
                ComputingPayloads::handleCreateAutomationJob);
        registrar.playToServer(JobActionPayload.TYPE, JobActionPayload.STREAM_CODEC,
                ComputingPayloads::handleJobAction);
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
        registrar.playToServer(EjectMediaPayload.TYPE, EjectMediaPayload.STREAM_CODEC,
                ComputingPayloads::handleEjectMedia);
        registrar.playToServer(CopyFilePayload.TYPE, CopyFilePayload.STREAM_CODEC,
                ComputingPayloads::handleCopyFile);
        registrar.playToServer(InstallFromMediaPayload.TYPE, InstallFromMediaPayload.STREAM_CODEC,
                ComputingPayloads::handleInstallFromMedia);
        registrar.playToServer(SetDesktopPrefsPayload.TYPE, SetDesktopPrefsPayload.STREAM_CODEC,
                ComputingPayloads::handleSetDesktopPrefs);
        registrar.playToServer(RequestSettingsPayload.TYPE, RequestSettingsPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestSettings);
        registrar.playToServer(SetSettingPayload.TYPE, SetSettingPayload.STREAM_CODEC,
                ComputingPayloads::handleSetSetting);
        registrar.playToServer(EndProcessPayload.TYPE, EndProcessPayload.STREAM_CODEC,
                ComputingPayloads::handleEndProcess);
        registrar.playToServer(RunProgramPayload.TYPE, RunProgramPayload.STREAM_CODEC,
                ComputingPayloads::handleRunProgram);
        registrar.playToClient(SettingsSnapshotPayload.TYPE, SettingsSnapshotPayload.STREAM_CODEC,
                ComputingPayloads::handleSettingsSnapshot);
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
        registrar.playToServer(RequestFolderContentPayload.TYPE, RequestFolderContentPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestFolderContent);
        registrar.playToClient(FolderContentPayload.TYPE, FolderContentPayload.STREAM_CODEC,
                ComputingPayloads::handleFolderContent);
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
        registrar.playToServer(SetOperationPriorityPayload.TYPE, SetOperationPriorityPayload.STREAM_CODEC,
                ComputingPayloads::handleSetOperationPriority);
        registrar.playToServer(CancelOperationPayload.TYPE, CancelOperationPayload.STREAM_CODEC,
                ComputingPayloads::handleCancelOperation);
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
        // The firmware boot manager: state request/reply, boot/install/boot-order actions, restart into setup.
        registrar.playToServer(RequestFirmwareStatePayload.TYPE, RequestFirmwareStatePayload.STREAM_CODEC,
                ComputingPayloads::handleRequestFirmwareState);
        registrar.playToClient(FirmwareStatePayload.TYPE, FirmwareStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    // The same hardware state feeds the setup screen and the POST's device-detection lines.
                    dev.jstech.computronics.client.FirmwareScreen.accept(payload);
                    dev.jstech.computronics.client.BootSequenceScreen.accept(payload);
                }));
        registrar.playToServer(FirmwareActionPayload.TYPE, FirmwareActionPayload.STREAM_CODEC,
                ComputingPayloads::handleFirmwareAction);
        registrar.playToServer(RequestFirmwarePayload.TYPE, RequestFirmwarePayload.STREAM_CODEC,
                ComputingPayloads::handleRequestFirmware);
        /*
         * The power-on self-test: the server asks the monitor to play it; the client reports it finished
         * (or that DEL asked for the setup) and the server opens the boot target.
         */
        registrar.playToClient(OpenPostPayload.TYPE, OpenPostPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.jstech.computronics.block.IPostScreenOpener.Holder.open(
                                payload.host(), payload.monitorPos(),
                                dev.jstech.computronics.os.FirmwareKind.values()[payload.firmwareKind()],
                                payload.name())));
        // A finished installer still waiting for its reboot: the monitor comes back to that prompt.
        registrar.playToClient(OpenInstallDonePayload.TYPE, OpenInstallDonePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.jstech.computronics.block.IInstallDoneScreenOpener.Holder.open(
                                payload.host(), payload.monitorPos(),
                                dev.jstech.computronics.os.FirmwareKind.values()[payload.firmwareKind()],
                                payload.osName(), payload.targetLabel(), payload.targetSlot(), payload.failure())));
        registrar.playToServer(PostCompletePayload.TYPE, PostCompletePayload.STREAM_CODEC,
                ComputingPayloads::handlePostComplete);
        registrar.playToServer(UninstallProgramPayload.TYPE, UninstallProgramPayload.STREAM_CODEC,
                ComputingPayloads::handleUninstallProgram);
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
        registrar.playToServer(RequestCraftManagerPayload.TYPE, RequestCraftManagerPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestCraftManager);
        // The Cluster Manager: the Cluster Management Computer's program asks, acts, and gets a state back.
        registrar.playToServer(RequestClusterManagerPayload.TYPE, RequestClusterManagerPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestClusterManager);
        registrar.playToServer(ClusterManagerActionPayload.TYPE, ClusterManagerActionPayload.STREAM_CODEC,
                ComputingPayloads::handleClusterManagerAction);
        registrar.playToServer(ClusterMoveOutPayload.TYPE, ClusterMoveOutPayload.STREAM_CODEC,
                ComputingPayloads::handleClusterMoveOut);
        registrar.playToServer(ClusterRenamePayload.TYPE, ClusterRenamePayload.STREAM_CODEC,
                ComputingPayloads::handleClusterRename);
        registrar.playToClient(ClusterManagerStatePayload.TYPE, ClusterManagerStatePayload.STREAM_CODEC,
                ComputingPayloads::handleClusterManagerState);
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
                    running ? "the network's query and job engine" : "stopped, start it to run jobs"));
            for (final dev.jstech.computronics.program.iql.IqlSavedObject job
                    : mainframe.iqlCatalog().ofType(
                            dev.jstech.computronics.program.iql.IqlDefinition.ObjectType.JOB)) {
                final boolean paused = mainframe.isJobPaused(job.name());
                final String state = paused ? "paused" : running ? "active" : "idle";
                lines.add(new ProcessListPayload.ProcessLine(ProcessListPayload.KIND_JOB, job.name(),
                        state, jobDetail(job)));
            }
        }
        PacketDistributor.sendToPlayer(player, new ProcessListPayload(lines));
    }

    private static String jobDetail(final dev.jstech.computronics.program.iql.IqlSavedObject job) {
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

    /*
     * Command Prompt: a typed line runs through the shell against the open host and the styled
     * output is streamed back. The CLI is an alternative interface over the same network operations.
     */

    private static final int CLI_WIDTH = 50;

    private static void handleRunCommand(final RunCommandPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof dev.jstech.computronics.menu.CommandPromptMenu menu)
                    || !menu.hostPos().equals(payload.hostPos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host)) {
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
            /*
             * An open ssh session runs the line on the remote machine, in its own shell family, the
             * local terminal is only the window. Everything else (ssh itself, exit) stays local.
             */
            final var localComputer =
                    new dev.jstech.computronics.program.ServerCliComputer(host, level);
            var computer = localComputer;
            final var session = sshTargetOf(host, level, payload.line());
            if (session != null) {
                computer = new dev.jstech.computronics.program.ServerCliComputer(
                        session, level);
            }
            /*
             * The shell speaks the installed OS kernel's family (DOS verbs on MC-DOS/Frames, POSIX on Linux), or
             * the live installer's verbs while a live medium is booted.
             */
            final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(
                    computer, CLI_WIDTH);
            final var response = shell.run(payload.line(), computer);
            final List<CommandOutputPayload.WireLine> wire = new ArrayList<>(response.lines().size());
            for (final var cliLine : response.lines()) {
                wire.add(new CommandOutputPayload.WireLine(cliLine.text(), cliLine.style().ordinal()));
            }
            final String prompt = computer.prompt();
            final var handOver = response.handOver();
            PacketDistributor.sendToPlayer(player, new CommandOutputPayload(response.clearScreen(), prompt, wire,
                    handOver == null ? "" : handOver.editor(),
                    handOver == null ? "" : handOver.path()));
            if (computer.firmwareRebootRequested()) {
                // "reboot --firmware": leave the terminal and enter the boot manager on the same monitor.
                player.closeContainer();
                dev.jstech.computronics.block.MonitorBlock.openFirmware(
                        player, level, menu.monitorPos(), payload.hostPos());
                return;
            }
            if (computer.rebootRequested()) {
                /*
                 * A plain "reboot": the terminal closes and the POST replays on the same monitor, after
                 * which whatever the boot target now is (a freshly installed OS included) comes up.
                 */
                if (level.getBlockEntity(payload.hostPos())
                        instanceof dev.jstech.computronics.os.IOsHost be) {
                    be.setNeedsPost(true);
                }
                player.closeContainer();
                dev.jstech.computronics.block.MonitorBlock.openPost(
                        player, level, menu.monitorPos(), payload.hostPos());
                return;
            }
            // Persist the typed line on the computer so the history survives closing the prompt or Monitor.
            if (host.console() != null && !payload.line().isBlank()) {
                host.console().pushHistory(payload.line().trim());
                ((net.minecraft.world.level.block.entity.BlockEntity) host).setChanged();
            }
        });
    }

    private static void launchProgram(final ServerPlayer player,
            final dev.jstech.computronics.terminal.IComputerTerminalHost host,
            final BlockPos monitorPos, final BlockPos hostPos, final String name) {
        dev.jstech.computronics.os.ProgramSpec program = null;
        for (final var candidate : dev.jstech.computronics.program.Programs.all()) {
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
        /*
         * Program run gate: the installed OS platform must be one the program supports, and the computer
         * must meet its CPU/VRAM minimums. Null-safe: programs with no declared requirement always pass.
         */
        if (player.level() instanceof ServerLevel osLevel
                && osLevel.getBlockEntity(hostPos) instanceof dev.jstech.computronics.os
                        .IOsHost osComputer
                && !dev.jstech.computronics.os.OsRegistry.canRunProgram(
                        osComputer.installedOsId(), program.id(),
                        osComputer.maxCpuMhz(), osComputer.totalVramMb())) {
            sendConsoleLine(player, program.commandName()
                    + " cannot run on this computer's OS or hardware", OperationRecord.STATUS_FAILED);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "The " + program.commandName() + " cannot run on this computer's OS or hardware."), false);
            return;
        }
        if (program.id().equals(dev.jstech.computronics.program.Programs.NMS)) {
            // The NMS is now a desktop window opened from its Frames desktop icon, not a server-side menu.
            sendConsoleLine(player, "open the NMS from its desktop icon on a Frames computer", -1);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Open the NMS from its desktop icon."), false);
        } else {
            sendConsoleLine(player, "the " + program.commandName() + " is already open", -1);
        }
    }

    /** One styled line back to the open Command Prompt (status -1 = dim, FAILED = red, else green). */
    private static void sendConsoleLine(final ServerPlayer player, final String text, final int status) {
        final dev.jstech.computronics.program.cli.CliStyle style = status == OperationRecord.STATUS_FAILED
                ? dev.jstech.computronics.program.cli.CliStyle.ERROR
                : status < 0 ? dev.jstech.computronics.program.cli.CliStyle.DIM
                : dev.jstech.computronics.program.cli.CliStyle.OK;
        final List<CommandOutputPayload.WireLine> wire = new ArrayList<>();
        for (final String line : wrapToConsole(text)) {
            wire.add(new CommandOutputPayload.WireLine(line, style.ordinal()));
        }
        // An empty prompt means "keep the current prompt", so this helper does not change the directory.
        PacketDistributor.sendToPlayer(player, new CommandOutputPayload(false, "", wire));
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
                                   final dev.jstech.computronics.terminal.IComputerTerminalHost host) {
        final net.minecraft.world.phys.Vec3 p = player.position();
        if (hostPos.distToCenterSqr(p) <= 64.0) {
            return true;
        }
        if (host instanceof dev.jstech.core.peripheral.IPeripheralOwner owner) {
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
                            instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host)
                    || !nmsNear(player, payload.hostPos(), host)) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                PacketDistributor.sendToPlayer(player,
                        new IqlResultPayload(false, "the network has no running Mainframe", List.of()));
                return;
            }
            final var computer = new dev.jstech.computronics.program.ServerCliComputer(host, level);
            final var engine = new dev.jstech.computronics.program.IqlEngine(
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
                dev.jstech.computronics.client.NmsApp.accept(payload));
    }

    private static void handleRequestNmsSchema(final RequestNmsSchemaPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host)
                    || !nmsNear(player, payload.hostPos(), host)) {
                return;
            }
            PacketDistributor.sendToPlayer(player, nmsSchema(level, host));
        });
    }

    private static void handleNmsSchema(final NmsSchemaPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.NmsApp.acceptSchema(payload));
    }

    private static void handleSaveScript(final SaveScriptPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos()) instanceof IComputerTerminalHost host)
                    || host.networkUuid() == null) {
                return;
            }
            /*
             * Save to the network's Mainframe (the same place the schema snapshot reads it back from)
             * whether the studio's host is the Mainframe itself or a PC on its network. Saving to the raw
             * host position instead would silently drop the script when the host is not the Mainframe.
             */
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
            final dev.jstech.computronics.terminal.IComputerTerminalHost host) {
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
        final int itemTypes = dev.jstech.computronics.operation.NetworkStorage
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
                        dev.jstech.computronics.program.iql.IqlDefinition.ObjectType.VIEW)),
                objectNames(catalog.ofType(
                        dev.jstech.computronics.program.iql.IqlDefinition.ObjectType.PROCEDURE)),
                objectNames(catalog.ofType(
                        dev.jstech.computronics.program.iql.IqlDefinition.ObjectType.JOB)),
                mainframe.savedScript());
    }

    private static List<String> objectNames(
            final List<dev.jstech.computronics.program.iql.IqlSavedObject> objects) {
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
        return "jsc-net-" + dev.jstech.core.util.ShortId.of(net.asString());
    }

    private static void handleRequestConsoleInit(final RequestConsoleInitPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof dev.jstech.computronics.menu.CommandPromptMenu menu
                    && menu.hostPos().equals(payload.hostPos())
                    && player.level().getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host) {
                sendConsoleInit(player, host);
            }
        });
    }

    private static void sendConsoleInit(final ServerPlayer player,
            final dev.jstech.computronics.terminal.IComputerTerminalHost host) {
        final var console = host.console();
        final List<String> history = console == null ? List.of() : console.history();
        final List<ConsoleInitPayload.WireCommand> commands = new ArrayList<>();
        /*
         * Tab completion offers the installed shell family's verbs (ls/cat on Linux, dir/type on DOS), or the
         * live installer's while a live medium is booted.
         */
        final boolean live = console != null && console.liveInstall() != null;
        /*
         * Completion and hints offer only what this machine can run: a verb another kind of computer owns
         * (the cluster command outside a Cluster Management Computer) is no command here, and must not be
         * hinted as one.
         */
        final var cli = host instanceof net.minecraft.world.level.block.entity.BlockEntity
                ? new dev.jstech.computronics.program.ServerCliComputer(host, player.serverLevel())
                : null;
        for (final var command : dev.jstech.computronics.program.cli.CliCommands.commandsFor(
                dev.jstech.computronics.program.ServerCliComputer.shellFamilyOf(host), live)) {
            if (commands.size() >= ConsoleInitPayload.MAX_COMMANDS) {
                break;
            }
            if (cli != null && !command.available(cli)) {
                continue;
            }
            commands.add(new ConsoleInitPayload.WireCommand(command.name(), command.usage()));
        }
        /*
         * The devices Tab can complete for /dev/ arguments (mkfs, mount, grub-install): the disks in slot
         * order during a live install, or the mounted drives' device names on an installed POSIX system.
         */
        final List<String> devices = new ArrayList<>();
        if (live && host instanceof dev.jstech.computronics.os
                .IOsHost computer) {
            for (int i = 0; i < computer.diskSlots(); i++) {
                if (computer.diskInSlot(i).getItem()
                        instanceof dev.jstech.computronics.item.DiskItem) {
                    devices.add("sd" + (char) ('a' + i));
                }
            }
        } else if (dev.jstech.computronics.program.ServerCliComputer.shellFamilyOf(host)
                == dev.jstech.computronics.os.ShellFamily.POSIX
                && player.level() instanceof ServerLevel serverLevel) {
            for (final var mount : new dev.jstech.computronics.program.ServerCliComputer(
                    host, serverLevel).mounts()) {
                if (devices.size() < ConsoleInitPayload.MAX_DEVICES && mount.ready()) {
                    devices.add(mount.device());
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new ConsoleInitPayload(
                ((net.minecraft.world.level.block.entity.BlockEntity) host).getBlockPos(),
                List.copyOf(history), commands, devices));
    }

    private static void handleOpenComputerUi(final OpenComputerUiPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                // Only the firmware setup is a client-only screen; the desktop opens as a server-side menu.
                dev.jstech.computronics.block.IFirmwareScreenOpener.Holder.open(
                        payload.host(), payload.monitorPos(),
                        dev.jstech.computronics.os.FirmwareKind.values()[payload.firmwareKind()],
                        payload.name()));
    }

    private static void handleConsoleInit(final ConsoleInitPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.CommandPromptScreen.acceptInit(payload));
    }

    private static void handleOpenProgram(final OpenProgramPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level().getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.terminal.IComputerTerminalHost terminalHost)) {
                return;
            }
            /*
             * Anti-spoof: either this host's terminal menu is open, or the player is within reach of the
             * monitor they used (the desktop shell is a client-only screen with no server-side menu, so a
             * program opened from it cannot be validated against an open container).
             */
            final boolean viaTerminal = player.containerMenu instanceof ComputerTerminalMenu terminal
                    && terminal.hostPos().equals(payload.hostPos());
            /*
             * The desktop path is only valid when the monitor is actually a linked peripheral of this host,
             * so a player near any monitor cannot open a program bound to a foreign computer.
             */
            final boolean nearMonitor = player.distanceToSqr(
                    net.minecraft.world.phys.Vec3.atCenterOf(payload.monitorPos())) <= 64.0
                    && terminalHost instanceof dev.jstech.core.peripheral.IPeripheralOwner owner
                    && owner.linkedEndpoints().contains(payload.monitorPos().asLong());
            if (!viaTerminal && !nearMonitor) {
                return;
            }
            final String id = payload.programId();
            if (id.equals(dev.jstech.computronics.program.Programs.COMMAND_PROMPT.toString())
                    || id.equals("command_prompt")) {
                final net.minecraft.network.chat.Component title =
                        player.level().getBlockState(payload.hostPos()).getBlock().getName();
                /*
                 * The host's board-derived era drives the prompt's GUI skin; capture it at open time. It is
                 * not re-synced afterwards because the board is only swapped in the computer's own assembly
                 * GUI, never from the running prompt.
                 */
                final dev.jstech.core.tier.HardwareEra hostEra =
                        player.level().getBlockEntity(payload.hostPos())
                                instanceof dev.jstech.computronics.os
                                        .IOsHost host ? host.displayEra() : null;
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (windowId, inv, p) -> new dev.jstech.computronics.menu.CommandPromptMenu(
                                windowId, inv, payload.monitorPos(), payload.hostPos(), hostEra), title),
                        buf -> dev.jstech.computronics.menu.CommandPromptMenu.writeOpenBuffer(
                                buf, payload.monitorPos(), payload.hostPos(), hostEra));
            } else {
                /*
                 * The NMS and other windowed programs open through the shared launcher, which checks they
                 * are installed and (for the NMS) that the IQL Engine is running on the network's Mainframe.
                 */
                launchProgram(player, terminalHost, payload.monitorPos(), payload.hostPos(), id);
            }
        });
    }

    private static void handleCommandOutput(final CommandOutputPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.CommandPromptScreen.accept(payload));
    }

    private static void handleRequestDiskFiles(final RequestDiskFilesPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            final java.util.List<DiskFilesPayload.WireFile> wire = new java.util.ArrayList<>();
            final java.util.List<DiskFilesPayload.WireVolume> volumes = new java.util.ArrayList<>();
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer) {
                // The mountable volumes (drive tree): the system disk, then each linked drive with a medium.
                if (!computer.systemDisk().isEmpty()
                        && filesystemKindOf(computer)
                                != dev.jstech.computronics.os.FilesystemKind.NONE) {
                    volumes.add(new DiskFilesPayload.WireVolume("",
                            dev.jstech.computronics.os.VolumeLabel.of(
                                    computer.systemDisk(), "Local Disk")));
                }
                for (final long endpoint : computer.linkedEndpoints()) {
                    if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                            instanceof MediaReaderBlockEntity reader
                            && !reader.mediaSlot().getStackInSlot(0).isEmpty()) {
                        /*
                         * An installer's drive is named for what it installs ("Frames 11 Setup"), so the
                         * tree says what is in the drive before it is opened.
                         */
                        final net.minecraft.world.item.ItemStack medium = reader.mediaSlot().getStackInSlot(0);
                        final String fallback = dev.jstech.computronics.os.media
                                .InstallerProjection.facts(medium).map(f -> f.name() + " Setup").orElse("Removable Drive");
                        volumes.add(new DiskFilesPayload.WireVolume("media:" + endpoint,
                                dev.jstech.computronics.os.VolumeLabel.of(medium, fallback)));
                    }
                }
                final String reqDir = payload.dir();
                if (reqDir.startsWith("media:")) {
                    // Browsing a removable medium in a linked drive.
                    listMediaInto(wire, level, computer, reqDir);
                } else {
                    final net.minecraft.world.item.ItemStack disk = computer.systemDisk();
                    final dev.jstech.computronics.os.FilesystemKind kind =
                            filesystemKindOf(computer);
                    if (!disk.isEmpty()
                            && kind != dev.jstech.computronics.os.FilesystemKind.NONE) {
                        // Subdirectories first (folders before files, Windows-style).
                        for (final String d
                                : dev.jstech.computronics.os.fs.DiskFilesystem.listDirs(
                                        disk, reqDir, kind)) {
                            wire.add(new DiskFilesPayload.WireFile(d, "", 0L, false, true));
                        }
                        for (final dev.jstech.computronics.os.fs.DiskFilesystem.FileEntry e
                                : dev.jstech.computronics.os.fs.DiskFilesystem.list(
                                        disk, reqDir, kind)) {
                            wire.add(wireFile(disk, e, ""));
                        }
                    }
                    // At the root, removable media in linked drives appear as drives to open.
                    if (reqDir.isEmpty()) {
                        for (final long endpoint : computer.linkedEndpoints()) {
                            if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                                    instanceof dev.jstech.computronics.os.media
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
            final dev.jstech.computronics.os.IOsHost computer,
            final String reqDir) {
        final net.minecraft.world.item.ItemStack media = mediaStackFor(level, computer, reqDir);
        if (media.isEmpty()) {
            return;
        }
        final String rest = reqDir.substring("media:".length());
        final int slash = rest.indexOf('/');
        final long readerPos = Long.parseLong(slash < 0 ? rest : rest.substring(0, slash));
        final String subDir = slash < 0 ? "" : rest.substring(slash + 1);
        final dev.jstech.computronics.os.FilesystemKind kind =
                dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL;
        final String prefix = "media:" + readerPos + "/";
        /*
         * An installer shows the disc of its era: setup, readme, manifest and payload, generated from
         * the medium's stamp the way a disk's .dat files are generated from its storage. It carries no
         * stored files of its own, so the projection is the whole listing; a data medium lists what it
         * really holds.
         */
        for (final dev.jstech.computronics.os.fs.InstallerLayout.Entry e
                : dev.jstech.computronics.os.media.InstallerProjection.list(media, subDir)) {
            wire.add(new DiskFilesPayload.WireFile(prefix + e.path(),
                    e.directory() ? "" : e.type().extension(), 0L, true, e.directory()));
        }
        for (final String d : dev.jstech.computronics.os.fs.DiskFilesystem.listDirs(
                media, subDir, kind)) {
            wire.add(new DiskFilesPayload.WireFile(prefix + d, "", 0L, false, true));
        }
        for (final dev.jstech.computronics.os.fs.DiskFilesystem.FileEntry e
                : dev.jstech.computronics.os.fs.DiskFilesystem.list(media, subDir, kind)) {
            wire.add(wireFile(media, e, prefix));
        }
    }

    /**
     * A listed file on the wire. A {@code .dat} row also carries the item it projects and how many are
     * stored, so the explorer shows the item and its count rather than a file name a player has to decode.
     */
    private static DiskFilesPayload.WireFile wireFile(final net.minecraft.world.item.ItemStack volume,
            final dev.jstech.computronics.os.fs.DiskFilesystem.FileEntry e, final String prefix) {
        String itemId = "";
        long count = 0L;
        if (e.type() == dev.jstech.computronics.os.fs.FileType.DAT
                && volume.getItem() instanceof dev.jstech.computronics.item.DiskItem) {
            final StorageKey key = resolveDatKey(volume, e.path());
            if (key != null && key.item() != null) {
                itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(key.item()).toString();
                count = dev.jstech.computronics.storage.DriveVolumes.contents(volume)
                        .items().getOrDefault(key, 0L);
            }
        }
        return new DiskFilesPayload.WireFile(prefix + e.path(), e.type().extension(), e.weight(), e.readOnly(),
                false, itemId, count);
    }

    /**
     * Resolves a {@code media:<readerPos>[/sub]} path to the medium's {@link net.minecraft.world.item.ItemStack}
     * in a linked drive, or {@link net.minecraft.world.item.ItemStack#EMPTY} if not reachable.
     */
    static net.minecraft.world.item.ItemStack mediaStackFor(final ServerLevel level,
            final dev.jstech.computronics.os.IOsHost computer,
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
                        instanceof dev.jstech.computronics.os.media
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
            final dev.jstech.computronics.os.IOsHost computer,
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
                instanceof dev.jstech.computronics.os.media.MediaReaderBlockEntity reader) {
            reader.setChanged();
            level.sendBlockUpdated(net.minecraft.core.BlockPos.of(readerPos),
                    reader.getBlockState(), reader.getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Free space on a medium in mB-equivalents (capacity minus its stored files). */
    private static long mediaFreeWeight(final net.minecraft.world.item.ItemStack media) {
        final long cap = media.getItem()
                instanceof dev.jstech.computronics.os.media.FormattedMediaItem fm
                ? fm.format().capacityItems() : 64L;
        final long capWeight = cap
                * dev.jstech.computronics.storage.StorageKey.MB_EQ_PER_ITEM;
        final long fsUsed = dev.jstech.computronics.os.fs.DiskFilesystem.filesWeight(media);
        /*
         * A DATA medium can also hold a stored item/fluid snapshot (MEDIA_DATA); both consume the medium's
         * capacity, so deduct both, mirroring IOsHost.systemDiskFreeWeight (stored items +
         * FILESYSTEM). Ignoring MEDIA_DATA let the player write files past the medium's real capacity.
         */
        final long dataUsed = media.getOrDefault(
                        dev.jstech.computronics.ComputingModule.MEDIA_DATA.get(),
                        dev.jstech.computronics.storage.ServerStorageContents.EMPTY)
                .usedWeight();
        return Math.max(0L, capWeight - fsUsed - dataUsed);
    }

    private static void handleDiskFiles(final DiskFilesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!dev.jstech.computronics.client.os.CodeFileReplies.listing(payload)) {
                dev.jstech.computronics.client.os.FilesApps.accept(payload);
            }
        });
    }

    private static void handleRequestDesktopFiles(final RequestDesktopFilesPayload payload,
                                                  final IPayloadContext context) {
        context.enqueueWork(() -> {
            final java.util.List<DiskFilesPayload.WireFile> wire = new java.util.ArrayList<>();
            final String[] prefs = {"", ""};
            // accent override (0=none), brightness, clock12h (0/1), taskbar centered (1) vs left (0), dark (0/1)
            final int[] deskPrefs = {0, 100, 0, 1, 0};
            final java.util.List<String> programs = new java.util.ArrayList<>();
            final java.util.List<DesktopFilesPayload.WireIconCell> iconCells = new java.util.ArrayList<>();
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer) {
                final net.minecraft.world.item.ItemStack disk = computer.systemDisk();
                final dev.jstech.computronics.os.FilesystemKind kind =
                        filesystemKindOf(computer);
                prefs[0] = computer.console().wallpaper();
                prefs[1] = computer.console().computerName();
                deskPrefs[0] = computer.console().settings().accent();
                deskPrefs[1] = computer.console().settings().brightness();
                deskPrefs[2] = computer.console().settings().clock12h() ? 1 : 0;
                deskPrefs[3] = computer.console().settings().taskbarCentered() ? 1 : 0;
                deskPrefs[4] = computer.console().settings().darkMode() ? 1 : 0;
                /*
                 * Installed programs that open as their own desktop window (vs. the always-present built-in
                 * apps). Each is gated by the installed OS, hardware and host scope; the built-in apps are
                 * added on the client, so only installable desktop apps flow through this list.
                 */
                for (final dev.jstech.computronics.os.ProgramSpec spec
                        : dev.jstech.computronics.os.OsRegistry.programs()) {
                    final dev.jstech.computronics.os.OsDef hostOs = computer.installedOs();
                    if (spec.installable()
                            && spec.kind() == dev.jstech.computronics.os.ProgramKind.APP
                            && hostOs != null && spec.platforms().contains(hostOs.platform())
                            && installedAndAllowed(computer, spec.id())) {
                        programs.add(spec.id().getPath());
                    }
                }
                /*
                 * The desktop folder only exists on a hierarchical (desktop OS) disk; a POSIX kernel keeps it
                 * under the home directory, the DOS family under Users/Public.
                 */
                if (!disk.isEmpty()
                        && kind == dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL) {
                    final dev.jstech.computronics.os.OsDef osDef = computer.installedOs();
                    final String desktopDir = dev.jstech.computronics.os.fs.SystemLayout.desktopDirFor(
                            osDef == null ? null : dev.jstech.computronics.os.OsRegistry.getKernel(
                                    osDef.kernelId()));
                    for (final String d
                            : dev.jstech.computronics.os.fs.DiskFilesystem.listDirs(
                                    disk, desktopDir, kind)) {
                        wire.add(new DiskFilesPayload.WireFile(d, "", 0L, false, true));
                    }
                    for (final dev.jstech.computronics.os.fs.DiskFilesystem.FileEntry e
                            : dev.jstech.computronics.os.fs.DiskFilesystem.list(
                                    disk, desktopDir, kind)) {
                        wire.add(new DiskFilesPayload.WireFile(
                                e.path(), e.type().extension(), e.weight(), e.readOnly(), false));
                    }
                }
                /*
                 * Pinned icon cells. A "file:" pin whose desktop file no longer exists is dropped here and
                 * forgotten from the console state too, so a stale position never haunts a later file that
                 * happens to take the same name (self-healing). "app:" launcher pins are always kept.
                 */
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
            /*
             * The machine's open windows travel with the desktop listing, so the desktop that is opening
             * restores them from the machine and not from a cache in this client.
             */
            context.reply(DesktopWindowsPayload.of(payload.hostPos(),
                    context.player().level().getBlockEntity(payload.hostPos()) instanceof IOsHost machine
                            ? machine.openWindows() : java.util.List.of()));
            /*
             * Programs the player installed from the Mirror get a launcher of their own, so the icon on
             * the desktop is not only for what came with the machines.
             */
            final java.util.List<DesktopFilesPayload.WireCommunity> community = new java.util.ArrayList<>();
            if (context.player().level().getBlockEntity(payload.hostPos())
                    instanceof dev.jstech.computronics.terminal.IComputerTerminalHost terminal) {
                final var console = terminal.console();
                if (console != null) {
                    for (final var one : console.community()) {
                        community.add(new DesktopFilesPayload.WireCommunity(
                                one.name(), one.icon(), one.entry()));
                    }
                }
            }
            context.reply(new DesktopFilesPayload(wire, prefs[0], prefs[1], programs, iconCells,
                    new DesktopFilesPayload.Prefs(deskPrefs[0], deskPrefs[1], deskPrefs[2] != 0,
                            deskPrefs[3] != 0, deskPrefs[4] != 0), community));
        });
    }

    private static void handleSetDesktopPrefs(final SetDesktopPrefsPayload payload,
                                              final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer) {
                computer.console().setWallpaper(payload.wallpaper());
                computer.console().setComputerName(payload.computerName());
                computer.setChanged();
            }
        });
    }

    private static void handleRequestSettings(final RequestSettingsPayload payload,
                                              final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer) {
                PacketDistributor.sendToPlayer(player, buildSettingsSnapshot(computer, payload.hostPos()));
            }
        });
    }

    private static void handleEndProcess(final EndProcessPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.blockentity.AbstractComputerBlockEntity computer
                    && computer.cannon().stop(payload.id())) {
                computer.setChanged();
                PacketDistributor.sendToPlayer(player, buildSettingsSnapshot(
                        (dev.jstech.computronics.os.IOsHost) computer, payload.hostPos()));
            }
        });
    }

    private static void handleSetSetting(final SetSettingPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer
                    && computer instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host) {
                /*
                 * Route through the same setConfig the MC-DOS 'config' command uses, so both front-ends
                 * clamp and persist identically.
                 */
                new dev.jstech.computronics.program.ServerCliComputer(host, level)
                        .setConfig(payload.key(), payload.value());
                PacketDistributor.sendToPlayer(player, buildSettingsSnapshot(computer, payload.hostPos()));
            }
        });
    }

    private static void handleSettingsSnapshot(final SettingsSnapshotPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            dev.jstech.computronics.client.os.SettingsApp.accept(payload);
            dev.jstech.computronics.client.os.SystemMonitorApp.accept(payload);
            dev.jstech.computronics.client.os.TaskManagerApp.accept(payload);
        });
    }

    /** Reads the full Settings snapshot (editable knobs + read-only specs, disks and programs) from a computer. */
    private static SettingsSnapshotPayload buildSettingsSnapshot(
            final dev.jstech.computronics.os.IOsHost computer,
            final BlockPos pos) {
        final dev.jstech.computronics.program.ComputerConsoleState console = computer.console();
        final dev.jstech.computronics.program.ComputerSettings st = console.settings();
        final ItemStack sysDisk = computer.systemDisk();
        final int netshare = dev.jstech.computronics.item.DiskItem.publicPermille(sysDisk);
        final int cpuCount = computer.installedCpus();
        final String cpuLabel = cpuCount + (cpuCount == 1 ? " CPU" : " CPUs");
        final net.minecraft.resources.ResourceLocation osId = computer.installedOsId();
        final String osLabel = osId == null ? "none" : osId.getPath();
        final dev.jstech.computronics.os.OsDef os = computer.installedOs();
        final String platform = os == null ? "-" : os.platform().label();
        final List<String> installed = new ArrayList<>(console.installed());
        final List<SettingsSnapshotPayload.DiskUse> disks = new ArrayList<>();
        final long mbEq = dev.jstech.computronics.storage.StorageKey.MB_EQ_PER_ITEM;
        for (final ItemStack stack : computer.diskStacks()) {
            if (!(stack.getItem() instanceof dev.jstech.computronics.item.DiskItem diskItem)) {
                continue;
            }
            // Megabytes follow the disk's own era: what an item costs there is what its usage is worth.
            final long mbPerItem = diskItem.spec().era().mbPerItem();
            final long capMb = diskItem.spec().capacityMb();
            final long storageUsed = dev.jstech.computronics.storage.DriveVolumes.usedWeight(stack);
            final long fsUsed = dev.jstech.computronics.os.fs.DiskFilesystem.filesWeight(stack);
            final net.minecraft.resources.ResourceLocation dOsId =
                    stack.get(dev.jstech.computronics.ComputingModule.SYSTEM_OS.get());
            final dev.jstech.computronics.os.OsDef dOs =
                    dOsId != null ? dev.jstech.computronics.os.OsRegistry.getOs(dOsId) : null;
            final long osReserved = dOs != null ? dOs.footprintItemsOn(diskItem.spec().era()) * mbEq : 0L;
            final long usedMb = (storageUsed + fsUsed + osReserved) * mbPerItem / mbEq;
            disks.add(new SettingsSnapshotPayload.DiskUse(
                    stack.getHoverName().getString(), capMb, usedMb, stack == sysDisk));
        }
        // The memory ledger: what the system, its desktop, its services and its windows hold right now.
        final dev.jstech.computronics.os.RamLedger ledger = computer.ramLedger();
        final List<SettingsSnapshotPayload.RamUse> ramUses = new ArrayList<>();
        for (final dev.jstech.computronics.os.RamLedger.Entry entry : ledger.entries()) {
            ramUses.add(new SettingsSnapshotPayload.RamUse(
                    entry.name(), entry.mb(), entry.kind().name(), entry.id()));
        }
        return new SettingsSnapshotPayload(pos, console.wallpaper(), console.computerName(),
                st.accent(), st.clock12h(), st.guiScale(), st.brightness(),
                String.valueOf(st.defaultSaveDrive()), st.removableAutoOpen(), st.themePreset(),
                st.taskbarCentered(), st.darkMode(),
                netshare, cpuLabel, computer.maxCpuMhz(),
                computer.ramTotalMb(), computer.totalVramMb(),
                osLabel, platform, installed, disks, ledger.usedMb(), ramUses);
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
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer) {
                computer.console().setIconCell(payload.iconKey(), payload.cell());
                computer.setChanged();
            }
        });
    }

    private static void handleDesktopFiles(final DesktopFilesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.DesktopScreen.acceptDesktop(payload));
    }

    private static void handleRequestThisPc(final RequestThisPcPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final java.util.List<ThisPcPayload.WireDisk> disks = new java.util.ArrayList<>();
            final java.util.List<ThisPcPayload.WireMedia> media = new java.util.ArrayList<>();
            final java.util.List<String> installed = new java.util.ArrayList<>();
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer) {
                final net.minecraft.world.item.ItemStack sys = computer.systemDisk();
                int slot = 0;
                for (final net.minecraft.world.item.ItemStack stack : computer.diskStacks()) {
                    if (stack.getItem() instanceof dev.jstech.computronics.item.DiskItem diskItem) {
                        final long cap = diskItem.spec().capacityItems();
                        final long storageW =
                                dev.jstech.computronics.storage.DriveVolumes.usedWeight(stack);
                        final long fsW = dev.jstech.computronics.os.fs.DiskFilesystem.filesWeight(stack);
                        final net.minecraft.resources.ResourceLocation osId =
                                stack.get(dev.jstech.computronics.ComputingModule.SYSTEM_OS.get());
                        final dev.jstech.computronics.os.OsDef os =
                                osId != null ? dev.jstech.computronics.os.OsRegistry.getOs(osId) : null;
                        final long osItems = os != null ? os.footprintItemsOn(diskItem.spec().era()) : 0L;
                        final long mbEq = dev.jstech.computronics.storage.StorageKey.MB_EQ_PER_ITEM;
                        final long storeItems = storageW / mbEq;
                        final long fileItems = fsW / mbEq;
                        final long usedItems = storeItems + fileItems + osItems;
                        /*
                         * The three shares travel separately, so the disk can show where its space
                         * actually went instead of one anonymous "used" number.
                         */
                        disks.add(new ThisPcPayload.WireDisk(slot, stack.getHoverName().getString(),
                                cap, usedItems, stack == sys, osId != null ? osId.getPath() : "",
                                osItems, storeItems, fileItems));
                    }
                    slot++;
                }
                for (final long endpoint : computer.linkedEndpoints()) {
                    if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                            instanceof dev.jstech.computronics.os.media
                                    .MediaReaderBlockEntity reader) {
                        media.add(mediaRow(computer, payload.hostPos(), endpoint, reader));
                    }
                }
                installed.addAll(computer.console().installed());
                context.reply(new ThisPcPayload(machineCard(level, computer, payload.hostPos()), disks, media, installed));
                return;
            }
            context.reply(new ThisPcPayload(ThisPcPayload.WireMachine.EMPTY, disks, media, installed));
        });
    }

    /** One drive row for This PC: what is in the drive and, for an installer, what it would install. */
    private static ThisPcPayload.WireMedia mediaRow(
            final dev.jstech.computronics.os.IOsHost computer, final net.minecraft.core.BlockPos host,
            final long endpoint, final dev.jstech.computronics.os.media.MediaReaderBlockEntity reader) {
        final net.minecraft.world.item.ItemStack m = reader.mediaSlot().getStackInSlot(0);
        final dev.jstech.computronics.os.media.MediaKind kind = m.isEmpty() ? null : reader.insertedKind();
        final net.minecraft.resources.ResourceLocation pl = m.isEmpty() ? null : reader.insertedPayload();
        String payloadName = "";
        int payloadYear = 0;
        String packageId = "";
        String needs = "";
        boolean installable = false;
        if (pl != null && kind == dev.jstech.computronics.os.media.MediaKind.PROGRAM_INSTALL) {
            final dev.jstech.computronics.os.ProgramSpec spec =
                    dev.jstech.computronics.os.OsRegistry.getProgram(pl);
            installable = !computer.console().isInstalled(pl.toString());
            if (spec != null) {
                payloadName = spec.displayName();
                payloadYear = dev.jstech.computronics.os.Branding.year(spec.era());
                packageId = spec.commandName();
                needs = joinPlain(dev.jstech.computronics.os.MinSpecTooltip.programMinSpec(pl));
            }
        } else if (pl != null && kind == dev.jstech.computronics.os.media.MediaKind.OS_INSTALL) {
            final dev.jstech.computronics.os.OsDef os =
                    dev.jstech.computronics.os.OsRegistry.getOs(pl);
            if (os != null) {
                payloadName = os.displayName();
                payloadYear = dev.jstech.computronics.os.Branding.osYear(os.displayName(), os.minEra());
                packageId = os.id().getPath();
                needs = joinPlain(dev.jstech.computronics.os.MinSpecTooltip.osMinSpec(pl));
            }
        }
        final long stored = kind == dev.jstech.computronics.os.media.MediaKind.DATA
                ? reader.insertedData().total() : 0L;
        final net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.of(endpoint);
        final int blocksAway = Math.abs(at.getX() - host.getX()) + Math.abs(at.getY() - host.getY())
                + Math.abs(at.getZ() - host.getZ());
        return new ThisPcPayload.WireMedia(endpoint, reader.driveType().name(),
                m.isEmpty() ? "" : m.getHoverName().getString(), kind != null ? kind.name() : "",
                pl != null ? pl.getPath() : "", installable, payloadName, payloadYear, packageId, needs,
                stored, blocksAway);
    }

    /** The machine card for This PC: what this computer is, in one block of text the client draws. */
    private static ThisPcPayload.WireMachine machineCard(
            final ServerLevel level, final dev.jstech.computronics.os.IOsHost computer,
            final net.minecraft.core.BlockPos host) {
        final String kind;
        if (computer instanceof MainframeBlockEntity) {
            kind = "Mainframe";
        } else if (computer instanceof dev.jstech.computronics.blockentity.CraftingComputerBlockEntity) {
            kind = "Crafting Computer";
        } else if (computer instanceof dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity) {
            kind = "Cluster Management Computer";
        } else if (computer instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity) {
            kind = "Server";
        } else {
            kind = "Personal Computer";
        }
        final dev.jstech.computronics.os.OsDef os = computer.installedOs();
        final String osLabel = os == null ? "" : os.displayName();
        final int osYear = os == null ? 0
                : dev.jstech.computronics.os.Branding.osYear(os.displayName(), os.minEra());
        final NetworkUuid network = computer.networkUuid();
        /*
         * Hardware by what is seated, read off the parts themselves so every computer type answers
         * the same way whatever its slot layout.
         */
        String board = "";
        String cpu = "";
        int cpus = 0;
        int gpus = 0;
        String psu = "";
        boolean valid = false;
        if (computer instanceof dev.jstech.computronics.blockentity.AbstractComputerBlockEntity be) {
            final net.neoforged.neoforge.items.ItemStackHandler hardware = be.getHardware();
            for (int i = 0; i < hardware.getSlots(); i++) {
                final net.minecraft.world.item.ItemStack part = hardware.getStackInSlot(i);
                if (part.isEmpty()) {
                    continue;
                }
                if (part.getItem() instanceof dev.jstech.computronics.item.MotherboardItem) {
                    board = part.getHoverName().getString();
                } else if (part.getItem() instanceof dev.jstech.computronics.item.CpuItem) {
                    cpus++;
                    if (cpu.isEmpty()) {
                        cpu = part.getHoverName().getString();
                    }
                } else if (part.getItem() instanceof dev.jstech.computronics.item.GpuItem) {
                    gpus++;
                } else if (part.getItem() instanceof dev.jstech.computronics.item.PsuItem) {
                    psu = part.getHoverName().getString();
                }
            }
            valid = be.buildValid();
        }
        final int mhz = computer.maxCpuMhz();
        if (!cpu.isEmpty() && mhz > 0) {
            cpu = cpu + " · " + (mhz >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f GHz", mhz / 1000.0) : mhz + " MHz");
        }
        // Linked peripherals by name, each kind counted once.
        final java.util.Map<String, Integer> peripherals = new java.util.LinkedHashMap<>();
        for (final long endpoint : computer.linkedEndpoints()) {
            final net.minecraft.world.level.block.entity.BlockEntity be =
                    level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint));
            final String label;
            if (be instanceof dev.jstech.computronics.os.media.MediaReaderBlockEntity reader) {
                label = switch (reader.driveType()) {
                    case FLOPPY_DRIVE -> "Floppy Drive";
                    case CD_DRIVE -> "CD Drive";
                    case DVD_DRIVE -> "DVD Drive";
                    case DOCK_STATION -> "Dock Station";
                };
            } else if (be instanceof dev.jstech.computronics.blockentity.MonitorBlockEntity) {
                label = "Monitor";
            } else if (be != null) {
                label = be.getBlockState().getBlock().getName().getString();
            } else {
                continue;
            }
            peripherals.merge(label, 1, Integer::sum);
        }
        final StringBuilder joined = new StringBuilder();
        for (final java.util.Map.Entry<String, Integer> e : peripherals.entrySet()) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            if (e.getValue() > 1) {
                joined.append(e.getValue()).append(" × ");
            }
            joined.append(e.getKey());
        }
        return new ThisPcPayload.WireMachine(computer.customName(), kind,
                dev.jstech.computronics.os.MinSpecTooltip.eraLabel(computer.displayEra()),
                osLabel, osYear, network == null ? "" : networkLabel(network), board, cpu, cpus,
                (int) Math.min(Integer.MAX_VALUE, computer.ramBuffer()), computer.totalVramMb(), gpus, psu,
                valid, joined.toString());
    }

    private static String joinPlain(final java.util.List<net.minecraft.network.chat.Component> lines) {
        final StringBuilder sb = new StringBuilder();
        for (final net.minecraft.network.chat.Component line : lines) {
            final String text = line.getString().trim();
            if (text.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(text);
        }
        return sb.length() > 190 ? sb.substring(0, 190) : sb.toString();
    }

    private static void handleEjectMedia(final EjectMediaPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os.IOsHost computer
                    && computer.linkedEndpoints().contains(payload.readerPos())
                    && level.getBlockEntity(net.minecraft.core.BlockPos.of(payload.readerPos()))
                            instanceof dev.jstech.computronics.os.media.MediaReaderBlockEntity reader) {
                final net.minecraft.world.item.ItemStack ejected = reader.ejectMedia();
                if (!ejected.isEmpty() && !player.addItem(ejected)) {
                    final net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.of(payload.readerPos());
                    net.minecraft.world.Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 1.0,
                            at.getZ() + 0.5, ejected);
                }
            }
        });
    }

    private static void handleThisPc(final ThisPcPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.ThisPcApp.accept(payload));
    }

    private static void handleInstallFromMedia(final InstallFromMediaPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer)) {
                return;
            }
            // The drive must be a media reader currently linked to this computer.
            if (!computer.linkedEndpoints().contains(payload.readerPos())
                    || !(level.getBlockEntity(net.minecraft.core.BlockPos.of(payload.readerPos()))
                            instanceof dev.jstech.computronics.os.media
                                    .MediaReaderBlockEntity reader)) {
                return;
            }
            if (reader.insertedKind()
                    != dev.jstech.computronics.os.media.MediaKind.PROGRAM_INSTALL) {
                return;
            }
            final net.minecraft.resources.ResourceLocation pl = reader.insertedPayload();
            // A program needs an installed OS to host it.
            if (pl == null || computer.installedOs() == null) {
                return;
            }
            /*
             * Program install gate: the OS platform must be supported and the hardware must meet the
             * program's CPU/VRAM/disk minimums (e.g. the NMS installs only on the Frames platform).
             */
            if (!dev.jstech.computronics.os.OsRegistry.canInstallProgram(
                    computer.installedOsId(), pl,
                    computer.maxCpuMhz(), computer.totalVramMb(), computer.systemDiskFreeMb())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "This program's platform or hardware requirements are not met by this computer."),
                        false);
                return;
            }
            /*
             * Host gate: a program bound to a specific computer (the Crafting Manager to a Crafting Computer,
             * the Mainframe services to a Mainframe) installs only there. Driven by the descriptor's host
             * scope, not a per-program check.
             */
            final dev.jstech.computronics.os.ProgramSpec spec =
                    dev.jstech.computronics.os.OsRegistry.getProgram(pl);
            if (!hostScopeAllows(spec, computer)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        hostScopeMessage(spec)), false);
                return;
            }
            /*
             * The Automation Engine is a Mainframe service: installing its floppy on the Mainframe turns the
             * job agent on.
             */
            if (pl.equals(Programs.AUTOMATION_ENGINE) && computer instanceof MainframeBlockEntity mainframe) {
                mainframe.installAutomationEngine();
            }
            // The Mirror is a Mainframe service too: its floppy turns the package repository on.
            if (pl.equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jsc", "mirror"))
                    && computer instanceof MainframeBlockEntity mainframe) {
                mainframe.installMirror();
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
            boolean busy = false;
            String prompt = "C:\\>";
            /* Set when the command was one that gives the terminal to an editor. */
            dev.jstech.computronics.program.cli.CliShell.HandOver handOver = null;
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host) {
                final var computer =
                        new dev.jstech.computronics.program.ServerCliComputer(host, level);
                /*
                 * A program has the terminal: everything typed goes to it, not to the shell, and what it
                 * printed since the last time keeps coming until it returns.
                 */
                final var running = computer.foreground();
                if (running != null) {
                    busy = drainForeground(running, payload.line(), wire);
                    context.reply(new DesktopShellOutputPayload(false, busy, computer.prompt(), wire));
                    return;
                }
                final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(
                        computer, CLI_WIDTH);
                final var response = shell.run(payload.line(), computer);
                clear = response.clearScreen();
                handOver = response.handOver();
                for (final var cliLine : response.lines()) {
                    wire.add(new DesktopShellOutputPayload.WireLine(cliLine.text(), cliLine.style().ordinal()));
                }
                prompt = computer.prompt();
                /*
                 * The command just run may have been one that starts a program at this terminal, in
                 * which case the prompt does not come back with this reply.
                 */
                busy = computer.foreground() != null;
                /*
                 * The reboot verbs work from the desktop's terminal window too: the desktop closes and the
                 * monitor either replays the POST (plain reboot) or enters the firmware setup.
                 */
                final BlockPos monitorPos = player.containerMenu
                        instanceof dev.jstech.computronics.menu.DesktopMenu desktop
                        ? desktop.monitorPos() : null;
                if (monitorPos != null && computer.firmwareRebootRequested()) {
                    player.closeContainer();
                    dev.jstech.computronics.block.MonitorBlock.openFirmware(
                            player, level, monitorPos, payload.hostPos());
                    return;
                }
                if (monitorPos != null && computer.rebootRequested()) {
                    if (level.getBlockEntity(payload.hostPos()) instanceof dev.jstech.computronics.os.IOsHost be) {
                        be.setNeedsPost(true);
                    }
                    player.closeContainer();
                    dev.jstech.computronics.block.MonitorBlock.openPost(
                            player, level, monitorPos, payload.hostPos());
                    return;
                }
            }
            context.reply(new DesktopShellOutputPayload(clear, busy, prompt, wire,
                    handOver == null ? "" : handOver.editor(),
                    handOver == null ? "" : handOver.path()));
        });
    }

    /**
     * What the shell sends when the player asks the program in front to stop.
     *
     * <p>The value is the single byte U+0003, the one a terminal has always sent for this and one no
     * keyboard puts into a line of text, so nothing a player writes can be mistaken for it. An empty
     * line means something else entirely: the shell asking whether there is more output to show.
     */
    public static final String INTERRUPT = "";

    /**
     * Answers a line typed while a program has the terminal, and says whether it still has it.
     *
     * <p>What a program prints reaches the terminal from the machine's own tick, so nothing is
     * collected here: this is only the keyboard, and while a program is in front the one thing the
     * keyboard can say to it is to stop.
     */
    private static boolean drainForeground(
            final dev.jstech.computronics.cannon.machine.MachinePrograms processes, final String typed,
            final java.util.List<DesktopShellOutputPayload.WireLine> wire) {
        if (!INTERRUPT.equals(typed)) {
            return true;
        }
        final int id = processes.held();
        processes.release();
        processes.stop(id);
        wire.add(new DesktopShellOutputPayload.WireLine("^C",
                dev.jstech.computronics.program.cli.CliStyle.DIM.ordinal()));
        return false;
    }

    private static void handleDesktopShellOutput(final DesktopShellOutputPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            /*
             * A computer has one console and this is what it said, so it goes to every window looking at
             * it: the terminal window, an editor's terminal panel, and the Network Interactor's own
             * embedded command line. Each ignores it when it is not open.
             */
            dev.jstech.computronics.client.os.ShellViews.accept(payload);
            dev.jstech.computronics.client.os.NetworkInteractorApp.acceptConsole(payload);
        });
    }

    private static void handleSaveFile(final SaveFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            boolean ok = false;
            String msg = "No computer";
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer) {
                final String path = payload.path();
                final boolean media = path.startsWith("media:");
                final net.minecraft.world.item.ItemStack vol =
                        media ? mediaStackFor(level, computer, path) : computer.systemDisk();
                final String real = media ? mediaSubPath(path) : path;
                final dev.jstech.computronics.os.FilesystemKind kind = media
                        ? dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL
                        : filesystemKindOf(computer);
                final int dot = real.lastIndexOf('.');
                final String ext = dot >= 0 && dot < real.length() - 1
                        ? real.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
                final dev.jstech.computronics.os.fs.FileType type =
                        dev.jstech.computronics.os.fs.FileType.fromExtension(ext).orElse(null);
                if (vol.isEmpty()
                        || kind == dev.jstech.computronics.os.FilesystemKind.NONE) {
                    msg = media ? "No medium" : "No system disk";
                } else if (type == null) {
                    msg = "Unknown type (.txt/.iql/.cfg/.csv/.cmd)";
                } else if (!type.userEditable()) {
                    msg = "." + type.extension() + " is read-only";
                } else {
                    final long oldWeight = dev.jstech.computronics.os.fs.DiskFilesystem
                            .read(vol, real)
                            .map(c -> dev.jstech.computronics.os.fs.FsPaths.sizeMbEq(
                                    c.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                                    dev.jstech.computronics.os.fs.DiskFilesystem.eraOf(vol)))
                            .orElse(0L);
                    final long free = media ? mediaFreeWeight(vol) + oldWeight
                            : computer.systemDiskFreeWeight() + oldWeight;
                    final var result = dev.jstech.computronics.os.fs.DiskFilesystem.write(
                            vol, real, type, payload.content(), free, kind, level.getGameTime());
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
        context.enqueueWork(() -> {
            // Whoever asked for the save said so first; a result nobody is waiting for has no window left.
            dev.jstech.computronics.client.os.CodeFileReplies.saved(payload.ok(), payload.message());
        });
    }

    private static void handleDeleteFile(final DeleteFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer)) {
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
            final dev.jstech.computronics.os.FilesystemKind kind = media
                    ? dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            // Try removing a real file first; if the path is a folder, remove it recursively.
            if (dev.jstech.computronics.os.fs.DiskFilesystem.delete(vol, real)
                    || dev.jstech.computronics.os.fs.DiskFilesystem.rmdir(vol, real, kind)) {
                if (media) {
                    commitMedia(level, computer, path);
                } else {
                    computer.setChanged();
                }
            }
        });
    }

    /** Resolves the filesystem kind of the computer's installed OS, or NONE when absent. */
    static dev.jstech.computronics.os.FilesystemKind filesystemKindOf(
            final dev.jstech.computronics.os.IOsHost computer) {
        final dev.jstech.computronics.os.OsDef os = computer.installedOs();
        if (os == null) {
            return dev.jstech.computronics.os.FilesystemKind.NONE;
        }
        final dev.jstech.computronics.os.KernelDef kernel =
                dev.jstech.computronics.os.OsRegistry.getKernel(os.kernelId());
        return kernel != null ? kernel.filesystem()
                : dev.jstech.computronics.os.FilesystemKind.NONE;
    }

    /**
     * Computes the available free weight on the given disk, mirroring the formula used in
     * {@link dev.jstech.computronics.os.IOsHost#installOs}:
     * capacity minus storage used minus filesystem used minus the OS footprint.
     */
    private static long computeDiskFreeWeight(
            final dev.jstech.computronics.os.IOsHost computer,
            final net.minecraft.world.item.ItemStack disk) {
        if (!(disk.getItem() instanceof dev.jstech.computronics.item.DiskItem diskItem)) {
            return 0L;
        }
        final long capacityWeight = diskItem.spec().capacityItems()
                * dev.jstech.computronics.storage.StorageKey.MB_EQ_PER_ITEM;
        final long storageUsed = dev.jstech.computronics.storage.DriveVolumes.usedWeight(disk);
        final long fsUsed = dev.jstech.computronics.os.fs.DiskFilesystem.filesWeight(disk);
        final long osReserved = computer.reservedByOs()
                * dev.jstech.computronics.storage.StorageKey.MB_EQ_PER_ITEM;
        return Math.max(0L, capacityWeight - storageUsed - fsUsed - osReserved);
    }

    private static void handleMkdir(final MkdirPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer)) {
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
            final dev.jstech.computronics.os.FilesystemKind kind = media
                    ? dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            if (dev.jstech.computronics.os.fs.DiskFilesystem.mkdir(vol, real, kind)) {
                if (media) {
                    commitMedia(level, computer, path);
                } else {
                    computer.setChanged();
                }
            }
        });
    }

    /**
     * Copies a file within a volume or across to another one; the source stays. A projected file has no
     * bytes and is refused by the read; a name already taken gets a numbered copy rather than overwriting.
     */
    private static void handleCopyFile(final CopyFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os.IOsHost computer)) {
                return;
            }
            final String src = payload.src();
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
            final var read = dev.jstech.computronics.os.fs.DiskFilesystem.read(srcVol, realSrc);
            if (read.isEmpty()) {
                return;
            }
            final String name = realSrc.contains("/") ? realSrc.substring(realSrc.lastIndexOf('/') + 1) : realSrc;
            final int dot = name.lastIndexOf('.');
            final String stem = dot > 0 ? name.substring(0, dot) : name;
            final String ext = dot >= 0 && dot < name.length() - 1
                    ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
            final dev.jstech.computronics.os.fs.FileType type =
                    dev.jstech.computronics.os.fs.FileType.fromExtension(ext)
                            .orElse(dev.jstech.computronics.os.fs.FileType.TXT);
            final dev.jstech.computronics.os.FilesystemKind dstKind = dstMedia
                    ? dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            // "name - Copy.ext", then "name - Copy (2).ext", the way a desktop names a duplicate.
            String candidate = name;
            final String suffix = ext.isEmpty() ? "" : "." + ext;
            for (int n = 1; n < 100; n++) {
                final String path = realDstDir.isEmpty() ? candidate : realDstDir + "/" + candidate;
                if (!dev.jstech.computronics.os.fs.DiskFilesystem.exists(dstVol, path)) {
                    break;
                }
                candidate = stem + (n == 1 ? " - Copy" : " - Copy (" + n + ")") + suffix;
            }
            final String destPath = realDstDir.isEmpty() ? candidate : realDstDir + "/" + candidate;
            final long free = dstMedia ? mediaFreeWeight(dstVol) : computer.systemDiskFreeWeight();
            if (dev.jstech.computronics.os.fs.DiskFilesystem.write(
                    dstVol, destPath, type, read.get(), free, dstKind, level.getGameTime())
                    == dev.jstech.computronics.os.fs.DiskFilesystem.WriteResult.OK) {
                if (dstMedia) {
                    commitMedia(level, computer, destDir);
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
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer)) {
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
            final dev.jstech.computronics.os.FilesystemKind srcKind = srcMedia
                    ? dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            if (volumeKey(src).equals(volumeKey(destDir))) {
                // Same volume, an in-place move.
                if (dev.jstech.computronics.os.fs.DiskFilesystem.move(
                        srcVol, realSrc, realDstDir, srcKind)) {
                    if (srcMedia) {
                        commitMedia(level, computer, src);
                    } else {
                        computer.setChanged();
                    }
                }
                return;
            }
            /*
             * Cross-volume (disk <-> media): copy the file then delete the source. Directories are
             * not copied across volumes here.
             */
            final var read = dev.jstech.computronics.os.fs.DiskFilesystem.read(srcVol, realSrc);
            if (read.isEmpty()) {
                return;
            }
            final String name = realSrc.contains("/")
                    ? realSrc.substring(realSrc.lastIndexOf('/') + 1) : realSrc;
            final int dot = name.lastIndexOf('.');
            final String ext = dot >= 0 && dot < name.length() - 1
                    ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
            final dev.jstech.computronics.os.fs.FileType type =
                    dev.jstech.computronics.os.fs.FileType.fromExtension(ext)
                            .orElse(dev.jstech.computronics.os.fs.FileType.TXT);
            final dev.jstech.computronics.os.FilesystemKind dstKind = dstMedia
                    ? dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            final String destPath = realDstDir.isEmpty() ? name : realDstDir + "/" + name;
            final long free = dstMedia ? mediaFreeWeight(dstVol) : computer.systemDiskFreeWeight();
            if (dev.jstech.computronics.os.fs.DiskFilesystem.write(
                    dstVol, destPath, type, read.get(), free, dstKind, level.getGameTime())
                    == dev.jstech.computronics.os.fs.DiskFilesystem.WriteResult.OK) {
                dev.jstech.computronics.os.fs.DiskFilesystem.delete(srcVol, realSrc);
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
     * removable medium does not copy a file; it moves the stored item. The flow is conservative end to end so
     * an item is never lost or duplicated:
     * <ol>
     *   <li>Resolve {@code datPath} back to its {@link StorageKey} by re-projecting the system disk (the
     *       projection is deterministic, so the same path maps back to the same key).</li>
     *   <li>Extract the full stored quantity of that key from the computer's local storage.</li>
     *   <li>Insert as much as the medium's free capacity allows into its {@code MEDIA_DATA} snapshot; whatever
     *       does not fit is returned to the computer's storage.</li>
     * </ol>
     * The source {@code .dat} vanishes on its own once the key leaves the disk's volume, and the item then
     * shows up under the medium's projection, with no second item-movement path, no byte copy.
     */
    private static void handleMediumTransfer(final MediumTransferPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.hostPos(), payload.monitorPos());
            if (host == null
                    || !(host instanceof dev.jstech.computronics.os
                            .IOsHost computer)) {
                return;
            }
            // The destination must be a DATA medium in a linked drive; anything else cannot hold a snapshot.
            final String mediaKey = payload.mediaVolumeKey();
            if (!mediaKey.startsWith("media:")) {
                return;
            }
            final net.minecraft.world.item.ItemStack media = mediaStackFor(level, computer, mediaKey);
            if (media.isEmpty()
                    || dev.jstech.computronics.os.media.MediaItem.kind(media)
                            != dev.jstech.computronics.os.media.MediaKind.DATA) {
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
     * extracted, capped by what fits, so the sum across the two stores is invariant, and nothing is created or
     * destroyed. Returns the number of native units actually moved.
     *
     * <p>Exposed so it can be exercised directly by a GameTest with real component stacks, without a
     * player/menu round-trip.
     */
    public static long transferDatToMedium(final IComputerTerminalHost host, final StorageKey key,
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
                    dev.jstech.computronics.os.media.MediaItem.data(media).items());
            next.merge(key, extracted, Long::sum);
            dev.jstech.computronics.os.media.MediaItem.setData(media,
                    new dev.jstech.computronics.storage.ServerStorageContents(next));
        } catch (final RuntimeException e) {
            /*
             * The medium write failed after the items already left local storage; put them back so the
             * exceptional path still conserves items (nothing lost), then rethrow.
             */
            host.localStore().insert(key, extracted);
            throw e;
        }
        return extracted;
    }

    /**
     * Resolves a {@code .dat} path back to the {@link StorageKey} it projects, by re-running the deterministic
     * {@link dev.jstech.computronics.os.fs.StorageProjection} over the disk's storage volume
     * and matching the requested path. Returns {@code null} when no projected entry matches (e.g. a stale path).
     */
    @org.jetbrains.annotations.Nullable
    public static StorageKey resolveDatKey(final net.minecraft.world.item.ItemStack disk, final String datPath) {
        if (disk.isEmpty()) {
            return null;
        }
        final dev.jstech.computronics.storage.ServerStorageContents storage =
                dev.jstech.computronics.storage.DriveVolumes.contents(disk);
        /*
         * The projection emits one entry per key in iteration order, with the same path each time; pair each
         * emitted path with the storage key at the same position to invert the path back to its key.
         */
        final java.util.List<dev.jstech.computronics.os.fs.DiskFilesystem.FileEntry> entries =
                dev.jstech.computronics.os.fs.StorageProjection.project(storage);
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
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer)) {
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
            dev.jstech.computronics.os.VolumeLabel.set(vol, payload.label());
            if (media) {
                commitMedia(level, computer, key);
            } else {
                computer.setChanged();
            }
        });
    }

    /**
     * Reads a whole folder of one kind of file at once.
     *
     * <p>An editor that reports on a program's neighbours has to read them all, and twenty files one at
     * a time is twenty round trips for what is really one question. Only the machine's own disk is read,
     * because a removable medium is browsed rather than compiled against.
     */
    private static void handleRequestFolderContent(final RequestFolderContentPayload payload,
                                                   final IPayloadContext context) {
        context.enqueueWork(() -> {
            final java.util.List<FolderContentPayload.WireFile> files = new java.util.ArrayList<>();
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os.IOsHost computer) {
                final net.minecraft.world.item.ItemStack disk = computer.systemDisk();
                final dev.jstech.computronics.os.FilesystemKind kind = filesystemKindOf(computer);
                if (!disk.isEmpty() && kind != dev.jstech.computronics.os.FilesystemKind.NONE) {
                    final String suffix = payload.extension().toLowerCase(java.util.Locale.ROOT);
                    for (final dev.jstech.computronics.os.fs.DiskFilesystem.FileEntry entry
                            : dev.jstech.computronics.os.fs.DiskFilesystem.list(disk, payload.dir(), kind)) {
                        if (files.size() >= FolderContentPayload.MAX_FILES) {
                            break;
                        }
                        if (!entry.path().toLowerCase(java.util.Locale.ROOT).endsWith(suffix)) {
                            continue;
                        }
                        final String text = dev.jstech.computronics.os.fs.DiskFilesystem
                                .read(disk, entry.path()).orElse("");
                        /*
                         * A file too long for one reply is left out rather than cut: half a program
                         * would compile to complaints that are the payload's fault, not the player's.
                         */
                        if (text.length() <= FolderContentPayload.MAX_TEXT) {
                            files.add(new FolderContentPayload.WireFile(entry.path(), text));
                        }
                    }
                }
            }
            context.reply(new FolderContentPayload(payload.dir(), files));
        });
    }

    private static void handleFolderContent(final FolderContentPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.CodeFileReplies.folder(payload));
    }

    private static void handleRequestFileContent(final RequestFileContentPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            String content = "";
            boolean exists = false;
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer) {
                final java.util.Optional<String> read = readDiskFile(level, computer, payload.path());
                if (read.isPresent()) {
                    content = read.get();
                    exists = true;
                }
            }
            context.reply(new FileContentPayload(payload.path(), content, exists));
        });
    }

    /**
     * Reads a file the file explorer named, which is a path from the root of a disk rather than one
     * relative to wherever a shell happens to be.
     */
    private static java.util.Optional<String> readDiskFile(
            final ServerLevel level, final dev.jstech.computronics.os.IOsHost computer, final String path) {
        final boolean media = path.startsWith("media:");
        final net.minecraft.world.item.ItemStack vol =
                media ? mediaStackFor(level, computer, path) : computer.systemDisk();
        if (vol.isEmpty()) {
            return java.util.Optional.empty();
        }
        /*
         * A projected file on an installer (its readme, manifest or autorun) has no stored bytes to
         * read: its text is generated from the medium's stamp.
         */
        final java.util.Optional<String> projected = media
                ? dev.jstech.computronics.os.media.InstallerProjection.text(vol, mediaSubPath(path))
                : java.util.Optional.empty();
        return projected.isPresent() ? projected
                : dev.jstech.computronics.os.fs.DiskFilesystem.read(vol, media ? mediaSubPath(path) : path);
    }

    private static void handleRunProgram(final RunProgramPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.blockentity.AbstractComputerBlockEntity computer)) {
                return;
            }
            final String name = dev.jstech.computronics.os.fs.FsPaths.fileName(payload.path());
            final java.util.List<DesktopShellOutputPayload.WireLine> wire = new java.util.ArrayList<>();
            final java.util.Optional<String> listing = readDiskFile(level, computer, payload.path());
            if (listing.isEmpty()) {
                wire.add(new DesktopShellOutputPayload.WireLine(name + ": file not found",
                        dev.jstech.computronics.program.cli.CliStyle.ERROR.ordinal()));
                PacketDistributor.sendToPlayer(player,
                        new DesktopShellOutputPayload(false, false, "", wire));
                return;
            }
            final int room = dev.jstech.computronics.cannon.machine.MachinePrograms.DEFAULT_HEAP_MB;
            if (!computer.ramLedger().fits(room)) {
                wire.add(new DesktopShellOutputPayload.WireLine(name + ": not enough memory to run it",
                        dev.jstech.computronics.program.cli.CliStyle.ERROR.ordinal()));
                PacketDistributor.sendToPlayer(player,
                        new DesktopShellOutputPayload(false, false, "", wire));
                return;
            }
            final var started = computer.cannon().start(name, listing.get(), room, computer);
            if (!started.ok()) {
                wire.add(new DesktopShellOutputPayload.WireLine(started.message(),
                        dev.jstech.computronics.program.cli.CliStyle.ERROR.ordinal()));
                PacketDistributor.sendToPlayer(player,
                        new DesktopShellOutputPayload(false, false, "", wire));
                return;
            }
            computer.setChanged();
            final var one = computer.cannon().byId(started.id());
            final boolean console = one != null && !one.process().isService();
            if (console) {
                computer.cannon().hold(started.id());
            } else {
                wire.add(new DesktopShellOutputPayload.WireLine(started.message(),
                        dev.jstech.computronics.program.cli.CliStyle.OK.ordinal()));
            }
            PacketDistributor.sendToPlayer(player,
                    new DesktopShellOutputPayload(false, console, "", wire));
        });
    }

    private static void handleFileContent(final FileContentPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            /*
             * Every window that opens a file says it is waiting for that file, by name, before it asks.
             * An answer nobody is waiting for belongs to a window that has since closed, and is dropped.
             */
            dev.jstech.computronics.client.os.CodeFileReplies.content(
                    payload.path(), payload.content(), payload.exists());
        });
    }

    private static void handleRenameFile(final RenameFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer)) {
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
            final dev.jstech.computronics.os.FilesystemKind kind = media
                    ? dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL
                    : filesystemKindOf(computer);
            // rename() re-keys a real file or directory in place (rejecting .dat projections).
            if (dev.jstech.computronics.os.fs.DiskFilesystem.rename(vol,
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
            } else {
                // The desktop Craft Planner has no container menu; route the catalogue to it.
                dev.jstech.computronics.client.os.CraftPlannerApp.acceptCatalog(payload.entries());
            }
        });
    }

    private static void handleCraftPlan(final CraftPlanPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setCraftPlan(payload);
            } else {
                // The desktop Network Interactor has no container menu; route the plan to its craft popup.
                dev.jstech.computronics.client.os.NetworkInteractorApp.acceptCraftPlan(payload);
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
        final var stock = dev.jstech.computronics.operation.NetworkStorage
                .of(level, net).query();
        // "Any" cells are judged against what the network holds, the way a craft would resolve them.
        final var patterns = dev.jstech.computronics.crafting.AnyTagResolver
                .resolveAll(mainframe.networkPatterns(), stock);
        final java.util.Map<StorageKey, CraftCatalogPayload.Entry> entries = new java.util.LinkedHashMap<>();
        for (final var pattern : patterns) {
            final StorageKey key = StorageKey.of(pattern.result());
            if (entries.containsKey(key)) {
                continue;
            }
            final byte dot;
            if (dev.jstech.computronics.crafting.CraftPlanner
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
            entries.put(key, new CraftCatalogPayload.Entry(pattern.result().copy(), dot,
                    mainframe.hasMultiStageRecipe(key), wire(pattern.name(), CraftCatalogPayload.MAX_LABEL)));
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
            final String label = recipe.proc().map(p -> p.name()).orElse(recipe.multi().map(m -> m.name()).orElse(""));
            entries.put(key, new CraftCatalogPayload.Entry(result, dot, recipe.multi().isPresent(),
                    wire(label, CraftCatalogPayload.MAX_LABEL)));
        }
        return java.util.List.copyOf(entries.values());
    }

    private static void handleCraftPlanRequest(final CraftPlanRequestPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            final IComputerTerminalHost host = craftHost(context, payload.monitorPos(), payload.hostPos());
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
            final var machines = mainframe.networkProcessingPatterns();
            final var stock = dev.jstech.computronics.operation.NetworkStorage
                    .of(level, host.networkUuid()).query();
            final StorageKey key = StorageKey.of(payload.result());
            /*
             * A machine recipe whose inputs are in stock, or a multi-stage pipeline, plans by its own inputs.
             * Otherwise the recursive planner expands bench and machine patterns alike, so a machine-made
             * ingredient shows up as the raw materials of its own recipe rather than as missing.
             */
            final var machinePlan = planMachineRecipe(mainframe, key, payload.quantity(), stock);
            if (machinePlan != null && (machinePlan.feasible() || !machinePlan.plainMachine())) {
                PacketDistributor.sendToPlayer(player, new CraftPlanPayload(
                        payload.result(), payload.quantity(), machinePlan.rows(),
                        machinePlan.feasible(), machinePlan.maxFeasible(), machinePlan.estimateTicks()));
                return;
            }
            /*
             * The recursive plan is CPU work over immutable inputs: it runs on a virtual thread and the reply
             * goes out from the main thread when it is ready (the dialog shows "planning..." meanwhile). Without
             * a dispatcher the plan is made here and now instead.
             */
            final long quantity = payload.quantity();
            final ItemStack result = payload.result();
            final java.util.function.Supplier<PlanPreview> preview =
                    () -> planPreview(key, quantity, patterns, machines, stock);
            final java.util.function.Consumer<PlanPreview> reply = made ->
                    PacketDistributor.sendToPlayer(player, new CraftPlanPayload(result, quantity, made.rows(),
                            made.feasible(), made.maxFeasible(), estimateTicks(level, mainframe, made.plan())));
            final boolean queued = mainframe.submitOperation(task -> {
                final PlanPreview made = preview.get();
                task.onMainThread(() -> reply.accept(made));
                return dev.jstech.core.operation.IOperationResult.success();
            }, dev.jstech.core.operation.OperationPriority.MEDIUM);
            if (!queued) {
                reply.accept(preview.get());
            }
        });
    }

    /** A plan preview: the raw-ingredient rows (need vs have), whether it is feasible, and how many are. */
    private record PlanPreview(dev.jstech.computronics.crafting.CraftPlanner.Plan plan,
                               java.util.List<CraftPlanPayload.Row> rows, boolean feasible, long maxFeasible) {
    }

    /** Plans {@code quantity} of {@code key} and shapes the dialog's rows; pure over its inputs. */
    private static PlanPreview planPreview(final StorageKey key, final long quantity,
                                           final java.util.List<dev.jstech.computronics.crafting.CraftingPattern> patterns,
                                           final java.util.List<dev.jstech.computronics.crafting.ProcessingPattern> machines,
                                           final java.util.Map<StorageKey, Long> stock) {
        final var plan = dev.jstech.computronics.crafting.CraftPlanner.plan(key, quantity, patterns, machines, stock);
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
        final long maxFeasible = feasible ? quantity
                : dev.jstech.computronics.crafting.CraftPlanner.maxFeasible(key, quantity, patterns, machines, stock);
        return new PlanPreview(plan, java.util.List.copyOf(rows), feasible, maxFeasible);
    }

    private static int estimateTicks(final ServerLevel level, final MainframeBlockEntity mainframe,
                                     final dev.jstech.computronics.crafting.CraftPlanner.Plan plan) {
        long units = 0;
        long machineTicks = 0;
        for (final var step : plan.steps()) {
            units += step.runs() * step.unitsPerRun();
            if (step.isMachine()) {
                machineTicks += step.machine().timeoutTicks();
            }
        }
        long rate = 0;
        for (final net.minecraft.core.BlockPos pos : mainframe.craftingComputerPositions()) {
            if (level.getBlockEntity(pos)
                    instanceof dev.jstech.computronics.blockentity.CraftingComputerBlockEntity cc
                    && cc.canCraft()) {
                rate = Math.max(rate, cc.craftingThroughput());
            }
        }
        if (rate <= 0) {
            return 0;
        }
        return (int) Math.max(1, (units + rate - 1) / rate + machineTicks);
    }

    /** A machine recipe's plan for the request popup: raw rows (need vs have), feasibility, max and estimate. */
    private record MachinePlan(java.util.List<CraftPlanPayload.Row> rows, boolean feasible, long maxFeasible,
                               int estimateTicks, boolean plainMachine) {
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
            final dev.jstech.computronics.crafting.ProcessingPattern first;
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
                    estimate += s.proc().map(dev.jstech.computronics.crafting.ProcessingPattern
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
                            Math.min(quantity, forwardYield(multi, maxFirst)), estimate, false);
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
                    Math.min(quantity, maxFinal), estimate, recipe.proc().isPresent());
        }
        return null;
    }

    private static long ceilDiv(final long amount, final long perRun) {
        return (amount + perRun - 1) / perRun;
    }

    /** How much of the final result a pipeline yields when its first stage produces {@code firstOutput}. */
    private static long forwardYield(final dev.jstech.computronics.crafting.MultiStagePattern multi,
                                     final long firstOutput) {
        // Walk the stage demands for one unit of final result to get each stage's output per final unit.
        final long[] perUnit = multi.stageDemands(1);
        return perUnit.length == 0 || perUnit[0] <= 0 ? firstOutput : firstOutput / perUnit[0];
    }

    private static void handleCraftSubmit(final CraftSubmitPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final IComputerTerminalHost host = craftHost(context, payload.monitorPos(), payload.hostPos());
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
            /*
             * The shared entry point runs a machine or multi-stage recipe directly, else plans a recursive
             * craft; onSettle refreshes the screen when it settles, and refresh.run() updates it now. The
             * multiStage flag picks the pipeline over the flat recursive path when a result has both.
             */
            final var op = mainframe.submitCraftRequest(resultKey, payload.quantity(), payload.partial(),
                    host.originLabel(MoveLabels.TERMINAL), refresh, payload.multiStage());
            if (op != null) {
                op.setPriority(payload.priority());
            }
            refresh.run();
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

    private static void handleRackBayPower(final RackBayPowerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.rackPos())
                            instanceof dev.jstech.computronics.blockentity
                                    .ServerRackBlockEntity rack
                    // Only a player actually standing at the open rack GUI may flip its switches.
                    && player.containerMenu
                            instanceof dev.jstech.computronics.menu.ServerRackMenu) {
                rack.toggleBayPower(payload.slot());
            }
        });
    }

    /**
     * The machine an open ssh session points at, or null when the line must run locally. {@code ssh}
     * and {@code exit} always run on the local terminal: one opens the session, the other closes it.
     * A session whose machine went away (broken, unpowered) is dropped, so the shell falls back home
     * instead of talking to a ghost.
     */
    @org.jetbrains.annotations.Nullable
    private static dev.jstech.computronics.terminal.IComputerTerminalHost sshTargetOf(
            final dev.jstech.computronics.terminal.IComputerTerminalHost host,
            final ServerLevel level, final String line) {
        final var console = host.console();
        if (console == null || console.sshTarget() == null) {
            return null;
        }
        final String verb = line.trim().split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (verb.equals("ssh") || verb.equals("exit") || verb.equals("logout")) {
            return null;
        }
        final var target = level.getBlockEntity(BlockPos.of(console.sshTarget()));
        if (target instanceof dev.jstech.computronics.terminal.IComputerTerminalHost remote
                && remote.computerRunning()) {
            return remote;
        }
        console.setSshTarget(null);
        return null;
    }

    private static void handleRemoteControl(final RemoteControlPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.terminal
                                    .IComputerTerminalHost host)) {
                return;
            }
            final var cli = new dev.jstech.computronics.program.ServerCliComputer(host, level);
            if (payload.action() == RemoteControlPayload.ACTION_LIST) {
                final List<RemoteHostsPayload.Entry> entries = new ArrayList<>();
                cli.remoteMachines().forEach((hostname, machine) -> {
                    if (entries.size() >= RemoteHostsPayload.MAX_HOSTS) {
                        return;
                    }
                    final var remote = new dev.jstech.computronics.program
                            .ServerCliComputer(
                            (dev.jstech.computronics.terminal.IComputerTerminalHost) machine,
                            level);
                    final var os = machine instanceof dev.jstech.computronics.os.IOsHost h
                            ? h.installedOs() : null;
                    entries.add(new RemoteHostsPayload.Entry(machine.getBlockPos().asLong(), hostname,
                            remote.type(), os == null ? "" : os.displayName(), remote.running()));
                });
                PacketDistributor.sendToPlayer(player, new RemoteHostsPayload(entries));
                return;
            }
            /*
             * Take over: put the chosen machine's own session on this monitor, exactly as walking to
             * it would. Reachability is re-checked here so a stale window cannot reach off-network.
             */
            final BlockPos target = BlockPos.of(payload.targetPos());
            final boolean reachable = cli.remoteMachines().values().stream()
                    .anyMatch(machine -> machine.getBlockPos().equals(target));
            if (!reachable) {
                return;
            }
            /*
             * Mark the screen as showing the remote machine BEFORE opening it: every menu validates
             * through the monitor, and without this the new session is torn down on its first tick
             * for showing a computer the cable does not link.
             */
            if (level.getBlockEntity(payload.monitorPos())
                    instanceof dev.jstech.computronics.blockentity.MonitorBlockEntity monitor) {
                monitor.setRemoteSession(target);
            }
            player.closeContainer();
            dev.jstech.computronics.block.MonitorBlock.bootOrPost(
                    player, level, payload.monitorPos(), target);
        });
    }

    private static void handleRemoteHosts(final RemoteHostsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.RemoteControlApp.accept(payload));
    }

    private static void handleOpenKvm(final OpenKvmPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.block.IKvmScreenOpener.Holder.open(payload));
    }

    private static void handleKvmSelect(final KvmSelectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.rackPos())
                            instanceof dev.jstech.computronics.blockentity
                                    .ServerRackBlockEntity rack)) {
                return;
            }
            // The switch has to be there for the monitor to address a bay at all.
            if (rack.computerSlots().size() > 1 && !rack.hasKvmSwitch()) {
                return;
            }
            rack.setActiveChannel(payload.slot());
            // With the channel set, the rack answers as that machine: start its session.
            dev.jstech.computronics.block.MonitorBlock.openSelectedChannel(
                    player, level, payload.monitorPos(), payload.rackPos());
        });
    }

    private static void handleMachinePower(final MachinePowerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.os.IOsHost computer)) {
                return;
            }
            /*
             * The screen closes either way: a machine that just powered off has nothing to show, and
             * a restart comes back through the power-on self-test like any other cold start.
             */
            player.closeContainer();
            switch (payload.action()) {
                case MachinePowerPayload.ACTION_SHUTDOWN -> computer.setPowered(false);
                case MachinePowerPayload.ACTION_RESTART -> {
                    computer.setPowered(false);
                    computer.setPowered(true);
                    dev.jstech.computronics.block.MonitorBlock.openPost(
                            player, level, payload.monitorPos(), payload.hostPos());
                }
                default -> {
                    // Logging off leaves the machine running; the screen is already closed.
                }
            }
        });
    }

    private static void handleRenamePc(final RenamePcPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && hasOpenAssemblyFor(player, payload.pcPos())
                    && player.level().getBlockEntity(payload.pcPos())
                            instanceof dev.jstech.computronics.os.IOsHost computer) {
                computer.setCustomName(payload.name());
            }
        });
    }

    private static boolean hasOpenAssemblyFor(final ServerPlayer player, final net.minecraft.core.BlockPos pos) {
        if (player.containerMenu instanceof PersonalComputerMenu menu) {
            return menu.pcPos().equals(pos);
        }
        if (player.containerMenu instanceof dev.jstech.computronics.menu.CraftingComputerMenu menu) {
            return menu.computerPos().equals(pos);
        }
        /*
         * A supercomputer node is a rack computer now: it is renamed through the Server assembly GUI
         * like any other server, so it has no assembly menu of its own to check here.
         */
        return false;
    }

    private static void handleLocalUpload(final TerminalLocalUploadPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final IComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
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
            /*
             * Take the items out of local storage and carry them in the Operation; whatever the network
             * cannot hold is returned to local storage when it settles, so nothing is ever lost.
             */
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
                    instanceof dev.jstech.computronics.menu.ServerAssemblyMenu menu) {
                menu.setServerName(payload.name());
            }
        });
    }

    // Network-operation dispatch: the ONLY way storage is touched. Every request

    private static void returnToPlayer(final ServerPlayer player, final ItemStack stack) {
        DataHandoff.returnToPlayer(player, stack);
    }

    public static void dispatchQuery(final ServerPlayer player, final PersonalComputerBlockEntity pc) {
        dispatch(player, pc, (level, net, mf) -> mf.submitOperation(
                new dev.jstech.computronics.operation.NetworkQueryOperationTask(level, net, player),
                dev.jstech.core.operation.OperationPriority.MEDIUM));
    }

    @FunctionalInterface
    private interface IOperationSubmit {
        boolean submit(ServerLevel level, NetworkUuid net, MainframeBlockEntity mainframe);
    }

    private static boolean dispatch(final ServerPlayer player, final PersonalComputerBlockEntity pc,
                                    final IOperationSubmit submit) {
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
            final IComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
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
            final dev.jstech.computronics.operation.NetworkIndex index = mainframe.networkIndex();
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
                    /*
                     * The disks are read now; the catalog is built off the tick and swapped in later, when
                     * the run is logged and the grid refreshed.
                     */
                    final ItemStack reindexIcon = labelledIcon(Items.COMPASS, "index");
                    mainframe.reindexAsync(() -> {
                        mainframe.recordOperation(OperationRecord.TYPE_REINDEX, reindexIcon, index.catalogSize(),
                                index.catalogSize(), OperationRecord.STATUS_COMPLETED, java.util.List.of());
                        player.displayClientMessage(Component.literal("REINDEX complete - catalog rebuilt from disks"),
                                true);
                        dispatchTerminalQuery(player, net, level);
                    });
                    player.displayClientMessage(Component.literal("REINDEX started - rebuilding the catalog from disks"),
                            true);
                    return;
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
            dispatchTerminalQuery(player, net, level); // the catalog may have changed, so refresh the grid
        });
    }

    private static void handleTerminalDrop(final TerminalDropPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final IComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
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
            final dev.jstech.computronics.operation.NetworkIndex index = mainframe.networkIndex();
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

    static MainframeBlockEntity resolveMainframe(final ServerLevel level, final NetworkUuid network) {
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
                    instanceof dev.jstech.computronics.menu.ComputerTerminalMenu terminal) {
                terminal.setNetworkItems(payload.items());
            }
        });
    }

    public static void dispatchTerminalQuery(final ServerPlayer player, final NetworkUuid net,
                                             final ServerLevel level) {
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        if (mainframe != null) {
            mainframe.submitOperation(
                    new dev.jstech.computronics.operation.NetworkQueryOperationTask(level, net, player),
                    dev.jstech.core.operation.OperationPriority.MEDIUM);
        }
    }

    private static void handleTerminalSelect(final TerminalSelectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final IComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
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
            /*
             * A MOVE must never pull from its own destination Server: extracting and re-inserting into
             * the same store would churn items in place. Drop the target from the sources.
             */
            if (dest.move() && dest.target() != null) {
                sources = sourcesWithout(level, net, sources, dest.target());
                if (sources.isEmpty()) {
                    return; // the only chosen source was the destination, nothing to move
                }
            }
            final StorageKey key = payload.key();
            final var op = dest.move()
                    ? mainframe.submitNetworkMove(key, payload.quantity(), dest.handler(), dest.label(), sources)
                    : mainframe.submitNetworkSelect(key, payload.quantity(), dest.handler(), dest.label(), sources);
            if (op != null) {
                if (!dest.move()) {
                    op.abortWhen(gone(host)); // the pull lands in this computer: stop once it is gone
                }
                op.onSettle(() -> sendSnapshot(player, level, net));
            }
        });
    }

    /**
     * A resolved SELECT destination: where the pulled items land, the provenance label, whether it is a MOVE (into another Server), and that target Server's node (so it can be excluded as a source).
     */
    private record Dest(dev.jstech.computronics.storage.IDataSink handler, String label,
                        boolean move, @Nullable NodeUuid target) {
    }

    @Nullable
    private static Dest resolveDest(final IComputerTerminalHost host, final ServerLevel level,
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
            return new Dest(new dev.jstech.computronics.storage.StoreSink(mf.localStore()),
                    "Mainframe", false, null);
        }
        // A Personal Computer on the network: a SELECT into its own local storage (leaves the network).
        for (final NetworkSystem.PersonalComputerNode pc : NetworkSystem.get(level).personalComputersOf(net)) {
            if (pc.nodeUuid().equals(target)
                    && level.getBlockEntity(BlockPos.of(pc.pos())) instanceof PersonalComputerBlockEntity pcBe) {
                return new Dest(new dev.jstech.computronics.storage.StoreSink(pcBe.localStore()),
                        pcLabel(pcBe, target), false, null);
            }
        }
        return resolveServerDest(level, net, key);
    }

    @Nullable
    private static Dest resolveTerminalDest(final IComputerTerminalHost host) {
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
                        instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity rack
                        ? new Dest(new dev.jstech.computronics.storage.StoreSink(
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
            final IComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
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
            final DataHandoff.ISource source = fromCursor
                    ? DataHandoff.cursor(player) : DataHandoff.slot(menu.getSlot(idx), player);
            /*
             * A right-click hands over ONE: one item, or what a held container holds, and a held empty
             * container over a fluid or chemical entry fills from it instead. Left click and shift-click
             * deposit the stack as items, the way a chest takes them.
             */
            final boolean one = idx == TerminalInsertPayload.CURSOR_ONE;
            final Runnable refresh = () -> sendSnapshot(player, level, net);
            if (one && payload.entry().isPresent() && DataContainers.canTake(source.get(), payload.entry().get())) {
                DataHandoff.fillFromNetwork(mainframe, level, net, player, source, payload.entry().get(),
                        host.originLabel(MoveLabels.TERMINAL), refresh);
                return;
            }
            DataHandoff.intoNetwork(mainframe, level, net, player, source, one ? 1 : source.get().getCount(),
                    one, host.originLabel(MoveLabels.TERMINAL), refresh);
        });
    }

    private static void handleRequestBreakdown(final RequestServerBreakdownPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            final IComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host != null && host.networkUuid() != null
                    && context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level) {
                PacketDistributor.sendToPlayer(player, collectBreakdown(level, host.networkUuid(), payload.key()));
                /*
                 * The advanced-mode destination picker needs every computer that can hold items (the
                 * Mainframe's local storage and every Server), not just those holding the clicked item.
                 */
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
                dev.jstech.computronics.client.os.NetworkInteractorApp.acceptServers(
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
     * Reuses the same dispatch as the MC-NET terminal SELECT, and only the host resolution (niHost) differs.
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
                    return; // the only chosen source was the destination, nothing to move
                }
            }
            final long qty = Math.min(payload.quantity(), Integer.MAX_VALUE);
            final var op = dest.move()
                    ? mainframe.submitNetworkMove(payload.key(), qty, dest.handler(), dest.label(), sources)
                    : mainframe.submitNetworkSelect(payload.key(), qty, dest.handler(), dest.label(), sources);
            if (op != null) {
                op.setPriority(payload.priority());
                if (!dest.move()) {
                    op.abortWhen(gone(host)); // the pull lands in this computer: stop once it is gone
                }
            }
            if (op != null && host instanceof dev.jstech.computronics.os
                    .IOsHost computer) {
                op.onSettle(() -> sendNetworkInteractor(player, level, computer));
            }
        });
    }

    private static void handleSetOperationPriority(final SetOperationPriorityPayload payload,
                                                   final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            /*
             * Any computer on the network may re-prioritise its Operations: the same proximity-to-a-linked-
             * monitor check the other desktop requests use, so a player cannot drive a foreign network.
             */
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            if (host == null || host.networkUuid() == null) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                return;
            }
            mainframe.setOperationPriority(payload.operationId(), payload.priority());
            dispatchActiveOperations(player, host.networkUuid(), level);
        });
    }

    private static void handleCancelOperation(final CancelOperationPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            if (host == null || host.networkUuid() == null) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                return;
            }
            mainframe.cancelOperation(payload.operationId());
            // The cancelled Operation is logged on the Mainframe's next tick: refresh both views then.
            mainframe.runNextTick(() -> {
                dispatchActiveOperations(player, host.networkUuid(), level);
                dispatchTerminalOpsLog(player, host.networkUuid(), level);
            });
        });
    }

    /**
     * A stop condition for a pull into a computer's own storage: once that computer is gone from the world,
     * nothing more is taken out of the network for it.
     */
    private static java.util.function.BooleanSupplier gone(final IComputerTerminalHost host) {
        return host instanceof net.minecraft.world.level.block.entity.BlockEntity be ? be::isRemoved : () -> false;
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
                            instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity rack
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
                            instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity rack
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
                dev.jstech.computronics.client.os.NetworkInteractorApp
                        .acceptOps(payload.operations());
                dev.jstech.computronics.client.os.NetworkManagerApp
                        .acceptOpsLog(payload.operations());
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
                dev.jstech.computronics.client.os.NetworkInteractorApp
                        .acceptActiveOps(payload.operations(), payload.scSlotsUsed(), payload.scSlotsTotal());
                dev.jstech.computronics.client.os.NetworkManagerApp
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

    private static IComputerTerminalHost openTerminal(final IPayloadContext context,
                                                     final BlockPos monitorPos, final BlockPos hostPos) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof ComputerTerminalMenu menu
                && menu.monitorPos().equals(monitorPos)
                && menu.hostPos().equals(hostPos)
                && player.level().getBlockEntity(hostPos) instanceof IComputerTerminalHost host) {
            return host;
        }
        return null;
    }

    /**
     * Resolves the computer for a craft request that may come from the MC-NET terminal (its container menu) OR
     * the desktop Network Interactor (no menu, authenticated by proximity to a linked monitor). Tries the
     * terminal first, then the NI host, so the shared craft flow works from both.
     */
    private static IComputerTerminalHost craftHost(final IPayloadContext context, final BlockPos monitorPos,
                                                   final BlockPos hostPos) {
        final IComputerTerminalHost terminal = openTerminal(context, monitorPos, hostPos);
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
        final Map<NodeUuid, Long> perServer = dev.jstech.computronics.operation.NetworkStorage
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
        return dev.jstech.core.network.NetworkSystem.get(level).locationOf(node)
                .map(loc -> level.getBlockEntity(net.minecraft.core.BlockPos.of(loc.rackPos()))
                        instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity rack
                        ? rack.getServers().getStackInSlot(loc.slot()) : ItemStack.EMPTY)
                .map(dev.jstech.computronics.item.ServerItem::customName)
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

    private static void handleRequestNetworkManager(final RequestNetworkManagerPayload payload,
                                                    final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos()) instanceof MainframeBlockEntity mf) {
                final NetworkUuid net = mf.networkUuid();
                final String netId = net != null ? ShortId.of(net.asString()) : "";
                PacketDistributor.sendToPlayer(player,
                        new NetworkManagerPayload(payload.hostPos(), netId, collectNodes(level, mf),
                                collectHardware(level, mf), collectStatistics(level, mf)));
            }
        });
    }

    /** The last hour's Operation statistics of a Mainframe, by type, for the Stats tab. */
    static NetworkManagerPayload.Statistics collectStatistics(final ServerLevel level, final MainframeBlockEntity mf) {
        final long now = level.getGameTime();
        final List<NetworkManagerPayload.TypeStat> types = new ArrayList<>();
        for (final var summary : mf.statistics().summaries(now)) {
            if (types.size() >= NetworkManagerPayload.MAX_STAT_TYPES) {
                break;
            }
            types.add(new NetworkManagerPayload.TypeStat((byte) summary.type(), summary.count(),
                    summary.shortfallPercent(), summary.averageWait(), summary.averageRun(), summary.moved()));
        }
        return new NetworkManagerPayload.Statistics(types, mf.statistics().peakConcurrentLastDay(now),
                mf.statistics().movedLastHour(now));
    }

    private static void handleNetworkManager(final NetworkManagerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.NetworkManagerApp.accept(payload));
    }

    private static List<NetworkNodeInfo> collectNodes(final ServerLevel level, final MainframeBlockEntity mf) {
        final UnitFormatter fmt = UnitFormatter.forCurrentLocale();
        final List<NetworkNodeInfo> nodes = new ArrayList<>();
        final NetworkUuid net = mf.networkUuid();

        nodes.add(computerNodeInfo(NetworkNodeInfo.KIND_MAINFRAME, mf, mf.nodeUuid().asString(),
                fmt.compact(mf.capacity(), Unit.IT_PER_TICK)));

        if (net != null) {
            final NetworkSystem system = NetworkSystem.get(level);
            for (final ServerNode server : system.serversOf(net)) {
                if (nodes.size() >= NetworkManagerPayload.MAX_NODES) {
                    break;
                }
                nodes.add(serverNodeInfo(level, system, server, fmt));
            }
            for (final SubframeNode subframe : system.subframesOf(net)) {
                if (nodes.size() >= NetworkManagerPayload.MAX_NODES) {
                    break;
                }
                nodes.add(new NetworkNodeInfo(NetworkNodeInfo.KIND_SUBFRAME,
                        ShortId.of(subframe.nodeUuid().asString()), "",
                        fmt.compact(subframe.contributedCapacity(), Unit.IT_PER_TICK), true,
                        0, 0, 0L, 0L, NetworkNodeInfo.SHARE_UNKNOWN, ""));
            }
            for (final NetworkSystem.PersonalComputerNode pc : system.personalComputersOf(net)) {
                if (nodes.size() >= NetworkManagerPayload.MAX_NODES) {
                    break;
                }
                /*
                 * A Cluster Management Computer takes a PC's place on the network (same layout, same role
                 * in the topology), but the overview names it for what it is.
                 */
                final int kind = level.getBlockEntity(BlockPos.of(pc.pos()))
                        instanceof dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity
                        ? NetworkNodeInfo.KIND_CLUSTER_MANAGEMENT : NetworkNodeInfo.KIND_PC;
                nodes.add(resolveComputerNode(level, kind, pc.nodeUuid().asString(),
                        pc.pos(), fmt.compact(pc.capacity(), Unit.IT_PER_TICK)));
            }
            for (final NetworkSystem.CraftingComputerNode cc : system.craftingComputersOf(net)) {
                if (nodes.size() >= NetworkManagerPayload.MAX_NODES) {
                    break;
                }
                nodes.add(resolveComputerNode(level, NetworkNodeInfo.KIND_CRAFTING, cc.nodeUuid().asString(),
                        cc.pos(), fmt.compact(cc.capacity(), Unit.IT_PER_TICK)));
            }
            for (final NetworkSystem.SupercomputerNode sc : system.supercomputersOf(net)) {
                if (nodes.size() >= NetworkManagerPayload.MAX_NODES) {
                    break;
                }
                /*
                 * A supercomputer is a whole cluster bridged by an HBW interface (its pos is that interface,
                 * not a single computer). It is on the network whenever its uplink is; it is online (able
                 * to take crafts) only with at least one rated node.
                 */
                final String scName = level.getBlockEntity(BlockPos.of(sc.pos()))
                        instanceof dev.jstech.computronics.blockentity.HbwInterfaceBlockEntity hub
                        ? hub.customName() : "";
                nodes.add(new NetworkNodeInfo(NetworkNodeInfo.KIND_SUPERCOMPUTER,
                        ShortId.of(sc.nodeUuid().asString()), scName, sc.parallelCrafts() + " crafts",
                        sc.parallelCrafts() > 0, 0, 0, 0L, 0L, NetworkNodeInfo.SHARE_UNKNOWN, ""));
            }
        }
        return nodes;
    }

    /** Builds an enriched node row from a resolved computer block entity (name, specs, OS, storage share). */
    private static NetworkNodeInfo computerNodeInfo(final int kind,
            final dev.jstech.computronics.os.IOsHost c,
            final String uuid, final String detail) {
        final int share = dev.jstech.computronics.item.DiskItem.publicPermille(c.systemDisk());
        /*
         * Total capacity is only summed for the Mainframe; a generic computer reports its free space, which is
         * the "available storage" the tooltip shows, with total left as 0 (unknown).
         */
        return new NetworkNodeInfo(kind, ShortId.of(uuid), c.customName(), detail, c.isRunning(),
                c.maxCpuMhz(), c.totalVramMb(), c.systemDiskFreeMb(), 0L,
                share, osLabelOf(c.installedOsId()));
    }

    /** Resolves the computer at {@code posLong}; falls back to a bare row if it is not loaded as a computer. */
    private static NetworkNodeInfo resolveComputerNode(final ServerLevel level, final int kind, final String uuid,
                                                       final long posLong, final String detail) {
        if (level.getBlockEntity(BlockPos.of(posLong))
                instanceof dev.jstech.computronics.os.IOsHost c) {
            return computerNodeInfo(kind, c, uuid, detail);
        }
        return new NetworkNodeInfo(kind, ShortId.of(uuid), "", detail, false,
                0, 0, 0L, 0L, NetworkNodeInfo.SHARE_UNKNOWN, "");
    }

    /** A server lives as a disk in a rack, so it carries a name and storage but no processor/OS of its own. */
    private static NetworkNodeInfo serverNodeInfo(final ServerLevel level, final NetworkSystem system,
                                                  final ServerNode server, final UnitFormatter fmt) {
        final long total = server.storageItems();
        final long free = system.locationOf(server.nodeUuid())
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                        instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity rack
                        ? rack.getServerStorage(loc.slot()).free() : 0L)
                .orElse(0L);
        return new NetworkNodeInfo(NetworkNodeInfo.KIND_SERVER, ShortId.of(server.nodeUuid().asString()),
                serverLabel(level, server.nodeUuid()), String.format(java.util.Locale.ROOT, "%,d items", total), true,
                0, 0, free, total, NetworkNodeInfo.SHARE_UNKNOWN, "");
    }

    /** A short, friendly label for an installed OS id, or {@code none} when no OS is installed. */
    private static String osLabelOf(final net.minecraft.resources.ResourceLocation osId) {
        if (osId == null) {
            return "none";
        }
        return switch (osId.getPath()) {
            case "frames_95" -> "Frames 95";
            case "frames_xp" -> "Frames XP";
            case "frames_11" -> "Frames 11";
            case "mc_dos" -> "MC-DOS";
            case "mc_net" -> "MC-NET";
            default -> osId.getPath();
        };
    }

    /** Network-wide hardware totals for the Network Manager's Hardware tab. */
    private static NetworkManagerPayload.Hardware collectHardware(final ServerLevel level,
                                                                  final MainframeBlockEntity mf) {
        long storage = mf.localStorageCapacity();
        final NetworkUuid net = mf.networkUuid();
        if (net != null) {
            final NetworkSystem system = NetworkSystem.get(level);
            for (final ServerNode server : system.serversOf(net)) {
                storage += server.storageItems();
            }
        }
        return new NetworkManagerPayload.Hardware(
                mf.orchestrationCapacity(), mf.pooledQueues(), mf.computerRamBuffer(), storage);
    }

    private static void handleRequestStorageInsights(final RequestStorageInsightsPayload payload,
                                                     final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level) {
                final var host = niHost(player, level, payload.host(), payload.monitorPos());
                if (host != null && host.networkUuid() != null) {
                    PacketDistributor.sendToPlayer(player, collectStorageInsights(level, host.networkUuid()));
                }
            }
        });
    }

    private static void handleStorageInsights(final StorageInsightsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.StorageInsightsApp.accept(payload));
    }

    private static void handleRequestCraftPlanner(final RequestCraftPlannerPayload payload,
                                                  final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level) {
                final var host = niHost(player, level, payload.host(), payload.monitorPos());
                if (host == null || host.networkUuid() == null) {
                    return;
                }
                if (payload.target().isEmpty()) {
                    dispatchCraftCatalog(player, host.networkUuid(), level);
                } else {
                    PacketDistributor.sendToPlayer(player, collectCraftPlanner(level, host.networkUuid(),
                            StorageKey.of(payload.target()), Math.max(1, payload.quantity())));
                }
            }
        });
    }

    private static void handleCraftPlanner(final CraftPlannerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.CraftPlannerApp.accept(payload));
    }

    /** Runs the recursive planner for the Craft Planner: feasibility, the ordered stages, and the raw bill. */
    private static CraftPlannerPayload collectCraftPlanner(final ServerLevel level, final NetworkUuid net,
                                                           final StorageKey key, final long quantity) {
        final MainframeBlockEntity mf = resolveMainframe(level, net);
        if (mf == null) {
            return new CraftPlannerPayload(key.stack(1), quantity, false, false, 0L, 0L,
                    List.of(), List.of(), List.of());
        }
        final var patterns = mf.networkPatterns();
        final var machines = mf.networkProcessingPatterns();
        final Map<StorageKey, Long> stock = mf.networkIndex().snapshot();
        final var plan = dev.jstech.computronics.crafting.CraftPlanner.plan(
                key, quantity, patterns, machines, stock);
        if (plan.steps().isEmpty()) {
            return new CraftPlannerPayload(key.stack(1), quantity, false, false, 0L, 0L,
                    List.of(), List.of(), List.of());
        }
        final long maxFeasible = dev.jstech.computronics.crafting.CraftPlanner.maxFeasible(
                key, quantity, patterns, machines, stock);
        final List<CraftPlannerPayload.Stage> stages = new ArrayList<>();
        for (final var step : plan.steps()) {
            if (stages.size() >= CraftPlannerPayload.MAX_STAGES) {
                break;
            }
            stages.add(new CraftPlannerPayload.Stage(step.resultName(), step.isMachine(), step.runs(),
                    step.produced()));
        }
        final List<CraftPlanPayload.Row> ingredients = new ArrayList<>();
        for (final Map.Entry<StorageKey, Long> e : plan.rawConsumption().entrySet()) {
            if (ingredients.size() >= CraftPlannerPayload.MAX_INGREDIENTS) {
                break;
            }
            ingredients.add(new CraftPlanPayload.Row(e.getKey().stack(1), e.getValue(),
                    stock.getOrDefault(e.getKey(), 0L)));
        }
        final List<CraftPlannerPayload.TreeNode> tree = new ArrayList<>();
        treeWalk(key, Math.max(1, quantity), 0, patterns, machines, tree, new java.util.HashSet<>());
        return new CraftPlannerPayload(key.stack(1), quantity, true, plan.feasible(), plan.produced(),
                maxFeasible, stages, ingredients, tree);
    }

    /** Recursively expands one recipe path (crafting preferred, then a machine) into a pre-order tree. */
    private static void treeWalk(final StorageKey key, final long need, final int depth,
            final List<dev.jstech.computronics.crafting.CraftingPattern> patterns,
            final List<dev.jstech.computronics.crafting.ProcessingPattern> machines,
            final List<CraftPlannerPayload.TreeNode> out, final java.util.Set<StorageKey> visiting) {
        if (out.size() >= CraftPlannerPayload.MAX_TREE || depth > 6) {
            return;
        }
        Map<StorageKey, Long> inputs = null;
        long perRun = 1;
        for (final var cp : patterns) {
            if (StorageKey.of(cp.result()).equals(key)) {
                inputs = cp.ingredientTotals();
                perRun = Math.max(1, cp.result().getCount());
                break;
            }
        }
        if (inputs == null) {
            for (final var pp : machines) {
                final var o = pp.primaryOutput();
                if (o != null && o.key().equals(key)) {
                    final Map<StorageKey, Long> merged = new java.util.LinkedHashMap<>();
                    for (final var pi : pp.inputs()) {
                        merged.merge(pi.key(), pi.amount(), Long::sum);
                    }
                    inputs = merged;
                    perRun = Math.max(1, o.amount());
                    break;
                }
            }
        }
        final boolean craftable = inputs != null;
        out.add(new CraftPlannerPayload.TreeNode(depth, key.stack(1), need, craftable));
        if (!craftable || !visiting.add(key)) {
            return;
        }
        final long runs = Math.max(1, (need + perRun - 1) / perRun);
        for (final Map.Entry<StorageKey, Long> e : inputs.entrySet()) {
            treeWalk(e.getKey(), e.getValue() * runs, depth + 1, patterns, machines, out, visiting);
        }
        visiting.remove(key);
    }

    // Automation Manager: the job list, engine status, create, and pause/resume/delete

    private static void handleRequestAutomation(final RequestAutomationPayload payload,
                                                final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level) {
                final var host = niHost(player, level, payload.host(), payload.monitorPos());
                if (host != null && host.networkUuid() != null) {
                    PacketDistributor.sendToPlayer(player,
                            buildAutomation(resolveMainframe(level, host.networkUuid())));
                }
            }
        });
    }

    private static void handleAutomation(final AutomationPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.AutomationManagerApp.accept(payload));
    }

    private static AutomationPayload buildAutomation(final MainframeBlockEntity mf) {
        if (mf == null) {
            return new AutomationPayload(false, "no Mainframe", List.of(), List.of());
        }
        final boolean online = mf.isAutomationEngineActive() || mf.isIqlEngineActive();
        final String label = mf.isAutomationEngineInstalled() ? "Automation Engine"
                : mf.isIqlEngineInstalled() ? "IQL Engine" : "none";
        final List<AutomationPayload.JobRow> rows = new ArrayList<>();
        for (final var job : mf.iqlCatalog().ofType(
                dev.jstech.computronics.program.iql.IqlDefinition.ObjectType.JOB)) {
            if (rows.size() >= AutomationPayload.MAX_JOBS) {
                break;
            }
            rows.add(new AutomationPayload.JobRow(job.name(), inferJobType(job.body(), job.triggerKind()),
                    triggerSummary(job.triggerKind(), job.triggerSpec()), mf.isJobPaused(job.name())));
        }
        // The .iql scripts saved on the Mainframe's system disk, so an IQL-Script job can pick one.
        final List<String> files = new ArrayList<>();
        final ItemStack sysDisk = mf.systemDisk();
        if (!sysDisk.isEmpty()) {
            for (final DiskFilesystem.FileEntry entry
                    : DiskFilesystem.list(sysDisk, "", filesystemKindOf(mf))) {
                if (entry.type() == FileType.IQL && files.size() < AutomationPayload.MAX_FILES) {
                    files.add(entry.path());
                }
            }
        }
        return new AutomationPayload(online, label, rows, files);
    }

    private static String inferJobType(final String body,
            final dev.jstech.computronics.program.iql.IqlDefinition.TriggerKind kind) {
        final String b = body.trim().toUpperCase(java.util.Locale.ROOT);
        if (b.startsWith("MOVE")) {
            return "Periodic Move";
        }
        if (b.startsWith("CRAFT")) {
            return kind == dev.jstech.computronics.program.iql
                    .IqlDefinition.TriggerKind.WHEN ? "Keep Stock" : "Batch Craft";
        }
        return "Custom";
    }

    private static String triggerSummary(
            final dev.jstech.computronics.program.iql.IqlDefinition.TriggerKind kind,
            final String spec) {
        return switch (kind) {
            case EVERY -> "every " + spec;
            case WHEN -> spec;
            default -> "manual";
        };
    }

    private static void handleCreateAutomationJob(final CreateAutomationJobPayload payload,
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
            final MainframeBlockEntity mf = resolveMainframe(level, host.networkUuid());
            if (mf == null) {
                return;
            }
            final var def = compileJob(player, mf, payload);
            if (def != null) {
                mf.iqlCatalog().put(
                        dev.jstech.computronics.program.iql.IqlSavedObject.from(def));
                mf.markIqlCatalogChanged();
                PacketDistributor.sendToPlayer(player, buildAutomation(mf));
            }
        });
    }

    private static dev.jstech.computronics.program.iql.IqlDefinition compileJob(
            final ServerPlayer player, final MainframeBlockEntity mf, final CreateAutomationJobPayload p) {
        final String name = p.name().trim();
        if (name.isEmpty()) {
            jobError(player, "Give the job a name.");
            return null;
        }
        final String item = p.item().trim();
        final long amount = Math.max(1, p.amount());
        final var type = dev.jstech.computronics.program.iql.IqlDefinition.ObjectType.JOB;
        final var every = dev.jstech.computronics.program.iql.IqlDefinition.TriggerKind.EVERY;
        final var when = dev.jstech.computronics.program.iql.IqlDefinition.TriggerKind.WHEN;
        switch (p.jobType()) {
            case CreateAutomationJobPayload.TYPE_KEEP_STOCK -> {
                if (item.isEmpty()) {
                    jobError(player, "Keep Stock needs an item.");
                    return null;
                }
                return dev.jstech.computronics.program.iql.IqlDefinition.create(
                        type, name, "CRAFT " + amount + " " + item, when, "qty(" + item + ") < " + amount);
            }
            case CreateAutomationJobPayload.TYPE_BATCH_CRAFT -> {
                if (item.isEmpty() || !validInterval(p.interval())) {
                    jobError(player, "Batch Craft needs an item and a valid interval (e.g. 30s, 5m).");
                    return null;
                }
                return dev.jstech.computronics.program.iql.IqlDefinition.create(
                        type, name, "CRAFT " + amount + " " + item, every, p.interval().trim());
            }
            case CreateAutomationJobPayload.TYPE_PERIODIC_MOVE -> {
                final String from = p.from().trim();
                final String to = p.to().trim();
                if (from.isEmpty() || to.isEmpty() || !validInterval(p.interval())) {
                    jobError(player, "Periodic Move needs FROM, TO, and a valid interval (e.g. 30s).");
                    return null;
                }
                final String what = item.isEmpty() ? "*" : amount + " " + item;
                return dev.jstech.computronics.program.iql.IqlDefinition.create(
                        type, name, "MOVE " + what + " FROM " + from + " TO " + to, every, p.interval().trim());
            }
            case CreateAutomationJobPayload.TYPE_IQL_SCRIPT -> {
                // The chosen .iql filename rides in the item field; its content becomes the job body.
                if (item.isEmpty() || !validInterval(p.interval())) {
                    jobError(player, "An IQL Script job needs a .iql file and a valid interval (e.g. 30s).");
                    return null;
                }
                final ItemStack sysDisk = mf.systemDisk();
                final var content = sysDisk.isEmpty() ? java.util.Optional.<String>empty()
                        : DiskFilesystem.read(sysDisk, item);
                if (content.isEmpty() || content.get().isBlank()) {
                    jobError(player, "Script not found on the Mainframe disk: " + item);
                    return null;
                }
                return dev.jstech.computronics.program.iql.IqlDefinition.create(
                        type, name, content.get(), every, p.interval().trim());
            }
            default -> {
                return null;
            }
        }
    }

    private static boolean validInterval(final String spec) {
        try {
            return dev.jstech.computronics.program.iql.IqlDuration.toTicks(spec.trim()) > 0;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private static void jobError(final ServerPlayer player, final String message) {
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), false);
    }

    private static void handleJobAction(final JobActionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final var host = niHost(player, level, payload.host(), payload.monitorPos());
            if (host == null || host.networkUuid() == null) {
                return;
            }
            final MainframeBlockEntity mf = resolveMainframe(level, host.networkUuid());
            if (mf == null) {
                return;
            }
            switch (payload.action()) {
                case JobActionPayload.ACTION_PAUSE -> mf.pauseJob(payload.name());
                case JobActionPayload.ACTION_RESUME -> mf.restartJob(payload.name());
                case JobActionPayload.ACTION_DELETE -> {
                    mf.iqlCatalog().remove(
                            dev.jstech.computronics.program.iql.IqlDefinition.ObjectType.JOB,
                            payload.name());
                    mf.markIqlCatalogChanged();
                }
                default -> { }
            }
            PacketDistributor.sendToPlayer(player, buildAutomation(mf));
        });
    }

    /** Builds the Storage Insights dashboard: totals, the biggest and smallest types, and per-server usage. */
    private static StorageInsightsPayload collectStorageInsights(final ServerLevel level, final NetworkUuid net) {
        final Map<StorageKey, Long> totals =
                dev.jstech.computronics.operation.NetworkStorage.of(level, net).query();
        long totalItems = 0;
        for (final long v : totals.values()) {
            totalItems += v;
        }
        final List<Map.Entry<StorageKey, Long>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        final List<NetworkItemEntry> top = new ArrayList<>();
        for (int i = 0; i < sorted.size() && i < StorageInsightsPayload.MAX_TOP; i++) {
            top.add(new NetworkItemEntry(sorted.get(i).getKey(), sorted.get(i).getValue()));
        }
        final List<NetworkItemEntry> low = new ArrayList<>();
        for (int i = sorted.size() - 1; i >= 0 && low.size() < StorageInsightsPayload.MAX_LOW; i--) {
            if (sorted.get(i).getValue() > 0) {
                low.add(new NetworkItemEntry(sorted.get(i).getKey(), sorted.get(i).getValue()));
            }
        }
        final NetworkSystem system = NetworkSystem.get(level);
        final List<NetworkItemEntry.StorageShare> servers = new ArrayList<>();
        int serverCount = 0;
        for (final ServerNode server : system.serversOf(net)) {
            serverCount++;
            if (servers.size() >= StorageInsightsPayload.MAX_SERVERS) {
                continue;
            }
            final long used = system.locationOf(server.nodeUuid())
                    .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                            instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity rack
                            ? rack.getServerStorage(loc.slot()).used() : 0L)
                    .orElse(0L);
            servers.add(new NetworkItemEntry.StorageShare(serverLabel(level, server.nodeUuid()), used));
        }
        return new StorageInsightsPayload(totalItems, totals.size(), serverCount, top, low, servers);
    }

    /** Whether {@code progId} is installed on the computer AND runnable on its current OS (version + specs). */
    private static boolean installedAndAllowed(
            final dev.jstech.computronics.os.IOsHost computer,
            final net.minecraft.resources.ResourceLocation progId) {
        return computer.console() != null
                && computer.console().isInstalled(progId.toString())
                && hostScopeAllows(dev.jstech.computronics.os.OsRegistry.getProgram(progId),
                        computer)
                && dev.jstech.computronics.os.OsRegistry.canRunProgram(
                        computer.installedOsId(), progId, computer.maxCpuMhz(), computer.totalVramMb());
    }

    /** Whether a program's host scope permits it on this computer (a null spec places no restriction). */
    private static boolean hostScopeAllows(
            final dev.jstech.computronics.os.ProgramSpec spec,
            final dev.jstech.computronics.os.IOsHost computer) {
        if (spec == null) {
            return true;
        }
        return switch (spec.hostScope()) {
            case ANY -> true;
            case MAINFRAME -> computer
                    instanceof dev.jstech.computronics.blockentity.MainframeBlockEntity;
            case CRAFTING_COMPUTER -> computer
                    instanceof CraftingComputerBlockEntity;
            /*
             * A rack answers as the machine it is showing, so scoping to SERVER means "this session
             * is a rack server", which is exactly where the headless server services belong.
             */
            case SERVER -> computer
                    instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity;
            case CLUSTER_MANAGEMENT_COMPUTER -> computer
                    instanceof dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity;
        };
    }

    /** The player-facing reason a program's host scope rejected this computer. */
    private static String hostScopeMessage(final dev.jstech.computronics.os.ProgramSpec spec) {
        if (spec == null) {
            return "This program cannot install on this computer.";
        }
        return switch (spec.hostScope()) {
            case MAINFRAME -> "The " + spec.displayName() + " only installs on a Mainframe.";
            case CRAFTING_COMPUTER -> "The " + spec.displayName() + " only installs on a Crafting Computer.";
            case SERVER -> "The " + spec.displayName() + " only installs on a server in a rack.";
            case CLUSTER_MANAGEMENT_COMPUTER -> "The " + spec.displayName()
                    + " only installs on a Cluster Management Computer.";
            default -> "This program cannot install on this computer.";
        };
    }

    private static void handleRequestItemDetail(final RequestItemDetailPayload payload,
                                                final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && !payload.item().isEmpty()) {
                final var host = niHost(player, level, payload.host(), payload.monitorPos());
                if (host != null && host.networkUuid() != null) {
                    PacketDistributor.sendToPlayer(player,
                            collectItemDetail(level, host.networkUuid(), StorageKey.of(payload.item())));
                }
            }
        });
    }

    private static void handleItemDetail(final ItemDetailPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.StorageInsightsApp.acceptDetail(payload));
    }

    /** One item's detail: the network total, where it is stored, what it makes, and which buses filter it. */
    private static ItemDetailPayload collectItemDetail(final ServerLevel level, final NetworkUuid network,
                                                       final StorageKey key) {
        final NetworkSystem system = NetworkSystem.get(level);
        final long total = dev.jstech.computronics.operation.NetworkStorage.of(level, network)
                .query().getOrDefault(key, 0L);

        // Where it is stored: per server that holds any.
        final List<NetworkItemEntry.StorageShare> stored = new ArrayList<>();
        for (final ServerNode server : system.serversOf(network)) {
            if (stored.size() >= ItemDetailPayload.MAX_STORED) {
                break;
            }
            final long held = system.locationOf(server.nodeUuid())
                    .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                            instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity rack
                            ? rack.getServerStorage(loc.slot()).count(key) : 0L)
                    .orElse(0L);
            if (held > 0) {
                stored.add(new NetworkItemEntry.StorageShare(serverLabel(level, server.nodeUuid()), held));
            }
        }

        // What it makes: the products of any pattern that consumes it as an ingredient.
        final List<net.minecraft.world.item.ItemStack> uses = new ArrayList<>();
        final java.util.Set<StorageKey> seen = new java.util.HashSet<>();
        final MainframeBlockEntity mf = resolveMainframe(level, network);
        if (mf != null) {
            for (final var pattern : mf.networkPatterns()) {
                if (uses.size() >= ItemDetailPayload.MAX_USES) {
                    break;
                }
                if (pattern.ingredientTotals().containsKey(key)) {
                    final StorageKey rk = StorageKey.of(pattern.result());
                    if (seen.add(rk)) {
                        uses.add(pattern.result().copy());
                    }
                }
            }
            for (final var proc : mf.networkProcessingPatterns()) {
                if (uses.size() >= ItemDetailPayload.MAX_USES) {
                    break;
                }
                final boolean consumes = proc.inputs().stream().anyMatch(in -> in.key().equals(key));
                final var out = proc.primaryOutput();
                if (consumes && out != null && seen.add(out.key())) {
                    uses.add(out.key().stack(1));
                }
            }
        }

        // Which buses filter it: walk the network's cable positions and read each bus's filter.
        final List<ItemDetailPayload.BusRef> buses = new ArrayList<>();
        for (final long posLong : system.connectivity().positionsOf(network)) {
            if (buses.size() >= ItemDetailPayload.MAX_BUSES) {
                break;
            }
            if (!(level.getBlockEntity(BlockPos.of(posLong))
                    instanceof dev.jstech.computronics.blockentity.DataCableBlockEntity cable)) {
                continue;
            }
            for (final net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                if (cable.getPart(dir)
                        instanceof dev.jstech.computronics.block.part.AbstractBusPart bus
                        && key.equals(bus.filterKey())) {
                    buses.add(new ItemDetailPayload.BusRef(bus.name(), busKind(bus.type())));
                }
            }
        }
        return new ItemDetailPayload(key.stack(1), total, stored, uses, buses);
    }

    private static String busKind(final dev.jstech.computronics.block.part.CablePartType type) {
        final String name = type.name();
        return name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    public static void sendSnapshot(final ServerPlayer player, final ServerLevel level, final NetworkUuid network) {
        if (player == null || player.isRemoved()) {
            return; // no one to send to (e.g. the requester logged out before the Operation settled)
        }
        final Map<StorageKey, Long> totals = network == null
                ? Map.of()
                : dev.jstech.computronics.operation.NetworkStorage.of(level, network).query();
        final List<NetworkItemEntry> entries = new ArrayList<>(Math.min(totals.size(),
                NetworkSnapshotPayload.MAX_ENTRIES));
        /*
         * Bounded by the wire cap so encoding never overflows the StreamCodec. The entry carries the
         * full stack (components and all), so the terminal shows the enchanted item, not a bare one.
         */
        totals.entrySet().stream().limit(NetworkSnapshotPayload.MAX_ENTRIES)
                .forEach(e -> entries.add(new NetworkItemEntry(e.getKey(), e.getValue())));
        PacketDistributor.sendToPlayer(player, new NetworkSnapshotPayload(entries));
    }

    private static void handleRequestNetworkInteractor(final RequestNetworkInteractorPayload payload,
                                                       final IPayloadContext context) {
        context.enqueueWork(() -> {
            /*
             * Proximity + monitor-link gated, like the mutating handlers, since the snapshot leaks the whole
             * network's contents, so a player must be at a monitor actually linked to this host.
             */
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && niHost(player, level, payload.host(), payload.monitorPos()) != null
                    && level.getBlockEntity(payload.host())
                            instanceof dev.jstech.computronics.os
                                    .IOsHost computer) {
                sendNetworkInteractor(player, level, computer);
            }
        });
    }

    /** Builds and sends a fresh Network Interactor snapshot (network grid, local grid, status, craft catalog). */
    private static void sendNetworkInteractor(final ServerPlayer player, final ServerLevel level,
            final dev.jstech.computronics.os.IOsHost computer) {
        final dev.jstech.core.uuid.NetworkUuid network = computer.networkUuid();
        // The whole network's items (Network Storage tab).
        final List<NetworkItemEntry> networkItems = new ArrayList<>();
        long usedItems = 0L;
        if (network != null) {
            final dev.jstech.core.network.NetworkSystem system =
                    dev.jstech.core.network.NetworkSystem.get(level);
            final dev.jstech.computronics.operation.NetworkStorage storage =
                    dev.jstech.computronics.operation.NetworkStorage.of(level, network);
            final Map<dev.jstech.computronics.storage.StorageKey, Long> totals = storage.query();
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
        if (computer instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host) {
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

    /** A human label for a storage node in the details panel's per-server breakdown, such as a server's rack position
     *  and slot, or a generic label for a published Personal Computer (which has no rack location). */
    private static String serverLabel(final dev.jstech.core.network.NetworkSystem system,
                                      final dev.jstech.core.uuid.NodeUuid node) {
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
    static dev.jstech.computronics.terminal.IComputerTerminalHost niHost(
            final ServerPlayer player, final ServerLevel level, final BlockPos hostPos, final BlockPos monitorPos) {
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(monitorPos)) > 64.0) {
            return null;
        }
        if (!(level.getBlockEntity(hostPos)
                instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host)) {
            return null;
        }
        /*
         * Anti-spoof: the monitor must actually be a linked peripheral of this host, so a player near any
         * monitor cannot drive a foreign computer by sending that computer's position as the host.
         */
        if (!(host instanceof dev.jstech.core.peripheral.IPeripheralOwner owner)
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
            if (level.getBlockEntity(pos) instanceof dev.jstech.computronics.blockentity
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
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
                return;
            }
            // The whole stack as items, the way a chest takes a shift-click; a bucket goes in as a bucket.
            final DataHandoff.ISource source = DataHandoff.inventory(player, slot);
            final int amount = source.get().getCount();
            final dev.jstech.computronics.os.IOsHost computer =
                    host instanceof dev.jstech.computronics.os.IOsHost c ? c : null;
            final Runnable refresh = () -> {
                if (computer != null) {
                    sendNetworkInteractor(player, level, computer);
                }
            };
            if (payload.target() == NiShiftInsertPayload.TARGET_STORAGE) {
                if (DataHandoff.intoLocalStore(host.localStore(), player, source, amount, false)
                        == DataHandoff.Outcome.DEPOSITED) {
                    refresh.run();
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
            DataHandoff.intoNetwork(mainframe, level, host.networkUuid(), player, source, amount, false,
                    host.originLabel(MoveLabels.INTERACTOR), refresh);
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
                final var op = mainframe.submitNetworkSelect(key, safeAmount, host.localStorage(),
                        host.originLabel(MoveLabels.INTERACTOR));
                if (op != null) {
                    op.setPriority(payload.priority());
                    op.abortWhen(gone(host));
                }
                if (op != null && host instanceof dev.jstech.computronics.os
                        .IOsHost computer) {
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
                final var op = mainframe.submitNetworkInsert(key, taken, host.originLabel(MoveLabels.INTERACTOR));
                if (op == null) {
                    host.localStore().insert(key, taken); // no live dispatcher: put it straight back
                    return;
                }
                op.setPriority(payload.priority());
                op.onSettle(() -> {
                    final long leftover = op.leftover();
                    if (leftover > 0L) {
                        host.localStore().insert(key, leftover);
                    }
                    if (host instanceof dev.jstech.computronics.os
                            .IOsHost computer) {
                        sendNetworkInteractor(player, level, computer);
                    }
                });
            } else if (!key.isItem()) {
                return; // a fluid or chemical cannot be held in the inventory
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
                if (host instanceof dev.jstech.computronics.os
                        .IOsHost computer) {
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
            /*
             * Left click deposits the whole stack as items; a right-click hands over ONE: one item, or what a
             * held container holds, and a held empty container over a fluid or chemical entry fills from it.
             */
            final DataHandoff.ISource source = DataHandoff.cursor(player);
            final boolean one = !payload.whole();
            final int amount = one ? 1 : source.get().getCount();
            final boolean fill = one && payload.entry().isPresent()
                    && DataContainers.canTake(source.get(), payload.entry().get());
            final dev.jstech.computronics.os.IOsHost computer =
                    host instanceof dev.jstech.computronics.os.IOsHost c ? c : null;
            final Runnable refresh = () -> {
                if (computer != null) {
                    sendNetworkInteractor(player, level, computer);
                }
            };
            if (payload.target() == NiDepositPayload.TARGET_STORAGE) {
                final DataHandoff.Outcome outcome = fill
                        ? DataHandoff.fillFromLocalStore(host.localStore(), player, source, payload.entry().get())
                        : DataHandoff.intoLocalStore(host.localStore(), player, source, amount, one);
                if (outcome == DataHandoff.Outcome.DEPOSITED || outcome == DataHandoff.Outcome.FILLED) {
                    refresh.run();
                }
                return;
            }
            // Network: the handoff runs over ticks and the view refreshes when it settles.
            if (host.networkUuid() == null) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                return;
            }
            if (fill) {
                DataHandoff.fillFromNetwork(mainframe, level, host.networkUuid(), player, source,
                        payload.entry().get(), host.originLabel(MoveLabels.INTERACTOR), refresh);
            } else {
                DataHandoff.intoNetwork(mainframe, level, host.networkUuid(), player, source, amount, one,
                        host.originLabel(MoveLabels.INTERACTOR), refresh);
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
            final dev.jstech.computronics.os.IOsHost computer =
                    host instanceof dev.jstech.computronics.os
                            .IOsHost c ? c : null;
            final Runnable refreshNi = () -> {
                if (computer != null) {
                    sendNetworkInteractor(player, level, computer);
                }
            };
            /*
             * The shared entry point runs a machine or multi-stage recipe directly, else plans a recursive
             * craft; refreshNi resends the Network Interactor now and again when the operation settles.
             */
            mainframe.submitCraftRequest(StorageKey.of(payload.result()), safeAmount, true,
                    host.originLabel(MoveLabels.INTERACTOR), refreshNi);
            refreshNi.run();
        });
    }

    private static void handleNetworkInteractor(final NetworkInteractorPayload payload,
                                                final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.NetworkInteractorApp.accept(payload));
    }

    // Local storage (the Storage tab): disk-backed, component-preserving quantity view.

    private static void handleLocalSnapshot(final LocalStorageSnapshotPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setLocalItems(payload.items());
                menu.setDiskPrivacy(payload.disks());
            }
        });
    }

    public static void dispatchLocalSnapshot(final ServerPlayer player, final IComputerTerminalHost host) {
        final Map<StorageKey, Long> view = host.localStore().view();
        final List<NetworkItemEntry> entries = new ArrayList<>(
                Math.min(view.size(), LocalStorageSnapshotPayload.MAX_ENTRIES));
        view.entrySet().stream().limit(LocalStorageSnapshotPayload.MAX_ENTRIES)
                .forEach(e -> entries.add(new NetworkItemEntry(e.getKey(), e.getValue())));
        /*
         * Per-disk privacy state for the Storage tab's slider; empty for a host with no slider, which
         * makes the Storage tab show the static "always public" badge instead of a control.
         */
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
            final IComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !(context.player() instanceof ServerPlayer player)
                    || payload.quantity() <= 0L) {
                return;
            }
            final StorageKey key = payload.key();
            if (!key.isItem()) {
                return; // a fluid or chemical cannot be held in the inventory, withdraw it via an Export Bus
            }
            final int maxStack = Math.max(1, key.stack(1).getMaxStackSize());
            /*
             * Take only as much as the player's inventory can actually hold, so a "withdraw all" on a
             * huge stack never extracts more than fits, since items must never be destroyed by overflow.
             */
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
                    break; // inventory unexpectedly full, return the remainder below
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
            final IComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !(context.player() instanceof ServerPlayer player)) {
                return;
            }
            /*
             * A Server or the Mainframe is always fully public: it carries no slider, so a privacy
             * write to one is a stale or spoofed packet. Warn lightly and ignore it.
             */
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
            final int permille = dev.jstech.computronics.storage.DiskPrivacy
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

    // OS install flow: the client asks the server to scan linked media readers and install the OS.

    // Firmware boot manager

    private static void handleRequestFirmwareState(final RequestFirmwareStatePayload payload,
                                                   final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos()) instanceof IOsHost computer) {
                PacketDistributor.sendToPlayer(player, buildFirmwareState(level, computer, payload.hostPos()));
            }
        });
    }

    /** Everything the boot manager lists for {@code computer}: disks, linked media, boot order, hardware. */
    static FirmwareStatePayload buildFirmwareState(final ServerLevel level,
                                                  final IOsHost computer, final BlockPos pos) {
        final HardwareEra era = computer.displayEra() != null ? computer.displayEra() : HardwareEra.STANDARD;
        final List<FirmwareStatePayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i < computer.diskSlots(); i++) {
            final net.minecraft.world.item.ItemStack disk = computer.diskInSlot(i);
            if (!(disk.getItem() instanceof dev.jstech.computronics.item.DiskItem)) {
                continue;
            }
            final net.minecraft.resources.ResourceLocation osId =
                    disk.get(dev.jstech.computronics.ComputingModule.SYSTEM_OS.get());
            final OsDef os = osId == null ? null : OsRegistry.getOs(osId);
            entries.add(new FirmwareStatePayload.Entry(FirmwareStatePayload.KIND_DISK, i,
                    os == null ? "" : os.id().toString(),
                    os == null ? "(no system)" : os.displayName(),
                    "Disk " + i + ": " + disk.getHoverName().getString(),
                    os != null, -1));
        }
        for (final long endpoint : computer.linkedEndpoints()) {
            if (!(level.getBlockEntity(BlockPos.of(endpoint)) instanceof MediaReaderBlockEntity reader)) {
                continue;
            }
            final String drive = reader.driveType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            final net.minecraft.world.item.ItemStack media = reader.mediaSlot().getStackInSlot(0);
            if (media.isEmpty()) {
                entries.add(new FirmwareStatePayload.Entry(FirmwareStatePayload.KIND_MEDIA, endpoint, "",
                        "(no medium)", drive, false, -1));
                continue;
            }
            final OsDef os = reader.insertedKind() == MediaKind.OS_INSTALL && reader.insertedPayload() != null
                    ? OsRegistry.getOs(reader.insertedPayload()) : null;
            if (os == null) {
                entries.add(new FirmwareStatePayload.Entry(FirmwareStatePayload.KIND_MEDIA, endpoint, "",
                        media.getHoverName().getString(), drive, false, -1));
                continue;
            }
            final boolean eraOk = OsGating.canInstall(os.minEra(), era);
            entries.add(new FirmwareStatePayload.Entry(FirmwareStatePayload.KIND_MEDIA, endpoint,
                    os.id().toString(),
                    os.displayName() + (os.installMode() == dev.jstech.computronics.os.InstallMode.GUIDED
                            ? " installer" : " (live)"),
                    drive + (eraOk ? "" : " - " + eraName(os.minEra()) + " era or newer"), eraOk,
                    os.installMode().ordinal()));
        }
        final int cpuMhz = computer.maxCpuMhz();
        final String cpuLabel = cpuMhz > 0 ? cpuMhz + " MHz" : "not detected";
        final int ramMb = (int) Math.min(Integer.MAX_VALUE, computer.ramBuffer());
        return new FirmwareStatePayload(pos, era.ordinal(), cpuLabel, cpuMhz, ramMb, computer.bootDiskSlot(),
                computer.defaultInstallSlot(), entries, raidInfoOf(level, computer));
    }

    /**
     * What the firmware's storage page shows: the controller in this machine's bay, the array it
     * runs, and what each mode would give. Only a rack server has one, since a desk computer's firmware
     * simply has no storage page.
     */
    private static FirmwareStatePayload.RaidInfo raidInfoOf(
            final ServerLevel level, final dev.jstech.computronics.os.IOsHost computer) {
        if (!(computer instanceof dev.jstech.computronics.blockentity
                .ServerRackBlockEntity rack)) {
            return FirmwareStatePayload.RaidInfo.ABSENT;
        }
        final int slot = rack.soleComputerSlot();
        if (slot < 0 || rack.raidControllerSlot(slot) < 0) {
            return FirmwareStatePayload.RaidInfo.ABSENT;
        }
        final List<Long> sizes = new ArrayList<>();
        for (final ItemStack drive : rack.claimedDriveStacks(slot)) {
            if (drive.getItem() instanceof dev.jstech.computronics.item.DiskItem disk) {
                sizes.add(disk.spec().capacityItems());
            }
        }
        final var modes = dev.jstech.computronics.rack.RaidMode.values();
        final List<Long> capacities = new ArrayList<>(modes.length);
        for (final var mode : modes) {
            // NONE presents the drives as they are; the others present the array they would form.
            capacities.add(mode == dev.jstech.computronics.rack.RaidMode.NONE
                    ? sizes.stream().mapToLong(Long::longValue).sum()
                    : mode.usableCapacity(sizes));
        }
        return new FirmwareStatePayload.RaidInfo(true, rack.raidModeOf(slot).ordinal(),
                rack.raidMemberCount(slot), sizes.size(), capacities);
    }

    private static void handleFirmwareAction(final FirmwareActionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos()) instanceof IOsHost computer)) {
                return;
            }
            switch (payload.action()) {
                case FirmwareActionPayload.ACTION_BOOT_DISK -> {
                    computer.setBootDiskSlot((int) payload.ref());
                    computer.setPendingInstallSlot(IOsHost.NO_PENDING_INSTALL); // the reboot the installer asked for
                    if (computer.hasOs()) {
                        /*
                         * Booting a disk from the firmware is a restart, so it replays POST like any
                         * other. Handing straight over to the system skipped the self-test the machine
                         * has to run, and left the session fixed on whatever it was before.
                         */
                        computer.setNeedsPost(true);
                        dev.jstech.computronics.block.MonitorBlock.openPost(
                                player, level, payload.monitorPos(), payload.hostPos());
                        return;
                    }
                }
                case FirmwareActionPayload.ACTION_SET_BOOT -> computer.setBootDiskSlot((int) payload.ref());
                case FirmwareActionPayload.ACTION_RAID_MODE -> {
                    final var modes = dev.jstech.computronics.rack.RaidMode.values();
                    final int mode = (int) payload.ref();
                    if (computer instanceof dev.jstech.computronics.blockentity
                            .ServerRackBlockEntity rack && mode >= 0 && mode < modes.length) {
                        rack.setRaidMode(rack.soleComputerSlot(), modes[mode]);
                    }
                }
                case FirmwareActionPayload.ACTION_FORMAT -> computer.formatDisk((int) payload.ref());
                case FirmwareActionPayload.ACTION_INSTALL -> {
                    final String failure = installFailure(level, computer, payload.ref(), payload.target());
                    if (failure != null) {
                        /*
                         * The client's installer has just played its progress to the end: end it on the
                         * refusal, not on a "complete" the disk never saw.
                         */
                        final HardwareEra era = computer.displayEra();
                        final int slot = payload.target();
                        PacketDistributor.sendToPlayer(player, new OpenInstallDonePayload(payload.hostPos(),
                                payload.monitorPos(),
                                dev.jstech.computronics.os.FirmwareKind
                                        .forEra(era != null ? era : HardwareEra.STANDARD).ordinal(),
                                "", slot < 0 ? "the default disk" : "Disk " + slot, slot, failure));
                    }
                }
                case FirmwareActionPayload.ACTION_BOOT_MEDIA -> {
                    if (level.getBlockEntity(BlockPos.of(payload.ref())) instanceof MediaReaderBlockEntity reader
                            && reader.insertedKind() == MediaKind.OS_INSTALL && reader.insertedPayload() != null) {
                        final OsDef os = OsRegistry.getOs(reader.insertedPayload());
                        if (os != null && os.installMode()
                                != dev.jstech.computronics.os.InstallMode.GUIDED) {
                            // A live medium: boot its shell and let the player install the system by hand.
                            computer.console().startLiveInstall(os.id().getPath().equals("arch")
                                    ? dev.jstech.computronics.program.install.LiveInstallState.Distro.ARCH
                                    : dev.jstech.computronics.program.install.LiveInstallState.Distro.GENTOO);
                            computer.setChanged();
                            dev.jstech.computronics.block.MonitorBlock.openBootTarget(
                                    player, level, payload.monitorPos(), payload.hostPos());
                            return;
                        } else {
                            final int target = payload.target() >= 0 ? payload.target() : computer.defaultInstallSlot();
                            if (installOsFromReader(level, computer, payload.ref(), target)) {
                                computer.setBootDiskSlot(target);
                                dev.jstech.computronics.block.MonitorBlock.openBootTarget(
                                        player, level, payload.monitorPos(), payload.hostPos());
                                return;
                            }
                        }
                    }
                }
                default -> {
                }
            }
            PacketDistributor.sendToPlayer(player, buildFirmwareState(level, computer, payload.hostPos()));
        });
    }

    /**
     * Installs the OS from the medium in the reader at {@code readerPos} (or from any linked installer medium
     * when {@code -1}) onto disk slot {@code targetSlot} ({@code -1} = the default target). Unlike the legacy
     * no-OS path this allows a second system beside an installed one (dual boot). Returns whether it installed.
     */
    public static boolean installOsFromReader(final ServerLevel level, final IOsHost computer,
                                              final long readerPos, final int targetSlot) {
        return installFailure(level, computer, readerPos, targetSlot) == null;
    }

    /**
     * The install behind {@link #installOsFromReader}, telling why it did not happen: {@code null} once the
     * system is on the disk, otherwise a sentence for the player. The installer screen plays its progress
     * on the client before the write, so without this a refused install (a system newer than the machine's
     * era, a live medium, no room on the disk) looked exactly like a finished one.
     */
    @Nullable
    public static String installFailure(final ServerLevel level, final IOsHost computer,
                                        final long readerPos, final int targetSlot) {
        final HardwareEra hostEra = computer.installedEra() != null ? computer.installedEra() : HardwareEra.STANDARD;
        String failure = null;
        for (final long endpoint : computer.linkedEndpoints()) {
            if (readerPos >= 0 && endpoint != readerPos) {
                continue;
            }
            if (!(level.getBlockEntity(BlockPos.of(endpoint)) instanceof MediaReaderBlockEntity reader)
                    || reader.insertedKind() != MediaKind.OS_INSTALL || reader.insertedPayload() == null) {
                continue;
            }
            final OsDef def = OsRegistry.getOs(reader.insertedPayload());
            if (def == null) {
                failure = "The system on the medium is not known to this machine.";
                continue;
            }
            if (!OsGating.canInstall(def.minEra(), hostEra)) {
                failure = def.displayName() + " needs " + eraName(def.minEra()) + " era hardware or newer; this machine is "
                        + eraName(hostEra) + " era.";
                continue;
            }
            /*
             * A live/source medium (Arch, Gentoo) never one-click installs: it must be BOOTED and the
             * system put on the disk by hand through its shell. Only guided installers land here.
             */
            if (def.installMode() != dev.jstech.computronics.os.InstallMode.GUIDED) {
                failure = def.displayName() + " is put on the disk by hand from its own shell: boot the medium instead.";
                continue;
            }
            if (computer.installOs(def.id(), targetSlot)) {
                /*
                 * The files are on the disk, but the machine is still running the installer until it
                 * restarts: remember that, so the monitor comes back to the reboot prompt, not the system.
                 */
                computer.setPendingInstallSlot(targetSlot);
                return null;
            }
            return computer.defaultInstallSlot() < 0
                    ? "No disk is installed to put " + def.displayName() + " on."
                    : "The target disk has no room for " + def.displayName() + " ("
                            + def.footprintMb() + " MB needed).";
        }
        return failure != null ? failure : "No installation medium is in a drive linked to this machine.";
    }

    /** The era as the firmware names it to the player: "Vintage", "Legacy", "Standard" ... */
    private static String eraName(final HardwareEra era) {
        final String lower = era.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static void handleRequestFirmware(final RequestFirmwarePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos()) instanceof IOsHost) {
                // Leave whatever screen the request came from (the desktop or the terminal) and enter setup.
                player.closeContainer();
                dev.jstech.computronics.block.MonitorBlock.openFirmware(
                        player, level, payload.monitorPos(), payload.hostPos());
            }
        });
    }

    private static void handleUninstallProgram(final UninstallProgramPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host) {
                final String id = payload.programId();
                final String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                new dev.jstech.computronics.program.ServerCliComputer(host, level)
                        .packageRemove(name);
            }
        });
    }

    /** A player left the monitor: the layout they left behind becomes the machine's. */
    private static void handleDesktopWindowsOnServer(final DesktopWindowsPayload payload,
                                                     final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.host()) instanceof IOsHost computer
                    && computer.isRunning()) {
                /*
                 * A machine that has since been switched off or restarted keeps its empty desktop: the
                 * layout in flight belongs to a session that no longer exists. The machine keeps only the
                 * windows its RAM holds: a client that claims more than fits is trimmed to what does.
                 */
                if (!computer.needsPost()) {
                    computer.setOpenWindows(computer.windowsWithinBudget(payload.toOpenWindows()));
                }
            }
        });
    }

    /** The desktop is opening: hand it the windows the machine has. */
    private static void handleDesktopWindowsOnClient(final DesktopWindowsPayload payload,
                                                     final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.DesktopScreen.applyWindows(payload));
    }

    private static void handlePostComplete(final PostCompletePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos()) instanceof IOsHost computer)) {
                return;
            }
            if (!computer.isRunning()) {
                return; // powered off mid-POST: the screen just stays dark
            }
            computer.setNeedsPost(false);
            /*
             * POST is the moment the machine decides what it is running. Fixing it here is what makes a
             * freshly installed (or removed) desktop package wait for a restart instead of appearing the
             * next time the monitor is opened.
             */
            computer.setBootedDesktopId(computer.installedDesktopId());
            if (payload.enterSetup()) {
                dev.jstech.computronics.block.MonitorBlock.openFirmware(
                        player, level, payload.monitorPos(), payload.hostPos());
            } else {
                dev.jstech.computronics.block.MonitorBlock.openBootTarget(
                        player, level, payload.monitorPos(), payload.hostPos());
            }
        });
    }

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
     * {@link IOsHost#installOs(net.minecraft.resources.ResourceLocation)}.
     *
     * <p>The reader must be linked to the computer over the COMPUTING peripheral cable system
     * (same way a monitor links). Only readers that are already auto-linked endpoints are
     * considered; a reader placed in the world but not yet linked on the peripheral system
     * will not be found here.
     *
     * <p>All gating conditions must be satisfied in order:
     * <ol>
     *   <li>The computer block entity must be an {@link IOsHost} with no OS yet.</li>
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
        if (!(level.getBlockEntity(computerPos) instanceof IOsHost computer)) {
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
            // Live/source media (Arch, Gentoo) install only by hand through their booted shell.
            if (def.installMode() != dev.jstech.computronics.os.InstallMode.GUIDED) {
                continue;
            }
            // installOs checks the footprint against free storage; false means it did not fit.
            computer.installOs(osId);
            return; // first valid linked reader wins
        }
    }

    /** The names of the {@code .craft} files on a medium, in listing order, capped to what a wire field carries. */
    static List<String> craftFileListFromMedia(final ItemStack media) {
        final List<DiskFilesystem.FileEntry> entries =
                DiskFilesystem.list(media, "", FilesystemKind.HIERARCHICAL);
        final List<String> names = new ArrayList<>();
        for (final DiskFilesystem.FileEntry e : entries) {
            /*
             * A name the wire cannot carry would disconnect the player on every listing; the filesystem's own
             * name limit is the cap, so this only guards against a path the filesystem should never hold.
             */
            if (e.type() == FileType.CRAFT && names.size() < CraftManagerStatePayload.MAX_MEDIA_FILES
                    && e.path().length() <= dev.jstech.computronics.os.fs.FsPaths.MAX_NAME_LENGTH) {
                names.add(e.path());
            }
        }
        return names;
    }

    // Crafting Manager (B2): media ↔ ROM transfer

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

    // the Cluster Manager: the Cluster Management Computer's program

    private static void handleRequestClusterManager(final RequestClusterManagerPayload payload,
                                                     final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity cmc)) {
                return;
            }
            PacketDistributor.sendToPlayer(player, buildClusterManagerState(cmc, level, payload.selKind(), payload.selIndex(), ""));
        });
    }

    /** Routes the Cluster Manager state to the open window. */
    private static void handleClusterManagerState(final ClusterManagerStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.ClusterManagerApp.accept(payload));
    }

    private static void handleClusterManagerAction(final ClusterManagerActionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity cmc)) {
                return;
            }
            final var ref = clusterRef(cmc, payload.kind(), payload.index());
            String status = "";
            if (ref == null && payload.action() != ClusterManagerActionPayload.ACTION_REFRESH
                    && payload.action() != ClusterManagerActionPayload.ACTION_CANCEL_JOB) {
                status = "select a cluster first";
            } else {
                final var node = new dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity
                        .NodeRef(BlockPos.of(payload.rackPos()), payload.row());
                status = switch (payload.action()) {
                    case ClusterManagerActionPayload.ACTION_INSTALL_SYSTEM_ALL -> cmc.startJob(ref,
                            dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.JobKind.SYSTEM);
                    case ClusterManagerActionPayload.ACTION_INSTALL_PROGRAM_ALL -> cmc.startJob(ref,
                            dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.JobKind.PROGRAM);
                    case ClusterManagerActionPayload.ACTION_INSTALL_SYSTEM_NODE -> cmc.startJob(ref,
                            dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.JobKind.SYSTEM,
                            List.of(node));
                    case ClusterManagerActionPayload.ACTION_INSTALL_PROGRAM_NODE -> cmc.startJob(ref,
                            dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.JobKind.PROGRAM,
                            List.of(node));
                    case ClusterManagerActionPayload.ACTION_POWER_ALL_ON -> cmc.powerAll(ref, true) + " bay(s) switched on";
                    case ClusterManagerActionPayload.ACTION_POWER_ALL_OFF -> cmc.powerAll(ref, false) + " bay(s) switched off";
                    case ClusterManagerActionPayload.ACTION_TOGGLE_NODE -> cmc.toggleNode(node.rack(), node.row())
                            ? "" : "that row is not a node this card reaches";
                    case ClusterManagerActionPayload.ACTION_CANCEL_JOB -> cmc.cancelJob() ? "job cancelled after the nodes being written" : "";
                    case ClusterManagerActionPayload.ACTION_CYCLE_BALANCE -> {
                        if (ref.face() != null && level.getBlockEntity(ref.anchor()) instanceof ServerRouterBlockEntity router) {
                            router.cycleLoadBalanceMode(ref.face());
                        }
                        yield "";
                    }
                    case ClusterManagerActionPayload.ACTION_DEPOSIT, ClusterManagerActionPayload.ACTION_DEPOSIT_ONE ->
                            depositIntoSection(player, cmc, ref, payload.action() == ClusterManagerActionPayload.ACTION_DEPOSIT_ONE);
                    default -> "";
                };
            }
            PacketDistributor.sendToPlayer(player, buildClusterManagerState(cmc, level, payload.kind(), payload.index(), status));
        });
    }

    private static void handleClusterRename(final ClusterRenamePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity cmc)) {
                return;
            }
            final var ref = clusterRef(cmc, payload.kind(), payload.index());
            String status = "select a cluster first";
            if (ref != null) {
                final String typed = payload.name().strip().replaceAll("\\p{Cntrl}", "");
                final String name = typed.length() > ClusterRenamePayload.MAX_NAME
                        ? typed.substring(0, ClusterRenamePayload.MAX_NAME) : typed;
                if (ref.face() != null && level.getBlockEntity(ref.anchor()) instanceof ServerRouterBlockEntity router) {
                    router.setSectionName(ref.face(), name);
                    status = name.isEmpty() ? "section name cleared" : "section renamed to " + name;
                } else if (ref.face() == null && cmc.supercomputerAt(ref.anchor())
                        instanceof dev.jstech.computronics.blockentity.HbwInterfaceBlockEntity hub) {
                    hub.setCustomName(name);
                    status = name.isEmpty() ? "supercomputer name cleared" : "supercomputer renamed to " + name;
                }
            }
            PacketDistributor.sendToPlayer(player, buildClusterManagerState(cmc, level, payload.kind(), payload.index(), status));
        });
    }

    private static void handleClusterMoveOut(final ClusterMoveOutPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.quantity() <= 0L
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity cmc)) {
                return;
            }
            final var ref = clusterRef(cmc, ClusterManagerStatePayload.KIND_DATACENTER, payload.index());
            final NetworkUuid net = cmc.networkUuid();
            if (ref == null || net == null
                    || !(level.getBlockEntity(BlockPos.of(payload.destPos())) instanceof IComputerTerminalHost dest)
                    || !net.equals(dest.networkUuid())) {
                return; // the destination must be on this machine's own network
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            final java.util.Set<NodeUuid> sources = new java.util.HashSet<>();
            final var section = cmc.sectionAt(ref.anchor(), ref.face());
            if (mainframe == null || section == null) {
                return;
            }
            sources.addAll(section.section().servers());
            if (sources.isEmpty()) {
                return;
            }
            final var op = mainframe.submitNetworkMove(payload.key(), payload.quantity(), dest.localStorage(),
                    cmc.originLabel(MoveLabels.CLUSTER_MANAGER), sources);
            if (op != null) {
                op.onSettle(() -> PacketDistributor.sendToPlayer(player,
                        buildClusterManagerState(cmc, level, ClusterManagerStatePayload.KIND_DATACENTER, payload.index(), "")));
            }
            PacketDistributor.sendToPlayer(player,
                    buildClusterManagerState(cmc, level, ClusterManagerStatePayload.KIND_DATACENTER, payload.index(), "moving"));
        });
    }

    /** The cluster a (kind, index) pair names in the state's own order, or null. */
    @org.jetbrains.annotations.Nullable
    private static dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.ClusterRef clusterRef(
            final dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity cmc,
            final int kind, final int index) {
        if (kind == ClusterManagerStatePayload.KIND_SUPERCOMPUTER) {
            final var hubs = cmc.supercomputers();
            return index >= 0 && index < hubs.size()
                    ? new dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.ClusterRef(
                            dev.jstech.computronics.rack.RackChassis.RackType.SUPERCOMPUTER,
                            hubs.get(index).getBlockPos(), null)
                    : null;
        }
        if (kind == ClusterManagerStatePayload.KIND_DATACENTER) {
            final var sections = cmc.datacenterSections();
            return index >= 0 && index < sections.size()
                    ? new dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.ClusterRef(
                            dev.jstech.computronics.rack.RackChassis.RackType.SERVER,
                            sections.get(index).routerPos(), sections.get(index).face())
                    : null;
        }
        return null;
    }

    /** Puts the stack on the player's cursor into the section's servers, spread by its balance mode. */
    private static String depositIntoSection(
            final ServerPlayer player,
            final dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity cmc,
            final dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.ClusterRef ref,
            final boolean single) {
        final ItemStack cursor = player.containerMenu.getCarried();
        final var section = cmc.sectionAt(ref.anchor(), ref.face());
        if (cursor.isEmpty() || section == null || !(player.level() instanceof ServerLevel level)) {
            return "";
        }
        final List<ServerStore> stores = sectionStores(level, section.section().servers());
        if (stores.isEmpty()) {
            return "no servers in that section";
        }
        final ServerRouterBlockEntity router =
                level.getBlockEntity(ref.anchor()) instanceof ServerRouterBlockEntity r ? r : null;
        final dev.jstech.computronics.datacenter.LoadBalanceMode mode = router != null
                ? router.loadBalanceMode(ref.face())
                : dev.jstech.computronics.datacenter.LoadBalanceMode.ROUND_ROBIN;
        final StorageKey key = StorageKey.of(cursor);
        final long want = single ? 1L : cursor.getCount();
        /*
         * The rotation lives on the router, so a run of single-item deposits really does move down the row
         * of servers instead of piling onto the first one every time.
         */
        final int start = router != null && ref.face() != null ? router.nextBalanceStart(ref.face()) : 0;
        final long stored = LoadBalancer.insert(stores, key, want, mode, start);
        if (stored > 0L) {
            cursor.shrink((int) stored);
            player.containerMenu.setCarried(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
            player.containerMenu.broadcastChanges();
        }
        return stored > 0L ? "deposited " + stored : "the section has no room";
    }

    /** Everything the Cluster Manager shows, for one machine and one selected cluster. */
    public static ClusterManagerStatePayload buildClusterManagerState(
            final dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity cmc,
            final ServerLevel level, final int selKind, final int selIndex, final String status) {
        final var card = cmc.clusterCard();
        final var systemDisc = cmc.medium(dev.jstech.computronics.os.media.MediaKind.OS_INSTALL);
        final var program = cmc.medium(dev.jstech.computronics.os.media.MediaKind.PROGRAM_INSTALL);
        final var head = new ClusterManagerStatePayload.Head(card != null, card == null ? 0 : card.reach().ordinal(),
                cmc.parallelLanes(), systemDisc == null ? "" : systemDisc.label(), program == null ? "" : program.label(),
                status.isEmpty() ? cmc.lastJobSummary() : status);
        final List<ClusterManagerStatePayload.WireCluster> clusters = new ArrayList<>();
        final var hubs = cmc.supercomputers();
        for (int i = 0; i < hubs.size() && clusters.size() < ClusterManagerStatePayload.MAX_CLUSTERS; i++) {
            final var hub = hubs.get(i);
            final int nodes = hub.clusterNodes().size();
            clusters.add(new ClusterManagerStatePayload.WireCluster(ClusterManagerStatePayload.KIND_SUPERCOMPUTER, i,
                    dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity
                            .supercomputerName(hub, i),
                    hub.clusterOnline(), nodes, hub.craftSlotsInUse(), hub.parallelCrafts(), 0,
                    nodes + " nodes · " + hub.craftSlotsInUse() + "/" + hub.parallelCrafts(),
                    cmc.reaches(dev.jstech.computronics.rack.RackChassis.RackType.SUPERCOMPUTER)));
        }
        final var sections = cmc.datacenterSections();
        for (int i = 0; i < sections.size() && clusters.size() < ClusterManagerStatePayload.MAX_CLUSTERS; i++) {
            final var ref = sections.get(i);
            long used = 0L;
            long total = 0L;
            for (final ServerStore store : sectionStores(level, ref.section().servers())) {
                used += store.usedWeight();
                total += store.capacityWeight();
            }
            final int mode = level.getBlockEntity(ref.routerPos()) instanceof ServerRouterBlockEntity router
                    ? router.loadBalanceMode(ref.face()).ordinal() : 0;
            clusters.add(new ClusterManagerStatePayload.WireCluster(ClusterManagerStatePayload.KIND_DATACENTER, i,
                    ref.label(), ref.section().serverCount() > 0, ref.section().serverCount(), used, total, mode,
                    ref.section().rackCount() + " racks · " + ref.section().serverCount() + " srv",
                    cmc.reaches(dev.jstech.computronics.rack.RackChassis.RackType.SERVER)));
        }
        // The selected cluster in detail.
        ClusterManagerStatePayload.Detail detail = ClusterManagerStatePayload.Detail.none();
        final List<NetworkItemEntry> items = new ArrayList<>();
        final List<ClusterManagerStatePayload.WireDest> dests = new ArrayList<>();
        if (selKind == ClusterManagerStatePayload.KIND_SUPERCOMPUTER && selIndex >= 0 && selIndex < hubs.size()) {
            final var hub = hubs.get(selIndex);
            final List<ClusterManagerStatePayload.WireNode> nodes = new ArrayList<>();
            final java.util.Map<BlockPos, Integer> rackIndex = new java.util.LinkedHashMap<>();
            final var slots = hub.clusterSlots();
            int i = 0;
            for (final var node : hub.clusterNodes()) {
                if (level.getBlockEntity(node.rack()) instanceof ServerRackBlockEntity rack && nodes.size() < ClusterManagerStatePayload.MAX_NODES) {
                    final int rIdx = rackIndex.computeIfAbsent(node.rack(), r -> rackIndex.size() + 1);
                    final ItemStack server = rack.getServers().getStackInSlot(node.row());
                    final var host = rack.unitHost(node.row());
                    final var phi = dev.jstech.computronics.blockentity.HbwInterfaceBlockEntity.installedPhi(server);
                    final int slotIndex = i < slots.size() ? i : -1;
                    final int code = slotIndex >= 0 ? slots.get(slotIndex).code()
                            : dev.jstech.computronics.blockentity.HbwInterfaceBlockEntity.SLOT_EMPTY;
                    nodes.add(new ClusterManagerStatePayload.WireNode(node.rack().asLong(), rIdx, node.row(),
                            dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.nodeName(rack, node.row()),
                            osLabel(host), programsLabel(host),
                            phi == null ? -1 : dev.jstech.computronics.blockentity.HbwInterfaceBlockEntity.modelIndex(phi.spec()),
                            rack.bayPowerOn(node.row()), slotIndex, code, 0L, 0L,
                            nodeState(cmc, rack, node.row(), server, host, slotIndex, code)));
                }
                i++;
            }
            final List<ClusterManagerStatePayload.WireCraft> queue = new ArrayList<>();
            final NetworkUuid net = hub.networkUuid();
            final MainframeBlockEntity mainframe = net == null ? null : resolveMainframe(level, net);
            if (mainframe != null) {
                final java.util.Map<java.util.UUID, Integer> held = hub.heldSlots();
                final List<ClusterManagerStatePayload.WireCraft> waiting = new ArrayList<>();
                for (final var operation : mainframe.liveOperations()) {
                    if (!(operation instanceof dev.jstech.computronics.crafting.NetworkCraftOperation craft)
                            || craft.isDone()) {
                        continue;
                    }
                    final OperationRecord record = craft.liveRecord();
                    final String label = record.key().displayName().getString() + " x" + record.requested();
                    final int held0 = held.getOrDefault(craft.operationId(), 0);
                    if (held0 > 0) {
                        queue.add(new ClusterManagerStatePayload.WireCraft(label, craft.requesterLabel(), held0, false));
                    } else if (record.status() == OperationRecord.STATUS_WAITING && craft.usesSupercomputer(hub.getBlockPos())) {
                        waiting.add(new ClusterManagerStatePayload.WireCraft(label, craft.requesterLabel(), 0, true));
                    }
                }
                queue.addAll(waiting);
            }
            final BlockPos hp = hub.getBlockPos();
            detail = new ClusterManagerStatePayload.Detail(selKind, selIndex,
                    dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity
                            .supercomputerName(hub, selIndex),
                    "HBW Interface at " + hp.getX() + ", " + hp.getY() + ", " + hp.getZ() + " · " + rackIndex.size()
                            + " rack" + (rackIndex.size() == 1 ? "" : "s") + " · " + hub.craftSlotsInUse() + "/"
                            + hub.parallelCrafts() + " crafts",
                    hub.clusterOnline(), 0, nodes,
                    queue.size() > ClusterManagerStatePayload.MAX_QUEUE ? queue.subList(0, ClusterManagerStatePayload.MAX_QUEUE) : queue);
        } else if (selKind == ClusterManagerStatePayload.KIND_DATACENTER && selIndex >= 0 && selIndex < sections.size()) {
            final var ref = sections.get(selIndex);
            final NetworkSystem system = NetworkSystem.get(level);
            final List<ClusterManagerStatePayload.WireNode> nodes = new ArrayList<>();
            final java.util.Map<BlockPos, Integer> rackIndex = new java.util.LinkedHashMap<>();
            /*
             * Every seated server in the section's cabinets, switched on or off: a bay the manager powered
             * off has left the network, and must still be listed so the manager can power it back on.
             */
            for (final long rackLong : ref.section().rackPositions()) {
                final BlockPos rackPos = BlockPos.of(rackLong);
                if (!(level.getBlockEntity(rackPos) instanceof ServerRackBlockEntity rack)) {
                    continue;
                }
                final int rIdx = rackIndex.computeIfAbsent(rackPos, r -> rackIndex.size() + 1);
                for (final int row : rack.computerSlots()) {
                    if (nodes.size() >= ClusterManagerStatePayload.MAX_NODES) {
                        break;
                    }
                    final var host = rack.unitHost(row);
                    final ServerStore store = rack.getServerStorage(row);
                    nodes.add(new ClusterManagerStatePayload.WireNode(rackPos.asLong(), rIdx, row,
                            dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.nodeName(rack, row),
                            osLabel(host), programsLabel(host), -1, rack.bayPowerOn(row), -1, 0,
                            store == null ? 0L : store.usedWeight(), store == null ? 0L : store.capacityWeight(),
                            nodeState(cmc, rack, row, rack.getServers().getStackInSlot(row), host, -1, -1)));
                }
            }
            final int mode = level.getBlockEntity(ref.routerPos()) instanceof ServerRouterBlockEntity router
                    ? router.loadBalanceMode(ref.face()).ordinal() : 0;
            final BlockPos rp = ref.routerPos();
            detail = new ClusterManagerStatePayload.Detail(selKind, selIndex, ref.label(),
                    ref.section().rackCount() + " rack" + (ref.section().rackCount() == 1 ? "" : "s") + " · "
                            + ref.section().serverCount() + " servers @ " + rp.getX() + ", " + rp.getY() + ", " + rp.getZ(),
                    ref.section().serverCount() > 0, mode, nodes, List.of());
            // The section's inventory, and where a move-out can go: the network's computers with local storage.
            final java.util.Map<StorageKey, Long> totals = dev.jstech.computronics.operation.NetworkStorage
                    .ofServers(level, ref.section().servers()).query();
            totals.entrySet().stream().limit(ClusterManagerStatePayload.MAX_ITEMS)
                    .forEach(e -> items.add(new NetworkItemEntry(e.getKey(), e.getValue())));
            final NetworkUuid net = cmc.networkUuid();
            if (net != null) {
                for (final var pc : system.personalComputersOf(net)) {
                    if (level.getBlockEntity(BlockPos.of(pc.pos())) instanceof IComputerTerminalHost pcHost
                            && pcHost.localStorageCapacity() > 0L && dests.size() < ClusterManagerStatePayload.MAX_DESTS) {
                        final String name = level.getBlockEntity(BlockPos.of(pc.pos()))
                                instanceof dev.jstech.computronics.os.IOsHost os && !os.customName().isEmpty()
                                ? os.customName() : "PC-" + pc.nodeUuid().asString().substring(0, 4);
                        dests.add(new ClusterManagerStatePayload.WireDest(pc.pos(), name));
                    }
                }
                system.mainframePositionOf(net).ifPresent(mfPos -> {
                    if (level.getBlockEntity(BlockPos.of(mfPos)) instanceof IComputerTerminalHost host
                            && host.localStorageCapacity() > 0L) {
                        dests.add(new ClusterManagerStatePayload.WireDest(mfPos, "Mainframe"));
                    }
                });
            }
        }
        // The job in flight, if any.
        ClusterManagerStatePayload.WireJob job = ClusterManagerStatePayload.WireJob.none(cmc.lastJobSummary());
        final var running = cmc.job();
        if (running != null) {
            final List<ClusterManagerStatePayload.WireLane> lanes = new ArrayList<>();
            for (final var lane : running.lanes()) {
                if (lanes.size() < ClusterManagerStatePayload.MAX_LANES) {
                    lanes.add(new ClusterManagerStatePayload.WireLane(lane.name(), lane.permille()));
                }
            }
            final var ref = running.cluster();
            int clusterIndex = -1;
            final int clusterKind = ref.kind() == dev.jstech.computronics.rack.RackChassis.RackType.SUPERCOMPUTER
                    ? ClusterManagerStatePayload.KIND_SUPERCOMPUTER : ClusterManagerStatePayload.KIND_DATACENTER;
            if (clusterKind == ClusterManagerStatePayload.KIND_SUPERCOMPUTER) {
                for (int i = 0; i < hubs.size(); i++) {
                    if (hubs.get(i).getBlockPos().equals(ref.anchor())) {
                        clusterIndex = i;
                    }
                }
            } else {
                for (int i = 0; i < sections.size(); i++) {
                    if (sections.get(i).routerPos().equals(ref.anchor()) && sections.get(i).face() == ref.face()) {
                        clusterIndex = i;
                    }
                }
            }
            job = new ClusterManagerStatePayload.WireJob(true, running.kind().ordinal(), running.medium().label(),
                    clusterKind, clusterIndex, running.done(), running.skipped(), running.queued(), running.total(),
                    running.elapsedTicks(), running.cancelled(), lanes, cmc.lastJobSummary());
        }
        return new ClusterManagerStatePayload(head, clusters, detail, job, items, dests);
    }

    /**
     * What one machine in a cluster is doing, in the order it matters to the player: a machine that is not
     * assembled cannot be powered, one with no power cannot be written to, and a supercomputer node without
     * a working coprocessor holds a slot without contributing to a single craft. Without this the manager
     * showed the disk and the system but never whether the machine was actually up.
     */
    private static int nodeState(
            final dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity cmc,
            final ServerRackBlockEntity rack, final int row, final ItemStack server,
            final dev.jstech.computronics.os.IOsHost host, final int slotIndex, final int code) {
        if (dev.jstech.computronics.item.ServerItem.build(server) == null) {
            return ClusterManagerStatePayload.STATE_INCOMPLETE;
        }
        if (!rack.bayPowerOn(row)) {
            return ClusterManagerStatePayload.STATE_BAY_OFF;
        }
        final var job = cmc.job();
        if (job != null) {
            for (final var lane : job.lanes()) {
                if (lane.node().row() == row && lane.node().rack().equals(rack.getBlockPos())) {
                    return ClusterManagerStatePayload.STATE_INSTALLING;
                }
            }
        }
        if (code >= 0) {   // a supercomputer node: its cluster slot decides whether it counts for anything
            if (slotIndex < 0) {
                return ClusterManagerStatePayload.STATE_UNSLOTTED;
            }
            if (code == dev.jstech.computronics.blockentity.HbwInterfaceBlockEntity.SLOT_EMPTY) {
                return ClusterManagerStatePayload.STATE_NO_COPROCESSOR;
            }
            if (code == dev.jstech.computronics.blockentity.HbwInterfaceBlockEntity.SLOT_UNDER_RATED) {
                return ClusterManagerStatePayload.STATE_UNDER_RATED;
            }
        }
        return host.installedOsId() == null ? ClusterManagerStatePayload.STATE_NO_SYSTEM
                : ClusterManagerStatePayload.STATE_ONLINE;
    }

    private static String osLabel(final dev.jstech.computronics.os.IOsHost host) {
        final net.minecraft.resources.ResourceLocation osId = host.installedOsId();
        final OsDef os = osId == null ? null : OsRegistry.getOs(osId);
        return os == null ? "" : os.displayName();
    }

    private static String programsLabel(final dev.jstech.computronics.os.IOsHost host) {
        if (host.console() == null) {
            return "";
        }
        final StringBuilder out = new StringBuilder();
        for (final String id : host.console().installed()) {
            final net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            final dev.jstech.computronics.os.ProgramSpec spec = rl == null ? null : OsRegistry.getProgram(rl);
            if (spec == null || spec.preinstalled()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(spec.displayName());
            if (out.length() > 140) {
                out.append(", ...");
                break;
            }
        }
        return out.toString();
    }

    /** Routes the Crafting Manager state payload to the open {@link dev.jstech.computronics.client.os.CraftingManagerApp}. */
    private static void handleCraftManagerState(final CraftManagerStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jstech.computronics.client.os.CraftingManagerApp.accept(payload));
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
                    romNames.add(craftFileNameFor(p) + ".craft");
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
                            dev.jstech.computronics.crafting.NetworkRecipe.ofProcessing(p.get()));
                } else if ("multi".equals(kind)) {
                    final var p = CraftFile.parseMultiStage(content.get(), level.registryAccess());
                    if (p.isEmpty()) {
                        continue;
                    }
                    parsed++;
                    added = cc.loadMachineRecipe(
                            dev.jstech.computronics.crafting.NetworkRecipe.ofMultiStage(p.get()));
                } else {
                    final var p = CraftFile.parse(content.get(), level.registryAccess());
                    if (p.isEmpty()) {
                        continue;
                    }
                    parsed++;
                    added = cc.loadPattern(p.get());
                    diskName = craftFileNameFor(p.get()) + ".craft";
                }
                /*
                 * The recipe registers in the ROM (what the network can craft) and the .craft is mirrored under
                 * crafts/ on the system disk so it shows up in the Files app.
                 */
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
                final String diskName = craftFileNameFor(rom.get(idx)) + ".craft";
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
    static void writeCraftToDisk(final CraftingComputerBlockEntity cc, final String fileName,
                                         final String content) {
        final net.minecraft.world.item.ItemStack disk = cc.systemDisk();
        if (disk.isEmpty()) {
            return;
        }
        final dev.jstech.computronics.os.FilesystemKind kind = filesystemKindOf(cc);
        if (kind == dev.jstech.computronics.os.FilesystemKind.NONE) {
            return;
        }
        final String path;
        if (kind == dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL) {
            DiskFilesystem.mkdir(disk, CRAFTS_DIR, kind);
            path = CRAFTS_DIR + "/" + fileName;
        } else {
            path = fileName;
        }
        DiskFilesystem.write(disk, path, FileType.CRAFT, content, cc.systemDiskFreeWeight(), kind,
                cc.getLevel() == null ? 0L : cc.getLevel().getGameTime());
    }

    /** Deletes a mirrored {@code .craft} from the Crafting Computer's system disk, if present. */
    private static void deleteCraftFromDisk(final CraftingComputerBlockEntity cc, final String fileName) {
        final net.minecraft.world.item.ItemStack disk = cc.systemDisk();
        if (disk.isEmpty()) {
            return;
        }
        final dev.jstech.computronics.os.FilesystemKind kind = filesystemKindOf(cc);
        if (kind == dev.jstech.computronics.os.FilesystemKind.NONE) {
            return;
        }
        final String path = kind == dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL
                ? CRAFTS_DIR + "/" + fileName : fileName;
        DiskFilesystem.delete(disk, path);
    }

    /**
     * Reconciles the {@code crafts/} folder on the system disk against the Recipe ROM: writes a
     * {@code .craft} for any ROM pattern missing its mirror file. This self-heals a disk whose ROM
     * predates the mirror, so opening the Crafting Manager always presents a consistent view.
     */
    static void reconcileCraftsFolder(final CraftingComputerBlockEntity cc, final ServerLevel level) {
        if (cc.systemDisk().isEmpty()) {
            return;
        }
        boolean wrote = false;
        for (final CraftingPattern pattern : cc.romPatterns()) {
            final String diskName = craftFileNameFor(pattern) + ".craft";
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
    static boolean craftFileExistsOnDisk(final CraftingComputerBlockEntity cc, final String fileName) {
        final net.minecraft.world.item.ItemStack disk = cc.systemDisk();
        if (disk.isEmpty()) {
            return false;
        }
        final dev.jstech.computronics.os.FilesystemKind kind = filesystemKindOf(cc);
        if (kind == dev.jstech.computronics.os.FilesystemKind.NONE) {
            return false;
        }
        final String path = kind == dev.jstech.computronics.os.FilesystemKind.HIERARCHICAL
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
                final String base;
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
                    base = sanitizeFileBase(r.displayName());
                } else {
                    if (idx < 0 || idx >= rom.size()) {
                        continue;
                    }
                    final CraftingPattern pattern = rom.get(idx);
                    content = CraftFile.serialize(pattern, level.registryAccess());
                    base = craftFileNameFor(pattern);
                }
                if (content.isEmpty()) {
                    continue;
                }
                /*
                 * A recipe already on the disc under this name is never overwritten: a different one gets the
                 * next free suffix, the same one is simply there already. The encoder writes by the same rule.
                 */
                final String fileName = DiskFilesystem.uniquePath(media, base, ".craft", content.get());
                final long freeWeight = mediaFreeWeightFor(media);
                DiskFilesystem.write(media, fileName, FileType.CRAFT, content.get(),
                        freeWeight, FilesystemKind.HIERARCHICAL, level.getGameTime());
            }
            // Propagate the updated filesystem component to the reader slot.
            final String rest = key.substring("media:".length());
            final int slash = rest.indexOf('/');
            final String rawPos = slash < 0 ? rest : rest.substring(0, slash);
            try {
                final long encoded = Long.parseLong(rawPos);
                if (level.getBlockEntity(net.minecraft.core.BlockPos.of(encoded))
                        instanceof dev.jstech.computronics.os.media.MediaReaderBlockEntity reader) {
                    reader.setChanged();
                }
            } catch (final NumberFormatException ignored) {
            }
            PacketDistributor.sendToPlayer(player, buildCraftManagerState(cc, level));
        });
    }

    /**
     * Builds a full {@link CraftManagerStatePayload} for {@code cc}: picks the linked drive whose writable
     * removable medium holds {@code .craft} files (or, when none does, the first one holding writable
     * media, so a download still has a target), lists that medium's files, and annotates each ROM pattern
     * with whether a matching file already exists on it.
     */
    public static CraftManagerStatePayload buildCraftManagerState(final CraftingComputerBlockEntity cc,
                                                                   final ServerLevel level) {
        return buildCraftManagerState(cc, level, "");
    }

    private static CraftManagerStatePayload buildCraftManagerState(final CraftingComputerBlockEntity cc,
                                                                    final ServerLevel level, final String status) {
        String mediaVolumeKey = "";
        String mediaLabel = "";
        List<String> mediaFiles = List.of();

        /*
         * A computer commonly has more than one drive linked (a floppy drive, a DVD drive, a dock), and the
         * one with a blank medium in it may well come first: the disc the player just wrote is the one they
         * mean, wherever it sits.
         */
        for (final long endpoint : cc.linkedEndpoints()) {
            if (level.getBlockEntity(net.minecraft.core.BlockPos.of(endpoint))
                    instanceof dev.jstech.computronics.os.media.MediaReaderBlockEntity reader) {
                final ItemStack m = reader.mediaSlot().getStackInSlot(0);
                if (m.isEmpty()
                        || !(m.getItem() instanceof dev.jstech.computronics.os.media.FormattedMediaItem fmt)
                        || !fmt.writable()) {
                    continue;
                }
                final List<String> files = craftFileListFromMedia(m);
                if (mediaVolumeKey.isEmpty() || !files.isEmpty()) {
                    mediaVolumeKey = "media:" + endpoint;
                    mediaLabel = wire(dev.jstech.computronics.os.VolumeLabel.of(m, "Removable Drive"), 64);
                    mediaFiles = files;
                }
                if (!files.isEmpty()) {
                    break;
                }
            }
        }

        final Set<String> mediaFileSet = new HashSet<>(mediaFiles);
        final List<CraftManagerStatePayload.WireRomEntry> romEntries = new ArrayList<>();
        final List<CraftingPattern> rom = cc.romPatterns();
        for (int i = 0; i < rom.size() && i < CraftManagerStatePayload.MAX_ROM_ENTRIES; i++) {
            final CraftingPattern p = rom.get(i);
            /*
             * Every string below is cut to its wire field: a result renamed to a long name or a modded machine
             * with a long id must never make the state impossible to send.
             */
            final String name = wire(p.displayName(), 64);
            final String fileName = craftFileNameFor(p) + ".craft";
            romEntries.add(new CraftManagerStatePayload.WireRomEntry(i, name, mediaFileSet.contains(fileName)));
        }
        /*
         * Machine recipes (processing / multi-stage) share the ROM and must be listed too, since an invisible entry
         * reads as "not loaded" and then the duplicate check looks wrong. Their indices are offset so the
         * remove action can tell them apart from the bench patterns above.
         */
        final var machineRecipes = cc.machineRecipes();
        for (int i = 0; i < machineRecipes.size()
                && romEntries.size() < CraftManagerStatePayload.MAX_ROM_ENTRIES; i++) {
            final var r = machineRecipes.get(i);
            final String name = wire(r.displayName() + (r.multi().isPresent() ? " [multi]" : " [machine]"), 64);
            romEntries.add(new CraftManagerStatePayload.WireRomEntry(
                    CraftManagerStatePayload.MACHINE_ROM_BASE + i, name, false));
        }
        /*
         * The routed machines (the Machines tab): each distinct machine type the wired switches declare, with
         * its concurrency config. Keyed by the machine's block registry id, which is what a pattern targets.
         * One wire per PHYSICAL machine (deduped by position). Paused/Feed are read per machine; Max Jobs is the
         * machine type's shared ceiling. The label distinguishes machines of one type by their face and position.
         */
        final List<CraftManagerStatePayload.WireMachine> machines = new ArrayList<>();
        final Set<net.minecraft.core.BlockPos> seen = new HashSet<>();
        for (final var dm : cc.availableMachines()) {
            final net.minecraft.core.BlockPos pos = dm.machinePos();
            final String typeKey = dm.machineType();
            if (typeKey == null || typeKey.isBlank() || pos == null || !seen.add(pos)
                    || machines.size() >= CraftManagerStatePayload.MAX_MACHINES) {
                continue;
            }
            final String machineKey = CraftingComputerBlockEntity.machineStateKey(pos);
            final CraftingComputerBlockEntity.MachineConfig perMachine = cc.machineConfig(machineKey);
            final int typeMaxJobs = cc.machineConfig(typeKey).maxJobs();
            final String face = dm.face() != null
                    ? dm.face().getName().substring(0, 1).toUpperCase(java.util.Locale.ROOT) + " " : "";
            final String label = !dm.name().isBlank() ? dm.name()
                    : face + "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
            machines.add(new CraftManagerStatePayload.WireMachine(
                    wire(machineKey, 64), wire(typeKey, 48), wire(label, 80),
                    perMachine.locked(), perMachine.feedMax(), typeMaxJobs));
        }
        return new CraftManagerStatePayload(mediaVolumeKey, mediaLabel, mediaFiles, romEntries,
                cc.craftingCardFactor() > 0.0, wire(status, 96), machines);
    }

    /** Cuts {@code s} to the {@code max} characters its wire field carries: sending more disconnects the player. */
    private static String wire(final String s, final int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * The file base-name a bench pattern goes by: the name its author gave it, sanitized, or its result's
     * registry path when it has none (what every pattern was called before names existed).
     */
    static String craftFileNameFor(final CraftingPattern pattern) {
        return pattern.name().isEmpty() ? craftFileNameFor(pattern.result()) : sanitizeFileBase(pattern.name());
    }

    /** Derives a safe file base-name from the result {@link ItemStack}'s registry path. */
    static String craftFileNameFor(final ItemStack result) {
        final net.minecraft.resources.ResourceLocation key =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem());
        return sanitizeFileBase(key == null ? "pattern" : key.getPath());
    }

    /** Clamps a display or registry name to a safe file base (letters/digits/underscore, max 32 chars). */
    static String sanitizeFileBase(final String base) {
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
        if (!(media.getItem() instanceof dev.jstech.computronics.os.media.FormattedMediaItem fmt)) {
            return 0L;
        }
        final long capWeight = (long) fmt.format().capacityItems()
                * dev.jstech.computronics.storage.StorageKey.MB_EQ_PER_ITEM;
        final long fsUsed = dev.jstech.computronics.os.fs.DiskFilesystem.filesWeight(media);
        return Math.max(0L, capWeight - fsUsed);
    }

    // IQL filesystem: save/open/list .iql files on the Mainframe's system disk

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
                            instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host)
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
                            freeWeight, kind, mainframe.getLevel() == null ? 0L : mainframe.getLevel().getGameTime());
            final boolean ok = result == DiskFilesystem.WriteResult.OK;
            if (ok) {
                mainframe.setChanged();
            }
            final String status = switch (result) {
                case OK -> "saved: " + fileName;
                case DISK_FULL -> "disk full, free space on the Mainframe's system disk";
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
                            instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host)
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
                dev.jstech.computronics.client.NmsApp.acceptFileList(payload));
    }

    /** Reads an {@code .iql} file from the Mainframe's disk and sends its content back. */
    private static void handleOpenIqlFile(final OpenIqlFilePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jstech.computronics.terminal.IComputerTerminalHost host)
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
                dev.jstech.computronics.client.NmsApp.acceptFileContent(payload));
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
            final IComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
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
            final DataHandoff.ISource source = fromCursor
                    ? DataHandoff.cursor(player) : DataHandoff.slot(menu.getSlot(idx), player);
            /*
             * A right-click hands over ONE: one item, or what a held container holds, and a held empty
             * container over a fluid or chemical entry fills from the disks instead. Left click and
             * shift-click deposit the stack as items, the way a chest takes them.
             */
            final boolean one = idx == TerminalLocalDepositPayload.CURSOR_ONE;
            final DataHandoff.Outcome outcome;
            if (one && payload.entry().isPresent() && DataContainers.canTake(source.get(), payload.entry().get())) {
                outcome = DataHandoff.fillFromLocalStore(host.localStore(), player, source, payload.entry().get());
            } else {
                outcome = DataHandoff.intoLocalStore(host.localStore(), player, source,
                        one ? 1 : source.get().getCount(), one);
            }
            if (outcome == DataHandoff.Outcome.DEPOSITED || outcome == DataHandoff.Outcome.FILLED) {
                dispatchLocalSnapshot(player, host);
            }
        });
    }
}
