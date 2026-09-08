/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client;

import dev.jstech.computers.JsComputers;
import dev.jstech.computers.block.SupercomputerRackBlock;
import dev.jstech.computers.blockentity.ServerRackBlockEntity;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Picks the cabinet model for a rack: the Supercomputer Rack has its own, the Server Rack one per era.
 * Every model shares one animation file (the roof fans); textures are one atlas per model.
 */
public final class RackGeoModel extends GeoModel<ServerRackBlockEntity> {

    private static final ResourceLocation ANIMATIONS =
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "animations/rack.animation.json");

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
        return ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "geo/" + modelName(rack) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(final ServerRackBlockEntity rack) {
        return ResourceLocation.fromNamespaceAndPath(JsComputers.MODID,
                "textures/block/rack/" + modelName(rack) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(final ServerRackBlockEntity rack) {
        return ANIMATIONS;
    }
}
