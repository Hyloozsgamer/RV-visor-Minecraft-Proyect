package com.rvvisor.core.data;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class VRDevicePose {
    private final Vector3f position = new Vector3f();
    private final Quaternionf orientation = new Quaternionf();
    private final Matrix4f matrix = new Matrix4f();
    private final Vector3f velocity = new Vector3f();
    private final Vector3f angularVelocity = new Vector3f();
    private boolean valid = false;

    public void set(Vector3f pos, Quaternionf rot) {
        this.position.set(pos);
        // Normaliza siempre, OpenXR/OpenVR a veces devuelve quats no normalizados
        this.orientation.set(rot).normalize();
        this.matrix.translationRotate(pos, this.orientation);
        this.valid = true;
    }

    public void set(Vector3f pos, Quaternionf rot, Vector3f vel, Vector3f angVel) {
        set(pos, rot);
        if (vel != null) this.velocity.set(vel);
        if (angVel != null) this.angularVelocity.set(angVel);
    }

    public void setFromMatrix(Matrix4f mat) {
        this.matrix.set(mat);
        mat.getTranslation(this.position);
        // getUnnormalizedRotation puede devolver quat con escala
        mat.getUnnormalizedRotation(this.orientation).normalize();
        this.valid = true;
    }

    public void setPosition(Vector3f pos) {
        set(pos, this.orientation);
    }

    public void setOrientation(Quaternionf rot) {
        set(this.position, rot);
    }

    public void copyFrom(VRDevicePose other) {
        if (other == null || !other.valid) {
            this.valid = false;
            return;
        }
        this.position.set(other.position);
        this.orientation.set(other.orientation);
        this.matrix.set(other.matrix);
        this.velocity.set(other.velocity);
        this.angularVelocity.set(other.angularVelocity);
        this.valid = true;
    }

    public void interpolate(VRDevicePose previous, VRDevicePose current, float alpha) {
        if (!previous.valid && !current.valid) {
            this.valid = false;
            return;
        }
        if (!previous.valid) {
            this.copyFrom(current);
            return;
        }
        if (!current.valid) {
            this.copyFrom(previous);
            return;
        }

        // Clamp alpha porque partialTicks a veces viene >1.0
        float t = Math.max(0f, Math.min(1f, alpha));

        previous.position.lerp(current.position, t, this.position);
        previous.orientation.slerp(current.orientation, t, this.orientation);
        this.orientation.normalize();

        previous.velocity.lerp(current.velocity, t, this.velocity);
        previous.angularVelocity.lerp(current.angularVelocity, t, this.angularVelocity);

        this.matrix.translationRotate(this.position, this.orientation);
        this.valid = true;
    }

    // --- Getters defensivos ---
    // Nunca devuelvas la referencia interna directa para modificaciones externas

    public Vector3f getPosition() {
        return new Vector3f(this.position);
    }

    // Para uso interno de alto rendimiento sin alloc
    public Vector3f getPositionRef() {
        return this.position;
    }

    public Quaternionf getOrientation() {
        return new Quaternionf(this.orientation);
    }

    public Matrix4f getMatrix() {
        return new Matrix4f(this.matrix);
    }

    public Matrix4f getMatrixRef() {
        return this.matrix;
    }

    public Vector3f getVelocity() {
        return new Vector3f(this.velocity);
    }

    public Vector3f getAngularVelocity() {
        return new Vector3f(this.angularVelocity);
    }

    public boolean isValid() {
        return this.valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public float getYawDegrees() {
        // Evita alloc: usa la matriz directamente
        // Forward neutral es (0, 0, -1) -> atan2(0, 1) = 0 grados (alineado con la vista del jugador)
        Vector3f fwd = getForwardVector();
        return (float) Math.toDegrees(Math.atan2(fwd.x, -fwd.z));
    }

    public float getPitchDegrees() {
        Vector3f fwd = getForwardVector();
        float len = fwd.length();
        if (len < 0.0001f) return 0.0f;
        return (float) Math.toDegrees(Math.asin(-fwd.y / len));
    }

    public float getRollDegrees() {
        return (float) Math.toDegrees(Math.atan2(this.matrix.m01(), this.matrix.m11()));
    }

    public Vector3f getForwardVector() {
        return this.matrix.transformDirection(new Vector3f(0, 0, -1), new Vector3f());
    }

    public Vector3f getUpVector() {
        return this.matrix.transformDirection(new Vector3f(0, 1, 0), new Vector3f());
    }

    public Vector3f getRightVector() {
        return this.matrix.transformDirection(new Vector3f(1, 0, 0), new Vector3f());
    }

    public void reset() {
        this.position.zero();
        this.orientation.identity();
        this.matrix.identity();
        this.velocity.zero();
        this.angularVelocity.zero();
        this.valid = false;
    }
}
