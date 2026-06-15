/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.material;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaterialFormTest {

    @Test
    void tagPath_dust_iron() {
        assertEquals("dusts/iron", MaterialForm.DUST.tagPath("iron"));
    }

    @Test
    void tagPath_plate_copper() {
        assertEquals("plates/copper", MaterialForm.PLATE.tagPath("copper"));
    }

    @Test
    void tagPath_ingot_gold() {
        assertEquals("ingots/gold", MaterialForm.INGOT.tagPath("gold"));
    }

    @Test
    void tagPath_nugget_iron() {
        assertEquals("nuggets/iron", MaterialForm.NUGGET.tagPath("iron"));
    }

    @Test
    void tagPath_bolt_tin() {
        assertEquals("bolts/tin", MaterialForm.BOLT.tagPath("tin"));
    }

    @Test
    void tagPath_rod_bronze() {
        assertEquals("rods/bronze", MaterialForm.ROD.tagPath("bronze"));
    }

    @Test
    void tagPath_gear_iron() {
        assertEquals("gears/iron", MaterialForm.GEAR.tagPath("iron"));
    }

    @Test
    void itemKey_iron_dust() {
        assertEquals("iron_dust", MaterialForm.DUST.itemKey("iron"));
    }

    @Test
    void itemKey_iron_plate() {
        assertEquals("iron_plate", MaterialForm.PLATE.itemKey("iron"));
    }

    @Test
    void itemKey_copper_plate() {
        assertEquals("copper_plate", MaterialForm.PLATE.itemKey("copper"));
    }

    @Test
    void itemKey_iron_ingot() {
        assertEquals("iron_ingot", MaterialForm.INGOT.itemKey("iron"));
    }

    @Test
    void itemKey_iron_nugget() {
        assertEquals("iron_nugget", MaterialForm.NUGGET.itemKey("iron"));
    }

    @Test
    void itemKey_iron_bolt() {
        assertEquals("iron_bolt", MaterialForm.BOLT.itemKey("iron"));
    }

    @Test
    void itemKey_iron_rod() {
        assertEquals("iron_rod", MaterialForm.ROD.itemKey("iron"));
    }

    @Test
    void itemKey_iron_gear() {
        assertEquals("iron_gear", MaterialForm.GEAR.itemKey("iron"));
    }

    @Test
    void itemKey_uses_lowercase_form_name() {
        for (final MaterialForm form : MaterialForm.values()) {
            final String key = form.itemKey("iron");
            assertEquals("iron_" + form.name().toLowerCase(), key,
                    "itemKey must use lowercase form name for " + form);
        }
    }

    @Test
    void tagPath_uses_plural_folder() {
        assertEquals("ingots/iron", MaterialForm.INGOT.tagPath("iron"));
        assertEquals("nuggets/iron", MaterialForm.NUGGET.tagPath("iron"));
        assertEquals("dusts/iron", MaterialForm.DUST.tagPath("iron"));
        assertEquals("plates/iron", MaterialForm.PLATE.tagPath("iron"));
        assertEquals("bolts/iron", MaterialForm.BOLT.tagPath("iron"));
        assertEquals("rods/iron", MaterialForm.ROD.tagPath("iron"));
        assertEquals("gears/iron", MaterialForm.GEAR.tagPath("iron"));
    }
}
