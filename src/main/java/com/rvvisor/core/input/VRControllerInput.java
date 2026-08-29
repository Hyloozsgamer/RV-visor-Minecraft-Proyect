package com.rvvisor.core.input;

import com.rvvisor.RVVisorMod;
import com.rvvisor.core.provider.IVRProvider;
import com.rvvisor.core.provider.OpenXRProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.gui.screens.PauseScreen;
import org.lwjgl.openvr.OpenVR;
import org.lwjgl.openvr.VRControllerAxis;
import org.lwjgl.openvr.VRControllerState;

import static org.lwjgl.openvr.VR.*;
import static org.lwjgl.openvr.VRSystem.*;

/**
 * High-performance VR Controller Input Engine.
 * Supports OpenXR and OpenVR runtimes with analog smooth locomotion,
 * snap/smooth turning, jump, sneak, mine, place, and menu interaction.
 * Completely null-safe and crash-proof when runtimes are not loaded.
 */
public class VRControllerInput {
    private final VRControllerState leftState = VRControllerState.calloc();
    private final VRControllerState rightState = VRControllerState.calloc();

    // Analog stick states
    private float moveStickX = 0.0f;
    private float moveStickY = 0.0f;
    private float turnStickX = 0.0f;
    private float turnStickY = 0.0f;

    // Digital button states
    private boolean isJumping = false;
    private boolean isSneaking = false;
    private boolean isSprinting = false;
    private boolean isAttacking = false;
    private boolean isUsingItem = false;
    private boolean isMenuPressed = false;


