package com.rvvisor.mixin;

import com.rvvisor.RVVisorMod;
import com.rvvisor.core.data.VRDevicePose;
import com.rvvisor.core.data.VRTrackingContext;
import com.rvvisor.core.input.VRControllerInput;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void rvvisor$injectVRInput(boolean slowDown, float sneakingSlowDownMultiplier, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            VRTrackingContext tracking = mod.getTrackingContext();
            if (tracking != null) {
                VRDevicePose hmd = tracking.getRenderHmdPose();
                if (hmd != null && hmd.isValid()) {
                    float headYawRad = (float) Math.toRadians(hmd.getYawDegrees());
                    float cos = (float) Math.cos(headYawRad);
                    float sin = (float) Math.sin(headYawRad);
                    float forward = this.forwardImpulse;
                    float left = this.leftImpulse;
                    this.forwardImpulse = forward * cos - left * sin;
                    this.leftImpulse = forward * sin + left * cos;
                }
            }

            VRControllerInput input = mod.getControllerInput();
            if (input != null) {
                input.applyToPlayerInput(this);
            }
        }
    }
}
