package com.rvvisor.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import com.rvvisor.core.data.VRTrackingContext;

public class VRHandRenderer {
    public VRHandRenderer() {
    }

    // Match signature from ItemInHandRendererMixin
    public void renderHands(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, int light, float tickDelta, VRTrackingContext context) {
    }

    // Match signature from VRRenderEngine
    public void renderHands(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, int light, float tickDelta, VRTrackingContext context, int eye, Camera camera) {
    }
}
