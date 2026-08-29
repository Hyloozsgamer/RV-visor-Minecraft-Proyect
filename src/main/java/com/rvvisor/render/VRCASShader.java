package com.rvvisor.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rvvisor.RVVisorMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class VRCASShader {
    private static final String DEFAULT_VSH = """
            #version 150
            in vec2 Position;
            in vec2 UV;
            out vec2 vUV;
            void main() {
                vUV = UV;
                gl_Position = vec4(Position, 0.0, 1.0);
            }
            """;

    private static final String DEFAULT_FSH = """
            #version 150
            uniform sampler2D uTexture;
            uniform vec2 uInvResolution;
            uniform float uSharpness;
            in vec2 vUV;
            out vec4 fragColor;
            void main() {
                vec3 a = texture(uTexture, vUV + vec2(0.0, -uInvResolution.y)).rgb;
                vec3 b = texture(uTexture, vUV + vec2(-uInvResolution.x, 0.0)).rgb;
                vec3 c = texture(uTexture, vUV).rgb;
                vec3 d = texture(uTexture, vUV + vec2(uInvResolution.x, 0.0)).rgb;
                vec3 e = texture(uTexture, vUV + vec2(0.0, uInvResolution.y)).rgb;
                float lA = dot(a, vec3(0.2126, 0.7152, 0.0722));
                float lB = dot(b, vec3(0.2126, 0.7152, 0.0722));
                float lC = dot(c, vec3(0.2126, 0.7152, 0.0722));
                float lD = dot(d, vec3(0.2126, 0.7152, 0.0722));
                float lE = dot(e, vec3(0.2126, 0.7152, 0.0722));
                float minL = min(min(min(lA, lB), min(lD, lE)), lC);
                float maxL = max(max(max(lA, lB), max(lD, lE)), lC);
                float sharp = sqrt(min(1.0 - maxL, minL) / max(maxL, 0.00001));
                float w = sharp * mix(-0.125, -0.2, clamp(uSharpness, 0.0, 1.0));
                vec3 res = (a*w + b*w + d*w + e*w + c) / (1.0 + 4.0*w);
                fragColor = vec4(clamp(res, 0.0, 1.0), 1.0);
            }
            """;

    private int programId = 0;
    private int vaoId = 0;
    private int vboId = 0;
    private int uTextureLoc = -1, uInvResolutionLoc = -1, uSharpnessLoc = -1;
    private boolean initialized = false, failed = false;

    public void ensureInitialized() {
        if (this.initialized || this.failed) return;
        RenderSystem.assertOnRenderThread();
        try {
            String vshSource = loadShaderSource("/assets/rvvisor/shaders/cas/rvvisor_cas.vsh", DEFAULT_VSH);
            String fshSource = loadShaderSource("/assets/rvvisor/shaders/cas/rvvisor_cas.fsh", DEFAULT_FSH);

            int vsh = compileShader(GL20.GL_VERTEX_SHADER, vshSource);
            int fsh = compileShader(GL20.GL_FRAGMENT_SHADER, fshSource);

            this.programId = GL20.glCreateProgram();
            GL20.glAttachShader(this.programId, vsh);
            GL20.glAttachShader(this.programId, fsh);
            GL20.glBindAttribLocation(this.programId, 0, "Position");
            GL20.glBindAttribLocation(this.programId, 1, "UV");
            GL20.glLinkProgram(this.programId);

            if (GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                RVVisorMod.LOGGER.error("[CAS] Link fail: {}", GL20.glGetProgramInfoLog(this.programId));
                this.failed = true;
                return;
            }
            GL20.glDeleteShader(vsh);
            GL20.glDeleteShader(fsh);

            this.uTextureLoc = GL20.glGetUniformLocation(this.programId, "uTexture");
            this.uInvResolutionLoc = GL20.glGetUniformLocation(this.programId, "uInvResolution");
            this.uSharpnessLoc = GL20.glGetUniformLocation(this.programId, "uSharpness");

            // Quad
            float[] quad = {-1,1,0,1, -1,-1,0,0, 1,-1,1,0, -1,1,0,1, 1,-1,1,0, 1,1,1,1};
            this.vaoId = GL30.glGenVertexArrays();
            this.vboId = GL15.glGenBuffers();
            GL30.glBindVertexArray(this.vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quad, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 16, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 16, 8);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            this.initialized = true;
        } catch (Exception e) {
            RVVisorMod.LOGGER.error("[CAS] init fail", e);
            this.failed = true;
        }
    }

    public void render(int sourceTextureId, int targetFboId, int width, int height, float sharpness) {
        if (!this.initialized) ensureInitialized();
        if (!this.initialized || this.failed) return;
        RenderSystem.assertOnRenderThread();

        int prevFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFboId);
        GlStateManager._viewport(0, 0, width, height);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableScissor();

        GL20.glUseProgram(this.programId);

        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(sourceTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        if (this.uTextureLoc >= 0) GL20.glUniform1i(this.uTextureLoc, 0);
        if (this.uInvResolutionLoc >= 0) GL20.glUniform2f(this.uInvResolutionLoc, 1.0f / (float) width, 1.0f / (float) height);
        if (this.uSharpnessLoc >= 0) GL20.glUniform1f(this.uSharpnessLoc, sharpness);

        GL30.glBindVertexArray(this.vaoId);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        // Restore
        GL20.glUseProgram(0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        // Do not force scissor, let Minecraft decide
    }

    private int compileShader(int type, String src) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, src);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException(GL20.glGetShaderInfoLog(id));
        }
        return id;
    }

    private String loadShaderSource(String resourcePath, String defaultFallback) {
        try (InputStream in = VRCASShader.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
        return defaultFallback;
    }

    public void destroy() {
        if (this.vboId != 0) {
            GL15.glDeleteBuffers(this.vboId);
            this.vboId = 0;
        }
        if (this.vaoId != 0) {
            GL30.glDeleteVertexArrays(this.vaoId);
            this.vaoId = 0;
        }
        if (this.programId != 0) {
            GL20.glDeleteProgram(this.programId);
            this.programId = 0;
        }
        this.initialized = false;
    }
}
