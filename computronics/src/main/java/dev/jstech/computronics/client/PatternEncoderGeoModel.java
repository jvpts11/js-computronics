/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.blockentity.PatternEncoderBlockEntity;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Picks the body model for a Pattern Encoder: one per hardware era, because a floppy burner, a CD burner
 * and a slot-in DVD/USB burner are different machines and not a repaint. Every model shares one animation
 * file (the disc spin and the activity lamp); textures are one atlas per model.
 */
public final class PatternEncoderGeoModel extends GeoModel<PatternEncoderBlockEntity> {

    private static final ResourceLocation ANIMATIONS =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "animations/pattern_encoder.animation.json");

    /** The model name of an encoder body, by era. */
    public static String modelName(final HardwareEra era) {
        if (era == HardwareEra.VINTAGE) {
            return "vintage_pattern_encoder";
        }
        if (era == HardwareEra.LEGACY) {
            return "legacy_pattern_encoder";
        }
        return "pattern_encoder";
    }

    @Override
    public ResourceLocation getModelResource(final PatternEncoderBlockEntity encoder) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID,
                "geo/" + modelName(encoder.era()) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(final PatternEncoderBlockEntity encoder) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID,
                "textures/block/pattern_encoder/" + modelName(encoder.era()) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(final PatternEncoderBlockEntity encoder) {
        return ANIMATIONS;
    }
}
