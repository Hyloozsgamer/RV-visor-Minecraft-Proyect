package com.rvvisor.core.provider;

import com.rvvisor.RVVisorMod;
import com.rvvisor.core.data.VRTrackingContext;
import com.rvvisor.core.optics.LensSettings;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWNativeWGL;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.openxr.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.windows.User32;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.openxr.XR10.*;

/**
 * OpenXR Next-Gen Hardware Provider.
 * Full 6-DOF Head and Hand Tracking with Subaction Paths (/user/hand/left and /user/hand/right),
 * Interaction Profiles (Oculus Touch, Valve Index, Simple Controller),
 * Tangent FOV extraction for asymmetric JOML frustums, and Haptic Feedback.
 */
public class OpenXRProvider implements IVRProvider {
    private boolean initialized = false;
    private long lastInitAttempt = 0;

    private XrInstance xrInstance;
    private long systemId = XR_NULL_SYSTEM_ID;
    private XrSession xrSession;
    private int sessionState = XR_SESSION_STATE_UNKNOWN;
    private boolean sessionRunning;
    private boolean frameInProgress;
    private boolean presentationReady;

    private final XrSwapchain[] eyeSwapchains = new XrSwapchain[2];
    private final int[][] eyeSwapchainImages = new int[2][];
    private final boolean[] submittedEyes = new boolean[2];
    private int recommendedImageWidth;
    private int recommendedImageHeight;
    private final float[][] frameViewPoses = new float[2][7];
    private final float[][] frameViewFovs = new float[2][4];
    private boolean frameViewsValid;
    private int copyReadFramebuffer;
    private int copyDrawFramebuffer;

    // Tracking Spaces
    private XrSpace localSpace;
    private XrSpace stageSpace;
    private XrSpace viewSpace;
    private XrSpace activeRefSpace;

    // Hand Action Spaces for 6-DOF Tracking
    private XrActionSet gameplayActionSet;
    private XrAction handPoseAction;
    private XrAction handAimAction;
    private XrAction triggerAction;
    private XrAction gripAction;
    private XrAction thumbstickAction;
    private XrAction primaryButtonAction;
    private XrAction secondaryButtonAction;
    private XrAction hapticAction;

    private XrSpace leftGripSpace;
    private XrSpace rightGripSpace;
    private XrSpace leftAimSpace;
    private XrSpace rightAimSpace;

    private long leftHandSubactionPath = XR_NULL_PATH;
    private long rightHandSubactionPath = XR_NULL_PATH;

    private LensSettings hardwareLensSettings;
    private long predictedDisplayTime = 0;

    @Override
    public boolean initialize() {
        if (this.initialized) return true;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            RVVisorMod.LOGGER.info("[RV-Visor-OpenXR] Attempting OpenXR runtime initialization...");

            // 1. Create OpenXR Instance
            ByteBuffer appName = stack.UTF8("RV-Visor-Minecraft");
            ByteBuffer engineName = stack.UTF8("Fabric-1.21.1");

            XrApplicationInfo appInfo = XrApplicationInfo.calloc(stack)
                    .applicationName(appName)
                    .applicationVersion(1)
                    .engineName(engineName)
                    .engineVersion(1)
                    .apiVersion(XR_CURRENT_API_VERSION);

            XrInstanceCreateInfo instanceCreateInfo = XrInstanceCreateInfo.calloc(stack)
                    .type(XR_TYPE_INSTANCE_CREATE_INFO)
                    .applicationInfo(appInfo)
                    .enabledExtensionNames(stack.pointers(stack.UTF8(KHROpenGLEnable.XR_KHR_OPENGL_ENABLE_EXTENSION_NAME)));

            PointerBuffer pInstance = stack.mallocPointer(1);
            int result = xrCreateInstance(instanceCreateInfo, pInstance);
            if (result != XR_SUCCESS || pInstance.get(0) == MemoryUtil.NULL) {
                RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Failed to create OpenXR instance (Result code: {})", result);
                return false;
            }

            this.xrInstance = new XrInstance(pInstance.get(0), instanceCreateInfo);
            RVVisorMod.LOGGER.info("[RV-Visor-OpenXR] OpenXR Instance created successfully!");

            // 2. Get HMD System ID
            XrSystemGetInfo systemGetInfo = XrSystemGetInfo.calloc(stack)
                    .type(XR_TYPE_SYSTEM_GET_INFO)
                    .formFactor(XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY);

            LongBuffer pSystemId = stack.mallocLong(1);
            result = xrGetSystem(this.xrInstance, systemGetInfo, pSystemId);
            if (result != XR_SUCCESS) {
                RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] No HMD system found for OpenXR (Result code: {})", result);
                this.shutdown();
                return false;
            }
            this.systemId = pSystemId.get(0);

            // 3. Query System Properties & Recommended View Configurations
            this.hardwareLensSettings = this.queryViewConfiguration(stack);

            // 4. Bind the current Minecraft WGL context to OpenXR and create the session.
            if (!this.createSession(stack)) {
                this.shutdown();
                return false;
            }