    public void pollControllers() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        try {
            RVVisorMod mod = RVVisorMod.getInstance();
            if (mod == null || !mod.isVrActive()) return;

            IVRProvider provider = mod.getVrProvider();

            // 1. OpenXR Input Polling
            if (provider instanceof OpenXRProvider openXRProvider) {
                openXRProvider.pollInput(this);
                this.updateMinecraftActions(mc);
                return;
            }

            // 2. OpenVR / SteamVR Input Polling (Protected against null VRSystem)
            if (OpenVR.VRSystem != null) {
                this.pollOpenVRControllers(mc);
                this.updateMinecraftActions(mc);
            }
        } catch (Throwable t) {
            // Guard against any runtime exceptions during input polling
        }
    }

    private boolean isInvPressed = false;
    private boolean wasHotbarStickTilted = false;

    private void pollOpenVRControllers(Minecraft mc) {
        if (OpenVR.VRSystem == null) return;

        // Left Controller (Movement & Utility)
        int leftIndex = VRSystem_GetTrackedDeviceIndexForControllerRole(ETrackedControllerRole_TrackedControllerRole_LeftHand);
        if (leftIndex != k_unTrackedDeviceIndexInvalid && leftIndex < k_unMaxTrackedDeviceCount) {
            if (VRSystem_GetControllerState(leftIndex, this.leftState)) {
                VRControllerAxis joystick = this.leftState.rAxis(0);
                this.moveStickX = Math.abs(joystick.x()) > 0.15f ? joystick.x() : 0.0f;
                this.moveStickY = Math.abs(joystick.y()) > 0.15f ? joystick.y() : 0.0f;

                long buttons = this.leftState.ulButtonPressed();
                this.isSprinting = (buttons & (1L << EVRButtonId_k_EButton_SteamVR_Touchpad)) != 0;
                this.isSneaking = (buttons & (1L << EVRButtonId_k_EButton_Grip)) != 0;

                // 1. Menu / Pause button (Y Button on Meta Quest / ApplicationMenu)
                boolean menuBtn = (buttons & (1L << EVRButtonId_k_EButton_ApplicationMenu)) != 0;
                if (menuBtn && !this.isMenuPressed) {
                    this.isMenuPressed = true;
                    mc.execute(() -> {
                        if (mc.screen == null) {
                            mc.setScreen(new PauseScreen(true));
                        } else {
                            mc.setScreen(null);
                        }
                    });
                } else if (!menuBtn) {
                    this.isMenuPressed = false;
                }

                // 2. Inventory button (X Button on Meta Quest = k_EButton_A on left controller)
                boolean invBtn = (buttons & (1L << EVRButtonId_k_EButton_A)) != 0;
                if (invBtn && !this.isInvPressed) {
                    this.isInvPressed = true;
                    mc.execute(() -> {
                        if (mc.screen == null) {
                            if (mc.player != null) {
                                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                            }
                        } else {
                            if (mc.player != null) {
                                mc.player.closeContainer();
                            }
                            mc.setScreen(null);
                        }
                    });
                } else if (!invBtn) {
                    this.isInvPressed = false;
                }
            }
        }

        // Right Controller (Turning, Jump, Action)
        int rightIndex = VRSystem_GetTrackedDeviceIndexForControllerRole(ETrackedControllerRole_TrackedControllerRole_RightHand);
        if (rightIndex != k_unTrackedDeviceIndexInvalid && rightIndex < k_unMaxTrackedDeviceCount) {
            if (VRSystem_GetControllerState(rightIndex, this.rightState)) {
                VRControllerAxis joystick = this.rightState.rAxis(0);
                this.turnStickX = Math.abs(joystick.x()) > 0.12f ? joystick.x() : 0.0f;
                this.turnStickY = Math.abs(joystick.y()) > 0.12f ? joystick.y() : 0.0f;

                long buttons = this.rightState.ulButtonPressed();
                // A Button -> Jump
                this.isJumping = (buttons & (1L << EVRButtonId_k_EButton_A)) != 0;

                // Triggers (Attack / Mine & Place / Use)
                VRControllerAxis trigger = this.rightState.rAxis(1);
                this.isAttacking = trigger.x() > 0.5f || (buttons & (1L << EVRButtonId_k_EButton_SteamVR_Trigger)) != 0;
                this.isUsingItem = (buttons & (1L << EVRButtonId_k_EButton_Grip)) != 0;
            }
        }
    }

    // Smooth look sensitivity: degrees per tick at full stick deflection
    private static final float LOOK_SENSITIVITY = 3.5f;

    public void updateMinecraftActions(Minecraft mc) {
        if (mc == null) return;

        if (mc.options != null) {
            mc.options.keyAttack.setDown(this.isAttacking);
            mc.options.keyUse.setDown(this.isUsingItem);
            mc.options.keySprint.setDown(this.isSprinting);
        }

        // Smooth look with right joystick (clean, pure rotation without accidental hotbar switching)
        if (mc.player != null && mc.screen == null) {
            // Hotbar cycling: hold Left Controller Grip (Sneak) + tilt right stick, OR dedicated hotbar inputs
            if (this.isSneaking && Math.abs(this.turnStickX) > 0.60f) {
                if (!this.wasHotbarStickTilted) {
                    int delta = (this.turnStickX > 0) ? 1 : -1;
                    int next = (mc.player.getInventory().selected + delta + 9) % 9;
                    mc.player.getInventory().selected = next;
                    this.wasHotbarStickTilted = true;
                }
            } else {
                if (Math.abs(this.turnStickX) < 0.35f) {
                    this.wasHotbarStickTilted = false;
                }
                // Pure smooth rotation
                if (this.turnStickX != 0.0f || this.turnStickY != 0.0f) {
                    float deltaYaw   =  this.turnStickX * LOOK_SENSITIVITY;
                    float deltaPitch = -this.turnStickY * LOOK_SENSITIVITY;
                    mc.player.setYRot(mc.player.getYRot() + deltaYaw);
                    float newPitch = mc.player.getXRot() + deltaPitch;
                    mc.player.setXRot(Math.max(-90.0f, Math.min(90.0f, newPitch)));
                }
            }
        }
    }

    /**
     * Injects the VR analog movement and buttons into Minecraft 1.21.1 Input tick.
     */
    public void applyToPlayerInput(Input input) {
        if (!RVVisorMod.getInstance().isVrActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen != null) {
            // Freeze movement completely when any menu/inventory is open
            input.forwardImpulse = 0.0f;
            input.leftImpulse = 0.0f;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
            return;
        }

        boolean moving = (this.moveStickX != 0.0f || this.moveStickY != 0.0f);
        if (moving) {
            input.forwardImpulse = this.moveStickY;
            input.leftImpulse = -this.moveStickX;
            input.up = this.moveStickY > 0.2f;
            input.down = this.moveStickY < -0.2f;
            input.left = this.moveStickX < -0.2f;
            input.right = this.moveStickX > 0.2f;
        }

        if (this.isJumping) {
            input.jumping = true;
        }
        if (this.isSneaking) {
            input.shiftKeyDown = true;
        }
    }

    public void setMoveStick(float x, float y) {
        this.moveStickX = Math.abs(x) > 0.15f ? x : 0.0f;
        this.moveStickY = Math.abs(y) > 0.15f ? y : 0.0f;
    }

    public void setTurnStickX(float x) {
        this.turnStickX = x;
    }

    public void setTurnStickY(float y) {
        this.turnStickY = y;
    }

    public float getTurnStickX() {
        return this.turnStickX;
    }

    public float getTurnStickY() {
        return this.turnStickY;
    }

    public boolean isAttacking() {
        return this.isAttacking;
    }

    public boolean isUsingItem() {
        return this.isUsingItem;
    }

    public boolean isJumping() {
        return this.isJumping;
    }

    public boolean isSneaking() {
        return this.isSneaking;
    }

    public void setJumping(boolean jumping) {
        this.isJumping = jumping;
    }

    public void setSneaking(boolean sneaking) {
        this.isSneaking = sneaking;
    }

    public void setSprinting(boolean sprinting) {
        this.isSprinting = sprinting;
    }

    public void setAttacking(boolean attacking) {
        this.isAttacking = attacking;
    }

    public void setUsingItem(boolean usingItem) {
        this.isUsingItem = usingItem;
    }

    public void setMenuPressed(boolean menuPressed) {
        this.isMenuPressed = menuPressed;
    }

    public void destroy() {
        this.leftState.free();
        this.rightState.free();
    }
}
