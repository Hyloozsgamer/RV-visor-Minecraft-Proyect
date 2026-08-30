package com.rvvisor.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rvvisor.RVVisorMod;
import com.rvvisor.core.data.VRDevicePose;
import com.rvvisor.core.data.VRTrackingContext;
import com.rvvisor.core.input.VRControllerInput;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * 3D Spatial-Anchored Floating GUI Canvas & Interactive Laser Pointer Engine for VR.
 * Menus (Pause Menu, InventoryScreen, Crafting, Chests, Configs) are locked permanently in 3D tracking space
 * at the exact location where opened, staying 100% fixed in the air while looking around or moving.
 * 100% transparent backdrop — ZERO black boxes covering the 3D world.
 */
public class VRFloatingGuiRenderer {
    private VREyeFramebuffer guiFramebuffer;
    private static final int GUI_WIDTH = 1920;
    private static final int GUI_HEIGHT = 1080;

    private float guiDistance = 1.35f;
    private float guiWidthMeters = 1.55f;
    private float guiHeightMeters = 0.88f;

    // Laser & Pointer State
    private boolean isLaserHittingGui = false;
    private float laserHitU = 0.5f;
    private float laserHitV = 0.5f;
    private double currentMouseX = -1;
    private double currentMouseY = -1;
    private boolean lastTriggerState = false;
    private boolean lastGripState = false;

    // 3D Spatial Anchor in Tracking Space: Pinned in the air in 3D world coordinates
    private Vector3f anchoredMenuPos = null;
    private Quaternionf anchoredMenuRot = null;

    public void init() {
        if (this.guiFramebuffer == null) {
            this.guiFramebuffer = new VREyeFramebuffer("VR_Floating_GUI", GUI_WIDTH, GUI_HEIGHT);
        }
    }

