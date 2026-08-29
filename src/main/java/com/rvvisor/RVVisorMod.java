package com.rvvisor;

import com.rvvisor.core.data.VRTrackingContext;
import com.rvvisor.core.input.VRControllerInput;
import com.rvvisor.core.optics.LensSettings;
import com.rvvisor.core.provider.CustomVisorBridge;
import com.rvvisor.core.provider.IVRProvider;
import com.rvvisor.core.provider.OpenVRProvider;
import com.rvvisor.render.VRRenderEngine;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RVVisorMod implements ClientModInitializer {
    public static final String MOD_ID = "rvvisor";
    public static final String MOD_NAME = "RV-Visor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static RVVisorMod instance;
    private IVRProvider vrProvider;
    private final VRTrackingContext trackingContext = new VRTrackingContext();
    private LensSettings lensSettings = new LensSettings();
    private VRRenderEngine renderEngine;
    private final VRControllerInput controllerInput = new VRControllerInput();
    private boolean vrActive = true;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("[RV-Visor] Initializing Next-Gen Virtual Reality Mod for Minecraft...");

        // Initialize Lens and Optics settings
        this.lensSettings.loadDefaults();

        // Use the native OpenVR / SteamVR provider with auto-reconnection
        try {
            LOGGER.info("[RV-Visor] Initializing OpenVR / SteamVR provider...");
            OpenVRProvider openVR = new OpenVRProvider();
            openVR.initialize();
            this.vrProvider = openVR;
            LOGGER.info("[RV-Visor] Active VR provider: {} (State: {})", this.vrProvider.getProviderName(), openVR.isInitialized() ? "Connected" : "Waiting for SteamVR");
        } catch (Throwable t) {
            LOGGER.error("[RV-Visor] Failed during provider selection, falling back to Custom Visor Bridge", t);
            this.vrProvider = new CustomVisorBridge();
            this.vrProvider.initialize();
        }

        // Apply recommended lens settings from hardware provider if available
        LensSettings recommended = this.vrProvider.getRecommendedLensSettings();
        if (recommended != null) {
            this.lensSettings.applyHardwareDefaults(recommended);
        }

        // Initialize Render Engine
        this.renderEngine = new VRRenderEngine(this.vrProvider, this.trackingContext, this.lensSettings);
        LOGGER.info("[RV-Visor] Render Engine initialized at resolution {}x{} per eye (Scale: {}x)",
                this.lensSettings.getEffectiveWidth(), this.lensSettings.getEffectiveHeight(),
                this.lensSettings.getSupersamplingScale());
    }

    public static RVVisorMod getInstance() {
        return instance;
    }

    public IVRProvider getVrProvider() {
        return this.vrProvider;
    }

    public VRTrackingContext getTrackingContext() {
        return this.trackingContext;
    }

    public LensSettings getLensSettings() {
        return this.lensSettings;
    }

    public VRRenderEngine getRenderEngine() {
        return this.renderEngine;
    }

    public VRControllerInput getControllerInput() {
        return this.controllerInput;
    }

    public boolean isVrActive() {
        return this.vrActive && this.vrProvider != null && this.vrProvider.isInitialized();
    }

    public void setVrActive(boolean active) {
        this.vrActive = active;
        LOGGER.info("[RV-Visor] VR Mode set to: {}", active);
    }
}
