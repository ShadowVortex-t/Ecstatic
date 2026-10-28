package com.angryalchemist.ecstatic.render;

import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Sun direction helper. (The old CPU-shaded ocean plane was never drawn and has been removed.) */
public final class LodOceanPlane {
    private LodOceanPlane() {
    }

    static Vector3f sunDirection(float sunAngleDeg) {
        Matrix4f sunRot = new Matrix4f().rotate(Axis.YP.rotationDegrees(-90.0F)).rotate(Axis.XP.rotationDegrees(sunAngleDeg));
        Vector4f sunDir4 = sunRot.transform(new Vector4f(0.0F, 1.0F, 0.0F, 0.0F));
        return new Vector3f(sunDir4.x, sunDir4.y, sunDir4.z).normalize();
    }
}
