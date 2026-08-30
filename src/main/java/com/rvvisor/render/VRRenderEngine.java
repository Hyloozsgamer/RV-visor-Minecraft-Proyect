package com.rvvisor.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rvvisor.RVVisorMod;
import com.rvvisor.core.data.VRTrackingContext;
import com.rvvisor.core.optics.LensSettings;
import com.rvvisor.core.provider.IVRProvider;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Master VR Rendering Engine for Fabric 1.21.1
 * Direct 4x MSAA rendering per eye with AMD FidelityFX CAS sharpening pass.
 */
public class VRRenderEngine {
    private final IVRProvider vrProvider;
    private final VRTrackingContext trackingContext;
    private final LensSettings lensSettings;

    private VREyeFramebuffer leftEyeFbo;
    private VREyeFramebuffer rightEyeFbo;

    private final VRFloatingGuiRenderer guiRenderer = new VRFloatingGuiRenderer();
    private final VRHandRenderer handRenderer = new VRHandRenderer();
    private final VRMirrorRenderer mirrorRenderer = new VRMirrorRenderer();
    private final VRHudRenderer hudRenderer = new VRHudRenderer();

    private int currentEyePass = -1;
    private boolean isRenderingVR = false;
    private long frameStartNanos = System.nanoTime();

    public VRRenderEngine(IVRProvider vrProvider, VRTrackingContext trackingContext, LensSettings lensSettings) {
        this.vrProvider = vrProvider;
        this.trackingContext = trackingContext;
        this.lensSettings = lensSettings;
    }

    public void ensureFramebuffers() {
        int w = this.lensSettings.getEffectiveWidth();
        int h = this.lensSettings.getEffectiveHeight();
        int msaa = this.lensSettings.getMsaaSamples();

        if (this.leftEyeFbo == null) {
            this.leftEyeFbo = new VREyeFramebuffer("Left_Eye", w, h, msaa);
        } else if (this.leftEyeFbo.getWidth() != w || this.leftEyeFbo.getHeight() != h || this.leftEyeFbo.getMsaaSamples() != msaa) {
            this.leftEyeFbo.resize(w, h, msaa);
        }

        if (this.rightEyeFbo == null) {
            this.rightEyeFbo = new VREyeFramebuffer("Right_Eye", w, h, msaa);
        } else if (this.rightEyeFbo.getWidth() != w || this.rightEyeFbo.getHeight() != h || this.rightEyeFbo.getMsaaSamples() != msaa) {
            this.rightEyeFbo.resize(w, h, msaa);
        }

        this.leftEyeFbo.ensureInitialized();
        this.rightEyeFbo.ensureInitialized();
    }

    public void onRenderFrameStart() {
        RVVisorMod mod = RVVisorMod.getInstance();
        if (mod == null || !mod.isVrActive()) return;

        // 1. Guarda poses del frame previo para interpolacion
        this.trackingContext.beginNewFrame();

        // 2. Inicio de ciclo en VR provider
        this.vrProvider.beginFrame();

        // 3. Consulta poses del hardware
        this.vrProvider.pollPoses(this.trackingContext);

        // 4. Recalcula posicion de ojos con el IPD actual
        this.trackingContext.onPosesUpdated(this.lensSettings.getIpd());

        if (mod.getControllerInput() != null) {
            mod.getControllerInput().pollControllers();
        }

        this.ensureFramebuffers();
    }

    /**
     * Inicia el render del ojo directamente en su propio FBO dedicado (sin tocar ni contaminar MainRenderTarget).
     */
    public void beginEyePass(int eye, float partialTicks) {
        this.currentEyePass = eye;
        this.isRenderingVR = true;
        this.trackingContext.updateInterpolatedPoses(partialTicks);

        VREyeFramebuffer fbo = (eye == 0) ? this.leftEyeFbo : this.rightEyeFbo;
        Minecraft mc = Minecraft.getInstance();

        if (fbo != null && mc != null) {
            fbo.ensureInitialized();
            fbo.bindWrite(true);
            fbo.clear(Minecraft.ON_OSX);

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderSystem.disableScissor();
            GlStateManager._viewport(0, 0, fbo.getWidth(), fbo.getHeight());
            RenderSystem.viewport(0, 0, fbo.getWidth(), fbo.getHeight());
        }
    }

    /**
     * Finaliza el render del ojo y restaura el binding del framebuffer.
     */
    public void endEyePass() {
        VREyeFramebuffer fbo = (this.currentEyePass == 0) ? this.leftEyeFbo : this.rightEyeFbo;
        if (fbo != null) {
            fbo.resolveMSAA();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getMainRenderTarget() != null) {
            mc.getMainRenderTarget().bindWrite(true);
        }
        this.currentEyePass = -1;
    }

