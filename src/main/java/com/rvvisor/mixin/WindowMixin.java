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

    private static final int VIRTUAL_GUI_WIDTH = 1920;
    private static final int VIRTUAL_GUI_HEIGHT = 1080;
    private static final int VIRTUAL_GUI_SCALE = 3;
    private static final int VIRTUAL_SCALED_WIDTH = 640;
    private static final int VIRTUAL_SCALED_HEIGHT = 360;

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
            cir.setReturnValue(VIRTUAL_GUI_WIDTH);
        }
    }

    @Inject(method = "getScreenHeight", at = @At("HEAD"), cancellable = true, require = 0)
    private void rvvisor$getVrScreenHeight(CallbackInfoReturnable<Integer> cir) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            cir.setReturnValue(VIRTUAL_GUI_HEIGHT);
        }
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true, require = 0)
    private void rvvisor$getVrScaledWidth(CallbackInfoReturnable<Integer> cir) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            cir.setReturnValue(VIRTUAL_SCALED_WIDTH);
        }
    }

    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true, require = 0)
    private void rvvisor$getVrScaledHeight(CallbackInfoReturnable<Integer> cir) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            cir.setReturnValue(VIRTUAL_SCALED_HEIGHT);
        }
    }

    @Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true, require = 0)
    private void rvvisor$getVrGuiScale(CallbackInfoReturnable<Integer> cir) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            cir.setReturnValue(VIRTUAL_GUI_SCALE);
        }
    }
}
