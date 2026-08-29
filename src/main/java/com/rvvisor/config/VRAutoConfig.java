package com.rvvisor.config;

import com.rvvisor.RVVisorMod;
import com.rvvisor.core.optics.LensSettings;
import com.rvvisor.core.provider.IVRProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Automatic VR Pre-World Auto-Configuration Engine.
 * Automatically detects VR hardware (SteamVR / OpenVR / OpenXR), applies optimal
 * hardware parameters, loads persistent user config from rvvisor.json, and configures Minecraft
 * client options (no bobbing, unlimited FPS, vsync off) before entering any world.
 */
public class VRAutoConfig {
    private static boolean autoConfigApplied = false;
    private static String statusMessage = "Iniciando RV-Visor...";
    private static boolean vrHardwareConnected = false;

    public static synchronized void runAutoConfig(Minecraft mc) {
        if (autoConfigApplied) return;

        try {
            RVVisorMod mod = RVVisorMod.getInstance();
            if (mod == null) return;

            IVRProvider provider = mod.getVrProvider();
            if (provider != null && !provider.isInitialized()) {
                provider.initialize();
            }

            // 1. Optimización de opciones de Minecraft para VR
            if (mc != null && mc.options != null) {
                // Desactiva el balanceo de cámara para evitar mareos en VR
                mc.options.bobView().set(false);

                // Desactiva V-Sync del monitor para que SteamVR gestione el refresco a 90/120 Hz
                mc.options.enableVsync().set(false);

                // FPS Ilimitados para evitar cuello de botella con el compositor de SteamVR
                mc.options.framerateLimit().set(260);
            }

            // 2. Calibración óptica de hardware
            if (provider != null && provider.isInitialized()) {
                vrHardwareConnected = true;
                LensSettings settings = mod.getLensSettings();
                if (settings != null) {
                    LensSettings recommended = provider.getRecommendedLensSettings();
                    if (recommended != null) {
                        settings.applyHardwareDefaults(recommended);
                    }

                    // Valores por defecto: MSAA 2x, CAS Sharpness OFF (0.0), Escala 1.0x
                    settings.setMsaaSamples(2);
                    settings.setSharpness(0.0f); // Default OFF
                    settings.setSupersamplingScale(1.0f);

                    // Carga configuración persistente guardada por el usuario en config/rvvisor.json
                    VRConfigFile.load(settings);

                    if (mod.getRenderEngine() != null) {
                        mod.getRenderEngine().ensureFramebuffers();
                    }
                }

                statusMessage = "🟢 VR Conectado: " + provider.getProviderName() + " (Auto-Configurado 90 FPS / MSAA 2x)";
                RVVisorMod.LOGGER.info("[RV-Visor] {}", statusMessage);
            } else {
                vrHardwareConnected = false;
                statusMessage = "🟡 Esperando conexión con SteamVR / Visor...";
                RVVisorMod.LOGGER.info("[RV-Visor] VR provider pending connection during auto-config.");
            }

            autoConfigApplied = true;
        } catch (Throwable t) {
            RVVisorMod.LOGGER.error("[RV-Visor] Error during VR auto-configuration", t);
            statusMessage = "🔴 Error en auto-configuración VR";
        }
    }

    public static void forceReapply(Minecraft mc) {
        autoConfigApplied = false;
        runAutoConfig(mc);
    }

    public static boolean isVrHardwareConnected() {
        return vrHardwareConnected;
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static Component getStatusComponent() {
        return Component.literal(statusMessage);
    }
}
