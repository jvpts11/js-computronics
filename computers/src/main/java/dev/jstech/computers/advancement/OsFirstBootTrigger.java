/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jstech.computers.ComputingModule;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Fires when a player boots a computer into an installed operating system (opening its desktop, terminal
 * or network GUI on a monitor). An instance may name the OS it wants, so an advancement can wait for one
 * distribution in particular (the challenge advancements for the hand-installed Arch and the
 * compiled-from-source Gentoo) or accept any OS.
 */
public class OsFirstBootTrigger extends SimpleCriterionTrigger<OsFirstBootTrigger.Instance> {

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** Reports that {@code player} booted a computer into {@code osId}. */
    public void trigger(final ServerPlayer player, final ResourceLocation osId) {
        this.trigger(player, instance -> instance.matches(osId));
    }

    /** A criterion satisfied by booting the given OS (or any OS when empty). */
    public static Criterion<Instance> booted(final Optional<ResourceLocation> osId) {
        return ComputingModule.OS_FIRST_BOOT.get().createCriterion(new Instance(Optional.empty(), osId));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> os)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                ResourceLocation.CODEC.optionalFieldOf("os").forGetter(Instance::os)
        ).apply(inst, Instance::new));

        public boolean matches(final ResourceLocation osId) {
            return os.isEmpty() || os.get().equals(osId);
        }
    }
}
