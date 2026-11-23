package cc.swelink.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.render.TextureSetup;

import org.joml.Matrix3x2fc;

public final class SdfPipeline {
    private static final int FILL = 0;
    private static final int BORDER = 1;
    private static final int SHADOW = 2;
    private static final int CUTOUT = 4;
    private static final int BACKDROP_SHADOW = 5;
    private static final int DOCKED_BORDER = 6;
    private static final int DOCKED_FILL = 7;
    private static final int FIXED_POINT_SCALE = 16;
    private static final float ROUNDNESS = 0.95f;
    private static final float MIN_CORNER = 5;

    private final Renderer2D renderer;

    SdfPipeline(Renderer2D renderer) {
        this.renderer = renderer;
    }

    public static float corner(float radius, float width, float height) {
        if (!(radius > 0)) return 0;
        float limit = Math.max(0, Math.min(width, height) / 2);
        return Math.min(radius < MIN_CORNER || radius >= limit ? radius : radius * ROUNDNESS, limit);
    }

    public static float corner(float radius) {
        return corner(radius, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    public void rect(float x, float y, float width, float height, int color) {
        roundedRect(x, y, width, height, 0, color);
    }

    public void roundedRect(float x, float y, float width, float height, float radius, int color) {
        shape(x, y, width, height, corner(radius, width, height), 0, 0, color, FILL);
    }

    public void border(float x, float y, float width, float height, float radius, float borderWidth, int color) {
        if (borderWidth > 0) {
            shape(x, y, width, height, corner(radius, width, height), borderWidth, 0, color, BORDER);
        }
    }

    public void dockedBorder(float x, float y, float width, float height, float radius, float borderWidth, int color,
                             int edges) {
        if (!Renderer2D.drawable(x, y, width, height) || !(borderWidth > 0) || (color >>> 24) == 0) {
            return;
        }
        float clamped = corner(radius, width, height);
        int encodedRadius = fixed(clamped);
        int encodedBorder = fixed(borderWidth);
        int shapeType = DOCKED_BORDER | (edges & 15) << 4;
        float padding = clamped + borderWidth + 1;
        float x1 = x - padding;
        float y1 = y - padding;
        float x2 = x + width + padding;
        float y2 = y + height + padding;
        renderer.submit(Pipelines.SDF, TextureSetup.noTexture(), x1, y1, x2 - x1, y2 - y1, (vertices, pose) -> {
            vertex(vertices, pose, x1, y1, x1 - x, y1 - y, width, height, encodedRadius, encodedBorder, 0, shapeType, color);
            vertex(vertices, pose, x1, y2, x1 - x, y2 - y, width, height, encodedRadius, encodedBorder, 0, shapeType, color);
            vertex(vertices, pose, x2, y2, x2 - x, y2 - y, width, height, encodedRadius, encodedBorder, 0, shapeType, color);
            vertex(vertices, pose, x2, y1, x2 - x, y1 - y, width, height, encodedRadius, encodedBorder, 0, shapeType, color);
        });
    }

    public void dockedArea(float x, float y, float width, float height, float radius, int color, int edges,
                           float[] xs, float[] tops, float[] bottoms, int from, int to) {
        if (to - from < 2 || !Renderer2D.drawable(x, y, width, height) || (color >>> 24) == 0) return;
        float clamped = corner(radius, width, height);
        int encodedRadius = fixed(clamped);
        int shapeType = DOCKED_FILL | (edges & 15) << 4;
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (int i = from; i < to; i++) {
            left = Math.min(left, xs[i]);
            right = Math.max(right, xs[i]);
            top = Math.min(top, tops[i]);
            bottom = Math.max(bottom, bottoms[i]);
        }
        if (!(right > left && bottom > top)) return;
        renderer.submit(Pipelines.SDF, TextureSetup.noTexture(), left, top, right - left, bottom - top, (vertices, pose) -> {
            for (int i = from; i + 1 < to; i++) {
                float x1 = xs[i];
                float x2 = xs[i + 1];
                float top1 = tops[i];
                float top2 = tops[i + 1];
                float bottom1 = Math.max(top1, bottoms[i]);
                float bottom2 = Math.max(top2, bottoms[i + 1]);
                vertex(vertices, pose, x1, top1, x1 - x, top1 - y, width, height, encodedRadius, 0, 0, shapeType, color);
                vertex(vertices, pose, x1, bottom1, x1 - x, bottom1 - y, width, height, encodedRadius, 0, 0, shapeType, color);
                vertex(vertices, pose, x2, bottom2, x2 - x, bottom2 - y, width, height, encodedRadius, 0, 0, shapeType, color);
                vertex(vertices, pose, x2, top2, x2 - x, top2 - y, width, height, encodedRadius, 0, 0, shapeType, color);
            }
        });
    }

    public void dockedPolyline(float x, float y, float width, float height, float radius, int color, int edges,
                               float[] xs, float[] ys, int count, float thickness) {
        if (count < 2 || !(thickness > 0) || !Renderer2D.drawable(x, y, width, height) || (color >>> 24) == 0) return;
        float clamped = corner(radius, width, height);
        int encodedRadius = fixed(clamped);
        int shapeType = DOCKED_FILL | (edges & 15) << 4;
        float pixel = renderer.pixelSize();
        float core = Math.max(0, thickness / 2 - pixel / 2);
        float outer = core + pixel;
        float[] normalX = new float[count];
        float[] normalY = new float[count];
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int previous = Math.max(i - 1, 0);
            int next = Math.min(i + 1, count - 1);
            float dx = xs[next] - xs[previous];
            float dy = ys[next] - ys[previous];
            float length = (float) Math.hypot(dx, dy);
            if (length > 0) {
                normalX[i] = dy / length;
                normalY[i] = -dx / length;
            } else {
                normalY[i] = -1;
            }
            left = Math.min(left, xs[i]);
            right = Math.max(right, xs[i]);
            top = Math.min(top, ys[i]);
            bottom = Math.max(bottom, ys[i]);
        }
        int clear = color & 0xFFFFFF;
        renderer.submit(Pipelines.SDF, TextureSetup.noTexture(), left - outer, top - outer,
                right - left + outer * 2, bottom - top + outer * 2, (vertices, pose) -> {
            for (int i = 0; i + 1 < count; i++) {
                int j = i + 1;
                float[][] bands = {{core, -core}, {outer, core}, {-core, -outer}};
                int[][] colors = {{color, color}, {clear, color}, {color, clear}};
                for (int band = 0; band < 3; band++) {
                    float from = bands[band][0];
                    float to = bands[band][1];
                    float ax = xs[i] + normalX[i] * from, ay = ys[i] + normalY[i] * from;
                    float bx = xs[i] + normalX[i] * to, by = ys[i] + normalY[i] * to;
                    float cx = xs[j] + normalX[j] * to, cy = ys[j] + normalY[j] * to;
                    float dx = xs[j] + normalX[j] * from, dy = ys[j] + normalY[j] * from;
                    vertex(vertices, pose, ax, ay, ax - x, ay - y, width, height, encodedRadius, 0, 0, shapeType, colors[band][0]);
                    vertex(vertices, pose, bx, by, bx - x, by - y, width, height, encodedRadius, 0, 0, shapeType, colors[band][1]);
                    vertex(vertices, pose, cx, cy, cx - x, cy - y, width, height, encodedRadius, 0, 0, shapeType, colors[band][1]);
                    vertex(vertices, pose, dx, dy, dx - x, dy - y, width, height, encodedRadius, 0, 0, shapeType, colors[band][0]);
                }
            }
        });
    }

    public void circle(float centerX, float centerY, float radius, int color) {
        shape(centerX - radius, centerY - radius, radius * 2, radius * 2, radius, 0, 0, color, FILL);
    }

    public void clippedRoundedRect(float x, float y, float width, float height, float radius, int color,
                                   float left, float top, float right, float bottom) {
        clippedShape(x, y, width, height, corner(radius, width, height), color, FILL, left, top, right, bottom);
    }

    private void clippedShape(float x, float y, float width, float height, float radius, int color, int shapeType,
                              float left, float top, float right, float bottom) {
        if (!Renderer2D.drawable(x, y, width, height)) {
            return;
        }
        float padding = shapeType == CUTOUT ? 0 : 1;
        float x1 = Math.max(x - padding, left);
        float y1 = Math.max(y - padding, top);
        float x2 = Math.min(x + width + padding, right);
        float y2 = Math.min(y + height + padding, bottom);
        int encodedRadius = fixed(Math.clamp(radius, 0, Math.min(width, height) / 2));
        renderer.submit(Pipelines.SDF, TextureSetup.noTexture(), x1, y1, x2 - x1, y2 - y1, (vertices, pose) -> {
            vertex(vertices, pose, x1, y1, x1 - x, y1 - y, width, height, encodedRadius, 0, 0, shapeType, color);
            vertex(vertices, pose, x1, y2, x1 - x, y2 - y, width, height, encodedRadius, 0, 0, shapeType, color);
            vertex(vertices, pose, x2, y2, x2 - x, y2 - y, width, height, encodedRadius, 0, 0, shapeType, color);
            vertex(vertices, pose, x2, y1, x2 - x, y1 - y, width, height, encodedRadius, 0, 0, shapeType, color);
        });
    }

    public void roundLine(float x1, float y1, float x2, float y2, float thickness, int color) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (!(length > 0 && thickness > 0) || !Float.isFinite(length + thickness)) {
            return;
        }
        var pose = renderer.graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate((x1 + x2) / 2, (y1 + y2) / 2);
            pose.rotate((float) Math.atan2(y2 - y1, x2 - x1));
            float span = length + thickness;
            shape(-span / 2, -thickness / 2, span, thickness, thickness / 2, 0, 0, color, FILL);
        } finally {
            pose.popMatrix();
        }
    }

    public void shadow(float x, float y, float width, float height, float radius, float sigma, int color) {
        if (sigma > 0) {
            shape(x, y, width, height, corner(radius, width, height), 0, sigma, color, SHADOW);
        }
    }

    public void clippedBackdropShadow(float x, float y, float width, float height, float radius, float sigma, int color,
                                      float left, float top, float right, float bottom) {
        if (sigma > 0) {
            shape(x, y, width, height, corner(radius, width, height), 0, sigma, color, BACKDROP_SHADOW, left, top, right, bottom);
        }
    }

    private void shape(float x, float y, float width, float height, float radius, float border, float sigma, int color, int shapeType) {
        shape(x, y, width, height, radius, border, sigma, color, shapeType,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    private void shape(float x, float y, float width, float height, float radius, float border, float sigma, int color,
                       int shapeType, float left, float top, float right, float bottom) {
        if (!Renderer2D.drawable(x, y, width, height) || (color >>> 24) == 0) {
            return;
        }
        if (!Float.isFinite(radius + border + sigma) || radius < 0 || border < 0 || sigma < 0) {
            return;
        }
        float maxRadius = Math.min(width, height) * 0.5f;
        int encodedRadius = fixed(Math.min(radius, maxRadius));
        int encodedBorder = fixed(Math.min(border, maxRadius));
        int encodedSigma = fixed(sigma);
        float padding = shapeType == BACKDROP_SHADOW ? sigma * 4.5f + 2 : shapeType == SHADOW ? sigma * 4 + 2 : 1;
        float x1 = Math.max(x - padding, left);
        float y1 = Math.max(y - padding, top);
        float x2 = Math.min(x + width + padding, right);
        float y2 = Math.min(y + height + padding, bottom);
        renderer.submit(Pipelines.SDF, TextureSetup.noTexture(), x1, y1, x2 - x1, y2 - y1, (vertices, pose) -> {
            vertex(vertices, pose, x1, y1, x1 - x, y1 - y, width, height, encodedRadius, encodedBorder, encodedSigma, shapeType, color);
            vertex(vertices, pose, x1, y2, x1 - x, y2 - y, width, height, encodedRadius, encodedBorder, encodedSigma, shapeType, color);
            vertex(vertices, pose, x2, y2, x2 - x, y2 - y, width, height, encodedRadius, encodedBorder, encodedSigma, shapeType, color);
            vertex(vertices, pose, x2, y1, x2 - x, y1 - y, width, height, encodedRadius, encodedBorder, encodedSigma, shapeType, color);
        });
    }

    private static void vertex(VertexConsumer vertices, Matrix3x2fc pose, float x, float y, float localX, float localY,
                               float width, float height, int radius, int border, int sigma, int shapeType, int color) {
        vertices.addVertexWith2DPose(pose, x, y).setColor(color).setUv(localX, localY).setUv3(width, height)
                .setUv1(radius, border).setUv2(sigma, shapeType);
    }

    private static int fixed(float value) {
        return Math.round(Math.clamp(value, 0, 2047) * FIXED_POINT_SCALE);
    }
}
