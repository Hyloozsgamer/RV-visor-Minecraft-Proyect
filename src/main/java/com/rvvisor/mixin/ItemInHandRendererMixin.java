package com.rvvisor.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rvvisor.RVVisorMod;
import com.rvvisor.render.VRHandRenderer;
import com.rvvisor.render.VRRenderEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void rvvisor$overrideHandsWithItems(float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int combinedLight, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            VRRenderEngine engine = mod.getRenderEngine();
            if (engine != null && engine.getHandRenderer() != null) {
                // Delegate 6-DOF hand and held item rendering to VRHandRenderer
                engine.getHandRenderer().renderHands(poseStack, bufferSource, combinedLight, partialTicks, mod.getTrackingContext());
                ci.cancel();
            }
        }
    }
}
