/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.datagen;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.advancement.OsFirstBootTrigger;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The mod's advancement tree. The computing branch roots at booting any operating system and carries the
 * two challenges for the distributions installed the hard way: Arch by hand, Gentoo from source.
 */
public final class JscAdvancementProvider extends AdvancementProvider {

    public JscAdvancementProvider(final PackOutput output, final CompletableFuture<HolderLookup.Provider> registries,
                                  final ExistingFileHelper existingFiles) {
        super(output, registries, existingFiles, List.of(new Computing()));
    }

    static final class Computing implements AdvancementGenerator {

        @Override
        public void generate(final HolderLookup.Provider registries, final Consumer<AdvancementHolder> saver,
                             final ExistingFileHelper existingFiles) {
            final AdvancementHolder root = Advancement.Builder.advancement()
                    .display(new ItemStack(ComputingModule.MAINFRAME_ITEM.get()),
                            Component.translatable("advancements.jsc.computing.root.title"),
                            Component.translatable("advancements.jsc.computing.root.description"),
                            ResourceLocation.withDefaultNamespace("textures/block/deepslate_tiles.png"),
                            AdvancementType.TASK, true, true, false)
                    .addCriterion("boot_any_os", OsFirstBootTrigger.booted(Optional.empty()))
                    .save(saver, id("computing/root"), existingFiles);

            Advancement.Builder.advancement()
                    .parent(root)
                    .display(new ItemStack(ComputingModule.USB_FLASH_DRIVE.get()),
                            Component.translatable("advancements.jsc.computing.i_use_arch_btw.title"),
                            Component.translatable("advancements.jsc.computing.i_use_arch_btw.description"),
                            null, AdvancementType.CHALLENGE, true, true, false)
                    .addCriterion("boot_arch", OsFirstBootTrigger.booted(Optional.of(os("arch"))))
                    .save(saver, id("computing/i_use_arch_btw"), existingFiles);

            Advancement.Builder.advancement()
                    .parent(root)
                    .display(new ItemStack(ComputingModule.CD_ROM.get()),
                            Component.translatable("advancements.jsc.computing.recompile.title"),
                            Component.translatable("advancements.jsc.computing.recompile.description"),
                            null, AdvancementType.CHALLENGE, true, true, false)
                    .addCriterion("boot_gentoo", OsFirstBootTrigger.booted(Optional.of(os("gentoo"))))
                    .save(saver, id("computing/recompile"), existingFiles);
        }

        private static ResourceLocation id(final String path) {
            return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, path);
        }

        private static ResourceLocation os(final String path) {
            return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, path);
        }
    }
}
