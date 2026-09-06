/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jstech.computronics.blockentity.PatternEncoderBlockEntity;
import dev.jstech.computronics.os.media.MediaFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws a Pattern Encoder as its era's body. Everything the machine states, it states in hardware: the
 * medium in the bay (a floppy edge, a disc on the tray or in the slit, a stick in the port), the display
 * face for the job's phase, the link lamp when a computer is on the other end of the cable, and the
 * activity lamp while the head is down. Bone visibility is set here; motion is the animation file's.
 */
public final class PatternEncoderRenderer extends GeoBlockRenderer<PatternEncoderBlockEntity> {

    public PatternEncoderRenderer(final BlockEntityRendererProvider.Context context) {
        super(new PatternEncoderGeoModel());
    }

    @Override
    public void preRender(final PoseStack poseStack, final PatternEncoderBlockEntity encoder,
                          final BakedGeoModel model, final MultiBufferSource bufferSource,
                          final VertexConsumer buffer, final boolean isReRender, final float partialTick,
                          final int packedLight, final int packedOverlay, final int colour) {
        super.preRender(poseStack, encoder, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
        // The baked model is shared by every encoder of this era, so every flag is set on every render.
        final boolean media = encoder.hasMedia();
        final MediaFormat format = encoder.mediaFormat();
        final boolean usb = format == MediaFormat.USB;
        final boolean linked = encoder.ownerPos() != null;
        final boolean busy = encoder.busy();
        show(model, "disk", media);
        show(model, "disc", media && !usb);
        show(model, "usb_stick", media && usb);
        show(model, "slot_glow", media && !usb);
        show(model, "busy_lamp", busy);
        show(model, "write_led", busy);
        show(model, "ring", busy);
        show(model, "link_lamp", linked);
        show(model, "power_led", linked);
        show(model, "link_dot", linked);
        final PatternEncoderBlockEntity.Phase phase = encoder.phase();
        show(model, "display_nolink", !linked);
        show(model, "display_idle", linked && phase == PatternEncoderBlockEntity.Phase.IDLE);
        show(model, "display_busy", linked && busy);
        show(model, "display_done", linked && phase == PatternEncoderBlockEntity.Phase.DONE);
        show(model, "display_error", linked && phase == PatternEncoderBlockEntity.Phase.ERROR);
    }

    private static void show(final BakedGeoModel model, final String bone, final boolean visible) {
        model.getBone(bone).ifPresent(b -> b.setHidden(!visible));
    }
}
