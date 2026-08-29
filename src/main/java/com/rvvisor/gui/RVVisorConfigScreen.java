package com.rvvisor.gui;

import com.rvvisor.config.VRConfigFile;
import com.rvvisor.core.optics.LensSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RVVisorConfigScreen extends Screen {
    private final Screen parent;
    private final LensSettings settings;

    public RVVisorConfigScreen(Screen parent, LensSettings settings) {
        super(Component.literal("RV-Visor Config"));
        this.parent = parent;
        this.settings = settings;
    }

    @Override
    protected void init() {
        // 1. Pixel Density (Supersampling)
        this.addRenderableWidget(Button.builder(
            Component.literal("Pixel Density: " + String.format("%.1fx", this.settings.getSupersamplingScale()) + " (" + this.settings.getEffectiveWidth() + "x" + this.settings.getEffectiveHeight() + ")"),
            b -> {
                float next = (float) (Math.round((this.settings.getSupersamplingScale() + 0.1f) * 10.0) / 10.0);
                if (next > 2.5f) next = 0.5f;
                this.settings.setSupersamplingScale(next);
                VRConfigFile.save(this.settings);
                b.setMessage(Component.literal("Pixel Density: " + String.format("%.1fx", this.settings.getSupersamplingScale()) + " (" + this.settings.getEffectiveWidth() + "x" + this.settings.getEffectiveHeight() + ")"));
            }
        ).bounds(this.width / 2 - 120, this.height / 2 - 45, 240, 20).build());

        // 2. Hardware MSAA Anti-Aliasing
        this.addRenderableWidget(Button.builder(
            getMsaaButtonText(),
            b -> {
                this.settings.cycleMsaa();
                VRConfigFile.save(this.settings);
                b.setMessage(getMsaaButtonText());
            }
        ).bounds(this.width / 2 - 120, this.height / 2 - 20, 240, 20).build());

        // 3. AMD FidelityFX CAS Sharpening
        this.addRenderableWidget(Button.builder(
            getSharpnessButtonText(),
            b -> {
                float current = this.settings.getSharpness();
                float next;
                if (current < 0.05f) next = 0.3f;
                else if (current < 0.35f) next = 0.6f;
                else if (current < 0.65f) next = 0.8f;
                else if (current < 0.85f) next = 1.0f;
                else next = 0.0f;
                this.settings.setSharpness(next);
                VRConfigFile.save(this.settings);
                b.setMessage(getSharpnessButtonText());
            }
        ).bounds(this.width / 2 - 120, this.height / 2 + 5, 240, 20).build());

        // 4. Re-aplicar Auto-Configuración
        this.addRenderableWidget(Button.builder(
            Component.literal("⚡ Restablecer Valores Óptimos"),
            b -> {
                this.settings.setSupersamplingScale(1.0f);
                this.settings.setMsaaSamples(2);
                this.settings.setSharpness(0.0f);
                VRConfigFile.save(this.settings);
                this.init(); // Refresh UI button texts
            }
        ).bounds(this.width / 2 - 120, this.height / 2 + 30, 240, 20).build());

        // 5. Hecho / Close
        this.addRenderableWidget(Button.builder(Component.literal("Hecho"), b -> this.onClose())
            .bounds(this.width / 2 - 120, this.height / 2 + 55, 240, 20).build());
    }

    private Component getMsaaButtonText() {
        int samples = this.settings.getMsaaSamples();
        if (samples <= 1) {
            return Component.literal("Anti-Aliasing: MSAA Desactivado");
        }
        String tag = (samples == 2) ? " (Recomendado VR)" : "";
        return Component.literal("Anti-Aliasing: MSAA " + samples + "x" + tag);
    }

    private Component getSharpnessButtonText() {
        float s = this.settings.getSharpness();
        if (s <= 0.01f) {
            return Component.literal("Nitidez CAS: Desactivado (Recomendado)");
        }
        int pct = Math.round(s * 100);
        return Component.literal("Nitidez CAS: " + pct + "%");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(this.font, "RV-Visor - Configuración Óptica & Nitidez VR", this.width / 2, this.height / 2 - 70, 0x55FFFF);
        g.drawCenteredString(this.font, "Atajos: Numpad +/- (Res), F8 (Nitidez), Y (Menú)", this.width / 2, this.height / 2 + 65, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        VRConfigFile.save(this.settings);
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
