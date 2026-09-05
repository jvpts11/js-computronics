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
import dev.jsc.jscomputronics.module.computing.block.SupercomputerRackBlock;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Picks the cabinet model for a rack: the Supercomputer Rack has its own, the Server Rack one per era.
 * Every model shares one animation file (the roof fans); textures are one atlas per model.
 */
public final class RackGeoModel extends GeoModel<ServerRackBlockEntity> {

    private static final ResourceLocation ANIMATIONS =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "animations/rack.animation.json");

    /** The model name of a cabinet: by type first, then by era. */
    public static String modelName(final ServerRackBlockEntity rack) {
        if (rack.getBlockState().getBlock() instanceof SupercomputerRackBlock) {
            return "supercomputer_rack";
        }
        final HardwareEra era = rack.rackEra();
        if (era == HardwareEra.VINTAGE) {
            return "vintage_server_rack";
        }
        if (era == HardwareEra.LEGACY) {
            return "legacy_server_rack";
        }
        return "server_rack";
    }

    @Override
    public ResourceLocation getModelResource(final ServerRackBlockEntity rack) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "geo/" + modelName(rack) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(final ServerRackBlockEntity rack) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID,
                "textures/block/rack/" + modelName(rack) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(final ServerRackBlockEntity rack) {
        return ANIMATIONS;
    }
}
