package com.rvvisor.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * High-performance Spectator Mirror Renderer for Desktop Monitor.
 * Automatically crops and scales the square VR eye textures to 16:9 widescreen
 * filling the entire monitor window with zero black bars or corner distortion.
 */
public class VRMirrorRenderer {
    private final Minecraft minecraft = Minecraft.getInstance();

    public enum MirrorMode {
        LEFT_EYE,
        RIGHT_EYE,
        DUAL_STEREO_SIDE_BY_SIDE
    }

    private MirrorMode mirrorMode = MirrorMode.LEFT_EYE;

    public void renderMirror(VREyeFramebuffer leftEye, VREyeFramebuffer rightEye, int windowWidth, int windowHeight) {
        if (leftEye == null || rightEye == null) return;

        RenderSystem.assertOnRenderThreadOrInit();

        int physicalWidth = (this.minecraft.getWindow() != null) ? this.minecraft.getWindow().getWidth() : windowWidth;
        int physicalHeight = (this.minecraft.getWindow() != null) ? this.minecraft.getWindow().getHeight() : windowHeight;

        // Usa GlStateManager para no desincronizar el estado interno de Minecraft
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        GlStateManager._viewport(0, 0, physicalWidth, physicalHeight);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        RenderSystem.disableScissor();

        if (this.mirrorMode == MirrorMode.LEFT_EYE) {
            this.blitCropped(leftEye.getFramebufferId(), 0, 0, physicalWidth, physicalHeight, leftEye.getWidth(), leftEye.getHeight());
        } else if (this.mirrorMode == MirrorMode.RIGHT_EYE) {
            this.blitCropped(rightEye.getFramebufferId(), 0, 0, physicalWidth, physicalHeight, rightEye.getWidth(), rightEye.getHeight());
        } else {
            int halfWidth = physicalWidth / 2;
            this.blitDirect(leftEye.getFramebufferId(), 0, 0, halfWidth, physicalHeight, leftEye.getWidth(), leftEye.getHeight());
            this.blitDirect(rightEye.getFramebufferId(), halfWidth, 0, physicalWidth, physicalHeight, rightEye.getWidth(), rightEye.getHeight());
        }

        // Devuelve el binding a MainRenderTarget para que Minecraft continue su flujo sin perderse
        if (this.minecraft.getMainRenderTarget() != null) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.minecraft.getMainRenderTarget().frameBufferId);
        } else {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
    }

    /**
     * Crops the center 16:9 portion of the square VR eye framebuffer to prevent aspect ratio distortion on widescreen monitors.
     */
    private void blitCropped(int srcFbo, int dstX0, int dstY0, int dstX1, int dstY1, int srcWidth, int srcHeight) {
        int dstWidth = dstX1 - dstX0;
        int dstHeight = dstY1 - dstY0;
        if (dstWidth <= 0 || dstHeight <= 0) return;

        float targetAspect = (float) dstWidth / (float) dstHeight;
        float srcAspect = (float) srcWidth / (float) srcHeight;

        int srcCropX0 = 0;
        int srcCropY0 = 0;
        int srcCropX1 = srcWidth;
        int srcCropY1 = srcHeight;

        if (targetAspect > srcAspect) {
            // Screen is wider than source: crop top and bottom
            int cropHeight = (int) (srcWidth / targetAspect);
            int offsetY = (srcHeight - cropHeight) / 2;
            srcCropY0 = offsetY;
            srcCropY1 = offsetY + cropHeight;
        } else {
            // Screen is narrower: crop sides
            int cropWidth = (int) (srcHeight * targetAspect);
            int offsetX = (srcWidth - cropWidth) / 2;
            srcCropX0 = offsetX;
            srcCropX1 = offsetX + cropWidth;
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GL30.glBlitFramebuffer(
                srcCropX0, srcCropY0, srcCropX1, srcCropY1,
                dstX0, dstY1, dstX1, dstY0,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_LINEAR
        );
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
    }

    private void blitDirect(int srcFbo, int dstX0, int dstY0, int dstX1, int dstY1, int srcWidth, int srcHeight) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GL30.glBlitFramebuffer(
                0, 0, srcWidth, srcHeight,
                dstX0, dstY1, dstX1, dstY0,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_LINEAR
        );
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
    }

    public MirrorMode getMirrorMode() {
        return this.mirrorMode;
    }

    public void setMirrorMode(MirrorMode mirrorMode) {
        this.mirrorMode = mirrorMode;
    }
}
