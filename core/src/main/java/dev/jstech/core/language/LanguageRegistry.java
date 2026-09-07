/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.language;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Every language the computers of this world know.
 *
 * <p>An addon may add one, and may take one away, including the one this series ships with. That is
 * deliberate: a pack that wants its computers programmed in something else entirely should not have to
 * live beside a language nobody in it uses.
 *
 * <p>Everything that deals in programs resolves through here by extension rather than by naming a
 * language, so the prompt, the editor and the file explorer work with whatever happens to be registered.
 */
public final class LanguageRegistry {

    private final Map<ResourceLocation, IProgrammingLanguage> byId = new LinkedHashMap<>();

    /**
     * Adds one, replacing any already registered under that id.
     *
     * <p>Replacing rather than refusing lets an addon improve a language in place, which is the same
     * gesture as adding one and needs no separate ceremony.
     */
    public void register(final IProgrammingLanguage language) {
        if (language != null && language.id() != null) {
            byId.put(language.id(), language);
        }
    }

    /** Takes one away; false when it was not there. */
    public boolean unregister(final ResourceLocation id) {
        return byId.remove(id) != null;
    }

    /** The one with that id, or null. */
    @Nullable
    public IProgrammingLanguage get(final ResourceLocation id) {
        return byId.get(id);
    }

    /**
     * The language whose files end in that extension, or null.
     *
     * <p>Both what a person writes and what a compiler produces count, so this answers for a source file
     * and for the thing built from it alike.
     */
    @Nullable
    public IProgrammingLanguage byExtension(final String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        final String wanted = extension.toLowerCase(Locale.ROOT);
        for (final IProgrammingLanguage language : byId.values()) {
            if (language.sourceExtensions().contains(wanted)
                    || language.binaryExtensions().contains(wanted)) {
                return language;
            }
        }
        return null;
    }

    /** The language that RUNS files ending in that extension, or null. */
    @Nullable
    public IProgrammingLanguage runnerOf(final String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        final String wanted = extension.toLowerCase(Locale.ROOT);
        for (final IProgrammingLanguage language : byId.values()) {
            if (language.binaryExtensions().contains(wanted)) {
                return language;
            }
        }
        return null;
    }

    /** Every language there is, in the order they were registered. */
    public List<IProgrammingLanguage> all() {
        return List.copyOf(byId.values());
    }
}
