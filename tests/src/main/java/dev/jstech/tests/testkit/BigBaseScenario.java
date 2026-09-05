/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.testkit;

import dev.jsc.jscomputronics.common.hardware.DiskSize;
import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jstech.core.multiblock.AbstractMultiblockControllerBlock;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.HardwareItems;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import dev.jsc.jscomputronics.module.industrial.blockentity.AbstractMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A big, realistic base built to order: a Mainframe on a bandwidth backbone, datacenter sections of
 * fully cabled cabinets hanging off Server Routers, supercomputers on their own fabrics, personal
 * computers and Crafting Computers with machines behind them, a catalog of thousands of item types
 * (some of them component variants) spread over every server, and feedstock for the crafting traffic.
 * The scale benchmarks build it in the 48-block test arena; {@code /jsc benchmark build} builds the
 * same thing in a real world to walk around in.
 *
 * <p>Two layouts: the {@link Layout#WIDE} one runs a single backbone across the arena with sections
 * south of it and everything else north; the {@link Layout#DENSE} one runs two backbones with sections
 * on both sides, a spine for the supercomputers between them, and the desks along a west connector —
 * as many cabinets as the arena holds. Cabinets are formed like placed ones, part blocks and all.
 */
public final class BigBaseScenario {

    public enum Layout { WIDE, DENSE }

    /** Which chassis every rack seats: 1U servers (eight a cabinet) or 2U storage servers with eight drive bays each. */
    public enum Chassis { SERVER, STORAGE }

    /**
     * How big the base is. Every knob is bounded by what fits the arena or a cabinet; see {@link #validate}.
     *
     * @param routers         Server Routers per backbone side
     * @param racksPerRouter  cabinets on each router's section (the dense layout's one short row seats six at most)
     * @param serversPerRack  chassis per cabinet
     * @param drivesPerServer drives in each server's bay (a 1U chassis cables three, a storage chassis eight)
     * @param driveSize       the size of every drive
     * @param maxLot          the most items one seeded key gets; the average lot is half of it
     * @param feedstock       logs and copper ingots seeded for the crafting traffic
     * @param machineDesks    Crafting Computers with a Compressor behind a crafting switch
     * @param mainframeGpus   graphics cards in the Mainframe: each one is another parallel operation queue
     */
    public record Params(Layout layout, Chassis chassis, int routers, int racksPerRouter, int serversPerRack,
                         int types, int variantPercent, int supercomputers, int nodeRacksPerSupercomputer,
                         int personalComputers, int machineDesks, int drivesPerServer, DiskSize driveSize,
                         int maxLot, long feedstock, int mainframeGpus) {

        /** A busy late-game base: 24 cabinets, 192 servers, 4000 item types, two supercomputers, one machine desk. */
        public static final Params DEFAULT = new Params(Layout.WIDE, Chassis.SERVER, 4, 6, 8, 4000, 25, 2, 4, 4, 1,
                2, DiskSize.TB_1, 256, 100_000, 2);

        /**
         * A base the size of an expert pack's endgame: 180 cabinets of 2U storage servers on eight 8 TB
         * drives each (about 190 million items of room), 30 000 item types half of them component variants
         * in lots of up to 8000 — some 120 million items — four supercomputers, five desks and six
         * machine desks, two million logs and copper ingots to craft from, and a Mainframe with as many
         * graphics cards as its supply can feed (six queues).
         */
        public static final Params EXPERT_PACK = new Params(Layout.DENSE, Chassis.STORAGE, 6, 8, 4, 30_000, 50, 4, 4,
                5, 6, 8, DiskSize.TB_8, 8_000, 2_000_000, 5);

        public int racks() {
            return layout == Layout.WIDE ? routers * racksPerRouter
                    : routers * (3 * racksPerRouter + Math.min(racksPerRouter, DENSE_SHORT_ROW_RACKS));
        }

        public int servers() {
            return racks() * serversPerRack;
        }

        public int nodes() {
            return supercomputers * nodeRacksPerSupercomputer * NODES_PER_RACK;
        }

        public Params withTypes(final int newTypes) {
            return new Params(layout, chassis, routers, racksPerRouter, serversPerRack, newTypes, variantPercent,
                    supercomputers, nodeRacksPerSupercomputer, personalComputers, machineDesks, drivesPerServer,
                    driveSize, maxLot, feedstock, mainframeGpus);
        }

        /** Throws when the base would not fit the arena or a cabinet. */
        public void validate() {
            check(routers >= 1 && routers <= MAX_ROUTERS, "routers must be 1.." + MAX_ROUTERS);
            check(racksPerRouter >= 1 && racksPerRouter <= MAX_RACKS_PER_ROUTER,
                    "racksPerRouter must be 1.." + MAX_RACKS_PER_ROUTER);
            final int chassisPerRack = chassis == Chassis.STORAGE ? ServerRackBlockEntity.CAPACITY_U / 2
                    : ServerRackBlockEntity.CAPACITY_U;
            check(serversPerRack >= 1 && serversPerRack <= chassisPerRack, "serversPerRack must be 1.." + chassisPerRack);
            check(types >= 1, "types must be positive");
            check(variantPercent >= 0 && variantPercent <= 100, "variantPercent must be 0..100");
            final int maxSupercomputers = layout == Layout.WIDE ? MAX_SUPERCOMPUTERS : MAX_SUPERCOMPUTERS_DENSE;
            check(supercomputers >= 0 && supercomputers <= maxSupercomputers,
                    "supercomputers must be 0.." + maxSupercomputers);
            final int maxNodeRacks = layout == Layout.WIDE ? MAX_NODE_RACKS : MAX_NODE_RACKS_DENSE;
            check(nodeRacksPerSupercomputer >= 1 && nodeRacksPerSupercomputer <= maxNodeRacks,
                    "nodeRacksPerSupercomputer must be 1.." + maxNodeRacks);
            check(personalComputers >= 0 && personalComputers <= MAX_PCS, "personalComputers must be 0.." + MAX_PCS);
            final int maxDesks = layout == Layout.WIDE ? 1 : MAX_MACHINE_DESKS_DENSE;
            check(machineDesks >= 0 && machineDesks <= maxDesks, "machineDesks must be 0.." + maxDesks);
            final int maxDrives = chassis == Chassis.STORAGE ? STORAGE_DRIVE_BAYS : SERVER_DRIVE_BAYS;
            check(drivesPerServer >= 1 && drivesPerServer <= maxDrives, "drivesPerServer must be 1.." + maxDrives);
            check(driveSize != null, "driveSize must be set");
            check(maxLot >= 1, "maxLot must be positive");
            check(feedstock >= 0, "feedstock must not be negative");
            check(mainframeGpus >= 0 && mainframeGpus <= MainframeBlockEntity.GPU_SLOTS,
                    "mainframeGpus must be 0.." + MainframeBlockEntity.GPU_SLOTS);
        }

        private static void check(final boolean ok, final String message) {
            if (!ok) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    /** The built base, with handles on what the load generator and the assertions need. */
    public record Built(Params params, MainframeBlockEntity mainframe, List<ServerRackBlockEntity> serverRacks,
                        List<ServerRackBlockEntity> nodeRacks, List<HbwInterfaceBlockEntity> hubs,
                        List<PersonalComputerBlockEntity> personalComputers,
                        List<CraftingComputerBlockEntity> craftingComputers,
                        List<AbstractMachineBlockEntity> machines, @Nullable ProcessingPattern processing,
                        List<StorageKey> catalog) {

        /**
         * The crafting traffic this base can take: a bench recipe (planks), a chained one (sticks from
         * logs through planks) and, when a machine is standing, a processing one (copper plates), every
         * {@code everyTicks}; the machines are topped up with power every tick so the presses never starve.
         */
        public BenchmarkLoad.Workload workload(final int everyTicks, final long recipeQuantity,
                                               final long processingQuantity) {
            final List<BenchmarkLoad.CraftRequest> requests = new ArrayList<>();
            if (!craftingComputers.isEmpty()) {
                requests.add(BenchmarkLoad.CraftRequest.recipe(StorageKey.of(Items.OAK_PLANKS), recipeQuantity));
                requests.add(BenchmarkLoad.CraftRequest.recipe(StorageKey.of(Items.STICK), recipeQuantity));
            }
            if (processing != null) {
                requests.add(BenchmarkLoad.CraftRequest.processing(processing, processingQuantity));
            }
            final List<Runnable> hooks = new ArrayList<>();
            if (!machines.isEmpty()) {
                hooks.add(() -> {
                    for (final AbstractMachineBlockEntity machine : machines) {
                        machine.getEnergy().setEnergyStored(machine.getEnergy().getMaxEnergyStored());
                    }
                });
            }
            return new BenchmarkLoad.Workload(List.copyOf(requests), everyTicks, List.copyOf(hooks));
        }
    }

    public static final int NODES_PER_RACK = 2;
    public static final int MAX_ROUTERS = 6;
    public static final int MAX_RACKS_PER_ROUTER = 8;
    public static final int DENSE_SHORT_ROW_RACKS = 6;
    public static final int MAX_SUPERCOMPUTERS = 6;
    public static final int MAX_SUPERCOMPUTERS_DENSE = 4;
    public static final int MAX_NODE_RACKS = 10;
    public static final int MAX_NODE_RACKS_DENSE = 4;
    public static final int MAX_PCS = 5;
    public static final int MAX_MACHINE_DESKS_DENSE = 6;
    public static final int SERVER_DRIVE_BAYS = 3;
    public static final int STORAGE_DRIVE_BAYS = 8;

    // The floor row every test builds on.
    private static final int Y = 2;

    // Wide layout: one backbone across the middle; sections south of it, everything else north.
    private static final int W_BACKBONE_Z = 12;
    private static final int W_MAINFRAME_X = 2;
    private static final int W_BACKBONE_X0 = 3;
    private static final int W_BACKBONE_X1 = 45;
    private static final int PITCH = 7;
    private static final int W_ROUTER_X0 = 6;
    private static final int W_HUB_X0 = 9;
    private static final int W_PC_X0 = 12;
    private static final int W_CRAFTING_X = 4;
    private static final int W_NORTH_ROW_Z = W_BACKBONE_Z - 1;
    private static final int W_ROUTER_Z = W_BACKBONE_Z + 1;

    // Dense layout: two backbones joined by a west connector, a spine between them for the supercomputers,
    // desks along the connector (computers east of it, machine desks west of it).
    private static final int D_CONNECTOR_X = 3;
    private static final int D_BACKBONE_A_Z = 12;
    private static final int D_BACKBONE_B_Z = 37;
    private static final int D_BACKBONE_X1 = 46;
    private static final int D_SPINE_Z = 23;
    private static final int D_ROUTER_X0 = 9;
    private static final int D_HUB_X0 = 6;
    private static final int D_HUB_PITCH = 10;
    private static final int D_DESK_Z0 = 14;
    private static final int D_DESK_PITCH = 2;
    private static final int D_MACHINE_DESK_PITCH = 4;

    private BigBaseScenario() {
    }

    /** Builds the base and seeds its catalog and feedstock; positions are relative to the builder's origin. */
    public static Built build(final TestWorldBuilder world, final Params params) {
        params.validate();
        return params.layout() == Layout.WIDE ? buildWide(world, params) : buildDense(world, params);
    }

    /**
     * A Mainframe with the standard build plus the base's graphics cards, powered on. The cards are the
     * frugal Standard-era ones and the supply the largest there is, so a full set of slots still fits the
     * power budget: queues are what the benchmark wants from them, not throughput.
     */
    private static MainframeBlockEntity placeMainframe(final TestWorldBuilder world, final Params params, final BlockPos pos) {
        // The controller alone, deliberately: the layout packs the backbone right against it, and a raised
        // Mainframe's side parts would land on the first cable of that backbone and cut the network at its
        // source. The orchestrator works from its controller, which is what this base measures.
        world.setBlock(pos, ComputingModule.MAINFRAME.get());
        final MainframeBlockEntity mainframe = world.blockEntity(pos, MainframeBlockEntity.class);
        TestWorldBuilder.installMainframeBuild(mainframe);
        mainframe.getInventory().setStackInSlot(MainframeBlockEntity.PSU_SLOT, new ItemStack(HardwareItems.PSU_850G.get()));
        for (int gpu = 0; gpu < params.mainframeGpus(); gpu++) {
            mainframe.getInventory().setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START + gpu,
                    new ItemStack(HardwareItems.GPU_VERTEX_GTX_550_TI.get()));
        }
        mainframe.togglePower();
        return mainframe;
    }

    private static Built buildWide(final TestWorldBuilder world, final Params params) {
        final MainframeBlockEntity mainframe = placeMainframe(world, params, new BlockPos(W_MAINFRAME_X, Y, W_BACKBONE_Z));
        for (int x = W_BACKBONE_X0; x <= W_BACKBONE_X1; x++) {
            world.setBlock(new BlockPos(x, Y, W_BACKBONE_Z), ComputingModule.HBW_CABLE.get());
        }
        final List<ServerRackBlockEntity> serverRacks = new ArrayList<>();
        for (int r = 0; r < params.routers(); r++) {
            final int rx = W_ROUTER_X0 + r * PITCH;
            serverRacks.addAll(placeSection(world, params, rx, W_ROUTER_Z, Direction.SOUTH, params.racksPerRouter()));
        }
        final List<HbwInterfaceBlockEntity> hubs = new ArrayList<>();
        final List<ServerRackBlockEntity> nodeRacks = new ArrayList<>();
        for (int s = 0; s < params.supercomputers(); s++) {
            final int hx = W_HUB_X0 + s * PITCH;
            world.setBlock(new BlockPos(hx, Y, W_NORTH_ROW_Z), ComputingModule.HBW_INTERFACE.get());
            hubs.add(world.blockEntity(new BlockPos(hx, Y, W_NORTH_ROW_Z), HbwInterfaceBlockEntity.class));
            nodeRacks.addAll(placeCabinetRow(world, ComputingModule.HPC_CABLE.get(), ComputingModule.SUPERCOMPUTER_RACK.get(),
                    hx, W_NORTH_ROW_Z - 1, Direction.NORTH, params.nodeRacksPerSupercomputer(), BigBaseScenario::seatNodes));
        }
        final List<PersonalComputerBlockEntity> pcs = new ArrayList<>();
        for (int p = 0; p < params.personalComputers(); p++) {
            pcs.add(world.placeRunningPersonalComputer(placeDesk(world, W_PC_X0 + p * PITCH, W_NORTH_ROW_Z, Direction.NORTH)));
        }
        final List<CraftingComputerBlockEntity> crafting = new ArrayList<>();
        final List<AbstractMachineBlockEntity> machines = new ArrayList<>();
        if (params.machineDesks() > 0) {
            final BlockPos desk = placeDesk(world, W_CRAFTING_X, W_NORTH_ROW_Z, Direction.NORTH);
            crafting.add(placeCraftingComputer(world, desk));
            machines.add(placeMachineLine(world, desk, Direction.NORTH));
        }
        return finish(world, params, mainframe, serverRacks, nodeRacks, hubs, pcs, crafting, machines);
    }

    private static Built buildDense(final TestWorldBuilder world, final Params params) {
        final MainframeBlockEntity mainframe =
                placeMainframe(world, params, new BlockPos(D_CONNECTOR_X - 1, Y, D_BACKBONE_A_Z));
        for (int x = D_CONNECTOR_X; x <= D_BACKBONE_X1; x++) {
            world.setBlock(new BlockPos(x, Y, D_BACKBONE_A_Z), ComputingModule.HBW_CABLE.get());
            world.setBlock(new BlockPos(x, Y, D_BACKBONE_B_Z), ComputingModule.HBW_CABLE.get());
        }
        for (int z = D_BACKBONE_A_Z + 1; z < D_BACKBONE_B_Z; z++) {
            world.setBlock(new BlockPos(D_CONNECTOR_X, Y, z), ComputingModule.HBW_CABLE.get());
        }
        for (int x = D_CONNECTOR_X + 1; x <= D_BACKBONE_X1; x++) {
            world.setBlock(new BlockPos(x, Y, D_SPINE_Z), ComputingModule.HBW_CABLE.get());
        }
        // Four rows of sections: both sides of each backbone. The row facing the spine is kept short so
        // its cables never touch the spine and turn a section into the whole network.
        final List<ServerRackBlockEntity> serverRacks = new ArrayList<>();
        for (int r = 0; r < params.routers(); r++) {
            final int rx = D_ROUTER_X0 + r * PITCH;
            serverRacks.addAll(placeSection(world, params, rx, D_BACKBONE_A_Z - 1, Direction.NORTH, params.racksPerRouter()));
            serverRacks.addAll(placeSection(world, params, rx, D_BACKBONE_A_Z + 1, Direction.SOUTH,
                    Math.min(params.racksPerRouter(), DENSE_SHORT_ROW_RACKS)));
            serverRacks.addAll(placeSection(world, params, rx, D_BACKBONE_B_Z - 1, Direction.NORTH, params.racksPerRouter()));
            serverRacks.addAll(placeSection(world, params, rx, D_BACKBONE_B_Z + 1, Direction.SOUTH, params.racksPerRouter()));
        }
        // Supercomputers hang south of the spine: the interface on it, the fabric running east, the node
        // cabinets rear to the fabric in the two free rows before the next section band.
        final List<HbwInterfaceBlockEntity> hubs = new ArrayList<>();
        final List<ServerRackBlockEntity> nodeRacks = new ArrayList<>();
        for (int s = 0; s < params.supercomputers(); s++) {
            final int hx = D_HUB_X0 + s * D_HUB_PITCH;
            world.setBlock(new BlockPos(hx, Y, D_SPINE_Z + 1), ComputingModule.HBW_INTERFACE.get());
            hubs.add(world.blockEntity(new BlockPos(hx, Y, D_SPINE_Z + 1), HbwInterfaceBlockEntity.class));
            for (int j = 1; j <= params.nodeRacksPerSupercomputer(); j++) {
                world.setBlock(new BlockPos(hx + 2 * j - 1, Y, D_SPINE_Z + 1), ComputingModule.HPC_CABLE.get());
                world.setBlock(new BlockPos(hx + 2 * j, Y, D_SPINE_Z + 1), ComputingModule.HPC_CABLE.get());
                final ServerRackBlockEntity rack = placeCabinet(world, ComputingModule.SUPERCOMPUTER_RACK.get(),
                        new BlockPos(hx + 2 * j, Y, D_SPINE_Z + 3), Direction.SOUTH);
                seatNodes(rack);
                nodeRacks.add(rack);
            }
        }
        final List<PersonalComputerBlockEntity> pcs = new ArrayList<>();
        for (int p = 0; p < params.personalComputers(); p++) {
            pcs.add(world.placeRunningPersonalComputer(
                    placeDesk(world, D_CONNECTOR_X + 1, D_DESK_Z0 + p * D_DESK_PITCH, Direction.EAST)));
        }
        final List<CraftingComputerBlockEntity> crafting = new ArrayList<>();
        final List<AbstractMachineBlockEntity> machines = new ArrayList<>();
        for (int m = 0; m < params.machineDesks(); m++) {
            final BlockPos desk = placeDesk(world, D_CONNECTOR_X - 1, D_DESK_Z0 + m * D_MACHINE_DESK_PITCH, Direction.WEST);
            crafting.add(placeCraftingComputer(world, desk));
            machines.add(placeMachineLine(world, desk, Direction.SOUTH));
        }
        return finish(world, params, mainframe, serverRacks, nodeRacks, hubs, pcs, crafting, machines);
    }

    private static Built finish(final TestWorldBuilder world, final Params params, final MainframeBlockEntity mainframe,
                                final List<ServerRackBlockEntity> serverRacks, final List<ServerRackBlockEntity> nodeRacks,
                                final List<HbwInterfaceBlockEntity> hubs, final List<PersonalComputerBlockEntity> pcs,
                                final List<CraftingComputerBlockEntity> crafting,
                                final List<AbstractMachineBlockEntity> machines) {
        final ProcessingPattern processing = machines.isEmpty() ? null : copperPlatePattern();
        final List<StorageKey> catalog = catalog(params.types(), params.variantPercent());
        final Built built = new Built(params, mainframe, List.copyOf(serverRacks), List.copyOf(nodeRacks), List.copyOf(hubs),
                List.copyOf(pcs), List.copyOf(crafting), List.copyOf(machines), processing, catalog);
        seed(built, catalog, 1L);
        seedFeedstock(built);
        return built;
    }

    /** A Server Router on the backbone with one section of cabinets running away from it. */
    private static List<ServerRackBlockEntity> placeSection(final TestWorldBuilder world, final Params params, final int rx,
                                                            final int routerZ, final Direction away, final int racks) {
        // The router's back is its uplink: it faces away from the backbone, so its back meets the cable.
        world.setBlock(new BlockPos(rx, Y, routerZ), ComputingModule.SERVER_ROUTER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, away));
        return placeCabinetRow(world, ComputingModule.HBW_CABLE.get(), ComputingModule.SERVER_RACK.get(), rx,
                routerZ + away.getStepZ(), away, racks, rack -> {
                    for (int slot = 0; slot < params.serversPerRack(); slot++) {
                        final int row = params.chassis() == Chassis.STORAGE ? slot * 2 : slot;
                        rack.getServers().setStackInSlot(row, params.chassis() == Chassis.STORAGE
                                ? ComputingModule.defaultStorageServer() : ComputingModule.defaultServer());
                        for (int drive = 0; drive < params.drivesPerServer(); drive++) {
                            rack.insertDrive(row, new ItemStack(ComputingModule.disk(StorageTier.NVME, params.driveSize())));
                        }
                    }
                });
    }

    /** Something to do to every cabinet just placed: mount its units. */
    private interface RackFiller {
        void fill(ServerRackBlockEntity rack);
    }

    /**
     * A cable running from {@code (x, start)} away from the backbone, with cabinets on both sides, rear to
     * the cable. Cabinets are two blocks wide and deep with the controller at the front-right, so a cabinet
     * whose rear meets a cable at {@code x} has its controller two blocks off the cable, and the two sides
     * are staggered by one so their footprints tile the cable without touching.
     */
    private static List<ServerRackBlockEntity> placeCabinetRow(final TestWorldBuilder world, final Block cable,
                                                               final Block rackBlock, final int x, final int start,
                                                               final Direction away, final int count,
                                                               final RackFiller filler) {
        final int step = away.getStepZ();
        final int perSide = (count + 1) / 2;
        for (int i = 0; i <= 2 * perSide; i++) {
            world.setBlock(new BlockPos(x, Y, start + i * step), cable);
        }
        final List<ServerRackBlockEntity> racks = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            final boolean east = k % 2 == 0;
            final int slot = k / 2;
            // Going south: an east cabinet (facing east) spans z..z+1, a west one (facing west) spans z-1..z.
            // Going north the same shapes are placed mirrored, so the stagger keeps the footprints apart.
            final int z = start + step * (1 + 2 * slot) + (step > 0 ? (east ? 0 : 1) : (east ? -1 : 0));
            final Direction facing = east ? Direction.EAST : Direction.WEST;
            final int cx = east ? x + 2 : x - 2;
            final ServerRackBlockEntity rack = placeCabinet(world, rackBlock, new BlockPos(cx, Y, z), facing);
            filler.fill(rack);
            racks.add(rack);
        }
        return racks;
    }

    /** Places a cabinet the way a player does: the controller plus its eleven part blocks. */
    private static ServerRackBlockEntity placeCabinet(final TestWorldBuilder world, final Block rackBlock,
                                                      final BlockPos pos, final Direction facing) {
        final BlockState state = rackBlock.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
        // A cabinet refuses to raise its eleven parts into occupied space, so in a real world (as opposed to
        // an empty test arena) the ground has to come out first — otherwise every cabinet stays a lone
        // controller block, no rack forms, and the base has nothing on its network at all.
        world.clearFor(dev.jsc.jscomputronics.module.computing.block.ServerRackStructure.allPositions(pos, facing));
        world.setBlock(pos, state);
        ((AbstractMultiblockControllerBlock) rackBlock).setPlacedBy(world.level(), world.absolute(pos), state,
                null, ItemStack.EMPTY);
        return world.blockEntity(pos, ServerRackBlockEntity.class);
    }

    private static void seatNodes(final ServerRackBlockEntity rack) {
        for (int node = 0; node < NODES_PER_RACK; node++) {
            final int row = node * 2;
            rack.getServers().setStackInSlot(row, ComputingModule.defaultSupercomputerNode());
            rack.insertDrive(row, new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)));
        }
    }

    /**
     * A Personal Router at {@code (x, z)} on a backbone cable and an Ethernet drop one block {@code toward};
     * returns where the computer goes, two blocks along, rear to the drop.
     */
    private static BlockPos placeDesk(final TestWorldBuilder world, final int x, final int z, final Direction toward) {
        world.setBlock(new BlockPos(x, Y, z), ComputingModule.PERSONAL_ROUTER.get());
        world.setBlock(new BlockPos(x, Y, z).relative(toward), ComputingModule.ETHERNET_CABLE.get());
        return new BlockPos(x, Y, z).relative(toward, 2);
    }

    private static CraftingComputerBlockEntity placeCraftingComputer(final TestWorldBuilder world, final BlockPos desk) {
        final CraftingComputerBlockEntity cc = world.placeRunningCraftingComputer(desk);
        cc.loadPattern(planksPattern());
        cc.loadPattern(sticksPattern());
        return cc;
    }

    /** A crafting cable, a Crafting Switch and a Compressor in a line from the computer, in the given direction. */
    private static AbstractMachineBlockEntity placeMachineLine(final TestWorldBuilder world, final BlockPos computer,
                                                               final Direction away) {
        world.setBlock(computer.relative(away, 1), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(computer.relative(away, 2), ComputingModule.CRAFTING_SWITCH.get());
        world.setBlock(computer.relative(away, 3), IndustrialModule.COMPRESSOR.get());
        return world.blockEntity(computer.relative(away, 3), AbstractMachineBlockEntity.class);
    }

    private static CraftingPattern planksPattern() {
        final List<ItemStack> grid = emptyGrid();
        grid.set(0, new ItemStack(Items.OAK_LOG));
        return new CraftingPattern(grid, new ItemStack(Items.OAK_PLANKS, 4));
    }

    private static CraftingPattern sticksPattern() {
        final List<ItemStack> grid = emptyGrid();
        grid.set(0, new ItemStack(Items.OAK_PLANKS));
        grid.set(3, new ItemStack(Items.OAK_PLANKS));
        return new CraftingPattern(grid, new ItemStack(Items.STICK, 4));
    }

    private static List<ItemStack> emptyGrid() {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return grid;
    }

    /** The Compressor's ingot-to-plate recipe as a processing pattern, or null when the plate item is not registered. */
    @Nullable
    private static ProcessingPattern copperPlatePattern() {
        final Item plate = BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath("jsc", "copper_plate")).orElse(null);
        if (plate == null) {
            return null;
        }
        final String machineType = BuiltInRegistries.BLOCK.getKey(IndustrialModule.COMPRESSOR.get()).toString();
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.COPPER_INGOT), 1L)),
                List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(plate), 1L, 100)),
                machineType, 120);
    }

    /**
     * {@code types} distinct storage keys: every registered item once, then component variants of them
     * (a custom name, the way a base's renamed tools and labelled shulkers are distinct entries). A share
     * of the first lap is a variant too, so a small catalog still exercises component-bearing keys.
     * Deterministic, so two runs see the same catalog.
     */
    public static List<StorageKey> catalog(final int types, final int variantPercent) {
        final List<Item> items = new ArrayList<>();
        for (final Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                items.add(item);
            }
        }
        final Random random = new Random(0x5CA1EL);
        final List<StorageKey> keys = new ArrayList<>(types);
        for (int i = 0; i < types; i++) {
            final ItemStack stack = new ItemStack(items.get(i % items.size()));
            final int lap = i / items.size();
            if (lap > 0 || random.nextInt(100) < variantPercent) {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("lot " + (lap + 1) + "-" + (i % 97)));
            }
            keys.add(StorageKey.of(stack));
        }
        return keys;
    }

    /**
     * Spreads {@code keys} over every server, a random lot of each, spilling a lot a full server refuses onto
     * the servers after it; returns how many items went in.
     */
    public static long seed(final Built base, final List<StorageKey> keys, final long randomSeed) {
        final Random random = new Random(randomSeed);
        final int servers = base.params().servers();
        final int maxLot = base.params().maxLot();
        long items = 0;
        int cursor = 0;
        for (final StorageKey key : keys) {
            long lot = 1 + random.nextInt(maxLot);
            for (int attempt = 0; attempt < servers && lot > 0L; attempt++) {
                final long stored = serverStore(base, (cursor + attempt) % servers).insert(key, lot);
                items += stored;
                lot -= stored;
            }
            cursor++;
        }
        return items;
    }

    /** Logs and copper ingots for the crafting traffic, spread thinly over every server so nothing fills up. */
    private static void seedFeedstock(final Built base) {
        final int servers = base.params().servers();
        for (final Item item : List.of(Items.OAK_LOG, Items.COPPER_INGOT)) {
            long remaining = base.params().feedstock();
            final long share = Math.max(1L, remaining / servers);
            for (int pass = 0; pass < 2 && remaining > 0L; pass++) {
                for (int server = 0; server < servers && remaining > 0L; server++) {
                    final ServerStore store = serverStore(base, server);
                    final long room = store.free() / 4;
                    final long put = Math.min(Math.min(share, room), remaining);
                    if (put > 0L) {
                        remaining -= store.insert(item, put);
                    }
                }
            }
        }
    }

    /** The store of the {@code index}-th server of the base, cabinet by cabinet then chassis by chassis. */
    private static ServerStore serverStore(final Built base, final int index) {
        final List<ServerRackBlockEntity> racks = base.serverRacks();
        final ServerRackBlockEntity rack = racks.get(index % racks.size());
        return rack.getServerStorage(serverRow(base.params(), (index / racks.size()) % base.params().serversPerRack()));
    }

    /** The rack row a server index sits in: 2U storage chassis take every other row. */
    public static int serverRow(final Params params, final int server) {
        return params.chassis() == Chassis.STORAGE ? server * 2 : server;
    }

    /** How many items the base's servers can hold in all, and how many they hold now. */
    public static long[] storageItems(final Built base) {
        long capacity = 0;
        long used = 0;
        for (final ServerRackBlockEntity rack : base.serverRacks()) {
            for (int slot = 0; slot < base.params().serversPerRack(); slot++) {
                capacity += rack.getServerStorage(serverRow(base.params(), slot)).capacity();
                used += rack.getServerStorage(serverRow(base.params(), slot)).used();
            }
        }
        return new long[] {capacity, used};
    }
}
