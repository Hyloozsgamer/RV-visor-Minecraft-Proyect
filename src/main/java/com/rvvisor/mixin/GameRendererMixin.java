package com.rvvisor.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rvvisor.RVVisorMod;
import com.rvvisor.core.optics.LensSettings;
import com.rvvisor.render.VRRenderEngine;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Camera mainCamera;

    @Shadow
    @Final
    private net.minecraft.client.renderer.LightTexture lightTexture;

    @Shadow
    public abstract void renderLevel(DeltaTracker deltaTracker);

    @Shadow
    public abstract Matrix4f getProjectionMatrix(double fov);

    private boolean isInsideStereoPass = false;

    /**
     * Master Stereo Render Loop:
     * When inside a Minecraft world, executes Left & Right Eye passes using
     * MainRenderTarget,
     * copies native block/entity textures with shaders, renders Pause Menu / GUI
     * screens,
     * and submits to SteamVR / Virtual Desktop.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rvvisor$renderStereoPipeline(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod == null || !mod.isVrActive() || this.isInsideStereoPass) {
            return;
        }

        // Only hijack the render pipeline when a level is actually loaded
        if (this.minecraft.level == null) {
            return;
        }

        VRRenderEngine engine = mod.getRenderEngine();
        if (engine == null)
            return;

        try {
            this.isInsideStereoPass = true;
            float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
            int windowWidth = this.minecraft.getWindow().getWidth();
            int windowHeight = this.minecraft.getWindow().getHeight();

            // 1. Poll VR tracking and update poses
            engine.onRenderFrameStart();

            // Update dynamic lighting (caves, torches, sun, moon, lightmaps)
            if (this.lightTexture != null) {
                this.lightTexture.updateLightTexture(partialTicks);
            }

            // Render active screen (Pause menu, Options, Inventory) into floating 3D canvas FBO
            if (this.minecraft.screen != null) {
                engine.getGuiRenderer().renderScreenToFbo(this.minecraft, deltaTracker, partialTicks);
            } else {
                engine.getHudRenderer().updateHudTexture(this.minecraft, deltaTracker);
            }

            float depthFar = this.getDepthFar();
            if (depthFar < 100.0f) depthFar = 1000.0f;

            // 2. EYE PASS 0: Left Eye (direct stereo render)
            engine.beginEyePass(LensSettings.EYE_LEFT, partialTicks);
            this.renderLevel(deltaTracker);
            // Render 3D Hands, crosshair, and floating GUI in world/eye space
            engine.renderVRWorldElements(this.mainCamera, partialTicks, engine.getEyeProjectionMatrix(0.05f, depthFar));
            engine.endEyePass();

            // 3. EYE PASS 1: Right Eye (direct stereo render)
            engine.beginEyePass(LensSettings.EYE_RIGHT, partialTicks);
            this.renderLevel(deltaTracker);
            // Render 3D Hands, crosshair, and floating GUI in world/eye space
            engine.renderVRWorldElements(this.mainCamera, partialTicks, engine.getEyeProjectionMatrix(0.05f, depthFar));
            engine.endEyePass();

            // 4. Submit stereo textures to OpenXR / OpenVR compositor and render spectator mirror
            engine.finishAndSubmitFrame(windowWidth, windowHeight);

            // Cancel standard flat mono render pass
            ci.cancel();
        } catch (Throwable t) {
            RVVisorMod.LOGGER.error("[RV-Visor] Error during VR stereo render pass", t);
        } finally {
            this.isInsideStereoPass = false;
        }
    }

    @Shadow
    public abstract float getDepthFar();

    @Inject(method = "getProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void rvvisor$overrideProjectionMatrix(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            VRRenderEngine engine = mod.getRenderEngine();
            if (engine != null && engine.isRenderingVR()) {
                float depthFar = this.getDepthFar();
                cir.setReturnValue(engine.getEyeProjectionMatrix(0.05f, depthFar > 100.0f ? depthFar : 1000.0f));
            }
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void rvvisor$cancelBobView(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive() && this.minecraft.level != null) {
            // Cancel camera view bobbing in VR to eliminate motion sickness
            ci.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void rvvisor$cancelBobHurt(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive() && this.minecraft.level != null) {
            // Cancel damage view tilt in VR
            ci.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void rvvisor$cancelVanillaItemInHand(Camera camera, float partialTicks, Matrix4f projectionMatrix, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive() && this.minecraft.level != null) {
            // Cancel 2D vanilla arm rendering so VR 6-DOF hand renderer takes full control
            ci.cancel();
        }
    }
}
