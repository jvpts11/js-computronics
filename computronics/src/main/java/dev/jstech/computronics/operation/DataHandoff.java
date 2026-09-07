/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.operation.index.ItemLocation;
import dev.jstech.computronics.storage.DataContainers;
import dev.jstech.computronics.storage.IDataSink;
import dev.jstech.computronics.storage.LocalStore;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.uuid.NetworkUuid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * What passes between a player's hands and a computer, by every route the GUIs offer (the cursor, a menu
 * slot or an inventory slot; the terminal or the desktop). Handing a stack over stores it as its item, the way
 * a chest takes it; handing over what a held container HOLDS stores the fluid or chemical and returns the
 * container emptied; and a held empty container over a fluid or chemical entry fills from it. The two sides (
 * the network's servers, written and read over ticks by an INSERT or a SELECT, and a computer's own disks,
 * written and read at once) go through this one path, so no route can treat a bucket differently.
 */
public final class DataHandoff {

    /** Where a handoff draws its stack from, and how it writes back what is left of it. */
    public interface ISource {
        ItemStack get();

        void set(ItemStack remaining);

        /** Whether a container that left this source could go back into it right now (the place is free). */
        boolean canTakeBack();
    }

    /** What a handoff did. */
    public enum Outcome {
        /** Data left the source: an INSERT is running, or the local store has taken it. */
        DEPOSITED,
        /** A container is filling: a SELECT is running, or the local store has filled it already. */
        FILLED,
        /** Nothing to hand over: an empty source, an amount of zero, or an entry that is not fluid or chemical. */
        NOTHING,
        /** No room, or nothing to take; the source is untouched (a bucket needs the whole 1 000 mB either way). */
        NO_ROOM,
        /** The network has no live dispatcher (Mainframe off, or without an OS); the source is untouched. */
        NO_DISPATCHER
    }

    private DataHandoff() {
    }

