package com.rvvisor.core.optics;

import com.rvvisor.RVVisorMod;
import com.rvvisor.render.VREyeFramebuffer;
import org.joml.Matrix4f;

/**
 * High-performance Lens Resolution & Optical Configuration Engine.
 * Supports per-eye resolutions, dynamic supersampling (DRS for 90Hz), IPD adjustment,
 * and asymmetric frustum projection matching OpenVR / SteamVR optics.
 */
public class LensSettings {
    public static final int EYE_LEFT = 0;
    public static final int EYE_RIGHT = 1;

    // Fallback resolution: Meta Quest 3 via Meta Link @ 90 Hz (per eye)
    // At runtime, applyHardwareDefaults() overwrites this with SteamVR's recommended size.
    private static final int BASE_WIDTH  = 2784;
    private static final int BASE_HEIGHT = 1504;

    private int baseWidth  = BASE_WIDTH;
    private int baseHeight = BASE_HEIGHT;

    // Dynamic resolution scaling for 90 Hz target
    private static final float MIN_SS    = 0.80f; // ~2227x1203 — emergency floor
    private static final float MAX_SS    = 1.10f; // ~3062x1654 — above-native sharpening
    private static final float TARGET_SS = 1.00f; // 1:1 native

    private float supersamplingScale = TARGET_SS;
    private boolean dynamicResolutionEnabled = false;

    // Smoothing history for dynamic resolution
    private final float[] frameTimeHistory = new float[30];
    private int historyIndex = 0;
    private long lastAdjustTime = 0;

    // 90Hz = 11.11ms per frame
    private static final float TARGET_FRAME_TIME_MS = 11.11f;
    private static final float LOWER_THRESHOLD_MS = 11.7f; // < 85 fps -> step down
    private static final float UPPER_THRESHOLD_MS = 10.5f; // > 95 fps -> step up

    // AMD FidelityFX CAS Sharpness (0.40 = sweet spot at 1.2x supersampling to prevent shimmering)
    private float sharpness = 0.40f;

    // Hardware MSAA Samples (1 = Direct texture render without MSAA depth blit issues)
    private int msaaSamples = 1;

    // Interpupillary Distance (IPD) in meters (default: 63mm = 0.063m)
    private float ipd = 0.063f;

    // Asymmetric FOV tangents (OpenXR format: Up is positive, Down is negative)
    private float leftEyeFovLeft = -1.097f;
    private float leftEyeFovRight = 0.942f;
    private float leftEyeFovUp = 0.990f;
    private float leftEyeFovDown = -1.040f;

    private float rightEyeFovLeft = -0.942f;
    private float rightEyeFovRight = 1.097f;
    private float rightEyeFovUp = 0.990f;
    private float rightEyeFovDown = -1.040f;
    private boolean openXrFovConvention = true;

    // Lens distortion coefficients
    private float distortionK1 = 0.0f;
    private float distortionK2 = 0.0f;

    // Near and far clipping planes
    private float nearClip = 0.02f;
    private float farClip = 1000.0f;

    public void loadDefaults() {
        this.baseWidth = BASE_WIDTH;
        this.baseHeight = BASE_HEIGHT;
        this.supersamplingScale = TARGET_SS;
        this.msaaSamples = 1;
        this.sharpness = 0.40f;
        this.ipd = 0.063f;
        this.openXrFovConvention = true;

        this.leftEyeFovLeft = -1.097f;
        this.leftEyeFovRight = 0.942f;
        this.leftEyeFovUp = 0.990f;
        this.leftEyeFovDown = -1.040f;

        this.rightEyeFovLeft = -0.942f;
        this.rightEyeFovRight = 1.097f;
        this.rightEyeFovUp = 0.990f;
        this.rightEyeFovDown = -1.040f;
    }