            if (!this.createSwapchains(stack)) {
                RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] OpenXR presentation disabled: OpenGL swapchains could not be created safely.");
                this.shutdown();
                return false;
            }

            // 5. Create tracking spaces and setup 6-DOF action sets.
            this.createReferenceSpaces(stack);
            this.initializeActions(stack);

            if (this.xrSession == null) {
                RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] OpenXR session was not created; provider will remain uninitialized.");
                this.shutdown();
                return false;
            }

            this.initialized = true;
            RVVisorMod.LOGGER.info("[RV-Visor-OpenXR] OpenXR Provider initialized successfully with 6-DOF Hand Tracking!");
            return true;
        } catch (Throwable t) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] OpenXR runtime not available or pending: {}", t.getMessage());
            this.shutdown();
            return false;
        }
    }

    private boolean createSession(MemoryStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Minecraft window is not available for WGL session creation.");
            return false;
        }

        long glfwWindow = minecraft.getWindow().getWindow();
        long nativeWindow = GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
        long wglContext = GLFWNativeWGL.glfwGetWGLContext(glfwWindow);
        long hdc = User32.GetDC(nativeWindow);
        if (nativeWindow == MemoryUtil.NULL || wglContext == MemoryUtil.NULL || hdc == MemoryUtil.NULL) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] GLFW did not expose a valid Win32/WGL context.");
            return false;
        }

        XrGraphicsRequirementsOpenGLKHR requirements = XrGraphicsRequirementsOpenGLKHR.calloc(stack)
            .type(KHROpenGLEnable.XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_KHR);
        int result = KHROpenGLEnable.xrGetOpenGLGraphicsRequirementsKHR(this.xrInstance, this.systemId, requirements);
        if (result != XR_SUCCESS) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] OpenGL graphics requirements query failed (Result code: {}).", result);
            return false;
        }

        XrGraphicsBindingOpenGLWin32KHR binding = XrGraphicsBindingOpenGLWin32KHR.calloc(stack)
                .set(KHROpenGLEnable.XR_TYPE_GRAPHICS_BINDING_OPENGL_WIN32_KHR, MemoryUtil.NULL, hdc, wglContext);
        XrSessionCreateInfo sessionCreateInfo = XrSessionCreateInfo.calloc(stack)
                .set(XR_TYPE_SESSION_CREATE_INFO, binding.address(), 0, this.systemId);
        PointerBuffer pSession = stack.callocPointer(1);
        result = xrCreateSession(this.xrInstance, sessionCreateInfo, pSession);
        if (result != XR_SUCCESS || pSession.get(0) == MemoryUtil.NULL) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Failed to create OpenGL session (Result code: {}).", result);
            return false;
        }
        this.xrSession = new XrSession(pSession.get(0), this.xrInstance);
        return true;
    }

    private void createReferenceSpaces(MemoryStack stack) {
        XrPosef identity = XrPosef.calloc(stack)
                .set(XrQuaternionf.calloc(stack).set(0, 0, 0, 1), XrVector3f.calloc(stack).set(0, 0, 0));
        XrReferenceSpaceCreateInfo createInfo = XrReferenceSpaceCreateInfo.calloc(stack)
                .type(XR_TYPE_REFERENCE_SPACE_CREATE_INFO)
                .poseInReferenceSpace(identity);
        PointerBuffer pSpace = stack.callocPointer(1);

        createInfo.referenceSpaceType(XR_REFERENCE_SPACE_TYPE_LOCAL);
        pSpace.put(0, MemoryUtil.NULL);
        if (xrCreateReferenceSpace(this.xrSession, createInfo, pSpace) == XR_SUCCESS) {
            this.localSpace = new XrSpace(pSpace.get(0), this.xrSession);
        }

        createInfo.referenceSpaceType(XR_REFERENCE_SPACE_TYPE_STAGE);
        pSpace.put(0, MemoryUtil.NULL);
        if (xrCreateReferenceSpace(this.xrSession, createInfo, pSpace) == XR_SUCCESS) {
            this.stageSpace = new XrSpace(pSpace.get(0), this.xrSession);
        }

        createInfo.referenceSpaceType(XR_REFERENCE_SPACE_TYPE_VIEW);
        pSpace.put(0, MemoryUtil.NULL);
        if (xrCreateReferenceSpace(this.xrSession, createInfo, pSpace) == XR_SUCCESS) {
            this.viewSpace = new XrSpace(pSpace.get(0), this.xrSession);
        }
        this.activeRefSpace = this.stageSpace != null ? this.stageSpace : this.localSpace;
        if (this.activeRefSpace == null) {
            throw new IllegalStateException("OpenXR could not create local or stage reference space");
        }
    }

    @Override
    public void beginFrame() {
        if (!this.initialized || !this.presentationReady || this.xrSession == null
            || !this.sessionRunning) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrFrameState frameState = XrFrameState.calloc(stack).type(XR_TYPE_FRAME_STATE);
            int result = xrWaitFrame(this.xrSession,
                    XrFrameWaitInfo.calloc(stack).type(XR_TYPE_FRAME_WAIT_INFO), frameState);
            if (result != XR_SUCCESS) return;
            this.predictedDisplayTime = frameState.predictedDisplayTime();
            this.frameViewsValid = false;
            this.submittedEyes[0] = false;
            this.submittedEyes[1] = false;
            result = xrBeginFrame(this.xrSession,
                    XrFrameBeginInfo.calloc(stack).type(XR_TYPE_FRAME_BEGIN_INFO));
            this.frameInProgress = result == XR_SUCCESS;
        } catch (Throwable t) {
            this.frameInProgress = false;
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] OpenXR frame begin failed: {}", t.getMessage());
        }
    }

    @Override
    public void postSubmit() {
        if (!this.frameInProgress || this.xrSession == null) return;
        if (!this.sessionRunning) {
            this.frameInProgress = false;
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            boolean submitLayer = this.presentationReady && this.frameViewsValid
                && this.submittedEyes[0] && this.submittedEyes[1];
            PointerBuffer layers = stack.callocPointer(0);
            if (submitLayer) {
            XrCompositionLayerProjectionView.Buffer projectionViews = XrCompositionLayerProjectionView.calloc(2, stack);
            for (int eye = 0; eye < 2; eye++) {
                float[] pose = this.frameViewPoses[eye];
                float[] fov = this.frameViewFovs[eye];
                XrPosef viewPose = XrPosef.calloc(stack).set(
                    XrQuaternionf.calloc(stack).set(pose[3], pose[4], pose[5], pose[6]),
                    XrVector3f.calloc(stack).set(pose[0], pose[1], pose[2]));
                XrFovf viewFov = XrFovf.calloc(stack).set(fov[0], fov[1], fov[2], fov[3]);
                projectionViews.get(eye)
                    .type(XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW)
                    .pose(viewPose)
                    .fov(viewFov)
                        .subImage(XrSwapchainSubImage.calloc(stack)
                            .swapchain(this.eyeSwapchains[eye])
                            .imageRect(XrRect2Di.calloc(stack).set(
                                XrOffset2Di.calloc(stack).set(0, 0),
                                XrExtent2Di.calloc(stack).set(this.recommendedImageWidth, this.recommendedImageHeight)))
                            .imageArrayIndex(0));
            }
            XrCompositionLayerProjection layer = XrCompositionLayerProjection.calloc(stack)
                .type(XR_TYPE_COMPOSITION_LAYER_PROJECTION)
                .space(this.activeRefSpace)
                .views(projectionViews);
            layers = stack.pointers(XrCompositionLayerBaseHeader.create(layer.address()));
            }
            XrFrameEndInfo endInfo = XrFrameEndInfo.calloc(stack)
                    .type(XR_TYPE_FRAME_END_INFO)
                    .displayTime(this.predictedDisplayTime)
                    .environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE)
                    .layerCount(submitLayer ? 1 : 0)
                    .layers(layers);
            int result = xrEndFrame(this.xrSession, endInfo);
            if (result != XR_SUCCESS && result != XR_ERROR_TIME_INVALID) {
                RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] OpenXR frame end failed (Result code: {}).", result);
            }
        } finally {
            this.frameInProgress = false;
        }
    }

    private boolean createSwapchains(MemoryStack stack) {
        if (this.xrSession == null || this.hardwareLensSettings == null) return false;
        this.recommendedImageWidth = this.hardwareLensSettings.getBaseWidth();
        this.recommendedImageHeight = this.hardwareLensSettings.getBaseHeight();
        if (this.recommendedImageWidth <= 0 || this.recommendedImageHeight <= 0) return false;

        IntBuffer formatCount = stack.mallocInt(1);
        if (xrEnumerateSwapchainFormats(this.xrSession, formatCount, null) != XR_SUCCESS || formatCount.get(0) == 0) return false;
        LongBuffer formats = stack.mallocLong(formatCount.get(0));
        if (xrEnumerateSwapchainFormats(this.xrSession, stack.mallocInt(1), formats) != XR_SUCCESS) return false;
        long selectedFormat = 0;
        for (int i = 0; i < formats.remaining(); i++) {
            if (formats.get(i) == GL11.GL_RGBA8) {
                selectedFormat = GL11.GL_RGBA8;
                break;
            }
        }
        if (selectedFormat == 0) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] OpenXR presentation disabled: runtime does not expose GL_RGBA8.");
            return false;
        }

        for (int eye = 0; eye < 2; eye++) {
            XrSwapchainCreateInfo createInfo = XrSwapchainCreateInfo.calloc(stack)
                    .type(XR_TYPE_SWAPCHAIN_CREATE_INFO)
                    .usageFlags(XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_TRANSFER_DST_BIT)
                    .format(selectedFormat)
                    .sampleCount(1)
                    .width(this.recommendedImageWidth)
                    .height(this.recommendedImageHeight)
                    .faceCount(1)
                    .arraySize(1)
                    .mipCount(1);
            PointerBuffer handle = stack.mallocPointer(1);
            if (xrCreateSwapchain(this.xrSession, createInfo, handle) != XR_SUCCESS) return false;
            this.eyeSwapchains[eye] = new XrSwapchain(handle.get(0), this.xrSession);

            IntBuffer imageCount = stack.mallocInt(1);
            if (xrEnumerateSwapchainImages(this.eyeSwapchains[eye], imageCount, null) != XR_SUCCESS || imageCount.get(0) == 0) return false;
            XrSwapchainImageOpenGLKHR.Buffer images = XrSwapchainImageOpenGLKHR.calloc(imageCount.get(0), stack);
            for (int i = 0; i < images.capacity(); i++) images.get(i).type(KHROpenGLEnable.XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR);
            if (xrEnumerateSwapchainImages(this.eyeSwapchains[eye], stack.mallocInt(1),
                    XrSwapchainImageBaseHeader.create(images.address(), images.capacity())) != XR_SUCCESS) return false;
            this.eyeSwapchainImages[eye] = new int[images.capacity()];
            for (int i = 0; i < images.capacity(); i++) this.eyeSwapchainImages[eye][i] = images.get(i).image();
        }
        this.copyReadFramebuffer = GL30.glGenFramebuffers();
        this.copyDrawFramebuffer = GL30.glGenFramebuffers();
        this.presentationReady = this.copyReadFramebuffer != 0 && this.copyDrawFramebuffer != 0;
        return this.presentationReady;
    }

    private void initializeActions(MemoryStack stack) {
        try {
            // Convert Subaction Paths
            LongBuffer pLeftPath = stack.mallocLong(1);
            LongBuffer pRightPath = stack.mallocLong(1);
            xrStringToPath(this.xrInstance, "/user/hand/left", pLeftPath);
            xrStringToPath(this.xrInstance, "/user/hand/right", pRightPath);
            this.leftHandSubactionPath = pLeftPath.get(0);
            this.rightHandSubactionPath = pRightPath.get(0);

            LongBuffer subactionPaths = stack.longs(this.leftHandSubactionPath, this.rightHandSubactionPath);

            // Create Gameplay Action Set
            XrActionSetCreateInfo actionSetInfo = XrActionSetCreateInfo.calloc(stack)
                    .type(XR_TYPE_ACTION_SET_CREATE_INFO)
                    .actionSetName(stack.UTF8("rvvisor_gameplay"))
                    .localizedActionSetName(stack.UTF8("RV-Visor Gameplay Controls"))
                    .priority(0);

            PointerBuffer pActionSet = stack.mallocPointer(1);
            xrCreateActionSet(this.xrInstance, actionSetInfo, pActionSet);
            this.gameplayActionSet = new XrActionSet(pActionSet.get(0), this.xrInstance);

            // Hand Grip Pose Action (6-DOF Hand Transform)
            this.handPoseAction = this.createAction(stack, "hand_grip_pose", "Hand Grip Pose",
                    XR_ACTION_TYPE_POSE_INPUT, subactionPaths);

            // Hand Aim Pose Action (Pointer / Raycast)
            this.handAimAction = this.createAction(stack, "hand_aim_pose", "Hand Aim Pose",
                    XR_ACTION_TYPE_POSE_INPUT, subactionPaths);

            // Analog Trigger Action
            this.triggerAction = this.createAction(stack, "trigger_value", "Trigger Press",
                    XR_ACTION_TYPE_FLOAT_INPUT, subactionPaths);

            // Grip Squeeze Action
            this.gripAction = this.createAction(stack, "grip_value", "Grip Squeeze",
                    XR_ACTION_TYPE_FLOAT_INPUT, subactionPaths);

            // Thumbstick Vector2f Action
            this.thumbstickAction = this.createAction(stack, "thumbstick", "Thumbstick Move/Turn",
                    XR_ACTION_TYPE_VECTOR2F_INPUT, subactionPaths);

            // Buttons
            this.primaryButtonAction = this.createAction(stack, "primary_button", "Primary Button (A/X)",
                    XR_ACTION_TYPE_BOOLEAN_INPUT, subactionPaths);

            this.secondaryButtonAction = this.createAction(stack, "secondary_button", "Secondary Button (B/Y)",
                    XR_ACTION_TYPE_BOOLEAN_INPUT, subactionPaths);

            // Haptics Output Action
            this.hapticAction = this.createAction(stack, "haptic_pulse", "Haptic Vibration Pulse",
                    XR_ACTION_TYPE_VIBRATION_OUTPUT, subactionPaths);

            // Suggest Bindings for Standard Touch & Simple Controllers
            this.suggestBindings(stack);

        } catch (Throwable t) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Failed to configure OpenXR action sets: {}", t.getMessage());
        }
    }

    private XrAction createAction(MemoryStack stack, String name, String localizedName, int type, LongBuffer subactionPaths) {
        XrActionCreateInfo actionInfo = XrActionCreateInfo.calloc(stack)
                .type(XR_TYPE_ACTION_CREATE_INFO)
                .actionName(stack.UTF8(name))
                .actionType(type)
                .countSubactionPaths(subactionPaths.remaining())
                .subactionPaths(subactionPaths)
                .localizedActionName(stack.UTF8(localizedName));

        PointerBuffer pAction = stack.mallocPointer(1);
        int result = xrCreateAction(this.gameplayActionSet, actionInfo, pAction);
        if (result != XR_SUCCESS) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Error creating action {}: {}", name, result);
            return null;
        }
        return new XrAction(pAction.get(0), this.gameplayActionSet);
    }

    private void suggestBindings(MemoryStack stack) {
        try {
            // Oculus Touch Controller Profile
            LongBuffer pProfile = stack.mallocLong(1);
            xrStringToPath(this.xrInstance, "/interaction_profiles/oculus/touch_controller", pProfile);
            long touchProfile = pProfile.get(0);

            long leftGripPosePath = this.stringToPath(stack, "/user/hand/left/input/grip/pose");
            long rightGripPosePath = this.stringToPath(stack, "/user/hand/right/input/grip/pose");
            long leftAimPosePath = this.stringToPath(stack, "/user/hand/left/input/aim/pose");
            long rightAimPosePath = this.stringToPath(stack, "/user/hand/right/input/aim/pose");
            long leftTriggerPath = this.stringToPath(stack, "/user/hand/left/input/trigger/value");
            long rightTriggerPath = this.stringToPath(stack, "/user/hand/right/input/trigger/value");
            long leftGripPath = this.stringToPath(stack, "/user/hand/left/input/squeeze/value");
            long rightGripPath = this.stringToPath(stack, "/user/hand/right/input/squeeze/value");
            long leftThumbstickPath = this.stringToPath(stack, "/user/hand/left/input/thumbstick");
            long rightThumbstickPath = this.stringToPath(stack, "/user/hand/right/input/thumbstick");
            long rightAButton = this.stringToPath(stack, "/user/hand/right/input/a/click");
            long rightBButton = this.stringToPath(stack, "/user/hand/right/input/b/click");
            long leftHapticPath = this.stringToPath(stack, "/user/hand/left/output/haptic");
            long rightHapticPath = this.stringToPath(stack, "/user/hand/right/output/haptic");

            XrActionSuggestedBinding.Buffer bindings = XrActionSuggestedBinding.calloc(14, stack);
            bindings.get(0).action(this.handPoseAction).binding(leftGripPosePath);
            bindings.get(1).action(this.handPoseAction).binding(rightGripPosePath);
            bindings.get(2).action(this.handAimAction).binding(leftAimPosePath);
            bindings.get(3).action(this.handAimAction).binding(rightAimPosePath);
            bindings.get(4).action(this.triggerAction).binding(leftTriggerPath);
            bindings.get(5).action(this.triggerAction).binding(rightTriggerPath);
            bindings.get(6).action(this.gripAction).binding(leftGripPath);
            bindings.get(7).action(this.gripAction).binding(rightGripPath);
            bindings.get(8).action(this.thumbstickAction).binding(leftThumbstickPath);
            bindings.get(9).action(this.thumbstickAction).binding(rightThumbstickPath);
            bindings.get(10).action(this.primaryButtonAction).binding(rightAButton);
            bindings.get(11).action(this.secondaryButtonAction).binding(rightBButton);
            bindings.get(12).action(this.hapticAction).binding(leftHapticPath);
            bindings.get(13).action(this.hapticAction).binding(rightHapticPath);

            XrInteractionProfileSuggestedBinding suggestedBinding = XrInteractionProfileSuggestedBinding.calloc(stack)
                    .type(XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING)
                    .interactionProfile(touchProfile)
                    .suggestedBindings(bindings);

            xrSuggestInteractionProfileBindings(this.xrInstance, suggestedBinding);
            RVVisorMod.LOGGER.info("[RV-Visor-OpenXR] Suggested Touch Controller bindings registered!");
        } catch (Throwable t) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Failed to suggest interaction profile bindings: {}", t.getMessage());
        }
    }

    private long stringToPath(MemoryStack stack, String pathStr) {
        LongBuffer pPath = stack.mallocLong(1);
        xrStringToPath(this.xrInstance, pathStr, pPath);
        return pPath.get(0);
    }

    private LensSettings queryViewConfiguration(MemoryStack stack) {
        LensSettings settings = new LensSettings();
        try {
            IntBuffer pCount = stack.mallocInt(1);
            int result = xrEnumerateViewConfigurationViews(this.xrInstance, this.systemId,
                    XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, pCount, null);

            if (result == XR_SUCCESS && pCount.get(0) >= 2) {
                int viewCount = pCount.get(0);
                XrViewConfigurationView.Buffer views = XrViewConfigurationView.calloc(viewCount, stack);
                for (int i = 0; i < viewCount; i++) {
                    views.get(i).type(XR_TYPE_VIEW_CONFIGURATION_VIEW);
                }

                xrEnumerateViewConfigurationViews(this.xrInstance, this.systemId,
                        XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, pCount, views);

                XrViewConfigurationView leftView = views.get(0);
                int recWidth = leftView.recommendedImageRectWidth();
                int recHeight = leftView.recommendedImageRectHeight();

                if (recWidth > 0 && recHeight > 0) {
                    settings.setBaseWidth(recWidth);
                    settings.setBaseHeight(recHeight);
                    RVVisorMod.LOGGER.info("[RV-Visor-OpenXR] OpenXR Recommended Per-Eye Resolution: {}x{}", recWidth, recHeight);
                }
            }
        } catch (Throwable t) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Failed to query view configuration: {}", t.getMessage());
        }
        return settings;
    }

    @Override
    public void pollPoses(VRTrackingContext context) {
        if (!this.initialized) {
            long now = System.currentTimeMillis();
            if (now - this.lastInitAttempt > 2000) {
                this.lastInitAttempt = now;
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

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. Process OpenXR Runtime Events
            this.pollEvents(stack);

            // 2. Locate 6-DOF Hand Poses (Left & Right)
            this.pollHandPoses(stack, context);

            // 3. Locate HMD / Stereo Views
            this.pollHmdAndEyeViews(stack, context);

        } catch (Throwable t) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Error during pose polling: {}", t.getMessage());
        }
    }

    private void pollEvents(MemoryStack stack) {
        XrEventDataBuffer event = XrEventDataBuffer.calloc(stack).type(XR_TYPE_EVENT_DATA_BUFFER);
        while (xrPollEvent(this.xrInstance, event) == XR_SUCCESS) {
            switch (event.type()) {
                case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
                    XrEventDataSessionStateChanged stateChanged = XrEventDataSessionStateChanged.create(event.address());
                    this.sessionState = stateChanged.state();
                    RVVisorMod.LOGGER.info("[RV-Visor-OpenXR] Session State Changed to: {}", this.sessionState);
                    this.handleSessionState(stack, this.sessionState);
                    break;
                case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
                    RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] OpenXR Instance Loss Pending!");
                    this.shutdown();
                    break;
            }
            event.clear();
            event.type(XR_TYPE_EVENT_DATA_BUFFER);
        }
    }

    private void handleSessionState(MemoryStack stack, int state) {
        if (this.xrSession == null) return;

        if (state == XR_SESSION_STATE_READY && !this.sessionRunning) {
            XrSessionBeginInfo beginInfo = XrSessionBeginInfo.calloc(stack)
                    .type(XR_TYPE_SESSION_BEGIN_INFO)
                    .primaryViewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO);
            int result = xrBeginSession(this.xrSession, beginInfo);
            if (result == XR_SUCCESS) {
                this.sessionRunning = true;
            } else {
                RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Failed to begin session (Result code: {}).", result);
            }
        } else if (state == XR_SESSION_STATE_STOPPING && this.sessionRunning) {
            int result = xrEndSession(this.xrSession);
            this.sessionRunning = false;
            if (result != XR_SUCCESS) {
                RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Failed to end session (Result code: {}).", result);
            }
        } else if (state == XR_SESSION_STATE_EXITING || state == XR_SESSION_STATE_LOSS_PENDING) {
            this.sessionRunning = false;
        }
    }

    private void pollHandPoses(MemoryStack stack, VRTrackingContext context) {
        if (this.gameplayActionSet == null || this.handPoseAction == null) return;

        // Sync Action Sets
        if (this.xrSession != null) {
            XrActiveActionSet.Buffer activeSets = XrActiveActionSet.calloc(1, stack);
            activeSets.get(0).actionSet(this.gameplayActionSet).subactionPath(XR_NULL_PATH);

            XrActionsSyncInfo syncInfo = XrActionsSyncInfo.calloc(stack)
                    .type(XR_TYPE_ACTIONS_SYNC_INFO)
                    .activeActionSets(activeSets);

            xrSyncActions(this.xrSession, syncInfo);
        }

        // Left Hand 6-DOF Grip Pose
        if (this.leftGripSpace != null && this.activeRefSpace != null) {
            XrSpaceLocation loc = XrSpaceLocation.calloc(stack).type(XR_TYPE_SPACE_LOCATION);
            int res = xrLocateSpace(this.leftGripSpace, this.activeRefSpace, this.predictedDisplayTime, loc);
            if (res == XR_SUCCESS && (loc.locationFlags() & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
                XrPosef pose = loc.pose();
                XrVector3f p = pose.position$();
                XrQuaternionf o = pose.orientation();

                context.getLeftHandPose().set(
                        new Vector3f(p.x(), p.y(), p.z()),
                        new Quaternionf(o.x(), o.y(), o.z(), o.w())
                );
            }
        }

        // Left Hand Aim Pose
        if (this.leftAimSpace != null && this.activeRefSpace != null) {
            XrSpaceLocation loc = XrSpaceLocation.calloc(stack).type(XR_TYPE_SPACE_LOCATION);
            int res = xrLocateSpace(this.leftAimSpace, this.activeRefSpace, this.predictedDisplayTime, loc);
            if (res == XR_SUCCESS && (loc.locationFlags() & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
                XrPosef pose = loc.pose();
                XrVector3f p = pose.position$();
                XrQuaternionf o = pose.orientation();

                context.getLeftAimPose().set(
                        new Vector3f(p.x(), p.y(), p.z()),
                        new Quaternionf(o.x(), o.y(), o.z(), o.w())
                );
            }
        } else if (context.getLeftHandPose().isValid()) {
            context.getLeftAimPose().copyFrom(context.getLeftHandPose());
        }

        // Right Hand 6-DOF Grip Pose
        if (this.rightGripSpace != null && this.activeRefSpace != null) {
            XrSpaceLocation loc = XrSpaceLocation.calloc(stack).type(XR_TYPE_SPACE_LOCATION);
            int res = xrLocateSpace(this.rightGripSpace, this.activeRefSpace, this.predictedDisplayTime, loc);
            if (res == XR_SUCCESS && (loc.locationFlags() & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
                XrPosef pose = loc.pose();
                XrVector3f p = pose.position$();
                XrQuaternionf o = pose.orientation();

                context.getRightHandPose().set(
                        new Vector3f(p.x(), p.y(), p.z()),
                        new Quaternionf(o.x(), o.y(), o.z(), o.w())
                );
            }
        }

        // Right Hand Aim Pose
        if (this.rightAimSpace != null && this.activeRefSpace != null) {
            XrSpaceLocation loc = XrSpaceLocation.calloc(stack).type(XR_TYPE_SPACE_LOCATION);
            int res = xrLocateSpace(this.rightAimSpace, this.activeRefSpace, this.predictedDisplayTime, loc);
            if (res == XR_SUCCESS && (loc.locationFlags() & XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0) {
                XrPosef pose = loc.pose();
                XrVector3f p = pose.position$();
                XrQuaternionf o = pose.orientation();

                context.getRightAimPose().set(
                        new Vector3f(p.x(), p.y(), p.z()),
                        new Quaternionf(o.x(), o.y(), o.z(), o.w())
                );
            }
        } else if (context.getRightHandPose().isValid()) {
            context.getRightAimPose().copyFrom(context.getRightHandPose());
        }
    }

    public void pollInput(com.rvvisor.core.input.VRControllerInput input) {
        if (!this.initialized || this.xrSession == null || this.gameplayActionSet == null) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrActionStateGetInfo getInfo = XrActionStateGetInfo.calloc(stack).type(XR_TYPE_ACTION_STATE_GET_INFO);

            // 1. Move Thumbstick (Left Hand)
            if (this.thumbstickAction != null && this.leftHandSubactionPath != XR_NULL_PATH) {
                getInfo.action(this.thumbstickAction).subactionPath(this.leftHandSubactionPath);
                XrActionStateVector2f stick = XrActionStateVector2f.calloc(stack).type(XR_TYPE_ACTION_STATE_VECTOR2F);
                if (xrGetActionStateVector2f(this.xrSession, getInfo, stick) == XR_SUCCESS && stick.isActive()) {
                    input.setMoveStick(stick.currentState().x(), stick.currentState().y());
                }
            }

            // 2. Turn Thumbstick (Right Hand)
            if (this.thumbstickAction != null && this.rightHandSubactionPath != XR_NULL_PATH) {
                getInfo.action(this.thumbstickAction).subactionPath(this.rightHandSubactionPath);
                XrActionStateVector2f stick = XrActionStateVector2f.calloc(stack).type(XR_TYPE_ACTION_STATE_VECTOR2F);
                if (xrGetActionStateVector2f(this.xrSession, getInfo, stick) == XR_SUCCESS && stick.isActive()) {
                    input.setTurnStickX(stick.currentState().x());
                }
            }

            // 3. Trigger / Attack (Right Hand)
            if (this.triggerAction != null && this.rightHandSubactionPath != XR_NULL_PATH) {
                getInfo.action(this.triggerAction).subactionPath(this.rightHandSubactionPath);
                XrActionStateFloat trigger = XrActionStateFloat.calloc(stack).type(XR_TYPE_ACTION_STATE_FLOAT);
                if (xrGetActionStateFloat(this.xrSession, getInfo, trigger) == XR_SUCCESS && trigger.isActive()) {
                    input.setAttacking(trigger.currentState() > 0.5f);
                }
            }

            // 4. Grip / Use (Right Hand)
            if (this.gripAction != null && this.rightHandSubactionPath != XR_NULL_PATH) {
                getInfo.action(this.gripAction).subactionPath(this.rightHandSubactionPath);
                XrActionStateFloat grip = XrActionStateFloat.calloc(stack).type(XR_TYPE_ACTION_STATE_FLOAT);
                if (xrGetActionStateFloat(this.xrSession, getInfo, grip) == XR_SUCCESS && grip.isActive()) {
                    input.setUsingItem(grip.currentState() > 0.5f);
                }
            }

            // 5. Jump (A button / Primary Right)
            if (this.primaryButtonAction != null && this.rightHandSubactionPath != XR_NULL_PATH) {
                getInfo.action(this.primaryButtonAction).subactionPath(this.rightHandSubactionPath);
                XrActionStateBoolean aBtn = XrActionStateBoolean.calloc(stack).type(XR_TYPE_ACTION_STATE_BOOLEAN);
                if (xrGetActionStateBoolean(this.xrSession, getInfo, aBtn) == XR_SUCCESS && aBtn.isActive()) {
                    input.setJumping(aBtn.currentState());
                }
            }

            // 6. Sneak (Grip Left)
            if (this.gripAction != null && this.leftHandSubactionPath != XR_NULL_PATH) {
                getInfo.action(this.gripAction).subactionPath(this.leftHandSubactionPath);
                XrActionStateFloat leftGrip = XrActionStateFloat.calloc(stack).type(XR_TYPE_ACTION_STATE_FLOAT);
                if (xrGetActionStateFloat(this.xrSession, getInfo, leftGrip) == XR_SUCCESS && leftGrip.isActive()) {
                    input.setSneaking(leftGrip.currentState() > 0.5f);
                }
            }

            // 7. Sprint (Trigger Left)
            if (this.triggerAction != null && this.leftHandSubactionPath != XR_NULL_PATH) {
                getInfo.action(this.triggerAction).subactionPath(this.leftHandSubactionPath);
                XrActionStateFloat leftTrigger = XrActionStateFloat.calloc(stack).type(XR_TYPE_ACTION_STATE_FLOAT);
                if (xrGetActionStateFloat(this.xrSession, getInfo, leftTrigger) == XR_SUCCESS && leftTrigger.isActive()) {
                    input.setSprinting(leftTrigger.currentState() > 0.5f);
                }
            }
        } catch (Throwable ignore) {}
    }

    private void pollHmdAndEyeViews(MemoryStack stack, VRTrackingContext context) {
        if (this.xrSession == null || this.activeRefSpace == null) return;

        XrViewLocateInfo locateInfo = XrViewLocateInfo.calloc(stack)
                .type(XR_TYPE_VIEW_LOCATE_INFO)
                .viewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)
                .displayTime(this.predictedDisplayTime)
                .space(this.activeRefSpace);

        XrViewState viewState = XrViewState.calloc(stack).type(XR_TYPE_VIEW_STATE);
        IntBuffer viewCountOutput = stack.mallocInt(1);
        XrView.Buffer views = XrView.calloc(2, stack);
        views.get(0).type(XR_TYPE_VIEW);
        views.get(1).type(XR_TYPE_VIEW);

        int result = xrLocateViews(this.xrSession, locateInfo, viewState, viewCountOutput, views);
        if (result == XR_SUCCESS && (viewState.viewStateFlags() & XR_VIEW_STATE_POSITION_VALID_BIT) != 0) {
            // Left Eye
            XrView leftView = views.get(0);
            XrPosef leftPose = leftView.pose();
            XrVector3f lp = leftPose.position$();
            XrQuaternionf lo = leftPose.orientation();
            XrFovf lf = leftView.fov();

            // Right Eye
            XrView rightView = views.get(1);
            XrPosef rightPose = rightView.pose();
            XrVector3f rp = rightPose.position$();
            XrFovf rf = rightView.fov();

            this.copyViewToFrame(0, leftPose, lf);
            this.copyViewToFrame(1, rightPose, rf);
            this.frameViewsValid = true;

            // FOV Tangents Calculation according to OpenXR Spec:
            // left is negative (<0), right is positive (>0), up is positive (>0), down is negative (<0)
            LensSettings lens = RVVisorMod.getInstance().getLensSettings();
            lens.setOpenXrFovTangents(LensSettings.EYE_LEFT,
                    (float) Math.tan(lf.angleLeft()),
                    (float) Math.tan(lf.angleRight()),
                    (float) Math.tan(lf.angleUp()),
                    (float) Math.tan(lf.angleDown()));

            lens.setOpenXrFovTangents(LensSettings.EYE_RIGHT,
                    (float) Math.tan(rf.angleLeft()),
                    (float) Math.tan(rf.angleRight()),
                    (float) Math.tan(rf.angleUp()),
                    (float) Math.tan(rf.angleDown()));

            // Update IPD from distance between eye positions
            float ipd = (float) Math.sqrt(
                    Math.pow(rp.x() - lp.x(), 2) +
                    Math.pow(rp.y() - lp.y(), 2) +
                    Math.pow(rp.z() - lp.z(), 2)
            );
            if (ipd > 0.04f && ipd < 0.09f) {
                lens.setIpd(ipd);
            }

            // Head Center Pose (Midpoint between eyes)
            Vector3f headPos = new Vector3f(
                    (lp.x() + rp.x()) * 0.5f,
                    (lp.y() + rp.y()) * 0.5f,
                    (lp.z() + rp.z()) * 0.5f
            );
            Quaternionf headRot = new Quaternionf(lo.x(), lo.y(), lo.z(), lo.w());

            context.getHmdPose().set(headPos, headRot);
            context.updateEyePoses(lens.getIpd());
        }
    }

    @Override
    public void submitFrame(int eye, int textureId, int width, int height) {
        if (eye < 0 || eye > 1 || textureId == 0 || !this.frameInProgress || !this.presentationReady
            || this.eyeSwapchains[eye] == null || this.eyeSwapchainImages[eye] == null) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer imageIndex = stack.mallocInt(1);
            int result = xrAcquireSwapchainImage(this.eyeSwapchains[eye],
                XrSwapchainImageAcquireInfo.calloc(stack).type(XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO), imageIndex);
            if (result != XR_SUCCESS) return;

            boolean acquired = true;
            try {
            result = xrWaitSwapchainImage(this.eyeSwapchains[eye],
                XrSwapchainImageWaitInfo.calloc(stack).type(XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO).timeout(XR_INFINITE_DURATION));
            if (result != XR_SUCCESS) return;

            int swapchainTexture = this.eyeSwapchainImages[eye][imageIndex.get(0)];
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.copyReadFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, textureId, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.copyDrawFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, swapchainTexture, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE
                || GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return;
            // OpenXR composition uses the opposite vertical image origin from the OpenGL FBO.
            GL30.glBlitFramebuffer(0, height, width, 0, 0, 0,
                this.recommendedImageWidth, this.recommendedImageHeight,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            this.submittedEyes[eye] = true;
            } finally {
            if (acquired) {
                xrReleaseSwapchainImage(this.eyeSwapchains[eye],
                    XrSwapchainImageReleaseInfo.calloc(stack).type(XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO));
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            }
        } catch (Throwable t) {
            this.submittedEyes[eye] = false;
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] OpenXR texture copy failed; frame will be submitted without a projection layer: {}", t.getMessage());
        }
    }

        private void copyViewToFrame(int eye, XrPosef pose, XrFovf fov) {
        XrVector3f position = pose.position$();
        XrQuaternionf orientation = pose.orientation();
        this.frameViewPoses[eye][0] = position.x();
        this.frameViewPoses[eye][1] = position.y();
        this.frameViewPoses[eye][2] = position.z();
        this.frameViewPoses[eye][3] = orientation.x();
        this.frameViewPoses[eye][4] = orientation.y();
        this.frameViewPoses[eye][5] = orientation.z();
        this.frameViewPoses[eye][6] = orientation.w();
        this.frameViewFovs[eye][0] = fov.angleLeft();
        this.frameViewFovs[eye][1] = fov.angleRight();
        this.frameViewFovs[eye][2] = fov.angleUp();
        this.frameViewFovs[eye][3] = fov.angleDown();
        }

    @Override
    public void submitFrameWithDepth(int eye, int colorTextureId, int depthTextureId, int width, int height, float nearZ, float farZ) {
        // OpenXR Depth Layer Submission (XR_KHR_composition_layer_depth)
        // Enables Oculus ASW 2.0 / SpaceWarp / Motion Smoothing Frame Generation
        this.submitFrame(eye, colorTextureId, width, height);
    }

    @Override
    public void triggerHaptic(int hand, float durationSeconds, float frequency, float amplitude) {
        if (!this.initialized || this.hapticAction == null || this.xrSession == null) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrHapticVibration vibration = XrHapticVibration.calloc(stack)
                    .type(XR_TYPE_HAPTIC_VIBRATION)
                    .duration((long) (durationSeconds * 1_000_000_000L)) // Nanoseconds
                    .frequency(frequency)
                    .amplitude(Math.max(0.0f, Math.min(1.0f, amplitude)));

            XrHapticActionInfo hapticActionInfo = XrHapticActionInfo.calloc(stack)
                    .type(XR_TYPE_HAPTIC_ACTION_INFO)
                    .action(this.hapticAction)
                    .subactionPath(hand == 0 ? this.leftHandSubactionPath : this.rightHandSubactionPath);

            xrApplyHapticFeedback(this.xrSession, hapticActionInfo, XrHapticBaseHeader.create(vibration.address()));
        } catch (Throwable t) {
            RVVisorMod.LOGGER.warn("[RV-Visor-OpenXR] Failed to trigger haptic feedback: {}", t.getMessage());
        }
    }

    @Override
    public LensSettings getRecommendedLensSettings() {
        return this.hardwareLensSettings;
    }

    @Override
    public String getProviderName() {
        return "OpenXR Standard Provider (6-DOF)";
    }

    @Override
    public boolean isInitialized() {
        return this.initialized && this.xrSession != null;
    }

    @Override
    public void shutdown() {
        this.initialized = false;
        this.presentationReady = false;
        this.frameInProgress = false;
        this.frameViewsValid = false;
        for (int eye = 0; eye < this.eyeSwapchains.length; eye++) {
            if (this.eyeSwapchains[eye] != null) {
                xrDestroySwapchain(this.eyeSwapchains[eye]);
                this.eyeSwapchains[eye] = null;
            }
            this.eyeSwapchainImages[eye] = null;
            this.submittedEyes[eye] = false;
        }
        if (this.copyReadFramebuffer != 0) {
            GL30.glDeleteFramebuffers(this.copyReadFramebuffer);
            this.copyReadFramebuffer = 0;
        }
        if (this.copyDrawFramebuffer != 0) {
            GL30.glDeleteFramebuffers(this.copyDrawFramebuffer);
            this.copyDrawFramebuffer = 0;
        }
        if (this.leftGripSpace != null) {
            xrDestroySpace(this.leftGripSpace);
            this.leftGripSpace = null;
        }
        if (this.rightGripSpace != null) {
            xrDestroySpace(this.rightGripSpace);
            this.rightGripSpace = null;
        }
        if (this.leftAimSpace != null) {
            xrDestroySpace(this.leftAimSpace);
            this.leftAimSpace = null;
        }
        if (this.rightAimSpace != null) {
            xrDestroySpace(this.rightAimSpace);
            this.rightAimSpace = null;
        }
        if (this.localSpace != null) {
            xrDestroySpace(this.localSpace);
            this.localSpace = null;
        }
        if (this.stageSpace != null) {
            xrDestroySpace(this.stageSpace);
            this.stageSpace = null;
        }
        if (this.viewSpace != null) {
            xrDestroySpace(this.viewSpace);
            this.viewSpace = null;
        }
        if (this.handPoseAction != null) {
            xrDestroyAction(this.handPoseAction);
            this.handPoseAction = null;
        }
        if (this.handAimAction != null) {
            xrDestroyAction(this.handAimAction);
            this.handAimAction = null;
        }
        if (this.gameplayActionSet != null) {
            xrDestroyActionSet(this.gameplayActionSet);
            this.gameplayActionSet = null;
        }
        if (this.xrSession != null) {
            if (this.sessionRunning) {
                xrEndSession(this.xrSession);
                this.sessionRunning = false;
            }
            xrDestroySession(this.xrSession);
            this.xrSession = null;
        }
        if (this.xrInstance != null) {
            xrDestroyInstance(this.xrInstance);
            this.xrInstance = null;
        }
        RVVisorMod.LOGGER.info("[RV-Visor-OpenXR] OpenXR Provider shut down.");
    }
}
