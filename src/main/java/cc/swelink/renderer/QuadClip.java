package cc.swelink.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.List;

final class QuadClip {
    private static final Matrix3x2fc IDENTITY = new Matrix3x2f();
    private static final float STRIP = 0.25f;

    record Shape(float left, float top, float right, float bottom, float radius) {
        float inset(float y0, float y1) {
            if (radius <= 0) return 0;
            float dy;
            if (y0 < top + radius) {
                dy = top + radius - y0;
            } else if (y1 > bottom - radius) {
                dy = y1 - (bottom - radius);
            } else {
                return 0;
            }
            if (dy >= radius) return radius;
            return radius - (float) Math.sqrt(radius * radius - dy * dy);
        }

        boolean touchesCorner(float x0, float y0, float x1, float y1) {
            return radius > 0 && (y0 < top + radius || y1 > bottom - radius)
                    && (x0 < left + radius || x1 > right - radius);
        }
    }

    @FunctionalInterface
    interface Color {
        int at(float localX, float localY);
    }

    private QuadClip() {
    }

    static void quad(VertexConsumer vertices, Matrix3x2fc pose, List<Shape> shapes, float left, float top, float right,
                     float bottom, float u0, float v0, float u1, float v1, Color color) {
        if (shapes.isEmpty() || pose.m01() != 0 || pose.m10() != 0 || pose.m00() <= 0 || pose.m11() <= 0) {
            emit(vertices, pose, left, top, right, bottom, u0, v0, u1, v1, color, left, top, right, bottom);
            return;
        }
        float sx = pose.m00(), sy = pose.m11(), tx = pose.m20(), ty = pose.m21();
        float screenLeft = left * sx + tx, screenRight = right * sx + tx;
        float screenTop = top * sy + ty, screenBottom = bottom * sy + ty;
        float x0 = screenLeft, x1 = screenRight, y0 = screenTop, y1 = screenBottom;
        boolean corners = false;
        for (Shape shape : shapes) {
            x0 = Math.max(x0, shape.left);
            x1 = Math.min(x1, shape.right);
            y0 = Math.max(y0, shape.top);
            y1 = Math.min(y1, shape.bottom);
        }
        if (x1 <= x0 || y1 <= y0) return;
        for (Shape shape : shapes) {
            corners |= shape.touchesCorner(x0, y0, x1, y1);
        }
        if (!corners) {
            screen(vertices, pose, x0, y0, x1, y1, screenLeft, screenTop, screenRight, screenBottom, u0, v0, u1, v1, color);
            return;
        }
        for (float stripTop = y0; stripTop < y1; stripTop += STRIP) {
            float stripBottom = Math.min(y1, stripTop + STRIP);
            float stripLeft = x0, stripRight = x1;
            for (Shape shape : shapes) {
                float inset = shape.inset(stripTop, stripBottom);
                stripLeft = Math.max(stripLeft, shape.left + inset);
                stripRight = Math.min(stripRight, shape.right - inset);
            }
            if (stripRight > stripLeft) {
                screen(vertices, pose, stripLeft, stripTop, stripRight, stripBottom,
                        screenLeft, screenTop, screenRight, screenBottom, u0, v0, u1, v1, color);
            }
        }
    }

    private static void screen(VertexConsumer vertices, Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                               float left, float top, float right, float bottom,
                               float u0, float v0, float u1, float v1, Color color) {
        float clipU0 = lerp(u0, u1, (x0 - left) / (right - left));
        float clipU1 = lerp(u0, u1, (x1 - left) / (right - left));
        float clipV0 = lerp(v0, v1, (y0 - top) / (bottom - top));
        float clipV1 = lerp(v0, v1, (y1 - top) / (bottom - top));
        float sx = pose.m00(), sy = pose.m11(), tx = pose.m20(), ty = pose.m21();
        emit(vertices, IDENTITY, x0, y0, x1, y1, clipU0, clipV0, clipU1, clipV1, color,
                (x0 - tx) / sx, (y0 - ty) / sy, (x1 - tx) / sx, (y1 - ty) / sy);
    }

    private static void emit(VertexConsumer vertices, Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                             float u0, float v0, float u1, float v1, Color color,
                             float localX0, float localY0, float localX1, float localY1) {
        vertices.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color.at(localX0, localY0));
        vertices.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color.at(localX0, localY1));
        vertices.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color.at(localX1, localY1));
        vertices.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color.at(localX1, localY0));
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