    /**
     * Renders the active Minecraft Screen into the high-res offscreen GUI Framebuffer with 100% transparency.
     */
    public void renderScreenToFbo(Minecraft mc, DeltaTracker deltaTracker, float partialTicks) {
        if (mc.screen == null) {
            this.anchoredMenuPos = null;
            this.anchoredMenuRot = null;
            return;
        }
        try {
            this.init();
            this.guiFramebuffer.ensureInitialized();

            this.guiFramebuffer.bindWrite(true);
            this.guiFramebuffer.clear(0.0f, 0.0f, 0.0f, 0.0f); // 100% transparent backdrop — ZERO black box

            RenderSystem.viewport(0, 0, GUI_WIDTH, GUI_HEIGHT);

            int guiScaledWidth = mc.getWindow().getGuiScaledWidth();
            int guiScaledHeight = mc.getWindow().getGuiScaledHeight();

            Matrix4f ortho = new Matrix4f().setOrtho(0.0F, (float) guiScaledWidth, (float) guiScaledHeight, 0.0F, -1000.0F, 1000.0F);
            RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

            org.joml.Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            GlStateManager._disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            GuiGraphics guiGraphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());

            int mouseX = (int) (this.isLaserHittingGui ? this.currentMouseX : -100);
            int mouseY = (int) (this.isLaserHittingGui ? this.currentMouseY : -100);

            if (this.isLaserHittingGui && mc.screen != null) {
                mc.screen.mouseMoved(mouseX, mouseY);
            }

            mc.screen.renderWithTooltip(guiGraphics, mouseX, mouseY, partialTicks);
            guiGraphics.flush();

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            GlStateManager._enableDepthTest();

            this.guiFramebuffer.unbindWrite();
            mc.getMainRenderTarget().bindWrite(true);
        } catch (Throwable t) {
            RVVisorMod.LOGGER.error("[RV-Visor] Error in renderScreenToFbo", t);
        }
    }

    /**
     * Renders the 2D GUI as a floating 3D canvas pinned permanently in 3D Space, plus the glowing laser pointer.
     */
    public void renderGuiInWorld(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || this.guiFramebuffer == null || !this.guiFramebuffer.isComplete()) {
            this.anchoredMenuPos = null;
            this.anchoredMenuRot = null;
            return;
        }

        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod == null || mod.getTrackingContext() == null) return;

        VRTrackingContext tracking = mod.getTrackingContext();
        VRDevicePose hmdPose = tracking.getRenderHmdPose();
        if (!hmdPose.isValid()) return;

        try {
            // Anchor menu in 3D tracking space at eye level when it opens
            if (this.anchoredMenuPos == null) {
                Vector3f headPos = hmdPose.getPosition();
                Quaternionf headRot = hmdPose.getOrientation();

                // Horizontal forward vector (ignoring pitch/roll so menu stays upright like a TV screen)
                Vector3f fwd = headRot.transform(new Vector3f(0.0f, 0.0f, -1.0f), new Vector3f());
                float hLen = (float) Math.sqrt(fwd.x * fwd.x + fwd.z * fwd.z);
                if (hLen < 0.001f) {
                    fwd.set(0.0f, 0.0f, -1.0f);
                } else {
                    fwd.x /= hLen;
                    fwd.z /= hLen;
                }

                // Position 1.35m in front of head at eye level
                this.anchoredMenuPos = new Vector3f(headPos.x + fwd.x * this.guiDistance, headPos.y, headPos.z + fwd.z * this.guiDistance);

                // Upright orientation facing the player
                float yaw = (float) Math.atan2(fwd.x, -fwd.z);
                this.anchoredMenuRot = new Quaternionf().rotateY(yaw);
            }

            // Compute relative menu position and orientation relative to live head pose
            Vector3f liveHeadPos = hmdPose.getPosition();
            Quaternionf liveHeadRot = hmdPose.getOrientation();
            Quaternionf liveHeadRotInv = new Quaternionf(liveHeadRot).conjugate();

            Vector3f relPos = liveHeadRotInv.transform(new Vector3f(this.anchoredMenuPos).sub(liveHeadPos), new Vector3f());
            Quaternionf relRot = liveHeadRotInv.mul(this.anchoredMenuRot, new Quaternionf());

            float halfW = this.guiWidthMeters / 2.0f;
            float halfH = this.guiHeightMeters / 2.0f;

            // 1. Process Controller Laser & Raycast Interaction against the 3D pinned plane
            this.processLaserInteractionSpatial(mc, tracking, hmdPose, relPos, relRot, halfW, halfH);

            // 2. Set Projection and ModelView matrix to render the anchored quad in Eye Space
            RenderSystem.setProjectionMatrix(mod.getRenderEngine().getEyeProjectionMatrix(0.05f, 1000.0f), com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);

            org.joml.Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.identity();
            modelViewStack.translate(relPos.x, relPos.y, relPos.z);
            modelViewStack.rotate(relRot);
            RenderSystem.applyModelViewMatrix();

            GlStateManager._disableDepthTest();
            GlStateManager._disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // 3. Render 3D Pinned Menu Quad
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, this.guiFramebuffer.getColorTextureId());

            com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
            com.mojang.blaze3d.vertex.BufferBuilder buffer = tesselator.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);

            buffer.addVertex(-halfW,  halfH, 0.0f).setUv(0.0f, 1.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f);
            buffer.addVertex(-halfW, -halfH, 0.0f).setUv(0.0f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f);
            buffer.addVertex( halfW, -halfH, 0.0f).setUv(1.0f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f);
            buffer.addVertex( halfW,  halfH, 0.0f).setUv(1.0f, 1.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f);

            com.mojang.blaze3d.vertex.MeshData meshData = buffer.build();
            if (meshData != null) {
                com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(meshData);
            }

            // 4. Render Glowing Cursor Dot on GUI Quad if laser is hitting
            if (this.isLaserHittingGui) {
                float cursorX = (this.laserHitU - 0.5f) * this.guiWidthMeters;
                float cursorY = (0.5f - this.laserHitV) * this.guiHeightMeters;
                float dotSize = 0.020f;
                float dotZ = 0.005f;

                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                com.mojang.blaze3d.vertex.BufferBuilder dotBuffer = tesselator.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
                dotBuffer.addVertex(cursorX - dotSize, cursorY + dotSize, dotZ).setColor(0.0f, 0.9f, 1.0f, 0.95f);
                dotBuffer.addVertex(cursorX - dotSize, cursorY - dotSize, dotZ).setColor(0.0f, 0.9f, 1.0f, 0.95f);
                dotBuffer.addVertex(cursorX + dotSize, cursorY - dotSize, dotZ).setColor(0.0f, 0.9f, 1.0f, 0.95f);
                dotBuffer.addVertex(cursorX + dotSize, cursorY + dotSize, dotZ).setColor(0.0f, 0.9f, 1.0f, 0.95f);

                com.mojang.blaze3d.vertex.MeshData dotMesh = dotBuffer.build();
                if (dotMesh != null) {
                    com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(dotMesh);
                }
            }

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();

            // 5. Render 3D Laser Beam from Right Controller
            this.renderLaserBeamEyeSpace(relPos);

            GlStateManager._enableCull();
            GlStateManager._enableDepthTest();
        } catch (Throwable t) {
            RVVisorMod.LOGGER.error("[RV-Visor] Error in renderGuiInWorld", t);
        }
    }

    /**
     * Raycasts the Right Controller orientation against the 3D pinned GUI billboard plane.
     */
    private void processLaserInteractionSpatial(Minecraft mc, VRTrackingContext tracking, VRDevicePose hmdPose, Vector3f relPos, Quaternionf relRot, float halfW, float halfH) {
        VRDevicePose rightHand = tracking.getRenderRightAimPose().isValid() ?
                tracking.getRenderRightAimPose() : tracking.getRenderRightHandPose();

        if (!rightHand.isValid()) {
            this.isLaserHittingGui = false;
            return;
        }

        Vector3f headPos = hmdPose.getPosition();
        Vector3f handPos = rightHand.getPosition();
        Quaternionf headRot = hmdPose.getOrientation();
        Quaternionf headRotInv = new Quaternionf(headRot).conjugate();

        Vector3f rawOffset = new Vector3f(handPos.x - headPos.x, handPos.y - headPos.y, handPos.z - headPos.z);
        Vector3f rayOrigin = headRotInv.transform(rawOffset, new Vector3f());

        Quaternionf relHandRot = headRotInv.mul(rightHand.getOrientation(), new Quaternionf());
        Vector3f rayDir = relHandRot.transform(new Vector3f(0, 0, -1), new Vector3f()).normalize();

        // Plane normal and center in Eye Space
        Vector3f planeNormal = relRot.transform(new Vector3f(0, 0, 1), new Vector3f()).normalize();
        Vector3f planeCenter = relPos;

        double denom = rayDir.dot(planeNormal);
        if (Math.abs(denom) > 1e-4) {
            Vector3f p0_l0 = new Vector3f(planeCenter).sub(rayOrigin);
            float t = (p0_l0.dot(planeNormal)) / (float) denom;
            if (t > 0 && t < 10.0f) {
                Vector3f hitPoint = new Vector3f(rayOrigin).add(new Vector3f(rayDir).mul(t));
                Vector3f relHit = new Vector3f(hitPoint).sub(planeCenter);

                Quaternionf relRotInv = new Quaternionf(relRot).conjugate();
                Vector3f localHit = relRotInv.transform(relHit, new Vector3f());

                if (localHit.x >= -halfW && localHit.x <= halfW && localHit.y >= -halfH && localHit.y <= halfH) {
                    this.isLaserHittingGui = true;
                    this.laserHitU = (localHit.x + halfW) / this.guiWidthMeters;
                    this.laserHitV = (halfH - localHit.y) / this.guiHeightMeters;

                    int guiScaledWidth = mc.getWindow().getGuiScaledWidth();
                    int guiScaledHeight = mc.getWindow().getGuiScaledHeight();
                    this.currentMouseX = this.laserHitU * guiScaledWidth;
                    this.currentMouseY = this.laserHitV * guiScaledHeight;

                    VRControllerInput input = RVVisorMod.getInstance().getControllerInput();
                    boolean triggerPressed = input != null && input.isAttacking();
                    boolean gripPressed = input != null && input.isUsingItem();
                    final double clickX = this.currentMouseX;
                    final double clickY = this.currentMouseY;

                    // Left Click (Trigger - Grab stack / Click button)
                    if (triggerPressed && !this.lastTriggerState) {
                        mc.execute(() -> {
                            if (mc.screen != null) {
                                mc.screen.mouseClicked(clickX, clickY, 0);
                            }
                        });
                    } else if (!triggerPressed && this.lastTriggerState) {
                        mc.execute(() -> {
                            if (mc.screen != null) {
                                mc.screen.mouseReleased(clickX, clickY, 0);
                            }
                        });
                    }
                    this.lastTriggerState = triggerPressed;

                    // Right Click (Grip - Split half stack / Place 1 item)
                    if (gripPressed && !this.lastGripState) {
                        mc.execute(() -> {
                            if (mc.screen != null) {
                                mc.screen.mouseClicked(clickX, clickY, 1);
                            }
                        });
                    } else if (!gripPressed && this.lastGripState) {
                        mc.execute(() -> {
                            if (mc.screen != null) {
                                mc.screen.mouseReleased(clickX, clickY, 1);
                            }
                        });
                    }
                    this.lastGripState = gripPressed;
                    return;
                }
            }
        }

        this.isLaserHittingGui = false;
    }

    /**
     * Renders a glowing laser beam shooting from the right controller in Eye Space.
     */
    private void renderLaserBeamEyeSpace(Vector3f relMenuPos) {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod == null || mod.getTrackingContext() == null) return;

        VRTrackingContext tracking = mod.getTrackingContext();
        VRDevicePose hmdPose = tracking.getRenderHmdPose();
        VRDevicePose rightHand = tracking.getRenderRightAimPose().isValid() ?
                tracking.getRenderRightAimPose() : tracking.getRenderRightHandPose();

        if (!hmdPose.isValid() || !rightHand.isValid()) return;

        Vector3f headPos = hmdPose.getPosition();
        Vector3f handPos = rightHand.getPosition();
        Quaternionf headRot = hmdPose.getOrientation();
        Quaternionf headRotInv = new Quaternionf(headRot).conjugate();

        Vector3f rawOffset = new Vector3f(handPos.x - headPos.x, handPos.y - headPos.y, handPos.z - headPos.z);
        Vector3f startPos = headRotInv.transform(rawOffset, new Vector3f());

        Quaternionf relRot = headRotInv.mul(rightHand.getOrientation(), new Quaternionf());
        Vector3f dir = relRot.transform(new Vector3f(0, 0, -1), new Vector3f()).normalize();

        float length = this.isLaserHittingGui ? Math.abs(relMenuPos.z) : 2.5f;
        Vector3f endPos = new Vector3f(startPos).add(new Vector3f(dir).mul(length));

        org.joml.Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder buffer = tesselator.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);

        // Core cyan laser line
        buffer.addVertex(startPos.x, startPos.y, startPos.z).setColor(0.0f, 0.9f, 1.0f, 0.95f);
        buffer.addVertex(endPos.x, endPos.y, endPos.z).setColor(0.0f, 0.9f, 1.0f, 0.2f);

        // Soft outer aura line
        buffer.addVertex(startPos.x + 0.002f, startPos.y + 0.002f, startPos.z).setColor(0.4f, 1.0f, 1.0f, 0.5f);
        buffer.addVertex(endPos.x + 0.002f, endPos.y + 0.002f, endPos.z).setColor(0.4f, 1.0f, 1.0f, 0.1f);

        com.mojang.blaze3d.vertex.MeshData meshData = buffer.build();
        if (meshData != null) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(meshData);
        }

        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    public void destroy() {
        if (this.guiFramebuffer != null) {
            this.guiFramebuffer.destroy();
            this.guiFramebuffer = null;
        }
    }
}
