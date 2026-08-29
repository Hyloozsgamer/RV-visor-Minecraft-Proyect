package com.rvvisor.mixin;

import com.mojang.blaze3d.platform.Window;
import com.rvvisor.RVVisorMod;
import com.rvvisor.render.VRRenderEngine;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class WindowMixin {

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void rvvisor$getVrWidth(CallbackInfoReturnable<Integer> cir) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            VRRenderEngine engine = mod.getRenderEngine();
            if (engine != null && engine.isRenderingVR()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getMainRenderTarget() != null) {
                    cir.setReturnValue(mc.getMainRenderTarget().width);
                }
            }
        }
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void rvvisor$getVrHeight(CallbackInfoReturnable<Integer> cir) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            VRRenderEngine engine = mod.getRenderEngine();
            if (engine != null && engine.isRenderingVR()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getMainRenderTarget() != null) {
                    cir.setReturnValue(mc.getMainRenderTarget().height);
                }
            }
        }
    }

    @Inject(method = "getScreenWidth", at = @At("HEAD"), cancellable = true, require = 0)
    private void rvvisor$getVrScreenWidth(CallbackInfoReturnable<Integer> cir) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            VRRenderEngine engine = mod.getRenderEngine();
            if (engine != null && engine.isRenderingVR()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getMainRenderTarget() != null) {
                    cir.setReturnValue(mc.getMainRenderTarget().width);
                }
            }
        }
    }

    @Inject(method = "getScreenHeight", at = @At("HEAD"), cancellable = true, require = 0)
    private void rvvisor$getVrScreenHeight(CallbackInfoReturnable<Integer> cir) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            VRRenderEngine engine = mod.getRenderEngine();
            if (engine != null && engine.isRenderingVR()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getMainRenderTarget() != null) {
                    cir.setReturnValue(mc.getMainRenderTarget().height);
                }
            }
        }
    }
}
