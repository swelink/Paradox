package cc.swelink.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

import java.util.function.Supplier;

public final class Renderer2D implements AutoCloseable {
    private static final float PACKED_LIMIT = 2047;
    final GuiGraphicsExtractor graphics;
    public final SdfPipeline sdf;
    public final TexturePipeline textures;
    public final TextRenderer text;
    public final ClipStack clips;
    public final BlurRenderer blur = BlurRenderer.INSTANCE;
    private boolean closed;
    private float opacity = 1;

    public Renderer2D(GuiGraphicsExtractor graphics) {
        this(graphics, TextRenderer.Font.INTER);
    }

    public Renderer2D(GuiGraphicsExtractor graphics, TextRenderer.Font font) {
        this.graphics = graphics;
        sdf = new SdfPipeline(this);
        textures = new TexturePipeline(this);
        text = new TextRenderer(this, font);
        clips = new ClipStack(graphics);
    }

    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    public void opacity(float value) {
        opacity = Math.clamp(value, 0, 1);
    }

    public float opacity() {
        return opacity;
    }

    public OpacityScope pushOpacity(float factor) {
        float previous = opacity;
        opacity(previous * factor);
        return () -> opacity = previous;
    }

    @FunctionalInterface
    public interface OpacityScope extends AutoCloseable {
        @Override
        void close();
    }

    public void fill(float x, float y, float width, float height, int color) {
        fill(x, y, width, height, color, true);
    }

    public void fill(float x, float y, float width, float height, int color, boolean pixelAligned) {
        float pixelScale = Minecraft.getInstance().getWindow().getGuiScale();
        submit(RenderPipelines.GUI, TextureSetup.noTexture(), x, y, width, height, (vertices, pose) -> {
            if (!pixelAligned) {
                vertices.addVertexWith2DPose(pose, x, y).setColor(color);
                vertices.addVertexWith2DPose(pose, x, y + height).setColor(color);
                vertices.addVertexWith2DPose(pose, x + width, y + height).setColor(color);
                vertices.addVertexWith2DPose(pose, x + width, y).setColor(color);
                return;
            }
            float[] box = pixelBox(pose, x, y, width, height, pixelScale);
            vertices.addVertex(box[0], box[1], 0).setColor(color);
            vertices.addVertex(box[0], box[3], 0).setColor(color);
            vertices.addVertex(box[2], box[3], 0).setColor(color);
            vertices.addVertex(box[2], box[1], 0).setColor(color);
        });
    }

    public void gradient(float x, float y, float width, float height, int topLeft, int bottomLeft, int bottomRight, int topRight) {
        gradient(x, y, width, height, topLeft, bottomLeft, bottomRight, topRight, true);
    }

    public void gradient(float x, float y, float width, float height, int topLeft, int bottomLeft,
                         int bottomRight, int topRight, boolean pixelAligned) {
        float pixelScale = Minecraft.getInstance().getWindow().getGuiScale();
        submit(RenderPipelines.GUI, TextureSetup.noTexture(), x, y, width, height, (vertices, pose) -> {
            if (!pixelAligned) {
                vertices.addVertexWith2DPose(pose, x, y).setColor(topLeft);
                vertices.addVertexWith2DPose(pose, x, y + height).setColor(bottomLeft);
                vertices.addVertexWith2DPose(pose, x + width, y + height).setColor(bottomRight);
                vertices.addVertexWith2DPose(pose, x + width, y).setColor(topRight);
                return;
            }
            float[] box = edgeBox(pose, x, y, width, height, pixelScale);
            vertices.addVertex(box[0], box[1], 0).setColor(topLeft);
            vertices.addVertex(box[0], box[3], 0).setColor(bottomLeft);
            vertices.addVertex(box[2], box[3], 0).setColor(bottomRight);
            vertices.addVertex(box[2], box[1], 0).setColor(topRight);
        });
    }

    public void area(float[] xs, float[] ys, int count, float bottom, int color) {
        if (count < 2) return;
        float left = xs[0];
        float right = xs[0];
        float top = bottom;
        for (int i = 0; i < count; i++) {
            left = Math.min(left, xs[i]);
            right = Math.max(right, xs[i]);
            top = Math.min(top, ys[i]);
        }
        submit(RenderPipelines.GUI, TextureSetup.noTexture(), left, top, right - left, bottom - top, (vertices, pose) -> {
            for (int i = 0; i + 1 < count; i++) {
                vertices.addVertexWith2DPose(pose, xs[i], ys[i]).setColor(color);
                vertices.addVertexWith2DPose(pose, xs[i], bottom).setColor(color);
                vertices.addVertexWith2DPose(pose, xs[i + 1], bottom).setColor(color);
                vertices.addVertexWith2DPose(pose, xs[i + 1], ys[i + 1]).setColor(color);
            }
        });
    }

    public float pixelSize() {
        var current = graphics.pose();
        float scale = (float) Math.sqrt(Math.abs(current.m00() * current.m11() - current.m01() * current.m10()));
        return 1 / Math.max(Minecraft.getInstance().getWindow().getGuiScale() * scale, 0.0001f);
    }

