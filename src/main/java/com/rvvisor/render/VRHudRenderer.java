package com.rvvisor.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.rvvisor.RVVisorMod;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Bottom-Right "Sticker-Style" VR HUD Engine.
 * Renders a compact, sleek, non-intrusive holographic widget in the bottom-right of the visor.
 * Displays Hearts, Food, Armor, XP, and 9 Hotbar items with 100% crystal-clear transparency (ZERO black box).
 */
public class VRHudRenderer {
    private VREyeFramebuffer hudFramebuffer;
    private static final int HUD_TEX_WIDTH = 512;
    private static final int HUD_TEX_HEIGHT = 160;

    private float hudDistance = 1.15f;
    private float hudWidthMeters = 0.52f;  // Compact sticker width
    private float hudHeightMeters = 0.16f; // Compact sticker height

    public void init() {
        if (this.hudFramebuffer == null) {
            this.hudFramebuffer = new VREyeFramebuffer("VR_Sticker_HUD", HUD_TEX_WIDTH, HUD_TEX_HEIGHT);
        }
    }

    /**
     * Renders the compact sticker HUD with health, food, and hotbar into the transparent framebuffer.
     */
    public void updateHudTexture(Minecraft mc, DeltaTracker deltaTracker) {
        if (mc == null || mc.player == null || mc.level == null) return;
        if (mc.options.hideGui || mc.screen != null) return;

        try {
            this.init();
            this.hudFramebuffer.ensureInitialized();

            this.hudFramebuffer.bindWrite(true);
            this.hudFramebuffer.clear(0.0f, 0.0f, 0.0f, 0.0f); // 100% Transparent backdrop — ZERO black box

            RenderSystem.viewport(0, 0, HUD_TEX_WIDTH, HUD_TEX_HEIGHT);

            Matrix4f ortho = new Matrix4f().setOrtho(0.0F, (float) HUD_TEX_WIDTH, (float) HUD_TEX_HEIGHT, 0.0F, -1000.0F, 1000.0F);
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
            Player player = mc.player;
            Font font = mc.font;

            // 1. Health Hearts (❤️)
            int health = (int) Math.ceil(player.getHealth());
            int heartStartX = 14;
            int heartY = 10;
            for (int i = 0; i < 10; i++) {
                int hVal = (i + 1) * 2;
                String heartSymbol = (health >= hVal) ? "§c❤" : ((health >= hVal - 1) ? "§e❤" : "§8❤");
                guiGraphics.drawString(font, heartSymbol, heartStartX + (i * 12), heartY, 0xFFFFFF, true);
            }

            // 2. Hunger Food (🍗)
            int food = player.getFoodData().getFoodLevel();
            int foodStartX = 155;
            for (int i = 0; i < 10; i++) {
                int fVal = (i + 1) * 2;
                String foodSymbol = (food >= fVal) ? "§6🍖" : ((food >= fVal - 1) ? "§e🍖" : "§8🍖");
                guiGraphics.drawString(font, foodSymbol, foodStartX + (i * 12), heartY, 0xFFFFFF, true);
            }

            // 3. Armor & Level Status
            int armor = player.getArmorValue();
            if (armor > 0) {
                guiGraphics.drawString(font, "§b🛡" + armor, 290, heartY, 0x55FFFF, true);
            }
            int xpLevel = player.experienceLevel;
            if (xpLevel > 0) {
                guiGraphics.drawString(font, "§aLv." + xpLevel, 345, heartY, 0x55FF55, true);
            }

            // 4. 9-Slot Hotbar with 3D Items (Pure transparent slot frames)
            int selectedSlot = player.getInventory().selected;
            int slotStartX = 10;
            int slotY = 32;
            int slotSize = 54;

            for (int i = 0; i < 9; i++) {
                int curSlotX = slotStartX + (i * slotSize);
                boolean isSelected = (i == selectedSlot);

                // Highlight active slot
                if (isSelected) {
                    guiGraphics.fill(curSlotX, slotY, curSlotX + 48, slotY + 48, 0x5500E5FF);
                    guiGraphics.renderOutline(curSlotX - 1, slotY - 1, 50, 50, 0xFF00FFFF);
                } else {
                    guiGraphics.renderOutline(curSlotX, slotY, 48, 48, 0x884B5563);
                }

                // Render Item
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    guiGraphics.renderItem(stack, curSlotX + 16, slotY + 16);
                    guiGraphics.renderItemDecorations(font, stack, curSlotX + 16, slotY + 16);
                }
            }

            guiGraphics.flush();

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            GlStateManager._enableDepthTest();

            this.hudFramebuffer.unbindWrite();
            mc.getMainRenderTarget().bindWrite(true);
        } catch (Throwable t) {
            RVVisorMod.LOGGER.error("[RV-Visor] Error in updateHudTexture", t);
        }
    }

    /**
     * Renders the Sticker HUD positioned in the bottom-right corner of the visor view.
     */
    public void renderHudInWorld(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (this.hudFramebuffer == null || !this.hudFramebuffer.isComplete()) return;

        try {
            float dist = this.hudDistance;
            float halfW = this.hudWidthMeters / 2.0f;
            float halfH = this.hudHeightMeters / 2.0f;

            // Sticker Position: Bottom-Right of the visor lenses
            float posX = 0.38f;
            float posY = -0.32f;

            org.joml.Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            GlStateManager._disableDepthTest();
            GlStateManager._disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, this.hudFramebuffer.getColorTextureId());

            com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
            com.mojang.blaze3d.vertex.BufferBuilder buffer = tesselator.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);

            buffer.addVertex(posX - halfW, posY + halfH, -dist).setUv(0.0f, 1.0f).setColor(1.0f, 1.0f, 1.0f, 0.95f);
            buffer.addVertex(posX - halfW, posY - halfH, -dist).setUv(0.0f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 0.95f);
            buffer.addVertex(posX + halfW, posY - halfH, -dist).setUv(1.0f, 0.0f).setColor(1.0f, 1.0f, 1.0f, 0.95f);
            buffer.addVertex(posX + halfW, posY + halfH, -dist).setUv(1.0f, 1.0f).setColor(1.0f, 1.0f, 1.0f, 0.95f);

            com.mojang.blaze3d.vertex.MeshData meshData = buffer.build();
            if (meshData != null) {
                com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(meshData);
            }

            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            GlStateManager._enableCull();
            GlStateManager._enableDepthTest();
        } catch (Throwable t) {
            RVVisorMod.LOGGER.error("[RV-Visor] Error in renderHudInWorld", t);
        }
    }

    public void destroy() {
        if (this.hudFramebuffer != null) {
            this.hudFramebuffer.destroy();
            this.hudFramebuffer = null;
        }
    }
}
