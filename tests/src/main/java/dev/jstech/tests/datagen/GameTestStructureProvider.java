/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.datagen;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import dev.jstech.tests.JsTests;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Generates the empty structure templates that the GameTests run inside, under the test mod's namespace.
 */
public final class GameTestStructureProvider implements DataProvider {

    private final PackOutput output;

    public GameTestStructureProvider(final PackOutput output) {
        this.output = output;
    }

    @Override
    public String getName() {
        return "J's Tech Series GameTest Structures";
    }

    @Override
    public CompletableFuture<?> run(final CachedOutput cache) {
        // "empty" is the 9x6x9 box the correctness tests run in; "bench" is a large arena for the
        // performance benchmarks that build a physically huge network (hundreds of ticking cables).
        return CompletableFuture.allOf(
                write(cache, "empty", emptyArena(9, 6, 9)),
                write(cache, "bench", emptyArena(48, 8, 48)));
    }

    // SHA-1 is the digest the vanilla datagen cache keys files by; it is a cache
    // key, not a security primitive, so Guava's deprecation does not apply here.
    @SuppressWarnings("deprecation")
    private CompletableFuture<?> write(final CachedOutput cache, final String name, final CompoundTag tag) {
        final Path path = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(JsTests.MODID).resolve("structure").resolve(name + ".nbt");
        return CompletableFuture.runAsync(() -> {
            try {
                final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                NbtIo.writeCompressed(tag, bytes);
                final byte[] data = bytes.toByteArray();
                final HashCode hash = Hashing.sha1().hashBytes(data);
                cache.writeIfNeeded(path, data, hash);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Util.backgroundExecutor());
    }

    private static CompoundTag emptyArena(final int sx, final int sy, final int sz) {
        final CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.put("size", intList(sx, sy, sz));
        root.put("entities", new ListTag());

        // Palette: index 0 = barrier floor.
        final ListTag palette = new ListTag();
        palette.add(blockState("minecraft:barrier"));
        root.put("palette", palette);

        // Only the floor is written; unwritten cells default to structure void
        // (left as the surrounding air the GameTest framework provides).
        final ListTag blocks = new ListTag();
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                blocks.add(blockEntry(0, x, 0, z));
            }
        }
        root.put("blocks", blocks);
        return root;
    }

    private static CompoundTag blockState(final String name) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    private static CompoundTag blockEntry(final int state, final int x, final int y, final int z) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("state", state);
        tag.put("pos", intList(x, y, z));
        return tag;
    }

    private static ListTag intList(final int... values) {
        final ListTag list = new ListTag();
        for (final int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }
}
