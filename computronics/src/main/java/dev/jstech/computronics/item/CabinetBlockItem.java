/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.item;

import dev.jstech.computronics.JsComputronics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * The item of a multiblock cabinet — a rack or a Mainframe — which shows the cabinet itself.
 *
 * <p>A cabinet's block model is a GeckoLib model drawn by the block entity, so the block itself renders
 * nothing, and its item, having no block model to fall back on, showed a flat icon that looked like a
 * different mod's placeholder next to the machine it places. This item hands the inventory the same
 * model the world uses, shrunk to fit the slot.
 *
 * <p>The renderer is created inside {@link #createGeoRenderer}, which only the client ever calls, so
 * the client-only classes it names are never loaded on a server.
 */
public class CabinetBlockItem extends BlockItem implements GeoItem {

    /**
     * How a cabinet is fitted into an item slot: its longest side in model units (16 to a block), which
     * decides how far it shrinks, and the offset in blocks that brings the cabinet's own middle onto the
     * item's middle. A cabinet is modelled around its controller block, which is rarely its centre.
     */
    public record Fit(float span, float offsetX, float offsetY, float offsetZ) {
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The texture folder this family's atlases live in, e.g. {@code rack}. */
    private final String folder;
    /** The model this cabinet is drawn with, e.g. {@code vintage_server_rack}. */
    private final String model;
    /** The animation file the family shares, e.g. {@code rack}. */
    private final String animation;
    private final Fit fit;

    public CabinetBlockItem(final Block block, final Properties properties, final String folder,
                            final String model, final String animation, final Fit fit) {
        super(block, properties);
        this.folder = folder;
        this.model = model;
        this.animation = animation;
        this.fit = fit;
    }

    public ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "geo/" + model + ".geo.json");
    }

    public ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID,
                "textures/block/" + folder + "/" + model + ".png");
    }

    public ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID,
                "animations/" + animation + ".animation.json");
    }

    public Fit fit() {
        return fit;
    }

    @Override
    public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) {
        // An item in a slot is a still photograph of the cabinet: the fans only turn in the world.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(final Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private Object renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new dev.jstech.computronics.client.CabinetItemRenderer();
                }
                return (net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer) renderer;
            }
        });
    }
}
