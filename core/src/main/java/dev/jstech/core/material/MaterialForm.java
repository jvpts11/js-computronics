/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.material;

/**
 * The physical/chemical forms a material can take. Each form declares the {@code c:} tag folder
 * it belongs to and the item registry key suffix it contributes.
 *
 * <p>Vanilla forms (INGOT, NUGGET) exist for all base materials in vanilla Minecraft.
 * Mod forms (DUST, PLATE, …) are items registered by this mod; their activation is controlled
 * per-material by {@link ModMaterial#activeModForms()}.
 *
 * <p>Forms listed here but not activated in any {@link ModMaterial} are defined-inactive placeholders
 * that cost nothing at runtime and make future activation a one-line change.
 */
public enum MaterialForm {
    INGOT("ingots"),
    NUGGET("nuggets"),
    DUST("dusts"),
    PLATE("plates"),
    BOLT("bolts"),
    ROD("rods"),
    GEAR("gears");

    private final String tagFolder;

    MaterialForm(final String tagFolder) {
        this.tagFolder = tagFolder;
    }

    /** Returns the {@code c:} tag path for this form of the given material, e.g. {@code "dusts/iron"}. */
    public String tagPath(final String materialName) {
        return tagFolder + "/" + materialName;
    }

    /** Returns the item registry key for this form of the given material, e.g. {@code "iron_dust"}. */
    public String itemKey(final String materialName) {
        return materialName + "_" + name().toLowerCase();
    }
}
