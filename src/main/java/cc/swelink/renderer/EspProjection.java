package cc.swelink.renderer;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class EspProjection {
    public record Bounds(float x, float y, float width, float height) {
    }

    public static Bounds project(AABB box, Vec3 camera, Matrix4fc matrix, float width, float height) {
        Vector4f[] corners = new Vector4f[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = matrix.transform(new Vector4f(
                    (float) (((i & 1) == 0 ? box.minX : box.maxX) - camera.x),
                    (float) (((i & 2) == 0 ? box.minY : box.maxY) - camera.y),
                    (float) (((i & 4) == 0 ? box.minZ : box.maxZ) - camera.z), 1));
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            for (int bit : new int[]{1, 2, 4}) {
                if ((i & bit) != 0) {
                    continue;
                }
                Vector4f a = new Vector4f(corners[i]);
                Vector4f b = new Vector4f(corners[i | bit]);
                float da = a.w - ScreenSpaceLine.NEAR_W;
                float db = b.w - ScreenSpaceLine.NEAR_W;
                if (da < 0 && db < 0) {
                    continue;
                }
                if (da < 0) {
                    a.lerp(b, da / (da - db));
                } else if (db < 0) {
                    b.lerp(a, db / (db - da));
                }
                for (Vector4f point : new Vector4f[]{a, b}) {
                    if (point.w <= 0.001f || !point.isFinite() || point.z > point.w) {
                        continue;
                    }
                    float x = (point.x / point.w + 1) * width / 2;
                    float y = (1 - point.y / point.w) * height / 2;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (!Float.isFinite(minX + minY + maxX + maxY) || maxX < 0 || maxY < 0 || minX > width || minY > height) {
            return null;
        }
        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        maxX = Math.min(width, maxX);
        maxY = Math.min(height, maxY);
        return maxX > minX && maxY > minY ? new Bounds(minX, minY, maxX - minX, maxY - minY) : null;
    }

    private EspProjection() {
    }
}