    private static void gradientVertex(VertexConsumer vertices, Matrix3x2fc pose, float x, float y,
                                       float localX, float localY, int width, int height, int radius, int color) {
        vertices.addVertexWith2DPose(pose, x, y).setColor(color).setUv(0, 0).setUv3(localX, localY)
                .setUv1(width, height).setUv2(radius, 0);
    }

    public void hueBar(float x, float y, float width, float height) {
        colorControl(x, y, width, height, height / 2, 0xFFFFFFFF, 10);
    }

    public void colorField(float x, float y, float width, float height, float radius, int hue) {
        colorControl(x, y, width, height, radius, hue, 11);
    }

    private void colorControl(float x, float y, float width, float height, float radius, int color, int type) {
        if (!packable(x, y, width, height)) return;
        int w = Math.round(width * 16), h = Math.round(height * 16), r = Math.round(SdfPipeline.corner(radius, width, height) * 16);
        submit(type == 10 ? Pipelines.HUE_BAR : Pipelines.COLOR_FIELD, TextureSetup.noTexture(), x, y, width, height, (vertices, pose) -> {
            gradientVertex(vertices, pose, x, y, 0, 0, w, h, r, color);
            gradientVertex(vertices, pose, x, y + height, 0, height, w, h, r, color);
            gradientVertex(vertices, pose, x + width, y + height, width, height, w, h, r, color);
            gradientVertex(vertices, pose, x + width, y, width, 0, w, h, r, color);
        });
    }

    private static float[] edgeBox(Matrix3x2fc pose, float x, float y, float width, float height, float pixelScale) {
        Vector2f start = pose.transformPosition(x, y, new Vector2f());
        Vector2f end = pose.transformPosition(x + width, y + height, new Vector2f());
        float left = Math.round(Math.min(start.x, end.x) * pixelScale);
        float top = Math.round(Math.min(start.y, end.y) * pixelScale);
        float right = Math.max(left + 1, Math.round(Math.max(start.x, end.x) * pixelScale));
        float bottom = Math.max(top + 1, Math.round(Math.max(start.y, end.y) * pixelScale));
        return new float[]{left / pixelScale, top / pixelScale, right / pixelScale, bottom / pixelScale};
    }

    private static float[] pixelBox(Matrix3x2fc pose, float x, float y, float width, float height, float pixelScale) {
        Vector2f start = pose.transformPosition(x, y, new Vector2f());
        Vector2f end = pose.transformPosition(x + width, y + height, new Vector2f());
        float left = Math.round(Math.min(start.x, end.x) * pixelScale);
        float top = Math.round(Math.min(start.y, end.y) * pixelScale);
        float columns = Math.max(1, Math.round(Math.abs(end.x - start.x) * pixelScale));
        float rows = Math.max(1, Math.round(Math.abs(end.y - start.y) * pixelScale));
        return new float[]{left / pixelScale, top / pixelScale, (left + columns) / pixelScale, (top + rows) / pixelScale};
    }

    void submit(RenderPipeline pipeline, TextureSetup texture, float x, float y, float width, float height, Vertices vertices) {
        submitDeferred(pipeline, () -> texture, x, y, width, height, vertices);
    }

    void submitDeferred(RenderPipeline pipeline, Supplier<TextureSetup> texture, float x, float y, float width, float height, Vertices vertices) {
        if (closed || opacity == 0) {
            return;
        }
        if (!drawable(x, y, width, height)) {
            return;
        }
        var pose = new Matrix3x2f(graphics.pose());
        var access = GuiGraphicsAccess.of(graphics);
        var scissor = access.paradox$scissorArea();
        int left = (int) Math.floor(x);
        int top = (int) Math.floor(y);
        var bounds = new ScreenRectangle(left, top,
                (int) Math.ceil(x + width) - left, (int) Math.ceil(y + height) - top)
                .transformMaxBounds(pose);
        if (scissor != null) {
            bounds = bounds.intersection(scissor);
            if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
                return;
            }
        }
        access.paradox$renderState().addGuiElement(new Element(pipeline, texture, scissor, bounds, pose, vertices, opacity));
    }

    static boolean drawable(float x, float y, float width, float height) {
        return width > 0 && height > 0 && Float.isFinite(x + y + width + height);
    }

    static boolean packable(float x, float y, float width, float height) {
        return drawable(x, y, width, height) && width <= PACKED_LIMIT && height <= PACKED_LIMIT;
    }

    @Override
    public void close() {
        if (!closed) {
            clips.close();
            closed = true;
        }
    }

    @FunctionalInterface
    interface Vertices {
        void build(VertexConsumer consumer, Matrix3x2fc pose);
    }

    private record Element(RenderPipeline pipeline, Supplier<TextureSetup> texture, ScreenRectangle scissorArea,
                           ScreenRectangle bounds, Matrix3x2fc pose,
                           Vertices vertices, float opacity) implements GuiElementRenderState {
        @Override
        public TextureSetup textureSetup() {
            return texture.get();
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            vertices.build(opacity == 1 ? consumer : new OpacityVertexConsumer(consumer, opacity), pose);
        }
    }
}
