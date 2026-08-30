package com.rvvisor.mixin;

import com.rvvisor.RVVisorMod;
import com.rvvisor.core.data.VRDevicePose;
import com.rvvisor.core.data.VRTrackingContext;
import com.rvvisor.render.VRRenderEngine;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private Vec3 position;

    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void rvvisor$overrideCameraPose(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTicks, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            VRTrackingContext tracking = mod.getTrackingContext();
            VRRenderEngine engine = mod.getRenderEngine();

            if (tracking != null && engine != null) {
                int currentEye = engine.getCurrentEyePass();
                VRDevicePose eyePose = (currentEye >= 0) ? tracking.getRenderEyePose(currentEye) : tracking.getRenderHmdPose();

                if (eyePose != null && eyePose.isValid()) {
                    Vec3 entityPos = entity.getPosition(partialTicks);

                    // Transform physical room-space tracking offset into Minecraft world space
                    float bodyYaw = entity.getViewYRot(partialTicks);
                    float bodyYawRad = (float) Math.toRadians(bodyYaw);
                    float cos = (float) Math.cos(bodyYawRad);
                    float sin = (float) Math.sin(bodyYawRad);

                    float localX = eyePose.getPosition().x;
                    float localY = eyePose.getPosition().y;
                    float localZ = eyePose.getPosition().z;

                    float worldOffsetX = localX * cos - localZ * sin;
                    float worldOffsetZ = localX * sin + localZ * cos;

                    // Natural standing eye height
                    float eyeYOffset = (localY > 0.05f) ? localY : (float) entity.getEyeHeight();

                    Vec3 vrEyePos = new Vec3(
                            entityPos.x + worldOffsetX,
                            entityPos.y + eyeYOffset,
                            entityPos.z + worldOffsetZ
                    );

                    this.setPosition(vrEyePos);

                    // Synchronize yaw and pitch with Minecraft coordinate convention (negate pitch like Vivecraft)
                    float finalYaw = eyePose.getYawDegrees() + bodyYaw;
                    float finalPitch = -eyePose.getPitchDegrees();
                    this.setRotation(finalYaw, finalPitch);
                }
            }
        }
    }
}
