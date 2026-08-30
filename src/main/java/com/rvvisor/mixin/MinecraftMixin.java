package com.rvvisor.mixin;

import com.rvvisor.RVVisorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    @Final
    public GameRenderer gameRenderer;




    @Inject(method = "destroy", at = @At("HEAD"))
    private void rvvisor$onDestroy(CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null) {
            if (mod.getRenderEngine() != null) {
                mod.getRenderEngine().destroy();
            }
            if (mod.getVrProvider() != null) {
                mod.getVrProvider().shutdown();
            }
        }
    }

    /**
     * Intercepts the Escape key in VR mode to reliably open / close the Pause Menu.
     * The VR compositor window may not forward key events correctly, so we poll
     * the GLFW key state directly every tick via handleKeybinds.
     */
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void rvvisor$handleEscapeKey(CallbackInfo ci) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod == null || !mod.isVrActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        // Poll hotkeys in real time (F8 Nitidez, F9 MSAA, Y Menú, Numpad +/- Resolución)
        if (mod.getLensSettings() != null && mod.getRenderEngine() != null) {
            com.rvvisor.render.VRResolutionControls.onClientTick(mc, mod.getLensSettings(), mod.getRenderEngine());
        }

        long window = mc.getWindow().getWindow();
        boolean escPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
        if (escPressed && !this.rvvisor$escWasPressed) {
            if (mc.screen == null) {
                mc.setScreen(new PauseScreen(true));
            } else if (mc.screen instanceof PauseScreen) {
                mc.setScreen(null);
            }
        }
        this.rvvisor$escWasPressed = escPressed;
    }

    private boolean rvvisor$escWasPressed = false;

    /**
     * Disables the Fabulous graphics post-chain processing in VR.
     * This forces Minecraft to use standard Forward Rendering for water/translucency,
     * which inherently works flawlessly with our Stereo VR Framebuffers,
     * mimicking Vivecraft's "RenderVrFast" behavior.
     */
    @Inject(method = "useShaderTransparency", at = @At("HEAD"), cancellable = true)
    private static void rvvisor$disableFabulousInVR(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod != null && mod.isVrActive()) {
            cir.setReturnValue(false);
        }
    }
}
