package com.angryalchemist.ecstatic.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

final class LodFarPlaneProjection {
    private static final float NEAR_PLANE_BLOCKS = 0.05F;

    private LodFarPlaneProjection() {
    }

    /**
     * Rebuilds the vanilla perspective projection with a much further far plane. Vanilla uses a reversed depth range
     * (near and far swapped) and a device-dependent clip range, so this mirrors {@code Projection#getMatrix}.
     */
    static Matrix4f withExtendedFarPlane(Matrix4f source, float farPlaneBlocks) {
        float h = 1.0F / source.m11();
        float aspect = source.m11() / source.m00();
        float fovyRadians = 2.0F * (float)Math.atan(h);
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        return new Matrix4f().setPerspective(fovyRadians, aspect, farPlaneBlocks, NEAR_PLANE_BLOCKS, zZeroToOne);
    }
}
