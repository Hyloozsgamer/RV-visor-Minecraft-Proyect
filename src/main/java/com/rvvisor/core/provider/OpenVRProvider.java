package com.rvvisor.core.provider;

import com.rvvisor.RVVisorMod;
import com.rvvisor.core.data.VRTrackingContext;
import com.rvvisor.core.optics.LensSettings;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.openvr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.openvr.VR.*;
import static org.lwjgl.openvr.VRCompositor.*;
import static org.lwjgl.openvr.VRSystem.*;

/**
 * OpenVR / SteamVR hardware provider using LWJGL OpenVR bindings.
 * Handles SteamVR runtime connection, pose polling via WaitGetPoses,
 * lens tangent extraction, and frame submission with PostPresentHandoff.
 */
public class OpenVRProvider implements IVRProvider {
    private boolean initialized = false;
    private long lastConnectAttempt = 0;
    private final IntBuffer errorBuffer = MemoryUtil.memAllocInt(1);
    private TrackedDevicePose.Buffer trackedDevicePoses;
    private final Texture leftEyeTexture = Texture.calloc();
    private final Texture rightEyeTexture = Texture.calloc();
    private final VRTextureBounds textureBounds = VRTextureBounds.calloc();
    private final Matrix4f tempMatrix = new Matrix4f();
    private LensSettings hardwareLensSettings;

    @Override
    public boolean initialize() {
        if (this.initialized) return true;

        try {
            this.errorBuffer.put(0, 0);
            int token = VR_InitInternal(this.errorBuffer, EVRApplicationType_VRApplication_Scene);

            int error = this.errorBuffer.get(0);
            if (error != EVRInitError_VRInitError_None || token == 0) {
                RVVisorMod.LOGGER.warn("[RV-Visor] SteamVR / OpenVR not connected (Init error code: {}). Will retry when SteamVR is ready.", error);
                return false;
            }

            OpenVR.create(token);

            if (OpenVR.VRSystem == null || OpenVR.VRCompositor == null) {
                RVVisorMod.LOGGER.warn("[RV-Visor] OpenVR VRSystem or VRCompositor unavailable.");
                return false;
            }

            // Set tracking space to standing / roomscale
            VRCompositor_SetTrackingSpace(ETrackingUniverseOrigin_TrackingUniverseStanding);

            if (this.trackedDevicePoses == null) {
                this.trackedDevicePoses = TrackedDevicePose.calloc(k_unMaxTrackedDeviceCount);
            }

            // SteamVR compositor expects the texture as-is from OpenGL (Y=0 at bottom).
            // vMin=0 / vMax=1 maps the full texture without any vertical flip.
            this.textureBounds.uMin(0.0f).uMax(1.0f).vMin(0.0f).vMax(1.0f);

            this.leftEyeTexture.eType(VR.ETextureType_TextureType_OpenGL);
            this.leftEyeTexture.eColorSpace(VR.EColorSpace_ColorSpace_Gamma);

            this.rightEyeTexture.eType(VR.ETextureType_TextureType_OpenGL);
            this.rightEyeTexture.eColorSpace(VR.EColorSpace_ColorSpace_Gamma);

            // Fetch recommended settings from SteamVR
            this.hardwareLensSettings = this.queryRecommendedSettings();

            // Register SteamVR Application Manifest so RV-Visor appears as a game in SteamVR
            this.registerApplicationManifest();

            this.initialized = true;
            RVVisorMod.LOGGER.info("[RV-Visor] SteamVR / OpenVR Compositor initialized and connected successfully!");
            return true;
        } catch (Throwable t) {
            RVVisorMod.LOGGER.warn("[RV-Visor] OpenVR runtime connection pending: {}", t.getMessage());
            return false;
        }
    }

    private void registerApplicationManifest() {
        try {
            java.io.File manifestDir = new java.io.File("openvr");
            if (!manifestDir.exists()) manifestDir.mkdirs();
            java.io.File manifestFile = new java.io.File(manifestDir, "rvvisor.vrmanifest");
            
            try (java.io.InputStream in = RVVisorMod.class.getResourceAsStream("/assets/rvvisor/rvvisor.vrmanifest");
                 java.io.FileOutputStream out = new java.io.FileOutputStream(manifestFile)) {
                if (in != null) {
                    in.transferTo(out);
                }
            }

            if (manifestFile.exists()) {
                String fullPath = manifestFile.getCanonicalPath().replace('\\', '/');
                int err = org.lwjgl.openvr.VRApplications.VRApplications_AddApplicationManifest(fullPath, false);
                int pid = (int) ProcessHandle.current().pid();
                int idErr = org.lwjgl.openvr.VRApplications.VRApplications_IdentifyApplication(pid, "rvvisor.rvvisor_mod");
                RVVisorMod.LOGGER.info("[RV-Visor] Registered SteamVR Application Manifest (path: {}, addErr: {}, idErr: {}, PID: {})", fullPath, err, idErr, pid);
            }
        } catch (Throwable t) {
            RVVisorMod.LOGGER.warn("[RV-Visor] Could not register SteamVR manifest: {}", t.getMessage());
        }
    }

