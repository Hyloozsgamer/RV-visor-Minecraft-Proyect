package com.rvvisor.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rvvisor.core.optics.LensSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Real-time VR Supersampling Resolution Controller.
 * Supports universal hotkeys across all keyboard layouts:
 * - Numpad [+] / [-]
 * - PageUp / PageDown
 * - F7 (Subir) / F6 (Bajar)
 * - Standard [+] / [-]
 */
public class VRResolutionControls {

    private static final float STEP = 0.1f;
    private static long lastAdjustTime = 0;
    private static final long COOLDOWN_MS = 180;

    public static void onClientTick(Minecraft mc, LensSettings lensSettings, VRRenderEngine renderEngine) {
        if (mc == null || mc.player == null || lensSettings == null || renderEngine == null) return;
        if (mc.screen != null) return; // No ajustar durante menús abiertos
        if (mc.getWindow() == null) return;

        long now = System.currentTimeMillis();
        if (now - lastAdjustTime < COOLDOWN_MS) return;

        long window = mc.getWindow().getWindow();

        // Hotkey 'Y' para abrir el menú de configuración de RV-Visor en juego
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Y) == GLFW.GLFW_PRESS) {
            lastAdjustTime = now;
            mc.setScreen(new com.rvvisor.gui.RVVisorConfigScreen(null, lensSettings));
            return;
        }

        // Hotkey F8 / Numpad * para alternar nitidez AMD CAS en vivo
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F8) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_KP_MULTIPLY) == GLFW.GLFW_PRESS) {
            lastAdjustTime = now;
            float currentSharp = lensSettings.getSharpness();
            float nextSharp;
            if (currentSharp < 0.05f) nextSharp = 0.3f;
            else if (currentSharp < 0.35f) nextSharp = 0.6f;
            else if (currentSharp < 0.65f) nextSharp = 0.8f;
            else if (currentSharp < 0.85f) nextSharp = 1.0f;
            else nextSharp = 0.0f;
            lensSettings.setSharpness(nextSharp);

            int pct = Math.round(nextSharp * 100);
            String label = (nextSharp <= 0.01f) ? "§cDesactivado" : ("§a" + pct + "%" + (pct == 60 ? " §6(DCS Sweetspot)" : ""));
            mc.player.displayClientMessage(
                Component.literal("§6[RV-Visor] §bNitidez CAS: " + label), true
            );
            return;
        }

        // Hotkey F9 para alternar Hardware MSAA en vivo
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F9) == GLFW.GLFW_PRESS) {
            lastAdjustTime = now;
            lensSettings.cycleMsaa();
            RenderSystem.recordRenderCall(renderEngine::ensureFramebuffers);

            int samples = lensSettings.getMsaaSamples();
            String label = (samples <= 1) ? "§cDesactivado" : ("§a" + samples + "x" + (samples == 2 ? " §6(Recomendado VR)" : ""));
            mc.player.displayClientMessage(
                Component.literal("§6[RV-Visor] §bAnti-Aliasing: MSAA " + label), true
            );
            return;
        }

        // Hotkeys para subir resolución / calidad (supersampling)
        boolean increase = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_KP_ADD) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_PAGE_UP) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F7) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_EQUAL) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_KP_EQUAL) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_BRACKET) == GLFW.GLFW_PRESS;

        // Hotkeys para bajar resolución / calidad
        boolean decrease = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_KP_SUBTRACT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_PAGE_DOWN) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F6) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_MINUS) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SLASH) == GLFW.GLFW_PRESS;

        if (!increase && !decrease) return;

        float current = lensSettings.getSupersamplingScale();
        float next = current + (increase ? STEP : -STEP);
        next = (float) (Math.round(Math.max(0.5f, Math.min(3.0f, next)) * 10.0) / 10.0);

        if (Math.abs(next - current) < 0.01f) return;

        lensSettings.setSupersamplingScale(next);
        lastAdjustTime = now;

        // Reasignar framebuffers con la nueva resolución en el render thread
        RenderSystem.recordRenderCall(renderEngine::ensureFramebuffers);

        // Feedback visual inmediato en pantalla y chat
        mc.player.displayClientMessage(
            Component.literal(String.format("§6[RV-Visor] §bCalidad VR: §e%.1fx §7(%dx%d px/ojo)",
                next,
                lensSettings.getEffectiveWidth(),
                lensSettings.getEffectiveHeight()
            )), true
        );
    }
}
