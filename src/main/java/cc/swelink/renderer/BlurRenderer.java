package cc.swelink.renderer;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.PostProcessingModule;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;

import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class BlurRenderer implements AutoCloseable {
    public static final int LEFT = 1;
    public static final int TOP = 2;
    public static final int RIGHT = 4;
    public static final int BOTTOM = 8;
    private static final float MENU_GLASS_SPLIT = 1f / 16;
    public static final BlurRenderer INSTANCE = new BlurRenderer();
    public static final BlurRenderer FROSTED_HUD = new BlurRenderer();
    private static final RenderPipeline DOWNSAMPLE = pipeline("downsample");
    private static final RenderPipeline SCENE_COPY = pipeline("scene_copy");
    private static final RenderPipeline GAUSSIAN_HORIZONTAL = pipeline("gaussian_horizontal");
    private static final RenderPipeline GAUSSIAN_VERTICAL = pipeline("gaussian_vertical");
    private static final Map<GuiRenderState, List<Request>> FRAMES = new IdentityHashMap<>();
    private static final Map<GuiRenderState, Request> OPEN = new IdentityHashMap<>();
    private static final List<TextureTarget[]> POOL = new ArrayList<>();
    private static final Request DISABLED = new Request(1, 0);
    private static TextureTarget flat;
    private static boolean closing;

    public synchronized void frostedGlass(Renderer2D renderer, Glass glass, float x, float y, float width, float height,
                                          float radius, int tint) {
        frostedGlass(renderer, glass, x, y, width, height, radius, tint, 0, 0, 0);
    }

    public synchronized void frostedGlass(Renderer2D renderer, Glass glass, float x, float y, float width, float height,
                                          float radius, int tint, int edges) {
        frostedGlass(renderer, glass, x, y, width, height, radius, tint, 0, 0, edges);
    }

    public synchronized void frostedGlass(Renderer2D renderer, Glass glass, float x, float y, float width, float height,
                                          float radius, int tint, float solidWidth, int solidColor, int edges) {
        if (glass == Glass.HUD && solidWidth == 0) {
            solidWidth = MENU_GLASS_SPLIT;
            solidColor = tint;
        }
        glass.target().roundedSurface(renderer, x, y, width, height, radius, glass.downsample, glass.passes, tint,
                Pipelines.FROSTED_GLASS, solidWidth, solidColor, edges);
    }


    public synchronized void hudGlass(Renderer2D renderer, float x, float y, float width, float height,
                                      float radius, int tint) {
        frostedGlass(renderer, Glass.HUD, x, y, width, height, radius, tint, MENU_GLASS_SPLIT, tint, 0);
    }

    public synchronized void frostedGlassShape(Renderer2D renderer, float x, float y, float width, float height, int tint,
                                               float[] corners, float[] fillets, int verticalFillets) {
        FROSTED_HUD.shapedSurface(renderer, x, y, width, height, tint, corners, fillets, verticalFillets);
    }

    public synchronized void frostedGlassWithHeader(Renderer2D renderer, Glass glass, float x, float y, float width,
                                                    float height, float radius, int tint, float headerHeight,
                                                    int headerColor) {
        frostedGlassWithHeader(renderer, glass, x, y, width, height, radius, tint, headerHeight, headerColor, 0);
    }

    public synchronized void frostedGlassWithHeader(Renderer2D renderer, Glass glass, float x, float y, float width,
                                                    float height, float radius, int tint, float headerHeight,
                                                    int headerColor, int edges) {
        frostedGlass(renderer, glass, x, y, width, height, radius, tint, -Math.clamp(headerHeight, 0, height),
                headerColor, edges);
    }

    private void roundedSurface(Renderer2D renderer, float x, float y, float width, float height,
                                float radius, int downsample, int passes, int tint, RenderPipeline pipeline) {
        roundedSurface(renderer, x, y, width, height, radius, downsample, passes, tint, pipeline, 0, 0, 0);
    }

    private void roundedSurface(Renderer2D renderer, float x, float y, float width, float height,
                                float radius, int downsample, int passes, int tint, RenderPipeline pipeline,
                                float solidWidth, int solidColor, int edges) {
        if (!Renderer2D.drawable(x, y, width, height) || (tint >>> 24) == 0) {
            return;
        }
        Request request = request(renderer, downsample, passes, x, y, width, height);
        Supplier<TextureSetup> texture = () -> textureSetup(request);
        float clampedRadius = Math.min(SdfPipeline.corner(radius, width, height), 127);
        int encodedRadius = Math.round(clampedRadius * 16) | (edges & 15) << 11;
        int encodedSplit = Math.round(Math.clamp(solidWidth, -Math.min(height, 2047), Math.min(width, 2047)) * 16);
        float spreadX = (edges & (TOP | BOTTOM)) != 0 ? clampedRadius : 0;
        float spreadY = (edges & (LEFT | RIGHT)) != 0 ? clampedRadius : 0;
        float x1 = x - spreadX;
        float y1 = y - spreadY;
        float x2 = x + width + spreadX;
        float y2 = y + height + spreadY;
        renderer.submitDeferred(pipeline, texture, x1, y1, x2 - x1, y2 - y1, (vertices, pose) -> {
            blurVertex(vertices, pose, x1, y1, -spreadX, -spreadY, width, height, encodedRadius, tint, encodedSplit, solidColor);
            blurVertex(vertices, pose, x1, y2, -spreadX, height + spreadY, width, height, encodedRadius, tint, encodedSplit, solidColor);
            blurVertex(vertices, pose, x2, y2, width + spreadX, height + spreadY, width, height, encodedRadius, tint, encodedSplit, solidColor);
            blurVertex(vertices, pose, x2, y1, width + spreadX, -spreadY, width, height, encodedRadius, tint, encodedSplit, solidColor);
        });
    }

    private void shapedSurface(Renderer2D renderer, float x, float y, float width, float height, int tint,
                               float[] corners, float[] fillets, int verticalFillets) {
        if (!Renderer2D.drawable(x, y, width, height) || (tint >>> 24) == 0) {
            return;
        }
        float limit = Math.min(width, height) * 0.5f;
        int[] radius = new int[4];
        int[] fillet = new int[4];
        float spread = 0;
        for (int i = 0; i < 4; i++) {
            radius[i] = halfUnits(Math.min(corners[i], limit));
            fillet[i] = halfUnits(Math.min(fillets[i], height));
            spread = Math.max(spread, fillet[i] / 2f);
        }
        Request request = request(renderer, Glass.HUD.downsample, Glass.HUD.passes, x, y, width, height);
        Supplier<TextureSetup> texture = () -> textureSetup(request);
        int first = 0x8000 | fillet[2] | fillet[3] << 5 | (verticalFillets & 15) << 10;
        int second = radius[0] | radius[1] << 5 | radius[2] << 10;
        int third = radius[3] | fillet[0] << 5 | fillet[1] << 10;
        int fourth = (tint >>> 24) << 8;
        int color = tint | 0xFF000000;
        float pad = spread;
        float x1 = x - pad;
        float y1 = y - pad;
        float x2 = x + width + pad;
        float y2 = y + height + pad;
        renderer.submitDeferred(Pipelines.FROSTED_GLASS, texture, x1, y1, x2 - x1, y2 - y1, (vertices, pose) -> {
            shapedVertex(vertices, pose, x1, y1, -pad, -pad, width, height, color, first, second, third, fourth);
            shapedVertex(vertices, pose, x1, y2, -pad, height + pad, width, height, color, first, second, third, fourth);
            shapedVertex(vertices, pose, x2, y2, width + pad, height + pad, width, height, color, first, second, third, fourth);
            shapedVertex(vertices, pose, x2, y1, width + pad, -pad, width, height, color, first, second, third, fourth);
        });
    }

    private static int halfUnits(float value) {
        return Math.clamp(Math.round(value * 2), 0, 31);
    }

    private static void shapedVertex(VertexConsumer vertices, Matrix3x2fc pose, float x, float y, float localX, float localY,
                                     float width, float height, int color, int first, int second, int third, int fourth) {
        vertices.addVertexWith2DPose(pose, x, y).setColor(color).setUv(localX, localY).setUv3(width, height)
                .setUv1(first, second).setUv2(third, fourth);
    }

    private static void blurVertex(VertexConsumer vertices, Matrix3x2fc pose, float x, float y,
                                   float localX, float localY, float width, float height, int radius, int tint,
                                   int split, int solidColor) {
        vertices.addVertexWith2DPose(pose, x, y).setColor(split != 0 ? tint | 0xFF000000 : tint).setUv(localX, localY)
                .setUv3(width, height).setUv1(radius, split)
                .setUv2(solidColor >> 8 & 0xFFFF, (tint >>> 24) << 8 | solidColor & 0xFF);
    }

    public synchronized void backdrop(Renderer2D renderer, float x, float y, float width, float height, int downsample, int passes, int tint) {
        Request request = request(renderer, downsample, passes, x, y, width, height);
        float u0 = x / renderer.graphics.guiWidth();
        float v0 = 1 - y / renderer.graphics.guiHeight();
        float u1 = (x + width) / renderer.graphics.guiWidth();
        float v1 = 1 - (y + height) / renderer.graphics.guiHeight();
        renderer.submitDeferred(RenderPipelines.GUI_TEXTURED, () -> textureSetup(request), x, y, width, height, (vertices, pose) -> {
            vertices.addVertexWith2DPose(pose, x, y).setUv(u0, v0).setColor(tint);
            vertices.addVertexWith2DPose(pose, x, y + height).setUv(u0, v1).setColor(tint);
            vertices.addVertexWith2DPose(pose, x + width, y + height).setUv(u1, v1).setColor(tint);
            vertices.addVertexWith2DPose(pose, x + width, y).setUv(u1, v0).setColor(tint);
        });
    }

    private Request request(Renderer2D renderer, int downsample, int passes, float x, float y, float width, float height) {
        var pose = renderer.graphics.pose();
        var from = pose.transformPosition(x, y, new Vector2f());
        var to = pose.transformPosition(x + width, y + height, new Vector2f());
        float[] area = {Math.min(from.x, to.x), Math.min(from.y, to.y), Math.max(from.x, to.x), Math.max(from.y, to.y)};
        return mark(renderer.graphics, downsample, passes, area);
    }

    public static synchronized void group(Renderer2D renderer) {
        mark(renderer.graphics, Glass.HUD.downsample, Glass.HUD.passes, null);
    }

    public static synchronized void endGroup(Renderer2D renderer) {
        OPEN.remove(GuiGraphicsAccess.of(renderer.graphics).paradox$renderState());
    }

    public static synchronized void endGroup(GuiGraphicsExtractor graphics) {
        OPEN.remove(GuiGraphicsAccess.of(graphics).paradox$renderState());
    }

    private static synchronized Request mark(GuiGraphicsExtractor graphics, int downsample, int passes, float[] area) {
        closing = false;
        if (!PostProcessingModule.active()) return DISABLED;
        GuiRenderState state = GuiGraphicsAccess.of(graphics).paradox$renderState();
        int clampedDownsample = Math.clamp(downsample, 1, 8);
        int clampedPasses = Math.clamp(passes, 0, 8);
        Request open = OPEN.get(state);
        if (open != null && open.downsample == clampedDownsample && open.passes == clampedPasses && !open.overlaps(area)) {
            if (area != null) open.areas.add(area);
            return open;
        }
        Request request = new Request(clampedDownsample, clampedPasses);
        if (area != null) request.areas.add(area);
        OPEN.put(state, request);
        FRAMES.computeIfAbsent(state, key -> new ArrayList<>()).add(request);
        graphics.nextStratum();
        state.addGuiElement(new Marker(request, new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight())));
        graphics.nextStratum();
        return request;
    }

    public static Consumer<RenderTarget> captureFor(GuiElementRenderState element) {
        return element instanceof Marker marker ? target -> capture(marker.request, target) : null;
    }

    private static TextureSetup textureSetup(Request request) {
        GpuTextureView view = request == DISABLED ? flatTexture() : request.result;
        return TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }

    private static synchronized GpuTextureView flatTexture() {
        if (flat == null) {
            flat = new TextureTarget("Paradox flat glass", 1, 1, GpuFormat.RGBA8_UNORM, null);
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(flat.getColorTexture(),
                    new Vector4f(0.2f, 0.2f, 0.2f, 1));
        }
        return flat.getColorTextureView();
    }

    public static synchronized void prepareFrame(GuiRenderState state) {
        RenderSystem.assertOnRenderThread();
        OPEN.remove(state);
        List<Request> frame = FRAMES.remove(state);
        if (frame == null) {
            if (closing) release();
            return;
        }
        RenderTarget source = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        for (int i = 0; i < frame.size(); i++) {
            Request request = frame.get(i);
            int width = Math.max(1, (source.width + request.downsample - 1) / request.downsample);
            int height = Math.max(1, (source.height + request.downsample - 1) / request.downsample);
            if (POOL.size() <= i) POOL.add(new TextureTarget[2]);
            TextureTarget[] pair = POOL.get(i);
            if (pair[0] == null || pair[0].width != width || pair[0].height != height) {
                if (pair[0] != null) pair[0].destroyBuffers();
                if (pair[1] != null) pair[1].destroyBuffers();
                pair[0] = new TextureTarget("Paradox blur A" + i, width, height, GpuFormat.RGBA8_UNORM, null);
                pair[1] = new TextureTarget("Paradox blur B" + i, width, height, GpuFormat.RGBA8_UNORM, null);
            }
            request.first = pair[0];
            request.second = pair[1];
            request.result = pair[0].getColorTextureView();
        }
    }

    private static void capture(Request request, RenderTarget source) {
        if (request.first == null) return;
        pass(request.passes == 0 ? SCENE_COPY : DOWNSAMPLE, source.getColorTextureView(), request.first);
        for (int i = 0; i < request.passes; i++) {
            pass(GAUSSIAN_HORIZONTAL, request.first.getColorTextureView(), request.second);
            pass(GAUSSIAN_VERTICAL, request.second.getColorTextureView(), request.first);
        }
    }

    static void copyScene(RenderTarget source, TextureTarget output) {
        pass(SCENE_COPY, source.getColorTextureView(), output);
    }

    private static void pass(RenderPipeline pipeline, GpuTextureView input, TextureTarget output) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var pass = encoder.createRenderPass(() -> "Paradox backdrop blur", output.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setUniform("Sampler0", input, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }

    private static RenderPipeline pipeline(String name) {
        return RenderPipeline.builder().withLocation(Paradox.id("pipeline/" + name))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(Paradox.id("core/" + name))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false).withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, 15)).build();
    }

    @Override
    public void close() {
        synchronized (BlurRenderer.class) {
            closing = true;
        }
    }

    public static synchronized void shutdown() {
        FRAMES.clear();
        OPEN.clear();
        release();
        if (flat != null) {
            flat.destroyBuffers();
            flat = null;
        }
    }

    private static void release() {
        for (TextureTarget[] pair : POOL) {
            for (TextureTarget target : pair) if (target != null) target.destroyBuffers();
        }
        POOL.clear();
    }

    private static final class Request {
        final int downsample;
        final int passes;
        final List<float[]> areas = new ArrayList<>();
        TextureTarget first;
        TextureTarget second;
        GpuTextureView result;

        Request(int downsample, int passes) {
            this.downsample = downsample;
            this.passes = passes;
        }

        boolean overlaps(float[] area) {
            if (area == null) return false;
            for (float[] drawn : areas) {
                if (area[0] < drawn[2] && drawn[0] < area[2] && area[1] < drawn[3] && drawn[1] < area[3]) return true;
            }
            return false;
        }
    }

    private record Marker(Request request, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public RenderPipeline pipeline() {
            return Pipelines.GUI_BLUR_MARKER;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            vertices.addVertex(0, 0, 0).setColor(0);
            vertices.addVertex(0, bounds.height(), 0).setColor(0);
            vertices.addVertex(bounds.width(), bounds.height(), 0).setColor(0);
            vertices.addVertex(bounds.width(), 0, 0).setColor(0);
        }
    }
}
