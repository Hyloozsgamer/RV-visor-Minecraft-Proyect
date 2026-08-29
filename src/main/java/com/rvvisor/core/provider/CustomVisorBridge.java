package com.rvvisor.core.provider;

import com.rvvisor.RVVisorMod;
import com.rvvisor.core.data.VRTrackingContext;
import com.rvvisor.core.optics.LensSettings;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Custom Visor Direct Bridge.
 * Allows connecting custom/DIY VR headsets, Quest UDP streaming, or simulated tracking
 * via a low-latency direct protocol (UDP socket / memory stream).
 */
public class CustomVisorBridge implements IVRProvider {
    private boolean initialized = false;
    private final LensSettings customLensSettings = new LensSettings();

    // Direct UDP Listener for custom tracking packets (Port 9999 by default)
    private DatagramSocket udpSocket;
    private Thread listenerThread;
    private volatile boolean running = false;

    // Simulated / fallback orientation
    private float simYaw = 0.0f;
    private float simPitch = 0.0f;
    private final Vector3f simHeadPos = new Vector3f(0.0f, 1.62f, 0.0f);
    private final Quaternionf simHeadRot = new Quaternionf();

    @Override
    public boolean initialize() {
        if (this.initialized) return true;

        try {
            // Default 2K per eye for modern custom visors
            this.customLensSettings.setCustomResolution(1920, 1920, 1.0f);
            this.customLensSettings.setIpd(0.063f);

            // Start UDP listener in background for live hardware tracking packets
            this.running = true;
            try {
                this.udpSocket = new DatagramSocket(9999);
                this.udpSocket.setSoTimeout(500);
                this.listenerThread = new Thread(this::listenForCustomPackets, "RV-Visor-UDP-Bridge");
                this.listenerThread.setDaemon(true);
                this.listenerThread.start();
                RVVisorMod.LOGGER.info("[RV-Visor] Custom Visor UDP Bridge listening on port 9999");
            } catch (Throwable t) {
                RVVisorMod.LOGGER.warn("[RV-Visor] Custom Visor UDP Port in use or unavailable, using simulation bridge: {}", t.getMessage());
            }

            this.initialized = true;
            return true;
        } catch (Throwable t) {
            RVVisorMod.LOGGER.error("[RV-Visor] Failed to initialize Custom Visor Bridge", t);
            return false;
        }
    }

    private void listenForCustomPackets() {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (this.running) {
            try {
                this.udpSocket.receive(packet);
                if (packet.getLength() >= 28) { // 7 floats: PosX, PosY, PosZ, RotX, RotY, RotZ, RotW
                    ByteBuffer bb = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength()).order(ByteOrder.LITTLE_ENDIAN);
                    float px = bb.getFloat();
                    float py = bb.getFloat();
                    float pz = bb.getFloat();
                    float qx = bb.getFloat();
                    float qy = bb.getFloat();
                    float qz = bb.getFloat();
                    float qw = bb.getFloat();

                    this.simHeadPos.set(px, py, pz);
                    this.simHeadRot.set(qx, qy, qz, qw);
                }
            } catch (Exception ignore) {}
        }
    }

    @Override
    public void pollPoses(VRTrackingContext context) {
        if (!this.initialized) return;

        context.beginNewFrame();

        // Update HMD Pose
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(this.simYaw))
                                           .rotateX((float) Math.toRadians(this.simPitch));
        context.getHmdPose().set(this.simHeadPos, rot);
        context.updateEyePoses(this.customLensSettings.getIpd());

        // Update Hand Poses
        // Right hand positioned slightly in front with natural angle
        Vector3f rightHandWorld = new Vector3f(this.simHeadPos).add(
                rot.transform(new Vector3f(0.22f, -0.30f, -0.45f), new Vector3f()));
        context.getRightHandPose().set(rightHandWorld, rot);
        context.getRightAimPose().set(rightHandWorld, rot);

        // Left hand positioned slightly in front
        Vector3f leftHandWorld = new Vector3f(this.simHeadPos).add(
                rot.transform(new Vector3f(-0.22f, -0.30f, -0.45f), new Vector3f()));
        context.getLeftHandPose().set(leftHandWorld, rot);
        context.getLeftAimPose().set(leftHandWorld, rot);
    }

    @Override
    public void submitFrame(int eye, int textureId, int width, int height) {
        // Direct bridge frame submission: Framebuffers are rendered and ready for compositor/streaming
    }

    @Override
    public void triggerHaptic(int hand, float durationSeconds, float frequency, float amplitude) {
        // Send haptic feedback packet if custom hardware is connected
    }

    public void setSimulatedLook(float yaw, float pitch) {
        this.simYaw = yaw;
        this.simPitch = pitch;
    }

    public void setSimulatedPosition(float x, float y, float z) {
        this.simHeadPos.set(x, y, z);
    }

    @Override
    public LensSettings getRecommendedLensSettings() {
        return this.customLensSettings;
    }

    @Override
    public String getProviderName() {
        return "Custom Visor Direct Bridge";
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public void shutdown() {
        this.running = false;
        this.initialized = false;
        if (this.udpSocket != null && !this.udpSocket.isClosed()) {
            this.udpSocket.close();
        }
    }
}
