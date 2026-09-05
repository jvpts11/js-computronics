/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.fs;

import com.mojang.serialization.Codec;
import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;

import java.util.Optional;

/**
 * Serializes and parses a {@link CraftingPattern} to and from the UTF-8 string content stored in a
 * {@link FileType#CRAFT} file on a computer's system disk.
 *
 * <p>The wire format is SNBT (Stringified NBT), produced by encoding the pattern with
 * {@link CraftingPattern#CODEC} over {@link NbtOps} backed by the server's
 * {@link HolderLookup.Provider}. SNBT is self-contained ASCII text, survives round-trips through
 * the filesystem's UTF-8 string storage, and matches the same encoding path already used in
 * the Crafting Computer's {@code saveExtra}/{@code loadExtra} methods.
 */
public final class CraftFile {

    private CraftFile() {
    }

    /**
     * Serializes {@code pattern} to an SNBT string suitable for storage in a {@code .craft} file.
     *
     * <p>Returns an empty {@link Optional} when encoding fails.
     *
     * @param pattern    the pattern to serialize
     * @param registries the server's holder-lookup provider (needed for item-component codecs)
     * @return the SNBT string, or empty on failure
     */
    public static Optional<String> serialize(final CraftingPattern pattern,
                                             final HolderLookup.Provider registries) {
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final var result = CraftingPattern.CODEC.encodeStart(ops, pattern);
        if (result.isError()) {
            JsComputronics.LOGGER.warn("Failed to serialize CraftingPattern to .craft: {}",
                    result.error().map(e -> e.message()).orElse("unknown"));
            return Optional.empty();
        }
        final Tag tag = result.getOrThrow();
        return Optional.of(tag.toString());
    }

    /**
     * Parses a {@link CraftingPattern} from the content string of a {@code .craft} file.
     *
     * <p>Returns an empty {@link Optional} when the content is not valid SNBT or does not decode
     * to a {@link CraftingPattern}.
     *
     * @param content    the UTF-8 string content of the file (SNBT)
     * @param registries the server's holder-lookup provider (needed for item-component codecs)
     * @return the parsed pattern, or empty on failure
     */
    public static Optional<CraftingPattern> parse(final String content,
                                                   final HolderLookup.Provider registries) {
        final Tag tag;
        try {
            tag = TagParser.parseTag(content);
        } catch (final Exception e) {
            JsComputronics.LOGGER.warn("Failed to parse .craft file content as SNBT: {}",
                    e.getMessage());
            return Optional.empty();
        }
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final var result = CraftingPattern.CODEC.parse(ops, tag);
        if (result.isError()) {
            JsComputronics.LOGGER.warn("Failed to decode CraftingPattern from .craft: {}",
                    result.error().map(e -> e.message()).orElse("unknown"));
            return Optional.empty();
        }
        return Optional.of(result.getOrThrow());
    }

    // --- Typed .craft for machine recipes. A wrapper tags the kind so parse can route; the legacy bench
    // .craft above stays untagged for back-compat, and typeOf() reports "craft" for it.

    public static Optional<String> serializeProcessing(final ProcessingPattern pattern,
                                                       final HolderLookup.Provider registries) {
        return wrap("proc", ProcessingPattern.CODEC, pattern, registries);
    }

    public static Optional<String> serializeMultiStage(final MultiStagePattern pattern,
                                                       final HolderLookup.Provider registries) {
        return wrap("multi", MultiStagePattern.CODEC, pattern, registries);
    }

    public static Optional<ProcessingPattern> parseProcessing(final String content,
                                                              final HolderLookup.Provider registries) {
        return unwrap(content, "proc", ProcessingPattern.CODEC, registries);
    }

    public static Optional<MultiStagePattern> parseMultiStage(final String content,
                                                              final HolderLookup.Provider registries) {
        return unwrap(content, "multi", MultiStagePattern.CODEC, registries);
    }

    /** The recipe kind a {@code .craft} holds: "proc", "multi", or "craft" for the legacy untagged bench pattern. */
    public static String typeOf(final String content) {
        try {
            if (TagParser.parseTag(content) instanceof CompoundTag c && c.contains("type")) {
                return c.getString("type");
            }
        } catch (final Exception ignored) {
            // not parseable as a tagged compound — treat as legacy bench
        }
        return "craft";
    }

    private static <T> Optional<String> wrap(final String type, final Codec<T> codec, final T value,
                                             final HolderLookup.Provider registries) {
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final var result = codec.encodeStart(ops, value);
        if (result.isError()) {
            JsComputronics.LOGGER.warn("Failed to serialize {} pattern to .craft: {}", type,
                    result.error().map(e -> e.message()).orElse("unknown"));
            return Optional.empty();
        }
        final CompoundTag wrapped = new CompoundTag();
        wrapped.putString("type", type);
        wrapped.put("data", result.getOrThrow());
        return Optional.of(wrapped.toString());
    }

    private static <T> Optional<T> unwrap(final String content, final String type, final Codec<T> codec,
                                          final HolderLookup.Provider registries) {
        final Tag tag;
        try {
            tag = TagParser.parseTag(content);
        } catch (final Exception e) {
            return Optional.empty();
        }
        if (!(tag instanceof CompoundTag c) || !type.equals(c.getString("type"))) {
            return Optional.empty();
        }
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        return codec.parse(ops, c.get("data")).result();
    }
}
