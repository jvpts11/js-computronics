/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMaterialTest {

    // materialName()

    @Test
    void iron_materialName() {
        assertEquals("iron", ModMaterial.IRON.materialName());
    }

    @Test
    void copper_materialName() {
        assertEquals("copper", ModMaterial.COPPER.materialName());
    }

    @Test
    void gold_materialName() {
        assertEquals("gold", ModMaterial.GOLD.materialName());
    }

    @Test
    void tin_materialName() {
        assertEquals("tin", ModMaterial.TIN.materialName());
    }

    @Test
    void bronze_materialName() {
        assertEquals("bronze", ModMaterial.BRONZE.materialName());
    }

    // isFormActive()

    @Test
    void iron_dust_is_active() {
        assertTrue(ModMaterial.IRON.isFormActive(MaterialForm.DUST));
    }

    @Test
    void iron_plate_is_active() {
        assertTrue(ModMaterial.IRON.isFormActive(MaterialForm.PLATE));
    }

    @Test
    void iron_ingot_is_active() {
        assertTrue(ModMaterial.IRON.isFormActive(MaterialForm.INGOT));
    }

    @Test
    void iron_nugget_is_active() {
        assertTrue(ModMaterial.IRON.isFormActive(MaterialForm.NUGGET));
    }

    @Test
    void iron_bolt_is_not_active() {
        assertFalse(ModMaterial.IRON.isFormActive(MaterialForm.BOLT));
    }

    @Test
    void iron_rod_is_not_active() {
        assertFalse(ModMaterial.IRON.isFormActive(MaterialForm.ROD));
    }

    @Test
    void iron_gear_is_not_active() {
        assertFalse(ModMaterial.IRON.isFormActive(MaterialForm.GEAR));
    }

    @Test
    void copper_plate_is_active() {
        assertTrue(ModMaterial.COPPER.isFormActive(MaterialForm.PLATE));
    }

    @Test
    void copper_ingot_is_active() {
        assertTrue(ModMaterial.COPPER.isFormActive(MaterialForm.INGOT));
    }

    @Test
    void copper_nugget_is_not_active() {
        // Vanilla 1.21.1 has no copper nugget.
        assertFalse(ModMaterial.COPPER.isFormActive(MaterialForm.NUGGET));
    }

    @Test
    void copper_dust_is_not_active() {
        assertFalse(ModMaterial.COPPER.isFormActive(MaterialForm.DUST));
    }

    @Test
    void gold_ingot_is_active() {
        assertTrue(ModMaterial.GOLD.isFormActive(MaterialForm.INGOT));
    }

    @Test
    void gold_nugget_is_active() {
        assertTrue(ModMaterial.GOLD.isFormActive(MaterialForm.NUGGET));
    }

    @Test
    void gold_dust_is_not_active() {
        assertFalse(ModMaterial.GOLD.isFormActive(MaterialForm.DUST));
    }

    @Test
    void tin_is_fully_inactive() {
        for (final MaterialForm form : MaterialForm.values()) {
            assertFalse(ModMaterial.TIN.isFormActive(form),
                    "TIN must be inactive for form " + form);
        }
    }

    @Test
    void bronze_is_fully_inactive() {
        for (final MaterialForm form : MaterialForm.values()) {
            assertFalse(ModMaterial.BRONZE.isFormActive(form),
                    "BRONZE must be inactive for form " + form);
        }
    }

    // isVanillaForm()

    @Test
    void iron_ingot_is_vanilla() {
        assertTrue(ModMaterial.IRON.isVanillaForm(MaterialForm.INGOT));
    }

    @Test
    void iron_nugget_is_vanilla() {
        assertTrue(ModMaterial.IRON.isVanillaForm(MaterialForm.NUGGET));
    }

    @Test
    void iron_dust_is_not_vanilla() {
        assertFalse(ModMaterial.IRON.isVanillaForm(MaterialForm.DUST));
    }

    @Test
    void iron_plate_is_not_vanilla() {
        assertFalse(ModMaterial.IRON.isVanillaForm(MaterialForm.PLATE));
    }

    @Test
    void copper_ingot_is_vanilla() {
        assertTrue(ModMaterial.COPPER.isVanillaForm(MaterialForm.INGOT));
    }

    @Test
    void copper_nugget_is_not_vanilla() {
        // Vanilla Minecraft 1.21.1 has no copper nugget; NUGGET is therefore not a vanilla form for copper.
        assertFalse(ModMaterial.COPPER.isVanillaForm(MaterialForm.NUGGET));
    }

    @Test
    void copper_plate_is_not_vanilla() {
        assertFalse(ModMaterial.COPPER.isVanillaForm(MaterialForm.PLATE));
    }

    @Test
    void gold_ingot_is_vanilla() {
        assertTrue(ModMaterial.GOLD.isVanillaForm(MaterialForm.INGOT));
    }

    @Test
    void gold_nugget_is_vanilla() {
        assertTrue(ModMaterial.GOLD.isVanillaForm(MaterialForm.NUGGET));
    }

    // isModForm()

    @Test
    void iron_dust_is_mod_form() {
        assertTrue(ModMaterial.IRON.isModForm(MaterialForm.DUST));
    }

    @Test
    void iron_plate_is_mod_form() {
        assertTrue(ModMaterial.IRON.isModForm(MaterialForm.PLATE));
    }

    @Test
    void iron_ingot_is_not_mod_form() {
        assertFalse(ModMaterial.IRON.isModForm(MaterialForm.INGOT));
    }

    @Test
    void iron_nugget_is_not_mod_form() {
        assertFalse(ModMaterial.IRON.isModForm(MaterialForm.NUGGET));
    }

    @Test
    void copper_plate_is_mod_form() {
        assertTrue(ModMaterial.COPPER.isModForm(MaterialForm.PLATE));
    }

    @Test
    void copper_ingot_is_not_mod_form() {
        assertFalse(ModMaterial.COPPER.isModForm(MaterialForm.INGOT));
    }

    @Test
    void gold_has_no_mod_forms() {
        for (final MaterialForm form : MaterialForm.values()) {
            assertFalse(ModMaterial.GOLD.isModForm(form),
                    "GOLD must have no mod forms, but got true for " + form);
        }
    }

    // vanillaForms / activeModForms set immutability

    @Test
    void vanillaForms_returns_unmodifiable_set() {
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> ModMaterial.IRON.vanillaForms().add(MaterialForm.DUST));
    }

    @Test
    void activeModForms_returns_unmodifiable_set() {
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> ModMaterial.IRON.activeModForms().add(MaterialForm.INGOT));
    }
}
