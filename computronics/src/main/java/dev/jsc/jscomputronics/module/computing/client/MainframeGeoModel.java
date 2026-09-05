/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jstech.core.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Picks the cabinet model for a Mainframe: one per hardware era, because an era is a different shape of
 * machine and not a repaint. Every model shares one animation file (the roof fans and the tape reels);
 * textures are one atlas per model.
 */
public final class MainframeGeoModel extends GeoModel<MainframeBlockEntity> {

    private static final ResourceLocation ANIMATIONS =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "animations/mainframe.animation.json");

    /** The model name of a cabinet, by era. */
    public static String modelName(final HardwareEra era) {
        if (era == HardwareEra.VINTAGE) {
            return "vintage_mainframe";
        }
        if (era == HardwareEra.LEGACY) {
            return "legacy_mainframe";
        }
        return "mainframe";
    }

    @Override
    public ResourceLocation getModelResource(final MainframeBlockEntity mainframe) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID,
                "geo/" + modelName(mainframe.mainframeEra()) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(final MainframeBlockEntity mainframe) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID,
                "textures/block/mainframe/" + modelName(mainframe.mainframeEra()) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(final MainframeBlockEntity mainframe) {
        return ANIMATIONS;
    }
}
