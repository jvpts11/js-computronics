/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.material;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static dev.jsc.jscomputronics.common.material.MaterialForm.DUST;
import static dev.jsc.jscomputronics.common.material.MaterialForm.INGOT;
import static dev.jsc.jscomputronics.common.material.MaterialForm.NUGGET;
import static dev.jsc.jscomputronics.common.material.MaterialForm.PLATE;

/**
 * The base materials this mod works with. Each entry declares which {@link MaterialForm}s have
 * vanilla items (so only {@code c:} tag entries are needed) and which forms this mod registers
 * as new items.
 *
 * <p>Adding a new form for an existing material is a one-line change: add the form to the
 * appropriate {@link EnumSet} in the constructor call. Adding a new material is a one-line
 * constant. Inactive entries (tier 1+, empty form sets) cost nothing at runtime and are
 * placeholders for future progression tiers.
 */
public enum ModMaterial {
    IRON  ("iron",   0, EnumSet.of(INGOT, NUGGET), EnumSet.of(DUST, PLATE)),
    COPPER("copper", 0, EnumSet.of(INGOT),          EnumSet.of(PLATE)),
    GOLD  ("gold",   0, EnumSet.of(INGOT, NUGGET), EnumSet.noneOf(MaterialForm.class)),
    TIN   ("tin",    1, EnumSet.noneOf(MaterialForm.class), EnumSet.noneOf(MaterialForm.class)),
    BRONZE("bronze", 1, EnumSet.noneOf(MaterialForm.class), EnumSet.noneOf(MaterialForm.class));

    private final String materialName;
    private final int tier;
    private final Set<MaterialForm> vanillaForms;
    private final Set<MaterialForm> activeModForms;

    ModMaterial(final String materialName, final int tier,
                final Set<MaterialForm> vanillaForms,
                final Set<MaterialForm> activeModForms) {
        this.materialName = materialName;
        this.tier = tier;
        this.vanillaForms = Collections.unmodifiableSet(vanillaForms);
        this.activeModForms = Collections.unmodifiableSet(activeModForms);
    }

    /** The snake_case name used in registry keys and tag paths, e.g. {@code "iron"}. */
    public String materialName() {
        return materialName;
    }

    /** Industrial tier at which this material becomes available (0 = base tier). */
    public int tier() {
        return tier;
    }

    /**
     * Forms that exist as vanilla Minecraft items — this mod only needs to add them to {@code c:} tags,
     * not register new items.
     */
    public Set<MaterialForm> vanillaForms() {
        return vanillaForms;
    }

    /**
     * Forms for which this mod registers new items. Empty means the material is currently inactive.
     */
    public Set<MaterialForm> activeModForms() {
        return activeModForms;
    }

    /** Whether the given form has an item for this material (vanilla or mod-registered). */
    public boolean isFormActive(final MaterialForm form) {
        return vanillaForms.contains(form) || activeModForms.contains(form);
    }

    /** Whether the given form's item comes from vanilla Minecraft (not registered by this mod). */
    public boolean isVanillaForm(final MaterialForm form) {
        return vanillaForms.contains(form);
    }

    /** Whether the given form's item is registered by this mod. */
    public boolean isModForm(final MaterialForm form) {
        return activeModForms.contains(form);
    }
}
