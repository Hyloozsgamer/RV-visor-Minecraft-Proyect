package com.rvvisor.mixin;

import com.rvvisor.RVVisorMod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openvr.OpenVR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.LongFunction;

import static org.lwjgl.openvr.VR.*;

/**
 * OpenVR LWJGL Mixin:
 * Safe interface loading to ensure compatibility with SteamVR, Virtual Desktop,
 * Oculus OpenVR runtime, and OpenComposite without crashing on missing non-essential tables.
 */
@Mixin(value = OpenVR.class, remap = false)
public abstract class OpenVRMixin {

    @Shadow
    @Nullable
    public static OpenVR.IVRSystem VRSystem;

    @Shadow
    @Nullable
    public static OpenVR.IVRChaperone VRChaperone;

    @Shadow
    @Nullable
    public static OpenVR.IVRChaperoneSetup VRChaperoneSetup;

    @Shadow
    @Nullable
    public static OpenVR.IVRCompositor VRCompositor;

    @Shadow
    @Nullable
    public static OpenVR.IVROverlay VROverlay;

    @Shadow
    @Nullable
    public static OpenVR.IVRRenderModels VRRenderModels;

    @Shadow
    @Nullable
    public static OpenVR.IVRExtendedDisplay VRExtendedDisplay;

    @Shadow
    @Nullable
    public static OpenVR.IVRSettings VRSettings;

    @Shadow
    @Nullable
    public static OpenVR.IVRApplications VRApplications;

    @Shadow
    @Nullable
    public static OpenVR.IVRScreenshots VRScreenshots;

    @Shadow
    @Nullable
    public static OpenVR.IVRInput VRInput;

    @Shadow
    private static int token;

    @Shadow
    @Nullable
    private static <T> T getGenericInterface(String interfaceNameVersion, LongFunction<T> supplier) {
        return null;
    }

    /**
     * @reason Safe interface instantiation for Virtual Desktop and OpenVR runtimes.
     * @author RV-Visor Team
     */
    @Overwrite
    public static void create(int tok) {
        token = tok;

        VRSystem = getGenericInterface(IVRSystem_Version, OpenVR.IVRSystem::new);
        VRChaperone = getGenericInterface(IVRChaperone_Version, OpenVR.IVRChaperone::new);
        VRChaperoneSetup = getGenericInterface(IVRChaperoneSetup_Version, OpenVR.IVRChaperoneSetup::new);
        VRCompositor = getGenericInterface(IVRCompositor_Version, OpenVR.IVRCompositor::new);
        if (VRCompositor == null) {
            RVVisorMod.LOGGER.warn("[RV-Visor] OpenVR '{}' failed to load, falling back to IVRCompositor_026", IVRCompositor_Version);
            VRCompositor = getGenericInterface("IVRCompositor_026", OpenVR.IVRCompositor::new);
        }
        VROverlay = getGenericInterface(IVROverlay_Version, OpenVR.IVROverlay::new);
        VRRenderModels = getGenericInterface(IVRRenderModels_Version, OpenVR.IVRRenderModels::new);
        VRExtendedDisplay = getGenericInterface(IVRExtendedDisplay_Version, OpenVR.IVRExtendedDisplay::new);
        VRSettings = getGenericInterface(IVRSettings_Version, OpenVR.IVRSettings::new);
        VRApplications = getGenericInterface(IVRApplications_Version, OpenVR.IVRApplications::new);
        VRScreenshots = getGenericInterface(IVRScreenshots_Version, OpenVR.IVRScreenshots::new);
        VRInput = getGenericInterface(IVRInput_Version, OpenVR.IVRInput::new);
    }
}
