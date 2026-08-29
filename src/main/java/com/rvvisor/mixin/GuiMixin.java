package com.rvvisor.mixin;

import com.rvvisor.RVVisorMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void rvvisor$cancel2DCrosshairInVR(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            // Cancel flat 2D HUD crosshair in VR to avoid double vision and eye fatigue.
            // The true 3D in-world crosshair is rendered in VRRenderEngine.
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void rvvisor$renderVrStatusHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod == null) return;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.options.hideGui) return;

        boolean isConnected = mod.isVrActive();
        String status = isConnected ? "🥽 RV-Visor: Conectado (SteamVR 90Hz)" : "🥽 RV-Visor: Esperando SteamVR / Visor...";
        int color = isConnected ? 0x55FF55 : 0xFFCC00;

        guiGraphics.drawString(mc.font, status, 6, 6, color, true);
    }
}
