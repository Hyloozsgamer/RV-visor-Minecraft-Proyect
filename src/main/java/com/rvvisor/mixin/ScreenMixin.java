package com.rvvisor.mixin;

import com.rvvisor.RVVisorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    /**
     * Disable 2D screen blur shaders in VR to prevent double vision and severe distortions
     */
    @Inject(method = "renderBlurredBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void rvvisor$noGuiBlur(float delta, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                ci.cancel();
            }
        }
    }

    /**
     * Disable the default 0xC0101010 dark semi-transparent tint behind menus in VR
     * so the 3D Minecraft world remains 100% bright and crystal clear behind the floating panel.
     */
    @Inject(method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void rvvisor$noMenuBackground(GuiGraphics guiGraphics, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                ci.cancel();
            }
        }
    }

    /**
     * Disable transparent background dimming in VR
     */
    @Inject(method = "renderTransparentBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void rvvisor$noTransparentBackground(GuiGraphics guiGraphics, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                ci.cancel();
            }
        }
    }

}
