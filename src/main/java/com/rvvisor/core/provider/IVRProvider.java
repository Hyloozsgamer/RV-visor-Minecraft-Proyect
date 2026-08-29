package com.rvvisor.core.provider;

import com.rvvisor.core.data.VRTrackingContext;
import com.rvvisor.core.optics.LensSettings;

/**
 * Interface defining VR Hardware/Runtime Providers (OpenVR, OpenXR, Custom Direct Visor Bridge).
 */
public interface IVRProvider {
    /**
     * Initializes connection to the VR runtime/hardware.
     * @return true if successfully initialized
     */
    boolean initialize();

    /**
     * Shuts down the provider and releases allocated native resources.
     */
    void shutdown();

    /**
     * @return whether this provider is currently active and initialized.
     */
    boolean isInitialized();

    /**
     * Called at the start of a frame before polling poses.
     */
    default void beginFrame() {}

    /**
     * Polls the latest tracking poses and inputs from the VR hardware.
     */
    void pollPoses(VRTrackingContext context);

    /**
     * Submits a rendered eye texture (OpenGL handle) to the VR compositor.
     */
    void submitFrame(int eye, int textureId, int width, int height);

    /**
     * Submits both color and depth textures to the VR compositor (OpenXR Depth Layer / Oculus ASW 2.0).
     */
    default void submitFrameWithDepth(int eye, int colorTextureId, int depthTextureId, int width, int height, float nearZ, float farZ) {
        this.submitFrame(eye, colorTextureId, width, height);
    }

    /**
     * Called immediately after submitting both eye frames.
     */
    default void postSubmit() {}

    /**
     * Triggers haptic vibration on the specified hand controller.
     * @param hand 0 = Left, 1 = Right
     * @param durationSeconds Duration of vibration in seconds
     * @param frequency Vibration frequency in Hz
     * @param amplitude Vibration strength (0.0 to 1.0)
     */
    void triggerHaptic(int hand, float durationSeconds, float frequency, float amplitude);

    /**
     * @return recommended lens parameters provided by the hardware/runtime.
     */
    LensSettings getRecommendedLensSettings();

    /**
     * @return the human-readable name of this VR provider.
     */
    String getProviderName();
}
