package com.rvvisor.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * Dedicated high-performance offscreen framebuffer for VR eye views.
 * Supports hardware Multisample Anti-Aliasing (MSAA 2x / 4x / 8x) with fast GPU resolve,
 * native RGBA8 color texture and 24-bit Depth Texture (GL_DEPTH_ATTACHMENT)
 * for OpenXR Depth Layer Submission & Oculus ASW 2.0 Frame Generation.
 */
public class VREyeFramebuffer {
    private final String name;
    private int width;
    private int height;
    private int msaaSamples = 1;

    // Resolved standard FBO & textures
    private int framebufferId = -1;
    private int colorTextureId = -1;
    private int depthTextureId = -1;

    // Secondary FBO & texture for ping-pong post-processing (CAS)
    private int secondaryFramebufferId = -1;
    private int secondaryColorTextureId = -1;

    // Multisample MSAA FBO & renderbuffers
    private int msaaFramebufferId = -1;
    private int msaaColorRenderbufferId = -1;
    private int msaaDepthRenderbufferId = -1;

    public VREyeFramebuffer(String name, int width, int height) {
        this(name, width, height, 1);
    }

    public VREyeFramebuffer(String name, int width, int height, int msaaSamples) {
        this.name = name;
        this.width = width;
        this.height = height;
        int maxSamples = GL30.glGetInteger(org.lwjgl.opengl.GL30.GL_MAX_SAMPLES);
        this.msaaSamples = Math.min(Math.max(1, msaaSamples), Math.max(1, maxSamples));
    }

    public void ensureInitialized() {
        if (this.framebufferId != -1) return;
        this.init();
    }

    public void init() {
        RenderSystem.assertOnRenderThreadOrInit();
        this.destroy();

        // 1. Generate Resolved Standard FBO
        this.framebufferId = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferId);

        // 2. Generate Color Texture (RGBA8)
        this.colorTextureId = GlStateManager._genTexture();
        GlStateManager._bindTexture(this.colorTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, this.width, this.height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.colorTextureId, 0);