    public void applyHardwareDefaults(LensSettings hardware) {
        if (hardware == null) return;
        if (hardware.baseWidth > 0) this.baseWidth = hardware.baseWidth;
        if (hardware.baseHeight > 0) this.baseHeight = hardware.baseHeight;
        if (hardware.ipd > 0.04f && hardware.ipd < 0.09f) this.ipd = hardware.ipd;
        this.leftEyeFovLeft = hardware.leftEyeFovLeft;
        this.leftEyeFovRight = hardware.leftEyeFovRight;
        this.leftEyeFovUp = hardware.leftEyeFovUp;
        this.leftEyeFovDown = hardware.leftEyeFovDown;
        this.rightEyeFovLeft = hardware.rightEyeFovLeft;
        this.rightEyeFovRight = hardware.rightEyeFovRight;
        this.rightEyeFovUp = hardware.rightEyeFovUp;
        this.rightEyeFovDown = hardware.rightEyeFovDown;
        this.openXrFovConvention = hardware.openXrFovConvention;
    }

    public int getEffectiveWidth() {
        return Math.max(256, Math.round(this.baseWidth * this.supersamplingScale));
    }

    public int getEffectiveHeight() {
        return Math.max(256, Math.round(this.baseHeight * this.supersamplingScale));
    }

    public boolean needsResize(VREyeFramebuffer fbo) {
        if (fbo == null) return true;
        return fbo.getWidth() != this.getEffectiveWidth() || fbo.getHeight() != this.getEffectiveHeight();
    }

    /**
     * Dynamic Resolution Scaling (DRS) update called at the end of each frame.
     * Smoothly steps resolution up or down every 500ms based on average frame timing.
     */
    public void updateDynamicResolution(float frameTimeMs) {
        if (!this.dynamicResolutionEnabled) return;

        this.frameTimeHistory[this.historyIndex % this.frameTimeHistory.length] = frameTimeMs;
        this.historyIndex++;

        long now = System.currentTimeMillis();
        if (now - this.lastAdjustTime < 500) return;
        this.lastAdjustTime = now;

        float avgFrameTime = this.getAverageFrameTime();

        if (avgFrameTime > LOWER_THRESHOLD_MS && this.supersamplingScale > MIN_SS) {
            this.supersamplingScale = Math.max(MIN_SS, (float) (Math.round((this.supersamplingScale - 0.05f) * 100.0) / 100.0));
            RVVisorMod.LOGGER.debug("[RV-Visor] DynRes Scale DOWN -> {}x{} (scale: {}x, avg {}ms)",
                    this.getEffectiveWidth(), this.getEffectiveHeight(), this.supersamplingScale, String.format("%.2f", avgFrameTime));
        } else if (avgFrameTime < UPPER_THRESHOLD_MS && this.supersamplingScale < MAX_SS) {
            this.supersamplingScale = Math.min(MAX_SS, (float) (Math.round((this.supersamplingScale + 0.02f) * 100.0) / 100.0));
            RVVisorMod.LOGGER.debug("[RV-Visor] DynRes Scale UP -> {}x{} (scale: {}x, avg {}ms)",
                    this.getEffectiveWidth(), this.getEffectiveHeight(), this.supersamplingScale, String.format("%.2f", avgFrameTime));
        }
    }

    private float getAverageFrameTime() {
        float sum = 0f;
        int count = 0;
        for (float f : this.frameTimeHistory) {
            if (f > 0f) {
                sum += f;
                count++;
            }
        }
        return count == 0 ? TARGET_FRAME_TIME_MS : sum / count;
    }

    public int getBaseWidth() {
        return this.baseWidth;
    }

    public void setBaseWidth(int baseWidth) {
        this.baseWidth = Math.max(256, baseWidth);
    }

    public int getBaseHeight() {
        return this.baseHeight;
    }

    public void setBaseHeight(int baseHeight) {
        this.baseHeight = Math.max(256, baseHeight);
    }

    public float getSupersamplingScale() {
        return this.supersamplingScale;
    }

    public void setSupersamplingScale(float scale) {
        this.supersamplingScale = Math.max(0.25f, Math.min(3.0f, scale));
    }

    public void stepSupersamplingUp() {
        this.setSupersamplingScale(Math.min(3.0f, (float) (Math.round((this.supersamplingScale + 0.1f) * 10.0) / 10.0)));
    }

    public void stepSupersamplingDown() {
        this.setSupersamplingScale(Math.max(0.5f, (float) (Math.round((this.supersamplingScale - 0.1f) * 10.0) / 10.0)));
    }

    public boolean isDynamicResolutionEnabled() {
        return this.dynamicResolutionEnabled;
    }

