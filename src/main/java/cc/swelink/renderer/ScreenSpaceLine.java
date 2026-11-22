package cc.swelink.renderer;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class ScreenSpaceLine {
    public static final float NEAR_W = 0.06f;

    private ScreenSpaceLine() {
    }

    public static Vec3[] quad(Matrix4fc projection, Matrix4fc inverse, Vec3 from, Vec3 to,
                              int width, int height, float pixels) {
        if (width <= 0 || height <= 0 || pixels <= 0) return null;
        var start = projection.transform(new Vector4f((float) from.x, (float) from.y, (float) from.z, 1));
        var end = projection.transform(new Vector4f((float) to.x, (float) to.y, (float) to.z, 1));
        if (!start.isFinite() || !end.isFinite()) return null;
        float nearStart = start.w - NEAR_W;
        float nearEnd = end.w - NEAR_W;
        if (nearStart < 0 && nearEnd < 0) return null;
        if (nearStart < 0) start.lerp(end, nearStart / (nearStart - nearEnd));
        else if (nearEnd < 0) end.lerp(start, nearEnd / (nearEnd - nearStart));
        if (start.w <= 1.0e-5f || end.w <= 1.0e-5f) return null;
        float dx = (end.x / end.w - start.x / start.w) * width;
        float dy = (end.y / end.w - start.y / start.w) * height;
        double length = Math.hypot(dx, dy);
        if (length < 1.0e-5) return null;
        float offsetX = (float) (-dy / length * pixels / width);
        float offsetY = (float) (dx / length * pixels / height);
        Vec3[] corners = {
                unproject(inverse, start, -offsetX, -offsetY),
                unproject(inverse, end, -offsetX, -offsetY),
                unproject(inverse, end, offsetX, offsetY),
                unproject(inverse, start, offsetX, offsetY)
        };
        for (Vec3 point : corners) if (point == null) return null;
        return corners;
    }

    private static Vec3 unproject(Matrix4fc inverse, Vector4f point, float offsetX, float offsetY) {
        var result = inverse.transform(new Vector4f(point.x + offsetX * point.w,
                point.y + offsetY * point.w, point.z, point.w));
        if (!result.isFinite() || Math.abs(result.w) < 1.0e-8f) return null;
        return new Vec3(result.x / result.w, result.y / result.w, result.z / result.w);
    }
}
