package cc.swelink.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.util.IdentityHashMap;
import java.util.Map;

public final class UniformGuiFade {
    private static final Map<GuiRenderState, Request> REQUESTS = new IdentityHashMap<>();
    private static TextureTarget backdrop;

    private UniformGuiFade() {
    }

    public static void begin(GuiGraphicsExtractor graphics, float opacity) {
        if (opacity >= 1) return;
        GuiRenderState state = GuiGraphicsAccess.of(graphics).paradox$renderState();
        Request request = new Request(Math.clamp(opacity, 0, 1));
        REQUESTS.put(state, request);
        graphics.nextStratum();
        state.addGuiElement(new Boundary(request, true, new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight())));
        graphics.nextStratum();
    }

    public static void end(GuiGraphicsExtractor graphics) {
        GuiRenderState state = GuiGraphicsAccess.of(graphics).paradox$renderState();
        Request request = REQUESTS.get(state);
        if (request == null) return;
        graphics.nextStratum();
        state.addGuiElement(new Boundary(request, false, new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight())));
        graphics.nextStratum();
    }

    public static boolean isStart(GuiElementRenderState element) {
        return element instanceof Boundary boundary && boundary.start;
    }

    public static void prepare(GuiRenderState state) {
        Request request = REQUESTS.get(state);
        if (request == null) return;
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (backdrop == null || backdrop.width != target.width || backdrop.height != target.height) {
            if (backdrop != null) backdrop.destroyBuffers();
            backdrop = new TextureTarget("Paradox uniform GUI fade", target.width, target.height, GpuFormat.RGBA8_UNORM, null);
        }
        request.texture = TextureSetup.singleTexture(backdrop.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    public static void capture(RenderTarget source) {
        BlurRenderer.copyScene(source, backdrop);
    }

    public static void finish(GuiRenderState state) {
        REQUESTS.remove(state);
    }

    public static void shutdown() {
        REQUESTS.clear();
        if (backdrop != null) {
            backdrop.destroyBuffers();
            backdrop = null;
        }
    }

    private static final class Request {
        final float opacity;
        TextureSetup texture;

        Request(float opacity) {
            this.opacity = opacity;
        }
    }

    private record Boundary(Request request, boolean start, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public RenderPipeline pipeline() {
            return start ? Pipelines.GUI_FADE_MARKER : Pipelines.GUI_FADE_OVERLAY;
        }

        @Override
        public TextureSetup textureSetup() {
            return start ? TextureSetup.noTexture() : request.texture;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            int color = start ? 0 : Math.round(255 * (1 - request.opacity)) << 24 | 0xFFFFFF;
            vertex(vertices, 0, 0, 0, 1, color);
            vertex(vertices, 0, bounds.height(), 0, 0, color);
            vertex(vertices, bounds.width(), bounds.height(), 1, 0, color);
            vertex(vertices, bounds.width(), 0, 1, 1, color);
        }

        private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color) {
            vertices.addVertex(x, y, 0).setColor(color);
            if (!start) vertices.setUv(u, v);
        }
    }
}