    public void setDynamicResolutionEnabled(boolean enabled) {
        this.dynamicResolutionEnabled = enabled;
    }

    public float getSharpness() {
        return this.sharpness;
    }

    public void setSharpness(float sharpness) {
        this.sharpness = Math.max(0.0f, Math.min(1.0f, sharpness));
    }

    public void stepSharpnessUp() {
        this.setSharpness(Math.min(1.0f, (float) (Math.round((this.sharpness + 0.1f) * 10.0) / 10.0)));
    }

    public void stepSharpnessDown() {
        this.setSharpness(Math.max(0.0f, (float) (Math.round((this.sharpness - 0.1f) * 10.0) / 10.0)));
    }

    public int getMsaaSamples() {
        return this.msaaSamples;
    }

    public void setMsaaSamples(int samples) {
        if (samples <= 1) this.msaaSamples = 1;
        else if (samples <= 2) this.msaaSamples = 2;
        else if (samples <= 4) this.msaaSamples = 4;
        else this.msaaSamples = 8;
    }

    public void cycleMsaa() {
        if (this.msaaSamples == 1) this.setMsaaSamples(2);
        else if (this.msaaSamples == 2) this.setMsaaSamples(4);
        else if (this.msaaSamples == 4) this.setMsaaSamples(8);
        else this.setMsaaSamples(1);
    }

    public float getIpd() {
        return this.ipd;
    }

    public void setIpd(float ipdMeters) {
        this.ipd = Math.max(0.045f, Math.min(0.085f, ipdMeters));
    }

    public void setCustomResolution(int width, int height, float supersampling) {
        this.setBaseWidth(width);
        this.setBaseHeight(height);
        this.setSupersamplingScale(supersampling);
    }

    public void setFovTangents(int eye, float left, float right, float top, float bottom) {
        this.openXrFovConvention = false;
        this.setFovTangentsInternal(eye, left, right, top, bottom);
    }

    public void setOpenXrFovTangents(int eye, float left, float right, float up, float down) {
        this.openXrFovConvention = true;
        this.setFovTangentsInternal(eye, left, right, up, down);
    }

    private void setFovTangentsInternal(int eye, float left, float right, float top, float bottom) {
        if (eye == EYE_LEFT) {
            this.leftEyeFovLeft = left;
            this.leftEyeFovRight = right;
            this.leftEyeFovUp = top;
            this.leftEyeFovDown = bottom;
        } else {
            this.rightEyeFovLeft = left;
            this.rightEyeFovRight = right;
            this.rightEyeFovUp = top;
            this.rightEyeFovDown = bottom;
        }
    }

    /**
     * Calculates the asymmetric frustum projection matrix for the specified eye.
     * Matches OpenVR/SteamVR projection tangent bounds.
     */
    public Matrix4f calculateProjectionMatrix(int eye, float zNear, float zFar) {
        float l = (eye == EYE_LEFT) ? this.leftEyeFovLeft : this.rightEyeFovLeft;
        float r = (eye == EYE_LEFT) ? this.leftEyeFovRight : this.rightEyeFovRight;
        float top = (eye == EYE_LEFT) ? this.leftEyeFovUp : this.rightEyeFovUp;
        float bottom = (eye == EYE_LEFT) ? this.leftEyeFovDown : this.rightEyeFovDown;

        float jomlLeft = l * zNear;
        float jomlRight = r * zNear;
        float jomlBottom = this.openXrFovConvention ? bottom * zNear : top * zNear;
        float jomlTop = this.openXrFovConvention ? top * zNear : bottom * zNear;

        return new Matrix4f().frustum(
                jomlLeft,
                jomlRight,
                jomlBottom,
                jomlTop,
                zNear,
                zFar,
                false
        );
    }

    public float getNearClip() {
        return this.nearClip;
    }

    public void setNearClip(float nearClip) {
        this.nearClip = nearClip;
    }

    public float getFarClip() {
        return this.farClip;
    }

    public void setFarClip(float farClip) {
        this.farClip = farClip;
    }

    public float getDistortionK1() {
        return this.distortionK1;
    }

    public float getDistortionK2() {
        return this.distortionK2;
    }
}
