package cc.swelink.renderer;

import cc.swelink.systems.modules.render.PostProcessingModule;
import cc.swelink.mixin.PictureInPictureRendererAccessor;
import cc.swelink.Paradox;
import cc.swelink.utils.render.PostEffectUtils;
import cc.swelink.utils.render.OutlineShaderSettings;
import cc.swelink.utils.render.ShaderKernels;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import java.util.Optional;
import java.util.Set;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import org.joml.Quaternionf;

public final class EspPreviewRenderer extends PictureInPictureRenderer<EspPreviewRenderState> {
    private static final RenderPipeline PREVIEW_INPUT = RenderPipeline.builder()
            .withLocation(Paradox.id("pipeline/esp_preview_input"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Paradox.id("core/esp_preview_input"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, 15)).build();
    private final CrossFrameResourcePool glowPool = new CrossFrameResourcePool(2);
    private TextureTarget glowTarget;

    @Override
    public Class<EspPreviewRenderState> getRenderStateClass() {
        return EspPreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(EspPreviewRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        var client = Minecraft.getInstance();
        client.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        pose.rotate(state.rotation());
        pose.translate(0, -state.entity().boundingBoxHeight / 2, 0);
        var camera = new CameraRenderState();
        camera.orientation = new Quaternionf(state.rotation()).conjugate().rotateY((float) Math.PI);
        client.getEntityRenderDispatcher().submit(state.entity(), camera, 0, 0, 0, pose, collector);
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2f;
    }

    @Override
    protected String getTextureLabel() {
        return "Paradox ESP player preview";
    }

    @Override
    protected void blitTexture(EspPreviewRenderState state, GuiRenderState renderState) {
        if (state.opacity() == 1) {
            super.blitTexture(state, renderState);
        } else {
            var view = ((PictureInPictureRendererAccessor) (Object) this).paradox$textureView();
            var texture = TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderState.addGuiElement(new Overlay(state, texture, false));
        }
        if (state.shader() && PostProcessingModule.active()) {
            var view = ((PictureInPictureRendererAccessor) (Object) this).paradox$textureView();
            var chain = Minecraft.getInstance().getShaderManager().getPostChain(Paradox.id("esp_preview"),
                    Set.of(PostChain.MAIN_TARGET_ID));
            if (chain == null) return;
            if (glowTarget == null || glowTarget.width != view.getWidth(0) || glowTarget.height != view.getHeight(0)) {
                if (glowTarget != null) glowTarget.destroyBuffers();
                glowTarget = new TextureTarget("Paradox ESP preview glow", view.getWidth(0), view.getHeight(0), GpuFormat.RGBA8_UNORM, null);
                glowPool.clear();
            }
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Paradox ESP preview glow input", glowTarget.getColorTextureView(), Optional.empty())) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(PREVIEW_INPUT));
                pass.setUniform("Sampler0", view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(3, 1, 0, 0);
            }
            ShaderKernels.configure(chain, state.outlineWidth(), state.glow());
            PostEffectUtils.writeUniform(chain, "EspConfig", state.outlineWidth(), state.fill(), state.glow(), state.shape());
            OutlineShaderSettings.configureWave(chain, state.waveMode());
            int color = state.color();
            PostEffectUtils.writeUniform(chain, "HandColor", ((color >>> 16) & 255) / 255f,
                    ((color >>> 8) & 255) / 255f, (color & 255) / 255f, 0);
            var projection = RenderSystem.getProjectionMatrixBuffer();
            var projectionType = RenderSystem.getProjectionType();
            try {
                chain.process(glowTarget, glowPool);
            } finally {
                RenderSystem.setProjectionMatrix(projection, projectionType);
                glowPool.endFrame();
            }
            var texture = TextureSetup.singleTexture(glowTarget.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderState.addGlyphToCurrentLayer(new Overlay(state, texture, true));
        }
    }

    @Override
    public void close() {
        super.close();
        if (glowTarget != null) {
            glowTarget.destroyBuffers();
            glowTarget = null;
        }
        glowPool.close();
    }

    private record Overlay(EspPreviewRenderState state, TextureSetup textureSetup, boolean shader) implements GuiElementRenderState {
        @Override
        public RenderPipeline pipeline() {
            return shader ? Pipelines.ESP_PREVIEW : RenderPipelines.GUI_TEXTURED;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return state.scissorArea();
        }

        @Override
        public ScreenRectangle bounds() {
            return state.bounds();
        }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            vertex(vertices, state.x0(), state.y0(), 0, 1);
            vertex(vertices, state.x0(), state.y1(), 0, 0);
            vertex(vertices, state.x1(), state.y1(), 1, 0);
            vertex(vertices, state.x1(), state.y0(), 1, 1);
        }

        private void vertex(VertexConsumer vertices, float x, float y, float u, float v) {
            vertices.addVertexWith2DPose(state.pose(), x, y)
                    .setColor((Math.round(255 * state.opacity()) << 24) | 0xFFFFFF).setUv(u, v);
            if (shader) vertices.setUv3(0, 0).setUv1(state.outlineWidth() * 16, Math.round(state.fill() * 16))
                    .setUv2(state.glow() * 16, state.shape());
        }
    }
}