    /** The stack on the player's cursor, in whichever menu is open now. */
    public static ISource cursor(final Player player) {
        final AbstractContainerMenu menu = player.containerMenu;
        return new ISource() {
            @Override
            public ItemStack get() {
                return menu.getCarried();
            }

            @Override
            public void set(final ItemStack remaining) {
                menu.setCarried(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
                menu.broadcastChanges();
            }

            @Override
            public boolean canTakeBack() {
                return player.containerMenu == menu && menu.getCarried().isEmpty();
            }
        };
    }

    /** A slot of the menu the player has open now. */
    public static ISource slot(final Slot slot, final Player player) {
        final AbstractContainerMenu menu = player.containerMenu;
        return new ISource() {
            @Override
            public ItemStack get() {
                return slot.getItem();
            }

            @Override
            public void set(final ItemStack remaining) {
                slot.set(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
                menu.broadcastChanges();
            }

            @Override
            public boolean canTakeBack() {
                return player.containerMenu == menu && slot.getItem().isEmpty();
            }
        };
    }

    /** A slot of the player's own inventory, by index. */
    public static ISource inventory(final Player player, final int index) {
        return new ISource() {
            @Override
            public ItemStack get() {
                return player.getInventory().getItem(index);
            }

            @Override
            public void set(final ItemStack remaining) {
                player.getInventory().setItem(index, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
                player.containerMenu.broadcastChanges();
            }

            @Override
            public boolean canTakeBack() {
                return player.getInventory().getItem(index).isEmpty();
            }
        };
    }

    /**
     * Hands a stack to the network. {@code amount} is how many items to send (clamped to the stack). With
     * {@code contents} set, a held container hands over what it holds instead (one container's worth) and
     * comes back emptied; without it a bucket is stored as the item it is. {@code afterSettle} runs when the
     * INSERT has settled and any leftover is back with the player, so a caller can refresh the player's view.
     */
    public static Outcome intoNetwork(final MainframeBlockEntity mainframe, final ServerLevel level,
                                      final NetworkUuid network, final Player player, final ISource source,
                                      final int amount, final boolean contents, final String label,
                                      final Runnable afterSettle) {
        final ItemStack stack = source.get();
        if (stack.isEmpty() || amount <= 0) {
            return Outcome.NOTHING;
        }
        if (contents && DataContainers.holdsData(stack)) {
            long room = 0L;
            for (final ItemLocation free : mainframe.networkIndex().freeSpace(level, network)) {
                room += free.quantity();
            }
            final Optional<DataContainers.Drained> drained = DataContainers.drain(stack, room);
            if (drained.isEmpty()) {
                return Outcome.NO_ROOM;
            }
            final DataContainers.Drained data = drained.get();
            final NetworkInsertOperation op = mainframe.submitNetworkInsert(data.key(), data.amount(), label);
            if (op == null) {
                return Outcome.NO_DISPATCHER;
            }
            stack.shrink(1);
            source.set(stack);
            /*
             * The emptied container comes back once the write has settled, refilled with whatever the
             * network could not take after all, so nothing is lost on the way.
             */
            op.onSettle(() -> {
                handBack(player, source, DataContainers.fill(data.container(), data.key(), op.leftover()).container());
                afterSettle.run();
            });
            return Outcome.DEPOSITED;
        }
        final int count = Math.min(amount, stack.getCount());
        final ItemStack inFlight = stack.copyWithCount(count);
        final NetworkInsertOperation op = mainframe.submitNetworkInsert(StorageKey.of(inFlight), count, label);
        if (op == null) {
            return Outcome.NO_DISPATCHER;
        }
        stack.shrink(count);
        source.set(stack);
        // Whatever does not fit comes back with its original components when the Operation settles.
        op.onSettle(() -> {
            final long leftover = op.leftover();
            if (leftover > 0L) {
                returnToPlayer(player, inFlight.copyWithCount((int) Math.min(leftover, count)));
            }
            afterSettle.run();
        });
        return Outcome.DEPOSITED;
    }

    /**
     * Hands a stack to a computer's own disks, at once: the store takes what fits and the rest stays in the
     * source. With {@code contents} set, a held container hands over what the store has room for and comes
     * back straight away.
     */
    public static Outcome intoLocalStore(final LocalStore store, final Player player, final ISource source,
                                         final int amount, final boolean contents) {
        final ItemStack stack = source.get();
        if (stack.isEmpty() || amount <= 0) {
            return Outcome.NOTHING;
        }
        if (contents && DataContainers.holdsData(stack)) {
            final Optional<DataContainers.Drained> drained = DataContainers.drain(stack, store.freeWeight());
            if (drained.isEmpty()) {
                return Outcome.NO_ROOM;
            }
            final DataContainers.Drained data = drained.get();
            final long stored = store.insert(data.key(), data.amount());
            if (stored <= 0L) {
                return Outcome.NO_ROOM;
            }
            stack.shrink(1);
            source.set(stack);
            handBack(player, source, DataContainers.fill(data.container(), data.key(), data.amount() - stored).container());
            return Outcome.DEPOSITED;
        }
        final long stored = store.insert(StorageKey.of(stack), Math.min(amount, stack.getCount()));
        if (stored <= 0L) {
            return Outcome.NO_ROOM;
        }
        stack.shrink((int) stored);
        source.set(stack);
        return Outcome.DEPOSITED;
    }

    /**
     * Fills ONE held container with {@code key} from the network: a bucket takes a full bucket or nothing, a
     * tank item takes what it has room for. The container is in flight while the SELECT runs and comes back
     * filled, onto the cursor it left if that is still free, with anything it could not take after all
     * put back into the network.
     */
    public static Outcome fillFromNetwork(final MainframeBlockEntity mainframe, final ServerLevel level,
                                          final NetworkUuid network, final Player player, final ISource source,
                                          final StorageKey key, final String label, final Runnable afterSettle) {
        final ItemStack stack = source.get();
        if (stack.isEmpty() || !(key.isFluid() || key.isChemical())) {
            return Outcome.NOTHING;
        }
        final long want = DataContainers.roomFor(stack, key, mainframe.networkIndex().available(key));
        if (want <= 0L) {
            return Outcome.NO_ROOM;
        }
        final CollectingSink sink = new CollectingSink();
        final NetworkSelectOperation op = mainframe.submitNetworkSelect(key, want, sink, label);
        if (op == null) {
            return Outcome.NO_DISPATCHER;
        }
        final ItemStack container = stack.copyWithCount(1);
        stack.shrink(1);
        source.set(stack);
        op.onSettle(() -> {
            final DataContainers.Filled filled = DataContainers.fill(container, key, sink.total());
            final long leftover = sink.total() - filled.taken();
            if (leftover > 0L) {
                NetworkStorage.of(level, network).insert(key, leftover);
            }
            handBack(player, source, filled.container());
            afterSettle.run();
        });
        return Outcome.FILLED;
    }

    /** Fills ONE held container with {@code key} from a computer's own disks, at once. */
    public static Outcome fillFromLocalStore(final LocalStore store, final Player player, final ISource source,
                                             final StorageKey key) {
        final ItemStack stack = source.get();
        if (stack.isEmpty() || !(key.isFluid() || key.isChemical())) {
            return Outcome.NOTHING;
        }
        final long want = DataContainers.roomFor(stack, key, store.count(key));
        if (want <= 0L) {
            return Outcome.NO_ROOM;
        }
        final long got = store.extract(key, want);
        final DataContainers.Filled filled = DataContainers.fill(stack.copyWithCount(1), key, got);
        final long leftover = got - filled.taken();
        if (leftover > 0L) {
            store.insert(key, leftover);
        }
        if (filled.taken() <= 0L) {
            return Outcome.NO_ROOM;
        }
        stack.shrink(1);
        source.set(stack);
        handBack(player, source, filled.container());
        return Outcome.FILLED;
    }

    /** Hands a stack back to the player: into the inventory, or dropped where they stood if they have left. */
    public static void returnToPlayer(final Player player, final ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (player.isRemoved()) {
            Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
        } else {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    /** A container going back where it came from when that place is still free, else to the inventory. */
    private static void handBack(final Player player, final ISource source, final ItemStack container) {
        if (container.isEmpty()) {
            return;
        }
        if (source.canTakeBack()) {
            source.set(container);
        } else {
            returnToPlayer(player, container);
        }
    }

    /** Receives what a SELECT pulls out of the servers and only counts it; the container is filled at the end. */
    private static final class CollectingSink implements IDataSink {

        private long total;

        @Override
        public long insert(final StorageKey key, final long amount, final boolean simulate) {
            if (!simulate) {
                total += amount;
            }
            return amount;
        }

        long total() {
            return total;
        }
    }
}
