/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.material;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registers one {@link DeferredItem} for every (material, form) pair where
 * {@link ModMaterial#activeModForms()} is non-empty.
 *
 * <p>Call {@link #register(DeferredRegister.Items)} exactly once, immediately after declaring
 * the {@code DeferredRegister.Items} in the module class. Retrieve items via
 * {@link #get(ModMaterial, MaterialForm)}.
 */
public final class MaterialItems {

    private static final Map<ModMaterial, EnumMap<MaterialForm, DeferredItem<Item>>> REGISTRY =
            new EnumMap<>(ModMaterial.class);

    private MaterialItems() {}

    /**
     * Iterates every active (material × mod-form) pair and registers a plain {@code Item} for each.
     * Must be called before any field that calls {@link #get}.
     */
    public static void register(final DeferredRegister.Items items) {
        for (final ModMaterial mat : ModMaterial.values()) {
            if (mat.activeModForms().isEmpty()) continue;
            final EnumMap<MaterialForm, DeferredItem<Item>> formMap = new EnumMap<>(MaterialForm.class);
            for (final MaterialForm form : mat.activeModForms()) {
                formMap.put(form, items.register(
                        form.itemKey(mat.materialName()),
                        () -> new Item(new Item.Properties())));
            }
            REGISTRY.put(mat, formMap);
        }
    }

    /**
     * Returns the registered {@link DeferredItem} for the given material+form pair.
     *
     * @throws IllegalStateException if the pair is not in {@link ModMaterial#activeModForms()} or
     *                               if {@link #register} has not been called yet
     */
    public static DeferredItem<Item> get(final ModMaterial mat, final MaterialForm form) {
        final EnumMap<MaterialForm, DeferredItem<Item>> formMap = REGISTRY.get(mat);
        if (formMap == null || !formMap.containsKey(form)) {
            throw new IllegalStateException(
                    "Not registered: " + mat + "/" + form
                    + ". Check activeModForms or call MaterialItems.register() first");
        }
        return formMap.get(form);
    }
}