    public void finishAndSubmitFrame(int windowWidth, int windowHeight) {
        if (!this.isRenderingVR) return;

        long frameEnd = System.nanoTime();
        float frameTimeMs = (frameEnd - this.frameStartNanos) / 1_000_000.0f;
        this.frameStartNanos = frameEnd;

        try {
            float nearZ = this.lensSettings.getNearClip();
            float farZ = this.lensSettings.getFarClip();

            if (this.leftEyeFbo != null && this.leftEyeFbo.isComplete()) {
                this.vrProvider.submitFrameWithDepth(
                        LensSettings.EYE_LEFT,
                        this.leftEyeFbo.getColorTextureId(),
                        this.leftEyeFbo.getDepthTextureId(),
                        this.leftEyeFbo.getWidth(),
                        this.leftEyeFbo.getHeight(),
                        nearZ,
                        farZ
                );
            }

            if (this.rightEyeFbo != null && this.rightEyeFbo.isComplete()) {
                this.vrProvider.submitFrameWithDepth(
                        LensSettings.EYE_RIGHT,
                        this.rightEyeFbo.getColorTextureId(),
                        this.rightEyeFbo.getDepthTextureId(),
                        this.rightEyeFbo.getWidth(),
                        this.rightEyeFbo.getHeight(),
                        nearZ,
                        farZ
                );
            }

            this.vrProvider.postSubmit();

            // Dynamic Resolution Update (auto-steps scale based on 90Hz frame time si esta activo)
            this.lensSettings.updateDynamicResolution(frameTimeMs);

            // If DRS scale changed, resize framebuffers smoothly
            if (this.lensSettings.needsResize(this.leftEyeFbo)) {
                int newW = this.lensSettings.getEffectiveWidth();
                int newH = this.lensSettings.getEffectiveHeight();
                int msaa = this.lensSettings.getMsaaSamples();
                if (this.leftEyeFbo != null) this.leftEyeFbo.resize(newW, newH, msaa);
                if (this.rightEyeFbo != null) this.rightEyeFbo.resize(newW, newH, msaa);
            }

            // Restore MainRenderTarget to desktop window resolution
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getMainRenderTarget() != null) {
                if (mc.getMainRenderTarget().width != windowWidth || mc.getMainRenderTarget().height != windowHeight) {
                    mc.getMainRenderTarget().resize(windowWidth, windowHeight, Minecraft.ON_OSX);
                }
            }

            // Mirror al monitor desktop
            this.mirrorRenderer.renderMirror(this.leftEyeFbo, this.rightEyeFbo, windowWidth, windowHeight);

        } finally {
            this.currentEyePass = -1;
            this.isRenderingVR = false;
        }
    }

    public Matrix4f getEyeProjectionMatrix(float near, float far) {
        if (this.currentEyePass >= 0 && this.vrProvider instanceof com.rvvisor.core.provider.OpenVRProvider openVR) {
            Matrix4f nativeProj = openVR.getProjectionMatrix(this.currentEyePass, near, far);
            if (nativeProj != null) {
                return nativeProj;
            }
        }

        if (this.currentEyePass < 0) {
            Minecraft mc = Minecraft.getInstance();
            float aspect = (mc != null && mc.getWindow() != null) ?
                (float) mc.getWindow().getWidth() / (float) mc.getWindow().getHeight() : 16f / 9f;
            return new Matrix4f().perspective((float) Math.toRadians(70.0), aspect, near, far);
        }
        return this.lensSettings.calculateProjectionMatrix(this.currentEyePass, near, far);
    }

    public Matrix4f getEyeViewMatrix(float partialTicks) {
        if (this.currentEyePass < 0 || this.trackingContext == null) {
            return new Matrix4f().identity();
        }
        return this.trackingContext.getEyeViewMatrix(this.currentEyePass);
    }

    public Vector3f getEyePosition(int eye) {
        return this.trackingContext.getEyePosition(eye);
    }

    public int getCurrentEyePass() {
        return this.currentEyePass;
    }

    public boolean isRenderingVR() {
        return this.isRenderingVR;
    }

    public VRHandRenderer getHandRenderer() {
        return this.handRenderer;
    }

    public void renderVRWorldElements(Camera camera, float partialTicks, Matrix4f projectionMatrix) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        PoseStack poseStack = new PoseStack();

        int defaultLight = LevelRenderer.getLightColor(mc.level, mc.player.blockPosition());
        this.handRenderer.renderHands(poseStack, bufferSource, defaultLight, partialTicks, this.trackingContext, this.currentEyePass, camera);
        this.renderCrosshairInWorld(poseStack, bufferSource, camera, partialTicks);
        this.hudRenderer.renderHudInWorld(poseStack, bufferSource, camera, partialTicks);
        this.guiRenderer.renderGuiInWorld(poseStack, bufferSource, camera, partialTicks);

        bufferSource.endBatch();
    }

    public void renderCrosshairInWorld(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.screen != null || mc.options.hideGui) return;

        Vec3 camPos = camera.getPosition();
        Vec3 lookVec = new Vec3(camera.getLookVector().x, camera.getLookVector().y, camera.getLookVector().z);

        Vec3 hitPos;
        HitResult hitResult = mc.hitResult;
        boolean hasHit = (hitResult != null && hitResult.getType() != HitResult.Type.MISS);

        if (hasHit) {
            hitPos = hitResult.getLocation();
        } else {
            hitPos = camPos.add(lookVec.scale(6.0));
        }

        Vec3 toCam = camPos.subtract(hitPos).normalize();
        Vec3 renderPos = hitPos.add(toCam.scale(0.015));

        poseStack.pushPose();
        poseStack.translate(renderPos.x - camPos.x, renderPos.y - camPos.y, renderPos.z - camPos.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot() + 180.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-camera.getXRot()));

        double dist = camPos.distanceTo(renderPos);
        float scale = (float) Math.max(0.005f, Math.min(0.20f, dist * 0.018f));
        poseStack.scale(scale, scale, scale);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.gui());
        Matrix4f mat = poseStack.last().pose();

        float r = 1.0f, g = 1.0f, b = 1.0f, a = 0.9f;
        if (hasHit && hitResult.getType() == HitResult.Type.ENTITY) {
            r = 1.0f; g = 0.25f; b = 0.25f;
        } else if (hasHit && hitResult.getType() == HitResult.Type.BLOCK) {
            r = 0.35f; g = 0.85f; b = 1.0f;
        }

        drawSolidQuad(consumer, mat, -0.05f, -0.05f, 0.05f, 0.05f, 0.0f, r, g, b, 1.0f);

        float inner = 0.10f;
        float outer = 0.35f;
        float thickness = 0.04f;

        drawSolidQuad(consumer, mat, -thickness * 0.5f, inner, thickness * 0.5f, outer, 0.0f, r, g, b, a);
        drawSolidQuad(consumer, mat, -thickness * 0.5f, -outer, thickness * 0.5f, -inner, 0.0f, r, g, b, a);
        drawSolidQuad(consumer, mat, -outer, -thickness * 0.5f, -inner, thickness * 0.5f, 0.0f, r, g, b, a);
        drawSolidQuad(consumer, mat, inner, -thickness * 0.5f, outer, thickness * 0.5f, 0.0f, r, g, b, a);

        if (mc.gameMode != null && mc.gameMode.isDestroying()) {
            float ringSize = 0.45f;
            drawSolidQuad(consumer, mat, -ringSize, -ringSize, ringSize, -ringSize + 0.06f, 0.0f, 1.0f, 0.8f, 0.1f, 0.95f);
        }

        if (mc.player != null) {
            float attackCooldown = mc.player.getAttackStrengthScale(0.0f);
            if (attackCooldown < 0.99f) {
                float barW = 0.30f;
                float barH = 0.04f;
                float yPos = -0.50f;
                drawSolidQuad(consumer, mat, -barW, yPos, barW, yPos + barH, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f);
                drawSolidQuad(consumer, mat, -barW, yPos, -barW + (barW * 2.0f * attackCooldown), yPos + barH, 0.0f, 1.0f, 1.0f, 1.0f, 0.9f);
            }
        }

        poseStack.popPose();
    }

    private void drawSolidQuad(VertexConsumer consumer, Matrix4f mat, float minX, float minY, float maxX, float maxY, float z,
                                float r, float g, float b, float a) {
        int argb = net.minecraft.util.FastColor.ARGB32.colorFromFloat(a, r, g, b);
        consumer.addVertex(mat, minX, minY, z).setColor(argb);
        consumer.addVertex(mat, maxX, minY, z).setColor(argb);
        consumer.addVertex(mat, maxX, maxY, z).setColor(argb);
        consumer.addVertex(mat, minX, maxY, z).setColor(argb);
    }

    public VRFloatingGuiRenderer getGuiRenderer() {
        return this.guiRenderer;
    }

    public VRHudRenderer getHudRenderer() {
        return this.hudRenderer;
    }

    public VRMirrorRenderer getMirrorRenderer() {
        return this.mirrorRenderer;
    }

    public void destroy() {
        if (this.leftEyeFbo != null) {
            this.leftEyeFbo.destroy();
            this.leftEyeFbo = null;
        }
        if (this.rightEyeFbo != null) {
            this.rightEyeFbo.destroy();
            this.rightEyeFbo = null;
        }
        this.guiRenderer.destroy();
        this.hudRenderer.destroy();
    }
}
