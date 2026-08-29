package com.rvvisor.core.data;

import com.rvvisor.core.optics.LensSettings;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class VRTrackingContext {
    private final VRDevicePose hmdPose = new VRDevicePose();
    private final VRDevicePose leftEyePose = new VRDevicePose();
    private final VRDevicePose rightEyePose = new VRDevicePose();
    private final VRDevicePose leftHandPose = new VRDevicePose();
    private final VRDevicePose rightHandPose = new VRDevicePose();
    private final VRDevicePose leftAimPose = new VRDevicePose();
    private final VRDevicePose rightAimPose = new VRDevicePose();

    private final VRDevicePose prevHmdPose = new VRDevicePose();
    private final VRDevicePose prevLeftEyePose = new VRDevicePose();
    private final VRDevicePose prevRightEyePose = new VRDevicePose();
    private final VRDevicePose prevLeftHandPose = new VRDevicePose();
    private final VRDevicePose prevRightHandPose = new VRDevicePose();
    private final VRDevicePose prevLeftAimPose = new VRDevicePose();
    private final VRDevicePose prevRightAimPose = new VRDevicePose();

    private final VRDevicePose renderHmdPose = new VRDevicePose();
    private final VRDevicePose renderLeftEyePose = new VRDevicePose();
    private final VRDevicePose renderRightEyePose = new VRDevicePose();
    private final VRDevicePose renderLeftHandPose = new VRDevicePose();
    private final VRDevicePose renderRightHandPose = new VRDevicePose();
    private final VRDevicePose renderLeftAimPose = new VRDevicePose();
    private final VRDevicePose renderRightAimPose = new VRDevicePose();

    private final Vector3f roomOrigin = new Vector3f(0, 0, 0);
    private float roomYaw = 0.0f;
    private float worldScale = 1.0f;
    private float currentIpd = 0.063f;

    public VRTrackingContext() {
        this.hmdPose.setPosition(new Vector3f(0, 1.62f, 0));
        this.hmdPose.setValid(true);
        // Inicializa prev = current para no tener glitch en frame 1
        this.prevHmdPose.copyFrom(this.hmdPose);
        updateEyePoses(this.currentIpd);
        this.prevLeftEyePose.copyFrom(this.leftEyePose);
        this.prevRightEyePose.copyFrom(this.rightEyePose);
        this.renderHmdPose.copyFrom(this.hmdPose);
        this.renderLeftEyePose.copyFrom(this.leftEyePose);
        this.renderRightEyePose.copyFrom(this.rightEyePose);
    }

    public void beginNewFrame() {
        this.prevHmdPose.copyFrom(this.hmdPose);
        this.prevLeftEyePose.copyFrom(this.leftEyePose);
        this.prevRightEyePose.copyFrom(this.rightEyePose);
        this.prevLeftHandPose.copyFrom(this.leftHandPose);
        this.prevRightHandPose.copyFrom(this.rightHandPose);
        this.prevLeftAimPose.copyFrom(this.leftAimPose);
        this.prevRightAimPose.copyFrom(this.rightAimPose);
    }

    // LLAMAR ESTO DESPUES DE QUE vrProvider ESCRIBA hmdPose
    public void onPosesUpdated(float ipd) {
        this.currentIpd = ipd;
        updateEyePoses(ipd);
    }

    public void updateInterpolatedPoses(float partialTicks) {
        this.renderHmdPose.interpolate(this.prevHmdPose, this.hmdPose, partialTicks);
        this.renderLeftEyePose.interpolate(this.prevLeftEyePose, this.leftEyePose, partialTicks);
        this.renderRightEyePose.interpolate(this.prevRightEyePose, this.rightEyePose, partialTicks);
        this.renderLeftHandPose.interpolate(this.prevLeftHandPose, this.leftHandPose, partialTicks);
        this.renderRightHandPose.interpolate(this.prevRightHandPose, this.rightHandPose, partialTicks);
        this.renderLeftAimPose.interpolate(this.prevLeftAimPose, this.leftAimPose, partialTicks);
        this.renderRightAimPose.interpolate(this.prevRightAimPose, this.rightAimPose, partialTicks);
    }

    public void updateEyePoses(float ipd) {
        float halfIpd = ipd * 0.5f;
        // Usa cuaternion, no la matriz completa con traslacion
        Quaternionf rot = this.hmdPose.getOrientation();
        Vector3f leftOffset = rot.transform(new Vector3f(-halfIpd, 0, 0));
        Vector3f rightOffset = rot.transform(new Vector3f(halfIpd, 0, 0));

        this.leftEyePose.set(new Vector3f(this.hmdPose.getPosition()).add(leftOffset), new Quaternionf(rot));
        this.rightEyePose.set(new Vector3f(this.hmdPose.getPosition()).add(rightOffset), new Quaternionf(rot));
    }

    public void recenter() {
        this.roomOrigin.set(this.hmdPose.getPosition().x, 0, this.hmdPose.getPosition().z);
        this.roomYaw = this.hmdPose.getYawDegrees();
    }

    public Matrix4f getEyeViewMatrix(int eye) {
        VRDevicePose eyePose = getRenderEyePose(eye);
        // View = inverse(eyePose)
        Matrix4f view = new Matrix4f(eyePose.getMatrix()).invert();

        // Aplica Room Origin + Yaw + WorldScale
        if (this.roomOrigin.lengthSquared() > 0.001f || this.roomYaw != 0) {
            view.translate(this.roomOrigin.x, this.roomOrigin.y, this.roomOrigin.z);
            view.rotateY((float) Math.toRadians(-this.roomYaw));
        }
        if (this.worldScale != 1.0f) {
            view.scale(this.worldScale);
        }
        return view;
    }

    public Vector3f getEyePosition(int eye) {
        return new Vector3f(getRenderEyePose(eye).getPosition()); // copia defensiva
    }

    public VRDevicePose getRenderEyePose(int eye) {
        return (eye == LensSettings.EYE_LEFT) ? this.renderLeftEyePose : this.renderRightEyePose;
    }

    public VRDevicePose getHmdPose() {
        return this.hmdPose;
    }

    public VRDevicePose getLeftEyePose() {
        return this.leftEyePose;
    }

    public VRDevicePose getRightEyePose() {
        return this.rightEyePose;
    }

    public VRDevicePose getLeftHandPose() {
        return this.leftHandPose;
    }

    public VRDevicePose getRightHandPose() {
        return this.rightHandPose;
    }

    public VRDevicePose getLeftAimPose() {
        return this.leftAimPose;
    }

    public VRDevicePose getRightAimPose() {
        return this.rightAimPose;
    }

    public VRDevicePose getRenderHmdPose() {
        return this.renderHmdPose;
    }

    public VRDevicePose getRenderLeftEyePose() {
        return this.renderLeftEyePose;
    }

    public VRDevicePose getRenderRightEyePose() {
        return this.renderRightEyePose;
    }

    public VRDevicePose getRenderLeftHandPose() {
        return this.renderLeftHandPose;
    }

    public VRDevicePose getRenderRightHandPose() {
        return this.renderRightHandPose;
    }

    public VRDevicePose getRenderLeftAimPose() {
        return this.renderLeftAimPose;
    }

    public VRDevicePose getRenderRightAimPose() {
        return this.renderRightAimPose;
    }

    public Vector3f getRoomOrigin() {
        return this.roomOrigin;
    }

    public float getRoomYaw() {
        return this.roomYaw;
    }

    public void setRoomYaw(float roomYaw) {
        this.roomYaw = roomYaw;
    }

    public float getWorldScale() {
        return this.worldScale;
    }

    public void setWorldScale(float worldScale) {
        this.worldScale = Math.max(0.1f, worldScale);
    }
}