        // 3. Generate Depth Texture (24-bit Depth for Water, Fog, and OpenXR Depth Layer ASW 2.0)
        this.depthTextureId = GlStateManager._genTexture();
        GlStateManager._bindTexture(this.depthTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL14.GL_NONE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, this.width, this.height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (java.nio.ByteBuffer) null);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, this.depthTextureId, 0);

        // Validate Resolved FBO
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            this.destroy();
            throw new RuntimeException("FBO [" + this.name + "] incomplete: " + status);
        }

        // 4. Generate Secondary FBO for Ping-Pong Post-Processing (CAS Sharpening)
        this.secondaryFramebufferId = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.secondaryFramebufferId);

        this.secondaryColorTextureId = GlStateManager._genTexture();
        GlStateManager._bindTexture(this.secondaryColorTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, this.width, this.height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.secondaryColorTextureId, 0);

        int secStatus = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (secStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            this.destroy();
            throw new RuntimeException("Secondary FBO [" + this.name + "] incomplete: " + secStatus);
        }

        // 5. Generate Hardware Multisample FBO (MSAA) if samples > 1
        if (this.msaaSamples > 1) {
            this.msaaFramebufferId = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.msaaFramebufferId);

            this.msaaColorRenderbufferId = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.msaaColorRenderbufferId);
            GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, this.msaaSamples, GL11.GL_RGBA8, this.width, this.height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, this.msaaColorRenderbufferId);

            this.msaaDepthRenderbufferId = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.msaaDepthRenderbufferId);
            GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, this.msaaSamples, GL14.GL_DEPTH_COMPONENT24, this.width, this.height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, this.msaaDepthRenderbufferId);

            int msaaStatus = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (msaaStatus != GL30.GL_FRAMEBUFFER_COMPLETE) {
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                this.destroy();
                throw new RuntimeException("MSAA FBO [" + this.name + "] incomplete: " + msaaStatus);
            }
        }

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GlStateManager._bindTexture(0);
    }

    public void bindWrite(boolean setViewport) {
        this.ensureInitialized();
        RenderSystem.assertOnRenderThread();
        int targetFbo = (this.msaaSamples > 1 && this.msaaFramebufferId != -1) ? this.msaaFramebufferId : this.framebufferId;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);
        if (this.msaaSamples > 1) {
            GL11.glEnable(GL13.GL_MULTISAMPLE);
        } else {
            GL11.glDisable(GL13.GL_MULTISAMPLE);
        }
        if (setViewport) {
            GlStateManager._viewport(0, 0, this.width, this.height);
        }
    }

    public void unbindWrite() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /**
     * Resolves the hardware multisample renderbuffer into standard color and depth textures.
     */
    public void resolveMSAA() {
        if (this.msaaSamples > 1 && this.msaaFramebufferId != -1 && this.framebufferId != -1) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.msaaFramebufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.framebufferId);

            GL30.glBlitFramebuffer(
                    0, 0, this.width, this.height,
                    0, 0, this.width, this.height,
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );

            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
    }

    public void clear(float r, float g, float b, float a) {
        this.bindWrite(true);
        RenderSystem.clearColor(r, g, b, a);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, false);
    }

    public boolean isComplete() {
        if (this.framebufferId == -1) return false;
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferId);
        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        return complete;
    }

    public void resize(int newWidth, int newHeight, int newMsaaSamples) {
        if (this.width == newWidth && this.height == newHeight && this.msaaSamples == newMsaaSamples) return;
        RenderSystem.assertOnRenderThreadOrInit();
        if (GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == this.framebufferId ||
            GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING) == this.msaaFramebufferId) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
        this.width = newWidth;
        this.height = newHeight;
        this.msaaSamples = Math.max(1, newMsaaSamples);
        this.init();
    }

    public void resize(int newWidth, int newHeight) {
        this.resize(newWidth, newHeight, this.msaaSamples);
    }

    public int getFramebufferId() {
        return this.framebufferId;
    }

    public int getMsaaFramebufferId() {
        return this.msaaFramebufferId;
    }

    public int getActiveWriteFramebufferId() {
        return (this.msaaSamples > 1 && this.msaaFramebufferId != -1) ? this.msaaFramebufferId : this.framebufferId;
    }

    public int getColorTextureId() {
        return this.colorTextureId;
    }

    public int getDepthTextureId() {
        return this.depthTextureId;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getMsaaSamples() {
        return this.msaaSamples;
    }

    public String getName() {
        return this.name;
    }

    public void blitSecondaryToPrimary() {
        if (this.secondaryFramebufferId != -1 && this.framebufferId != -1) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.secondaryFramebufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.framebufferId);

            GL30.glBlitFramebuffer(
                    0, 0, this.width, this.height,
                    0, 0, this.width, this.height,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST
            );

            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
    }

    public int getSecondaryFramebufferId() {
        return this.secondaryFramebufferId;
    }

    public int getSecondaryColorTextureId() {
        return this.secondaryColorTextureId;
    }

    public void destroy() {
        RenderSystem.assertOnRenderThreadOrInit();
        if (this.msaaFramebufferId != -1) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(this.msaaFramebufferId);
            this.msaaFramebufferId = -1;
        }
        if (this.msaaColorRenderbufferId != -1) {
            GL30.glDeleteRenderbuffers(this.msaaColorRenderbufferId);
            this.msaaColorRenderbufferId = -1;
        }
        if (this.msaaDepthRenderbufferId != -1) {
            GL30.glDeleteRenderbuffers(this.msaaDepthRenderbufferId);
            this.msaaDepthRenderbufferId = -1;
        }
        if (this.secondaryFramebufferId != -1) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GlStateManager._glDeleteFramebuffers(this.secondaryFramebufferId);
            this.secondaryFramebufferId = -1;
        }
        if (this.secondaryColorTextureId != -1) {
            GlStateManager._deleteTexture(this.secondaryColorTextureId);
            this.secondaryColorTextureId = -1;
        }
        if (this.framebufferId != -1) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GlStateManager._glDeleteFramebuffers(this.framebufferId);
            this.framebufferId = -1;
        }
        if (this.colorTextureId != -1) {
            GlStateManager._deleteTexture(this.colorTextureId);
            this.colorTextureId = -1;
        }
        if (this.depthTextureId != -1) {
            GlStateManager._deleteTexture(this.depthTextureId);
            this.depthTextureId = -1;
        }
    }
}
