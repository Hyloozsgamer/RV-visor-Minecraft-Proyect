package com.rvvisor.mixin;

import com.rvvisor.RVVisorMod;
import com.rvvisor.config.VRAutoConfig;
import com.rvvisor.gui.RVVisorConfigScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void rvvisor$onTitleScreenInit(CallbackInfo ci) {
        // Ejecuta la auto-configuración de VR en cuanto se entra al menú principal
        VRAutoConfig.runAutoConfig(this.minecraft);

        // Añade el botón de acceso directo a la configuración de RV-Visor en el menú principal
        this.addRenderableWidget(Button.builder(
                Component.literal("🥽 RV-Visor Config"),
                btn -> {
                    RVVisorMod mod = RVVisorMod.getInstance();
                    if (mod != null && this.minecraft != null) {
                        this.minecraft.setScreen(new RVVisorConfigScreen(this, mod.getLensSettings()));
                    }
                }
        ).bounds(this.width / 2 + 104, this.height / 4 + 48 + 72 + 12, 100, 20).build());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void rvvisor$renderVrStatus(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // Muestra el estado de la conexión VR en la parte superior izquierda de la pantalla
        String status = VRAutoConfig.getStatusMessage();
        int color = VRAutoConfig.isVrHardwareConnected() ? 0x55FF55 : 0xFFCC00;
        guiGraphics.drawString(this.font, status, 8, 8, color, true);
    }
}
