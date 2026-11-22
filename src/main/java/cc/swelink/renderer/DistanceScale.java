package cc.swelink.renderer;

import org.joml.Matrix4fc;

public final class DistanceScale {
    private static final float REFERENCE_ZOOM = (float) (1 / Math.tan(Math.toRadians(35)));

    private DistanceScale() {
    }

    public static float atDepth(float depth) {
        return atDepth(depth, 1.5f);
    }

    public static float atDepth(float depth, float maximum) {
        return Math.clamp(5f / Math.max(depth, 0.1f), 0.15f, maximum);
    }

    public static float depth(float w, Matrix4fc viewProjection) {
        float zoom = (float) Math.sqrt(viewProjection.m01() * viewProjection.m01()
                + viewProjection.m11() * viewProjection.m11() + viewProjection.m21() * viewProjection.m21());
        return zoom > 1.0e-4f ? w * REFERENCE_ZOOM / zoom : w;
    }
}