    private LensSettings queryRecommendedSettings() {
        LensSettings settings = new LensSettings();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer wBuf = stack.mallocInt(1);
            IntBuffer hBuf = stack.mallocInt(1);

            VRSystem_GetRecommendedRenderTargetSize(wBuf, hBuf);
            int recWidth = wBuf.get(0);
            int recHeight = hBuf.get(0);

            if (recWidth > 0 && recHeight > 0) {
                settings.setBaseWidth(recWidth);
                settings.setBaseHeight(recHeight);
                RVVisorMod.LOGGER.info("[RV-Visor] SteamVR Recommended Per-Eye Resolution: {}x{}", recWidth, recHeight);
            }

            // Raw FOV tangents
            FloatBuffer left = stack.mallocFloat(1);
            FloatBuffer right = stack.mallocFloat(1);
            FloatBuffer top = stack.mallocFloat(1);
            FloatBuffer bottom = stack.mallocFloat(1);

            VRSystem_GetProjectionRaw(EVREye_Eye_Left, left, right, top, bottom);
            settings.setFovTangents(LensSettings.EYE_LEFT, left.get(0), right.get(0), top.get(0), bottom.get(0));

            VRSystem_GetProjectionRaw(EVREye_Eye_Right, left, right, top, bottom);
            settings.setFovTangents(LensSettings.EYE_RIGHT, left.get(0), right.get(0), top.get(0), bottom.get(0));

            // Eye to head IPD calculation
            HmdMatrix34 eyeToHeadLeft = HmdMatrix34.calloc(stack);
            VRSystem_GetEyeToHeadTransform(EVREye_Eye_Left, eyeToHeadLeft);
            float eyeOffsetX = Math.abs(eyeToHeadLeft.m(3)); // Translation along X
            settings.setIpd(eyeOffsetX * 2.0f);
        } catch (Throwable t) {
            RVVisorMod.LOGGER.warn("[RV-Visor] Failed to query OpenVR lens settings, using defaults", t);
        }
        return settings;
    }

    @Override
    public void pollPoses(VRTrackingContext context) {
        if (!this.initialized) {
            long now = System.currentTimeMillis();
            if (now - this.lastConnectAttempt > 2000) {
                this.lastConnectAttempt = now;
                if (this.initialize()) {
                    RVVisorMod mod = RVVisorMod.getInstance();
                    if (mod != null && this.hardwareLensSettings != null) {
                        mod.getLensSettings().applyHardwareDefaults(this.hardwareLensSettings);
                        mod.getRenderEngine().ensureFramebuffers();
                    }
                }
            }
            if (!this.initialized) return;
        }

        context.beginNewFrame();

        // Wait for compositor vsync and get poses
        int error = VRCompositor_WaitGetPoses(this.trackedDevicePoses, null);
        if (error != EVRCompositorError_VRCompositorError_None) {
            return;
        }

        // HMD Pose
        TrackedDevicePose hmdPose = this.trackedDevicePoses.get(k_unTrackedDeviceIndex_Hmd);
        if (hmdPose.bPoseIsValid()) {
            this.convertHmdMatrixToMatrix4f(hmdPose.mDeviceToAbsoluteTracking(), this.tempMatrix);
            context.getHmdPose().setFromMatrix(this.tempMatrix);
            context.updateEyePoses(this.hardwareLensSettings != null ? this.hardwareLensSettings.getIpd() : 0.063f);
        }

        // Controllers
        int rightHandIdx = VRSystem_GetTrackedDeviceIndexForControllerRole(ETrackedControllerRole_TrackedControllerRole_RightHand);
        if (rightHandIdx != k_unTrackedDeviceIndexInvalid && rightHandIdx < k_unMaxTrackedDeviceCount) {
            TrackedDevicePose rPose = this.trackedDevicePoses.get(rightHandIdx);
            if (rPose.bPoseIsValid()) {
                this.convertHmdMatrixToMatrix4f(rPose.mDeviceToAbsoluteTracking(), this.tempMatrix);
                context.getRightHandPose().setFromMatrix(this.tempMatrix);
                context.getRightAimPose().setFromMatrix(this.tempMatrix);
            }
        }

        int leftHandIdx = VRSystem_GetTrackedDeviceIndexForControllerRole(ETrackedControllerRole_TrackedControllerRole_LeftHand);
        if (leftHandIdx != k_unTrackedDeviceIndexInvalid && leftHandIdx < k_unMaxTrackedDeviceCount) {
            TrackedDevicePose lPose = this.trackedDevicePoses.get(leftHandIdx);
            if (lPose.bPoseIsValid()) {
                this.convertHmdMatrixToMatrix4f(lPose.mDeviceToAbsoluteTracking(), this.tempMatrix);
                context.getLeftHandPose().setFromMatrix(this.tempMatrix);
                context.getLeftAimPose().setFromMatrix(this.tempMatrix);
            }
        }
    }

    @Override
    public void submitFrame(int eye, int textureId, int width, int height) {
        if (!this.initialized || textureId <= 0) return;

        if (eye == LensSettings.EYE_LEFT) {
            this.leftEyeTexture.handle(textureId);
            this.leftEyeTexture.eType(VR.ETextureType_TextureType_OpenGL);
            this.leftEyeTexture.eColorSpace(VR.EColorSpace_ColorSpace_Auto);
            int err = VRCompositor_Submit(EVREye_Eye_Left, this.leftEyeTexture, this.textureBounds, EVRSubmitFlags_Submit_Default);
            if (err != 0) {
                RVVisorMod.LOGGER.error("[RV-Visor] Submit Left Eye Error: {}", err);
            }
        } else {
            this.rightEyeTexture.handle(textureId);
            this.rightEyeTexture.eType(VR.ETextureType_TextureType_OpenGL);
            this.rightEyeTexture.eColorSpace(VR.EColorSpace_ColorSpace_Auto);
            int err = VRCompositor_Submit(EVREye_Eye_Right, this.rightEyeTexture, this.textureBounds, EVRSubmitFlags_Submit_Default);
            if (err != 0) {
                RVVisorMod.LOGGER.error("[RV-Visor] Submit Right Eye Error: {}", err);
            }
        }
    }

    @Override
    public void triggerHaptic(int hand, float durationSeconds, float frequency, float amplitude) {
        if (!this.initialized) return;
        int role = (hand == 0) ? ETrackedControllerRole_TrackedControllerRole_LeftHand : ETrackedControllerRole_TrackedControllerRole_RightHand;
        int controllerIndex = VRSystem_GetTrackedDeviceIndexForControllerRole(role);
        if (controllerIndex != k_unTrackedDeviceIndexInvalid) {
            short durationMicroseconds = (short) Math.min(Short.MAX_VALUE, Math.max(0, (int) (durationSeconds * 1_000_000.0f)));
            VRSystem_TriggerHapticPulse(controllerIndex, 0, durationMicroseconds);
        }
    }

    @Override
    public void postSubmit() {
        if (!this.initialized) return;
        VRCompositor_PostPresentHandoff();
        GL11.glFlush();
    }

    private void convertHmdMatrixToMatrix4f(HmdMatrix34 hmdMat, Matrix4f out) {
        out.set(
                hmdMat.m(0), hmdMat.m(4), hmdMat.m(8), 0.0f,
                hmdMat.m(1), hmdMat.m(5), hmdMat.m(9), 0.0f,
                hmdMat.m(2), hmdMat.m(6), hmdMat.m(10), 0.0f,
                hmdMat.m(3), hmdMat.m(7), hmdMat.m(11), 1.0f
        );
    }

    public Matrix4f getProjectionMatrix(int eye, float nearClip, float farClip) {
        if (!this.initialized) return null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int vrEye = (eye == LensSettings.EYE_LEFT) ? EVREye_Eye_Left : EVREye_Eye_Right;
            FloatBuffer left = stack.mallocFloat(1);
            FloatBuffer right = stack.mallocFloat(1);
            FloatBuffer top = stack.mallocFloat(1);
            FloatBuffer bottom = stack.mallocFloat(1);

            VRSystem_GetProjectionRaw(vrEye, left, right, top, bottom);

            float jomlLeft = left.get(0) * nearClip;
            float jomlRight = right.get(0) * nearClip;
            float jomlBottom = -bottom.get(0) * nearClip;
            float jomlTop = -top.get(0) * nearClip;

            return new Matrix4f().frustum(
                    jomlLeft,
                    jomlRight,
                    jomlBottom,
                    jomlTop,
                    nearClip,
                    farClip
            );
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public LensSettings getRecommendedLensSettings() {
        return this.hardwareLensSettings;
    }

    @Override
    public String getProviderName() {
        return "OpenVR / SteamVR";
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public void shutdown() {
        if (!this.initialized) return;
        this.initialized = false;
        try {
            VR_ShutdownInternal();
        } catch (Throwable ignore) {}
        if (this.trackedDevicePoses != null) {
            this.trackedDevicePoses.free();
        }
        this.leftEyeTexture.free();
        this.rightEyeTexture.free();
        this.textureBounds.free();
        MemoryUtil.memFree(this.errorBuffer);
        RVVisorMod.LOGGER.info("[RV-Visor] OpenVR shut down.");
    }
}
